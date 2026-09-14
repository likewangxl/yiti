import { getScreenCanvas, getScreenView, queryScreenData } from '@/api/screen';
import { ALL_SLOT_ORDER, BINDING_SLOTS, validateBinding } from './bindings';
import { CORPORATE_SLOT_ORDER, CORPORATE_TEMPLATE, isCorporateBindingSlot } from './corporateBindings';
import { RETAIL_SLOT_ORDER, RETAIL_TEMPLATE } from './retailBindings';

/**
 * 代码化大屏“验证已保存草稿数据”的运行时核验服务。
 *
 * 这个服务刻意和绑定编辑器、保存动作分离：它只读取当前屏已保存的草稿，
 * 不使用编辑中的 dsId 拼接请求，也不写入画布、绑定或数据源。核验结论只
 * 描述返回结构和覆盖缺口，不能替代指标口径、单位或发布验收。
 */

export const MAX_VERIFICATION_CONCURRENCY = 3;
export const VERIFICATION_DISCLAIMER = '取数结构核验不证明指标业务口径和单位正确';

export const OVERALL_STATUS = Object.freeze({
  UNVERIFIED: 'UNVERIFIED',
  IN_PROGRESS: 'IN_PROGRESS',
  UNSAVED: 'UNSAVED',
  STRUCTURE_CHECKED: 'STRUCTURE_CHECKED',
  HAS_GAPS: 'HAS_GAPS',
  CONFIG_ERROR: 'CONFIG_ERROR',
  PERMISSION_DENIED: 'PERMISSION_DENIED',
  STALE: 'STALE',
  // 保留显式常量供调用方做反误导判断；服务不会返回“完整/全部通过”。
  COMPLETE: 'COMPLETE'
});

export const SLOT_STATUS = Object.freeze({
  UNVERIFIED: 'UNVERIFIED',
  MISSING_CONFIG: 'MISSING_CONFIG',
  CONFIG_ERROR: 'CONFIG_ERROR',
  REQUEST_ERROR: 'REQUEST_ERROR',
  PERMISSION_DENIED: 'PERMISSION_DENIED',
  STRUCTURE_ERROR: 'STRUCTURE_ERROR',
  NO_ROWS: 'NO_ROWS',
  NOT_VERIFIABLE: 'NOT_VERIFIABLE',
  VERIFIED_WITH_WARNINGS: 'VERIFIED_WITH_WARNINGS',
  STRUCTURE_VERIFIED: 'STRUCTURE_VERIFIED'
});

export const OVERALL_STATUS_LABELS = Object.freeze({
  [OVERALL_STATUS.UNVERIFIED]: '尚未验证',
  [OVERALL_STATUS.IN_PROGRESS]: '正在验证已保存草稿',
  [OVERALL_STATUS.UNSAVED]: '当前绑定未保存',
  [OVERALL_STATUS.STRUCTURE_CHECKED]: '结构核验完成（不代表业务口径正确）',
  [OVERALL_STATUS.HAS_GAPS]: '存在数据或结构缺口',
  [OVERALL_STATUS.CONFIG_ERROR]: '配置无法核验',
  [OVERALL_STATUS.PERMISSION_DENIED]: '无权读取已保存草稿数据',
  [OVERALL_STATUS.STALE]: '画布已变化，本次结果已废弃'
});

export const SLOT_STATUS_LABELS = Object.freeze({
  [SLOT_STATUS.UNVERIFIED]: '尚未验证',
  [SLOT_STATUS.MISSING_CONFIG]: '缺少配置',
  [SLOT_STATUS.CONFIG_ERROR]: '配置错误',
  [SLOT_STATUS.REQUEST_ERROR]: '取数失败',
  [SLOT_STATUS.PERMISSION_DENIED]: '无权读取',
  [SLOT_STATUS.STRUCTURE_ERROR]: '结构错误',
  [SLOT_STATUS.NO_ROWS]: '无数据行',
  [SLOT_STATUS.NOT_VERIFIABLE]: '无法核验',
  [SLOT_STATUS.VERIFIED_WITH_WARNINGS]: '结构通过，存在缺口',
  [SLOT_STATUS.STRUCTURE_VERIFIED]: '结构通过'
});

const SINGLE_VALUE_SLOTS = new Set([
  'deposit', 'depositIncrease', 'depositAverage', 'loan', 'customers', 'revenue', 'rate',
  'retailAum', 'retailDeposit', 'retailDepositAverage', 'retailRevenue',
  'retailValueCustomers', 'retailLoan', 'retailNplRate',
  'corpDeposit', 'corpDepositAverage', 'corpLoan', 'corpRevenue', 'corpCustomers', 'corpNplRate'
]);
const TREND_SLOTS = new Set(['trend', 'branchTrend', 'retailTrend', 'corpTrend']);
const DIMENSION_FIELDS = new Set([
  'date', 'orgCode', 'orgName', 'cityCode', 'cityName', 'ownerOperatingOrgCode',
  'parentOrgCode', 'lng', 'lat', 'coordSys', 'located', 'label', 'name', 'owner', 'deadline'
]);
const METRIC_FIELDS = new Set([
  'value', 'change', 'deposit', 'depositIncrease', 'depositAverage', 'loan',
  'customers', 'revenue', 'rate', 'target', 'actual', 'count', 'increase', 'average',
  'corporate', 'retail', 'aum', 'income', 'nplRate', 'valueRate'
]);
const FATAL_ISSUES = new Set([
  'INVALID_SHAPE', 'INVALID_COLUMNS', 'INVALID_ROWS', 'MISSING_COLUMN',
  'INVALID_NUMBER', 'MULTIPLE_ROWS', 'DUPLICATE_DATE', 'DUPLICATE_ORG_CODE',
  'UNAUTHORIZED_ORG_CODE'
]);
const GAP_ISSUES = new Set([
  'NULL_VALUE', 'DATE_UNVERIFIABLE', 'MISSING_AUTHORIZED_DIRECTORY',
  'MISSING_AUTHORIZED_INSTITUTION', 'POSSIBLY_TRUNCATED',
  'DATE_MISMATCH', 'NO_ROWS', 'INVALID_DATE', 'CONSTANT_SOURCE'
]);

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function valueOf(value) {
  return value && typeof value === 'object' && value.__v_isRef === true
    ? value.value : value;
}

