import { resolveComponentTitle } from '../contract/displayContract';

const DISPLAY_COMPONENT_TYPES = new Set(['METRIC_CARD', 'COMPLETION']);
const REGION_ORDER = Object.freeze({ HEADER: 0, LEFT: 1, CENTER: 2, RIGHT: 3, BOTTOM: 4, OVERLAY: 5 });
const UNIT_LABELS = Object.freeze({
  AUTO: '', YUAN: '元', TEN_THOUSAND: '万元', HUNDRED_MILLION: '亿元',
  COUNT: '个', TEN_THOUSAND_COUNT: '万户', PERCENT: '%', RATIO: '%',
  元: '元', 万元: '万元', 亿元: '亿元', 个: '个', 户: '户', 万户: '万户', '%': '%'
});
const UNIT_ALIASES = Object.freeze({
  元: 'YUAN', 人民币元: 'YUAN', CNY: 'YUAN', RMB: 'YUAN', YUAN: 'YUAN',
  万元: 'TEN_THOUSAND', TEN_THOUSAND: 'TEN_THOUSAND',
  亿元: 'HUNDRED_MILLION', HUNDRED_MILLION: 'HUNDRED_MILLION',
  个: 'COUNT', 户: 'COUNT', 人: 'COUNT', COUNT: 'COUNT',
  万户: 'TEN_THOUSAND_COUNT', TEN_THOUSAND_COUNT: 'TEN_THOUSAND_COUNT',
  百分比: 'PERCENT', 百分数: 'PERCENT', '%': 'PERCENT', PCT: 'PERCENT', PERCENT: 'PERCENT',
  比例: 'RATIO', RATIO: 'RATIO'
});

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function finite(value) {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : null;
}

function token(value) {
  return text(value).toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '');
}

function canonicalUnit(value) {
  const raw = text(value);
  return UNIT_ALIASES[raw] || UNIT_ALIASES[raw.toUpperCase()] || null;
}

function unitLabel(value) {
  const raw = text(value);
  return UNIT_LABELS[raw] || UNIT_LABELS[raw.toUpperCase()] || raw;
}

export function displayUnitLabel(value) {
  return unitLabel(value);
}

function numberFormat(value, decimals, thousandsSeparator) {
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
    useGrouping: thousandsSeparator
  }).format(value);
}

/** 仅格式化已确定数值；不在展示层计算业务指标。 */
export function formatDisplayMetric(value, format = {}, sourceUnit = '') {
  const number = finite(value);
  const emptyText = Object.prototype.hasOwnProperty.call(format, 'emptyText') ? text(format.emptyText) : '—';
  if (number === null) return { text: emptyText, value: null, unit: unitLabel(format.displayUnit || sourceUnit), raw: value };

  const decimals = Number.isInteger(format.decimals) && format.decimals >= 0 && format.decimals <= 8 ? format.decimals : 2;
  const thousandsSeparator = format.thousandsSeparator !== false;
  const targetUnit = text(format.displayUnit) && text(format.displayUnit) !== 'AUTO' ? canonicalUnit(format.displayUnit) : canonicalUnit(sourceUnit);
  const originalUnit = canonicalUnit(sourceUnit);
  let displayValue = number;
  if (targetUnit && originalUnit && targetUnit !== originalUnit) {
    if (originalUnit === 'RATIO' && targetUnit === 'PERCENT') displayValue = number * 100;
    else if (originalUnit === 'PERCENT' && targetUnit === 'RATIO') displayValue = number;
    else if (['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION'].includes(originalUnit)
      && ['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION'].includes(targetUnit)) {
      const yuan = number * ({ YUAN: 1, TEN_THOUSAND: 1e4, HUNDRED_MILLION: 1e8 }[originalUnit]);
      displayValue = yuan / ({ YUAN: 1, TEN_THOUSAND: 1e4, HUNDRED_MILLION: 1e8 }[targetUnit]);
    } else if (originalUnit === 'COUNT' && targetUnit === 'TEN_THOUSAND_COUNT') displayValue = number / 10000;
    else if (originalUnit === 'TEN_THOUSAND_COUNT' && targetUnit === 'COUNT') displayValue = number * 10000;
  }
  let output = numberFormat(displayValue, decimals, thousandsSeparator);
  const negativeStyle = text(format.negativeStyle).toUpperCase();
  if (displayValue < 0 && negativeStyle === 'PARENTHESIS') output = `(${numberFormat(Math.abs(displayValue), decimals, thousandsSeparator)})`;
  const unit = unitLabel(text(format.displayUnit) === 'AUTO' ? sourceUnit : format.displayUnit);
  return { text: `${output}${unit}`, value: displayValue, unit, raw: value };
}

