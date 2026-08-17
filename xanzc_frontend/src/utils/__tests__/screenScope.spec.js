import { describe, expect, it } from 'vitest';
import {
  BIZ_LINES,
  VIEW_LEVELS,
  ORG_SCOPE_MODES,
  ANCHOR_POSITIONS,
  FIXED_SATELLITE_ORG_CODES,
  XIAN_SECONDARY_BRANCHES,
  XIAN_SECONDARY_BRANCH_DISCLAIMER,
  normalizeScreenScope,
  validateScreenScope,
  isDatasourceCompatible,
  normalizeMapConfig,
  buildScreenDataRequest,
  resolveCompositeMapNodes,
  resolveXianSecondaryBranches,
  validateCompositeMapConfig,
  runtimeSchemaVersion,
  filterActiveOrgGroups,
  filterReportScreenOrgGroups,
  filterOrgProfiles,
  datasourceReferenceLabel,
  datasourceReferenceState,
  diffCodes
} from '../screenScope';

describe('screenScope 业务条线、机构范围与地图配置契约', () => {
  it('公开独立的查看视角、业务条线和机构范围枚举', () => {
    expect(VIEW_LEVELS.map(x => x.value)).toEqual(['PROVINCE', 'BRANCH', 'PERSON']);
    expect(BIZ_LINES.map(x => x.value)).toEqual(['CORP', 'RETAIL', 'COMMON']);
    expect(ORG_SCOPE_MODES.map(x => x.value)).toEqual(['LEGACY_CONTEXT', 'NAMED_GROUP']);
  });

  it('旧屏缺字段时保持兼容，新屏显式默认共用/传统范围', () => {
    expect(normalizeScreenScope({ viewLevel: 'PROVINCE' })).toMatchObject({
      viewLevel: 'PROVINCE', bizLine: 'COMMON', orgScopeMode: 'LEGACY_CONTEXT',
      orgGroupCode: '', allowedRoleCodes: []
    });
  });

  it('命名机构组缺编码、未知条线或未知视角时校验失败', () => {
    expect(validateScreenScope({ viewLevel: 'PROVINCE', bizLine: 'CORP', orgScopeMode: 'NAMED_GROUP' }))
      .toEqual(expect.arrayContaining([expect.stringContaining('机构组')]));
    expect(validateScreenScope({ viewLevel: 'WRONG', bizLine: 'OTHER', orgScopeMode: 'LEGACY_CONTEXT' }).length)
      .toBeGreaterThanOrEqual(2);
  });

  it('按绑定矩阵判断数据源业务条线，COMMON 不是空值', () => {
    expect(isDatasourceCompatible('CORP', 'CORP')).toBe(true);
    expect(isDatasourceCompatible('CORP', 'COMMON')).toBe(true);
    expect(isDatasourceCompatible('CORP', 'RETAIL')).toBe(false);
    expect(isDatasourceCompatible('COMMON', 'CORP')).toBe(false);
  });

  it('统一把 v1 陕西地图和 v2 西安复合地图归一化', () => {
    expect(normalizeMapConfig({})).toMatchObject({ schemaVersion: 1, mode: 'SHAANXI_LEGACY' });
    expect(normalizeMapConfig({ schemaVersion: 2, mode: 'XIAN_COMPOSITE' })).toMatchObject({
      schemaVersion: 2, mode: 'XIAN_COMPOSITE', baseRegion: 'XIAN_OUTLINE',
      disclaimer: '组织分布示意，非地理比例'
    });
  });

  it('地图 schema 与 mode 必须同时精确声明，缺失、冲突或宽松字符串一律拒绝', () => {
    expect(normalizeMapConfig({ mode: 'XIAN_COMPOSITE' })).toMatchObject({ mode: 'UNSUPPORTED' });
    expect(normalizeMapConfig({ schemaVersion: 2 })).toMatchObject({ schemaVersion: 2, mode: 'UNSUPPORTED' });
    expect(normalizeMapConfig({ schemaVersion: 1, mode: 'XIAN_COMPOSITE' })).toMatchObject({ schemaVersion: 1, mode: 'UNSUPPORTED' });
    expect(normalizeMapConfig({ schemaVersion: '2', mode: 'XIAN_COMPOSITE' })).toMatchObject({ schemaVersion: '2', mode: 'UNSUPPORTED' });
    expect(normalizeMapConfig({ schemaVersion: 2, mode: 'SHAANXI_LEGACY' })).toMatchObject({ schemaVersion: 2, mode: 'UNSUPPORTED' });
    expect(normalizeMapConfig({ schemaVersion: null, mode: 'XIAN_COMPOSITE' })).toMatchObject({ mode: 'UNSUPPORTED' });
    expect(normalizeMapConfig({ schemaVersion: 1 })).toMatchObject({ schemaVersion: 1, mode: 'SHAANXI_LEGACY' });
  });

  it('v2 运行时请求只提交 screenCode + blockId，不接受 dsId/orgCodes 覆盖', () => {
    expect(buildScreenDataRequest({ screenCode: 'SCR_RETAIL', blockId: 8, period: 'LATEST',
      dateFrom: null, dateTo: null, contextParams: { orgCode: 'O1' }, schemaVersion: 2,
      dsId: 999, orgCodes: ['EVIL'], orgGroupCode: 'EVIL' })).toEqual({
      schemaVersion: 2, screenCode: 'SCR_RETAIL', blockId: 8, period: 'LATEST',
      contextParams: { orgCode: 'O1' }
    });
  });

  it('v1/v2 运行时请求剔除 nullish 可选字段，但所有非空日期与上下文原样保留', () => {
    expect(buildScreenDataRequest({ schemaVersion: 1, screenCode: 'SCR_BRANCH', dsId: 9002,
      period: 'RANGE', dateFrom: '2026-08-01', dateTo: '2026-08-13',
      contextParams: { orgCode: '128', empId: 'E001' } })).toEqual({
      schemaVersion: 1, screenCode: 'SCR_BRANCH', dsId: 9002, period: 'RANGE',
      dateFrom: '2026-08-01', dateTo: '2026-08-13', contextParams: { orgCode: '128', empId: 'E001' }
    });
    expect(buildScreenDataRequest({ schemaVersion: 2, screenCode: 'SCR_RETAIL', blockId: 8,
      period: 'RANGE', dateFrom: '2026-08-01', dateTo: '2026-08-13',
      contextParams: { orgCode: '128', empId: 'E001' } })).toEqual({
      schemaVersion: 2, screenCode: 'SCR_RETAIL', blockId: 8, period: 'RANGE',
      dateFrom: '2026-08-01', dateTo: '2026-08-13', contextParams: { orgCode: '128', empId: 'E001' }
    });
    for (const value of [0, false, '']) {
      expect(buildScreenDataRequest({ schemaVersion: 1, screenCode: 'SCR_BRANCH', dsId: 9002,
        contextParams: { orgCode: value, empId: value } }).contextParams)
        .toEqual({ orgCode: value, empId: value });
      expect(buildScreenDataRequest({ schemaVersion: 2, screenCode: 'SCR_RETAIL', blockId: 8,
        contextParams: { orgCode: value, empId: value } }).contextParams)
        .toEqual({ orgCode: value, empId: value });
    }
    expect(buildScreenDataRequest({ schemaVersion: 1, screenCode: 'SCR_BRANCH', dsId: 9002,
      dateFrom: null, dateTo: undefined, contextParams: { orgCode: null, empId: undefined } }))
      .toEqual({ schemaVersion: 1, screenCode: 'SCR_BRANCH', dsId: 9002, period: 'LATEST', contextParams: {} });
  });

  it('schema2 缺少 screenCode/blockId 或未知版本时 Fail Close，不降级为 v1 请求', () => {
    expect(() => buildScreenDataRequest({ schemaVersion: 2, blockId: 8 }))
      .toThrow(/screenCode/i);
    expect(() => buildScreenDataRequest({ schemaVersion: 2, screenCode: 'SCR_RETAIL' }))
      .toThrow(/blockId/i);
    expect(() => buildScreenDataRequest({ schemaVersion: 3, screenCode: 'SCR_RETAIL', blockId: 8, dsId: 99 }))
      .toThrow(/未知.*schema/i);
    expect(runtimeSchemaVersion({ runtimeSchemaVersion: 3 })).toBe(3);
  });

  it('运行时 schemaVersion 必须是 JSON 整数 1 或 2，字符串和 v1 缺 screenCode 均拒绝', () => {
    expect(() => buildScreenDataRequest({ schemaVersion: '2', screenCode: 'SCR_RETAIL', blockId: 8 }))
      .toThrow(/整数/);
    expect(() => buildScreenDataRequest({ schemaVersion: '1', screenCode: 'SCR_LEGACY', dsId: 8 }))
      .toThrow(/整数/);
    expect(() => buildScreenDataRequest({ schemaVersion: 1, dsId: 8 }))
      .toThrow(/screenCode/i);
    expect(buildScreenDataRequest({ schemaVersion: 1, screenCode: 'SCR_LEGACY', dsId: 8 }))
      .toMatchObject({ schemaVersion: 1, screenCode: 'SCR_LEGACY', dsId: 8 });
  });

  it('响应适配保留字符串 schemaVersion 原值，让运行渲染层 Fail Close，而不是 Number() 宽松接受', () => {
    expect(runtimeSchemaVersion({ runtimeSchemaVersion: '2' })).toBe('2');
    expect(runtimeSchemaVersion({ mapPackage: { schemaVersion: '2' } })).toBe('2');
    expect(normalizeMapConfig({ schemaVersion: '2' })).toMatchObject({ schemaVersion: '2', mode: 'UNSUPPORTED' });
  });

  it('无地图的 NAMED_GROUP 屏也必须进入 schema2 取数契约', () => {
    expect(runtimeSchemaVersion({ orgScopeMode: 'NAMED_GROUP' })).toBe(2);
    expect(runtimeSchemaVersion({ screen: { org_scope_mode: 'NAMED_GROUP' } })).toBe(2);
    expect(runtimeSchemaVersion({ orgScopeMode: 'LEGACY_CONTEXT' })).toBe(1);
  });

  it('版本探测对空响应安全，PRIMARY 画像大小写不影响复合地图校验', () => {
    expect(runtimeSchemaVersion(null)).toBe(1);
    const config = {
      schemaVersion: 2, mode: 'XIAN_COMPOSITE',
      localSelector: { cityCode: '610100', operatingLevel: 'PRIMARY' },
      satelliteNodes: [
        { anchor: 'LEFT', orgCode: 'BRANCH-1' },
        { anchor: 'RIGHT', orgCode: 'BRANCH-2' },
        { anchor: 'TOP', orgCode: 'BRANCH-3' },
        { anchor: 'FAR_TOP', orgCode: 'BRANCH-4' }
      ]
    };
    const profiles = [
      { orgCode: 'LOCAL-1', orgName: '西安本部', cityCode: '610100', operatingLevel: 'primary',
        coordSys: 'GCJ02', lng: 108.95, lat: 34.26, status: 'ACTIVE' },
      ...['BRANCH-1', 'BRANCH-2', 'BRANCH-3', 'BRANCH-4'].map(orgCode => ({
        orgCode, orgName: orgCode, operatingLevel: 'PRIMARY', status: 'ACTIVE'
      }))
    ];
    expect(validateCompositeMapConfig(config, profiles, profiles.map(x => x.orgCode))).toEqual([]);
  });

  it('机构组下拉只展示 ACTIVE 组，不能因后端忽略筛选参数而选到停用组', () => {
    expect(filterActiveOrgGroups([
      { groupCode: 'G1', status: 'ACTIVE' },
      { groupCode: 'G2', status: 'DISABLED' },
      { groupCode: 'G3', status: 0 }
    ]).map(x => x.groupCode)).toEqual(['G1', 'G3']);
  });

  it('机构组状态缺失时 Fail Close，不把未知状态当作可授权候选', () => {
    expect(filterActiveOrgGroups([{ groupCode: 'UNKNOWN' }])).toEqual([]);
  });

  it('机构组页面只消费 REPORT_SCREEN 用途，避免展示其他模块的命名集合', () => {
    expect(filterReportScreenOrgGroups([
      { groupCode: 'G1', groupPurpose: 'REPORT_SCREEN' },
      { groupCode: 'G2', groupPurpose: 'OTHER' },
      { groupCode: 'G3' }
    ]).map(x => x.groupCode)).toEqual(['G1']);
  });

  it('机构画像筛选在前端按返回 DTO 字段生效，机构关键词不混入城市', () => {
    const rows = [
      { orgCode: 'X1', orgName: '一部', cityCode: '610100', cityName: '西安市', orgNature: 'DEPARTMENT', operatingLevel: 'PRIMARY' },
      { orgCode: 'X2', orgName: '二部', cityCode: '610300', cityName: '咸阳市', orgNature: 'DEPARTMENT', operatingLevel: 'NONE' },
      { orgCode: 'B1', orgName: '宝鸡分行', cityCode: '610300', cityName: '宝鸡市', orgNature: 'SECONDARY_BRANCH', operatingLevel: 'PRIMARY' }
    ];
    expect(filterOrgProfiles(rows, { keyword: '一部', orgNature: 'DEPARTMENT', operatingLevel: 'PRIMARY' }))
      .toEqual([rows[0]]);
    expect(filterOrgProfiles(rows, { orgNature: 'SECONDARY_BRANCH' })).toEqual([rows[2]]);
    expect(filterOrgProfiles(rows, { keyword: '西安市' })).toEqual([]);
    expect(filterOrgProfiles(rows, { city: '610300' })).toEqual([rows[1], rows[2]]);
  });

  it('数据源引用展示消费新 DTO 的完整 screenCode，明确区分草稿和已发布', () => {
    expect(datasourceReferenceLabel({
      draftReferenceScreenCodes: ['SCR_CORP_DRAFT'], publishedReferenceScreenCodes: ['SCR_RETAIL_LIVE']
    })).toBe('草稿：SCR_CORP_DRAFT；已发布：SCR_RETAIL_LIVE');
    expect(datasourceReferenceLabel({})).toBe('未引用');
  });

  it('数据源完整草稿/发布引用决定冻结与删除提示：发布引用冻结语义字段，任一引用禁止删除', () => {
    const state = datasourceReferenceState({
      draftReferenceScreenCodes: ['SCR_DRAFT_A', 'SCR_DRAFT_B'],
      publishedReferenceScreenCodes: ['SCR_LIVE_A', 'SCR_ARCHIVE_B']
    });
    expect(state).toMatchObject({
      draftCodes: ['SCR_DRAFT_A', 'SCR_DRAFT_B'],
      publishedCodes: ['SCR_LIVE_A', 'SCR_ARCHIVE_B'],
      semanticFrozen: true,
      deleteBlocked: true
    });
    expect(state.guidance).toContain('新建副本→改草稿绑定→重新发布');
    expect(datasourceReferenceLabel({
      draftReferenceScreenCodes: state.draftCodes,
      publishedReferenceScreenCodes: state.publishedCodes
    })).toBe('草稿：SCR_DRAFT_A、SCR_DRAFT_B；已发布：SCR_LIVE_A、SCR_ARCHIVE_B');
  });

  it('覆盖式机构组保存可明确展示新增/移除差异', () => {
    expect(diffCodes(['A', 'B'], ['B', 'C'])).toEqual({ added: ['C'], removed: ['A'] });
  });

  it('复合地图分离本地点位和四个示意锚点，过滤非 PRIMARY/非西安节点', () => {
    const result = resolveCompositeMapNodes({
      schemaVersion: 2, mode: 'XIAN_COMPOSITE',
      satelliteNodes: [
        { orgCode: '128', anchor: 'LEFT', targetScreenCode: 'SCR_BRANCH' },
        { orgCode: '191', anchor: 'RIGHT' }
      ]
    }, [
      { orgCode: 'X1', orgName: '本地', cityCode: '610100', operatingLevel: 'PRIMARY', lng: 108.9, lat: 34.2, coordSys: 'GCJ02', status: 'ACTIVE' },
      { orgCode: 'OUTLET', orgName: '社区支行', cityCode: '610100', operatingLevel: 'SUBORDINATE', lng: 108.8, lat: 34.1, status: 'ACTIVE' },
      { orgCode: '128', orgName: '宝鸡分行', operatingLevel: 'PRIMARY', status: 'ACTIVE' },
      { orgCode: '191', orgName: '渭南分行', operatingLevel: 'PRIMARY', status: 'ACTIVE' }
    ]);
    expect(result.local).toHaveLength(1);
    expect(result.local[0].orgCode).toBe('X1');
    expect(result.satellite.map(x => x.anchor)).toEqual(['LEFT', 'RIGHT']);
    expect(ANCHOR_POSITIONS.FAR_TOP).toEqual({ left: '50%', top: '8px' });
    expect(ANCHOR_POSITIONS.TOP).toEqual({ left: '50%', top: '48px' });
    expect(FIXED_SATELLITE_ORG_CODES).toEqual({ LEFT: '128', RIGHT: '191', TOP: '169', FAR_TOP: '129' });
  });

  it('运行态将四个二级分行固定为真实机构语义与 SCR_BRANCH 目标，配置不能改写身份或目标屏', () => {
    expect(XIAN_SECONDARY_BRANCH_DISCLAIMER).toBe('二级分行示意位置，非地理比例');
    expect(XIAN_SECONDARY_BRANCHES).toEqual([
      { orgCode: '128', orgName: '宝鸡分行', anchor: 'LEFT', targetScreenCode: 'SCR_BRANCH' },
      { orgCode: '191', orgName: '渭南分行', anchor: 'RIGHT', targetScreenCode: 'SCR_BRANCH' },
      { orgCode: '169', orgName: '咸阳分行', anchor: 'TOP', targetScreenCode: 'SCR_BRANCH' },
      { orgCode: '129', orgName: '榆林分行', anchor: 'FAR_TOP', targetScreenCode: 'SCR_BRANCH' }
    ]);
    expect(resolveXianSecondaryBranches([
      { orgCode: '128', orgName: '伪造名称', anchor: 'RIGHT', targetScreenCode: 'OTHER' },
      { orgCode: '191', anchor: 'LEFT' },
      { orgCode: 'NOT_AUTHORIZED', orgName: '越权节点', anchor: 'TOP' }
    ])).toEqual([
      { orgCode: '128', orgName: '宝鸡分行', anchor: 'LEFT', targetScreenCode: 'SCR_BRANCH', position: { left: '8px', top: '50%' } },
      { orgCode: '191', orgName: '渭南分行', anchor: 'RIGHT', targetScreenCode: 'SCR_BRANCH', position: { right: '8px', top: '50%' } }
    ]);
  });

  it('示意锚点也只允许 PRIMARY 机构，坏配置不能把下属网点渲染成经营节点', () => {
    const result = resolveCompositeMapNodes({
      schemaVersion: 2, mode: 'XIAN_COMPOSITE',
      satelliteNodes: [{ orgCode: 'OUTLET', anchor: 'LEFT' }]
    }, [{ orgCode: 'OUTLET', orgName: '社区支行', operatingLevel: 'SUBORDINATE', status: 'ACTIVE' }]);

    expect(result.satellite).toEqual([]);
  });

  it('复合地图发布前必须补齐四锚点，且不能把缺画像节点当作有效示意节点', () => {
    const config = {
      schemaVersion: 2, mode: 'XIAN_COMPOSITE',
      satelliteNodes: [{ orgCode: '128', anchor: 'LEFT' }]
    };
    const profiles = [{ orgCode: 'X1', orgName: '本地', cityCode: '610100', operatingLevel: 'PRIMARY',
      lng: 108.9, lat: 34.2, coordSys: 'GCJ02', status: 'ACTIVE' }];
    const errors = validateCompositeMapConfig(config, profiles, ['X1', '128']);
    expect(errors).toEqual(expect.arrayContaining([
      expect.stringContaining('示意节点缺失'),
      expect.stringContaining('128')
    ]));
    expect(resolveCompositeMapNodes(config, profiles).satellite).toHaveLength(0);
  });

  it('示意节点机构必须是有效 PRIMARY，不能把下属网点挂到异地锚点', () => {
    const nodes = [
      { orgCode: '128', anchor: 'LEFT' }, { orgCode: '191', anchor: 'RIGHT' },
      { orgCode: '169', anchor: 'TOP' }, { orgCode: '129', anchor: 'FAR_TOP' }
    ];
    const profiles = ['128', '191', '169', '129'].map((orgCode, index) => ({
      orgCode, orgName: orgCode, operatingLevel: index === 0 ? 'SUBORDINATE' : 'PRIMARY', status: 'ACTIVE'
    }));
    expect(validateCompositeMapConfig({ schemaVersion: 2, mode: 'XIAN_COMPOSITE', satelliteNodes: nodes }, profiles, profiles.map(x => x.orgCode)))
      .toEqual(expect.arrayContaining([expect.stringContaining('128')]));
  });
});
