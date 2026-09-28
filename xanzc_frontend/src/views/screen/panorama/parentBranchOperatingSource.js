import { navigationRulesOf } from '../presentation/navigation/navigationModel';

/**
 * 支行详情只能继承这两张省级支行经营屏的已复核数据源。
 * sourceScreenCode/sourcePreview 是导航上下文，不是授权凭证；页面仍需用
 * 返回的 view、institutionRules 和目录重新完成授权判断。
 */
export const PARENT_BRANCH_SOURCE_SCREEN_CODES = Object.freeze(['SCR_PROVINCE', 'SCR_PROVINCE_MAP_V2']);
export const PARENT_BRANCH_SOURCE_PREVIEW = 'draft';
const SOURCE_SCREEN_CODE_SET = new Set(PARENT_BRANCH_SOURCE_SCREEN_CODES);
const TEMPLATE = 'branch-overview-v1';
const TEST_CLASSIFICATION = 'TEST';
const KPI_KEYS = new Set(['deposit', 'depositAverage', 'depositIncrease', 'loan', 'customers', 'revenue', 'rate']);
const CORE_KPI_DEFAULTS = Object.freeze([
  ['deposit', '存款余额', '亿元'],
  ['loan', '贷款余额', '亿元'],
  ['customers', '营销有效归属客户数', '万户'],
  ['revenue', '营业收入', '亿元'],
  ['rate', '目标完成率', '%']
]);

function text(value) {
  if (Array.isArray(value)) return text(value[0]);
  return value === null || value === undefined ? '' : String(value).trim();
}

