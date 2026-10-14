// 大屏数据源 config_json 组装/解析纯函数（spec 2026-07-17 §3，供 Datasources.vue 使用，vitest 全覆盖）
//
// 表单模型（Datasources.vue 弹窗绑定的响应式对象）与库表 config_json 的双向转换集中在本文件，
// 保证"保存时组装"与"编辑时回填"口径一致；校验规则与后端 ScreenDatasourceServiceImpl 的
// 43009（SCREEN_DS_CONFIG_INVALID）分支一一对齐，尽量把报错拦在提交前并给出友好中文文案。
//
// 后端契约要点（report-analytics-center 已交付）：
//   - KPI_DETAIL：SNAPSHOT 强制 ds_type=SINGLE、TREND 强制 TIMESERIES（显式传不一致会 43009）；
//     TREND 必须携带 metrics 快照且每项 metricCode+metricName 非空；valueCol ∈ score|completeRate（可选，默认 score）
//   - fieldMeta（全 source_kind 通用）：col 非空且不重复、role ∈ DIM|METRIC 必填；METRIC 可选 amountScale 预设
//   - WIDE_TABLE aggregation：groupBy ∈ NONE|SUBJECT|DATE（DATE→TIMESERIES 否则 SINGLE，后端强制）、
//     agg ∈ SUM|AVG|MAX|MIN|COUNT；filters 的 op 白名单，IN 的 value 为逗号分隔字符串（后端拆成多 ? 绑定）
//   - scopeMode ∈ SUBJECT|GLOBAL|NAMED_GROUP（缺省 SUBJECT；NAMED_GROUP 仅机构宽表且试跑须显式测试组）
import { BIZ_LINES } from './screenScope';

// ===== 枚举常量（与后端白名单一致，模板下拉复用） =====
export const FIELD_ROLES = ['DIM', 'METRIC'];
export const AGG_GROUP_BYS = ['NONE', 'SUBJECT', 'DATE'];
export const AGG_FUNCS = ['SUM', 'AVG', 'MAX', 'MIN', 'COUNT'];
export const FILTER_OPS = ['EQ', 'NE', 'IN', 'GT', 'GE', 'LT', 'LE'];
export const KPI_VALUE_COLS = ['score', 'completeRate'];
export const SCOPE_MODES = ['SUBJECT', 'GLOBAL', 'NAMED_GROUP'];
export const SUPPORTED_SOURCE_KINDS = [
  'WIDE_TABLE', 'KPI_RESULT', 'KPI_DETAIL', 'M98_STAT', 'FREE_REPORT', 'CUSTOM_SQL'
];
export const CONFIG_SCHEMA_VERSIONS = [1, 2];
export const M98_PROFILES = [
  'CORP_REVENUE', 'RETAIL_REVENUE', 'RETAIL_LOAN', 'CORP_NPL_RATE', 'RETAIL_NPL_RATE'
];
export const M98_PROFILE_BIZ_LINES = Object.freeze({
  CORP_REVENUE: 'CORP',
  CORP_NPL_RATE: 'CORP',
  RETAIL_REVENUE: 'RETAIL',
  RETAIL_LOAN: 'RETAIL',
  RETAIL_NPL_RATE: 'RETAIL'
});
export const FREE_REPORT_DATA_CLASSIFICATION = 'TEST';
export const FREE_REPORT_PROFILE = 'BRANCH_OVERVIEW';
export const FREE_REPORT_BATCH_PREFIX = 'TEST_BRANCH_';
/** 度量字段金额量级预设：约定展示单位、固定 2 位小数及按元原始值的转换级别。 */
export const AMOUNT_SCALE_OPTIONS = [
  { value: 'YUAN', label: '元', unit: '元', decimals: 2 },
  { value: 'TEN_THOUSAND_YUAN', label: '万元', unit: '万元', decimals: 2 },
  { value: 'HUNDRED_MILLION_YUAN', label: '亿元', unit: '亿元', decimals: 2 }
];
const AMOUNT_SCALE_VALUES = AMOUNT_SCALE_OPTIONS.map(option => option.value);
export const AMOUNT_SCALE_FACTORS = Object.freeze({
  YUAN: 1,
  TEN_THOUSAND_YUAN: 1 / 10000,
  HUNDRED_MILLION_YUAN: 1 / 100000000
});
/** 预设周期模板（与设计器周期下拉一致） */
export const TIME_PARAM_PRESETS = ['LATEST', 'LAST_10D', 'LAST_1M', 'LAST_6M_EOM'];
const FIELD_META_CONTROLLED_KEYS = new Set(['col', 'alias', 'role', 'amountScale', 'unit', 'decimals']);

