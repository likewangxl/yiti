const SOURCE_UNITS = Object.freeze(['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION', 'COUNT', 'TEN_THOUSAND_COUNT', 'PERCENT', 'RATIO']);
const UNIT_ALIASES = Object.freeze({ 元: 'YUAN', 人民币元: 'YUAN', CNY: 'YUAN', RMB: 'YUAN', YUAN: 'YUAN', 万元: 'TEN_THOUSAND', TEN_THOUSAND: 'TEN_THOUSAND', 亿元: 'HUNDRED_MILLION', HUNDRED_MILLION: 'HUNDRED_MILLION', 个: 'COUNT', 户: 'COUNT', 人: 'COUNT', COUNT: 'COUNT', 万户: 'TEN_THOUSAND_COUNT', TEN_THOUSAND_COUNT: 'TEN_THOUSAND_COUNT', 百分比: 'PERCENT', 百分数: 'PERCENT', '%': 'PERCENT', PERCENT: 'PERCENT', 比例: 'RATIO', RATIO: 'RATIO' });
const UNIT_KINDS = Object.freeze({ YUAN: 'amount', TEN_THOUSAND: 'amount', HUNDRED_MILLION: 'amount', COUNT: 'count', TEN_THOUSAND_COUNT: 'count', PERCENT: 'ratio', RATIO: 'ratio' });
const UNIT_SCALE = Object.freeze({ YUAN: 1, TEN_THOUSAND: 1e4, HUNDRED_MILLION: 1e8, COUNT: 1, TEN_THOUSAND_COUNT: 1e4, PERCENT: 1, RATIO: 1 });

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

export function canonicalComparisonUnit(value) {
  const raw = text(value);
  return UNIT_ALIASES[raw] || UNIT_ALIASES[raw.toUpperCase()] || null;
}

function finite(value) {
  if (value === null || value === undefined || typeof value === 'boolean' || Array.isArray(value)
      || (typeof value === 'object' && value !== null) || (typeof value === 'string' && value.trim() === '')) return null;
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : null;
}

function parseDate(value) {
  const key = text(value);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(key)) return null;
  const [year, month, day] = key.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day ? date : null;
}

export function comparisonDates(dataDate) {
  const current = parseDate(dataDate);
  if (!current) return { day: null, month: null, year: null };
  const day = new Date(current.getTime());
  day.setUTCDate(day.getUTCDate() - 1);
  const month = new Date(Date.UTC(current.getUTCFullYear(), current.getUTCMonth(), 0));
  const year = new Date(Date.UTC(current.getUTCFullYear() - 1, 11, 31));
  const format = date => `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}-${String(date.getUTCDate()).padStart(2, '0')}`;
  return { day: format(day), month: format(month), year: format(year) };
}

export function validateComparisonConfig(config, context = {}) {
  const issues = [];
  if (!isObject(config)) return ['比较配置必须是对象'];
  const allowed = new Set(['enabled', 'historyBlockId', 'valueFields', 'dateField', 'sourceUnit']);
  Object.keys(config).forEach(key => { if (!allowed.has(key)) issues.push(`比较配置包含未知字段: ${key}`); });
  if (typeof config.enabled !== 'boolean') issues.push('比较配置 enabled 必须是布尔值');
  if (config.enabled === false) {
    if (Object.keys(config).some(key => key !== 'enabled')) issues.push('关闭比较时只允许保存 enabled');
    return issues;
  }
  const historyBlockId = config.historyBlockId;
  if (!Number.isSafeInteger(historyBlockId) || historyBlockId <= 0) issues.push('比较历史区块无效');
  if (Array.isArray(context.allowedHistoryBlockIds) && !context.allowedHistoryBlockIds.includes(historyBlockId)) issues.push('比较历史区块必须来自当前屏趋势区块');
  if (!Array.isArray(config.valueFields) || config.valueFields.length < 1 || config.valueFields.length > (context.maxValueFields || 8)) issues.push('比较指标字段数量必须在允许范围内');
  const fields = Array.isArray(config.valueFields) ? config.valueFields : [];
  const normalizedFields = fields.map(field => typeof field === 'string' ? field.trim() : field);
  if (new Set(normalizedFields).size !== normalizedFields.length) issues.push('比较指标字段不能重复');
  if (fields.some(field => typeof field !== 'string' || !field.trim() || field.length > 100)) issues.push('比较指标字段不能为空或过长');
  if (context.componentType === 'COMPLETION' && fields.length > 1) issues.push('完成率比较不能合计多个字段');
  if (typeof config.dateField !== 'string' || !config.dateField.trim() || config.dateField.length > 100) issues.push('比较日期字段不能为空或过长');
  const sourceUnit = text(config.sourceUnit);
  if (!SOURCE_UNITS.includes(sourceUnit)) issues.push('比较源单位不合法');
  if (context.componentType === 'COMPLETION' && sourceUnit && !['PERCENT', 'RATIO'].includes(sourceUnit)) issues.push('完成率比较源单位必须是百分数或比例');
  return issues;
}

