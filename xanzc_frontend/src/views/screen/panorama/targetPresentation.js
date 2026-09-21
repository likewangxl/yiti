/**
 * 目标完成率的展示契约。
 *
 * 完成率属于有明确业务语义的字段，展示层只按这里列出的 key 读取，不能
 * 从贷款余额、字段名称或其他金额指标推导贷款目标完成率。
 */

const DEPOSIT_RATE_KEYS = Object.freeze([
  'depositRate',
  'retailDepositCompletionRate',
  // 旧分行大屏的单一目标完成率口径，继续作为存款完成率兼容入口。
  'rate',
  'targetRate',
  'completionRate',
  'targetCompletionRate',
  'target'
]);

const LOAN_RATE_KEYS = Object.freeze([
  'loanRate',
  'retailLoanCompletionRate'
]);

export const TARGET_PRESENTATION_SPECS = Object.freeze([
  Object.freeze({
    key: 'deposit',
    variant: 'deposit',
    label: '零售存款目标完成率',
    rateKeys: DEPOSIT_RATE_KEYS
  }),
  Object.freeze({
    key: 'loan',
    variant: 'loan',
    label: '零售贷款目标完成率',
    rateKeys: LOAN_RATE_KEYS
  })
]);

function finiteValue(value) {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value === 'boolean') return null;
  const number = typeof value === 'number' ? value : Number(String(value).trim());
  return Number.isFinite(number) ? number : null;
}

function sourceKpis(source) {
  if (Array.isArray(source)) return source;
  if (source && typeof source === 'object' && Array.isArray(source.kpis)) return source.kpis;
  if (source && typeof source === 'object' && Array.isArray(source.targets)) return source.targets;
  return [];
}

function hasOwn(source, key) {
  return source && typeof source === 'object' && Object.prototype.hasOwnProperty.call(source, key);
}

function directItem(source, key) {
  if (!hasOwn(source, key)) return null;
  const raw = source[key];
  if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
    return { key, ...raw };
  }
  return { key, value: raw };
}

function findItem(source, key) {
  const kpis = sourceKpis(source);
  const item = kpis.find(candidate => candidate && String(candidate.key || '') === key);
  if (item) return item;
  if (!Array.isArray(source)) return directItem(source, key);
  return null;
}

function targetSpec(kind = 'deposit') {
  return TARGET_PRESENTATION_SPECS.find(spec => spec.key === kind) || TARGET_PRESENTATION_SPECS[0];
}

/**
 * 按优先级解析一个目标完成率。key 存在但 value 无效时保留该绑定的空态，
 * 不继续拿低优先级的旧值覆盖明确的空结果。
 */
export function resolveTargetRate(source, kind = 'deposit') {
  const spec = targetSpec(kind);
  for (const key of spec.rateKeys) {
    const item = findItem(source, key);
    if (!item) continue;
    // Legacy target may also be a monetary target, so only explicitly typed
    // percentages can enter a completion gauge through this ambiguous key.
    if (key === 'target' && !['%', 'PERCENT', 'PCT'].includes(String(item.unit || '').trim().toUpperCase())) continue;
    return {
      key,
      value: finiteValue(item.value),
      item
    };
  }
  return { key: null, value: null, item: null };
}

function round(value, digits = 1) {
  const number = finiteValue(value);
  if (number === null) return null;
  const scale = 10 ** digits;
  return Math.round((number + Number.EPSILON) * scale) / scale;
}

function targetGapText(value) {
  const number = finiteValue(value);
  if (number === null) return '暂无目标数据';
  const gap = round(number - 100, 1);
  if (number < 100) return gap === 0 ? '距目标还差不到0.1个百分点' : `距目标还差${Math.abs(gap)}个百分点`;
  if (number > 100) return gap === 0 ? '超目标不到0.1个百分点' : `超目标${gap}个百分点`;
  return '已达到目标';
}

function cardDate(item, fallbackDate = '') {
  if (!item) return '—';
  const date = item?.date ?? item?.dataDate ?? item?.periodDate ?? fallbackDate;
  return date === null || date === undefined || String(date).trim() === '' ? '—' : String(date);
}

function cardLabel(item, fallback) {
  const label = stripTestModifier(typeof item?.label === 'string' ? item.label : '');
  return !label || ['目标完成率', '完成率'].includes(label) ? fallback : label;
}

/**
 * 为分行总览构造存款、贷款两张目标卡。每张卡都保留自身来源、日期和缺口，
 * 贷款卡缺少独立绑定时 value/gap 均为空并显示“暂无目标数据”。
 */
export function buildTargetCards(source = {}, options = {}) {
  const fallbackDate = options?.dataDate
    ?? (source && typeof source === 'object' ? source.dataDate : '')
    ?? '';
  return TARGET_PRESENTATION_SPECS.map(spec => {
    const resolved = resolveTargetRate(source, spec.key);
    const value = resolved.value;
    const gap = value === null ? null : round(value - 100, 1);
    const item = resolved.item;
    return {
      key: spec.key,
      variant: spec.variant,
      label: cardLabel(item, spec.label),
      sourceKey: resolved.key,
      value,
      rate: value,
      date: cardDate(item, fallbackDate),
      period: item?.periodLabel || item?.periodName || item?.period || '',
      gap,
      gapText: targetGapText(value),
      distance: { text: targetGapText(value), value: gap, state: value === null ? 'unknown' : value < 100 ? 'below' : value > 100 ? 'above' : 'achieved' },
      hasData: value !== null,
      bound: Boolean(item),
      message: value === null ? '暂无目标数据' : ''
    };
  });
}

/**
 * 仅清理标题末尾的测试标记；数据质量提示和其他内容不经过此函数。
 */
export function stripTestModifier(value) {
  const text = value === null || value === undefined ? '' : String(value).trim();
  if (!text) return '';
  return text
    .replace(/\s*[（(]\s*测试\s*[)）]\s*$/u, '')
    .replace(/\s*[-—_]\s*测试\s*$/u, '')
    .trim();
}

// 便于调用方按语义命名，保留同一实现的显式别名。
export const createTargetCards = buildTargetCards;
export const resolveTargetCards = buildTargetCards;