/** 新建弹窗的默认表单模型（编辑时以 parseConfigJson 的补丁覆盖） */
export function defaultDsModel() {
  return {
    sourceKind: 'WIDE_TABLE',
    // 新建必须由管理员显式选择；COMMON 是有效值而不是隐式默认。
    bizLine: '',
    // 仅 CUSTOM_SQL 由用户选择；其余类型由 deriveDsType 按配置推导（只读展示）
    dsType: 'SINGLE',
    wide: { table: 'EMP_INDEX_RESULT', metricCodes: [], slotCols: [], timeParams: [...TIME_PARAM_PRESETS] },
    kpi: { cycleType: 'MONTHLY' },
    kpiDetail: {
      schemeCode: '', subjectType: 'EMP', mode: 'SNAPSHOT',
      metrics: [], valueCol: 'score', timeParams: [...TIME_PARAM_PRESETS]
    },
    sql: { text: '', dateCol: '' },
    m98: { profile: 'CORP_REVENUE' },
    freeReport: {
      dataClassification: FREE_REPORT_DATA_CLASSIFICATION,
      profile: FREE_REPORT_PROFILE,
      batchId: ''
    },
    // ===== 全类型通用段 =====
    fieldMeta: [],          // [{col, alias, role, amountScale, unit, decimals}]
    aggEnabled: false,      // 仅 WIDE_TABLE：聚合开关（关闭时 aggregation 不落盘）
    aggregation: { groupBy: 'NONE', agg: 'SUM', filters: [] },
    scopeMode: 'SUBJECT',
    preservedTimeParamJson: null,
    preservedKpiDetailValueCol: false,
    preservedKpiDetailTimeParams: false,
    // 编辑回填时保留后端已知但当前表单未展示的配置扩展；保存时原样透传。
    configExtensions: {}
  };
}

/**
 * 推导 ds_type（与后端保存时的强制规则一致，前端用于只读展示与提交值）：
 *   WIDE_TABLE：未开聚合 → TIMESERIES（现状）；开聚合 groupBy=DATE → TIMESERIES，NONE/SUBJECT → SINGLE
 *   KPI_RESULT：TIMESERIES（后端强制）
 *   KPI_DETAIL：SNAPSHOT → SINGLE、TREND → TIMESERIES
 *   CUSTOM_SQL：跟随用户选择
 */
export function deriveDsType(model) {
  switch (model.sourceKind) {
    case 'WIDE_TABLE':
      if (model.aggEnabled) return model.aggregation.groupBy === 'DATE' ? 'TIMESERIES' : 'SINGLE';
      return 'TIMESERIES';
    case 'KPI_RESULT':
      return 'TIMESERIES';
    case 'KPI_DETAIL':
      return model.kpiDetail.mode === 'SNAPSHOT' ? 'SINGLE' : 'TIMESERIES';
    case 'M98_STAT':
    case 'FREE_REPORT':
      return 'SINGLE';
    case 'CUSTOM_SQL':
      return model.dsType;
    default:
      return null;
  }
}

/** 整行为空的 fieldMeta 行（用户点了"加一行"但没填）→ 组装/校验时统一跳过 */
function isEmptyFieldMetaRow(r) {
  return !String(r.col || '').trim() && !String(r.alias || '').trim()
    && !String(r.amountScale || '').trim() && !String(r.unit || '').trim()
    && (r.decimals === null || r.decimals === undefined || r.decimals === '');
}

/** fieldMeta 表单行 → 落盘形态：trim、空可选项不落键、decimals 数字化；无有效行返回 null */
function normalizeFieldMeta(rows) {
  const out = [];
  for (const r of rows || []) {
    if (isEmptyFieldMetaRow(r)) continue;
    const item = Object.fromEntries(Object.entries(r)
      .filter(([key]) => !FIELD_META_CONTROLLED_KEYS.has(key))
      .map(([key, value]) => [key, cloneConfigValue(value)]));
    item.col = String(r.col || '').trim();
    if (String(r.alias || '').trim()) item.alias = String(r.alias).trim();
    item.role = r.role;  // role 后端必填校验，UI 上默认 METRIC 不会为空
    // amountScale 是度量字段的互斥展示预设；非度量行即使残留脏值也不得落盘。
    if (r.role === 'METRIC' && AMOUNT_SCALE_VALUES.includes(r.amountScale)) {
      item.amountScale = r.amountScale;
    } else {
      if (String(r.unit || '').trim()) item.unit = String(r.unit).trim();
      if (r.decimals !== null && r.decimals !== undefined && r.decimals !== '') {
        item.decimals = Number(r.decimals);
      }
    }
    out.push(item);
  }
  return out.length ? out : null;
}

