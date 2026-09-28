/**
 * 分行配置化大屏的业务增长曲线只读适配器。
 *
 * 该适配器故意把来源收窄到分行主趋势 block57 及四个已确认字段，避免
 * 使用业务结构 block64 的最新快照拼出历史曲线。所有金额先统一为元，
 * 交给 PanoramaTrend 的 amountFriendly 负责图表坐标展示。
 */

const TREND_COMPONENT = 'TREND';
const SOURCE_BLOCK_ID = 57;
const BRANCH_TEMPLATE = 'branch-overview-v1';

export const BUSINESS_GROWTH_FIELDS = Object.freeze({
  retailDeposit: '测试_直营零售存款',
  retailLoan: '测试_直营零售贷款',
  corpDeposit: '测试_直营对公存款',
  corpLoan: '测试_直营对公贷款'
});

const BUSINESS_LINES = Object.freeze([
  Object.freeze({
    key: 'RETAIL',
    title: '零售业务',
    emptyMessage: '零售业务历史存贷款数据待接入',
    fields: Object.freeze({ deposit: 'retailDeposit', loan: 'retailLoan' })
  }),
  Object.freeze({
    key: 'CORP',
    title: '对公业务',
    emptyMessage: '对公业务历史存贷款数据待接入',
    fields: Object.freeze({ deposit: 'corpDeposit', loan: 'corpLoan' })
  })
]);

const UNIT_ALIASES = Object.freeze({
  YUAN: 'YUAN', 元: 'YUAN', CNY: 'YUAN',
  TEN_THOUSAND: 'TEN_THOUSAND', TEN_THOUSAND_YUAN: 'TEN_THOUSAND', 万元: 'TEN_THOUSAND',
  HUNDRED_MILLION: 'HUNDRED_MILLION', HUNDRED_MILLION_YUAN: 'HUNDRED_MILLION', 亿元: 'HUNDRED_MILLION'
});

const UNIT_SCALE = Object.freeze({
  YUAN: 1,
  TEN_THOUSAND: 1e4,
  HUNDRED_MILLION: 1e8
});

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function canonicalUnit(value) {
  const raw = text(value);
  return UNIT_ALIASES[raw] || UNIT_ALIASES[raw.toUpperCase()] || null;
}

function finite(value) {
  if (value === null || value === undefined || typeof value === 'boolean') return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : null;
}

function presentationOf(source) {
  if (!isRecord(source)) return {};
  if (isRecord(source.displayPresentation)) return presentationOf(source.displayPresentation);
  if (source.displaySchemaVersion !== undefined || source.display) return source;
  if (isRecord(source.presentation)) return presentationOf(source.presentation);
  if (isRecord(source.canvasStyle?.presentation)) return presentationOf(source.canvasStyle.presentation);
  if (isRecord(source.renderPackage?.canvasStyle?.presentation)) return presentationOf(source.renderPackage.canvasStyle.presentation);
  return {};
}

function componentsOf(presentation) {
  return Array.isArray(presentation?.display?.components)
    ? presentation.display.components.filter(component => component?.componentType === TREND_COMPONENT
      && component.visible !== false
      && String(component.layoutRegion || '').toUpperCase() === 'CENTER')
    : [];
}

function blockIdOf(ref) {
  const value = ref?.blockId;
  return Number.isSafeInteger(value) ? value : Number.isSafeInteger(Number(value)) ? Number(value) : null;
}

function isBranchTrend(component, ref) {
  return String(component?.componentId || '') === 'branch-trend'
    || String(component?.componentId || '') === 'branchTrend'
    || ['branchTrend', 'BRANCH_TREND'].includes(String(ref?.slot || '').trim())
    || ['branchTrend', 'BRANCH_TREND'].includes(String(ref?.bindingKey || '').trim())
    || ['branchTrend', 'BRANCH_TREND'].includes(String(ref?.semantic || '').trim());
}

function mainTrendEntry(presentation) {
  const trends = componentsOf(presentation);
  const candidates = trends
    .map(component => ({ component, ref: Array.isArray(component.dataRefs) ? component.dataRefs[0] || {} : {} }))
    .filter(item => blockIdOf(item.ref) === SOURCE_BLOCK_ID && !isBranchTrend(item.component, item.ref));
  const preferred = candidates.find(item => String(item.ref.role || '').toUpperCase() === 'PRIMARY')
    || candidates.find(item => item.component.componentId === 'legacy-trend-57')
    || candidates[0];
  return preferred || null;
}

function mainTrendRef(presentation) {
  return mainTrendEntry(presentation)?.ref || null;
}

function sourceBlock(model) {
  const blocks = isRecord(model?.blockResults) ? model.blockResults : {};
  return blocks[SOURCE_BLOCK_ID] ?? blocks[String(SOURCE_BLOCK_ID)] ?? null;
}

function sourceRows(block) {
  if (Array.isArray(block)) return block;
  return Array.isArray(block?.rows) ? block.rows : [];
}

