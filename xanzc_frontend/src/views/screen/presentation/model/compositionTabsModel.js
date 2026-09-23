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
  if (tab.total.unitMismatch) {
    return { ...tab, state: 'UNIT_MISMATCH', statusMessage: '公司、零售与总量单位类型不一致，无法计算占比' };
  }
  if (tab.total.value === null) {
    return { ...tab, state: 'NO_TOTAL', statusMessage: '总量来源待接入，未计算占比' };
  }
  if (tab.total.value === 0) {
    return { ...tab, state: 'ZERO_DENOMINATOR', statusMessage: '总量为0，无法计算占比' };
  }
  if (tab.total.value < 0) {
    return { ...tab, state: 'NEGATIVE_VALUE', statusMessage: '总量不能为负，无法计算占比' };
  }
  const total = tab.total.value;
  const corporateValid = corporate.exists && corporate.value !== null && !corporate.unitMismatch && corporate.value >= 0;
  const retailValid = retail.exists && retail.value !== null && !retail.unitMismatch && retail.value >= 0;
  const corporateShare = corporateValid ? Math.round((corporate.value / total * 100) * 1e12) / 1e12 : null;
  const retailShare = retailValid ? Math.round((retail.value / total * 100) * 1e12) / 1e12 : null;
  const missingSide = corporateShare === null || retailShare === null;
  const difference = missingSide ? null : Math.round((total - corporate.value - retail.value) * 1e12) / 1e12;
  const withShare = (item, share) => ({ ...item, share, shareText: share === null ? '待接入' : `${formatNumber(share)}%` });
  const hasInvalidValue = (corporate.value !== null && corporate.value < 0) || (retail.value !== null && retail.value < 0);
  const hasUnitMismatch = corporate.unitMismatch || retail.unitMismatch;
  const state = hasInvalidValue ? 'NEGATIVE_VALUE' : hasUnitMismatch ? 'UNIT_MISMATCH' : missingSide ? 'MISSING_SIDE' : 'READY';
  const statusMessage = state === 'READY' ? '' : state === 'MISSING_SIDE' ? '公司或零售构成来源缺失，部分占比待接入' : state === 'NEGATIVE_VALUE' ? '构成值不能为负，无法计算占比' : '公司或零售单位类型不一致，部分无法计算占比';
  const next = { ...tab, corporate: withShare(corporate, corporateShare), retail: withShare(retail, retailShare), state, statusMessage };
  if (difference > 0) next.other = { value: difference, text: formatNumber(difference), unit: tab.total.unit, share: Math.round((difference / total * 100) * 1e12) / 1e12, shareText: `${formatNumber(difference / total * 100)}%` };
  if (difference < 0) next.gap = { value: Math.abs(difference), signedValue: difference, text: formatNumber(Math.abs(difference)), unit: tab.total.unit, share: Math.round((Math.abs(difference) / total * 100) * 1e12) / 1e12, shareText: `${formatNumber(Math.abs(difference) / total * 100)}%` };
  return next;
}

function buildIntermediaryIncome(content, source) {
  const incomeRatio = isObject(content?.incomeRatio) ? content.incomeRatio : {};
  const numeratorField = text(incomeRatio.numeratorField);
  const denominatorField = text(incomeRatio.denominatorField);
  const targetUnit = text(incomeRatio.unit);
  const pending = {
    state: 'PENDING', ratio: null, ratioText: '待接入',
    numerator: blankValue(targetUnit), denominator: blankValue(targetUnit), statusMessage: '中间收入与营业收入来源待接入'
  };
  if (!numeratorField || !denominatorField || !source) return pending;
  const numerator = readTabValue(source, numeratorField, targetUnit);
  const denominator = readTabValue(source, denominatorField, targetUnit);
  const base = { state: 'MISSING', ratio: null, ratioText: '待接入', numerator, denominator, statusMessage: '中间收入或营业收入来源缺失' };
  if (numerator.unitMismatch || denominator.unitMismatch
      || numerator.type !== 'AMOUNT' || denominator.type !== 'AMOUNT') {
    return { ...base, state: 'UNIT_MISMATCH', statusMessage: '中间收入与营业收入单位类型不一致，无法计算' };
  }
  if (!numerator.exists || !denominator.exists || numerator.value === null || denominator.value === null) return base;
  if (numerator.value < 0 || denominator.value < 0) {
    return { ...base, state: 'NEGATIVE_VALUE', statusMessage: '中间收入与营业收入不能为负，无法计算' };
  }
  if (denominator.value === 0) return { ...base, state: 'ZERO_DENOMINATOR', statusMessage: '营业收入为0，无法计算占比' };
  const ratio = Math.round((numerator.value / denominator.value * 100) * 1e12) / 1e12;
  return { ...base, state: 'READY', ratio, ratioText: `${formatNumber(ratio)}%`, statusMessage: '' };
}

