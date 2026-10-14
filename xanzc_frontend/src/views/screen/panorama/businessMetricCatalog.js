import { buildBusinessSourceCandidates } from '../presentation/sources/businessSourceCandidates';
import { getDatasourceFieldOptions, UNIT_VALUES } from './bindings';

const UNIT_ALIASES = Object.freeze({
  元: 'YUAN',
  RMB: 'YUAN',
  YUAN: 'YUAN',
  万元: 'TEN_THOUSAND',
  TEN_THOUSAND: 'TEN_THOUSAND',
  TEN_THOUSAND_YUAN: 'TEN_THOUSAND',
  亿元: 'HUNDRED_MILLION',
  HUNDRED_MILLION: 'HUNDRED_MILLION',
  HUNDRED_MILLION_YUAN: 'HUNDRED_MILLION',
  个: 'COUNT',
  个数: 'COUNT',
  COUNT: 'COUNT',
  万户: 'TEN_THOUSAND_COUNT',
  TEN_THOUSAND_COUNT: 'TEN_THOUSAND_COUNT',
  '%': 'PERCENT',
  百分数: 'PERCENT',
  PERCENT: 'PERCENT',
  比例: 'RATIO',
  RATIO: 'RATIO'
});

const UNIT_LABELS = Object.freeze({
  YUAN: '元',
  TEN_THOUSAND: '万元',
  HUNDRED_MILLION: '亿元',
  COUNT: '个数',
  TEN_THOUSAND_COUNT: '万户',
  PERCENT: '%',
  RATIO: '比例'
});

const SHAPE_LABELS = Object.freeze({
  SINGLE: '单值',
  TIMESERIES: '时序',
  TABLE: '明细'
});

const ACTIVE_STATUS_VALUES = new Set(['ACTIVE', '1', 'TRUE']);
const INACTIVE_STATUS_VALUES = new Set(['DISABLED', 'INACTIVE', '0', 'FALSE']);
const KNOWN_SOURCE_SHAPES = new Set(['SINGLE', 'TIMESERIES']);
const MAX_SUPPORTED_SCHEMA_VERSION = 2;
const EMPTY_ARRAY = Object.freeze([]);

function object(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
}

function text(value) {
  return String(value ?? '').trim();
}

function upper(value) {
  return text(value).toUpperCase();
}

function list(value) {
  if (Array.isArray(value)) return value.flatMap(item => list(item));
  const valueText = text(value);
  return valueText ? [valueText] : [];
}

function unique(values) {
  return [...new Set(values.map(text).filter(Boolean))];
}

function configValueOf(source = {}) {
  return source?.configJson ?? source?.config_json ?? source?.config
    ?? source?.configuration;
}

function parseConfigResult(source = {}) {
  const value = configValueOf(source);
  if (object(value) === value) return { config: value, valid: true, supplied: true };
  if (typeof value !== 'string' || !text(value)) {
    return { config: {}, valid: false, supplied: false, reason: '数据源配置缺失，需由技术人员维护' };
  }
  try {
    const parsed = JSON.parse(value);
    if (object(parsed) === parsed) return { config: parsed, valid: true, supplied: true };
    return { config: {}, valid: false, supplied: true, reason: '数据源配置不是对象，需由技术人员维护' };
  } catch {
    return { config: {}, valid: false, supplied: true, reason: '数据源配置无法解析，需由技术人员维护' };
  }
}

function parseConfig(source = {}) {
  return parseConfigResult(source).config;
}

function sourceIdOf(source = {}) {
  const raw = source?.id ?? source?.dsId ?? source?.datasourceId ?? source?.sourceId;
  const number = Number(raw);
  if (Number.isSafeInteger(number) && number > 0) return number;
  return text(raw);
}

function sourceIdentityOf(source = {}) {
  const raw = source?.id ?? source?.dsId ?? source?.datasourceId ?? source?.sourceId;
  const number = Number(raw);
  if (Number.isSafeInteger(number) && number > 0) return String(number);
  return text(raw);
}

function validSourceId(source = {}) {
  const raw = source?.id ?? source?.dsId ?? source?.datasourceId ?? source?.sourceId;
  if (typeof raw === 'string' && !/^\d+$/.test(raw.trim())) return false;
  const number = Number(raw);
  return Number.isSafeInteger(number) && number > 0;
}

