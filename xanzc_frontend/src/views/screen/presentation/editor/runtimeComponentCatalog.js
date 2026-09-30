import { legacyProvinceRingConfigs } from '../model/compositionTabsModel';
import { getEffectiveBusinessGrowthSeries } from '../model/businessGrowthModel';

const BRANCH_TEMPLATE = 'branch-overview-v1';
const BRANCH_METRIC_IDS = Object.freeze([
  'business-retail-deposit-balance', 'business-retail-deposit-rate',
  'business-retail-loan-balance', 'business-retail-loan-rate',
  'business-corp-deposit-balance', 'business-corp-deposit-rate',
  'business-corp-loan-balance', 'business-corp-loan-rate'
]);
const REVENUE_IDS = Object.freeze(['business-revenue-operating', 'business-revenue-fee']);
const DEFAULT_TITLES = Object.freeze({
  'business-retail-deposit-balance': '存款余额', 'business-retail-deposit-rate': '存款完成率',
  'business-retail-loan-balance': '贷款余额', 'business-retail-loan-rate': '贷款完成率',
  'business-corp-deposit-balance': '存款余额', 'business-corp-deposit-rate': '存款完成率',
  'business-corp-loan-balance': '贷款余额', 'business-corp-loan-rate': '贷款完成率',
  'business-revenue-operating': '营业收入', 'business-revenue-fee': '中间业务收入',
  'legacy-trend-57': '业务增长曲线', 'legacy-map-58': '经营机构分布', 'legacy-ranking-58': '机构排名'
});
const LEGACY_AUTO_METRIC_LABELS = Object.freeze({ deposit: '存款余额', loan: '贷款余额' });

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function presentationOf(source) {
  if (!isObject(source)) return {};
  if (isObject(source.displayPresentation)) return presentationOf(source.displayPresentation);
  if (source.displaySchemaVersion !== undefined || source.display) return source;
  if (isObject(source.presentation)) return presentationOf(source.presentation);
  if (isObject(source.canvasStyle?.presentation)) return presentationOf(source.canvasStyle.presentation);
  return {};
}

function metricLabelsOf(source) {
  if (isObject(source?.metricLabels)) return source.metricLabels;
  if (isObject(source?.canvasStyle?.metricLabels)) return source.canvasStyle.metricLabels;
  if (isObject(source?.canvasStyle?.presentation?.metricLabels)) return source.canvasStyle.presentation.metricLabels;
  return {};
}

export function resolveConfiguredMetricLabel(source, key, fallback) {
  const value = text(metricLabelsOf(source)[key]);
  return value && value !== LEGACY_AUTO_METRIC_LABELS[key] ? value : fallback;
}

function defaultTitle(component, key) {
  const custom = component?.text?.titleMode === 'CUSTOM' ? text(component.text.title) : '';
  return custom || text(component?.text?.title) || DEFAULT_TITLES[key] || key;
}

function byId(components, id, type) {
  return components.find(component => component?.componentId === id
    && (!type || component.componentType === type)
    && component.visible !== false) || null;
}

function componentEntry(component, key = component?.componentId) {
  return {
    key,
    dataConfigKey: key,
    kind: 'DISPLAY_COMPONENT',
    label: defaultTitle(component, key),
    editable: true,
    sourceComponentId: component?.componentId || key,
    component
  };
}

