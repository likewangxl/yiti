import { RETAIL_BINDING_SLOTS, RETAIL_SLOT_ORDER } from './retailBindings';
import {
  CORPORATE_BINDING_SLOTS,
  CORPORATE_SLOT_ORDER,
  isCorporateBindingSlot,
  normalizeCorporateBinding,
  validateCorporateBinding
} from './corporateBindings';

/**
 * 代码化经营大屏的绑定契约。
 *
 * 绑定是发布包中的数据身份，不是一个可由标题、旧组件类型或列名推断出的配置。
 * 因此这里集中声明槽位、语义字段和单位白名单，管理端与运行时都只消费这一份契约。
 */

export const UNIT_VALUES = Object.freeze([
  'YUAN',
  'TEN_THOUSAND',
  'HUNDRED_MILLION',
  'COUNT',
  'TEN_THOUSAND_COUNT',
  'PERCENT',
  'RATIO'
]);

export const PERIOD_VALUES = Object.freeze([
  'LATEST',
  'LAST_10D',
  'LAST_1M',
  'LAST_6M_EOM'
]);

export const PERIOD_LABELS = Object.freeze({
  LATEST: '最新数据',
  LAST_10D: '近10天',
  LAST_1M: '近1个月',
  LAST_6M_EOM: '近6个月月末'
});

const field = (semantic, label, options = {}) => Object.freeze({
  semantic,
  label,
  required: false,
  kind: options.kind || 'metric',
  unitKinds: options.unitKinds || [],
  ...options
});

const amountUnits = Object.freeze(['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION']);
const countUnits = Object.freeze(['COUNT', 'TEN_THOUSAND_COUNT']);
const ratioUnits = Object.freeze(['PERCENT', 'RATIO']);
const compositionValueUnits = Object.freeze(amountUnits.concat(ratioUnits));

const compositionRowFields = Object.freeze([
  field('name', '构成名称', { required: true, kind: 'dimension' }),
  field('value', '构成值', { required: true, unitKinds: compositionValueUnits })
]);

const compositionColumnFields = Object.freeze([
  field('corporate', '对公业务', { required: true, unitKinds: compositionValueUnits }),
  field('retail', '零售业务', { required: true, unitKinds: compositionValueUnits }),
  field('total', '总量/分母', { unitKinds: compositionValueUnits })
]);

/** 业务构成绑定的两种固定形状；管理页按模式消费，不允许自由扩展字段。 */
export const COMPOSITION_MODES = Object.freeze(['rows', 'columns']);
const COMPOSITION_FIELD_SPECS = Object.freeze({
  rows: compositionRowFields,
  columns: compositionColumnFields
});

const singleMetric = (label, kind = 'amount') => Object.freeze({
  label,
  innerType: 'METRIC_CARD',
  required: ['value'],
  fields: Object.freeze([
    field('value', '数值', { required: true, unitKinds: kind === 'count' ? countUnits : kind === 'ratio' ? ratioUnits : amountUnits }),
    field('change', '较上期变化', { unitKinds: ratioUnits }),
    field('date', '数据日期', { kind: 'dimension' })
  ])
});

/**
 * 槽位 semantic 说明：
 * - branches 只要求 orgCode；orgName/cityCode 由授权 panoramaInstitutions 优先补齐。
 * - trend 必须有 date，并至少配置 deposit、loan 或 depositIncrease 之一；
 *   branchTrend 保持支行原有 deposit/loan/customers/rate 形状。
 * - citySummary 必须有 cityCode，并至少配置一个可展示指标。
 */
