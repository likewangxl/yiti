import {
  BINDING_SLOTS,
  SLOT_ORDER,
  normalizeBinding
} from './bindings';
import { RETAIL_BINDING_SLOTS, RETAIL_SLOT_ORDER, RETAIL_TEMPLATE } from './retailBindings';
import { isDatasourceCompatible, normalizeScreenScope } from '@/utils/screenScope';

/**
 * 代码化大屏的安全默认绑定。
 *
 * 这里故意不从来源名称、字段别名或列名的相似度推断业务口径。能够自动落盘的
 * 字段必须有明确 semantic、metricCode，或属于引擎固定输出的内置维度。没有唯一
 * 候选、范围不相容或单位没有证据时，返回缺口交给配置人员处理。
 */

export const BRANCH_TEMPLATE = 'branch-overview-v1';
export const AUTO_BIND_STATUS = Object.freeze({
  APPLIED: 'applied',
  PRESERVED: 'preserved',
  AMBIGUOUS: 'ambiguous',
  MISSING: 'missing',
  BLOCKED: 'blocked'
});

const AMOUNT_UNITS = new Set(['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION']);
const UNIT_LABELS = Object.freeze({
  YUAN: '元',
  TEN_THOUSAND: '万元',
  HUNDRED_MILLION: '亿元',
  COUNT: '个数',
  TEN_THOUSAND_COUNT: '万户',
  PERCENT: '%',
  RATIO: '比例'
});
const AMOUNT_FIELD_NAMES = new Set([
  'value', 'deposit', 'depositIncrease', 'depositAverage', 'loan', 'revenue',
  'rate', 'corporate', 'retail', 'actual', 'target', 'aum', 'increase', 'nplRate'
]);
const SINGLE_VALUE_SLOTS = new Set([
  'deposit', 'depositIncrease', 'depositAverage', 'loan', 'customers', 'revenue', 'rate',
  'retailAum', 'retailDeposit', 'retailDepositAverage', 'retailRevenue', 'retailValueCustomers',
  'retailLoan', 'retailNplRate'
]);
const TIME_SERIES_SLOTS = new Set(['trend', 'retailTrend', 'branchTrend']);
const DIMENSION_SEMANTICS = new Set([
  'date', 'dataDate', 'orgCode', 'orgName', 'cityCode', 'cityName', 'ownerOperatingOrgCode',
  'parentOrgCode', 'lng', 'lat', 'coordSys', 'located', 'name', 'label', 'owner', 'deadline'
]);

// 这是用户已确认的现有机构指标宽表原始口径，只适用于 ORG_INDEX_RESULT。
// 旧数据源 fieldMeta 中的“万元”是展示遗留值，不能覆盖这里的受控口径。
const CONFIRMED_ORG_AMOUNT_CODES = new Set(['M_0265', 'M_0277', 'M_0309']);

const METRIC_RULES = Object.freeze({
  deposit: {
    value: { semantics: ['deposit', 'depositBalance', 'balance.deposit'] }
  },
  depositAverage: {
    value: { semantics: ['depositAverage', 'depositMonthAverage', 'average.deposit'], codes: ['M_0265'] }
  },
  depositIncrease: {
    // M_0266 是“月均余额较上月”，已知不是时点余额净增，不能自动拿来冒充净增。
    value: { semantics: ['depositIncrease', 'depositBalanceIncrease', 'increase.deposit'] }
  },
  loan: {
    value: { semantics: ['loan', 'loanBalance', 'balance.loan'] }
  },
  customers: {
    value: { semantics: ['customers', 'customerCount', 'customers.total'] }
  },
  revenue: {
    value: { semantics: ['revenue', 'operatingRevenue', 'revenue.total'] }
  },
  rate: {
    value: { semantics: ['rate', 'completionRate', 'targetCompletionRate'] }
  },
  trend: {
    date: { semantics: ['date', 'dataDate'], builtin: ['data_date'] },
    deposit: { semantics: ['deposit', 'depositBalance'] },
    loan: { semantics: ['loan', 'loanBalance'] },
    depositIncrease: { semantics: ['depositIncrease', 'depositBalanceIncrease'] },
    customers: { semantics: ['customers', 'customerCount'] },
    rate: { semantics: ['rate', 'completionRate'] }
  },
  composition: {
    name: { semantics: ['compositionName', 'businessName'] },
    value: { semantics: ['compositionValue', 'businessValue'] },
    corporate: { semantics: ['corporate', 'corporateDeposit', 'composition.corporate'], codes: ['M_0277'] },
    retail: { semantics: ['retail', 'retailDeposit', 'composition.retail'], codes: ['M_0309'] }
  },
  ranking: {
    orgCode: { builtin: ['org_code'], semantics: ['orgCode'] },
    name: { builtin: ['org_name'], semantics: ['orgName'] },
    value: { semantics: ['rankingValue', 'rankingDeposit', 'depositBalance'] },
    increase: { semantics: ['depositIncrease', 'depositBalanceIncrease'] },
    average: { semantics: ['depositAverage', 'depositMonthAverage'], codes: ['M_0265'] },
    change: { semantics: ['change', 'changeRate'] }
  },
  attention: {
    label: { semantics: ['attentionLabel', 'label'] },
    count: { semantics: ['attentionCount', 'count'] },
    orgCode: { builtin: ['org_code'], semantics: ['orgCode'] }
  },
  branches: {
    orgCode: { builtin: ['org_code'], semantics: ['orgCode'] },
    orgName: { builtin: ['org_name'], semantics: ['orgName'] },
    cityCode: { semantics: ['cityCode'] },
    cityName: { semantics: ['cityName'] },
    ownerOperatingOrgCode: { semantics: ['ownerOperatingOrgCode'] },
    parentOrgCode: { semantics: ['parentOrgCode'] },
    lng: { semantics: ['lng', 'longitude'] },
    lat: { semantics: ['lat', 'latitude'] },
    coordSys: { semantics: ['coordSys', 'coordinateSystem'] },
    located: { semantics: ['located', 'isLocated'] },
    deposit: { semantics: ['deposit', 'depositBalance'] },
    loan: { semantics: ['loan', 'loanBalance'] },
    customers: { semantics: ['customers', 'customerCount'] },
    target: { semantics: ['target', 'targetValue'] },
    rate: { semantics: ['rate', 'completionRate'] }
  },
  branchTrend: {
    date: { semantics: ['date', 'dataDate'], builtin: ['data_date'] },
    deposit: { semantics: ['deposit', 'depositBalance'] },
    loan: { semantics: ['loan', 'loanBalance'] },
    customers: { semantics: ['customers', 'customerCount'] },
    rate: { semantics: ['rate', 'completionRate'] }
  },
  citySummary: {
    cityCode: { semantics: ['cityCode'] },
    cityName: { semantics: ['cityName'] },
    orgCode: { builtin: ['org_code'], semantics: ['orgCode'] },
    orgName: { builtin: ['org_name'], semantics: ['orgName'] },
    deposit: { semantics: ['deposit', 'depositBalance'] },
    loan: { semantics: ['loan', 'loanBalance'] },
    customers: { semantics: ['customers', 'customerCount'] },
    revenue: { semantics: ['revenue', 'operatingRevenue'] },
    rate: { semantics: ['rate', 'completionRate'] }
  }
});

