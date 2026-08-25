/**
 * 大屏范围/地图契约的唯一前端适配点。
 *
 * 这里故意不根据屏名称、机构名称或角色名称推断业务条线；这些字段必须由
 * 管理端显式保存。运行时收到旧发布包时只在本模块做一次 v1 兼容归一化。
 */

export const VIEW_LEVELS = [
  { value: 'PROVINCE', label: '全辖' },
  { value: 'BRANCH', label: '机构' },
  { value: 'PERSON', label: '个人' }
];

export const BIZ_LINES = [
  { value: 'CORP', label: '公司/对公' },
  { value: 'RETAIL', label: '零售' },
  { value: 'COMMON', label: '共用' }
];

export const ORG_SCOPE_MODES = [
  { value: 'LEGACY_CONTEXT', label: '传统上下文' },
  { value: 'NAMED_GROUP', label: '命名机构组' }
];

export const MAP_MODES = [
  { value: 'SHAANXI_LEGACY', label: '陕西兼容地图' },
  { value: 'XIAN_COMPOSITE', label: '西安复合经营地图' }
];

/**
 * 地图设计器可选地域。code 使用国家统计用行政区划代码，districtCount 只用于配置提示，
 * 真正的区县名称仍以随包 GeoJSON 为准，避免把展示文案当作边界数据源。
 */
export const SHAANXI_MAP_REGIONS = Object.freeze([
  Object.freeze({ code: '610000', label: '陕西省（全省概览）', shortName: '陕西省', districtCount: 10, level: 'PROVINCE' }),
  Object.freeze({ code: '610100', label: '西安市（经营六区）', shortName: '西安市', districtCount: 6, level: 'CITY', composite: true }),
  Object.freeze({ code: '610200', label: '铜川市', shortName: '铜川市', districtCount: 4, level: 'CITY' }),
  Object.freeze({ code: '610300', label: '宝鸡市', shortName: '宝鸡市', districtCount: 12, level: 'CITY' }),
  Object.freeze({ code: '610400', label: '咸阳市', shortName: '咸阳市', districtCount: 14, level: 'CITY' }),
  Object.freeze({ code: '610500', label: '渭南市', shortName: '渭南市', districtCount: 11, level: 'CITY' }),
  Object.freeze({ code: '610600', label: '延安市', shortName: '延安市', districtCount: 13, level: 'CITY' }),
  Object.freeze({ code: '610700', label: '汉中市', shortName: '汉中市', districtCount: 11, level: 'CITY' }),
  Object.freeze({ code: '610800', label: '榆林市', shortName: '榆林市', districtCount: 12, level: 'CITY' }),
  Object.freeze({ code: '610900', label: '安康市', shortName: '安康市', districtCount: 10, level: 'CITY' }),
  Object.freeze({ code: '611000', label: '商洛市', shortName: '商洛市', districtCount: 7, level: 'CITY' })
]);

const LEGACY_MAP_REGION_CODES = new Set(
  SHAANXI_MAP_REGIONS.filter(region => !region.composite).map(region => region.code)
);

export const MAP_ANCHORS = ['LEFT', 'RIGHT', 'TOP', 'FAR_TOP'];

/** 当前业务确认的异地经营节点映射；配置编辑器只允许调整目标屏，不允许改写机构身份。 */
export const FIXED_SATELLITE_ORG_CODES = Object.freeze({
  LEFT: '128',
  RIGHT: '191',
  TOP: '169',
  FAR_TOP: '129'
});

/**
 * 西安运行态的二级分行只使用这四个真实机构语义。
 *
 * 本期明确为前端硬编码：发布包中的 satelliteNodes 只能作为已授权机构的出现
 * 许可，不能篡改机构名称、锚点或目标屏，也不能注入新的机构编码。这样继续消费
 * 后端已裁剪的授权渲染包，同时不把展示配置误作权限依据。
 */