function presentationFrom(source) {
  if (!isObject(source)) return null;
  if (source.displaySchemaVersion !== undefined || source.display) return source;
  if (isObject(source.canvasStyle?.presentation)) return source.canvasStyle.presentation;
  if (isObject(source.renderPackage?.canvasStyle?.presentation)) return source.renderPackage.canvasStyle.presentation;
  return null;
}

function sourceMaps(model = {}) {
  const maps = [model.blockResults, model.displayValues, model.metricValues, model.blocks, model.results];
  return maps.filter(isObject);
}

function lookupObject(map, ref, component, field) {
  if (!isObject(map)) return null;
  const keys = [ref?.blockId, String(ref?.blockId || ''), ref?.metricCode, ref?.metricName, component?.componentId]
    .map(text).filter(Boolean);
  for (const key of keys) {
    const value = map[key];
    if (value !== undefined) {
      if (isObject(value)) return value;
      return { [field || 'value']: value };
    }
  }
  return null;
}

function itemMatches(item, keys) {
  if (!isObject(item)) return false;
  const candidateKeys = [item.key, item.metricCode, item.metric_code, item.semantic, item.code, item.name, item.label]
    .map(token).filter(Boolean);
  return keys.some(key => candidateKeys.includes(token(key)));
}

function lookupArray(array, keys) {
  if (!Array.isArray(array)) return null;
  return array.find(item => itemMatches(item, keys)) || null;
}

function resolveSource(model, component, ref, field) {
  const keys = [ref?.metricCode, ref?.metricName, field, component?.componentId].map(text).filter(Boolean);
  for (const map of sourceMaps(model)) {
    const value = lookupObject(map, ref, component, field);
    if (value) return value;
  }
  const arrays = [model.kpis, model.targets, model.metrics, model.values, model.items];
  for (const array of arrays) {
    const value = lookupArray(array, keys);
    if (value) return value;
  }
  return null;
}

function sourceUnit(source, key, fallbackUnit = '') {
  const metadata = Array.isArray(source?.columnsMeta)
    ? source.columnsMeta.find(item => text(item?.col ?? item?.name ?? item?.key) === text(key)) : null;
  // amountScale is a display scaling hint, not a data unit. When metadata
  // names this field but has no unit, continue with unitByField/ref below.
  if (metadata && text(metadata.unit)) return metadata.unit;
  const fieldUnit = source?.unitByField?.[key];
  if (text(fieldUnit)) return fieldUnit;
  const sourceUnitValue = source?.unit || source?.unitCode || null;
  const hasFieldMetadata = Boolean(metadata) || Object.keys(source?.unitByField || {}).length > 0;
  if (hasFieldMetadata && text(fallbackUnit)) return fallbackUnit;
  if (text(sourceUnitValue)) {
    // Keep legacy KPI sources' source.unit precedence. A wide block that
    // exposes a field with an explicit ratio ref must not inherit a block
    // level amount unit when its field metadata is unavailable.
    const sourceKind = canonicalUnit(sourceUnitValue);
    const fallbackKind = canonicalUnit(fallbackUnit);
    const sourceIsRatio = ['RATIO', 'PERCENT'].includes(sourceKind);
    const fallbackIsRatio = ['RATIO', 'PERCENT'].includes(fallbackKind);
    const sourceIsAmount = ['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION'].includes(sourceKind);
    const fallbackIsAmount = ['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION'].includes(fallbackKind);
    if (sourceIsRatio !== fallbackIsRatio && (sourceIsAmount || fallbackIsAmount)) return fallbackUnit;
    return sourceUnitValue;
  }
  return text(fallbackUnit) ? fallbackUnit : null;
}

