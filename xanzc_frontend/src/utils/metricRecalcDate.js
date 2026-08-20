import dayjs from 'dayjs';

export const METRIC_RECALC_DATE_MESSAGES = Object.freeze({
  TODAY_OR_FUTURE: '当天及未来日期不可重算',
  OLDER_THAN_20_NON_MONTH_END: '超过20天只能选择月末'
});

const METRIC_JOB_KEYS = new Set([
  'LEVEL1_METRIC_CALC',
  'LEVEL2_METRIC_CALC',
  'LEVEL3_METRIC_CALC'
]);
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function asLocalDay(value) {
  if (value === null || value === undefined || value === '') return null;
  const parsed = dayjs.isDayjs(value) ? value : dayjs(value);
  if (!parsed.isValid()) return null;
  if (typeof value === 'string' && (!DATE_PATTERN.test(value) || parsed.format('YYYY-MM-DD') !== value)) return null;
  return parsed.startOf('day');
}

function todayDay(value) {
  return asLocalDay(value) || dayjs().startOf('day');
}

/** 返回本地日历日的今天，避免 UTC ISO 日期造成跨时区误判。 */
export function todayDate(now = dayjs()) {
  return todayDay(now).format('YYYY-MM-DD');
}

/** 返回本地日历日的昨日，用于指标重算弹窗默认值。 */
export function yesterdayDate(now = dayjs()) {
  return todayDay(now).subtract(1, 'day').format('YYYY-MM-DD');
}

export function isNaturalMonthEnd(value) {
  const date = asLocalDay(value);
  return Boolean(date && date.isSame(date.endOf('month'), 'day'));
}

/**
 * 返回指标重算日期的业务错误；空值/非法值由页面的必填校验负责提示。
 * 今天及未来不允许重算；历史超过 20 个自然日时只允许自然月末。
 */
export function getMetricRecalcDateError(value, now = dayjs()) {
  const date = asLocalDay(value);
  if (!date) return null;
  const base = todayDay(now);
  if (!date.isBefore(base, 'day')) return METRIC_RECALC_DATE_MESSAGES.TODAY_OR_FUTURE;
  const age = base.diff(date, 'day');
  if (age > 20 && !isNaturalMonthEnd(date)) return METRIC_RECALC_DATE_MESSAGES.OLDER_THAN_20_NON_MONTH_END;
  return null;
}

export function isMetricRecalcDateDisabled(value, now = dayjs()) {
  return Boolean(getMetricRecalcDateError(value, now));
}

export function isFutureDate(value, now = dayjs()) {
  const date = asLocalDay(value);
  return Boolean(date && date.isAfter(todayDay(now), 'day'));
}

export function isFutureDateDisabled(value, now = dayjs()) {
  return isFutureDate(value, now);
}

export function isMetricJobKey(jobKey) {
  return METRIC_JOB_KEYS.has(jobKey) || (typeof jobKey === 'string' && jobKey.startsWith('PERF_METRIC_'));
}
