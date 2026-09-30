import { canonicalUnit, formatDisplayMetric } from '../model/displayMetricsModel';

const AMOUNT_UNITS = Object.freeze(['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION']);
const AMOUNT_UNIT_META = Object.freeze({
  YUAN: { scale: 1, suffix: '元' },
  TEN_THOUSAND: { scale: 1e4, suffix: '万' },
  HUNDRED_MILLION: { scale: 1e8, suffix: '亿' }
});
const BAR_DEFINITIONS = Object.freeze([
  { key: 'day', label: '较昨日', comparisonKey: 'day' },
  { key: 'month', label: '较上月', comparisonKey: 'month' },
  { key: 'year', label: '较上年', comparisonKey: 'year' }
]);

function finite(value) {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  if (typeof value !== 'string' || value.trim() === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

/** 仅接受真实存在的 ISO 日，避免把任意比较字符串展示成业务基准日。 */
export function isValidOverviewDate(value) {
  const date = text(value);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return false;
  const [year, month, day] = date.split('-').map(Number);
  if (year < 1 || month < 1 || month > 12 || day < 1 || day > 31) return false;
  const parsed = new Date(Date.UTC(year, month - 1, day));
  return parsed.getUTCFullYear() === year
    && parsed.getUTCMonth() === month - 1
    && parsed.getUTCDate() === day;
}

function previousDay(value) {
  if (!isValidOverviewDate(value)) return '';
  const [year, month, day] = text(value).split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day - 1));
  return [date.getUTCFullYear(), date.getUTCMonth() + 1, date.getUTCDate()]
    .map((part, index) => String(part).padStart(index === 0 ? 4 : 2, '0')).join('-');
}

function previousMonthEnd(value) {
  if (!isValidOverviewDate(value)) return '';
  const [year, month] = text(value).split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, 0));
  return [date.getUTCFullYear(), date.getUTCMonth() + 1, date.getUTCDate()]
    .map((part, index) => String(part).padStart(index === 0 ? 4 : 2, '0')).join('-');
}

function previousYearEnd(value) {
  if (!isValidOverviewDate(value)) return '';
  const year = Number(text(value).slice(0, 4)) - 1;
  return String(year).padStart(4, '0') + '-12-31';
}

function expectedReferenceDate(definition, currentDate) {
  if (definition.key === 'day') return previousDay(currentDate);
  if (definition.key === 'month') return previousMonthEnd(currentDate);
  return previousYearEnd(currentDate);
}

function normalizedUnit(displayUnit, metric) {
  const requested = canonicalUnit(displayUnit);
  if (AMOUNT_UNITS.includes(requested)) return requested;
  const source = canonicalUnit(metric?.unit || metric?.sourceUnit);
  return AMOUNT_UNITS.includes(source) ? source : 'YUAN';
}

function displayText(value, unit) {
  if (value === null) return '待接入';
  return formatDisplayMetric(value, {
    displayUnit: unit,
    decimals: 2,
    thousandsSeparator: true
  }, 'YUAN').text;
}

function shortGrowthText(value, unit) {
  if (value === null || value === undefined) return '—';
  const meta = AMOUNT_UNIT_META[unit] || AMOUNT_UNIT_META.YUAN;
  const amount = value / meta.scale;
  const rounded = Math.round(Math.abs(amount) * 100) / 100;
  const magnitude = String(rounded);
  const sign = amount > 0 ? '+' : amount < 0 ? '-' : '';
  return sign + magnitude + meta.suffix;
}

function pendingBar(definition, referenceDate = '') {
  return {
    key: definition.key,
    label: definition.label,
    referenceDate,
    value: null,
    text: '待接入',
    growth: null,
    growthText: '—',
    percent: 0,
    state: 'PENDING'
  };
}

/**
 * 将摘要卡当前余额和比较增量转换为日/月/年三个基期条。
 * 基期只在比较增量和项目约定的基准日都合法时还原；比较数学仍保留原始增量。
 */
export function buildOverviewBalanceChartModel(metric, displayUnit = '') {
  const unit = normalizedUnit(displayUnit, metric);
  const currentDate = text(metric?.date);
  if (metric?.comparisonConfigured === true && metric?.comparisons === null) {
    return {
      state: 'DISABLED',
      unit,
      currentDate: isValidOverviewDate(currentDate) ? currentDate : '',
      maxValue: null,
      bars: [],
      comparisonConfigured: true
    };
  }
  const current = finite(metric?.baseValue);
  const currentReady = isValidOverviewDate(currentDate)
    && metric?.dateValid !== false
    && current !== null
    && current >= 0;

  const bars = BAR_DEFINITIONS.map(definition => {
    const comparison = metric?.comparisons?.[definition.comparisonKey];
    const referenceDate = text(comparison?.referenceDate);
    const delta = finite(comparison?.baseValue);
    const expectedDate = expectedReferenceDate(definition, currentDate);
    const dateReady = isValidOverviewDate(referenceDate) && referenceDate === expectedDate;
    const historical = currentReady && dateReady && delta !== null ? current - delta : null;
    if (historical === null || !Number.isFinite(historical) || historical < 0) {
      return pendingBar(definition, isValidOverviewDate(referenceDate) ? referenceDate : '');
    }
    return {
      key: definition.key,
      label: definition.label,
      referenceDate,
      value: historical,
      text: displayText(historical, unit),
      growth: delta,
      growthText: shortGrowthText(delta, unit),
      percent: 0,
      state: 'READY'
    };
  });

  const readyValues = bars.map(item => item.value).filter(value => value !== null);
  const maxValue = readyValues.length ? Math.max(...readyValues) : null;
  const normalizedBars = bars.map(item => ({
    ...item,
    percent: item.value === null || maxValue === null || maxValue === 0
      ? 0
      : Number(((item.value / maxValue) * 100).toFixed(2))
  }));

  return {
    state: currentReady ? 'READY' : 'PENDING',
    unit,
    currentDate: isValidOverviewDate(currentDate) ? currentDate : '',
    maxValue,
    bars: normalizedBars,
    comparisonConfigured: metric?.comparisonConfigured === true
  };
}
