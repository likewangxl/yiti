<template>
  <div class="records-container">
    <h2 class="page-title">任务处理</h2>

    <FileIntegrationNotice />

    <div v-if="loadError" class="load-error" role="alert">{{ loadError }}</div>

    <div class="stat-bar">
      <div
        v-for="tab in tabs"
        :key="tab.value"
        :class="['stat-item', 'stat-chip', `${tab.value}-stat`, `${tab.value}-chip`, { 'is-active': activeTab === tab.value }]"
        role="button"
        tabindex="0"
        :aria-pressed="activeTab === tab.value"
        @click="changeTab(tab.value)"
        @keydown.enter.prevent.self="changeTab(tab.value)"
        @keydown.space.prevent.self="changeTab(tab.value)"
      >
        <span class="stat-num"><strong>{{ countByStatus(tab.value) }}</strong></span>
        <span class="stat-label">{{ tab.label }}</span>
      </div>
    </div>

    <div class="filter-row">
      <el-radio-group v-model="activeTab" size="small" @change="handleTabChange">
        <el-radio-button label="pending">待处理</el-radio-button>
        <el-radio-button label="reviewing">审核中</el-radio-button>
        <el-radio-button label="passed">已通过</el-radio-button>
        <el-radio-button label="rejected">已驳回</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="query.title"
        clearable
        size="small"
        placeholder="任务标题"
        class="query-title"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="query.nature" clearable size="small" placeholder="任务性质" class="query-select">
        <el-option label="定时任务" value="PERIODIC" />
        <el-option label="临时任务" value="TEMPORARY" />
      </el-select>
      <el-select v-model="query.cycle" clearable size="small" placeholder="周期" class="query-select">
        <el-option v-for="option in cycleOptions" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <el-button type="primary" size="small" @click="handleSearch">查询</el-button>
      <el-button size="small" @click="handleReset">重置</el-button>
    </div>

    <div class="table-card" v-loading="loading">
      <el-table :data="filteredRecords" stripe style="width: 100%" :row-class-name="tableRowClass">
        <el-table-column label="维度" width="120">
          <template #default="{ row }">
            <span v-if="row.isFourDimension" class="dim-tag" :style="{ background: getDimColor(row.dimension) }">{{ getDimLabel(row.dimension) }}</span>
            <span v-else :class="['dim-tag', isPeriodicTaskNature(row.nature) ? 'general-tag' : 'temporary-tag']">{{ dimensionLabel(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="任务标题 / 考核项" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button class="title-link" link type="primary" data-test="task-title" @click="openRow(row)">
              {{ row.title || row.item || '—' }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="任务说明" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-for="(part, index) in descriptionParts(row.description)" :key="`${row.id || row.assignmentId}-${index}`">
              <a
                v-if="part.type === 'link'"
                :href="part.href"
                target="_blank"
                rel="noreferrer noopener"
                data-test="description-link"
              >{{ part.value }}</a>
              <span v-else>{{ part.value }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="任务性质" width="100">
          <template #default="{ row }">{{ taskNatureLabel(row.nature) }}</template>
        </el-table-column>
        <el-table-column label="定期周期" width="110">
          <template #default="{ row }">{{ row.isPeriodic ? cycleLabel(row.cycle) : '—' }}</template>
        </el-table-column>
        <el-table-column v-if="activeTab !== 'pending'" prop="submitter" label="提交人" width="110" />
        <el-table-column v-if="activeTab !== 'pending'" prop="date" label="提交日期" width="150" />
        <el-table-column v-if="showMaterialColumns" label="审核状态" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.isFourDimension" :class="['status-badge', row.status]">{{ statusLabel(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="showMaterialColumns" label="得分" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.isFourDimension" class="score-placeholder">—</span>
          </template>
        </el-table-column>
        <el-table-column v-if="showMaterialColumns" label="审核意见" min-width="150">
          <template #default="{ row }">
            <span v-if="row.isFourDimension" class="feedback-text">{{ row.feedback || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!filteredRecords.length && !loading && !loadError" description="暂无任务处理记录" />
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
// 报送员任务处理：四维材料与临时任务统一从 assignment 工作台分页读取。
// 旧材料行只作为服务端统一响应中的兼容形态归一化，不再额外请求 /re/submits/my。
// 审核状态、权限和 assignment 重提语义由后端状态机负责。
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listMyTaskAssignments } from '@/api/redengine'
import FileIntegrationNotice from '../components/FileIntegrationNotice.vue'
import {
  CYCLE_OPTIONS,
  buildWorkflowQuery,
  cycleLabel,
  isFourDimensionTask,
  linkifyDescription,
  normalizeAssignmentPage
} from '../tasks/task-domain'
import {
  isPeriodicTaskNature,
  taskDimensionLabel,
  taskNatureLabel
} from './task-display'

const router = useRouter()
const cycleOptions = CYCLE_OPTIONS

const activeTab = ref('pending')
const tabs = [
  { value: 'pending', label: '待处理' },
  { value: 'reviewing', label: '审核中' },
  { value: 'passed', label: '已通过' },
  { value: 'rejected', label: '已驳回' }
]
const query = reactive({ title: '', nature: '', cycle: '' })
const appliedQuery = ref({})
const loading = ref(false)
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)
const records = ref([])
const loadError = ref('')
const statusTotals = ref({ pending: 0, reviewing: 0, passed: 0, rejected: 0 })
const reloadVersion = ref(0)
const TAB_VALUES = ['pending', 'reviewing', 'passed', 'rejected']

const STATUS_MAP = { 0: 'reviewing', 1: 'reviewing', 2: 'passed', 3: 'rejected' }
const DIM_LABEL = { dim1: '外联共建', dim2: '业务提升', dim3: '头雁先锋', dim4: '督导响应' }
const DIM_COLOR = { dim1: '#dc2626', dim2: '#2563eb', dim3: '#ca8a04', dim4: '#16a34a' }
const TASK_STATUS_MAP = {
  UNREPORTED: 'pending',
  TODO: 'pending',
  OVERDUE_UNREPORTED: 'pending',
  BRANCH_PENDING: 'reviewing',
  BRANCH_APPROVED: 'reviewing',
  ORG_PENDING: 'reviewing',
  APPROVED: 'passed',
  COMPLETED: 'passed',
  REJECTED: 'rejected',
  REJECTED_BY_BRANCH: 'rejected',
  REJECTED_BY_ORG: 'rejected'
}
function statusLabel(status) {
  return { pending: '待处理', reviewing: '审核中', passed: '已通过', rejected: '已驳回' }[status] || '—'
}

function mapTaskStatus(status) {
  return TASK_STATUS_MAP[String(status || '').toUpperCase()] || 'reviewing'
}

function normalizeMaterialRow(row = {}) {
  const status = STATUS_MAP[row.status] || 'reviewing'
  return {
    ...row,
    id: row.id,
    source: 'material',
    taskId: row.taskId,
    assignmentId: row.assignmentId,
    legacyReviewId: row.legacyReviewId ?? row.submitId ?? row.reviewId,
    periodKey: row.periodKey,
    detailItemCode: row.detailItemCode ?? row.itemCode,
    title: row.taskTitle || row.title || '',
    dimension: row.dimension,
    item: row.itemName ? `${row.itemCode ? `${row.itemCode} ` : ''}${row.itemName}` : (row.itemCode || '—'),
    description: row.description || row.projectName || row.itemName || '',
    nature: 'PERIODIC',
    cycle: row.cycleType || row.cycle || '',
    isPeriodic: true,
    isFourDimension: true,
    submitter: row.submitterId || '—',
    date: row.submitDate || '—',
    status,
    feedback: row.reviewFeedback || ''
  }
}

function isLegacyMaterialRow(row = {}) {
  return row.source === 'material'
    || (!row.taskId && !row.taskTitle && !row.businessType && !row.taskNature && !row.nature
      && (row.dimension || row.itemCode))
}

function normalizeTaskRow(row = {}) {
  if (isLegacyMaterialRow(row)) return normalizeMaterialRow(row)
  const task = row.task || row.taskDefinition || {}
  const assignment = { ...task, ...row }
  const normalized = normalizeAssignmentPage([assignment]).records[0]
  const taskNature = assignment.taskNature || assignment.nature
  const fourDimension = isFourDimensionTask(assignment)
  const rawStatus = assignment.submission?.status
    || assignment.currentSubmission?.status
    || assignment.submissionStatus
    || assignment.status
    || assignment.stage
  return {
    ...assignment,
    ...normalized,
    source: 'task',
    id: assignment.assignmentId || assignment.id,
    taskId: assignment.taskId || task.taskId || task.id,
    assignmentId: assignment.assignmentId || assignment.id,
    title: assignment.taskTitle || assignment.title || task.title || '—',
    item: assignment.taskTitle || assignment.title || task.title || '—',
    description: assignment.taskDescription || assignment.description || task.description || '',
    nature: taskNature || '',
    cycle: assignment.cycleType || assignment.cycle || task.cycleType || task.cycle || '',
    isPeriodic: isPeriodicTaskNature(taskNature),
    isFourDimension: fourDimension,
    submitter: normalized.submitterName || assignment.submitterName || '—',
    date: normalized.submittedAt || assignment.submittedAt || '—',
    status: normalized.isUnreported ? 'pending' : mapTaskStatus(rawStatus),
    workflowStatus: String(rawStatus || (normalized.isUnreported ? 'UNREPORTED' : '')).toUpperCase(),
    feedback: assignment.reviewFeedback
      || assignment.feedback
      || assignment.reviewOpinion
      || assignment.submission?.reviewFeedback
      || assignment.currentSubmission?.reviewFeedback
      || ''
  }
}

function taskQuery() {
  return buildWorkflowQuery(
    appliedQuery.value,
    activeTab.value,
    pageNo.value,
    pageSize.value
  )
}

async function loadTaskRows() {
  try {
    const result = await listMyTaskAssignments(taskQuery())
    const page = normalizeAssignmentPage(result)
    const rows = page.records.map(normalizeTaskRow)
    return { rows, total: page.total, error: '' }
  } catch {
    return { rows: [], total: 0, error: '任务数据加载失败' }
  }
}

async function loadOtherStatusTotals(filters, currentTab) {
  const tabs = TAB_VALUES.filter((tab) => tab !== currentTab)
  const entries = await Promise.all(tabs.map(async (tab) => {
    try {
      const result = await listMyTaskAssignments(buildWorkflowQuery(filters, tab, 1, 1))
      return [tab, normalizeAssignmentPage(result).total]
    } catch {
      return [tab, null]
    }
  }))
  return entries.reduce((totals, [tab, value]) => {
    if (value !== null && Number.isFinite(Number(value))) totals[tab] = Number(value)
    return totals
  }, {})
}

async function reload() {
  const requestId = ++reloadVersion.value
  const requestedTab = activeTab.value
  const filters = { ...appliedQuery.value }
  loading.value = true
  loadError.value = ''
  try {
    // 当前页先读取，保证“一次请求”的 mock/真实响应对应当前列表；其余页签只取
    // pageSize=1 的 total。所有请求带同一份筛选快照，旧请求返回时由版本号丢弃。
    const rowsPromise = loadTaskRows()
    const totalsPromise = loadOtherStatusTotals(filters, requestedTab)
    const [result, otherTotals] = await Promise.all([rowsPromise, totalsPromise])
    if (requestId !== reloadVersion.value) return
    records.value = result.rows
    total.value = result.total
    statusTotals.value = {
      ...statusTotals.value,
      [requestedTab]: result.total,
      ...otherTotals
    }
    if (result.error) loadError.value = result.error
  } finally {
    if (requestId === reloadVersion.value) loading.value = false
  }
}

async function handleSearch() {
  appliedQuery.value = { ...query }
  pageNo.value = 1
  await reload()
}

async function handleReset() {
  Object.assign(query, { title: '', nature: '', cycle: '' })
  appliedQuery.value = {}
  pageNo.value = 1
  await reload()
}

async function handleTabChange() {
  pageNo.value = 1
  await reload()
}

async function changeTab(tab) {
  if (!TAB_VALUES.includes(tab)) return
  activeTab.value = tab
  await handleTabChange()
}

async function handlePageChange(nextPage) {
  pageNo.value = nextPage
  await reload()
}

async function handleSizeChange(nextSize) {
  pageSize.value = nextSize
  pageNo.value = 1
  await reload()
}

function openRow(row) {
  if (!row?.taskId && !row?.id) return
  const taskId = row.taskId || row.id
  const assignmentId = row.assignmentId
  if (row.source === 'material' || row.isFourDimension || isFourDimensionTask(row)) {
    const query = { taskId }
    if (assignmentId !== undefined && assignmentId !== null) query.assignmentId = assignmentId
    for (const key of ['taskInstanceId', 'periodKey', 'detailItemCode']) {
      const value = row[key] ?? (key === 'detailItemCode' ? row.itemCode : undefined)
      if (value !== undefined && value !== null && value !== '') query[key] = value
    }
    router.push({ path: '/redengine/report', query })
    return
  }
  const query = { taskId, tab: activeTab.value }
  if (assignmentId !== undefined && assignmentId !== null && assignmentId !== '') query.assignmentId = assignmentId
  const workflowStatus = row.workflowStatus || row.submissionStatus || row.status
  if (workflowStatus) query.status = String(workflowStatus).toUpperCase()
  for (const key of ['taskInstanceId', 'periodKey', 'detailItemCode']) {
    const value = row[key] ?? (key === 'detailItemCode' ? row.itemCode : undefined)
    if (value !== undefined && value !== null && value !== '') query[key] = value
  }
  router.push({ path: '/redengine/task-entry', query })
}

function countByStatus(status) {
  return Number(statusTotals.value[status] ?? 0)
}

const filteredRecords = computed(() => records.value)
const showMaterialColumns = computed(() => activeTab.value !== 'pending' && records.value.some((row) => row.isFourDimension))
const descriptionParts = (description) => linkifyDescription(description)
const getDimLabel = (dim) => DIM_LABEL[dim] || dim || '—'
const getDimColor = (dim) => DIM_COLOR[dim] || '#64748b'
const dimensionLabel = (row) => row?.isFourDimension ? getDimLabel(row.dimension) : taskDimensionLabel(row?.nature)
const tableRowClass = ({ row }) => (row.status === 'rejected' ? 'row-rejected' : '')

onMounted(reload)

defineExpose({
  activeTab,
  appliedQuery,
  changeTab,
  countByStatus,
  cycleLabel,
  descriptionParts,
  dimensionLabel,
  filteredRecords,
  handlePageChange,
  handleReset,
  handleSearch,
  handleSizeChange,
  handleTabChange,
  loadTaskRows,
  loadError,
  normalizeTaskRow,
  openRow,
  pageNo,
  pageSize,
  query,
  records,
  reload,
  showMaterialColumns,
  statusTotals,
  statusLabel,
  taskNatureLabel,
  total
})
</script>

<style scoped lang="scss">
.records-container { padding: 0; }
.page-title { margin: 0 0 16px; color: #1e293b; font-size: 22px; font-weight: 700; }

.load-error {
  margin-bottom: 16px;
  padding: 10px 14px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #991b1b;
  background: #fef2f2;
  font-size: 13px;
}

.stat-bar { display: flex; flex-wrap: wrap; gap: 16px; margin-bottom: 20px; }
.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 8px;
  color: #475569;
  font-size: 13px;
  cursor: pointer;
  user-select: none;

  .stat-num {
    color: #1e293b;
    font-size: 24px;
    font-weight: 700;
  }

  .stat-label { line-height: 1.2; }

  strong { font-weight: inherit; }
}
.stat-chip {
  border-radius: 8px;
}
.pending-stat, .pending-chip { background: #fef9c3; .stat-num { color: #ca8a04; } }
.reviewing-stat, .reviewing-chip { background: #dbeafe; .stat-num { color: #2563eb; } }
.passed-stat, .passed-chip { background: #dcfce7; .stat-num { color: #16a34a; } }
.rejected-stat, .rejected-chip { background: #fee2e2; .stat-num { color: #dc2626; } }
.stat-item.is-active { box-shadow: inset 0 0 0 1px currentColor; }
.stat-item:focus-visible { outline: 2px solid #2563eb; outline-offset: 2px; }

.filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}
.query-title { width: 190px; }
.query-select { width: 132px; }

.table-card {
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, .04);
}
.title-link {
  max-width: 100%;
  padding: 0;
  overflow: hidden;
  font-size: 13px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dim-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 4px;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}
.general-tag { background: #0f766e; }
.temporary-tag { background: #7c3aed; }
.status-badge {
  display: inline-block;
  padding: 3px 9px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.status-badge.reviewing { color: #1d4ed8; background: #dbeafe; }
.status-badge.passed { color: #166534; background: #dcfce7; }
.status-badge.rejected { color: #b91c1c; background: #fee2e2; }
.feedback-text { color: #64748b; font-size: 12px; }
.score-placeholder { color: #cbd5e1; }
.pager { display: flex; justify-content: flex-end; padding: 12px 14px; }

:deep(.el-table) {
  .el-table__header th { color: #475569; background: #f8fafc; font-size: 13px; font-weight: 600; }
  .el-table__row td { padding: 10px 0; color: #334155; font-size: 13px; }
}
:deep(.row-rejected) { background: #fef2f2 !important; }
</style>
