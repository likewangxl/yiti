const CONTRACTS = Object.freeze({
  BR_DIRECT_DEP: 'deposit', BR_DIRECT_LOAN: 'loan', BR_DIRECT_CUSTOMERS: 'customers',
  BR_DIRECT_REVENUE: 'revenue', BR_DIRECT_RETAIL_DEP: 'retailDeposit', BR_DIRECT_CORP_DEP: 'corpDeposit',
  BR_DIRECT_RETAIL_LOAN: 'retailLoan', BR_DIRECT_CORP_LOAN: 'corpLoan',
  BR_DIRECT_DEP_RATE: 'retailDepositRate', BR_DIRECT_LOAN_RATE: 'retailLoanRate',
  BR_DIRECT_CORP_DEP_RATE: 'corpDepositRate', BR_DIRECT_CORP_LOAN_RATE: 'corpLoanRate',
  BR_DIRECT_RETAIL_NPL_RATE: 'retailNplRate', BR_DIRECT_CORP_NPL_RATE: 'corpNplRate',
  BR_DIRECT_RETAIL_NPL_BAL: 'retailNplBalance', BR_DIRECT_CORP_NPL_BAL: 'corpNplBalance',
  BR_DIRECT_DEP_TARGET: 'retailDepositTarget', BR_DIRECT_LOAN_TARGET: 'retailLoanTarget',
  BR_DIRECT_CORP_DEP_TARGET: 'corpDepositTarget', BR_DIRECT_CORP_LOAN_TARGET: 'corpLoanTarget'
});
const RATIO_CODES = new Set(['BR_DIRECT_DEP_RATE', 'BR_DIRECT_LOAN_RATE', 'BR_DIRECT_CORP_DEP_RATE', 'BR_DIRECT_CORP_LOAN_RATE', 'BR_DIRECT_RETAIL_NPL_RATE', 'BR_DIRECT_CORP_NPL_RATE']);
const AMOUNT_UNITS = new Set(['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION', 'COUNT']);
const RATE_FORMULAS = Object.freeze({
  BR_DIRECT_DEP_RATE: ['BR_DIRECT_RETAIL_DEP', 'BR_DIRECT_DEP_TARGET'],
  BR_DIRECT_LOAN_RATE: ['BR_DIRECT_RETAIL_LOAN', 'BR_DIRECT_LOAN_TARGET'],
  BR_DIRECT_CORP_DEP_RATE: ['BR_DIRECT_CORP_DEP', 'BR_DIRECT_CORP_DEP_TARGET'],
  BR_DIRECT_CORP_LOAN_RATE: ['BR_DIRECT_CORP_LOAN', 'BR_DIRECT_CORP_LOAN_TARGET'],
  BR_DIRECT_RETAIL_NPL_RATE: ['BR_DIRECT_RETAIL_NPL_BAL', 'BR_DIRECT_RETAIL_LOAN'],
  BR_DIRECT_CORP_NPL_RATE: ['BR_DIRECT_CORP_NPL_BAL', 'BR_DIRECT_CORP_LOAN']
});

