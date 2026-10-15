/**
 * 把 PERF_METRIC_DEF 目录项组装成宽表数据源和组件绑定。
 *
 * 输出列名使用指标名称（引擎别名），不是 val_N。单位缺失时不猜测。
 * M_0266 是月均余额较上月，不能建议为时点存款净增。
 */

const AGG_FUNCS = Object.freeze(['SUM', 'AVG', 'MAX', 'MIN', 'COUNT']);
const GROUP_BYS = Object.freeze(['NONE', 'SUBJECT', 'DATE']);
const FILTER_OPS = Object.freeze(['EQ', 'NE', 'GT', 'GE', 'LT', 'LE']);
const BIZ_LINES = Object.freeze(['CORP', 'RETAIL', 'COMMON']);
const PERIODS = Object.freeze(['LATEST', 'LAST_10D', 'LAST_1M', 'LAST_6M_EOM']);
const RATIO_UNITS = new Set(['PERCENT', 'RATIO']);

const BLOCKED_REASONS = Object.freeze({
  citySummary: '城市汇总必须使用已经按城市汇总的来源，系统不会把机构指标自动相加。',
  attention: '关注事项不是宽表指标，不能从指标目录直接生成。',
  retailAttention: '关注事项不是宽表指标，不能从指标目录直接生成。',
  corpAttention: '关注事项不是宽表指标，不能从指标目录直接生成。',
  branches: '机构目录来自授权画像，不能用指标宽表代替机构身份。',
  retailSegments: '客群分段需要名称维度，不能只由指标宽表聚合生成。',
  corpSegments: '客群分段需要名称维度，不能只由指标宽表聚合生成。'
});

const DATE_SLOTS = new Set(['trend', 'branchTrend', 'retailTrend', 'corpTrend']);
const SUBJECT_SLOTS = new Set(['ranking', 'retailRanking', 'corpRanking']);
const INCREASE_SLOTS = new Set(['depositIncrease']);
const INCREASE_SEMANTICS = new Set(['depositIncrease', 'increase', 'change']);

export function bindingShape(slot, compositionMode) {
  if (BLOCKED_REASONS[slot]) {
    return { blocked: true, reason: BLOCKED_REASONS[slot], groupBy: null, period: null };
  }
  if (slot === 'composition' && compositionMode !== 'columns') {
    return {
      blocked: true,
      reason: '构成的行模式需要名称和数值明细，不能由指标宽表直接生成。请改用对公/零售双列，或在高级配置中选择已有来源。',
      groupBy: null,
      period: null
    };
  }
  if (DATE_SLOTS.has(slot)) return { blocked: false, groupBy: 'DATE', period: 'LAST_6M_EOM' };
  if (SUBJECT_SLOTS.has(slot)) return { blocked: false, groupBy: 'SUBJECT', period: 'LATEST' };
  return { blocked: false, groupBy: 'NONE', period: 'LATEST' };
}

export function filterCatalog(items, query = {}) {
  const keyword = text(query.keyword).toLowerCase();
  const baseDim = upper(query.baseDim);
  const category = text(query.category);
  return (Array.isArray(items) ? items : []).filter(item => {
    if (query.bindableOnly && !item?.bindable) return false;
    if (baseDim && upper(item?.baseDim) !== baseDim) return false;
    if (category && text(item?.metricCategory) !== category) return false;
    if (!keyword) return true;
    const hay = `${item?.metricCode || ''} ${item?.metricName || ''} ${item?.metricCategory || ''}`.toLowerCase();
    return hay.includes(keyword);
  });
}

/**
 * 只给出需要人工确认的建议。对公/零售必须名称里带条线，通用“存款余额”不会被当成对公。
 */
export function suggestAssignments({ template, slot, fieldSpecs = [], metrics = [] } = {}) {
  const notes = [];
  const bindable = (Array.isArray(metrics) ? metrics : []).filter(item => item?.bindable);
  if (INCREASE_SLOTS.has(slot) || fieldSpecs.some(field => INCREASE_SEMANTICS.has(field?.semantic))) {
    notes.push('M_0266（一般性存款月均余额较上月）不是时点存款净增，不会自动建议到“存款净增”。请按口径手工选择，或留空。');
  }
  const assignments = {};
  const candidates = {};
  for (const field of fieldSpecs) {
    if (field?.kind === 'dimension') continue;
    const matched = bindable.filter(metric => matchesMetric(template, slot, field.semantic, metric));
    candidates[field.semantic] = matched;
    assignments[field.semantic] = matched.length === 1 ? matched[0] : null;
  }
  return { assignments, candidates, notes };
}

