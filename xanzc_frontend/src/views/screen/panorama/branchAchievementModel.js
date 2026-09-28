/**
 * 支行目标完成与核心经营指标的纯展示模型。
 *
 * 这里不从标签、总额或其他指标猜测业务口径。只有来源明确提供的字段才会
 * 被读取，缺失值保持为 null，方便页面把“暂无数据”和真实的零值区分开。
 */

const CORE_DEFINITIONS = Object.freeze([
  ['corpDeposit', '对公存款'],
  ['retailDeposit', '对私存款'],
  ['corpLoan', '对公贷款'],
  ['retailLoan', '对私贷款'],
  ['revenue', '营业收入'],
  ['intermediaryIncome', '中间业务收入']
]);

const CORE_ALIASES = Object.freeze({
  corpDeposit: [],
  retailDeposit: [],
  corpLoan: ['corporateLoan'],
  retailLoan: [],
  revenue: ['operatingRevenue'],
  intermediaryIncome: []
});

const OWN = Object.prototype.hasOwnProperty;

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function hasOwn(value, key) {
  return isObject(value) && OWN.call(value, key);
}

/** 仅接受有限 number/string；布尔值、空白、NaN 和 Infinity 都不是业务数字。 */
function finiteNumber(value) {
  if (value === null || value === undefined || typeof value === 'boolean') return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  if (typeof value !== 'number' && typeof value !== 'string') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function cloneValue(value, seen = new WeakMap()) {
  if (Array.isArray(value)) return value.map(item => cloneValue(item, seen));
  if (!isObject(value)) return value;
  if (seen.has(value)) return seen.get(value);
  const clone = {};
  seen.set(value, clone);
  for (const [key, item] of Object.entries(value)) clone[key] = cloneValue(item, seen);
  return clone;
}

function directionOf(value) {
  return text(value).toUpperCase() === 'DOWN' ? 'DOWN' : 'UP';
}

function targetKeyOf(target, index) {
  for (const key of ['key', 'metricCode', 'name', 'label']) {
    const candidate = text(target?.[key]);
    if (candidate) return candidate;
  }
  return `target-${index}`;
}

function firstPresent(source, fields) {
  for (const field of fields) {
    if (hasOwn(source, field)) return source[field];
  }
  return undefined;
}

function missingItem(source, direction, id, reason, rate = null, gap = null) {
  return {
    ...source,
    id,
    rate,
    gap,
    state: 'missing',
    missingReason: reason,
    direction
  };
}

/**
 * 生成支行考核目标模型。
 *
 * @param {Array<object>} targets 目标来源数组
 * @returns {{items: Array<object>, total: number, completed: number, incomplete: number, missing: number, attainmentRate: number|null}}
 */
export function buildBranchAchievementModel(targets = []) {
  const sourceTargets = Array.isArray(targets) ? targets : [];
  const items = sourceTargets.map((target, index) => {
    const source = isObject(target) ? cloneValue(target) : {};
    const id = `${targetKeyOf(source, index)}-${index}`;
    const direction = directionOf(source.direction);
    const actualFields = ['actual', 'actualValue'];
    const targetFields = ['target', 'targetValue'];
    const actual = finiteNumber(firstPresent(source, actualFields));
    const targetValue = finiteNumber(firstPresent(source, targetFields));
    const rawRate = finiteNumber(source.rate);

    // 来源完成率只在调用方明确声明 rateOnly 且完全没有实际/目标字段时可用。
    // 没有实际和目标时仍无法判断达标状态，因此单列为 missing，不计入 incomplete。
    if (actual === null && targetValue === null && source.rateOnly === true) {
      return missingItem(source, direction, id,
        '缺少实际值和目标值，无法判定达标状态', rawRate, null);
    }

    if (targetValue === null) {
      return missingItem(source, direction, id, '目标值缺失或非有限数字');
    }
    if (targetValue <= 0) {
      return missingItem(source, direction, id, '目标值必须大于0');
    }
    if (actual === null) {
      return missingItem(source, direction, id, '实际值缺失或非有限数字');
    }

    const gap = direction === 'DOWN' ? actual - targetValue : targetValue - actual;
    const safeGap = Number.isFinite(gap) ? gap : null;
    const rateRaw = direction === 'DOWN'
      ? (actual === 0 ? null : targetValue / actual * 100)
      : actual / targetValue * 100;
    const rate = rateRaw === null || Number.isFinite(rateRaw) ? rateRaw : null;
    const completed = direction === 'DOWN' ? actual <= targetValue : actual >= targetValue;
    return {
      ...source,
      id,
      rate,
      gap: safeGap,
      state: completed ? 'completed' : 'incomplete',
      missingReason: null,
      direction
    };
  });

  const completed = items.filter(item => item.state === 'completed').length;
  const incomplete = items.filter(item => item.state === 'incomplete').length;
  const missing = items.length - completed - incomplete;
  const assessable = completed + incomplete;
  return {
    items,
    total: items.length,
    completed,
    incomplete,
    missing,
    attainmentRate: assessable > 0 ? completed / assessable * 100 : null
  };
}

function normalizeKpiEntries(kpis) {
  if (Array.isArray(kpis)) {
    return kpis
      .filter(item => isObject(item))
      .map(item => ({ key: text(item.key), item }));
  }
  if (!isObject(kpis)) return [];
  return Object.entries(kpis).map(([key, value]) => ({
    key: text(key),
    item: isObject(value) ? { ...value, key: value.key ?? key } : { key, value }
  }));
}

function sourceKpi(entries, key) {
  const candidates = [key, ...(CORE_ALIASES[key] || [])];
  for (const candidate of candidates) {
    const exact = entries.find(entry => entry.key === candidate);
    if (exact) return exact.item;
  }
  return null;
}

/**
 * 生成固定六张核心经营指标卡。
 *
 * 只读取 kpis 中明确声明的 key；不会把存款总额拆成对公/对私，也不会把
 * corpRevenue（FTP 收入）当作营业收入。来源的 label/date/unit 原样保留。
 *
 * @param {object} model 页面来源模型
 * @returns {Array<object>} 固定顺序的六张卡
 */
export function buildBranchCoreMetrics(model = {}) {
  const sourceModel = isObject(model) ? model : {};
  const entries = normalizeKpiEntries(sourceModel.kpis);
  return CORE_DEFINITIONS.map(([key, defaultLabel]) => {
    const source = sourceKpi(entries, key);
    if (!source) {
      return { key, label: defaultLabel, value: null, status: '未绑定/暂无数据' };
    }
    const result = cloneValue(source);
    const value = finiteNumber(source.value);
    result.key = key;
    result.value = value;
    if (value === null) result.status = '未绑定/暂无数据';
    else if (result.status === undefined || result.status === null || text(result.status) === '') result.status = '已绑定';
    return result;
  });
}

export const BRANCH_CORE_METRIC_KEYS = Object.freeze(CORE_DEFINITIONS.map(([key]) => key));

export { finiteNumber as parseFiniteNumber };
