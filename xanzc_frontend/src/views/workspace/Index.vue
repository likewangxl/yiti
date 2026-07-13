<template>
  <div class="workspace">
    <!-- 顶部欢迎条 -->
    <div class="hero">
      <div class="greet">{{ greeting }}</div>
      <div class="desc">{{ desc }}</div>
    </div>

    <div class="cols">
      <!-- 公告 -->
      <div class="card-section">
        <div class="card-h">
          <div class="title">公告</div>
          <a class="more" @click="$router.push('/workspace/announcements')">全部</a>
        </div>
        <div class="ann-list">
          <div v-if="!announcements.length" class="ann-empty">暂无公告</div>
          <div v-for="a in announcements" :key="a.id" class="ann-item" @click="$router.push('/announcement/' + a.id)">
            <div class="ann-title">{{ a.title }}</div>
            <div class="ann-date">{{ fmtDate(a.publishDate) }}</div>
          </div>
        </div>
      </div>

      <!-- 通知 -->
      <div class="card-section notify">
        <div class="card-h">
          <div class="title">通知 <span v-if="ntfUnread" class="ntf-badge">{{ ntfUnread }}</span></div>
          <a class="more" @click="$router.push('/system/notifications')">全部 →</a>
        </div>
        <div v-if="!ntfRows.length" class="ntf-empty">暂无通知</div>
        <div v-for="n in ntfRows" :key="n.id || n.title" class="ntf" :class="{ unread: !n.isRead }"
             @click="onNtfClick(n)">
          <span class="dot" :class="{ active: !n.isRead }"></span>
          <div class="body">
            <div class="t">{{ n.title || '—' }}</div>
            <div v-if="n.content" class="desc">{{ n.content }}</div>
            <div class="meta">
              <el-tag size="small" effect="plain" :class="ntfTypeCls(n.bizType)">{{ ntfTypeLabel(n.bizType) }}</el-tag>
              <span class="time">{{ fmtDateTime(n.createdTime) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 我的任务 -->
    <div class="card-section">
      <div class="card-h">
        <div class="title">我的任务</div>
        <el-radio-group v-model="taskTab" size="small" @change="loadTasks">
          <el-radio-button value="PENDING">待办</el-radio-button>
          <el-radio-button value="DONE">已办</el-radio-button>
        </el-radio-group>
        <a class="more" @click="loadTasks">刷新</a>
      </div>
      <el-table :data="tasks" stripe size="small" v-loading="tasksLoading" empty-text="暂无任务">
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
            <span class="sla-dot" :class="slaKey(row.slaStatus)"></span>
            {{ slaText(row.slaStatus) }}
          </template>
        </el-table-column>
        <el-table-column label="剩余时长" width="100">
          <template #default="{ row }">
            <span :class="['remain', slaKey(row.slaStatus)]">{{ row.remain || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button v-if="taskTab === 'PENDING'" type="primary" link size="small" @click="goHandle(row)">办理</el-button>
            <el-button v-else type="primary" link size="small" @click="goDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 待认领转交：秘书/行长转交给我、待我认领的任务（两阶段转交第二阶段） -->
    <div class="card-section">
      <div class="card-h">
        <div class="title">待认领转交</div>
        <a class="more" @click="loadTransferInbox">刷新</a>
      </div>
      <el-table :data="transferInboxRows" stripe size="small" v-loading="transferInboxLoading" empty-text="暂无待认领转交">
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
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :loading="row._acting" @click="acceptTransfer(row)">认领</el-button>
            <el-button type="danger" link size="small" :loading="row._acting" @click="declineTransfer(row)">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 我转出的（只读）：我发起的转交，展示进度，不提供任何操作（撤回等走审批流监控页） -->
    <div class="card-section">
      <div class="card-h">
        <div class="title">我转出的</div>
        <span class="hint">只读</span>
        <a class="more" @click="loadTransferOutbox">刷新</a>
      </div>
      <el-table :data="transferOutboxRows" stripe size="small" v-loading="transferOutboxLoading" empty-text="暂无转出记录">
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
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewOutboxRow(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import { fmtDateTime } from '@/utils/datetime';
import { useUserStore } from '@/stores/user';
import { workspace as initial } from '@/mock';
import { getWorkspace, listNotifications, getUnreadNotificationCount, markRead } from '@/api/workspace';
import {
  listTodoTasks, listDoneTasks,
  transferInbox, transferAccept, transferDecline, transferOutbox
} from '@/api/workflow';
import { listRecentAnnouncements } from '@/api/announcement';

const router = useRouter();
const store = useUserStore();
const data = ref(initial);
const slaText = (s) => ({ normal: '正常', warn: '预警', overdue: '超时', GREEN: '正常', YELLOW: '预警', RED: '超时' }[s] || '正常');
const slaKey = (s) => ({ GREEN: 'normal', YELLOW: 'warn', RED: 'overdue' }[s] || s || 'normal');

function fmtDate(t) {
  if (!t) return '-';
  return String(t).replace('T', ' ').slice(0, 16);
}

const greeting = computed(() => {
  const h = new Date().getHours();
  let t = '上午好';
  if (h >= 18) t = '晚上好';
  else if (h >= 13) t = '下午好';
  else if (h >= 11) t = '中午好';
  return `${t}，${store.displayName || '当前用户'}`;
});
const desc = computed(() => {
  const role = store.roleName || '—';
  const org  = store.orgName  || '—';
  return `当前角色：${role} · 机构：${org}`;
});

// 公告
const announcements = ref([]);
async function loadAnnouncements() {
  try {
    const r = await listRecentAnnouncements(10);
    announcements.value = Array.isArray(r) ? r : [];
  } catch { announcements.value = []; }
}

// 通知（从真 API 加载，取最近 5 条）
const ntfRows = ref([]);
const ntfUnread = ref(0);
const ntfTypeCls = (t) => ({ WORKFLOW: 'tag-info', SYSTEM: 'tag-success', BUSINESS: 'tag-warning', PERF: 'tag-info', APPROVAL: 'tag-warning' }[t] || 'tag-info');
const ntfTypeLabel = (t) => ({ WORKFLOW: '流程', SYSTEM: '系统', BUSINESS: '业务', PERF: '绩效', APPROVAL: '审批' }[t] || t || '-');
async function loadNotifications() {
  try {
    const r = await listNotifications({ pageNo: 1, pageSize: 5 });
    ntfRows.value = r?.records || (Array.isArray(r) ? r : []);
  } catch { ntfRows.value = []; }
  try { ntfUnread.value = await getUnreadNotificationCount(); } catch {}
}

// 点击通知：标记已读（未读角标 -1）后进入通知中心（详情）页
async function onNtfClick(n) {
  if (n && n.id && !n.isRead) {
    try {
      await markRead(n.id);
      n.isRead = true;
      ntfUnread.value = Math.max(0, (ntfUnread.value || 0) - 1);
    } catch { /* 标记失败不阻塞跳转 */ }
  }
  router.push('/system/notifications');
}

// 我的任务
const taskTab = ref('PENDING');
const tasks = ref([]);
const tasksLoading = ref(false);
async function loadTasks() {
  tasksLoading.value = true;
  try {
    const fn = taskTab.value === 'PENDING' ? listTodoTasks : listDoneTasks;
    const r = await fn({ pageSize: 20 });
    // listTodoTasks/listDoneTasks 经 unwrapPage 返回 {records,total}（非数组），必须取 records
    tasks.value = Array.isArray(r) ? r : (r?.records || []);
  } catch { tasks.value = []; } finally { tasksLoading.value = false; }
}


// 转交待认领收件箱：秘书/行长转交给我、待我认领或拒绝的任务
const transferInboxRows = ref([]);
const transferInboxLoading = ref(false);
async function loadTransferInbox() {
  transferInboxLoading.value = true;
  try {
    transferInboxRows.value = await transferInbox();
  } catch { transferInboxRows.value = []; } finally { transferInboxLoading.value = false; }
}

async function acceptTransfer(row) {
  row._acting = true;
  try {
    await transferAccept(row.id);
    ElMessage.success('已认领');
    loadTransferInbox();
  } catch { /* http.js 拦截器已 toast 错误详情 */ } finally { row._acting = false; }
}

async function declineTransfer(row) {
  let reason;
  try {
    const r = await ElMessageBox.prompt('请填写拒绝理由（必填）', `拒绝转交：${row.nodeName || row.businessKey || ''}`, {
      type: 'warning', inputPattern: /\S+/, inputErrorMessage: '拒绝理由必填'
    });
    reason = r.value;
  } catch { return; } // 用户取消
  row._acting = true;
  try {
    await transferDecline(row.id, { reason });
    ElMessage.success('已拒绝');
    loadTransferInbox();
  } catch { /* http.js 拦截器已 toast 错误详情 */ } finally { row._acting = false; }
}

// 我转出的（只读）：我发起的转交进度
const transferOutboxRows = ref([]);
const transferOutboxLoading = ref(false);
async function loadTransferOutbox() {
  transferOutboxLoading.value = true;
  try {
    transferOutboxRows.value = await transferOutbox();
  } catch { transferOutboxRows.value = []; } finally { transferOutboxLoading.value = false; }
}

const TRANSFER_STATUS_MAP = {
  PENDING_ACCEPT: { label: '待认领', cls: 'tag-warning' },
  ACCEPTED: { label: '已认领', cls: 'tag-success' },
  REJECTED: { label: '已拒绝', cls: 'tag-danger' },
  CANCELLED: { label: '已撤回', cls: 'tag-info' }
};
const transferStatusLabel = (s) => TRANSFER_STATUS_MAP[s]?.label || s || '-';
const transferStatusCls = (s) => TRANSFER_STATUS_MAP[s]?.cls || 'tag-info';

// 查看：复用「审批流监控」页详情抽屉（该页仅秘书/行长可访问，与发起转交同一角色门槛，权限对齐）
function viewOutboxRow(row) {
  router.push({ path: '/system/workflow-monitor', query: { processInstanceId: row.processInstanceId, businessKey: row.businessKey || undefined } });
}

// bizType → 本台（绩效/管理台）对应审批页。LOAN/SUPPORT/LEAD/TOUCH 属业务台(xanpd)，
// 本台无对应页面，给出提示而非误跳 /perf/adjust。
function resolveTaskRoute(row, mode) {
  const id = row.taskId || row.id;
  if (!id) return null;
  // 已办「详情」→ 切到目标页"已审批"tab 并弹只读详情；待办「办理」→ 切"待我审批"tab 并弹审批窗。
  // bizKey(businessKey, 形如 ALLOC_ADJUST:{applyId}) 透传，供接收页即便不在当前页也能直接拉详情。
  const isDetail = mode === 'detail';
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
  return null;
}

// 待办「办理」：跳转对应审批页
function goHandle(row) {
  const route = resolveTaskRoute(row, 'handle');
  if (!route) {
    return ElMessage.info('该审批请到对应业务系统处理');
  }
  router.push(route);
}

// 已办「详情」：跳转对应页面查看（审批通过/驳回的记录在审批页只读展示）
function goDetail(row) {
  const route = resolveTaskRoute(row, 'detail');
  if (!route) {
    return ElMessage.info('该审批详情请到对应业务系统查看');
  }
  router.push(route);
}

onMounted(async () => {
  try { const r = await getWorkspace(); if (r) data.value = r; } catch {}
  loadTasks();
  loadAnnouncements();
  loadNotifications();
  loadTransferInbox();
  loadTransferOutbox();
});
</script>

<style lang="scss" scoped>
.workspace {
  display: flex; flex-direction: column; gap: 12px;
}

.hero {
  background: linear-gradient(135deg, $primary 0%, $primary-400 100%);
  color: #fff;
  border-radius: 4px;
  padding: 20px 24px;
  .greet { font-size: 20px; font-weight: 600; }
  .desc { font-size: 13px; opacity: .85; margin-top: 6px; }
}

.cols {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 12px;
}

.card-section {
  margin-bottom: 0;
  padding: 0;
}

.card-h {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 20px;
  border-bottom: 1px solid $border-1;
  .title { font-size: 14px; font-weight: 600; }
  .hint  { font-size: 12px; color: $text-3; }
  .more  { margin-left: auto; color: $primary; cursor: pointer; font-size: 12px; }
}

// 公告列表
.ann-list {
  padding: 8px 20px;
  .ann-empty { color: $text-3; font-size: 13px; padding: 12px 0; text-align: center; }
  .ann-item {
    display: flex; justify-content: space-between; align-items: center;
    padding: 10px 0;
    border-bottom: 1px solid $border-3;
    cursor: pointer;
    &:last-child { border-bottom: none; }
    &:hover .ann-title { color: $primary; }
    .ann-title { font-size: 13px; color: $text-1; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ann-date { font-size: 12px; color: $text-3; margin-left: 16px; flex-shrink: 0; }
  }
}
.ann-more {
  padding: 8px 20px 12px;
  text-align: center;
  a { color: $primary; cursor: pointer; font-size: 13px; }
  a:hover { text-decoration: underline; }
}

.task-sub {
  font-size: 11px; color: $text-3; margin-top: 2px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.sla-dot {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%;
  margin-right: 6px; vertical-align: middle;
  &.normal  { background: $success; }
  &.warn    { background: $warning; }
  &.overdue { background: $danger; }
}
.remain.warn { color: $warning; font-weight: 500; }
.remain.overdue { color: $danger; font-weight: 600; }

.notify {
  .ntf-badge {
    display: inline-block; min-width: 18px; height: 18px; line-height: 18px;
    text-align: center; font-size: 11px; font-weight: 600; color: #fff;
    background: $danger; border-radius: 9px; padding: 0 5px; margin-left: 6px;
  }
  .ntf-empty { padding: 24px 20px; text-align: center; color: $text-3; font-size: 13px; }
  .ntf {
    display: flex; gap: 12px; padding: 12px 20px;
    border-bottom: 1px solid $border-3; cursor: pointer; transition: background .15s;
    &:last-child { border-bottom: none; }
    &:hover { background: $bg-soft; }
    &.unread { background: rgba(30, 91, 186, .03); }
    .dot {
      width: 6px; height: 6px; border-radius: 50%; margin-top: 8px;
      background: $border-2; flex-shrink: 0;
      &.active { background: $danger; }
    }
    .body { flex: 1; min-width: 0; }
    .t { font-size: 13px; color: $text-1; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .desc { font-size: 12px; color: $text-3; margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .meta { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
    .time { font-size: 11px; color: $text-4; }
  }
}
</style>
