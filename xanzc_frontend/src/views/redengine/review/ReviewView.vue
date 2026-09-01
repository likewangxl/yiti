<template>
  <div class="review-container">
    <div class="page-heading">
      <div>
        <h2 class="page-title">工作台</h2>
        <p class="page-desc">组织审核员处理四大维度材料和临时任务</p>
      </div>
    </div>

    <div v-if="loadError" class="load-error" role="alert">{{ loadError }}</div>

    <FileIntegrationNotice />

    <div class="review-layout">
      <!-- 左侧：审核队列 -->
      <div class="queue-panel" v-loading="loading">
        <div class="queue-filter">
          <el-radio-group v-model="activeTab" size="small" @change="handleTabChange">
            <el-radio-button v-for="tab in tabs" :key="tab.value" :label="tab.value">{{ tab.label }}</el-radio-button>
          </el-radio-group>
          <el-input v-model="query.title" size="small" clearable placeholder="任务标题" @keyup.enter="handleSearch" />
          <el-select v-model="query.nature" size="small" clearable placeholder="任务类型">
            <el-option label="四大维度材料上报" value="FOUR_DIMENSION" />
            <el-option label="临时任务" value="TEMPORARY" />
          </el-select>
          <el-select v-model="query.cycle" size="small" clearable placeholder="周期">
            <el-option v-for="option in cycleOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
          <el-button size="small" type="primary" @click="handleSearch">查询</el-button>
        </div>
        <div class="queue-header">
          <span class="badge pending-badge">待处理 {{ pendingItems.length }}</span>
          <span class="badge reviewing-badge">审核中 {{ reviewingItems.length }}</span>
          <span class="badge pass-badge">已通过 {{ passedItems.length }}</span>
          <span class="badge reject-badge">已驳回 {{ rejectedItems.length }}</span>
        </div>
        <div class="queue-list">
          <div
            v-for="item in filteredItems"
            :key="itemKey(item)"
            :class="['queue-item', { active: selectedItem?.id === item.id }]"
            role="button"
            tabindex="0"
            :aria-pressed="selectedItem?.id === item.id"
            @click="selectItem(item)"
            @keydown.enter.prevent.self="selectItem(item)"
            @keydown.space.prevent.self="selectItem(item)"
          >
            <div class="item-branch">{{ item.branch }}</div>
            <div class="item-name">{{ item.item }}</div>
            <div class="item-meta">{{ item.submitter }} · {{ item.date }}</div>
            <div v-if="item.source === 'task'" class="item-kind">
              {{ taskNatureLabel(item.nature) }}<span v-if="item.isPeriodic && item.cycle"> · {{ cycleLabel(item.cycle) }}</span>
            </div>
            <span :class="['status-tag', item.status]">
              {{ statusLabel(item.status) }}
            </span>
          </div>
          <div v-if="filteredItems.length === 0 && !loadError" class="queue-empty">暂无{{ activeTabLabel }}任务</div>
        </div>
        <div class="queue-pager">
          <el-pagination
            v-model:current-page="pageNo"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </div>

      <!-- 中间：内容预览 -->
      <div v-if="selectedItem" class="preview-panel" v-loading="previewLoading">
        <div class="preview-header">
          <div class="preview-title">{{ selectedItem.item }}</div>
          <div class="preview-sub">{{ selectedItem.branch }} · {{ selectedItem.submitter }}</div>
        </div>
        <div class="preview-body">
          <div v-if="selectedItem.isTask" class="task-content-block">
            <div class="block-title">📋 任务填报内容</div>
            <div v-if="selectedItem.description" class="task-description">
              <template v-for="(part, index) in descriptionParts(selectedItem.description)" :key="`description-${index}`">
                <a
                  v-if="part.type === 'link'"
                  :href="part.href"
                  target="_blank"
                  rel="noreferrer noopener"
                >{{ part.value }}</a>
                <span v-else>{{ part.value }}</span>
              </template>
            </div>
            <div class="task-content">{{ selectedItem.content }}</div>
          </div>

          <!-- 结构化数据 -->
          <div v-if="selectedItem.formData" class="form-data-block">
            <div class="block-title">📋 结构化填报数据（核验区）</div>
            <div v-for="(value, key) in selectedItem.formData" :key="key" class="form-row">
              <span class="form-key">{{ key }}:</span>
              <span class="form-value">{{ value }}</span>
            </div>
          </div>

          <!-- 附件清单 -->
          <div class="files-block">
            <div class="block-title">📄 附件清单</div>
            <div v-if="selectedItem.files.length === 0" class="file-item file-empty">暂无可查询附件</div>
            <template v-for="(file, idx) in selectedItem.files" :key="idx">
              <el-button
                v-if="selectedItem.source === 'task'"
                link
                class="file-item"
                @click="downloadAttachment(selectedItem, file)"
              >
                <span class="file-icon">{{ getFileIcon(file) }}</span>
                <span>{{ fileName(file) }}</span>
              </el-button>
              <div v-else class="file-item">
                <span class="file-icon">{{ getFileIcon(file) }}</span>
                <span>{{ fileName(file) }}</span>
              </div>
            </template>
          </div>
        </div>
      </div>
      <div v-else class="preview-panel empty-panel">
        <div class="empty-text">← 请从左侧选择一项进行审核</div>
      </div>

      <!-- 右侧：评分与裁决 -->
      <div v-if="selectedItem && selectedItem.isFourDimension" class="scoring-panel">
        <h3 class="scoring-title">评分与裁决</h3>

        <div class="info-block">
          <div class="info-label">考核项</div>
          <div class="info-value">{{ selectedItem.item }}</div>
        </div>

        <div class="rule-block">
          <div class="rule-label">📋 评分规则</div>
          <div class="rule-text">{{ selectedItem.rule }}</div>
        </div>

        <div class="suggest-block">
          <div class="suggest-label">📐 满分上限</div>
          <div class="suggest-value">{{ selectedItem.maxScore }} 分</div>
        </div>

        <div class="score-input">
          <label>最终得分</label>
          <el-input-number
            v-model="finalScore"
            :min="0"
            :max="selectedItem.maxScore"
            style="width: 100%;"
          />
        </div>

        <div class="comment-input">
          <label>审核意见</label>
          <el-input
            v-model="reviewComment"
            type="textarea"
            :rows="3"
            placeholder="输入审核意见..."
          />
        </div>

        <!-- 操作按钮 -->
        <div v-if="selectedItem.status === 'pending'" class="action-buttons">
          <el-button type="success" class="action-btn" :loading="acting" @click="handleApprove">
            {{ selectedItem.isTask ? '✅ 通过' : `✅ 通过并计 ${finalScore ?? 0} 分` }}
          </el-button>
          <el-button class="action-btn reject-btn" :loading="acting" @click="handleRejectClick">
            ❌ 驳回
          </el-button>
        </div>

        <div v-if="selectedItem.status === 'passed'" class="result-block passed">
          ✅ 已通过<span v-if="selectedItem.isFourDimension">，计 {{ selectedItem.finalScore }} 分</span>
        </div>
        <div v-if="selectedItem.status === 'rejected'" class="result-block rejected">
          <div>❌ 已驳回</div>
          <div class="reject-reason">{{ selectedItem.rejectReason }}</div>
        </div>
      </div>

      <div v-if="selectedItem && selectedItem.isTask" class="task-action-panel">
        <h3 class="scoring-title">任务处理</h3>
        <div class="task-action-summary">
          <div>任务性质：{{ taskNatureLabel(selectedItem.nature) }}</div>
          <div v-if="selectedItem.isPeriodic && selectedItem.cycle">周期：{{ cycleLabel(selectedItem.cycle) }}</div>
          <div>提交时间：{{ selectedItem.date }}</div>
        </div>
        <div v-if="selectedItem.status === 'pending'" class="action-buttons">
          <el-button type="success" class="action-btn" :loading="acting" @click="handleApprove">✅ 通过</el-button>
          <el-button class="action-btn reject-btn" :loading="acting" @click="handleRejectClick">❌ 驳回</el-button>
        </div>
        <div v-if="selectedItem.status === 'reviewing'" class="result-block reviewing">审核中</div>
        <div v-if="selectedItem.status === 'passed'" class="result-block passed">✅ 已通过</div>
        <div v-if="selectedItem.status === 'rejected'" class="result-block rejected">
          <div>❌ 已驳回</div>
          <div class="reject-reason">{{ selectedItem.rejectReason }}</div>
        </div>
      </div>
    </div>

    <!-- 驳回弹窗 -->
    <el-dialog v-model="showRejectModal" title="驳回原因" width="500px">
      <div class="reject-info">
        <div class="reject-item-name">{{ selectedItem?.item }}</div>
        <div class="reject-branch">支部：{{ selectedItem?.branch }}</div>
      </div>
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="4"
        placeholder="请输入驳回原因，例如：未见明确痛点研讨议程，不计分"
      />
      <template #footer>
        <el-button @click="showRejectModal = false">取消</el-button>
        <el-button type="danger" :loading="acting" @click="handleConfirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 组织审核工作台：左侧保留生产环境的沉浸式队列，增加任务页签和任务类型筛选。
