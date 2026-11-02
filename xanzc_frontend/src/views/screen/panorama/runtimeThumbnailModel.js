const LEGACY_KPIS = Object.freeze({
  'branch-overview-v1': ['deposit', 'loan', 'customers', 'revenue'],
  'corporate-overview-v1': ['corpDeposit', 'corpDepositAverage', 'corpLoan', 'corpRevenue', 'corpCustomers', 'corpNplRate'],
  'retail-overview-v1': ['retailAum', 'retailDeposit', 'retailRevenue', 'retailValueCustomers', 'retailLoan', 'retailNplRate', 'retailDepositAverage']
});

const LEGACY_TARGETS = Object.freeze({
  deposit: '.panorama-kpi:nth-of-type(1)', loan: '.panorama-kpi:nth-of-type(2)', customers: '.panorama-kpi:nth-of-type(3)', revenue: '.panorama-kpi:nth-of-type(4)',
  corpDeposit: '.corporate-kpi:nth-of-type(1)', corpDepositAverage: '.corporate-kpi:nth-of-type(2)', corpLoan: '.corporate-kpi:nth-of-type(3)', corpRevenue: '.corporate-kpi:nth-of-type(4)', corpCustomers: '.corporate-kpi:nth-of-type(5)', corpNplRate: '.corporate-kpi:nth-of-type(6)',
  retailAum: '.retail-kpi:nth-of-type(1)', retailDeposit: '.retail-kpi:nth-of-type(2)', retailRevenue: '.retail-kpi:nth-of-type(3)', retailValueCustomers: '.retail-kpi:nth-of-type(4)', retailLoan: '.retail-kpi:nth-of-type(5)', retailNplRate: '.retail-kpi:nth-of-type(6)', retailDepositAverage: '.retail-kpi:nth-of-type(7)',
  depositIncrease: '.panorama-deposit-panel .panorama-deposit-card:nth-of-type(1)', depositAverage: '.panorama-deposit-panel .panorama-deposit-card:nth-of-type(2)', composition: '.panorama-composition-panel', attention: '.panorama-attention-panel', branches: '.panorama-map-panel', trend: '.panorama-trend-panel', corpTrend: '.corporate-trend-panel', retailTrend: '.retail-trend-panel', rate: '[data-target-key="deposit"]', loanRate: '[data-target-key="loan"]', ranking: '.panorama-ranking-panel', corpRanking: '.corporate-ranking-panel', retailRanking: '.retail-ranking-panel', corpSegments: '[data-testid="corporate-segments"]', retailSegments: '[data-testid="retail-segments"]', corpAttention: '[data-testid="corporate-attention"]', retailAttention: '[data-testid="retail-attention-entry"] > summary', corpTargets: '.corporate-target-panel', retailTargets: '.retail-target-panel', 'leadership-corporateDeposit': '[data-diagnostic="corporateDepositCompletion"]', 'leadership-corporateLoan': '[data-diagnostic="corporateLoanCompletion"]', 'leadership-corporateRevenue': '[data-diagnostic="corporateRevenueCompletion"]', 'leadership-institutionCompletion': '[data-diagnostic="institutionCompletion"]'
});
const DEFAULT_LABELS = Object.freeze({ 'overview-deposit': '存款余额', 'overview-loan': '贷款余额', 'institution-map': '辖区机构分布', 'business-growth': '主要指标趋势', 'institution-ranking': '全辖机构对比' });

const EMPTY_MODEL = Object.freeze({ kpis: [], composition: [], institutions: [], trend: [], targets: [], attention: [], ranking: [], title: '经营大屏', scopeLabel: '当前大屏授权范围', dataDate: '', date: '', loading: false, error: '' });

function entryMap(entries) { return new Map((Array.isArray(entries) ? entries : []).map(item => [String(item?.key || item?.slot || ''), item])); }
function labelFor(key, map) { const item = map.get(key); return item?.displayLabel || item?.visualLabel || item?.label || DEFAULT_LABELS[key] || key; }
function emptyMetric(key, label) { return { key, label, value: null, change: null }; }

