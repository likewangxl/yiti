/**
 * 分行经营测试批次的唯一前端适配边界。
 *
 * 该模块只消费服务端已经投影好的 FREE_REPORT 固定二维结果。它不创建机构、指标、
 * 金额或状态数据；任何无法从发布身份、授权目录或响应行证明的值都会被拒绝或保留为空。
 */

export const BRANCH_TEST_SCREEN_CODE = 'SCR_BRANCH_OPERATING_TEST';
export const BRANCH_TEST_TEMPLATE = 'branch-overview-v1';
export const BRANCH_TEST_SCHEMA_VERSION = 2;
export const BRANCH_TEST_SOURCE_LABEL = '测试数据 · 非实际经营数据';

export const BRANCH_TEST_COLUMNS = Object.freeze([
  'org_code', 'kind', 'key', 'name', 'data_date', 'unit', 'status', 'owner',
  'value', 'yoy', 'mom', 'actual', 'target', 'rate', 'gap', 'deposit', 'loan',
  'amount', 'increase', 'count', 'pending', 'days'
]);

export const BRANCH_TEST_KINDS = Object.freeze([
  'kpi', 'target', 'history', 'composition', 'marketing', 'project', 'team', 'attention'
]);

export const BRANCH_TEST_UNITS = Object.freeze(['YUAN', 'COUNT', 'PERCENT']);

const COLUMN_SET = new Set(BRANCH_TEST_COLUMNS);
const KIND_SET = new Set(BRANCH_TEST_KINDS);
const UNIT_SET = new Set(BRANCH_TEST_UNITS);
const NUMERIC_COLUMNS = new Set([
  'value', 'yoy', 'mom', 'actual', 'target', 'rate', 'gap', 'deposit', 'loan',
  'amount', 'increase', 'count', 'pending', 'days'
]);
const TEXT_COLUMNS = new Set(['org_code', 'kind', 'key', 'name', 'data_date', 'unit', 'status', 'owner']);
const KPI_KEYS = Object.freeze(['deposit', 'depositAverage', 'loan', 'revenue', 'customers', 'rate']);
const KPI_KEY_SET = new Set(KPI_KEYS);
const BRANCH_TEST_INNER_TYPE = 'TABLE_LIST';
const HUNDRED_MILLION = 100000000;
const TEN_THOUSAND = 10000;

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function text(value, field, { required = false } = {}) {
  if (value === null || value === undefined) {
    if (required) throw new Error(`${field}缺失`);
    return null;
  }
  if (typeof value !== 'string') throw new Error(`${field}必须为文本`);
  const result = value.trim();
  if (!result) {
    if (required) throw new Error(`${field}缺失`);
    return null;
  }
  return result;
}

function orgCodeOf(value, field = 'org_code') {
  if (value === null || value === undefined) throw new Error(`${field}缺失`);
  const result = String(value).trim();
  if (!result) throw new Error(`${field}缺失`);
  return result;
}

function parseObject(value, field) {
  if (isRecord(value)) return value;
  if (typeof value !== 'string' || !value.trim()) throw new Error(`${field}必须为对象`);
  try {
    const parsed = JSON.parse(value);
    if (!isRecord(parsed)) throw new Error();
    return parsed;
  } catch {
    throw new Error(`${field}不是有效 JSON`);
  }
}

function isValidDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

function dateText(value, field = 'data_date') {
  const result = text(value, field);
  if (result !== null && !isValidDate(result)) throw new Error(`${field}日期无效`);
  return result;
}

function numberValue(value, field) {
  if (value === null || value === undefined) return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  if (typeof value === 'boolean' || (typeof value !== 'number' && typeof value !== 'string')) {
    throw new Error(`数字字段 ${field} 类型无效`);
  }
  const result = typeof value === 'number' ? value : Number(value.trim());
  if (!Number.isFinite(result)) throw new Error(`数字字段 ${field} 不是有限数值`);
  return result;
}

function columnName(column) {
  if (typeof column === 'string') return column.trim();
  if (isRecord(column)) return String(column.col ?? column.name ?? '').trim();
  return '';
}