// 四大维度材料沿用原评分与审核 API；临时任务使用 assignment 审核 API，并在预览区展示
// 填报正文和附件。任务审核 API 的资源路径需与 red-engine-center 后端同步发布。
import { ref, computed, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  approveOrgTask,
  approveSubmit,
  downloadTaskAttachment,
  getOrgTaskReview,
  getReviewPreview,
  listOrgTaskReviews,
  rejectOrgTask,
  rejectSubmit
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

// 评分标准文案：与 report/JointView.vue 的 rule-box 文案保持一致（产品既定标准，非编造数据）
const RULE_TEXT = {
  '1.1': '每次1.5分，最高6分。纯座谈不计分。',
  '1.2': '每份协议1分，最高4分。',
  '1.3': '取最高项不叠加：破冰(2分)→推进(4分)→落地(8分)。满分25分。',
  '2.1': '实际达成量÷目标×50分。≥100%计50分；60%-99%按公式折算；<60%计0分。',
  '2.2': '实际达成量÷目标×50分。≥100%计50分；60%-99%按公式折算；<60%计0分。',
  '4.1': '支部书记亲自带队拜访核心客户/协调关键部门，每次1分，满分5分。',
  '4.2': '有明确责任分工(2分)；难题攻克取得明显进展(3分)。满分5分。',
  sup: '按时提交节点跟踪表、典型案例、工作总结。逾期1天扣1分，逾期3天不得分。满分5分。',
}
const DIM_LABEL = { dim1: '外联共建', dim2: '业务提升', dim3: '头雁先锋', dim4: '督导响应' }
const tabs = [
  { value: 'pending', label: '待处理' },
  { value: 'reviewing', label: '审核中' },
  { value: 'passed', label: '已通过' },
  { value: 'rejected', label: '已驳回' }
]
const cycleOptions = CYCLE_OPTIONS
const TASK_STATUS_MAP = {
  UNREPORTED: 'reviewing',
  TODO: 'reviewing',
  BRANCH_PENDING: 'reviewing',
  BRANCH_APPROVED: 'reviewing',
  ORG_PENDING: 'pending',
  APPROVED: 'passed',
  COMPLETED: 'passed',
  REJECTED: 'rejected',
  REJECTED_BY_BRANCH: 'rejected',
  REJECTED_BY_ORG: 'rejected'
}

const loading = ref(false)
const loadError = ref('')
const previewLoading = ref(false)
const acting = ref(false)
const reviewItems = ref([])
const pageNo = ref(1)
const pageSize = ref(50)
const total = ref(0)

const activeTab = ref('pending')
const query = reactive({ title: '', nature: '', cycle: '' })
const appliedQuery = ref({})

const selectedItem = ref(null)
const finalScore = ref(0)
const reviewComment = ref('')
const showRejectModal = ref(false)
const rejectReason = ref('')

const pendingItems = computed(() => reviewItems.value.filter((i) => i.status === 'pending'))
const reviewingItems = computed(() => reviewItems.value.filter((i) => i.status === 'reviewing'))
const passedItems = computed(() => reviewItems.value.filter((i) => i.status === 'passed'))
const rejectedItems = computed(() => reviewItems.value.filter((i) => i.status === 'rejected'))
// 组织队列已经由服务端按页签和状态分页；不在客户端再次过滤，避免丢行或污染 total。
const filteredItems = computed(() => reviewItems.value)
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

function toQueueItem(row) {
  return {
    ...row,
    id: row.id,
    source: 'material',
    legacyReviewId: row.legacyReviewId ?? row.submitId ?? row.reviewId,
    taskId: row.taskId,
    assignmentId: row.assignmentId,
    periodKey: row.periodKey,
    detailItemCode: row.detailItemCode ?? row.itemCode,
    branch: row.orgName || (row.orgId != null ? `组织#${row.orgId}` : '—'),
    dim: DIM_LABEL[row.dimension] || row.dimension || '-',
    item: row.itemName ? `${row.itemCode ? row.itemCode + ' ' : ''}${row.itemName}` : (row.itemCode || '-'),
    itemCode: row.itemCode,
    submitter: row.submitterId || '—',
    date: row.submitDate || '—',
    description: '',
    content: '',
    nature: 'PERIODIC',
    cycle: row.cycleType || row.cycle || '',
    isPeriodic: true,
    isTemporary: false,
    isTask: false,
    isFourDimension: true,
    maxScore: row.maxScore ?? 100,
    rule: RULE_TEXT[row.itemCode] || '（暂无对应静态评分标准文案）',
    formData: parseFormData(row.formData),
    files: parseFiles(row.fileUrls),
    status: legacyStatus(row.status),
    finalScore: row.score ?? row.finalScore ?? null,
    rejectReason: row.reviewFeedback || row.feedback || '',
    reviewNote: row.reviewFeedback || row.feedback || ''
  }
}

function isLegacyMaterialRow(row = {}) {
  return row.source === 'material'
    || (!row.taskId && !row.taskTitle && !row.businessType && !row.taskNature && !row.nature
      && (row.dimension || row.itemCode))
}

function mapTaskStatus(status) {
  return TASK_STATUS_MAP[String(status || '').toUpperCase()] || 'reviewing'
}

function normalizeTaskRow(row = {}) {
  if (isLegacyMaterialRow(row)) return toQueueItem(row)
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
  return {
    ...assignment,
    ...normalized,
    id: assignment.assignmentId || assignment.id,
    source: 'task',
    taskId: assignment.taskId || task.taskId || task.id,
    assignmentId: assignment.assignmentId || assignment.id,
    branch: normalized.branchName || assignment.branchName || '—',
    dim: fourDimension ? (DIM_LABEL[assignment.dimension] || assignment.dimension || '四大维度材料上报') : taskNatureLabel(taskNature),
    item: assignment.taskTitle || assignment.title || task.title || '—',
    itemCode: assignment.itemCode,
    submitter: normalized.submitterName || assignment.submitterName || '—',
    date: normalized.submittedAt || assignment.submittedAt || '—',
    description: assignment.taskDescription || assignment.description || task.description || '',
    content: normalized.content || assignment.content || assignment.formData || '—',
    nature: taskNature,
    cycle: assignment.cycleType || assignment.cycle || task.cycleType || task.cycle || '',
    isPeriodic,
    isTemporary: !isPeriodic,
    isTask,
    isFourDimension: fourDimension,
    formData: parseFormData(assignment.formData),
    files: normalized.files || [],
    status: normalized.isUnreported ? 'reviewing' : mapTaskStatus(rawStatus),
    finalScore: assignment.score ?? assignment.finalScore ?? null,
    rejectReason: assignment.reviewFeedback || assignment.feedback || assignment.reviewOpinion || '',
    reviewNote: assignment.reviewFeedback || assignment.feedback || assignment.reviewOpinion || ''
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

function legacyStatus(value) {
  const normalized = String(value ?? '').toUpperCase()
  if (value === 2 || normalized === 'APPROVED') return 'passed'
  if (value === 3 || normalized === 'REJECTED' || normalized === 'REJECTED_BY_ORG') return 'rejected'
  return 'pending'
}

function taskQuery() {
  const filters = { ...appliedQuery.value }
  if (filters.nature === 'FOUR_DIMENSION') {
    filters.businessType = 'FOUR_DIMENSION'
    delete filters.nature
  }
  return buildWorkflowQuery(filters, activeTab.value, pageNo.value, pageSize.value)
}

async function loadTaskRows() {
  if (typeof listOrgTaskReviews !== 'function') return { rows: [], total: 0, error: '任务审核队列不可用' }
  try {
    const result = await listOrgTaskReviews(taskQuery())
    const page = normalizeAssignmentPage(result)
    const rows = page.records.map(normalizeTaskRow)
    return { rows, total: page.total, error: '' }
  } catch {
    return { rows: [], total: 0, error: '任务审核队列加载失败' }
  }
}

async function reload() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await loadTaskRows()
    reviewItems.value = result.rows
    total.value = result.total
    if (result.error) loadError.value = result.error
    if (selectedItem.value) {
      selectedItem.value = reviewItems.value.find((item) => item.id === selectedItem.value.id) || null
    }
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

// 只在"切换到不同条目"时把评分清零；同一条目的重赋值（handleApprove/handleConfirmReject
// 成功后 `selectedItem.value = { ...item }`、selectItem() 里 getReviewPreview 异步返回后
// `selectedItem.value = merged`，均只是同 id 对象换引用触发视图刷新）不应清空已录入的分值——
// 否则审核通过后"最终得分"输入框会被误重置为 0，与同屏"已通过，计 X 分"结果横幅自相矛盾
// （Playwright 联调 Task 17d 截图复现），且若用户在预览异步加载期间已手填分数，预览返回时
// 也会静默清零用户刚输入的值。
watch(selectedItem, (val, oldVal) => {
  if (val && (!oldVal || val.id !== oldVal.id) && val.isFourDimension) finalScore.value = 0
})

const selectItem = async (item) => {
  if (!item) return
  selectedItem.value = item
  reviewComment.value = item.reviewNote || ''
  previewLoading.value = true
  try {
    const reviewId = legacyReviewId(item)
    if (item.source === 'task' && item.isFourDimension && reviewId && typeof getReviewPreview === 'function') {
      const detail = await getReviewPreview(reviewId)
      if (detail) {
        Object.assign(item, {
          formData: parseFormData(detail.formData),
          files: parseFiles(detail.fileUrls),
          content: detail.content || item.content
        })
      }
    } else if (item.source === 'task' && typeof getOrgTaskReview === 'function') {
      const detail = await getOrgTaskReview(item.assignmentId)
      if (detail) Object.assign(item, normalizeTaskRow({ ...item, ...detail }))
    } else if (item.source === 'material' && typeof getReviewPreview === 'function') {
      const detail = await getReviewPreview(item.id)
      if (detail) {
        Object.assign(item, {
          formData: parseFormData(detail.formData),
          files: parseFiles(detail.fileUrls),
          content: detail.content || item.content
        })
      }
    }
  } catch (error) {
    ElMessage.error(error?.message || '加载详情失败')
  } finally {
    previewLoading.value = false
  }
}

const getFileIcon = (file) => {
  const name = fileName(file).toLowerCase()
  if (name.endsWith('.pdf')) return '📕'
  if (name.endsWith('.xlsx') || name.endsWith('.xls')) return '📗'
  if (name.endsWith('.zip')) return '📦'
  if (name.endsWith('.png') || name.endsWith('.jpg') || name.endsWith('.jpeg')) return '🖼️'
  if (name.endsWith('.docx') || name.endsWith('.doc')) return '📄'
  return '📄'
}

const fileName = (file) => typeof file === 'string' ? file : file?.fileName || file?.name || '未命名附件'

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

const handleApprove = async () => {
  if (!selectedItem.value) return
  acting.value = true
  try {
    const item = reviewItems.value.find((row) => row.id === selectedItem.value.id) || selectedItem.value
    if (item.source === 'task' && !item.isFourDimension) {
      if (typeof approveOrgTask !== 'function') return
      await approveOrgTask(item.assignmentId, { feedback: reviewComment.value || undefined })
      item.status = 'passed'
      item.reviewNote = reviewComment.value
      selectedItem.value = { ...item }
      ElMessage.success(`✅ 已通过${item.isTask ? taskNatureLabel(item.nature) : '任务'}`)
    } else {
      const reviewId = legacyReviewId(item)
      if (!reviewId) throw new Error('缺少旧材料审核标识，无法审核')
      await approveSubmit(reviewId, { score: finalScore.value, feedback: reviewComment.value || undefined })
      item.status = 'passed'
      item.finalScore = finalScore.value
      selectedItem.value = { ...item }
      ElMessage.success(`✅ 通过并计 ${finalScore.value} 分`)
    }
  } catch (error) {
    ElMessage.error(error?.message || '操作失败')
  } finally {
    acting.value = false
  }
}

const handleRejectClick = () => {
  rejectReason.value = ''
  showRejectModal.value = true
}

const handleConfirmReject = async () => {
  if (!selectedItem.value) return
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回意见')
    return
  }
  acting.value = true
  try {
    const item = reviewItems.value.find((row) => row.id === selectedItem.value.id) || selectedItem.value
    if (item.source === 'task' && !item.isFourDimension) {
      if (typeof rejectOrgTask !== 'function') return
      await rejectOrgTask(item.assignmentId, { feedback: rejectReason.value.trim() })
    } else {
      const reviewId = legacyReviewId(item)
      if (!reviewId) throw new Error('缺少旧材料审核标识，无法驳回')
      await rejectSubmit(reviewId, { feedback: rejectReason.value.trim() })
    }
    item.status = 'rejected'
    item.rejectReason = rejectReason.value.trim()
    item.reviewNote = rejectReason.value.trim()
    selectedItem.value = { ...item }
    showRejectModal.value = false
    ElMessage.success('✅ 已驳回')
  } catch (error) {
    ElMessage.error(error?.message || '操作失败')
  } finally {
    acting.value = false
  }
}

const itemKey = (item) => `${item.source || 'legacy'}-${item.id}`
const descriptionParts = (description) => linkifyDescription(description)

onMounted(reload)

defineExpose({
  activeTab,
  activeTabLabel,
  appliedQuery,
  descriptionParts,
  downloadAttachment,
  filteredItems,
  loadError,
  finalScore,
  getFileIcon,
  handleApprove,
  handleConfirmReject,
  handlePageChange,
  handleRejectClick,
  handleReset,
  handleSearch,
  handleSizeChange,
  handleTabChange,
  loadTaskRows,
  normalizeTaskRow,
  pendingItems,
  query,
  rejectedItems,
  rejectReason,
  reload,
  reviewingItems,
  reviewItems,
  selectItem,
  selectedItem,
  showRejectModal,
  statusLabel,
  passedItems,
  pageNo,
  pageSize,
  total
})
</script>

<style scoped lang="scss">
.review-container {
  padding: 0;

  .page-title {
    font-size: 22px;
    font-weight: 700;
    color: #1e293b;
    margin: 0 0 20px 0;
  }
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-desc {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.load-error {
  margin-bottom: 16px;
  padding: 10px 14px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #991b1b;
  background: #fef2f2;
  font-size: 13px;
}

.review-layout {
  display: grid;
  grid-template-columns: 240px 330px 1fr;
  gap: 16px;
  height: calc(100vh - 180px);
}

.queue-panel   { grid-column: 1; }
.scoring-panel { grid-column: 2; }
.preview-panel { grid-column: 3; }
.task-action-panel { grid-column: 2; }

/* Queue Panel */
.queue-panel {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.queue-filter {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border-bottom: 1px solid #e2e8f0;
}

.queue-filter :deep(.el-radio-group) {
  display: flex;
  flex-wrap: wrap;
}

.queue-filter :deep(.el-radio-button__inner) {
  padding: 7px 8px;
  font-size: 11px;
}

.queue-filter :deep(.el-input),
.queue-filter :deep(.el-select) {
  width: 100%;
}

.queue-header {
  padding: 12px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.pending-badge { background: #fef9c3; color: #92400e; }
.pass-badge { background: #dcfce7; color: #166534; }
.reject-badge { background: #fee2e2; color: #b91c1c; }
.reviewing-badge { background: #dbeafe; color: #1d4ed8; }

.queue-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.queue-pager {
  display: flex;
  justify-content: flex-end;
  padding: 8px 10px;
  border-top: 1px solid #e2e8f0;
  overflow-x: auto;
}

.queue-empty {
  padding: 40px 12px;
  color: #94a3b8;
  font-size: 12px;
  text-align: center;
}

.queue-item {
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  margin-bottom: 6px;
  cursor: pointer;
  background: #f8fafc;
  transition: all 0.2s;

  &:focus-visible { outline: 2px solid #2563eb; outline-offset: 2px; }

  &:hover { background: #f1f5f9; }

  &.active {
    background: #fee2e2;
    border-color: #dc2626;
  }

  .item-branch {
    font-size: 12px;
    font-weight: 600;
    color: #1e293b;
    margin-bottom: 2px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .item-name {
    font-size: 11px;
    color: #64748b;
    margin-bottom: 2px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .item-meta {
    font-size: 10px;
    color: #94a3b8;
    margin-bottom: 4px;
  }

  .item-kind {
    font-size: 10px;
    color: #64748b;
    margin-bottom: 4px;
  }

  .status-tag {
    font-size: 10px;
    padding: 1px 6px;
    border-radius: 3px;
    font-weight: 600;

    &.pending { background: #fef9c3; color: #92400e; }
    &.reviewing { background: #dbeafe; color: #1d4ed8; }
    &.passed { background: #dcfce7; color: #166534; }
    &.rejected { background: #fee2e2; color: #b91c1c; }
  }
}

/* Preview Panel */
.preview-panel {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.empty-panel {
  display: flex;
  align-items: center;
  justify-content: center;

  .empty-text {
    color: #94a3b8;
    font-size: 14px;
  }
}

.preview-header {
  padding: 14px 16px;
  border-bottom: 1px solid #e2e8f0;

  .preview-title {
    font-size: 14px;
    font-weight: 600;
    color: #1e293b;
    margin-bottom: 2px;
  }

  .preview-sub {
    font-size: 12px;
    color: #64748b;
  }
}

.preview-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.form-data-block {
  background: #dbeafe;
  border: 1px solid #93c5fd;
  border-radius: 8px;
  padding: 14px;
  margin-bottom: 16px;

  .block-title {
    font-size: 13px;
    font-weight: 600;
    color: #1e40af;
    margin-bottom: 10px;
  }

  .form-row {
    font-size: 13px;
    margin-bottom: 6px;
    display: flex;
    gap: 10px;

    .form-key {
      font-weight: 600;
      color: #1e40af;
      min-width: 90px;
    }

    .form-value { color: #1e293b; }
  }
}

.task-content-block {
  padding: 14px;
  margin-bottom: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;

  .block-title {
    margin-bottom: 8px;
    color: #1e293b;
    font-size: 13px;
    font-weight: 600;
  }

  .task-description,
  .task-content {
    color: #334155;
    font-size: 13px;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .task-description { margin-bottom: 8px; }
}

.files-block {
  margin-bottom: 16px;

  .block-title {
    font-size: 13px;
    font-weight: 600;
    color: #1e293b;
    margin-bottom: 8px;
  }

  .file-item {
    padding: 6px 10px;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    margin-bottom: 4px;
    font-size: 13px;
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;

    &:hover { background: #f1f5f9; }
  }

  .file-empty {
    color: #94a3b8;
    cursor: default;

    &:hover { background: #f8fafc; }
  }
}

/* Scoring Panel */
.scoring-panel {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.task-action-panel {
  grid-column: 2;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.task-action-summary {
  color: #475569;
  font-size: 13px;
  line-height: 1.8;
}

.scoring-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 14px 0;
}

.info-block {
  margin-bottom: 12px;

  .info-label {
    font-size: 12px;
    font-weight: 600;
    color: #1e293b;
    margin-bottom: 2px;
  }

  .info-value {
    font-size: 13px;
    color: #475569;
  }
}

.rule-block {
  background: #fef9c3;
  border: 1px solid #fef3c7;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 12px;

  .rule-label {
    font-size: 11px;
    font-weight: 600;
    color: #92400e;
    margin-bottom: 4px;
  }

  .rule-text {
    font-size: 12px;
    color: #92400e;
    line-height: 1.5;
  }
}

.suggest-block {
  background: #dcfce7;
  border: 1px solid #bbf7d0;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 14px;

  .suggest-label {
    font-size: 11px;
    font-weight: 600;
    color: #166534;
    margin-bottom: 4px;
  }

  .suggest-value {
    font-size: 18px;
    font-weight: 700;
    color: #16a34a;
  }
}

.score-input, .comment-input {
  margin-bottom: 12px;

  label {
    display: block;
    font-size: 12px;
    font-weight: 600;
    color: #1e293b;
    margin-bottom: 6px;
  }
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: auto;

  .action-btn {
    width: 100%;
    font-weight: 600;
  }

  .reject-btn {
    background: #fecaca;
    color: #b91c1c;
    border: none;

    &:hover {
      background: #fca5a5;
    }
  }
}

.result-block {
  padding: 10px;
  border-radius: 6px;
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  margin-top: auto;

  &.passed {
    background: #dcfce7;
    color: #166534;
  }

  &.rejected {
    background: #fee2e2;
    color: #b91c1c;
    text-align: left;

    .reject-reason {
      font-size: 11px;
      color: #7f1d1d;
      margin-top: 4px;
      font-weight: 400;
    }
  }
}

/* Reject Modal */
.reject-info {
  background: #fee2e2;
  padding: 10px;
  border-radius: 6px;
  margin-bottom: 14px;

  .reject-item-name {
    font-weight: 600;
    color: #7f1d1d;
    font-size: 14px;
  }

  .reject-branch {
    font-size: 12px;
    color: #991b1b;
    margin-top: 4px;
  }
}

@media (max-width: 1024px) {
  .review-layout {
    grid-template-columns: 1fr;
    height: auto;
  }
}
</style>
