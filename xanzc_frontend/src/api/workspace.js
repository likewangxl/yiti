import { call } from './http';
import { workspace as mock } from '@/mock';

/**
 * 后端 WorkspaceDTO 字段名跟前端视图差很远，做一层 adapter：
 *   recentTodos[].lightStatus(GREEN/YELLOW/RED)  → view.todos[].sla(normal/warn/overdue)
 *   recentTodos[].processName/taskTitle/overdueInfo → view.todos[].name/node/remain
 *   recentNotifications[].title/bizType/sentTime/readStatus → view.notifications[].{title,tag,time,read}
 *   shortcuts[].shortcutIcon/shortcutName → view.shortcuts[].{icon,label}
 * stats / hero 后端没对应（metricCards 字段不一致），保留 mock 兜底。
 */
const SLA_MAP = { GREEN: 'normal', YELLOW: 'warn', RED: 'overdue' };

function fmtTime(t) {
  if (!t) return '';
  // yiti 返回 ISO LocalDateTime "2026-05-06T10:00:00"，截到分钟
  return String(t).slice(0, 16).replace('T', ' ');
}

const BIZ_TYPE_LABEL = {
  ALLOC_ADJUST: '业绩调整',
  TARGET_ADJUST: '目标修正',
  LOAN: '贷款业务',
  LEAD: '线索管理',
  SUPPORT: '支撑业务',
};

function adaptWorkspace(dto) {
  if (!dto) return mock;
  const todos = (dto.recentTodos || []).map(t => ({
    name: t.processName || t.taskTitle || '—',
    node: t.taskTitle || '',
    sla: SLA_MAP[t.lightStatus] || 'normal',
    remain: t.overdueInfo || ''
  }));
  const notifications = (dto.recentNotifications || []).map(n => ({
    title: n.summary || n.content || n.title || '',
    tag: BIZ_TYPE_LABEL[n.bizType] || n.bizType || '',
    time: fmtTime(n.sentTime),
    rawTime: n.sentTime || '',
    read: n.readStatus === 'READ'
  }))
    // 未读优先 + 时间倒序
    .sort((a, b) => {
      if (a.read !== b.read) return a.read ? 1 : -1;
      return (b.rawTime || '').localeCompare(a.rawTime || '');
    })
    .slice(0, 10);
  const shortcuts = (dto.shortcuts || []).map(s => ({
    icon: s.shortcutIcon || '🔗',
    label: s.shortcutName || ''
  }));
  return {
    greet: '',
    desc: '',
    stats: [
      { label: '待办任务',   value: 0, trend: '', trendType: '' },
      { label: '未读通知',   value: 0, trend: '', trendType: '' },
      { label: '本月 KPI 总分',  value: '-', trend: '', trendType: '' },
      { label: '进行中触达任务', value: '-', trend: '', trendType: '' }
    ],
    todos: todos,
    notifications: notifications,
    shortcuts: shortcuts
  };
}

/**
 * 工作台首页聚合数据
 * 后端：GET /api/portal/workspace
 */
export async function getWorkspace() {
  const dto = await call('get', '/portal/workspace', {}, null);
  return adaptWorkspace(dto);
}

/**
 * 通知未读数  GET /api/notifications/unread-count
 * 后端直接返回 number
 */
export function getUnreadCount() {
  // 后端不通时角标显示 0，避免造成"有 3 条未读"的假象
  return call('get', '/notifications/unread-count', {}, 0);
}

/**
 * 标记单条已读
 */
/**
 * 通知列表（分页）
 */
export function listNotifications(params = {}) {
  return call('get', '/notifications', { params: { pageNo: 1, pageSize: 50, ...params } },
    { total: 0, records: [] });
}

export function markRead(id) {
  return call('put', `/notifications/${id}/read`, {}, { ok: true });
}

/**
 * 全部标记已读
 */
export function markAllRead() {
  return call('put', '/notifications/read-all', {}, { ok: true });
}

/**
 * 工作台「待办」stat 卡用：当前用户全部待办 count（业绩调整 + 目标修正 + 其他 bizType）。
 * 不带 bizType 时 /workflow/tasks 的 query.count() 就是全部待办的准确 total。
 */
export function getMyTodoCount() {
  return call('get', '/workflow/tasks',
    { params: { pageSize: 1, pageNo: 1 } },
    { total: 0, records: [] }
  ).then(r => Number(r?.total) || 0);
}

/**
 * 工作台「未读通知」stat 卡用：未读通知数.
 */
export function getUnreadNotificationCount() {
  return call('get', '/notifications/unread-count', {}, 0).then(r => Number(r) || 0);
}
