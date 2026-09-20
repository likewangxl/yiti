/**
 * 零售大屏展示层的可核对派生值。
 *
 * 这里不补齐业务数据，也不把机构快照加总成全辖结果。所有值都来自已经
 * 进入零售 model 的字段，派生结果只用于标签、筛选和有限的展示摘要。
 */

export function finiteMetric(value) {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value === 'boolean' || typeof value === 'object') return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function round(value, digits = 2) {
  const number = finiteMetric(value);
  if (number === null) return null;
  const scale = 10 ** digits;
  return Math.round((number + Number.EPSILON) * scale) / scale;
}

function firstMetric(...values) {
  for (const value of values) {
    const number = finiteMetric(value);
    if (number !== null) return number;
  }
  return null;
}

function textValue(...values) {
  for (const value of values) {
    if (value !== null && value !== undefined && String(value).trim() !== '') return String(value);
  }
  return '';
}

function normalizedToken(value) {
  return String(value || '').trim().toUpperCase().replace(/[\s-]+/g, '_');
}

/**
 * 机构目录里的 PRIMARY/SECONDARY_BRANCH 只用于归入“已分类经营机构”，
 * 不能据此推导同层可比或允许跨层级加总；其余值保留为“其他待分类”。
 */
export function classifyInstitution(row = {}) {
  const tokens = [row?.orgNature, row?.operatingLevel, row?.institutionType, row?.orgType]
    .map(normalizedToken)
    .filter(Boolean);
  if (tokens.some(token => token === 'PRIMARY'
    || token === 'PRIMARY_BRANCH'
    || token === 'SECONDARY_BRANCH'
    || token === 'SECONDARY'
    || token === 'BRANCH'
    || token.includes('PRIMARY_BRANCH')
    || token.includes('SECONDARY_BRANCH'))) return 'primary';
  return 'other';
}

function normalizeInstitutionRow(row = {}) {
  const balance = firstMetric(row.deposit, row.balance, row.depositBalance);
  const average = firstMetric(row.average, row.depositAverage, row.averageDeposit, row.monthAverage);
  return {
    ...row,
    deposit: balance,
    average,
    difference: balance === null || average === null ? null : average - balance,
    institutionType: classifyInstitution(row),
    date: textValue(row.date, row.dataDate, row.periodDate)
  };
}

export function buildDepositComparison(input = {}) {
  const balance = firstMetric(input.balance, input.deposit, input.depositBalance);
  const average = firstMetric(input.average, input.depositAverage, input.averageDeposit, input.monthAverage);
  return {
    balance,
    average,
    difference: balance === null || average === null ? null : average - balance,
    differenceLabel: '月日均 − 时点余额',
    differenceMeaning: '口径对照，不代表净增',
    isNetIncrease: false
  };
}

/**
 * 构造机构存款对照表数据。排序保留来源顺序，避免把混合机构的数值
 * 顺序包装成业务名次；页面只允许余额/月日均两个观测指标切换。
 */
export function buildInstitutionDepositView(rows = [], selectedFilter) {
  const normalizedRows = (Array.isArray(rows) ? rows : [])
    .filter(row => row && typeof row === 'object')
    .map(normalizeInstitutionRow);
  const hasPrimary = normalizedRows.some(row => row.institutionType === 'primary');
  const hasOther = normalizedRows.some(row => row.institutionType === 'other');
  const defaultFilter = hasPrimary ? 'primary' : 'all';
  const filter = ['primary', 'other', 'all'].includes(selectedFilter)
    ? selectedFilter
    : defaultFilter;
  const filteredRows = normalizedRows.filter(row => filter === 'all' || row.institutionType === filter);
  const mixed = hasPrimary && hasOther;
  return {
    rows: normalizedRows,
    filteredRows,
    filter,
    defaultFilter,
    mixed,
    title: '机构存款对比',
    subtitle: mixed ? '机构类型混合，仅作数值对照，不汇总、不显示业务名次' : '保留来源顺序，仅作数值对照',
    filterOptions: [
      { key: 'primary', label: '已分类经营机构', count: normalizedRows.filter(row => row.institutionType === 'primary').length },
      { key: 'other', label: '其他待分类', count: normalizedRows.filter(row => row.institutionType === 'other').length },
      { key: 'all', label: '全部', count: normalizedRows.length }
    ]
  };
}

