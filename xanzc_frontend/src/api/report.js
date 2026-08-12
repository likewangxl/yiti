// 报表分析 API —— 全部对接 yiti `/api/reports/...`
//
// 后端控制器分布（来自 yiti/report-analytics-center/...controller/）：
//   - DynamicQueryController            POST /api/reports/dynamic-query
//   - DynamicQueryExportController      POST /api/reports/dynamic-query/export
//   - SavedQueryController              GET/POST/PUT/DELETE /api/reports/saved-queries[/{id}]
//   - DashboardController               GET /api/reports/dashboard/{president|org/{code}|emp/{id}}
//   - RptSqlProbeController             POST /api/reports/sql-probe/execute
//                                       GET  /api/reports/sql-probe/history[/{id}]
//                                       GET  /api/reports/sql-probe/schema-whitelist
//   - MetaController                    GET  /api/reports/query-dimensions
//   - RptExportController               GET  /api/reports/export-tasks/{id}[/download]
//
// 所有调用走 http.js 的 call(method, path, config, fallback)，后端不可用时使用 mock 兜底。
import { call } from './http';
import http, { API_BASE } from './http';
import {
  reportDynamic, reportDashboard, reportPresets, reportSchemes,
  reportDimensions, reportSqlWhitelist, reportSqlHistory, reportSqlProbeResult
} from '@/mock';

// ===== 维度元数据 =====
//
// yiti MetaController：GET /api/reports/query-dimensions?dim={EMP|ORG|CUST}
//   - dim 为后端 @RequestParam @NotBlank，不传会 400 (VALID_002)
//   - 返回 QueryDimensionRespDTO：{ dim, dimName, metrics:[{ groupCode, groupName, children:[...] }] }
//     —— 形态是「单维度的指标分组树」，**不是** dim 列表（[EMP/ORG/CUST] 在前端硬编码）
//
// mock fallback 仍是 [{code,label}, ...] 数组（历史形态），调用方需自己识别响应类型。
export function getQueryDimensions(dim) {
  return call('get', '/reports/query-dimensions', { params: { dim } }, reportDimensions);
}

// ===== 动态查询 =====
//
// 后端 DynamicQueryReqDTO:{ dim, subjectIds:[id...], metricCodes:[code...], dataDate:"2026-04-22" }
// 后端 DynamicQueryRespDTO:{ dim, dataDate, columns:[{metricCode,metricName,unit}],
//                             rows:[{ subjectId, subjectName, [metricCode]:value, ... }], rowCount }
//
// view 习惯使用 row.subject 渲染对象列(table 的 prop="subject"),这里把 subjectName 派生成 subject。
// mock fallback 形态:{ metrics, subjects, result:[{ subject, [code]:value }] } 保持不变。
export async function queryDynamic(payload) {
  const body = {
    dim: payload.dim,
    subjectIds:  (payload.subjects || []).map(s => (typeof s === 'string' ? s : s?.id)).filter(Boolean),
    metricCodes: (payload.metrics  || []).map(m => (typeof m === 'string' ? m : m?.code)).filter(Boolean),
    dataDate:    payload.date  // 后端 LocalDate 接受 ISO 字符串
  };
  // 服务端分页（可选）：传了 pageNo/pageSize 后端只返回当前页对象，不选对象=按范围查全部也不会拖爆
  if (payload.pageNo)   body.pageNo   = payload.pageNo;
  if (payload.pageSize) body.pageSize = payload.pageSize;
  const r = await call('post', '/reports/dynamic-query', { data: body }, reportDynamic);
  if (Array.isArray(r?.rows)) {
    r.rows = r.rows.map(row => {
      // 后端 subjectName -> view 期望的 subject;保留 subjectId 备用
      const out = { ...row };
      if (out.subject == null && out.subjectName != null) out.subject = out.subjectName;
      return out;
    });
  }
  return r;
}
export function exportDynamic(payload) {
  const body = {
    dim: payload.dim,
    subjectIds:  (payload.subjects || []).map(s => (typeof s === 'string' ? s : s?.id)).filter(Boolean),
    metricCodes: (payload.metrics  || []).map(m => (typeof m === 'string' ? m : m?.code)).filter(Boolean),
    dataDate:    payload.date
  };
  return call('post', '/reports/dynamic-query/export', { data: body }, () => ({ taskId: 'EXP-MOCK-' + Date.now() }));
}

