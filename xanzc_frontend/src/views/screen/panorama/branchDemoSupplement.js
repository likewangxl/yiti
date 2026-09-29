const TEMPLATE = 'branch-overview-v1';
const HEADER_BLOCK = 31;
const COMPOSITION_BLOCK = 64;
const TREND_BLOCK = 57;

const HEADER_UNITS = Object.freeze({
  测试_直营零售存款: 'YUAN',
  测试_零售存款目标完成率: 'PERCENT',
  测试_直营零售贷款: 'YUAN',
  测试_零售贷款目标完成率: 'PERCENT',
  测试_直营对公存款: 'YUAN',
  测试_对公存款目标完成率: 'PERCENT',
  测试_直营对公贷款: 'YUAN',
  测试_对公贷款目标完成率: 'PERCENT',
  测试_直营营业收入: 'YUAN',
  测试_直营中间业务收入: 'YUAN'
});

const CORP_RATES = Object.freeze({
  测试_对公存款目标完成率: 93.6,
  测试_对公贷款目标完成率: 88.2
});

const TREND_FIELDS = Object.freeze([
  '测试_直营零售存款', '测试_直营零售贷款',
  '测试_直营对公存款', '测试_直营对公贷款'
]);

// 结算性存款是 TEST 视觉演示样本，独立于其他存款指标，不从现有字段推算。
const SETTLEMENT_DEPOSIT_VALUE = 803456700;
const SETTLEMENT_DEPOSIT_BASES = Object.freeze({
  day: 802201700,
  month: 806913400,
  year: 700000000
});

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function own(value, key) {
  return isObject(value) && Object.prototype.hasOwnProperty.call(value, key);
}

function blank(value) {
  return value === null || value === undefined || (typeof value === 'string' && value.trim() === '');
}

function number(value) {
  if (value === null || value === undefined || typeof value === 'boolean') return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  const parsed = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function clone(value) {
  if (Array.isArray(value)) return value.map(clone);
  if (isObject(value)) return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, clone(item)]));
  return value;
}

function presentationOf(source) {
  if (!isObject(source)) return {};
  if (source.displaySchemaVersion !== undefined || source.display) return source;
  if (isObject(source.displayPresentation)) return presentationOf(source.displayPresentation);
  if (isObject(source.presentation)) return presentationOf(source.presentation);
  if (isObject(source.canvasStyle?.presentation)) return presentationOf(source.canvasStyle.presentation);
  if (isObject(source.renderPackage?.canvasStyle?.presentation)) return presentationOf(source.renderPackage.canvasStyle.presentation);
  return {};
}

function blockEntry(model, id) {
  const blocks = model?.blockResults;
  if (!isObject(blocks)) return null;
  for (const key of [id, String(id)]) {
    if (own(blocks, key) && isObject(blocks[key])) return { key: String(key), block: blocks[key] };
  }
  return null;
}

function rowsOf(block) {
  return Array.isArray(block?.rows) ? block.rows : [];
}