export const BINDING_SLOTS = Object.freeze({
  deposit: singleMetric('存款余额'),
  depositIncrease: singleMetric('存款较上月净增'),
  depositAverage: singleMetric('存款月均余额'),
  loan: singleMetric('贷款余额'),
  customers: singleMetric('营销有效归属客户数', 'count'),
  revenue: singleMetric('手工测试收入'),
  rate: singleMetric('目标完成率', 'ratio'),
  trend: Object.freeze({
    label: '经营趋势',
    innerType: 'LINE_TREND',
    required: ['date'],
    atLeastOneOf: ['deposit', 'loan', 'depositIncrease'],
    fields: Object.freeze([
      field('date', '日期', { required: true, kind: 'dimension' }),
      field('deposit', '存款余额', { unitKinds: amountUnits }),
      field('loan', '贷款余额', { unitKinds: amountUnits }),
      field('depositIncrease', '存款较上月净增', { unitKinds: amountUnits }),
      field('customers', '营销有效归属客户数', { unitKinds: countUnits }),
      field('rate', '完成率', { unitKinds: ratioUnits })
    ])
  }),
  composition: Object.freeze({
    label: '业务构成',
    innerType: 'PIE_SHARE',
    // required/fields are mode-neutral metadata. validateBinding applies the
    // exact rows or columns shape after getCompositionMode resolves it.
    required: [],
    fields: Object.freeze([...compositionRowFields, ...compositionColumnFields]),
    modes: COMPOSITION_MODES
  }),
  ranking: Object.freeze({
    label: '机构排名',
    innerType: 'RANK_LIST',
    required: ['orgCode', 'name', 'value'],
    fields: Object.freeze([
      field('orgCode', '机构号', { required: true, kind: 'dimension' }),
      field('name', '机构名称', { required: true, kind: 'dimension' }),
      field('value', '排名值', { required: true, unitKinds: amountUnits }),
      field('increase', '存款较上月净增', { unitKinds: amountUnits }),
      field('average', '存款月均余额', { unitKinds: amountUnits }),
      field('change', '较上期变化', { unitKinds: ratioUnits })
    ])
  }),
  attention: Object.freeze({
    label: '经营关注',
    innerType: 'TABLE_LIST',
    required: ['label', 'count'],
    fields: Object.freeze([
      field('label', '关注事项', { required: true, kind: 'dimension' }),
      field('count', '数量', { required: true, unitKinds: countUnits }),
      field('orgCode', '机构号', { kind: 'dimension' })
    ])
  }),
  branches: Object.freeze({
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
      field('located', '是否已定位', { kind: 'dimension' }),
      field('deposit', '存款余额', { unitKinds: amountUnits }),
      field('loan', '贷款余额', { unitKinds: amountUnits }),
      field('customers', '营销有效归属客户数', { unitKinds: countUnits }),
      field('target', '目标值', { unitKinds: amountUnits }),
      field('rate', '完成率', { unitKinds: ratioUnits })
    ])
  }),
  branchTrend: Object.freeze({
    label: '支行趋势',
    innerType: 'LINE_TREND',
    required: ['date'],
    atLeastOneOf: ['deposit', 'loan'],
    fields: Object.freeze([
      field('date', '日期', { required: true, kind: 'dimension' }),
      field('deposit', '存款余额', { unitKinds: amountUnits }),
      field('loan', '贷款余额', { unitKinds: amountUnits }),
      field('customers', '营销有效归属客户数', { unitKinds: countUnits }),
      field('rate', '完成率', { unitKinds: ratioUnits })
    ])
  }),
  citySummary: Object.freeze({
    label: '城市汇总',
    innerType: 'TABLE_LIST',
    required: [],
    oneOfRequired: ['cityCode', 'orgCode'],
    atLeastOneOf: ['deposit', 'loan', 'customers', 'revenue', 'rate'],
    fields: Object.freeze([
      field('orgCode', '机构号', { kind: 'dimension' }),
      field('cityCode', '城市编码', { kind: 'dimension' }),
      field('cityName', '城市名称', { kind: 'dimension' }),
      field('deposit', '存款余额', { unitKinds: amountUnits }),
      field('loan', '贷款余额', { unitKinds: amountUnits }),
      field('customers', '营销有效归属客户数', { unitKinds: countUnits }),
      field('revenue', '手工测试收入', { unitKinds: amountUnits }),
      field('rate', '完成率', { unitKinds: ratioUnits })
    ])
  }),
  // loanRate is an optional independent ratio card. It must never be inferred
  // from the legacy rate binding or from the loan amount.
  loanRate: singleMetric('零售贷款目标完成率', 'ratio'),
  // 零售模板槽位进入全局身份白名单；branches 保留这里的分行完整契约，
  // 零售管理页通过 RETAIL_BINDING_SLOTS.branches 只展示身份字段。
  ...Object.fromEntries(Object.entries(RETAIL_BINDING_SLOTS).filter(([slot]) => slot !== 'branches')),
  // 对公模板拥有独立的 corp* 身份；branches 仍由基础模板声明完整契约，
  // 对公/零售管理页各自只消费 corporate/retail 的身份槽定义。
  ...Object.fromEntries(Object.entries(CORPORATE_BINDING_SLOTS).filter(([slot]) => slot !== 'branches'))
});

