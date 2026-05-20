import { call } from './http';
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
  return call('get', '/perf/kpi-schemes', { params: { pageSize: 100, ...params } }, perfKpiRules).then(unwrapPage);
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
export function batchUpsertTargetValues(items) {
  return call('post', '/perf/target-values/batch', { data: { items } }, { ok: true });
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
  const resp = await call('post', '/perf/import/upload', {
    data: fd,
    params,
    headers: { 'Content-Type': 'multipart/form-data' }
  }, () => 'IMP-MOCK-' + Date.now());
  const batchId = (resp && typeof resp === 'object') ? (resp.batchId || resp.id) : resp;
  // 写入 localStorage 历史
  pushLocalImport({
    batchId,
    id: batchId,
    type: importType,
    file: file?.name || '-',
    fileSize: file?.size || 0,
    uploader: meta.uploader || '当前用户',
    valid: 0, total: 0,
    status: 'PROCESSING',
    time: new Date().toISOString().slice(0, 19).replace('T', ' ')
  });
  return batchId;
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
      newValue:   Number(data.newValue ?? 0)
    }]
  };
  return call('post', '/perf/target-adjust/create', { data: payload }, { id: 'ADJ-MOCK-' + Date.now() });
}
export function withdrawTargetAdjust(id, reason) {
  return call('post', `/perf/target-adjust/${id}/withdraw`,
    { data: { reason }, headers: { 'X-Audit-Reason': encodeURIComponent(reason || '') } },
    { ok: true });
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
