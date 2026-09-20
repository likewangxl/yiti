import http, { API_BASE } from './http';

const PERSONAL_PAGE = Object.freeze({
  todos: { pageNo: 1, pageSize: 20 },
  touch: { pageNo: 1, pageSize: 10 },
  customers: { pageSize: 6 },
  progress: { pageSize: 6 }
});

const STATUS_LABELS = Object.freeze({
  DRAFT: '草稿',
  IN_APPROVAL: '审批中',
  IN_PROGRESS: '办理中',
  COMPLETED: '已完成',
  REJECTED: '已驳回',
  CANCELLED: '已撤回',
  CANCELED: '已撤回'
});

const STATUS_RANK = Object.freeze({ overdue: 0, warn: 1, normal: 2, unknown: 3 });

const BIZ_TYPE_LABELS = Object.freeze({
  ALLOC_ADJUST: '业绩调整',
  TARGET_ADJUST: '目标修正',
  ASSET_PROJECT: '资产立项',
  LEAD: '线索管理',
  SUPPORT: '中台支持'
});

const TOUCH_TYPE_LABELS = Object.freeze({
  FIRST_TOUCH: '首次触达',
  FOLLOW_UP: '持续跟进'
});

const TOUCH_STATUS_LABELS = Object.freeze({
  PENDING: '待处理',
  IN_PROGRESS: '进行中',
  SUCCESS: '已完成',
  CANCELLED: '已取消',
  CANCELED: '已取消'
});

function get(path, params) {
  const config = { silent: true };
  if (params) config.params = params;
  return http.get(`${API_BASE}${path}`, config);
}

export function getPersonalWorkspace() {
  return get('/portal/workspace');
}

export function getPersonalTodoTasks() {
  return get('/workflow/tasks', PERSONAL_PAGE.todos);
}

export function getPersonalTouchTasks(status) {
  return get('/touch-tasks', { status, ...PERSONAL_PAGE.touch });
}

export function getPersonalCustomers() {
  return get('/marketing/customers/mine', PERSONAL_PAGE.customers);
}

export function getPersonalAssetProjects() {
  return get('/marketing/asset-projects', { tab: 'MY', ...PERSONAL_PAGE.progress });
}

export function getPersonalSupportRequests() {
  return get('/support-requests', { onlyMine: true, ...PERSONAL_PAGE.progress });
}

/**
 * 读取个人驾驶舱各数据源。每个 promise 保留自己的 fulfilled/rejected 状态，
 * 由页面模型层决定分区空态和错误态，避免一个来源失败清空其他来源。
 */
export async function loadPersonalDashboard() {
  const [workspace, todos, touchPending, touchInProgress, customers, assets, supports] =
    await Promise.allSettled([
      getPersonalWorkspace(),
      getPersonalTodoTasks(),
      getPersonalTouchTasks('PENDING'),
      getPersonalTouchTasks('IN_PROGRESS'),
      getPersonalCustomers(),
      getPersonalAssetProjects(),
      getPersonalSupportRequests()
    ]);
  return { workspace, todos, touchPending, touchInProgress, customers, assets, supports };
}

function hasOwn(value, key) {
  return !!value && typeof value === 'object' && Object.prototype.hasOwnProperty.call(value, key);
}

function asText(value) {
  return value === null || value === undefined || value === '' ? '' : String(value);
}

function asTotal(value) {
  if (typeof value === 'boolean' || value === null || value === undefined) return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isInteger(number) && number >= 0 ? number : null;
}

function sourceError(source) {
  if (!source || source.status === 'fulfilled') return null;
  const reason = source.reason || {};
  const status = reason?.response?.status ?? reason?.status ?? reason?.code;
  const forbidden = Number(status) === 403 || String(status).toUpperCase() === 'FORBIDDEN';
  return {
    status: forbidden ? 'forbidden' : 'error',
    message: asText(reason.message) || (forbidden ? '当前账号暂无权限查看此区域' : '数据源暂不可用')
  };
}

