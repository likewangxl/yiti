import { SETTLEMENT_DEPOSIT_MAPPINGS } from './settlementDepositMapping.js';

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

const SETTLEMENT_SAMPLES = Object.freeze({
  current: Object.freeze({ corpSettlementDeposit: 500000000, retailSettlementDeposit: 303456700 }),
  day: Object.freeze({ corpSettlementDeposit: 500000000, retailSettlementDeposit: 302201700 }),
  month: Object.freeze({ corpSettlementDeposit: 500000000, retailSettlementDeposit: 306913400 }),
  year: Object.freeze({ corpSettlementDeposit: 430000000, retailSettlementDeposit: 270000000 })
});

function ensureSettlementUnit(block, field) {
  const unitByField = isObject(block?.unitByField) ? block.unitByField : {};
  if (!own(unitByField, field) || blank(unitByField[field])) {
    block.unitByField = { ...unitByField, [field]: 'YUAN' };
  }
}

function fillSettlementCurrent(block, dataDate, fields) {
  if (!isObject(block) || !parseUtcDate(dataDate)) return false;
  let changed = false;
  for (const mapping of SETTLEMENT_DEPOSIT_MAPPINGS) {
    const value = SETTLEMENT_SAMPLES.current[mapping.semantic];
    let fieldChanged = false;
    const currentRows = rowsOf(block).filter(row => dateOf(row) === dataDate);
    const rowHasSource = currentRows.some(row => !blank(row[mapping.fieldAlias]) || !blank(row[mapping.physicalColumn]));
    if (blank(block[mapping.fieldAlias]) && blank(block[mapping.physicalColumn]) && !rowHasSource) {
      block[mapping.fieldAlias] = value;
      fieldChanged = true;
    }
    for (const row of currentRows) {
      if (dateOf(row) !== dataDate || !blank(row[mapping.fieldAlias]) || !blank(row[mapping.physicalColumn])) continue;
      row[mapping.fieldAlias] = value;
      fieldChanged = true;
    }
    if (fieldChanged) {
      ensureSettlementUnit(block, mapping.fieldAlias);
      fields.add(mapping.fieldAlias);
      changed = true;
    }
  }
  return changed;
}

function settlementSampleForDate(dataDate, date) {
  if (date === previousDay(dataDate)) return SETTLEMENT_SAMPLES.day;
  if (date === previousMonthEnd(dataDate)) return SETTLEMENT_SAMPLES.month;
  if (date === previousYearEnd(dataDate)) return SETTLEMENT_SAMPLES.year;
  return null;
}

