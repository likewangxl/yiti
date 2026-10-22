/**
 * 大屏期间比较存在 canvasStyle.periodComparisons，不写入 presentation.display.comparisons。
 * 后者是严格 DTO，未知字段会导致画布保存失败。
 *
 * 每个槽位 day / month / year 对应预览上的较昨日、较上月、较上年：
 * - UNSET：不覆盖。总览条保持待接入，指标卡保持暂无数据。
 * - METRIC：结果表该列的最新值就是有符号增量（目录里名称含“较上”的指标）。
 *   只有来源行 data_date 等于大屏数据日，或最新查询只返回一行且没有日期时，
 *   才把 referenceDate 标成约定基准日（昨日、上月月末、上年年末）。绑错口径仍会显示。
 * - OFFSET：增量 = 当前主值 − 该指标在基准日的值。缺该日行则无值。
 *   数据源必须按日期聚合，运行时从上年年末到数据日做 RANGE 查询。
 *
 * 金额统一折成元。非金额单位失败关闭，不猜测。
 */

import { comparisonDates } from './explicitComparisons';

export const PERIOD_COMPARISON_KEYS = Object.freeze(['day', 'month', 'year']);
export const PERIOD_COMPARISON_LABELS = Object.freeze({
  day: '较昨日',
  month: '较上月',
  year: '较上年'
});