const RETAIL_SEMANTICS = Object.freeze({
  retailAum: { value: ['aum', 'retailAum'], change: ['change', 'aumChange'] },
  retailDeposit: { value: ['deposit', 'retailDeposit', 'depositBalance'], change: ['change', 'depositChange'] },
  retailDepositAverage: { value: ['depositAverage', 'retailDepositAverage'], change: ['change'] },
  retailRevenue: { value: ['revenue', 'retailRevenue'], change: ['change', 'revenueChange'] },
  retailValueCustomers: { value: ['valueCustomers', 'retailValueCustomers', 'customerCount'], change: ['change'] },
  retailLoan: { value: ['loan', 'retailLoan', 'loanBalance'], change: ['change', 'loanChange'] },
  retailNplRate: { value: ['nplRate', 'retailNplRate'], change: ['change', 'nplChange'] },
  retailTrend: { date: ['date', 'dataDate'], aum: ['aum', 'retailAum'], deposit: ['deposit', 'retailDeposit'] },
  retailSegments: { name: ['segmentName', 'customerSegment'], customers: ['customers', 'customerCount'], aum: ['aum', 'segmentAum'] },
  retailRanking: { orgCode: ['orgCode'], name: ['orgName'], aum: ['aum', 'retailAum'], increase: ['increase', 'aumIncrease'], rate: ['rate', 'completionRate'], nplRate: ['nplRate'] },
  retailAttention: { label: ['attentionLabel', 'label'], count: ['attentionCount', 'count'], owner: ['owner'], deadline: ['deadline'] },
  retailTargets: { name: ['targetName'], actual: ['actual', 'actualValue'], target: ['target', 'targetValue'] },
  branches: METRIC_RULES.branches
});

function object(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
}

function parseJson(value) {
  if (object(value) === value) return value;
  if (typeof value !== 'string') return {};
  try { return object(JSON.parse(value)); } catch { return {}; }
}

function upper(value) {
  return String(value ?? '').trim().toUpperCase();
}

function text(value) {
  return String(value ?? '').trim();
}

function list(value) {
  if (Array.isArray(value)) return value.map(text).filter(Boolean);
  return text(value) ? [text(value)] : [];
}

function normalizeUnit(value) {
  const key = upper(value).replaceAll(' ', '');
  const labels = {
    元: 'YUAN', RMB: 'YUAN', YUAN: 'YUAN',
    万元: 'TEN_THOUSAND', TEN_THOUSAND: 'TEN_THOUSAND', TEN_THOUSAND_YUAN: 'TEN_THOUSAND',
    亿元: 'HUNDRED_MILLION', HUNDRED_MILLION: 'HUNDRED_MILLION', HUNDRED_MILLION_YUAN: 'HUNDRED_MILLION',
    个: 'COUNT', 个数: 'COUNT', COUNT: 'COUNT',
    万户: 'TEN_THOUSAND_COUNT', TEN_THOUSAND_COUNT: 'TEN_THOUSAND_COUNT',
    '%': 'PERCENT', 百分数: 'PERCENT', PERCENT: 'PERCENT',
    比例: 'RATIO', RATIO: 'RATIO'
  };
  return labels[value] || labels[key] || null;
}