function sourceStatusReason(source = {}) {
  const raw = source?.status;
  const status = upper(raw);
  if (!status) return '数据来源状态未知，需由技术人员维护';
  if (ACTIVE_STATUS_VALUES.has(status) || INACTIVE_STATUS_VALUES.has(status)) {
    return INACTIVE_STATUS_VALUES.has(status) ? '数据来源已停用' : '';
  }
  return '数据来源状态未知，需由技术人员维护';
}

function sourceValidation(source = {}) {
  const parsed = parseConfigResult(source);
  const config = parsed.config;
  const reasons = [];
  if (!validSourceId(source)) reasons.push('数据来源标识无效，需由技术人员维护');
  const statusReason = sourceStatusReason(source);
  if (statusReason) reasons.push(statusReason);
  if (!parsed.valid) reasons.push(parsed.reason || '数据源配置无法解析，需由技术人员维护');

  const rawShape = text(source?.dsType ?? source?.ds_type ?? config?.dsType ?? config?.ds_type);
  const shape = upper(rawShape);
  if (!KNOWN_SOURCE_SHAPES.has(shape)) {
    reasons.push(`数据源结果形状未知（${rawShape || '空'}），需由技术人员维护`);
  }

  const rawVersion = config?.schemaVersion ?? config?.schema_version;
  if (rawVersion !== undefined && rawVersion !== null && text(rawVersion)) {
    const version = Number(rawVersion);
    if (!Number.isSafeInteger(version) || version < 1 || version > MAX_SUPPORTED_SCHEMA_VERSION) {
      reasons.push(`schemaVersion ${text(rawVersion)} 超出受支持范围，需由技术人员维护`);
    }
  }

  return {
    config,
    configValid: parsed.valid,
    sourceId: sourceIdOf(source),
    shape: shape || '',
    reasons
  };
}

function fieldOf(item = {}) {
  // Keep the same output-column identity as getDatasourceFieldOptions: a
  // metric's metricName/metricCode is the declared field when no `col` exists.
  return text(item.col || item.metricName || item.metric_name || item.metricCode || item.metric_code);
}

function nestedMetadata(item = {}) {
  return object(item.metadata || item.meta);
}

function explicitSemantics(item = {}) {
  const metadata = nestedMetadata(item);
  return unique([
    ...list(item.semantic),
    ...list(item.semantics),
    ...list(item.semanticCode),
    ...list(item.semantic_code),
    ...list(metadata.semantic),
    ...list(metadata.semantics),
    ...list(metadata.semanticCode),
    ...list(metadata.semantic_code)
  ]);
}

function metricCodeOf(item = {}) {
  const metadata = nestedMetadata(item);
  return text(item.metricCode || item.metric_code || metadata.metricCode || metadata.metric_code);
}

function metricNameOf(item = {}) {
  const metadata = nestedMetadata(item);
  return text(item.alias || item.metricName || item.metric_name || item.name || metadata.alias
    || metadata.metricName || metadata.metric_name || fieldOf(item));
}

function explicitUnitOf(item = {}) {
  const metadata = nestedMetadata(item);
  return text(item.unit || item.rawUnit || item.raw_unit || item.sourceUnit || item.source_unit
    || item.inputUnit || item.input_unit || metadata.unit || metadata.rawUnit || metadata.raw_unit
    || metadata.sourceUnit || metadata.source_unit || metadata.inputUnit || metadata.input_unit);
}

function normalizeUnit(value) {
  const raw = text(value);
  if (!raw) return null;
  const normalized = UNIT_ALIASES[raw] || UNIT_ALIASES[upper(raw).replaceAll(' ', '')];
  return UNIT_VALUES.includes(normalized) ? normalized : null;
}

function unitLabel(unit) {
  return UNIT_LABELS[unit] || unit || '单位待维护';
}

function shapeLabel(shape) {
  return SHAPE_LABELS[upper(shape)] || '待确认';
}

