const COMPONENT_TYPE = 'COMPOSITION_TABS';
const REGION_ORDER = Object.freeze({ HEADER: 0, LEFT: 1, CENTER: 2, RIGHT: 3, BOTTOM: 4, OVERLAY: 5 });
const UNIT_ALIASES = Object.freeze({
  YUAN: 'YUAN', 元: 'YUAN', 人民币元: 'YUAN', CNY: 'YUAN', RMB: 'YUAN',
  TEN_THOUSAND: 'TEN_THOUSAND', 万元: 'TEN_THOUSAND',
  HUNDRED_MILLION: 'HUNDRED_MILLION', 亿元: 'HUNDRED_MILLION',
  COUNT: 'COUNT', 个: 'COUNT', 户: 'COUNT', 人: 'COUNT',
  TEN_THOUSAND_COUNT: 'TEN_THOUSAND_COUNT', 万户: 'TEN_THOUSAND_COUNT',
  PERCENT: 'PERCENT', '%': 'PERCENT', 百分比: 'PERCENT', 百分数: 'PERCENT',
  RATIO: 'RATIO', 比例: 'RATIO'
});
const UNIT_LABELS = Object.freeze({
  YUAN: '元', TEN_THOUSAND: '万元', HUNDRED_MILLION: '亿元', COUNT: '个',
  TEN_THOUSAND_COUNT: '万户', PERCENT: '%', RATIO: '%'
});

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function token(value) {
  return text(value).toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '');
}

function finite(value) {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : null;
}

function canonicalUnit(value) {
  const raw = text(value);
  return UNIT_ALIASES[raw] || UNIT_ALIASES[raw.toUpperCase()] || null;
}

function unitLabel(value) {
  const unit = canonicalUnit(value);
  return UNIT_LABELS[unit] || text(value);
}

function unitType(unit) {
  if (['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION'].includes(unit)) return 'AMOUNT';
  if (['COUNT', 'TEN_THOUSAND_COUNT'].includes(unit)) return 'COUNT';
  if (['PERCENT', 'RATIO'].includes(unit)) return 'RATIO';
  return null;
}

function unitScale(unit) {
  return ({ YUAN: 1, TEN_THOUSAND: 1e4, HUNDRED_MILLION: 1e8, COUNT: 1, TEN_THOUSAND_COUNT: 1e4 }[unit] || 1);
}

function formatNumber(value) {
  const number = finite(value);
  if (number === null) return '—';
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 8 }).format(number);
}

function presentationFrom(source) {
  if (!isObject(source)) return null;
  if (source.displaySchemaVersion !== undefined || source.display) return source;
  if (isObject(source.canvasStyle?.presentation)) return source.canvasStyle.presentation;
  if (isObject(source.renderPackage?.canvasStyle?.presentation)) return source.renderPackage.canvasStyle.presentation;
  return null;
}

function sortComponents(components) {
  return components
    .map((component, index) => ({ component, index }))
    .filter(item => item.component?.componentType === COMPONENT_TYPE && item.component?.visible !== false)
    .sort((left, right) => (REGION_ORDER[text(left.component?.layoutRegion).toUpperCase()] ?? 99)
      - (REGION_ORDER[text(right.component?.layoutRegion).toUpperCase()] ?? 99)
      || ((Number.isInteger(left.component?.order) ? left.component.order : left.index)
        - (Number.isInteger(right.component?.order) ? right.component.order : right.index))
      || left.index - right.index)
    .map(item => item.component);
}

