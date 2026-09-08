/**
 * 经营全景展示层的纯计算。
 *
 * 数据适配层只负责把后端结果还原为固定模型，这里只决定展示顺序、图形
 * 选择和安全的数值边界，避免模板里把缺失值误当成 0 或把不同指标混算。
 */

export const CORE_KPI_KEYS = Object.freeze(['deposit', 'loan', 'customers', 'revenue']);

export const RANKING_METRICS = Object.freeze([
  { key: 'deposit', label: '存款余额', unit: '亿元' },
  { key: 'increase', label: '存款净增', unit: '亿元' },
  { key: 'average', label: '月均余额', unit: '亿元' }
]);

export function finiteMetric(value) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

export function coreKpis(kpis = []) {
  const source = Array.isArray(kpis) ? kpis : [];
  return CORE_KPI_KEYS.map(key => source.find(item => item?.key === key) || {
    key,
    label: '',
    value: null,
    unit: ''
  });
}

export function findKpi(kpis = [], key) {
  return (Array.isArray(kpis) ? kpis : []).find(item => item?.key === key) || {
    key,
    label: '',
    value: null,
    unit: ''
  };
}

export function hasTrendMetric(rows = [], key) {
  return (Array.isArray(rows) ? rows : []).some(row => finiteMetric(row?.[key]) !== null);
}

export function defaultTrendMetric(rows = []) {
  return hasTrendMetric(rows, 'depositIncrease') ? 'depositIncrease' : 'deposit';
}

export function rankingValue(row, metric = 'deposit') {
  if (!row || !['deposit', 'increase', 'average'].includes(metric)) return null;
  return finiteMetric(row[metric]);
}

/**
 * 保持源数据相对顺序作为同值的稳定排序；缺值放在明细末尾，供明细表显示
 * “—”，调用 topRankingRows 时会先排除缺值，不会把余额冒充净增/月均。
 */
export function sortRankingRows(rows = [], metric = 'deposit') {
  const source = Array.isArray(rows) ? rows : [];
  return source
    .map((row, index) => ({ row, index, value: rankingValue(row, metric) }))
    .sort((left, right) => {
      if (left.value === null && right.value === null) return left.index - right.index;
      if (left.value === null) return 1;
      if (right.value === null) return -1;
      return right.value - left.value || left.index - right.index;
    })
    .map(item => item.row);
}

export function topRankingRows(rows = [], metric = 'deposit', limit = 10) {
  const count = Math.max(0, Number(limit) || 0);
  return sortRankingRows(rows, metric)
    .filter(row => rankingValue(row, metric) !== null)
    .slice(0, count);
}

export function clampProgress(value) {
  const number = finiteMetric(value);
  return number === null ? null : Math.min(100, Math.max(0, number));
}