function normalizeBusinessDisabledReason(reason) {
  const genericSourceReason = '当前来源能力未认证，需由技术人员维护';
  const normalized = String(reason ?? '').split('；').map(item => item.trim()).filter(Boolean).map(item => {
    if (/结果形状不匹配.*SINGLE/i.test(item)) return '当前组件需要单值结果';
    if (/结果形状不匹配.*TIMESERIES/i.test(item)) return '当前组件需要时序结果';
    if (/结果形状不匹配.*TABLE/i.test(item)) return '当前组件需要明细结果';
    if (/命名机构组宽表必须使用|当前机构范围(?:只允许|需要|不能使用)/i.test(item)) {
      return '当前机构范围未认证';
    }
    if (/命名机构组(?:仅支持|不支持)/i.test(item)) return '当前机构范围未认证';
    if (/业务条线不匹配/i.test(item)) return '业务条线不匹配，需选择当前屏允许的来源';
    if (/自定义SQL|\bSQL\b|\bSELECT\b|\bFROM\b/i.test(item)) return genericSourceReason;
    if (/数据源结果形状未知/i.test(item)) return '数据源结果形状未认证，需由技术人员维护';
    if (/schemaVersion/i.test(item)) return '数据源配置版本未认证，需由技术人员维护';
    if (/ORG_INDEX_RESULT|EMP_INDEX_RESULT|CUST_INDEX_RESULT|org_code|emp_id|cust_no|val_\d+/i.test(item)) {
      return genericSourceReason;
    }
    return item;
  });
  return [...new Set(normalized)].join('；');
}

function fieldMetadataByColumn(source) {
  const config = parseConfig(source);
  const records = [
    ...(Array.isArray(config.fieldMeta) ? config.fieldMeta : []),
    ...(Array.isArray(config.fields) ? config.fields : []),
    ...(Array.isArray(config.columns) ? config.columns : []),
    ...(Array.isArray(config.metrics) ? config.metrics : [])
  ];
  const metadata = new Map();
  records.forEach(item => {
    const record = object(item);
    const field = fieldOf(record);
    if (!field) return;
    const current = metadata.get(field) || {
      record: {},
      unitDeclarations: [],
      amountScales: []
    };
    current.record = { ...current.record, ...record };
    const declaredUnit = explicitUnitOf(record);
    if (declaredUnit) current.unitDeclarations.push(declaredUnit);
    const nested = nestedMetadata(record);
    const amountScale = record.amountScale || record.amount_scale
      || nested.amountScale || nested.amount_scale;
    if (text(amountScale)) current.amountScales.push(text(amountScale));
    metadata.set(field, current);
  });
  for (const current of metadata.values()) {
    const normalizedUnits = current.unitDeclarations.map(normalizeUnit);
    const distinctUnits = new Set(normalizedUnits.filter(Boolean));
    const conflicts = [];
    if (current.unitDeclarations.some((_, index) => !normalizedUnits[index])) {
      conflicts.push('字段存在未认证的原始单位声明');
    }
    if (distinctUnits.size > 1) conflicts.push('同一字段的原始单位声明冲突');
    if (current.amountScales.length && current.unitDeclarations.length) {
      conflicts.push('amountScale 与显式原始单位同时存在');
    }
    current.conflicts = conflicts;
  }
  return metadata;
}

function metricRecordForField(candidate, field) {
  const metrics = Array.isArray(candidate?.metrics) ? candidate.metrics : EMPTY_ARRAY;
  return metrics.find(item => fieldOf(item) === field) || null;
}

function sourceDescriptionOf(sourceCandidate, source) {
  return text(sourceCandidate?.name || source?.dsName || source?.ds_name)
    || `数据来源 #${sourceIdOf(sourceCandidate || source)}`;
}

function expectedSemanticList(context = {}) {
  return unique([
    ...list(context.semantic),
    ...list(context.semantics)
  ]);
}

function normalizeCatalogContext(context = {}) {
  const screen = object(context.screen);
  return {
    ...context,
    screenBizLine: context.screenBizLine ?? context.screen_biz_line
      ?? screen.bizLine ?? screen.biz_line,
    scopeMode: context.scopeMode ?? context.orgScopeMode ?? context.org_scope_mode
      ?? screen.orgScopeMode ?? screen.org_scope_mode ?? screen.scopeMode ?? screen.scope_mode,
    expectedShape: context.expectedShape ?? context.shape ?? screen.expectedShape ?? screen.shape
  };
}