function explicitSourceUnit(source, key) {
  const metadata = Array.isArray(source?.columnsMeta)
    ? source.columnsMeta.find(item => text(item?.col ?? item?.name ?? item?.key) === text(key)) : null;
  if (metadata && text(metadata.unit)) return { present: true, unit: metadata.unit };
  const fieldUnit = source?.unitByField?.[key];
  return text(fieldUnit) ? { present: true, unit: fieldUnit } : { present: false, unit: null };
}

function sourceValue(source, field, componentType, fallbackUnit = '') {
  if (!source) return { present: false, value: null, unit: null, key: null };
  // `value` is the legacy single-value contract. Once a component names an
  // explicit field, that field is the complete data contract: falling back to
  // the block's generic value can show a different metric under the right
  // title (for example a missing corporate rate displaying a retail rate).
  const candidates = componentType === 'COMPLETION' && field === 'value'
    ? ['value', 'rate', 'completionRate', 'completion_rate'] : [field];
  const key = candidates.find(candidate => Object.prototype.hasOwnProperty.call(source, candidate));
  if (!key) return { present: false, value: null, unit: sourceUnit(source, field, fallbackUnit), key: null };
  return { present: true, value: source[key], unit: sourceUnit(source, key, fallbackUnit), key };
}

const BUSINESS_HEADER_COMPONENT = /^business-(?:retail|corp|revenue)-/;
const HISTORY_DATE_FIELDS = Object.freeze(['date', 'dataDate', 'data_date']);

function isBusinessHeaderComponent(component) {
  return text(component?.layoutRegion).toUpperCase() === 'HEADER'
    && BUSINESS_HEADER_COMPONENT.test(text(component?.componentId));
}

function parseDateKey(value) {
  const key = text(value);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(key)) return null;
  const [year, month, day] = key.split('-').map(Number);
  if (year < 1 || month < 1 || month > 12 || day < 1 || day > 31) return null;
  const date = new Date(0);
  date.setUTCFullYear(year, month - 1, day);
  date.setUTCHours(0, 0, 0, 0);
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return null;
  return { key, year, month, day };
}

function previousMonthEnd(dataDate) {
  const current = parseDateKey(dataDate);
  if (!current) return null;
  const date = new Date(0);
  date.setUTCFullYear(current.year, current.month - 1, 0);
  date.setUTCHours(0, 0, 0, 0);
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const day = String(date.getUTCDate()).padStart(2, '0');
  return `${String(year).padStart(4, '0')}-${month}-${day}`;
}

function rowDateKey(row) {
  if (!isObject(row)) return '';
  for (const key of HISTORY_DATE_FIELDS) {
    if (Object.prototype.hasOwnProperty.call(row, key) && text(row[key])) return text(row[key]);
  }
  return '';
}

function unavailableMonthDelta() {
  return { state: 'NO_VALUE', value: null, rawValue: null, unit: '', text: '较上月 暂无数据' };
}

function signedText(value, formattedText) {
  return value > 0 ? `+${formattedText}` : formattedText;
}

