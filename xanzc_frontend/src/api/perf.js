import { call, unwrapPage } from './http';
import {
  perfMetricsTree, perfMetricDetail, perfKpiRules,
  perfTargets, perfImports, perfComputeBatches
} from '@/mock';

// 后端 perf 真实路径整理（PerfXxxController @RequestMapping）：
// MetricDef:    /api/perf/metrics            GET 列表 / POST 新增 / GET {code} 详情 / PUT 更新 / DELETE
//                                            /val-slots / {code}/{trial-run|execute|status|slot/release|refs|ref-by}
// KpiScheme:    /api/perf/kpi-schemes        GET 列表 / POST 新增 / {id} CRUD / {id}/items CRUD / {id}/publish
// TargetPlan:   /api/perf/target-plans       GET 列表 / POST 新增 / {id} GET/PUT
// TargetValue:  /api/perf/target-values      GET 列表 / POST upsert / /batch
// PerfImport:   /api/perf/import/...         POST upload / batches/{id} GET（无全局列表）
// PerfRunTask:  /api/perf/run-tasks          GET 列表 / GET {id}
// AllocAdjust:  /api/perf/alloc-adjust/list  GET 列表 / /create POST / {id} / {id}/withdraw
// TargetAdjust: /api/perf/target-adjust/list GET 列表 / /create POST / {id} / {id}/withdraw
// PerfCalc:     /api/perf/recalc             POST 触发重算
// SysControl:   /api/perf/sys-control        GET 当前 / /history / /init / /switch-version / /rollback

// ============================================================
// 指标库 Metrics
// ============================================================
// V1.10：后端 GET /api/perf/metrics 改为一次性返回 List<MetricDefRespDTO>，
// 不再分页（ResponseWrapper.success(list)）。前端不再 unwrapPage。
export function listMetrics(params = {}) {
  // params: baseDim, metricLevel, status, keyword
  return call('get', '/perf/metrics', { params }, perfMetricsTree);
}
export function getMetricsTree() {
  return listMetrics();  // 兼容老调用
}
// V1.10：GET /api/perf/metrics/categories → List<{value, label}>
export function listMetricCategories() {
  return call('get', '/perf/metrics/categories', {}, []);
}
export function getMetricDetail(code) {
  return call('get', `/perf/metrics/${code}`, {}, () => perfMetricDetail[code] || perfMetricDetail.M0002);
}
export function createMetric(data) {
  return call('post', '/perf/metrics', { data }, { id: 'mock' });
}
export function updateMetric(code, data) {
  return call('put', `/perf/metrics/${code}`, { data }, { ok: true });
}
export function deleteMetric(code, reason = '前端删除') {
  // 后端 ReleaseSlotReqDTO { reason } 走 @RequestBody
  return call('delete', `/perf/metrics/${code}`, { data: { reason } }, { ok: true });
}
export function changeMetricStatus(code, status, reason) {
  return call('put', `/perf/metrics/${code}/status`, { data: { status, reason } }, { ok: true });
}
export function trialRunMetric(code, data) {
  return call('post', `/perf/metrics/${code}/trial-run`, { data }, { result: '试运行成功（mock）' });
}
export function executeMetric(code, data) {
  return call('post', `/perf/metrics/${code}/execute`, { data }, { taskId: 'mock' });
}
export function listMetricRefs(code) {
  return call('get', `/perf/metrics/${code}/refs`, {}, []);
}
export function listMetricRefBy(code) {
  return call('get', `/perf/metrics/${code}/ref-by`, {}, []);
}
export function listValSlots(baseDim) {
  return call('get', '/perf/metrics/val-slots', { params: { baseDim } }, []);
}

