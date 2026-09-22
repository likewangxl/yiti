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

// 这是旧 bindingKey 的编码清单，不是标题或模糊别名匹配。新增旧槽位必须先进入清单和测试。
const RULES = Object.freeze([
  {
    type: 'METRIC_CARD',
    keys: [
      'deposit', 'loan', 'revenue', 'customers', 'customerCount', 'aum', 'nplRate',
      'corpDeposit', 'corpLoan', 'corpRevenue', 'corpCustomers', 'corpNplRate',
      'retailAum', 'retailDeposit', 'retailLoan', 'retailRevenue', 'retailCustomers',
      'retailNplRate', 'branchDeposit', 'branchLoan', 'branchRevenue', 'branchCustomers'
    ]
  },
  {
    type: 'COMPLETION',
    keys: [
      'completion', 'completionRate', 'rate', 'target', 'targets',
      'corpTargets', 'retailTargets', 'branchTargets', 'corpCompletion', 'retailCompletion'
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
    keys: ['ranking', 'corpRanking', 'retailRanking', 'branchRanking', 'citySummary', 'institutionRanking']
  },
  {
    type: 'MAP',
    keys: ['map', 'cityMap', 'provinceMap', 'institutionMap', 'orgMap']
  },
  {
    type: 'DETAIL_TABLE',
    keys: ['attention', 'details', 'detail', 'branches', 'branchList', 'projects', 'team', 'teams', 'corpAttention']
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
  MAP: 'MAP',
  MAP_CENTER: 'MAP',
  TABLE_LIST: 'DETAIL_TABLE',
  DETAIL_TABLE: 'DETAIL_TABLE'
});

const DATE_FIELDS = new Set(['date', 'dataDate', 'data_date', 'period', 'month', 'statDate', 'stat_date']);
const IDENTITY_FIELDS = new Set(['orgCode', 'org_code', 'orgName', 'org_name', 'name', 'label', 'date', 'dataDate', 'data_date', 'period', 'month']);
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
  return {
    root,
    source: parsedSource,
    canvasStyle: deepClone(style),
    components,
    bindSnapshots: snapshots,
    metricLabels,
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
  const sourceKind = text(bind?.sourceKind || snapshot?.sourceKind || bind?.source_kind || snapshot?.source_kind).toUpperCase();
  return {
    sourceKind,
    metricCode: text(bind?.metricCode || bind?.metric_code || snapshot?.metricCode || snapshot?.metric_code),
    metricName: text(bind?.metricName || bind?.metric_name || snapshot?.metricName || snapshot?.metric_name),
    dimension: text(bind?.dimension || snapshot?.dimension).toUpperCase() || 'ORG',
    formula: text(bind?.formula || snapshot?.formula)
  };
}

function resolveRule(bindingKey) {
  return RULE_BY_KEY.get(text(bindingKey).toLowerCase()) || null;
}

function structureType(component, rule) {
  const encoded = INNER_TYPE_TO_COMPONENT[innerTypeOf(component)] || null;
  if (!encoded || !rule) return encoded;
  // TABLE_LIST 在旧版同时承载排名和明细，bindingKey 是其正式身份编码，允许这一个明确例外。
  if (innerTypeOf(component) === 'TABLE_LIST' && ['RANKING', 'DETAIL_TABLE'].includes(rule.type)) return rule.type;
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
  const metrics = entries.filter(item => !IDENTITY_FIELDS.has(item.semantic));
  if (!metrics.length) return { status: MIGRATION_STATUS.MISSING_FIELDS, reasons: ['缺少排名指标字段'] };
  const rankingMetrics = [];
  for (const item of metrics) {
    const unit = unitFor(bind, item.semantic, bind.fields);
    if (!unit) return { status: MIGRATION_STATUS.MISSING_FIELDS, reasons: [`排名字段 ${item.semantic} 缺少原始单位`] };
    rankingMetrics.push({
      metricKey: safeToken(item.semantic, `metric-${rankingMetrics.length + 1}`),
      field: item.field,
      label: labelFor(bindingKey, item.field, metricLabels, bind, item.semantic),
      unit,
      direction: text(bind?.direction || bind?.sortDirection || bind?.sort_direction).toUpperCase() || 'DESC'
    });
  }
  const component = baseComponent({ ...base, componentType: 'RANKING', title: text(metricLabels?.[bindingKey]), blockId, unit: rankingMetrics[0].unit, meta });
  component.content.rankingMetrics = rankingMetrics;
  return {
    status: text(bind?.direction || bind?.sortDirection || bind?.sort_direction)
      ? MIGRATION_STATUS.MIGRATED : MIGRATION_STATUS.NEEDS_CONFIRMATION,
    reasons: text(bind?.direction || bind?.sortDirection || bind?.sort_direction)
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
  for (const item of entries) {
    const unit = unitFor(bind, item.semantic, bind.fields);
    if (!unit) return { status: MIGRATION_STATUS.MISSING_FIELDS, reasons: [`明细字段 ${item.semantic} 缺少原始单位`] };
    columns.push({
      columnKey: safeToken(item.semantic, `column-${columns.length + 1}`),
      field: item.field,
      label: labelFor(bindingKey, item.field, metricLabels, bind, item.semantic),
      unit,
      visible: true
    });
  }
  const component = baseComponent({ ...base, componentType: 'DETAIL_TABLE', title: text(metricLabels?.[bindingKey]), blockId, unit: columns[0].unit, meta });
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
  const bind = bindOf(snapshot);
  if (!snapshot || !bind) return { ...baseEntry, status: MIGRATION_STATUS.MISSING_FIELDS, reasons: ['缺少 bindSnapshots[blockId].bind'] };
  const fields = fieldsOf(bind);
  const units = unitsOf(bind);
  if (!fields || !Object.keys(fields).length) return { ...baseEntry, status: MIGRATION_STATUS.MISSING_FIELDS, reasons: ['缺少 bind.fields'] };
  if (!units || !Object.keys(units).length) return { ...baseEntry, status: MIGRATION_STATUS.MISSING_FIELDS, reasons: ['缺少 bind.units'] };
  if (!fieldEntries(fields).length) return { ...baseEntry, status: MIGRATION_STATUS.MISSING_FIELDS, reasons: ['bind.fields 未声明有效字段编码'] };
  const meta = sourceMeta(snapshot, bind);
  const encodedType = structureType(component, rule);
  const expectedType = rule.type;
  if (innerTypeOf(component) && !encodedType) {
    return { ...baseEntry, status: MIGRATION_STATUS.NEEDS_CONFIRMATION, reasons: [`旧组件结构未登记: ${innerTypeOf(component)}`] };
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
    return { ...common, status: MIGRATION_STATUS.NEEDS_CONFIRMATION, reasons: [`旧组件结构 ${innerTypeOf(component)} 与 bindingKey 类型不一致`] };
  }
  let converted;
  const input = { entries: fieldEntries(fields), bindingKey, metricLabels: context.metricLabels, bind, blockId, meta, base };
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
    displaySchemaVersion: 1,
    display: { components: [] }
  };
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
        || extracted.root?.canvasStyle?.presentation?.template || extracted.root?.template
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