function formatMonthDelta(currentValue, previousValue, format, sourceUnit, ratio) {
  // A RATIO display unit is still rendered as a percentage, but its raw
  // value is fractional. Normalize both sides to PERCENT before subtracting
  // so the result is expressed in percentage points rather than 0.xx points.
  const comparisonFormat = ratio ? { ...format, displayUnit: 'PERCENT' } : format;
  const current = formatDisplayMetric(currentValue, comparisonFormat, sourceUnit);
  const previous = formatDisplayMetric(previousValue, comparisonFormat, sourceUnit);
  if (current.value === null || previous.value === null) return unavailableMonthDelta();
  const displayDelta = current.value - previous.value;
  if (!Number.isFinite(displayDelta)) return unavailableMonthDelta();
  const decimals = Number.isInteger(format?.decimals) && format.decimals >= 0 && format.decimals <= 8
    ? format.decimals : 2;
  const thousandsSeparator = format?.thousandsSeparator !== false;
  if (ratio) {
    const number = numberFormat(displayDelta, decimals, thousandsSeparator);
    return {
      state: 'READY', value: displayDelta, rawValue: finite(currentValue) - finite(previousValue), unit: '个百分点',
      text: `较上月 ${signedText(displayDelta, number)}个百分点`
    };
  }
  const formatted = formatDisplayMetric(
    finite(currentValue) - finite(previousValue),
    { ...format, negativeStyle: 'SIGNED' },
    sourceUnit
  );
  if (formatted.value === null) return unavailableMonthDelta();
  return {
    state: 'READY', value: formatted.value, rawValue: finite(currentValue) - finite(previousValue), unit: formatted.unit,
    text: `较上月 ${signedText(formatted.value, formatted.text)}`
  };
}

/**
 * 只从 blockResults 中选择一个同时含当前日和上月月末的历史结果。
 * 当前历史值必须与主卡值一致，避免把另一指标或另一批次的历史行误当作比较基准。
 */
function buildMonthDelta(model, component, ref, field, main, format) {
  const dataDate = text(model?.dataDate);
  const previousDate = previousMonthEnd(dataDate);
  if (!previousDate || finite(main?.value) === null || !field) return unavailableMonthDelta();
  const currentDate = dataDate;
  const blockResults = isObject(model?.blockResults) ? model.blockResults : {};
  const candidates = [];
  for (const [key, source] of Object.entries(blockResults)) {
    if (!isObject(source) || !Array.isArray(source.rows)) continue;
    const sourceId = text(source.blockId ?? key);
    if (text(ref?.blockId) && sourceId === text(ref.blockId)) continue;
    const currentRows = source.rows.filter(row => rowDateKey(row) === currentDate);
    const previousRows = source.rows.filter(row => rowDateKey(row) === previousDate);
    if (!currentRows.length || !previousRows.length) continue;
    if (!currentRows.some(row => Object.prototype.hasOwnProperty.call(row, field))
      || !previousRows.some(row => Object.prototype.hasOwnProperty.call(row, field))) continue;
    candidates.push({ source, currentRows, previousRows });
  }
  if (candidates.length !== 1) return unavailableMonthDelta();
  const [{ source: historicalSource, currentRows, previousRows }] = candidates;
  if (currentRows.length !== 1 || previousRows.length !== 1) return unavailableMonthDelta();
  const historicalCurrent = finite(currentRows[0][field]);
  const historicalPrevious = finite(previousRows[0][field]);
  const mainValue = finite(main.value);
  if (historicalCurrent === null || historicalPrevious === null || mainValue === null
    || historicalCurrent !== mainValue) return unavailableMonthDelta();
  const sourceUnit = main.unit || ref.unit || '';
  const currentUnit = canonicalUnit(sourceUnit);
  const historicalUnit = explicitSourceUnit(historicalSource, field);
  if (historicalUnit.present) {
    const normalizedHistoricalUnit = canonicalUnit(historicalUnit.unit);
    if (!currentUnit || !normalizedHistoricalUnit || currentUnit !== normalizedHistoricalUnit) return unavailableMonthDelta();
  }
  const canonicalDisplayUnit = canonicalUnit(format?.displayUnit);
  const ratio = currentUnit === 'RATIO' || currentUnit === 'PERCENT'
    || canonicalDisplayUnit === 'RATIO' || canonicalDisplayUnit === 'PERCENT';
  return formatMonthDelta(mainValue, historicalPrevious, format, sourceUnit, ratio);
}

function componentTitle(component, ref, source, templateTitle) {
  return resolveComponentTitle(component, {
    metricName: ref?.metricName || source?.label || source?.name,
    templateTitle
  });
}