function sourceShapeOf(sourceCandidate, source) {
  const config = parseConfig(source);
  const shape = upper(source?.dsType || source?.ds_type || config.dsType || config.ds_type
    || sourceCandidate?.shape);
  return shape || 'UNKNOWN';
}

function sourceCandidatesOf(sources, context) {
  if (Array.isArray(context?.sourceCandidates)) return context.sourceCandidates;
  if (Array.isArray(context?.candidates)) return context.candidates;
  return buildBusinessSourceCandidates(Array.isArray(sources) ? sources : [], context || {});
}

function appendReason(reasons, reason) {
  const value = text(reason);
  if (value && !reasons.includes(value)) reasons.push(value);
}

function savedBindingEvidence(savedBindings, sourceId, field) {
  const references = [];
  for (const binding of Array.isArray(savedBindings) ? savedBindings : EMPTY_ARRAY) {
    if (sourceIdentityOf(binding) !== String(sourceId)) continue;
    const fields = object(binding?.fields);
    const units = object(binding?.units);
    for (const [semantic, column] of Object.entries(fields)) {
      if (text(column) !== field) continue;
      const rawUnit = text(units[semantic]);
      references.push({ rawUnit, unit: normalizeUnit(rawUnit) });
    }
  }
  if (!references.length) return { status: 'NONE', references };
  const units = new Set(references.map(item => item.unit).filter(Boolean));
  if (references.some(item => !item.rawUnit || !item.unit) || units.size !== 1) {
    return {
      status: 'CONFLICT',
      references,
      reason: '已保存绑定对同一字段的单位证据缺失或冲突，需由技术人员维护'
    };
  }
  return { status: 'RESOLVED', references, unit: [...units][0] };
}

function fieldOptionsFor(sourceCandidate, source, validation) {
  const fromSource = validation.configValid ? getDatasourceFieldOptions(source) : EMPTY_ARRAY;
  const fromCandidate = Array.isArray(sourceCandidate?.fields) ? sourceCandidate.fields : EMPTY_ARRAY;
  // Once the original config is parseable, its explicit field list is the
  // authority. Candidate-only fields are displayable only for an invalid
  // config, where every resulting entry is already fail-closed.
  if (validation.configValid) return fromSource;
  const options = [];
  const seen = new Set();
  for (const item of [...fromSource, ...fromCandidate]) {
    const field = text(item?.col);
    const kind = text(item?.kind);
    if ((!field && kind !== 'DATASET_NOTICE') || seen.has(field || kind)) continue;
    seen.add(field || kind);
    options.push(item);
  }
  return options;
}

