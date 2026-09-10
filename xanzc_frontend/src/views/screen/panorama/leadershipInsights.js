/**
 * 分行经营大屏的展示层诊断。
 *
 * 这里只从已经适配到 model 的机构、排名、汇总和趋势字段派生状态，绝不把
 * 缺失值转成 0，也不把下级机构相加后冒充城市/全辖汇总。金额目标和完成率
 * 是两个不同口径：targetGap 只有金额目标与存款余额都存在时才会出现。
 */

const RATE_KEYS = Object.freeze([
  'rate', 'targetRate', 'completionRate', 'targetCompletionRate', 'completion'
]);
const DEPOSIT_TARGET_KEYS = Object.freeze([
  'depositTarget', 'targetDeposit', 'deposit_target', 'target'
]);
const AMOUNT_FRACTION_DIGITS = 6;

export function finiteMetric(value) {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value === 'boolean' || (typeof value !== 'number' && typeof value !== 'string')) return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function summaryNumber(value) {
  const number = finiteMetric(value);
  return number === null ? null : new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(number);
}

export function summarizeDepositMovement(value) {
  const number = finiteMetric(value);
  if (number === null) return { text: '存款较上月暂无数据', state: 'unknown', value: null };
  if (number < 0) return { text: `存款较上月净减${summaryNumber(Math.abs(number))}亿元`, state: 'down', value: number };
  if (number > 0) return { text: `存款较上月净增${summaryNumber(number)}亿元`, state: 'up', value: number };
  return { text: '存款较上月持平', state: 'flat', value: 0 };
}

export function summarizeTargetDistance(value) {
  const number = finiteMetric(value);
  if (number === null) return { text: '目标完成率暂无数据', state: 'unknown', value: null };
  const gap = round(number - 100, 1);
  // 是否达标取决于原始完成率，不能因显示舍入而把99.99%判为已达到目标。
  if (number < 100) return { text: gap === 0 ? '距目标还差不到0.1个百分点' : `距目标还差${summaryNumber(Math.abs(gap))}个百分点`, state: 'below', value: gap };
  if (number > 100) return { text: gap === 0 ? '超目标不到0.1个百分点' : `超目标${summaryNumber(gap)}个百分点`, state: 'above', value: gap };
  return { text: '已达到目标', state: 'achieved', value: 0 };
}

export function summarizeProvinceTargetStatus(insights = {}) {
  const available = Number(insights?.coverage?.rate?.available) || 0;
  const total = Number(insights?.coverage?.rate?.total) || 0;
  if (!available || !total) {
    return { hasData: false, headline: '目标机构暂无数据', detail: '暂无可用目标完成率' };
  }
  const below = Number(insights?.statusCounts?.below) || 0;
  const unknown = Math.max(0, total - available);
  return {
    hasData: true,
    headline: `未完成目标机构${below}家`,
    detail: `完成率低于100%，已提供${available}/${total}家`
  };
}

export function summarizeCityTargetStatus(insights = {}) {
  const sampleSize = Number(insights?.sampleSize) || 0;
  if (!sampleSize) {
    return { hasData: false, achievedText: '暂无', belowText: '', unknownText: '暂无' };
  }
  const available = Number(insights?.coverage?.rate?.available) || 0;
  if (!available) {
    return { hasData: false, achievedText: '暂无', belowText: '', unknownText: '暂无' };
  }
  const counts = insights?.statusCounts || {};
  return {
    hasData: true,
    achievedText: `已完成目标${Number(counts.achieved) || 0}家`,
    belowText: `未完成目标${Number(counts.below) || 0}家`,
    unknownText: `未提供${Number(counts.unknown) || 0}家`
  };
}

function round(value, digits = 2) {
  const number = finiteMetric(value);
  if (number === null) return null;
  const scale = 10 ** digits;
  return Math.round((number + Number.EPSILON) * scale) / scale;
}

function roundAmount(value) {
  return round(value, AMOUNT_FRACTION_DIGITS);
}

function sourceObjects(row) {
  if (!row || typeof row !== 'object') return [];
  const metrics = row.metrics && typeof row.metrics === 'object' ? row.metrics : {};
  return [metrics, row];
}

function firstMetric(row, keys) {
  for (const source of sourceObjects(row)) {
    for (const key of keys) {
      const value = finiteMetric(source?.[key]);
      if (value !== null) return value;
    }
  }
  return null;
}

function firstRaw(row, keys) {
  for (const source of sourceObjects(row)) {
    for (const key of keys) {
      if (source?.[key] !== undefined && source?.[key] !== null && source?.[key] !== '') return source[key];
    }
  }
  return null;
}

function unitLooksPercent(value) {
  const unit = String(value || '').trim().toUpperCase();
  return unit === '%' || unit === 'PERCENT' || unit === 'RATIO' || unit.includes('百分');
}

export function resolveDeposit(row) {
  return firstMetric(row, ['deposit', 'depositBalance', 'balance']);
}

export function resolveCompletionRate(row) {
  return firstMetric(row, RATE_KEYS);
}

