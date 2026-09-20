<template>
  <main class="workspace" aria-labelledby="workspace-title">
    <header class="workspace-head">
      <div class="workspace-heading">
        <PageTitle id="workspace-title" title="工作台" />
        <p class="workspace-desc">集中查看待处理事项、通知和流程转交进度。</p>
        <el-button
          type="primary"
          plain
          size="small"
          class="workspace-personal-cockpit-entry"
          aria-label="打开我的经营驾驶舱"
          @click="router.push('/personal-dashboard')"
        >
          我的经营驾驶舱
        </el-button>
      </div>
      <dl class="workspace-summary" aria-label="工作台事项摘要">
        <div class="summary-item">
          <dt>待办总数</dt>
          <dd>{{ todoCount }}</dd>
          <span>项</span>
        </div>
        <div class="summary-item">
          <dt>未读</dt>
          <dd>{{ ntfUnread }}</dd>
          <span>条</span>
        </div>
      </dl>
    </header>

    <section class="welcome-strip" aria-label="当前用户信息">
      <p class="greet">{{ greeting }}</p>
      <p class="desc">{{ desc }}</p>
    </section>

    <div class="cols">
      <section class="card-section announcements-card" aria-label="公告" :aria-busy="announcementsLoading">
        <div class="card-h">
          <h2 class="section-title">公告</h2>
          <el-button type="primary" link size="small" aria-label="查看全部公告" @click="router.push('/workspace/announcements')">全部</el-button>
        </div>
        <div v-if="announcementsLoading" class="list-state" role="status" aria-live="polite">公告加载中</div>
        <div v-else-if="!announcements.length" class="list-state" role="status">暂无公告</div>
        <div v-else class="ann-list">
          <button
            v-for="announcement in announcements"
            :key="announcement.id"
            class="ann-item"
            type="button"
            :aria-label="`查看公告：${announcement.title || '未命名公告'}`"
            @click="router.push('/announcement/' + announcement.id)"
          >
            <span class="ann-title">{{ announcement.title || '未命名公告' }}</span>
            <span class="ann-date">{{ fmtDate(announcement.publishDate) }}</span>
          </button>
        </div>
      </section>

      <section class="card-section notify" aria-label="通知" :aria-busy="notificationsLoading">
        <div class="card-h">
          <h2 class="section-title">通知</h2>
          <span v-if="ntfUnread" class="ntf-badge" :aria-label="`${ntfUnread} 条未读通知`">{{ ntfUnread }}</span>
          <el-button type="primary" link size="small" aria-label="查看全部通知" @click="router.push('/system/notifications')">全部</el-button>
        </div>
        <div v-if="notificationsLoading" class="list-state" role="status" aria-live="polite">通知加载中</div>
        <div v-else-if="!ntfRows.length" class="list-state" role="status">暂无通知</div>
        <div v-else class="ntf-list">
          <button
            v-for="notification in ntfRows"
            :key="notification.id || notification.title"
            class="ntf"
            :class="{ unread: !notification.isRead }"
            type="button"
            :aria-label="`查看通知：${notification.title || '未命名通知'}`"
            @click="onNtfClick(notification)"
          >
            <span class="dot" :class="{ active: !notification.isRead }" aria-hidden="true"></span>
            <span class="ntf-body">
              <span class="ntf-title-row">
                <span class="ntf-title">{{ notification.title || '—' }}</span>
                <span v-if="!notification.isRead" class="ntf-unread-text">未读</span>
              </span>
              <span v-if="notification.content" class="ntf-desc">{{ notification.content }}</span>
              <span class="meta">
                <el-tag size="small" effect="plain" :class="ntfTypeCls(notification.bizType)">{{ ntfTypeLabel(notification.bizType) }}</el-tag>
                <span class="time">{{ fmtDateTime(notification.createdTime) }}</span>
              </span>
            </span>
          </button>
        </div>
      </section>
    </div>

    <section class="card-section task-section" aria-label="我的任务" :aria-busy="tasksLoading">
      <div class="card-h">
        <h2 class="section-title">我的任务</h2>
        <el-radio-group v-model="taskTab" size="small" aria-label="任务类型" @change="loadTasks">
          <el-radio-button value="PENDING">待办</el-radio-button>
          <el-radio-button value="DONE">已办</el-radio-button>
        </el-radio-group>
        <el-button type="primary" link size="small" aria-label="刷新我的任务" :loading="tasksLoading" :disabled="tasksLoading" @click="loadTasks">刷新</el-button>
      </div>
      <div v-if="tasksLoading" class="table-state" role="status" aria-live="polite">{{ taskLoadingText }}</div>
      <el-table v-else-if="tasks.length" :data="tasks" stripe size="small">
        <el-table-column label="流程 / 标题" min-width="220">
          <template #default="{ row }">
            <div>{{ row.processName || row.taskName || '-' }}</div>
            <div class="task-sub">{{ row.title || row.bizSummary || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="当前节点" width="120">
          <template #default="{ row }">{{ row.nodeName || row.taskName || '-' }}</template>
        </el-table-column>
        <el-table-column label="发起人" width="100">
          <template #default="{ row }">{{ row.startUserName || row.startUserId || '-' }}</template>
        </el-table-column>
        <el-table-column label="发起时间" width="140">
          <template #default="{ row }">{{ fmtDateTime(row.startTime || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="SLA" width="90">
          <template #default="{ row }">
            <span class="sla-dot" :class="slaKey(row.slaStatus)" aria-hidden="true"></span>
            {{ slaText(row.slaStatus) }}
          </template>
        </el-table-column>
        <el-table-column label="剩余时长" width="100">
          <template #default="{ row }">
            <span :class="['remain', slaKey(row.slaStatus)]">{{ row.remain || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" class-name="operation-cell" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="taskTab === 'PENDING' ? goHandle(row) : goDetail(row)">{{ taskTab === 'PENDING' ? '办理' : '详情' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="table-state" role="status">{{ taskEmptyText }}</div>
    </section>

    <section class="card-section" aria-label="待认领转交" :aria-busy="transferInboxLoading">
      <div class="card-h">
        <h2 class="section-title">待认领转交</h2>
        <el-button type="primary" link size="small" aria-label="刷新待认领转交" @click="loadTransferInbox">刷新</el-button>
      </div>
      <div v-if="transferInboxLoading" class="table-state" role="status" aria-live="polite">待认领转交加载中</div>
      <el-table v-else-if="transferInboxRows.length" :data="transferInboxRows" stripe size="small">
        <el-table-column label="节点 / 业务键" min-width="200">
          <template #default="{ row }">
            <div>{{ row.nodeName || '-' }}</div>
            <div class="task-sub">{{ row.businessKey || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="原办理人" width="110">
          <template #default="{ row }">{{ row.fromEmpId || '-' }}</template>
        </el-table-column>
        <el-table-column label="发起人" width="110">
          <template #default="{ row }">{{ row.initiatorEmpId || '-' }}</template>
        </el-table-column>
        <el-table-column label="转交原因" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.transferReason || '-' }}</template>
        </el-table-column>
        <el-table-column label="发起时间" width="140">
          <template #default="{ row }">{{ fmtDateTime(row.initiatedTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" class-name="operation-cell" width="120" fixed="right">
          <template #default="{ row }">
            <BpAdaptiveRowActions>
              <template #primary><el-button type="primary" link size="small" :loading="row._acting" :disabled="row._acting" @click="acceptTransfer(row)">认领</el-button></template>
              <template #expanded><el-button v-if="row.status === 'PENDING_ACCEPT'" type="danger" link size="small" :disabled="row._acting" @click="declineTransfer(row)">拒绝</el-button></template>
              <template #compact><el-dropdown v-if="row.status === 'PENDING_ACCEPT'" trigger="click" popper-class="bp-crud-menu"><el-button link size="small" :disabled="row._acting" aria-label="更多转交操作">更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item divided class="danger-item" :disabled="row._acting" @click="declineTransfer(row)">拒绝</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template>
            </BpAdaptiveRowActions>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="table-state" role="status">暂无待认领转交</div>
    </section>

    <section class="card-section" aria-label="我发起的转交" :aria-busy="transferOutboxLoading">
      <div class="card-h">
        <h2 class="section-title">我发起的</h2>
        <el-button type="primary" link size="small" aria-label="刷新我发起的转交" @click="loadTransferOutbox">刷新</el-button>
      </div>
      <div v-if="transferOutboxLoading" class="table-state" role="status" aria-live="polite">转出记录加载中</div>
      <el-table v-else-if="transferOutboxRows.length" :data="transferOutboxRows" stripe size="small">
        <el-table-column label="节点 / 业务键" min-width="200">
          <template #default="{ row }">
            <div>{{ row.nodeName || '-' }}</div>
            <div class="task-sub">{{ row.businessKey || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="接收人" width="110">
          <template #default="{ row }">{{ row.toEmpId || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :class="transferStatusCls(row.status)">{{ transferStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发起时间" width="140">
          <template #default="{ row }">{{ fmtDateTime(row.initiatedTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" class-name="operation-cell" width="120" fixed="right">
          <template #default="{ row }">
            <BpAdaptiveRowActions>
              <template #primary><el-button type="primary" link size="small" @click="viewOutboxRow(row)">查看</el-button></template>
              <template #expanded><el-button v-if="row.status === 'PENDING_ACCEPT'" type="danger" link size="small" :disabled="row._acting" @click="cancelTransfer(row)">撤回</el-button></template>
              <template #compact><el-dropdown v-if="row.status === 'PENDING_ACCEPT'" trigger="click" popper-class="bp-crud-menu"><el-button link size="small" :disabled="row._acting" aria-label="更多转出记录操作">更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item divided class="danger-item" :disabled="row._acting" @click="cancelTransfer(row)">撤回</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template>
            </BpAdaptiveRowActions>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="table-state" role="status">暂无转出记录</div>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import PageTitle from '@/components/PageTitle.vue';
import BpAdaptiveRowActions from '@/components/BpAdaptiveRowActions.vue';
import { fmtDateTime } from '@/utils/datetime';
import { useUserStore } from '@/stores/user';
import { getUnreadNotificationCount, listNotifications, markRead } from '@/api/workspace';
import {
  listTodoTasks, listDoneTasks,
  transferInbox, transferAccept, transferDecline, transferOutbox, transferCancel
} from '@/api/workflow';
import { listRecentAnnouncements } from '@/api/announcement';

const router = useRouter();
const store = useUserStore();
const slaText = (status) => ({ normal: '正常', warn: '预警', overdue: '超时', GREEN: '正常', YELLOW: '预警', RED: '超时' }[status] || '正常');
const slaKey = (status) => ({ GREEN: 'normal', YELLOW: 'warn', RED: 'overdue' }[status] || status || 'normal');

function fmtDate(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 16);
}

const greeting = computed(() => {
  const hour = new Date().getHours();
  let period = '上午好';
  if (hour >= 18) period = '晚上好';
  else if (hour >= 13) period = '下午好';
  else if (hour >= 11) period = '中午好';
  return `${period}，${store.displayName || '当前用户'}`;
});
const desc = computed(() => `已分配角色：${store.roleSummary || '—'} · 机构：${store.orgName || '—'}`);

const announcements = ref([]);
const announcementsLoading = ref(false);
async function loadAnnouncements() {
  announcementsLoading.value = true;
  try {
    const result = await listRecentAnnouncements(3);
    announcements.value = Array.isArray(result) ? result.slice(0, 3) : [];
  } catch {
    announcements.value = [];
  } finally {
    announcementsLoading.value = false;
  }
}

const ntfRows = ref([]);
const ntfUnread = ref(0);
const notificationsLoading = ref(false);
const ntfTypeCls = (type) => ({ WORKFLOW: 'tag-info', SYSTEM: 'tag-success', BUSINESS: 'tag-warning', PERF: 'tag-info', APPROVAL: 'tag-warning' }[type] || 'tag-info');
const ntfTypeLabel = (type) => ({ WORKFLOW: '流程', SYSTEM: '系统', BUSINESS: '业务', PERF: '绩效', APPROVAL: '审批' }[type] || type || '-');

async function loadNotifications() {
  notificationsLoading.value = true;
  try {
    // 两个接口没有数据依赖，必须同时发起，避免未读角标被列表请求无谓阻塞。
    const [listResult, unreadResult] = await Promise.allSettled([
      listNotifications({ pageNo: 1, pageSize: 3 }),
      getUnreadNotificationCount()
    ]);
    const rows = listResult.status === 'fulfilled' ? listResult.value : [];
    ntfRows.value = (rows?.records || (Array.isArray(rows) ? rows : [])).slice(0, 3);
    ntfUnread.value = unreadResult.status === 'fulfilled' ? Number(unreadResult.value) || 0 : 0;
  } finally {
    notificationsLoading.value = false;
  }
}

const markingNotificationIds = new Set();
async function onNtfClick(notification) {
  if (!notification?.id || notification.isRead) {
    router.push('/system/notifications');
    return;
  }
  if (markingNotificationIds.has(notification.id)) return;

  markingNotificationIds.add(notification.id);
  try {
    await markRead(notification.id);
    notification.isRead = true;
    ntfUnread.value = Math.max(0, ntfUnread.value - 1);
    window.dispatchEvent(new Event('notification-changed'));
    router.push('/system/notifications');
  } catch {
    // 写操作失败由统一 HTTP 拦截器提供真实错误反馈；不编造失败原因，也不误导为已读。
    router.push('/system/notifications');
  } finally {
    markingNotificationIds.delete(notification.id);
  }
}

const taskTab = ref('PENDING');
const tasks = ref([]);
const tasksLoading = ref(false);
const todoCount = ref(0);
let tasksRequestGeneration = 0;
const taskEmptyText = computed(() => (taskTab.value === 'PENDING' ? '暂无待办任务' : '暂无已办任务'));
const taskLoadingText = computed(() => (taskTab.value === 'PENDING' ? '待办任务加载中' : '已办任务加载中'));

async function loadTasks() {
  const requestedTab = taskTab.value;
  const requestGeneration = ++tasksRequestGeneration;
  tasksLoading.value = true;
  try {
    const result = await (requestedTab === 'PENDING' ? listTodoTasks : listDoneTasks)({ pageSize: 20 });
    // 同一页签连续刷新也可能乱序返回，只有最新一代请求可以提交结果。
    if (requestGeneration !== tasksRequestGeneration || requestedTab !== taskTab.value) return;
    tasks.value = Array.isArray(result) ? result : (result?.records || []);
    if (requestedTab === 'PENDING') {
      const total = Number(result?.total);
      todoCount.value = Number.isFinite(total) ? total : tasks.value.length;
    }
  } catch {
    if (requestGeneration === tasksRequestGeneration && requestedTab === taskTab.value) {
      tasks.value = [];
      if (requestedTab === 'PENDING') todoCount.value = 0;
    }
  } finally {
    if (requestGeneration === tasksRequestGeneration && requestedTab === taskTab.value) {
      tasksLoading.value = false;
    }
  }
}

const transferInboxRows = ref([]);
const transferInboxLoading = ref(false);
async function loadTransferInbox() {
  transferInboxLoading.value = true;
  try {
    const result = await transferInbox();
    transferInboxRows.value = Array.isArray(result) ? result : [];
  } catch {
    transferInboxRows.value = [];
  } finally {
    transferInboxLoading.value = false;
  }
}

const transferOutboxRows = ref([]);
const transferOutboxLoading = ref(false);
async function loadTransferOutbox() {
  transferOutboxLoading.value = true;
  try {
    const result = await transferOutbox();
    transferOutboxRows.value = Array.isArray(result) ? result : [];
  } catch {
    transferOutboxRows.value = [];
  } finally {
    transferOutboxLoading.value = false;
  }
}

const transferWritingIds = new Set();
function beginTransferWrite(row) {
  if (!row || row.id == null || transferWritingIds.has(row.id)) return false;
  transferWritingIds.add(row.id);
  row._acting = true;
  return true;
}
function endTransferWrite(row) {
  if (!row || row.id == null) return;
  transferWritingIds.delete(row.id);
  row._acting = false;
}
function transferSubject(row) {
  return row?.nodeName || row?.businessKey || '该任务';
}

async function acceptTransfer(row) {
  if (!beginTransferWrite(row)) return;
  try {
    await ElMessageBox.confirm(`确认认领转交：${transferSubject(row)}？`, '认领转交', {
      type: 'warning', confirmButtonText: '认领', cancelButtonText: '取消'
    });
    await transferAccept(row.id);
    ElMessage.success('已认领');
    await loadTransferInbox();
  } catch {
    // 用户取消或服务端写入失败都不改变列表；服务端失败由统一拦截器反馈真实原因。
  } finally {
    endTransferWrite(row);
  }
}

async function declineTransfer(row) {
  if (!beginTransferWrite(row)) return;
  try {
    const result = await ElMessageBox.prompt('请填写拒绝理由（必填）', `拒绝转交：${transferSubject(row)}`, {
      type: 'warning', inputPattern: /\S+/, inputErrorMessage: '拒绝理由必填'
    });
    const reason = String(result?.value || '').trim();
    if (!reason) return;
    await transferDecline(row.id, { reason });
    ElMessage.success('已拒绝');
    await loadTransferInbox();
  } catch {
    // 用户取消或服务端写入失败时保留原行，避免伪造状态变化。
  } finally {
    endTransferWrite(row);
  }
}

async function cancelTransfer(row) {
  if (!beginTransferWrite(row)) return;
  try {
    await ElMessageBox.confirm(`确认撤回转交：${transferSubject(row)}？`, '撤回转交', {
      type: 'warning', confirmButtonText: '撤回', cancelButtonText: '取消'
    });
    await transferCancel(row.id);
    ElMessage.success('已撤回');
    await loadTransferOutbox();
  } catch {
    // 用户取消或服务端写入失败时保留原行，避免伪造状态变化。
  } finally {
    endTransferWrite(row);
  }
}

const TRANSFER_STATUS_MAP = {
  PENDING_ACCEPT: { label: '待认领', cls: 'tag-warning' },
  ACCEPTED: { label: '已认领', cls: 'tag-success' },
  REJECTED: { label: '已拒绝', cls: 'tag-danger' },
  CANCELLED: { label: '已撤回', cls: 'tag-info' }
};
const transferStatusLabel = (status) => TRANSFER_STATUS_MAP[status]?.label || status || '-';
const transferStatusCls = (status) => TRANSFER_STATUS_MAP[status]?.cls || 'tag-info';

function viewOutboxRow(row) {
  router.push({
    path: '/system/workflow-monitor',
    query: { processInstanceId: row.processInstanceId, businessKey: row.businessKey || undefined }
  });
}

function resolveTaskRoute(row, mode) {
  const id = row.taskId || row.id;
  if (!id) return null;
  const isDetail = mode === 'detail';
  if (row.bizType === 'ASSET_PROJECT') {
    const assetProjectId = row.bizId || (String(row.businessKey || '').startsWith('ASSET_PROJECT:') ? String(row.businessKey).slice(14) : '');
    if (!assetProjectId) return null;
    return {
      path: `/marketing/asset-projects/${assetProjectId}`,
      query: { tab: isDetail ? 'PROCESSED' : 'PENDING', taskId: id }
    };
  }
  if (row.bizType === 'TARGET_ADJUST') {
    return isDetail
      ? { path: '/perf/targets', query: { tab: 'done', taskId: id, bizKey: row.businessKey } }
      : { path: '/perf/targets', query: { tab: 'todo', taskId: id } };
  }
  if (row.bizType === 'ALLOC_ADJUST') {
    return isDetail
      ? { path: '/perf/adjust', query: { tab: 'done', taskId: id, action: 'view', bizKey: row.businessKey } }
      : { path: '/perf/adjust', query: { tab: 'todo', taskId: id, action: 'open' } };
  }
  if (row.bizType === 'SUPPORT' || row.bizType === 'SUPPORT_DEPT') {
    const supportId = row.bizId || (String(row.businessKey || '').startsWith('SUPPORT:')
      ? String(row.businessKey).slice('SUPPORT:'.length)
      : '');
    if (!supportId) return null;
    return {
      path: `/bizexec/supports/${supportId}`,
      query: { tab: isDetail ? 'DEPT_DONE' : 'DEPT_TODO', taskId: id }
    };
  }
  return null;
}

function goHandle(row) {
  const route = resolveTaskRoute(row, 'handle');
  if (!route) {
    ElMessage.info('该审批请到对应业务系统处理');
    return;
  }
  router.push(route);
}

function goDetail(row) {
  const route = resolveTaskRoute(row, 'detail');
  if (!route) {
    ElMessage.info('该审批详情请到对应业务系统查看');
    return;
  }
  router.push(route);
}

onMounted(() => {
  // 所有可见区块相互独立：直接并行启动，避免无用聚合请求延迟首屏。
  loadTasks();
  loadAnnouncements();
  loadNotifications();
  loadTransferInbox();
  loadTransferOutbox();
});
</script>

<style lang="scss" scoped>
.workspace {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  max-width: var(--layout-content-max-width);
  margin: 0 auto;
}

.workspace-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-6);
  min-height: 52px;
}

.workspace-heading {
  min-width: 0;
}

.workspace-heading :deep(.page-title) {
  margin: 0;
  color: var(--color-text-strong);
  font-size: 18px;
  font-weight: 600;
  line-height: 28px;
}

.workspace-desc,
.welcome-strip p {
  margin: 0;
}

.workspace-desc {
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 18px;
}

.workspace-summary {
  display: flex;
  flex: 0 0 auto;
  overflow: hidden;
  margin: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-surface);
  box-shadow: var(--shadow-surface);
}

.summary-item {
  display: flex;
  align-items: baseline;
  gap: var(--space-1);
  min-width: 126px;
  padding: var(--space-2) var(--space-3);
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 18px;
}

.summary-item + .summary-item {
  border-left: 1px solid var(--color-border);
}

.summary-item dt,
.summary-item dd {
  margin: 0;
}

.summary-item dd {
  color: var(--color-text-strong);
  font-size: 22px;
  font-weight: 600;
  line-height: 24px;
  font-variant-numeric: tabular-nums;
}

.welcome-strip {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-brand-700);
  border-radius: var(--radius-control);
  background: var(--color-workspace-strip);
}

.greet {
  color: var(--color-text-strong);
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
}

.desc {
  color: var(--color-text);
  font-size: 12px;
  line-height: 18px;
}

.cols {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, .72fr);
  gap: var(--space-3);
}

