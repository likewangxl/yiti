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
            <template #default="{ row }">{{ row.startTime || row.createTime || '-' }}</template>
          </el-table-column>
          <el-table-column label="审批结果" width="100" v-if="taskTab === 'DONE'">
            <template #default="{ row }">
              <el-tag v-if="row.bizStatus" size="small" :type="bizStatusTag(row.bizStatus)" effect="plain">
                {{ bizStatusText(row.bizStatus) }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column width="110">
            <template #header>
              审批时效
              <el-tooltip placement="top" content="审批是否按时（基于 wf_timeout_rule.warning_hours / timeout_hours）；🟢 正常 / 🟡 临近超时 / 🔴 已超时">
                <span class="hdr-help">?</span>
              </el-tooltip>
            </template>
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
import { useUserStore } from '@/stores/user';
import { workspace as initial } from '@/mock';
import { getWorkspace } from '@/api/workspace';
import { listTodoTasks, listDoneTasks } from '@/api/workflow';
import { getAdjustDetail, getTargetAdjust } from '@/api/perf';

const router = useRouter();
const store = useUserStore();
const data = ref(initial);
const slaText = (s) => ({ normal: '正常', warn: '预警', overdue: '超时', GREEN: '正常', YELLOW: '预警', RED: '超时' }[s] || '正常');
// 把后端 slaStatus(GREEN/YELLOW/RED) 映射到模板 CSS 类（normal/warn/overdue），兼容旧值
const slaKey = (s) => ({ GREEN: 'normal', YELLOW: 'warn', RED: 'overdue' }[s] || s || 'normal');

// 业务申请状态展示（lazy load 自业务详情接口；和 SLA 是不同维度）
const bizStatusText = (s) => ({ DRAFT: '草稿', IN_APPROVAL: '审批中', APPROVED: '已通过', REJECTED: '已驳回/撤回', WITHDRAWN: '已撤回' }[s] || s || '-');
const bizStatusTag = (s) => ({ DRAFT: 'info', IN_APPROVAL: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info' }[s] || 'info');

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
    // lazy-load 每行业务申请状态（已办 tab 需要展示；后端 TaskRespDTO 不含 bizStatus）
    fetchBizStatuses();
  } catch { tasks.value = []; } finally { tasksLoading.value = false; }
}
async function fetchBizStatuses() {
  const detailFn = (t) => {
    if (['ALLOC_ADJUST', 'PERF_ALLOC_ADJUST'].includes(t)) return getAdjustDetail;
    if (['TARGET_ADJUST', 'PERF_TARGET_ADJUST'].includes(t)) return getTargetAdjust;
    return null;
  };
  await Promise.all(tasks.value.map(async (row) => {
    const fn = detailFn(row.bizType);
    const id = row.bizId;
    if (!fn || !id) return;
    try {
      const d = await fn(id);
      // 后端响应可能直接是 apply 对象（含 .status）或包一层 { apply: {...} }
      row.bizStatus = d?.status || d?.apply?.status || null;
    } catch { /* 单条失败不影响整体 */ }
  }));
}
function goHandle(row) {
  const id = row.id || row.taskId;
  if (!id) return;
  // 业绩调整审批：跳到 /perf/adjust 页内打开审批抽屉，不开独立菜单
  // 其他 bizType 暂时也走该路径（接入时再分流），保持"审批办理在业务页内"的设计
  if (['ALLOC_ADJUST', 'PERF_ALLOC_ADJUST'].includes(row.bizType)) {
    router.push({ path: '/perf/adjust', query: { taskId: id, action: 'open' } });
  } else {
    router.push({ path: '/perf/adjust', query: { taskId: id, action: 'open' } });
  }
}

onMounted(async () => {
  // 顶部欢迎条 / stat 卡 / 通知 / 快捷入口 仍走 portal/workspace 聚合
  try { const r = await getWorkspace(); if (r) data.value = r; } catch (e) { /* noop */ }
  // 待办列表独立从 workflow/tasks 拉
  loadTasks();
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
.hdr-help {
  display: inline-block; width: 14px; height: 14px; line-height: 14px;
  text-align: center; border-radius: 50%;
  background: $border-2; color: #fff; font-size: 11px;
  margin-left: 4px; cursor: help;
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