function parseObject(value, fallback = {}) {
  const source = valueOf(value);
  if (isObject(source)) return source;
  if (typeof source !== 'string' || !source.trim()) return fallback;
  try {
    const parsed = JSON.parse(source);
    return isObject(parsed) ? parsed : fallback;
  } catch {
    return fallback;
  }
}

function text(value) {
  return value === undefined || value === null ? '' : String(value).trim();
}

function positiveId(value) {
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isSafeInteger(number) && number > 0 ? number : null;
}

function integerVersion(value) {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 ? value : null;
}

function pick(source, ...keys) {
  if (!isObject(source)) return undefined;
  for (const key of keys) {
    if (source[key] !== undefined && source[key] !== null) return source[key];
  }
  return undefined;
}

function stableSerialize(value) {
  if (Array.isArray(value)) return `[${value.map(stableSerialize).join(',')}]`;
  if (!isObject(value)) return JSON.stringify(value);
  return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${stableSerialize(value[key])}`).join(',')}}`;
}

function errorStatus(error) {
  const raw = error?.status
    ?? error?.response?.status
    ?? error?.response?.data?.status
    ?? error?.response?.data?.code
    ?? error?.code;
  const status = Number(raw);
  return status === 401 || status === 403 ? status : null;
}

function errorMessage(error, fallback = '读取已保存草稿数据失败') {
  return text(error?.message || error?.response?.data?.message || error?.response?.data?.msg) || fallback;
}

function unwrapResponse(value) {
  if (!isObject(value)) return value;
  if (value.columns === undefined && value.rows === undefined && isObject(value.data)) return value.data;
  return value;
}

function screenPackage(view) {
  const raw = pick(view, 'renderPackage', 'render_package', 'renderPackageJson', 'render_package_json');
  return parseObject(raw, {});
}

function flattenComponents(components, output = []) {
  for (const component of Array.isArray(components) ? components : []) {
    if (component?.component === 'Group') flattenComponents(component.children, output);
    else output.push(component);
  }
  return output;
}

function draftComponents(canvas) {
  const raw = pick(canvas, 'canvasDraftJson', 'canvas_draft_json', 'draftJson', 'draft');
  const draft = parseObject(raw, {});
  return flattenComponents(draft.components || canvas?.components);
}

function packageComponents(view) {
  const pkg = screenPackage(view);
  return flattenComponents(pkg.components || view?.components);
}

function componentSlot(component) {
  return text(component?.propValue?.bindingKey || component?.prop_value?.bindingKey);
}

function blockMap(canvas) {
  const map = new Map();
  for (const block of Array.isArray(canvas?.blocks) ? canvas.blocks : []) {
    const id = positiveId(block?.id ?? block?.blockId ?? block?.block_id);
    if (id) map.set(id, block);
  }
  return map;
}

function bindingSource(block, component, snapshot) {
  const snapshotBind = snapshot?.bind ?? snapshot?.bindJson ?? snapshot?.bind_json;
  const raw = block?.bindJson ?? block?.bind_json ?? component?.bindJson
    ?? component?.bind_json ?? snapshotBind;
  return parseObject(raw, null);
}

function normalizeFields(value) {
  const fields = parseObject(value, {});
  return Object.fromEntries(Object.entries(fields)
    .filter(([, column]) => column !== undefined && column !== null && text(column))
    .map(([semantic, column]) => [semantic, text(column)]));
}

function normalizeUnits(value) {
  const units = parseObject(value, {});
  return Object.fromEntries(Object.entries(units)
    .filter(([, unit]) => unit !== undefined && unit !== null && text(unit))
    .map(([semantic, unit]) => [semantic, text(unit)]));
}

function defaultPeriod(slot) {
  return TREND_SLOTS.has(slot) ? 'LAST_6M_EOM' : 'LATEST';
}

/** 只保留绑定身份用于比对，避免把编辑器的展示/位置属性当成数据绑定差异。 */
export function normalizeVerificationBinding(raw, slot = '') {
  const source = parseObject(raw, {});
  const dsIdRaw = source.dsId ?? source.datasourceId ?? source.datasource_id;
  const dsId = dsIdRaw === undefined || dsIdRaw === null || dsIdRaw === ''
    ? null : (positiveId(dsIdRaw) || text(dsIdRaw));
  return {
    dsId,
    period: text(source.period) || defaultPeriod(slot),
    fields: normalizeFields(source.fields),
    units: normalizeUnits(source.units)
  };
}

function extractBindingMapFromComponents(components, blocks, snapshots, slotSet) {
  const map = new Map();
  const issues = [];
  for (const component of components) {
    if (component?.component !== 'ChartWidget') continue;
    const slot = componentSlot(component);
    if (!slot || !slotSet.has(slot)) continue;
    const blockId = positiveId(component.blockId ?? component.block_id);
    const block = blockId ? blocks.get(blockId) : null;
    const snapshot = blockId ? snapshots[String(blockId)] : null;
    const binding = bindingSource(block, component, snapshot);
    const previous = map.get(slot);
    if (previous && previous.blockId !== blockId) {
      issues.push({ slot, code: 'DUPLICATE_SLOT', message: `槽位 ${slot} 对应多个已保存组件` });
      continue;
    }
    if (!previous || (!previous.binding && binding)) {
      map.set(slot, {
        slot,
        blockId,
        binding,
        component,
        saved: true
      });
    }
  }
  return { map, issues };
}

function extractSavedBindings(canvas, view, slotOrder) {
  const slotSet = new Set(slotOrder);
  const blocks = blockMap(canvas);
  const pkg = screenPackage(view);
  const snapshots = isObject(pkg.bindSnapshots)
    ? pkg.bindSnapshots
    : isObject(view?.bindSnapshots) ? view.bindSnapshots : {};
  const canvasResult = extractBindingMapFromComponents(draftComponents(canvas), blocks, snapshots, slotSet);
  const viewResult = extractBindingMapFromComponents(packageComponents(view), blocks, snapshots, slotSet);
  const map = new Map(canvasResult.map);
  for (const [slot, candidate] of viewResult.map.entries()) {
    const existing = map.get(slot);
    if (!existing) map.set(slot, candidate);
    else if (!existing.binding && candidate.binding) map.set(slot, { ...existing, ...candidate });
    else if (existing.blockId === null && candidate.blockId) map.set(slot, { ...existing, ...candidate });
  }
  const issues = [...canvasResult.issues, ...viewResult.issues];
  return { map, issues, blocks, package: pkg };
}