.card-section {
  margin: 0;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-surface);
  box-shadow: var(--shadow-surface);
}

.card-h {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 48px;
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--color-border);
}

.section-title {
  margin: 0;
  color: var(--color-text-strong);
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}

.card-h :deep(.el-button) {
  margin-left: auto;
}

.task-section .card-h :deep(.el-radio-group) {
  margin-left: auto;
}

.task-section .card-h :deep(.el-button) {
  margin-left: 0;
}

.list-state,
.table-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 88px;
  padding: var(--space-4);
  color: var(--color-text-muted);
  font-size: 14px;
  line-height: 22px;
}

.table-state {
  min-height: 176px;
}

.ann-list {
  padding: 0 var(--space-4);
}

.ann-item,
.ntf {
  width: 100%;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
}

.ann-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 48px;
  padding: 0;
  border-bottom: 1px solid var(--color-border);
  cursor: pointer;
}

.ann-item:last-child {
  border-bottom: 0;
}

.ann-item:hover .ann-title,
.ann-item:focus-visible .ann-title {
  color: var(--color-brand-700);
}

.ann-title,
.ntf-title,
.ntf-desc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ann-title {
  flex: 1;
  min-width: 0;
  color: var(--color-text-strong);
  font-size: 14px;
  line-height: 22px;
}

