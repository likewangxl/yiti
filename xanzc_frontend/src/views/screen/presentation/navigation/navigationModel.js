/**
 * 经营大屏导航的唯一目标表与 query 适配。
 *
 * 页面之间只传递可序列化的选择上下文；实际屏权限和机构范围必须在
 * resolveAuthorizedScreen 中重新读取服务端目录及视图，不能由浏览器 query
 * 充当授权凭证。
 */

export const BUSINESS_LINE_TARGETS = Object.freeze({
  COMMON: Object.freeze({ businessLine: 'COMMON', template: 'branch-overview-v1', screenCode: 'SCR_PROVINCE' }),
  CORP: Object.freeze({ businessLine: 'CORP', template: 'corporate-overview-v1', screenCode: 'SCR_CORP_OVERVIEW' }),
  RETAIL: Object.freeze({ businessLine: 'RETAIL', template: 'retail-overview-v1', screenCode: 'SCR_RETAIL_OVERVIEW' })
});

const QUERY_KEYS = Object.freeze(['cityCode', 'orgCode', 'businessLine', 'period', 'metricKey', 'view', 'state', 'source']);
const BUSINESS_LINES = new Set(Object.keys(BUSINESS_LINE_TARGETS));
const MAX_SERIALIZED_STATE_LENGTH = 8192;

function text(value) {
  if (Array.isArray(value)) return text(value[0]);
  return value === null || value === undefined ? '' : String(value).trim();
}

function upper(value) {
  return text(value).toUpperCase().replace(/[\s-]+/g, '_');
}

function navError(code, message, details = {}) {
  const error = new Error(message);
  error.code = code;
  Object.assign(error, details);
  return error;
}