export const XIAN_SECONDARY_BRANCHES = Object.freeze([
  Object.freeze({ orgCode: '128', orgName: '宝鸡分行', anchor: 'LEFT', targetScreenCode: 'SCR_BRANCH' }),
  Object.freeze({ orgCode: '191', orgName: '渭南分行', anchor: 'RIGHT', targetScreenCode: 'SCR_BRANCH' }),
  Object.freeze({ orgCode: '169', orgName: '咸阳分行', anchor: 'TOP', targetScreenCode: 'SCR_BRANCH' }),
  Object.freeze({ orgCode: '129', orgName: '榆林分行', anchor: 'FAR_TOP', targetScreenCode: 'SCR_BRANCH' })
]);

export const XIAN_SECONDARY_BRANCH_DISCLAIMER = '二级分行示意位置，非地理比例';

// 百分比坐标只用于示意节点布局，不是经纬度，也不编码指标数值。
export const ANCHOR_POSITIONS = Object.freeze({
  LEFT: Object.freeze({ left: '8px', top: '50%' }),
  RIGHT: Object.freeze({ right: '8px', top: '50%' }),
  // 以节点左上角定位，避免 TOP/FAR_TOP 的 translate(-50%, -50%) 把可点击区域推出容器。
  FAR_TOP: Object.freeze({ left: '50%', top: '8px' }),
  TOP: Object.freeze({ left: '50%', top: '48px' })
});

export const COMPOSITE_DISCLAIMER = '组织分布示意，非地理比例';

function pick(value, ...keys) {
  for (const key of keys) {
    if (value?.[key] !== undefined && value?.[key] !== null) return value[key];
  }
  return undefined;
}

function hasOwn(value, key) {
  return Object.prototype.hasOwnProperty.call(value, key);
}

function active(value) {
  return value === undefined || value === null || value === '' || value === 'ACTIVE' || value === 0 || value === '0' || value === true;
}

/** 统一屏配置字段名，并保留存量 PROVINCE/BRANCH/PERSON。 */
export function normalizeScreenScope(input = {}) {
  const viewLevel = pick(input, 'viewLevel', 'view_level') || 'BRANCH';
  const bizLine = pick(input, 'bizLine', 'biz_line', 'BIZ_LINE') || 'COMMON';
  const orgScopeMode = pick(input, 'orgScopeMode', 'org_scope_mode', 'ORG_SCOPE_MODE') || 'LEGACY_CONTEXT';
  const group = pick(input, 'orgGroupCode', 'org_group_code', 'ORG_GROUP_CODE') || '';
  const roles = pick(input, 'allowedRoleCodes', 'accessRoleCodes', 'screenAccessRoleCodes', 'allowed_role_codes') || [];
  return {
    ...input,
    viewLevel,
    bizLine,
    orgScopeMode,
    orgGroupCode: group,
    allowedRoleCodes: Array.isArray(roles) ? roles.filter(Boolean) : [],
    // 对后端返回 snake_case 的对象也提供 camelCase，避免模板散落兼容判断。
    accessRoleCodes: Array.isArray(roles) ? roles.filter(Boolean) : []
  };
}

/**
 * 计算运行时区块取数契约版本。
 *
 * canvas 的 schemaVersion 只描述画布结构，不能用来决定 /screen/data 的请求形态。
 * 命名机构组即使没有地图组件，也必须走 screenCode + blockId 的服务端解析路径；
 * 旧 LEGACY_CONTEXT 屏才继续携带 dsId。
 */
