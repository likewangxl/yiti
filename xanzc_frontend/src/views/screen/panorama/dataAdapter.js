import { BINDING_SLOTS, UNIT_VALUES, normalizeBinding } from './bindings';

const DISPLAY_UNIT_LABELS = Object.freeze({
  HUNDRED_MILLION: '亿元',
  TEN_THOUSAND: '万元',
  YUAN: '元',
  TEN_THOUSAND_COUNT: '万户',
  COUNT: '个',
  PERCENT: '%',
  RATIO: '%'
});

const AMOUNT_SCALES_PROVING_YUAN = new Set([
  'YUAN', 'TEN_THOUSAND_YUAN', 'HUNDRED_MILLION_YUAN'
]);

const SLOT_ORDER = Object.keys(BINDING_SLOTS);

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

function emptyMetrics() {
  return { deposit: null, loan: null, customers: null, target: null, rate: null };
}

export function createEmptyPanoramaModel() {
  return {
    title: '分行经营总览',
    dataDate: '',
    kpis: [],
    trend: [],
    composition: [],
    rankings: [],
    attention: [],
    institutions: [],
    issues: [],
    citySummaries: {}
  };
}

function issue(list, slot, code, message, fieldName = '') {
  const key = `${slot}|${code}|${fieldName}`;
  if (list.some(item => `${item.slot}|${item.code}|${item.field || ''}` === key)) return;
  list.push({ slot, code, field: fieldName || undefined, message: message || `${slot}: ${code}` });
}

function unwrapResponse(response) {
  if (!isObject(response)) return response;
  // queryScreenData 经 http.call 解包后通常就是 data；兼容测试/连接器把 data 再包一层的形态。
  if (response.columns === undefined && response.rows === undefined && isObject(response.data)) return response.data;
  return response;
}

/** 后端运行时契约是 columns + 二维 rows；遇到对象行不静默猜测。 */
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
    if (typeof column === 'string') return column;
    if (isObject(column)) return asText(column.col || column.name || column.key);
    return asText(column);
  });
  if (columns.some(column => !column)) {
    issue(issues, slot, 'INVALID_COLUMNS', '数据响应存在空列名');
    return null;
  }
  const rows = [];
  for (const rawRow of table.rows) {
    if (!Array.isArray(rawRow)) {
      issue(issues, slot, 'INVALID_ROWS', '数据响应 rows 必须是二维数组');
      continue;
    }
    const row = {};
    columns.forEach((column, index) => { row[column] = rawRow[index] === undefined ? null : rawRow[index]; });
    rows.push(row);
  }
  return { ...table, columns, rows, columnsMeta: Array.isArray(table.columnsMeta) ? table.columnsMeta : [] };
}

function columnMeta(table, column) {
  if (!column || !Array.isArray(table?.columnsMeta)) return null;
  return table.columnsMeta.find(meta => asText(meta?.col || meta?.name) === column) || null;
}

/**
 * columnsMeta.amountScale 是展示预设，ScreenQueryEngine 返回的 rows 仍然按元提供。
 * 因此它只能证明金额原值单位为 YUAN，不能把 HUNDRED_MILLION_YUAN 当作输入亿元再次换算。
 */
function resolveInputUnit(binding, semantic, meta) {
  const explicit = binding?.units?.[semantic];
  if (UNIT_VALUES.includes(explicit)) return explicit;
  if (AMOUNT_SCALES_PROVING_YUAN.has(asText(meta?.amountScale).toUpperCase())) return 'YUAN';
  return null;
}