function unwrapResponse(response) {
  if (isRecord(response) && response.columns === undefined && response.rows === undefined && isRecord(response.data)) {
    return response.data;
  }
  return response;
}

/** 将固定二维结果转成按列名取值的内部行；列顺序可变化，列集合不可变化。 */
function readTable(response) {
  const table = unwrapResponse(response);
  if (!isRecord(table)) throw new Error('分行测试响应必须为对象');
  if (!Array.isArray(table.columns) || !Array.isArray(table.rows)) {
    throw new Error('分行测试响应必须包含 columns 和 rows');
  }
  const columns = table.columns.map(columnName);
  if (columns.length !== BRANCH_TEST_COLUMNS.length || columns.some(column => !column)) {
    throw new Error('分行测试响应列定义不完整');
  }
  if (new Set(columns).size !== columns.length || !columns.every(column => COLUMN_SET.has(column))) {
    throw new Error('分行测试响应列定义不匹配固定契约');
  }
  for (const column of BRANCH_TEST_COLUMNS) {
    if (!columns.includes(column)) throw new Error(`分行测试响应缺少列 ${column}`);
  }
  const indexes = Object.fromEntries(columns.map((column, index) => [column, index]));
  const rows = table.rows.map((rawRow, rowIndex) => {
    if (!Array.isArray(rawRow) || rawRow.length !== columns.length) {
      throw new Error(`分行测试响应第 ${rowIndex + 1} 行不是固定二维形状`);
    }
    const row = {};
    for (const column of BRANCH_TEST_COLUMNS) {
      const rawValue = rawRow[indexes[column]];
      if (NUMERIC_COLUMNS.has(column)) row[column] = numberValue(rawValue, `${column}(${rowIndex + 1})`);
      else if (column === 'org_code') row[column] = orgCodeOf(rawValue);
      else if (column === 'kind') row[column] = text(rawValue, `kind(${rowIndex + 1})`, { required: true });
      else if (column === 'key') row[column] = text(rawValue, `key(${rowIndex + 1})`);
      else if (column === 'data_date') row[column] = dateText(rawValue);
      else if (column === 'unit') row[column] = text(rawValue, `unit(${rowIndex + 1})`);
      else if (TEXT_COLUMNS.has(column)) row[column] = text(rawValue, `${column}(${rowIndex + 1})`);
      else row[column] = rawValue === undefined ? null : rawValue;
    }
    if (!KIND_SET.has(row.kind)) throw new Error(`kind 不受支持: ${row.kind}`);
    if (row.unit !== null && !UNIT_SET.has(row.unit)) throw new Error(`unit 不受支持: ${row.unit}`);
    return row;
  });
  return { table, columns, rows };
}

function qualityOf(table, rows) {
  const quality = table.quality;
  if (!isRecord(quality)) throw new Error('分行测试响应缺少质量信息');
  if (quality.dataClassification !== 'TEST') throw new Error('分行测试数据质量分类必须为 TEST');
  const version = text(quality.version, 'quality.version', { required: true });
  if (!version.startsWith('TEST_BRANCH_OPERATING_')) throw new Error('分行测试批次 version 不匹配');
  if (quality.batchId !== undefined && quality.batchId !== null) {
    const batchId = text(quality.batchId, 'quality.batchId', { required: true });
    if (batchId !== version) throw new Error('分行测试响应存在跨批次质量信息');
  }
  const dataDate = dateText(quality.dataDate, 'quality.dataDate');
  if (!dataDate) throw new Error('分行测试质量缺少 dataDate');
  const rowDates = rows.map(row => row.data_date).filter(Boolean);
  const maxDate = rowDates.length ? rowDates.reduce((max, date) => date > max ? date : max, rowDates[0]) : null;
  if (maxDate !== dataDate) throw new Error('quality.dataDate 不是全体响应行最大日期');
  return { ...quality, version, dataDate };
}

function packageOf(view) {
  if (!isRecord(view)) throw new Error('分行测试视图必须为对象');
  return parseObject(view.renderPackageJson ?? view.render_package_json ?? view.renderPackage ?? view.render_package, 'renderPackageJson');
}