function fillSettlementHistory(block, dataDate, fields) {
  if (!isObject(block) || !parseUtcDate(dataDate) || !Array.isArray(block.rows)) return false;
  let changed = false;
  for (const date of [previousDay(dataDate), previousMonthEnd(dataDate), previousYearEnd(dataDate)]) {
    const sample = settlementSampleForDate(dataDate, date);
    if (!sample) continue;
    let matches = block.rows.filter(row => dateOf(row) === date);
    if (!matches.length) {
      const row = { data_date: date };
      block.rows.push(row);
      matches = [row];
      changed = true;
    }
    for (const mapping of SETTLEMENT_DEPOSIT_MAPPINGS) {
      const value = sample[mapping.semantic];
      let fieldChanged = false;
      for (const row of matches) {
        if (blank(row[mapping.fieldAlias]) && blank(row[mapping.physicalColumn])) {
          row[mapping.fieldAlias] = value;
          fieldChanged = true;
        }
      }
      if (fieldChanged) {
        ensureSettlementUnit(block, mapping.fieldAlias);
        fields.add(mapping.fieldAlias);
        changed = true;
      }
    }
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
  if (!isObject(block) || !Array.isArray(block.rows) || !block.rows.length) return false;
  const rows = block.rows;
  const originalRows = rows.slice();
  const originalCount = originalRows.length;
  const validDataDate = parseUtcDate(dataDate) ? dataDate : '';
  const currentDate = validDataDate && originalRows.some(row => dateOf(row) === validDataDate)
    ? validDataDate : dateOf(originalRows.at(-1));
  let changed = false;
  const historical = new Map(TREND_FIELDS.map(field => [field, 'YUAN']));
  for (const [field, unit] of headers) historical.set(field, unit);
  const anchors = new Map([...historical.keys()]
    .map(field => [field, currentValue(header, field, dataDate)])
    .filter(([, anchor]) => anchor !== null));
  if (!anchors.size) return false;
  if (validDataDate) {
    const targetDates = [];
    for (let offset = 0; offset < 365; offset += 1) {
      const date = parseUtcDate(validDataDate);
      date.setUTCDate(date.getUTCDate() - offset);
      targetDates.push(isoDate(date));
    }
    const yearEnd = previousYearEnd(validDataDate);
    if (yearEnd && !targetDates.includes(yearEnd)) targetDates.push(yearEnd);
    const existingDates = new Set(rows.map(dateOf));
    for (const date of targetDates) {
      if (existingDates.has(date)) continue;
      rows.push({ data_date: date });
      existingDates.add(date);
      changed = true;
    }
  }
  for (const [field, unit] of historical) {
    const anchor = anchors.get(field);
    if (anchor === undefined) continue;
    let fieldChanged = false;
    originalRows.forEach((row, index) => {
      if (!blank(row[field])) return;
      const rowDate = dateOf(row);
      const value = rowDate === currentDate
        ? anchor
        : anchor * (0.94 + 0.06 * index / Math.max(1, originalCount - 1));
      row[field] = value;
      fields.add(field);
      fieldChanged = true;
      changed = true;
    });
    rows.slice(originalCount).forEach(row => {
      if (!blank(row[field])) return;
      const rowDate = dateOf(row);
      const parsedRowDate = parseUtcDate(rowDate);
      const parsedCurrentDate = parseUtcDate(validDataDate || currentDate);
      const dayDistance = parsedRowDate && parsedCurrentDate
        ? Math.max(0, Math.round((parsedCurrentDate.getTime() - parsedRowDate.getTime()) / 86400000))
        : 1;
      const progress = Math.min(1, dayDistance / 364);
      const value = rowDate === (validDataDate || currentDate)
        ? anchor : anchor * (0.88 + 0.12 * (1 - progress));
      row[field] = value;
      fields.add(field);
      fieldChanged = true;
      changed = true;
    });
    if (fieldChanged) markUnit(block, field, unit);
  }
  return changed;
}

const TOTAL_AMOUNT_UNITS = Object.freeze({
  YUAN: true, 元: true, 人民币元: true, CNY: true, RMB: true,
  TEN_THOUSAND: true, 万元: true, HUNDRED_MILLION: true, 亿元: true
});

function supplementTotalYearComparisons(model, dataDate) {
  const kpis = Array.isArray(model?.kpis) ? model.kpis : [];
  const referenceDate = previousYearEnd(dataDate);
  if (!kpis.length || !referenceDate) return { kpis: model?.kpis, changed: false };
  let changed = false;
  const nextKpis = kpis.map(kpi => {
    if (!isObject(kpi) || !['deposit', 'loan'].includes(text(kpi.key))) return kpi;
    const value = number(kpi.value);
    const unit = text(kpi.unit ?? kpi.sourceUnit);
    const currentDate = text(kpi.dataDate ?? kpi.date ?? kpi.sourceDate ?? dataDate);
    const comparisons = isObject(kpi.comparisons) ? kpi.comparisons : {};
    if (value === null || !TOTAL_AMOUNT_UNITS[unit] || !parseUtcDate(currentDate)
      || currentDate !== dataDate || Object.prototype.hasOwnProperty.call(comparisons, 'year')) return kpi;
    changed = true;
    return {
      ...kpi,
      comparisons: {
        ...comparisons,
        year: { value: value * 0.12, unit, referenceDate }
      }
    };
  });
  return { kpis: changed ? nextKpis : model?.kpis, changed };
}

function allowed(source, presentation, model) {
  if (presentation.template !== TEMPLATE) return false;
  const state = source?.state ?? presentation.state;
  const classification = source?.dataClassification
    ?? source?.canvasStyle?.dataClassification
    ?? source?.renderPackage?.canvasStyle?.dataClassification
    ?? presentation.dataClassification;
  if (!['draft', 'published'].includes(text(state).toLowerCase())) return false;
  if (classification !== undefined && text(classification).toUpperCase() !== 'TEST') return false;
  const orgCode = [source?.orgCode, source?.context?.orgCode, presentation.orgCode,
    model?.orgCode, model?.identity?.orgCode].find(value => text(value));
  const cityCode = [source?.cityCode, source?.context?.cityCode, presentation.cityCode,
    model?.cityCode, model?.identity?.cityCode].find(value => text(value));
  const empId = [source?.empId, source?.context?.empId, model?.empId, model?.identity?.empId]
    .find(value => text(value));
  if (orgCode || cityCode || empId) return false;
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
  const dataDate = text(model.dataDate);
  const kpiSupplement = supplementTotalYearComparisons(model, dataDate);
  const nextBlocks = { ...model.blockResults };
  const headers = configuredHeaders(presentation);
  const headerEntry = blockEntry(model, HEADER_BLOCK);
  const nextHeader = headerEntry ? clone(headerEntry.block) : null;

  if (headerEntry && nextHeader) {
    const fieldsBeforeHeader = fields.size;
    for (const [field, unit] of headers) {
      if (CORP_RATES[field] !== undefined) {
        const value = currentValue(nextHeader, field, dataDate) ?? CORP_RATES[field];
        ensureField(nextHeader, field, value, unit, fields);
      }
    }
    if (headers.has('测试_直营中间业务收入')) {
      const revenue = currentValue(nextHeader, '测试_直营营业收入', dataDate);
      const existingFee = currentValue(nextHeader, '测试_直营中间业务收入', dataDate);
      ensureField(nextHeader, '测试_直营中间业务收入', existingFee ?? (revenue === null ? 5000000 : revenue * 0.27), 'YUAN', fields);
    }
    const settlementChanged = fillSettlementCurrent(nextHeader, dataDate, fields);
    if (settlementChanged || fields.size > fieldsBeforeHeader) nextBlocks[headerEntry.key] = nextHeader;
  }

  const income = incomeConfig(presentation);
  const compositionEntry = blockEntry(model, COMPOSITION_BLOCK);
  if (income && compositionEntry) {
    const nextComposition = clone(compositionEntry.block);
    if (fillComposition(nextComposition, income, fields)) nextBlocks[compositionEntry.key] = nextComposition;
  }

  const trendEntry = blockEntry(model, TREND_BLOCK);
  const trendHeader = nextHeader || (headerEntry ? headerEntry.block : null);
  if (trendEntry) {
    const nextTrend = clone(trendEntry.block);
    let trendChanged = false;
    if (trendHeader && hasMainTrend(presentation)) {
      trendChanged = supplementTrend(nextTrend, trendHeader, headers, dataDate, fields);
    }
    trendChanged = fillSettlementHistory(nextTrend, dataDate, fields) || trendChanged;
    if (trendChanged) nextBlocks[trendEntry.key] = nextTrend;
  }

  const changed = kpiSupplement.changed
    || Object.keys(nextBlocks).some(key => nextBlocks[key] !== model.blockResults[key]);
  if (!changed) return { model, fields: [] };
  const nextModel = { ...model, blockResults: nextBlocks };
  if (kpiSupplement.changed) nextModel.kpis = kpiSupplement.kpis;
  return { model: nextModel, fields: [...fields] };
}
