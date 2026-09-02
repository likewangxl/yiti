<template>
  <div class="dashboard-container" v-loading="loading">
    <h1 class="page-title">工作台</h1>

    <div v-if="loadError" class="load-error" role="alert">{{ loadError }}</div>

    <div class="stat-grid">
      <div class="stat-card task-stat" data-metric="task-overview">
        <div class="stat-label">任务概览</div>
        <div class="stat-value">{{ taskOverviewCount }}</div>
        <div class="stat-sub">已发布任务</div>
      </div>

      <div v-if="showOrgMetrics" class="stat-card org-score-stat" data-metric="org-score">
        <div class="stat-label">所在机构得分</div>
        <div class="stat-value">{{ displayValue(summary.organizationScore) }}</div>
        <div class="stat-sub">{{ summary.organizationName }}</div>
      </div>
      <div v-if="showOrgMetrics" class="stat-card org-rank-stat" data-metric="org-rank">
        <div class="stat-label">所在机构排名</div>
        <div class="stat-value">{{ displayValue(summary.organizationRank) }}</div>
        <div class="stat-sub">当前考核周期</div>
      </div>
    </div>

    <div v-if="isOrganizationView" class="section-card organization-task-section">
      <div class="section-title">任务概览</div>
      <div v-if="summary.organizationTasks.length === 0" class="todo-empty">暂无已发布任务</div>
      <ul v-else class="todo-list">
        <li v-for="item in summary.organizationTasks" :key="item.id" class="todo-item">
          <span class="todo-dot"></span>
          <span class="todo-text">{{ item.title }}</span>
          <span class="todo-branch">{{ item.taskNatureLabel }}</span>
          <span class="todo-time">{{ item.endTime }}</span>
        </li>
      </ul>
    </div>

    <div class="section-card todo-section">
      <div class="section-title">待办事项</div>
      <div v-if="todoDisplayItems.length === 0" class="todo-empty">暂无待办事项</div>
      <ul v-else class="todo-list">
        <li
          v-for="item in todoDisplayItems"
          :key="item.id"
          :class="['todo-item', { 'is-actionable': todoRoute(item) }]"
          :role="todoRoute(item) ? 'button' : undefined"
          :tabindex="todoRoute(item) ? 0 : undefined"
          @click="openTodo(item)"
          @keydown.enter.prevent.self="openTodo(item)"
          @keydown.space.prevent.self="openTodo(item)"
        >
          <span class="todo-dot"></span>
          <span class="todo-text">{{ item.title }}</span>
          <span v-if="isOrganizationView" class="todo-branch">{{ item.branchName }}</span>
          <span v-if="isOrganizationView && !item.assignmentId" class="todo-score">得分 {{ displayValue(item.score) }}</span>
          <span v-if="isOrganizationView && !item.assignmentId" class="todo-rank">排名 {{ displayValue(item.rank) }}</span>
          <span v-else-if="isOrganizationView" class="todo-status">{{ item.statusLabel }}</span>
          <span v-else class="todo-status">{{ item.statusLabel }}</span>
          <span class="todo-time">{{ item.time }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getHomeSummary } from '@/api/redengine'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()
const loading = ref(false)
const loadError = ref('')

const emptySummary = () => ({
  mode: '',
  todoCount: 0,
  todoItems: [],
  todoDisplayItems: [],
  organizationTasks: [],
  taskOverviewCount: 0,
  organizationName: '--',
  organizationScore: null,
  organizationRank: null
})

const summary = ref(emptySummary())

function firstDefined(...values) {
  return values.find((value) => value !== null && value !== undefined && value !== '') ?? null
}

function listValue(value) {
  return Array.isArray(value) ? value : []
}