export function buildMetricBindingPlan(input = {}) {
  const shape = bindingShape(input.slot, input.compositionMode);
  const empty = { ok: false, errors: [], warnings: [], binding: null, datasourceRequest: null, existingDatasourceId: null, previewRow: null };
  if (shape.blocked) return { ...empty, errors: [shape.reason] };

  const errors = [];
  const warnings = [];
  const reason = text(input.reason);
  if (!reason && !input.comparisonsOnly) errors.push('必须填写原因');
  const bizLine = upper(input.screen?.bizLine);
  if (!BIZ_LINES.includes(bizLine)) errors.push('屏业务条线无效');
  const namedGroup = upper(input.screen?.orgScopeMode) === 'NAMED_GROUP';
  const scopeMode = namedGroup ? 'NAMED_GROUP' : 'GLOBAL';
  if (namedGroup && !text(input.screen?.orgGroupCode)) {
    warnings.push('当前屏未填写机构组编码，命名机构组试跑前需要选择测试机构组。');
  }
  let groupBy = upper(input.aggregation?.groupBy) || shape.groupBy;
  if (!GROUP_BYS.includes(groupBy)) errors.push('聚合维度不受支持');

  const metrics = Array.isArray(input.metrics) ? input.metrics : [];
  const byCode = new Map(metrics.map(item => [item?.metricCode, item]));
  const selected = [];
  for (const [semantic, assignment] of Object.entries(input.assignments || {})) {
    if (!assignment?.metricCode) continue;
    const metric = byCode.get(assignment.metricCode);
    const fieldSpec = (input.fieldSpecs || []).find(item => item.semantic === semantic);
    const label = fieldSpec?.label || semantic;
    if (!metric?.bindable) {
      errors.push(`${label} 的指标 ${assignment.metricCode} 不可绑定`);
      continue;
    }
    if (namedGroup && upper(metric.baseDim) !== 'ORG') {
      errors.push('命名机构组大屏只能绑定机构维度指标');
      continue;
    }
    const unit = text(assignment.unit);
    if (!unit) errors.push(`${label} 缺少单位，系统不会猜测`);
    else if (fieldSpec?.unitKinds?.length && !fieldSpec.unitKinds.includes(unit)) errors.push(`${label} 的单位不适用`);
    selected.push({ semantic, metric, unit, label });
  }
  const mainUnitKind = resolveMainUnitKind(input, selected);
  const periodSelected = collectPeriodMetrics(input.periodComparisons, byCode, namedGroup, errors, mainUnitKind);
  if (input.comparisonsOnly) {
    if (!periodSelected.length) errors.push('请至少配置一个期间比较');
  } else if (!selected.length) errors.push('请至少选择一个指标');
  for (const field of input.fieldSpecs || []) {
    if (field?.kind === 'dimension' || !field?.required) continue;
    if (input.comparisonsOnly) continue;
    if (!selected.some(item => item.semantic === field.semantic)) errors.push(`请配置${field.label}`);
  }
  const dimensions = new Set([...selected, ...periodSelected].map(item => upper(item.metric.baseDim)));
  if (dimensions.size > 1) errors.push('同一数据源只能使用同一维度的指标');
  const table = selected[0]?.metric.tableName || periodSelected[0]?.metric.tableName || '';
  if (periodSelected.some(item => item.metric.tableName !== table)) errors.push('期间比较必须与主指标同一结果表');
  if (namedGroup && table && table !== 'ORG_INDEX_RESULT') errors.push('命名机构组只能使用机构指标宽表');
  if (periodSelected.some(item => item.mode === 'OFFSET')) {
    if (groupBy === 'SUBJECT') errors.push('按主体聚合不能回推基准日');
    else if (groupBy !== 'DATE') {
      groupBy = 'DATE';
      warnings.push('已有期间比较按基准日回推，聚合维度改为按日期，否则结果表不会按日返回历史值。主值查询仍使用最新周期。');
    }
  }

  let agg = upper(input.aggregation?.agg);
  if (!agg) agg = selected.some(item => RATIO_UNITS.has(item.unit)) ? 'AVG' : upper(selected[0]?.metric.summaryRule);
  if (!AGG_FUNCS.includes(agg)) agg = 'SUM';
  if (groupBy === 'NONE' && selected.some(item => RATIO_UNITS.has(item.unit)) && agg === 'SUM') {
    warnings.push('完成率或比例使用求和会失真，建议改为平均。');
  }
  if (DATE_SLOTS.has(input.slot) && groupBy !== 'DATE') warnings.push('趋势组件需要按日期聚合，否则没有时间轴。');
  if (SUBJECT_SLOTS.has(input.slot) && groupBy !== 'SUBJECT') warnings.push('排名组件需要按主体聚合，否则不会返回机构行。');

  const filters = [];
  for (const filter of input.filters || []) {
    const op = upper(filter?.op);
    const value = text(filter?.value);
    if (!FILTER_OPS.includes(op)) {
      errors.push('过滤运算符不受支持');
      continue;
    }
    if (!value) {
      errors.push('过滤值不能为空');
      continue;
    }
    if (filter.target === 'legacy') {
      const legacyColumn = text(filter.col);
      if (!legacyColumn) errors.push('历史过滤条件缺少原始列');
      else filters.push({ col: legacyColumn, op, value });
      continue;
    }
    let col = '';
    if (filter.target === 'data_date') col = 'data_date';
    else if (filter.target === 'subject') col = selected[0]?.metric.subjectColumn || '';
    else if (filter.target === 'metric') {
      const metric = byCode.get(filter.metricCode);
      if (!metric?.valSlot) errors.push('过滤指标缺少槽位');
      else col = `val_${metric.valSlot}`;
    }
    if (!col) errors.push('过滤列不受支持');
    else filters.push({ col, op, value });
  }

  const fields = {};
  const units = {};
  const seen = new Set();
  for (const item of selected) {
    const column = text(item.metric.outputColumn);
    if (!column || seen.has(column)) errors.push('指标输出列重复或为空');
    seen.add(column);
    fields[item.semantic] = column;
    units[item.semantic] = item.unit;
  }
  if (groupBy === 'DATE') {
    for (const field of input.fieldSpecs || []) {
      if (field?.kind === 'dimension' && (field.semantic === 'date' || field.semantic === 'dataDate')) {
        fields[field.semantic] = 'data_date';
      }
    }
  }
  if (groupBy === 'SUBJECT' && upper(selected[0]?.metric.baseDim) === 'ORG') {
    for (const field of input.fieldSpecs || []) {
      if (field?.semantic === 'orgCode') fields.orgCode = 'org_code';
      if (field?.semantic === 'name' || field?.semantic === 'orgName') fields[field.semantic] = 'org_name';
    }
  }

  if (errors.length) return { ...empty, errors, warnings };

  const metricItems = [];
  const seenCodes = new Set();
  for (const item of [...selected, ...periodSelected]) {
    if (seenCodes.has(item.metric.metricCode)) continue;
    seenCodes.add(item.metric.metricCode);
    metricItems.push(item);
  }
  const config = {
    schemaVersion: 2,
    scopeMode,
    table,
    metrics: metricItems.map(item => ({ metricCode: item.metric.metricCode })),
    aggregation: filters.length ? { groupBy, agg, filters } : { groupBy, agg },
    fieldMeta: [
      ...selected.map(item => ({
        col: item.metric.outputColumn,
        alias: item.metric.metricName,
        role: 'METRIC',
        unit: item.unit,
        semantic: item.semantic
      })),
      ...periodSelected
        .filter(item => !selected.some(main => main.metric.metricCode === item.metric.metricCode))
        .map(item => ({
          col: item.metric.outputColumn,
          alias: item.metric.metricName,
          role: 'METRIC',
          unit: item.unit,
          semantic: item.semantic
        }))
    ]
  };
  const configJson = JSON.stringify(config);
  const dsType = groupBy === 'DATE' ? 'TIMESERIES' : 'SINGLE';
  const period = PERIODS.includes(input.period) ? input.period : shape.period;
  const existing = findExisting(input.datasources, {
    table, scopeMode, bizLine, groupBy, agg, filters,
    codes: metricItems.map(item => item.metric.metricCode)
  });
  const periodComparisons = storedPeriodComparisons(input.periodComparisons, periodSelected);
  return {
    ok: true,
    errors: [],
    warnings,
    comparisonsOnly: Boolean(input.comparisonsOnly),
    periodComparisons,
    extraMetrics: periodSelected.map(item => ({
      metricCode: item.metric.metricCode,
      metricName: item.metric.metricName,
      outputColumn: item.metric.outputColumn,
      unit: item.unit,
      tableName: item.metric.tableName,
      semantic: item.semantic,
      mode: item.mode
    })),
    binding: input.comparisonsOnly ? null : { period, fields, units },
    datasourceRequest: input.comparisonsOnly || existing ? null : {
      dsName: datasourceName(input.slotLabel, selected),
      dsType,
      sourceKind: 'WIDE_TABLE',
      bizLine,
      configJson,
      timeParamJson: JSON.stringify(PERIODS),
      status: 'ACTIVE',
      reason
    },
    existingDatasourceId: existing?.id ?? null,
    previewRow: { sourceKind: 'WIDE_TABLE', dsType, configJson }
  };
}

