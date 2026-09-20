/**
 * 零售经营大屏的展示层派生指标。
 *
 * 这里只对已经进入零售 model 的值做比较，不补零、不把缺失字段当作负数，
 * 也不把不同目标的金额相加。客户分层的占比只在客户数和 AUM 均存在且非负
 * 的同一批样本内计算，避免用不同分母制造看似完整的客群画像。
 */

const RANKING_METRIC_KEYS = Object.freeze([
  'aum', 'deposit', 'average', 'increase', 'rate', 'nplRate'
]);
const LEGACY_RANKING_METRIC_KEYS = Object.freeze(['aum', 'increase', 'rate', 'nplRate']);

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

/**
 * 亿元 / 万户 = 万元 / 户。零客户、负资产和缺失值均不能形成户均值。
 */
export function segmentAverageAum(segment) {
  const customers = finiteMetric(segment?.customers);
  const aum = finiteMetric(segment?.aum);
  if (customers === null || customers <= 0 || aum === null || aum < 0) return null;
  return round(aum / customers, 4);
}

function validSegment(segment) {
  const customers = finiteMetric(segment?.customers);
  const aum = finiteMetric(segment?.aum);
  return customers !== null && customers >= 0 && aum !== null && aum >= 0
    ? { segment, customers, aum }
    : null;
}

/**
 * 客户占比和资产占比使用完全相同的有效分层样本，返回值单位为百分比。
 */
export function buildSegmentComparisons(segments = []) {
  const validRows = (Array.isArray(segments) ? segments : [])
    .map(validSegment)
    .filter(Boolean);
  const customerTotal = validRows.reduce((sum, row) => sum + row.customers, 0);
  const aumTotal = validRows.reduce((sum, row) => sum + row.aum, 0);
  return validRows.map(({ segment, customers, aum }) => ({
    ...segment,
    customers,
    aum,
    averageAum: segmentAverageAum({ customers, aum }),
    customerShare: customerTotal > 0 ? round(customers / customerTotal * 100) : null,
    aumShare: aumTotal > 0 ? round(aum / aumTotal * 100) : null
  }));
}

function rowsFrom(model, key) {
  return Array.isArray(model?.[key]) ? model[key].filter(Boolean) : [];
}

function rankingBoundMetricKeys(model, rankings = []) {
  const bindingFields = model?.rankingBoundFields
    || model?.rankingBinding?.fields
    || model?.rankingBindingFields;
  if (Array.isArray(bindingFields)) {
    return RANKING_METRIC_KEYS.filter(key => bindingFields.includes(key));
  }
  if (bindingFields && typeof bindingFields === 'object') {
    return RANKING_METRIC_KEYS.filter(key => Object.prototype.hasOwnProperty.call(bindingFields, key)
      && bindingFields[key] !== undefined && bindingFields[key] !== null
      && String(bindingFields[key]).trim() !== '');
  }
  const inferred = new Set();
  for (const row of rankings) {
    for (const key of RANKING_METRIC_KEYS) {
      if (Object.prototype.hasOwnProperty.call(row || {}, key)) inferred.add(key);
    }
  }
  if (inferred.has('deposit') && !inferred.has('aum')) {
    // Deposit rankings are the current real-data shape. Without binding
    // metadata, a null rate/npl property is still treated as unbound so it
    // cannot turn into a spurious business exception.
    return ['deposit', 'average', 'increase'].filter(key => inferred.has(key));
  }
  if (inferred.size) return RANKING_METRIC_KEYS.filter(key => inferred.has(key));
  // Models assembled by callers before ranking binding metadata existed use
  // the legacy AUM shape. Keep that interpretation for compatibility.
  return LEGACY_RANKING_METRIC_KEYS;
}

/**
 * 派生经营观察条需要的计数。`growthComparableCount` 和
 * `missingMetricCount` 让界面可以在没有净增样本时显示“不可判断”，避免把
 * 缺失数据误说成零家负增机构。
 */
export function buildRetailLeadershipInsights(model = {}) {
  const rankings = rowsFrom(model, 'rankings');
  const rankingMetricKeys = rankingBoundMetricKeys(model, rankings);
  const growthValues = rankings
    .map(row => finiteMetric(row?.increase))
    .filter(value => value !== null);
  const missingMetricCount = rankings.length
    ? rankings.reduce((count, row) => count + rankingMetricKeys
      .filter(key => finiteMetric(row?.[key]) === null).length, 0)
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