function normalizeSummary(payload) {
  const source = payload && typeof payload === 'object' ? payload : {}
  const taskOverview = source.taskOverview || source.tasks || {}
  const rows = listValue(firstDefined(
    taskOverview.todoItems,
    taskOverview.todos,
    taskOverview.pendingTasks,
    source.todoItems,
    source.todos,
    source.pendingTasks,
    []
  ))
  const organizationTasks = listValue(firstDefined(source.organizationTasks, taskOverview.organizationTasks, []))
    .map((row, index) => ({
      id: firstDefined(row.taskId, row.id, `task-${index}`),
      title: firstDefined(row.title, row.taskTitle, row.name, '--'),
      taskNatureLabel: firstDefined(row.taskNatureLabel, row.taskNature, '--'),
      endTime: firstDefined(row.currentWindowEndAt, row.endTime, row.taskEndTime, '--')
    }))
  const branchRankings = listValue(firstDefined(source.branchRankings, source.rankings, []))
    .map((row, index) => ({
      id: firstDefined(row.branchId, row.id, `branch-${index}`),
      title: firstDefined(row.title, row.branchName, '--'),
      branchName: firstDefined(row.branchName, row.branch, row.orgName, '--'),
      score: firstDefined(row.score, row.finalScore),
      rank: firstDefined(row.rank, row.ranking),
      time: firstDefined(row.quarter, '--')
    }))
  const organization = source.organization || source.org || {}

  return {
    mode: firstDefined(source.mode, ''),
    todoCount: Number(firstDefined(
      taskOverview.todoCount,
      taskOverview.pendingCount,
      source.todoCount,
      source.pendingCount,
      rows.length
    ) ?? 0),
    todoItems: rows.map((row, index) => ({
      id: firstDefined(row.id, row.taskId, row.assignmentId, `todo-${index}`),
      taskId: firstDefined(row.taskId),
      taskInstanceId: firstDefined(row.taskInstanceId),
      assignmentId: firstDefined(row.assignmentId),
      title: firstDefined(row.taskTitle, row.title, row.taskName, row.name, '--'),
      branchName: firstDefined(row.branchName, row.branch, row.partyOrgName, row.organizationName, '--'),
      score: firstDefined(row.score, row.finalScore, row.organizationScore),
      rank: firstDefined(row.rank, row.ranking, row.organizationRank),
      statusLabel: firstDefined(row.statusLabel, row.status, '--'),
      workflowStatus: firstDefined(
        row.submission?.status,
        row.currentSubmission?.status,
        row.submissionStatus,
        row.status,
        row.assignmentStatus
      ),
      time: firstDefined(row.windowEndAt, row.dueTime, row.deadline, row.time, row.remainingTime, '--')
    })),
    todoDisplayItems: branchRankings,
    organizationTasks,
    taskOverviewCount: Number(firstDefined(
      source.organizationTaskCount,
      taskOverview.organizationTaskCount,
      organizationTasks.length,
      source.todoCount,
      rows.length
    ) ?? 0),
    organizationName: firstDefined(
      source.branchName,
      source.organizationName,
      source.orgName,
      organization.name,
      organization.orgName,
      organization.branchName,
      '--'
    ),
    organizationScore: firstDefined(
      source.branchScore,
      source.institutionScore,
      source.organizationScore,
      source.orgScore,
      organization.score,
      organization.finalScore
    ),
    organizationRank: firstDefined(
      source.branchRank,
      source.institutionRank,
      source.organizationRank,
      source.orgRank,
      organization.rank,
      organization.ranking
    )
  }
}

function hasRole(...codes) {
  return typeof userStore.hasRoleCode === 'function' && userStore.hasRoleCode(...codes)
}

// 组织首页模式只接受后端实际角色：系统管理员或组织审核员。
// R_RE_ORGADM 不是当前角色常量，不能让历史别名改变页面数据分支。
const isOrganizationRole = computed(() => hasRole('SYS_ADMIN', 'R_RE_ORGREV'))
const isOrganizationView = computed(() => {
  const mode = String(summary.value.mode || '').toUpperCase()
  if (mode === 'ORGANIZATION') return true
  if (mode === 'INSTITUTION') return false
  return isOrganizationRole.value
})
const showOrgMetrics = computed(() => !isOrganizationView.value && hasRole('R_RE_SECR', 'R_RE_REPORT', 'R_RE_BRREV'))

const taskOverviewCount = computed(() => isOrganizationView.value
  ? summary.value.taskOverviewCount
  : summary.value.todoCount)
const todoDisplayItems = computed(() => {
  if (!isOrganizationView.value) return summary.value.todoItems
  // 组织审核员的真实待办来自后端 todoItems；旧接口未返回待办时才保留排名展示兼容。
  return summary.value.todoItems.length ? summary.value.todoItems : summary.value.todoDisplayItems
})