function normalizeSemanticValues(item) {
  const metadata = object(item.metadata || item.meta);
  return new Set([
    ...list(item.semantic),
    ...list(item.semantics),
    ...list(item.semanticCode),
    ...list(metadata.semantic),
    ...list(metadata.semantics),
    ...list(metadata.semanticCode)
  ]);
}

function metricCodeOf(item) {
  const metadata = object(item.metadata || item.meta);
  return text(item.metricCode || item.metric_code || item.code || metadata.metricCode || metadata.metric_code);
}

function metricNameOf(item) {
  return text(item.metricName || item.metric_name || item.name || item.alias || item.col);
}

function configuredFieldMeta(config) {
  const fields = [
    ...(Array.isArray(config.fieldMeta) ? config.fieldMeta : []),
    ...(Array.isArray(config.fields) ? config.fields : []),
    ...(Array.isArray(config.columns) ? config.columns : [])
  ];
  return fields.map(item => typeof item === 'string' ? { col: item } : object(item));
}

function outputColumnOf(metric, fieldMeta) {
  const code = metricCodeOf(metric);
  const name = metricNameOf(metric);
  const explicit = text(metric.outputCol || metric.outputColumn || metric.col || metric.column);
  const byCode = fieldMeta.find(field => metricCodeOf(field) && metricCodeOf(field) === code);
  const byName = fieldMeta.find(field => text(field.col) && (text(field.col) === name || text(field.col) === code));
  return text(explicit || byCode?.col || byName?.col || name);
}

function mergeMetadata(metric, field) {
  const metricMeta = object(metric);
  const fieldMeta = object(field);
  const nested = { ...object(metricMeta.metadata || metricMeta.meta), ...object(fieldMeta.metadata || fieldMeta.meta) };
  return {
    ...metricMeta,
    ...fieldMeta,
    ...nested,
    metricCode: metricCodeOf(fieldMeta) || metricCodeOf(metricMeta) || null,
    semanticValues: new Set([...normalizeSemanticValues(metricMeta), ...normalizeSemanticValues(fieldMeta)])
  };
}

function datasourceConfig(source) {
  return parseJson(source?.configJson || source?.config || source?.configuration);
}

function sourceFields(source) {
  const config = datasourceConfig(source);
  const fieldMeta = configuredFieldMeta(config);
  const byCol = new Map(fieldMeta.map(field => [text(field.col), field]).filter(([col]) => col));
  const records = [];
  const add = (item, extra = {}) => {
    const merged = mergeMetadata(item, extra);
    const col = text(merged.col || merged.outputCol || merged.outputColumn);
    if (!col) return;
    const duplicate = records.find(row => row.col === col);
    const candidate = {
      col,
      label: text(merged.alias || merged.metricName || merged.name || col),
      role: upper(merged.role || 'UNKNOWN'),
      semanticValues: merged.semanticValues || new Set(),
      metricCode: text(merged.metricCode),
      inputUnit: normalizeUnit(merged.inputUnit || merged.rawUnit || merged.sourceUnit || merged.unit),
      explicitInputUnit: text(merged.inputUnit || merged.rawUnit || merged.sourceUnit || merged.unit),
      builtin: merged.builtin === true,
      sourceType: upper(merged.sourceType || merged.valueSource || merged.dataOrigin),
      scope: upper(merged.scope || merged.measureScope || merged.metricScope),
      aggregation: upper(merged.aggregation || merged.grain || merged.periodType),
      metadata: merged
    };
    if (duplicate) {
      duplicate.semanticValues = new Set([...duplicate.semanticValues, ...candidate.semanticValues]);
      duplicate.metricCode ||= candidate.metricCode;
      duplicate.label = duplicate.label || candidate.label;
      duplicate.inputUnit ||= candidate.inputUnit;
      duplicate.explicitInputUnit ||= candidate.explicitInputUnit;
      duplicate.builtin ||= candidate.builtin;
      duplicate.metadata = { ...duplicate.metadata, ...candidate.metadata };
      return;
    }
    records.push(candidate);
  };

  for (const field of fieldMeta) add(field);
  for (const metric of Array.isArray(config.metrics) ? config.metrics : []) {
    const col = outputColumnOf(metric, fieldMeta);
    if (!col) continue;
    const linked = byCol.get(col) || {};
    // 引导式 metrics 目录本身就是数值输出；只有 fieldMeta 明确声明为
    // DIM 时才允许覆盖，否则缺省为 METRIC（与 bindings.js 的候选规则一致）。
    add({ ...metric, col, role: metric.role || linked.role || 'METRIC' }, linked);
  }

  const sourceKind = upper(source?.sourceKind || source?.source_kind || config.sourceKind);
  const table = upper(config.table);
  const aggregation = upper(config?.aggregation?.groupBy || '');
  const addBuiltin = (col, label) => add({ col, alias: label, role: 'DIM', builtin: true });
  if (sourceKind === 'WIDE_TABLE') {
    if (!aggregation || aggregation === 'DATE') addBuiltin('data_date', '数据日期');
    if (aggregation === 'SUBJECT' && table === 'ORG_INDEX_RESULT') {
      addBuiltin('org_code', '机构号');
      addBuiltin('org_name', '机构名称');
    }
  } else if (sourceKind === 'KPI_DETAIL' && upper(config.mode) === 'SNAPSHOT') {
    addBuiltin('metric_code', '指标编码');
  }
  return records;
}