function dateOf(row) {
  return text(row?.date ?? row?.data_date ?? row?.dataDate);
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

function previousDay(value) {
  const date = parseUtcDate(value);
  if (!date) return '';
  date.setUTCDate(date.getUTCDate() - 1);
  return isoDate(date);
}

function previousMonthEnd(value) {
  const date = parseUtcDate(value);
  if (!date) return '';
  date.setUTCDate(0);
  return isoDate(date);
}

function previousYearEnd(value) {
  const date = parseUtcDate(value);
  if (!date) return '';
  date.setUTCFullYear(date.getUTCFullYear() - 1, 11, 31);
  return isoDate(date);
}

function settlementKpi(dataDate) {
  return {
    key: 'settlementDeposit',
    label: '结算性存款',
    status: '演示数据',
    isDemo: true,
    value: SETTLEMENT_DEPOSIT_VALUE,
    unit: 'YUAN',
    dataDate,
    comparisons: {
      day: { value: SETTLEMENT_DEPOSIT_VALUE - SETTLEMENT_DEPOSIT_BASES.day, unit: 'YUAN', referenceDate: previousDay(dataDate) },
      month: { value: SETTLEMENT_DEPOSIT_VALUE - SETTLEMENT_DEPOSIT_BASES.month, unit: 'YUAN', referenceDate: previousMonthEnd(dataDate) },
      year: { value: SETTLEMENT_DEPOSIT_VALUE - SETTLEMENT_DEPOSIT_BASES.year, unit: 'YUAN', referenceDate: previousYearEnd(dataDate) }
    }
  };
}

function supplementSettlementKpi(model, dataDate, fields) {
  const kpis = Array.isArray(model?.kpis) ? model.kpis : [];
  if (kpis.some(item => text(item?.key) === 'settlementDeposit') || !parseUtcDate(dataDate)) {
    return { kpis: model?.kpis, changed: false };
  }
  fields.add('settlementDeposit');
  return { kpis: [...kpis, settlementKpi(dataDate)], changed: true };
}

function currentValue(block, field, dataDate = '') {
  const direct = number(block?.[field]);
  if (direct !== null) return direct;
  const rows = rowsOf(block);
  const current = rows.find(row => dateOf(row) === dataDate && number(row?.[field]) !== null);
  if (current) return number(current[field]);
  for (let index = rows.length - 1; index >= 0; index -= 1) {
    const value = number(rows[index]?.[field]);
    if (value !== null) return value;
  }
  return null;
}

function markUnit(block, field, unit) {
  const unitByField = isObject(block.unitByField) ? block.unitByField : {};
  if (!own(unitByField, field) || blank(unitByField[field])) {
    block.unitByField = { ...unitByField, [field]: unit };
  }
}

function ensureField(block, field, value, unit, fields) {
  if (!isObject(block) || number(value) === null) return false;
  let changed = false;
  if (blank(block[field])) {
    block[field] = value;
    changed = true;
  }
  for (const row of rowsOf(block)) {
    if (blank(row[field])) {
      row[field] = value;
      changed = true;
    }
  }
  if (changed) {
    markUnit(block, field, unit);
    fields.add(field);
  }
  return changed;
}

function configuredHeaders(presentation) {
  const result = new Map();
  const components = Array.isArray(presentation?.display?.components) ? presentation.display.components : [];
  for (const component of components) {
    if (component?.componentType !== 'METRIC_CARD' || component.visible === false
      || text(component.layoutRegion).toUpperCase() !== 'HEADER') continue;
    const ref = Array.isArray(component.dataRefs) ? component.dataRefs[0] : null;
    const field = text(component.content?.mainField);
    if (Number(ref?.blockId) === HEADER_BLOCK && HEADER_UNITS[field]) result.set(field, HEADER_UNITS[field]);
  }
  return result;
}

function hasMainTrend(presentation) {
  const components = Array.isArray(presentation?.display?.components) ? presentation.display.components : [];
  return components.some(component => {
    if (component?.componentType !== 'TREND' || component.visible === false
      || text(component.layoutRegion).toUpperCase() !== 'CENTER') return false;
    const ref = Array.isArray(component.dataRefs) ? component.dataRefs[0] : null;
    if (Number(ref?.blockId) !== TREND_BLOCK) return false;
    const slot = text(ref?.slot || ref?.bindingKey || ref?.semantic).toUpperCase();
    return !['BRANCHTREND', 'BRANCH_TREND'].includes(slot)
      && (text(ref?.role).toUpperCase() === 'PRIMARY' || /57$/.test(text(component.componentId)));
  });
}

function incomeConfig(presentation) {
  const components = Array.isArray(presentation?.display?.components) ? presentation.display.components : [];
  for (const component of components) {
    if (component?.componentType !== 'COMPOSITION_TABS' || component.visible === false) continue;
    const ref = Array.isArray(component.dataRefs) ? component.dataRefs[0] : null;
    if (Number(ref?.blockId) !== COMPOSITION_BLOCK) continue;
    const tabs = Array.isArray(component.content?.tabs) ? component.content.tabs : [];
    const tab = tabs.find(item => text(item?.tabKey || item?.key).toLowerCase() === 'income');
    if (!tab) continue;
    const corporateField = text(tab.corporateField);
    const retailField = text(tab.retailField);
    const totalField = text(tab.totalField);
    if (corporateField && retailField && totalField) return { corporateField, retailField, totalField };
  }
  return null;
}

function fillComposition(block, config, fields) {
  const fallbackTotal = number(block[config.totalField]);
  let changed = false;
  const fill = (target, total) => {
    if (!isObject(target) || total === null) return false;
    const corporateRaw = target[config.corporateField];
    const retailRaw = target[config.retailField];
    const corporateBlank = blank(corporateRaw);
    const retailBlank = blank(retailRaw);
    if ((!corporateBlank && number(corporateRaw) === null) || (!retailBlank && number(retailRaw) === null)) return false;
    if (!corporateBlank && !retailBlank) return false;
    const corporate = number(corporateRaw);
    const retail = number(retailRaw);
    const nextCorporate = corporate !== null ? corporate : (retail !== null ? total - retail : total * 0.6);
    const nextRetail = retail !== null ? retail : total - nextCorporate;
    if (corporateBlank) {
      target[config.corporateField] = nextCorporate;
      fields.add(config.corporateField);
      markUnit(block, config.corporateField, 'YUAN');
      changed = true;
    }
    if (retailBlank) {
      target[config.retailField] = nextRetail;
      fields.add(config.retailField);
      markUnit(block, config.retailField, 'YUAN');
      changed = true;
    }
    return true;
  };
  fill(block, fallbackTotal);
  for (const row of rowsOf(block)) fill(row, number(row[config.totalField]) ?? fallbackTotal);
  return changed;
}

function supplementTrend(block, header, headers, dataDate, fields) {
  const rows = rowsOf(block);
  if (!rows.length) return false;
  const currentDate = dataDate && rows.some(row => dateOf(row) === dataDate)
    ? dataDate : dateOf(rows.at(-1));
  const count = rows.length;
  let changed = false;
  const historical = new Map(TREND_FIELDS.map(field => [field, 'YUAN']));
  for (const [field, unit] of headers) historical.set(field, unit);
  for (const [field, unit] of historical) {
    const anchor = currentValue(header, field, dataDate);
    if (anchor === null) continue;
    let fieldChanged = false;
    rows.forEach((row, index) => {
      if (!blank(row[field])) return;
      const value = dateOf(row) === currentDate ? anchor : anchor * (0.94 + 0.06 * index / Math.max(1, count - 1));
      row[field] = value;
      fields.add(field);
      fieldChanged = true;
      changed = true;
    });
    if (fieldChanged) markUnit(block, field, unit);
  }
  return changed;
}

function allowed(source, presentation, model) {
  if (presentation.template !== TEMPLATE) return false;
  const state = source?.state ?? presentation.state;
  const classification = source?.dataClassification
    ?? source?.canvasStyle?.dataClassification
    ?? source?.renderPackage?.canvasStyle?.dataClassification
    ?? presentation.dataClassification;
  if (state !== undefined && text(state).toLowerCase() !== 'draft') return false;
  if (classification !== undefined && text(classification).toUpperCase() !== 'TEST') return false;
  if (text(source?.orgCode ?? source?.context?.orgCode ?? presentation.orgCode ?? model?.orgCode)
    || text(source?.cityCode ?? source?.context?.cityCode ?? presentation.cityCode ?? model?.cityCode)) return false;
  if (source?.loading === true || model?.loading === true || text(source?.error) || text(model?.error)
    || source?.permissionStatus || model?.permissionStatus) return false;
  return true;
}

/**
 * 为演示视图补固定数值，所有已有非空值均保留。
 * Runtime 负责确认 TEST 草稿、全辖范围及取数/权限状态；此处只适配展示数据。
 */
export function supplementBranchDemoModel(model, sourcePresentation) {
  const source = isObject(sourcePresentation) ? sourcePresentation : {};
  const presentation = presentationOf(source);
  if (!allowed(source, presentation, model) || !isObject(model?.blockResults)) return { model, fields: [] };

  const fields = new Set();
  const settlement = supplementSettlementKpi(model, model.dataDate, fields);
  const nextBlocks = { ...model.blockResults };
  const headers = configuredHeaders(presentation);
  const headerEntry = blockEntry(model, HEADER_BLOCK);
  let nextHeader = null;

  if (headerEntry && headers.size) {
    nextHeader = clone(headerEntry.block);
    for (const [field, unit] of headers) {
      if (CORP_RATES[field] !== undefined) {
        const value = currentValue(nextHeader, field, text(model.dataDate)) ?? CORP_RATES[field];
        ensureField(nextHeader, field, value, unit, fields);
      }
    }
    if (headers.has('测试_直营中间业务收入')) {
      const revenue = currentValue(nextHeader, '测试_直营营业收入', text(model.dataDate));
      const existingFee = currentValue(nextHeader, '测试_直营中间业务收入', text(model.dataDate));
      ensureField(nextHeader, '测试_直营中间业务收入', existingFee ?? (revenue === null ? 5000000 : revenue * 0.27), 'YUAN', fields);
    }
    if (fields.size) nextBlocks[headerEntry.key] = nextHeader;
  }

  const income = incomeConfig(presentation);
  const compositionEntry = blockEntry(model, COMPOSITION_BLOCK);
  if (income && compositionEntry) {
    const nextComposition = clone(compositionEntry.block);
    if (fillComposition(nextComposition, income, fields)) nextBlocks[compositionEntry.key] = nextComposition;
  }

  const trendEntry = blockEntry(model, TREND_BLOCK);
  const trendHeader = nextHeader || (headerEntry ? headerEntry.block : null);
  if (trendHeader && trendEntry && hasMainTrend(presentation)) {
    const nextTrend = clone(trendEntry.block);
    if (supplementTrend(nextTrend, trendHeader, headers, text(model.dataDate), fields)) nextBlocks[trendEntry.key] = nextTrend;
  }

  const changed = settlement.changed
    || Object.keys(nextBlocks).some(key => nextBlocks[key] !== model.blockResults[key]);
  if (!changed) return { model, fields: [] };
  const nextModel = { ...model, blockResults: nextBlocks };
  if (settlement.changed) nextModel.kpis = settlement.kpis;
  return { model: nextModel, fields: [...fields] };
}
