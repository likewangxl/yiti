import {
  RETAIL_BINDING_SLOTS,
  RETAIL_SLOT_ORDER,
  normalizeRetailBinding
} from './retailBindings';

const SINGLE_SLOTS = Object.freeze([
  'retailAum', 'retailDeposit', 'retailDepositAverage', 'retailRevenue',
  'retailValueCustomers', 'retailLoan', 'retailNplRate'
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

function applyRetailPresentation(model, options = {}) {
  if (!options || Object.keys(options).length === 0) return model;
  const view = options?.view || {};
  const title = options?.title || view.screenName || view.screen_name;
  if (title) model.title = String(title);
  const scopeLabel = options?.scopeLabel || view.scopeLabel || view.scope_label || '当前大屏授权范围';
  model.scopeLabel = String(scopeLabel);
  return model;
}

export function createEmptyRetailModel(options = {}) {
  return applyRetailPresentation({
    title: '零售经营总览',
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
  if (response.columns === undefined && response.rows === undefined && isObject(response.data)) {
    return response.data;
  }
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
  return {
    ...table,
    columns,
    rows,
    rawRowCount: sourceRows.length
  };
}

function responseDataDate(table) {
  const date = pick(table, 'dataDate', 'data_date', 'date');
  const value = emptyValue(date);
  return value === null ? '' : String(value);
}

function fieldColumn(binding, semantic) {
  const column = binding?.fields?.[semantic];
  return column === undefined || column === null || String(column).trim() === ''
    ? '' : String(column).trim();
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

function metricKind(slot, semantic) {
  if (slot === 'retailAttention' && semantic === 'count') return 'rawCount';
  if (slot === 'retailValueCustomers' && semantic === 'value') return 'customerCount';
  if (slot === 'retailSegments' && semantic === 'customers') return 'customerCount';
  if (semantic === 'change' || semantic === 'rate' || semantic === 'nplRate'
      || (slot === 'retailNplRate' && semantic === 'value')) return 'ratio';
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
    const kind = metricKind(slot, semantic);
    return { value: null, unit: displayUnit(binding?.units?.[semantic], kind) };
  }
  const unit = binding?.units?.[semantic] || null;
  const kind = metricKind(slot, semantic);
  const converted = convertMetric(row[column], unit, kind, slot, semantic, issues);
  if (converted !== null && !Number.isFinite(converted)) {
    issue(issues, slot, 'INVALID_NUMBER', `字段 ${semantic} 换算结果不是有限数值`, semantic);
  }
  return {
    value: converted !== null && Number.isFinite(converted) ? converted : null,
    unit: displayUnit(unit, kind)
  };
}

function canonicalKpi(slot, item = {}) {
  const result = {
    key: slot,
    label: RETAIL_BINDING_SLOTS[slot]?.label || slot,
    value: item.value ?? null,
    unit: item.unit ?? null,
    change: item.change ?? null
  };
  if (Object.prototype.hasOwnProperty.call(item, 'date')) result.date = item.date;
  return result;
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
  const change = fieldColumn(binding, 'change')
    ? readMetric(row, binding, 'change', slot, issues).value : null;
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
    issue(issues, 'retailTrend', 'NO_ROWS', '数据响应没有趋势行');
    return;
  }
  if (!fieldColumn(binding, 'aum') && !fieldColumn(binding, 'deposit')) {
    issue(issues, 'retailTrend', 'MISSING_METRIC', '趋势至少需要 aum 或 deposit');
    return;
  }
  const duplicateDates = new Set();
  const dateCounts = new Map();
  for (const row of table.rows) {
    const key = rowDimensionKey(row, binding, 'date');
    if (key !== null) dateCounts.set(key, (dateCounts.get(key) || 0) + 1);
  }
  for (const [key, count] of dateCounts.entries()) if (count > 1) duplicateDates.add(key);
  const seenDates = new Set();
  for (const row of table.rows) {
    const date = readDimension(row, binding, 'date', 'retailTrend', issues, true);
    if (date === null) {
      issue(issues, 'retailTrend', 'MISSING_DIMENSION', '趋势行缺少日期，已跳过', 'date');
      continue;
    }
    const dateKey = date === null ? '__NULL_DATE__' : String(date);
    if (duplicateDates.has(dateKey)) {
      issue(issues, 'retailTrend', 'DUPLICATE_DATE', `趋势存在重复日期，已排除重复日期行`, 'date');
      continue;
    }
    if (seenDates.has(dateKey)) {
      issue(issues, 'retailTrend', 'DUPLICATE_DATE', '趋势存在重复日期，已排除重复日期行', 'date');
      continue;
    }
    seenDates.add(dateKey);
    const aum = fieldColumn(binding, 'aum')
      ? readMetric(row, binding, 'aum', 'retailTrend', issues).value : null;
    const deposit = fieldColumn(binding, 'deposit')
      ? readMetric(row, binding, 'deposit', 'retailTrend', issues).value : null;
    model.trend.push({ date, aum, deposit });
  }
}

function adaptSegments(table, binding, model, issues) {
  if (!table?.rows?.length) {
    issue(issues, 'retailSegments', 'NO_ROWS', '数据响应没有客群行');
    return;
  }
  for (const row of table.rows) {
    const name = readDimension(row, binding, 'name', 'retailSegments', issues, true);
    const customers = readMetric(row, binding, 'customers', 'retailSegments', issues, true).value;
    const aum = readMetric(row, binding, 'aum', 'retailSegments', issues, true).value;
    model.segments.push({ name, customers, aum });
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
    located: BooleanFlag(pick(item, 'located'))
  })).filter(item => item.orgCode);
}

function BooleanFlag(value) {
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return Number.isFinite(value) && value !== 0;
  const text = String(value ?? '').trim().toLowerCase();
  return ['true', '1', 'yes', 'y'].includes(text);
}

function normalizedCoordSys(value) {
  return String(value ?? '').trim().toUpperCase().replace(/[\s_-]/g, '');
}

function validCoordinate(lng, lat, coordSys) {
  return Number.isFinite(lng) && Number.isFinite(lat)
    && lng >= -180 && lng <= 180
    && lat >= -90 && lat <= 90
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
    located: BooleanFlag(pick(raw, 'located')) && coordinateOk
  };
}