function parseObject(value) {
  if (value && typeof value === 'object' && !Array.isArray(value)) return value;
  if (typeof value !== 'string' || value.length === 0 || value.length > MAX_SERIALIZED_STATE_LENGTH) return null;
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function serializeObject(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return '';
  try {
    const serialized = JSON.stringify(value);
    return serialized.length <= MAX_SERIALIZED_STATE_LENGTH ? serialized : '';
  } catch {
    return '';
  }
}

function setQueryValue(query, key, value) {
  const normalized = text(value);
  if (normalized) query[key] = normalized;
  else delete query[key];
}

/** 将页面上下文编码为 Vue Router 可接受的纯字符串 query。 */
export function buildNavigationQuery(context = {}, baseQuery = {}) {
  const query = {};
  const base = baseQuery && typeof baseQuery === 'object' ? baseQuery : {};
  // 只保留白名单字段；路由目标始终由本模块生成，不能把任意 query 当 URL。
  for (const key of QUERY_KEYS) {
    if (key === 'view' || key === 'state') continue;
    if (key === 'businessLine') {
      const value = upper(context[key] ?? base[key]);
      if (BUSINESS_LINES.has(value)) query[key] = value;
      continue;
    }
    if (key === 'source') {
      const value = text(context[key] ?? base[key]).toLowerCase();
      if (value === 'test' || value === 'live') query[key] = value;
      continue;
    }
    setQueryValue(query, key, context[key] ?? base[key]);
  }
  for (const key of ['view', 'state']) {
    const value = context[key] !== undefined ? context[key] : base[key];
    const serialized = typeof value === 'string' ? (parseObject(value) ? value : '') : serializeObject(value);
    if (serialized) query[key] = serialized;
  }
  return query;
}

/** 从 route.query 读取上下文，非法 JSON 变为 null 而不是抛出或猜测。 */
export function parseNavigationQuery(query = {}) {
  const source = query && typeof query === 'object' ? query : {};
  const businessLine = upper(source.businessLine);
  return {
    cityCode: text(source.cityCode),
    orgCode: text(source.orgCode),
    businessLine: BUSINESS_LINES.has(businessLine) ? businessLine : '',
    period: text(source.period),
    metricKey: text(source.metricKey),
    view: parseObject(source.view ?? source.viewport),
    state: parseObject(source.state ?? source.viewState),
    source: ['test', 'live'].includes(text(source.source).toLowerCase()) ? text(source.source).toLowerCase() : ''
  };
}

/** 返回固定的代码化大屏路由；businessLine 不是路径输入。 */
export function routeForBusinessLine(businessLine, context = {}, baseQuery = {}) {
  const key = upper(businessLine);
  const target = BUSINESS_LINE_TARGETS[key];
  if (!target) return null;
  return {
    name: 'CodeScreenPage',
    params: { template: target.template },
    query: buildNavigationQuery({ ...context, businessLine: key }, baseQuery)
  };
}

/** 旧支行 URL 仍使用固定受保护路由，不复制机构页面或拼任意 path。 */
export function routeForInstitution(context = {}, baseQuery = {}) {
  const query = buildNavigationQuery({ ...context, businessLine: upper(context.businessLine || 'COMMON') }, baseQuery);
  if (!query.orgCode) return null;
  return { name: 'BranchOperatingPage', query };
}

function valuesOf(value) {
  return (Array.isArray(value) ? value : []).map(upper).filter(Boolean);
}

function rulesOf(source) {
  if (!source || typeof source !== 'object' || Array.isArray(source)) return null;
  const rules = source.navigationRules || source.navigation?.rules || source.institutionNavigationRules || source;
  if (!rules || typeof rules !== 'object' || Array.isArray(rules)) return null;
  if (rules.allowedOperatingLevels instanceof Set
      || rules.allowedOrgNatures instanceof Set
      || rules.hiddenOperatingLevels instanceof Set
      || rules.hiddenOrgNatures instanceof Set) return rules;
  const allowedOperatingLevels = new Set(valuesOf(rules.allowedOperatingLevels ?? rules.operatingLevels));
  const allowedOrgNatures = new Set(valuesOf(rules.allowedOrgNatures ?? rules.orgNatures));
  const hiddenOperatingLevels = new Set(valuesOf(rules.hiddenOperatingLevels ?? rules.excludedOperatingLevels));
  const hiddenOrgNatures = new Set(valuesOf(rules.hiddenOrgNatures ?? rules.excludedOrgNatures));
  if (!allowedOperatingLevels.size && !allowedOrgNatures.size && !hiddenOperatingLevels.size && !hiddenOrgNatures.size) return null;
  return { allowedOperatingLevels, allowedOrgNatures, hiddenOperatingLevels, hiddenOrgNatures };
}

export function navigationRulesOf(view) {
  return rulesOf(view?.navigationRules || view?.navigation?.rules || view?.institutionNavigationRules);
}

/**
 * 只根据调用方提供的服务端规则与画像字段识别层级。
 * 没有规则时一律待确认；orgName/orgCode 永远不参与判断。
 */
export function classifyInstitutionLayer(institution = {}, explicitRules = null) {
  const rules = rulesOf(explicitRules);
  if (!rules) return { known: false, displayable: false, value: '', reason: 'LAYER_UNCONFIRMED' };
  const values = [institution?.operatingLevel, institution?.operating_level, institution?.orgNature, institution?.org_nature]
    .map(upper)
    .filter(Boolean);
  const operatingLevels = valuesOf([institution?.operatingLevel, institution?.operating_level]);
  const orgNatures = valuesOf([institution?.orgNature, institution?.org_nature]);
  const hidden = operatingLevels.find(value => rules.hiddenOperatingLevels.has(value))
    || orgNatures.find(value => rules.hiddenOrgNatures.has(value));
  const displayable = operatingLevels.find(value => rules.allowedOperatingLevels.has(value))
    || orgNatures.find(value => rules.allowedOrgNatures.has(value));
  if (hidden) return { known: true, displayable: false, value: hidden, reason: 'LAYER_EXCLUDED' };
  if (displayable) return { known: true, displayable: true, value: displayable, reason: '' };
  return { known: false, displayable: false, value: '', reason: 'LAYER_UNCONFIRMED' };
}

function institutionCode(institution) {
  return text(institution?.orgCode ?? institution?.org_code ?? institution);
}

function institutionDirectory(view) {
  const directory = view?.panoramaInstitutions ?? view?.panorama_institutions;
  return Array.isArray(directory) ? directory.filter(Boolean) : [];
}

/** 解析一个目标屏的机构授权，不把查询结果或旧页面机构当授权目录。 */
export function resolveInstitution(view, orgCode, explicitRules = undefined) {
  const code = text(orgCode);
  const rules = explicitRules === undefined ? navigationRulesOf(view) : explicitRules;
  if (!code) return { orgCode: '', authorized: false, institution: null, layer: classifyInstitutionLayer({}, rules), reason: 'ORG_REQUIRED' };
  const institution = institutionDirectory(view).find(item => institutionCode(item) === code) || null;
  if (!institution) return { orgCode: code, authorized: false, institution: null, layer: classifyInstitutionLayer({}, rules), reason: 'ORG_NOT_AUTHORIZED' };
  const layer = classifyInstitutionLayer(institution, rules);
  return { orgCode: code, authorized: true, institution, layer, reason: layer.known ? layer.reason : 'LAYER_UNCONFIRMED' };
}

function catalogEntry(catalog, target) {
  if (!Array.isArray(catalog)) throw navError('CATALOG_INVALID', '大屏授权目录格式无效');
  const matches = catalog.filter(item => item
    && text(item.screenCode) === target.screenCode
    && text(item.template) === target.template
    && ['TEST', 'LIVE'].includes(upper(item.dataMode)));
  if (matches.length !== 1) {
    throw navError('SCREEN_NOT_AUTHORIZED', '目标条线大屏未获得唯一授权', { target, matches });
  }
  return matches[0];
}

/**
 * 切换条线时必须按“目录 → 目标视图 → 机构交集”顺序重新确认。
 * orgCode 为空表示省级上下文，不伪造默认机构。
 */
export async function resolveAuthorizedScreen({ businessLine, orgCode = '', listAvailableScreens, getScreenView, navigationRules = undefined }) {
  const key = upper(businessLine);
  const target = BUSINESS_LINE_TARGETS[key];
  if (!target) throw navError('BUSINESS_LINE_UNSUPPORTED', '不支持的经营条线');
  if (typeof listAvailableScreens !== 'function' || typeof getScreenView !== 'function') {
    throw navError('NAVIGATION_DEPENDENCY_MISSING', '缺少屏授权复核能力');
  }
  const catalog = await listAvailableScreens();
  const entry = catalogEntry(catalog, target);
  const view = await getScreenView(entry.screenCode);
  if (!view || text(view.screenCode) !== target.screenCode) {
    throw navError('SCREEN_VIEW_MISMATCH', '目标屏视图身份不匹配', { target, entry });
  }
  const code = text(orgCode);
  if (code) {
    const resolved = resolveInstitution(view, code, navigationRules === undefined ? navigationRulesOf(view) : navigationRules);
    if (!resolved.authorized) throw navError('ORG_NOT_AUTHORIZED', '当前机构不在目标屏授权目录中', { target, entry, view, resolved });
    if (!resolved.layer.known) throw navError('ORG_LAYER_UNCONFIRMED', '当前机构经营层级待确认', { target, entry, view, resolved });
    if (!resolved.layer.displayable) throw navError('ORG_NOT_DISPLAYABLE', '当前机构不属于允许展示的经营层级', { target, entry, view, resolved });
    return { target, entry, view, institution: resolved.institution, layer: resolved.layer };
  }
  return { target, entry, view, institution: null, layer: null };
}

export { QUERY_KEYS };
