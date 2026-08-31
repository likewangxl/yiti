<template>
  <div class="records-container">
    <h2 class="page-title">上报信息</h2>

    <div class="stat-bar">
      <div class="stat-chip pending-chip">待处理 <strong>{{ countByStatus('pending') }}</strong></div>
      <div class="stat-chip reviewing-chip">审核中 <strong>{{ countByStatus('reviewing') }}</strong></div>
      <div class="stat-chip passed-chip">已通过 <strong>{{ countByStatus('passed') }}</strong></div>
      <div class="stat-chip rejected-chip">已驳回 <strong>{{ countByStatus('rejected') }}</strong></div>
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
            <span v-if="row.isTemporary" class="dim-tag temporary-tag">临时任务</span>
            <span v-else class="dim-tag" :style="{ background: getDimColor(row.dimension) }">{{ getDimLabel(row.dimension) }}</span>
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
          <template #default="{ row }">{{ natureLabel(row.nature) }}</template>
        </el-table-column>
        <el-table-column label="定期周期" width="110">
          <template #default="{ row }">{{ row.isPeriodic ? cycleLabel(row.cycle) : '—' }}</template>
        </el-table-column>
        <el-table-column v-if="activeTab !== 'pending'" prop="submitter" label="提交人" width="110" />
        <el-table-column v-if="activeTab !== 'pending'" prop="date" label="提交日期" width="150" />
        <el-table-column v-if="showMaterialColumns" label="审核状态" width="100" align="center">
          <template #default="{ row }">
            <span v-if="!row.isTemporary" :class="['status-badge', row.status]">{{ statusLabel(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="showMaterialColumns" label="得分" width="80" align="center">
          <template #default="{ row }">
            <span v-if="!row.isTemporary" class="score-placeholder">—</span>
          </template>
        </el-table-column>
        <el-table-column v-if="showMaterialColumns" label="审核意见" min-width="150">
          <template #default="{ row }">
            <span v-if="!row.isTemporary" class="feedback-text">{{ row.feedback || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="showTemporaryFeedback" label="驳回意见" min-width="160">
          <template #default="{ row }">
            <span v-if="row.isTemporary" class="feedback-text temporary-feedback" data-test="temporary-feedback">{{ row.feedback || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!filteredRecords.length && !loading" description="暂无上报信息" />
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
// 报送员上报信息：四维材料继续复用原 /re/submits/my 数据，任务协同记录来自 assignment。
// 页面只负责组合两类记录；审核状态、权限和 assignment 重提语义由后端状态机负责。
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMySubmits, listMyTaskAssignments } from '@/api/redengine'
import {
  CYCLE_OPTIONS,
  buildTaskQuery,
  cycleLabel,
  isFourDimensionTask,
  isPeriodicNature,
  linkifyDescription,
  natureLabel,
  normalizeAssignmentPage
} from '../tasks/task-domain'

const router = useRouter()
const cycleOptions = CYCLE_OPTIONS

const activeTab = ref('pending')
const query = reactive({ title: '', nature: '', cycle: '' })
const appliedQuery = ref({})
const loading = ref(false)
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)
const records = ref([])

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
const TASK_STATUS_QUERY = {
  pending: 'UNREPORTED',
  // 审核中同时包含支部审核和组织审核两个状态，不能压成一个不存在的枚举值；
  // 省略服务端状态过滤后在页面按状态机结果收敛，避免漏掉 BRANCH_PENDING。
  reviewing: undefined,
  passed: 'APPROVED',
  // 驳回包含 REJECTED_BY_BRANCH、REJECTED_BY_ORG 两种后端状态，页面统一映射为已驳回。
  rejected: undefined
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
    title: row.taskTitle || row.title || '',
    dimension: row.dimension,
    item: row.itemName ? `${row.itemCode ? `${row.itemCode} ` : ''}${row.itemName}` : (row.itemCode || '—'),
    description: row.description || row.projectName || row.itemName || '',
    nature: 'PERIODIC',
    cycle: row.cycleType || row.cycle || '',
    isPeriodic: true,
    isTemporary: false,
    isFourDimension: true,
    submitter: row.submitterId || '—',
    date: row.submitDate || '—',
    status,
    feedback: row.reviewFeedback || ''
  }
}

function normalizeTaskRow(row = {}) {
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
    isPeriodic: isPeriodicNature(taskNature),
    isTemporary: !fourDimension,
    isFourDimension: fourDimension,
    submitter: normalized.submitterName || assignment.submitterName || '—',
    date: normalized.submittedAt || assignment.submittedAt || '—',
    status: normalized.isUnreported ? 'pending' : mapTaskStatus(rawStatus),
    feedback: assignment.reviewFeedback
      || assignment.feedback
      || assignment.reviewOpinion
      || assignment.submission?.reviewFeedback
      || assignment.currentSubmission?.reviewFeedback
      || ''
  }
}

function taskQuery() {
  return buildTaskQuery({
    ...appliedQuery.value,
    status: TASK_STATUS_QUERY[activeTab.value]
  }, pageNo.value, pageSize.value)
}

function matchesTab(row) {
  return row.status === activeTab.value
}

async function loadTaskRows() {
  try {
    const result = await listMyTaskAssignments(taskQuery())
    const rows = normalizeAssignmentPage(result).records.map(normalizeTaskRow).filter(matchesTab)
    return { rows, total: Number(result?.total ?? rows.length) }
  } catch {
    return { rows: [], total: 0 }
  }
}

async function loadMaterialRows() {
  if (activeTab.value === 'pending') return { rows: [], total: 0 }
  try {
    const result = await getMySubmits(pageNo.value, pageSize.value)
    const rawRows = result?.records || (Array.isArray(result) ? result : [])
    const rows = rawRows.map(normalizeMaterialRow).filter((row) => {
      if (!matchesTab(row)) return false
      if (appliedQuery.value.title && !`${row.title} ${row.item}`.includes(appliedQuery.value.title)) return false
      if (appliedQuery.value.nature && appliedQuery.value.nature !== 'PERIODIC') return false
      if (appliedQuery.value.cycle && row.cycle !== appliedQuery.value.cycle) return false
      return true
    })
    return { rows, total: Number(result?.total ?? rows.length) }
  } catch {
    return { rows: [], total: 0 }
  }
}

async function reload() {
  loading.value = true
  try {
    const [taskResult, materialResult] = await Promise.all([loadTaskRows(), loadMaterialRows()])
    records.value = [...materialResult.rows, ...taskResult.rows]
    total.value = materialResult.total + taskResult.total
  } finally {
    loading.value = false
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
    router.push({ path: '/redengine/report', query })
    return
  }
  const location = router.resolve({ path: '/redengine/task-entry', query: { taskId, assignmentId } })
  window.open(location.href, '_blank', 'noopener,noreferrer')
}

function countByStatus(status) {
  return records.value.filter((row) => row.status === status).length
}

const filteredRecords = computed(() => records.value)
const showMaterialColumns = computed(() => activeTab.value !== 'pending' && records.value.some((row) => !row.isTemporary))
const showTemporaryFeedback = computed(() => activeTab.value === 'rejected' && records.value.some((row) => row.isTemporary))
const descriptionParts = (description) => linkifyDescription(description)
const getDimLabel = (dim) => DIM_LABEL[dim] || dim || '—'
const getDimColor = (dim) => DIM_COLOR[dim] || '#64748b'
const tableRowClass = ({ row }) => (row.status === 'rejected' ? 'row-rejected' : '')

onMounted(reload)

defineExpose({
  activeTab,
  appliedQuery,
  countByStatus,
  descriptionParts,
  filteredRecords,
  handlePageChange,
  handleReset,
  handleSearch,
  handleSizeChange,
  handleTabChange,
  loadTaskRows,
  normalizeTaskRow,
  openRow,
  pageNo,
  pageSize,
  query,
  records,
  reload,
  showMaterialColumns,
  showTemporaryFeedback,
  statusLabel,
  total
})
</script>

<style scoped lang="scss">
.records-container { padding: 0; }
.page-title { margin: 0 0 16px; color: #1e293b; font-size: 22px; font-weight: 700; }

.stat-bar { display: flex; gap: 12px; margin-bottom: 16px; }
.stat-chip {
  padding: 6px 14px;
  border-radius: 20px;
  color: #475569;
  font-size: 13px;

  strong { margin-left: 4px; color: #1e293b; }
}
.pending-chip { background: #fef9c3; strong { color: #92400e; } }
.reviewing-chip { background: #dbeafe; strong { color: #1d4ed8; } }
.passed-chip { background: #dcfce7; strong { color: #166534; } }
.rejected-chip { background: #fee2e2; strong { color: #b91c1c; } }

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