const AMOUNT_UNITS = Object.freeze(['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION']);
const AMOUNT_SCALE = Object.freeze({ YUAN: 1, TEN_THOUSAND: 1e4, HUNDRED_MILLION: 1e8 });

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function finite(value) {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  if (typeof value !== 'string' || value.trim() === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

export function emptyPeriodSlot() {
  return { mode: 'UNSET', metricCode: '', outputColumn: '', unit: '', tableName: '' };
}

export function normalizePeriodComparisons(raw) {
  const source = isObject(raw) ? raw : {};
  const next = {};
  for (const key of PERIOD_COMPARISON_KEYS) {
    const slot = isObject(source[key]) ? source[key] : {};
    const mode = slot.mode === 'METRIC' || slot.mode === 'OFFSET' ? slot.mode : 'UNSET';
    const unit = AMOUNT_UNITS.includes(text(slot.unit)) ? text(slot.unit) : '';
    next[key] = mode === 'UNSET'
      ? emptyPeriodSlot()
      : {
        mode,
        metricCode: text(slot.metricCode),
        outputColumn: text(slot.outputColumn),
        unit,
        tableName: text(slot.tableName)
      };
  }
  return next;
}

export function normalizePeriodComparisonMap(raw) {
  if (!isObject(raw)) return {};
  const next = {};
  for (const [key, value] of Object.entries(raw)) {
    const name = text(key);
    if (!name || name.length > 80 || !isObject(value)) continue;
    next[name] = normalizePeriodComparisons(value);
  }
  return next;
}

function slotReady(slot) {
  return slot.mode !== 'UNSET' && slot.metricCode && slot.outputColumn;
}

function rowDate(row) {
  return text(row?.data_date || row?.dataDate || row?.date);
}

/**
 * RANGE may repeat the current date or contain multiple rows for a malformed
 * source.  Keep the complete LATEST rows authoritative and retain duplicate
 * historical rows so resolvePeriodComparisons can fail closed on ambiguity.
 */
function mergeCurrentAndRangeRows(currentRows, rangeRows, currentDate = '') {
  const current = (Array.isArray(currentRows) ? currentRows : []).filter(isObject);
  const currentDates = new Set(current.map(rowDate).filter(Boolean));
  if (currentDate) currentDates.add(text(currentDate));
  const historical = (Array.isArray(rangeRows) ? rangeRows : [])
    .filter(isObject)
    .filter(row => !currentDates.has(rowDate(row)));
  return [...historical, ...current];
}

function toYuan(value, unit) {
  if (!AMOUNT_UNITS.includes(unit)) return null;
  const number = finite(value);
  return number === null ? null : number * AMOUNT_SCALE[unit];
}

function noValue() {
  return { state: 'NO_VALUE', baseValue: null, referenceDate: '', unit: 'YUAN' };
}

/**
 * @returns {{ active: boolean, comparisons: Record<string, object|null> }}
 * 未配置槽位为 null。已配置但缺数、错单位或日期对不上时为 NO_VALUE。
 * READY.baseValue 是折成元的有符号增量。
 */
export function resolvePeriodComparisons({ config, dataDate, currentYuan, rows } = {}) {
  const normalized = normalizePeriodComparisons(config);
  const dates = comparisonDates(dataDate);
  const list = (Array.isArray(rows) ? rows : []).filter(isObject);
  const comparisons = {};
  let active = false;
  for (const key of PERIOD_COMPARISON_KEYS) {
    const slot = normalized[key];
    if (slot.mode === 'UNSET') {
      comparisons[key] = null;
      continue;
    }
    active = true;
    const referenceDate = dates[key] || '';
    if (!slotReady(slot) || !slot.unit || !referenceDate) {
      comparisons[key] = noValue();
      continue;
    }
    if (slot.mode === 'METRIC') {
      const dated = list.filter(row => rowDate(row) === text(dataDate) && Object.prototype.hasOwnProperty.call(row, slot.outputColumn));
      const undated = list.filter(row => !rowDate(row) && Object.prototype.hasOwnProperty.call(row, slot.outputColumn));
      const usable = dated.length === 1 ? dated[0] : (dated.length === 0 && undated.length === 1 && list.length === 1 ? undated[0] : null);
      const delta = usable ? toYuan(usable[slot.outputColumn], slot.unit) : null;
      comparisons[key] = delta === null ? noValue() : { state: 'READY', baseValue: delta, referenceDate, unit: 'YUAN' };
      continue;
    }
    const historicalRows = list.filter(row => rowDate(row) === referenceDate && Object.prototype.hasOwnProperty.call(row, slot.outputColumn));
    const historical = historicalRows.length === 1 ? toYuan(historicalRows[0][slot.outputColumn], slot.unit) : null;
    const current = finite(currentYuan);
    comparisons[key] = historical === null || current === null
      ? noValue()
      : { state: 'READY', baseValue: current - historical, referenceDate, unit: 'YUAN' };
  }
  return { active, comparisons };
}

function blockIdOf(value) {
  const blockId = Number(value);
  return Number.isSafeInteger(blockId) && blockId > 0 ? blockId : null;
}

function needsRange(config) {
  return PERIOD_COMPARISON_KEYS.some(key => config?.[key]?.mode === 'OFFSET');
}

/**
 * 把每个配置键对应到已加载区块行。
 * overview-deposit / overview-loan 使用同名槽位区块；指标卡使用 dataRefs.blockId。
 * 结算性存款没有独立区块，扫描全部已加载行。OFFSET 需要这些区块的 RANGE 结果。
 */
export function indexPeriodComparisonSources({ configs, slots = {}, components = [], blockResults = {}, rangeResults = {}, dataDate = '' } = {}) {
  const rangeBlockIds = [];
  const sources = {};
  const componentBlock = new Map((Array.isArray(components) ? components : []).map(component => [
    text(component?.componentId),
    blockIdOf(component?.dataRefs?.[0]?.blockId)
  ]));
  for (const [key, config] of Object.entries(configs || {})) {
    let blockId = null;
    if (key === 'overview-deposit') blockId = blockIdOf(slots.deposit);
    else if (key === 'overview-loan') blockId = blockIdOf(slots.loan);
    else if (key !== 'overview-settlementDeposit') blockId = componentBlock.get(key) || null;
    const ranged = needsRange(config);
    if (ranged) {
      const targets = blockId ? [blockId] : [slots.deposit, slots.loan].map(blockIdOf).filter(Boolean);
      rangeBlockIds.push(...targets);
    }
    if (blockId) {
      const currentRows = Array.isArray(blockResults[String(blockId)]?.rows) ? blockResults[String(blockId)].rows : [];
      const rangeRows = Array.isArray(rangeResults[String(blockId)]?.rows) ? rangeResults[String(blockId)].rows : [];
      sources[key] = ranged
        ? mergeCurrentAndRangeRows(currentRows, rangeRows, dataDate)
        : currentRows;
    } else {
      const currentRows = Object.values(blockResults || {})
        .flatMap(item => Array.isArray(item?.rows) ? item.rows : []);
      const rangeRows = Object.values(rangeResults || {})
        .flatMap(item => Array.isArray(item?.rows) ? item.rows : []);
      sources[key] = ranged
        ? mergeCurrentAndRangeRows(currentRows, rangeRows, dataDate)
        : currentRows;
    }
  }
  return { sources, rangeBlockIds: [...new Set(rangeBlockIds)] };
}
