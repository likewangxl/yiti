import {
  BINDING_SLOTS,
  PERIOD_LABELS,
  SLOT_ORDER,
  getCompositionMode,
  getDatasourceFieldOptions,
  normalizeBinding,
  validateBinding
} from './bindings';
import { CORPORATE_TEMPLATE, isCorporateBindingSlot } from './corporateBindings';
import { isDatasourceCompatible } from '@/utils/screenScope';
import { isNamedGroupSafeDatasource } from '../designer/widgets/chart-widget/dsFilter';

/**
 * 绑定管理页的状态只描述“配置能否被结构化理解”。它不执行查询、试跑、列探测，
 * 也不读取发布快照；STRUCTURALLY_AVAILABLE 仍必须在真实链路中核对。
 */
export const READINESS_STATUS = Object.freeze({
  UNCONFIGURED: 'UNCONFIGURED',
  STRUCTURALLY_AVAILABLE: 'STRUCTURALLY_AVAILABLE',
  CONFIG_ERROR: 'CONFIG_ERROR'
});

export const READINESS_STATUS_LABELS = Object.freeze({
  [READINESS_STATUS.UNCONFIGURED]: '未配置',
  [READINESS_STATUS.STRUCTURALLY_AVAILABLE]: '结构可用，待核对',
  [READINESS_STATUS.CONFIG_ERROR]: '配置错误'
});

export const SLOT_COUNT = SLOT_ORDER.length;

export const STATIC_READINESS_DISCLAIMER = '静态预检：仅检查屏、画布、数据源和绑定结构，未执行取数、试跑、列探测，未发布任何版本；结构可用仍需真实数据联调核对。';

const RUNTIME_PENDING_CHECK = Object.freeze({
  code: 'RUNTIME_NOT_VERIFIED',
  message: '尚未执行真实取数、试跑或发布核对'
});

const ACTIVE_DATASOURCE_STATUSES = new Set(['ACTIVE', '1', 1, true]);

function parse(value, fallback = {}) {
  if (value && typeof value === 'object' && !Array.isArray(value)) return value;
  if (typeof value !== 'string') return fallback;
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : fallback;
  } catch {
    return fallback;
  }
}

function text(value) {
  return value === undefined || value === null ? '' : String(value).trim();
}

function pick(value, ...keys) {
  for (const key of keys) {
    if (value && value[key] !== undefined && value[key] !== null && value[key] !== '') return value[key];
  }
  return undefined;
}