// 动态查询「选择对象」数据范围：{ mode: ALL|ORG_SUBTREE|SELF, selfEmpId, selfName, orgCodes:[] }
// dim 决定数据范围来源：EMP→REPORT_DYN_EMP（员工维度）/ ORG→REPORT_DYN_ORG（机构维度），两维度独立配置
export async function getPickerScope(dim = 'EMP') {
  return call('get', '/reports/scope/picker', { params: { dim } }, { mode: 'ALL', selfEmpId: '', selfName: '', orgCodes: [] });
}

// 动态查询「选择对象」员工搜索：搜 PT_USER（按工号/姓名），REPORT 权限
export async function searchReportEmployees(keyword, limit = 20) {
  const r = await call('get', '/reports/employees/search', { params: { keyword, limit } }, []);
  return Array.isArray(r) ? r : (r?.records || []);
}

// 动态查询「选择对象」客户搜索：按客户名/客户号搜（不限范围）；返回 [{id,name,org}]
export async function searchReportCustomers(keyword, limit = 20) {
  const r = await call('get', '/reports/customers/search', { params: { keyword, limit } }, []);
  return Array.isArray(r) ? r : (r?.records || []);
}

// 同步导出：直接拿后端 xlsx 流并触发浏览器下载（不走异步任务/MinIO）
export async function exportDynamicFile(payload) {
  const body = {
    dim: payload.dim,
    subjectIds:  (payload.subjects || []).map(s => (typeof s === 'string' ? s : s?.id)).filter(Boolean),
    metricCodes: (payload.metrics  || []).map(m => (typeof m === 'string' ? m : m?.code)).filter(Boolean),
    dataDate:    payload.date
  };
  const resp = await http.request({
    method: 'post',
    url: API_BASE + '/reports/dynamic-query/export-file',
    data: body,
    responseType: 'blob'
  });
  // 拦截器对非 envelope（blob）原样返回，这里 resp 即 Blob
  const blob = resp instanceof Blob ? resp : new Blob([resp]);
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `动态指标查询_${payload.dim || ''}_${payload.date || ''}.xlsx`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

// ===== 保存方案（动态查询的"我的方案"）=====
//
// 后端契约（SavedQuerySaveReqDTO / SavedQuerySummaryDTO / SavedQueryDetailRespDTO）：
//   保存入参   { name, dim, subjectIds: '["E001",...]'(JSON字符串), metricCodes: '["M0001",...]' }
//   列表项     { id, name, dim, createdTime, updatedTime }
//   详情       { id, name, dim, subjectIds, metricCodes, version, createdTime, updatedTime }
//
// 前端 view 用的友好形态：metrics/subjects 是数组。这里在 api 层做双向适配。

/** 列表 —— 透传后端摘要 DTO（mock fallback 同结构）。 */
export function listSavedQueries(params = {}) {
  return call('get', '/reports/saved-queries', { params }, reportSchemes);
}

/** 详情 —— 把 subjectIds / metricCodes JSON 字符串解成数组，并派生 view 友好字段 subjects/metrics。 */
export async function getSavedQuery(id) {
  const r = await call('get', `/reports/saved-queries/${id}`, {},
    () => reportSchemes.find(s => s.id === id) || reportSchemes[0]);
  if (!r) return r;
  // 真接口 → JSON 字符串；mock fallback → 可能本身就是数组
  if (typeof r.metricCodes === 'string') {
    try { r.metrics = JSON.parse(r.metricCodes); } catch { r.metrics = []; }
  } else if (Array.isArray(r.metrics)) {
    // mock 兜底已是数组
  } else {
    r.metrics = [];
  }
  if (typeof r.subjectIds === 'string') {
    try { r.subjects = JSON.parse(r.subjectIds); } catch { r.subjects = []; }
  } else if (Array.isArray(r.subjects)) {
    // mock 兜底已是数组
  } else {
    r.subjects = [];
  }
  return r;
}

// 归一化对象列表为 [{id,name,org}]，兼容传入纯 id 字符串（老调用）或完整对象
function normSubjects(list) {
  return (list || []).map(s =>
    typeof s === 'string'
      ? { id: s, name: s, org: '' }
      : { id: s?.id, name: s?.name || s?.id, org: s?.org || '' }
  ).filter(s => s.id);
}

/**
 * 保存方案。
 * @param payload {{ name:string, dim:string, metrics:string[], subjects:Array<string|{id,name,org}> }}
 * 内部把 metrics → metricCodes JSON 字符串、subjects → subjectIds JSON 字符串（存完整对象含名称）。
 */
export function saveQuery(payload) {
  // 存「完整对象」{id,name,org} 而非仅 id —— 载入方案时可直接反显名称，无需再反查候选池
  const subjects    = normSubjects(payload.subjects);
  const metricCodes = (payload.metrics  || []).map(m => (typeof m === 'string' ? m : m?.code)).filter(Boolean);
  const body = {
    name: payload.name,
    dim:  payload.dim,
    subjectIds:  JSON.stringify(subjects),
    metricCodes: JSON.stringify(metricCodes)
  };
  return call('post', '/reports/saved-queries', { data: body },
    () => ({ id: 'SQ-MOCK-' + Date.now(), name: body.name, dim: body.dim, subjectIds: body.subjectIds, metricCodes: body.metricCodes }));
}

/** 更新方案（部分字段）。乐观锁 version 由 getSavedQuery 取出后回带。 */
export function updateSavedQuery(id, payload) {
  const body = {};
  if (payload.name != null) body.name = payload.name;
  if (payload.dim  != null) body.dim  = payload.dim;
  if (Array.isArray(payload.subjects)) {
    body.subjectIds = JSON.stringify(normSubjects(payload.subjects));
  }
  if (Array.isArray(payload.metrics)) {
    body.metricCodes = JSON.stringify(payload.metrics.map(m => (typeof m === 'string' ? m : m?.code)).filter(Boolean));
  }
  // 后端契约字段是 expectedVersion(乐观锁),兼容老调用方仍传 version
  const ev = payload.expectedVersion != null ? payload.expectedVersion : payload.version;
  if (ev != null) body.expectedVersion = ev;
  return call('put', `/reports/saved-queries/${id}`, { data: body }, () => ({ id, ...payload }));
}

export function deleteSavedQuery(id) {
  return call('delete', `/reports/saved-queries/${id}`, {}, () => ({ ok: true }));
}

// ===== 行长仪表盘 =====
// 后端 DashboardController 入参约定为 ?dataDate=YYYY-MM-DD（V1.14 # 2 后），
// 前端历史调用方仍传 { date }（看 Dashboard.vue / getDashboard 兼容入口），
// 这里做一次性映射 date → dataDate，避免每个调用点重复改造。
function mapDashboardParams(params = {}) {
  const { date, dataDate, ...rest } = params || {};
  const finalDate = dataDate ?? date;
  return finalDate != null ? { ...rest, dataDate: finalDate } : rest;
}

// 后端 PresidentDashboardRespDTO 的字段名与 Dashboard.vue 消费模型不同。
// 适配层只统一容器结构，业务字段必须原样保留：趋势系列的 name/unit 和排名的
// rank/orgName/achievementRate/target/actual 都由页面直接消费，避免把 actual 误当达成率。
function normalizeChart(chart) {
  if (!chart || typeof chart !== 'object') return { xAxis: [], series: [] };
  return {
    xAxis: Array.isArray(chart.xAxis) ? chart.xAxis : (Array.isArray(chart.xaxis) ? chart.xaxis : []),
    series: Array.isArray(chart.series) ? chart.series.map(series => ({ ...series })) : []
  };
}

function normalizeLegacyTrend(trend) {
  const xAxis = Array.isArray(trend?.xAxis) ? trend.xAxis : (Array.isArray(trend?.months) ? trend.months : []);
  if (Array.isArray(trend?.series)) {
    return { xAxis, series: trend.series.map(series => ({ ...series })) };
  }
  // 仅兼容历史 mock 结构；真实接口始终优先使用带 name/unit 的 series。
  const series = [];
  if (Array.isArray(trend?.deposit)) series.push({ name: '存款', unit: trend.depositUnit || '', data: trend.deposit });
  if (Array.isArray(trend?.loan)) series.push({ name: '贷款', unit: trend.loanUnit || '', data: trend.loan });
  return { xAxis, series };
}

function normalizeRanking(items) {
  return (Array.isArray(items) ? items : []).map((item, index) => ({
    ...item,
    rank: item.rank ?? (index + 1),
    orgName: item.orgName ?? item.org ?? '',
    achievementRate: item.achievementRate ?? null,
    target: item.target ?? null,
    actual: item.actual ?? item.val ?? null,
    // 当前后端 DTO 未固定 unit 字段；一旦后端补充，页面使用其真实单位而不臆造金额单位。
    unit: item.unit ?? item.actualUnit ?? item.targetUnit ?? ''
  }));
}

function adaptDashboardResp(r) {
  if (!r || typeof r !== 'object') return r;
  if (r.trend) {
    return {
      ...r,
      date: r.dataDate || r.date || '',
      trend: normalizeLegacyTrend(r.trend),
      ranking: normalizeRanking(r.ranking)
    };
  }

  const depositTrend = normalizeChart(r.depositTrend);
  const loanTrend = normalizeChart(r.loanTrend);
  // 存款与贷款趋势通常使用同一时间轴；当其中一项为空时，使用另一项的有效时间轴。
  const xAxis = depositTrend.xAxis.length ? depositTrend.xAxis : loanTrend.xAxis;

  return {
    org: r.org || '总行',
    date: r.dataDate || r.date || '',
    stats: Array.isArray(r.stats) ? r.stats : [],
    trend: {
      xAxis,
      series: [...depositTrend.series, ...loanTrend.series]
    },
    ranking: normalizeRanking(r.orgRanking),
    // 透传后端原字段，方便其他组件按需消费（含 V1.14 # 2 stats 元数据）
    summaryMetrics: r.summaryMetrics,
    topCustomers: r.topCustomers,
    dataVersion: r.dataVersion
  };
}

export function getDashboardPresident(params = {}) {
  // 不传 mock 兜底：行长仪表盘要展示真实数据，接口失败宁可空也不显示假数据
  return call('get', '/reports/dashboard/president',
    { params: mapDashboardParams(params) }, null).then(adaptDashboardResp);
}
export function getDashboardByOrg(orgCode, params = {}) {
  return call('get', `/reports/dashboard/org/${orgCode}`,
    { params: mapDashboardParams(params) }, reportDashboard).then(adaptDashboardResp);
}
export function getDashboardByEmp(empId, params = {}) {
  return call('get', `/reports/dashboard/emp/${empId}`,
    { params: mapDashboardParams(params) }, reportDashboard).then(adaptDashboardResp);
}
// 兼容老调用（Dashboard.vue 旧版本）
export function getDashboard(orgCode = '0001', date) {
  return getDashboardPresident({ orgCode, date });
}
// 行长仪表盘 —— PDF 导出（异步任务）
export function exportDashboardPdf(payload) {
  return call('post', '/reports/dashboard/president/export', { data: payload },
    () => ({ taskId: 'PDF-MOCK-' + Date.now(), status: 'PENDING' }));
}

// ===== 预置报表 =====
// 后端没有 /api/reports/presets 这个 endpoint，"预置报表"语义改为聚合下面 3 个真实汇总接口（C.2/C.3/C.4）
// 前端 Presets.vue 渲染成 3 张卡片，点击卡片调对应汇总接口

// C.3 绩效汇总 PerfSummary（必填 dim/cycleType，可选 subjectIds[]/pageNo/pageSize）
export function getPerfSummary(params = {}) {
  return call('get', '/reports/perf-summary', { params }, { records: [], total: 0 });
}
export function exportPerfSummary(payload) {
  return call('post', '/reports/perf-summary/export', { data: payload }, { taskId: 'EXP-MOCK-' + Date.now() });
}

// C.4 客户池汇总 CustPoolSummary（全可选：orgId/pageNo/pageSize）
export function getCustPoolSummary(params = {}) {
  return call('get', '/reports/customer-pool-summary', { params }, { records: [], total: 0 });
}
export function exportCustPoolSummary(payload) {
  return call('post', '/reports/customer-pool-summary/export', { data: payload }, { taskId: 'EXP-MOCK-' + Date.now() });
}

// C.2 机构触达汇总 TouchSummary（必填 startDate/endDate，可选 orgId/pageNo/pageSize）
export function getTouchSummary(params = {}) {
  return call('get', '/reports/touch-task-summary', { params }, { records: [], total: 0 });
}
export function exportTouchSummary(payload) {
  return call('post', '/reports/touch-task-summary/export', { data: payload }, { taskId: 'EXP-MOCK-' + Date.now() });
}

// ===== SQL 探查 =====
export function executeSqlProbe(payload) {
  return call('post', '/reports/sql-probe/execute', { data: payload }, reportSqlProbeResult);
}
export function getSqlWhitelist() {
  return call('get', '/reports/sql-probe/schema-whitelist', {}, reportSqlWhitelist);
}
export function getSqlHistory(params = {}) {
  return call('get', '/reports/sql-probe/history', { params }, reportSqlHistory);
}
export function getSqlHistoryItem(id) {
  return call('get', `/reports/sql-probe/history/${id}`, {}, () => reportSqlHistory.find(h => h.id === id) || reportSqlHistory[0]);
}
// SQL 探查「异步下载」：创建任务(后台跑) / 轮询任务列表 / 下载文件
export function createSqlExport(payload) {
  return call('post', '/reports/sql-probe/export', { data: payload }, { taskId: 'mock' });
}
export function listSqlExportTasks() {
  return call('get', '/reports/sql-probe/export/tasks', {}, []);
}
// 下载：responseType=blob 直接拿字节（拦截器对非 envelope 原样返回 blob），调用方用临时 <a download> 触发，不跳转
export function downloadSqlExportBlob(taskId) {
  return http.get(`${API_BASE}/reports/sql-probe/export/${taskId}/download`, { responseType: 'blob' });
}

// ===== 导出任务 =====
export function getExportTask(taskId) {
  return call('get', `/reports/export-tasks/${taskId}`, {}, () => ({ taskId, status: 'DONE', url: '#' }));
}

// ===== KPI/积分自由报表 =====
export function importFreeReport(reportName, file) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('reportName', reportName);
  // 不手动设 Content-Type，axios 检测到 FormData 自动加 multipart/form-data + boundary
  return call('post', '/reports/free/import', { data: formData }, { batchId: 'mock' });
}
export function queryFreeReportData(params = {}) {
  return call('get', '/reports/free/data', { params: { pageNo: 1, pageSize: 20, ...params } },
    { total: 0, records: [] });
}
export function getFreeReportColumns(batchId) {
  return call('get', '/reports/free/columns', { params: { batchId } }, []);
}
export function listFreeReportBatches(params = {}) {
  return call('get', '/reports/free/batches', { params }, []);
}
export function downloadFreeReportFile(batchId) {
  return call('get', `/reports/free/batches/${batchId}/download`, {}, { url: '#' });
}
export function deleteFreeReportBatch(batchId) {
  return call('delete', `/reports/free/batches/${batchId}`, {}, { ok: true });
}
// 禁用/启用自由报表（仅自由报表操作人）
export function disableFreeReportBatch(batchId) {
  return call('post', `/reports/free/batches/${batchId}/disable`, {}, { ok: true });
}
export function enableFreeReportBatch(batchId) {
  return call('post', `/reports/free/batches/${batchId}/enable`, {}, { ok: true });
}

