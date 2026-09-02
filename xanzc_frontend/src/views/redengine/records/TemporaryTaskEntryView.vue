<template>
  <div class="temporary-task-entry">
    <div v-if="loading" class="entry-loading">正在加载任务…</div>
    <div v-else-if="loadError" class="load-error" role="alert">{{ loadError }}</div>
    <template v-else-if="task">
      <div class="entry-header">
        <div>
          <div class="entry-kicker">{{ taskEntryTitle(task.nature) }}</div>
          <h2 class="page-title">{{ task.title }}</h2>
        </div>
        <span :class="['status-badge', `status-${String(task.status || '').toLowerCase()}`]">{{ statusLabel(task.status) }}</span>
      </div>

      <FileIntegrationNotice />

      <div class="task-card">
        <div class="task-meta">
          <span>任务性质：{{ taskNatureLabel(task.nature) }}</span>
          <span>开始时间：{{ task.startAt || '—' }}</span>
          <span>截止时间：{{ task.endAt || '—' }}</span>
        </div>
        <div class="task-description">
          <span class="meta-label">任务说明</span>
          <span v-for="(part, index) in descriptionParts" :key="index">
            <a
              v-if="part.type === 'link'"
              :href="part.href"
              target="_blank"
              rel="noreferrer noopener"
              data-test="description-link"
            >{{ part.value }}</a>
            <span v-else>{{ part.value }}</span>
          </span>
        </div>
        <div v-if="task.feedback" class="rejection-note">
          <span class="meta-label">驳回意见</span>
          <span>{{ task.feedback }}</span>
        </div>
      </div>

      <div :class="['entry-card', { 'is-readonly': !canEdit }]">
        <div class="form-row">
          <label for="temporary-task-content" class="form-label required">填报内容</label>
          <textarea
            id="temporary-task-content"
            v-model="form.content"
            class="content-input"
            :disabled="!canEdit"
            rows="8"
            placeholder="请输入本次任务填报内容"
          />
        </div>

        <div v-if="task.requiresFile || existingFiles.length" class="form-row file-row">
          <span class="form-label" :class="{ required: task.requiresFile }">附件</span>
          <div class="file-control">
            <input ref="fileInput" type="file" multiple :accept="fileAccept" :disabled="!canEdit" @change="handleFileChange" />
            <div class="file-policy">{{ filePolicyText }}</div>
            <div v-if="existingFiles.length" class="file-list">
              <div v-for="file in existingFiles" :key="file.fileId" class="file-item existing-file">
                <span>{{ file.fileName }}</span><small>已上传</small>
              </div>
            </div>
            <div v-if="selectedFiles.length" class="file-list">
              <div v-for="(file, index) in selectedFiles" :key="`${file.name}-${index}`" class="file-item">
                <span>{{ file.name }}</span>
                <button v-if="canEdit" type="button" class="remove-file" @click="removeFile(index)">移除</button>
              </div>
            </div>
          </div>
        </div>

        <div class="entry-actions">
          <el-button :disabled="submitting" @click="goBack">返回</el-button>
          <el-button v-if="canEdit" type="primary" :loading="submitting" @click="handleSubmit">提交填报</el-button>
        </div>
      </div>
    </template>
    <el-empty v-else description="任务不存在或已失效" />
  </div>
</template>

<script setup>
// 临时任务填报使用 assignmentId 作为唯一业务上下文。重提时仍提交同一 assignment，
// 由服务端按当前版本号执行乐观并发控制；前端不自行改变审核状态。
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMyTaskAssignment, submitTask, uploadFile } from '@/api/redengine'
import { linkifyDescription } from '../tasks/task-domain'
import FileIntegrationNotice from '../components/FileIntegrationNotice.vue'
import { taskEntryTitle, taskNatureLabel } from './task-display'

const route = useRoute()
const router = useRouter()
const assignmentId = computed(() => {
  const raw = route.params?.assignmentId || route.query?.assignmentId
  if (raw === undefined || raw === null || raw === '') return raw
  const numeric = Number(raw)
  return Number.isNaN(numeric) ? raw : numeric
})
const task = ref(null)
const loading = ref(false)
const loadError = ref('')
const submitting = ref(false)
const form = reactive({ content: '' })
const selectedFiles = ref([])
const existingFiles = ref([])
const fileInput = ref(null)

