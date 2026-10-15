const AMOUNT_UNITS = Object.freeze(['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION']);
const RATIO_UNITS = Object.freeze(['PERCENT', 'RATIO']);
const COUNT_UNITS = Object.freeze(['COUNT', 'TEN_THOUSAND_COUNT']);
const ALL_METRIC_UNITS = Object.freeze([...AMOUNT_UNITS, ...RATIO_UNITS, ...COUNT_UNITS]);

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function upper(value) {
  return text(value).toUpperCase();
}

function object(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function parseJson(value, fallback = {}) {
  if (object(value)) return value;
  try {
    const parsed = JSON.parse(value || '');
    return object(parsed) ? parsed : fallback;
  } catch {
    return fallback;
  }
}

const FILTER_OPS = Object.freeze(['EQ', 'NE', 'GT', 'GE', 'LT', 'LE']);

/** 把旧数据源的 SQL 过滤列反向映射为表单过滤；无法证明语义时保持只读原条件。 */
export function normalizeInitialFilters(filters = [], { datasource = {}, catalog = [] } = {}) {
  const config = parseJson(datasource?.configJson, {});
  const sourceCodes = new Set((Array.isArray(config.metrics) ? config.metrics : [])
    .map(item => text(item?.metricCode)).filter(Boolean));
  const sourceMetrics = (Array.isArray(catalog) ? catalog : [])
    .filter(item => !sourceCodes.size || sourceCodes.has(text(item?.metricCode)));
  const bySlot = new Map();
  const byColumn = new Map();
  const subjectColumns = new Set(['org_code', 'org_id', 'subject_code', 'subject_id']);
  for (const metric of sourceMetrics) {
    const code = text(metric?.metricCode);
    if (!code) continue;
    if (metric?.valSlot !== undefined && metric?.valSlot !== null) bySlot.set(String(metric.valSlot), code);
    for (const column of [metric?.outputColumn, metric?.metricName]) {
      if (text(column)) byColumn.set(text(column), code);
    }
    if (text(metric?.subjectColumn)) subjectColumns.add(text(metric.subjectColumn));
  }
  for (const item of Array.isArray(config.fieldMeta) ? config.fieldMeta : []) {
    const column = text(item?.col || item?.outputColumn);
    if (column && upper(item?.role) === 'DIM' && (/(_code|_id)$/i.test(column) || /subject|org/i.test(column))) {
      subjectColumns.add(column);
    }
  }
  return (Array.isArray(filters) ? filters : []).map(filter => {
    if (filter?.target) return { ...filter };
    const col = text(filter?.col || filter?.column || filter?.field);
    const op = upper(filter?.op);
    const value = filter?.value ?? '';
    const base = { col, op, value };
    if (!col || !FILTER_OPS.includes(op)) {
      return { ...base, target: 'legacy', readonly: true };
    }
    if (col === 'data_date') return { ...base, target: 'data_date' };
    if (subjectColumns.has(col)) return { ...base, target: 'subject' };
    const slotMatch = /^val_(\d+)$/i.exec(col);
    const metricCode = slotMatch ? bySlot.get(slotMatch[1]) : byColumn.get(col);
    if (metricCode) return { ...base, target: 'metric', metricCode };
    return { ...base, target: 'legacy', readonly: true };
  });
}

export function unitKind(value) {
  const unit = upper(value);
  if (AMOUNT_UNITS.includes(unit)) return 'AMOUNT';
  if (RATIO_UNITS.includes(unit)) return 'RATIO';
  if (COUNT_UNITS.includes(unit)) return 'COUNT';
  return '';
}

function unitsFor(value) {
  const kind = unitKind(value);
  if (kind === 'AMOUNT') return [...AMOUNT_UNITS];
  if (kind === 'RATIO') return [...RATIO_UNITS];
  if (kind === 'COUNT') return [...COUNT_UNITS];
  return [...ALL_METRIC_UNITS];
}

function componentUnit(component, fallback = '') {
  return text(component?.dataRefs?.[0]?.unit) || text(fallback);
}

function tabFor(entry, component) {
  const tabs = Array.isArray(component?.content?.tabs) ? component.content.tabs : [];
  const selectedKey = text(entry?.tabKey);
  const actual = tabs.find(tab => text(tab?.tabKey) === selectedKey);
  if (actual) return actual;
  if (entry?.tabConfig && object(entry.tabConfig)) return entry.tabConfig;
  const sourceKey = text(entry?.sourceTabKey);
  return tabs.find(tab => text(tab?.tabKey) === sourceKey) || tabs[0] || {};
}

function metricField(semantic, label, unit, required = true) {
  return { semantic, label, kind: 'metric', required, unitKinds: unitsFor(unit) };
}

/** 根据 schema1 当前可见组件建立表单字段，避免沿用 template slot 的通用字段。 */
export function buildSchemaFieldSpecs({ entry, component } = {}) {
  const kind = text(entry?.kind);
  const type = upper(component?.componentType);
  if (kind === 'BUSINESS_GROWTH') {
    return [
      metricField('retailDeposit', '零售存款', 'YUAN'),
      metricField('retailLoan', '零售贷款', 'YUAN'),
      metricField('corpDeposit', '对公存款', 'YUAN'),
      metricField('corpLoan', '对公贷款', 'YUAN'),
      { semantic: 'date', label: '数据日期', kind: 'dimension' }
    ];
  }
  if (kind === 'COMPOSITION_TAB') {
    const tab = tabFor(entry, component);
    const unit = text(tab.unit) || componentUnit(component, 'YUAN');
    return [
      metricField('corporate', '对公', unit),
      metricField('retail', '零售', unit),
      metricField('total', '总量', unit, false)
    ];
  }
  if (kind === 'INSTITUTION_RANKING' || type === 'RANKING') {
    const metrics = Array.isArray(component?.content?.rankingMetrics) ? component.content.rankingMetrics : [];
    const fields = metrics.filter(item => item?.visible !== false).map(item => ({
      semantic: text(item.metricKey) || 'value',
      label: text(item.label) || text(item.metricKey) || '排名指标',
      kind: 'metric',
      required: true,
      unitKinds: unitsFor(item.unit || componentUnit(component, 'YUAN'))
    }));
    return [
      ...fields,
      { semantic: 'orgCode', label: '机构编码', kind: 'dimension' },
      { semantic: 'name', label: '机构名称', kind: 'dimension' }
    ];
  }
  if (type === 'METRIC_CARD' || type === 'COMPLETION') {
    const specs = [metricField('value', '当前指标', componentUnit(component))];
    for (const item of Array.isArray(component?.content?.subFields) ? component.content.subFields : []) {
      const field = text(item?.field || item?.key);
      if (!field) continue;
      specs.push(metricField(`sub:${field}`, text(item?.label) || field, item?.unit || componentUnit(component), false));
    }
    return specs;
  }
  if (type === 'TREND') {
    const series = Array.isArray(component?.content?.series) ? component.content.series : [];
    return [
      ...series.filter(item => item?.visible !== false).map(item => metricField(
        text(item.seriesKey) || text(item.field), text(item.label) || text(item.seriesKey) || '趋势指标', item.unit
      )),
      { semantic: 'date', label: '数据日期', kind: 'dimension' }
    ];
  }
  if (type === 'COMPOSITION_TABS') {
    const specs = [];
    for (const tab of Array.isArray(component?.content?.tabs) ? component.content.tabs : []) {
      for (const field of ['corporateField', 'retailField', 'totalField', 'nameField', 'valueField']) {
        if (!Object.prototype.hasOwnProperty.call(tab, field) || !text(tab[field])) continue;
        const kind = field.endsWith('Field') && ['nameField', 'valueField'].includes(field) ? 'metric' : 'metric';
        specs.push(metricField(`tab:${text(tab.tabKey)}:${field}`, `${text(tab.label) || text(tab.tabKey) || '构成'}·${field.replace('Field', '')}`, tab.unit, false));
      }
    }
    return specs;
  }
  if (type === 'DETAIL_TABLE') {
    return (Array.isArray(component?.content?.columns) ? component.content.columns : [])
      .filter(item => text(item?.field))
      .map(item => ({ semantic: `column:${text(item.columnKey) || text(item.field)}`, label: text(item.label) || text(item.field),
        kind: ['orgCode', 'orgName', 'cityCode', 'cityName'].includes(text(item.columnKey)) ? 'dimension' : 'metric',
        required: false, unitKinds: unitsFor(item.unit) }));
  }
  return [];
}

function catalogEntryByCode(catalog, code) {
  return (Array.isArray(catalog) ? catalog : []).find(item => text(item?.metricCode) === text(code));
}

function catalogEntryForColumn(catalog, column) {
  const value = text(column);
  if (!value) return [];
  return (Array.isArray(catalog) ? catalog : []).filter(item => text(item?.outputColumn) === value || text(item?.metricName) === value);
}

/** 将无 fieldMeta 的旧 WIDE_TABLE metrics 快照与当前目录 outputColumn 合并为可选字段。 */
export function buildDatasourceFieldOptions(datasource = {}, catalog = []) {
  const config = parseJson(datasource.configJson, {});
  const options = [];
  const seen = new Set();
  const add = item => {
    const col = text(item?.col || item?.outputColumn || item?.metricName || item?.metricCode);
    if (!col || seen.has(col)) return;
    seen.add(col);
    options.push({
      ...item,
      col,
      label: text(item?.label || item?.alias || item?.metricName || col),
      role: upper(item?.role || 'METRIC'),
      unit: text(item?.unit) || null
    });
  };
  for (const item of Array.isArray(config.fieldMeta) ? config.fieldMeta : []) add(item);
  for (const item of Array.isArray(config.metrics) ? config.metrics : []) {
    const catalogRows = catalogEntryByCode(catalog, item?.metricCode)
      ? [catalogEntryByCode(catalog, item.metricCode)] : catalogEntryForColumn(catalog, item?.metricName);
    if (catalogRows.length === 1) {
      const metric = catalogRows[0];
      add({ ...item, ...metric, col: metric.outputColumn || metric.metricName, metricCode: metric.metricCode });
    } else add(item);
  }
  return options;
}

/** 只有目录唯一命中时才回填 code；历史手工列留给用户继续查看/编辑。 */
export function resolveInitialCatalogAssignments({ fields = {}, units = {}, fieldSpecs = [], catalog = [], datasource } = {}) {
  const options = buildDatasourceFieldOptions(datasource, catalog);
  const sourceConfig = parseJson(datasource?.configJson, {});
  const sourceKind = upper(datasource?.sourceKind || datasource?.source_kind);
  const sourceMetrics = Array.isArray(sourceConfig.metrics) ? sourceConfig.metrics : [];
  const sourceFieldMeta = Array.isArray(sourceConfig.fieldMeta) ? sourceConfig.fieldMeta : [];
  const sourceCanProveMetric = metric => {
    if (!datasource) return true;
    if (sourceKind !== 'WIDE_TABLE') return false;
    return sourceMetrics.some(item => text(item?.metricCode) === text(metric?.metricCode)
      || text(item?.metricName) === text(metric?.metricName))
      || sourceFieldMeta.some(item => text(item?.col) === text(metric?.outputColumn || metric?.metricName)
        && upper(item?.role || 'METRIC') === 'METRIC');
  };
  const assignments = {};
  for (const spec of Array.isArray(fieldSpecs) ? fieldSpecs : []) {
    if (spec?.kind === 'dimension') continue;
    const column = text(fields[spec.semantic]);
    const matches = (Array.isArray(catalog) ? catalog : []).filter(item =>
      text(item?.outputColumn) === column || text(item?.metricName) === column
    );
    if (matches.length !== 1 || !sourceCanProveMetric(matches[0])) continue;
    const option = options.find(item => item.col === column);
    const unit = text(units[spec.semantic]) || text(matches[0].unit) || text(option?.unit);
    const allowed = !spec.unitKinds?.length || !unitKind(unit) || spec.unitKinds.includes(unit)
      || spec.unitKinds.some(value => unitKind(value) === unitKind(unit));
    if (allowed) assignments[spec.semantic] = { metricCode: matches[0].metricCode, unit };
  }
  return assignments;
}

/** 从当前组件内容读取回显字段；不会把共享 slot 的 fields.value 当成卡片字段。 */
export function buildSchemaInitialBinding({ entry, component, binding = {} } = {}) {
  const fields = {};
  const units = {};
  const kind = text(entry?.kind);
  const type = upper(component?.componentType);
  const put = (semantic, field, unit) => {
    if (text(field)) fields[semantic] = text(field);
    if (text(unit)) units[semantic] = upper(unit);
  };
  if (kind === 'BUSINESS_GROWTH') {
    const series = Array.isArray(entry?.effectiveSeries) ? entry.effectiveSeries
      : Array.isArray(component?.content?.series) ? component.content.series : [];
    for (const item of series) put(item.seriesKey, item.field, item.unit);
    if (binding.fields?.date) put('date', binding.fields.date, binding.units?.date);
    return { fields, units };
  }
  if (kind === 'COMPOSITION_TAB') {
    const tab = tabFor(entry, component);
    // tab.unit is a display target; query units come from the saved binding or
    // directory metadata and must not be rewritten from the visual scale.
    const unitForField = (field, preferredSemantic) => {
      if (text(binding.fields?.[preferredSemantic]) === text(field)) return binding.units?.[preferredSemantic];
      const semantic = Object.keys(binding.fields || {}).find(key => text(binding.fields[key]) === text(field));
      return semantic ? binding.units?.[semantic] : '';
    };
    put('corporate', tab.corporateField, unitForField(tab.corporateField, 'corporate'));
    put('retail', tab.retailField, unitForField(tab.retailField, 'retail'));
    put('total', tab.totalField, unitForField(tab.totalField, 'total'));
    return { fields, units };
  }
  if (kind === 'INSTITUTION_RANKING' || type === 'RANKING') {
    const metrics = Array.isArray(component?.content?.rankingMetrics) ? component.content.rankingMetrics : [];
    for (const item of metrics) {
      const semantic = text(item.metricKey) || 'value';
      const sourceSemantic = semantic === 'value' ? 'value' : semantic;
      put(sourceSemantic, binding.fields?.[sourceSemantic] || item.field, binding.units?.[sourceSemantic] || item.unit);
    }
    if (binding.fields?.orgCode) put('orgCode', binding.fields.orgCode, binding.units?.orgCode);
    if (binding.fields?.name) put('name', binding.fields.name, binding.units?.name);
    return { fields, units };
  }
  if (type === 'METRIC_CARD' || type === 'COMPLETION') {
    put('value', component?.content?.mainField, componentUnit(component));
    for (const item of Array.isArray(component?.content?.subFields) ? component.content.subFields : []) {
      const field = text(item?.field || item?.key);
      if (field) put(`sub:${field}`, field, item?.unit || componentUnit(component));
    }
    return { fields, units };
  }
  if (type === 'TREND') {
    for (const item of Array.isArray(component?.content?.series) ? component.content.series : []) {
      put(item.seriesKey || item.field, item.field, item.unit);
    }
    if (binding.fields?.date) put('date', binding.fields.date, binding.units?.date);
  }
  if (type === 'COMPOSITION_TABS') {
    for (const tab of Array.isArray(component?.content?.tabs) ? component.content.tabs : []) {
      for (const field of ['corporateField', 'retailField', 'totalField', 'nameField', 'valueField']) {
        if (text(tab[field])) put(`tab:${text(tab.tabKey)}:${field}`, tab[field], tab.unit);
      }
    }
  }
  if (type === 'DETAIL_TABLE') {
    for (const column of Array.isArray(component?.content?.columns) ? component.content.columns : []) {
      if (text(column?.field)) put(`column:${text(column.columnKey) || text(column.field)}`, column.field, column.unit);
    }
  }
  return { fields, units };
}

function stable(value) {
  if (Array.isArray(value)) return `[${value.map(stable).join(',')}]`;
  if (object(value)) return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${stable(value[key])}`).join(',')}}`;
  return JSON.stringify(value ?? null);
}

/** 克隆兼容宽表来源并合并目录指标，保留未编辑指标和既有查询口径。 */
export function mergeCompatibleDatasourceRequest(source = {}, request = {}) {
  const sourceKind = upper(source.sourceKind || source.source_kind);
  const requestKind = upper(request.sourceKind || request.source_kind);
  const base = parseJson(source.configJson, null);
  const next = parseJson(request.configJson, null);
  if (sourceKind !== 'WIDE_TABLE' || requestKind !== 'WIDE_TABLE' || !base || !next
      || !text(base.table) || base.table !== next.table
      || upper(base.scopeMode || 'GLOBAL') !== upper(next.scopeMode || 'GLOBAL')) {
    return { ok: false, reason: '当前数据源不是可安全合并的同表宽表来源' };
  }
  if (base.aggregation && next.aggregation && stable(base.aggregation) !== stable(next.aggregation)) {
    return { ok: false, reason: '聚合或过滤口径发生变化，已停止覆盖共享来源，请先明确拆分数据源' };
  }
  const metrics = [];
  const byCode = new Map();
  for (const item of [...(Array.isArray(base.metrics) ? base.metrics : []), ...(Array.isArray(next.metrics) ? next.metrics : [])]) {
    const code = text(item?.metricCode);
    if (!code || byCode.has(code)) continue;
    byCode.set(code, item);
    metrics.push(item);
  }
  const fieldMeta = [];
  const byColumn = new Map();
  for (const item of Array.isArray(base.fieldMeta) ? base.fieldMeta : []) {
    const col = text(item?.col || item?.outputColumn);
    if (!col || byColumn.has(col)) continue;
    byColumn.set(col, item);
    fieldMeta.push(item);
  }
  for (const item of Array.isArray(next.fieldMeta) ? next.fieldMeta : []) {
    const col = text(item?.col || item?.outputColumn);
    if (!col) continue;
    const existing = byColumn.get(col);
    if (existing && text(existing.unit) && text(item.unit) && upper(existing.unit) !== upper(item.unit)) {
      return { ok: false, reason: `共享来源字段 ${col} 的单位发生冲突，已停止合并` };
    }
    if (existing) continue;
    byColumn.set(col, item);
    fieldMeta.push(item);
  }
  const merged = { ...base, ...next, metrics, fieldMeta };
  if (base.aggregation) merged.aggregation = base.aggregation;
  return { ok: true, configJson: JSON.stringify(merged), config: merged };
}

export { AMOUNT_UNITS, RATIO_UNITS, COUNT_UNITS, ALL_METRIC_UNITS };