function lookupSource(model, component, tab) {
  const refs = Array.isArray(component?.dataRefs) ? component.dataRefs : [];
  const maps = [model.blockResults, model.displayValues, model.metricValues, model.blocks, model.results]
    .filter(isObject);
  const keys = refs.flatMap(ref => [ref?.blockId, String(ref?.blockId || ''), ref?.metricCode, ref?.metricName])
    .concat([component?.componentId, tab?.tabKey, tab?.label]).map(text).filter(Boolean);
  for (const map of maps) {
    for (const key of keys) {
      if (Object.prototype.hasOwnProperty.call(map, key)) {
        const value = map[key];
        if (isObject(value)) return value;
        return { value };
      }
    }
  }
  const arrays = [model.compositionTabs, model.composition, model.tabs, model.values, model.items];
  for (const array of arrays) {
    if (!Array.isArray(array)) continue;
    const found = array.find(item => isObject(item) && [item.tabKey, item.key, item.metricCode, item.name, item.label]
      .map(token).some(candidate => keys.map(token).includes(candidate)));
    if (found) return found;
  }
  // A caller may pass a single already-resolved block result. This fallback does
  // not infer a metric from the title; the configured fields still gate reads.
  if (isObject(model) && (Object.prototype.hasOwnProperty.call(model, tab?.corporateField)
    || Object.prototype.hasOwnProperty.call(model, tab?.retailField))) return model;
  return null;
}

function readField(source, field) {
  const name = text(field);
  if (!source || !name || !Object.prototype.hasOwnProperty.call(source, name)) return { exists: false, raw: null };
  return { exists: true, raw: source[name] };
}

function fieldUnit(source, field, fallback) {
  return source?.units?.[field]
    || source?.unitByField?.[field]
    || source?.[`${field}Unit`]
    || source?.unit
    || fallback
    || '';
}

function normalizeValue(raw, sourceUnit, targetUnit) {
  const value = finite(raw);
  if (value === null) return { value: null, unit: unitLabel(targetUnit || sourceUnit), type: unitType(canonicalUnit(targetUnit || sourceUnit)), valid: false };
  const source = canonicalUnit(sourceUnit) || canonicalUnit(targetUnit);
  const target = canonicalUnit(targetUnit) || source;
  const sourceType = unitType(source);
  const targetType = unitType(target);
  if (sourceType && targetType && sourceType !== targetType) {
    return { value: null, unit: unitLabel(target), type: sourceType, valid: false, unitMismatch: true };
  }
  if (sourceType === 'RATIO') {
    // All shares are percentage points. RATIO is converted once; an already
    // adapted '%' value is left untouched when sourceUnit is PERCENT.
    const percentValue = source === 'RATIO' ? value * 100 : value;
    return { value: percentValue, unit: unitLabel(target || 'PERCENT'), type: 'RATIO', valid: true };
  }
  if (sourceType && targetType && sourceType === targetType && sourceType !== 'RATIO') {
    const base = value * unitScale(source);
    return { value: base / unitScale(target), unit: unitLabel(target), type: targetType, valid: true };
  }
  return { value, unit: unitLabel(target || source), type: targetType || sourceType, valid: true };
}

function blankValue(unit = '') {
  return { value: null, text: '—', unit: unitLabel(unit), share: null, shareText: '占比不可计算' };
}

function readTabValue(source, field, configuredUnit) {
  const raw = readField(source, field);
  const unit = fieldUnit(source, field, configuredUnit);
  const normalized = normalizeValue(raw.raw, unit, configuredUnit);
  return {
    exists: raw.exists,
    value: normalized.value,
    text: normalized.value === null ? '—' : formatNumber(normalized.value),
    unit: normalized.unit,
    type: normalized.type,
    valid: normalized.valid,
    unitMismatch: normalized.unitMismatch === true,
    share: null,
    shareText: '占比不可计算'
  };
}