function idOf(value) {
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

function sourceStatusIsActive(source) {
  const raw = pick(source, 'status', 'dsStatus', 'ds_status');
  // 老数据源目录在极少数测试快照中没有 status；来源存在时把状态缺失留给
  // “待核对”，避免静态页擅自把它当成已取数成功，同时保持目录可读。
  if (raw === undefined || raw === null || raw === '') return true;
  return ACTIVE_DATASOURCE_STATUSES.has(raw)
    || ACTIVE_DATASOURCE_STATUSES.has(text(raw).toUpperCase());
}

function sourceStatusKnown(source) {
  const raw = pick(source, 'status', 'dsStatus', 'ds_status');
  return raw !== undefined && raw !== null && raw !== '';
}

function sourceConfig(source = {}) {
  return parse(pick(source, 'configJson', 'config_json', 'config'), {});
}

function sourceKindOf(source, config) {
  return text(pick(source, 'sourceKind', 'source_kind') || config.sourceKind).toUpperCase();
}

function screenBizLineOf(screen) {
  return text(pick(screen, 'bizLine', 'biz_line', 'BIZ_LINE') || 'COMMON').toUpperCase();
}

function screenOrgScopeModeOf(screen) {
  return text(pick(screen, 'orgScopeMode', 'org_scope_mode', 'ORG_SCOPE_MODE') || 'LEGACY_CONTEXT').toUpperCase();
}

function corporateScopeIssues(slot, source, screen, template = '') {
  if ((slot === 'branches' && template !== CORPORATE_TEMPLATE)
      || (!isCorporateBindingSlot(slot) && template !== CORPORATE_TEMPLATE)) return [];
  const screenLine = screenBizLineOf(screen);
  const sourceLine = text(pick(source, 'bizLine', 'biz_line', 'BIZ_LINE') || '').toUpperCase();
  const issues = [];
  if (screenLine !== 'CORP' || sourceLine !== 'CORP') {
    issues.push({
      code: 'CORPORATE_SCOPE_REQUIRED',
      message: '对公模板仅允许 CORP 条线大屏与 CORP 数据源'
    });
  }
  return issues;
}

function scopeIssues(screen, source) {
  if (!screen || !source) return [];

  const issues = [];
  const screenBizLine = screenBizLineOf(screen);
  const datasourceBizLine = text(pick(source, 'bizLine', 'biz_line', 'BIZ_LINE') || 'COMMON').toUpperCase();
  if (!isDatasourceCompatible(screenBizLine, datasourceBizLine)) {
    issues.push({
      code: 'DATASOURCE_SCOPE_MISMATCH',
      message: `数据源业务条线 ${datasourceBizLine} 与当前屏 ${screenBizLine} 不兼容`
    });
  }

  if (screenOrgScopeModeOf(screen) === 'NAMED_GROUP' && !isNamedGroupSafeDatasource(source)) {
    issues.push({
      code: 'NAMED_GROUP_DATASOURCE_UNSAFE',
      message: '命名机构组屏仅允许 ORG_INDEX_RESULT + org_code 的 WIDE_TABLE 数据源'
    });
  }
  return issues;
}

function isCompositionColumnsDatasource(source, config) {
  return sourceKindOf(source, config) === 'WIDE_TABLE'
    && text(config?.table) === 'ORG_INDEX_RESULT';
}

function sourceSql(source, config) {
  const candidates = [
    pick(source, 'sql', 'sqlText', 'sql_text', 'querySql', 'query_sql', 'query', 'statement'),
    pick(config, 'sql', 'sqlText', 'sql_text', 'querySql', 'query_sql', 'query', 'statement')
  ];
  for (const candidate of candidates) {
    if (typeof candidate === 'string' && candidate.trim()) return candidate.trim();
  }
  // 有些历史配置把 SQL 放在 query 对象内；这里只读其文本，不尝试执行。
  const nested = config.query && typeof config.query === 'object'
    ? pick(config.query, 'sql', 'sqlText', 'sql_text', 'querySql', 'query_sql', 'statement') : '';
  return typeof nested === 'string' ? nested.trim() : '';
}

function stripSqlComments(sql) {
  return String(sql || '')
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/--[^\n]*/g, ' ')
    .trim();
}

function splitSqlExpressions(value) {
  const expressions = [];
  let start = 0;
  let quote = '';
  let depth = 0;
  const input = String(value || '');
  for (let index = 0; index < input.length; index += 1) {
    const char = input[index];
    if (quote) {
      if (char === quote && input[index - 1] !== '\\') quote = '';
      continue;
    }
    if (char === "'" || char === '"' || char === '`') {
      quote = char;
      continue;
    }
    if (char === '(') depth += 1;
    if (char === ')') depth = Math.max(0, depth - 1);
    if (char === ',' && depth === 0) {
      expressions.push(input.slice(start, index).trim());
      start = index + 1;
    }
  }
  if (input.slice(start).trim()) expressions.push(input.slice(start).trim());
  return expressions;
}

function isLiteralExpression(expression) {
  const withoutAlias = String(expression || '')
    .replace(/\s+AS\s+(?:'[^']*'|"[^"]*"|`[^`]*`|[\w$\u4e00-\u9fff]+)\s*$/i, '')
    .replace(/\s+(?:'[^']*'|"[^"]*"|`[^`]*`|[\w$\u4e00-\u9fff]+)\s*$/i, '')
    .trim();
  return /^[-+]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(withoutAlias)
    || /^'(?:[^']|'')*'$/.test(withoutAlias)
    || /^"(?:[^"]|"")*"$/.test(withoutAlias)
    || /^(?:NULL|TRUE|FALSE)$/i.test(withoutAlias);
}

/** 检测明显的 SELECT 常量结果；这是静态风险提示，不能替代真实结果核验。 */
function hasConstantSql(sql, config) {
  if (config.constant === true || config.isConstant === true || config.constantSql === true) return true;
  const normalized = stripSqlComments(sql);
  if (!/^\s*SELECT\b/i.test(normalized)) return false;
  const selectBody = normalized
    .replace(/^\s*SELECT\s+/i, '')
    .split(/\bFROM\b|\bWHERE\b|\bGROUP\s+BY\b|\bORDER\s+BY\b/i)[0]
    .trim();
  if (!selectBody || /\bFROM\b/i.test(normalized)) return false;
  const expressions = splitSqlExpressions(selectBody);
  return expressions.length > 0 && expressions.every(isLiteralExpression);
}