function displayValue(value) {
  return value === null || value === undefined || value === '' ? '--' : value
}

function todoRoute(item) {
  if (!item || isOrganizationView.value && !item.assignmentId) return null
  const query = { tab: 'pending' }
  for (const key of ['taskId', 'taskInstanceId', 'assignmentId']) {
    const value = item[key]
    if (value !== undefined && value !== null && value !== '') query[key] = value
  }
  if (item.workflowStatus) query.status = item.workflowStatus

  if (hasRole('R_RE_ORGREV', 'SYS_ADMIN')) return { path: '/redengine/review', query }
  if (hasRole('R_RE_SECR')) return { path: '/redengine/branch-review', query }
  if (hasRole('R_RE_REPORT')) return { path: '/redengine/records', query }
  return null
}

function openTodo(item) {
  const target = todoRoute(item)
  if (target) router?.push(target)
}

async function loadSummary() {
  loading.value = true
  loadError.value = ''
  try {
    const response = await getHomeSummary()
    summary.value = normalizeSummary(response)
  } catch {
    summary.value = emptySummary()
    loadError.value = '工作台数据加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(loadSummary)
</script>

<style scoped lang="scss">
.dashboard-container {
  padding: 0;

  .page-title {
    font-size: 28px;
    font-weight: 700;
    margin-bottom: 24px;
    color: #1e293b;
  }

  .load-error {
    margin-bottom: 16px;
    padding: 10px 14px;
    color: #991b1b;
    background: #fef2f2;
    border: 1px solid #fecaca;
    border-radius: 6px;
    font-size: 13px;
  }

  .stat-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
    margin-bottom: 24px;
  }

  .stat-card {
    border-radius: 8px;
    padding: 20px;
    transition: box-shadow 0.2s;
    background: #fff;
    border-top: 3px solid #dc2626;

    &:hover {
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
    }

    .stat-label {
      font-size: 12px;
      color: #64748b;
      margin-bottom: 8px;
    }

    .stat-value {
      font-size: 32px;
      font-weight: 700;
      line-height: 1.2;
      margin-bottom: 4px;
      color: #dc2626;
    }

    .stat-sub {
      font-size: 12px;
      color: #94a3b8;
    }
  }

  .org-score-stat {
    background: #fee2e2;
    border-top-color: #dc2626;
  }

  .org-rank-stat {
    background: #fef9c3;
    border-top-color: #ca8a04;

    .stat-value {
      color: #ca8a04;
    }
  }

  .section-card {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  }

  .section-title {
    font-size: 16px;
    font-weight: 600;
    padding-left: 12px;
    margin-bottom: 16px;
    border-left: 3px solid #dc2626;
    color: #dc2626;
  }

  .todo-empty {
    color: #94a3b8;
    font-size: 13px;
    padding: 12px 0;
  }

  .todo-list {
    list-style: none;
    padding: 0;
    margin: 0;
  }

  .todo-item {
    display: flex;
    align-items: center;
    padding: 10px 0;
    border-bottom: 1px solid #f1f5f9;
    gap: 12px;

    &.is-actionable {
      cursor: pointer;
    }

    &.is-actionable:focus-visible {
      outline: 2px solid #2563eb;
      outline-offset: 2px;
    }

    &:last-child {
      border-bottom: none;
    }
  }

  .todo-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #dc2626;
    flex-shrink: 0;
  }

  .todo-text {
    flex: 1;
    font-size: 14px;
    color: #334155;
  }

  .todo-branch,
  .todo-score,
  .todo-rank,
  .todo-time {
    font-size: 12px;
    color: #64748b;
    flex-shrink: 0;
  }

  .todo-score,
  .todo-rank {
    color: #991b1b;
  }
}

@media (max-width: 1000px) {
  .dashboard-container {
    .stat-grid {
      grid-template-columns: repeat(2, 1fr);
    }

    .todo-item {
      align-items: flex-start;
      flex-wrap: wrap;

      .todo-text {
        min-width: calc(100% - 24px);
      }
    }
  }
}

@media (max-width: 768px) {
  .dashboard-container {
    .stat-grid {
      grid-template-columns: 1fr;
    }
  }
}
</style>
