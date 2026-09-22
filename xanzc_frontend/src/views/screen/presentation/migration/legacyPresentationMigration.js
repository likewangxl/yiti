import { DISPLAY_UNITS } from '../contract/displayContract';
import {
  cancelEditorSession,
  createPresentationEditorSession,
  deepClone
} from '../editor/presentationEditorModel';

/**
 * 旧画布迁移的结果状态。状态是面向审核的稳定协议，不能用“已转换”覆盖不确定项。
 */
export const MIGRATION_STATUS = Object.freeze({
  MIGRATED: 'MIGRATED',
  UNRESOLVED: 'UNRESOLVED',
  MISSING_FIELDS: 'MISSING_FIELDS',
  NEEDS_CONFIRMATION: 'NEEDS_CONFIRMATION'
});
export const MIGRATION_STATUSES = MIGRATION_STATUS;

export const MIGRATION_STATUS_LABELS = Object.freeze({
  [MIGRATION_STATUS.MIGRATED]: '已迁移',
  [MIGRATION_STATUS.UNRESOLVED]: '无法确定',
  [MIGRATION_STATUS.MISSING_FIELDS]: '缺字段',
  [MIGRATION_STATUS.NEEDS_CONFIRMATION]: '待确认'
});

const STATUS_KEYS = Object.freeze({
  [MIGRATION_STATUS.MIGRATED]: 'migrated',
  [MIGRATION_STATUS.UNRESOLVED]: 'unresolved',
  [MIGRATION_STATUS.MISSING_FIELDS]: 'missingFields',
  [MIGRATION_STATUS.NEEDS_CONFIRMATION]: 'needsConfirmation'
});

const UNIT_ALIASES = Object.freeze({
  YUAN: 'YUAN',
  CNY: 'YUAN',
  TEN_THOUSAND: 'TEN_THOUSAND',
  TEN_THOUSAND_YUAN: 'TEN_THOUSAND',
  WAN: 'TEN_THOUSAND',
  HUNDRED_MILLION: 'HUNDRED_MILLION',
  HUNDRED_MILLION_YUAN: 'HUNDRED_MILLION',
  YI: 'HUNDRED_MILLION',
  COUNT: 'COUNT',
  TEN_THOUSAND_COUNT: 'TEN_THOUSAND_COUNT',
  TEN_THOUSAND_HOUSEHOLDS: 'TEN_THOUSAND_COUNT',
  PERCENT: 'PERCENT',
  '%': 'PERCENT',
  RATIO: 'RATIO'
});

const LAYOUT_BY_TYPE = Object.freeze({
  METRIC_CARD: 'HEADER',
  COMPLETION: 'LEFT',
  TREND: 'CENTER',
  COMPOSITION_TABS: 'LEFT',
  RANKING: 'RIGHT',
  MAP: 'CENTER',
  DETAIL_TABLE: 'BOTTOM'
});

// 新展示运行包需要明确的机构目录过滤规则。该默认值来自已核对的真实
// 机构分类枚举；迁移器不从机构名称、数量或标题推导规则。
const DEFAULT_INSTITUTION_RULES = Object.freeze({
  allowedOperatingLevels: Object.freeze(['PRIMARY']),
  allowedOrgNatures: Object.freeze(['SECONDARY_BRANCH'])
});

// 这是旧 bindingKey 的编码清单，不是标题或模糊别名匹配。新增旧槽位必须先进入清单和测试。
const RULES = Object.freeze([
  {
    type: 'METRIC_CARD',
    keys: [
      'deposit', 'loan', 'revenue', 'customers', 'customerCount', 'aum', 'nplRate', 'rate',
      'depositIncrease', 'depositAverage', 'loanRate',
      'corpDeposit', 'corpDepositAverage', 'corpLoan', 'corpRevenue', 'corpCustomers', 'corpNplRate',
      'retailAum', 'retailDeposit', 'retailDepositAverage', 'retailLoan', 'retailRevenue',
      'retailCustomers', 'retailValueCustomers', 'retailNplRate',
      'branchDeposit', 'branchLoan', 'branchRevenue', 'branchCustomers'
    ]
  },
  {
    type: 'COMPLETION',
    keys: [
      'completion', 'completionRate', 'target', 'targets',
      'branchTargets', 'corpCompletion', 'retailCompletion'
    ]
  },
  {
    type: 'TREND',
    keys: ['trend', 'branchTrend', 'corpTrend', 'retailTrend', 'depositTrend', 'loanTrend', 'revenueTrend']
  },
  {
    type: 'COMPOSITION_TABS',
    keys: ['composition', 'corpComposition', 'retailComposition', 'businessComposition', 'compositionTabs']
  },
  {
    type: 'RANKING',
    keys: ['ranking', 'corpRanking', 'retailRanking', 'branchRanking', 'institutionRanking']
  },
  {
    type: 'MAP',
    keys: ['map', 'cityMap', 'provinceMap', 'institutionMap', 'orgMap']
  },
  {
    type: 'DETAIL_TABLE',
    keys: [
      'attention', 'details', 'detail', 'branches', 'branchList', 'projects', 'team', 'teams',
      'citySummary', 'corpSegments', 'corpAttention', 'corpTargets',
      'retailSegments', 'retailAttention', 'retailTargets'
    ]
  }
]);

const RULE_BY_KEY = new Map(RULES.flatMap(rule => rule.keys.map(key => [key.toLowerCase(), rule])));