// ============================================================
// KPI 规则 (KpiScheme + KpiItem)
// ============================================================
export function listKpiRules(params = {}) {
  return call('get', '/perf/kpi-schemes', { params: { pageSize: 100, ...params } }, perfKpiRules);
}
export function getKpiSchemeDetail(id) {
  return call('get', `/perf/kpi-schemes/${id}`, {}, { items: [] });
}
export function createKpiScheme(data) {
  return call('post', '/perf/kpi-schemes', { data }, { id: 'mock' });
}
export function updateKpiScheme(id, data) {
  return call('put', `/perf/kpi-schemes/${id}`, { data }, { ok: true });
}
export function deleteKpiScheme(id, reason) {
  // ReleaseSlotReqDTO 走 body
  return call('delete', `/perf/kpi-schemes/${id}`, { data: { reason } }, { ok: true });
}
export function publishKpiScheme(id, reason) {
  return call('post', `/perf/kpi-schemes/${id}/publish`, { data: { reason } }, { ok: true });
}
export function addKpiItem(schemeId, data) {
  return call('post', `/perf/kpi-schemes/${schemeId}/items`, { data }, { id: 'mock' });
}
export function updateKpiItem(schemeId, itemId, data) {
  return call('put', `/perf/kpi-schemes/${schemeId}/items/${itemId}`, { data }, { ok: true });
}
export function deleteKpiItem(schemeId, itemId, reason) {
  // ReleaseSlotReqDTO 走 body
  return call('delete', `/perf/kpi-schemes/${schemeId}/items/${itemId}`, { data: { reason } }, { ok: true });
}

// ============================================================
// 目标管理 Target
// ============================================================
export function listTargets(params = {}) {
  return call('get', '/perf/target-plans', { params: { pageSize: 50, ...params } }, perfTargets).then(unwrapPage);
}
export function getTargetPlanDetail(id) {
  return call('get', `/perf/target-plans/${id}`, {}, {});
}
// 客户财务统计展示（XAN_M98_CUST_STAT_SHOW3）：按客户编号取一行用于反显名称/余额
export function getCustStat(custId, statisDt) {
  const params = { custId, pageNo: 1, pageSize: 1 };
  // 统计日期 STATIS_DT：新建调整申请传昨日；查看/审批传申请日期-1（均 yyyy-MM-dd）
  if (statisDt) params.statisDt = statisDt;
  return call('get', '/perf/stat-show/cust', { params }, { records: [] })
    .then(unwrapPage)
    .then(r => (Array.isArray(r) ? r[0] : (r?.records || [])[0]) || null);
}
// 客户指标宽表（CUST_INDEX_RESULT）：按客户编号 + 数据日期取指定指标编号(MC_xxx)的值，
// 返回 { 指标编号: 数值 }；查无数据的指标编号在结果中缺省 → 前端显示 '-'
export function getCustIndexValues(custId, dataDate, codes) {
  const params = { custId, dataDate, codes: Array.isArray(codes) ? codes.join(',') : codes };
  return call('get', '/perf/metrics/cust-index-values', { params }, {});
}
export function createTargetPlan(data) {
  return call('post', '/perf/target-plans', { data }, { id: 'mock' });
}
export function updateTargetPlan(id, data) {
  return call('put', `/perf/target-plans/${id}`, { data }, { ok: true });
}
export function listTargetValues(params = {}) {
  return call('get', '/perf/target-values', { params }, []).then(unwrapPage);
}
export function upsertTargetValue(data) {
  return call('post', '/perf/target-values', { data }, { ok: true });
}
export function batchUpsertTargetValues(values) {
  return call('post', '/perf/target-values/batch', { data: { values } }, { ok: true });
}

// ============================================================
// 数据导入 Import
// 后端 PerfImportController 端点（V1.1）：
//   POST   /api/perf/import/upload?importType=XXX     → 上传 + 同步启动（返回 batchId String）
//   GET    /api/perf/import/batches/{batchId}         → 批次详情
//   GET    /api/perf/import/batches/{batchId}/errors  → 错误明细 List<String>
//   POST   /api/perf/import/batches/{batchId}/retry   → 重试失败批次
//   DELETE /api/perf/import/batches/{batchId}         → 删除（高危, 必填 reason）
//   ※ 后端没有"批次列表"端点；前端"最近导入"列表通过 localStorage 维护用户本地上传记录
// ============================================================
const LS_RECENT_IMPORTS = 'perfImports:recent:v1';

