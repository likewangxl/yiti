/**
 * 业务增长曲线的纯时间采样器。
 *
 * 输入必须是 buildBusinessGrowthModel 已统一为“元”的行。这里仅按日期筛选、
 * 排序或按月取最后一个有效余额，绝不重新换算金额，也不为了补齐图形而造行。
 */

export const BUSINESS_GROWTH_PERIODS = Object.freeze([
  Object.freeze({ key: 'WEEK', label: '近七日' }),
  Object.freeze({ key: 'MONTH', label: '近一月' }),
  Object.freeze({ key: 'YEAR', label: '近一年' })
]);

export const DEFAULT_BUSINESS_GROWTH_PERIOD = 'WEEK';

const PERIOD_KEYS = new Set(BUSINESS_GROWTH_PERIODS.map(item => item.key));
const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function parseDate(value) {
  const dateText = text(value);
  const match = DATE_PATTERN.exec(dateText);
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  // Date.UTC treats years 0..99 as 1900..1999; setUTCFullYear keeps the
  // strict YYYY-MM-DD validation correct even for the full four-digit range.
  const parsed = new Date(0);
  parsed.setUTCHours(0, 0, 0, 0);
  parsed.setUTCFullYear(year, month - 1, day);
  const timestamp = parsed.getTime();
  if (parsed.getUTCFullYear() !== year
    || parsed.getUTCMonth() !== month - 1
    || parsed.getUTCDate() !== day) return null;
  return { text: dateText, timestamp, year, month, day };
}

function rowDate(row) {
  if (!row || typeof row !== 'object' || Array.isArray(row)) return '';
  return text(row.date ?? row.dataDate ?? row.data_date);
}

function normalizedRows(rows) {
  if (!Array.isArray(rows)) return [];
  return rows
    .map((row, index) => {
      const parsed = parseDate(rowDate(row));
      if (!parsed) return null;
      // Keep every normalized metric unchanged. The index only stabilizes a source
      // order tie when a caller accidentally supplies the same date twice.
      return { row: { ...row, date: parsed.text }, parsed, index };
    })
    .filter(Boolean)
    .sort((left, right) => left.parsed.timestamp - right.parsed.timestamp || left.index - right.index);
}

function periodKey(period) {
  const value = text(period).toUpperCase();
  return PERIOD_KEYS.has(value) ? value : DEFAULT_BUSINESS_GROWTH_PERIOD;
}

function anchorFromOptions(rows, options) {
  const entries = normalizedRows(rows);
  const configured = typeof options === 'string'
    ? options
    : options?.anchorDate ?? options?.dataDate ?? options?.modelDate ?? '';
  const configuredDate = parseDate(configured);
  if (configuredDate) return configuredDate;
  return entries.at(-1)?.parsed || null;
}

function addDays(parsed, days) {
  const date = new Date(parsed.timestamp);
  date.setUTCDate(date.getUTCDate() + days);
  return date.getTime();
}

function monthKey(year, month) {
  return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}`;
}

function shiftMonth(year, month, offset) {
  const zeroBased = year * 12 + (month - 1) + offset;
  return { year: Math.floor(zeroBased / 12), month: (zeroBased % 12 + 12) % 12 + 1 };
}

function selectYear(entries, anchor) {
  const selected = [];
  for (let offset = -11; offset <= 0; offset += 1) {
    const target = shiftMonth(anchor.year, anchor.month, offset);
    const key = monthKey(target.year, target.month);
    // The current month may contain rows after the server's declared dataDate.
    // They are outside the anchor range and must not become a future point.
    const monthEntries = entries.filter(({ parsed }) => parsed.text.slice(0, 7) === key
      && parsed.timestamp <= anchor.timestamp);
    if (monthEntries.length) selected.push(monthEntries.at(-1).row);
  }
  return selected;
}

/**
 * 选择单个业务线在指定时间粒度下的曲线行。
 *
 * WEEK/MONTH 使用锚点含当天的自然日窗口；YEAR 使用锚点月份及前 11 个月，
 * 每个月只保留该月截至锚点范围的最后有效余额。服务端缺失的日/月保持缺口。
 */
export function selectBusinessGrowthRows(rows, period = DEFAULT_BUSINESS_GROWTH_PERIOD, options = {}) {
  const entries = normalizedRows(rows);
  const anchor = anchorFromOptions(rows, options);
  if (!anchor) return [];

  const normalizedPeriod = periodKey(period);
  if (normalizedPeriod === 'YEAR') return selectYear(entries, anchor);

  const days = normalizedPeriod === 'MONTH' ? 30 : 7;
  const startTimestamp = addDays(anchor, -(days - 1));
  return entries
    .filter(({ parsed }) => parsed.timestamp >= startTimestamp && parsed.timestamp <= anchor.timestamp)
    .map(({ row }) => row);
}

/**
 * 将 buildBusinessGrowthModel 的结果切换到一个时间粒度；所有源对象保持不变。
 */
export function buildBusinessGrowthPeriodModel(source, period = DEFAULT_BUSINESS_GROWTH_PERIOD, options = {}) {
  const normalizedPeriod = periodKey(period);
  const groups = Array.isArray(source?.groups) ? source.groups : [];
  const configuredAnchor = typeof options === 'string'
    ? options
    : options?.anchorDate ?? options?.dataDate ?? source?.dataDate ?? source?.anchorDate ?? '';
  const anchor = anchorFromOptions(groups.flatMap(group => Array.isArray(group?.rows) ? group.rows : []), configuredAnchor);

  return {
    ...(source && typeof source === 'object' ? source : {}),
    period: normalizedPeriod,
    anchorDate: anchor?.text || '',
    groups: groups.map(group => ({
      ...group,
      rows: selectBusinessGrowthRows(group?.rows, normalizedPeriod, { anchorDate: anchor?.text || configuredAnchor })
    }))
  };
}

export const filterBusinessGrowthRows = selectBusinessGrowthRows;
export const normalizeBusinessGrowthPeriod = periodKey;