function directBindingState(state) {
  const source = parseObject(state, {});
  if (isObject(source.bindings)) return source.bindings;
  if (isObject(source.slots)) return source.slots;
  return source;
}

function extractCurrentBindings(bindingState, canvas, slotOrder) {
  const slotSet = new Set(slotOrder);
  const source = directBindingState(bindingState);
  const map = new Map();
  for (const slot of slotOrder) {
    if (!Object.prototype.hasOwnProperty.call(source, slot)) continue;
    const value = parseObject(source[slot], null);
    if (value) map.set(slot, value);
  }
  // A caller that has not split bindingState by slot can still be checked from
  // the current editor draft. This fallback is read-only and never writes it.
  if (!map.size && bindingState === undefined) {
    const result = extractBindingMapFromComponents(
      draftComponents(canvas), blockMap(canvas), {}, slotSet
    );
    for (const [slot, entry] of result.map.entries()) {
      if (entry.binding) map.set(slot, entry.binding);
    }
  }
  return map;
}

export function compareSavedBindings({ saved, current, slotOrder }) {
  const changedSlots = [];
  for (const slot of slotOrder) {
    const savedBinding = saved.get(slot)?.binding;
    const currentBinding = current.get(slot);
    const savedKey = savedBinding ? stableSerialize(normalizeVerificationBinding(savedBinding, slot)) : null;
    const currentKey = currentBinding ? stableSerialize(normalizeVerificationBinding(currentBinding, slot)) : null;
    if (savedKey !== currentKey) changedSlots.push(slot);
  }
  return { equal: changedSlots.length === 0, changedSlots };
}

function emptySlotResult(slot) {
  return {
    slot,
    label: BINDING_SLOTS[slot]?.label || slot,
    status: SLOT_STATUS.UNVERIFIED,
    statusLabel: SLOT_STATUS_LABELS[SLOT_STATUS.UNVERIFIED],
    binding: null,
    blockId: null,
    rowCount: null,
    maxRows: null,
    coverage: { label: '未核验', available: null, total: null, missing: [] },
    dates: [],
    dateState: 'UNVERIFIABLE',
    zeroCount: 0,
    sampleOnly: false,
    issues: []
  };
}

function issue(slotResult, code, message, severity = 'error', field = '') {
  if (slotResult.issues.some(item => item.code === code && item.field === field)) return;
  slotResult.issues.push({ code, message, severity, ...(field ? { field } : {}) });
}

function fieldSpec(slot, semantic) {
  return (BINDING_SLOTS[slot]?.fields || []).find(item => item.semantic === semantic) || null;
}

function bindingConfigIssues(slot, rawBinding) {
  const binding = normalizeVerificationBinding(rawBinding, slot);
  const issues = [];
  if (!positiveId(binding.dsId)) issues.push({ code: 'MISSING_DATASOURCE', message: '未配置有效数据源' });
  const spec = BINDING_SLOTS[slot];
  if (!spec) return [{ code: 'UNSUPPORTED_SLOT', message: `槽位不受支持: ${slot}` }];
  if (!binding.fields || !Object.keys(binding.fields).length) {
    issues.push({ code: 'MISSING_FIELDS', message: '未配置字段映射' });
  }
  if (spec.required) {
    for (const semantic of spec.required) {
      if (!binding.fields?.[semantic]) issues.push({ code: 'MISSING_FIELD', message: `缺少字段映射: ${semantic}`, field: semantic });
    }
  }
  if (spec.atLeastOneOf?.length && !spec.atLeastOneOf.some(semantic => binding.fields?.[semantic])) {
    issues.push({ code: 'MISSING_FIELD', message: `至少需要一个字段: ${spec.atLeastOneOf.join('、')}` });
  }
  if (spec.oneOfRequired?.length && !spec.oneOfRequired.some(semantic => binding.fields?.[semantic])) {
    issues.push({ code: 'MISSING_FIELD', message: `至少需要一个身份字段: ${spec.oneOfRequired.join('、')}` });
  }
  if (binding.period !== 'LATEST' && binding.period !== 'LAST_10D'
      && binding.period !== 'LAST_1M' && binding.period !== 'LAST_6M_EOM') {
    issues.push({ code: 'INVALID_PERIOD', message: `周期无效: ${binding.period}` });
  }
  for (const [semantic] of Object.entries(binding.fields || {})) {
    const specField = fieldSpec(slot, semantic);
    if (!specField) {
      issues.push({ code: 'UNSUPPORTED_FIELD', message: `字段不受支持: ${semantic}`, field: semantic });
      continue;
    }
    if (specField.unitKinds?.length && !binding.units?.[semantic]) {
      issues.push({ code: 'MISSING_UNIT', message: `缺少单位: ${semantic}`, field: semantic });
    }
  }
  // 对公模板拥有独立的字段/单位白名单；共享运行时只负责把契约错误
  // 转换为逐槽核验结果，不能把未知单位或跨槽字段带入请求。
  if (isCorporateBindingSlot(slot)) {
    for (const message of validateBinding(slot, rawBinding)) {
      const field = /(?:字段|单位)(?:不受支持|无效|不适用|未绑定字段|缺少)[：: ]*([\w.-]+)/.exec(message)?.[1] || '';
      const code = message.includes('缺少单位') ? 'MISSING_UNIT'
        : message.includes('单位') ? 'INVALID_UNIT' : 'BINDING_INVALID';
      if (!issues.some(item => item.code === code && item.message === message)) {
        issues.push({ code, message, ...(field ? { field } : {}) });
      }
    }
  }
  return issues;
}

function normalizeColumn(column) {
  if (typeof column === 'string') return column.trim();
  if (isObject(column)) return text(column.col ?? column.name ?? column.key);
  return text(column);
}

function numericValue(value) {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value === 'number') return Number.isFinite(value) ? value : NaN;
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : NaN;
  }
  return NaN;
}