const INNER_TYPE_TO_COMPONENT = Object.freeze({
  METRIC_CARD: 'METRIC_CARD',
  NUMBER_CARD: 'METRIC_CARD',
  KPI_CARD: 'METRIC_CARD',
  COMPLETION: 'COMPLETION',
  PROGRESS_BAR: 'COMPLETION',
  GAUGE: 'COMPLETION',
  TARGET_CARD: 'COMPLETION',
  LINE_TREND: 'TREND',
  BAR_LINE: 'TREND',
  AREA_LINE: 'TREND',
  TREND: 'TREND',
  PIE_SHARE: 'COMPOSITION_TABS',
  DONUT: 'COMPOSITION_TABS',
  COMPOSITION: 'COMPOSITION_TABS',
  RANK_LIST: 'RANKING',
  RANKING: 'RANKING',
  FLOW_STATUS: 'DETAIL_TABLE',
  TABLE: 'DETAIL_TABLE',
  DATA_TABLE: 'DETAIL_TABLE',
  MAP: 'MAP',
  MAP_CENTER: 'MAP',
  TABLE_LIST: 'DETAIL_TABLE',
  DETAIL_TABLE: 'DETAIL_TABLE'
});

const DATE_FIELDS = new Set(['date', 'dataDate', 'data_date', 'period', 'month', 'statDate', 'stat_date']);
const IDENTITY_FIELDS = new Set([
  'orgCode', 'org_code', 'orgName', 'org_name', 'name', 'label', 'cityCode', 'city_code',
  'cityName', 'city_name', 'ownerOperatingOrgCode', 'owner_operating_org_code',
  'parentOrgCode', 'parent_org_code', 'lng', 'lat', 'coordSys', 'coord_sys', 'located',
  'date', 'dataDate', 'data_date', 'period', 'month', 'statDate', 'stat_date'
]);
const MAIN_FIELD_PRIORITY = Object.freeze(['value', 'amount', 'actual', 'rate', 'count', 'deposit', 'loan', 'revenue', 'aum']);

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function hasOwn(value, key) {
  return isObject(value) && Object.prototype.hasOwnProperty.call(value, key);
}