export function runtimeSchemaVersion(input = {}) {
  const screen = input?.screen || input?.screenMeta || {};
  const renderPackage = input?.renderPackage || input?.render_package || {};
  const explicitRaw = [
    pick(input, 'runtimeSchemaVersion', 'runtime_schema_version', 'screenSchemaVersion', 'screen_schema_version'),
    pick(screen, 'runtimeSchemaVersion', 'runtime_schema_version', 'screenSchemaVersion', 'screen_schema_version')
  ].find(value => value !== undefined && value !== null && value !== '');
  // 显式未知版本必须原样传到请求适配点并被拒绝，不能以 Number() 把字符串 "2" 宽松归类成 v2。
  if (explicitRaw !== undefined) return explicitRaw;

  const scopeMode = pick(input, 'orgScopeMode', 'org_scope_mode', 'ORG_SCOPE_MODE')
    ?? pick(screen, 'orgScopeMode', 'org_scope_mode', 'ORG_SCOPE_MODE');
  if (String(scopeMode || '').toUpperCase() === 'NAMED_GROUP') return 2;

  const mapPayload = input?.mapPackage || input?.map_payload || input?.mapPayload;
  const mapVersionRaw = pick(mapPayload || {}, 'schemaVersion', 'schema_version');
  if (mapVersionRaw !== undefined && mapVersionRaw !== null && mapVersionRaw !== '') return mapVersionRaw;
  if (String(mapPayload?.mode || '').toUpperCase() === 'XIAN_COMPOSITE') return 2;

  const packageVersionRaw = pick(renderPackage, 'runtimeSchemaVersion', 'runtime_schema_version');
  if (packageVersionRaw !== undefined && packageVersionRaw !== null && packageVersionRaw !== '') return packageVersionRaw;
  return 1;
}

/** 管理端列表接口当前按关键字筛选，前端仍需对状态做一次 Fail Close 过滤。 */
export function filterActiveOrgGroups(groups = []) {
  return (Array.isArray(groups) ? groups : []).filter(group => {
    const status = pick(group, 'status', 'recordStatus');
    return status === 'ACTIVE' || status === 0 || status === '0' || status === true;
  });
}

/** 本期命名机构组契约只服务大屏，其他用途集合不得进入配置下拉或编辑页。 */
export function filterReportScreenOrgGroups(groups = []) {
  return (Array.isArray(groups) ? groups : []).filter(group =>
    String(pick(group, 'groupPurpose', 'group_purpose') || '').toUpperCase() === 'REPORT_SCREEN');
}

/** 机构关键词和城市字段独立筛选；性质/经营等级在 DTO 返回后本地完成。 */
export function filterOrgProfiles(profiles = [], filters = {}) {
  const keyword = String(filters.keyword || '').trim().toLowerCase();
  const nature = String(filters.orgNature || '').trim();
  const level = String(filters.operatingLevel || '').trim();
  const city = String(filters.city || '').trim().toLowerCase();
  return (Array.isArray(profiles) ? profiles : []).filter(profile => {
    const keywordText = [
      pick(profile, 'orgCode', 'org_code'),
      pick(profile, 'orgName', 'org_name')
    ].filter(Boolean).join(' ').toLowerCase();
    const cityText = [
      pick(profile, 'cityCode', 'city_code'),
      pick(profile, 'cityName', 'city_name')
    ].filter(Boolean).join(' ').toLowerCase();
    return (!keyword || keywordText.includes(keyword))
      && (!nature || pick(profile, 'orgNature', 'org_nature') === nature)
      && (!level || pick(profile, 'operatingLevel', 'operating_level') === level)
      && (!city || cityText.includes(city));
  });
}

/** 覆盖保存前用于展示成员/角色快照差异；结果稳定按目标快照顺序返回。 */
export function diffCodes(before = [], after = []) {
  const oldSet = new Set((Array.isArray(before) ? before : []).map(String));
  const next = (Array.isArray(after) ? after : []).map(String);
  const nextSet = new Set(next);
  return {
    added: next.filter(code => !oldSet.has(code)),
    removed: [...oldSet].filter(code => !nextSet.has(code))
  };
}

/** 数据源 DTO 的草稿/发布引用列表使用完整 screenCode；旧字段仅作只读展示兼容。 */
export function datasourceReferenceLabel(row = {}) {
  const codes = value => (Array.isArray(value) ? value : []).filter(Boolean).map(String);
  const draft = codes(row.draftReferenceScreenCodes);
  const published = codes(row.publishedReferenceScreenCodes);
  const parts = [];
  if (draft.length) parts.push(`草稿：${draft.join('、')}`);
  if (published.length) parts.push(`已发布：${published.join('、')}`);
  if (parts.length) return parts.join('；');
  const legacy = row.references || row.screenReferences || row.screenNames || [];
  if (Array.isArray(legacy)) {
    return legacy.map(item => typeof item === 'string' ? item : (item.screenCode || item.screenName || item.name))
      .filter(Boolean).join('、') || '未引用';
  }
  return legacy || '未引用';
}

