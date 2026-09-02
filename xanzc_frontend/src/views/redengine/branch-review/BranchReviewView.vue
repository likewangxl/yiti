<template>
  <div class="branch-review-container">
    <h2 class="page-title">支部审核工作台</h2>
    <p class="page-desc">处理支部报送员提交的任务；审核通过后可单独提交至组织审核</p>

    <FileIntegrationNotice />

    <div v-if="loadError" class="load-error" role="alert">{{ loadError }}</div>

    <div class="stat-bar">
      <div
        v-for="tab in tabs"
        :key="tab.value"
        :class="['stat-item', `${tab.value}-stat`, { 'is-active': activeTab === tab.value }]"
        role="button"
        tabindex="0"
        :aria-pressed="activeTab === tab.value"
        @click="changeTab(tab.value)"
        @keydown.enter.prevent.self="changeTab(tab.value)"
        @keydown.space.prevent.self="changeTab(tab.value)"
      >
        <span class="stat-num">{{ countByStatus(tab.value) }}</span>
        <span class="stat-label">{{ tab.label }}</span>
      </div>
    </div>

    <div class="filter-bar">
      <el-radio-group v-model="activeTab" size="small" @change="handleTabChange">
        <el-radio-button v-for="tab in tabs" :key="tab.value" :label="tab.value">{{ tab.label }}</el-radio-button>
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

    <div class="review-list" v-loading="loading">
      <div v-if="filteredItems.length === 0 && !loadError" class="empty-state">暂无{{ activeTabLabel }}记录</div>

      <div
        v-for="item in filteredItems"
        :key="itemKey(item)"
        :class="['review-card', { selected: selectedItem?.id === item.id }]"
        role="button"
        tabindex="0"
        :aria-pressed="selectedItem?.id === item.id"
        @click="selectItem(item)"
        @keydown.enter.prevent.self="selectItem(item)"
        @keydown.space.prevent.self="selectItem(item)"
      >
        <div class="card-header">
          <div class="card-left">
            <span class="dim-badge" :style="{ background: getDimColor(item.dim) }">{{ item.dim }}</span>
            <span class="item-name">{{ item.itemName }}</span>
          </div>
          <div class="card-right">
            <el-tag
              :type="item.status === 'passed' ? 'success' : item.status === 'rejected' ? 'danger' : item.status === 'reviewing' ? 'info' : 'warning'"
              size="small"
            >{{ statusLabel(item.status) }}</el-tag>
          </div>
        </div>

        <div class="card-body">
          <div class="meta-row">
            <span class="meta-item" v-if="item.branch">党支部：{{ item.branch }}</span>
            <span class="meta-item">📋 提交人：{{ item.submitter }}</span>
            <span class="meta-item">📅 {{ item.submitDate }}</span>
            <span v-if="item.source === 'task'" class="meta-item">任务性质：{{ taskNatureLabel(item.nature) }}</span>
            <span v-if="item.source === 'task' && item.isPeriodic && item.cycle" class="meta-item">周期：{{ cycleLabel(item.cycle) }}</span>
          </div>
          <div v-if="item.description" class="description">
            <span class="field-label">任务说明：</span>
            <template v-for="(part, index) in descriptionParts(item.description)" :key="`${itemKey(item)}-description-${index}`">
              <a
                v-if="part.type === 'link'"
                :href="part.href"
                target="_blank"
                rel="noreferrer noopener"
                @click.stop
              >{{ part.value }}</a>
              <span v-else>{{ part.value }}</span>
            </template>
          </div>
          <div v-if="item.summary" class="summary">
            <span class="field-label">本次填报内容：</span>
            <template v-for="(part, index) in descriptionParts(item.summary)" :key="`${itemKey(item)}-summary-${index}`">
              <a
                v-if="part.type === 'link'"
                :href="part.href"
                target="_blank"
                rel="noreferrer noopener"
                @click.stop
              >{{ part.value }}</a>
              <span v-else>{{ part.value }}</span>
            </template>
          </div>

          <div v-if="item.files && item.files.length > 0" class="files-row">
            <template v-for="(file, idx) in item.files" :key="`${itemKey(item)}-file-${idx}`">
              <el-button
                v-if="item.source === 'task'"
                link
                size="small"
                class="file-tag"
                @click.stop="downloadAttachment(item, file)"
              >{{ getFileIcon(file) }} {{ fileName(file) }}</el-button>
              <span v-else class="file-tag">{{ getFileIcon(file) }} {{ fileName(file) }}</span>
            </template>
          </div>

          <div v-if="item.formData" class="form-data">
            <div v-for="(val, key) in item.formData" :key="key" class="data-row">
              <span class="data-key">{{ key }}：</span>
              <span class="data-val">{{ val }}</span>
            </div>
          </div>
        </div>

        <div v-if="item.status === 'pending'" class="card-actions" @click.stop>
          <div class="action-note">
            <el-input v-model="item.reviewNote" size="small" placeholder="审核意见（选填）" style="width: 300px;" />
          </div>
          <div class="action-btns">
            <el-button
              v-if="!showSubmitToOrg(item)"
              type="success"
              size="small"
              :loading="item.acting"
              @click="handleApprove(item)"
            >✅ 审核通过</el-button>
            <el-button
              v-else
              type="primary"
              size="small"
              :loading="item.acting"
              @click="handleSubmitToOrg(item)"
            >提交至组织审核</el-button>
            <el-button type="danger" size="small" plain :loading="item.acting" @click="openReject(item)">↩ 驳回</el-button>
          </div>
        </div>

        <div v-if="item.status === 'reviewing'" class="result-bar reviewing-bar">已提交至组织审核，等待组织审核员处理</div>
        <div v-if="item.status === 'passed'" class="result-bar approved-bar">
          ✅ 已通过
          <span v-if="item.reviewNote" class="result-note">备注：{{ item.reviewNote }}</span>
        </div>
        <div v-if="item.status === 'rejected'" class="result-bar rejected-bar">
          ↩ 已驳回，已退回报送员
          <span v-if="item.reviewNote" class="result-note">原因：{{ item.reviewNote }}</span>
        </div>
      </div>
    </div>

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

    <el-dialog v-model="showRejectModal" title="填写驳回意见" width="500px">
      <div class="reject-info">
        <div class="reject-item-name">{{ selectedItem?.itemName }}</div>
        <div class="reject-branch">党支部：{{ selectedItem?.branch || '—' }}</div>
      </div>
      <el-input v-model="rejectReason" type="textarea" :rows="4" placeholder="请输入驳回意见" />
      <template #footer>
        <el-button @click="showRejectModal = false">取消</el-button>
        <el-button type="danger" :loading="acting" @click="handleConfirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  approveBranchTask,
  approveSubmit,
  downloadTaskAttachment,
  getBranchTaskReview,
  getReviewPreview,
  listBranchTaskReviews,
  rejectBranchTask,
  rejectSubmit,
  submitBranchTaskToOrg
} from '@/api/redengine'
import {
  CYCLE_OPTIONS,
  buildWorkflowQuery,
  cycleLabel,
  isFourDimensionTask,
  linkifyDescription,
  normalizeAssignmentPage
} from '../tasks/task-domain'
import FileIntegrationNotice from '../components/FileIntegrationNotice.vue'
import { isPeriodicTaskNature, taskNatureLabel } from '../records/task-display'