function matchesMetric(template, slot, semantic, metric) {
  if (INCREASE_SLOTS.has(slot) || INCREASE_SEMANTICS.has(semantic)) return false;
  const name = coreName(metric?.metricName);
  const code = text(metric?.metricCode);
  if ((slot === 'depositAverage' || semantic === 'average') && code === 'M_0265') return true;
  if (slot === 'composition' && semantic === 'corporate') {
    return code === 'M_0277' || (name.includes('对公') && name.includes('一般性存款余额') && pointBalance(name));
  }
  if (slot === 'composition' && semantic === 'retail') {
    return code === 'M_0309' || (name.includes('零售') && name.includes('一般性存款余额') && pointBalance(name));
  }
  const corporate = template === 'corp-overview-v1' || String(slot || '').startsWith('corp');
  const retail = !corporate && (template === 'retail-overview-v1' || String(slot || '').startsWith('retail'));
  if (corporate) return matchesLine(name, '对公', slot, semantic);
  if (retail) return matchesLine(name, '零售', slot, semantic);
  if (semantic === 'value' && slot === 'deposit') {
    return pointDeposit(name) && !name.includes('对公') && !name.includes('零售') && !name.includes('基础') && !name.includes('同业');
  }
  if (semantic === 'value' && slot === 'loan') return name.includes('贷款') && pointBalance(name);
  if (semantic === 'value' && slot === 'depositAverage') return name.includes('一般性存款月均余额') && !name.includes('较');
  if (semantic === 'deposit' || (semantic === 'value' && slot === 'ranking')) return pointDeposit(name) && !name.includes('基础') && !name.includes('同业');
  if (semantic === 'loan') return name.includes('贷款') && pointBalance(name);
  return false;
}

