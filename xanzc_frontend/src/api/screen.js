// 经营管理大屏 API —— 对接 yiti report-analytics-center screen 子域
//
// 后端控制器（report-analytics-center/controller/）：
//   - ScreenDatasourceAdminController  /api/screen/admin/datasources[...]
//   - ScreenConfigAdminController      /api/screen/admin/screens[...] + /map-points
//   - ScreenViewController             GET /api/screen/view/{screenCode}
//   - ScreenDataController             POST /api/screen/data
//
// 大屏展示类接口一律不给 mock 兜底（宁可空屏不给假数据，同 Dashboard.vue 先例）。
import { call } from './http';

// ===== 数据源管理 =====
export function listScreenDatasources(params = {}) {
  // 后端 DTO 只声明 dsType/keyword；bizLine 由前端按返回 DTO 过滤，不能发送未声明查询字段。
  const query = {};
  if (params.dsType) query.dsType = params.dsType;
  if (params.keyword) query.keyword = params.keyword;
  return call('get', '/screen/admin/datasources', { params: query }, []);
}

const SCREEN_DATASOURCE_STATUSES = new Set(['ACTIVE', 'DISABLED']);

/** 数据源写操作统一在 API 边界保留原因并拒绝非法状态，避免任一页面绕开审计契约。 */
function normalizeDatasourceWrite(data = {}) {
  const reason = String(data.reason || '').trim();
  if (!reason) throw new Error('数据源变更必须填写原因');
  const status = data.status;
  if (status !== undefined && status !== null && status !== '' && !SCREEN_DATASOURCE_STATUSES.has(status)) {
    throw new Error('数据源状态只能是 ACTIVE 或 DISABLED');
  }
  return { ...data, reason };
}

export function saveScreenDatasource(data) {
  return call('post', '/screen/admin/datasources', { data: normalizeDatasourceWrite(data) });
}
export function updateScreenDatasource(id, data) {
  return call('put', `/screen/admin/datasources/${id}`, { data: normalizeDatasourceWrite(data) });
}
export function deleteScreenDatasource(id, reason) {
  const normalizedReason = String(reason || '').trim();
  if (!normalizedReason) throw new Error('删除数据源必须填写原因');
  return call('delete', `/screen/admin/datasources/${id}?reason=${encodeURIComponent(normalizedReason)}`, {});
}
export function tryRunScreenDatasource(data) {
  return call('post', '/screen/admin/datasources/try-run', { data: normalizeDatasourceWrite(data) });
}

/** 已保存数据源的列探测是独立高危资源，不能经运行时 /screen/data 兼容链路替代。 */
export function probeScreenDatasourceColumns(id, data = {}) {
  const datasourceId = Number(id);
  if (!Number.isSafeInteger(datasourceId) || datasourceId <= 0) {
    throw new Error('列探测必须指定有效数据源 ID');
  }
  const reason = String(data.reason || '').trim();
  if (!reason) throw new Error('列探测必须填写原因');
  const contextParams = data.contextParams && typeof data.contextParams === 'object' ? data.contextParams : {};
  const body = {
    period: data.period || 'LATEST',
    dateFrom: data.dateFrom ?? null,
    dateTo: data.dateTo ?? null,
    contextParams,
    reason
  };
  const testOrgGroupCode = String(data.testOrgGroupCode || '').trim();
  if (testOrgGroupCode) body.testOrgGroupCode = testOrgGroupCode;
  return call('post', `/screen/admin/datasources/${datasourceId}/probe-columns`, { data: body }, null);
}

// KPI 方案下拉（KPI_DETAIL 数据源配置用，仅 ACTIVE 方案）：[{schemeCode, schemeName}]
export function listKpiSchemes() {
  return call('get', '/screen/admin/kpi-schemes', {}, []);
}

// ===== 大屏布局管理 =====
export function listScreens() {
  return call('get', '/screen/admin/screens', {}, []);
}
export function getScreen(id) {
  return call('get', `/screen/admin/screens/${id}`, {}, null);
}

const SCREEN_STATUSES = new Set(['ACTIVE', 'DISABLED']);

/** 屏元数据状态不再兼容 DRAFT/ENABLED；未提供时保持服务端既有值/创建默认。 */
function assertScreenStatus(data = {}) {
  if (!Object.prototype.hasOwnProperty.call(data, 'status')) return;
  if (!SCREEN_STATUSES.has(data.status)) throw new Error('大屏状态只能是 ACTIVE 或 DISABLED');
}

/**
 * 屏级角色属于独立的 PERMISSION_CHANGE 契约。通用屏保存不得顺带覆盖角色白名单，
 * 否则一次普通元数据编辑会绕过高危原因与 CAS 校验。
 */
function withoutScreenRoleFields(data = {}) {
  const {
    allowedRoleCodes, accessRoleCodes, accessRoles, screenAccessRoleCodes,
    allowed_role_codes, access_role_codes, ...payload
  } = data;
  return payload;
}