function scopeMode(options = {}) {
  return String(options?.view?.orgScopeMode || options?.view?.org_scope_mode || '').toUpperCase();
}

function adaptRanking(table, binding, model, issues, options) {
  if (!table?.rows?.length) {
    issue(issues, 'retailRanking', 'NO_ROWS', '数据响应没有排名行');
    return;
  }
  const directory = directoryFrom(options);
  const namedGroup = scopeMode(options) === 'NAMED_GROUP';
  if (namedGroup && !directory.length) {
    issue(issues, 'retailRanking', 'NO_AUTHORIZED_DIRECTORY', '命名机构组未返回授权机构目录');
    return;
  }
  const directoryByCode = new Map(directory.map(item => [item.orgCode, item]));
  const duplicateOrgs = new Set();
  const orgCounts = new Map();
  for (const row of table.rows) {
    const key = rowDimensionKey(row, binding, 'orgCode');
    if (key !== null) orgCounts.set(key, (orgCounts.get(key) || 0) + 1);
  }
  for (const [key, count] of orgCounts.entries()) if (count > 1) duplicateOrgs.add(key);
  const seen = new Set();
  for (const row of table.rows) {
    const orgCode = readDimension(row, binding, 'orgCode', 'retailRanking', issues, true);
    const code = orgCode === null ? '' : String(orgCode).trim();
    if (!code) {
      issue(issues, 'retailRanking', 'MISSING_DIMENSION', '排名行缺少机构号，已跳过', 'orgCode');
      continue;
    }
    if (namedGroup && !directoryByCode.has(code)) {
      issue(issues, 'retailRanking', 'UNAUTHORIZED_ORG', '来源包含授权目录外机构，已排除', 'orgCode');
      continue;
    }
    if (duplicateOrgs.has(code)) {
      issue(issues, 'retailRanking', 'DUPLICATE_ORG', '排名存在重复机构，已排除重复机构行', 'orgCode');
      continue;
    }
    if (seen.has(code)) {
      issue(issues, 'retailRanking', 'DUPLICATE_ORG', '排名存在重复机构，已排除重复机构行', 'orgCode');
      continue;
    }
    seen.add(code);
    const name = readDimension(row, binding, 'name', 'retailRanking', issues, true);
    const aum = readMetric(row, binding, 'aum', 'retailRanking', issues, true).value;
    const increase = fieldColumn(binding, 'increase')
      ? readMetric(row, binding, 'increase', 'retailRanking', issues).value : null;
    const rate = fieldColumn(binding, 'rate')
      ? readMetric(row, binding, 'rate', 'retailRanking', issues).value : null;
    const nplRate = fieldColumn(binding, 'nplRate')
      ? readMetric(row, binding, 'nplRate', 'retailRanking', issues).value : null;
    // NAMED_GROUP 只允许目录补充 cityCode；名称、城市名称、坐标不由目录猜测。
    const cityCode = namedGroup ? (directoryByCode.get(code)?.cityCode ?? null) : null;
    model.rankings.push({ orgCode: code || null, name, aum, increase, rate, nplRate, cityCode });
  }
}