function matchesLine(name, line, slot, semantic) {
  if (!name.includes(line)) return false;
  const wantsLoan = semantic === 'loan' || /loan/i.test(slot);
  const wantsAverage = /average/i.test(slot) || semantic === 'average';
  if (wantsLoan) return name.includes('贷款') && pointBalance(name);
  if (wantsAverage) return name.includes('存款') && name.includes('月均') && !name.includes('较');
  if (semantic === 'value' || semantic === 'deposit') return name.includes('存款') && pointBalance(name);
  return false;
}

function pointBalance(name) {
  return name.includes('余额') && !/较|日均|月均/.test(name);
}

function pointDeposit(name) {
  return name.includes('存款') && pointBalance(name);
}

function coreName(name) {
  return text(name).replace(/-(机构|员工|客户)$/u, '');
}

function findExisting(datasources, expected) {
  for (const source of Array.isArray(datasources) ? datasources : []) {
    if (upper(source?.status) !== 'ACTIVE') continue;
    if (upper(source?.sourceKind || source?.source_kind) !== 'WIDE_TABLE') continue;
    if (upper(source?.bizLine || source?.biz_line) !== expected.bizLine) continue;
    const config = parseConfig(source.configJson ?? source.config_json);
    if (!config) continue;
    if (config.table !== expected.table || upper(config.scopeMode) !== expected.scopeMode) continue;
    const codes = (config.metrics || []).map(item => item?.metricCode).filter(Boolean).sort();
    const wanted = [...expected.codes].sort();
    if (codes.length !== wanted.length || codes.some((code, index) => code !== wanted[index])) continue;
    const aggregation = config.aggregation || {};
    if (upper(aggregation.groupBy) !== expected.groupBy || upper(aggregation.agg) !== expected.agg) continue;
    if (JSON.stringify(aggregation.filters || []) !== JSON.stringify(expected.filters || [])) continue;
    return source;
  }
  return null;
}

function parseConfig(value) {
  if (value && typeof value === 'object') return value;
  try {
    const parsed = JSON.parse(value || '');
    return parsed && typeof parsed === 'object' ? parsed : null;
  } catch {
    return null;
  }
}

function datasourceName(slotLabel, selected) {
  const metricNames = selected.map(item => item.metric.metricName).filter(Boolean).join('、');
  const name = `指标绑定·${text(slotLabel) || '组件'}·${metricNames}`;
  return name.length <= 100 ? name : `${name.slice(0, 97)}...`;
}

const PERIOD_LABELS = Object.freeze({ day: '较昨日', month: '较上月', year: '较上年' });
const PERIOD_SEMANTICS = Object.freeze({ day: 'periodDay', month: 'periodMonth', year: 'periodYear' });
const PERIOD_UNITS = new Set(['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION', 'PERCENT', 'RATIO']);