function numeric(value) {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function metricKind(slot, semantic, unit = null) {
  if (slot === 'attention' && semantic === 'count') return 'rawCount';
  if (semantic === 'customers' || (slot === 'customers' && semantic === 'value') || semantic === 'count') return 'count';
  if (semantic === 'rate' || semantic === 'change'
      || (slot === 'rate' && semantic === 'value')) return 'ratio';
  // 构成值允许按比例绑定；只有单位明确为百分数/比例时才走比例换算，
  // 这样同一个 semantic=value 仍可安全表示金额构成。
  if (semantic === 'value' && (unit === 'PERCENT' || unit === 'RATIO')) return 'ratio';
  // value in composition/ranking is an amount by the product contract unless the binding says PERCENT/RATIO.
  return 'amount';
}

function convertMetric(value, unit, kind, slot, semantic, issues) {
  if (value === null || value === undefined || value === '') return null;
  const raw = numeric(value);
  if (raw === null) {
    issue(issues, slot, 'INVALID_NUMBER', `字段 ${semantic} 不是数值`, semantic);
    return null;
  }
  if (!unit) {
    issue(issues, slot, 'UNKNOWN_UNIT', `字段 ${semantic} 缺少可证明单位`, semantic);
    return null;
  }
  if (kind === 'count') {
    if (unit === 'COUNT') return raw / 10000;
    if (unit === 'TEN_THOUSAND_COUNT') return raw;
    issue(issues, slot, 'UNIT_MISMATCH', `字段 ${semantic} 需要 COUNT 或 TEN_THOUSAND_COUNT`, semantic);
    return null;
  }
  if (kind === 'rawCount') {
    if (unit === 'COUNT') return raw;
    if (unit === 'TEN_THOUSAND_COUNT') return raw * 10000;
    issue(issues, slot, 'UNIT_MISMATCH', `字段 ${semantic} 需要 COUNT 或 TEN_THOUSAND_COUNT`, semantic);
    return null;
  }
  if (kind === 'ratio') {
    if (unit === 'PERCENT') return raw;
    if (unit === 'RATIO') return raw * 100;
    issue(issues, slot, 'UNIT_MISMATCH', `字段 ${semantic} 需要 PERCENT 或 RATIO`, semantic);
    return null;
  }
  if (unit === 'YUAN') return raw / 100000000;
  if (unit === 'TEN_THOUSAND') return raw / 10000;
  if (unit === 'HUNDRED_MILLION') return raw;
  issue(issues, slot, 'UNIT_MISMATCH', `字段 ${semantic} 需要金额单位`, semantic);
  return null;
}

function displayUnit(slot, semantic, unit) {
  const kind = metricKind(slot, semantic, unit);
  if (kind === 'count') return '万户';
  if (kind === 'ratio') return '%';
  // Panorama model amount values are canonical 亿元 regardless of original raw unit.
  return unit ? '亿元' : null;
}

function readDimension(row, binding, semantic, table, slot, issues, required = false) {
  const column = binding?.fields?.[semantic];
  if (!column) {
    if (required) issue(issues, slot, 'MISSING_FIELD', `缺少字段映射: ${semantic}`, semantic);
    return null;
  }
  if (!Object.prototype.hasOwnProperty.call(row, column)) {
    issue(issues, slot, 'MISSING_COLUMN', `响应缺少列: ${column}`, semantic);
    return null;
  }
  return row[column] === undefined ? null : row[column];
}

function readMetric(row, binding, semantic, table, slot, issues, required = false) {
  const column = binding?.fields?.[semantic];
  if (!column) {
    if (required) issue(issues, slot, 'MISSING_FIELD', `缺少字段映射: ${semantic}`, semantic);
    return { value: null, unit: null };
  }
  if (!Object.prototype.hasOwnProperty.call(row, column)) {
    issue(issues, slot, 'MISSING_COLUMN', `响应缺少列: ${column}`, semantic);
    return { value: null, unit: null };
  }
  const raw = row[column] === undefined ? null : row[column];
  const unit = resolveInputUnit(binding, semantic, columnMeta(table, column));
  return {
    value: convertMetric(raw, unit, metricKind(slot, semantic, unit), slot, semantic, issues),
    unit: displayUnit(slot, semantic, unit)
  };
}

function firstRow(table, slot, issues) {
  if (!table?.rows?.length) {
    issue(issues, slot, 'NO_ROWS', '数据响应没有数据行');
    return null;
  }
  return table.rows[0];
}

function canonicalKpi(slot, item) {
  return {
    key: slot,
    label: BINDING_SLOTS[slot]?.label || slot,
    value: item?.value ?? null,
    unit: item?.unit ?? null,
    change: item?.change ?? null
  };
}

function responseDataDate(table) {
  const date = pick(table, 'dataDate', 'data_date', 'date');
  return date === undefined || date === null ? '' : String(date);
}

function adaptSingle(slot, table, binding, model, issues) {
  const row = firstRow(table, slot, issues);
  if (!row) {
    model.kpis.push(canonicalKpi(slot));
    return;
  }
  const item = readMetric(row, binding, 'value', table, slot, issues, true);
  const change = binding.fields?.change
    ? readMetric(row, binding, 'change', table, slot, issues).value : null;
  model.kpis.push(canonicalKpi(slot, { ...item, change }));
  if (!model.dataDate && binding.fields?.date) {
    const date = readDimension(row, binding, 'date', table, slot, issues);
    if (date !== null && date !== undefined && date !== '') model.dataDate = String(date);
  }
}

function adaptTrend(slot, table, binding, model, issues) {
  if (!table?.rows?.length) {
    issue(issues, slot, 'NO_ROWS', '数据响应没有趋势行');
    return;
  }
  for (const row of table.rows) {
    const date = readDimension(row, binding, 'date', table, slot, issues, true);
    const deposit = readMetric(row, binding, 'deposit', table, slot, issues).value;
    const loan = readMetric(row, binding, 'loan', table, slot, issues).value;
    model.trend.push({ date: date === undefined ? null : date, deposit, loan });
  }
}

function adaptComposition(table, binding, model, issues, slot = 'composition') {
  if (!table?.rows?.length) {
    issue(issues, slot, 'NO_ROWS', '数据响应没有构成行');
    return;
  }
  for (const row of table.rows) {
    const name = readDimension(row, binding, 'name', table, slot, issues, true);
    const value = readMetric(row, binding, 'value', table, slot, issues, true);
    model.composition.push({ name: name ?? null, value: value.value, unit: value.unit });
  }
}

function adaptRanking(table, binding, model, issues) {
  if (!table?.rows?.length) {
    issue(issues, 'ranking', 'NO_ROWS', '数据响应没有排名行');
    return;
  }
  for (const row of table.rows) {
    const orgCode = readDimension(row, binding, 'orgCode', table, 'ranking', issues, true);
    const name = readDimension(row, binding, 'name', table, 'ranking', issues, true);
    const value = readMetric(row, binding, 'value', table, 'ranking', issues, true);
    const change = binding.fields?.change
      ? readMetric(row, binding, 'change', table, 'ranking', issues).value : null;
    // 统一模型以 deposit 作为排名主指标；binding semantic 仍保留 value 以避免猜列名。
    model.rankings.push({ orgCode: orgCode ?? '', name: name ?? '', deposit: value.value, change });
  }
}

function adaptAttention(table, binding, model, issues) {
  if (!table?.rows?.length) {
    issue(issues, 'attention', 'NO_ROWS', '数据响应没有关注事项');
    return;
  }
  for (const row of table.rows) {
    const label = readDimension(row, binding, 'label', table, 'attention', issues, true);
    const count = readMetric(row, binding, 'count', table, 'attention', issues, true);
    model.attention.push({ label: label ?? '', count: count.value });
  }
}

const INSTITUTION_FIELDS = Object.freeze([
  'orgCode', 'orgName', 'cityCode', 'cityName', 'ownerOperatingOrgCode', 'parentOrgCode', 'operatingLevel',
  'orgNature', 'lng', 'lat', 'coordSys', 'located'
]);

function normalizeInstitution(raw = {}) {
  const orgCode = asText(pick(raw, 'orgCode', 'org_code'));
  const lng = numeric(pick(raw, 'lng', 'longitude'));
  const lat = numeric(pick(raw, 'lat', 'latitude'));
  const out = { orgCode };
  for (const fieldName of INSTITUTION_FIELDS) {
    if (fieldName === 'orgCode') continue;
    const value = pick(raw, fieldName, {
      orgName: 'org_name', cityCode: 'city_code', cityName: 'city_name',
      ownerOperatingOrgCode: 'owner_operating_org_code',
      parentOrgCode: 'parent_org_code', operatingLevel: 'operating_level',
      orgNature: 'org_nature', coordSys: 'coord_sys'
    }[fieldName]);
    if (value !== undefined && value !== null) out[fieldName] = value;
  }
  out.orgName = asText(out.orgName);
  out.cityCode = asText(out.cityCode);
  out.lng = lng;
  out.lat = lat;
  out.coordSys = asText(out.coordSys);
  out.located = Boolean(out.located) && Number.isFinite(lng) && Number.isFinite(lat);
  out.metrics = emptyMetrics();
  out.trend = [];
  out.attention = [];
  return out;
}

function directoryFrom(options = {}) {
  const source = options?.panoramaInstitutions
    || options?.view?.panoramaInstitutions
    || options?.view?.panorama_institutions
    || [];
  return (Array.isArray(source) ? source : []).map(normalizeInstitution).filter(item => item.orgCode);
}

function adaptBranches(table, binding, model, issues, options) {
  const rows = table?.rows || [];
  const directory = directoryFrom(options);
  const scopeMode = String(options?.view?.orgScopeMode || options?.view?.org_scope_mode || '').toUpperCase();
  const sourceByCode = new Map();
  for (const row of rows) {
    const code = readDimension(row, binding, 'orgCode', table, 'branches', issues, true);
    if (code !== null && code !== undefined && code !== '') sourceByCode.set(String(code), row);
  }
  // NAMED_GROUP 没有目录时不能退回数据源 rows 扩大机构集合；LEGACY_CONTEXT 才允许
  // 使用后端已授权的 rows 作为机构身份来源。
  const sourceCodes = directory.length
    ? directory.map(item => item.orgCode)
    : scopeMode === 'NAMED_GROUP' ? [] : [...sourceByCode.keys()];
  if (!rows.length && !directory.length) issue(issues, 'branches', 'NO_ROWS', '数据响应没有机构行');
  if (scopeMode === 'NAMED_GROUP' && !directory.length) {
    issue(issues, 'branches', 'NO_AUTHORIZED_DIRECTORY', '命名机构组未返回授权机构目录');
  }
  if (!directory.length && rows.length && !binding.fields?.cityCode) {
    issue(issues, 'branches', 'MISSING_CITY_CODE', '无授权机构目录时必须绑定 cityCode');
  }
  for (const code of sourceCodes) {
    const directoryItem = directory.find(item => item.orgCode === code);
    const row = sourceByCode.get(code);
    const item = directoryItem ? normalizeInstitution(directoryItem) : normalizeInstitution({
      orgCode: code,
      orgName: row ? readDimension(row, binding, 'orgName', table, 'branches', issues) : '',
      cityCode: row ? readDimension(row, binding, 'cityCode', table, 'branches', issues) : '',
      cityName: row ? readDimension(row, binding, 'cityName', table, 'branches', issues) : '',
      ownerOperatingOrgCode: row ? readDimension(row, binding, 'ownerOperatingOrgCode', table, 'branches', issues) : '',
      parentOrgCode: row ? readDimension(row, binding, 'parentOrgCode', table, 'branches', issues) : '',
      lng: row ? readDimension(row, binding, 'lng', table, 'branches', issues) : null,
      lat: row ? readDimension(row, binding, 'lat', table, 'branches', issues) : null,
      coordSys: row ? readDimension(row, binding, 'coordSys', table, 'branches', issues) : '',
      located: row ? readDimension(row, binding, 'located', table, 'branches', issues) : false
    });
    if (!item.cityCode && row && binding.fields?.cityCode) {
      item.cityCode = asText(readDimension(row, binding, 'cityCode', table, 'branches', issues));
    }
    if (!row) {
      model.institutions.push(item);
      continue;
    }
    for (const semantic of ['deposit', 'loan', 'customers', 'target', 'rate']) {
      if (!binding.fields?.[semantic]) continue;
      item.metrics[semantic] = readMetric(row, binding, semantic, table, 'branches', issues).value;
    }
    // Directory is authoritative for identity/coordinates. Only use source identity when no directory exists.
    if (!directoryItem) {
      item.orgName = asText(readDimension(row, binding, 'orgName', table, 'branches', issues));
      item.cityName = asText(readDimension(row, binding, 'cityName', table, 'branches', issues));
      item.lng = numeric(readDimension(row, binding, 'lng', table, 'branches', issues));
      item.lat = numeric(readDimension(row, binding, 'lat', table, 'branches', issues));
      item.coordSys = asText(readDimension(row, binding, 'coordSys', table, 'branches', issues));
      item.located = Boolean(readDimension(row, binding, 'located', table, 'branches', issues))
        && Number.isFinite(item.lng) && Number.isFinite(item.lat);
    }
    model.institutions.push(item);
  }
}

function adaptCitySummary(table, binding, model, issues, options) {
  const rows = table?.rows || [];
  if (!rows.length) {
    issue(issues, 'citySummary', 'NO_ROWS', '数据响应没有城市汇总');
    return;
  }
  const directory = directoryFrom(options);
  const directoryByOrg = new Map(directory.map(item => [String(item.orgCode), item]));
  const directoryCityNames = new Map();
  const directoryCityCodes = new Set();
  for (const item of directory) {
    if (!item.cityCode) continue;
    directoryCityCodes.add(String(item.cityCode));
    if (item.cityName) directoryCityNames.set(String(item.cityCode), item.cityName);
  }
  const hasOrgIdentity = Boolean(binding.fields?.orgCode);
  const hasCityIdentity = Boolean(binding.fields?.cityCode);
  if (!hasOrgIdentity && !hasCityIdentity) {
    issue(issues, 'citySummary', 'MISSING_IDENTITY', '城市汇总必须绑定城市编码或机构号');
    return;
  }
  const scopeMode = String(options?.view?.orgScopeMode || options?.view?.org_scope_mode || '').toUpperCase();
  if (!directory.length && scopeMode === 'NAMED_GROUP') {
    issue(issues, 'citySummary', 'NO_AUTHORIZED_DIRECTORY', '命名机构组未返回授权机构目录');
    return;
  }
  if (hasOrgIdentity && !directory.length) {
    issue(issues, 'citySummary', 'NO_AUTHORIZED_DIRECTORY', '按机构号解析城市必须有授权机构目录');
    return;
  }
  const seenCities = new Set();
  const duplicateCities = new Set();
  for (const row of rows) {
    let cityCode;
    let cityName;
    if (hasOrgIdentity) {
      const orgCode = readDimension(row, binding, 'orgCode', table, 'citySummary', issues, true);
      const directoryItem = orgCode === null || orgCode === undefined
        ? null : directoryByOrg.get(String(orgCode));
      if (!directoryItem) {
        if (orgCode !== null && orgCode !== undefined && orgCode !== '') {
          issue(issues, 'citySummary', 'UNAUTHORIZED_ORG', `机构未在授权目录中: ${String(orgCode)}`, 'orgCode');
        }
        continue;
      }
      cityCode = directoryItem.cityCode;
      cityName = directoryItem.cityName;
      if (!cityCode) {
        issue(issues, 'citySummary', 'MISSING_CITY_MAPPING', `授权机构缺少城市映射: ${String(orgCode)}`, 'orgCode');
        continue;
      }
    } else {
      cityCode = readDimension(row, binding, 'cityCode', table, 'citySummary', issues, true);
      if (cityCode === null || cityCode === undefined || cityCode === '') continue;
      cityCode = String(cityCode);
      if (directory.length && !directoryCityCodes.has(cityCode)) {
        issue(issues, 'citySummary', 'UNAUTHORIZED_CITY', `城市不在授权机构目录中: ${cityCode}`, 'cityCode');
        continue;
      }
      cityName = directoryCityNames.get(cityCode) || null;
    }
    cityCode = String(cityCode || '').trim();
    if (!cityCode) {
      issue(issues, 'citySummary', 'MISSING_CITY_CODE', '城市汇总缺少可解析的城市编码', 'cityCode');
      continue;
    }
    if (duplicateCities.has(cityCode)) continue;
    if (seenCities.has(cityCode)) {
      duplicateCities.add(cityCode);
      delete model.citySummaries[cityCode];
      issue(issues, 'citySummary', 'DUPLICATE_CITY', `城市汇总存在重复城市: ${cityCode}`, 'cityCode');
      continue;
    }
    seenCities.add(cityCode);
    const city = { kpis: [], dataDate: responseDataDate(table) };
    const rowCityName = binding.fields?.cityName
      ? readDimension(row, binding, 'cityName', table, 'citySummary', issues) : null;
    if (rowCityName !== null && rowCityName !== undefined && rowCityName !== '') city.cityName = rowCityName;
    else if (cityName !== null && cityName !== undefined && cityName !== '') city.cityName = cityName;
    for (const semantic of ['deposit', 'loan', 'customers', 'revenue', 'rate']) {
      if (!binding.fields?.[semantic]) continue;
      const metric = readMetric(row, binding, semantic, table, 'citySummary', issues);
      city.kpis.push({ key: semantic, label: BINDING_SLOTS[semantic]?.label || semantic,
        value: metric.value, unit: metric.unit, change: null });
    }
    model.citySummaries[String(cityCode)] = city;
  }
}

function adaptSlot(slot, table, binding, model, issues, options) {
  switch (slot) {
    case 'deposit':
    case 'loan':
    case 'customers':
    case 'revenue':
    case 'rate':
      adaptSingle(slot, table, binding, model, issues); break;
    case 'trend':
      adaptTrend(slot, table, binding, model, issues); break;
    case 'composition':
      adaptComposition(table, binding, model, issues); break;
    case 'ranking':
      adaptRanking(table, binding, model, issues); break;
    case 'attention':
      adaptAttention(table, binding, model, issues); break;
    case 'branches':
      adaptBranches(table, binding, model, issues, options); break;
    case 'branchTrend':
      // 支行趋势只属于选中机构，runtime 通过 applyBranchTrend 独立注入，
      // 不能把它混入全辖 trend。
      break;
    case 'citySummary':
      adaptCitySummary(table, binding, model, issues, options); break;
    default:
      issue(issues, slot, 'UNSUPPORTED_SLOT', `槽位不受支持: ${slot}`);
  }
}

/**
 * 把每个 slot 的响应合成为 Dashboard 统一模型。
 * results 形态：{ [slot]: { binding, response } }，response 必须是 columns/rows 二维数据。
 */
export function adaptPanoramaResults(results = {}, options = {}) {
  const model = createEmptyPanoramaModel();
  const issues = [];
  for (const slot of SLOT_ORDER) {
    const result = results?.[slot];
    if (!result) continue;
    const binding = normalizeBinding(result.binding || {}, slot);
    const table = parseTableResponse(result.response, slot, issues);
    if (!table) continue;
    const beforeIssueCount = issues.length;
    adaptSlot(slot, table, binding, model, issues, options);
    if (!model.dataDate) {
      const date = responseDataDate(table);
      if (date) model.dataDate = date;
    }
    // A table without a binding mapping must remain explicitly diagnosable even when it has zero rows.
    if (issues.length === beforeIssueCount && !BINDING_SLOTS[slot]) {
      issue(issues, slot, 'UNSUPPORTED_SLOT', `槽位不受支持: ${slot}`);
    }
  }
  // 未绑定/请求失败的 branches 也必须保留服务端授权目录中的机构空行；目录是唯一集合，
  // 这里只复制身份与坐标，不从名称或旧数据填充任何指标。
  if (!model.institutions.length) {
    const directory = directoryFrom(options);
    const scopeMode = String(options?.view?.orgScopeMode || options?.view?.org_scope_mode || '').toUpperCase();
    if (directory.length) model.institutions = directory.map(item => normalizeInstitution(item));
    else if (scopeMode === 'NAMED_GROUP' && (options?.view?.panoramaInstitutions || options?.view?.panorama_institutions)) {
      issue(issues, 'branches', 'NO_AUTHORIZED_DIRECTORY', '命名机构组未返回授权机构目录');
    }
  }
  const title = options?.title || options?.view?.screenName || options?.view?.screen_name;
  if (title) model.title = String(title);
  model.issues = issues;
  return model;
}

/** 将 branchTrend 单独查询的结果注入当前机构，供 runtime 在选中支行后使用。 */
export function applyBranchTrend(model, result, binding, orgCode, options = {}) {
  const issues = Array.isArray(model?.issues) ? model.issues : [];
  const table = parseTableResponse(result, 'branchTrend', issues);
  if (!table || !model || !Array.isArray(model.institutions)) {
    if (model) model.issues = issues;
    return model;
  }
  const trendModel = { trend: [] };
  adaptTrend('branchTrend', table, normalizeBinding(binding || {}, 'branchTrend'), trendModel, issues);
  const institution = model.institutions.find(item => String(item.orgCode) === String(orgCode));
  if (institution) institution.trend = trendModel.trend;
  if (!model.dataDate) model.dataDate = responseDataDate(table);
  if (model) model.issues = issues;
  void options;
  return model;
}

export function displayUnitLabel(unit) {
  return DISPLAY_UNIT_LABELS[unit] || null;
}