function chartWidgets(components, result = []) {
  if (!Array.isArray(components)) throw new Error('发布包 components 必须为数组');
  for (const component of components) {
    if (!isRecord(component)) throw new Error('发布包组件必须为对象');
    if (component.component === 'ChartWidget') result.push(component);
    // Jackson serializes the DTO's absent child list as null.  Treat null as
    // an empty child list, while still rejecting a non-null non-array shape.
    if (component.children !== undefined && component.children !== null) {
      chartWidgets(component.children, result);
    }
  }
  return result;
}

function positiveBlockId(value) {
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error('发布包 blockId 必须为正整数');
  return value;
}

function directoryOf(view) {
  const source = view?.panoramaInstitutions ?? view?.panorama_institutions;
  if (!Array.isArray(source)) throw new Error('分行测试视图缺少授权机构目录');
  const seen = new Set();
  return source.map((item, index) => {
    if (!isRecord(item)) throw new Error(`授权机构目录第 ${index + 1} 项无效`);
    const code = orgCodeOf(item.orgCode ?? item.org_code, `授权机构目录第 ${index + 1} 项机构号`);
    if (seen.has(code)) throw new Error(`授权机构目录存在重复机构号: ${code}`);
    seen.add(code);
    return { ...item, orgCode: code };
  });
}

/** 严格读取测试屏发布包，返回可信的 attention 组件/快照身份。 */
export function parseBranchTestView(response) {
  if (!isRecord(response)) throw new Error('分行测试视图响应必须为对象');
  if (response.state !== 'published') throw new Error('分行测试视图必须为 published');
  if (response.screenCode !== BRANCH_TEST_SCREEN_CODE) throw new Error('分行测试屏编码不匹配');
  if (response.runtimeSchemaVersion !== BRANCH_TEST_SCHEMA_VERSION) throw new Error('分行测试运行 schema 必须为 2');
  if (response.orgScopeMode !== 'NAMED_GROUP') throw new Error('分行测试屏机构范围必须为 NAMED_GROUP');
  const renderPackage = packageOf(response);
  if (renderPackage.schemaVersion !== BRANCH_TEST_SCHEMA_VERSION) throw new Error('发布包 schema 必须为 2');
  const style = renderPackage.canvasStyle;
  if (!isRecord(style) || style.dataClassification !== 'TEST') throw new Error('发布包数据分类必须为 TEST');
  const presentation = style.presentation;
  if (!isRecord(presentation) || presentation.type !== 'CODE' || presentation.template !== BRANCH_TEST_TEMPLATE) {
    throw new Error('分行测试发布包模板身份不匹配');
  }
  const components = renderPackage.components;
  const charts = chartWidgets(components);
  const attentionCharts = charts.filter(item => item.propValue?.bindingKey === 'attention');
  if (attentionCharts.length !== 1) throw new Error('发布包必须恰有一个唯一 attention ChartWidget');
  if (attentionCharts[0].innerType !== BRANCH_TEST_INNER_TYPE) {
    throw new Error('attention ChartWidget innerType 必须为 TABLE_LIST');
  }
  const chartIds = charts.map(component => positiveBlockId(component.blockId));
  if (new Set(chartIds).size !== chartIds.length) throw new Error('发布包 ChartWidget blockId 重复');
  const snapshots = renderPackage.bindSnapshots;
  if (!isRecord(snapshots)) throw new Error('发布包缺少 bindSnapshots');
  const expectedKeys = new Set(chartIds.map(String));
  const actualKeys = Object.keys(snapshots);
  if (actualKeys.length !== expectedKeys.size || actualKeys.some(key => !expectedKeys.has(key))) {
    throw new Error('发布包组件与 bindSnapshots 身份不一致');
  }
  const snapshotById = {};
  for (const chart of charts) {
    const blockId = positiveBlockId(chart.blockId);
    const snapshot = snapshots[String(blockId)];
    if (!isRecord(snapshot) || !isRecord(snapshot.bind)) throw new Error('发布包缺少可信绑定快照');
    if (snapshot.componentType !== BRANCH_TEST_INNER_TYPE) {
      throw new Error('发布包绑定快照 componentType 必须为 TABLE_LIST');
    }
    if (!Number.isSafeInteger(snapshot.bind.dsId) || snapshot.bind.dsId <= 0) {
      throw new Error('发布包绑定快照缺少可信数据源身份');
    }
    snapshotById[String(blockId)] = snapshot;
  }
  const institutions = directoryOf(response);
  const attentionChart = attentionCharts[0];
  const attentionSnapshot = snapshotById[String(attentionChart.blockId)];
  return {
    ...response,
    renderPackage,
    institutions,
    attention: {
      blockId: attentionChart.blockId,
      component: attentionChart,
      snapshot: attentionSnapshot,
      bind: attentionSnapshot.bind
    }
  };
}

