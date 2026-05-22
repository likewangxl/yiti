<template>
  <div class="workspace">
    <!-- 顶部欢迎条 -->
    <div class="hero">
      <div class="greet">{{ greeting }}</div>
      <div class="desc">{{ desc }}</div>
    </div>

    <!-- 4 stat 卡 -->
    <div class="stats">
      <div v-for="s in data.stats" :key="s.label" class="stat">
        <div class="label">{{ s.label }}</div>
        <div class="value">{{ s.value }}</div>
        <div :class="['trend', s.trendType]">{{ s.trend }}</div>
      </div>
    </div>

    <div class="cols">
      <!-- 待办 / 已办 -->
      <div class="card-section">
        <div class="card-h">
          <div class="title">我的任务</div>
          <el-radio-group v-model="taskTab" size="small" @change="loadTasks">
            <el-radio-button label="PENDING">待办</el-radio-button>
            <el-radio-button label="DONE">已办</el-radio-button>
          </el-radio-group>
          <a class="more" @click="$router.push('/workspace')">刷新</a>
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

      <!-- 通知 -->
      <div class="card-section notify">
        <div class="card-h">
          <div class="title">通知</div>
          <a class="more">全部</a>
        </div>
        <div v-for="(n, i) in data.notifications" :key="i" class="ntf">
          <span class="dot" :class="{ unread: !n.read }"></span>
          <div class="body">
            <div class="t">{{ n.title }}</div>
            <div class="m">{{ n.tag }} · {{ n.time }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="card-section">
      <div class="card-h"><div class="title">快捷入口</div></div>
      <div class="shortcuts">
        <div v-for="s in data.shortcuts" :key="s.label" class="sc">
          <div class="ico">{{ s.icon }}</div>
          <div class="lbl">{{ s.label }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { fmtDateTime } from '@/utils/datetime';
import { useUserStore } from '@/stores/user';
import { workspace as initial } from '@/mock';
import { getWorkspace, getMyTodoCount, getUnreadNotificationCount } from '@/api/workspace';
import { listTodoTasks, listDoneTasks } from '@/api/workflow';

const router = useRouter();
const store = useUserStore();
const data = ref(initial);
const slaText = (s) => ({ normal: '正常', warn: '预警', overdue: '超时', GREEN: '正常', YELLOW: '预警', RED: '超时' }[s] || '正常');
// 把后端 slaStatus(GREEN/YELLOW/RED) 映射到模板 CSS 类（normal/warn/overdue），兼容旧值
const slaKey = (s) => ({ GREEN: 'normal', YELLOW: 'warn', RED: 'overdue' }[s] || s || 'normal');

// 欢迎语 + 描述：用 userStore 真实信息 + 时段问候，不再用 mock 张三
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

// 我的任务（待办 / 已办）—— 直连 workflow-center，不再依赖 portal/workspace.todos
// 之所以另开数据源：portal/workspace 当前未聚合 workflow/tasks，会落回 mock 张三
const taskTab = ref('PENDING');
const tasks = ref([]);
const tasksLoading = ref(false);
async function loadTasks() {
  tasksLoading.value = true;
  try {
    const fn = taskTab.value === 'PENDING' ? listTodoTasks : listDoneTasks;
    const r = await fn({ pageNo: 1, pageSize: 20 });
    tasks.value = Array.isArray(r) ? r : (r?.records || []);
  } catch { tasks.value = []; } finally { tasksLoading.value = false; }
}
function goHandle(row) {
  const id = row.id || row.taskId;
  if (!id) return;
  // 跳 /perf/adjust 的「待我审批」tab 并自动弹审批 dialog（tab=todo + action=open）
  // 落地后由 Adjust.vue mounted 读 query 切 tab + 在 todos 数组里找对应 taskId 弹审批
  router.push({ path: '/perf/adjust', query: { tab: 'todo', taskId: id, action: 'open' } });
}

onMounted(async () => {
  // 顶部欢迎条 / stat 卡 / 通知 / 快捷入口 仍走 portal/workspace 聚合
  try { const r = await getWorkspace(); if (r) data.value = r; } catch (e) { /* noop */ }
  // 待办列表独立从 workflow/tasks 拉
  loadTasks();
  // stats[0]「待办任务」+ stats[1]「未读通知」覆盖为真实 count（其他 2 卡保持 mock）
  Promise.all([getMyTodoCount(), getUnreadNotificationCount()])
    .then(([todo, unread]) => {
      const stats = data.value?.stats;
      if (Array.isArray(stats) && stats.length >= 2) {
        stats[0].value = todo;
        stats[0].trend = '';        // mock 的 "较昨日 -2" 无意义，清空
        stats[0].trendType = '';
        stats[1].value = unread;
        stats[1].trend = '';
        stats[1].trendType = '';
      }
    })
    .catch((err) => { console.warn('[workspace stats] load failed, keep mock', err); });
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

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
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
  .ntf {
    display: flex; gap: 12px; padding: 12px 20px;
    border-bottom: 1px solid $border-3;
    &:last-child { border-bottom: none; }
    .dot { width: 6px; height: 6px; border-radius: 50%; margin-top: 8px; background: $border-2; flex-shrink: 0; }
    .dot.unread { background: $danger; }
    .body { flex: 1; min-width: 0; }
    .t { font-size: 13px; color: $text-1; }
    .m { font-size: 11px; color: $text-3; margin-top: 4px; }
  }
}

.shortcuts {
  padding: 16px 20px;
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 12px;
  .sc {
    background: $bg-soft;
    border: 1px solid $border-1;
    border-radius: 4px;
    padding: 16px 8px;
    text-align: center;
    cursor: pointer;
    transition: .15s;
    &:hover { border-color: $primary-400; transform: translateY(-2px); box-shadow: 0 4px 12px rgba(30,91,186,.15); }
    .ico { font-size: 26px; margin-bottom: 6px; }
    .lbl { font-size: 12px; color: $text-2; }
  }
}
</style>
