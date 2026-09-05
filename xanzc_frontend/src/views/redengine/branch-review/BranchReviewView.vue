<template>
  <div class="branch-review-container">
    <h2 class="page-title">任务处理</h2>
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

    <div class="source-tabs" role="tablist" aria-label="任务来源">
      <button
        v-for="source in sourceTabs"
        :id="`branch-review-source-tab-${source.value}`"
        :key="source.value"
        type="button"
        :class="['source-tab', { 'is-active': activeSource === source.value }]"
        role="tab"
        :aria-selected="activeSource === source.value"
        :tabindex="activeSource === source.value ? 0 : -1"
        aria-controls="branch-review-source-panel"
        :data-source-tab="source.value"
        @click="changeSource(source.value)"
        @keydown="handleSourceKeydown($event, source.value)"
      >{{ source.label }}</button>
    </div>

    <div class="review-sources" v-loading="loading">
      <section
        id="branch-review-source-panel"
        class="review-source-section"
        :data-source="activeSource"
        role="tabpanel"
        tabindex="0"
        :aria-labelledby="`branch-review-source-tab-${activeSource}`"
      >
        <div class="source-heading review-source-heading">
          <div>
            <h3>{{ activeSourceMeta.title }}</h3>
            <p>{{ activeSourceMeta.description }}</p>
          </div>
          <span class="source-total">共 {{ activeSourceTotal }} 条</span>
        </div>
        <div class="review-list">
          <template v-if="activeSource === 'task'">
            <div v-if="items.length === 0 && !loadError" class="empty-state">暂无{{ activeTabLabel }}任务</div>

            <div
              v-for="item in items"
              :key="itemKey(item)"
              :class="['review-card', { selected: selectedItem && itemKey(selectedItem) === itemKey(item) }]"
              role="button"
              tabindex="0"
              :aria-pressed="selectedItem && itemKey(selectedItem) === itemKey(item)"
              @click="selectItem(item)"
              @keydown.enter.prevent.self="selectItem(item)"
              @keydown.space.prevent.self="selectItem(item)"
            >
              <div class="card-body card-summary">
                <div class="card-summary-row is-responsive">
                  <span class="dim-badge" :style="{ background: getDimColor(item.dim) }">{{ item.dim }}</span>
                  <span class="item-name">任务名称：{{ item.itemName }}</span>
                  <span class="meta-item summary-description">任务说明：{{ item.description || '—' }}</span>
                  <span class="meta-item" v-if="item.branch">党支部：{{ item.branch }}</span>
                  <span class="meta-item">📋 提交人：{{ item.submitter }}</span>
                  <span class="meta-item">📅 提交时间：{{ item.submitDate }}</span>
                  <span class="meta-item">任务性质：{{ taskNatureLabel(item.nature) }}</span>
                  <span v-if="item.isPeriodic && item.cycle" class="meta-item">周期：{{ cycleLabel(item.cycle) }}</span>
                  <el-tag
                    class="summary-status"
                    :type="item.status === 'passed' ? 'success' : item.status === 'rejected' ? 'danger' : item.status === 'reviewing' ? 'info' : 'warning'"
                    size="small"
                  >{{ statusLabel(item.status) }}</el-tag>
                </div>
                <div class="card-hint">点击查看详情并处理</div>
              </div>
            </div>
          </template>

          <template v-else>
            <div v-if="legacyItems.length === 0 && !loadError" class="empty-state">暂无{{ activeTabLabel }}四大维度材料</div>

            <div
              v-for="item in legacyItems"
              :key="itemKey(item)"
              :class="['review-card', { selected: selectedItem && itemKey(selectedItem) === itemKey(item) }]"
              role="button"
              tabindex="0"
              :aria-pressed="selectedItem && itemKey(selectedItem) === itemKey(item)"
              @click="selectItem(item)"
              @keydown.enter.prevent.self="selectItem(item)"
              @keydown.space.prevent.self="selectItem(item)"
            >
              <div class="card-body card-summary">
                <div class="card-summary-row is-responsive">
                  <span class="dim-badge" :style="{ background: getDimColor(item.dim) }">{{ item.dim }}</span>
                  <span class="item-name">任务名称：{{ item.itemName }}</span>
                  <span class="meta-item" v-if="item.branch">党支部：{{ item.branch }}</span>
                  <span class="meta-item">📋 提交人：{{ item.submitter }}</span>
                  <span class="meta-item">📅 提交时间：{{ item.submitDate }}</span>
                  <span class="meta-item">材料来源：直接上报</span>
                  <el-tag
                    class="summary-status"
                    :type="item.status === 'passed' ? 'success' : item.status === 'rejected' ? 'danger' : item.status === 'reviewing' ? 'info' : 'warning'"
                    size="small"
                  >{{ statusLabel(item.status) }}</el-tag>
                </div>
                <div class="card-hint">点击查看详情并处理</div>
              </div>
            </div>
          </template>
        </div>

        <div v-if="activeSource === 'task'" class="pager task-pager">
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
        <div v-else class="pager material-pager">
          <el-pagination
            v-model:current-page="legacyPageNo"
            v-model:page-size="legacyPageSize"
            :total="legacyTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="handleLegacyPageChange"
            @size-change="handleLegacySizeChange"
          />
        </div>
      </section>
    </div>

    <el-dialog
      v-model="showDetailDialog"
      :title="detailDialogTitle"
      width="760px"
      :close-on-click-modal="false"
      destroy-on-close
      @closed="handleDetailClosed"
    >
      <div v-if="selectedItem" class="detail-dialog task-detail-dialog">
        <div class="detail-meta">
          <span v-if="selectedItem.branch">党支部：{{ selectedItem.branch }}</span>
          <span>提交人：{{ selectedItem.submitter }}</span>
          <span>提交时间：{{ selectedItem.submitDate }}</span>
          <span>状态：{{ statusLabel(selectedItem.status) }}</span>
        </div>

        <section v-if="selectedItem.isFourDimension" class="detail-section four-dimension-detail" data-test="four-dimension-detail">
          <h3>四大维度材料</h3>
          <div class="detail-grid">
            <div><span class="field-label">维度：</span>{{ getDimLabel(selectedItem.dimensionCode || selectedItem.dimension || selectedItem.dim) }}</div>
            <div><span class="field-label">任务名称：</span>{{ selectedItem.itemName || selectedItem.taskTitle || '—' }}</div>
            <div><span class="field-label">材料明细编码：</span>{{ selectedItem.itemCode || '—' }}</div>
          </div>
          <div class="detail-subsection">
            <h4>结构化材料</h4>
            <div v-if="selectedItem.formData" class="form-data">
              <div v-for="(val, key) in selectedItem.formData" :key="key" class="data-row">
                <span class="data-key">{{ key }}：</span>
                <span class="data-val">{{ val }}</span>
              </div>
            </div>
            <div v-else class="detail-empty">暂无结构化材料</div>
          </div>
          <div class="detail-subsection">
            <h4>材料摘要</h4>
            <div class="material-preview">{{ selectedItem.legacyPreview || selectedItem.summary || '暂无材料摘要' }}</div>
          </div>
          <div class="detail-subsection">
            <h4>附件</h4>
            <div v-if="selectedItem.files?.length" class="files-row">
              <template v-for="(file, idx) in selectedItem.files" :key="`${itemKey(selectedItem)}-file-${idx}`">
                <el-button
                  v-if="selectedItem.source === 'task'"
                  link
                  size="small"
                  class="file-tag"
                  @click.stop="downloadAttachment(selectedItem, file)"
                >{{ getFileIcon(file) }} {{ fileName(file) }}</el-button>
                <span v-else class="file-tag">{{ getFileIcon(file) }} {{ fileName(file) }}</span>
              </template>
            </div>
            <div v-else class="detail-empty">暂无附件</div>
          </div>
        </section>

        <section v-else class="detail-section task-detail" data-test="task-detail">
          <h3>任务内容</h3>
          <div class="detail-grid">
            <div><span class="field-label">任务性质：</span>{{ taskNatureLabel(selectedItem.nature) }}</div>
            <div v-if="selectedItem.isPeriodic && selectedItem.cycle"><span class="field-label">周期：</span>{{ cycleLabel(selectedItem.cycle) }}</div>
          </div>
          <div v-if="selectedItem.description" class="description">
            <span class="field-label">任务说明：</span>
            <template v-for="(part, index) in descriptionParts(selectedItem.description)" :key="`${itemKey(selectedItem)}-description-${index}`">
              <a
                v-if="part.type === 'link'"
                :href="part.href"
                target="_blank"
                rel="noreferrer noopener"
              >{{ part.value }}</a>
              <span v-else>{{ part.value }}</span>
            </template>
          </div>
          <div class="summary">
            <span class="field-label">本次填报内容：</span>
            <template v-if="selectedItem.summary">
              <template v-for="(part, index) in descriptionParts(selectedItem.summary)" :key="`${itemKey(selectedItem)}-summary-${index}`">
                <a
                  v-if="part.type === 'link'"
                  :href="part.href"
                  target="_blank"
                  rel="noreferrer noopener"
                >{{ part.value }}</a>
                <span v-else>{{ part.value }}</span>
              </template>
            </template>
            <span v-else class="detail-empty">暂无填报内容</span>
          </div>
          <div class="detail-subsection">
            <h4>任务附件</h4>
            <div v-if="selectedItem.files?.length" class="files-row">
              <template v-for="(file, idx) in selectedItem.files" :key="`${itemKey(selectedItem)}-file-${idx}`">
                <el-button
                  v-if="selectedItem.source === 'task'"
                  link
                  size="small"
                  class="file-tag"
                  @click.stop="downloadAttachment(selectedItem, file)"
                >{{ getFileIcon(file) }} {{ fileName(file) }}</el-button>
                <span v-else class="file-tag">{{ getFileIcon(file) }} {{ fileName(file) }}</span>
              </template>
            </div>
            <div v-else class="detail-empty">暂无任务附件</div>
          </div>
        </section>

        <div v-if="selectedItem.status === 'pending'" class="dialog-actions" @click.stop>
          <div class="action-note">
            <el-input v-model="selectedItem.reviewNote" size="small" placeholder="审核意见（选填）" style="width: 300px;" />
          </div>
          <div class="action-btns">
            <el-button
              v-if="!showSubmitToOrg(selectedItem)"
              type="success"
              size="small"
              :loading="selectedItem.acting"
              @click="handleApprove(selectedItem)"
            >✅ 审核通过</el-button>
            <el-button
              v-else
              type="primary"
              size="small"
              :loading="selectedItem.acting"
              @click="handleSubmitToOrg(selectedItem)"
            >提交至组织审核</el-button>
            <el-button type="danger" size="small" plain :loading="selectedItem.acting" @click="openReject(selectedItem)">↩ 驳回</el-button>
          </div>
        </div>

        <div v-if="selectedItem.status === 'reviewing'" class="result-bar reviewing-bar">已提交至组织审核，等待组织审核员处理</div>
        <div v-if="selectedItem.status === 'passed'" class="result-bar approved-bar">
          ✅ 已通过
          <span v-if="selectedItem.reviewNote" class="result-note">备注：{{ selectedItem.reviewNote }}</span>
        </div>
        <div v-if="selectedItem.status === 'rejected'" class="result-bar rejected-bar">
          ↩ 已驳回，已退回报送员
          <span v-if="selectedItem.reviewNote" class="result-note">原因：{{ selectedItem.reviewNote }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>

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
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  approveBranchTask,
  approveSubmit,
  downloadTaskAttachment,
  getBranchTaskReview,
  getReviewQueue,
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
  normalizePageResult,
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
const sourceTabs = [
  { value: 'task', label: '任务填报' },
  { value: 'material', label: '四大维度材料上报' }
]
const cycleOptions = CYCLE_OPTIONS
const TAB_VALUES = ['pending', 'reviewing', 'passed', 'rejected']
const SOURCE_VALUES = ['task', 'material']
const route = useRoute()
const activeTab = ref('pending')
const activeSource = ref('task')
const query = reactive({ title: '', nature: '', cycle: '' })
const appliedQuery = ref({})
const loading = ref(false)
const acting = ref(false)
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)
const items = ref([])
const legacyPageNo = ref(1)
const legacyPageSize = ref(20)
const legacyTotal = ref(0)
const legacyItems = ref([])
const loadError = ref('')
const statusTotals = ref({ pending: 0, reviewing: 0, passed: 0, rejected: 0 })
const reloadVersion = ref(0)
const selectedItem = ref(null)
const showDetailDialog = ref(false)
const showRejectModal = ref(false)
const rejectReason = ref('')
const autoOpenedSubmitId = ref('')

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
const activeSourceMeta = computed(() => activeSource.value === 'material'
  ? {
      title: '四大维度材料上报',
      description: '兼容直接上报的旧材料记录：查看维度、材料明细、结构化材料及附件'
    }
  : {
      title: '任务填报',
      description: '临时任务和普通任务：查看任务说明、本次填报内容及任务附件'
    })
