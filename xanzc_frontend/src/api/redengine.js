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
// 旧材料审核队列现在也按工作台页签分页；保留历史的两个位置参数调用，避免旧页面升级时
// 丢失分页语义。新页面使用 params 对象并显式传递 tab。
export const getReviewQueue = (paramsOrPageNo = 1, pageSize = 10, tab) => {
  const params = paramsOrPageNo && typeof paramsOrPageNo === 'object'
    ? { ...paramsOrPageNo }
    : {
        pageNo: paramsOrPageNo,
        pageSize,
        ...(tab ? { tab } : {})
      };
  return call('get', '/re/reviews/queue', { params });
};
export const getReviewPreview = (id) => call('get', `/re/reviews/${id}/preview`);
export const approveSubmit = (id, data) => call('post', `/re/reviews/${id}/approve`, { data });
export const rejectSubmit = (id, data) => call('post', `/re/reviews/${id}/reject`, { data });

// ── 驾驶舱 ──
// 红色引擎首页聚合：仅返回后端真实数据，页面不以本地静态值冒充生产结果。
export const getHomeSummary = () => call('get', '/re/home/summary');
export const getHomeRanking = () => call('get', '/re/home/ranking');
// 新版预警池按季度一次返回红黄牌；逾期任务使用 assignment 维度分页和扣分。
export const getWarningPool = () => call('get', '/re/home/warning-pool');
export const getTaskOverdueList = (params = {}) => call('get', '/re/home/overdue', { params });
export const executeTaskOverdue = (data) => call('post', '/re/home/overdue/execute', { data });
export const getCockpitOverview = () => call('get', '/re/cockpit/overview');
export const getRanking = () => call('get', '/re/cockpit/ranking');
export const getOverdueList = (params) => params === undefined
  ? call('get', '/re/cockpit/overdue')
  : call('get', '/re/cockpit/overdue', { params });
// 纠偏（后端实际签名 ReCockpitController.executeOverdue 是
// @PostMapping("/overdue/execute") @Valid @RequestBody ReOverdueExecuteReqDTO，
// 字段 submitId/deductionPoints/reason，reason 为 @NotBlank 必填，非简报原先的 query params）
export const executeOverdue = (data) => call('post', '/re/cockpit/overdue/execute', { data });
export const getRedWarning = () => call('get', '/re/cockpit/warning/red');
export const getYellowWarning = () => call('get', '/re/cockpit/warning/yellow');

// ── 文件上传（复用平台 governance 端点，非红色引擎自建）──
export const uploadFile = (formData) =>
  call('post', '/files/upload', { data: formData, headers: { 'Content-Type': 'multipart/form-data' } });

// ── 任务协同 ──
// 任务模块以任务主表、党支部任务实例和填报记录分层返回；页面只通过这些 wrapper
// 访问 /api/re，避免在视图内拼接 URL 或另建 axios 实例。
// 任务管理的查询 DTO 使用 ReTaskNature.SCHEDULED；页面/旧原型沿用 PERIODIC
// 作为“定时任务”别名。只在任务管理资源边界做转换，避免把工作流查询中
// 已由后端 @InitBinder 支持的 PERIODIC 值误改掉。
function normalizeTaskManagementQuery(params = {}) {
  const taskNature = typeof params.taskNature === 'string'
    ? params.taskNature.trim().toUpperCase()
    : params.taskNature;
  if (taskNature !== 'PERIODIC') return params;
  return { ...params, taskNature: 'SCHEDULED' };
}

export const listTasks = (params = {}) => call('get', '/re/tasks', {
  params: normalizeTaskManagementQuery(params)
});
export const createTask = (data) => call('post', '/re/tasks', { data });
export const getTaskDetail = (taskId) => call('get', `/re/tasks/${taskId}`);
export const listTaskAssignments = (taskId, params = {}) =>
  call('get', `/re/tasks/${taskId}/assignments`, { params });
// 组织审核员新增任务时使用红色引擎受控员工候选接口，避免跨越全局用户管理资源的数据范围。
export const listEligibleUsers = (params = {}) =>
  call('get', '/re/tasks/eligible-users', { params });

// 报送员任务处理：列表按当前用户可见的党支部 assignment 查询，详情与提交使用同一 assignment。
// 查询字段由 ReTaskAssignmentPageQueryDTO/任务列表契约共同承载；提交请求使用
// ReTaskSubmissionReqDTO 的 assignmentId、content、fileObjectIds、clientRequestId。
export const listMyTaskAssignments = (params = {}) =>
  call('get', '/re/tasks/my-assignments', { params });
export const getMyTaskAssignment = (assignmentId) =>
  call('get', `/re/tasks/assignments/${assignmentId}`);
export const submitTask = (data) =>
  call('post', '/re/tasks/submissions', { data });

// 四维明细项由治理中心 RE_ITEM_CODE 字典维护；任务类型是 red-engine DTO 固定枚举，
// 文件类型由任务配置写入 RE_TASK_FILE_TYPE，当前没有独立的任务元数据 REST 端点。
export const listMaterialDetailItems = () => call('get', '/sys/dicts/RE_ITEM_CODE/items');

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

// 任务审核工作台：支部审核与组织审核使用独立的 assignment 资源。
// 列表参数按 ReTaskWorkflowPageQueryDTO 的 tab 工作台契约传递；服务端负责把
// 页签转换为状态集合并在分页前过滤，响应中的 status/submissionStatus 仍只用于展示。
export const listBranchTaskReviews = (params = {}) =>
  call('get', '/re/reviews/tasks/branch/queue', { params });
export const getBranchTaskReview = (assignmentId) =>
  call('get', `/re/reviews/tasks/branch/${assignmentId}`);
export const approveBranchTask = (assignmentId, data = {}) =>
  call('post', `/re/reviews/tasks/branch/${assignmentId}/approve`, { data });
export const submitBranchTaskToOrg = (assignmentId, data = {}) =>
  call('post', `/re/reviews/tasks/branch/${assignmentId}/submit-to-org`, { data });
export const rejectBranchTask = (assignmentId, data) =>
  call('post', `/re/reviews/tasks/branch/${assignmentId}/reject`, { data });

export const listOrgTaskReviews = (params = {}) =>
  call('get', '/re/reviews/tasks/org/queue', { params });
export const getOrgTaskReview = (assignmentId) =>
  call('get', `/re/reviews/tasks/org/${assignmentId}`);
export const approveOrgTask = (assignmentId, data = {}) =>
  call('post', `/re/reviews/tasks/org/${assignmentId}/approve`, { data });
export const rejectOrgTask = (assignmentId, data) =>
  call('post', `/re/reviews/tasks/org/${assignmentId}/reject`, { data });
