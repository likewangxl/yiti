/**
 * 零售经营总览的独立绑定契约。
 *
 * 零售模板只消费本文件声明的零售槽位；branches 是唯一和分行模板共享的
 * 槽位，且仅承载机构身份。槽位语义不从列名、标题或旧分行槽位推断。
 */

export const RETAIL_TEMPLATE = 'retail-overview-v1';

export const RETAIL_AMOUNT_UNITS = Object.freeze(['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION']);
export const RETAIL_COUNT_UNITS = Object.freeze(['COUNT', 'TEN_THOUSAND_COUNT']);
export const RETAIL_RATIO_UNITS = Object.freeze(['PERCENT', 'RATIO']);
export const RETAIL_UNIT_VALUES = Object.freeze([
  ...RETAIL_AMOUNT_UNITS, ...RETAIL_COUNT_UNITS, ...RETAIL_RATIO_UNITS
]);

const field = (semantic, label, options = {}) => Object.freeze({
  semantic,
  label,
  required: false,
  kind: options.kind || 'metric',
  unitKinds: options.unitKinds || [],
  ...options
});

const singleMetric = (label, unitKinds) => Object.freeze({
  label,
  innerType: 'METRIC_CARD',
  required: ['value'],
  fields: Object.freeze([
    field('value', '数值', { required: true, unitKinds }),
    field('change', '较上期变化', { unitKinds: RETAIL_RATIO_UNITS }),
    field('date', '数据日期', { kind: 'dimension' })
  ])
});

const branchIdentitySlot = Object.freeze({
  label: '支行机构',
  innerType: 'TABLE_LIST',
  required: ['orgCode'],
  fields: Object.freeze([
    field('orgCode', '机构号', { required: true, kind: 'dimension' }),
    field('orgName', '机构名称', { kind: 'dimension' }),
    field('cityCode', '城市编码', { kind: 'dimension' }),
    field('cityName', '城市名称', { kind: 'dimension' }),
    field('ownerOperatingOrgCode', '经营归属机构号', { kind: 'dimension' }),
    field('parentOrgCode', '上级机构号', { kind: 'dimension' }),
    field('lng', '经度', { kind: 'dimension' }),
    field('lat', '纬度', { kind: 'dimension' }),
    field('coordSys', '坐标系', { kind: 'dimension' }),
    field('located', '是否已定位', { kind: 'dimension' })
  ])
});

export const RETAIL_BINDING_SLOTS = Object.freeze({
  retailAum: singleMetric('零售AUM', RETAIL_AMOUNT_UNITS),
  retailDeposit: singleMetric('储蓄存款余额', RETAIL_AMOUNT_UNITS),
  retailDepositAverage: singleMetric('储蓄月日均', RETAIL_AMOUNT_UNITS),
  retailRevenue: singleMetric('零售营业收入', RETAIL_AMOUNT_UNITS),
  retailValueCustomers: singleMetric('价值客户', RETAIL_COUNT_UNITS),
  retailLoan: singleMetric('个人贷款', RETAIL_AMOUNT_UNITS),
  retailNplRate: singleMetric('个贷不良率', RETAIL_RATIO_UNITS),
  retailTrend: Object.freeze({
    label: '零售经营趋势',
    innerType: 'LINE_TREND',
    required: ['date'],
    atLeastOneOf: ['aum', 'deposit'],
    fields: Object.freeze([
      field('date', '日期', { required: true, kind: 'dimension' }),
      field('aum', '零售AUM', { unitKinds: RETAIL_AMOUNT_UNITS }),
      field('deposit', '储蓄存款余额', { unitKinds: RETAIL_AMOUNT_UNITS })
    ])
  }),
  retailSegments: Object.freeze({
    label: '零售客群',
    innerType: 'TABLE_LIST',
    required: ['name', 'customers', 'aum'],
    fields: Object.freeze([
      field('name', '客群名称', { required: true, kind: 'dimension' }),
      field('customers', '客户数', { required: true, unitKinds: RETAIL_COUNT_UNITS }),
      field('aum', '客群AUM', { required: true, unitKinds: RETAIL_AMOUNT_UNITS })
    ])
  }),
  retailRanking: Object.freeze({
    label: '零售机构排名',
    innerType: 'RANK_LIST',
    required: ['orgCode', 'name', 'aum'],
    fields: Object.freeze([
      field('orgCode', '机构号', { required: true, kind: 'dimension' }),
      field('name', '机构名称', { required: true, kind: 'dimension' }),
      field('aum', '零售AUM', { required: true, unitKinds: RETAIL_AMOUNT_UNITS }),
      field('increase', '较上月AUM净增', { unitKinds: RETAIL_AMOUNT_UNITS }),
      field('rate', 'AUM年度目标完成率', { unitKinds: RETAIL_RATIO_UNITS }),
      field('nplRate', '个贷不良率', { unitKinds: RETAIL_RATIO_UNITS })
    ])
  }),
  retailAttention: Object.freeze({
    label: '零售经营关注',
    innerType: 'TABLE_LIST',
    required: ['label', 'count'],
    fields: Object.freeze([
      field('label', '关注事项', { required: true, kind: 'dimension' }),
      field('count', '数量', { required: true, unitKinds: ['COUNT'] }),
      field('owner', '责任人', { kind: 'dimension' }),
      field('deadline', '截止日期', { kind: 'dimension' })
    ])
  }),
  retailTargets: Object.freeze({
    label: '零售目标',
    innerType: 'TABLE_LIST',
    required: ['name', 'actual', 'target'],
    fields: Object.freeze([
      field('name', '目标名称', { required: true, kind: 'dimension' }),
      field('actual', '实际值', { required: true, unitKinds: RETAIL_AMOUNT_UNITS }),
      field('target', '目标值', { required: true, unitKinds: RETAIL_AMOUNT_UNITS })
    ])
  }),
  // 共享的基础身份槽。零售适配器只读取这里的机构身份字段。
  branches: branchIdentitySlot
});