export function buildRuntimeThumbnailModel({ entries = [], template = '', screenTitle = '', presentation = null } = {}) {
  const map = entryMap(entries);
  const title = String(screenTitle || presentation?.title || '经营大屏');
  const schemaComponents = presentation?.displaySchemaVersion === 1
    ? (presentation?.display?.components || []).filter(component => component?.visible !== false)
    : [];
  let kpiKeys = schemaComponents.length
    ? schemaComponents.filter(component => ['METRIC_CARD', 'AMOUNT_TOTAL', 'COMPLETION'].includes(String(component.componentType || '').toUpperCase())).map(component => component.configKey || component.key || component.id).filter(Boolean)
    : (LEGACY_KPIS[template] || []);
  if (!schemaComponents.length && !kpiKeys.some(key => map.has(String(key)))) kpiKeys = [];
  const model = {
    ...EMPTY_MODEL,
    title,
    kpis: [...new Set(kpiKeys)].map(key => emptyMetric(String(key), labelFor(String(key), map))),
    composition: [], institutions: [], trend: [], targets: [], attention: [], ranking: []
  };
  return model;
}

/** Attribute values are directory identifiers, never selector fragments. */
function attribute(name, value) { return `[${name}=${JSON.stringify(String(value))}]`; }
const CITY_KEYS = Object.freeze({
  'city-deposit-total': 'overview-deposit', 'city-loan-total': 'overview-loan',
  'city-deposit-distribution': 'overview-deposit-composition', 'city-loan-distribution': 'overview-loan-composition',
  'city-settlement-deposit': 'overview-settlementDeposit',
  'city-retail-deposit':'business-retail-deposit-balance', 'city-retail-deposit-rate':'business-retail-deposit-rate',
  'city-retail-loan':'business-retail-loan-balance', 'city-retail-loan-rate':'business-retail-loan-rate',
  'city-corp-deposit':'business-corp-deposit-balance', 'city-corp-deposit-rate':'business-corp-deposit-rate',
  'city-corp-loan':'business-corp-loan-balance', 'city-corp-loan-rate':'business-corp-loan-rate',
  'city-revenue':'business-revenue-operating', 'city-intermediary-income':'business-revenue-fee'
});
function selectorsFor(entry, template, context, schema) {
  const key = String(entry.key || entry.slot || '');
  if (context === 'city') {
    if (key === 'city-branch-ranking') return ['.city-list-panel'];
    if (key === 'city-institution-map') return ['.city-map-panel'];
    return [CITY_KEYS[key] ? attribute('data-config-key', CITY_KEYS[key]) : null];
  }
  if (context === 'branch') {
    if (key === 'branch-employee-ranking') return [attribute('data-config-key', 'institution-ranking')];
    if (key === 'branch-institution-map') return [attribute('data-config-key', 'institution-map')];
    return [entry.parentKey ? attribute('data-config-key', entry.parentKey) : null];
  }
  if (schema) {
    const selector = attribute('data-config-key', key);
    // Generic schema1 regions use component ids; grouped branch regions expose semantic config keys.
    return [entry.sourceComponentId ? `${selector}, ${attribute('data-component-id', entry.sourceComponentId)}:not([data-config-key]):not([data-config-key] *)` : selector];
  }
  if (key === 'branches') return [template === 'corporate-overview-v1' ? '.corporate-map-panel' : template === 'retail-overview-v1' ? '.retail-map-panel' : '.panorama-map-panel'];
  const shared = {
    corpDeposit: 'corporate-deposit-balance', corpDepositAverage: 'corporate-deposit-average',
    retailDeposit: 'retail-deposit-balance', retailDepositAverage: 'retail-deposit-average'
  };
  if (shared[key]) return [LEGACY_TARGETS[key], attribute('data-testid', shared[key])];
  if (['retailLoan','retailNplRate'].includes(key)) return [LEGACY_TARGETS[key], attribute('data-credit-key',key)];
  return [LEGACY_TARGETS[key] || null];
}

/** Map directory entries to the actual runtime regions; unmatched entries stay outside the canvas. */
export function runtimeThumbnailTargets({ entries = [], template = '', presentation = null, context = '' } = {}) {
  const source = Array.isArray(entries) ? entries : [];
  return source.flatMap(entry => selectorsFor(entry, template, context, presentation?.displaySchemaVersion === 1)
    .map((selector, occurrence) => ({ entryKey: String(entry.key || entry.slot || ''), selector, occurrence })));
}

export { LEGACY_KPIS, LEGACY_TARGETS };