function recordsOf(value) {
  if (Array.isArray(value)) return { records: value, total: null, valid: true };
  if (!value || typeof value !== 'object') return { records: [], total: null, valid: false };
  const candidates = ['records', 'list', 'content', 'rows', 'data'];
  const key = candidates.find(name => hasOwn(value, name));
  const records = key ? value[key] : undefined;
  const normalized = Array.isArray(records) ? records : [];
  const rawTotal = hasOwn(value, 'total') ? value.total : value.totalCount;
  const total = asTotal(rawTotal);
  return { records: normalized, total, valid: Array.isArray(records) };
}

function sourcePage(source) {
  const error = sourceError(source);
  if (error) return { records: [], total: null, error };
  const page = recordsOf(source?.value);
  if (!page.valid) {
    return { records: [], total: null, error: { status: 'error', message: '数据源未返回有效列表' } };
  }
  return { ...page, error: null };
}

function sectionState({ items, errors = [], hasSource = true }) {
  const validErrors = errors.filter(Boolean);
  if (items.length) return { status: 'ready', errors: validErrors };
  if (!hasSource) return { status: 'empty', errors: validErrors };
  if (validErrors.length) {
    return {
      status: validErrors.every(error => error.status === 'forbidden') ? 'forbidden' : 'error',
      errors: validErrors
    };
  }
  return { status: 'empty', errors: [] };
}

function joinMessages(messages) {
  return messages.filter(Boolean).join('；');
}

function sourceCountMessage(label, page, error) {
  if (error) return `${label}不可用`;
  if (page.total === null) return `${label}总数未知`;
  return `${label} ${page.total} 条`;
}

function formatStatus(value) {
  const key = asText(value).toUpperCase();
  return STATUS_LABELS[key] || asText(value) || '状态待更新';
}

function urgencyOf(item = {}) {
  const explicit = asText(item.urgency).toLowerCase();
  if (['overdue', 'warn', 'normal', 'unknown'].includes(explicit)) return explicit;
  const sla = asText(item.slaStatus || item.status).toUpperCase();
  if (sla === 'RED') return 'overdue';
  if (sla === 'YELLOW') return 'warn';
  if (sla === 'GREEN' || sla === 'BLUE') return 'normal';
  return 'unknown';
}