export function saveScreen(data) {
  // 新建必须由表单显式提交 bizLine/orgScopeMode；API 层绝不以 COMMON/LEGACY_CONTEXT 静默补值。
  // 后端 POST 已收敛为纯创建端点，带 id 的调用必须改走独立 metadata PUT，不能静默错写。
  const { id, screenId, blocks, ...payload } = withoutScreenRoleFields(data);
  if (id !== undefined && id !== null && id !== '') {
    throw new Error('已有大屏的元数据更新必须使用 metadata 端点');
  }
  assertScreenStatus(payload);
  return call('post', '/screen/admin/screens', { data: payload });
}

/**
 * 元数据/范围专用保存：无 id 走纯创建 POST；有 id 强制走独立 PUT /metadata。
 * 两个分支均明确剔除 blocks/角色，确保普通编辑不会重建画布区块或绕过 PERMISSION_CHANGE。
 */
export function saveScreenMetadata(data = {}) {
  const { id, screenId, blocks, ...metadata } = withoutScreenRoleFields(data);
  assertScreenStatus(metadata);
  if (id === undefined || id === null || id === '') {
    return call('post', '/screen/admin/screens', { data: metadata });
  }
  const expectedVersion = metadata.expectedVersion;
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0) {
    throw new Error('元数据保存必须携带有效版本');
  }
  const reason = String(metadata.reason || '').trim();
  if (!reason) throw new Error('元数据保存必须填写原因');
  // null 的语义是“不改”；空串才是服务端识别的显式清空机构组。
  const { orgGroupCode, ...body } = metadata;
  if (orgGroupCode !== null && orgGroupCode !== undefined) body.orgGroupCode = orgGroupCode;
  body.expectedVersion = expectedVersion;
  body.reason = reason;
  return call('put', `/screen/admin/screens/${encodeURIComponent(id)}/metadata`, { data: body });
}
export function deleteScreen(id) {
  return call('delete', `/screen/admin/screens/${id}`, {});
}
export function listMapPoints() {
  return call('get', '/screen/admin/map-points', {}, []);
}
export function saveMapPoints(points) {
  return call('put', '/screen/admin/map-points', { data: points });
}
/** 设计器地图指标只读快照；服务端按当前屏权限和机构范围返回，不在前端补模拟值。 */
export function listScreenMapRegionMetrics(screenId) {
  // 设计器只把指标作为可选地图增强；屏级无权限由后端 fail-close，前端不弹整屏 toast。
  return call('get', `/screen/admin/screens/${encodeURIComponent(screenId)}/map-region-metrics`, { silent: true }, []);
}

// 屏级查看角色白名单：与 auth 机构组角色绑定分别校验，不能在前端拼接权限。
export function listScreenAccessRoles(screenId) {
  return call('get', `/screen/admin/screens/${screenId}/access-roles`, {}, []);
}
export function saveScreenAccessRoles(screenId, data) {
  const expectedVersion = data?.expectedVersion;
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0) {
    throw new Error('查看角色保存必须携带有效版本');
  }
  const reason = String(data?.reason || '').trim();
  if (!reason) throw new Error('查看角色保存必须填写原因');
  if (!Array.isArray(data?.roleCodes)) throw new Error('查看角色必须为 roleCodes 数组');
  return call('put', `/screen/admin/screens/${screenId}/access-roles`, {
    data: { roleCodes: [...data.roleCodes], expectedVersion, reason }
  });
}

// 屏级角色候选列表沿用 auth 角色目录；列表只用于配置体验，后端保存时重新校验。
export function listScreenRoles(params = {}) {
  return call('get', '/admin/roles/all', { params }, []);
}

// ===== 机构画像与命名机构组（auth-permission-center 管理端契约） =====
export function listOrgProfiles(params = {}) {
  // keyword 只匹配机构编码/名称；city 是后端独立画像城市筛选，性质/经营等级留在前端 DTO 筛选。
  const query = {};
  if (params.keyword) query.keyword = params.keyword;
  if (params.city) query.city = params.city;
  return call('get', '/admin/org-profiles', { params: query }, []);
}
export function updateOrgProfile(orgCode, data) {
  return call('put', `/admin/org-profiles/${encodeURIComponent(orgCode)}`, { data });
}
export function listOrgGroups(params = {}) {
  // auth 控制器无查询参数，状态/用途筛选由管理端页面按 DTO 做本地过滤。
  return call('get', '/admin/org-groups', { params: {} }, []);
}
export function createOrgGroup(data) {
  return call('post', '/admin/org-groups', { data });
}
export function updateOrgGroup(groupCode, data) {
  return call('put', `/admin/org-groups/${encodeURIComponent(groupCode)}`, { data });
}
export function saveOrgGroupMembers(groupCode, data) {
  return call('put', `/admin/org-groups/${encodeURIComponent(groupCode)}/members`, { data });
}
export function saveOrgGroupRoles(groupCode, data) {
  return call('put', `/admin/org-groups/${encodeURIComponent(groupCode)}/roles`, { data });
}

