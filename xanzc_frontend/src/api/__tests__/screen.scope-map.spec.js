import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../http', () => ({ call: vi.fn().mockResolvedValue([]) }));

import { call } from '../http';
import {
  listOrgProfiles,
  updateOrgProfile,
  listOrgGroups,
  listScreens,
  listScreenDatasources,
  listScreenMapRegionMetrics,
  createOrgGroup,
  updateOrgGroup,
  saveOrgGroupMembers,
  saveOrgGroupRoles,
  listScreenAccessRoles,
  saveScreenAccessRoles,
  saveScreen,
  saveScreenMetadata,
  queryScreenData,
  discardScreenCanvas,
  listScreenRoles,
  listScreenPublishLogs,
  probeScreenDatasourceColumns,
  saveScreenDatasource,
  updateScreenDatasource,
  deleteScreenDatasource,
  tryRunScreenDatasource,
  publishScreenCanvas,
  rollbackScreenCanvas
} from '../screen';

describe('大屏范围与机构配置 API', () => {
  beforeEach(() => call.mockClear());

  it('机构画像和机构组使用规格约定的独立管理端点，城市不混入 keyword', async () => {
    await listOrgProfiles({ city: '610100' });
    expect(call).toHaveBeenCalledWith('get', '/admin/org-profiles', { params: { city: '610100' } });
    await updateOrgProfile('X1', { operatingLevel: 'PRIMARY' });
    expect(call).toHaveBeenCalledWith('put', '/admin/org-profiles/X1', { data: { operatingLevel: 'PRIMARY' } });
    await listOrgGroups({ status: 'ACTIVE' });
    expect(call).toHaveBeenCalledWith('get', '/admin/org-groups', { params: {} });
  });

  it('数据源列表只向后端发送其声明的 dsType/keyword，业务条线由前端按 DTO 过滤', async () => {
    await listScreenDatasources({ bizLine: 'CORP', dsType: 'SINGLE', keyword: '存款' });
    expect(call).toHaveBeenCalledWith('get', '/screen/admin/datasources', {
      params: { dsType: 'SINGLE', keyword: '存款' }
    });
  });

  it('通用屏保存不静默补业务字段，也绝不夹带角色白名单', async () => {
    await saveScreen({
      screenName: '兼容屏', viewLevel: 'BRANCH', bizLine: 'COMMON', orgScopeMode: 'LEGACY_CONTEXT',
      allowedRoleCodes: ['R_SHOULD_NOT_BE_SENT'], accessRoleCodes: ['R_ALSO_NOT_SENT']
    });
    expect(call).toHaveBeenCalledWith('post', '/screen/admin/screens', {
      data: {
        screenName: '兼容屏', viewLevel: 'BRANCH', bizLine: 'COMMON', orgScopeMode: 'LEGACY_CONTEXT'
      }
    });
  });

  it('已有屏的元数据/范围编辑走独立 PUT /metadata，携带 CAS+原因并省略 id/blocks 与所有角色字段', async () => {
    await saveScreenMetadata({
      id: 12, screenCode: 'SCR_RETAIL', screenName: '零售总览', viewLevel: 'PROVINCE',
      bizLine: 'RETAIL', orgScopeMode: 'NAMED_GROUP', orgGroupCode: 'ORG_RETAIL',
      expectedVersion: 8, reason: '调整机构范围',
      blocks: [{ id: 99 }], allowedRoleCodes: ['R1'], accessRoleCodes: ['R2']
    });
    expect(call).toHaveBeenCalledWith('put', '/screen/admin/screens/12/metadata', {
      data: {
        screenCode: 'SCR_RETAIL', screenName: '零售总览', viewLevel: 'PROVINCE',
        bizLine: 'RETAIL', orgScopeMode: 'NAMED_GROUP', orgGroupCode: 'ORG_RETAIL',
        expectedVersion: 8, reason: '调整机构范围'
      }
    });
  });

  it('元数据的机构组空串显式清空、null 不改，并且 reason/version 缺失时 Fail Close', async () => {
    await saveScreenMetadata({
      id: 12, screenName: '零售总览', viewLevel: 'PROVINCE', bizLine: 'RETAIL',
      orgScopeMode: 'LEGACY_CONTEXT', orgGroupCode: '', expectedVersion: 9, reason: '解除机构组绑定'
    });
    expect(call).toHaveBeenLastCalledWith('put', '/screen/admin/screens/12/metadata', {
      data: {
        screenName: '零售总览', viewLevel: 'PROVINCE', bizLine: 'RETAIL',
        orgScopeMode: 'LEGACY_CONTEXT', orgGroupCode: '', expectedVersion: 9, reason: '解除机构组绑定'
      }
    });
    await saveScreenMetadata({
      id: 12, screenName: '零售总览', viewLevel: 'PROVINCE', bizLine: 'RETAIL',
      orgScopeMode: 'LEGACY_CONTEXT', orgGroupCode: null, expectedVersion: 9, reason: '其他字段调整'
    });
    expect(call).toHaveBeenLastCalledWith('put', '/screen/admin/screens/12/metadata', {
      data: {
        screenName: '零售总览', viewLevel: 'PROVINCE', bizLine: 'RETAIL',
        orgScopeMode: 'LEGACY_CONTEXT', expectedVersion: 9, reason: '其他字段调整'
      }
    });
    expect(() => saveScreenMetadata({ id: 12, expectedVersion: 9 })).toThrow(/原因/);
    expect(() => saveScreenMetadata({ id: 12, reason: '缺版本' })).toThrow(/版本/);
  });

  it('屏元数据状态只允许 ACTIVE/DISABLED，不把 DRAFT/ENABLED 历史值写回服务端', async () => {
    await saveScreenMetadata({
      id: 12, screenName: '零售总览', viewLevel: 'PROVINCE', bizLine: 'RETAIL',
      orgScopeMode: 'LEGACY_CONTEXT', status: 'DISABLED', expectedVersion: 9, reason: '临时停用'
    });
    expect(call).toHaveBeenLastCalledWith('put', '/screen/admin/screens/12/metadata', {
      data: {
        screenName: '零售总览', viewLevel: 'PROVINCE', bizLine: 'RETAIL',
        orgScopeMode: 'LEGACY_CONTEXT', status: 'DISABLED', expectedVersion: 9, reason: '临时停用'
      }
    });
    expect(() => saveScreenMetadata({
      id: 12, status: 'DRAFT', expectedVersion: 9, reason: '不能回写草稿态'
    })).toThrow(/状态/);
    expect(() => saveScreen({
      screenName: '非法状态屏', viewLevel: 'BRANCH', bizLine: 'COMMON', orgScopeMode: 'LEGACY_CONTEXT', status: 'ENABLED'
    })).toThrow(/状态/);
  });

  it('元数据 helper 在无 id 时只能走新建 POST，禁止把已有 id 带到创建端点', async () => {
    await saveScreenMetadata({
      screenName: '新屏', viewLevel: 'BRANCH', bizLine: 'COMMON', orgScopeMode: 'LEGACY_CONTEXT', blocks: []
    });
    expect(call).toHaveBeenCalledWith('post', '/screen/admin/screens', {
      data: { screenName: '新屏', viewLevel: 'BRANCH', bizLine: 'COMMON', orgScopeMode: 'LEGACY_CONTEXT' }
    });
    expect(() => saveScreen({ id: 12, screenName: '不能更新' })).toThrow(/metadata/);
  });

  it('机构组成员/角色使用覆盖式 PUT，不与普通机构更新混用', async () => {
    await createOrgGroup({ groupCode: 'G1', groupName: '一级经营机构', groupPurpose: 'REPORT_SCREEN' });
    await updateOrgGroup('G1', { groupName: '全辖一级经营机构' });
    await saveOrgGroupMembers('G1', { orgCodes: ['X1'], reason: '初始化' });
    await saveOrgGroupRoles('G1', { roleCodes: ['R_SCREEN_RETAIL_VIEWER'], reason: '初始化' });
    expect(call).toHaveBeenCalledWith('post', '/admin/org-groups', { data: { groupCode: 'G1', groupName: '一级经营机构', groupPurpose: 'REPORT_SCREEN' } });
    expect(call).toHaveBeenCalledWith('put', '/admin/org-groups/G1', { data: { groupName: '全辖一级经营机构' } });
    expect(call).toHaveBeenCalledWith('put', '/admin/org-groups/G1/members', { data: { orgCodes: ['X1'], reason: '初始化' } });
    expect(call).toHaveBeenCalledWith('put', '/admin/org-groups/G1/roles', { data: { roleCodes: ['R_SCREEN_RETAIL_VIEWER'], reason: '初始化' } });
  });

  it('机构组编码作为路径段编码，避免保留字符改变管理端点', async () => {
    await saveOrgGroupMembers('GROUP/1', { orgCodes: [], reason: '清理' });
    await saveOrgGroupRoles('GROUP/1', { roleCodes: [], reason: '清理' });
    expect(call).toHaveBeenCalledWith('put', '/admin/org-groups/GROUP%2F1/members', {
      data: { orgCodes: [], reason: '清理' }
    });
    expect(call).toHaveBeenCalledWith('put', '/admin/org-groups/GROUP%2F1/roles', {
      data: { roleCodes: [], reason: '清理' }
    });
  });

  it('屏级角色白名单 API 与严格 schema2 区块请求契约隔离', async () => {
    await listScreenAccessRoles(12);
    await saveScreenAccessRoles(12, { roleCodes: ['R1'], reason: '配置', expectedVersion: 7 });
    expect(call).toHaveBeenCalledWith('get', '/screen/admin/screens/12/access-roles', {});
    expect(call).toHaveBeenCalledWith('put', '/screen/admin/screens/12/access-roles', {
      data: { roleCodes: ['R1'], reason: '配置', expectedVersion: 7 }
    });
    await queryScreenData({ screenCode: 'SCR_RETAIL', blockId: 9, period: 'LATEST', schemaVersion: 2, dsId: 4 });
    expect(call).toHaveBeenLastCalledWith('post', '/screen/data', {
      data: { screenCode: 'SCR_RETAIL', blockId: 9, period: 'LATEST', schemaVersion: 2 }, silent: true, timeout: 60000
    }, null);
  });

  it('权限配置目录和发布归档读取不提供空数组降级，底层错误原样向上传递', async () => {
    await listScreenRoles({ enabled: true });
    expect(call).toHaveBeenCalledWith('get', '/admin/roles/all', { params: { enabled: true } });
    await listScreenPublishLogs(12);
    expect(call).toHaveBeenCalledWith('get', '/screen/admin/canvas/12/publish-logs', {});

    const forbidden = Object.assign(new Error('forbidden'), { response: { status: 403 } });
    call.mockRejectedValueOnce(forbidden);
    await expect(listScreenAccessRoles(12)).rejects.toBe(forbidden);
    call.mockRejectedValueOnce(forbidden);
    await expect(listScreenRoles()).rejects.toBe(forbidden);
    call.mockRejectedValueOnce(forbidden);
    await expect(listOrgGroups()).rejects.toBe(forbidden);
    call.mockRejectedValueOnce(forbidden);
    await expect(listScreenPublishLogs(12)).rejects.toBe(forbidden);
    call.mockRejectedValueOnce(forbidden);
    await expect(listScreens()).rejects.toBe(forbidden);
    call.mockRejectedValueOnce(forbidden);
    await expect(listScreenDatasources()).rejects.toBe(forbidden);
  });

  it('设计器地图指标为可选请求且静默处理屏级无权限，不改变后端 fail-close', async () => {
    await listScreenMapRegionMetrics(12);
    expect(call).toHaveBeenLastCalledWith(
      'get', '/screen/admin/screens/12/map-region-metrics', { silent: true }, []
    );
  });

  it('运行接口拒绝隐式 v1、未知版本和缺少 schema2 发布包身份的请求', () => {
    expect(() => queryScreenData({ dsId: 9 })).toThrow(/schemaVersion=1/);
    expect(() => queryScreenData({ schemaVersion: '1', screenCode: 'SCR_LEGACY', dsId: 9 })).toThrow(/整数/);
    expect(() => queryScreenData({ schemaVersion: '2', screenCode: 'SCR_RETAIL', blockId: 1 })).toThrow(/整数/);
    expect(() => queryScreenData({ schemaVersion: 3, dsId: 9 })).toThrow(/未知/);
    expect(() => queryScreenData({ schemaVersion: 2, screenCode: 'SCR_RETAIL' })).toThrow(/blockId/);
    expect(() => queryScreenData({ schemaVersion: 1, dsId: 9 })).toThrow(/screenCode/);
  });

  it('草稿取数只保留服务端可复核的 screenCode + blockId，不发送 dsId', async () => {
    await queryScreenData({
      schemaVersion: 1, previewState: 'draft', screenCode: 'SCR_DRAFT', blockId: 19,
      dsId: 9002, period: 'LATEST', contextParams: { orgCode: '128' }
    });
    expect(call).toHaveBeenLastCalledWith('post', '/screen/data', {
      data: {
        schemaVersion: 1, previewState: 'draft', screenCode: 'SCR_DRAFT', blockId: 19,
        period: 'LATEST', contextParams: { orgCode: '128' }
      },
      silent: true, timeout: 60000
    }, null);
  });

  it('已保存数据源列探测只走独立 probe 资源，body 含原因与测试机构组', async () => {
    await probeScreenDatasourceColumns(72, {
      period: 'LAST_1M', dateFrom: '2026-07-01', dateTo: '2026-07-31',
      contextParams: { orgCode: 'O1', empId: null }, testOrgGroupCode: 'G_REPORT', reason: '配置数值列'
    });
    expect(call).toHaveBeenLastCalledWith('post', '/screen/admin/datasources/72/probe-columns', {
      data: {
        period: 'LAST_1M', dateFrom: '2026-07-01', dateTo: '2026-07-31',
        contextParams: { orgCode: 'O1', empId: null }, testOrgGroupCode: 'G_REPORT', reason: '配置数值列'
      }
    }, null);
    expect(() => probeScreenDatasourceColumns(72, { reason: '  ' })).toThrow(/原因/);
  });

  it('放弃草稿走独立 CAS+reason 契约，调用方不能只传 screenId', async () => {
    await discardScreenCanvas(12, { expectedVersion: 7, reason: '撤回误改草稿' });
    expect(call).toHaveBeenLastCalledWith('post', '/screen/admin/canvas/discard', {
      data: { screenId: 12, expectedVersion: 7, reason: '撤回误改草稿' }
    });
    expect(() => discardScreenCanvas(12, { expectedVersion: 7 })).toThrow(/原因/);
    expect(() => discardScreenCanvas(12, { reason: '缺版本' })).toThrow(/版本/);
  });

  it('发布和回滚均为独立高危操作：只接受完整 CAS+reason body，缺失时 Fail Close', async () => {
    await publishScreenCanvas({ screenId: 12, expectedVersion: 7, reason: '月末版本发布' });
    expect(call).toHaveBeenLastCalledWith('post', '/screen/admin/canvas/publish', {
      data: { screenId: 12, expectedVersion: 7, reason: '月末版本发布' }
    });
    await rollbackScreenCanvas({ screenId: 12, publishLogId: 99, expectedVersion: 8, reason: '回退异常发布' });
    expect(call).toHaveBeenLastCalledWith('post', '/screen/admin/canvas/rollback', {
      data: { screenId: 12, publishLogId: 99, expectedVersion: 8, reason: '回退异常发布' }
    });
    expect(() => publishScreenCanvas({ screenId: 12, expectedVersion: 7 })).toThrow(/原因/);
    expect(() => rollbackScreenCanvas({ screenId: 12, publishLogId: 99, expectedVersion: 8, reason: ' ' })).toThrow(/原因/);
    expect(() => rollbackScreenCanvas({ screenId: 12, publishLogId: 99, reason: '缺版本' })).toThrow(/版本/);
  });

  it('数据源写入、试跑和删除统一要求原因，状态只允许 ACTIVE/DISABLED，删除原因在 query 中编码', async () => {
    const body = {
      dsName: '机构经营宽表', sourceKind: 'WIDE_TABLE', dsType: 'SINGLE', bizLine: 'RETAIL',
      configJson: '{}', timeParamJson: '[]', status: 'ACTIVE', reason: '新增经营指标'
    };
    await saveScreenDatasource(body);
    expect(call).toHaveBeenLastCalledWith('post', '/screen/admin/datasources', { data: body });
    await updateScreenDatasource(72, { ...body, status: 'DISABLED', reason: '临时停用' });
    expect(call).toHaveBeenLastCalledWith('put', '/screen/admin/datasources/72', {
      data: { ...body, status: 'DISABLED', reason: '临时停用' }
    });
    await tryRunScreenDatasource({ datasourceId: 72, reason: '核对试跑结果' });
    expect(call).toHaveBeenLastCalledWith('post', '/screen/admin/datasources/try-run', {
      data: { datasourceId: 72, reason: '核对试跑结果' }
    });
    await deleteScreenDatasource(72, '删除重复配置/需审计');
    expect(call).toHaveBeenLastCalledWith('delete',
      '/screen/admin/datasources/72?reason=%E5%88%A0%E9%99%A4%E9%87%8D%E5%A4%8D%E9%85%8D%E7%BD%AE%2F%E9%9C%80%E5%AE%A1%E8%AE%A1', {});

    expect(() => saveScreenDatasource({ ...body, reason: ' ' })).toThrow(/原因/);
    expect(() => updateScreenDatasource(72, { ...body, status: 'ENABLED' })).toThrow(/状态/);
    expect(() => tryRunScreenDatasource({ datasourceId: 72 })).toThrow(/原因/);
    expect(() => deleteScreenDatasource(72, ' ')).toThrow(/原因/);
  });

  it('原生 JSON 整数边界拒绝字符串 blockId/dsId，不把字符串 Number() 宽松转换', () => {
    expect(() => queryScreenData({ schemaVersion: 2, screenCode: 'SCR_RETAIL', blockId: '123' })).toThrow(/blockId/);
    expect(() => queryScreenData({ schemaVersion: 1, screenCode: 'SCR_LEGACY', dsId: '123' })).toThrow(/dsId/);
  });
});