function createEntry(sourceCandidate, source, fieldOption, metadataDetails, context, validation, sourceIdentityIssue = '') {
  const optionKind = text(fieldOption?.kind);
  if (optionKind === 'DATASET_NOTICE') {
    const noticeReasons = [];
    const sourceCandidateReason = text(sourceCandidate?.disabledReason);
    if (sourceCandidate?.disabled) appendReason(noticeReasons, sourceCandidateReason || '当前来源不满足屏级范围或结果形状要求');
    validation.reasons.forEach(reason => appendReason(noticeReasons, reason));
    appendReason(noticeReasons, sourceIdentityIssue);
    appendReason(noticeReasons, fieldOption?.disabledReason || '该来源仅供专用模板消费，单位随记录返回');
    const sourceId = validation.sourceId;
    const shape = validation.shape || sourceShapeOf(sourceCandidate, source);
    return {
      key: `${sourceId}:dataset`,
      sourceId,
      datasourceId: sourceId,
      field: null,
      metricName: text(fieldOption.label) || '专用经营数据集',
      sourceDescription: sourceDescriptionOf(sourceCandidate, source),
      rawUnit: null,
      rawUnitLabel: '单位随记录',
      unit: null,
      unitEvidence: null,
      unitEvidenceMessage: '',
      shape,
      shapeLabel: shapeLabel(shape),
      semantic: null,
      semantics: [],
      metricCode: null,
      profile: null,
      role: 'NOTICE',
      kind: 'DATASET_NOTICE',
      disabled: true,
      disabledReason: normalizeBusinessDisabledReason(noticeReasons.join('；'))
    };
  }
  const field = text(fieldOption?.col || fieldOf(metadataDetails?.record || metadataDetails));
  if (!field || upper(fieldOption?.role) !== 'METRIC') return null;

  const metadata = object(metadataDetails?.record || metadataDetails);
  const metricRecord = metricRecordForField(sourceCandidate, field);
  const merged = {
    ...object(metricRecord),
    ...object(metadata),
    ...object(fieldOption)
  };
  const semantics = explicitSemantics(merged);
  const metricCode = metricCodeOf(merged) || text(fieldOption?.metricCode);
  const reasons = [];
  const sourceCandidateReason = text(sourceCandidate?.disabledReason);
  if (sourceCandidate?.disabled) appendReason(reasons, sourceCandidateReason || '当前来源不满足屏级范围或结果形状要求');
  validation.reasons.forEach(reason => appendReason(reasons, reason));
  appendReason(reasons, sourceIdentityIssue);
  if (!semantics.length && !metricCode) {
    appendReason(reasons, '业务语义未声明，需由技术人员维护');
  }

  const declaredUnits = Array.isArray(metadataDetails?.unitDeclarations)
    ? metadataDetails.unitDeclarations : [];
  const fallbackDeclaredUnit = explicitUnitOf(merged);
  const declaredUnit = text(declaredUnits[0] || fallbackDeclaredUnit);
  const metadataConflicts = Array.isArray(metadataDetails?.conflicts)
    ? [...metadataDetails.conflicts] : [];
  const nested = nestedMetadata(merged);
  const hasAmountScale = Boolean(text(merged.amountScale || merged.amount_scale
    || nested.amountScale || nested.amount_scale
    || (Array.isArray(metadataDetails?.amountScales) ? metadataDetails.amountScales[0] : '')));
  if (hasAmountScale && declaredUnit) {
    appendReason(metadataConflicts, 'amountScale 与显式原始单位同时存在');
  }

  let unit = null;
  let unitEvidence = null;
  let unitEvidenceMessage = '';
  if (metadataConflicts.length) {
    metadataConflicts.forEach(conflict => appendReason(reasons, `字段单位冲突：${conflict}`));
  } else if (declaredUnit) {
    unit = normalizeUnit(declaredUnit);
    unitEvidence = 'METADATA';
    if (!unit) {
      appendReason(reasons, `原始单位“${declaredUnit}”未在受控单位中认证，需由技术人员维护`);
    }
  } else {
    const saved = savedBindingEvidence(context.savedBindings, validation.sourceId, field);
    if (saved.status === 'RESOLVED') {
      unit = saved.unit;
      unitEvidence = 'SAVED_BINDING';
      unitEvidenceMessage = '沿用已保存绑定单位，请核对口径';
    } else if (saved.status === 'CONFLICT') {
      appendReason(reasons, saved.reason);
    } else {
      appendReason(reasons, '原始单位未认证，需由技术人员维护');
    }
  }

  const expectedSemantics = expectedSemanticList(context);
  const fixedSlot = text(merged.fixedSlot);
  const fixedSlotMatches = fixedSlot && text(context.slot) === fixedSlot;
  if (expectedSemantics.length && !expectedSemantics.some(expected => semantics.includes(expected))
      && !fixedSlotMatches) {
    appendReason(reasons, `未声明所需业务语义“${expectedSemantics.join('、')}”，需由技术人员维护`);
  }
  if (fixedSlot) {
    if (text(context.slot)) {
      if (fixedSlot !== text(context.slot)) appendReason(reasons, '固定指标不适用于当前组件');
    } else if (!expectedSemantics.length) {
      appendReason(reasons, '固定指标需要明确组件语义，不能跨profile自动匹配');
    }
  }

  const metricName = text(metadata.alias || metadata.metricName || metadata.metric_name
    || fieldOption.label || metricNameOf(metricRecord || merged) || field);
  const sourceId = validation.sourceId;
  const shape = validation.shape || sourceShapeOf(sourceCandidate, source);
  const entry = {
    key: `${sourceId}:${field}`,
    sourceId,
    datasourceId: sourceId,
    field,
    metricName,
    sourceDescription: sourceDescriptionOf(sourceCandidate, source),
    rawUnit: unit,
    rawUnitLabel: unitLabel(unit),
    unit,
    unitEvidence,
    unitEvidenceMessage,
    shape,
    shapeLabel: shapeLabel(shape),
    semantic: semantics[0] || null,
    semantics,
    metricCode: metricCode || null,
    profile: text(merged.profile) || null,
    role: 'METRIC',
    disabled: reasons.length > 0,
    disabledReason: normalizeBusinessDisabledReason(reasons.join('；'))
  };
  return entry;
}