/**
 * 数据源引用的管理端状态。
 *
 * 发布清单同时覆盖当前发布包与发布归档：存在时查询语义字段不能原地改动；
 * 草稿、发布或归档任一引用存在时均不得删除。这里仅驱动前端提示/禁用，服务端仍为最终安全边界。
 */
export function datasourceReferenceState(row = {}) {
  const codes = value => (Array.isArray(value) ? value : []).filter(Boolean).map(String);
  const draftCodes = codes(row.draftReferenceScreenCodes);
  const publishedCodes = codes(row.publishedReferenceScreenCodes);
  const semanticFrozen = publishedCodes.length > 0;
  const deleteBlocked = draftCodes.length > 0 || semanticFrozen;
  return {
    draftCodes,
    publishedCodes,
    semanticFrozen,
    deleteBlocked,
    guidance: semanticFrozen
      ? '该数据源存在发布/归档引用，查询语义字段已冻结；请新建副本→改草稿绑定→重新发布。'
      : (deleteBlocked ? '该数据源仍被草稿引用，不能删除；请先解除草稿绑定。' : '')
  };
}

/** 返回所有错误文本；保存/发布前都可复用，不承担权限安全边界。 */
export function validateScreenScope(input = {}) {
  const value = normalizeScreenScope(input);
  const errors = [];
  if (!VIEW_LEVELS.some(x => x.value === value.viewLevel)) errors.push('查看视角无效');
  if (!BIZ_LINES.some(x => x.value === value.bizLine)) errors.push('业务条线无效');
  if (!ORG_SCOPE_MODES.some(x => x.value === value.orgScopeMode)) errors.push('机构范围模式无效');
  if (value.orgScopeMode === 'NAMED_GROUP' && !String(value.orgGroupCode || '').trim()) errors.push('命名机构组必填');
  if (!Array.isArray(value.allowedRoleCodes)) errors.push('允许查看角色必须为数组');
  return errors;
}

/** 屏业务条线 -> 数据源业务条线绑定矩阵。COMMON 代表共用，不是未选择。 */
export function isDatasourceCompatible(screenBizLine, datasourceBizLine) {
  const screen = String(screenBizLine || '').toUpperCase();
  const data = String(datasourceBizLine || '').toUpperCase();
  if (!BIZ_LINES.some(x => x.value === screen) || !BIZ_LINES.some(x => x.value === data)) return false;
  if (screen === 'COMMON') return data === 'COMMON';
  return data === screen || data === 'COMMON';
}

export function bizLineLabel(value) {
  return BIZ_LINES.find(x => x.value === value)?.label || value || '共用';
}

export function viewLevelLabel(value) {
  return VIEW_LEVELS.find(x => x.value === value)?.label || value || '机构';
}

export function orgScopeModeLabel(value) {
  return ORG_SCOPE_MODES.find(x => x.value === value)?.label || value || '传统上下文';
}

/**
 * 地图组件配置集中适配：没有 schemaVersion/旧 propValue 的节点保持陕西 v1。
 * disclaimer 对 v2 固定展示，调用方不应根据空文案隐藏它。
 */