const activeSourceTotal = computed(() => activeSource.value === 'material' ? legacyTotal.value : total.value)
const detailDialogTitle = computed(() => selectedItem.value?.isFourDimension ? '四大维度材料详情' : '任务详情')

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
  const normalizedStatus = String(row.status ?? row.submitStatus ?? '').trim().toUpperCase()
  const status = ['2', 'PASSED', 'APPROVED', 'COMPLETED'].includes(normalizedStatus) ? 'passed'
    : ['3', 'REJECTED'].includes(normalizedStatus) ? 'rejected'
      : ['REVIEWING', 'ORG_PENDING'].includes(normalizedStatus) ? 'reviewing' : 'pending'
  return {
    ...row,
    id: row.id ?? row.submitId ?? row.legacyReviewId ?? row.reviewId,
    source: 'material',
    legacyReviewId: row.legacyReviewId ?? row.submitId ?? row.reviewId ?? row.id,
    assignmentId: row.assignmentId,
    taskId: row.taskId,
    periodKey: row.periodKey,
    detailItemCode: row.detailItemCode ?? row.itemCode,
    itemCode: row.itemCode ?? row.detailItemCode,
    branch: row.orgName || (row.orgId != null ? `组织#${row.orgId}` : '—'),
    dim: DIM_LABEL[row.dimension] || row.dimension || '—',
    itemName: row.itemName ? `${row.itemCode ? `${row.itemCode} ` : ''}${row.itemName}` : (row.itemCode || '—'),
    submitter: row.submitterId || '—',
    submitDate: row.submitDate || '—',
    description: '',
    summary: row.projectName || row.itemName || '—',
    legacyPreview: row.legacyPreview || row.materialPreview || row.projectName || row.content || '',
    files: parseFiles(row.fileUrls).length ? parseFiles(row.fileUrls) : parseFiles(row.files),
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

function normalizeLegacyPage(result) {
  const page = normalizePageResult(result)
  return {
    ...page,
    records: page.records.map(normalizeMaterialRow)
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
  const dimensionCode = assignment.dimensionCode || assignment.dimension || assignment.dim || ''
  const itemName = fourDimension
    ? (assignment.itemName || assignment.item || assignment.taskTitle || assignment.title || task.title || '—')
    : (assignment.taskTitle || assignment.title || task.title || '—')
  return {
    ...assignment,
    ...normalized,
    id: assignment.assignmentId || assignment.id,
    source: 'task',
    taskId: assignment.taskId || task.taskId || task.id,
    assignmentId: assignment.assignmentId || assignment.id,
    branch: normalized.branchName || assignment.branchName || '—',
    dim: fourDimension ? (DIM_LABEL[dimensionCode] || dimensionCode || '四大维度材料上报') : taskNatureLabel(taskNature),
    itemCode: assignment.itemCode || assignment.detailItemCode || '',
    dimension: dimensionCode,
    dimensionCode,
    itemName,
    description: assignment.taskDescription || assignment.description || task.description || '',
    summary: normalized.content || assignment.content || assignment.formData || '',
    legacyPreview: assignment.legacyPreview || assignment.materialPreview || assignment.projectName || '',
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

function legacyQuery(tab = activeTab.value, nextPage = legacyPageNo.value, nextSize = legacyPageSize.value) {
  return {
    pageNo: nextPage,
    pageSize: nextSize,
    tab: String(tab).toUpperCase()
  }
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

async function loadLegacyRows() {
  if (typeof getReviewQueue !== 'function') return { rows: [], total: 0, error: '旧材料审核队列不可用' }
  try {
    const result = await getReviewQueue(legacyQuery())
    const page = normalizeLegacyPage(result)
    return { rows: page.records, total: page.total, error: '' }
  } catch {
    return { rows: [], total: 0, error: '旧材料审核队列加载失败' }
  }
}

async function loadOtherStatusTotals(filters, currentTab) {
  const tabsToLoad = TAB_VALUES.filter((tab) => tab !== currentTab)
  const entries = await Promise.all(tabsToLoad.map(async (tab) => {
    const [taskTotal, legacyTotalForTab] = await Promise.all([
      (async () => {
        try {
          const result = await listBranchTaskReviews(buildWorkflowQuery(filters, tab, 1, 1))
          return normalizeAssignmentPage(result).total
        } catch {
          return null
        }
      })(),
      (async () => {
        try {
          const result = await getReviewQueue(legacyQuery(tab, 1, 1))
          return normalizeLegacyPage(result).total
        } catch {
          return null
        }
      })()
    ])
    if (taskTotal === null && legacyTotalForTab === null) return [tab, null]
    return [tab, Number(taskTotal || 0) + Number(legacyTotalForTab || 0)]
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
    const legacyRowsPromise = loadLegacyRows()
    const totalsPromise = loadOtherStatusTotals(filters, requestedTab)
    const [result, legacyResult, otherTotals] = await Promise.all([rowsPromise, legacyRowsPromise, totalsPromise])
    if (requestId !== reloadVersion.value) return
    items.value = result.rows
    total.value = result.total
    legacyItems.value = legacyResult.rows
    legacyTotal.value = legacyResult.total
    statusTotals.value = {
      ...statusTotals.value,
      [requestedTab]: result.total + legacyResult.total,
      ...otherTotals
    }
    const errors = [result.error, legacyResult.error].filter(Boolean)
    if (errors.length) loadError.value = errors.join('；')
    if (selectedItem.value) {
      const selectedKey = itemKey(selectedItem.value)
      selectedItem.value = [...items.value, ...legacyItems.value]
        .find((item) => itemKey(item) === selectedKey) || null
    }
    await openRouteLegacyDetail()
  } finally {
    if (requestId === reloadVersion.value) loading.value = false
  }
}

async function handleSearch() {
  appliedQuery.value = { ...query }
  pageNo.value = 1
  legacyPageNo.value = 1
  await reload()
}

async function handleReset() {
  Object.assign(query, { title: '', nature: '', cycle: '' })
  appliedQuery.value = {}
  pageNo.value = 1
  legacyPageNo.value = 1
  await reload()
}

async function handleTabChange() {
  pageNo.value = 1
  legacyPageNo.value = 1
  await reload()
}

function changeTab(tab) {
  activeTab.value = tab
  handleTabChange()
}

function changeSource(source) {
  if (SOURCE_VALUES.includes(source)) activeSource.value = source
}

function handleSourceKeydown(event, source) {
  const currentIndex = SOURCE_VALUES.indexOf(source)
  if (currentIndex < 0) return

  let nextIndex
  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
    nextIndex = (currentIndex + 1) % SOURCE_VALUES.length
  } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
    nextIndex = (currentIndex - 1 + SOURCE_VALUES.length) % SOURCE_VALUES.length
  } else if (event.key === 'Home') {
    nextIndex = 0
  } else if (event.key === 'End') {
    nextIndex = SOURCE_VALUES.length - 1
  } else {
    return
  }

  event.preventDefault()
  const nextSource = SOURCE_VALUES[nextIndex]
  activeSource.value = nextSource
  nextTick(() => document.getElementById(`branch-review-source-tab-${nextSource}`)?.focus())
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

async function handleLegacyPageChange(nextPage) {
  legacyPageNo.value = nextPage
  await reload()
}

async function handleLegacySizeChange(nextSize) {
  legacyPageSize.value = nextSize
  legacyPageNo.value = 1
  await reload()
}

function itemKey(item) {
  return `${item.source || 'legacy'}-${item.id}`
}

function removeLegacyItem(item) {
  const key = itemKey(item)
  const isSelected = selectedItem.value && itemKey(selectedItem.value) === key
  const hadItem = legacyItems.value.some((row) => itemKey(row) === key)
  if (hadItem) legacyItems.value = legacyItems.value.filter((row) => itemKey(row) !== key)
  if (!hadItem && !isSelected) return
  legacyTotal.value = Math.max(0, Number(legacyTotal.value || 0) - 1)
  if (isSelected) {
    showDetailDialog.value = false
    showRejectModal.value = false
    selectedItem.value = null
  }
}

function routeQueryValue(key) {
  const value = route?.query?.[key]
  return Array.isArray(value) ? value[0] : value
}

async function openRouteLegacyDetail() {
  const submitId = routeQueryValue('submitId')
  const source = String(routeQueryValue('source') || '').toLowerCase()
  if (!submitId || (source && source !== 'material') || autoOpenedSubmitId.value === String(submitId)) return
  if (!source) activeSource.value = 'material'
  const target = legacyItems.value.find((item) => String(legacyReviewId(item)) === String(submitId))
  if (target) {
    autoOpenedSubmitId.value = String(submitId)
    await selectItem(target)
    return
  }
  // 首页待办只携带 submitId，目标可能因服务端分页不在首屏；用详情接口按后端数据范围
  // 直接校验并打开，不把跨页记录伪造塞进当前分页列表。
  if (typeof getReviewPreview !== 'function') return
  try {
    const detail = await getReviewPreview(submitId)
    if (!detail || typeof detail !== 'object') return
    const fallback = normalizeMaterialRow({ ...detail, id: submitId, submitId })
    autoOpenedSubmitId.value = String(submitId)
    await selectItem(fallback, detail)
  } catch (error) {
    ElMessage.error(error?.message || '加载详情失败')
  }
}

async function selectItem(item, detailOverride = null) {
  if (!item) return
  selectedItem.value = item
  showDetailDialog.value = true
  const reviewId = legacyReviewId(item)
  if (item.source === 'material' && reviewId && typeof getReviewPreview === 'function') {
    try {
      const detail = detailOverride !== null ? detailOverride : await getReviewPreview(reviewId)
      if (detail) {
        const detailFormData = parseFormData(detail.formData)
        const detailFiles = parseFiles(detail.fileUrls)
        const fallbackFiles = parseFiles(detail.files)
        Object.assign(item, {
          ...(detailFormData ? { formData: detailFormData } : {}),
          ...(detailFiles.length ? { files: detailFiles } : (fallbackFiles.length ? { files: fallbackFiles } : {})),
          summary: detail.content || item.summary,
          legacyPreview: detail.legacyPreview || detail.materialPreview || detail.content || detail.projectName || item.legacyPreview
        })
      }
    } catch (error) {
      ElMessage.error(error?.message || '加载详情失败')
    }
  } else if (item.source === 'task' && typeof getBranchTaskReview === 'function') {
    try {
      const detail = await getBranchTaskReview(item.assignmentId)
      if (detail) {
        const detailRow = {
          ...item,
          ...detail,
          // 任务详情接口是当前页唯一的任务数据来源；在部分旧服务响应中缺少可选字段时，
          // 保留列表 DTO 已带的材料，避免打开弹窗后附件/结构化填报被空值覆盖。
          files: Array.isArray(detail.files) && detail.files.length ? detail.files : item.files,
          formData: detail.formData ?? item.formData,
          content: detail.content ?? item.content
        }
        Object.assign(item, normalizeTaskRow(detailRow))
      }
    } catch (error) {
      ElMessage.error(error?.message || '加载任务详情失败')
    }
  }
}

function handleDetailClosed() {
  if (!showRejectModal.value) selectedItem.value = null
}

function showSubmitToOrg(item) {
  return item?.source === 'task' && item.status === 'pending' && item.branchApproved === true
}

async function handleApprove(item = selectedItem.value) {
  if (!item) return
  item.acting = true
  try {
    if (item.source === 'task') {
      if (typeof approveBranchTask !== 'function') return
      await approveBranchTask(item.assignmentId, { feedback: item.reviewNote || undefined })
      item.status = 'pending'
      item.branchApproved = true
      ElMessage.success(`✅ 已通过「${item.itemName}」，请提交至组织审核`)
    } else {
      const reviewId = legacyReviewId(item)
      if (!reviewId) throw new Error('缺少旧材料审核标识，无法审核')
      const previousItemStatus = item.status
      await approveSubmit(reviewId, { feedback: item.reviewNote || undefined })
      item.status = 'passed'
      moveStatusTotal(previousItemStatus, item.status)
      removeLegacyItem(item)
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
    const previousItemStatus = item.status
    await submitBranchTaskToOrg(item.assignmentId, { feedback: item.reviewNote || undefined })
    item.status = 'reviewing'
    item.branchApproved = false
    moveStatusTotal(previousItemStatus, item.status)
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
  const previousItemStatus = item.status
  acting.value = true
  item.acting = true
  try {
    if (item.source === 'task') {
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
    moveStatusTotal(previousItemStatus, item.status)
    if (item.source === 'material') removeLegacyItem(item)
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
const getDimLabel = (dim) => DIM_LABEL[dim] || dim || '—'
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

onMounted(() => {
  const tab = String(routeQueryValue('tab') || '').toLowerCase()
  if (TAB_VALUES.includes(tab)) activeTab.value = tab
  const source = String(routeQueryValue('source') || '').toLowerCase()
  if (SOURCE_VALUES.includes(source)) activeSource.value = source
  reload()
})

defineExpose({
  activeTab,
  activeTabLabel,
  activeSource,
  activeSourceMeta,
  activeSourceTotal,
  appliedQuery,
  changeTab,
  changeSource,
  handleSourceKeydown,
  countByStatus,
  descriptionParts,
  downloadAttachment,
  filteredItems,
  handleApprove,
  handleConfirmReject,
  handleLegacyPageChange,
  handleLegacySizeChange,
  handlePageChange,
  handleReject: openReject,
  handleReset,
  handleSearch,
  handleSizeChange,
  handleSubmitToOrg,
  handleTabChange,
  items,
  legacyItems,
  legacyPageNo,
  legacyPageSize,
  legacyTotal,
  loadError,
  loadLegacyRows,
  loadTaskRows,
  normalizeTaskRow,
  openReject,
  pageNo,
  pageSize,
  query,
  reload,
  rejectReason,
  selectedItem,
  showDetailDialog,
  showDetailModal: showDetailDialog,
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

/* Source tabs */
.source-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 10px;
  border-bottom: 1px solid #dbe3ec;
}

.source-tab {
  border: 0;
  border-bottom: 2px solid transparent;
  border-radius: 6px 6px 0 0;
  padding: 9px 14px;
  color: #475569;
  background: transparent;
  font-size: 13px;
  line-height: 1.4;
  cursor: pointer;
  transition: color 0.2s, background-color 0.2s, border-color 0.2s;

  &:hover { color: #1d4ed8; background: #eff6ff; }
  &:focus-visible { outline: 2px solid #2563eb; outline-offset: 2px; }
}

.source-tab.is-active {
  color: #1d4ed8;
  border-bottom-color: #2563eb;
  background: #eff6ff;
  font-weight: 600;
}

/* Review list */
.review-sources {
  display: flex;
  flex-direction: column;
}

.review-source-section {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
}

.source-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;

  h3 {
    margin: 0 0 4px;
    color: #1e293b;
    font-size: 16px;
  }

  p {
    margin: 0;
    color: #64748b;
    font-size: 12px;
  }

  .source-total {
    flex: none;
    color: #475569;
    font-size: 12px;
  }
}

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

.card-summary {
  .card-summary-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    min-width: 0;
    gap: 8px 16px;
  }

  .card-summary-row .dim-badge,
  .card-summary-row .item-name,
  .card-summary-row .meta-item,
  .card-summary-row .summary-status {
    flex: 0 1 auto;
  }

  .card-summary-row .summary-description {
    min-width: 0;
    flex: 1 1 260px;
    line-height: 1.5;
    overflow-wrap: anywhere;
  }

  .card-summary-row .summary-status { flex: 0 0 auto; }

  .card-hint {
    margin-top: 12px;
    color: #2563eb;
    font-size: 12px;
  }
}

.detail-dialog {
  color: #334155;
  font-size: 13px;

  .detail-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 20px;
    padding-bottom: 14px;
    border-bottom: 1px solid #e2e8f0;
    color: #64748b;
  }

  .detail-section {
    padding-top: 16px;

    h3, h4 { margin: 0 0 10px; color: #1e293b; }
    h3 { font-size: 16px; }
    h4 { font-size: 13px; }
  }

  .detail-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px 20px;
    margin-bottom: 14px;
  }

  .detail-subsection {
    margin-top: 14px;
    padding: 12px;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    background: #f8fafc;
  }

  .description, .summary, .material-preview {
    margin-bottom: 14px;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .files-row {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .detail-empty { color: #94a3b8; }
}

/* Actions */
.card-actions, .dialog-actions {
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