function buildBusinessLines(tabs) {
  const byKey = new Map(tabs.map(tab => [tab.tabKey, tab]));
  const pending = key => ({ tabKey: key, label: key === 'deposit' ? '存款' : '贷款', state: 'PENDING', value: blankValue(''), share: null, shareText: '待接入' });
  const shareFor = tab => {
    if (!tab) return '待接入';
    if (tab.corporate?.share !== null && tab.corporate?.share !== undefined) return tab.corporate.shareText;
    if (tab.corporate?.value === null || tab.corporate?.value === undefined) return '待接入';
    return '占比不可计算';
  };
  const retailShareFor = tab => {
    if (!tab) return '待接入';
    if (tab.retail?.share !== null && tab.retail?.share !== undefined) return tab.retail.shareText;
    if (tab.retail?.value === null || tab.retail?.value === undefined) return '待接入';
    return '占比不可计算';
  };
  return [
    { businessLine: 'CORP', label: '公司', items: ['deposit', 'loan'].map(key => ({ ...pending(key), value: byKey.get(key)?.corporate || pending(key).value, share: byKey.get(key)?.corporate?.share ?? null, shareText: shareFor(byKey.get(key)), state: byKey.get(key)?.state || 'PENDING', total: byKey.get(key)?.total || blankValue(''), other: byKey.get(key)?.other || null, gap: byKey.get(key)?.gap || null, otherShareText: byKey.get(key)?.other?.shareText || '占比不可计算', gapShareText: byKey.get(key)?.gap?.shareText || '占比不可计算' })) },
    { businessLine: 'RETAIL', label: '零售', items: ['deposit', 'loan'].map(key => ({ ...pending(key), value: byKey.get(key)?.retail || pending(key).value, share: byKey.get(key)?.retail?.share ?? null, shareText: retailShareFor(byKey.get(key)), state: byKey.get(key)?.state || 'PENDING', total: byKey.get(key)?.total || blankValue(''), other: byKey.get(key)?.other || null, gap: byKey.get(key)?.gap || null, otherShareText: byKey.get(key)?.other?.shareText || '占比不可计算', gapShareText: byKey.get(key)?.gap?.shareText || '占比不可计算' })) }
  ];
}

function buildSections(tabs, intermediaryIncome) {
  const lines = buildBusinessLines(tabs);
  const incomeShareText = intermediaryIncome.state === 'READY'
    ? intermediaryIncome.ratioText
    : intermediaryIncome.state === 'PENDING' || intermediaryIncome.state === 'MISSING' ? '待接入' : '占比不可计算';
  return [
    { sectionKey: 'corporate', label: '公司', businessLine: 'CORP', items: lines[0].items },
    { sectionKey: 'retail', label: '零售', businessLine: 'RETAIL', items: lines[1].items },
    { sectionKey: 'income', label: '中间收入', businessLine: 'COMMON', items: [{ tabKey: 'income', label: '中间收入占营业收入', value: intermediaryIncome.numerator, denominator: intermediaryIncome.denominator, share: intermediaryIncome.ratio, shareText: incomeShareText, state: intermediaryIncome.state }] }
  ];
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
    const source = lookupSource(isObject(model) ? model : {}, component, null);
    const intermediaryIncome = buildIntermediaryIncome(component.content, source);
    return {
      componentId: text(component.componentId) || `composition-${index}`,
      componentType: COMPONENT_TYPE,
      layoutRegion: text(component.layoutRegion) || 'CENTER',
      order: Number.isInteger(component.order) ? component.order : index,
      title: component.text?.titleMode === 'CUSTOM' ? text(component.text.title) : (text(component.text?.title) || ''),
      subtitle: component.text?.subtitle ?? '',
      tabs,
      businessLines: buildBusinessLines(tabs),
      intermediaryIncome,
      sections: buildSections(tabs, intermediaryIncome)
    };
  });
  const tabs = components.flatMap(component => component.tabs);
  const rawInterval = Number(options.intervalMs);
  const intervalMs = Number.isInteger(rawInterval) && rawInterval > 0 ? rawInterval : 10000;
  const sections = components[0]?.sections || [];
  const rotationEnabled = sections.length > 1 || tabs.length > 1;
  return {
    enabled: true,
    components,
    tabs,
    sections,
    activeTabKey: tabs[0]?.tabKey || '',
    intervalMs,
    rotationEnabled,
    rotationState: rotationEnabled ? 'READY' : 'IDLE',
    issues: []
  };
}

export { canonicalUnit, unitLabel, unitType };