const tabs = [
  { value: 'pending', label: '待处理' },
  { value: 'reviewing', label: '审核中' },
  { value: 'passed', label: '已通过' },
  { value: 'rejected', label: '已驳回' }
]
const cycleOptions = CYCLE_OPTIONS
const activeTab = ref('pending')
const query = reactive({ title: '', nature: '', cycle: '' })
const appliedQuery = ref({})
const loading = ref(false)
const acting = ref(false)
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const items = ref([])
const loadError = ref('')
const statusTotals = ref({ pending: 0, reviewing: 0, passed: 0, rejected: 0 })
const reloadVersion = ref(0)
const TAB_VALUES = ['pending', 'reviewing', 'passed', 'rejected']
const selectedItem = ref(null)
const showRejectModal = ref(false)
const rejectReason = ref('')

const DIM_LABEL = { dim1: '外联共建', dim2: '业务提升', dim3: '头雁先锋', dim4: '督导响应' }
const DIM_COLOR = { dim1: '#dc2626', dim2: '#2563eb', dim3: '#ca8a04', dim4: '#16a34a' }
const TASK_STATUS_MAP = {
  BRANCH_PENDING: 'pending',
  BRANCH_APPROVED: 'pending',
  ORG_PENDING: 'reviewing',
  APPROVED: 'passed',
  COMPLETED: 'passed',
  REJECTED_BY_BRANCH: 'rejected',
  REJECTED_BY_ORG: 'rejected',
  REJECTED: 'rejected'
}