function addShares(tab) {
  const corporate = tab.corporate;
  const retail = tab.retail;
  if (corporate.unitMismatch || retail.unitMismatch || tab.total.unitMismatch) {
    return { ...tab, state: 'UNIT_MISMATCH', statusMessage: '公司、零售与总量单位类型不一致，无法计算占比' };
  }
  if (!corporate.exists || !retail.exists || corporate.value === null || retail.value === null) {
    return { ...tab, state: 'MISSING_SIDE', statusMessage: '公司或零售构成来源缺失，无法计算占比' };
  }
  if ([corporate.value, retail.value, tab.total.value].some(value => value !== null && value < 0)) {
    return { ...tab, state: 'NEGATIVE_VALUE', statusMessage: '构成值不能为负，无法计算占比' };
  }
  if (tab.total.value === null) {
    return { ...tab, state: 'NO_TOTAL', statusMessage: '总量来源待接入，未计算占比' };
  }
  if (tab.total.value === 0) {
    return { ...tab, state: 'ZERO_DENOMINATOR', statusMessage: '总量为0，无法计算占比' };
  }
  const total = tab.total.value;
  const corporateShare = corporate.value / total * 100;
  const retailShare = retail.value / total * 100;
  const difference = Math.round((total - corporate.value - retail.value) * 1e12) / 1e12;
  const withShare = (item, share) => ({ ...item, share, shareText: `${formatNumber(share)}%` });
  const next = { ...tab, corporate: withShare(corporate, corporateShare), retail: withShare(retail, retailShare), state: 'READY', statusMessage: '' };
  if (difference > 0) next.other = { value: difference, text: formatNumber(difference), unit: tab.total.unit, share: difference / total * 100, shareText: `${formatNumber(difference / total * 100)}%` };
  if (difference < 0) next.gap = { value: Math.abs(difference), signedValue: difference, text: formatNumber(Math.abs(difference)), unit: tab.total.unit, share: Math.abs(difference) / total * 100, shareText: `${formatNumber(Math.abs(difference) / total * 100)}%` };
  return next;
}

function buildTab(tabConfig, source, index) {
  const tabKey = text(tabConfig?.tabKey) || `tab-${index}`;
  const label = text(tabConfig?.label) || tabKey;
  const configuredUnit = text(tabConfig?.unit) || '';
  const corporate = readTabValue(source, tabConfig?.corporateField, configuredUnit);
  const retail = readTabValue(source, tabConfig?.retailField, configuredUnit);
  const total = text(tabConfig?.totalField)
    ? readTabValue(source, tabConfig.totalField, configuredUnit)
    : blankValue(configuredUnit);
  const base = {
    tabKey,
    label,
    unit: unitLabel(configuredUnit || corporate.unit || retail.unit),
    corporate,
    retail,
    total,
    other: null,
    gap: null,
    state: source ? 'PENDING' : 'NO_SOURCE',
    statusMessage: source ? '' : '构成来源待接入'
  };
  if (!source) return base;
  return addShares(base);
}

/** 将displaySchemaVersion=1的COMPOSITION_TABS适配为只读页签模型。 */
export function buildCompositionTabsModel(sourcePresentation, model = {}, options = {}) {
  const presentation = presentationFrom(sourcePresentation);
  if (!presentation || presentation.displaySchemaVersion !== 1) return { enabled: false, components: [], tabs: [] };
  const rawComponents = sortComponents(Array.isArray(presentation.display?.components) ? presentation.display.components : []);
  const components = rawComponents.map((component, index) => {
    const configs = Array.isArray(component.content?.tabs) ? component.content.tabs : [];
    const tabs = configs.map((tabConfig, tabIndex) => buildTab(tabConfig, lookupSource(isObject(model) ? model : {}, component, tabConfig), tabIndex));
    return {
      componentId: text(component.componentId) || `composition-${index}`,
      componentType: COMPONENT_TYPE,
      layoutRegion: text(component.layoutRegion) || 'CENTER',
      order: Number.isInteger(component.order) ? component.order : index,
      title: component.text?.titleMode === 'CUSTOM' ? text(component.text.title) : (text(component.text?.title) || ''),
      subtitle: component.text?.subtitle ?? '',
      tabs
    };
  });
  const tabs = components.flatMap(component => component.tabs);
  const rawInterval = Number(options.intervalMs);
  const intervalMs = Number.isInteger(rawInterval) && rawInterval > 0 ? rawInterval : 10000;
  const rotationEnabled = tabs.length > 1;
  return {
    enabled: true,
    components,
    tabs,
    activeTabKey: tabs[0]?.tabKey || '',
    intervalMs,
    rotationEnabled,
    rotationState: rotationEnabled ? 'READY' : 'IDLE',
    issues: []
  };
}

export { canonicalUnit, unitLabel, unitType };