export function normalizeMapConfig(raw = {}) {
  const source = typeof raw === 'string' ? safeJson(raw, null) : raw;
  if (!source || typeof source !== 'object' || Array.isArray(source)) {
    return { schemaVersion: null, mode: 'UNSUPPORTED' };
  }
  const hasSchemaVersion = hasOwn(source, 'schemaVersion');
  const hasMode = hasOwn(source, 'mode');
  // 只有两个字段都完全缺失的历史组件才按 v1 兼容；出现任一个字段就不再猜测另一个。
  if (!hasSchemaVersion && !hasMode) {
    return {
      ...source,
      schemaVersion: 1,
      mode: 'SHAANXI_LEGACY',
      baseRegion: 'SHAANXI',
      regionCode: '610000'
    };
  }
  // schema=1 只接受明确的陕西模式或历史省略 mode；v1 + XIAN 不能借 mode 提升为六区。
  if (source.schemaVersion === 1 && (!hasMode || source.mode === 'SHAANXI_LEGACY')) {
    const regionCode = LEGACY_MAP_REGION_CODES.has(String(source.regionCode || ''))
      ? String(source.regionCode)
      : '610000';
    return {
      ...source,
      schemaVersion: 1,
      mode: 'SHAANXI_LEGACY',
      baseRegion: regionCode === '610000' ? 'SHAANXI' : 'CITY_DISTRICT',
      regionCode
    };
  }
  // 地图包属于运行时不可信输入：必须是原生整数 2 且 mode 精确为 XIAN_COMPOSITE。
  // 字符串 "2"、只给 mode、只给 schema 或其他冲突组合全部 Fail Close。
  if (source.schemaVersion !== 2 || source.mode !== 'XIAN_COMPOSITE') {
    return { ...source, mode: 'UNSUPPORTED' };
  }
  const nodes = Array.isArray(source.satelliteNodes) ? source.satelliteNodes.map(x => ({ ...x })) : [];
  return {
    ...source,
    schemaVersion: 2,
    mode: 'XIAN_COMPOSITE',
    baseRegion: source.baseRegion || 'XIAN_OUTLINE',
    regionCode: '610100',
    localSelector: { cityCode: '610100', operatingLevel: 'PRIMARY', ...(source.localSelector || {}) },
    satelliteNodes: nodes,
    disclaimer: String(source.disclaimer || COMPOSITE_DISCLAIMER).trim() || COMPOSITE_DISCLAIMER
  };
}

function safeJson(value, fallback) {
  try { return JSON.parse(value); } catch { return fallback; }
}

function normalizeProfile(profile = {}) {
  return {
    ...profile,
    orgCode: String(pick(profile, 'orgCode', 'org_code') || ''),
    orgName: pick(profile, 'orgName', 'org_name') || pick(profile, 'orgCode', 'org_code') || '',
    cityCode: String(pick(profile, 'cityCode', 'city_code') || ''),
    operatingLevel: String(pick(profile, 'operatingLevel', 'operating_level') || ''),
    lng: Number(pick(profile, 'lng', 'longitude')),
    lat: Number(pick(profile, 'lat', 'latitude')),
    status: pick(profile, 'status', 'recordStatus')
  };
}

function isValidCoordinate(profile) {
  return Number.isFinite(profile.lng) && Number.isFinite(profile.lat)
    && profile.lng >= -180 && profile.lng <= 180 && profile.lat >= -90 && profile.lat <= 90;
}

/**
 * 把机构画像拆成真实经纬度 local 与示意 satellite 两个坐标空间。
 * satellite 节点的布局永远使用 anchor，不能把 anchor 转成伪造经纬度。
 */
export function resolveCompositeMapNodes(rawConfig, profiles = []) {
  const config = normalizeMapConfig(rawConfig);
  if (config.schemaVersion !== 2 || config.mode !== 'XIAN_COMPOSITE') return { local: [], satellite: [], config };
  const normalized = (Array.isArray(profiles) ? profiles : []).map(normalizeProfile);
  const activePrimary = normalized.filter(p => active(p.status)
    && p.operatingLevel.toUpperCase() === 'PRIMARY');
  const selector = config.localSelector || {};
  const local = activePrimary.filter(p => String(p.cityCode) === String(selector.cityCode || '610100')
    && isGcj02Profile(p) && isValidCoordinate(p));
  const byCode = new Map(normalized.map(p => [p.orgCode, p]));
  const satellite = (config.satelliteNodes || []).filter(node => MAP_ANCHORS.includes(node.anchor)).map(node => {
    const profile = byCode.get(String(node.orgCode));
    return {
      ...node,
      orgCode: String(node.orgCode || ''),
      orgName: node.orgName || profile?.orgName || node.orgCode,
      targetScreenCode: node.targetScreenCode || 'SCR_BRANCH',
      position: ANCHOR_POSITIONS[node.anchor]
    };
  }).filter(node => node.orgCode && byCode.has(node.orgCode)
    && active(byCode.get(node.orgCode)?.status)
    // 四个异地锚点是经营机构导航入口；即使服务端暂未拒绝坏草稿，前端也不能
    // 把下属网点/社区支行当作独立经营节点展示。
    && String(byCode.get(node.orgCode)?.operatingLevel || '').toUpperCase() === 'PRIMARY');
  return { local, satellite, config };
}