.ann-date,
.time {
  flex: 0 0 auto;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 18px;
  font-variant-numeric: tabular-nums;
}

.ntf-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 var(--space-1);
  border-radius: 10px;
  background: var(--color-danger-bg);
  color: var(--color-danger-fg);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.ntf-list {
  padding: 0 var(--space-4);
}

.ntf {
  display: flex;
  gap: var(--space-2);
  min-height: 74px;
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--color-border);
  cursor: pointer;
  transition: background-color var(--motion-fast) var(--ease-enter);
}

.ntf:last-child {
  border-bottom: 0;
}

.ntf:hover,
.ntf:focus-visible {
  background: var(--color-surface-soft);
}

.ntf.unread {
  background: var(--color-brand-100);
}

.dot {
  flex: 0 0 auto;
  width: 8px;
  height: 8px;
  margin-top: 7px;
  border-radius: 50%;
  background: var(--color-border-strong);
}

.dot.active {
  background: var(--color-brand-700);
}

.ntf-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.ntf-title-row,
.meta {
  display: flex;
  align-items: center;
  min-width: 0;
}

.ntf-title-row {
  gap: var(--space-2);
}

.ntf-title {
  flex: 1;
  min-width: 0;
  color: var(--color-text-strong);
  font-size: 14px;
  font-weight: 500;
  line-height: 22px;
}

.ntf-unread-text {
  flex: 0 0 auto;
  color: var(--color-brand-700);
  font-size: 12px;
  line-height: 18px;
}

.ntf-desc {
  display: block;
  color: var(--color-text);
  font-size: 12px;
  line-height: 18px;
}

.meta {
  gap: var(--space-2);
}

.task-sub {
  overflow: hidden;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sla-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: var(--space-1);
  border-radius: 50%;
  vertical-align: middle;
}

.sla-dot.normal {
  background: var(--color-success-fg);
}

.sla-dot.warn {
  background: var(--color-warning-fg);
}

.sla-dot.overdue {
  background: var(--color-danger-fg);
}

.remain.warn {
  color: var(--color-warning-fg);
  font-weight: 500;
}

.remain.overdue {
  color: var(--color-danger-fg);
  font-weight: 600;
}

.card-section :deep(.el-table) {
  --el-table-border-color: var(--color-border);
  --el-table-header-bg-color: var(--color-surface-soft);
  --el-table-row-hover-bg-color: var(--color-surface-soft);
  --el-table-text-color: var(--color-text);
  --el-table-header-text-color: var(--color-text);
}
</style>
