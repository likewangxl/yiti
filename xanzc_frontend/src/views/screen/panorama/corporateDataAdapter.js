import {
  CORPORATE_BINDING_SLOTS,
  CORPORATE_SLOT_ORDER,
  normalizeCorporateBinding
} from './corporateBindings.js';

const SINGLE_SLOTS = Object.freeze([
  'corpDeposit', 'corpDepositAverage', 'corpLoan', 'corpRevenue', 'corpCustomers', 'corpNplRate'
]);

const DISPLAY_UNIT_LABELS = Object.freeze({
  YUAN: '亿元',
  TEN_THOUSAND: '亿元',
  HUNDRED_MILLION: '亿元',
  COUNT: '个',
  TEN_THOUSAND_COUNT: '万户',
  PERCENT: '%',
  RATIO: '%'
});

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function pick(value, ...keys) {
  if (!isObject(value)) return undefined;
  for (const key of keys) {
    if (value[key] !== undefined && value[key] !== null) return value[key];
  }
  return undefined;
}

function asText(value) {
  return value === undefined || value === null ? '' : String(value);
}

function emptyValue(value) {
  if (value === undefined || value === null) return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  return value;
}

function numeric(value) {
  if (value === undefined || value === null) return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  if (typeof value === 'string') {
    const parsed = Number(value.trim());
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function applyCorporatePresentation(model, options = {}) {
  if (!options || Object.keys(options).length === 0) return model;
  const view = options?.view || {};
  const title = options?.title || view.screenName || view.screen_name;
  if (title) model.title = String(title);
  const scopeLabel = options?.scopeLabel || view.scopeLabel || view.scope_label || '当前大屏授权范围';
  model.scopeLabel = String(scopeLabel);
  return model;
}

export function createEmptyCorporateModel(options = {}) {
  return applyCorporatePresentation({
    title: '对公经营总览',
    dataDate: '',
    kpis: [],
    trend: [],
    segments: [],
    rankings: [],
    attention: [],
    targets: [],
    institutions: [],
    issues: []
  }, options);
}

function issue(list, slot, code, message, fieldName = '') {
  const key = `${slot}|${code}|${fieldName}`;
  if (list.some(item => `${item.slot}|${item.code}|${item.field || ''}` === key)) return;
  list.push({ slot, code, field: fieldName || undefined, message: message || `${slot}: ${code}` });
}

function unwrapResponse(response) {
  if (!isObject(response)) return response;
  if (response.columns === undefined && response.rows === undefined && isObject(response.data)) return response.data;
  return response;
}

/** 运行时只接受保存查询契约返回的 columns + 二维 rows。 */
function parseTableResponse(response, slot, issues) {
  const table = unwrapResponse(response);
  if (table === null || table === undefined) {
    issue(issues, slot, 'NULL_RESPONSE', '数据源返回为空');
    return null;
  }
  if (!Array.isArray(table.columns) || !Array.isArray(table.rows)) {
    issue(issues, slot, 'INVALID_SHAPE', '数据响应必须包含 columns 数组和 rows 二维数组');
    return null;
  }
  const columns = table.columns.map(column => {
    if (typeof column === 'string') return column.trim();
    if (isObject(column)) return asText(column.col || column.name || column.key).trim();
    return asText(column).trim();
  });
  if (columns.some(column => !column)) {
    issue(issues, slot, 'INVALID_COLUMNS', '数据响应存在空列名');
    return null;
  }
  if (new Set(columns).size !== columns.length) {
    issue(issues, slot, 'DUPLICATE_COLUMNS', '数据响应存在重复列名');
    return null;
  }
  const sourceRows = table.rows;
  const rows = [];
  for (const rawRow of sourceRows) {
    if (!Array.isArray(rawRow)) {
      issue(issues, slot, 'INVALID_ROWS', '数据响应 rows 必须是二维数组');
      continue;
    }
    const row = {};
    columns.forEach((column, index) => { row[column] = rawRow[index] === undefined ? null : rawRow[index]; });
    rows.push(row);
  }
  return { ...table, columns, rows, rawRowCount: sourceRows.length };
}

function responseDataDate(table) {
  const value = emptyValue(pick(table, 'dataDate', 'data_date', 'date'));
  return value === null ? '' : String(value);
}

function fieldColumn(binding, semantic) {
  const column = binding?.fields?.[semantic];
  return column === undefined || column === null || String(column).trim() === '' ? '' : String(column).trim();
}

function readDimension(row, binding, semantic, slot, issues, required = false) {
  const column = fieldColumn(binding, semantic);
  if (!column) {
    if (required) issue(issues, slot, 'MISSING_FIELD', `缺少字段映射: ${semantic}`, semantic);
    return null;
  }
  if (!Object.prototype.hasOwnProperty.call(row, column)) {
    issue(issues, slot, 'MISSING_COLUMN', `响应缺少列: ${column}`, semantic);
    return null;
  }
  return emptyValue(row[column]);
}

function metricKind(slot, semantic, binding) {
  if (slot === 'corpAttention' && semantic === 'count') return 'rawCount';
  if (slot === 'corpCustomers' && semantic === 'value') return 'customerCount';
  if (slot === 'corpSegments' && semantic === 'customers') return 'customerCount';
  if (slot === 'corpTargets' && ['actual', 'target'].includes(semantic)
      && ['COUNT', 'TEN_THOUSAND_COUNT'].includes(binding?.units?.[semantic])) return 'customerCount';
  if (semantic === 'change' || semantic === 'rate' || semantic === 'nplRate'
      || (slot === 'corpNplRate' && semantic === 'value')) return 'ratio';
  return 'amount';
}

function displayUnit(unit, kind = 'amount') {
  if (kind === 'customerCount') return '万户';
  if (kind === 'rawCount') return '个';
  if (kind === 'ratio') return '%';
  return DISPLAY_UNIT_LABELS[unit] || null;
}

function convertMetric(rawValue, unit, kind, slot, semantic, issues) {
  if (!unit) {
    issue(issues, slot, 'UNKNOWN_UNIT', `字段 ${semantic} 缺少显式单位`, semantic);
    return null;
  }
  const raw = emptyValue(rawValue);
  if (raw === null) return null;
  const value = numeric(raw);
  if (value === null) {
    issue(issues, slot, 'INVALID_NUMBER', `字段 ${semantic} 不是有限数值`, semantic);
    return null;
  }
  if (kind === 'ratio') {
    if (unit === 'PERCENT') return value;
    if (unit === 'RATIO') return value * 100;
    issue(issues, slot, 'UNIT_MISMATCH', `字段 ${semantic} 需要 PERCENT 或 RATIO`, semantic);
    return null;
  }
  if (kind === 'customerCount') {
    if (unit === 'COUNT') return value / 10000;
    if (unit === 'TEN_THOUSAND_COUNT') return value;
    issue(issues, slot, 'UNIT_MISMATCH', `字段 ${semantic} 需要 COUNT 或 TEN_THOUSAND_COUNT`, semantic);
    return null;
  }
  if (kind === 'rawCount') {
    if (unit === 'COUNT') return value;
    issue(issues, slot, 'UNIT_MISMATCH', `字段 ${semantic} 需要 COUNT`, semantic);
    return null;
  }
  if (unit === 'YUAN') return value / 100000000;
  if (unit === 'TEN_THOUSAND') return value / 10000;
  if (unit === 'HUNDRED_MILLION') return value;
  issue(issues, slot, 'UNIT_MISMATCH', `字段 ${semantic} 需要金额单位`, semantic);
  return null;
}

function readMetric(row, binding, semantic, slot, issues, required = false) {
  const column = fieldColumn(binding, semantic);
  if (!column) {
    if (required) issue(issues, slot, 'MISSING_FIELD', `缺少字段映射: ${semantic}`, semantic);
    return { value: null, unit: null };
  }
  if (!Object.prototype.hasOwnProperty.call(row, column)) {
    issue(issues, slot, 'MISSING_COLUMN', `响应缺少列: ${column}`, semantic);
    const kind = metricKind(slot, semantic, binding);
    return { value: null, unit: displayUnit(binding?.units?.[semantic], kind) };
  }
  const unit = binding?.units?.[semantic] || null;
  const kind = metricKind(slot, semantic, binding);
  const converted = convertMetric(row[column], unit, kind, slot, semantic, issues);
  if (converted !== null && !Number.isFinite(converted)) {
    issue(issues, slot, 'INVALID_NUMBER', `字段 ${semantic} 换算结果不是有限数值`, semantic);
  }
  return { value: converted !== null && Number.isFinite(converted) ? converted : null, unit: displayUnit(unit, kind) };
}

function canonicalKpi(slot, item = {}) {
  return {
    key: slot,
    label: CORPORATE_BINDING_SLOTS[slot]?.label || slot,
    value: item.value ?? null,
    unit: item.unit ?? null,
    change: item.change ?? null,
    ...(Object.prototype.hasOwnProperty.call(item, 'date') ? { date: item.date } : {})
  };
}

function adaptSingle(slot, table, binding, model, issues) {
  if (!table || table.rawRowCount !== 1 || table.rows.length !== 1) {
    issue(issues, slot, table?.rawRowCount ? 'INVALID_ROW_COUNT' : 'NO_ROWS',
      table?.rawRowCount ? '单值数据必须恰好一行' : '数据响应没有数据行');
    model.kpis.push(canonicalKpi(slot));
    return;
  }
  const row = table.rows[0];
  const item = readMetric(row, binding, 'value', slot, issues, true);
  const change = fieldColumn(binding, 'change') ? readMetric(row, binding, 'change', slot, issues).value : null;
  const dateColumn = fieldColumn(binding, 'date');
  const date = dateColumn ? readDimension(row, binding, 'date', slot, issues) : undefined;
  model.kpis.push(canonicalKpi(slot, {
    ...item,
    change,
    ...(dateColumn ? { date: date === null ? null : String(date) } : {})
  }));
}

function rowDimensionKey(row, binding, semantic) {
  const column = fieldColumn(binding, semantic);
  if (!column || !Object.prototype.hasOwnProperty.call(row, column)) return null;
  const value = emptyValue(row[column]);
  return value === null ? null : String(value).trim();
}

function adaptTrend(table, binding, model, issues) {
  if (!table?.rows?.length) {
    issue(issues, 'corpTrend', 'NO_ROWS', '数据响应没有趋势行');
    return;
  }
  if (!fieldColumn(binding, 'deposit') && !fieldColumn(binding, 'loan')) {
    issue(issues, 'corpTrend', 'MISSING_METRIC', '趋势至少需要 deposit 或 loan');
    return;
  }
  const dateCounts = new Map();
  for (const row of table.rows) {
    const key = rowDimensionKey(row, binding, 'date');
    if (key !== null) dateCounts.set(key, (dateCounts.get(key) || 0) + 1);
  }
  const duplicateDates = new Set([...dateCounts.entries()].filter(([, count]) => count > 1).map(([key]) => key));
  const seenDates = new Set();
  for (const row of table.rows) {
    const date = readDimension(row, binding, 'date', 'corpTrend', issues, true);
    if (date === null) {
      issue(issues, 'corpTrend', 'MISSING_DIMENSION', '趋势行缺少日期，已跳过', 'date');
      continue;
    }
    const dateKey = String(date);
    if (duplicateDates.has(dateKey) || seenDates.has(dateKey)) {
      issue(issues, 'corpTrend', 'DUPLICATE_DATE', '趋势存在重复日期，已排除重复日期行', 'date');
      continue;
    }
    seenDates.add(dateKey);
    const deposit = fieldColumn(binding, 'deposit')
      ? readMetric(row, binding, 'deposit', 'corpTrend', issues).value : null;
    const loan = fieldColumn(binding, 'loan')
      ? readMetric(row, binding, 'loan', 'corpTrend', issues).value : null;
    model.trend.push({ date, deposit, loan });
  }
}

function adaptSegments(table, binding, model, issues) {
  if (!table?.rows?.length) {
    issue(issues, 'corpSegments', 'NO_ROWS', '数据响应没有重点客群行');
    return;
  }
  for (const row of table.rows) {
    const name = readDimension(row, binding, 'name', 'corpSegments', issues, true);
    const customers = readMetric(row, binding, 'customers', 'corpSegments', issues, true).value;
    const loan = readMetric(row, binding, 'loan', 'corpSegments', issues, true).value;
    model.segments.push({ name, customers, loan });
  }
}

function directoryFrom(options = {}) {
  const source = options?.panoramaInstitutions
    || options?.view?.panoramaInstitutions
    || options?.view?.panorama_institutions
    || [];
  if (!Array.isArray(source)) return [];
  return source.map(item => ({
    orgCode: asText(pick(item, 'orgCode', 'org_code')).trim(),
    orgName: emptyValue(pick(item, 'orgName', 'org_name')),
    cityCode: emptyValue(pick(item, 'cityCode', 'city_code')),
    cityName: emptyValue(pick(item, 'cityName', 'city_name')),
    lng: numeric(pick(item, 'lng', 'longitude')),
    lat: numeric(pick(item, 'lat', 'latitude')),
    coordSys: emptyValue(pick(item, 'coordSys', 'coord_sys')),
    located: booleanFlag(pick(item, 'located'))
  })).filter(item => item.orgCode);
}

function booleanFlag(value) {
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return Number.isFinite(value) && value !== 0;
  return ['true', '1', 'yes', 'y'].includes(String(value ?? '').trim().toLowerCase());
}

function normalizedCoordSys(value) {
  return String(value ?? '').trim().toUpperCase().replace(/[\s_-]/g, '');
}

function validCoordinate(lng, lat, coordSys) {
  return Number.isFinite(lng) && Number.isFinite(lat)
    && lng >= -180 && lng <= 180 && lat >= -90 && lat <= 90
    && normalizedCoordSys(coordSys) === 'GCJ02';
}

function normalizeInstitutionIdentity(raw = {}) {
  const lng = numeric(pick(raw, 'lng', 'longitude'));
  const lat = numeric(pick(raw, 'lat', 'latitude'));
  const coordSys = emptyValue(pick(raw, 'coordSys', 'coord_sys'));
  const coordinateOk = validCoordinate(lng, lat, coordSys);
  return {
    orgCode: asText(pick(raw, 'orgCode', 'org_code')).trim(),
    orgName: emptyValue(pick(raw, 'orgName', 'org_name')),
    cityCode: emptyValue(pick(raw, 'cityCode', 'city_code')),
    cityName: emptyValue(pick(raw, 'cityName', 'city_name')),
    ownerOperatingOrgCode: emptyValue(pick(raw, 'ownerOperatingOrgCode', 'owner_operating_org_code')),
    parentOrgCode: emptyValue(pick(raw, 'parentOrgCode', 'parent_org_code')),
    lng: coordinateOk ? lng : null,
    lat: coordinateOk ? lat : null,
    coordSys,
    located: booleanFlag(pick(raw, 'located')) && coordinateOk
  };
}

function scopeMode(options = {}) {
  return String(options?.orgScopeMode || options?.org_scope_mode
    || options?.view?.orgScopeMode || options?.view?.org_scope_mode || '').toUpperCase();
}

function adaptRanking(table, binding, model, issues, options) {
  if (!table?.rows?.length) {
    issue(issues, 'corpRanking', 'NO_ROWS', '数据响应没有排名行');
    return;
  }
  const directory = directoryFrom(options);
  const namedGroup = scopeMode(options) === 'NAMED_GROUP';
  if (namedGroup && !directory.length) {
    issue(issues, 'corpRanking', 'NO_AUTHORIZED_DIRECTORY', '命名机构组未返回授权机构目录');
    return;
  }
  const directoryByCode = new Map(directory.map(item => [item.orgCode, item]));
  const counts = new Map();
  for (const row of table.rows) {
    const key = rowDimensionKey(row, binding, 'orgCode');
    if (key !== null) counts.set(key, (counts.get(key) || 0) + 1);
  }
  const duplicateOrgs = new Set([...counts.entries()].filter(([, count]) => count > 1).map(([key]) => key));
  const seen = new Set();
  for (const row of table.rows) {
    const orgCode = readDimension(row, binding, 'orgCode', 'corpRanking', issues, true);
    const code = orgCode === null ? '' : String(orgCode).trim();
    if (!code) {
      issue(issues, 'corpRanking', 'MISSING_DIMENSION', '排名行缺少机构号，已跳过', 'orgCode');
      continue;
    }
    if (namedGroup && !directoryByCode.has(code)) {
      issue(issues, 'corpRanking', 'UNAUTHORIZED_ORG', '来源包含授权目录外机构，已排除', 'orgCode');
      continue;
    }
    if (duplicateOrgs.has(code) || seen.has(code)) {
      issue(issues, 'corpRanking', 'DUPLICATE_ORG', '排名存在重复机构，已排除重复机构行', 'orgCode');
      continue;
    }
    seen.add(code);
    const name = readDimension(row, binding, 'name', 'corpRanking', issues, true);
    const deposit = readMetric(row, binding, 'deposit', 'corpRanking', issues, true).value;
    const increase = fieldColumn(binding, 'increase')
      ? readMetric(row, binding, 'increase', 'corpRanking', issues).value : null;
    const rate = fieldColumn(binding, 'rate')
      ? readMetric(row, binding, 'rate', 'corpRanking', issues).value : null;
    const nplRate = fieldColumn(binding, 'nplRate')
      ? readMetric(row, binding, 'nplRate', 'corpRanking', issues).value : null;
    const cityCode = namedGroup ? (directoryByCode.get(code)?.cityCode ?? null) : null;
    model.rankings.push({ orgCode: code, name, deposit, increase, rate, nplRate, cityCode });
  }
}

function adaptAttention(table, binding, model, issues) {
  if (!table?.rows?.length) {
    issue(issues, 'corpAttention', 'NO_ROWS', '数据响应没有关注事项');
    return;
  }
  for (const row of table.rows) {
    const label = readDimension(row, binding, 'label', 'corpAttention', issues, true);
    const count = readMetric(row, binding, 'count', 'corpAttention', issues, true).value;
    const owner = fieldColumn(binding, 'owner') ? readDimension(row, binding, 'owner', 'corpAttention', issues) : null;
    const deadline = fieldColumn(binding, 'deadline') ? readDimension(row, binding, 'deadline', 'corpAttention', issues) : null;
    model.attention.push({ label, count, owner, deadline });
  }
}

function adaptTargets(table, binding, model, issues) {
  if (!table?.rows?.length) {
    issue(issues, 'corpTargets', 'NO_ROWS', '数据响应没有目标行');
    return;
  }
  for (const row of table.rows) {
    const name = readDimension(row, binding, 'name', 'corpTargets', issues, true);
    const actual = readMetric(row, binding, 'actual', 'corpTargets', issues, true).value;
    const target = readMetric(row, binding, 'target', 'corpTargets', issues, true).value;
    model.targets.push({ name, actual, target });
  }
}

const IDENTITY_FIELDS = Object.freeze([
  ['orgName', 'orgName'], ['cityCode', 'cityCode'], ['cityName', 'cityName'],
  ['ownerOperatingOrgCode', 'ownerOperatingOrgCode'], ['parentOrgCode', 'parentOrgCode'],
  ['lng', 'lng'], ['lat', 'lat'], ['coordSys', 'coordSys'], ['located', 'located']
]);

function adaptBranches(table, binding, model, issues, options) {
  const directory = directoryFrom(options);
  const namedGroup = scopeMode(options) === 'NAMED_GROUP';
  if (namedGroup && !directory.length) {
    issue(issues, 'branches', 'NO_AUTHORIZED_DIRECTORY', '命名机构组未返回授权机构目录');
    return;
  }
  const rows = table?.rows || [];
  if (!rows.length && !directory.length) {
    issue(issues, 'branches', 'NO_ROWS', '数据响应没有机构行');
    return;
  }
  const sourceByCode = new Map();
  for (const row of rows) {
    const code = readDimension(row, binding, 'orgCode', 'branches', issues, true);
    if (code !== null && String(code).trim()) sourceByCode.set(String(code).trim(), row);
  }
  const codes = directory.length ? directory.map(item => item.orgCode) : [...sourceByCode.keys()];
  const seen = new Set();
  for (const code of codes) {
    if (seen.has(code)) continue;
    seen.add(code);
    const row = sourceByCode.get(code);
    const directoryItem = directory.find(item => item.orgCode === code);
    const rawIdentity = directoryItem || {
      orgCode: code,
      ...Object.fromEntries(IDENTITY_FIELDS.map(([name, semantic]) => [
        name, row && fieldColumn(binding, semantic)
          ? readDimension(row, binding, semantic, 'branches', issues) : null
      ]))
    };
    // 授权目录身份优先；不从业务查询行回填目录空坐标或城市。
    model.institutions.push(normalizeInstitutionIdentity(rawIdentity));
  }
}

function adaptSlot(slot, table, binding, model, issues, options) {
  if (SINGLE_SLOTS.includes(slot)) return adaptSingle(slot, table, binding, model, issues);
  switch (slot) {
    case 'corpTrend': return adaptTrend(table, binding, model, issues);
    case 'corpSegments': return adaptSegments(table, binding, model, issues);
    case 'corpRanking': return adaptRanking(table, binding, model, issues, options);
    case 'corpAttention': return adaptAttention(table, binding, model, issues);
    case 'corpTargets': return adaptTargets(table, binding, model, issues);
    case 'branches': return adaptBranches(table, binding, model, issues, options);
    default: return undefined;
  }
}

/**
 * 将对公模板发布包的独立槽位响应适配为稳定的经营总览模型。
 * 旧零售槽位和旧分行业务指标都会被忽略，唯一共享入口是 branches 身份槽。
 */
export function adaptCorporateResults(results = {}, options = {}) {
  const model = createEmptyCorporateModel(options);
  const issues = [];
  let knownDate = '';
  for (const slot of CORPORATE_SLOT_ORDER) {
    const result = results?.[slot];
    if (!result) continue;
    const binding = normalizeCorporateBinding(result.binding || {}, slot);
    const table = parseTableResponse(result.response, slot, issues);
    if (!table) {
      if (SINGLE_SLOTS.includes(slot)) model.kpis.push(canonicalKpi(slot));
      continue;
    }
    adaptSlot(slot, table, binding, model, issues, options);
    const kpi = SINGLE_SLOTS.includes(slot) ? model.kpis.at(-1) : null;
    const date = kpi?.date || responseDataDate(table);
    if (date) {
      if (!knownDate) knownDate = String(date);
      else if (knownDate !== String(date)) issue(issues, slot, 'MIXED_DATES', '对公槽位日期不一致，需核对统计期间', 'date');
      if (!model.dataDate) model.dataDate = String(date);
    }
  }
  if (!model.institutions.length) {
    for (const item of directoryFrom(options)) model.institutions.push(normalizeInstitutionIdentity(item));
  }
  model.issues = issues;
  return model;
}

export { DISPLAY_UNIT_LABELS };