/** filters 表单行 → 落盘形态：丢弃整行空；value 保持字符串（IN 为逗号分隔，后端拆分） */
function normalizeFilters(filters) {
  const out = (filters || [])
    .filter(f => String(f.col || '').trim() || String(f.value || '').trim())
    .map(f => ({ col: String(f.col || '').trim(), op: f.op, value: String(f.value ?? '').trim() }));
  return out.length ? out : null;
}

function cloneConfigValue(value) {
  if (Array.isArray(value)) return value.map(cloneConfigValue);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, cloneConfigValue(item)]));
  }
  return value;
}

function isPlainConfigObject(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function configError(message) {
  return new Error(message);
}

function assertSupportedSourceKind(sourceKind) {
  if (!SUPPORTED_SOURCE_KINDS.includes(sourceKind)) {
    throw configError(`数据源来源不受支持：${sourceKind || '空值'}`);
  }
}

function assertConfigVersion(config) {
  if (!isPlainConfigObject(config)) throw configError('数据源配置必须是 JSON 对象');
  // 未带版本的存量配置按后端 ScreenConfigSchema 的 legacy v1 处理；显式未知版本必须拒绝。
  if (config.schemaVersion !== undefined
    && (!Number.isInteger(config.schemaVersion) || !CONFIG_SCHEMA_VERSIONS.includes(config.schemaVersion))) {
    throw configError(`数据源配置版本不受支持：${String(config.schemaVersion)}`);
  }
}

function parseConfigInput(config) {
  if (typeof config === 'string') {
    if (!config.trim()) throw configError('数据源配置 JSON 为空，已拒绝保存');
    try {
      const parsed = JSON.parse(config);
      assertConfigVersion(parsed);
      return parsed;
    } catch (error) {
      if (error?.message?.includes('数据源配置')) throw error;
      throw configError(`数据源配置 JSON 解析失败：${error?.message || '格式非法'}`);
    }
  }
  assertConfigVersion(config);
  return config;
}

function parseTimeParams(timeParamJson) {
  if (timeParamJson === null || timeParamJson === undefined || timeParamJson === '') return null;
  try {
    const parsed = typeof timeParamJson === 'string' ? JSON.parse(timeParamJson) : timeParamJson;
    if (!Array.isArray(parsed)) throw configError('周期配置必须是数组');
    return parsed;
  } catch (error) {
    if (error?.message === '周期配置必须是数组') throw error;
    throw configError(`周期配置 JSON 解析失败：${error?.message || '格式非法'}`);
  }
}

function exactConfigFields(config, fields) {
  return Object.keys(config).every(key => fields.includes(key))
    && fields.every(key => Object.prototype.hasOwnProperty.call(config, key));
}

function assertStrictM98Config(config) {
  const fields = ['schemaVersion', 'scopeMode', 'profile', 'mode'];
  if (!exactConfigFields(config, fields)
    || config.schemaVersion !== 2
    || config.scopeMode !== 'NAMED_GROUP'
    || config.mode !== 'SUMMARY'
    || !M98_PROFILES.includes(config.profile)) {
    throw configError('M98_STAT 配置不符合固定 profile/SUMMARY 白名单');
  }
}

function assertStrictFreeReportConfig(config) {
  const fields = ['schemaVersion', 'scopeMode', 'dataClassification', 'profile', 'batchId'];
  if (!exactConfigFields(config, fields)
    || config.schemaVersion !== 2
    || config.scopeMode !== 'NAMED_GROUP'
    || config.dataClassification !== FREE_REPORT_DATA_CLASSIFICATION
    || config.profile !== FREE_REPORT_PROFILE
    || typeof config.batchId !== 'string'
    || config.batchId.trim() !== config.batchId
    || !config.batchId.startsWith(FREE_REPORT_BATCH_PREFIX)
    || config.batchId.length <= FREE_REPORT_BATCH_PREFIX.length) {
    throw configError('FREE_REPORT 仅允许 TEST/BRANCH_OVERVIEW/TEST_BRANCH_ 批次');
  }
}

function configExtensionFields(sourceKind) {
  const common = ['schemaVersion', 'scopeMode', 'fieldMeta', 'aggregation'];
  switch (sourceKind) {
    case 'WIDE_TABLE': return [...common, 'table', 'metrics'];
    case 'KPI_RESULT': return [...common, 'cycleType'];
    case 'KPI_DETAIL': return [...common, 'schemeCode', 'subjectType', 'mode', 'metrics', 'valueCol'];
    case 'CUSTOM_SQL': return [...common, 'sql', 'dateCol'];
    case 'M98_STAT': return ['schemaVersion', 'scopeMode', 'profile', 'mode'];
    case 'FREE_REPORT': return ['schemaVersion', 'scopeMode', 'dataClassification', 'profile', 'batchId'];
    default: return common;
  }
}

function extractConfigExtensions(sourceKind, config) {
  const known = new Set(configExtensionFields(sourceKind));
  const extensions = {};
  for (const [key, value] of Object.entries(config)) {
    if (!known.has(key)) extensions[key] = cloneConfigValue(value);
  }
  return extensions;
}

function buildConfigExtensions(model, sourceKind) {
  if (!isPlainConfigObject(model.configExtensions)) return {};
  const known = new Set(configExtensionFields(sourceKind));
  return Object.fromEntries(Object.entries(model.configExtensions)
    .filter(([key]) => !known.has(key))
    .map(([key, value]) => [key, cloneConfigValue(value)]));
}

/**
 * 表单模型 → config_json 对象（调用方 JSON.stringify 后提交）。
 * 统一携带 schemaVersion:2 与 scopeMode（显式优于隐式）；fieldMeta/aggregation 无内容时不落键，
 * 保持与旧数据 diff 最小。
 */
export function buildConfigJson(model) {
  assertSupportedSourceKind(model?.sourceKind);
  let cfg;
  switch (model.sourceKind) {
    case 'WIDE_TABLE':
      // 只传 metricCode，槽位/名称快照由后端重写（沿用现状）
      {
        const metricCodes = model.wide.metricCodes || [];
        const snapshots = Array.isArray(model.wide.metricSnapshots) ? model.wide.metricSnapshots : [];
        const keepSnapshots = snapshots.length === metricCodes.length
          && snapshots.every((metric, index) => metric?.metricCode === metricCodes[index]);
        cfg = {
          schemaVersion: 2,
          table: model.wide.table,
          metrics: keepSnapshots && snapshots.length
            ? cloneConfigValue(snapshots)
            : metricCodes.map(c => ({ metricCode: c }))
        };
        if (model.aggEnabled) {
          const agg = { groupBy: model.aggregation.groupBy, agg: model.aggregation.agg };
          const filters = normalizeFilters(model.aggregation.filters);
          if (filters) agg.filters = filters;
          cfg.aggregation = agg;
        }
        break;
      }
    case 'KPI_RESULT':
      cfg = { schemaVersion: 2, cycleType: model.kpi.cycleType };
      break;
    case 'KPI_DETAIL': {
      const d = model.kpiDetail;
      cfg = { schemaVersion: 2, schemeCode: d.schemeCode, subjectType: d.subjectType, mode: d.mode };
      if (d.mode === 'TREND') {
        // 保存时带 metricName 快照（后端校验 code+name 均非空，细项列名依赖它）
        cfg.metrics = d.metrics.map(m => ({ ...cloneConfigValue(m), metricCode: m.metricCode, metricName: m.metricName }));
        cfg.valueCol = d.valueCol || 'score';
      } else {
        if (Array.isArray(d.metrics) && d.metrics.length) {
          cfg.metrics = cloneConfigValue(d.metrics);
        }
        if (model.preservedKpiDetailValueCol && KPI_VALUE_COLS.includes(d.valueCol)) cfg.valueCol = d.valueCol;
      }
      break;
    }
    case 'M98_STAT':
      cfg = {
        schemaVersion: 2,
        scopeMode: 'NAMED_GROUP',
        profile: model.m98?.profile || '',
        mode: 'SUMMARY'
      };
      break;
    case 'FREE_REPORT':
      cfg = {
        schemaVersion: 2,
        scopeMode: 'NAMED_GROUP',
        dataClassification: model.freeReport?.dataClassification || FREE_REPORT_DATA_CLASSIFICATION,
        profile: model.freeReport?.profile || FREE_REPORT_PROFILE,
        batchId: String(model.freeReport?.batchId || '').trim()
      };
      break;
    case 'CUSTOM_SQL':
      cfg = { schemaVersion: 2, sql: model.sql.text, dateCol: model.sql.dateCol || null };
      break;
    default:
      throw configError(`数据源来源不受支持：${model.sourceKind}`);
  }
  if (model.sourceKind === 'M98_STAT' || model.sourceKind === 'FREE_REPORT') return cfg;
  const fieldMeta = normalizeFieldMeta(model.fieldMeta);
  if (fieldMeta) cfg.fieldMeta = fieldMeta;
  cfg.scopeMode = model.scopeMode || 'SUBJECT';
  return { ...buildConfigExtensions(model, model.sourceKind), ...cfg };
}

/** 预设周期 → time_param_json：WIDE_TABLE 与 KPI_DETAIL(TREND) 携带，其余 null */
export function buildTimeParamJson(model) {
  if (model.sourceKind === 'WIDE_TABLE') return JSON.stringify(model.wide.timeParams || []);
  if (model.sourceKind === 'KPI_DETAIL' && model.kpiDetail.mode === 'TREND') {
    return JSON.stringify(model.kpiDetail.timeParams || []);
  }
  if (model.sourceKind === 'KPI_DETAIL' && model.preservedKpiDetailTimeParams) {
    return JSON.stringify(model.kpiDetail.timeParams || []);
  }
  if (model.preservedTimeParamJson) return model.preservedTimeParamJson;
  return null;
}

/** fieldMeta 落盘行 → 表单行（补齐可选字段默认值，便于 v-model 绑定） */
function fieldMetaToRows(fieldMeta) {
  return (Array.isArray(fieldMeta) ? fieldMeta : []).map(f => {
    const row = {
      ...Object.fromEntries(Object.entries(f)
        .filter(([key]) => !FIELD_META_CONTROLLED_KEYS.has(key))
        .map(([key, value]) => [key, cloneConfigValue(value)])),
      col: f.col || '', alias: f.alias || '', role: f.role || 'METRIC',
      unit: f.unit || '', decimals: f.decimals ?? null
    };
    // 只为新配置回填有效预设；旧配置保持原表单形态，保证编辑兼容和最小 diff。
    if (row.role === 'METRIC' && AMOUNT_SCALE_VALUES.includes(f.amountScale)) row.amountScale = f.amountScale;
    return row;
  });
}

/** amountScale 是度量展示预设：试跑预览读取其单位/小数位并转换展示值，不改接口原始值。 */
function amountScaleDisplayOption(meta) {
  if (!meta || meta.role !== 'METRIC') return null;
  return AMOUNT_SCALE_OPTIONS.find(option => option.value === meta.amountScale) || null;
}

/** 将元计价原始值转换为金额量级展示值；空值/非数值按原值返回。 */
export function convertAmountScaleValue(value, amountScale) {
  const factor = AMOUNT_SCALE_FACTORS[amountScale];
  if (factor === undefined || value === null || value === undefined || value === '') return value;
  if (typeof value === 'number') return Number.isFinite(value) ? value * factor : value;
  if (typeof value === 'string' && value.trim() !== '') {
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric * factor : value;
  }
  return value;
}

/**
 * config_json（+ time_param_json）→ 表单模型补丁（编辑回填，读时兼容 v1 旧数据：
 * 缺 fieldMeta/aggregation/scopeMode 时补默认值）。返回的补丁按 Object.assign 语义覆盖 defaultDsModel。
 */
export function parseConfigJson(sourceKind, cfg, timeParamJson) {
  assertSupportedSourceKind(sourceKind);
  const c = parseConfigInput(cfg);
  if (sourceKind === 'M98_STAT') assertStrictM98Config(c);
  if (sourceKind === 'FREE_REPORT') assertStrictFreeReportConfig(c);
  const base = defaultDsModel();
  const scopeMode = c.scopeMode === undefined ? 'SUBJECT' : c.scopeMode;
  if (!SCOPE_MODES.includes(scopeMode)) throw configError(`数据范围模式不受支持：${String(c.scopeMode)}`);
  if (c.fieldMeta !== undefined && c.fieldMeta !== null && !Array.isArray(c.fieldMeta)) {
    throw configError('字段元数据必须是数组');
  }
  const timeParams = parseTimeParams(timeParamJson);
  const patch = {
    bizLine: 'COMMON',
    fieldMeta: fieldMetaToRows(c.fieldMeta),
    scopeMode,
    aggEnabled: false,
    preservedTimeParamJson: typeof timeParamJson === 'string'
      ? timeParamJson : (timeParamJson == null ? null : JSON.stringify(timeParamJson)),
    configExtensions: sourceKind === 'M98_STAT' || sourceKind === 'FREE_REPORT'
      ? {} : extractConfigExtensions(sourceKind, c)
  };

  if (sourceKind === 'WIDE_TABLE') {
    if (c.metrics !== undefined && !Array.isArray(c.metrics)) throw configError('宽表指标配置必须是数组');
    const metrics = Array.isArray(c.metrics) ? c.metrics : [];
    patch.wide = {
      table: c.table || base.wide.table,
      metricCodes: metrics.map(m => m.metricCode).filter(Boolean),
      metricSnapshots: cloneConfigValue(metrics),
      // 后端重写后的 config 携带 slot → 转成 val_N 供聚合过滤列下拉提示
      slotCols: metrics.filter(m => m.slot !== undefined && m.slot !== null).map(m => `val_${m.slot}`),
      timeParams: timeParams || [...TIME_PARAM_PRESETS]
    };
    const agg = c.aggregation;
    if (agg !== undefined && agg !== null && (!isPlainConfigObject(agg) || Array.isArray(agg))) {
      throw configError('宽表聚合配置必须是对象');
    }
    if (agg && typeof agg === 'object') {
      patch.aggEnabled = true;
      patch.aggregation = {
        groupBy: AGG_GROUP_BYS.includes(agg.groupBy) ? agg.groupBy : 'NONE',
        agg: AGG_FUNCS.includes(agg.agg) ? agg.agg : 'SUM',
        filters: (Array.isArray(agg.filters) ? agg.filters : [])
          .map(f => ({ col: f.col || '', op: FILTER_OPS.includes(f.op) ? f.op : 'EQ', value: f.value ?? '' }))
      };
    }
  } else if (sourceKind === 'KPI_RESULT') {
    patch.kpi = { cycleType: c.cycleType || 'MONTHLY' };
  } else if (sourceKind === 'KPI_DETAIL') {
    patch.kpiDetail = {
      schemeCode: c.schemeCode || '',
      subjectType: c.subjectType === 'ORG' ? 'ORG' : 'EMP',
      mode: c.mode === 'TREND' ? 'TREND' : 'SNAPSHOT',
      metrics: (Array.isArray(c.metrics) ? c.metrics : [])
        .map(m => ({ ...cloneConfigValue(m), metricCode: m.metricCode || '', metricName: m.metricName || '' })),
      valueCol: KPI_VALUE_COLS.includes(c.valueCol) ? c.valueCol : 'score',
      timeParams: timeParams || [...TIME_PARAM_PRESETS]
    };
    if (Object.prototype.hasOwnProperty.call(c, 'valueCol')) patch.preservedKpiDetailValueCol = true;
    if (timeParams !== null) patch.preservedKpiDetailTimeParams = true;
  } else if (sourceKind === 'M98_STAT') {
    patch.scopeMode = 'NAMED_GROUP';
    patch.m98 = { profile: c.profile };
  } else if (sourceKind === 'FREE_REPORT') {
    patch.scopeMode = 'NAMED_GROUP';
    patch.freeReport = {
      dataClassification: c.dataClassification,
      profile: c.profile,
      batchId: c.batchId
    };
  } else if (sourceKind === 'CUSTOM_SQL') {
    patch.sql = { text: c.sql || '', dateCol: c.dateCol || '' };
  }
  return patch;
}

/**
 * 提交前校验（与后端 43009 规则对齐），返回中文错误文案数组（空数组=通过）。
 * 名称/操作原因等非 config 字段仍由 Datasources.vue 单独校验。
 */
export function validateDsModel(model) {
  const errors = [];
  if (!BIZ_LINES.some(x => x.value === model.bizLine)) errors.push('业务条线必须选择公司/对公、零售或共用');
  if (!SUPPORTED_SOURCE_KINDS.includes(model.sourceKind)) {
    errors.push(`数据源来源不受支持：${model.sourceKind || '空值'}`);
  }
  switch (model.sourceKind) {
    case 'WIDE_TABLE':
      if (!model.wide.metricCodes.length) errors.push('请至少选择一个指标');
      if (model.aggEnabled) {
        if (!AGG_GROUP_BYS.includes(model.aggregation.groupBy)) errors.push('聚合维度（groupBy）取值非法');
        if (!AGG_FUNCS.includes(model.aggregation.agg)) errors.push('聚合函数（agg）取值非法');
        (model.aggregation.filters || []).forEach((f, i) => {
          const hasAny = String(f.col || '').trim() || String(f.value || '').trim();
          if (!hasAny) return; // 整行空 → 组装时会丢弃，不报错
          if (!String(f.col || '').trim() || !FILTER_OPS.includes(f.op) || !String(f.value ?? '').trim()) {
            errors.push(`过滤条件第 ${i + 1} 行需填全 列/运算符/值（IN 用逗号分隔多值）`);
          }
        });
      }
      break;
    case 'KPI_DETAIL': {
      const d = model.kpiDetail;
      if (!String(d.schemeCode || '').trim()) errors.push('请选择 KPI 方案');
      if (!['EMP', 'ORG'].includes(d.subjectType)) errors.push('主体类型只能是 EMP 或 ORG');
      if (!['SNAPSHOT', 'TREND'].includes(d.mode)) errors.push('模式只能是 SNAPSHOT 或 TREND');
      if (d.mode === 'TREND') {
        if (!d.metrics.length) {
          errors.push('趋势模式必须至少选择一个指标（细项）');
        } else if (d.metrics.some(m => !String(m.metricCode || '').trim() || !String(m.metricName || '').trim())) {
          errors.push('指标快照缺少编码或名称，请重新选择指标');
        }
        if (!KPI_VALUE_COLS.includes(d.valueCol)) errors.push('取值列只能是 score 或 completeRate');
      }
      break;
    }
    case 'M98_STAT': {
      const profile = model.m98?.profile;
      if (!M98_PROFILES.includes(profile)) errors.push('M98_STAT profile 不在固定白名单');
      if (model.scopeMode !== 'NAMED_GROUP') errors.push('M98_STAT 只能使用 NAMED_GROUP 范围');
      const expectedLine = M98_PROFILE_BIZ_LINES[profile];
      if (expectedLine && model.bizLine !== expectedLine) {
        errors.push(`M98_STAT ${profile} 只能使用 ${expectedLine} 条线`);
      }
      if ((model.fieldMeta || []).some(row => !isEmptyFieldMetaRow(row))) {
        errors.push('M98_STAT 不允许扩展字段元数据');
      }
      break;
    }
    case 'FREE_REPORT': {
      const report = model.freeReport || {};
      if (report.dataClassification !== FREE_REPORT_DATA_CLASSIFICATION
        || report.profile !== FREE_REPORT_PROFILE) {
        errors.push('FREE_REPORT 仅允许 TEST/BRANCH_OVERVIEW 配置');
      }
      if (model.scopeMode !== 'NAMED_GROUP') errors.push('FREE_REPORT 只能使用 NAMED_GROUP 范围');
      if (!String(report.batchId || '').startsWith(FREE_REPORT_BATCH_PREFIX)) {
        errors.push('FREE_REPORT 批次必须使用 TEST_BRANCH_ 前缀');
      }
      if (!['CORP', 'COMMON'].includes(model.bizLine)) {
        errors.push('FREE_REPORT 仅允许公司/对公或共用条线');
      }
      if ((model.fieldMeta || []).some(row => !isEmptyFieldMetaRow(row))) {
        errors.push('FREE_REPORT 不允许扩展字段元数据');
      }
      break;
    }
    case 'CUSTOM_SQL':
      if (!String(model.sql.text || '').trim()) errors.push('SQL 语句必填');
      if (model.dsType === 'TIMESERIES' && !String(model.sql.dateCol || '').trim()) {
        errors.push('时序型必须声明日期列');
      }
      break;
    default:
      break;
  }
  // fieldMeta 通用校验（col 非空不重复、role 枚举、decimals 非负整数）——整行空跳过
  const seen = new Set();
  (model.fieldMeta || []).forEach((r, i) => {
    if (isEmptyFieldMetaRow(r)) return;
    const col = String(r.col || '').trim();
    if (!col) { errors.push(`字段元数据第 ${i + 1} 行缺少列名（col）`); return; }
    if (seen.has(col)) errors.push(`字段元数据列名重复：${col}`);
    seen.add(col);
    if (!FIELD_ROLES.includes(r.role)) errors.push(`字段元数据第 ${i + 1} 行角色只能是 DIM 或 METRIC`);
    if (r.role === 'METRIC' && AMOUNT_SCALE_VALUES.includes(r.amountScale)) return;
    if (r.amountScale && !AMOUNT_SCALE_VALUES.includes(r.amountScale)) {
      errors.push(`字段元数据第 ${i + 1} 行金额量级取值不受支持`);
    }
    if (r.decimals !== null && r.decimals !== undefined && r.decimals !== '') {
      const n = Number(r.decimals);
      if (!Number.isInteger(n) || n < 0) errors.push(`字段元数据第 ${i + 1} 行小数位必须是非负整数`);
    }
  });
  if (!SCOPE_MODES.includes(model.scopeMode)) errors.push('数据范围模式只能是 SUBJECT、GLOBAL 或 NAMED_GROUP');
  if (model.scopeMode === 'NAMED_GROUP') {
    // NAMED_GROUP 的成员集合由后端受控注入，只允许有稳定机构映射的来源。
    if (model.sourceKind === 'KPI_DETAIL') {
      const detail = model.kpiDetail || {};
      if (detail.subjectType !== 'ORG' || detail.mode !== 'SNAPSHOT') {
        errors.push('KPI_DETAIL 的 NAMED_GROUP 只允许 ORG + SNAPSHOT');
      }
    } else if (model.sourceKind === 'M98_STAT' || model.sourceKind === 'FREE_REPORT') {
      // 两类固定来源的字段白名单和条线约束已在上面校验。
    } else if (model.sourceKind !== 'WIDE_TABLE') {
      errors.push('NAMED_GROUP 只允许 WIDE_TABLE（ORG_INDEX_RESULT + org_code）');
    } else if (model.wide?.table !== 'ORG_INDEX_RESULT') {
      errors.push('NAMED_GROUP 只允许 ORG_INDEX_RESULT + org_code 主体宽表');
    }
  }
  return errors;
}

/**
 * 读取试跑所需的数据范围模式。存量 config_json 缺 scopeMode 时仅显式兼容为 SUBJECT；
 * 解析失败或未知值不能被当作普通试跑继续执行，避免绕过 NAMED_GROUP 的测试组选择。
 */
export function datasourceTryRunScopeMode(configJson) {
  try {
    const config = parseConfigInput(configJson === '' ? {} : configJson);
    const mode = String(config.scopeMode || 'SUBJECT').toUpperCase();
    if (!SCOPE_MODES.includes(mode)) throw new Error('数据源范围模式不受支持，已拒绝试跑');
    return mode;
  } catch (error) {
    if (error?.message?.includes('数据源配置')) throw new Error('数据源配置无法解析或版本不受支持，已拒绝试跑');
    throw error;
  }
}

/**
 * 构建与 ScreenTryRunReqDTO 一致的试跑请求。NAMED_GROUP 只有显式测试组才允许发送，
 * 禁止由 orgCode/empId 或旧 config 的默认值扩大试跑范围。
 */
export function buildDatasourceTryRunRequest(row = {}, input = {}) {
  const scopeMode = datasourceTryRunScopeMode(row.configJson);
  const reason = String(input.reason || '').trim();
  if (!reason) throw new Error('高危试跑必须填写原因');
  const body = {
    sourceKind: row.sourceKind,
    dsType: row.dsType,
    configJson: row.configJson,
    period: input.period || 'LATEST',
    dateFrom: input.dateFrom || null,
    dateTo: input.dateTo || null,
    contextParams: { orgCode: input.orgCode || null, empId: input.empId || null },
    reason
  };
  if (scopeMode === 'NAMED_GROUP') {
    const testOrgGroupCode = String(input.testOrgGroupCode || '').trim();
    if (!testOrgGroupCode) throw new Error('命名机构组试跑必须选择测试机构组');
    body.testOrgGroupCode = testOrgGroupCode;
  }
  return body;
}

/**
 * 已保存数据源列探测请求（ScreenDatasourceProbeReqDTO）。
 *
 * 与配置态 try-run 不同：它不允许客户端重复提交 sourceKind/configJson，必须以 URL 中的
 * 已保存数据源 id 为准；这也是设计器禁止借 /screen/data schemaVersion=1 探列的前端边界。
 */
export function buildDatasourceProbeRequest(row = {}, input = {}) {
  const scopeMode = datasourceTryRunScopeMode(row.configJson);
  const reason = String(input.reason || '').trim();
  if (!reason) throw new Error('列探测必须填写原因');
  const suppliedContext = input.contextParams && typeof input.contextParams === 'object'
    ? input.contextParams : {};
  const normalizeContextValue = value => {
    if (value === null || value === undefined) return null;
    const text = String(value).trim();
    return text || null;
  };
  const body = {
    period: input.period || 'LATEST',
    dateFrom: input.dateFrom ?? null,
    dateTo: input.dateTo ?? null,
    contextParams: {
      orgCode: normalizeContextValue(suppliedContext.orgCode ?? input.orgCode),
      empId: normalizeContextValue(suppliedContext.empId ?? input.empId)
    },
    reason
  };
  if (scopeMode === 'NAMED_GROUP') {
    const testOrgGroupCode = String(input.testOrgGroupCode || '').trim();
    if (!testOrgGroupCode) throw new Error('命名机构组列探测必须选择测试机构组');
    body.testOrgGroupCode = testOrgGroupCode;
  }
  return body;
}

/**
 * 试跑预览列头：按 col 匹配 columnsMeta（可空，旧接口无此字段时退化为原始列名），
 * 别名替换列头、单位以全角括号附注。
 */
export function buildPreviewColumns(columns, columnsMeta) {
  const metaByCol = new Map((Array.isArray(columnsMeta) ? columnsMeta : []).map(m => [m.col, m]));
  return (columns || []).map(col => {
    const meta = metaByCol.get(col) || null;
    const preset = amountScaleDisplayOption(meta);
    const unit = preset?.unit || meta?.unit;
    let label = meta?.alias || col;
    if (unit) label += `（${unit}）`;
    return { col, label, meta };
  });
}

/**
 * 试跑预览单元格：null/undefined → "—"（如完成率 target=0 时后端返回 NULL）；
 * 数值且 meta.decimals 存在时按小数位格式化；其余原样返回。
 */
export function formatPreviewCell(value, meta) {
  if (value === null || value === undefined) return '—';
  const preset = amountScaleDisplayOption(meta);
  const decimals = preset?.decimals ?? meta?.decimals;
  const displayValue = preset ? convertAmountScaleValue(value, meta.amountScale) : value;
  if (typeof displayValue === 'number' && Number.isFinite(displayValue)
    && decimals !== null && decimals !== undefined) {
    return displayValue.toFixed(decimals);
  }
  return displayValue;
}
