import { buildBranchOverviewPresentation } from './branchOverviewPresentation.js';

const TEMPLATE = 'branch-overview-v1';
const AMOUNT_COMPONENT_KEYS = Object.freeze([
  ['deposit', 'total'], ['corpDeposit', 'corporate'], ['retailDeposit', 'retail']
]);
const LOAN_COMPONENT_KEYS = Object.freeze([
  ['loan', 'total'], ['corpLoan', 'corporate'], ['retailLoan', 'retail']
]);
const COMPLETION_RING_IDS = new Set([
  'business-retail-deposit-rate', 'business-retail-loan-rate',
  'business-corp-deposit-rate', 'business-corp-loan-rate'
]);
const CITY_SUMMARY_MODEL_FIELDS = Object.freeze([
  'blockResults', 'displayValues', 'metricValues', 'blocks', 'results', 'composition'
]);

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function clone(value) {
  if (Array.isArray(value)) return value.map(clone);
  if (isObject(value)) return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, clone(item)]));
  return value;
}

function presentationOf(source) {
  if (!isObject(source)) return {};
  if (isObject(source.displayPresentation)) return presentationOf(source.displayPresentation);
  if (source.displaySchemaVersion !== undefined || source.display) return source;
  if (isObject(source.presentation)) return presentationOf(source.presentation);
  if (isObject(source.canvasStyle?.presentation)) return presentationOf(source.canvasStyle.presentation);
  if (isObject(source.renderPackage?.canvasStyle?.presentation)) return presentationOf(source.renderPackage.canvasStyle.presentation);
  return {};
}

export function isCityOperatingHeaderEnabled(sourcePresentation) {
  const presentation = presentationOf(sourcePresentation);
  return presentation.displaySchemaVersion === 1 && presentation.template === TEMPLATE;
}

function cityDate(summary) {
  return text(summary?.dataDate ?? summary?.sourceDate ?? summary?.date);
}

function kpiByKey(kpis) {
  return new Map(kpis.filter(isObject).map(item => [text(item.key), item]));
}

function adaptCompositionValue(kpis, summary) {
  const byKey = kpiByKey(kpis);
  const summaryDate = cityDate(summary);
  const compose = (pairs) => {
    const values = {};
    const unitByField = {};
    const dates = new Set();
    let found = false;
    for (const [kpiKey, field] of pairs) {
      const item = byKey.get(kpiKey);
      if (!item) continue;
      found = true;
      values[field] = item.value;
      if (text(item.unit)) unitByField[field] = item.unit;
      const date = text(item.dataDate ?? item.date ?? summaryDate);
      if (date) dates.add(date);
    }
    if (!found) return null;
    if (dates.size > 1) return null;
    if (Object.keys(unitByField).length) values.unitByField = unitByField;
    if (dates.size === 1) values.dataDate = [...dates][0];
    return values;
  };
  return {
    deposit: compose(AMOUNT_COMPONENT_KEYS),
    loan: compose(LOAN_COMPONENT_KEYS)
  };
}

function mergeDisplayValues(summary, kpis) {
  const explicit = isObject(summary?.displayValues) ? clone(summary.displayValues) : {};
  const adapted = adaptCompositionValue(kpis, summary);
  for (const key of ['deposit', 'loan']) {
    if (!adapted[key]) continue;
    const existing = isObject(explicit[key]) ? explicit[key] : {};
    explicit[key] = {
      ...adapted[key],
      ...existing,
      unitByField: { ...(adapted[key].unitByField || {}), ...(existing.unitByField || {}) }
    };
  }
  return explicit;
}

function citySummaryModel(summary) {
  const source = isObject(summary) ? summary : {};
  const kpis = Array.isArray(source.kpis) ? source.kpis.filter(isObject).map(clone) : [];
  const model = {
    dataDate: cityDate(source),
    kpis,
    displayValues: mergeDisplayValues(source, kpis)
  };
  for (const field of CITY_SUMMARY_MODEL_FIELDS) {
    if (field === 'displayValues') continue;
    if (field === 'composition' && Array.isArray(source[field])) model[field] = clone(source[field]);
    else if (field !== 'composition' && isObject(source[field])) model[field] = clone(source[field]);
  }
  return model;
}

function headerPresentation() {
  const fallback = buildBranchOverviewPresentation(null);
  const components = (fallback.display?.components || [])
    .filter(component => component.layoutRegion === 'HEADER'
      || (component.layoutRegion === 'LEFT' && component.componentType === 'COMPOSITION_TABS'))
    .map(component => ({
      ...clone(component),
      ...(COMPLETION_RING_IDS.has(component.componentId) ? { componentType: 'METRIC_CARD' } : {})
    }));
  return {
    ...fallback,
    display: { ...fallback.display, components }
  };
}

/** 构造城市顶部专用模型；输入只能是当前 citySummary，禁止混入父层模型。 */
export function buildCityOperatingHeaderModel(sourcePresentation, citySummary) {
  return {
    enabled: isCityOperatingHeaderEnabled(sourcePresentation),
    presentation: headerPresentation(),
    model: citySummaryModel(citySummary)
  };
}

export { presentationOf };