function trendLabel(row) {
  return textValue(row?.date, row?.label, row?.dataDate, row?.periodDate);
}

function trendMetric(row) {
  return firstMetric(row?.deposit, row?.balance, row?.depositBalance);
}

/**
 * 趋势摘要只陈述同一序列的观察区间，不把区间首末差说成环比。
 */
export function buildTrendSummary(rows = []) {
  const source = (Array.isArray(rows) ? rows : [])
    .filter(row => row && typeof row === 'object')
    .map(row => ({ row, label: trendLabel(row), value: trendMetric(row) }))
    .filter(item => item.value !== null);
  if (!source.length) {
    return {
      startLabel: '',
      endLabel: '',
      depositChange: null,
      observationLabel: '观察区间待确认',
      peak: null,
      trough: null
    };
  }
  const first = source[0];
  const last = source[source.length - 1];
  const peak = source.reduce((current, item) => item.value > current.value ? item : current, source[0]);
  const trough = source.reduce((current, item) => item.value < current.value ? item : current, source[0]);
  return {
    startLabel: first.label,
    endLabel: last.label,
    depositChange: last.value - first.value,
    observationLabel: first.label && last.label
      ? `观察区间 ${first.label} 至 ${last.label}`
      : '观察区间待确认',
    peak: { date: peak.label, value: peak.value },
    trough: { date: trough.label, value: trough.value }
  };
}

function percent(available, total) {
  if (!total) return null;
  return round(available / total * 100);
}

function coverage(available, total) {
  return { available, total, percent: percent(available, total) };
}

export function buildRetailDisplayInsights(model = {}) {
  const rankings = Array.isArray(model?.rankings)
    ? model.rankings.filter(row => row && typeof row === 'object').map(normalizeInstitutionRow)
    : [];
  const institutions = Array.isArray(model?.institutions)
    ? model.institutions.filter(row => row && typeof row === 'object')
    : [];
  const institutionTotal = institutions.length || rankings.length;
  const institutionAvailable = rankings.filter(row => row.deposit !== null || row.average !== null).length;
  const datedRows = rankings.filter(row => row.date);
  const sourceDataDate = textValue(
    model?.sourceQualities?.retailRanking?.dataDate,
    model?.sourceQualities?.rankings?.dataDate,
    model?.sourceQualities?.retailDeposit?.dataDate
  );
  const dataDate = textValue(sourceDataDate, datedRows[0]?.date, model?.dataDate, model?.dateDate);
  const dateCoverageAvailable = sourceDataDate && rankings.length && !datedRows.length
    ? rankings.length
    : datedRows.length;

  const targets = Array.isArray(model?.targets)
    ? model.targets.filter(row => row && typeof row === 'object')
    : [];
  const validTargets = targets.filter(target => {
    const value = finiteMetric(target.target);
    return value !== null && value > 0;
  });
  const achievedTargets = validTargets.filter(target => {
    const actual = finiteMetric(target.actual);
    return actual !== null && actual >= Number(target.target);
  });

  return {
    institutionCoverage: coverage(institutionAvailable, institutionTotal),
    averageBelowBalanceCount: rankings.filter(row => row.average !== null && row.deposit !== null && row.average < row.deposit).length,
    validTargetCount: validTargets.length,
    targetCount: targets.length,
    achievedTargetCount: achievedTargets.length,
    dataDateCoverage: {
      date: dataDate,
      ...coverage(dateCoverageAvailable, rankings.length || institutionTotal),
      sourceLevel: Boolean(sourceDataDate && !datedRows.length)
    }
  };
}

export { normalizeInstitutionRow };