function text(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function parseJson(value, fallback = null) {
  if (isObject(value) || Array.isArray(value)) return deepClone(value);
  if (typeof value !== 'string' || !value.trim()) return fallback;
  try { return JSON.parse(value); } catch { return fallback; }
}

/**
 * 画布区块的 bindJson 是服务端返回的可信绑定入口，但它仍必须经过
 * 对象形状校验。数组、标量和损坏 JSON 都不能被迁移器当成绑定对象。
 */
function parseObjectJson(value) {
  if (isObject(value)) return deepClone(value);
  if (typeof value !== 'string' || !value.trim()) return null;
  try {
    const parsed = JSON.parse(value);
    return isObject(parsed) ? deepClone(parsed) : null;
  } catch {
    return null;
  }
}

const BLOCK_SOURCE_IDENTITY_KEYS = Object.freeze([
  'sourceKind', 'source_kind', 'metricCode', 'metric_code', 'metricName', 'metric_name',
  'dimension', 'formula', 'sourceId', 'source_id', 'datasourceId', 'datasource_id',
  'sourceDefinition', 'source_definition'
]);

/**
 * 将真实画布响应中的 blocks 转成迁移器使用的 blockId -> snapshot 映射。
 * 这里不从 styleJson/title/name 推断槽位或指标，且对损坏 bindJson 留下显式
 * fail-close 标记，便于逐项显示“缺字段”而不是把损坏项静默当成空绑定。
 */
export function buildBindSnapshotsFromBlocks(blocks) {
  if (!Array.isArray(blocks)) return null;
  const snapshots = {};
  for (const block of blocks) {
    if (!isObject(block)) continue;
    const blockId = positiveId(block.id ?? block.blockId);
    if (!blockId) continue;
    const bind = parseObjectJson(block.bindJson);
    if (!bind) {
      snapshots[String(blockId)] = {
        __invalidBindJson: true,
        invalidReason: `blocks[${blockId}].bindJson 必须是有效对象`
      };
      continue;
    }
    const snapshot = { bind };
    const componentType = text(block.componentType || block.component_type).toUpperCase();
    if (componentType) snapshot.componentType = componentType;
    for (const key of BLOCK_SOURCE_IDENTITY_KEYS) {
      if (hasOwn(block, key)) snapshot[key] = deepClone(block[key]);
    }
    snapshots[String(blockId)] = snapshot;
  }
  return snapshots;
}

export const buildMigrationSnapshotsFromBlocks = buildBindSnapshotsFromBlocks;

function positiveId(value) {
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isSafeInteger(number) && number > 0 ? number : null;
}

function normalizeUnit(value) {
  const key = text(value).toUpperCase();
  const unit = UNIT_ALIASES[key];
  return unit && DISPLAY_UNITS.includes(unit) && unit !== 'AUTO' ? unit : null;
}

function safeToken(value, fallback = 'item') {
  const raw = value === null || value === undefined ? '' : String(value).trim();
  const token = raw.replace(/[^A-Za-z0-9_-]/g, '-').replace(/^-+|-+$/g, '');
  return token || fallback;
}

function stableHash(value) {
  let hash = 2166136261;
  for (const character of String(value)) {
    hash ^= character.charCodeAt(0);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(36);
}

function stableComponentId(bindingKey, blockId, sourceId, sourceIndex, usedIds) {
  const key = safeToken(bindingKey, 'component').toLowerCase();
  const trace = safeToken(blockId || sourceId || `index-${sourceIndex}`, `index-${sourceIndex}`);
  const base = `legacy-${key}-${trace}`;
  let candidate = base.length <= 64 ? base : `${base.slice(0, 54)}-${stableHash(base).slice(0, 8)}`;
  if (!usedIds.has(candidate)) return candidate;
  let index = 2;
  while (usedIds.has(`${candidate.slice(0, Math.max(2, 64 - String(index).length - 1))}-${index}`)) index += 1;
  return `${candidate.slice(0, Math.max(2, 64 - String(index).length - 1))}-${index}`;
}

function firstObject(...values) {
  return values.find(isObject) || null;
}

function extractPresentation(source) {
  if (isObject(source) && source.displaySchemaVersion !== undefined && isObject(source.display)) return source;
  if (isObject(source?.presentation) && source.presentation.displaySchemaVersion !== undefined) return source.presentation;
  if (isObject(source?.canvasStyle?.presentation) && source.canvasStyle.presentation.displaySchemaVersion !== undefined) {
    return source.canvasStyle.presentation;
  }
  if (isObject(source?.renderPackage?.canvasStyle?.presentation)
    && source.renderPackage.canvasStyle.presentation.displaySchemaVersion !== undefined) {
    return source.renderPackage.canvasStyle.presentation;
  }
  return null;
}

/** 将 API 返回的 canvas/renderPackage/draft JSON 统一解包，不读取标题作身份。 */
function extractLegacyPackage(source) {
  const parsedCandidate = typeof source === 'string' ? parseJson(source, null) : source;
  const parsedSource = isObject(parsedCandidate) ? parsedCandidate : {};
  const parseIssues = [];
  if (typeof source === 'string' && parsedCandidate === null) parseIssues.push('旧配置不是有效 JSON');
  const renderPackage = parseJson(parsedSource.renderPackageJson, null)
    || (isObject(parsedSource.renderPackage) ? parsedSource.renderPackage : null);
  if (typeof parsedSource.renderPackageJson === 'string' && parsedSource.renderPackageJson.trim()
      && !parseJson(parsedSource.renderPackageJson, null)) {
    parseIssues.push('renderPackageJson 不是有效 JSON');
  }
  const draft = parseJson(parsedSource.canvasDraftJson, null);
  const canvasStyle = parseJson(parsedSource.canvasStyleJson, null);
  if (typeof parsedSource.canvasDraftJson === 'string' && parsedSource.canvasDraftJson.trim() && draft === null) {
    parseIssues.push('canvasDraftJson 不是有效 JSON');
  }
  if (typeof parsedSource.canvasStyleJson === 'string' && parsedSource.canvasStyleJson.trim() && canvasStyle === null) {
    parseIssues.push('canvasStyleJson 不是有效 JSON');
  }
  const root = renderPackage || parsedSource;
  const style = firstObject(root.canvasStyle, canvasStyle, parsedSource.canvasStyle) || {};
  const rawComponents = hasOwn(root, 'components') ? root.components
    : (hasOwn(draft, 'components') ? draft.components : parsedSource.components);
  const components = Array.isArray(rawComponents) ? rawComponents : [];
  const componentShapeIssue = [
    ...parseIssues,
    rawComponents !== undefined && !Array.isArray(rawComponents) ? '旧配置 components 必须为数组' : ''
  ].filter(Boolean).join('；');
  const snapshots = firstObject(
    parseJson(root.bindSnapshots, null),
    parseJson(parsedSource.bindSnapshots, null),
    parseJson(draft?.bindSnapshots, null),
    root.bindSnapshots,
    parsedSource.bindSnapshots,
    draft?.bindSnapshots
  ) || {};
  const metricLabels = firstObject(
    style.metricLabels,
    style.presentation?.metricLabels,
    parsedSource.metricLabels,
    parsedSource.presentation?.metricLabels
  ) || {};
  const rawBlocks = Array.isArray(parsedSource.blocks) ? parsedSource.blocks
    : (Array.isArray(root.blocks) ? root.blocks : null);
  const blockSnapshots = rawBlocks?.length ? buildBindSnapshotsFromBlocks(rawBlocks) : null;
  return {
    root,
    source: parsedSource,
    canvasStyle: deepClone(style),
    components,
    // 新画布 API 只返回 blocks；仅在没有有效 blocks 时兼容旧发布包快照。
    bindSnapshots: blockSnapshots || snapshots,
    blocks: rawBlocks ? deepClone(rawBlocks) : [],
    metricLabels,
    institutionRules: firstObject(
      style.presentation?.institutionRules,
      parsedSource.institutionRules,
      parsedSource.presentation?.institutionRules
    ),
    presentation: extractPresentation(parsedSource) || extractPresentation(root),
    componentShapeIssue
  };
}

function flattenComponents(components, parentPath = [], result = []) {
  if (!Array.isArray(components)) return result;
  components.forEach((component, index) => {
    if (!isObject(component)) {
      result.push({ component: null, path: [...parentPath, index] });
      return;
    }
    const path = [...parentPath, index];
    if (Array.isArray(component.children) && component.component !== 'ChartWidget') {
      flattenComponents(component.children, path, result);
      return;
    }
    result.push({ component, path });
  });
  return result;
}

function bindingKeyOf(component) {
  return text(component?.propValue?.bindingKey)
    || text(component?.prop_value?.bindingKey)
    || text(component?.bindingKey);
}

function innerTypeOf(component) {
  return text(component?.innerType || component?.inner_type).toUpperCase();
}

function snapshotOf(snapshots, blockId) {
  if (!blockId || !isObject(snapshots)) return null;
  return snapshots[String(blockId)] || snapshots[blockId] || null;
}

function bindOf(snapshot) {
  return firstObject(snapshot?.bind, snapshot?.binding);
}

function fieldsOf(bind) {
  return isObject(bind?.fields) ? bind.fields : null;
}

function unitsOf(bind) {
  return isObject(bind?.units) ? bind.units : null;
}

function fieldEntries(fields) {
  return Object.entries(fields || {})
    .map(([semantic, field]) => ({ semantic: text(semantic), field: text(field) }))
    .filter(item => item.semantic && item.field);
}

function unitFor(bind, semantic, fields) {
  const units = unitsOf(bind);
  if (!units) return null;
  const direct = normalizeUnit(units[semantic]);
  if (direct) return direct;
  const fallback = normalizeUnit(units.value);
  if (fallback && (!semantic || semantic === 'value')) return fallback;
  const entries = Object.entries(units).map(([, value]) => normalizeUnit(value)).filter(Boolean);
  if (entries.length === 1 && (fields ? Object.keys(fields).length === 1 : true)) return entries[0];
  return null;
}

function sourceMeta(snapshot, bind) {
  const identity = firstObject(
    bind?.sourceIdentity, bind?.source_identity,
    bind?.sourceDefinition, bind?.source_definition,
    snapshot?.sourceIdentity, snapshot?.source_identity,
    snapshot?.sourceDefinition, snapshot?.source_definition
  ) || {};
  const sourceKind = text(bind?.sourceKind || snapshot?.sourceKind || bind?.source_kind || snapshot?.source_kind
    || identity.sourceKind || identity.source_kind).toUpperCase();
  return {
    sourceKind,
    metricCode: text(bind?.metricCode || bind?.metric_code || snapshot?.metricCode || snapshot?.metric_code
      || identity.metricCode || identity.metric_code),
    metricName: text(bind?.metricName || bind?.metric_name || snapshot?.metricName || snapshot?.metric_name
      || identity.metricName || identity.metric_name),
    dimension: text(bind?.dimension || snapshot?.dimension || identity.dimension).toUpperCase() || 'ORG',
    formula: text(bind?.formula || snapshot?.formula || identity.formula)
  };
}

function resolveRule(bindingKey) {
  return RULE_BY_KEY.get(text(bindingKey).toLowerCase()) || null;
}

function structureType(component, rule, snapshot) {
  const encodedType = innerTypeOf(component) || text(snapshot?.componentType).toUpperCase();
  const encoded = INNER_TYPE_TO_COMPONENT[encodedType] || null;
  if (!encoded || !rule) return encoded;
  // TABLE_LIST 在旧版同时承载排名和明细，bindingKey 是其正式身份编码，允许这一个明确例外。
  if (encodedType === 'TABLE_LIST' && ['RANKING', 'DETAIL_TABLE'].includes(rule.type)) return rule.type;
  return encoded;
}

function preferredField(entries, bindingKey, allow = MAIN_FIELD_PRIORITY) {
  const bySemantic = new Map(entries.map(item => [item.semantic.toLowerCase(), item]));
  for (const semantic of allow) {
    if (bySemantic.has(semantic.toLowerCase())) return bySemantic.get(semantic.toLowerCase());
  }
  if (entries.length === 1) return entries[0];
  // 仅使用字段编码结构；这里不读取标题或 metricLabels 来选择字段。
  return null;
}

function labelFor(bindingKey, field, metricLabels, bind, semantic) {
  const explicitFieldLabels = firstObject(bind?.fieldLabels, bind?.field_labels);
  return text(explicitFieldLabels?.[semantic]) || text(metricLabels?.[bindingKey]) || text(field) || semantic;
}

function refFor(blockId, unit, meta) {
  return {
    blockId,
    role: 'PRIMARY',
    metricCode: meta.metricCode,
    metricName: meta.metricName,
    unit,
    dimension: ['ORG', 'EMP', 'CUST', 'COMMON'].includes(meta.dimension) ? meta.dimension : 'ORG',
    ...(meta.formula ? { formula: meta.formula } : {})
  };
}

function baseComponent({ componentId, componentType, order, visible, title, blockId, unit, meta }) {
  return {
    componentId,
    componentType,
    layoutRegion: LAYOUT_BY_TYPE[componentType],
    order,
    visible,
    text: {
      titleMode: title ? 'CUSTOM' : 'AUTO',
      title: title || '',
      subtitle: '',
      description: ''
    },
    format: {
      displayUnit: unit,
      decimals: 2,
      thousandsSeparator: true,
      negativeStyle: 'SIGNED',
      emptyText: '—'
    },
    content: { mainField: '', subFields: [], series: [], columns: [], tabs: [], rankingMetrics: [] },
    interaction: { action: 'NONE' },
    dataRefs: [refFor(blockId, unit, meta)]
  };
}

function makeMetricComponent(input, componentType) {
  const { entries, bindingKey, metricLabels, bind, blockId, meta, base } = input;
  const selected = preferredField(entries, bindingKey);
  if (!selected) return { status: MIGRATION_STATUS.NEEDS_CONFIRMATION, reasons: ['存在多个字段，无法确定主展示字段'] };
  const unit = unitFor(bind, selected.semantic, bind.fields);
  if (!unit) return { status: MIGRATION_STATUS.MISSING_FIELDS, reasons: [`字段 ${selected.semantic} 缺少原始单位`] };
  const component = baseComponent({ ...base, componentType, title: text(metricLabels?.[bindingKey]), blockId, unit, meta });
  component.content.mainField = selected.semantic;
  return { status: MIGRATION_STATUS.MIGRATED, component };
}

function makeTrendComponent(input) {
  const { entries, bindingKey, metricLabels, bind, blockId, meta, base } = input;
  const metrics = entries.filter(item => !DATE_FIELDS.has(item.semantic) && !IDENTITY_FIELDS.has(item.semantic));
  if (!metrics.length) return { status: MIGRATION_STATUS.MISSING_FIELDS, reasons: ['缺少趋势指标字段'] };
  const series = [];
  for (const item of metrics) {
    const unit = unitFor(bind, item.semantic, bind.fields);
    if (!unit) return { status: MIGRATION_STATUS.MISSING_FIELDS, reasons: [`趋势字段 ${item.semantic} 缺少原始单位`] };
    series.push({
      seriesKey: safeToken(item.semantic, `series-${series.length + 1}`),
      field: item.field,
      label: labelFor(bindingKey, item.field, metricLabels, bind, item.semantic),
      unit
    });
  }
  const component = baseComponent({ ...base, componentType: 'TREND', title: text(metricLabels?.[bindingKey]), blockId, unit: series[0].unit, meta });
  component.format.displayUnit = 'AUTO';
  component.content.series = series;
  return { status: MIGRATION_STATUS.MIGRATED, component };
}

function makeCompositionComponent(input) {
  const { entries, bindingKey, metricLabels, bind, blockId, meta, base } = input;
  const byKey = new Map(entries.map(item => [item.semantic.toLowerCase(), item]));
  const corporate = byKey.get('corporate') || byKey.get('corp');
  const retail = byKey.get('retail');
  if (!corporate || !retail) {
    return { status: MIGRATION_STATUS.NEEDS_CONFIRMATION, reasons: ['旧结构未同时声明 corporate/retail 字段'] };
  }
  const corporateUnit = unitFor(bind, corporate.semantic, bind.fields);
  const retailUnit = unitFor(bind, retail.semantic, bind.fields);
  if (!corporateUnit || !retailUnit) return { status: MIGRATION_STATUS.MISSING_FIELDS, reasons: ['结构字段缺少原始单位'] };
  if (corporateUnit !== retailUnit) return { status: MIGRATION_STATUS.NEEDS_CONFIRMATION, reasons: ['对公与零售字段单位不一致'] };
  const total = byKey.get('total');
  const component = baseComponent({ ...base, componentType: 'COMPOSITION_TABS', title: text(metricLabels?.[bindingKey]), blockId, unit: corporateUnit, meta });
  component.content.tabs = [{
    tabKey: 'business-structure',
    label: text(metricLabels?.[bindingKey]) || '业务结构',
    corporateField: corporate.field,
    retailField: retail.field,
    totalField: total?.field || '',
    unit: corporateUnit
  }];
  return { status: MIGRATION_STATUS.MIGRATED, component };
}

function makeRankingComponent(input) {
  const { entries, bindingKey, metricLabels, bind, blockId, meta, base } = input;
  const declaredDirection = text(bind?.direction || bind?.sortDirection || bind?.sort_direction).toUpperCase();
  // 历史 RANK_LIST 展示层始终按数值降序（panoramaViewModel.sortRankingRows）。
  // 因而真实旧包缺少 direction 时仍有确定语义；其他排名结构继续要求人工确认。
  const resolvedDirection = declaredDirection || (input.legacyRankList ? 'DESC' : '');
  const metrics = entries.filter(item => !IDENTITY_FIELDS.has(item.semantic));
  if (!metrics.length) return { status: MIGRATION_STATUS.MISSING_FIELDS, reasons: ['缺少排名指标字段'] };
  const rankingMetrics = [];
  let sourceUnit = null;
  for (const item of metrics) {
    const unit = unitFor(bind, item.semantic, bind.fields);
    if (!unit) return { status: MIGRATION_STATUS.MISSING_FIELDS, reasons: [`排名字段 ${item.semantic} 缺少原始单位`] };
    if (!sourceUnit) sourceUnit = unit;
    const modelUnit = ['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION'].includes(unit)
      ? 'HUNDRED_MILLION' : unit;
    rankingMetrics.push({
      metricKey: safeToken(item.semantic, `metric-${rankingMetrics.length + 1}`),
      // 经营大屏适配器把 ranking.value 归一为统一模型的 deposit；其余排名语义
      // 保持原键。展示协议必须消费统一模型字段，不能写数据库物理列名或请求别名。
      field: item.semantic === 'value' ? 'deposit' : item.semantic,
      label: labelFor(bindingKey, item.field, metricLabels, bind, item.semantic),
      unit: modelUnit,
      direction: resolvedDirection || 'DESC'
    });
  }
  const component = baseComponent({
    ...base, componentType: 'RANKING', title: text(metricLabels?.[bindingKey]), blockId, unit: sourceUnit, meta
  });
  component.format.displayUnit = rankingMetrics[0].unit;
  component.content.rankingMetrics = rankingMetrics;
  return {
    status: resolvedDirection
      ? MIGRATION_STATUS.MIGRATED : MIGRATION_STATUS.NEEDS_CONFIRMATION,
    reasons: resolvedDirection
      ? [] : ['旧排名未声明排序方向，请确认 DESC 是否符合原配置'],
    component
  };
}

function makeMapComponent(input) {
  const result = makeMetricComponent(input, 'MAP');
  if (result.component) result.component.format.displayUnit = 'AUTO';
  return result;
}

function makeDetailComponent(input) {
  const { entries, bindingKey, metricLabels, bind, blockId, meta, base } = input;
  if (!entries.length) return { status: MIGRATION_STATUS.MISSING_FIELDS, reasons: ['缺少明细列字段'] };
  const columns = [];
  let sourceUnit = null;
  for (const item of entries) {
    // 维度列没有原始数值单位，使用 AUTO 仅表示列按原始标量展示；
    // 至少一个指标列仍必须携带真实单位，作为 dataRef 的来源单位。
    const unit = IDENTITY_FIELDS.has(item.semantic) ? 'AUTO' : unitFor(bind, item.semantic, bind.fields);
    if (!unit) return { status: MIGRATION_STATUS.MISSING_FIELDS, reasons: [`明细字段 ${item.semantic} 缺少原始单位`] };
    if (unit !== 'AUTO' && !sourceUnit) sourceUnit = unit;
    columns.push({
      columnKey: safeToken(item.semantic, `column-${columns.length + 1}`),
      field: item.field,
      label: labelFor(bindingKey, item.field, metricLabels, bind, item.semantic),
      unit,
      visible: true
    });
  }
  if (!sourceUnit) return { status: MIGRATION_STATUS.MISSING_FIELDS, reasons: ['明细表缺少可作为来源单位的指标字段'] };
  const component = baseComponent({ ...base, componentType: 'DETAIL_TABLE', title: text(metricLabels?.[bindingKey]), blockId, unit: sourceUnit, meta });
  component.format.displayUnit = 'AUTO';
  component.content.columns = columns;
  return { status: MIGRATION_STATUS.MIGRATED, component };
}

function convertComponent(entry, context) {
  const component = entry.component;
  const bindingKey = bindingKeyOf(component);
  const blockId = positiveId(component?.blockId ?? component?.block_id);
  const baseEntry = {
    sourceIndex: entry.path.at(-1),
    sourcePath: entry.path.join('.'),
    sourceId: text(component?.id || component?.componentId),
    bindingKey,
    blockId,
    innerType: innerTypeOf(component),
    // 未识别项必须可回看，不能只留下一个错误字符串后丢弃旧节点。
    legacyComponent: deepClone(component)
  };
  if (!component || component.component !== 'ChartWidget') {
    return { ...baseEntry, status: MIGRATION_STATUS.UNRESOLVED, reasons: ['不是可识别的 ChartWidget 组件'] };
  }
  if (!bindingKey) return { ...baseEntry, status: MIGRATION_STATUS.MISSING_FIELDS, reasons: ['缺少 propValue.bindingKey'] };
  const rule = resolveRule(bindingKey);
  if (!rule) return { ...baseEntry, status: MIGRATION_STATUS.UNRESOLVED, reasons: [`bindingKey 未登记: ${bindingKey}`] };
  if (!blockId) return { ...baseEntry, status: MIGRATION_STATUS.MISSING_FIELDS, reasons: ['缺少有效 blockId'] };
  const snapshot = snapshotOf(context.bindSnapshots, blockId);
  if (snapshot?.__invalidBindJson) {
    return { ...baseEntry, status: MIGRATION_STATUS.MISSING_FIELDS,
      reasons: [snapshot.invalidReason || `blocks[${blockId}].bindJson 不是有效对象`] };
  }
  const bind = bindOf(snapshot);
  if (!snapshot || !bind) return { ...baseEntry, status: MIGRATION_STATUS.MISSING_FIELDS, reasons: ['缺少 bindSnapshots[blockId].bind'] };
  const fields = fieldsOf(bind);
  const units = unitsOf(bind);
  if (!fields || !Object.keys(fields).length) return { ...baseEntry, status: MIGRATION_STATUS.MISSING_FIELDS, reasons: ['缺少 bind.fields'] };
  const entries = fieldEntries(fields);
  if (!entries.length) return { ...baseEntry, status: MIGRATION_STATUS.MISSING_FIELDS, reasons: ['bind.fields 未声明有效字段编码'] };
  // 对公/零售真实旧包的 branches 只含机构编码与名称，没有任何数值指标或单位。
  // 新协议由后端 institutionRules + panoramaInstitutions 提供同一机构目录，故将该旧槽位
  // 明确吸收到机构规则中；不能为满足 dataRef 校验伪造 YUAN/COUNT 等原始单位。
  if (bindingKey === 'branches' && entries.every(item => IDENTITY_FIELDS.has(item.semantic))) {
    return {
      ...baseEntry,
      status: MIGRATION_STATUS.MIGRATED,
      snapshot: deepClone(snapshot),
      componentType: 'DETAIL_TABLE',
      component: null,
      absorbedBy: 'institutionRules',
      reasons: ['仅含机构身份字段，已由新协议机构规则与机构目录接管']
    };
  }
  if (!units || !Object.keys(units).length) return { ...baseEntry, status: MIGRATION_STATUS.MISSING_FIELDS, reasons: ['缺少 bind.units'] };
  const meta = sourceMeta(snapshot, bind);
  const encodedType = structureType(component, rule, snapshot);
  const expectedType = rule.type;
  const resolvedInnerType = innerTypeOf(component) || text(snapshot.componentType).toUpperCase();
  baseEntry.innerType = resolvedInnerType;
  if (resolvedInnerType && !encodedType) {
    return { ...baseEntry, status: MIGRATION_STATUS.NEEDS_CONFIRMATION, reasons: [`旧组件结构未登记: ${resolvedInnerType}`] };
  }
  const base = {
    componentId: stableComponentId(bindingKey, blockId, component.id, baseEntry.sourceIndex, context.usedIds),
    order: context.orders[LAYOUT_BY_TYPE[expectedType]] || 0,
    visible: component.visible !== false
  };
  context.usedIds.add(base.componentId);
  context.orders[LAYOUT_BY_TYPE[expectedType]] = base.order + 1;
  const common = {
    ...baseEntry,
    snapshot: deepClone(snapshot),
    sourceKind: meta.sourceKind,
    componentType: expectedType
  };
  if (encodedType && encodedType !== expectedType) {
    return { ...common, status: MIGRATION_STATUS.NEEDS_CONFIRMATION, reasons: [`旧组件结构 ${resolvedInnerType} 与 bindingKey 类型不一致`] };
  }
  let converted;
  const input = {
    entries, bindingKey, metricLabels: context.metricLabels, bind, blockId, meta, base,
    legacyRankList: resolvedInnerType === 'RANK_LIST'
  };
  if (expectedType === 'METRIC_CARD') converted = makeMetricComponent(input, 'METRIC_CARD');
  else if (expectedType === 'COMPLETION') converted = makeMetricComponent(input, 'COMPLETION');
  else if (expectedType === 'TREND') converted = makeTrendComponent(input);
  else if (expectedType === 'COMPOSITION_TABS') converted = makeCompositionComponent(input);
  else if (expectedType === 'RANKING') converted = makeRankingComponent(input);
  else if (expectedType === 'MAP') converted = makeMapComponent(input);
  else converted = makeDetailComponent(input);
  const result = { ...common, ...converted };
  if (meta.sourceKind === 'CUSTOM_SQL' && result.status === MIGRATION_STATUS.MIGRATED) {
    result.status = MIGRATION_STATUS.NEEDS_CONFIRMATION;
    result.reasons = ['旧来源为 CUSTOM_SQL，标准展示分支禁止自动迁入'];
  }
  return result;
}

function emptyPresentation(options = {}) {
  return {
    ...(options.type ? { type: options.type } : { type: 'CODE' }),
    ...(options.template ? { template: options.template } : { template: 'branch-overview-v1' }),
    institutionRules: deepClone(options.institutionRules || DEFAULT_INSTITUTION_RULES),
    displaySchemaVersion: 1,
    display: { components: [] }
  };
}

function appendDerivedMap(presentation, context) {
  const components = presentation.display.components;
  if (components.some(component => component.componentType === 'MAP')) return;
  const ranking = components.find(component => component.componentType === 'RANKING');
  const metric = ranking?.content?.rankingMetrics?.[0];
  const reference = ranking?.dataRefs?.[0];
  if (!metric || !reference?.blockId || !reference.unit) return;
  const componentId = stableComponentId('map', reference.blockId, '', components.length, context.usedIds);
  context.usedIds.add(componentId);
  const component = baseComponent({
    componentId,
    componentType: 'MAP',
    order: context.orders.CENTER || 0,
    visible: true,
    title: '',
    blockId: reference.blockId,
    unit: reference.unit,
    meta: {
      metricCode: reference.metricCode,
      metricName: reference.metricName || metric.label,
      dimension: reference.dimension || 'ORG',
      formula: reference.formula
    }
  });
  component.format.displayUnit = metric.unit;
  component.content.mainField = metric.metricKey;
  components.push(component);
  context.orders.CENTER = component.order + 1;
}

function alreadyMigratedResult(source, presentation, options = {}) {
  const components = Array.isArray(presentation?.display?.components) ? presentation.display.components : [];
  const entries = components.map((component, index) => ({
    status: MIGRATION_STATUS.MIGRATED,
    sourceIndex: index,
    sourcePath: String(index),
    componentId: component.componentId,
    componentType: component.componentType,
    blockId: positiveId(component.dataRefs?.[0]?.blockId),
    component: deepClone(component),
    reasons: ['输入已经是 displaySchemaVersion=1，保持原样']
  }));
  return buildResult(source, presentation, entries, {
    alreadyMigrated: true,
    options
  });
}

function buildResult(source, presentation, entries, { alreadyMigrated = false } = {}) {
  const groups = { migrated: [], unresolved: [], missingFields: [], needsConfirmation: [] };
  for (const entry of entries) groups[STATUS_KEYS[entry.status]]?.push(entry);
  const unmappedItems = entries.filter(entry => entry.status !== MIGRATION_STATUS.MIGRATED).map(entry => deepClone(entry));
  return {
    presentation: deepClone(presentation),
    entries: deepClone(entries),
    items: deepClone(entries),
    migrated: deepClone(groups.migrated),
    unresolved: deepClone(groups.unresolved),
    missingFields: deepClone(groups.missingFields),
    missing: deepClone(groups.missingFields),
    needsConfirmation: deepClone(groups.needsConfirmation),
    pendingConfirmation: deepClone(groups.needsConfirmation),
    confirmation: deepClone(groups.needsConfirmation),
    unmappedItems,
    summary: {
      migrated: groups.migrated.length,
      unresolved: groups.unresolved.length,
      missingFields: groups.missingFields.length,
      needsConfirmation: groups.needsConfirmation.length
    },
    canDeclareLossless: groups.unresolved.length === 0
      && groups.missingFields.length === 0
      && groups.needsConfirmation.length === 0,
    lossless: groups.unresolved.length === 0
      && groups.missingFields.length === 0
      && groups.needsConfirmation.length === 0,
    idempotent: alreadyMigrated,
    alreadyMigrated,
    rollbackSource: deepClone(source),
    originalSource: deepClone(source),
    legacySnapshot: deepClone(source)
  };
}

/**
 * 构造只读迁移预览。此函数没有网络/保存副作用，也不会修改传入的旧发布包。
 */
export function previewLegacyMigration(source, options = {}) {
  const extracted = extractLegacyPackage(source);
  const existingPresentation = extracted.presentation;
  if (existingPresentation) return alreadyMigratedResult(source, existingPresentation, options);
  const presentation = {
    ...emptyPresentation({
      type: options.type || extracted.canvasStyle?.presentation?.type
        || extracted.root?.canvasStyle?.presentation?.type || extracted.root?.type,
      template: options.template || extracted.canvasStyle?.presentation?.template
        || extracted.root?.canvasStyle?.presentation?.template || extracted.root?.template,
      institutionRules: options.institutionRules || extracted.institutionRules || DEFAULT_INSTITUTION_RULES
    }),
    ...(isObject(extracted.canvasStyle?.presentation) ? {
      type: extracted.canvasStyle.presentation.type || options.type || 'CODE',
      template: extracted.canvasStyle.presentation.template || options.template || 'branch-overview-v1'
    } : {})
  };
  const context = { bindSnapshots: extracted.bindSnapshots, metricLabels: extracted.metricLabels, usedIds: new Set(), orders: {} };
  const entries = extracted.componentShapeIssue
    ? [{ status: MIGRATION_STATUS.MISSING_FIELDS, sourceIndex: -1, sourcePath: '', bindingKey: '', blockId: null,
      reasons: [extracted.componentShapeIssue], legacyComponent: null }]
    : flattenComponents(extracted.components).map(entry => convertComponent(entry, context));
  presentation.display.components = entries
    .filter(entry => entry.status === MIGRATION_STATUS.MIGRATED && entry.component)
    .map(entry => deepClone(entry.component));
  // 三个历史经营屏的地图是固定页面模块，不存在独立 ChartWidget。整页配置化后
  // 必须把已确定的机构排名主指标复用为 MAP，才能保留地图能力且不发明新的数据源。
  appendDerivedMap(presentation, context);
  return buildResult(source, presentation, entries);
}

export const buildLegacyMigrationPreview = previewLegacyMigration;
export const buildMigrationPreview = previewLegacyMigration;
export const convertLegacyPresentation = previewLegacyMigration;
export const convertLegacyConfig = previewLegacyMigration;
export const migrateLegacyConfig = previewLegacyMigration;

/**
 * 将已预览的组件树放入当前编辑器会话。只更新本地 session，既不保存也不发布。
 * 默认允许生成“部分草稿”，但调用方可用 requireLossless 阻止不完整迁移。
 */
export function applyLegacyMigrationToEditorDraft(session, previewOrSource, options = {}) {
  if (!isObject(session) || !isObject(session.presentation)) throw new Error('需要当前展示编辑会话');
  const preview = previewOrSource?.presentation && previewOrSource?.summary
    ? previewOrSource : previewLegacyMigration(previewOrSource, {
      type: session.presentation.type,
      template: session.presentation.template
    });
  if (options.requireLossless && !preview.canDeclareLossless) {
    throw new Error('旧配置存在未识别或待确认项，不能声明无损迁移');
  }
  const next = createPresentationEditorSession(preview.presentation, {
    type: preview.presentation.type,
    template: preview.presentation.template
  });
  next.loadedSnapshot = deepClone(session.loadedSnapshot || session.presentation);
  next.selectedComponentId = null;
  next.reviewRequiredComponentIds = [];
  next.reviewRequired = false;
  next.dirty = true;
  if (JSON.stringify(next.presentation) === JSON.stringify(next.loadedSnapshot)) next.dirty = false;
  return next;
}

export const applyMigrationToEditorDraft = applyLegacyMigrationToEditorDraft;
export const applyLegacyMigration = applyLegacyMigrationToEditorDraft;
export const applyMigrationToDraft = applyLegacyMigrationToEditorDraft;

/** 回退只恢复本次加载的本地快照；线上发布回退仍由既有 CAS/发布日志入口负责。 */
export function rollbackLegacyMigration(session) {
  return cancelEditorSession(session);
}

export const rollbackMigration = rollbackLegacyMigration;

export function migrationStatusLabel(status) {
  return MIGRATION_STATUS_LABELS[status] || String(status || '');
}

export function knownLegacyBindingKeys() {
  return RULES.flatMap(rule => rule.keys);
}