function branchCatalog(presentation, source) {
  const components = Array.isArray(presentation.display?.components)
    ? presentation.display.components : [];
  const composition = components.find(item => item?.componentId === 'legacy-composition-64'
    && item?.componentType === 'COMPOSITION_TABS' && item.visible !== false && item.layoutRegion === 'LEFT')
    || components.find(item => item?.componentType === 'COMPOSITION_TABS' && item.visible !== false && item.layoutRegion === 'LEFT');
  const entries = [];
  const add = entry => {
    if (!entry || entries.some(item => item.key === entry.key)) return;
    entries.push({ dataConfigKey: entry.key, ...entry });
  };
  for (const key of ['deposit', 'loan']) {
    add({ key: `overview-${key}`, kind: 'DERIVED_TOTAL', label: resolveConfiguredMetricLabel(source || presentation, key, key === 'deposit' ? '存款总额' : '贷款总额'),
      editable: true, slot: key, bindingKey: key });
    if (composition) add({ key: `overview-${key}-composition`, kind: 'COMPOSITION_TAB', label: key === 'deposit' ? '存款业务分布' : '贷款业务分布',
      editable: true, sourceComponentId: composition.componentId, tabKey: key, slot: 'composition' });
  }
  add({ key: 'overview-settlementDeposit', kind: 'SYSTEM_METRIC', label: '结算性存款', editable: false,
    readonlyReason: '系统生成的演示指标，没有独立保存配置或绑定槽位' });

  for (const key of BRANCH_METRIC_IDS) {
    const component = byId(components, key, 'METRIC_CARD');
    if (component) add(componentEntry(component, key));
  }
  // 保留真实 schema 中未纳入分行业务命名表的可见指标卡，避免设计器丢失
  // 合法的自定义卡；现场标准全辖配置不会额外增加目录项。
  for (const component of components.filter(item => ['METRIC_CARD', 'COMPLETION'].includes(item?.componentType) && item.visible !== false)) {
    if (!entries.some(item => item.key === component.componentId)) add(componentEntry(component));
  }
  for (const key of REVENUE_IDS) {
    const component = byId(components, key, 'METRIC_CARD');
    if (component) add(componentEntry(component, key));
  }
  const trend = byId(components, 'legacy-trend-57', 'TREND');
  if (trend && trend.dataRefs?.some(ref => Number(ref?.blockId) === 57)) add({ ...componentEntry(trend, 'business-growth'), key: 'business-growth', label: defaultTitle(trend, 'legacy-trend-57'), kind: 'BUSINESS_GROWTH', sourceComponentId: trend.componentId,
    effectiveSeries: getEffectiveBusinessGrowthSeries(presentation) });
  const map = byId(components, 'legacy-map-58', 'MAP');
  if (map) add({ ...componentEntry(map, 'institution-map'), key: 'institution-map', label: defaultTitle(map, 'legacy-map-58') || '经营机构分布', kind: 'INSTITUTION_MAP' });
  const ranking = byId(components, 'legacy-ranking-58', 'RANKING');
  if (ranking) add({ ...componentEntry(ranking, 'institution-ranking'), key: 'institution-ranking', label: defaultTitle(ranking, 'legacy-ranking-58') || '机构排名', kind: 'INSTITUTION_RANKING' });

  const sourceTabKey = text(composition?.content?.tabs?.[0]?.tabKey);
  if (composition && composition.componentId !== 'legacy-composition-64'
      && !entries.some(item => item.key === `${text(composition.componentId)}-composition`)) {
    // 自定义 schema1 仍保留其真实 componentId，便于配置页选择而不猜 blockId。
    const fallbackTabs = Array.isArray(composition.content?.tabs) ? composition.content.tabs : [];
    const fallbackTab = fallbackTabs[0];
    if (fallbackTab) entries.push({ dataConfigKey: `${composition.componentId}-composition`, key: `${composition.componentId}-composition`,
      kind: 'COMPOSITION_TAB', label: text(fallbackTab.label) || '业务分布', editable: true,
      sourceComponentId: composition.componentId, tabKey: fallbackTab.tabKey || '', slot: 'composition', tabConfig: fallbackTab });
  }
  const tabs = composition ? legacyProvinceRingConfigs(composition, presentation.template) : [];
  for (const tab of tabs) {
    if (!['deposit', 'loan', 'income'].includes(text(tab?.tabKey))) continue;
    const key = text(tab.tabKey);
    if (key === 'income') continue;
    const defaultLabel = key === 'deposit' ? '存款业务分布' : '贷款业务分布';
    const customLabel = text(tab.label) && !['存款', '贷款', '业务结构'].includes(text(tab.label)) ? text(tab.label) : defaultLabel;
    const entry = entries.find(item => item.key === `overview-${key}-composition`)
      || entries.find(item => item.sourceComponentId === composition?.componentId && item.tabKey === key);
    if (entry) Object.assign(entry, { label: customLabel, tabConfig: tab, sourceTabKey,
      materializedTabs: tabs.filter(item => ['deposit', 'loan'].includes(text(item?.tabKey))) });
  }
  const groups = [
    { key: 'OVERVIEW', label: '顶部总览', entries: entries.filter(item => ['overview-deposit', 'overview-deposit-composition', 'overview-loan', 'overview-loan-composition', 'overview-settlementDeposit'].includes(item.key)) },
    { key: 'RETAIL', label: '零售业务', entries: entries.filter(item => item.key.startsWith('business-retail-')) },
    { key: 'CORP', label: '对公业务', entries: entries.filter(item => item.key.startsWith('business-corp-')) },
    { key: 'REVENUE', label: '收入', entries: entries.filter(item => REVENUE_IDS.includes(item.key)) },
    { key: 'ANALYSIS', label: '经营分析', entries: entries.filter(item => ['business-growth', 'institution-map', 'institution-ranking'].includes(item.key)) }
  ].filter(group => group.entries.length);
  return { mode: 'BRANCH_PROVINCE', template: presentation.template, entries, groups };
}

/** 根据展示协议和组件身份建立设计器与运行态之间的配置目录。 */
export function buildRuntimeComponentCatalog(source) {
  const presentation = presentationOf(source);
  const components = Array.isArray(presentation.display?.components) ? presentation.display.components : [];
  if (presentation.template === BRANCH_TEMPLATE && presentation.displaySchemaVersion === 1) {
    return branchCatalog(presentation, source);
  }
  const supported = new Set(['METRIC_CARD', 'COMPLETION', 'TREND', 'COMPOSITION_TABS', 'RANKING', 'MAP', 'DETAIL_TABLE']);
  const entries = components.filter(component => component?.visible !== false && supported.has(component?.componentType))
    .map(component => componentEntry(component));
  return { mode: 'DISPLAY_COMPONENTS', template: presentation.template || '', entries, groups: [] };
}

export { DEFAULT_TITLES, BRANCH_METRIC_IDS, REVENUE_IDS };