export function listImports() {
  // 后端无列表 API；从 localStorage 读最近 50 条本地上传记录
  // （Promise 形式保持调用方一致性）
  return Promise.resolve(loadLocalImports()).catch(() => perfImports);
}
function loadLocalImports() {
  try { return JSON.parse(localStorage.getItem(LS_RECENT_IMPORTS) || '[]'); } catch { return []; }
}
function pushLocalImport(rec) {
  const arr = loadLocalImports();
  arr.unshift(rec);
  try { localStorage.setItem(LS_RECENT_IMPORTS, JSON.stringify(arr.slice(0, 50))); } catch {}
}
function patchLocalImport(batchId, patch) {
  const arr = loadLocalImports();
  const i = arr.findIndex(x => x.batchId === batchId || x.id === batchId);
  if (i < 0) return;
  arr[i] = { ...arr[i], ...patch };
  try { localStorage.setItem(LS_RECENT_IMPORTS, JSON.stringify(arr)); } catch {}
}
function removeLocalImport(batchId) {
  const arr = loadLocalImports().filter(x => x.batchId !== batchId && x.id !== batchId);
  try { localStorage.setItem(LS_RECENT_IMPORTS, JSON.stringify(arr)); } catch {}
}
// 上传：importType ∈ TARGET / BASE_DATA / ALLOC / METRIC_DEF / METRIC_RESULT
// 后端要求 multipart `file` + 查询参 `importType`；
// V1.12 微调（2026-05-19）：METRIC_RESULT 必带 dataDate (yyyy-MM-dd)；其他类型忽略 dataDate。
// 返回 batchId（后端 ResponseWrapper.success(PerfImportUploadRespDTO).batchId）
export async function uploadImportFile(importType, file, dataDate, meta = {}) {
  const fd = new FormData();
  fd.append('file', file);
  // 走 axios 的 params 传 importType + dataDate，避免被 multipart body 吃掉
  const params = { importType };
  if (dataDate) {
    params.dataDate = dataDate;
  }
  // V1.11 后端响应破坏性变更：data 从 string 变为 PerfImportUploadRespDTO 对象，
  // 需要从对象里取 batchId 字段；mock 路径仍返回字符串，二者兼容
  // 2026-05-19 微调：返回完整对象（含 errorRows / errorSummary），让调用方区分"已提交但有错"与"完全成功"
  const resp = await call('post', '/perf/import/upload', {
    data: fd,
    params,
    headers: { 'Content-Type': 'multipart/form-data' }
  }, () => 'IMP-MOCK-' + Date.now());
  // 归一化为对象：mock 返回 string 时包成 { batchId } 兼容
  const normalized = (resp && typeof resp === 'object')
    ? { batchId: resp.batchId || resp.id, ...resp }
    : { batchId: resp };
  // 写入 localStorage 历史
  pushLocalImport({
    batchId: normalized.batchId,
    id: normalized.batchId,
    type: importType,
    file: file?.name || '-',
    fileSize: file?.size || 0,
    uploader: meta.uploader || '当前用户',
    valid: 0, total: 0,
    status: 'PROCESSING',
    time: new Date().toISOString().slice(0, 19).replace('T', ' ')
  });
  return normalized;
}
export function getImportBatch(batchId) {
  return call('get', `/perf/import/batches/${batchId}`, {}, {});
}
export function getImportErrors(batchId) {
  return call('get', `/perf/import/batches/${batchId}/errors`, {}, []);
}
export async function retryImport(batchId) {
  const r = await call('post', `/perf/import/batches/${batchId}/retry`, {}, { ok: true });
  patchLocalImport(batchId, { status: 'PROCESSING' });
  return r;
}
export async function deleteImportBatch(batchId, reason = '前端删除') {
  // 后端 @AuditLog reasonRequired=true：reason 须经 X-Audit-Reason header 或 body
  const r = await call('delete', `/perf/import/batches/${batchId}`, {
    data: { reason },
    headers: { 'X-Audit-Reason': encodeURIComponent(reason) }
  }, { ok: true });
  removeLocalImport(batchId);
  return r;
}
// 刷新单条状态（轮询/手动刷新用）
export async function refreshImportStatus(batchId) {
  try {
    const d = await getImportBatch(batchId);
    if (d?.status) {
      patchLocalImport(batchId, {
        status:  d.status,
        valid:   d.successRows ?? d.valid ?? 0,
        total:   d.totalRows   ?? d.total ?? 0,
        errMsg:  d.errorMsg ?? d.remark ?? null
      });
    }
    return d;
  } catch { return null; }
}
// 错误明细 → 触发浏览器下载 .txt
export async function downloadImportErrors(batchId) {
  const errs = await getImportErrors(batchId);
  const text = (Array.isArray(errs) ? errs : [String(errs)]).join('\n');
  const blob = new Blob([text || '(无错误明细)'], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = `import-errors-${batchId}.txt`;
  document.body.appendChild(a); a.click();
  setTimeout(() => { URL.revokeObjectURL(url); a.remove(); }, 0);
}

// ============================================================
// 业绩调整 / 目标修正
// ============================================================
export function listAdjusts(params = {}) {
  return call('get', '/perf/alloc-adjust/list', { params }, []).then(unwrapPage);
}
export function getAdjustDetail(id) {
  return call('get', `/perf/alloc-adjust/${id}`, {}, {});
}
export function submitAdjust(data) {
  return call('post', '/perf/alloc-adjust/create', { data }, { id: 'ADJ-MOCK-' + Date.now() });
}
export function withdrawAdjust(id, reason) {
  return call('post', `/perf/alloc-adjust/${id}/withdraw`, { data: { reason } }, { ok: true });
}

/** 分配预览：余额汇总 + 原业绩分配关系 */
export function getAllocPreview(params) {
  return call('get', '/report/alloc-preview', { params }, null);
}

/**
 * 员工自动补齐：按关键字模糊匹配 PT_USER 工号/登录名/中文名，返回 [{empId, username, empChnName}]。
 * 供分配明细员工号输入框联想（el-autocomplete）。
 */
export function suggestEmployees(keyword) {
  return call('get', '/perf/alloc-adjust/emp-suggest', { params: { keyword, limit: 20 } }, []);
}

/**
 * 机构自动补齐：按机构号/名称模糊匹配 EXT_ORG_INFO，返回 [{orgCode, orgName, ...}]。
 * 供原业绩分配「所属机构」输入框联想（el-autocomplete）。
 */
export function suggestOrgs(keyword) {
  return call('get', '/perf/alloc-adjust/org-suggest', { params: { keyword, limit: 20 } }, []);
}

/**
 * 业绩调整 - 我的待审批 列表（后端分页 + 4 字段过滤）.
 * @param {object} params - { keyword?, allocDim?, bizKind?, dateFrom?, dateTo?, pageNo?, pageSize? }
 * @returns PageResult 对象 { pageNo, pageSize, total, records }
 *          （不 unwrap 因为 unwrapPage 只剥 records 数组会丢 total）
 */
export function listMyAdjustTodos(params = {}) {
  return call('get', '/perf/alloc-adjust/my-todos',
    { params: { pageNo: 1, pageSize: 20, ...params } },
    { total: 0, records: [], pageNo: 1, pageSize: 20 }
  );
}

/**
 * 业绩调整 - 我的申请（后端分页 + 5 字段过滤；createdBy 后端硬约束当前用户）.
 * @param {object} params - { keyword?, allocDim?, bizKind?, status?, dateFrom?, dateTo?, pageNo?, pageSize? }
 */
export function listMyAdjustApplies(params = {}) {
  return call('get', '/perf/alloc-adjust/my-applies',
    { params: { pageNo: 1, pageSize: 20, ...params } },
    { total: 0, records: [], pageNo: 1, pageSize: 20 }
  );
}

/**
 * 业绩调整 - 已审批（后端分页 + 4 字段过滤）.
 * @param {object} params - { keyword?, allocDim?, bizKind?, dateFrom?, dateTo?, pageNo?, pageSize? }
 */
export function listMyAdjustDones(params = {}) {
  return call('get', '/perf/alloc-adjust/my-done',
    { params: { pageNo: 1, pageSize: 20, ...params } },
    { total: 0, records: [], pageNo: 1, pageSize: 20 }
  );
}
/**
 * 查询分配调整申请审批流记录（时间倒序，最新在上）
 * 后端: GET /api/perf/alloc-adjust/{id}/approval-history → List<ApprovalLogDTO>
 * 字段: nodeKey / nodeName / operator / operatorName / operatorOrgName / action / opinion / operateTime
 */
export function getAdjustApprovalHistory(id) {
  return call('get', `/perf/alloc-adjust/${id}/approval-history`, {}, []);
}

export function listTargetAdjusts(params = {}) {
  return call('get', '/perf/target-adjust/list', { params }, []).then(unwrapPage);
}
export function getTargetAdjust(id) {
  return call('get', `/perf/target-adjust/${id}`, {}, {});
}
/**
 * 提交目标修正（V1.2 Q3.2c）.
 *
 * 后端 TargetAdjustCreateReqDTO 字段：
 *   { planId, subjectType, subjectId, cycleKey, ownerOrgId, reason,
 *     adjustments: [{ metricCode, oldValue, newValue }] }
 *
 * 前端入参可以是单条便捷形式 { planId, subjectType, subjectId, metricCode,
 * oldValue, newValue, cycleKey?, ownerOrgId?, reason } —— 这里自动包装成 adjustments[].
 */
export function submitTargetAdjust(data) {
  // 已经是嵌套格式（含 adjustments 数组）→ 直接透传
  if (Array.isArray(data?.adjustments)) {
    return call('post', '/perf/target-adjust/create', { data }, { id: 'ADJ-MOCK-' + Date.now() });
  }
  // 便捷形式 → 包装成 1 条 adjustment
  const payload = {
    planId:      data.planId,
    subjectType: data.subjectType,
    subjectId:   data.subjectId,
    cycleKey:    data.cycleKey || '',
    ownerOrgId:  data.ownerOrgId || '',
    reason:      data.reason,
    adjustments: [{
      metricCode: data.metricCode,
      oldValue:   Number(data.oldValue ?? 0),
      newValue:   Number(data.newValue ?? 0),
      // 基础值修正（可空：未填则后端保留原 base_value 不抹除）
      oldBaseValue: data.oldBaseValue != null ? Number(data.oldBaseValue) : null,
      newBaseValue: data.newBaseValue != null ? Number(data.newBaseValue) : null
    }]
  };
  return call('post', '/perf/target-adjust/create', { data: payload }, { id: 'ADJ-MOCK-' + Date.now() });
}
export function withdrawTargetAdjust(id, reason) {
  return call('post', `/perf/target-adjust/${id}/withdraw`,
    { data: { reason }, headers: { 'X-Audit-Reason': encodeURIComponent(reason || '') } },
    { ok: true });
}
/**
 * 查询目标修正申请审批流记录（时间倒序，最新在上）
 * 后端: GET /api/perf/target-adjust/{id}/approval-history → List<ApprovalLogDTO>
 */
export function getTargetAdjustApprovalHistory(id) {
  return call('get', `/perf/target-adjust/${id}/approval-history`, {}, []);
}

// ============================================================
// 考核计算 Compute (RunTask + SysControl + Recalc)
// ============================================================
// ⚠️ 已弃用：后端 PerfRunTaskController 没有 /stats 端点（仅 GET / 和 GET /{id}）
// 请改用前端从 listComputeBatches 派生统计（参见 Compute.vue 的 computed `s`）
// export function getComputeStats() { ... }  // 删除以避免误用
export function listComputeBatches(params = {}) {
  return call('get', '/perf/run-tasks', { params }, perfComputeBatches).then(unwrapPage);
}
export function getComputeBatch(id) {
  return call('get', `/perf/run-tasks/${id}`, {}, {});
}
// 考核计算统计：最后一次 KPI 计算任务(PERF_METRIC_CALC_TASK) 成功/失败/耗时 + 本月任务数
export function getKpiScoreStats() {
  return call('get', '/perf/kpi-score/stats', {},
    { monthTaskCount: 0, lastSuccessCount: null, lastFailCount: null, lastDurationMs: null, lastStatus: null });
}
// 考核计算数据列表：KPI 方案级计算记录(PERF_KPI_CALC_LOG)，按数据日期 + 方案过滤，返回 {records, total}
export function listKpiCalcLogs(params = {}) {
  return call('get', '/perf/kpi-score/logs', { params }, { records: [], total: 0 }).then(unwrapPage);
}
// 考核计算记录最大数据日期（进入页面默认选中并展示最新一日）
export function getLatestKpiCalcLogDate() {
  return call('get', '/perf/kpi-score/logs/latest-date', {}, null);
}
// KPI 计算结果明细(PERF_KPI_SCORE)：维度/指标/对象/得分，按数据日期 + 方案 + 指标过滤分页
// KPI 计算结果详情（按对象分组）：返回 { metrics:[列定义], records:[对象行], total, pageNo, pageSize }
export function listKpiScoreResults(params = {}) {
  return call('get', '/perf/kpi-score/results', { params }, { metrics: [], records: [], total: 0 });
}
// 导出KPI得分（页面透视格式 Excel，blob）
export function exportKpiScores(params = {}) {
  return call('get', '/perf/kpi-score/export-scores', { params, responseType: 'blob' }, null);
}
// 导出KPI明细数据（PERF_KPI_SCORE 平铺 Excel，blob）
export function exportKpiScoreDetails(params = {}) {
  return call('get', '/perf/kpi-score/export-details', { params, responseType: 'blob' }, null);
}
// KPI 方案的指标下拉项（仅含该方案配置的指标，含名称）
export function listKpiSchemeMetrics(schemeCode) {
  return call('get', '/perf/kpi-score/scheme-metrics', { params: { schemeCode } }, []);
}
// 触发 KPI 分值计算（数据日期 + 方案 + 触发原因，记审批日志）
export function calcKpiScore(payload) {
  return call('post', '/perf/kpi-score/calc', { data: payload }, 'OK');
}
export function triggerCompute(payload) {
  return call('post', '/perf/recalc', { data: payload }, { batch: 'CALC-MOCK-' + Date.now() });
}
export function getCurrentVersion() {
  return call('get', '/perf/sys-control', {}, {});
}
export function listVersionHistory() {
  return call('get', '/perf/sys-control/history', {}, []);
}

// ============================================================
// 异步导出 Export（V1.2 Q6 4 类导出策略 + MinIO）
// 后端：POST /api/perf/export/{kpi|metric|alloc|detail} → ExportTaskRespDTO（taskId, status, downloadUrl）
//       GET  /api/perf/export/task/{taskId} → 轮询任务状态（成功后含 presigned URL）
// ============================================================
export function exportKpi(req) {
  return call('post', '/perf/export/kpi', { data: req }, { taskId: 'EXP-MOCK-' + Date.now(), status: 'PENDING' });
}
export function exportMetric(req) {
  return call('post', '/perf/export/metric', { data: req }, { taskId: 'EXP-MOCK-' + Date.now(), status: 'PENDING' });
}
export function exportAlloc(req) {
  return call('post', '/perf/export/alloc', { data: req }, { taskId: 'EXP-MOCK-' + Date.now(), status: 'PENDING' });
}
export function exportDetail(req) {
  return call('post', '/perf/export/detail', { data: req }, { taskId: 'EXP-MOCK-' + Date.now(), status: 'PENDING' });
}
export function getExportTask(taskId) {
  return call('get', `/perf/export/task/${taskId}`, {}, { taskId, status: 'SUCCESS', downloadUrl: '#' });
}
/**
 * 通用导出轮询（每 1.5s 拉一次，最长 60s）→ 拿到 SUCCESS 时自动 window.open(downloadUrl)
 */
export async function pollExportTask(taskId, { interval = 1500, timeout = 60000 } = {}) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    const t = await getExportTask(taskId);
    if (t.status === 'SUCCESS') {
      if (t.downloadUrl && t.downloadUrl !== '#') window.open(t.downloadUrl, '_blank');
      return t;
    }
    if (t.status === 'FAILED') throw new Error(t.errorMsg || '导出失败');
    await new Promise(r => setTimeout(r, interval));
  }
  throw new Error('导出超时');
}
