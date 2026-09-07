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
 * - trend/branchTrend 必须有 date，并至少配置 deposit 或 loan 之一。
 * - citySummary 必须有 cityCode，并至少配置一个可展示指标。
 */
export const BINDING_SLOTS = Object.freeze({
  deposit: singleMetric('存款余额'),
  loan: singleMetric('贷款余额'),
  customers: singleMetric('客户总量', 'count'),
  revenue: singleMetric('营收'),
  rate: singleMetric('目标完成率', 'ratio'),
  trend: Object.freeze({
    label: '经营趋势',
    innerType: 'LINE_TREND',
    required: ['date'],
    atLeastOneOf: ['deposit', 'loan'],
    fields: Object.freeze([
      field('date', '日期', { required: true, kind: 'dimension' }),
      field('deposit', '存款余额', { unitKinds: amountUnits }),
      field('loan', '贷款余额', { unitKinds: amountUnits }),
      field('customers', '客户总量', { unitKinds: countUnits }),
      field('rate', '完成率', { unitKinds: ratioUnits })
    ])
  }),
  composition: Object.freeze({
    label: '业务构成',
    innerType: 'PIE_SHARE',
    required: ['name', 'value'],
    fields: Object.freeze([
      field('name', '构成名称', { required: true, kind: 'dimension' }),
      field('value', '构成值', { required: true, unitKinds: amountUnits.concat(ratioUnits) })
    ])
  }),
  ranking: Object.freeze({
    label: '机构排名',
    innerType: 'RANK_LIST',
    required: ['orgCode', 'name', 'value'],
    fields: Object.freeze([
      field('orgCode', '机构号', { required: true, kind: 'dimension' }),
      field('name', '机构名称', { required: true, kind: 'dimension' }),
      field('value', '排名值', { required: true, unitKinds: amountUnits }),
      field('change', '较上期变化', { unitKinds: ratioUnits })
    ])
  }),
  attention: Object.freeze({
    label: '经营关注',
    innerType: 'TABLE_LIST',
    required: ['label', 'count'],
    fields: Object.freeze([
      field('label', '关注事项', { required: true, kind: 'dimension' }),
      field('count', '数量', { required: true, unitKinds: countUnits })
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
      field('customers', '客户总量', { unitKinds: countUnits }),
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
      field('customers', '客户总量', { unitKinds: countUnits }),
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
      field('customers', '客户总量', { unitKinds: countUnits }),
      field('revenue', '营收', { unitKinds: amountUnits }),
      field('rate', '完成率', { unitKinds: ratioUnits })
    ])
  })
});

export const SLOT_ORDER = Object.freeze(Object.keys(BINDING_SLOTS));

const SLOT_INNER_TYPES = Object.freeze(Object.fromEntries(
  SLOT_ORDER.map(slot => [slot, BINDING_SLOTS[slot].innerType])
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

/** 只取运行时需要的绑定字段，避免把旧 block 的 display 配置带进代码绑定。 */
export function normalizeBinding(raw = {}, slot = raw?.slot) {
  const source = parseJson(raw, {});
  const out = {};
  if (source.dsId !== undefined && source.dsId !== null && source.dsId !== '') out.dsId = source.dsId;
  const defaultPeriod = slot === 'trend' || slot === 'branchTrend' ? 'LAST_6M_EOM' : 'LATEST';
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
  const issues = [];
  if (!isBindingSlot(slot)) return ['槽位不受支持'];
  const binding = normalizeBinding(raw, slot);
  if (!Number.isSafeInteger(binding.dsId) || binding.dsId <= 0) issues.push('数据源无效');
  const spec = BINDING_SLOTS[slot];
  if (!PERIOD_VALUES.includes(binding.period)) issues.push('周期无效');
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
  for (const item of Array.isArray(config?.fieldMeta) ? config.fieldMeta : []) add(item);
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
  return SLOT_ORDER
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