function normalize(value, unit, targetKind) {
  const number = finite(value);
  const canonical = canonicalComparisonUnit(unit);
  if (number === null || !canonical || UNIT_KINDS[canonical] !== targetKind) return null;
  if (targetKind === 'ratio') return canonical === 'RATIO' ? number * 100 : number;
  return number * UNIT_SCALE[canonical];
}

function rowUnit(row, field, sourceUnit, unitByField) {
  return row?.unitByField?.[field] || row?.units?.[field] || row?.[`${field}Unit`] || unitByField?.[field] || row?.unit || sourceUnit;
}

export function computeExplicitComparison({ currentDate, period = 'month', mainValue, mainUnit, rows, unitByField, valueFields, dateField, sourceUnit }) {
  const dates = comparisonDates(currentDate);
  const referenceDate = dates[period] || null;
  const targetUnit = canonicalComparisonUnit(mainUnit);
  const configuredSourceUnit = canonicalComparisonUnit(sourceUnit);
  if (!referenceDate || !targetUnit || !configuredSourceUnit || !Array.isArray(rows) || !Array.isArray(valueFields) || !valueFields.length) return { state: 'NO_VALUE', value: null, referenceDate };
  const targetKind = UNIT_KINDS[targetUnit];
  const sourceKind = UNIT_KINDS[configuredSourceUnit];
  if (!targetKind || targetKind !== sourceKind) return { state: 'NO_VALUE', value: null, referenceDate };
  const dated = rows.filter(row => text(row?.[dateField]) || text(row?.date) || text(row?.data_date));
  const rowDate = row => text(row?.[dateField]);
  const currentRows = dated.filter(row => rowDate(row) === text(currentDate));
  const previousRows = dated.filter(row => rowDate(row) === referenceDate);
  if (currentRows.length !== 1 || previousRows.length !== 1) return { state: 'NO_VALUE', value: null, referenceDate };
  const sum = row => {
    let total = 0;
    for (const field of valueFields) {
      if (!Object.prototype.hasOwnProperty.call(row, field)) return null;
      const value = normalize(row[field], rowUnit(row, field, configuredSourceUnit, unitByField), sourceKind);
      if (value === null) return null;
      total += value;
    }
    return total;
  };
  const current = sum(currentRows[0]);
  const previous = sum(previousRows[0]);
  const main = normalize(mainValue, targetUnit, targetKind);
  if (current === null || previous === null || main === null) return { state: 'NO_VALUE', value: null, referenceDate };
  const tolerance = Math.max(1e-9, Math.abs(main) * 1e-9);
  if (Math.abs(current - main) > tolerance) return { state: 'NO_VALUE', value: null, referenceDate };
  const deltaBase = current - previous;
  const value = targetKind === 'ratio' ? deltaBase : deltaBase / UNIT_SCALE[targetUnit];
  return { state: 'READY', value, rawValue: value, unit: targetKind === 'ratio' ? 'PERCENT' : targetUnit, referenceDate };
}

export { SOURCE_UNITS, UNIT_KINDS };