function metricRule(template, slot, semantic) {
  if (template === RETAIL_TEMPLATE) {
    const explicit = RETAIL_SEMANTICS[slot]?.[semantic];
    if (Array.isArray(explicit)) return { semantics: explicit };
    if (explicit && typeof explicit === 'object') return explicit;
    if (slot === 'branches') return METRIC_RULES.branches[semantic] || null;
  }
  const standard = METRIC_RULES[slot]?.[semantic];
  if (standard) return standard;
  return { semantics: [semantic] };
}

function fieldSpec(template, slot, semantic) {
  const slots = template === RETAIL_TEMPLATE ? RETAIL_BINDING_SLOTS : BINDING_SLOTS;
  return slots[slot]?.fields?.find(field => field.semantic === semantic) || null;
}

function slotSpec(template, slot) {
  const slots = template === RETAIL_TEMPLATE ? RETAIL_BINDING_SLOTS : BINDING_SLOTS;
  return slots[slot] || null;
}

function candidateMatches(rule, candidate) {
  if (!rule || !candidate) return false;
  if (rule.builtin?.includes(candidate.col) && candidate.builtin) return true;
  const semanticMatched = (rule.semantics || []).some(value => candidate.semanticValues.has(value));
  const codeMatched = (rule.codes || []).includes(candidate.metricCode);
  return semanticMatched || codeMatched;
}

function amountSemantic(template, slot, semantic) {
  return AMOUNT_FIELD_NAMES.has(semantic) || Boolean(fieldSpec(template, slot, semantic)?.unitKinds?.some(unit => AMOUNT_UNITS.has(unit)));
}

function confirmedUnit(source, candidate, template, slot, semantic) {
  const config = datasourceConfig(source);
  const table = upper(config.table);
  if (source?.sourceKind === 'WIDE_TABLE' && table === 'ORG_INDEX_RESULT'
      && CONFIRMED_ORG_AMOUNT_CODES.has(candidate.metricCode) && amountSemantic(template, slot, semantic)) {
    return 'HUNDRED_MILLION';
  }
  return candidate.inputUnit;
}

function normalizedScope(screenScope) {
  return normalizeScreenScope(screenScope || {});
}

function sourceIsActive(source) {
  return source?.status === 'ACTIVE' || source?.status === 1 || source?.status === '1' || source?.status === true;
}

function sourceLineAllowed(source, screen, template) {
  const sourceLine = upper(source?.bizLine || source?.biz_line || '');
  const screenLine = upper(screen.bizLine || 'COMMON');
  if (template === RETAIL_TEMPLATE) return sourceLine === 'RETAIL';
  return isDatasourceCompatible(screenLine, sourceLine);
}

function configGroup(source) {
  return upper(datasourceConfig(source)?.aggregation?.groupBy || '');
}

function sourceScopeMode(source) {
  return upper(datasourceConfig(source)?.scopeMode || 'SUBJECT');
}

function explicitSourceShape(source) {
  const config = datasourceConfig(source);
  const schema = object(config.schema || config.outputSchema || config.resultSchema);
  return upper(config.resultShape || config.outputShape || config.rowShape || config.cardinality
    || schema.shape || schema.resultShape || schema.cardinality);
}

function hasExplicitOrgContext(source) {
  const config = datasourceConfig(source);
  return Boolean(text(config.subjectCol || config.subjectParam || config.orgCode || config.org_code));
}

const INSTITUTION_LIST_SLOTS = new Set(['branches', 'ranking', 'retailRanking']);

/**
 * CUSTOM_SQL 只有在配置明确声明逐行 TABLE 结果，并把 org_code 声明为 DIM 时，
 * 才能作为机构列表来源。scopeMode 描述权限范围，不代替 SQL 输出粒度证明。
 */
function isCustomSqlInstitutionTable(source) {
  const config = datasourceConfig(source);
  const sourceKind = upper(source?.sourceKind || source?.source_kind || config.sourceKind);
  if (sourceKind !== 'CUSTOM_SQL' || explicitSourceShape(source) !== 'TABLE') return false;
  return sourceFields(source).some(candidate => upper(candidate.col) === 'ORG_CODE'
    && candidate.role === 'DIM');
}