/**
 * `target` 在 dataAdapter 的 branches 契约中是金额字段。演示数据若标记为
 * demoOnly，则没有真实金额目标来源，不在经营矩阵里显示伪造的金额差。
 */
export function resolveDepositTarget(row) {
  if (row?.demoOnly || row?.demo || row?.isDemo) return null;
  const target = firstMetric(row, DEPOSIT_TARGET_KEYS);
  if (target === null) return null;
  const unit = firstRaw(row, ['depositTargetUnit', 'targetDepositUnit', 'targetUnit', 'target_unit']);
  if (unitLooksPercent(unit)) return null;
  // The normalized branches contract defines `target` as an amount. Explicit
  // aliases and a declared amount unit are accepted as well; no value-based
  // comparison with completion rate is used to guess a unit.
  return target;
}

function rowName(row) {
  return String(row?.orgName || row?.name || row?.org_name || row?.orgCode || '—');
}

function rowCode(row, fallback = '') {
  return String(row?.orgCode || row?.org_code || fallback || '');
}

function trendRows(row) {
  return Array.isArray(row?.trend) ? row.trend : [];
}

/**
 * 只使用有效的、按日期排序的存款点。两点不足以支持连续下降判断，首末
 * 变化也会保留为 null，从而让 UI 可以明确显示“样本不足”。
 */
export function deriveTrendObservation(rows = []) {
  const values = (Array.isArray(rows) ? rows : [])
    .map((row, index) => ({
      date: String(row?.date ?? row?.label ?? '').trim(),
      value: finiteMetric(row?.deposit ?? row?.depositBalance),
      index
    }))
    .filter(item => item.value !== null)
    .sort((left, right) => String(left.date).localeCompare(String(right.date)) || left.index - right.index);
  if (!values.length) {
    return { first: null, last: null, change: null, firstDate: '', lastDate: '', state: '无趋势数据', points: 0 };
  }
  const first = values[0];
  const last = values.at(-1);
  const comparableDates = values.every(item => item.date && isComparableDate(item.date))
    && new Set(values.map(item => item.date)).size === values.length;
  if (values.length > 1 && !comparableDates) {
    return { first: first.value, last: last.value, change: null, firstDate: first.date, lastDate: last.date, state: '日期不可比', points: values.length };
  }
  if (values.length < 2) {
    return { first: first.value, last: last.value, change: null, firstDate: first.date, lastDate: last.date, state: '样本不足', points: values.length };
  }
  const deltas = values.slice(1).map((item, index) => item.value - values[index].value);
  const change = roundAmount(last.value - first.value);
  const latestDelta = deltas.at(-1);
  const allDeclining = values.length >= 3 && deltas.every(delta => delta < 0);
  const state = allDeclining ? '连续下降' : latestDelta < 0 ? '最新回落' : latestDelta > 0 ? '最新回升' : '最新持平';
  return {
    first: first.value,
    last: last.value,
    change,
    firstDate: first.date,
    lastDate: last.date,
    state,
    points: values.length
  };
}

function isComparableDate(value) {
  const text = String(value || '').trim();
  if (!text) return false;
  if (/^D\d+$/i.test(text) || /^\d+$/.test(text)) return true;
  if (!/^\d{4}[-/]\d{1,2}(?:[-/]\d{1,2})?/.test(text)) return false;
  return Number.isFinite(Date.parse(text));
}

function rankValue(row, key) {
  if (key === 'deposit') return resolveDeposit(row);
  if (key === 'loan') return firstMetric(row, ['loan']);
  if (key === 'customers') return firstMetric(row, ['customers', 'customerCount']);
  if (key === 'increase') return firstMetric(row, ['increase', 'depositIncrease']);
  return firstMetric(row, [key]);
}

function statusFor(rate) {
  if (rate === null) return 'unknown';
  return rate >= 100 ? 'achieved' : 'below';
}

function mergeRows(model = {}) {
  const institutions = Array.isArray(model?.institutions) ? model.institutions.filter(Boolean) : [];
  const rankings = Array.isArray(model?.rankings) ? model.rankings.filter(Boolean) : [];
  const rankingByCode = new Map(rankings.map(item => [rowCode(item), item]).filter(([code]) => code));
  if (institutions.length) {
    return institutions.map((item, index) => {
      const ranking = rankingByCode.get(rowCode(item));
      return ranking ? { ...ranking, ...item, metrics: { ...(ranking.metrics || {}), ...(item.metrics || {}) }, trend: item.trend || ranking.trend } : item;
    });
  }
  return rankings;
}