// ===== 业绩分配审批历史（AMAS，只读）=====
// 后端 AmasApprovalHistoryController：
//   GET /api/reports/amas-approvals          列表（申请时间倒序，顶部查询项）
//   GET /api/reports/amas-approvals/{no}     详情（分配明细 + 审批流程）
// 分页响应经 http 拦截器返回 body.page（{ records, total, pageNo, pageSize }）。
export function listAmasApprovals(params = {}) {
  return call('get', '/reports/amas-approvals', { params }, { records: [], total: 0 });
}
export function getAmasApprovalDetail(perfAdjustNo) {
  return call('get', `/reports/amas-approvals/${encodeURIComponent(perfAdjustNo)}`, {},
    { approval: {}, allocations: [], apprRecords: [] });
}

// ===== 定价审批查询（AMAS_PRICE_APPROVAL，只读）=====
// 后端 AmasPriceApprovalController：GET /api/reports/amas-price-approvals
//   查询项：custName(客户名称,模糊) / applyFullname(申请人姓名,模糊) / apprStatus / applyTimeStart/End
//   数据范围按 BizType.REPORT 标签控制（本机构 / 本级及下级机构，基于 APPLY_DEPTNO）。
export function listPriceApprovals(params = {}) {
  return call('get', '/reports/amas-price-approvals', { params }, { records: [], total: 0 });
}
// GET /api/reports/amas-price-approvals/{priceApprId} —— 详情
export function getPriceApprovalDetail(priceApprId) {
  return call('get', `/reports/amas-price-approvals/${encodeURIComponent(priceApprId)}`, {}, {});
}

// ===== 业绩调整（PERF_ALLOC_ADJUST_APPLY，只读）—— 同页面「业绩调整」Tab =====
// 后端 AllocAdjustHistoryController：
//   GET /api/reports/alloc-adjust-applies          列表（申请时间倒序，顶部查询项）
//   GET /api/reports/alloc-adjust-applies/{id}     详情（申请信息 + 业绩分配数据）
export function listAllocAdjustApplies(params = {}) {
  return call('get', '/reports/alloc-adjust-applies', { params }, { records: [], total: 0 });
}
export function getAllocAdjustApplyDetail(id) {
  return call('get', `/reports/alloc-adjust-applies/${encodeURIComponent(id)}`, {},
    { apply: {}, items: [] });
}