/**
 * 把后端授权渲染包中的二级分行出现许可映射为本期冻结的运行态语义。
 *
 * 空数组表示调用方没有提供包时的本地/设计态；此时由既有 profile 适配路径处理。
 * 非空包严格按其中已授权 orgCode 取交集，避免前端绕过服务端授权扩大展示范围。
 */
export function resolveXianSecondaryBranches(authorizedNodes = []) {
  const source = Array.isArray(authorizedNodes) ? authorizedNodes : [];
  const authorizedCodes = new Set(source.map(node => String(node?.orgCode || '')).filter(Boolean));
  return XIAN_SECONDARY_BRANCHES
    .filter(node => authorizedCodes.has(node.orgCode))
    .map(node => ({ ...node, position: { ...ANCHOR_POSITIONS[node.anchor] } }));
}

/** 发布前的前端提示校验；服务端仍需重做全部校验。 */
export function validateCompositeMapConfig(rawConfig, profiles = [], memberOrgCodes = []) {
  const config = normalizeMapConfig(rawConfig);
  if (config.schemaVersion === 1) return [];
  if (config.schemaVersion !== 2 || config.mode !== 'XIAN_COMPOSITE') return ['地图 schemaVersion 不受支持'];
  const errors = [];
  const nodes = config.satelliteNodes || [];
  const anchors = new Set();
  const orgs = new Set();
  for (const node of nodes) {
    if (!MAP_ANCHORS.includes(node.anchor)) errors.push(`示意节点锚点无效: ${node.anchor || '-'}`);
    if (anchors.has(node.anchor)) errors.push(`示意节点锚点重复: ${node.anchor}`);
    anchors.add(node.anchor);
    if (orgs.has(String(node.orgCode))) errors.push(`地图机构重复: ${node.orgCode}`);
    orgs.add(String(node.orgCode));
  }
  for (const anchor of MAP_ANCHORS) {
    if (!anchors.has(anchor)) errors.push(`示意节点缺失: ${anchor}`);
  }
  const { local } = resolveCompositeMapNodes(config, profiles);
  const member = new Set((memberOrgCodes || []).map(String));
  const profileByCode = new Map((profiles || []).map(normalizeProfile).map(profile => [profile.orgCode, profile]));
  for (const node of nodes) {
    const code = String(node.orgCode || '');
    if (member.size && !member.has(code)) errors.push(`示意机构不在机构组: ${node.orgCode}`);
    const profile = profileByCode.get(code);
    if (!profile || !active(profile.status)) errors.push(`示意机构缺少有效机构画像: ${node.orgCode}`);
    else if (profile.operatingLevel.toUpperCase() !== 'PRIMARY') {
      errors.push(`示意机构必须为 PRIMARY: ${node.orgCode}`);
    }
  }
  const primary = (profiles || []).map(normalizeProfile)
    .filter(p => active(p.status) && p.operatingLevel.toUpperCase() === 'PRIMARY');
  for (const p of primary) {
    if (member.size && !member.has(p.orgCode)) continue;
    const selector = config.localSelector || {};
    const localCandidate = String(p.cityCode) === String(selector.cityCode || '610100')
      && p.operatingLevel.toUpperCase() === String(selector.operatingLevel || 'PRIMARY').toUpperCase();
    const localPoint = local.some(x => x.orgCode === p.orgCode);
    const satellite = orgs.has(p.orgCode);
    if (localCandidate && (!isGcj02Profile(p) || !isValidCoordinate(p))) {
      errors.push(`机构坐标必须为合法 GCJ-02: ${p.orgName || p.orgCode}`);
    }
    if (localPoint === satellite) {
      errors.push(`${localPoint ? '机构重复落位' : '一级经营机构未落位'}: ${p.orgName || p.orgCode}`);
    }
  }
  if (!String(config.disclaimer || '').trim()) errors.push('地图声明不可为空');
  return errors;
}