const isObject = value => value && typeof value === 'object' && !Array.isArray(value);
const text = value => value === null || value === undefined ? '' : String(value).trim();
const finite = value => !['number', 'string'].includes(typeof value) || String(value).trim() === '' || !Number.isFinite(Number(value)) ? null : Number(value);
const UNIT_SCALE = Object.freeze({ YUAN: 1, TEN_THOUSAND: 1e4, HUNDRED_MILLION: 1e8, COUNT: 1 });
function amount(value, unit) { const n = finite(value); const scale = UNIT_SCALE[String(unit || '').toUpperCase()]; return n === null || scale === undefined ? null : n * scale; }
function sourceConfig(snapshot) {
  const config = snapshot?.sourceDefinition?.config || snapshot?.sourceDefinition?.sourceDefinition?.config;
  if (!isObject(config) || config.table !== 'ORG_INDEX_RESULT' || String(config.scopeMode).toUpperCase() !== 'NAMED_GROUP') return null;
  const aggregation = config.aggregation || {};
  return String(aggregation.agg).toUpperCase() === 'SUM' && String(aggregation.groupBy).toUpperCase() === 'SUBJECT' ? config : null;
}
function metricDefinitions(config, table) {
  const configured = Array.isArray(config?.metrics) ? config.metrics : [];
  const meta = new Map((Array.isArray(table?.columnsMeta) ? table.columnsMeta : []).map(item => [text(item.col || item.alias), item]));
  return configured.filter(item => CONTRACTS[item.metricCode]).map(item => ({ ...item, semantic: CONTRACTS[item.metricCode], name: text(item.metricName), meta: meta.get(text(item.metricName)) || meta.get(text(item.metricCode)) }));
}
function rowValue(row, definition) {
  const value = row?.[definition.name] ?? row?.[definition.metricCode];
  return finite(value);
}
function validTable(table) {
  return isObject(table) && Array.isArray(table.rows) && table.rows.length && String(table.quality?.subjectValueMode).toUpperCase() === 'EXCLUSIVE';
}
function aggregateCity(rows, definitions, city) {
  const byCode = new Map();
  for (const definition of definitions) {
    if (!AMOUNT_UNITS.has(definition.meta?.unit || definition.unit)) continue;
    const values = rows.map(row => rowValue(row, definition));
    if (values.some(value => value === null)) continue;
    byCode.set(definition.metricCode, values.reduce((sum, value) => sum + value, 0));
  }
  const kpis = [];
  const aliases = {};
  for (const definition of definitions) {
    if (RATIO_CODES.has(definition.metricCode)) continue;
    const value = byCode.has(definition.metricCode) ? byCode.get(definition.metricCode) : null;
    if (definition.semantic.endsWith('Target') || definition.semantic.endsWith('Balance')) continue;
    kpis.push({ key: definition.semantic, label: definition.name || definition.semantic, value, unit: definition.meta?.unit || definition.unit || null, dataDate: city.dataDate });
    aliases[definition.name || definition.metricCode] = value;
  }
  for (const definition of definitions.filter(item => RATIO_CODES.has(item.metricCode))) {
    const [numerator, denominator] = RATE_FORMULAS[definition.metricCode] || [];
    const nDef = definitions.find(item => item.metricCode === numerator); const dDef = definitions.find(item => item.metricCode === denominator);
    const n = nDef ? rows.reduce((sum, row) => sum + (amount(rowValue(row, nDef), nDef.meta?.unit || nDef.unit) ?? NaN), 0) : NaN;
    const d = dDef ? rows.reduce((sum, row) => sum + (amount(rowValue(row, dDef), dDef.meta?.unit || dDef.unit) ?? NaN), 0) : NaN;
    const value = Number.isFinite(n) && Number.isFinite(d) && d > 0 ? n / d * 100 : null;
    kpis.push({ key: definition.semantic, label: definition.name || definition.semantic, value, unit: definition.meta?.unit || 'PERCENT', dataDate: city.dataDate });
    aliases[definition.name || definition.metricCode] = value;
  }
  const row = { cityCode: city.cityCode, cityName: city.cityName, data_date: city.dataDate, ...aliases };
  const unitByField = Object.fromEntries((city.columnsMeta || []).filter(meta => meta?.col && meta?.unit).map(meta => [meta.col, meta.unit]));
  return { cityName: city.cityName, dataDate: city.dataDate, kpis, blockResults: { [String(city.blockId)]: { ...row, rows: [row], blockId: city.blockId, dataDate: city.dataDate, columnsMeta: city.columnsMeta, units: city.units, unitByField, quality: city.quality } } };
}