/** 既有分行模板的 14 个槽位，供旧管理页、预检和兼容测试继续使用。 */
export const BRANCH_SLOT_ORDER = Object.freeze([
  'deposit', 'depositIncrease', 'depositAverage', 'loan', 'customers', 'revenue', 'rate', 'trend',
  'composition', 'ranking', 'attention', 'branches', 'branchTrend', 'citySummary'
]);

/** 新增的可选分行槽位单独列出，避免改变旧14槽位发布包的顺序与计数。 */
export const BRANCH_OPTIONAL_SLOT_ORDER = Object.freeze(['loanRate']);

// 保持历史导出语义；零售管理/运行时显式使用 RETAIL_SLOT_ORDER。
export const SLOT_ORDER = BRANCH_SLOT_ORDER;

/** 全局代码化组件构建顺序，包含分行槽位和零售新增槽位。 */
export const ALL_SLOT_ORDER = Object.freeze([
  ...BRANCH_SLOT_ORDER,
  ...BRANCH_OPTIONAL_SLOT_ORDER,
  ...RETAIL_SLOT_ORDER.filter(slot => !BRANCH_SLOT_ORDER.includes(slot)),
  ...CORPORATE_SLOT_ORDER.filter(slot => !BRANCH_SLOT_ORDER.includes(slot))
]);

const SLOT_INNER_TYPES = Object.freeze(Object.fromEntries(
  ALL_SLOT_ORDER.map(slot => [slot, BINDING_SLOTS[slot].innerType])
));

function parseJson(value, fallback = {}) {
  if (value && typeof value === 'object' && !Array.isArray(value)) return value;
  if (typeof value !== 'string') return fallback;
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : fallback;
  } catch {
    return fallback;
  }
}

export function isBindingSlot(slot) {
  return Object.prototype.hasOwnProperty.call(BINDING_SLOTS, String(slot || ''));
}

function hasSelectedField(fields, semantic) {
  const value = fields?.[semantic];
  return value !== undefined && value !== null && String(value).trim() !== '';
}

/**
 * 根据已选择字段识别业务构成形状。
 * corporate/retail 任一存在即进入双列模式；没有列模式字段时保持旧行模式。
 */
export function getCompositionMode(binding = {}) {
  const source = parseJson(binding, {});
  const fields = source?.fields && typeof source.fields === 'object' && !Array.isArray(source.fields)
    ? source.fields : {};
  return hasSelectedField(fields, 'corporate') || hasSelectedField(fields, 'retail') ? 'columns' : 'rows';
}

/** 管理页按当前模式取得固定字段定义，未知模式安全回到旧行模式。 */
export function getCompositionFieldSpecs(mode = 'rows') {
  return COMPOSITION_FIELD_SPECS[mode] || COMPOSITION_FIELD_SPECS.rows;
}

