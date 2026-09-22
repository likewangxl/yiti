const ACTIVE_VALUES = new Set(['ACTIVE', '1', 'TRUE']);

function parseConfig(value) {
  if (value && typeof value === 'object' && !Array.isArray(value)) return value;
  try {
    const parsed = JSON.parse(typeof value === 'string' ? value : '{}');
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function upper(value, fallback = '') {
  const text = String(value ?? '').trim().toUpperCase();
  return text || fallback;
}

function sourceCategory(kind) {
  if (kind === 'WIDE_TABLE') return 'METRIC';
  if (kind === 'KPI_DETAIL') return 'KPI';
  return 'CONTROLLED';
}

function fieldOptions(config) {
  const result = [];
  const seen = new Set();
  const add = item => {
    const col = String(item?.col || item?.metricName || item?.metricCode || '').trim();
    if (!col || seen.has(col)) return;
    seen.add(col);
    result.push({
      col,
      label: String(item?.alias || item?.metricName || item?.metricCode || col),
      role: upper(item?.role, item?.metricCode || item?.metricName ? 'METRIC' : 'UNKNOWN'),
      unit: item?.unit || null,
      metricCode: item?.metricCode || null
    });
  };
  (Array.isArray(config.fieldMeta) ? config.fieldMeta : []).forEach(add);
  (Array.isArray(config.metrics) ? config.metrics : []).forEach(add);
  if (config.table === 'ORG_INDEX_RESULT') add({ col: 'org_code', alias: '机构号', role: 'DIM' });
  if (config.aggregation?.groupBy === 'DATE') add({ col: 'data_date', alias: '日期', role: 'DIM' });
  return result;
}

function compatibilityReasons(source, config, context) {
  const reasons = [];
  const status = upper(source.status, 'ACTIVE');
  const kind = upper(source.sourceKind || source.source_kind);
  const line = upper(source.bizLine || source.biz_line, 'COMMON');
  const shape = upper(source.dsType || source.ds_type, 'SINGLE');
  const screenLine = upper(context.screenBizLine, 'COMMON');
  const namedGroup = upper(context.scopeMode) === 'NAMED_GROUP';
  const expectedShape = upper(context.expectedShape, 'ANY');
  if (!ACTIVE_VALUES.has(status)) reasons.push('数据来源已停用');
  if (screenLine !== 'COMMON' && line !== screenLine) reasons.push(`业务条线不匹配（需要${screenLine}）`);
  if (expectedShape !== 'ANY' && shape !== expectedShape) reasons.push(`结果形状不匹配（需要${expectedShape}）`);
  if (kind === 'CUSTOM_SQL') reasons.push('自定义SQL仅保留旧高级来源，不进入标准绑定');
  if (namedGroup) {
    if (kind === 'WIDE_TABLE'
        && !(config.table === 'ORG_INDEX_RESULT' && config.subjectCol === 'org_code')) {
      reasons.push('命名机构组宽表必须使用ORG_INDEX_RESULT和org_code');
    } else if (kind === 'KPI_DETAIL'
        && !(Number(config.schemaVersion) === 2 && upper(config.scopeMode) === 'NAMED_GROUP'
          && upper(config.subjectType) === 'ORG' && upper(config.mode) === 'SNAPSHOT' && shape === 'SINGLE')) {
      reasons.push('命名机构组KPI仅支持schema2 ORG SNAPSHOT SINGLE');
    } else if (kind === 'KPI_RESULT') {
      reasons.push('命名机构组不支持员工KPI总分');
    } else if (!['WIDE_TABLE', 'KPI_DETAIL', 'M98_STAT', 'FREE_REPORT', 'CUSTOM_SQL'].includes(kind)) {
      reasons.push('命名机构组不支持该来源类型');
    }
  }
  return reasons;
}

export function buildBusinessSourceCandidates(sources = [], context = {}) {
  return (Array.isArray(sources) ? sources : []).map(source => {
    const config = parseConfig(source.configJson ?? source.config_json);
    const kind = upper(source.sourceKind || source.source_kind);
    const fields = fieldOptions(config);
    const reasons = compatibilityReasons(source, config, context);
    const metrics = (Array.isArray(config.metrics) ? config.metrics : []).map(item => ({
      metricCode: item.metricCode || '', metricName: item.metricName || '', unit: item.unit || null
    }));
    const metricSearchLabel = metrics.flatMap(metric => [metric.metricName, metric.metricCode]).filter(Boolean).join(' ');
    return {
      ...source,
      id: Number(source.id),
      code: String(source.dsCode || source.ds_code || ''),
      name: String(source.dsName || source.ds_name || source.dsCode || `数据源 #${source.id}`),
      category: sourceCategory(kind),
      sourceKind: kind,
      bizLine: upper(source.bizLine || source.biz_line, 'COMMON'),
      shape: upper(source.dsType || source.ds_type, 'SINGLE'),
      dimension: upper(config.subjectType || (config.table === 'ORG_INDEX_RESULT' ? 'ORG'
        : config.table === 'EMP_INDEX_RESULT' ? 'EMP' : config.table === 'CUST_INDEX_RESULT' ? 'CUST' : 'COMMON')),
      scopeMode: upper(config.scopeMode, 'LEGACY_CONTEXT'),
      formula: kind === 'KPI_DETAIL'
        ? (upper(config.mode) === 'SNAPSHOT' || config.valueCol === 'completeRate'
          ? '完成率：实际值/目标值×100（现有大屏口径）' : 'KPI得分（方案既有结果）') : '',
      periods: (() => { try { const p = JSON.parse(source.timeParamJson || source.time_param_json || '[]'); return Array.isArray(p) ? p : []; } catch { return []; } })(),
      fields,
      metrics,
      disabled: reasons.length > 0,
      disabledReason: reasons.join('；'),
      displayLabel: `${source.dsName || source.ds_name || source.dsCode || `数据源 #${source.id}`} · ${sourceCategory(kind)} · ${source.dsCode || source.ds_code || '无编码'}${metricSearchLabel ? ` · ${metricSearchLabel}` : ''}${reasons.length ? `（${reasons.join('；')}）` : ''}`
    };
  });
}

export function filterBusinessSourceCandidates(candidates = [], filters = {}) {
  const keyword = String(filters.keyword || '').trim().toLowerCase();
  const category = upper(filters.category);
  const bizLine = upper(filters.bizLine);
  const dimension = upper(filters.dimension);
  const shape = upper(filters.shape);
  return candidates.filter(item => {
    if (category && item.category !== category) return false;
    if (bizLine && item.bizLine !== bizLine) return false;
    if (dimension && item.dimension !== dimension) return false;
    if (shape && item.shape !== shape) return false;
    if (!keyword) return true;
    return [item.name, item.code, item.sourceKind, item.disabledReason,
      ...item.metrics.flatMap(metric => [metric.metricCode, metric.metricName])]
      .some(value => String(value || '').toLowerCase().includes(keyword));
  });
}

export function reconcileBindingFields(binding = {}, candidate = {}) {
  const allowed = new Set((candidate.fields || []).map(item => item.col));
  const fields = Object.fromEntries(Object.entries(binding.fields || {}).filter(([, col]) => allowed.has(col)));
  const units = Object.fromEntries(Object.entries(binding.units || {}).filter(([semantic]) => fields[semantic]));
  return { fields, units };
}

export function createSourceRequestGate() {
  let generation = 0;
  return {
    async run(loader) {
      const token = ++generation;
      const value = await loader();
      return { value, stale: token !== generation };
    },
    invalidate() { generation += 1; }
  };
}