function sameSource(a, b) {
  const ca = sourceConfig(a); const cb = b?.sourceDefinition?.config;
  if (!ca || !cb || ca.table !== cb.table || ca.scopeMode !== cb.scopeMode
    || cb.aggregation?.agg !== 'SUM' || !['NONE', 'SUBJECT', 'DATE'].includes(cb.aggregation?.groupBy)) return false;
  const names = new Map((ca.metrics || []).map(x => [x.metricCode, x.metricName]));
  return cb.metrics?.length > 0 && cb.metrics.every(m => names.get(m.metricCode) === m.metricName);
}
function projectTables({ table, snapshot, directory }) {
  const config = sourceConfig(snapshot);
  if (!config || !validTable(table)) return {};
  const definitions = metricDefinitions(config, table);
  if (!definitions.length) return {};
  const rowsByOrg = new Map();
  for (const row of table.rows) {
    const org = text(row?.orgCode ?? row?.org_code);
    if (!org || rowsByOrg.has(org)) rowsByOrg.set(org, rowsByOrg.has(org) ? null : row);
    else rowsByOrg.set(org, row);
  }
  const cities = new Map();
  for (const item of directory) {
    const org = text(item?.orgCode ?? item?.org_code); const cityCode = text(item?.cityCode ?? item?.city_code);
    if (!org || !cityCode) continue;
    const city = cities.get(cityCode) || { cityCode, cityName: text(item.cityName ?? item.city_name), orgs: [], blockId: table.blockId, dataDate: text(table.dataDate || table.quality?.dataDate), columnsMeta: table.columnsMeta, units: table.units, quality: table.quality };
    city.orgs.push(org); cities.set(cityCode, city);
  }
  const result = {};
  for (const city of cities.values()) {
    if (new Set(city.orgs).size !== city.orgs.length) continue;
    const rows = city.orgs.map(org => rowsByOrg.get(org));
    if (rows.some(row => !row) || rows.some(row => text(row.data_date || row.dataDate) && text(row.data_date || row.dataDate) !== city.dataDate)) continue;
    result[city.cityCode] = aggregateCity(rows, definitions, city);
  }
  return result;
}

/**
 * 将已发布的直营机构独占明细投影为授权城市模型。
 * 只有相同指标代码与列映射的父屏引用可以复用投影，历史必须来自同批次。
 * 比例使用已确认的直营分子/分母合同，缺行、缺值不补零，也不使用全辖总量。
 */
export function buildCityOperatingSummaries({ table, snapshot, directory = [], snapshots = {}, presentation = null, historyTables = {} } = {}) {
  const result = projectTables({ table, snapshot, directory });
  if (!Object.keys(result).length) return result;
  const aliases = Object.entries(snapshots).filter(([, candidate]) => sameSource(snapshot, candidate));
  for (const [cityCode, city] of Object.entries(result)) {
    for (const component of presentation?.display?.components || []) {
      if (component?.visible === false) continue;
      for (const ref of component?.dataRefs || []) {
        const id = String(ref?.blockId || ''); if (!id || id === String(table.blockId)) continue;
        const candidate = snapshots[id];
        if (candidate && sameSource(snapshot, candidate)) city.blockResults[id] = { ...city.blockResults[String(table.blockId)], blockId: id };
      }
    }
    city.rangeResults = {};
    for (const [date, history] of Object.entries(historyTables || {})) {
      if (!history || !table.quality?.batchId || history.quality?.batchId !== table.quality?.batchId || !/^\d{4}-\d{2}-\d{2}$/.test(date)) continue;
      const normalized = { ...history, dataDate: date };
      const historical = projectTables({ table: normalized, snapshot, directory });
      const row = historical[cityCode];
      if (!row) continue;
      for (const [aliasId] of aliases) city.rangeResults[aliasId] = { ...(city.rangeResults[aliasId] || {}), rows: [...(city.rangeResults[aliasId]?.rows || []), { ...row.blockResults[String(table.blockId)].rows[0], date: normalized.dataDate }], columnsMeta: table.columnsMeta };
    }
  }
  return result;
}

export { CONTRACTS };
