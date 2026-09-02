<template>
  <div class="warning-container" v-loading="loading">
    <h2 class="page-title">红黄牌预警池</h2>

    <div v-if="loadError" class="load-error" role="alert">{{ loadError }}</div>

    <div class="section-block">
      <h3 class="section-title red-title">🔴 红牌支部（一票否决）</h3>
      <div class="card-grid">
        <div v-for="item in redCards" :key="item.id" class="warning-card red-card">
          <div class="card-top">
            <span class="branch-name">{{ item.branchName }}</span>
            <span class="locked-tag">🔒 已锁定</span>
          </div>
          <div class="score-row">
            <span class="big-score red-score">{{ displayValue(item.score) }}</span>
            <span class="score-unit">分</span>
          </div>
          <div class="dim-breakdown">
            <span class="dim-item">考核期间：{{ displayValue(item.period) }}</span>
          </div>
          <div class="card-warning red-warning">⚠️ 不符合年度考核资格</div>
        </div>
        <div v-if="redCards.length === 0" class="empty-tip">暂无红牌支部</div>
      </div>
    </div>

    <div class="section-block">
      <h3 class="section-title yellow-title">🟡 黄牌支部（预警）</h3>
      <div class="card-grid">
        <div v-for="item in yellowCards" :key="item.id" class="warning-card yellow-card">
          <div class="card-top">
            <span class="branch-name">{{ item.branchName }}</span>
            <span class="warn-tag">⚠ 预警</span>
          </div>
          <div class="score-row">
            <span class="big-score yellow-score">{{ displayValue(item.score) }}</span>
            <span class="score-unit">分</span>
          </div>
          <div class="dim-breakdown">
            <span class="dim-item">考核期间：{{ displayValue(item.period) }}</span>
          </div>
          <div class="card-warning yellow-warning">📌 建议加强整改</div>
        </div>
        <div v-if="yellowCards.length === 0" class="empty-tip">暂无黄牌支部</div>
      </div>
    </div>

    <div v-if="isSystemAdmin" class="section-block overdue-section">
      <h3 class="section-title overdue-title">⏰ 逾期上报待执行扣分</h3>
      <div class="overdue-filter">
        <el-select v-model="query.taskType" clearable size="small" placeholder="任务类型" class="query-select">
          <el-option label="四大维度材料上报" value="FOUR_DIMENSION" />
          <el-option label="临时任务" value="TEMPORARY" />
        </el-select>
        <el-input
          v-model="query.keyword"
          clearable
          size="small"
          placeholder="任务名称/内容/支部/上报人"
          class="query-input"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" size="small" @click="handleSearch">查询</el-button>
        <el-button size="small" @click="handleReset">重置</el-button>
      </div>

      <el-table :data="overdueItems" stripe class="overdue-table">
        <el-table-column prop="taskTypeLabel" label="任务类型" min-width="150" />
        <el-table-column prop="taskName" label="任务名称" min-width="170" />
        <el-table-column prop="taskContent" label="任务内容" min-width="190" show-overflow-tooltip />
        <el-table-column prop="branchName" label="对应党支部" min-width="140" />
        <el-table-column prop="startTime" label="任务开始时间" width="165" />
        <el-table-column prop="endTime" label="任务结束时间" width="165" />
        <el-table-column prop="submitterName" label="上报人" width="110" />
        <el-table-column prop="submittedAt" label="上报时间" width="165" />
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button type="danger" size="small" :disabled="!row.assignmentId" @click="openExecDialog(row)">扣分</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="overdueItems.length === 0" class="empty-tip overdue-empty">暂无逾期上报待执行扣分</div>
      <el-pagination
        v-if="overdueTotal > 0"
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="overdueTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-dialog v-model="execDialog.show" title="执行逾期扣分" width="480px">
      <div class="exec-branch">党支部：{{ execDialog.branchName }}</div>
      <div class="exec-field">
        <label for="deduction-points">扣分分值</label>
        <el-input-number id="deduction-points" v-model="execDialog.form.deductionPoints" :min="1" :max="100" />
      </div>
      <div class="exec-field">
        <label for="deduction-reason">扣分原因</label>
        <el-input
          id="deduction-reason"
          v-model="execDialog.form.reason"
          type="textarea"
          :rows="3"
          placeholder="请输入扣分原因，提交后将记录审计留痕"
        />
      </div>
      <template #footer>
        <el-button @click="execDialog.show = false">取消</el-button>
        <el-button type="danger" :loading="execDialog.submitting" @click="confirmExecute">确认扣分</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { executeTaskOverdue, getTaskOverdueList, getWarningPool } from '@/api/redengine'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const loading = ref(false)