/** 构造 schema2 的发布包身份请求；请求协议不再携带 dsId。 */
export function buildBranchTestRequest(view, orgCode = '') {
  const parsed = parseBranchTestView(view);
  const code = String(orgCode ?? '').trim();
  const institutions = parsed.institutions || directoryOf(parsed);
  if (code && !institutions.some(item => item.orgCode === code)) {
    throw new Error(`请求机构不在授权目录中: ${code}`);
  }
  return {
    schemaVersion: BRANCH_TEST_SCHEMA_VERSION,
    screenCode: BRANCH_TEST_SCREEN_CODE,
    blockId: parsed.attention.blockId,
    period: 'LATEST',
    contextParams: code ? { orgCode: code } : {}
  };
}

/**
 * 只把授权目录与服务端返回行的 org_code 做交集。
 * 同一机构拥有多种 kind/日期行是合法的，因此这里只拒绝目录重复和目录外行。
 */
export function branchTestInstitutions(response, view) {
  const parsedView = parseBranchTestView(view);
  const { rows } = readTable(response);
  const institutions = parsedView.institutions || directoryOf(parsedView);
  const authorized = new Set(institutions.map(item => item.orgCode));
  const returned = new Set();
  for (const row of rows) {
    if (!authorized.has(row.org_code)) throw new Error(`响应机构不在授权目录中: ${row.org_code}`);
    returned.add(row.org_code);
  }
  return institutions.filter(item => returned.has(item.orgCode));
}

function rowKey(row, index) {
  const key = row.key;
  if (row.kind === 'history') {
    const date = row.data_date;
    if (!key || !date) throw new Error(`history 第 ${index + 1} 行缺少 key 或 data_date`);
    return `${row.kind}|${key}|${date}`;
  }
  if (!key) throw new Error(`${row.kind} 第 ${index + 1} 行缺少 key`);
  return `${row.kind}|${key}`;
}

function requireUniqueRows(rows) {
  const seen = new Set();
  rows.forEach((row, index) => {
    const identity = rowKey(row, index);
    if (seen.has(identity)) throw new Error(`响应存在重复 key: ${identity}`);
    seen.add(identity);
  });
}

function requireUnit(row, expected, field) {
  if (row[field] === null || row[field] === undefined) return;
  if (row.unit !== expected) throw new Error(`${row.kind}.${row.key} 的 ${field} 单位必须为 ${expected}`);
}

function requireUnitIfPresent(row, expected, field = 'value') {
  if (row.unit !== null && row.unit !== expected) {
    throw new Error(`${row.kind}.${row.key} 的 ${field} 单位必须为 ${expected}`);
  }
}

function amountValue(row, field, displayScale = HUNDRED_MILLION, usesRowUnit = false) {
  const raw = row[field];
  if (raw === null) return null;
  if (usesRowUnit) requireUnit(row, 'YUAN', field);
  return raw / displayScale;
}

function countValue(row, field, usesRowUnit = false) {
  const raw = row[field];
  if (raw === null) return null;
  if (usesRowUnit) requireUnit(row, 'COUNT', field);
  return raw;
}

function percentValue(row, field, usesRowUnit = false) {
  const raw = row[field];
  if (raw === null) return null;
  if (usesRowUnit) requireUnit(row, 'PERCENT', field);
  return raw;
}