const pendingItems = computed(() => items.value.filter((item) => item.status === 'pending'))
const reviewingItems = computed(() => items.value.filter((item) => item.status === 'reviewing'))
const passedItems = computed(() => items.value.filter((item) => item.status === 'passed'))
const rejectedItems = computed(() => items.value.filter((item) => item.status === 'rejected'))
// 队列接口已按页签在服务端完成过滤；这里不能再次按状态过滤，否则后端返回的 total
// 与页面展示行会失配，尤其是审核中包含多个工作流阶段时。
const filteredItems = computed(() => items.value)
const activeTabLabel = computed(() => tabs.find((tab) => tab.value === activeTab.value)?.label || '')

function parseFormData(raw) {
  if (!raw) return null
  if (typeof raw === 'object') return raw
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function parseFiles(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

function statusLabel(status) {
  return { pending: '待处理', reviewing: '审核中', passed: '已通过', rejected: '已驳回' }[status] || '—'
}

function legacyReviewId(item) {
  if (!item) return null
  return item.legacyReviewId ?? item.submitId ?? item.reviewId
    ?? (item.source === 'material' ? item.id : null)
}

function mapTaskStatus(status) {
  return TASK_STATUS_MAP[String(status || '').toUpperCase()] || 'reviewing'
}

function normalizeMaterialRow(row = {}) {
  const numericStatus = row.status
  const status = numericStatus === 2 || numericStatus === 'APPROVED' ? 'passed'
    : numericStatus === 3 || numericStatus === 'REJECTED' ? 'rejected' : 'pending'
  return {
    ...row,
    id: row.id,
    source: 'material',
    legacyReviewId: row.legacyReviewId ?? row.submitId ?? row.reviewId,
    assignmentId: row.assignmentId,
    taskId: row.taskId,
    periodKey: row.periodKey,
    detailItemCode: row.detailItemCode ?? row.itemCode,
    branch: row.orgName || (row.orgId != null ? `组织#${row.orgId}` : '—'),
    dim: DIM_LABEL[row.dimension] || row.dimension || '—',
    itemName: row.itemName ? `${row.itemCode ? `${row.itemCode} ` : ''}${row.itemName}` : (row.itemCode || '—'),
    submitter: row.submitterId || '—',
    submitDate: row.submitDate || '—',
    description: '',
    summary: row.projectName || row.itemName || '—',
    files: parseFiles(row.fileUrls),
    formData: parseFormData(row.formData),
    nature: 'PERIODIC',
    cycle: row.cycleType || row.cycle || '',
    isPeriodic: true,
    isTemporary: false,
    isTask: false,
    isFourDimension: true,
    status,
    reviewNote: row.reviewFeedback || row.feedback || '',
    branchApproved: false,
    acting: false
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
  const normalized = normalizeAssignmentPage([assignment]).records[0] || {}
  const taskNature = assignment.taskNature || assignment.nature || task.taskNature || task.nature || ''
  const fourDimension = isFourDimensionTask({ ...task, ...assignment })
  const isTask = !fourDimension
  const isPeriodic = isPeriodicTaskNature(taskNature)
  const rawStatus = assignment.submission?.status
    || assignment.currentSubmission?.status
    || assignment.submissionStatus
    || assignment.status
    || assignment.stage
  const status = normalized.isUnreported ? 'pending' : mapTaskStatus(rawStatus)
  return {
    ...assignment,
    ...normalized,
    id: assignment.assignmentId || assignment.id,
    source: 'task',
    taskId: assignment.taskId || task.taskId || task.id,
    assignmentId: assignment.assignmentId || assignment.id,
    branch: normalized.branchName || assignment.branchName || '—',
    dim: fourDimension ? (DIM_LABEL[assignment.dimension] || assignment.dimension || '四大维度材料上报') : taskNatureLabel(taskNature),
    itemName: assignment.taskTitle || assignment.title || task.title || '—',
    description: assignment.taskDescription || assignment.description || task.description || '',
    summary: normalized.content || assignment.content || assignment.formData || '',
    nature: taskNature,
    cycle: assignment.cycleType || assignment.cycle || task.cycleType || task.cycle || '',
    isPeriodic,
    isTemporary: !isPeriodic,
    isTask,
    isFourDimension: fourDimension,
    submitter: normalized.submitterName || assignment.submitterName || '—',
    submitDate: normalized.submittedAt || assignment.submittedAt || '—',
    files: normalized.files || [],
    formData: parseFormData(assignment.formData),
    status,
    reviewNote: assignment.reviewFeedback || assignment.feedback || assignment.reviewOpinion || '',
    branchApproved: String(rawStatus || '').toUpperCase() === 'BRANCH_APPROVED',
    acting: false
  }
}

function taskQuery() {
  return buildWorkflowQuery(appliedQuery.value, activeTab.value, pageNo.value, pageSize.value)
}

async function loadTaskRows() {
  if (typeof listBranchTaskReviews !== 'function') return { rows: [], total: 0, error: '任务审核队列不可用' }
  try {
    const result = await listBranchTaskReviews(taskQuery())
    const page = normalizeAssignmentPage(result)
    const rows = page.records.map(normalizeTaskRow)
    return { rows, total: page.total, error: '' }
  } catch {
    return { rows: [], total: 0, error: '任务审核队列加载失败' }
  }
}

async function loadOtherStatusTotals(filters, currentTab) {
  const tabsToLoad = TAB_VALUES.filter((tab) => tab !== currentTab)
  const entries = await Promise.all(tabsToLoad.map(async (tab) => {
    try {
      const result = await listBranchTaskReviews(buildWorkflowQuery(filters, tab, 1, 1))
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
    const rowsPromise = loadTaskRows()
    const totalsPromise = loadOtherStatusTotals(filters, requestedTab)
    const [result, otherTotals] = await Promise.all([rowsPromise, totalsPromise])
    if (requestId !== reloadVersion.value) return
    items.value = result.rows
    total.value = result.total
    statusTotals.value = {
      ...statusTotals.value,
      [requestedTab]: result.total,
      ...otherTotals
    }
    if (result.error) loadError.value = result.error
    if (selectedItem.value) {
      selectedItem.value = items.value.find((item) => item.id === selectedItem.value.id) || null
    }
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

function changeTab(tab) {
  activeTab.value = tab
  handleTabChange()
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

function itemKey(item) {
  return `${item.source || 'legacy'}-${item.id}`
}

async function selectItem(item) {
  selectedItem.value = item
  const reviewId = legacyReviewId(item)
  if (item.source === 'task' && item.isFourDimension && reviewId && typeof getReviewPreview === 'function') {
    try {
      const detail = await getReviewPreview(reviewId)
      if (detail) Object.assign(item, {
        formData: parseFormData(detail.formData),
        files: parseFiles(detail.fileUrls),
        summary: detail.content || item.summary
      })
    } catch (error) {
      ElMessage.error(error?.message || '加载详情失败')
    }
  } else if (item.source === 'task' && typeof getBranchTaskReview === 'function') {
    try {
      const detail = await getBranchTaskReview(item.assignmentId)
      if (detail) Object.assign(item, normalizeTaskRow({ ...item, ...detail }))
    } catch (error) {
      ElMessage.error(error?.message || '加载任务详情失败')
    }
  }
}

function showSubmitToOrg(item) {
  return item?.source === 'task' && !item.isFourDimension && item.status === 'pending' && item.branchApproved === true
}

async function handleApprove(item = selectedItem.value) {
  if (!item) return
  item.acting = true
  try {
    if (item.source === 'task' && !item.isFourDimension) {
      if (typeof approveBranchTask !== 'function') return
      await approveBranchTask(item.assignmentId, { feedback: item.reviewNote || undefined })
      item.status = 'pending'
      item.branchApproved = true
      ElMessage.success(`✅ 已通过「${item.itemName}」，请提交至组织审核`)
    } else {
      const reviewId = legacyReviewId(item)
      if (!reviewId) throw new Error('缺少旧材料审核标识，无法审核')
      const previousStatus = item.status
      await approveSubmit(reviewId, { feedback: item.reviewNote || undefined })
      item.status = 'passed'
      moveStatusTotal(previousStatus, item.status)
      ElMessage.success(`✅ 已通过「${item.itemName}」`)
    }
  } catch (error) {
    ElMessage.error(error?.message || '操作失败')
  } finally {
    item.acting = false
  }
}

async function handleSubmitToOrg(item = selectedItem.value) {
  if (!item || !showSubmitToOrg(item) || typeof submitBranchTaskToOrg !== 'function') return
  item.acting = true
  try {
    const previousStatus = item.status
    await submitBranchTaskToOrg(item.assignmentId, { feedback: item.reviewNote || undefined })
    item.status = 'reviewing'
    item.branchApproved = false
    moveStatusTotal(previousStatus, item.status)
    ElMessage.success('✅ 已提交至组织审核')
  } catch (error) {
    ElMessage.error(error?.message || '操作失败')
  } finally {
    item.acting = false
  }
}

function openReject(item) {
  selectedItem.value = item
  rejectReason.value = item?.reviewNote || ''
  showRejectModal.value = true
}

async function handleConfirmReject() {
  if (!selectedItem.value) return
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回意见')
    return
  }
  const item = selectedItem.value
  const previousStatus = item.status
  acting.value = true
  item.acting = true
  try {
    if (item.source === 'task' && !item.isFourDimension) {
      if (typeof rejectBranchTask !== 'function') return
      await rejectBranchTask(item.assignmentId, { feedback: rejectReason.value.trim() })
    } else {
      const reviewId = legacyReviewId(item)
      if (!reviewId) throw new Error('缺少旧材料审核标识，无法驳回')
      await rejectSubmit(reviewId, { feedback: rejectReason.value.trim() })
    }
    item.status = 'rejected'
    item.branchApproved = false
    item.reviewNote = rejectReason.value.trim()
    moveStatusTotal(previousStatus, item.status)
    showRejectModal.value = false
    ElMessage.success('✅ 已驳回并退回报送员')
  } catch (error) {
    ElMessage.error(error?.message || '操作失败')
  } finally {
    acting.value = false
    item.acting = false
  }
}

function fileName(file) {
  return typeof file === 'string' ? file : file?.fileName || file?.name || '未命名附件'
}

function getFileIcon(file) {
  const name = fileName(file).toLowerCase()
  if (name.endsWith('.pdf')) return '📕'
  if (name.endsWith('.xlsx') || name.endsWith('.xls')) return '📗'
  if (name.endsWith('.zip')) return '📦'
  if (name.endsWith('.png') || name.endsWith('.jpg') || name.endsWith('.jpeg')) return '🖼️'
  if (name.endsWith('.docx') || name.endsWith('.doc')) return '📄'
  return '📄'
}

async function downloadAttachment(item, file) {
  if (item?.source !== 'task' || typeof downloadTaskAttachment !== 'function') return
  const fileId = file?.fileId || file?.id || file?.fileObjectId
  if (!fileId) return
  try {
    const blob = await downloadTaskAttachment(item.taskId, item.assignmentId, fileId)
    if (blob instanceof Blob && typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = fileName(file)
      anchor.click()
      URL.revokeObjectURL(url)
    }
  } catch (error) {
    ElMessage.error(error?.message || '附件下载失败')
  }
}

const descriptionParts = (description) => linkifyDescription(description)
const getDimColor = (dim) => DIM_COLOR[dim] || '#64748b'
const countByStatus = (status) => Number(statusTotals.value[status] ?? 0)
function moveStatusTotal(previousStatus, nextStatus) {
  if (!TAB_VALUES.includes(previousStatus) || !TAB_VALUES.includes(nextStatus) || previousStatus === nextStatus) return
  statusTotals.value = {
    ...statusTotals.value,
    [previousStatus]: Math.max(0, Number(statusTotals.value[previousStatus] ?? 0) - 1),
    [nextStatus]: Number(statusTotals.value[nextStatus] ?? 0) + 1
  }
}

onMounted(reload)

defineExpose({
  activeTab,
  activeTabLabel,
  appliedQuery,
  changeTab,
  countByStatus,
  descriptionParts,
  downloadAttachment,
  filteredItems,
  handleApprove,
  handleConfirmReject,
  handlePageChange,
  handleReject: openReject,
  handleReset,
  handleSearch,
  handleSizeChange,
  handleSubmitToOrg,
  handleTabChange,
  items,
  loadError,
  loadTaskRows,
  normalizeTaskRow,
  openReject,
  pageNo,
  pageSize,
  query,
  reload,
  rejectReason,
  selectedItem,
  showRejectModal,
  showSubmitToOrg,
  statusTotals,
  statusLabel,
  total
})
</script>

<style scoped lang="scss">
.branch-review-container { padding: 0; }

.page-title { font-size: 22px; font-weight: 700; color: #1e293b; margin: 0 0 4px 0; }
.page-desc { font-size: 13px; color: #64748b; margin: 0 0 20px 0; }
.load-error {
  margin-bottom: 16px;
  padding: 10px 14px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #991b1b;
  background: #fef2f2;
  font-size: 13px;
}

/* Stats bar */
.stat-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 13px;

  .stat-num {
    font-size: 24px;
    font-weight: 700;
  }

  .stat-label {
    color: #475569;
  }
}

.pending-stat { background: #fef9c3; .stat-num { color: #ca8a04; } }
.approved-stat, .passed-stat { background: #dcfce7; .stat-num { color: #16a34a; } }
.rejected-stat { background: #fee2e2; .stat-num { color: #dc2626; } }
.reviewing-stat { background: #dbeafe; .stat-num { color: #2563eb; } }
.stat-item { cursor: pointer; }
.stat-item.is-active { box-shadow: inset 0 0 0 1px currentColor; }
.stat-item:focus-visible,
.review-card:focus-visible { outline: 2px solid #2563eb; outline-offset: 2px; }

/* Filter */
.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}
.query-title { width: 190px; }
.query-select { width: 132px; }

/* Review list */
.review-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #94a3b8;
  font-size: 14px;
  background: #f8fafc;
  border-radius: 8px;
}

.review-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  }
}
.review-card.selected { border-color: #dc2626; }

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  border-bottom: 1px solid #f1f5f9;
  background: #fafbfc;

  .card-left {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .dim-badge {
    font-size: 11px;
    color: #fff;
    padding: 2px 10px;
    border-radius: 4px;
    font-weight: 600;
  }

  .item-name {
    font-size: 14px;
    font-weight: 600;
    color: #1e293b;
  }
}

.card-body {
  padding: 16px 20px;

  .meta-row {
    display: flex;
    gap: 20px;
    margin-bottom: 10px;

    .meta-item {
      font-size: 12px;
      color: #64748b;
    }
  }

  .summary {
    font-size: 14px;
    color: #334155;
    line-height: 1.6;
    margin-bottom: 12px;
  }

  .files-row {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 12px;

    .file-tag {
      font-size: 12px;
      background: #f1f5f9;
      padding: 4px 10px;
      border-radius: 6px;
      color: #475569;
    }
  }

  .form-data {
    background: #eff6ff;
    border: 1px solid #bfdbfe;
    border-radius: 8px;
    padding: 12px;

    .data-row {
      font-size: 13px;
      margin-bottom: 4px;
      display: flex;
      gap: 4px;

      .data-key {
        color: #1e40af;
        font-weight: 600;
        white-space: nowrap;
      }

      .data-val {
        color: #1e293b;
      }
    }
  }
}

/* Actions */
.card-actions {
  padding: 12px 20px;
  border-top: 1px solid #f1f5f9;
  background: #fafbfc;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;

  .action-note {
    flex: 1;
    min-width: 200px;
  }

  .action-btns {
    display: flex;
    gap: 8px;
  }
}

/* Result bars */
.result-bar {
  padding: 10px 20px;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 16px;

  .result-note {
    font-weight: 400;
    font-size: 12px;
    color: #475569;
  }
}

.approved-bar {
  background: #dcfce7;
  color: #166534;
}

.rejected-bar {
  background: #fee2e2;
  color: #991b1b;
}
.reviewing-bar {
  background: #dbeafe;
  color: #1d4ed8;
}

.pager { display: flex; justify-content: flex-end; padding: 12px 4px; }
</style>