const loadError = ref('')
const redCards = ref([])
const yellowCards = ref([])
const overdueItems = ref([])
const overdueTotal = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const query = reactive({ taskType: '', keyword: '' })

function readSystemAdmin() {
  if (typeof userStore.hasRoleCode === 'function') return userStore.hasRoleCode('SYS_ADMIN')
  const value = userStore.isSystemAdmin
  return value === true || value?.value === true
}

const isSystemAdmin = computed(readSystemAdmin)

function firstDefined(...values) {
  return values.find((value) => value !== null && value !== undefined && value !== '') ?? null
}

function displayValue(value) {
  return value === null || value === undefined || value === '' ? '--' : value
}

const taskTypeLabels = {
  FOUR_DIMENSION: '四大维度材料上报',
  TEMPORARY: '临时任务'
}

function normalizeWarningRows(payload, level, defaultQuarter) {
  const rows = Array.isArray(payload)
    ? payload
    : (payload?.records || payload?.list || payload?.content || [])
  return rows.map((row, index) => ({
    id: firstDefined(row.id, row.orgId, `${level}-${index}`),
    branchName: firstDefined(row.branchName, row.branch, row.orgName, row.partyOrgName, '--'),
    score: firstDefined(row.score, row.finalScore),
    period: firstDefined(row.quarter, row.period, row.assessmentPeriod, defaultQuarter),
    level
  })).sort((left, right) => {
    const leftScore = Number(left.score)
    const rightScore = Number(right.score)
    if (Number.isNaN(leftScore)) return 1
    if (Number.isNaN(rightScore)) return -1
    return leftScore - rightScore
  })
}

function normalizePageResult(payload) {
  if (Array.isArray(payload)) return { records: payload, total: payload.length }
  const records = payload?.records || payload?.list || payload?.content || payload?.rows || []
  return {
    records: Array.isArray(records) ? records : [],
    total: Number(payload?.total ?? payload?.totalCount ?? records.length)
  }
}

function normalizeOverdueRow(row, index) {
  const submitter = firstDefined(row.submitterName, row.submitter, row.reporterName)
  const submittedAt = firstDefined(row.submittedAt, row.submitTime, row.submitDate)
  const isUnreported = row.isUnreported === true
    || row.unreported === true
    || row.status === 'UNREPORTED'
    || row.status === 'DRAFT'
    || row.status === 0
    || row.status === '0'
    || (!submitter && !submittedAt)
  const taskType = firstDefined(row.taskType, row.businessType, '--')
  return {
    id: firstDefined(row.id, row.overdueId, `overdue-${index}`),
    assignmentId: firstDefined(row.assignmentId, row.submitId, row.submissionId, row.id),
    taskType,
    taskTypeLabel: firstDefined(row.taskTypeLabel, row.businessTypeLabel, taskTypeLabels[taskType], taskType),
    taskName: firstDefined(row.taskName, row.taskTitle, row.projectName, '--'),
    taskContent: firstDefined(row.taskContent, row.taskDescription, row.content, row.description, '--'),
    branchName: firstDefined(row.branchName, row.branch, row.orgName, row.partyOrgName, '--'),
    startTime: firstDefined(row.taskStartAt, row.taskStartTime, row.startTime, row.beginTime, row.temporaryStartTime, '--'),
    endTime: firstDefined(row.taskEndAt, row.taskEndTime, row.endTime, row.finishTime, row.temporaryEndTime, '--'),
    submitterName: isUnreported ? '--' : displayValue(submitter),
    submittedAt: isUnreported ? '--' : displayValue(submittedAt),
    deductionPoints: firstDefined(row.deductionPoints, 5)
  }
}

function overdueQuery() {
  return Object.fromEntries(Object.entries(query).filter(([, value]) => value !== null && value !== undefined && value !== ''))
}

async function loadOverdue() {
  const response = await getTaskOverdueList({ pageNo: pageNo.value, pageSize: pageSize.value, ...overdueQuery() })
  const result = normalizePageResult(response)
  overdueItems.value = result.records.map(normalizeOverdueRow)
  overdueTotal.value = result.total
}

async function reload() {
  loading.value = true
  loadError.value = ''
  redCards.value = []
  yellowCards.value = []
  overdueItems.value = []
  overdueTotal.value = 0
  try {
    const warningPool = await getWarningPool()
    const quarter = firstDefined(warningPool?.quarter, warningPool?.currentQuarter)
    redCards.value = normalizeWarningRows(
      warningPool?.redBranches || warningPool?.redCards || warningPool?.red,
      'red',
      quarter
    )
    yellowCards.value = normalizeWarningRows(
      warningPool?.yellowBranches || warningPool?.yellowCards || warningPool?.yellow,
      'yellow',
      quarter
    )
    if (isSystemAdmin.value) {
      try {
        await loadOverdue()
      } catch {
        overdueItems.value = []
        overdueTotal.value = 0
        loadError.value = '逾期扣分数据加载失败，请稍后重试'
      }
    }
  } catch {
    redCards.value = []
    yellowCards.value = []
    loadError.value = '预警数据加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const execDialog = reactive({
  show: false,
  submitting: false,
  assignmentId: null,
  branchName: '',
  form: { deductionPoints: 5, reason: '' }
})

