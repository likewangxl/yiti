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
             @click="$router.push('/system/notifications')">
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
        <el-table-column label="操作" width="80" v-if="taskTab === 'PENDING'">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="goHandle(row)">办理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>


  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { fmtDateTime } from '@/utils/datetime';
import { useUserStore } from '@/stores/user';
import { workspace as initial } from '@/mock';
import { getWorkspace, listNotifications, getUnreadNotificationCount } from '@/api/workspace';
import { listTodoTasks, listDoneTasks } from '@/api/workflow';
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

// 我的任务
const taskTab = ref('PENDING');
const tasks = ref([]);
const tasksLoading = ref(false);
async function loadTasks() {
  tasksLoading.value = true;
  try {
    const fn = taskTab.value === 'PENDING' ? listTodoTasks : listDoneTasks;
    const r = await fn({ pageSize: 20 });
    tasks.value = Array.isArray(r) ? r : [];
  } catch { tasks.value = []; } finally { tasksLoading.value = false; }
}


function goHandle(row) {
  const id = row.taskId || row.id;
  if (!id) return;
  if (row.bizType === 'TARGET_ADJUST') {
    router.push({ path: '/perf/targets', query: { tab: 'todo', taskId: id } });
  } else {
    router.push({ path: '/perf/adjust', query: { tab: 'todo', taskId: id, action: 'open' } });
  }
}

onMounted(async () => {
  try { const r = await getWorkspace(); if (r) data.value = r; } catch {}
  loadTasks();
  loadAnnouncements();
  loadNotifications();
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