const EDITABLE_STATUSES = Object.freeze(['UNREPORTED', 'REJECTED_BY_BRANCH', 'REJECTED_BY_ORG'])
const STATUS_LABELS = Object.freeze({
  UNREPORTED: '待提交',
  TODO: '待提交',
  OVERDUE_UNREPORTED: '待提交',
  REJECTED_BY_BRANCH: '支部驳回',
  REJECTED_BY_ORG: '组织驳回',
  BRANCH_PENDING: '支部审核中',
  BRANCH_APPROVED: '支部已审核',
  ORG_PENDING: '组织审核中',
  APPROVED: '已通过',
  COMPLETED: '已通过'
})

function asTaskModel(value = {}) {
  const taskValue = value.task || value.taskDefinition || {}
  const assignment = value.assignment || value
  const merged = { ...taskValue, ...assignment }
  const submission = merged.submission || merged.currentSubmission || {}
  const taskNature = merged.taskNature || merged.nature || ''
  const rawFiles = merged.files || merged.attachments || submission.files || submission.attachments || []
  const files = Array.isArray(rawFiles)
    ? rawFiles.map((file) => ({
      ...file,
      fileId: file.fileId || file.fileObjectId || file.id,
      fileName: file.fileName || file.name || '未命名附件'
    })).filter((file) => file.fileId)
    : []
  return {
    ...merged,
    assignmentId: merged.assignmentId || value.assignmentId || assignmentId.value,
    taskId: merged.taskId || taskValue.taskId || taskValue.id,
    taskNature,
    nature: taskNature,
    title: merged.taskTitle || merged.title || taskValue.title || '临时任务',
    description: merged.taskDescription || merged.description || taskValue.description || '',
    startAt: merged.windowStartAt || merged.temporaryStartTime || merged.startAt || merged.windowStart || '—',
    endAt: merged.windowEndAt || merged.temporaryEndTime || merged.endAt || merged.windowEnd || '—',
    requiresFile: Boolean(merged.requiresFile),
    fileTypeCodes: Array.isArray(merged.fileTypeCodes)
      ? merged.fileTypeCodes
      : (Array.isArray(merged.allowedFileTypes)
        ? merged.allowedFileTypes
        : (Array.isArray(merged.fileTypes) ? merged.fileTypes : [])),
    maxFileSizeBytes: merged.maxFileSizeBytes
      ?? merged.fileMaxSizeBytes
      ?? merged.maxFileSize
      ?? (Array.isArray(merged.fileTypes)
        ? merged.fileTypes.find((item) => item?.maxSizeBytes)?.maxSizeBytes
        : undefined),
    content: merged.content ?? submission.content ?? submission.contentText ?? '',
    feedback: merged.reviewFeedback
      || merged.feedback
      || merged.reviewOpinion
      || submission.reviewFeedback
      || submission.reviewOpinion
      || '',
    files,
    status: String(
      submission.status
      || merged.submissionStatus
      || merged.status
      || merged.stage
      || merged.assignmentStatus
      || 'UNREPORTED'
    ).trim().toUpperCase(),
    canEdit: EDITABLE_STATUSES.includes(String(
      submission.status
      || merged.submissionStatus
      || merged.status
      || merged.stage
      || merged.assignmentStatus
      || 'UNREPORTED'
    ).trim().toUpperCase())
  }
}

function fileTypeCode(value) {
  if (typeof value === 'string') return value.toUpperCase().replace(/^\./, '')
  return String(value?.code
    || value?.value
    || value?.fileTypeCode
    || value?.fileExtension
    || value?.mimeType
    || value?.name
    || '').toUpperCase().replace(/^\./, '')
}

function fileTypeLabel(value) {
  if (typeof value === 'string') return value
  return value?.fileTypeName || value?.name || value?.label || value?.fileTypeCode || value?.code || value?.value || value?.fileExtension || ''
}

function extensionOf(name) {
  return String(name || '').split('.').pop()?.toUpperCase() || ''
}