function unitKind(value) {
  const unit = upper(value);
  if (['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION'].includes(unit)) return 'AMOUNT';
  if (['PERCENT', 'RATIO'].includes(unit)) return 'RATIO';
  return '';
}

function resolveMainUnitKind(input, selected) {
  if (input.comparisonsOnly) return 'AMOUNT';
  const primaryField = (input.fieldSpecs || []).find(field => field?.semantic === 'value')
    || (input.fieldSpecs || []).find(field => field?.kind !== 'dimension' && field?.required)
    || (input.fieldSpecs || []).find(field => field?.kind !== 'dimension');
  const primary = selected.find(item => item.semantic === primaryField?.semantic) || selected[0];
  const selectedKind = unitKind(primary?.unit);
  if (selectedKind) return selectedKind;
  const kinds = [...new Set((primaryField?.unitKinds || []).map(unitKind).filter(Boolean))];
  return kinds.length === 1 ? kinds[0] : '';
}

function collectPeriodMetrics(raw, byCode, namedGroup, errors, mainUnitKind = '') {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return [];
  const selected = [];
  for (const key of ['day', 'month', 'year']) {
    const slot = raw[key] || {};
    const mode = slot.mode === 'METRIC' || slot.mode === 'OFFSET' ? slot.mode : 'UNSET';
    if (mode === 'UNSET') continue;
    const label = PERIOD_LABELS[key];
    const metric = byCode.get(slot.metricCode);
    if (!metric?.bindable) {
      errors.push(`${label} 的指标 ${text(slot.metricCode) || '未选择'} 不可绑定`);
      continue;
    }
    if (namedGroup && upper(metric.baseDim) !== 'ORG') {
      errors.push('命名机构组大屏只能绑定机构维度指标');
      continue;
    }
    const explicit = upper(slot.unit);
    const metricUnit = upper(metric.unit);
    const unit = PERIOD_UNITS.has(explicit) ? explicit : (PERIOD_UNITS.has(metricUnit) ? metricUnit : '');
    if (!unit) errors.push(`${label} 缺少明确单位，系统不会猜测`);
    else if (metricUnit && unitKind(unit) !== unitKind(metricUnit)) errors.push(`${label} 的单位量纲必须与当前主指标一致`);
    else if (mainUnitKind && unitKind(unit) !== mainUnitKind) errors.push(`${label} 的单位量纲必须与主指标一致`);
    selected.push({ key, mode, semantic: PERIOD_SEMANTICS[key], metric, unit, label });
  }
  return selected;
}

function storedPeriodComparisons(raw, periodSelected) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null;
  const stored = {};
  for (const key of ['day', 'month', 'year']) {
    const found = periodSelected.find(item => item.key === key);
    stored[key] = found
      ? {
        mode: found.mode,
        metricCode: found.metric.metricCode,
        outputColumn: text(found.metric.outputColumn),
        unit: found.unit,
        tableName: text(found.metric.tableName)
      }
      : { mode: 'UNSET', metricCode: '', outputColumn: '', unit: '', tableName: '' };
  }
  return stored;
}

/** 把期间比较指标并入已经绑定的宽表配置。主指标字段保持不变。 */
export function mergePeriodMetrics(config, additions, { forceDate = false } = {}) {
  if (!config || typeof config !== 'object' || !text(config.table)) return { ok: false, reason: '来源不是结果表宽表' };
  const metrics = Array.isArray(config.metrics) ? config.metrics.map(item => ({ ...item })) : [];
  const fieldMeta = Array.isArray(config.fieldMeta) ? config.fieldMeta.map(item => ({ ...item })) : [];
  for (const item of additions || []) {
    if (item?.tableName && item.tableName !== config.table) return { ok: false, reason: '期间比较必须与已绑定来源同一结果表' };
    if (!item?.metricCode || !item?.outputColumn) return { ok: false, reason: '期间比较指标缺少编码或输出列' };
    if (metrics.some(metric => metric?.metricCode === item.metricCode)) continue;
    metrics.push({ metricCode: item.metricCode });
    fieldMeta.push({
      col: item.outputColumn,
      alias: item.metricName || item.outputColumn,
      role: 'METRIC',
      unit: item.unit,
      semantic: item.semantic || 'periodComparison'
    });
  }
  const aggregation = { ...(config.aggregation || {}) };
  if (forceDate) aggregation.groupBy = 'DATE';
  return { ok: true, config: { ...config, metrics, fieldMeta, aggregation } };
}

function text(value) {
  return String(value ?? '').trim();
}

function upper(value) {
  const normalized = text(value);
  return normalized ? normalized.toUpperCase() : '';
}