/** 只取运行时需要的绑定字段，避免把旧 block 的 display 配置带进代码绑定。 */
export function normalizeBinding(raw = {}, slot = raw?.slot) {
  if (isCorporateBindingSlot(slot) && slot !== 'branches') return normalizeCorporateBinding(raw, slot);
  const source = parseJson(raw, {});
  const out = {};
  if (source.dsId !== undefined && source.dsId !== null && source.dsId !== '') out.dsId = source.dsId;
  const defaultPeriod = ['trend', 'branchTrend', 'retailTrend', 'corpTrend'].includes(slot)
    ? 'LAST_6M_EOM' : 'LATEST';
  out.period = String(source.period || defaultPeriod);
  out.fields = {};
  for (const [semantic, column] of Object.entries(source.fields || {})) {
    if (column !== undefined && column !== null && String(column).trim()) out.fields[semantic] = String(column).trim();
  }
  out.units = {};
  for (const [semantic, unit] of Object.entries(source.units || {})) {
    if (UNIT_VALUES.includes(unit)) out.units[semantic] = unit;
  }
  // slot 身份固定存于 ChartWidget.propValue.bindingKey；bindJson 只承载数据身份，
  // 不重复落盘一个可被篡改的 slot 字段。
  return out;
}

export function buildBinding(dsId, fields = {}, units = {}, period) {
  const payload = { dsId, fields, units };
  if (period !== undefined && period !== null && period !== '') payload.period = period;
  return normalizeBinding(payload);
}

export function validateBinding(slot, raw = {}) {
  if (isCorporateBindingSlot(slot) && slot !== 'branches') {
    const issues = validateCorporateBinding(slot, raw);
    const normalized = normalizeCorporateBinding(raw, slot);
    if (!PERIOD_VALUES.includes(normalized.period) && !issues.includes('周期无效')) issues.push('周期无效');
    return issues;
  }
  const issues = [];
  if (!isBindingSlot(slot)) return ['槽位不受支持'];
  const binding = normalizeBinding(raw, slot);
  if (!Number.isSafeInteger(binding.dsId) || binding.dsId <= 0) issues.push('数据源无效');
  const spec = BINDING_SLOTS[slot];
  if (!PERIOD_VALUES.includes(binding.period)) issues.push('周期无效');
  const compositionMode = slot === 'composition' ? getCompositionMode(binding) : null;
  const allowedFields = new Set((slot === 'composition'
    ? getCompositionFieldSpecs(compositionMode)
    : spec.fields || []).map(item => item.semantic));
  for (const semantic of Object.keys(raw.fields || {})) {
    if (!allowedFields.has(semantic)) issues.push(`字段不受支持: ${semantic}`);
  }
  if (slot === 'composition') {
    const mode = compositionMode;
    const hasRowField = hasSelectedField(binding.fields, 'name') || hasSelectedField(binding.fields, 'value');
    if (mode === 'columns' && hasRowField) {
      issues.push('构成字段模式不能混用');
    }
    const requiredFields = mode === 'columns' ? ['corporate', 'retail'] : ['name', 'value'];
    for (const semantic of requiredFields) {
      if (!hasSelectedField(binding.fields, semantic)) issues.push(`缺少字段: ${semantic}`);
    }
  } else {
    for (const semantic of spec.required || []) {
      if (!binding.fields?.[semantic]) issues.push(`缺少字段: ${semantic}`);
    }
  }
  const atLeastOneOf = spec.atLeastOneOf || [];
  if (atLeastOneOf.length && !atLeastOneOf.some(semantic => binding.fields?.[semantic])) {
    issues.push(`至少选择一个字段: ${atLeastOneOf.join('、')}`);
  }
  const oneOfRequired = spec.oneOfRequired || [];
  if (oneOfRequired.length && !oneOfRequired.some(semantic => binding.fields?.[semantic])) {
    issues.push(`至少选择一个身份字段: ${oneOfRequired.join('、')}`);
  }
  // Every mapped metric must carry an explicit, proven input unit.  A missing
  // unit is unsafe for rate/count fields as well as custom amount sources; the
  // editor may only fill YUAN automatically when saved metadata declares the
  // source's amount scale.
  for (const fieldSpec of spec.fields || []) {
    if (!fieldSpec.unitKinds?.length || !binding.fields?.[fieldSpec.semantic]) continue;
    if (!binding.units?.[fieldSpec.semantic]) issues.push(`缺少单位: ${fieldSpec.semantic}`);
  }
  for (const [semantic, unit] of Object.entries(raw.units || {})) {
    if (!allowedFields.has(semantic) || !binding.fields?.[semantic]) {
      issues.push(`单位未绑定字段: ${semantic}`);
      continue;
    }
    if (!UNIT_VALUES.includes(unit)) issues.push(`单位无效: ${semantic}`);
    const fieldSpec = (spec.fields || []).find(item => item.semantic === semantic);
    if (UNIT_VALUES.includes(unit) && fieldSpec?.unitKinds?.length && !fieldSpec.unitKinds.includes(unit)) {
      issues.push(`单位不适用: ${semantic}`);
    }
  }
  if (slot === 'composition' && compositionMode === 'columns'
      && hasSelectedField(binding.fields, 'corporate') && hasSelectedField(binding.fields, 'retail')) {
    const unitKind = unit => amountUnits.includes(unit) ? 'amount' : ratioUnits.includes(unit) ? 'ratio' : null;
    const kinds = ['corporate', 'retail', 'total']
      .filter(semantic => hasSelectedField(binding.fields, semantic))
      .map(semantic => unitKind(binding.units?.[semantic]));
    if (kinds.some(kind => kind && kind !== kinds.find(Boolean))) {
      issues.push('构成单位类型必须一致');
    }
  }
  return issues;
}