function normalizeDate(value) {
  let normalized = text(value);
  if (!normalized) return null;
  // Accept a date returned with a time suffix, but validate its calendar date.
  normalized = normalized.replace(/T.*$/, '').replace(/\s+.*$/, '');
  let match = normalized.match(/^(\d{4})[-/.](\d{1,2})(?:[-/.](\d{1,2}))?$/);
  if (!match && /^\d{8}$/.test(normalized)) {
    match = [normalized, normalized.slice(0, 4), normalized.slice(4, 6), normalized.slice(6, 8)];
  }
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = match[3] === undefined ? null : Number(match[3]);
  if (month < 1 || month > 12) return null;
  if (day !== null) {
    const maxDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
    if (day < 1 || day > maxDay) return null;
  }
  return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}${day === null ? '' : `-${String(day).padStart(2, '0')}`}`;
}

function dateSort(left, right) {
  return left.localeCompare(right);
}

function tableShape(response, slotResult) {
  const table = unwrapResponse(response);
  if (!isObject(table) || !Array.isArray(table.columns) || !Array.isArray(table.rows)) {
    issue(slotResult, 'INVALID_SHAPE', '数据响应必须包含 columns 数组和 rows 二维数组');
    return null;
  }
  const columns = table.columns.map(normalizeColumn);
  if (columns.some(column => !column)) {
    issue(slotResult, 'INVALID_COLUMNS', '数据响应存在空列名');
  }
  const seenColumns = new Set();
  for (const column of columns) {
    if (seenColumns.has(column)) issue(slotResult, 'INVALID_COLUMNS', `数据响应存在重复列: ${column}`);
    seenColumns.add(column);
  }
  const rows = table.rows;
  for (const row of rows) {
    if (!Array.isArray(row)) issue(slotResult, 'INVALID_ROWS', '数据响应 rows 必须是二维数组');
  }
  return { table, columns, rows };
}

function responseDataDate(table) {
  return pick(table, 'dataDate', 'data_date', 'date')
    ?? pick(table?.meta, 'dataDate', 'data_date', 'date');
}

function maxRowsOf(table) {
  const raw = pick(table, 'maxRows', 'max_rows') ?? pick(table?.meta, 'maxRows', 'max_rows');
  const maxRows = Number(raw);
  return Number.isSafeInteger(maxRows) && maxRows > 0 ? maxRows : null;
}

function isNumericField(slot, semantic) {
  if (DIMENSION_FIELDS.has(semantic)) return false;
  if (METRIC_FIELDS.has(semantic)) return true;
  return fieldSpec(slot, semantic)?.kind !== 'dimension';
}

function selectedColumnValues(slotResult, slot, binding, tableInfo) {
  const { table, columns, rows } = tableInfo;
  const columnIndex = new Map(columns.map((column, index) => [column, index]));
  const values = new Map();
  for (const [semantic, column] of Object.entries(binding.fields || {})) {
    if (!columnIndex.has(column)) {
      issue(slotResult, 'MISSING_COLUMN', `响应缺少列: ${column}`, 'error', semantic);
      values.set(semantic, []);
      continue;
    }
    const index = columnIndex.get(column);
    const semanticValues = [];
    for (const row of rows) {
      const raw = Array.isArray(row) ? row[index] : undefined;
      semanticValues.push(raw === undefined ? null : raw);
      if (raw === undefined || raw === null || raw === '') {
        issue(slotResult, 'NULL_VALUE', `字段 ${semantic} 存在 null/空值`, 'warning', semantic);
      } else if (isNumericField(slot, semantic)) {
        const number = numericValue(raw);
        if (Number.isNaN(number)) issue(slotResult, 'INVALID_NUMBER', `字段 ${semantic} 存在非数字值`, 'error', semantic);
        else if (number === 0) slotResult.zeroCount += 1;
      }
    }
    values.set(semantic, semanticValues);
  }
  // A response-level date is evidence only when the service returned it;
  // current screen data responses often omit it, so never use today's date.
  const responseDate = responseDataDate(table);
  if (responseDate) values.set('__responseDate', [responseDate]);
  return values;
}

function dateDetails(slotResult, slot, binding, tableInfo, values) {
  const dateColumn = binding.fields?.date;
  const rawDates = dateColumn && values.has('date') ? values.get('date')
    : values.has('__responseDate') ? values.get('__responseDate') : [];
  let dates = [];
  for (const rawDate of rawDates) {
    const normalized = normalizeDate(rawDate);
    if (normalized) dates.push(normalized);
    else if (text(rawDate)) issue(slotResult, 'INVALID_DATE', `日期无法识别: ${text(rawDate)}`, 'warning', 'date');
  }
  if (dateColumn && values.has('date')) {
    if (dates.length !== values.get('date').filter(value => text(value)).length) {
      issue(slotResult, 'DATE_UNVERIFIABLE', '存在空日期，无法完整核验数据日期', 'warning', 'date');
    }
  }
  dates = [...new Set(dates)].sort(dateSort);
  if (!dates.length) issue(slotResult, 'DATE_UNVERIFIABLE', '返回结果未提供可核验的数据日期', 'warning', 'date');
  if (TREND_SLOTS.has(slot) && dates.length < (tableInfo.rows.length ? 1 : 0)) {
    issue(slotResult, 'DATE_UNVERIFIABLE', '趋势日期无法核验', 'warning', 'date');
  }
  if (TREND_SLOTS.has(slot) && dateColumn && dates.length < tableInfo.rows.length) {
    issue(slotResult, 'DATE_UNVERIFIABLE', '趋势存在缺失日期，无法完整核验日期序列', 'warning', 'date');
  }
  const canonicalDates = rawDates.map(normalizeDate).filter(Boolean);
  if (TREND_SLOTS.has(slot)) {
    const counts = new Map();
    for (const date of canonicalDates) counts.set(date, (counts.get(date) || 0) + 1);
    if ([...counts.values()].some(count => count > 1)) issue(slotResult, 'DUPLICATE_DATE', '趋势返回重复日期', 'error', 'date');
  }
  slotResult.dates = dates;
  slotResult.dateState = dates.length ? 'VERIFIABLE' : 'UNVERIFIABLE';
  return dates;
}

function validateTableResult(slot, binding, response, slotResult) {
  const tableInfo = tableShape(response, slotResult);
  if (!tableInfo) {
    slotResult.status = SLOT_STATUS.STRUCTURE_ERROR;
    slotResult.statusLabel = SLOT_STATUS_LABELS[slotResult.status];
    return;
  }
  const { table, rows } = tableInfo;
  slotResult.rowCount = rows.length;
  slotResult.maxRows = maxRowsOf(table);
  if (slotResult.maxRows && rows.length >= slotResult.maxRows) {
    issue(slotResult, 'POSSIBLY_TRUNCATED', `返回行数达到 maxRows=${slotResult.maxRows}，结果可能被截断`, 'warning');
    slotResult.coverage.truncatedPossible = true;
  }
  if (!rows.length) {
    issue(slotResult, 'NO_ROWS', '数据响应没有数据行', 'warning');
    issue(slotResult, 'DATE_UNVERIFIABLE', '无数据行，无法核验数据日期', 'warning', 'date');
    slotResult.status = SLOT_STATUS.NO_ROWS;
    slotResult.statusLabel = SLOT_STATUS_LABELS[slotResult.status];
    return;
  }
  const values = selectedColumnValues(slotResult, slot, binding, tableInfo);
  dateDetails(slotResult, slot, binding, tableInfo, values);
  if (slotResult.coverage?.label === '未核验') {
    slotResult.coverage = {
      label: `返回 ${rows.length} 行`,
      available: rows.length,
      total: rows.length,
      missing: [],
      truncatedPossible: Boolean(slotResult.coverage?.truncatedPossible)
    };
  }
  if (SINGLE_VALUE_SLOTS.has(slot) && rows.length !== 1) {
    issue(slotResult, 'MULTIPLE_ROWS', '单值槽位应返回恰好一行', 'error');
  }
  const orgColumn = binding.fields?.orgCode;
  if (orgColumn && values.has('orgCode')) {
    const orgCodes = values.get('orgCode').map(text).filter(Boolean);
    const counts = new Map();
    for (const code of orgCodes) counts.set(code, (counts.get(code) || 0) + 1);
    if ([...counts.values()].some(count => count > 1)) {
      issue(slotResult, 'DUPLICATE_ORG_CODE', '返回结果存在重复机构编码', 'error', 'orgCode');
    }
    slotResult._returnedOrgCodes = [...new Set(orgCodes)];
  }
  const fatal = slotResult.issues.some(item => FATAL_ISSUES.has(item.code));
  const gaps = slotResult.issues.some(item => GAP_ISSUES.has(item.code));
  if (fatal) slotResult.status = SLOT_STATUS.STRUCTURE_ERROR;
  else if (gaps) slotResult.status = SLOT_STATUS.VERIFIED_WITH_WARNINGS;
  else slotResult.status = SLOT_STATUS.STRUCTURE_VERIFIED;
  slotResult.statusLabel = SLOT_STATUS_LABELS[slotResult.status];
}

function authorizedInstitutions(view, screen) {
  const viewHas = Object.prototype.hasOwnProperty.call(view || {}, 'panoramaInstitutions')
    || Object.prototype.hasOwnProperty.call(view || {}, 'panorama_institutions');
  // 只有本次 getScreenView(code, 'draft') 返回的当前屏目录才有授权证明。
  // input.screen 可能来自宿主缓存或管理目录，绝不能作为 branchTrend 授权回退。
  if (!viewHas) return null;
  const source = view?.panoramaInstitutions ?? view?.panorama_institutions;
  if (!Array.isArray(source)) return null;
  return source.map(item => ({
    orgCode: text(item?.orgCode ?? item?.org_code),
    orgName: text(item?.orgName ?? item?.org_name)
  })).filter(item => item.orgCode);
}

function applyBranchesCoverage(slotResult, institutions) {
  if (institutions === null) {
    issue(slotResult, 'MISSING_AUTHORIZED_DIRECTORY', '当前屏未返回授权机构目录，无法核验机构覆盖', 'warning');
    slotResult.coverage = { label: '授权目录不可核验', available: null, total: null, missing: [] };
    return;
  }
  const authorized = [...new Set(institutions.map(item => item.orgCode))];
  const returned = slotResult._returnedOrgCodes || [];
  const missing = authorized.filter(code => !returned.includes(code));
  const extra = returned.filter(code => !authorized.includes(code));
  if (extra.length) issue(slotResult, 'UNAUTHORIZED_ORG_CODE', `返回机构不在当前屏授权目录: ${extra.join('、')}`, 'error', 'orgCode');
  if (missing.length) issue(slotResult, 'MISSING_AUTHORIZED_ORG', `授权机构未返回数据: ${missing.join('、')}`, 'warning', 'orgCode');
  slotResult.coverage = {
    label: `${returned.filter(code => authorized.includes(code)).length}/${authorized.length}`,
    available: returned.filter(code => authorized.includes(code)).length,
    total: authorized.length,
    missing,
    extra,
    authorizedOnly: true,
    truncatedPossible: Boolean(slotResult.coverage?.truncatedPossible)
  };
}

function applyDateDifferences(results) {
  const anchors = results
    .filter(item => item.dateState === 'VERIFIABLE' && item.dates.length)
    .map(item => item.dates[item.dates.length - 1]);
  const unique = [...new Set(anchors)];
  if (unique.length <= 1) return;
  for (const item of results) {
    if (item.dateState === 'VERIFIABLE' && item.dates.length
        && item.dates[item.dates.length - 1] !== unique[0]) {
      issue(item, 'DATE_MISMATCH', `与其他槽位最新返回日期不一致: ${item.dates[item.dates.length - 1]}`, 'warning', 'date');
      if (item.status === SLOT_STATUS.STRUCTURE_VERIFIED) {
        item.status = SLOT_STATUS.VERIFIED_WITH_WARNINGS;
        item.statusLabel = SLOT_STATUS_LABELS[item.status];
      }
    }
  }
}

function publicSlotResult(slotResult) {
  const { _returnedOrgCodes, ...result } = slotResult;
  return result;
}

function initialResults(slotOrder) {
  return slotOrder.map(emptySlotResult);
}

function baseResult(status, results = [], extra = {}) {
  return {
    overallStatus: status,
    overallStatusLabel: OVERALL_STATUS_LABELS[status] || status,
    disclaimer: VERIFICATION_DISCLAIMER,
    businessMeaningVerified: false,
    results,
    ...extra
  };
}

function compareCanvasVersions(left, right) {
  const a = integerVersion(pick(left, 'canvasVersion', 'canvas_version', 'version'));
  const b = integerVersion(pick(right, 'canvasVersion', 'canvas_version', 'version'));
  return a === null || b === null || a !== b;
}

function resourceIdentity(source) {
  const value = parseObject(source, {});
  return {
    screenId: positiveId(value.id ?? value.screenId ?? value.screen_id),
    screenCode: text(value.screenCode ?? value.screen_code)
  };
}

function identitiesMatch(...identities) {
  if (!identities.length || identities.some(item => !item?.screenId || !item?.screenCode)) return false;
  const first = identities[0];
  return identities.every(item => item.screenId === first.screenId && item.screenCode === first.screenCode);
}

function currentScreenIdentity(input) {
  const screen = resourceIdentity(input?.screen);
  const canvas = resourceIdentity(input?.canvas);
  return { ...screen, inputMatches: identitiesMatch(screen, canvas) };
}

function schemaVersionOf(canvas, view, screen) {
  const runtime = pick(view, 'runtimeSchemaVersion', 'runtime_schema_version');
  if (runtime !== undefined && runtime !== null && runtime !== '') {
    return runtime === 1 || runtime === 2 ? runtime : null;
  }
  if (text(screen?.orgScopeMode ?? screen?.org_scope_mode).toUpperCase() === 'NAMED_GROUP') return 2;
  const pkg = screenPackage(view);
  if (Object.prototype.hasOwnProperty.call(pkg, 'schemaVersion')) {
    return pkg.schemaVersion === 1 || pkg.schemaVersion === 2 ? pkg.schemaVersion : null;
  }
  const draft = parseObject(pick(canvas, 'canvasDraftJson', 'canvas_draft_json'), {});
  if (Object.prototype.hasOwnProperty.call(draft, 'schemaVersion')) {
    return draft.schemaVersion === 1 || draft.schemaVersion === 2 ? draft.schemaVersion : null;
  }
  return null;
}

function codeTemplateOf(canvas, view) {
  const pkg = screenPackage(view);
  const pkgStyle = parseObject(pkg.canvasStyle ?? pkg.canvas_style, {});
  const canvasStyle = parseObject(pick(canvas, 'canvasStyleJson', 'canvas_style_json'), {});
  const presentation = parseObject(pkgStyle.presentation ?? canvasStyle.presentation, {});
  return text(presentation.template || pkg.presentation?.template);
}

function bindingForRequest(entry, slot) {
  const binding = normalizeVerificationBinding(entry.binding, slot);
  return binding;
}

function datasourceDetails(binding, datasources) {
  const id = positiveId(binding?.dsId);
  const source = (Array.isArray(datasources) ? datasources : [])
    .find(item => positiveId(item?.id ?? item?.dsId ?? item?.datasourceId) === id);
  return {
    datasourceId: id || binding?.dsId || null,
    datasourceName: text(source?.dsName ?? source?.ds_name ?? source?.name ?? source?.dsCode ?? source?.ds_code) || null
  };
}

function datasourceFor(binding, datasources) {
  const id = positiveId(binding?.dsId);
  return (Array.isArray(datasources) ? datasources : [])
    .find(item => positiveId(item?.id ?? item?.dsId ?? item?.datasourceId) === id) || null;
}

function stripSqlComments(sql) {
  return String(sql || '').replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/--[^\n]*/g, ' ').trim();
}

function constantSql(sql) {
  const normalized = stripSqlComments(sql);
  if (!/^SELECT\b/i.test(normalized) || /\bFROM\b/i.test(normalized)) return false;
  const body = normalized.replace(/^SELECT\s+/i, '')
    .split(/\bWHERE\b|\bGROUP\s+BY\b|\bORDER\s+BY\b/i)[0].trim();
  if (!body) return false;
  const expressions = body.split(',').map(item => item.trim()).filter(Boolean);
  return expressions.length > 0 && expressions.every(expression => {
    const withoutAlias = expression
      .replace(/\s+AS\s+(?:`[^`]*`|"[^"]*"|'[^']*'|[\w$\u4e00-\u9fff]+)\s*$/i, '')
      .replace(/\s+(?:`[^`]*`|"[^"]*"|'[^']*'|[\w$\u4e00-\u9fff]+)\s*$/i, '')
      .trim();
    return /^[-+]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(withoutAlias)
      || /^'(?:[^']|'')*'$/.test(withoutAlias)
      || /^"(?:[^"]|"")*"$/.test(withoutAlias)
      || /^(?:NULL|TRUE|FALSE)$/i.test(withoutAlias);
  });
}

function datasourceIsConstant(source) {
  if (!source) return false;
  const config = parseObject(source.configJson ?? source.config_json ?? source.config, {});
  if (config.constant === true || config.isConstant === true || config.constantSql === true) return true;
  const sql = source.sql ?? source.sqlText ?? source.sql_text ?? source.querySql ?? source.query_sql
    ?? config.sql ?? config.sqlText ?? config.sql_text ?? config.querySql ?? config.query_sql;
  return constantSql(sql);
}

function attachBindingDisplay(slotResult, binding, datasources) {
  const details = datasourceDetails(binding, datasources);
  slotResult.binding = {
    ...details,
    period: binding.period,
    fields: { ...binding.fields },
    units: { ...binding.units }
  };
}

function branchContext(institutions) {
  if (!Array.isArray(institutions)) return null;
  return institutions.find(item => text(item?.orgCode))?.orgCode || null;
}

function makeRequest({ slot, entry, screenCode, schemaVersion, institutions }) {
  const blockId = positiveId(entry?.blockId);
  if (!blockId) throw new Error('已保存组件缺少有效 blockId');
  const body = {
    schemaVersion,
    previewState: 'draft',
    screenCode,
    blockId,
    period: bindingForRequest(entry, slot).period
  };
  if (slot === 'branchTrend') {
    const orgCode = branchContext(institutions);
    if (!orgCode) return null;
    body.contextParams = { orgCode };
  }
  return body;
}

function createScheduler(requestFn, isAlive) {
  const queue = [];
  const inFlight = new Map();
  let active = 0;

  function pump() {
    while (active < MAX_VERIFICATION_CONCURRENCY && queue.length) {
      const task = queue.shift();
      if (!isAlive()) {
        task.resolve({ cancelled: true });
        continue;
      }
      task.started = true;
      active += 1;
      Promise.resolve().then(() => requestFn(task.body)).then(
        response => task.resolve({ response }),
        error => task.resolve({ error })
      ).finally(() => {
        active -= 1;
        if (inFlight.get(task.key) === task.promise) inFlight.delete(task.key);
        pump();
      });
    }
  }

  function enqueue(body) {
    const key = stableSerialize(body);
    if (inFlight.has(key)) return inFlight.get(key);
    let resolveTask;
    const promise = new Promise(resolve => { resolveTask = resolve; });
    const task = { key, body, resolve: resolveTask, promise, started: false };
    inFlight.set(key, promise);
    queue.push(task);
    pump();
    return promise;
  }

  function cancel() {
    while (queue.length) queue.shift().resolve({ cancelled: true });
    inFlight.clear();
  }

  return { enqueue, cancel };
}

/**
 * 创建一次可复用的草稿核验服务。依赖可注入，测试不会触碰真实 API；生产默认
 * 采用现有 screen API 封装。服务实例内的代际号保证切屏、重复点击和销毁后的
 * 迟到响应不会回写本轮结果。
 */
export function createRuntimeDataVerificationService(dependencies = {}) {
  const deps = {
    getScreenCanvas: dependencies.getScreenCanvas || getScreenCanvas,
    getScreenView: dependencies.getScreenView || getScreenView,
    queryScreenData: dependencies.queryScreenData || queryScreenData,
    logger: typeof dependencies.logger === 'function' ? dependencies.logger : () => {}
  };
  let generation = 0;
  let disposed = false;
  const scheduler = createScheduler(deps.queryScreenData, () => !disposed);

  function isCurrent(token) {
    return !disposed && token === generation;
  }

  function log(event, payload = {}) {
    try { deps.logger({ event, ...payload }); } catch { /* logging must not change verification */ }
  }

  function invalidate() {
    generation += 1;
  }

  async function verify(input = {}) {
    const token = ++generation;
    const template = text(input.template).toLowerCase();
    const defaultSlotOrder = template === CORPORATE_TEMPLATE
      ? CORPORATE_SLOT_ORDER
      : template === RETAIL_TEMPLATE ? RETAIL_SLOT_ORDER : ALL_SLOT_ORDER;
    const slotOrder = [...new Set(
      (Array.isArray(input.slotOrder) && input.slotOrder.length ? input.slotOrder : defaultSlotOrder)
        .map(slot => text(slot)).filter(Boolean)
    )];
    const pending = initialResults(slotOrder);
    if (disposed) return baseResult(OVERALL_STATUS.STALE, []);
    const identity = currentScreenIdentity(input);
    if (!identity.inputMatches) {
      return baseResult(OVERALL_STATUS.CONFIG_ERROR, pending, {
        message: '当前屏与编辑画布的 screenId/screenCode 不一致或缺失，已拒绝取数'
      });
    }
    log('verification-start', { screenId: identity.screenId, screenCode: identity.screenCode, slots: slotOrder });

    let savedCanvas;
    let draftView;
    try {
      [savedCanvas, draftView] = await Promise.all([
        deps.getScreenCanvas(identity.screenId),
        deps.getScreenView(identity.screenCode, 'draft')
      ]);
    } catch (error) {
      if (!isCurrent(token)) return baseResult(OVERALL_STATUS.STALE, []);
      const status = errorStatus(error);
      if (status) return baseResult(OVERALL_STATUS.PERMISSION_DENIED, [], { statusCode: status, message: errorMessage(error) });
      return baseResult(OVERALL_STATUS.CONFIG_ERROR, pending, { message: errorMessage(error) });
    }
    if (!isCurrent(token)) return baseResult(OVERALL_STATUS.STALE, []);
    if (!savedCanvas || !draftView) {
      return baseResult(OVERALL_STATUS.CONFIG_ERROR, pending, { message: '服务端未返回完整的已保存草稿' });
    }

    const savedIdentity = resourceIdentity(savedCanvas);
    const viewIdentity = resourceIdentity(draftView);
    if (!identitiesMatch(identity, savedIdentity, viewIdentity)) {
      return baseResult(OVERALL_STATUS.CONFIG_ERROR, pending, {
        message: '当前屏、服务端画布和草稿视图身份不一致，已拒绝取数'
      });
    }

    if (!codeTemplateOf(savedCanvas, draftView)) {
      return baseResult(OVERALL_STATUS.CONFIG_ERROR, pending, {
        applicable: false,
        message: '当前已保存草稿不是代码化大屏，数据结构核验不适用；历史画布不会按代码槽位补齐。'
      });
    }

    const inputCanvas = parseObject(input.canvas, {});
    if (compareCanvasVersions(inputCanvas, savedCanvas)) {
      return baseResult(OVERALL_STATUS.STALE, [], { message: '当前编辑画布版本与服务端已保存版本不一致' });
    }

    const saved = extractSavedBindings(savedCanvas, draftView, slotOrder);
    const current = extractCurrentBindings(input.bindingState, inputCanvas, slotOrder);
    const bindingComparison = compareSavedBindings({ saved: saved.map, current, slotOrder });
    if (!bindingComparison.equal) {
      log('verification-unsaved', { changedSlots: bindingComparison.changedSlots });
      return baseResult(OVERALL_STATUS.UNSAVED, pending, {
        message: '当前编辑绑定尚未保存，请先保存后再验证。不会自动写入。',
        changedSlots: bindingComparison.changedSlots
      });
    }

    const institutions = authorizedInstitutions(draftView, input.screen);
      const schemaVersion = schemaVersionOf(savedCanvas, draftView, input.screen);
    if (schemaVersion !== 1 && schemaVersion !== 2) {
      return baseResult(OVERALL_STATUS.CONFIG_ERROR, pending, {
        message: '服务端草稿缺少受支持的 runtime schemaVersion，已拒绝降级取数'
      });
    }
    const rows = initialResults(slotOrder);
    const requests = [];
    for (const row of rows) {
      const entry = saved.map.get(row.slot);
      if (!entry) {
        row.status = SLOT_STATUS.MISSING_CONFIG;
        row.statusLabel = SLOT_STATUS_LABELS[row.status];
        issue(row, 'MISSING_CONFIG', '当前槽位没有已保存组件或绑定');
        continue;
      }
      row.blockId = positiveId(entry.blockId);
      if (!row.blockId || !entry.binding) {
        row.status = SLOT_STATUS.CONFIG_ERROR;
        row.statusLabel = SLOT_STATUS_LABELS[row.status];
        issue(row, !row.blockId ? 'MISSING_BLOCK_ID' : 'MISSING_SAVED_BINDING', '已保存组件缺少可核验的数据身份');
        continue;
      }
      const binding = bindingForRequest(entry, row.slot);
      attachBindingDisplay(row, binding, input.datasources);
      if (datasourceIsConstant(datasourceFor(binding, input.datasources))) {
        issue(row, 'CONSTANT_SOURCE', '数据源配置检测到常量 SQL，返回行不能证明真实指标来源', 'warning');
      }
      const configErrors = bindingConfigIssues(row.slot, entry.binding);
      if (configErrors.length) {
        row.status = SLOT_STATUS.CONFIG_ERROR;
        row.statusLabel = SLOT_STATUS_LABELS[row.status];
        for (const configError of configErrors) issue(row, configError.code, configError.message, 'error', configError.field || '');
        continue;
      }
      const body = makeRequest({ slot: row.slot, entry, screenCode: identity.screenCode, schemaVersion, institutions });
      if (!body) {
        row.status = SLOT_STATUS.NOT_VERIFIABLE;
        row.statusLabel = SLOT_STATUS_LABELS[row.status];
        row.sampleOnly = true;
        issue(row, 'MISSING_AUTHORIZED_INSTITUTION', '当前屏没有授权机构，不能请求支行趋势汇总');
        continue;
      }
      log('verification-request', { slot: row.slot, body });
      requests.push({ row, entry, binding, body });
    }

    const settled = await Promise.all(requests.map(item => scheduler.enqueue(item.body)));
    if (!isCurrent(token)) return baseResult(OVERALL_STATUS.STALE, []);
    const permission = settled.find(item => errorStatus(item.error) || errorStatus(item.response));
    if (permission) {
      const status = errorStatus(permission.error) || errorStatus(permission.response);
      log('verification-permission-denied', { status });
      return baseResult(OVERALL_STATUS.PERMISSION_DENIED, [], {
        statusCode: status,
        message: errorMessage(permission.error || permission.response, `没有权限（${status}）`)
      });
    }
    for (let index = 0; index < requests.length; index += 1) {
      const request = requests[index];
      const settledItem = settled[index];
      const row = request.row;
      if (settledItem.cancelled) continue;
      if (settledItem.error) {
        row.status = SLOT_STATUS.REQUEST_ERROR;
        row.statusLabel = SLOT_STATUS_LABELS[row.status];
        issue(row, 'REQUEST_FAILED', errorMessage(settledItem.error), 'error');
        continue;
      }
      validateTableResult(row.slot, request.binding, settledItem.response, row);
      if (row.slot === 'branches') applyBranchesCoverage(row, institutions);
      if (row.slot === 'branchTrend') {
        row.sampleOnly = true;
        row.coverage = {
          label: '仅核验授权第一机构样本',
          available: row.rowCount,
          total: row.rowCount,
          sampleOnly: true,
          truncatedPossible: Boolean(row.coverage?.truncatedPossible)
        };
      }
    }

    // 末尾重新读取画布版本：数据查询期间若草稿被保存/切换，整轮结果作废。
    let latestCanvas;
    try {
      latestCanvas = await deps.getScreenCanvas(identity.screenId);
    } catch (error) {
      if (!isCurrent(token)) return baseResult(OVERALL_STATUS.STALE, []);
      const status = errorStatus(error);
      if (status) return baseResult(OVERALL_STATUS.PERMISSION_DENIED, [], { statusCode: status, message: errorMessage(error) });
      return baseResult(OVERALL_STATUS.STALE, [], { message: '无法复核画布版本，本次结果已废弃' });
    }
    if (!isCurrent(token) || compareCanvasVersions(savedCanvas, latestCanvas)) {
      return baseResult(OVERALL_STATUS.STALE, [], { message: '验证期间画布版本发生变化，本次结果已废弃' });
    }
    applyDateDifferences(rows);
    const publicRows = rows.map(publicSlotResult);
    const hasGap = publicRows.some(row => row.status !== SLOT_STATUS.STRUCTURE_VERIFIED
      || row.issues.some(item => GAP_ISSUES.has(item.code) || FATAL_ISSUES.has(item.code)));
    const overallStatus = hasGap ? OVERALL_STATUS.HAS_GAPS : OVERALL_STATUS.STRUCTURE_CHECKED;
    const result = baseResult(overallStatus, publicRows, {
      schemaVersion,
      savedCanvasVersion: integerVersion(pick(savedCanvas, 'canvasVersion', 'canvas_version', 'version')),
      authorizedInstitutionCount: institutions === null ? null : institutions.length,
      message: hasGap
        ? '已完成返回结构核验，但存在缺失、错误、日期或覆盖缺口；不能据此声称数据完整或全量。'
        : '已完成返回结构核验；不能据此证明指标业务口径和单位正确。'
    });
    log('verification-finished', { overallStatus, resultCount: publicRows.length });
    return result;
  }

  function dispose() {
    if (disposed) return;
    disposed = true;
    generation += 1;
    scheduler.cancel();
  }

  return { verify, invalidate, dispose };
}

/** 单次调用便捷入口；复杂场景应复用 createRuntimeDataVerificationService。 */
export function verifySavedDraftData(input = {}, dependencies = {}) {
  return createRuntimeDataVerificationService(dependencies).verify(input);
}

export function statusLabel(status) {
  return OVERALL_STATUS_LABELS[status] || SLOT_STATUS_LABELS[status] || status || '—';
}
