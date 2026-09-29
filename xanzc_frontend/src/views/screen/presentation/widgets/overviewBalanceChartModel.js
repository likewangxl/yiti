import { canonicalUnit, formatDisplayMetric } from '../model/displayMetricsModel';

const AMOUNT_UNITS = Object.freeze(['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION']);
const BAR_DEFINITIONS = Object.freeze([
  { key: 'current', label: '当前' },
  { key: 'month', label: '上月末', comparisonKey: 'month' },
  { key: 'day', label: '上日', comparisonKey: 'day' }
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

/** 仅接受真实存在的 ISO 日，避免把任意比较字符串展示成月末或上日。 */
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

function isEarlierDate(referenceDate, currentDate) {
  return isValidOverviewDate(referenceDate)
    && isValidOverviewDate(currentDate)
    && referenceDate < currentDate;
}

function previousMonthEnd(value) {
  if (!isValidOverviewDate(value)) return '';
  const [year, month] = text(value).split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, 0));
  return `${String(date.getUTCFullYear()).padStart(4, '0')}-${String(date.getUTCMonth() + 1).padStart(2, '0')}-${String(date.getUTCDate()).padStart(2, '0')}`;
}

function previousDay(value) {
  if (!isValidOverviewDate(value)) return '';
  const [year, month, day] = text(value).split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day - 1));
  return `${String(date.getUTCFullYear()).padStart(4, '0')}-${String(date.getUTCMonth() + 1).padStart(2, '0')}-${String(date.getUTCDate()).padStart(2, '0')}`;
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

function comparisonLabel(definition, referenceDate, currentDate) {
  if (definition.key === 'month' && referenceDate === previousMonthEnd(currentDate)) return '上月末';
  if (definition.key === 'day' && referenceDate === previousDay(currentDate)) return '上日';
  return '基准日';
}

function pendingBar(definition, referenceDate = '', currentDate = '') {
  return {
    key: definition.key,
    label: referenceDate ? comparisonLabel(definition, referenceDate, currentDate) : definition.label,
    referenceDate,
    value: null,
    percent: 0,
    text: '待接入',
    state: 'PENDING'
  };
}

/**
 * 将摘要卡已有的当前总额与三维比较增量转换为只读余额条形图。
 * 基期只在增量和显式基准日均合法时还原，负基期保持待接入。
 */
export function buildOverviewBalanceChartModel(metric, displayUnit = '') {
  const unit = normalizedUnit(displayUnit, metric);
  const currentDate = text(metric?.date);
  const current = finite(metric?.baseValue);
  const currentReady = isValidOverviewDate(currentDate)
    && metric?.dateValid !== false
    && current !== null
    && current >= 0;

  const currentBar = currentReady
    ? { key: 'current', label: '当前', referenceDate: currentDate, value: current, text: displayText(current, unit), state: 'READY' }
    : pendingBar(BAR_DEFINITIONS[0], isValidOverviewDate(currentDate) ? currentDate : '', currentDate);
  const bars = [currentBar];

  for (const definition of BAR_DEFINITIONS.slice(1)) {
    const comparison = metric?.comparisons?.[definition.comparisonKey];
    const referenceDate = text(comparison?.referenceDate);
    const delta = finite(comparison?.baseValue);
    const historical = currentReady && isEarlierDate(referenceDate, currentDate) && delta !== null
      ? current - delta
      : null;
    if (historical === null || !Number.isFinite(historical) || historical < 0) {
      bars.push(pendingBar(definition, isValidOverviewDate(referenceDate) ? referenceDate : '', currentDate));
      continue;
    }
    bars.push({
      key: definition.key,
      label: comparisonLabel(definition, referenceDate, currentDate),
      referenceDate,
      value: historical,
      text: displayText(historical, unit),
      state: 'READY'
    });
  }

  const readyValues = bars.map(item => item.value).filter(value => value !== null);
  const maxValue = readyValues.length ? Math.max(...readyValues) : null;
  const normalizedBars = bars.map(item => ({
    ...item,
    percent: item.value === null || maxValue === null || maxValue === 0
      ? 0
      : Number(((item.value / maxValue) * 100).toFixed(2))
  }));

  return {
    state: currentBar.state === 'READY' ? 'READY' : 'PENDING',
    unit,
    currentDate: isValidOverviewDate(currentDate) ? currentDate : '',
    maxValue,
    bars: normalizedBars
  };
}