/**
 * 数据源字段候选严格来自保存的 config_json.fieldMeta/metrics 快照。
 * 这里不调用 probe-columns，也不从列名猜测语义；重复列按首次出现保序去重。
 */
export function getDatasourceFieldOptions(datasource = {}) {
  const config = parseJson(datasource.configJson, datasource.configJson || {});
  const options = [];
  const seen = new Set();
  const add = (item, fallbackRole = 'METRIC') => {
    const col = String(item?.col || item?.metricName || item?.metricCode || '').trim();
    if (!col) return;
    if (seen.has(col)) {
      // A deterministic engine dimension may also be repeated in fieldMeta.
      // Preserve the metadata label/role while recording that the column is
      // still backed by the fixed output shape.
      if (item?.builtin) {
        const existing = options.find(option => option.col === col);
        if (existing) existing.builtin = true;
      }
      return;
    }
    seen.add(col);
    options.push({
      col,
      label: String(item?.alias || item?.metricName || item?.metricCode || col).trim(),
      role: String(item?.role || fallbackRole).toUpperCase(),
      amountScale: item?.amountScale || null,
      unit: item?.unit || null,
      builtin: item?.builtin === true
    });
  };
  // fieldMeta is an explicit output schema: a missing/unknown role must stay
  // unknown so the editor's DIM/METRIC filter fails closed. Legacy metrics
  // entries still default to METRIC below because their source kind defines
  // them as numeric output columns.
  for (const item of Array.isArray(config?.fieldMeta) ? config.fieldMeta : []) add(item, 'UNKNOWN');
  for (const item of Array.isArray(config?.metrics) ? config.metrics : []) add(item);

  // 这些列由 ScreenQueryEngine 的固定 SELECT 形状产生，而不是 datasource
  // metrics 配置。它们是可证明的维度候选，可以安全补入管理页；CUSTOM_SQL
  // 没有固定列契约，继续只显示其已声明 metadata。
  const sourceKind = String(datasource.sourceKind || datasource.source_kind || config?.sourceKind || '').toUpperCase();
  const table = String(config?.table || '').toUpperCase();
  const aggregation = config?.aggregation && typeof config.aggregation === 'object'
    ? String(config.aggregation.groupBy || '').toUpperCase() : '';
  const addDimension = (col, label = col) => add({ col, alias: label, role: 'DIM', builtin: true }, 'DIM');
  if (sourceKind === 'WIDE_TABLE') {
    if (!aggregation || aggregation === 'DATE') addDimension('data_date', '数据日期');
    if (aggregation === 'SUBJECT') {
      const fallbackSubject = {
        ORG_INDEX_RESULT: ['org_code', '机构号'],
        EMP_INDEX_RESULT: ['emp_id', '员工号'],
        CUST_INDEX_RESULT: ['cust_no', '客户号']
      }[table];
      const configuredSubject = String(config?.subjectCol || '').trim();
      const subject = configuredSubject
        ? [configuredSubject, configuredSubject]
        : fallbackSubject;
      if (subject) addDimension(subject[0], subject[1]);
      if (table === 'ORG_INDEX_RESULT') addDimension('org_name', '机构名称');
    }
  } else if (sourceKind === 'KPI_RESULT') {
    addDimension('data_date', '数据日期');
    addDimension('KPI总分', 'KPI总分');
  } else if (sourceKind === 'KPI_DETAIL') {
    const mode = String(config?.mode || '').toUpperCase();
    if (mode === 'SNAPSHOT') {
      for (const [col, label] of [
        ['metric_code', '指标编码'], ['细项名称', '细项名称'], ['目标值', '目标值'],
        ['实际值', '实际值'], ['权重', '权重'], ['得分', '得分'], ['完成率', '完成率'], ['缺口', '缺口']
      ]) add({ col, alias: label, role: col === 'metric_code' || col === '细项名称' ? 'DIM' : 'METRIC' });
    } else if (mode === 'TREND') {
      addDimension('data_date', '数据日期');
      for (const item of Array.isArray(config?.metrics) ? config.metrics : []) {
        const col = String(item?.metricName || item?.metricCode || '').trim();
        if (col) add({ col, alias: col, role: 'METRIC' });
      }
    }
  }
  return options;
}

