/**
 * 对公经营总览的独立绑定契约。
 *
 * 对公模板只消费 corp* 槽位；branches 与其他经营总览共享，但只承载机构身份。
 * 字段语义、单位和授权机构边界均由本文件声明，运行时不根据列名猜测业务含义。
 */

export const CORPORATE_TEMPLATE = 'corporate-overview-v1';

export const CORPORATE_AMOUNT_UNITS = Object.freeze(['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION']);
export const CORPORATE_COUNT_UNITS = Object.freeze(['COUNT', 'TEN_THOUSAND_COUNT']);
export const CORPORATE_RATIO_UNITS = Object.freeze(['PERCENT', 'RATIO']);
export const CORPORATE_UNIT_VALUES = Object.freeze([
  ...CORPORATE_AMOUNT_UNITS,
  ...CORPORATE_COUNT_UNITS,
  ...CORPORATE_RATIO_UNITS
]);
// 供设计器和外部集成使用的通用别名。
export const UNIT_VALUES = CORPORATE_UNIT_VALUES;

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
    field('change', '较上期变化', { unitKinds: CORPORATE_RATIO_UNITS }),
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

export const CORPORATE_BINDING_SLOTS = Object.freeze({
  corpDeposit: singleMetric('对公存款余额', CORPORATE_AMOUNT_UNITS),
  corpDepositAverage: singleMetric('对公存款月日均', CORPORATE_AMOUNT_UNITS),
  corpLoan: singleMetric('对公贷款余额', CORPORATE_AMOUNT_UNITS),
  corpRevenue: singleMetric('对公营业收入', CORPORATE_AMOUNT_UNITS),
  corpCustomers: singleMetric('有效对公客户', CORPORATE_COUNT_UNITS),
  corpNplRate: singleMetric('对公不良率', CORPORATE_RATIO_UNITS),
  corpTrend: Object.freeze({
    label: '对公经营趋势',
    innerType: 'LINE_TREND',
    required: ['date'],
    atLeastOneOf: ['deposit', 'loan'],
    fields: Object.freeze([
      field('date', '日期', { required: true, kind: 'dimension' }),
      field('deposit', '对公存款余额', { unitKinds: CORPORATE_AMOUNT_UNITS }),
      field('loan', '对公贷款余额', { unitKinds: CORPORATE_AMOUNT_UNITS })
    ])
  }),
  corpSegments: Object.freeze({
    label: '对公重点客群',
    innerType: 'TABLE_LIST',
    required: ['name', 'customers', 'loan'],
    fields: Object.freeze([
      field('name', '客群名称', { required: true, kind: 'dimension' }),
      field('customers', '客户数', { required: true, unitKinds: CORPORATE_COUNT_UNITS }),
      field('loan', '客群贷款', { required: true, unitKinds: CORPORATE_AMOUNT_UNITS })
    ])
  }),
  corpRanking: Object.freeze({
    label: '对公机构排名',
    innerType: 'RANK_LIST',
    required: ['orgCode', 'name', 'deposit'],
    fields: Object.freeze([
      field('orgCode', '机构号', { required: true, kind: 'dimension' }),
      field('name', '机构名称', { required: true, kind: 'dimension' }),
      field('deposit', '对公存款余额', { required: true, unitKinds: CORPORATE_AMOUNT_UNITS }),
      field('increase', '较上期存款净增', { unitKinds: CORPORATE_AMOUNT_UNITS }),
      field('rate', '对公存款目标完成率', { unitKinds: CORPORATE_RATIO_UNITS }),
      field('nplRate', '对公贷款不良率', { unitKinds: CORPORATE_RATIO_UNITS })
    ])
  }),
  corpAttention: Object.freeze({
    label: '对公经营关注',
    innerType: 'TABLE_LIST',
    required: ['label', 'count'],
    fields: Object.freeze([
      field('label', '关注事项', { required: true, kind: 'dimension' }),
      field('count', '数量', { required: true, unitKinds: ['COUNT'] }),
      field('owner', '责任部门/人', { kind: 'dimension' }),
      field('deadline', '截止日期', { kind: 'dimension' })
    ])
  }),
  corpTargets: Object.freeze({
    label: '对公目标',
    innerType: 'TABLE_LIST',
    required: ['name', 'actual', 'target'],
    fields: Object.freeze([
      field('name', '目标名称', { required: true, kind: 'dimension' }),
    // 对公首页目标统一为金额类目标，避免把客户数和金额进度放在同一进度条比较。
    field('actual', '实际值', { required: true, unitKinds: CORPORATE_AMOUNT_UNITS }),
    field('target', '目标值', { required: true, unitKinds: CORPORATE_AMOUNT_UNITS })
    ])
  }),
  // 共享基础身份槽。对公适配器不从 branches 读取任何经营指标。
  branches: branchIdentitySlot
});

export const CORPORATE_SLOT_ORDER = Object.freeze([
  'corpDeposit', 'corpDepositAverage', 'corpLoan', 'corpRevenue', 'corpCustomers', 'corpNplRate',
  'corpTrend', 'corpSegments', 'corpRanking', 'corpAttention', 'corpTargets', 'branches'
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

export function isCorporateBindingSlot(slot) {
  return Object.prototype.hasOwnProperty.call(CORPORATE_BINDING_SLOTS, String(slot || ''));
}

export function isCorporateTemplate(template) {
  return String(template || '').trim() === CORPORATE_TEMPLATE;
}

export function normalizeCorporateBinding(raw = {}, slot = '') {
  const source = parseJson(raw, {});
  const out = {};
  if (source.dsId !== undefined && source.dsId !== null && source.dsId !== '') out.dsId = source.dsId;
  out.period = String(source.period || (slot === 'corpTrend' ? 'LAST_6M_EOM' : 'LATEST'));
  out.fields = {};
  for (const [semantic, column] of Object.entries(source.fields || {})) {
    if (column !== undefined && column !== null && String(column).trim()) {
      out.fields[semantic] = String(column).trim();
    }
  }
  out.units = {};
  for (const [semantic, unit] of Object.entries(source.units || {})) {
    if (CORPORATE_UNIT_VALUES.includes(unit)) out.units[semantic] = unit;
  }
  return out;
}

export function validateCorporateBinding(slot, raw = {}) {
  const issues = [];
  if (!isCorporateBindingSlot(slot)) return ['槽位不受支持'];
  const source = parseJson(raw, {});
  const binding = normalizeCorporateBinding(raw, slot);
  const spec = CORPORATE_BINDING_SLOTS[slot];
  if (!Number.isSafeInteger(binding.dsId) || binding.dsId <= 0) issues.push('数据源无效');
  if (slot === 'corpTrend' && !['LATEST', 'LAST_6M_EOM', 'LAST_10D', 'LAST_1M'].includes(binding.period)) {
    issues.push('周期无效');
  }
  const allowedFields = new Set((spec.fields || []).map(item => item.semantic));
  for (const semantic of Object.keys(source.fields || {})) {
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
  for (const [semantic, unit] of Object.entries(source.units || {})) {
    if (!allowedFields.has(semantic) || !binding.fields?.[semantic]) {
      issues.push(`单位未绑定字段: ${semantic}`);
      continue;
    }
    if (!CORPORATE_UNIT_VALUES.includes(unit)) {
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
