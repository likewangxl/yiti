/**
 * 对公经营总览的展示层派生指标。
 *
 * 科技、绿色、普惠等标签可能交叉，页面只展示各来源行的客户数、贷款和户均贷款，
 * 不把客群行相加，也不把相对条形图解释为全客群占比。
 */

const RANKING_METRIC_KEYS = Object.freeze(['deposit', 'increase', 'rate', 'nplRate']);

export function finiteMetric(value) {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value === 'boolean' || typeof value === 'object') return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  try {
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  } catch {
    return null;
  }
}

function round(value, digits = 2) {
  const number = finiteMetric(value);
  if (number === null) return null;
  const scale = 10 ** digits;
  return Math.round((number + Number.EPSILON) * scale) / scale;
}

/** 亿元 / 万户 = 万元 / 户。零客户、负贷款和缺失值不能形成户均值。 */
export function segmentAverageLoan(segment) {
  const customers = finiteMetric(segment?.customers);
  const loan = finiteMetric(segment?.loan);
  if (customers === null || customers <= 0 || loan === null || loan < 0) return null;
  return round(loan / customers, 4);
}

/**
 * 返回可展示的客群行。`loanScale` 只是当前返回行中的视觉宽度，不能当作客群占比。
 * 来源行即使有缺失或负数也保留，避免把“待接入”误显示成“没有该客群”。
 */
export function buildSegmentComparisons(segments = []) {
  const sourceRows = (Array.isArray(segments) ? segments : [])
    .filter(segment => segment && typeof segment === 'object');
  const nonNegativeLoans = sourceRows
    .map(segment => finiteMetric(segment.loan))
    .filter(value => value !== null && value >= 0);
  const maxLoan = Math.max(0, ...nonNegativeLoans);
  return sourceRows.map(segment => {
    const loan = finiteMetric(segment.loan);
    return {
      ...segment,
      averageLoan: segmentAverageLoan(segment),
      loanScale: loan !== null && loan >= 0 && maxLoan > 0 ? round(loan / maxLoan * 100) : null
    };
  });
}

function rowsFrom(model, key) {
  return Array.isArray(model?.[key]) ? model[key].filter(Boolean) : [];
}

export function buildCorporateLeadershipInsights(model = {}) {
  const rankings = rowsFrom(model, 'rankings');
  const growthValues = rankings.map(row => finiteMetric(row?.increase)).filter(value => value !== null);
  const missingMetricCount = rankings.length
    ? rankings.reduce((count, row) => count + RANKING_METRIC_KEYS.filter(key => finiteMetric(row?.[key]) === null).length, 0)
    : null;
  const targets = rowsFrom(model, 'targets');
  const validTargets = targets.filter(target => {
    const value = finiteMetric(target?.target);
    return value !== null && value > 0;
  });
  const achievedTargets = validTargets.filter(target => {
    const actual = finiteMetric(target?.actual);
    return actual !== null && actual >= Number(target.target);
  });
  const gapTargets = validTargets.filter(target => {
    const actual = finiteMetric(target?.actual);
    return actual !== null && actual < Number(target.target);
  });
  return {
    negativeGrowthCount: growthValues.filter(value => value < 0).length,
    growthComparableCount: growthValues.length,
    growthSampleCount: rankings.length,
    achievedTargetCount: validTargets.length ? achievedTargets.length : null,
    missingMetricCount,
    validTargetCount: validTargets.length,
    targetCount: targets.length,
    targetGapCount: validTargets.length ? gapTargets.length : null,
    targetGapNames: gapTargets.map(target => String(target?.name || '未命名指标'))
  };
}

export { RANKING_METRIC_KEYS };