function sourceStructureIssue(source, screen, slot, mode) {
  const config = datasourceConfig(source);
  const sourceKind = upper(source?.sourceKind || source?.source_kind || config.sourceKind);
  const table = upper(config.table);
  const groupBy = configGroup(source);
  const scopeMode = sourceScopeMode(source);
  const named = upper(screen.orgScopeMode) === 'NAMED_GROUP';

  if (sourceKind === 'WIDE_TABLE' && SINGLE_VALUE_SLOTS.has(slot) && groupBy !== 'NONE') {
    return '单值展示需要 WIDE_TABLE 明确按 NONE 聚合为一行';
  }
  if (sourceKind === 'WIDE_TABLE' && TIME_SERIES_SLOTS.has(slot)) {
    if (slot === 'branchTrend' && !groupBy && hasExplicitOrgContext(source)) {
      // 支行趋势允许服务端由明确 orgCode 收窄未聚合宽表；不能靠屏名或来源名推断。
    } else if (groupBy !== 'DATE') {
      return '趋势展示需要 WIDE_TABLE 明确按 DATE 聚合';
    }
  }
  if (sourceKind === 'WIDE_TABLE' && ['branches', 'ranking', 'retailRanking'].includes(slot) && groupBy !== 'SUBJECT') {
    return '机构展示需要 WIDE_TABLE 明确按 SUBJECT 聚合';
  }
  if (INSTITUTION_LIST_SLOTS.has(slot) && sourceKind === 'CUSTOM_SQL'
      && !isCustomSqlInstitutionTable(source)) {
    return '机构展示需要 CUSTOM_SQL 明确 TABLE 逐机构结构和 org_code 机构维度';
  }
  if (INSTITUTION_LIST_SLOTS.has(slot) && scopeMode === 'GLOBAL'
      && !(sourceKind === 'CUSTOM_SQL' && isCustomSqlInstitutionTable(source))) {
    return '机构展示不能使用全局汇总来源';
  }
  if (sourceKind !== 'WIDE_TABLE' && SINGLE_VALUE_SLOTS.has(slot) && explicitSourceShape(source) !== 'SINGLE') {
    return '非宽表来源缺少已确认的单值结果结构';
  }
  if (sourceKind !== 'WIDE_TABLE' && TIME_SERIES_SLOTS.has(slot) && explicitSourceShape(source) !== 'TIMESERIES') {
    return '非宽表来源缺少已确认的时序结果结构';
  }

  if (named) {
    if (sourceKind !== 'WIDE_TABLE' || table !== 'ORG_INDEX_RESULT' || text(config.subjectCol) !== 'org_code') {
      return '当前机构范围只允许带 org_code 的机构指标宽表';
    }
    if (scopeMode === 'GLOBAL') return '当前机构范围不能使用全局汇总来源';
    if (groupBy && groupBy !== 'SUBJECT' && !(SINGLE_VALUE_SLOTS.has(slot) && groupBy === 'NONE')) {
      return '当前机构范围需要按机构汇总的来源';
    }
    if (slot === 'composition' && mode === 'columns') return '双列构成需要一行来源，命名机构组按机构多行来源粒度不符';
  }
  if (slot === 'composition' && mode === 'columns') {
    if (sourceKind !== 'WIDE_TABLE' || table !== 'ORG_INDEX_RESULT' || groupBy !== 'NONE') {
      return '双列构成需要 ORG_INDEX_RESULT 的一行固定宽表来源';
    }
  }
  if (slot === 'citySummary' && !['SUBJECT', 'DATE', ''].includes(groupBy)) return '城市汇总来源粒度不明确';
  return '';
}

function sourceTypeIssue(source, candidates) {
  const config = datasourceConfig(source);
  const sourceType = upper(config.sourceType || config.valueSource || source.sourceType);
  if (sourceType === 'CONSTANT' || sourceType === 'LITERAL' || config.constant === true) return '常量来源不能作为真实经营数据';
  if (candidates.some(candidate => candidate.sourceType === 'CONSTANT' || candidate.sourceType === 'LITERAL')) {
    return '常量来源不能作为真实经营数据';
  }
  return '';
}

function semanticBlockIssue(source, template, slot, required, optional) {
  const semantics = [...required, ...optional];
  // 仅当错误指标被明确声明为当前展示内容的候选时阻断；同一来源里的
  // 其他指标不能因为存在 M_0347/M_0266 就整体失效。
  if (slot === 'loan' && semantics.includes('value')) {
    const candidates = candidateFor(source, template, slot, 'value');
    if (candidates.some(candidate => candidate.metricCode === 'M_0347')
        && candidates.every(candidate => candidate.metricCode === 'M_0347')) {
      return '对公贷款来源不能作为全行贷款，请先提供明确的全行贷款指标';
    }
  }
  if (slot === 'depositIncrease' && semantics.includes('value')) {
    const candidates = candidateFor(source, template, slot, 'value');
    if (candidates.some(candidate => candidate.metricCode === 'M_0266')
        && candidates.every(candidate => candidate.metricCode === 'M_0266')) {
      return '月均余额较上月来源不能作为时点净增';
    }
  }
  return '';
}

function isEmptyBinding(binding) {
  return !binding || (!binding.dsId && !Object.keys(binding.fields || {}).length);
}

function hasAnyBinding(binding) {
  return Boolean(binding?.dsId || Object.keys(binding?.fields || {}).length);
}

function cloneBinding(binding, slot) {
  const cloned = normalizeBinding(binding || {}, slot);
  cloned.fields = { ...(binding?.fields || cloned.fields || {}) };
  cloned.units = { ...(binding?.units || cloned.units || {}) };
  return cloned;
}

function requiredSemantics(template, slot, mode) {
  const spec = slotSpec(template, slot);
  if (!spec) return [];
  if (slot === 'composition') return mode === 'columns' ? ['corporate', 'retail'] : ['name', 'value'];
  if (slot === 'citySummary') return ['cityCode'];
  return [...(spec.required || [])];
}