function openExecDialog(item) {
  execDialog.assignmentId = item.assignmentId
  execDialog.branchName = item.branchName
  execDialog.form = {
    deductionPoints: Number(item.deductionPoints || 5),
    reason: ''
  }
  execDialog.show = true
}

async function confirmExecute() {
  const reason = String(execDialog.form.reason || '').trim()
  if (!reason) {
    ElMessage.warning('请填写扣分原因')
    return
  }
  const deductionPoints = Number(execDialog.form.deductionPoints)
  if (!Number.isFinite(deductionPoints) || deductionPoints <= 0) {
    ElMessage.warning('请输入有效的扣分分值')
    return
  }
  execDialog.submitting = true
  try {
    await executeTaskOverdue({
      assignmentId: execDialog.assignmentId,
      deductionPoints,
      reason
    })
    ElMessage.success('已执行扣分')
    execDialog.show = false
    await loadOverdue()
  } catch (error) {
    ElMessage.error(error?.message || '执行扣分失败')
  } finally {
    execDialog.submitting = false
  }
}

async function handleSearch() {
  pageNo.value = 1
  await refreshOverdue()
}

async function refreshOverdue() {
  try {
    await loadOverdue()
  } catch {
    overdueItems.value = []
    overdueTotal.value = 0
    loadError.value = '逾期扣分数据加载失败，请稍后重试'
  }
}

async function handleReset() {
  query.taskType = ''
  query.keyword = ''
  await handleSearch()
}

async function handlePageChange(value) {
  pageNo.value = value
  await refreshOverdue()
}

async function handleSizeChange(value) {
  pageSize.value = value
  pageNo.value = 1
  await refreshOverdue()
}

onMounted(reload)
</script>

<style scoped lang="scss">
.warning-container {
  padding: 24px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 28px;
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

.section-block {
  margin-bottom: 32px;
}

.section-title {
  font-size: 17px;
  font-weight: 600;
  margin: 0 0 16px;
}

.red-title {
  color: #dc2626;
}

.yellow-title {
  color: #ca8a04;
}

.overdue-title {
  color: #b45309;
}

.empty-tip {
  color: #94a3b8;
  font-size: 13px;
  padding: 12px 0;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.warning-card {
  border-radius: 8px;
  padding: 20px;
  transition: box-shadow 0.2s;
}

.warning-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.red-card {
  background: #fee2e2;
  border: 2px solid #dc2626;
  border-top-width: 4px;
}

.yellow-card {
  background: #fef9c3;
  border: 2px solid #ca8a04;
  border-top-width: 4px;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.branch-name {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}

.locked-tag,
.warn-tag {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
}

.locked-tag {
  color: #dc2626;
  background: #fecaca;
  border: 1px solid #dc2626;
}

.warn-tag {
  color: #ca8a04;
  background: #fef08a;
  border: 1px solid #ca8a04;
}

.score-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 12px;
}

.big-score {
  font-size: 28px;
  font-weight: 700;
}

.red-score {
  color: #dc2626;
}

.yellow-score {
  color: #ca8a04;
}

.score-unit {
  font-size: 14px;
  color: #64748b;
}

.dim-breakdown {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.dim-item {
  font-size: 12px;
  color: #475569;
  background: rgba(255, 255, 255, 0.6);
  padding: 2px 8px;
  border-radius: 4px;
}

.card-warning {
  font-size: 13px;
  font-weight: 600;
  padding: 8px 12px;
  border-radius: 6px;
}

.red-warning {
  color: #991b1b;
  background: #fecaca;
}

.yellow-warning {
  color: #854d0e;
  background: #fde68a;
}

.overdue-filter {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.query-select {
  width: 180px;
}

.query-input {
  width: 190px;
}

.overdue-table {
  width: 100%;
}

.overdue-empty {
  padding: 14px 0;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.exec-branch {
  color: #64748b;
  font-size: 13px;
  margin-bottom: 18px;
}

.exec-field {
  margin-bottom: 16px;

  label {
    display: block;
    color: #475569;
    font-size: 13px;
    margin-bottom: 6px;
  }
}

@media (max-width: 768px) {
  .warning-container {
    padding: 16px;
  }

  .overdue-filter {
    align-items: stretch;
    flex-direction: column;
  }

  .query-select,
  .query-input {
    width: 100%;
  }
}
</style>
