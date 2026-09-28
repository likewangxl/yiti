const TYPES = new Set(['TREND', 'DETAIL_TABLE']);
const UNIT_KIND = {
  YUAN: 'amount', TEN_THOUSAND: 'amount', HUNDRED_MILLION: 'amount',
  COUNT: 'count', TEN_THOUSAND_COUNT: 'count', PERCENT: 'ratio', RATIO: 'ratio', AUTO: 'auto'
};

const record = value => value && typeof value === 'object' && !Array.isArray(value);
const finite = value => value === null || value === undefined || value === '' || typeof value === 'boolean'
  ? null : Number.isFinite(Number(value)) ? Number(value) : null;
const presentationOf = source => source?.displaySchemaVersion !== undefined ? source
  : source?.canvasStyle?.presentation || source?.renderPackage?.canvasStyle?.presentation || null;

function sourceRows(model, component, ref) {
  const maps = [model?.blockResults, model?.displayValues, model?.results].filter(record);
  for (const map of maps) {
    const found = map[ref?.blockId] ?? map[String(ref?.blockId || '')] ?? map[ref?.metricCode] ?? map[component.componentId];
    if (Array.isArray(found)) return found;
    if (Array.isArray(found?.rows)) return found.rows;
  }
  if (component.componentType === 'TREND') return Array.isArray(model?.trend) ? model.trend : [];
  if (component.componentId === 'branch-composition') return Array.isArray(model?.composition) ? model.composition : [];
  return Array.isArray(model?.items) ? model.items : Array.isArray(model?.rankings) ? model.rankings : [];
}

function authorizedDetailRows(rows, model, ref) {
  if (String(ref?.dimension || '').toUpperCase() !== 'ORG') return rows;
  const directory = Array.isArray(model?.institutions) ? model.institutions
    : Array.isArray(model?.authorizedDirectory) ? model.authorizedDirectory : [];
  if (!directory.length) return rows;
  const hasOrgIdentity = rows.some(row => record(row)
    && String(row.orgCode ?? row.org_code ?? '').trim());
  if (!hasOrgIdentity) return rows;
  const allowed = new Set(directory.map(item => String(item?.orgCode ?? item?.org_code ?? '').trim()).filter(Boolean));
  return rows.filter(row => allowed.has(String(row?.orgCode ?? row?.org_code ?? '').trim()));
}

function emptyTextOf(value) {
  return typeof value === 'string' && value.trim() ? value : '—';
}

function displayAutoValue(value, emptyText) {
  if (value === null || value === undefined || (typeof value === 'string' && !value.trim())) return emptyText;
  if (typeof value === 'string') return value;
  if (typeof value === 'number') return Number.isFinite(value) ? String(value) : emptyText;
  if (typeof value === 'bigint' || typeof value === 'boolean') return String(value);
  return emptyText;
}

function displayValue(value, unit, decimals = 2, configuredEmptyText = '—') {
  const emptyText = emptyTextOf(configuredEmptyText);
  if (unit === 'AUTO') return displayAutoValue(value, emptyText);
  const number = finite(value);
  if (number === null) return emptyText;
  const normalized = unit === 'RATIO' ? number * 100 : number;
  const label = ({ YUAN: '元', TEN_THOUSAND: '万元', HUNDRED_MILLION: '亿元', COUNT: '个', TEN_THOUSAND_COUNT: '万户', PERCENT: '%', RATIO: '%' })[unit] || '';
  return `${new Intl.NumberFormat('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals }).format(normalized)}${label}`;
}

function trendModel(component, rows) {
  const issues = [];
  const seenDates = new Set();
  const normalizedRows = [];
  for (const row of rows.filter(record)) {
    const date = String(row.date ?? row.dataDate ?? row.data_date ?? row.label ?? '').trim();
    if (!date) { issues.push('趋势行缺少日期'); continue; }
    if (seenDates.has(date)) { issues.push(`趋势日期重复: ${date}`); continue; }
    seenDates.add(date);
    normalizedRows.push(row);
  }
  const series = (component.content?.series || []).map((item, index) => ({
    key: item.field || item.seriesKey || `series-${index}`,
    seriesKey: item.seriesKey || item.field || `series-${index}`,
    field: item.field,
    label: item.label || item.field,
    unit: item.unit,
    values: normalizedRows.map(row => finite(row[item.field]))
  }));
  const kinds = new Set(series.map(item => UNIT_KIND[item.unit]).filter(kind => kind && kind !== 'auto'));
  if (kinds.size > 1) issues.push('趋势系列单位类型不兼容，不能共用同一坐标轴');
  if (!normalizedRows.length) issues.push('暂无趋势日期数据');
  return {
    componentId: component.componentId, componentType: 'TREND', title: component.text?.title || series[0]?.label || '趋势',
    subtitle: component.text?.subtitle || '', rows: normalizedRows, dates: normalizedRows.map(row => String(row.date ?? row.dataDate ?? row.data_date ?? row.label)),
    series, issues, state: issues.length ? 'INVALID' : 'READY'
  };
}

function tableModel(component, rows) {
  const columns = (component.content?.columns || []).filter(column => column.visible !== false).map(column => ({ ...column }));
  const normalizedRows = rows.filter(record).map((row, sourceIndex) => ({
    key: String(row.orgCode ?? row.id ?? row.code ?? sourceIndex), sourceIndex,
    cells: columns.map(column => ({ key: column.columnKey, value: row[column.field], text: displayValue(
      row[column.field], column.unit, component.format?.decimals ?? 2, component.format?.emptyText
    ) }))
  }));
  return {
    componentId: component.componentId, componentType: 'DETAIL_TABLE', title: component.text?.title || '明细',
    subtitle: component.text?.subtitle || '', columns, rows: normalizedRows,
    issues: columns.length ? [] : ['没有可见明细列'], state: columns.length ? 'READY' : 'INVALID'
  };
}

export function buildDisplaySeriesTableModel(source, model = {}) {
  const presentation = presentationOf(source);
  if (!presentation || presentation.displaySchemaVersion !== 1) return { enabled: false, components: [], hasTrend: false, hasTable: false };
  const components = (presentation.display?.components || [])
    .map((component, index) => ({ component, index }))
    .filter(({ component }) => TYPES.has(component?.componentType) && component.visible !== false)
    .sort((a, b) => (a.component.order ?? a.index) - (b.component.order ?? b.index) || a.index - b.index)
    .map(({ component }) => {
      const ref = component.dataRefs?.[0] || {};
      const source = sourceRows(model, component, ref);
      const rows = component.componentType === 'DETAIL_TABLE'
        ? authorizedDetailRows(source, model, ref) : source;
      return component.componentType === 'TREND' ? trendModel(component, rows) : tableModel(component, rows);
    });
  return { enabled: true, components, hasTrend: components.some(item => item.componentType === 'TREND'), hasTable: components.some(item => item.componentType === 'DETAIL_TABLE') };
}

export { displayValue };