function optionalCandidateSemantics(template, slot, mode) {
  const spec = slotSpec(template, slot);
  if (!spec) return [];
  // 机构列表的名称是引擎固定输出的身份维度。它可以补充机构号，
  // 但不能把其他可选指标一并按名称猜测。
  if (slot === 'branches') return ['orgName'];
  if (slot === 'attention') return ['orgCode'];
  const required = new Set(requiredSemantics(template, slot, mode));
  const candidates = [];
  for (const semantic of spec.atLeastOneOf || []) if (!required.has(semantic)) candidates.push(semantic);
  return candidates;
}

function candidateFor(source, template, slot, semantic) {
  const rule = metricRule(template, slot, semantic);
  const fields = sourceFields(source).filter(candidate => {
    const expected = fieldSpec(template, slot, semantic);
    if (!expected) return false;
    if (candidate.role !== upper(expected.kind === 'dimension' ? 'DIM' : 'METRIC')) return false;
    return candidateMatches(rule, candidate);
  });
  return fields;
}

function noUnitIssue(template, slot, semantic) {
  const label = fieldSpec(template, slot, semantic)?.label || semantic;
  return `展示内容“${label}”缺少明确原始单位，不能自动猜测`;
}

function candidateBindingForSource({ source, template, slot, mode, required, optional }) {
  const fields = {};
  const units = {};
  const issues = [];
  const candidateDetails = [];
  const requiredSet = new Set(required);
  const allSemantics = [...required, ...optional];
  for (const semantic of allSemantics) {
    const candidates = candidateFor(source, template, slot, semantic);
    if (candidates.length > 1) {
      issues.push(`展示内容“${fieldSpec(template, slot, semantic)?.label || semantic}”有多个可用字段，请选择`);
      continue;
    }
    if (!candidates.length) {
      if (requiredSet.has(semantic)) issues.push(`没有找到展示内容“${fieldSpec(template, slot, semantic)?.label || semantic}”的明确字段`);
      continue;
    }
    const candidate = candidates[0];
    const expectedSpec = fieldSpec(template, slot, semantic);
    if (expectedSpec?.kind === 'dimension') {
      fields[semantic] = candidate.col;
      candidateDetails.push({ semantic, ...candidate, unit: null });
      continue;
    }
    const unit = confirmedUnit(source, candidate, template, slot, semantic);
    if (expectedSpec?.unitKinds?.length && !unit) {
      issues.push(noUnitIssue(template, slot, semantic));
      continue;
    }
    if (unit && !(expectedSpec?.unitKinds || []).includes(unit)) {
      issues.push(`展示内容“${expectedSpec?.label || semantic}”的单位不适用`);
      continue;
    }
    fields[semantic] = candidate.col;
    if (unit) units[semantic] = unit;
    candidateDetails.push({ semantic, ...candidate, unit });
  }
  const atLeastOne = slotSpec(template, slot)?.atLeastOneOf || [];
  if (atLeastOne.length && !atLeastOne.some(semantic => fields[semantic])) {
    const matching = atLeastOne.flatMap(semantic => candidateFor(source, template, slot, semantic));
    if (matching.length > 1) issues.push('有多个可用指标，需先选择展示内容');
    else if (!matching.length) issues.push('没有找到可用的经营指标');
  }
  return { fields, units, issues, candidateDetails };
}

function sourceCandidateKey(source) {
  return String(source?.id ?? source?.dsId ?? '');
}