function buildComponent(component, model, index, options) {
  const ref = Array.isArray(component?.dataRefs) ? component.dataRefs[0] || {} : {};
  const content = isObject(component?.content) ? component.content : {};
  const field = text(content.mainField) || 'value';
  const source = resolveSource(model, component, ref, field);
  const main = sourceValue(source, field, component.componentType, ref.unit);
  const format = isObject(component?.format) ? component.format : {};
  const sourceUnit = main.unit || ref.unit || '';
  const formatted = formatDisplayMetric(main.value, format, sourceUnit);
  const state = !source || !main.present ? 'NO_SOURCE' : main.value === null || finite(main.value) === null ? 'NO_VALUE' : 'READY';
  const subFields = (Array.isArray(content.subFields) ? content.subFields : []).map((entry, subIndex) => {
    const definition = typeof entry === 'string' ? { field: entry, label: entry } : (isObject(entry) ? entry : {});
    const subField = text(definition.field) || text(definition.key);
    const subValue = sourceValue(source, subField, 'METRIC_CARD', definition.unit || format.displayUnit || ref.unit);
    const subFormat = { ...format, displayUnit: definition.unit || format.displayUnit };
    const subFormatted = formatDisplayMetric(subValue.value, subFormat, subValue.unit || definition.unit || ref.unit || '');
    return { ...definition, key: definition.key || subField || `sub-${subIndex}`, field: subField, value: finite(subValue.value), text: subFormatted.text, unit: subFormatted.unit, state: !source || !subValue.present ? 'NO_SOURCE' : subValue.value === null ? 'NO_VALUE' : 'READY' };
  });
  const result = {
    componentId: text(component?.componentId) || `component-${index}`,
    componentType: component.componentType,
    layoutRegion: text(component?.layoutRegion) || 'CENTER',
    order: Number.isInteger(component?.order) ? component.order : index,
    visible: component.visible !== false,
    title: componentTitle(component, ref, source, options.templateTitle),
    subtitle: component?.text?.subtitle ?? '',
    description: component?.text?.description ?? '',
    metricCode: text(ref.metricCode),
    metricName: text(ref.metricName),
    blockId: ref.blockId,
    value: finite(formatted.value),
    rawValue: main.value,
    sourceUnit,
    unit: formatted.unit,
    text: formatted.text,
    state,
    subFields
  };
  if (isBusinessHeaderComponent(component)) {
    result.monthDelta = buildMonthDelta(model, component, ref, main.key || field, main, format);
  }
  if (component.componentType === 'COMPLETION') {
    result.progress = result.value === null ? null : Math.max(0, Math.min(100, result.value));
  }
  return result;
}

/**
 * 将 displaySchemaVersion=1 的卡片配置适配到已有 Dashboard 模型。
 * 该函数只读已有结果，不根据 actual/target 等字段新算业务口径。
 */
export function buildDisplayMetricsModel(sourcePresentation, model = {}, options = {}) {
  const presentation = presentationFrom(sourcePresentation);
  if (!presentation || presentation.displaySchemaVersion !== 1) return { enabled: false, components: [], issues: [] };
  const rawComponents = Array.isArray(presentation.display?.components) ? presentation.display.components : [];
  const components = rawComponents
    .map((component, index) => ({ component, index }))
    .filter(({ component }) => DISPLAY_COMPONENT_TYPES.has(component?.componentType) && component?.visible !== false)
    .sort((left, right) => (REGION_ORDER[text(left.component?.layoutRegion).toUpperCase()] ?? 99)
      - (REGION_ORDER[text(right.component?.layoutRegion).toUpperCase()] ?? 99)
      || ((Number.isInteger(left.component?.order) ? left.component.order : left.index)
        - (Number.isInteger(right.component?.order) ? right.component.order : right.index))
      || left.index - right.index)
    .map(({ component, index }) => buildComponent(component, isObject(model) ? model : {}, index, options));
  return {
    enabled: true,
    components,
    byRegion: components.reduce((groups, component) => ((groups[component.layoutRegion] ||= []).push(component), groups), {}),
    issues: []
  };
}

export { canonicalUnit, unitLabel };