function dateValue(value) {
  if (!value) return null;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function compareEarlierFirst(left, right) {
  const a = dateValue(left);
  const b = dateValue(right);
  if (a === null && b === null) return 0;
  if (a === null) return 1;
  if (b === null) return -1;
  return a - b;
}

function compareLaterFirst(left, right) {
  const a = dateValue(left);
  const b = dateValue(right);
  if (a === null && b === null) return 0;
  if (a === null) return 1;
  if (b === null) return -1;
  return b - a;
}

function targetId(item, fallback) {
  return item?.id ?? item?.taskId ?? item?.assetProjectId ?? item?.requestId ?? fallback;
}

function workflowReason(item, urgency) {
  if (item?.overdueInfo || item?.reason) return item.overdueInfo || item.reason;
  return {
    overdue: '节点已超时，请优先处理',
    warn: '接近办理时限',
    normal: '等待办理',
    unknown: '时效信息未提供'
  }[urgency];
}

function mapWorkflowItem(item, index) {
  const id = targetId(item, `workflow-${index}`);
  const urgency = urgencyOf(item);
  return {
    id,
    title: item?.title || item?.taskTitle || item?.processName || item?.taskName || '未命名待办',
    customerName: item?.customerName || item?.custName || '',
    typeLabel: item?.bizTypeName || BIZ_TYPE_LABELS[asText(item?.bizType).toUpperCase()] || item?.bizType || '',
    nodeLabel: item?.taskName || item?.nodeName || '',
    urgency,
    deadline: item?.timeoutTime || item?.warningTime || item?.deadline || null,
    reason: workflowReason(item, urgency),
    actionLabel: '查看办理',
    target: {
      kind: 'todo', id, source: 'workflow',
      taskId: item?.taskId || item?.id || id,
      processInstanceId: item?.processInstanceId,
      businessKey: item?.businessKey,
      bizType: item?.bizType,
      bizId: item?.bizId,
      taskName: item?.taskName || item?.nodeName,
      nodeKey: item?.nodeKey
    }
  };
}

function touchMode(item = {}) {
  if (item.canOperateTask === true) return 'handle';
  if (item.canWriteLog === true) return 'supplement';
  return 'view';
}

function mapTouchItem(item, index) {
  const id = targetId(item, `touch-${index}`);
  const mode = touchMode(item);
  return {
    id,
    title: item?.taskNo || item?.title || item?.customerName || item?.custName || '触达任务',
    customerName: item?.customerName || item?.custName || '',
    typeLabel: TOUCH_TYPE_LABELS[asText(item?.taskType).toUpperCase()] || item?.taskType || '触达任务',
    nodeLabel: TOUCH_STATUS_LABELS[asText(item?.taskStatus).toUpperCase()] || item?.taskStatus || '',
    urgency: urgencyOf(item),
    deadline: item?.planFinishTime || item?.slaDeadline || item?.expectedFinishAt || null,
    reason: mode === 'handle' ? '本人可办理' : mode === 'supplement' ? '本人或协同可补录触达日志' : '当前账号可查看',
    actionLabel: { handle: '办理', supplement: '补录日志', view: '查看' }[mode],
    target: {
      kind: 'touch', id, mode,
      canOperateTask: item?.canOperateTask === true,
      canWriteLog: item?.canWriteLog === true
    }
  };
}

function mapCustomer(item, index) {
  const id = targetId(item, `customer-${index}`);
  return {
    id,
    name: item?.custName || item?.customerName || '未命名客户',
    isKey: hasOwn(item, 'isKeystone')
      ? item.isKeystone === 1 || item.isKeystone === true
        ? true
        : item.isKeystone === 0 || item.isKeystone === false
          ? false
          : null
      : null,
    opened: item?.isAccountOpened === 1 ? true : item?.isAccountOpened === 0 ? false : undefined,
    lastTouchTime: item?.lastTouchTime || null,
    touchRestricted: item?.touchRestricted === 1 || item?.touchRestricted === true,
    target: { kind: 'customer', id }
  };
}

function mapAsset(item, index) {
  const id = targetId(item, `asset-${index}`);
  const hasSubmittedTime = item?.submittedTime !== null && item?.submittedTime !== undefined && item?.submittedTime !== '';
  return {
    id,
    title: item?.projectName || item?.applyNo || `资产立项 ${id}`,
    customerName: item?.customerName || '',
    typeLabel: '资产立项',
    statusLabel: formatStatus(item?.status),
    nodeLabel: item?.currentNode || '',
    submittedTime: item?.submittedTime || item?.createdTime || null,
    timeLabel: hasSubmittedTime ? '提交时间' : '创建时间',
    sortTime: item?.submittedTime || item?.createdTime || null,
    target: { kind: 'asset', id }
  };
}

function mapSupport(item, index) {
  const id = targetId(item, `support-${index}`);
  return {
    id,
    title: item?.requestNo || item?.productName || `中台支持 ${id}`,
    customerName: item?.custName || '',
    typeLabel: item?.productName || '中台支持',
    statusLabel: formatStatus(item?.status),
    nodeLabel: item?.currentNodeName || '',
    submittedTime: item?.createdTime || null,
    timeLabel: '创建时间',
    sortTime: item?.createdTime || null,
    target: { kind: 'support', id }
  };
}

function buildMetrics(workspaceSource) {
  const error = sourceError(workspaceSource);
  if (error) return { status: error.status, message: error.message, items: [], total: null };
  const data = workspaceSource?.value && typeof workspaceSource.value === 'object' ? workspaceSource.value : {};
  const hasMetricCards = hasOwn(data, 'metricCards') && Array.isArray(data.metricCards);
  const raw = hasMetricCards ? data.metricCards : [];
  const aggregateErrors = data.aggregateErrors && typeof data.aggregateErrors === 'object'
    ? data.aggregateErrors : {};
  const metricError = hasOwn(aggregateErrors, 'metricCards') ? asText(aggregateErrors.metricCards) : '';
  const items = raw.slice(0, 6).map(card => ({
    metricCode: card?.metricCode,
    metricName: card?.metricName,
    currentValue: hasOwn(card, 'currentValue') ? card.currentValue : null,
    targetValue: hasOwn(card, 'targetValue') ? card.targetValue : null,
    achievementRate: hasOwn(card, 'completionRate') ? card.completionRate : null,
    unit: card?.unit,
    dataDate: hasOwn(card, 'dataTime') ? card.dataTime : null,
    mom: hasOwn(card, 'changeRate') ? card.changeRate : null
  }));
  const messages = [];
  if (metricError) messages.push(metricError);
  if (raw.length > 6) messages.push('展示前6项个人指标');
  return {
    status: items.length ? 'ready' : metricError || !hasMetricCards ? 'error' : 'empty',
    message: joinMessages(messages),
    items,
    total: null
  };
}

function buildPriorities(sources) {
  const workflow = sourcePage(sources.todos);
  const pending = sourcePage(sources.touchPending);
  const inProgress = sourcePage(sources.touchInProgress);
  const workflowItems = workflow.records.map(mapWorkflowItem);
  const touchItems = [...pending.records, ...inProgress.records].map(mapTouchItem);
  const items = [...workflowItems, ...touchItems].sort((a, b) => {
    const urgency = STATUS_RANK[a.urgency] - STATUS_RANK[b.urgency];
    if (urgency) return urgency;
    return compareEarlierFirst(a.deadline, b.deadline);
  }).slice(0, 6);
  const errors = [workflow.error, pending.error, inProgress.error];
  const state = sectionState({ items, errors });
  const messages = [
    sourceCountMessage('工作流待办', workflow, workflow.error),
    sourceCountMessage('触达待处理', pending, pending.error),
    sourceCountMessage('触达进行中', inProgress, inProgress.error),
    '近期事项按紧急程度排列，非全量排序',
    ...errors.filter(Boolean).map(error => error.message)
  ];
  return {
    status: state.status,
    message: joinMessages(messages),
    total: null,
    sourceTotals: {
      workflow: workflow.total,
      touchPending: pending.total,
      touchInProgress: inProgress.total
    },
    items
  };
}

function buildCustomers(source) {
  const page = sourcePage(source);
  const items = page.records.map(mapCustomer);
  const state = sectionState({ items, errors: [page.error] });
  return {
    status: state.status,
    message: page.error?.message || '',
    total: page.total,
    items
  };
}

function buildProgress(sources) {
  const assets = sourcePage(sources.assets);
  const supports = sourcePage(sources.supports);
  const items = [
    ...assets.records.map(mapAsset),
    ...supports.records.map(mapSupport)
  ].sort((a, b) => compareLaterFirst(a.sortTime, b.sortTime)).slice(0, 6);
  const errors = [assets.error, supports.error];
  const state = sectionState({ items, errors });
  const messages = [
    sourceCountMessage('资产立项', assets, assets.error),
    sourceCountMessage('中台支持', supports, supports.error),
    '各来源最近记录，含草稿',
    ...errors.filter(Boolean).map(error => error.message)
  ];
  return {
    status: state.status,
    message: joinMessages(messages),
    total: null,
    sourceTotals: { asset: assets.total, support: supports.total },
    items
  };
}

/**
 * 将各来源响应投影为 PersonalDashboard.vue 的展示模型。
 * 该函数不读取或接受 empId；身份只用于页面上下文显示，范围由服务端 Session 决定。
 */
export function buildPersonalDashboardModel(sources = {}, identity = {}) {
  return {
    identity: {
      name: identity?.name || '',
      orgName: identity?.orgName || ''
    },
    metrics: buildMetrics(sources.workspace),
    priorities: buildPriorities(sources),
    customers: buildCustomers(sources.customers),
    progress: buildProgress(sources),
    refreshedAt: null
  };
}

export { STATUS_LABELS };