function fileAcceptValue(value) {
  const code = fileTypeCode(value)
  return code.includes('/') ? code.toLowerCase() : `.${code.toLowerCase()}`
}

function formatBytes(bytes) {
  const value = Number(bytes)
  if (!Number.isFinite(value) || value <= 0) return ''
  if (value >= 1024 * 1024) return `${Math.round(value / 1024 / 1024 * 10) / 10} MB`
  if (value >= 1024) return `${Math.round(value / 1024 * 10) / 10} KB`
  return `${value} B`
}

const descriptionParts = computed(() => linkifyDescription(task.value?.description || ''))
const canEdit = computed(() => Boolean(task.value?.canEdit))
const filePolicyText = computed(() => {
  if (!task.value) return ''
  const types = task.value.fileTypeCodes.map(fileTypeLabel).filter(Boolean)
  const policy = []
  if (types.length) policy.push(`允许类型：${types.join('、')}`)
  const size = formatBytes(task.value.maxFileSizeBytes)
  if (size) policy.push(`单个文件大小上限：${size}`)
  if (!policy.length) return task.value.requiresFile ? '请按任务要求上传附件' : '附件可选'
  return policy.join('；')
})
const fileAccept = computed(() => {
  if (!task.value?.fileTypeCodes?.length) return undefined
  return task.value.fileTypeCodes.map(fileAcceptValue).filter(Boolean).join(',') || undefined
})

function validateFile(file) {
  const allowed = task.value?.fileTypeCodes?.map(fileTypeCode).filter(Boolean) || []
  const extension = extensionOf(file.name)
  const fileType = String(file.type || '').toUpperCase()
  if (allowed.length && !allowed.some((item) => item === extension || item === fileType)) {
    ElMessage.warning(`附件类型不符合要求，仅允许：${task.value.fileTypeCodes.map(fileTypeLabel).join('、')}`)
    return false
  }
  const maxBytes = Number(task.value?.maxFileSizeBytes)
  if (Number.isFinite(maxBytes) && maxBytes > 0 && file.size > maxBytes) {
    ElMessage.warning(`附件大小不能超过 ${formatBytes(maxBytes)}`)
    return false
  }
  return true
}

function handleFileChange(event) {
  if (!canEdit.value) return
  const files = Array.from(event.target?.files || []).filter(validateFile)
  selectedFiles.value = [...selectedFiles.value, ...files]
  if (fileInput.value) fileInput.value.value = ''
}

function removeFile(index) {
  if (!canEdit.value) return
  selectedFiles.value.splice(index, 1)
}

function clientRequestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `re-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

async function uploadSelectedFiles() {
  const ids = existingFiles.value.map((file) => file.fileId).filter(Boolean)
  if (!canEdit.value) return [...new Set(ids)]
  for (const file of selectedFiles.value) {
    const data = new FormData()
    data.append('file', file)
    const result = await uploadFile(data)
    const id = result?.fileObjectId || result?.fileId || result?.id || result?.data?.fileObjectId
    if (!id) throw new Error(`附件「${file.name}」上传后未返回文件编号`)
    ids.push(id)
  }
  return [...new Set(ids)]
}

async function handleSubmit() {
  if (!canEdit.value) {
    ElMessage.warning('当前任务状态不可编辑或重复提交')
    return
  }
  if (!String(form.content || '').trim()) {
    ElMessage.warning('请填写填报内容')
    return
  }
  if (task.value?.requiresFile && !selectedFiles.value.length && !existingFiles.value.length) {
    ElMessage.warning('请至少上传一个附件')
    return
  }
  submitting.value = true
  try {
    const fileObjectIds = await uploadSelectedFiles()
    await submitTask({
      assignmentId: Number(task.value.assignmentId) || task.value.assignmentId,
      content: String(form.content).trim(),
      fileObjectIds,
      clientRequestId: clientRequestId()
    })
    ElMessage.success('提交成功，已进入审核中')
    await router.push('/redengine/records')
  } catch (error) {
    ElMessage.error(error?.message || '提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.push('/redengine/records')
}

function statusLabel(status) {
  const normalized = String(status || '').trim().toUpperCase()
  return STATUS_LABELS[normalized] || '状态未知'
}

async function load() {
  loadError.value = ''
  if (!assignmentId.value) {
    loadError.value = '任务编号缺失，无法加载任务'
    return
  }
  loading.value = true
  try {
    const result = await getMyTaskAssignment(assignmentId.value)
    task.value = asTaskModel(result)
    form.content = task.value.content || ''
    existingFiles.value = task.value.files
  } catch {
    task.value = null
    loadError.value = '任务加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(load)

defineExpose({
  assignmentId,
  descriptionParts,
  existingFiles,
  fileAccept,
  filePolicyText,
  form,
  goBack,
  handleFileChange,
  handleSubmit,
  load,
  loadError,
  removeFile,
  selectedFiles,
  statusLabel,
  canEdit,
  task,
  uploadSelectedFiles
})
</script>

<style scoped lang="scss">
.temporary-task-entry { padding: 0; }
.entry-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 16px; }
.entry-kicker { margin-bottom: 5px; color: #64748b; font-size: 13px; }
.page-title { margin: 0; color: #1e293b; font-size: 22px; font-weight: 700; }
.status-badge { padding: 4px 10px; border-radius: 4px; color: #92400e; background: #fef9c3; font-size: 12px; font-weight: 600; }
.status-badge.status-branch_pending,
.status-badge.status-branch_approved,
.status-badge.status-org_pending { color: #1d4ed8; background: #dbeafe; }
.status-badge.status-approved,
.status-badge.status-completed { color: #166534; background: #dcfce7; }
.status-badge.status-rejected_by_branch,
.status-badge.status-rejected_by_org { color: #b91c1c; background: #fee2e2; }
.task-card, .entry-card { border: 1px solid #e2e8f0; border-radius: 10px; background: #fff; box-shadow: 0 1px 3px rgba(0, 0, 0, .04); }
.task-card { margin-bottom: 16px; padding: 16px 20px; }
.task-meta { display: flex; flex-wrap: wrap; gap: 24px; margin-bottom: 14px; color: #64748b; font-size: 12px; }
.task-description, .rejection-note { display: flex; gap: 12px; color: #334155; font-size: 13px; line-height: 1.7; white-space: pre-wrap; }
.rejection-note { margin-top: 12px; padding-top: 12px; border-top: 1px solid #f1f5f9; color: #b91c1c; }
.meta-label { flex: 0 0 70px; color: #64748b; font-weight: 600; }
.task-description a { color: #2563eb; }
.entry-card { padding: 20px; }
.entry-card.is-readonly { background: #f8fafc; }
.form-row { display: flex; align-items: flex-start; gap: 14px; margin-bottom: 18px; }
.form-label { flex: 0 0 80px; padding-top: 8px; color: #475569; font-size: 13px; font-weight: 600; }
.form-label.required::before { margin-right: 4px; color: #dc2626; content: '*'; }
.content-input { flex: 1; min-height: 150px; padding: 9px 11px; resize: vertical; border: 1px solid #dcdfe6; border-radius: 4px; outline: none; color: #334155; font: inherit; line-height: 1.6; }
.content-input:focus { border-color: #409eff; }
.file-control { flex: 1; }
.file-control input[type='file'] { color: #475569; font-size: 13px; }
.file-policy { margin-top: 8px; color: #94a3b8; font-size: 12px; }
.file-list { display: flex; flex-direction: column; gap: 6px; margin-top: 9px; }
.file-item { display: flex; justify-content: space-between; max-width: 520px; padding: 7px 10px; border-radius: 4px; background: #f8fafc; color: #475569; font-size: 12px; }
.existing-file small { color: #94a3b8; }
.remove-file { border: 0; background: transparent; color: #dc2626; cursor: pointer; font-size: 12px; }
.entry-actions { display: flex; justify-content: flex-end; gap: 10px; padding-top: 6px; border-top: 1px solid #f1f5f9; }
.entry-loading { padding: 60px 20px; color: #94a3b8; text-align: center; }
.load-error {
  margin-bottom: 16px;
  padding: 10px 14px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #991b1b;
  background: #fef2f2;
  font-size: 13px;
}
</style>