function object(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function parseObject(value, field) {
  if (object(value)) return value;
  if (typeof value !== 'string' || !value.trim()) throw new Error(`${field}缺失`);
  try {
    const parsed = JSON.parse(value);
    if (!object(parsed)) throw new Error();
    return parsed;
  } catch {
    throw new Error(`${field}不是有效 JSON`);
  }
}

function packageOf(view) {
  return parseObject(view?.renderPackage ?? view?.render_package ?? view?.renderPackageJson
    ?? view?.render_package_json, '源屏渲染包');
}

function directoryOf(view) {
  const directory = view?.panoramaInstitutions ?? view?.panorama_institutions;
  if (!Array.isArray(directory) || !directory.length) throw new Error('源屏授权机构目录缺失');
  const seen = new Set();
  return directory.map((item, index) => {
    if (!object(item)) throw new Error(`源屏授权机构目录第 ${index + 1} 项无效`);
    const orgCode = text(item.orgCode ?? item.org_code);
    if (!orgCode) throw new Error(`源屏授权机构目录第 ${index + 1} 项机构号缺失`);
    if (seen.has(orgCode)) throw new Error(`源屏授权机构目录存在重复机构号: ${orgCode}`);
    seen.add(orgCode);
    return { ...item, orgCode };
  });
}

function expectedStateOf(preview) {
  return preview === PARENT_BRANCH_SOURCE_PREVIEW ? 'draft' : 'published';
}

function previewContextOf(context = {}) {
  const preview = text(context.sourcePreview).toLowerCase();
  if (preview && preview !== PARENT_BRANCH_SOURCE_PREVIEW) {
    throw new Error('源屏 sourcePreview 仅允许 draft');
  }
  return preview;
}

function hasOwn(value, key) {
  return Object.prototype.hasOwnProperty.call(value || {}, key);
}

/**
 * 严格解析父级省级屏。published 源屏允许省略 sourcePreview，draft 必须显式
 * 携带 sourcePreview=draft；两种状态都要求响应状态与 preview 语义完全一致。
 */
export function parseParentBranchOperatingSource(response, context = {}) {
  if (!object(response)) throw new Error('父屏源响应必须为对象');
  const sourceScreenCode = text(context.sourceScreenCode || response.screenCode);
  if (!SOURCE_SCREEN_CODE_SET.has(sourceScreenCode)) throw new Error('父屏源屏编码不在白名单中');
  if (text(response.screenCode) !== sourceScreenCode) throw new Error('父屏源屏编码与响应不一致');
  const sourcePreview = previewContextOf(context);
  if (response.state !== expectedStateOf(sourcePreview)) throw new Error('父屏 state 与 sourcePreview=draft 不匹配');
  if (response.runtimeSchemaVersion !== 2) throw new Error('父屏运行 schema 必须为 2');
  if (text(response.orgScopeMode ?? response.org_scope_mode).toUpperCase() !== 'NAMED_GROUP') {
    throw new Error('父屏机构范围必须为 NAMED_GROUP');
  }
  if (!hasOwn(response, 'institutionRules') || !object(response.institutionRules)) {
    throw new Error('父屏顶层 institutionRules 规则缺失或无效');
  }
  const rules = navigationRulesOf({ institutionRules: response.institutionRules });
  if (!rules) throw new Error('父屏 institutionRules 规则缺失或无效');
  const renderPackage = packageOf(response);
  const packageSchema = Number(renderPackage.schemaVersion);
  const draftPackageSchema = sourcePreview === PARENT_BRANCH_SOURCE_PREVIEW
    && [1, 2].includes(packageSchema);
  const publishedPackageSchema = sourcePreview !== PARENT_BRANCH_SOURCE_PREVIEW && packageSchema === 2;
  if (!draftPackageSchema && !publishedPackageSchema) {
    throw new Error('父屏渲染包 schema 与源屏状态不匹配');
  }
  if (!Array.isArray(renderPackage.components) || !object(renderPackage.bindSnapshots)) {
    throw new Error('父屏渲染包绑定身份不完整');
  }
  const style = renderPackage.canvasStyle;
  if (!object(style) || style.dataClassification !== TEST_CLASSIFICATION) {
    throw new Error('父屏数据分类必须为 TEST');
  }
  const presentation = style.presentation;
  if (!object(presentation) || presentation.type !== 'CODE' || presentation.template !== TEMPLATE) {
    throw new Error('父屏模板身份不匹配');
  }
  const directory = directoryOf(response);
  return {
    ...response,
    screenCode: sourceScreenCode,
    sourceScreenCode,
    sourcePreview,
    renderPackage,
    panoramaInstitutions: directory,
    institutionRules: response.institutionRules,
    parentSource: Object.freeze({ screenCode: sourceScreenCode, preview: sourcePreview || null, state: response.state })
  };
}

function arrayOf(value) {
  return Array.isArray(value) ? value.filter(item => object(item)).map(item => ({ ...item })) : [];
}

function institutionOf(model, sourceView, orgCode) {
  const code = text(orgCode);
  const directory = Array.isArray(sourceView?.panoramaInstitutions) ? sourceView.panoramaInstitutions : [];
  const authorized = directory.find(item => text(item?.orgCode ?? item?.org_code) === code);
  if (!authorized) throw new Error('父屏机构不在授权目录中');
  const scopedInstitutions = Array.isArray(model?.institutions)
    ? model.institutions.filter(item => object(item)) : [];
  if (!scopedInstitutions.length) throw new Error('父屏单机构模型缺少机构范围');
  if (scopedInstitutions.some(item => text(item.orgCode ?? item.org_code) !== code)) {
    throw new Error('父屏单机构模型机构范围不精确');
  }
  return { directory: { ...authorized }, scoped: scopedInstitutions[0] };
}

function sourceRows(model, key, orgCode) {
  return arrayOf(model?.[key]).filter(item => {
    const rowOrgCode = text(item.orgCode ?? item.org_code);
    return !rowOrgCode || rowOrgCode === orgCode;
  });
}

/**
 * 将 usePanoramaData(singleOrg) 的模型变成 BranchOperatingDashboard 契约。
 * 这里只复制源模型已经明确给出的字段；citySummaries/rankings 等全辖字段
 * 故意丢弃，防止单机构页面把全市总量或演示值展示成支行指标。
 */
export function buildParentBranchOperatingModel({ model = {}, sourceView, orgCode = '' } = {}) {
  const source = parseParentBranchOperatingSource(sourceView, {
    sourceScreenCode: sourceView?.screenCode,
    sourcePreview: sourceView?.state === 'draft' ? PARENT_BRANCH_SOURCE_PREVIEW : ''
  });
  const code = text(orgCode);
  if (text(model?.orgCode ?? model?.org_code) && text(model?.orgCode ?? model?.org_code) !== code) {
    throw new Error('父屏单机构模型机构范围不精确');
  }
  const identity = institutionOf(model, source, code).directory;
  const sourceKpis = Array.isArray(model?.kpis)
    ? model.kpis.filter(item => object(item) && KPI_KEYS.has(text(item.key)))
      .filter(item => !text(item.orgCode ?? item.org_code) || text(item.orgCode ?? item.org_code) === code)
      .map(item => ({ ...item })) : [];
  const sourceKpiByKey = new Map(sourceKpis.map(item => [text(item.key), item]));
  const scopedKpis = CORE_KPI_DEFAULTS.map(([key, label, unit]) => sourceKpiByKey.get(key)
    || { key, label, value: null, unit, status: '源数据缺失' });
  for (const item of sourceKpis) {
    if (!CORE_KPI_DEFAULTS.some(([key]) => key === text(item.key))) scopedKpis.push(item);
  }
  const sourceMetadata = object(model?.sourceMetadata) ? { ...model.sourceMetadata } : {};
  const sourceQualities = object(model?.sourceQualities) ? { ...model.sourceQualities } : {};
  const sourceEntries = arrayOf(model?.sources);
  return {
    orgCode: code,
    orgName: text(identity.orgName ?? identity.org_name),
    cityCode: text(identity.cityCode ?? identity.city_code),
    cityName: text(identity.cityName ?? identity.city_name),
    dataDate: text(model?.dataDate),
    sourceLabel: 'TEST 测试数据 · 非实际经营数据',
    metricLabels: object(model?.metricLabels) ? { ...model.metricLabels } : {},
    kpis: scopedKpis,
    targets: sourceRows(model, 'targets', code),
    targetDate: text(model?.targetDate),
    trend: sourceRows(model, 'trend', code),
    trendUnit: text(model?.trendUnit),
    composition: sourceRows(model, 'composition', code),
    marketing: sourceRows(model, 'marketing', code),
    projects: sourceRows(model, 'projects', code),
    teams: sourceRows(model, 'teams', code),
    attention: sourceRows(model, 'attention', code),
    institutions: [{ ...identity, orgCode: code }],
    sources: [
      { label: '数据来源', detail: `分行经营总览 · ${source.sourcePreview ? '草稿预览' : '已发布'} · 屏编码 ${source.screenCode} · 单机构查询` },
      ...sourceEntries
    ],
    sourceQualities,
    sourceMetadata,
    issues: arrayOf(model?.issues),
    gaps: object(model?.gaps) ? { ...model.gaps } : {},
    citySummaries: {}
  };
}

export const parseParentBranchSource = parseParentBranchOperatingSource;
export const buildParentBranchModel = buildParentBranchOperatingModel;
