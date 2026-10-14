const M98_CONFIG_FIELDS = Object.freeze(['schemaVersion', 'scopeMode', 'profile', 'mode']);
const FREE_REPORT_CONFIG_FIELDS = Object.freeze([
  'schemaVersion', 'scopeMode', 'dataClassification', 'profile', 'batchId'
]);

export const M98_PROFILE_DEFINITIONS = Object.freeze({
  CORP_REVENUE: Object.freeze({
    profile: 'CORP_REVENUE', requiredBizLine: 'CORP', slot: 'corpRevenue',
    field: 'amount', label: '统计值', semantic: 'corpRevenue', unit: null,
    metricCode: 'M98_CORP_REVENUE'
  }),
  RETAIL_REVENUE: Object.freeze({
    profile: 'RETAIL_REVENUE', requiredBizLine: 'RETAIL', slot: 'retailRevenue',
    field: 'amount', label: '统计值', semantic: 'retailRevenue', unit: null,
    metricCode: 'M98_RETAIL_REVENUE'
  }),
  RETAIL_LOAN: Object.freeze({
    profile: 'RETAIL_LOAN', requiredBizLine: 'RETAIL', slot: 'retailLoan',
    field: 'amount', label: '统计值', semantic: 'retailLoan', unit: null,
    metricCode: 'M98_RETAIL_LOAN'
  }),
  CORP_NPL_RATE: Object.freeze({
    profile: 'CORP_NPL_RATE', requiredBizLine: 'CORP', slot: 'corpNplRate',
    field: 'ratio', label: '统计值', semantic: 'corpNplRate', unit: null,
    metricCode: 'M98_CORP_NPL_RATE'
  }),
  RETAIL_NPL_RATE: Object.freeze({
    profile: 'RETAIL_NPL_RATE', requiredBizLine: 'RETAIL', slot: 'retailNplRate',
    field: 'ratio', label: '统计值', semantic: 'retailNplRate', unit: null,
    metricCode: 'M98_RETAIL_NPL_RATE'
  })
});

export const M98_PROFILE_SLOTS = Object.freeze(Object.fromEntries(
  Object.values(M98_PROFILE_DEFINITIONS).map(definition => [definition.profile, definition.slot])
));

export const M98_PROFILES = new Set(Object.keys(M98_PROFILE_DEFINITIONS));

function object(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
}

function text(value) {
  return String(value ?? '').trim();
}

function upper(value) {
  return text(value).toUpperCase();
}

function configOf(source = {}) {
  const raw = source?.configJson ?? source?.config_json ?? source?.config ?? source?.configuration;
  if (object(raw) === raw) return raw;
  if (typeof raw !== 'string' || !text(raw)) return {};
  try {
    const parsed = JSON.parse(raw);
    return object(parsed);
  } catch {
    return {};
  }
}

function hasExactKeys(config, expected) {
  const keys = Object.keys(config).sort();
  return keys.length === expected.length && keys.every((key, index) => key === expected.slice().sort()[index]);
}

export function isStrictM98Source(source = {}) {
  const config = configOf(source);
  const profile = upper(config.profile);
  const definition = M98_PROFILE_DEFINITIONS[profile];
  return upper(source.sourceKind || source.source_kind || config.sourceKind) === 'M98_STAT'
    && upper(source.dsType || source.ds_type || config.dsType) === 'SINGLE'
    && hasExactKeys(config, M98_CONFIG_FIELDS)
    && Number(config.schemaVersion) === 2
    && upper(config.scopeMode) === 'NAMED_GROUP'
    && upper(config.mode) === 'SUMMARY'
    && Boolean(definition)
    && upper(source.bizLine || source.biz_line) === definition.requiredBizLine;
}

function isStrictFreeReportSource(source = {}) {
  const config = configOf(source);
  return upper(source.sourceKind || source.source_kind || config.sourceKind) === 'FREE_REPORT'
    && upper(source.dsType || source.ds_type || config.dsType) === 'SINGLE'
    && hasExactKeys(config, FREE_REPORT_CONFIG_FIELDS)
    && Number(config.schemaVersion) === 2
    && upper(config.scopeMode) === 'NAMED_GROUP'
    && upper(config.dataClassification) === 'TEST'
    && upper(config.profile) === 'BRANCH_OVERVIEW'
    && text(config.batchId).startsWith('TEST_BRANCH_');
}

/**
 * Returns only fixed, server-controlled output fields. Empty means the fixed
 * source configuration is invalid or the source kind has no fixed contract.
 */
export function getControlledSourceFieldOptions(source = {}) {
  const config = configOf(source);
  const kind = upper(source.sourceKind || source.source_kind || config.sourceKind);
  if (kind === 'M98_STAT' && isStrictM98Source(source)) {
    const definition = M98_PROFILE_DEFINITIONS[upper(config.profile)];
    return [
      {
        col: 'data_date', label: '数据日期', role: 'DIM', unit: null,
        semantic: 'dataDate', profile: definition.profile, fixed: true, builtin: true
      },
      {
        col: definition.field, label: definition.label, role: 'METRIC', unit: definition.unit,
        semantic: definition.semantic, metricCode: definition.metricCode,
        profile: definition.profile, fixedSlot: definition.slot, fixed: true, builtin: true
      }
    ];
  }
  if (kind === 'FREE_REPORT' && isStrictFreeReportSource(source)) {
    return [{
      kind: 'DATASET_NOTICE', role: 'NOTICE', disabled: true,
      label: '专用经营数据集',
      disabledReason: '该来源仅供专用模板消费，单位随记录返回'
    }];
  }
  return [];
}