function configuredSeries(presentation) {
  const component = mainTrendEntry(presentation)?.component;
  return Array.isArray(component?.content?.series) ? component.content.series : [];
}

function configuredUnitByField(presentation) {
  const units = new Map();
  const series = configuredSeries(presentation);
  for (const item of series) {
    const field = text(item?.field);
    const semantic = Object.entries(BUSINESS_GROWTH_FIELDS).find(([, value]) => value === field)?.[0] || '';
    if (!semantic || !canonicalUnit(item?.unit)) continue;
    const resolvedField = BUSINESS_GROWTH_FIELDS[semantic];
    if (!units.has(resolvedField)) units.set(resolvedField, item.unit);
  }
  return units;
}

function fieldUnit(block, presentation, field) {
  const configured = configuredUnitByField(presentation);
  const source = block?.unitByField?.[field]
    ?? configured.get(field);
  return canonicalUnit(source);
}

function validDates(rows) {
  const dates = new Set();
  const normalized = [];
  for (const row of rows) {
    if (!isRecord(row)) return { rows: [], issue: '趋势行不是对象' };
    const date = text(row.date ?? row.data_date);
    if (!date) return { rows: [], issue: '趋势行缺少日期' };
    if (dates.has(date)) return { rows: [], issue: `趋势日期重复: ${date}` };
    dates.add(date);
    normalized.push({ row, date });
  }
  return { rows: normalized, issue: '' };
}

function emptyGroup(line, issue = '') {
  const deposit = line.fields.deposit;
  const loan = line.fields.loan;
  return {
    businessLine: line.key,
    title: line.title,
    unit: '元',
    status: 'EMPTY',
    emptyMessage: line.emptyMessage,
    issue,
    fields: { deposit: BUSINESS_GROWTH_FIELDS[deposit], loan: BUSINESS_GROWTH_FIELDS[loan] },
    series: [
      { key: deposit, seriesKey: deposit, field: BUSINESS_GROWTH_FIELDS[deposit], label: '存款', unit: '元' },
      { key: loan, seriesKey: loan, field: BUSINESS_GROWTH_FIELDS[loan], label: '贷款', unit: '元' }
    ],
    rows: []
  };
}

function buildGroup(line, rows, block, presentation, dateResult) {
  const deposit = line.fields.deposit;
  const loan = line.fields.loan;
  const depositField = BUSINESS_GROWTH_FIELDS[deposit];
  const loanField = BUSINESS_GROWTH_FIELDS[loan];
  const base = emptyGroup(line);
  if (dateResult.issue) return { ...base, issue: dateResult.issue };
  if (!dateResult.rows.length) return { ...base, issue: '暂无趋势日期数据' };
  if (!dateResult.rows.every(({ row }) => Object.prototype.hasOwnProperty.call(row, depositField)
    && Object.prototype.hasOwnProperty.call(row, loanField))) {
    return { ...base, issue: '缺少固定业务线字段' };
  }

  const depositUnit = fieldUnit(block, presentation, depositField);
  const loanUnit = fieldUnit(block, presentation, loanField);
  if (!depositUnit || !loanUnit) return { ...base, issue: '业务线金额缺少单位证据' };

  const normalizedRows = dateResult.rows.map(({ row, date }) => ({
    date,
    [deposit]: (() => { const value = finite(row[depositField]); return value === null ? null : value * UNIT_SCALE[depositUnit]; })(),
    [loan]: (() => { const value = finite(row[loanField]); return value === null ? null : value * UNIT_SCALE[loanUnit]; })()
  }));
  const hasValue = normalizedRows.some(row => row[deposit] !== null || row[loan] !== null);
  if (!hasValue) return { ...base, issue: '历史存贷款数据全为空' };

  return {
    ...base,
    status: 'READY',
    issue: '',
    rows: normalizedRows
  };
}

/**
 * 构建分行业务增长曲线模型；返回对象为新对象，不修改展示配置或原始响应。
 */
export function buildBusinessGrowthModel(sourcePresentation, model = {}) {
  const presentation = presentationOf(sourcePresentation);
  const disabled = {
    enabled: false,
    title: '业务增长曲线',
    sourceBlockId: null,
    groups: []
  };
  if (presentation.template !== BRANCH_TEMPLATE) return disabled;

  const ref = mainTrendRef(presentation);
  if (!ref) {
    return {
      enabled: true,
      title: '业务增长曲线',
      sourceBlockId: null,
      sourceComponentId: null,
      groups: BUSINESS_LINES.map(line => emptyGroup(line, '未找到分行主趋势绑定'))
    };
  }
  const block = sourceBlock(model);
  const rows = sourceRows(block);
  const dateResult = validDates(rows);
  const groups = BUSINESS_LINES.map(line => buildGroup(line, rows, block, presentation, dateResult));
  return {
    enabled: true,
    title: '业务增长曲线',
    sourceBlockId: SOURCE_BLOCK_ID,
    sourceComponentId: mainTrendEntry(presentation)?.component?.componentId || null,
    groups
  };
}

export { canonicalUnit, presentationOf };