export const RETAIL_SLOT_ORDER = Object.freeze([
  'retailAum', 'retailDeposit', 'retailDepositAverage', 'retailRevenue',
  'retailValueCustomers', 'retailLoan', 'retailNplRate',
  'retailTrend', 'retailSegments', 'retailRanking', 'retailAttention', 'retailTargets',
  'branches'
]);

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function parseJson(value, fallback = {}) {
  if (isObject(value)) return value;
  if (typeof value !== 'string') return fallback;
  try {
    const parsed = JSON.parse(value);
    return isObject(parsed) ? parsed : fallback;
  } catch {
    return fallback;
  }
}

export function isRetailBindingSlot(slot) {
  return Object.prototype.hasOwnProperty.call(RETAIL_BINDING_SLOTS, String(slot || ''));
}

export function isRetailTemplate(template) {
  return String(template || '').trim() === RETAIL_TEMPLATE;
}

export function normalizeRetailBinding(raw = {}, slot = '') {
  const source = parseJson(raw, {});
  const out = {};
  if (source.dsId !== undefined && source.dsId !== null && source.dsId !== '') out.dsId = source.dsId;
  const defaultPeriod = slot === 'retailTrend' ? 'LAST_6M_EOM' : 'LATEST';
  out.period = String(source.period || defaultPeriod);
  out.fields = {};
  for (const [semantic, column] of Object.entries(source.fields || {})) {
    if (column !== undefined && column !== null && String(column).trim()) {
      out.fields[semantic] = String(column).trim();
    }
  }
  out.units = {};
  for (const [semantic, unit] of Object.entries(source.units || {})) {
    if (RETAIL_UNIT_VALUES.includes(unit)) out.units[semantic] = unit;
  }
  return out;
}

export function validateRetailBinding(slot, raw = {}) {
  const issues = [];
  if (!isRetailBindingSlot(slot)) return ['槽位不受支持'];
  const binding = normalizeRetailBinding(raw, slot);
  const spec = RETAIL_BINDING_SLOTS[slot];
  if (!Number.isSafeInteger(binding.dsId) || binding.dsId <= 0) issues.push('数据源无效');
  if (slot === 'retailTrend' && binding.period !== 'LATEST' && binding.period !== 'LAST_6M_EOM'
      && binding.period !== 'LAST_10D' && binding.period !== 'LAST_1M') {
    issues.push('周期无效');
  }
  const allowedFields = new Set((spec.fields || []).map(item => item.semantic));
  for (const semantic of Object.keys(raw.fields || {})) {
    if (!allowedFields.has(semantic)) issues.push(`字段不受支持: ${semantic}`);
  }
  for (const semantic of spec.required || []) {
    if (!binding.fields?.[semantic]) issues.push(`缺少字段: ${semantic}`);
  }
  const atLeastOneOf = spec.atLeastOneOf || [];
  if (atLeastOneOf.length && !atLeastOneOf.some(semantic => binding.fields?.[semantic])) {
    issues.push(`至少选择一个字段: ${atLeastOneOf.join('、')}`);
  }
  for (const fieldSpec of spec.fields || []) {
    if (!fieldSpec.unitKinds?.length || !binding.fields?.[fieldSpec.semantic]) continue;
    if (!binding.units?.[fieldSpec.semantic]) issues.push(`缺少单位: ${fieldSpec.semantic}`);
  }
  for (const [semantic, unit] of Object.entries(raw.units || {})) {
    if (!allowedFields.has(semantic) || !binding.fields?.[semantic]) {
      issues.push(`单位未绑定字段: ${semantic}`);
      continue;
    }
    if (!RETAIL_UNIT_VALUES.includes(unit)) {
      issues.push(`单位无效: ${semantic}`);
      continue;
    }
    const fieldSpec = (spec.fields || []).find(item => item.semantic === semantic);
    if (fieldSpec?.unitKinds?.length && !fieldSpec.unitKinds.includes(unit)) {
      issues.push(`单位不适用: ${semantic}`);
    }
  }
  return issues;
}
