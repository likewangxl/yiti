// 红色引擎（党建）前端 API 层。
// 全部走平台 http.js 的 call(method, url, config) —— 复用 yiti session/拦截器/ResponseWrapper 解包，
// 不引入独立 axios 实例、不碰 JWT/localStorage（F2）。URL 与红色引擎种子 SQL
// docs/superpowers/sql/2026-07-18-redengine-seed.sql 的 PT_RESOURCE.RESOURCE_URL 逐条核对一致。
import { call } from '@/api/http';

// ── 党组织 ──
export const getOrgTree = () => call('get', '/re/orgs/tree');
export const getOrg = (id) => call('get', `/re/orgs/${id}`);
export const addOrg = (data) => call('post', '/re/orgs', { data });
export const updateOrg = (id, data) => call('put', `/re/orgs/${id}`, { data });
// 纠偏（2026-07-19 修复审计缺口）：后端 ReOrgController.deleteOrg 现为
// @Valid @RequestBody ReOrgDeleteReqDTO（reason 为 @NotBlank，配合 @AuditLog(reasonRequired=true)
// 强制审计留痕），DELETE 请求体经 axios `data` 字段携带（call() 内部 http.request 透传）。
export const deleteOrg = (id, reason) => call('delete', `/re/orgs/${id}`, { data: { reason } });

// ── 用户党组织映射 ──
export const listUserMaps = (params = {}) => call('get', '/re/user-party-maps', { params });
export const bindUserMap = (data) => call('post', '/re/user-party-maps', { data });

// ── 上报 ──
export const createSubmit = (data) => call('post', '/re/submits', { data });
export const getMySubmits = (pageNo = 1, pageSize = 10) =>
  call('get', '/re/submits/my', { params: { pageNo, pageSize } });
export const getSubmit = (id) => call('get', `/re/submits/${id}`);

// ── 审核 ──
export const getReviewQueue = (pageNo = 1, pageSize = 10) =>
  call('get', '/re/reviews/queue', { params: { pageNo, pageSize } });
export const getReviewPreview = (id) => call('get', `/re/reviews/${id}/preview`);
export const approveSubmit = (id, data) => call('post', `/re/reviews/${id}/approve`, { data });
export const rejectSubmit = (id, data) => call('post', `/re/reviews/${id}/reject`, { data });

// ── 驾驶舱 ──
export const getCockpitOverview = () => call('get', '/re/cockpit/overview');
export const getRanking = () => call('get', '/re/cockpit/ranking');
export const getOverdueList = () => call('get', '/re/cockpit/overdue');
// 纠偏（后端实际签名 ReCockpitController.executeOverdue 是
// @PostMapping("/overdue/execute") @Valid @RequestBody ReOverdueExecuteReqDTO，
// 字段 submitId/deductionPoints/reason，reason 为 @NotBlank 必填，非简报原先的 query params）
export const executeOverdue = (data) => call('post', '/re/cockpit/overdue/execute', { data });
export const getRedWarning = () => call('get', '/re/cockpit/warning/red');
export const getYellowWarning = () => call('get', '/re/cockpit/warning/yellow');
export const archiveSettlement = (period) =>
  call('get', '/re/cockpit/archive/settlement', { params: { period } });
// 纠偏（2026-07-19 修复审计缺口）：后端 generateAnnualResult 现为
// @Valid @RequestBody ReAnnualGenerateReqDTO（reason 为 @NotBlank，@AuditLog 补齐 reasonRequired=true）
export const generateAnnual = (year, reason) => call('post', `/re/cockpit/archive/generate/${year}`, { data: { reason } });

// ── 导出（二进制直下）──
export const exportData = (type) => call('get', `/re/export/${type}`, { responseType: 'blob' });

// ── 文件上传（复用平台 governance 端点，非红色引擎自建）──
export const uploadFile = (formData) =>
  call('post', '/files/upload', { data: formData, headers: { 'Content-Type': 'multipart/form-data' } });

// ── 任务协同 ──
// 任务模块以任务主表、党支部任务实例和填报记录分层返回；页面只通过这些 wrapper
// 访问 /api/re，避免在视图内拼接 URL 或另建 axios 实例。
export const listTasks = (params = {}) => call('get', '/re/tasks', { params });
export const createTask = (data) => call('post', '/re/tasks', { data });
export const getTaskDetail = (taskId) => call('get', `/re/tasks/${taskId}`);
export const listTaskAssignments = (taskId, params = {}) =>
  call('get', `/re/tasks/${taskId}/assignments`, { params });

// 报送员任务处理：列表按当前用户可见的党支部 assignment 查询，详情与提交使用同一 assignment。
// 查询字段由 ReTaskAssignmentPageQueryDTO/任务列表契约共同承载；提交请求使用
// ReTaskSubmissionReqDTO 的 assignmentId、content、fileObjectIds、clientRequestId。
export const listMyTaskAssignments = (params = {}) =>
  call('get', '/re/tasks/my-assignments', { params });
export const getMyTaskAssignment = (assignmentId) =>
  call('get', `/re/tasks/assignments/${assignmentId}`);
export const submitTask = (data) =>
  call('post', '/re/tasks/submissions', { data });

// 任务类型、允许上传文件类型和四维明细项由后端字典维护，前端不复制生产字典值。
export const listTaskTypes = () => call('get', '/re/tasks/types', {});
export const listTaskFileTypes = () => call('get', '/re/tasks/file-types', {});
export const listMaterialDetailItems = () => call('get', '/re/tasks/material-details', {});

// 任务导出始终是一个异步 ZIP 作业；ZIP 内包含一个多 Sheet Excel，附件目录由服务端生成。
export const createTaskExport = (taskId, data = {}) =>
  call('post', `/re/tasks/${taskId}/exports`, { data });
export const getTaskExportStatus = (exportId) => call('get', `/re/task-exports/${exportId}`);
export const downloadTaskExport = (exportId) =>
  call('get', `/re/task-exports/${exportId}/download`, { responseType: 'blob' });
export const downloadTaskAttachment = (taskId, assignmentId, fileId) =>
  call('get', `/re/tasks/${taskId}/assignments/${assignmentId}/attachments/${fileId}/download`, {
    responseType: 'blob'
  });