function adaptAttention(table, binding, model, issues) {
  if (!table?.rows?.length) {
    issue(issues, 'retailAttention', 'NO_ROWS', '数据响应没有关注事项');
    return;
  }
  for (const row of table.rows) {
    const label = readDimension(row, binding, 'label', 'retailAttention', issues, true);
    const count = readMetric(row, binding, 'count', 'retailAttention', issues, true).value;
    const owner = fieldColumn(binding, 'owner')
      ? readDimension(row, binding, 'owner', 'retailAttention', issues) : null;
    const deadline = fieldColumn(binding, 'deadline')
      ? readDimension(row, binding, 'deadline', 'retailAttention', issues) : null;
    model.attention.push({ label, count, owner, deadline });
  }
}

function adaptTargets(table, binding, model, issues) {
  if (!table?.rows?.length) {
    issue(issues, 'retailTargets', 'NO_ROWS', '数据响应没有目标行');
    return;
  }
  for (const row of table.rows) {
    const name = readDimension(row, binding, 'name', 'retailTargets', issues, true);
    const actual = readMetric(row, binding, 'actual', 'retailTargets', issues, true).value;
    const target = readMetric(row, binding, 'target', 'retailTargets', issues, true).value;
    model.targets.push({ name, actual, target });
  }
}

const IDENTITY_FIELDS = Object.freeze([
  ['orgName', 'orgName', 'org_name'],
  ['cityCode', 'cityCode', 'city_code'],
  ['cityName', 'cityName', 'city_name'],
  ['ownerOperatingOrgCode', 'ownerOperatingOrgCode', 'owner_operating_org_code'],
  ['parentOrgCode', 'parentOrgCode', 'parent_org_code'],
  ['lng', 'lng', 'longitude'],
  ['lat', 'lat', 'latitude'],
  ['coordSys', 'coordSys', 'coord_sys'],
  ['located', 'located', 'located']
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
    const rawIdentity = directoryItem
      ? directoryItem
      : {
        orgCode: code,
        ...Object.fromEntries(IDENTITY_FIELDS.map(([name, semantic]) => [
          name, row && fieldColumn(binding, semantic)
            ? readDimension(row, binding, semantic, 'branches', issues) : null
        ]))
      };
    // 目录身份是权威值；即使目录字段为空，也不能回填查询行的城市/坐标。
    model.institutions.push(normalizeInstitutionIdentity(rawIdentity));
  }
}

function adaptSlot(slot, table, binding, model, issues, options) {
  if (SINGLE_SLOTS.includes(slot)) return adaptSingle(slot, table, binding, model, issues);
  switch (slot) {
    case 'retailTrend': return adaptTrend(table, binding, model, issues);
    case 'retailSegments': return adaptSegments(table, binding, model, issues);
    case 'retailRanking': return adaptRanking(table, binding, model, issues, options);
    case 'retailAttention': return adaptAttention(table, binding, model, issues);
    case 'retailTargets': return adaptTargets(table, binding, model, issues);
    case 'branches': return adaptBranches(table, binding, model, issues, options);
    default: return undefined;
  }
}

/**
 * 将零售模板发布包的独立槽位响应适配为稳定的零售经营总览模型。
 * 旧分行槽位会被忽略，唯一例外是 branches 身份槽。
 */
export function adaptRetailResults(results = {}, options = {}) {
  const model = createEmptyRetailModel(options);
  const issues = [];
  let knownDate = '';
  for (const slot of RETAIL_SLOT_ORDER) {
    const result = results?.[slot];
    if (!result) continue;
    const binding = normalizeRetailBinding(result.binding || {}, slot);
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
      else if (knownDate !== String(date)) {
        issue(issues, slot, 'MIXED_DATES', '零售槽位日期不一致，需核对统计期间', 'date');
      }
      if (!model.dataDate) model.dataDate = String(date);
    }
  }
  if (!model.institutions.length) {
    // 没有 branches 查询/响应时，发布包仍可携带授权目录；只复制身份。
    for (const item of directoryFrom(options)) {
      model.institutions.push(normalizeInstitutionIdentity(item));
    }
  }
  model.issues = issues;
  return model;
}

export { DISPLAY_UNIT_LABELS };