/**
 * Build one business metric entry per explicitly declared numeric field.
 * Source compatibility is delegated to the shared source candidate builder;
 * this model only exposes safe display/selection fields to business editors.
 */
export function buildBusinessMetricCatalog(sources = [], context = {}) {
  const normalizedContext = normalizeCatalogContext(context);
  const sourceList = Array.isArray(sources) ? sources : [];
  const rawById = new Map(sourceList.map(source => [sourceIdentityOf(source), source]));
  const entries = [];
  const seen = new Set();
  for (const sourceCandidate of sourceCandidatesOf(sourceList, normalizedContext)) {
    const candidateIdentity = sourceIdentityOf(sourceCandidate);
    const rawSource = rawById.get(candidateIdentity);
    const candidateLooksLikeSource = sourceCandidate && (
      Object.prototype.hasOwnProperty.call(sourceCandidate, 'status')
      || Object.prototype.hasOwnProperty.call(sourceCandidate, 'configJson')
      || Object.prototype.hasOwnProperty.call(sourceCandidate, 'config_json')
      || Object.prototype.hasOwnProperty.call(sourceCandidate, 'sourceKind')
      || Object.prototype.hasOwnProperty.call(sourceCandidate, 'dsType')
    );
    const source = rawSource || sourceCandidate?.rawSource
      || (candidateLooksLikeSource ? sourceCandidate : {});
    const validation = sourceValidation(source);
    const sourceIdentityIssue = sourceList.length && !rawSource
      ? '候选来源未在当前数据源集合中，需重新读取受控来源' : '';
    const options = fieldOptionsFor(sourceCandidate, source, validation);
    const metadata = fieldMetadataByColumn(source);
    for (const option of options) {
      const entry = createEntry(
        sourceCandidate,
        source,
        option,
        metadata.get(text(option?.col)) || {},
        normalizedContext,
        validation,
        sourceIdentityIssue
      );
      if (!entry || seen.has(entry.key)) continue;
      seen.add(entry.key);
      entries.push(entry);
    }
  }
  return entries;
}

/**
 * Filter catalog entries without changing their identity or disabled state.
 */
export function filterBusinessMetricCatalog(entries = [], query = '') {
  const keyword = typeof query === 'object' ? text(query.keyword) : text(query);
  const normalized = keyword.toLocaleLowerCase();
  if (!normalized) return Array.isArray(entries) ? entries : [];
  return (Array.isArray(entries) ? entries : []).filter(entry => [
    entry?.metricName,
    entry?.sourceDescription,
    entry?.field,
    entry?.semantic,
    ...(Array.isArray(entry?.semantics) ? entry.semantics : []),
    entry?.metricCode,
    entry?.shape,
    entry?.disabledReason
  ].some(value => text(value).toLocaleLowerCase().includes(normalized)));
}

/**
 * Convert a catalog item into the small selection contract used by a main-value editor.
 */
export function selectionFromBusinessMetric(entry = {}) {
  return {
    datasourceId: entry.sourceId ?? entry.datasourceId ?? null,
    field: text(entry.field),
    unit: entry.unit || null,
    unitEvidence: entry.unitEvidence || null,
    unitEvidenceMessage: text(entry.unitEvidenceMessage),
    profile: entry.profile || null,
    semantic: entry.semantic || null,
    semantics: Array.isArray(entry.semantics) ? [...entry.semantics] : [],
    metricCode: entry.metricCode || null,
    metricName: text(entry.metricName)
  };
}

export { UNIT_LABELS as BUSINESS_METRIC_UNIT_LABELS, unitLabel as businessMetricUnitLabel };
