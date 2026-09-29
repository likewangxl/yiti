const BLOCK_CURRENT = 31;
const BLOCK_TREND = 57;

const UNIT_ALIASES = Object.freeze({
  YUAN: 'YUAN', 元: 'YUAN', 人民币元: 'YUAN', CNY: 'YUAN', RMB: 'YUAN',
  TEN_THOUSAND: 'TEN_THOUSAND', 万元: 'TEN_THOUSAND',
  HUNDRED_MILLION: 'HUNDRED_MILLION', 亿元: 'HUNDRED_MILLION'
});

const UNIT_SCALES = Object.freeze({ YUAN: 1, TEN_THOUSAND: 1e4, HUNDRED_MILLION: 1e8 });

/** 指标定义来自后端宽表查询：metricName 是返回列别名，val_N 是物理槽位。 */
export const SETTLEMENT_DEPOSIT_MAPPINGS = Object.freeze([
  Object.freeze({
    metricCode: 'M_0298',
    physicalColumn: 'val_26',
    fieldAlias: '对公结算性存款余额-机构',
    semantic: 'corpSettlementDeposit'
  }),
  Object.freeze({
    metricCode: 'M_0331',
    physicalColumn: 'val_38',
    fieldAlias: '零售结算性存款余额-机构',
    semantic: 'retailSettlementDeposit'
  })
]);