/** 发布态 schema2 只认 blockId，schema1 只认发布 dsId；草稿态统一只认当前草稿 blockId。 */
export function buildScreenDataRequest(input = {}) {
  // 后端运行时 DTO 使用严格 String/Map<String, String> 反序列化：可选字段缺省会保留
  // null 业务语义，但显式 JSON null 会在进入服务前按 VALID_005 拒绝。这里只剔除 nullish
  // 可选值；非空（含显式空字符串）值原样保留，不改写身份、周期或路由上下文。
  const contextParams = {};
  if (input.contextParams?.orgCode !== undefined && input.contextParams?.orgCode !== null) {
    contextParams.orgCode = input.contextParams.orgCode;
  }
  if (input.contextParams?.empId !== undefined && input.contextParams?.empId !== null) {
    contextParams.empId = input.contextParams.empId;
  }
  const schemaVersion = input.schemaVersion;
  if (typeof schemaVersion !== 'number' || !Number.isInteger(schemaVersion)) {
    throw new Error('运行时 schemaVersion 必须为 JSON 整数 1 或 2');
  }
  if (input.previewState !== undefined && input.previewState !== 'draft') {
    throw new Error('previewState 仅允许显式值 draft');
  }
  if (input.previewState === 'draft') {
    if (![1, 2].includes(schemaVersion)) throw new Error(`未知草稿 schemaVersion: ${String(schemaVersion)}`);
    const screenCode = String(input.screenCode || '').trim();
    const blockId = input.blockId;
    if (!screenCode) throw new Error('草稿取数请求缺少 screenCode');
    if (!Number.isSafeInteger(blockId) || blockId <= 0) throw new Error('草稿取数请求缺少有效 blockId');
    const request = {
      schemaVersion,
      previewState: 'draft',
      screenCode,
      blockId,
      period: input.period || 'LATEST',
      contextParams
    };
    if (input.dateFrom !== undefined && input.dateFrom !== null) request.dateFrom = input.dateFrom;
    if (input.dateTo !== undefined && input.dateTo !== null) request.dateTo = input.dateTo;
    return request;
  }
  if (schemaVersion === 2) {
    const screenCode = String(input.screenCode || '').trim();
    const blockId = input.blockId;
    if (!screenCode) throw new Error('schema2 运行请求缺少 screenCode');
    if (!Number.isSafeInteger(blockId) || blockId <= 0) throw new Error('schema2 运行请求缺少有效 blockId');
    const request = {
      schemaVersion: 2,
      screenCode,
      blockId,
      period: input.period || 'LATEST',
      contextParams
    };
    if (input.dateFrom !== undefined && input.dateFrom !== null) request.dateFrom = input.dateFrom;
    if (input.dateTo !== undefined && input.dateTo !== null) request.dateTo = input.dateTo;
    return request;
  }
  if (schemaVersion !== 1) {
    throw new Error(`未知运行时 schemaVersion: ${String(input.schemaVersion)}`);
  }
  const screenCode = String(input.screenCode || '').trim();
  if (!screenCode) throw new Error('schema1 历史运行请求缺少 screenCode');
  if (!Number.isSafeInteger(input.dsId) || input.dsId <= 0) {
    throw new Error('schema1 历史运行请求缺少有效 dsId');
  }
  const request = {
    schemaVersion: 1,
    screenCode,
    dsId: input.dsId,
    period: input.period || 'LATEST',
    contextParams
  };
  if (input.dateFrom !== undefined && input.dateFrom !== null) request.dateFrom = input.dateFrom;
  if (input.dateTo !== undefined && input.dateTo !== null) request.dateTo = input.dateTo;
  return request;
}

export function anchorStyle(anchor) {
  return ANCHOR_POSITIONS[anchor] || {};
}

export function isGcj02Profile(profile) {
  return String(pick(profile, 'coordSys', 'coord_sys') || '').toUpperCase() === 'GCJ02';
}
