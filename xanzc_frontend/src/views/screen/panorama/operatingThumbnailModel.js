import { RETAIL_RUNTIME_KPI_ORDER } from './operatingRuntimeLayout.js';

const LEGACY_LABELS = Object.freeze({
  corpSegments: '重点客群信贷', corpAttention: '需要协调的事项', corpRanking: '对公机构排名 / 数值对照', corpTargets: '对公经营目标',
  retailSegments: '客户分层', retailAttention: '经营事项与数据核验', retailRanking: '机构存款对比', retailTargets: '零售经营目标',
  corpTrend: '对公存贷款趋势', retailTrend: '零售存款余额与月日均趋势', branches: '陕西省机构分布'
});

const LEGACY_LAYOUTS = Object.freeze({
  'corporate-overview-v1': {
    top: ['corpDeposit', 'corpDepositAverage', 'corpLoan', 'corpRevenue', 'corpCustomers', 'corpNplRate'],
    left: ['corpDeposit', 'corpDepositAverage', 'corpSegments', 'corpAttention'], center: ['branches', 'corpTrend'], right: ['corpRanking', 'corpTargets']
  },
  'retail-overview-v1': {
    top: RETAIL_RUNTIME_KPI_ORDER,
    left: ['retailDeposit', 'retailDepositAverage', 'retailSegments', 'retailLoan','retailNplRate','retailAttention'], center: ['branches', 'retailTrend'], right: ['retailRanking', 'retailTargets']
  },
  branch: {
    top: ['deposit', 'loan', 'customers', 'revenue'],
    summary:['leadership-corporateDeposit','leadership-corporateLoan','leadership-corporateRevenue','leadership-institutionCompletion'],
    left: ['depositIncrease', 'depositAverage', 'composition', 'attention'],
    center: ['branches', 'trend', 'branchTrend'],
    right: ['rate', 'loanRate', 'ranking'], bottom: ['citySummary']
  }
});

function visualKind(entry) {
  const key = String(entry.key || entry.slot || '');
  if (entry.visualKind) return entry.visualKind;
  const componentKind = {TREND:'BUSINESS_GROWTH',RANKING:'INSTITUTION_RANKING',MAP:'INSTITUTION_MAP',DETAIL_TABLE:'LIST',COMPOSITION_TABS:'COMPOSITION_TAB',COMPLETION:'COMPLETION'}[entry.component?.componentType];
  if (componentKind) return componentKind;
  if (key.endsWith('Trend') || ['business-growth','trend'].includes(key)) return 'BUSINESS_GROWTH';
  if (key.endsWith('Ranking') || ['institution-ranking','ranking'].includes(key)) return 'INSTITUTION_RANKING';
  if (key === 'branches' || key === 'institution-map') return 'INSTITUTION_MAP';
  if (key.endsWith('Segments') || key.endsWith('Attention') || key === 'attention') return 'LIST';
  if (key.endsWith('Targets')) return 'PROGRESS';
  if (key === 'composition') return 'COMPOSITION_TAB';
  if (['rate','loanRate'].includes(key)) return 'COMPLETION';
  return entry.kind;
}

function legacyRegion(template, viewLevel) {
  if (template === 'branch-overview-v1') return LEGACY_LAYOUTS.branch;
  return LEGACY_LAYOUTS[template] || null;
}

function displayEntry(original, region, occurrence, template) {
  const sourceKey = original.key || original.slot;
  const repeated = occurrence > 0;
  const fixedLabel=template==='branch-overview-v1'
    ? ({branches:'辖区机构分布',trend:'主要指标趋势',composition:'业务构成与占比',attention:'流程与经营关注',ranking:'全辖机构对比',depositIncrease:'较上月净增',depositAverage:'月均余额'})[sourceKey]
    : LEGACY_LABELS[sourceKey];
  return { ...original, key: sourceKey, displayKey: repeated ? `${sourceKey}__${region}` : sourceKey, sourceKey,
    dataConfigKey: repeated ? undefined : sourceKey, dataLinkedConfigKey: repeated ? sourceKey : undefined,
    label: original.displayLabel || original.visualLabel || fixedLabel || original.label || sourceKey,
    displayLabel: original.displayLabel || original.visualLabel || fixedLabel || original.label || sourceKey,
    visualKind: visualKind(original), originalEntry: original, region, occurrence, template };
}

export function buildOperatingThumbnailModel({ entries = [], template = '', viewLevel = '', presentation = null } = {}) {
  const source = Array.isArray(entries) ? entries : [];
  const schema1 = presentation?.displaySchemaVersion === 1 || source.some(entry => entry.component?.layoutRegion);
  const visible = source.filter(entry => entry.component?.visible !== false && entry.visible !== false);
  if (schema1) {
    const sorted = [...visible].sort((a, b) => Number(a.component?.order ?? a.order ?? 0) - Number(b.component?.order ?? b.order ?? 0));
    const regionMap = new Map();
    sorted.forEach(original => {
      const rawRegion = String(original.component?.layoutRegion || original.layoutRegion || 'TOP').toUpperCase();
      const region = rawRegion === 'HEADER' ? 'top' : rawRegion.toLowerCase();
      if (!regionMap.has(region)) regionMap.set(region, []);
      const sourceKey = original.key || original.slot;
      const occurrence = sorted.slice(0, sorted.indexOf(original) + 1).filter(item => (item.key || item.slot) === sourceKey).length - 1;
      regionMap.get(region).push(displayEntry(original, region, occurrence, template));
    });
    const regions = [...regionMap.entries()].map(([key, items]) => ({ key, label: key === 'top' ? '总览' : '经营分析', items }));
    return { mode: 'SCHEMA1', entries: sorted.map(item => displayEntry(item, 'catalog', 0, template)), groups: regions, regions };
  }
  const layout = legacyRegion(template, viewLevel);
  if (!layout) return { mode: 'EMPTY', entries: source, groups: [], regions: [] };
  const byKey = new Map(source.map(entry => [entry.key || entry.slot, entry]));
  const occurrences = new Map();
  const regions = Object.entries(layout).map(([key, keys]) => ({ key, label: key === 'top' ? '总览' : key === 'left' ? '经营重点' : key === 'center' ? '机构与趋势' : key === 'right' ? '排名与目标' : '经营分析', items: keys.map(slot => {
    const original = byKey.get(slot);
    if (!original) return null;
    const count = occurrences.get(slot) || 0; occurrences.set(slot, count + 1);
    return displayEntry(original, key, count, template);
  }).filter(Boolean) }));
  const regionsWithItems = regions.filter(region => region.items.length);
  const groups = [
    ...regionsWithItems.filter(region => region.key === 'top').map(region => ({ ...region, key: 'overview' })),
    { key: 'analysis', label: '经营分析', items: regionsWithItems.filter(region => region.key !== 'top').flatMap(region => region.items) }
  ].filter(group => group.items.length);
  return { mode: 'LEGACY', entries: source.map(item => displayEntry(item, 'catalog', 0, template)), groups, regions: regionsWithItems };
}

export { LEGACY_LABELS, LEGACY_LAYOUTS };