// ===== 大屏运行时 =====
// preview='draft' 读草稿包(需登录 + REPORT/READ 权限,详见 ScreenViewController);不传读发布态。
// silent:false(默认)——整屏加载失败要提示;区块级取数走 queryScreenData 单独 silent。
export function getScreenView(screenCode, preview) {
  const params = preview ? { preview } : {};
  return call('get', `/screen/view/${screenCode}`, { params }, null);
}

/**
 * /screen/data 的版本边界。后端已取消“根据 dsId/屏编码猜版本”的降级：
 * v1 只能由显式 schemaVersion=1 + dsId 进入，v2 只接受发布包身份。
 * 这里再做一次前端 Fail Close，避免新调用方绕开 BlockContainer 重新引入隐式协议。
 */
function normalizeScreenDataBody(body = {}) {
  if (body.schemaVersion === undefined || body.schemaVersion === null || body.schemaVersion === '') {
    throw new Error('数据请求必须显式携带 schemaVersion=1 或 schemaVersion=2');
  }
  const schemaVersion = body.schemaVersion;
  if (typeof schemaVersion !== 'number' || !Number.isInteger(schemaVersion)) {
    throw new Error('schemaVersion 必须为 JSON 整数 1 或 2');
  }
  if (schemaVersion === 1) {
    const screenCode = String(body.screenCode || '').trim();
    if (!screenCode) {
      throw new Error('schemaVersion=1 的历史数据请求必须显式携带 screenCode');
    }
    if (!Number.isSafeInteger(body.dsId) || body.dsId <= 0) {
      throw new Error('schemaVersion=1 的数据请求必须显式携带有效 dsId');
    }
    const { blockId, orgCodes, orgGroupCode, ...payload } = body;
    return { ...payload, schemaVersion: 1, screenCode, dsId: body.dsId };
  }
  if (schemaVersion === 2) {
    const screenCode = String(body.screenCode || '').trim();
    const blockId = body.blockId;
    if (!screenCode || !Number.isSafeInteger(blockId) || blockId <= 0) {
      throw new Error('schemaVersion=2 的数据请求必须显式携带 screenCode 和 blockId');
    }
    // schema2 服务端只从已发布 bindSnapshots 解析数据源，客户端 dsId/范围覆盖一律不应上送。
    const { dsId, orgCodes, orgGroupCode, ...payload } = body;
    return { ...payload, schemaVersion: 2, screenCode, blockId };
  }
  throw new Error('未知 screen data schemaVersion，已拒绝请求');
}
export function queryScreenData(body) {
  // silent:true —— 大屏区块取数失败不弹全局 toast，由 BlockContainer 按业务码内联展示（引导态/错误态），
  // 避免一屏多区块并行失败时 toast 轰炸（配合 http.js 响应拦截器的 silent 分支）
  return call('post', '/screen/data', { data: normalizeScreenDataBody(body), silent: true }, null);
}

// ===== 画布设计器 V2(双态) =====
export function getScreenCanvas(id) {
  return call('get', `/screen/admin/canvas/${id}`, {}, null);
}
export function saveScreenCanvas(data) {
  return call('post', '/screen/admin/canvas/save', { data });
}

function normalizeCanvasHighRiskPayload(data = {}, action, { rollback = false } = {}) {
  const screenId = data.screenId;
  if (!Number.isSafeInteger(screenId) || screenId <= 0) {
    throw new Error(`${action}必须指定有效屏 ID`);
  }
  const expectedVersion = data.expectedVersion;
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0) {
    throw new Error(`${action}必须携带有效版本`);
  }
  const reason = String(data.reason || '').trim();
  if (!reason) throw new Error(`${action}必须填写原因`);
  if (!rollback) return { screenId, expectedVersion, reason };
  const publishLogId = data.publishLogId;
  if (!Number.isSafeInteger(publishLogId) || publishLogId <= 0) {
    throw new Error('回滚必须指定有效发布归档 ID');
  }
  return { screenId, publishLogId, expectedVersion, reason };
}

export function publishScreenCanvas(data) {
  return call('post', '/screen/admin/canvas/publish', {
    data: normalizeCanvasHighRiskPayload(data, '发布')
  });
}
export function rollbackScreenCanvas(data) {
  return call('post', '/screen/admin/canvas/rollback', {
    data: normalizeCanvasHighRiskPayload(data, '回滚', { rollback: true })
  });
}
export function discardScreenCanvas(screenId, data = {}) {
  const id = Number(screenId);
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error('放弃草稿必须指定有效屏 ID');
  const expectedVersion = data.expectedVersion;
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0) {
    throw new Error('放弃草稿必须携带有效版本');
  }
  const reason = String(data.reason || '').trim();
  if (!reason) throw new Error('放弃草稿必须填写原因');
  return call('post', '/screen/admin/canvas/discard', { data: { screenId: id, expectedVersion, reason } });
}
export function listScreenPublishLogs(id) {
  return call('get', `/screen/admin/canvas/${id}/publish-logs`, {}, []);
}