function addHistoryValue(target, row, field) {
  const value = row[field];
  if (value === null) return;
  requireUnitIfPresent(row, 'YUAN', field);
  const converted = value / HUNDRED_MILLION;
  if (target[field] !== null && target[field] !== undefined) {
    throw new Error(`history ${row.data_date} 的 ${field} 重复`);
  }
  target[field] = converted;
}

function utcDate(value) {
  return new Date(`${value}T00:00:00Z`);
}

function monthEnd(year, monthIndex) {
  return new Date(Date.UTC(year, monthIndex + 1, 0)).toISOString().slice(0, 10);
}

function previousMonthEnd(asOf, monthsBefore) {
  const date = utcDate(asOf);
  return monthEnd(date.getUTCFullYear(), date.getUTCMonth() - monthsBefore);
}

function shiftedDate(asOf, monthsBefore) {
  const source = utcDate(asOf);
  const targetMonth = new Date(Date.UTC(source.getUTCFullYear(), source.getUTCMonth() - monthsBefore, 1));
  const lastDay = new Date(Date.UTC(targetMonth.getUTCFullYear(), targetMonth.getUTCMonth() + 1, 0)).getUTCDate();
  const day = Math.min(source.getUTCDate(), lastDay);
  return new Date(Date.UTC(targetMonth.getUTCFullYear(), targetMonth.getUTCMonth(), day)).toISOString().slice(0, 10);
}

function historyPoint(date, historyByDate) {
  const point = historyByDate.get(date);
  return point ? { date, deposit: point.deposit, loan: point.loan } : { date, deposit: null, loan: null };
}

function buildHistory(rows, dataDate) {
  const historyByDate = new Map();
  for (const row of rows) {
    if (!row.data_date || row.data_date > dataDate) continue;
    let point = historyByDate.get(row.data_date);
    if (!point) {
      point = { date: row.data_date, deposit: null, loan: null };
      historyByDate.set(row.data_date, point);
    }
    addHistoryValue(point, row, 'deposit');
    addHistoryValue(point, row, 'loan');
  }
  const dates = [5, 4, 3, 2, 1].map(months => previousMonthEnd(dataDate, months)).concat(dataDate);
  const uniqueDates = [...new Set(dates)].sort();
  const trend = uniqueDates.map(date => historyPoint(date, historyByDate));
  const comparisonDates = [shiftedDate(dataDate, 1), shiftedDate(dataDate, 12)];
  const comparisonTrend = [...new Set(comparisonDates)].sort().map(date => historyPoint(date, historyByDate));
  return { trend, comparisonTrend };
}

function kpiModel(row) {
  const key = row.key;
  const amountKeys = new Set(['deposit', 'depositAverage', 'loan', 'revenue']);
  let value;
  let unit;
  if (amountKeys.has(key)) {
    value = amountValue(row, 'value', HUNDRED_MILLION, true);
    unit = '亿元';
  } else if (key === 'customers') {
    value = countValue(row, 'value', true);
    unit = '户';
  } else if (key === 'rate') {
    value = percentValue(row, 'value', true);
    unit = '%';
  } else {
    return null;
  }
  return {
    key,
    label: row.name,
    value,
    unit,
    yoy: row.yoy,
    mom: row.mom,
    date: row.data_date,
    ...(row.status === null ? {} : { status: row.status })
  };
}

function targetModel(row) {
  requireUnitIfPresent(row, 'YUAN', 'actual');
  const actual = amountValue(row, 'actual');
  const target = amountValue(row, 'target');
  const gap = amountValue(row, 'gap');
  const rawActual = row.actual;
  const rawTarget = row.target;
  const rate = rawActual === null || rawTarget === null || rawTarget === 0 ? null : rawActual / rawTarget * 100;
  return { key: row.key, label: row.name, actual, target, unit: '亿元', date: row.data_date, rate, gap };
}

function compositionModel(row) {
  return { key: row.key, name: row.name, value: amountValue(row, 'value', HUNDRED_MILLION, true), unit: '亿元', date: row.data_date };
}

function marketingModel(row) {
  requireUnitIfPresent(row, 'COUNT', 'count');
  return { key: row.key, label: row.name, count: countValue(row, 'count') };
}

