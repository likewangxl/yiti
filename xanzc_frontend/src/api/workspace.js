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

function adaptWorkspace(dto) {
  if (!dto) return mock;
  const todos = (dto.recentTodos || []).map(t => ({
    name: t.processName || t.taskTitle || '—',
    node: t.taskTitle || '',
    sla: SLA_MAP[t.lightStatus] || 'normal',
    remain: t.overdueInfo || ''
  }));
  const notifications = (dto.recentNotifications || []).map(n => ({
    title: n.title || '',
    tag: n.bizType || '',
    time: fmtTime(n.sentTime),
    read: n.readStatus === 'READ'
  }));
  const shortcuts = (dto.shortcuts || []).map(s => ({
    icon: s.shortcutIcon || '🔗',
    label: s.shortcutName || ''
  }));
  return {
    // 这些字段后端没有，先沿用 mock 让 UI 有内容
    greet: mock.greet,
    desc: mock.desc,
    stats: mock.stats,
    // 后端有数据用后端，没数据用 mock 兜底（数据库里多半还没造数）
    todos: todos.length ? todos : mock.todos,
    notifications: notifications.length ? notifications : mock.notifications,
    shortcuts: shortcuts.length ? shortcuts : mock.shortcuts
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
  return call('get', '/notifications/unread-count', {}, 3);
}

/**
 * 标记单条已读
 */
export function markRead(id) {
  return call('put', `/notifications/${id}/read`, {}, { ok: true });
}

/**
 * 全部标记已读
 */
export function markAllRead() {
  return call('put', '/notifications/read-all', {}, { ok: true });
}