const COMPARISON_DEFINITIONS = Object.freeze([
  { key: 'day', offset: 'day' },
  { key: 'month', offset: 'month' },
  { key: 'year', offset: 'year' }
]);

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function own(value, key) {
  return isObject(value) && Object.prototype.hasOwnProperty.call(value, key);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function finite(value) {
  if (value === null || value === undefined || typeof value === 'boolean'
    || (typeof value === 'string' && value.trim() === '') || Array.isArray(value)
    || (typeof value === 'object' && value !== null)) return null;
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : null;
}

function parseUtcDate(value) {
  const date = text(value);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return null;
  const [year, month, day] = date.split('-').map(Number);
  if (year < 1 || month < 1 || month > 12 || day < 1 || day > 31) return null;
  const parsed = new Date(0);
  parsed.setUTCHours(0, 0, 0, 0);
  parsed.setUTCFullYear(year, month - 1, day);
  return parsed.getUTCFullYear() === year
    && parsed.getUTCMonth() === month - 1
    && parsed.getUTCDate() === day
    ? parsed : null;
}

function isoDate(date) {
  return [date.getUTCFullYear(), date.getUTCMonth() + 1, date.getUTCDate()]
    .map((part, index) => String(part).padStart(index === 0 ? 4 : 2, '0')).join('-');
}

function referenceDate(currentDate, offset) {
  const date = parseUtcDate(currentDate);
  if (!date) return '';
  if (offset === 'day') date.setUTCDate(date.getUTCDate() - 1);
  else if (offset === 'month') date.setUTCDate(0);
  else date.setUTCFullYear(date.getUTCFullYear() - 1, 11, 31);
  return isoDate(date);
}

function rowDate(row) {
  return text(row?.date ?? row?.data_date ?? row?.dataDate);
}

function blockOf(model, id) {
  const blocks = model?.blockResults;
  if (!isObject(blocks)) return null;
  for (const key of [id, String(id)]) {
    if (own(blocks, key) && isObject(blocks[key])) return blocks[key];
  }
  return null;
}

function rowsOf(block) {
  return Array.isArray(block?.rows) ? block.rows : [];
}

function canonicalUnit(value) {
  const raw = text(value);
  return UNIT_ALIASES[raw] || UNIT_ALIASES[raw.toUpperCase()] || null;
}

function metadataUnit(container, key) {
  if (!isObject(container)) return { present: false, value: '' };
  if (isObject(container.unitByField) && own(container.unitByField, key)) {
    return { present: true, value: text(container.unitByField[key]) };
  }
  if (isObject(container.units) && own(container.units, key)) return { present: true, value: text(container.units[key]) };
  if (Array.isArray(container.columnsMeta)) {
    const metadata = container.columnsMeta.find(item => text(item?.col ?? item?.name ?? item?.key) === key);
    if (metadata) return { present: true, value: text(metadata.unit ?? metadata.unitCode) };
  }
  if (isObject(container.columnsMeta) && isObject(container.columnsMeta[key])) {
    return { present: true, value: text(container.columnsMeta[key].unit ?? container.columnsMeta[key].unitCode) };
  }
  return { present: false, value: '' };
}

function unitFor(record, block, mapping, key) {
  // Raw val_N is safe only with explicit unitByField evidence; never infer it
  // from a block-level unit or from the mapping definition.
  if (key === mapping.physicalColumn) {
    for (const source of [record, block]) {
      if (isObject(source?.unitByField) && own(source.unitByField, key)) {
        return canonicalUnit(source.unitByField[key]);
      }
    }
    return null;
  }
  for (const candidate of [key, mapping.fieldAlias, mapping.physicalColumn]) {
    for (const source of [record, block]) {
      const evidence = metadataUnit(source, candidate);
      if (evidence.present) return canonicalUnit(evidence.value);
    }
  }
  return null;
}

function fieldValue(record, block, mapping) {
  if (!isObject(record)) return null;
  // The server returns val_N under metricName/fieldAlias. Prefer that alias
  // even when it is null or invalid; a raw slot must never silently replace it.
  const key = own(record, mapping.fieldAlias)
    ? mapping.fieldAlias
    : own(record, mapping.physicalColumn) ? mapping.physicalColumn : '';
  if (!key) return null;
  const raw = finite(record[key]);
  const unit = unitFor(record, block, mapping, key);
  if (raw === null || !unit) return null;
  return { baseValue: raw * UNIT_SCALES[unit], unit };
}

function currentRecord(block, dataDate) {
  const hasMappedField = SETTLEMENT_DEPOSIT_MAPPINGS.some(mapping =>
    own(block, mapping.fieldAlias) || own(block, mapping.physicalColumn));
  if (rowDate(block) === dataDate && hasMappedField) return block;
  const matches = rowsOf(block).filter(row => rowDate(row) === dataDate);
  return matches.length === 1 ? matches[0] : null;
}

function historyRecord(block, date) {
  const matches = rowsOf(block).filter(row => rowDate(row) === date);
  return matches.length === 1 ? matches[0] : null;
}

/**
 * 从分行 TEST 宽表中读取结算性存款。此函数是纯读取映射，不补值、不修改
 * 输入模型；所有金额先换算为元，比较值保持“当前值减基期值”的口径。
 */
export function buildSettlementDepositMetric(model = {}) {
  const dataDate = text(model?.dataDate);
  if (!parseUtcDate(dataDate)) return null;
  const currentBlock = blockOf(model, BLOCK_CURRENT);
  const current = currentRecord(currentBlock, dataDate);
  const currentValues = SETTLEMENT_DEPOSIT_MAPPINGS.map(mapping => fieldValue(current, currentBlock, mapping));
  if (currentValues.some(value => !value)) return null;

  const baseValue = currentValues.reduce((sum, value) => sum + value.baseValue, 0);
  const comparisons = {};
  const trendBlock = blockOf(model, BLOCK_TREND);
  for (const definition of COMPARISON_DEFINITIONS) {
    const targetDate = referenceDate(dataDate, definition.offset);
    const row = historyRecord(trendBlock, targetDate);
    const values = SETTLEMENT_DEPOSIT_MAPPINGS.map(mapping => fieldValue(row, trendBlock, mapping));
    if (values.some(value => !value)) continue;
    const historical = values.reduce((sum, value) => sum + value.baseValue, 0);
    comparisons[definition.key] = { baseValue: baseValue - historical, referenceDate: targetDate };
  }

  return { baseValue, date: dataDate, dateValid: true, unit: 'YUAN', comparisons };
}