function hasFixedOrgFilter(value) {
  const sql = stripSqlComments(value.sql);
  if (/(?:org[_\s]?code|orgCode)\s*(?:=|IN\s*\()\s*(?:'[^']+'|"[^"]+")/i.test(sql)) return true;
  const configText = JSON.stringify(value.config || {});
  if (/(?:"(?:orgCode|org_code|fixedOrgCode|fixed_org_code|orgFilter|org_filter)"\s*:\s*"[^"]+"|(?:orgCode|org_code|fixedOrgCode|fixed_org_code)\s*:\s*'[^']+')/i.test(configText)) return true;
  // 兼容 { filters: [{ field: 'org_code', value: 'ORG-1' }] } 等旧结构。
  return /(?:org[_\s]?code|orgCode)[^\]}]{0,100}(?:value|values)\s*[:=]\s*["'][^"']+["']/i.test(configText);
}

function groupByValue(config) {
  const aggregation = config?.aggregation && typeof config.aggregation === 'object' ? config.aggregation : {};
  return text(pick(aggregation, 'groupBy', 'group_by') || pick(config, 'groupBy', 'group_by')).toUpperCase();
}

function missingDateGroupBy(slot, source, config, sql) {
  if (slot !== 'trend' && slot !== 'branchTrend' && slot !== 'corpTrend') return false;
  const kind = sourceKindOf(source, config);
  if (kind === 'WIDE_TABLE' || text(pick(source, 'dsType', 'ds_type')).toUpperCase() === 'TIMESERIES') {
    return groupByValue(config) !== 'DATE';
  }
  if (kind === 'CUSTOM_SQL') {
    return !/\bGROUP\s+BY\b[\s\S]*(?:data_date|business_date|stat_date|\bdate\b)/i.test(sql);
  }
  return false;
}

function staticRisks(slot, source, config) {
  const sql = sourceSql(source, config);
  const risks = [];
  if (hasConstantSql(sql, config)) {
    risks.push({ code: 'CONSTANT_SQL', message: '检测到 SQL 直接返回常量结果，需核对真实指标来源' });
  }
  if (hasFixedOrgFilter({ sql, config })) {
    risks.push({ code: 'FIXED_ORG_FILTER', message: '检测到固定机构筛选，需核对是否覆盖当前授权机构范围' });
  }
  if (missingDateGroupBy(slot, source, config, sql)) {
    risks.push({ code: 'MISSING_DATE_GROUP_BY', message: '时序数据源未静态确认按 DATE 分组，需核对每日期一行的结果' });
  }
  return risks;
}

function unitLabel(unit) {
  return ({
    YUAN: '原始元值',
    TEN_THOUSAND: '原始万元',
    HUNDRED_MILLION: '原始亿元',
    COUNT: '原始个数',
    TEN_THOUSAND_COUNT: '原始万户',
    PERCENT: '百分数',
    RATIO: '比例'
  })[unit] || '未知单位';
}

function fieldDetails(slot, binding, source) {
  const options = getDatasourceFieldOptions(source || {});
  const spec = BINDING_SLOTS[slot];
  const fields = Object.entries(binding.fields || {}).map(([semantic, column]) => {
    const fieldSpec = (spec?.fields || []).find(item => item.semantic === semantic);
    const option = options.find(item => item.col === column);
    const unit = binding.units?.[semantic] || null;
    return {
      semantic,
      label: fieldSpec?.label || semantic,
      column,
      sourceFieldLabel: option?.label || column,
      role: option?.role || null,
      originalUnit: unit,
      unit,
      unitLabel: unitLabel(unit),
      amountScale: option?.amountScale || null
    };
  });
  return {
    fields,
    units: fields.map(item => ({ semantic: item.semantic, unit: item.unit, label: item.unitLabel })),
    fieldMap: Object.fromEntries(fields.map(item => [item.semantic, item.column]))
  };
}

function hardIssues(slot, raw, binding, source) {
  const issues = validateBinding(slot, { ...raw, ...binding, dsId: idOf(binding.dsId) || binding.dsId });
  const options = getDatasourceFieldOptions(source || {});
  const spec = BINDING_SLOTS[slot];
  for (const [semantic, column] of Object.entries(binding.fields || {})) {
    const fieldSpec = (spec?.fields || []).find(item => item.semantic === semantic);
    const option = options.find(item => item.col === column);
    if (!fieldSpec) continue;
    if (!option) {
      issues.push(`字段未在数据源元数据中声明: ${semantic}`);
      continue;
    }
    const expectedRole = fieldSpec.kind === 'dimension' ? 'DIM' : 'METRIC';
    if (String(option.role || '').toUpperCase() !== expectedRole) {
      issues.push(`字段角色不匹配: ${semantic}`);
    }
  }
  return [...new Set(issues)];
}

function normalizeArguments(input, canvasArg, datasourcesArg, bindingStateArg) {
  if (Array.isArray(input)) {
    return {
      screens: input,
      canvas: canvasArg || null,
      datasources: Array.isArray(datasourcesArg) ? datasourcesArg : [],
      bindingState: bindingStateArg || {},
      screen: null
    };
  }
  const options = input && typeof input === 'object' ? input : {};
  return {
    screens: Array.isArray(options.screens) ? options.screens : [],
    canvas: options.canvas || null,
    datasources: Array.isArray(options.datasources) ? options.datasources : [],
    bindingState: options.bindingState && typeof options.bindingState === 'object' ? options.bindingState : {},
    screen: options.screen || null
  };
}

function entryFor(slot, bindingState, datasources, screen, template = '') {
  const raw = bindingState?.[slot];
  const empty = !raw || typeof raw !== 'object' || raw.dsId === undefined || raw.dsId === null || raw.dsId === '';
  const normalized = normalizeBinding(raw || {}, slot);
  const binding = { ...normalized, dsId: idOf(normalized.dsId) || normalized.dsId };
  const source = empty ? null : datasources.find(item => idOf(item?.id) === idOf(binding.dsId));
  const details = fieldDetails(slot, binding, source);
  const entry = {
    slot,
    label: BINDING_SLOTS[slot].label,
    status: READINESS_STATUS.UNCONFIGURED,
    statusLabel: READINESS_STATUS_LABELS[READINESS_STATUS.UNCONFIGURED],
    datasourceId: source ? idOf(source.id) : (idOf(binding.dsId) || binding.dsId || null),
    datasourceName: source ? (text(pick(source, 'dsName', 'ds_name', 'dsCode', 'ds_code')) || null) : null,
    sourceKind: source ? sourceKindOf(source, sourceConfig(source)) : null,
    period: binding.period || (slot === 'trend' || slot === 'branchTrend' ? 'LAST_6M_EOM' : 'LATEST'),
    periodLabel: PERIOD_LABELS[binding.period] || binding.period || '',
    mappedFields: details.fields,
    originalUnits: details.units,
    fieldMap: details.fieldMap,
    fields: details.fieldMap,
    units: Object.fromEntries(details.units.map(item => [item.semantic, item.unit])),
    issues: [],
    pendingChecks: [],
    staticRisks: []
  };

  if (empty) return entry;

  if (!source) {
    entry.status = READINESS_STATUS.CONFIG_ERROR;
    entry.statusLabel = READINESS_STATUS_LABELS[entry.status];
    entry.issues.push({ code: 'DATASOURCE_NOT_FOUND', message: `数据源不存在或未在当前目录返回: ${String(binding.dsId)}` });
    return entry;
  }
  if (!sourceStatusIsActive(source)) {
    entry.status = READINESS_STATUS.CONFIG_ERROR;
    entry.statusLabel = READINESS_STATUS_LABELS[entry.status];
    entry.issues.push({ code: 'DATASOURCE_NOT_ACTIVE', message: '数据源不是 ACTIVE，不能作为当前屏可用来源' });
  }

  const config = sourceConfig(source);
  entry.issues.push(...scopeIssues(screen, source));
  entry.issues.push(...corporateScopeIssues(slot, source, screen, template));
  if (slot === 'composition' && getCompositionMode(binding) === 'columns'
      && !isCompositionColumnsDatasource(source, config)) {
    entry.issues.push({
      code: 'COMPOSITION_COLUMNS_DATASOURCE_UNSUPPORTED',
      message: '业务构成双列模式仅允许 WIDE_TABLE 且表为 ORG_INDEX_RESULT'
    });
  }
  const validationIssues = hardIssues(slot, raw, binding, source);
  for (const message of validationIssues) entry.issues.push({ code: 'BINDING_INVALID', message });
  entry.staticRisks = staticRisks(slot, source, config);
  entry.pendingChecks.push(...entry.staticRisks);
  if (!sourceStatusKnown(source)) {
    entry.pendingChecks.push({ code: 'DATASOURCE_STATUS_UNVERIFIED', message: '数据源目录未返回 ACTIVE 状态，需核对授权状态' });
  }
  if (!entry.issues.length) {
    entry.status = READINESS_STATUS.STRUCTURALLY_AVAILABLE;
    entry.statusLabel = READINESS_STATUS_LABELS[entry.status];
  } else {
    entry.status = READINESS_STATUS.CONFIG_ERROR;
    entry.statusLabel = READINESS_STATUS_LABELS[entry.status];
  }
  if (entry.status === READINESS_STATUS.STRUCTURALLY_AVAILABLE) entry.pendingChecks.push(RUNTIME_PENDING_CHECK);
  return entry;
}

/**
 * 分析当前绑定页可见的屏、画布、数据源和 bindingState。
 * 同时支持对象参数和历史测试方便使用的 (screens, canvas, datasources, bindingState) 参数。
 */
export function analyzeIntegrationReadiness(input = {}, canvasArg, datasourcesArg, bindingStateArg) {
  const { screens, canvas, datasources, bindingState, screen: explicitScreen } = normalizeArguments(
    input, canvasArg, datasourcesArg, bindingStateArg
  );
  const canvasId = idOf(pick(canvas, 'screenId', 'screen_id'));
  const screen = explicitScreen || screens.find(item => idOf(item?.id) === canvasId) || null;
  const explicitTemplate = text(input?.template || '').toLowerCase();
  const corporateSlotOrder = Array.isArray(input?.slotOrder)
    && input.slotOrder.some(slot => isCorporateBindingSlot(slot) && slot !== 'branches');
  const template = explicitTemplate === CORPORATE_TEMPLATE || corporateSlotOrder
    ? CORPORATE_TEMPLATE : text(input?.template || '');
  const slotOrder = Array.isArray(input.slotOrder) ? input.slotOrder.filter(slot => BINDING_SLOTS[slot]) : SLOT_ORDER;
  const entries = slotOrder.map(slot => {
    const entry = entryFor(slot, bindingState, datasources, screen, template);
    if (slotOrder.includes('retailAum') && entry.status !== READINESS_STATUS.UNCONFIGURED) {
      const source = datasources.find(item => String(item.id) === String(bindingState[slot]?.dsId));
      if (screenBizLineOf(screen) !== 'RETAIL' || text(pick(source, 'bizLine', 'biz_line') || 'COMMON').toUpperCase() !== 'RETAIL') {
        entry.issues.push({ code: 'RETAIL_SCOPE_REQUIRED', message: '零售模板必须使用零售条线大屏与数据源' });
        entry.status = READINESS_STATUS.CONFIG_ERROR;
        entry.statusLabel = READINESS_STATUS_LABELS[entry.status];
      }
    }
    return entry;
  });
  const statusCounts = Object.fromEntries(Object.values(READINESS_STATUS).map(status => [status, 0]));
  for (const entry of entries) statusCounts[entry.status] += 1;
  const issues = entries.flatMap(entry => entry.issues.map(issue => ({ slot: entry.slot, ...issue })));
  const pendingChecks = entries.flatMap(entry => entry.pendingChecks.map(check => ({ slot: entry.slot, ...check })));
  return {
    screenId: idOf(pick(screen, 'id', 'screenId', 'screen_id')) || canvasId,
    screenCode: text(pick(screen, 'screenCode', 'screen_code')) || null,
    screenName: text(pick(screen, 'screenName', 'screen_name')) || null,
    screenFound: Boolean(screen),
    canvasLoaded: Boolean(canvas && typeof canvas === 'object'),
    slotCount: entries.length,
    entries,
    slots: entries,
    configuredCount: entries.filter(item => item.status !== READINESS_STATUS.UNCONFIGURED).length,
    unconfiguredCount: statusCounts[READINESS_STATUS.UNCONFIGURED],
    structurallyAvailableCount: statusCounts[READINESS_STATUS.STRUCTURALLY_AVAILABLE],
    configErrorCount: statusCounts[READINESS_STATUS.CONFIG_ERROR],
    statusCounts,
    issues,
    pendingChecks,
    staticPreflight: true,
    runtimeVerified: false,
    published: false,
    disclaimer: STATIC_READINESS_DISCLAIMER
  };
}

export { hasConstantSql, hasFixedOrgFilter, missingDateGroupBy };