function projectModel(row) {
  requireUnitIfPresent(row, 'YUAN', 'amount');
  return {
    name: row.name,
    status: row.status,
    owner: row.owner,
    days: row.days,
    amount: amountValue(row, 'amount', TEN_THOUSAND),
    unit: '万元'
  };
}

function teamModel(row) {
  return {
    name: row.name,
    rate: percentValue(row, 'rate'),
    increase: row.increase === null ? null : row.increase / TEN_THOUSAND,
    increaseUnit: '万元',
    // team 行同时承载百分比、金额和待办三个固定列；每个列的单位由固定
    // 输出契约确定，不能用单行 unit（它只能描述 value）覆盖其它列。
    pending: row.pending
  };
}

function attentionModel(row) {
  requireUnitIfPresent(row, 'COUNT', 'count');
  return { label: row.name, count: countValue(row, 'count'), owner: row.owner };
}

function sourceEvidence(quality) {
  return [
    { label: '数据批次', detail: quality.version },
    { label: '数据分类', detail: quality.dataClassification },
    { label: '数据日期', detail: quality.dataDate }
  ];
}

/**
 * 将一个已通过发布/质量/列契约校验的机构响应转换为 BranchOperatingDashboard 模型。
 * 所有业务行先按服务端 org_code 精确收窄，再做派生换算，禁止从其他机构补缺。
 */
export function buildBranchTestModel(response, view, orgCode) {
  const parsedView = parseBranchTestView(view);
  const { table, rows } = readTable(response);
  const quality = qualityOf(table, rows);
  const requestedOrgCode = orgCodeOf(orgCode, '请求机构');
  const institutions = branchTestInstitutions(response, parsedView);
  const institution = institutions.find(item => item.orgCode === requestedOrgCode);
  if (!institution) throw new Error(`请求机构不在测试响应与授权目录交集中: ${requestedOrgCode}`);
  const selectedRows = rows.filter(row => row.org_code === requestedOrgCode);
  if (!selectedRows.length) throw new Error(`请求机构没有测试数据行: ${requestedOrgCode}`);
  requireUniqueRows(selectedRows);

  const kpiRows = selectedRows.filter(row => row.kind === 'kpi');
  const kpiByKey = new Map();
  for (const row of kpiRows) {
    if (KPI_KEY_SET.has(row.key)) {
      if (kpiByKey.has(row.key)) throw new Error(`KPI 存在重复 key: ${row.key}`);
      kpiByKey.set(row.key, row);
    }
  }
  const missingKpis = KPI_KEYS.filter(key => !kpiByKey.has(key) || kpiByKey.get(key).value === null);
  if (missingKpis.length) throw new Error(`KPI 数据缺口: ${missingKpis.join('、')}`);
  const kpis = KPI_KEYS.map(key => kpiModel(kpiByKey.get(key))).filter(Boolean);

  const targets = selectedRows.filter(row => row.kind === 'target').map(targetModel);
  const history = buildHistory(selectedRows.filter(row => row.kind === 'history'), quality.dataDate);
  const composition = selectedRows.filter(row => row.kind === 'composition').map(compositionModel);
  const marketing = selectedRows.filter(row => row.kind === 'marketing').map(marketingModel);
  const projects = selectedRows.filter(row => row.kind === 'project').map(projectModel);
  const teams = selectedRows.filter(row => row.kind === 'team').map(teamModel);
  const attention = selectedRows.filter(row => row.kind === 'attention').map(attentionModel);

  return {
    orgCode: requestedOrgCode,
    orgName: institution.orgName ?? institution.org_name ?? null,
    institutions,
    dataDate: quality.dataDate,
    sourceLabel: BRANCH_TEST_SOURCE_LABEL,
    kpis,
    targets,
    targetDate: quality.dataDate,
    trend: history.trend,
    trendUnit: '亿元',
    comparisonTrend: history.comparisonTrend,
    composition,
    marketing,
    projects,
    teams,
    attention,
    gaps: {},
    sources: sourceEvidence(quality)
  };
}
