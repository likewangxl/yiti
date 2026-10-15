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
 * 金额统一折成元；比例统一折成百分数，差值单位为百分点。不同量纲失败关闭。
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
const PERIOD_UNITS = Object.freeze([...AMOUNT_UNITS, 'PERCENT', 'RATIO']);
const UNIT_ALIASES = Object.freeze({
  元: 'YUAN', 人民币元: 'YUAN', CNY: 'YUAN', RMB: 'YUAN', YUAN: 'YUAN',
  万元: 'TEN_THOUSAND', TEN_THOUSAND: 'TEN_THOUSAND',
  亿元: 'HUNDRED_MILLION', HUNDRED_MILLION: 'HUNDRED_MILLION',
  百分比: 'PERCENT', 百分数: 'PERCENT', '%': 'PERCENT', PCT: 'PERCENT', PERCENT: 'PERCENT',
  比例: 'RATIO', RATIO: 'RATIO'
});
const UNIT_KINDS = Object.freeze({
  YUAN: 'amount', TEN_THOUSAND: 'amount', HUNDRED_MILLION: 'amount',
  PERCENT: 'ratio', RATIO: 'ratio'
});

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

function canonicalUnit(value) {
  const raw = text(value);
  return UNIT_ALIASES[raw] || UNIT_ALIASES[raw.toUpperCase()] || null;
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
    const candidateUnit = canonicalUnit(slot.unit);
    const unit = PERIOD_UNITS.includes(candidateUnit) ? candidateUnit : '';
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

function toCanonicalValue(value, unit) {
  const number = finite(value);
  const canonical = canonicalUnit(unit);
  const kind = UNIT_KINDS[canonical];
  if (number === null || !kind) return null;
  if (kind === 'ratio') {
    const percent = canonical === 'RATIO' ? number * 100 : number;
    return { value: Number(percent.toFixed(12)), unit: 'PERCENT', kind };
  }
  return { value: number * AMOUNT_SCALE[canonical], unit: 'YUAN', kind };
}

function noValue(unit = 'YUAN') {
  const canonical = canonicalUnit(unit);
  const outputUnit = UNIT_KINDS[canonical] === 'ratio' ? 'PERCENT' : 'YUAN';
  return { state: 'NO_VALUE', baseValue: null, referenceDate: '', unit: outputUnit };
}

function resolveCurrentValue({ currentYuan, currentValue, currentUnit }) {
  const hasExtendedCurrent = currentValue !== undefined || currentUnit !== undefined;
  if (hasExtendedCurrent) return toCanonicalValue(currentValue, currentUnit);
  return toCanonicalValue(currentYuan, 'YUAN');
}

/**
 * @returns {{ active: boolean, comparisons: Record<string, object|null> }}
 * 未配置槽位为 null。已配置但缺数、错单位或日期对不上时为 NO_VALUE。
 * READY.baseValue 是按量纲归一后的有符号增量：金额为元，比例为百分点。
 */
export function resolvePeriodComparisons({ config, dataDate, currentYuan, currentValue, currentUnit, rows } = {}) {
  const normalized = normalizePeriodComparisons(config);
  const dates = comparisonDates(dataDate);
  const list = (Array.isArray(rows) ? rows : []).filter(isObject);
  const current = resolveCurrentValue({ currentYuan, currentValue, currentUnit });
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
      comparisons[key] = noValue(slot.unit);
      continue;
    }
    const expectedKind = UNIT_KINDS[slot.unit];
    if (!current || current.kind !== expectedKind) {
      comparisons[key] = noValue(slot.unit);
      continue;
    }
    if (slot.mode === 'METRIC') {
      const dated = list.filter(row => rowDate(row) === text(dataDate) && Object.prototype.hasOwnProperty.call(row, slot.outputColumn));
      const undated = list.filter(row => !rowDate(row) && Object.prototype.hasOwnProperty.call(row, slot.outputColumn));
      const usable = dated.length === 1 ? dated[0] : (dated.length === 0 && undated.length === 1 && list.length === 1 ? undated[0] : null);
      const delta = usable ? toCanonicalValue(usable[slot.outputColumn], slot.unit) : null;
      comparisons[key] = delta === null
        ? noValue(slot.unit)
        : { state: 'READY', baseValue: delta.value, referenceDate, unit: delta.unit };
      continue;
    }
    const historicalRows = list.filter(row => rowDate(row) === referenceDate && Object.prototype.hasOwnProperty.call(row, slot.outputColumn));
    const currentRows = list.filter(row => rowDate(row) === text(dataDate) && Object.prototype.hasOwnProperty.call(row, slot.outputColumn));
    if (currentRows.length > 1) {
      comparisons[key] = noValue(slot.unit);
      continue;
    }
    const historical = historicalRows.length === 1
      ? toCanonicalValue(historicalRows[0][slot.outputColumn], slot.unit) : null;
    comparisons[key] = !historical
      ? noValue(slot.unit)
      : {
        state: 'READY',
        baseValue: current.kind === 'ratio' ? Number((current.value - historical.value).toFixed(12)) : current.value - historical.value,
        referenceDate,
        unit: current.unit
      };
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
export function indexPeriodComparisonSources({ configs, slots = {}, components = [], blockResults = {}, rangeResults = {} } = {}) {
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
      const bucket = ranged ? (rangeResults[String(blockId)] || blockResults[String(blockId)]) : blockResults[String(blockId)];
      sources[key] = Array.isArray(bucket?.rows) ? bucket.rows : [];
    } else {
      const pools = ranged && Object.keys(rangeResults).length ? rangeResults : blockResults;
      sources[key] = Object.values(pools || {}).flatMap(item => Array.isArray(item?.rows) ? item.rows : []);
    }
  }
  return { sources, rangeBlockIds: [...new Set(rangeBlockIds)] };
}