function existingBindingKey(component) {
  const propValue = component?.propValue;
  return propValue && typeof propValue === 'object' ? propValue.bindingKey : undefined;
}

/**
 * 由当前编辑的绑定树生成固定尺寸 CODE 组件。
 * 旧组件只用于寻找可复用 blockId；任何旧标题/innerType 都不参与槽位猜测。
 */
export function buildCodeComponents(bindings = {}, existingComponents = []) {
  const oldBySlot = new Map();
  for (const component of Array.isArray(existingComponents) ? existingComponents : []) {
    const slot = existingBindingKey(component);
    if (isBindingSlot(slot) && !oldBySlot.has(slot)) oldBySlot.set(slot, component);
  }
  return ALL_SLOT_ORDER
    .filter(slot => bindings[slot])
    .map(slot => {
      const old = oldBySlot.get(slot);
      const binding = normalizeBinding(bindings[slot], slot);
      return {
        id: old?.id || `panorama-${slot}`,
        component: 'ChartWidget',
        innerType: SLOT_INNER_TYPES[slot],
        blockId: Number.isSafeInteger(old?.blockId) && old.blockId > 0 ? old.blockId : null,
        style: { top: 0, left: 0, width: 320, height: 180 },
        propValue: { bindingKey: slot },
        bindJson: JSON.stringify(binding),
        styleJson: JSON.stringify({ title: BINDING_SLOTS[slot].label }),
        drillJson: '{}',
        isLock: true,
        isShow: true
      };
    });
}

export function parseBindingFromComponent(component = {}) {
  const slot = existingBindingKey(component);
  if (!isBindingSlot(slot)) return null;
  return { slot, binding: normalizeBinding(component.bindJson || {}) };
}