function normalizeRow(row, index = 0) {
  const deposit = resolveDeposit(row);
  const rate = resolveCompletionRate(row);
  const target = resolveDepositTarget(row);
  const trend = deriveTrendObservation(trendRows(row));
  const increase = firstMetric(row, ['increase', 'depositIncrease']);
  const periodChange = firstMetric(row, ['change']);
  const declining = (increase !== null && increase < 0)
    || (periodChange !== null && periodChange < 0)
    || trend.state === '连续下降'
    || trend.state === '最新回落';
  return {
    ...row,
    orgCode: rowCode(row, `row-${index}`),
    name: rowName(row),
    cityCode: String(row?.cityCode || row?.city_code || ''),
    deposit,
    loan: rankValue(row, 'loan'),
    customers: rankValue(row, 'customers'),
    rate,
    target,
    targetGap: deposit !== null && target !== null ? roundAmount(deposit - target) : null,
    increase,
    periodChange,
    trend,
    trendState: trend.state,
    declining,
    decliningEvidence: increase !== null || periodChange !== null || trend.change !== null,
    status: statusFor(rate)
  };
}

function coverage(rows, key) {
  const total = rows.length;
  const available = rows.filter(row => rankValue(row, key) !== null).length;
  return { available, total };
}

function countStatuses(rows) {
  return rows.reduce((counts, row) => {
    counts[row.status] += 1;
    return counts;
  }, { achieved: 0, below: 0, unknown: 0 });
}

function findKpi(kpis, keys) {
  const list = Array.isArray(kpis) ? kpis : [];
  return list.find(item => keys.includes(item?.key)) || null;
}

function diagnosticKpis(kpis, summary = null) {
  const source = summary && Array.isArray(summary.kpis) ? summary.kpis : kpis;
  const increaseKpi = findKpi(source, ['depositIncrease']);
  const rateKpi = findKpi(source, ['rate', 'targetRate', 'completionRate', 'targetCompletionRate', 'target']);
  const increase = finiteMetric(increaseKpi?.value);
  const rate = finiteMetric(rateKpi?.value);
  return {
    depositIncrease: increase,
    targetRate: rate,
    targetGapPoints: rate === null ? null : round(rate - 100, 1)
  };
}

function sortRows(rows) {
  return [...rows].sort((left, right) => {
    if (left.deposit === null && right.deposit === null) return 0;
    if (left.deposit === null) return 1;
    if (right.deposit === null) return -1;
    return right.deposit - left.deposit;
  });
}

function buildBase(rows, kpis, summary = null) {
  const normalized = rows.map(normalizeRow);
  const diagnostics = diagnosticKpis(kpis, summary);
  const decliningRows = normalized.filter(row => row.decliningEvidence);
  return {
    rows: sortRows(normalized),
    sampleSize: normalized.length,
    diagnostics: {
      ...diagnostics,
      decliningCount: decliningRows.length ? decliningRows.filter(row => row.declining).length : null
    },
    decliningCoverage: { available: decliningRows.length, total: normalized.length },
    statusCounts: countStatuses(normalized),
    coverage: {
      deposit: coverage(normalized, 'deposit'),
      loan: coverage(normalized, 'loan'),
      customers: coverage(normalized, 'customers'),
      rate: coverage(normalized, 'rate'),
      increase: coverage(normalized, 'increase')
    }
  };
}

export function buildProvinceInsights(model = {}) {
  return buildBase(mergeRows(model), model?.kpis);
}

function median(values) {
  const numbers = values.filter(value => value !== null).sort((left, right) => left - right);
  if (!numbers.length) return null;
  const middle = Math.floor(numbers.length / 2);
  return numbers.length % 2 ? numbers[middle] : (numbers[middle - 1] + numbers[middle]) / 2;
}

export function buildCityInsights({ cityCode = '', citySummary = null, institutions = [] } = {}) {
  const code = String(cityCode || '');
  const rows = (Array.isArray(institutions) ? institutions : [])
    .filter(item => String(item?.cityCode || item?.city_code || '') === code);
  const base = buildBase(rows, [], citySummary);
  const deposits = base.rows.map(row => row.deposit);
  const depositMedian = median(deposits);
  const ranked = base.rows.filter(row => row.deposit !== null);
  const rankedCodes = new Map();
  ranked.forEach((row, index) => {
    const previous = ranked[index - 1];
    const rank = index === 0 || row.deposit !== previous.deposit ? index + 1 : rankedCodes.get(previous.orgCode).rank;
    rankedCodes.set(row.orgCode, { rank, total: ranked.length });
  });
  return {
    ...base,
    cityCode: code,
    cityMedian: depositMedian,
    citySummary,
    selected(orgCode) {
      const row = base.rows.find(item => item.orgCode === String(orgCode || '')) || null;
      if (!row) return null;
      const rank = rankedCodes.get(row.orgCode) || { rank: null, total: ranked.length };
      return {
        ...row,
        rank: rank.rank,
        total: rank.total,
        median: depositMedian,
        medianDifference: row.deposit === null || depositMedian === null ? null : roundAmount(row.deposit - depositMedian)
      };
    }
  };
}

export function statusLabel(status) {
  return ({ achieved: '达标', below: '未达标', unknown: '未知' })[status] || '未知';
}

export function coverageLabel(item) {
  if (!item || !Number.isFinite(item.available) || !Number.isFinite(item.total)) return '—';
  return `${item.available}/${item.total}`;
}