function canonicalJson(value) {
  if (Array.isArray(value)) return `[${value.map(canonicalJson).sort().join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value ?? null);
}

/**
 * 同一张一行宽表仅因配置了额外指标而重复出现时视为等价候选。范围、聚合、
 * 过滤和选中的 metricCode/slot 任一不同都保留歧义，不能借“来源名称相似”消歧。
 */
function equivalentSourceSignature(item, required) {
  const source = item.source;
  const config = datasourceConfig(source);
  const groupBy = upper(config?.aggregation?.groupBy || '');
  if (groupBy !== 'NONE') return null;
  const metrics = required.map(semantic => {
    const detail = item.candidateDetails.find(candidate => candidate.semantic === semantic);
    return {
      code: detail?.metricCode || null,
      slot: detail?.metadata?.slot ?? null,
      col: detail?.col || null
    };
  });
  return canonicalJson({
    sourceKind: upper(source?.sourceKind || source?.source_kind),
    table: upper(config.table),
    subjectCol: text(config.subjectCol),
    scopeMode: upper(config.scopeMode || 'SUBJECT'),
    aggregation: config.aggregation || null,
    filters: config.aggregation?.filters || null,
    date: config.dateCol || config.dateColumn || null,
    metrics
  });
}

function collapseEquivalentCandidates(items, required) {
  const groups = new Map();
  for (const item of items) {
    const signature = equivalentSourceSignature(item, required);
    if (!signature) {
      groups.set(`unique:${sourceCandidateKey(item.source)}:${items.indexOf(item)}`, item);
      continue;
    }
    const current = groups.get(signature);
    if (!current) {
      groups.set(signature, item);
      continue;
    }
    const metricCount = sourceFields(item.source).length;
    const currentMetricCount = sourceFields(current.source).length;
    const itemId = Number(item.source.id);
    const currentId = Number(current.source.id);
    if (metricCount < currentMetricCount || (metricCount === currentMetricCount && itemId < currentId)) groups.set(signature, item);
  }
  return [...groups.values()];
}

function selectExistingSource({ existingBinding, datasources, template, screen, slot, mode }) {
  if (!hasAnyBinding(existingBinding) || !existingBinding.dsId) return null;
  const source = datasources.find(item => sourceCandidateKey(item) === String(existingBinding.dsId));
  if (!source || !sourceIsActive(source) || !sourceLineAllowed(source, screen, template)) return null;
  const issue = sourceStructureIssue(source, screen, slot, mode);
  if (issue) return null;
  return source;
}

/**
 * 为一个展示内容解析唯一默认绑定。
 * `existingBinding` 有完整字段时只作为首选并原样保留，调用方可以把它视为手工配置。
 */
export function resolveDefaultBinding(input = {}) {
  const template = input.template || BRANCH_TEMPLATE;
  const slot = text(input.slot);
  const screen = normalizedScope(input.screenScope || input.screen || {});
  const datasources = Array.isArray(input.datasources) ? input.datasources : [];
  const spec = slotSpec(template, slot);
  if (!spec) return { status: AUTO_BIND_STATUS.BLOCKED, binding: null, candidates: [], gap: '展示内容类型不受支持' };
  const mode = slot === 'composition' ? (input.mode === 'columns' ? 'columns' : 'rows') : null;
  const required = requiredSemantics(template, slot, mode);
  const optional = optionalCandidateSemantics(template, slot, mode);
  const existing = input.existingBinding;

  const preferredSource = selectExistingSource({ existingBinding: existing, datasources, template, screen, slot, mode });
  if (preferredSource && required.every(semantic => existing?.fields?.[semantic])
      && required.every(semantic => !fieldSpec(template, slot, semantic)?.unitKinds?.length || existing?.units?.[semantic])) {
    return {
      status: AUTO_BIND_STATUS.PRESERVED,
      binding: cloneBinding(existing, slot),
      source: preferredSource,
      candidates: [preferredSource],
      gap: '',
      appliedSemantics: required
    };
  }

  const scopeMatches = [];
  const scopeRejected = [];
  const blocked = [];
  for (const source of datasources) {
    if (!sourceIsActive(source) || !sourceLineAllowed(source, screen, template)) continue;
    const matching = sourceFields(source).filter(candidate =>
      required.concat(optional).some(semantic => candidateMatches(metricRule(template, slot, semantic), candidate))
    );
    if (!matching.length && !(slot === 'branches' || (slot === 'trend' && candidateFor(source, template, slot, 'date').length))) continue;
    const typeIssue = sourceTypeIssue(source, matching);
    if (typeIssue) { blocked.push({ source, gap: typeIssue }); continue; }
    const semanticIssue = semanticBlockIssue(source, template, slot, required, optional);
    if (semanticIssue) { blocked.push({ source, gap: semanticIssue }); continue; }
    const structureIssue = sourceStructureIssue(source, screen, slot, mode);
    if (structureIssue) { scopeRejected.push({ source, gap: structureIssue }); continue; }
    scopeMatches.push(source);
  }

  if (!scopeMatches.length) {
    if (slot === 'composition' && input.mode === undefined) {
      const columns = resolveDefaultBinding({ ...input, mode: 'columns' });
      if (columns.status !== AUTO_BIND_STATUS.MISSING || columns.binding) return columns;
    }
    const blockedGap = blocked[0]?.gap;
    const scopeGap = scopeRejected[0]?.gap;
    return {
      status: blockedGap ? AUTO_BIND_STATUS.BLOCKED : AUTO_BIND_STATUS.MISSING,
      binding: null,
      candidates: [],
      gap: blockedGap || scopeGap || '当前屏范围和条线没有可用来源'
    };
  }

  const viable = [];
  const incomplete = [];
  for (const source of scopeMatches) {
    const candidateBinding = candidateBindingForSource({ source, template, slot, mode, required, optional });
    if (candidateBinding.issues.length) {
      incomplete.push({ source, ...candidateBinding });
    } else {
      viable.push({ source, ...candidateBinding });
    }
  }

  const collapsedViable = collapseEquivalentCandidates(viable, required);
  if (collapsedViable.length > 1) {
    const preferredId = input.preferredDatasourceId || input.sourceId;
    const preferred = collapsedViable.find(item => String(item.source.id) === String(preferredId));
    if (preferred) {
      const binding = normalizeBinding({
        dsId: preferred.source.id,
        fields: preferred.fields,
        units: preferred.units,
        period: input.period
      }, slot);
      return { status: AUTO_BIND_STATUS.APPLIED, binding, source: preferred.source, candidates: collapsedViable.map(item => item.source), gap: '', appliedSemantics: Object.keys(preferred.fields) };
    }
    return {
      status: AUTO_BIND_STATUS.AMBIGUOUS,
      binding: null,
      candidates: collapsedViable.map(item => item.source),
      gap: '同一展示内容存在多个完整来源，请先选择数据来源'
    };
  }
  if (collapsedViable.length === 1) {
    const selected = collapsedViable[0];
    const binding = normalizeBinding({
      dsId: selected.source.id,
      fields: selected.fields,
      units: selected.units,
      period: input.period
    }, slot);
    return { status: AUTO_BIND_STATUS.APPLIED, binding, source: selected.source, candidates: [selected.source], gap: '', appliedSemantics: Object.keys(selected.fields) };
  }

  // 初次进入构成展示时不要求用户先知道行/列技术形状：只有在默认行模式
  // 没有完整候选时，才尝试固定的一行“对公 + 零售”列式契约。用户明确切换
  // 过模式（input.mode 有值）时不会走这里，避免覆盖手工意图。
  if (slot === 'composition' && input.mode === undefined) {
    const columns = resolveDefaultBinding({ ...input, mode: 'columns' });
    if (columns.status !== AUTO_BIND_STATUS.MISSING || columns.binding) return columns;
  }

  const candidate = incomplete[0];
  return {
    status: AUTO_BIND_STATUS.MISSING,
    binding: null,
    candidates: incomplete.map(item => item.source),
    gap: candidate?.issues?.[0] || '来源字段不足，需手工配置'
  };
}

/**
 * 批量为“空展示内容”应用默认绑定。已有绑定（包括手工半成品）一律保留。
 */
export function applyDefaultBindings(input = {}) {
  const template = input.template || BRANCH_TEMPLATE;
  const order = Array.isArray(input.slots) ? input.slots : (template === RETAIL_TEMPLATE ? RETAIL_SLOT_ORDER : SLOT_ORDER);
  const current = input.bindings && typeof input.bindings === 'object' ? input.bindings : {};
  const bindings = Object.fromEntries(Object.entries(current).map(([slot, binding]) => [slot, cloneBinding(binding, slot)]));
  const applied = [];
  const preserved = [];
  const gaps = [];
  for (const slot of order) {
    const existing = bindings[slot];
    if (!isEmptyBinding(existing)) {
      preserved.push(slot);
      continue;
    }
    const result = resolveDefaultBinding({ ...input, slot, existingBinding: existing });
    if (result.binding && result.status === AUTO_BIND_STATUS.APPLIED) {
      bindings[slot] = result.binding;
      applied.push(slot);
    } else if (result.gap) {
      gaps.push({ slot, status: result.status, message: result.gap, candidates: result.candidates || [] });
    }
  }
  return { bindings, applied, preserved, gaps };
}

/**
 * 数据源切换后的语义预填：只补缺失字段和单位，已有人工映射不被覆盖。
 */
export function prefillBindingForDatasource(input = {}) {
  const current = cloneBinding(input.binding || {}, input.slot);
  const result = resolveDefaultBinding({ ...input, datasources: [input.datasource], sourceId: input.datasource?.id });
  if (!result.binding) return { ...result, binding: current };
  for (const [semantic, column] of Object.entries(result.binding.fields || {})) {
    if (!current.fields[semantic]) current.fields[semantic] = column;
    if (!current.units[semantic] && result.binding.units?.[semantic]) current.units[semantic] = result.binding.units[semantic];
  }
  if (!current.dsId && input.datasource?.id) current.dsId = input.datasource.id;
  return { ...result, binding: current, status: AUTO_BIND_STATUS.APPLIED };
}

export function unitLabel(unit) {
  return UNIT_LABELS[unit] || unit || '单位待确认';
}

export function summarizeBinding(label, binding, datasources = []) {
  if (!hasAnyBinding(binding) || !binding?.dsId) return `${label}：未配置`;
  const source = datasources.find(item => String(item.id) === String(binding.dsId));
  const sourceLabel = source?.dsName || source?.ds_name || `数据来源 #${binding.dsId}`;
  const pairs = Object.entries(binding.fields || {}).map(([semantic, column]) =>
    DIMENSION_SEMANTICS.has(semantic)
      ? String(column)
      : `${column}${binding.units?.[semantic] ? `（${unitLabel(binding.units[semantic])}）` : '（单位待确认）'}`
  );
  return `${label}：${sourceLabel} → ${pairs.join('、') || '字段待配置'}`;
}

export function bindingPreview({ template = BRANCH_TEMPLATE, screenScope, datasources, bindings, slots } = {}) {
  const order = Array.isArray(slots) ? slots : (template === RETAIL_TEMPLATE ? RETAIL_SLOT_ORDER : SLOT_ORDER);
  const applied = [];
  const gaps = [];
  for (const slot of order) {
    const binding = bindings?.[slot];
    const result = resolveDefaultBinding({ template, screenScope, datasources, slot, existingBinding: binding });
    if (hasAnyBinding(binding)) {
      applied.push({ slot, summary: summarizeBinding(slotSpec(template, slot)?.label || slot, binding, datasources) });
      if (result.gap && result.status !== AUTO_BIND_STATUS.PRESERVED) {
        gaps.push({ slot, status: result.status, message: result.gap });
      }
    } else {
      gaps.push({ slot, status: result.status, message: result.gap || '未配置' });
    }
  }
  return { applied, gaps };
}

export const autoBindSlot = resolveDefaultBinding;
export const autoBindEmptySlots = applyDefaultBindings;
