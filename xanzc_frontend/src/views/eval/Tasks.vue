<template>
  <!-- 评价任务管理页面（管理端：统一列表 = 规则任务 + 导入批次） -->
  <div class="eval-tasks-page">
    <div class="page-h">
      <h1>评价任务</h1>
      <span class="desc">发起评价活动 · 管理任务生命周期</span>
      <div class="actions">
        <el-button type="primary" @click="openWizard">新增待处理任务</el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="filter.status" placeholder="全部状态" clearable style="width: 140px" @change="handleFilterChange">
        <el-option label="全部" value="" />
        <el-option label="进行中" :value="0" />
        <el-option label="已结束" :value="1" />
        <el-option label="草稿" :value="2" />
      </el-select>
      <el-input v-model="filter.keyword" placeholder="搜索任务名称/批次/创建人" clearable style="width: 240px; margin-left: 12px"
        @keyup.enter="handleFilterChange" @clear="handleFilterChange">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button style="margin-left: 8px" @click="handleFilterChange">搜索</el-button>
    </div>

    <!-- 统一任务列表 -->
    <el-table v-loading="tableLoading" :data="tableData" border stripe style="width: 100%; margin-top: 16px">
      <el-table-column label="来源" width="100" align="center">
        <template #default="{ row }">
          <span class="tag-type">{{ row.sourceType === 'AUTO' ? '自动生成' : '手工导入' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="任务类型" width="100" align="center">
        <template #default="{ row }">
          {{ row.taskType === 'EVAL' ? '评价任务' : (row.taskType === 'REWARD' ? '奖励分配' : row.taskType) }}
        </template>
      </el-table-column>
      <el-table-column prop="taskName" label="任务名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="开始时间" width="170" align="center">
        <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="截止时间" width="170" align="center">
        <template #default="{ row }">{{ formatDateTime(row.endTime) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <span v-if="row.status === 2" class="tag-warning">草稿</span>
          <span v-else-if="row.status === 0" class="tag-success">进行中</span>
          <span v-else-if="row.status === 1" class="tag-info">已结束</span>
          <span v-else class="tag-info">{{ row.status }}</span>
        </template>
      </el-table-column>
      <el-table-column label="明细数" width="80" align="center">
        <template #default="{ row }">{{ row.itemCount != null ? row.itemCount : '—' }}</template>
      </el-table-column>
      <el-table-column prop="createBy" label="创建人" width="110" align="center" />
      <el-table-column label="操作" width="240" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDetail(row)">详情</el-button>
          <el-button v-if="row.status === 2" type="success" link @click="handlePublish(row)">发布</el-button>
          <el-button v-if="row.sourceType === 'AUTO' && row.status === 0" type="danger" link @click="handleCloseTask(row)">关闭</el-button>
          <el-button type="primary" link @click="handleExport(row)">导出</el-button>
          <el-button v-if="isDeadlinePassed(row)" type="danger" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="pager.pageNo" v-model:page-size="pager.pageSize"
        :total="pager.total" :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper" background
        @size-change="loadList" @current-change="loadList"
      />
    </div>

    <!-- ===== 新建任务弹窗 ===== -->
    <el-dialog v-model="createDialog.visible" title="新建评价任务" width="680px"
      :close-on-click-modal="false" @closed="resetCreateForm">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="110px" label-position="right">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="createForm.taskName" placeholder="请输入任务名称" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="截止时间" prop="endTime">
          <el-date-picker v-model="createForm.endTime" type="datetime" placeholder="请选择截止时间"
            value-format="YYYY-MM-DD HH:mm:ss" :disabled-date="disabledDate" style="width: 100%" />
        </el-form-item>
        <el-form-item label="被评价人" prop="beEvalUserIds">
          <el-select v-model="createForm.beEvalUserIds" multiple filterable remote reserve-keyword
            placeholder="输入姓名或账号搜索用户" :remote-method="remoteSearchUsers" :loading="userSearchLoading" style="width: 100%">
            <el-option v-for="user in userOptions" :key="user.userId"
              :label="`${user.userchnname}（${user.username}）`" :value="user.userId" />
          </el-select>
          <div class="form-tip">已选 {{ createForm.beEvalUserIds.length }} 人</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="createDialog.submitting" @click="handleCreateTask">保存</el-button>
      </template>
    </el-dialog>

    <!-- ===== 新增待处理任务向导 ===== -->
    <el-dialog v-model="wizard.visible" title="新增待处理任务" width="660px"
      :close-on-click-modal="false" @closed="resetWizard">
      <el-form label-width="100px" label-position="right">
        <el-form-item label="任务来源">
          <el-select v-model="wizard.source" placeholder="请选择来源" style="width: 100%" @change="onSourceChange">
            <el-option v-for="o in sourceOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <template v-if="wizard.source === 'AUTO'">
          <el-alert type="info" :closable="false" show-icon
            title="自动生成沿用现有规则驱动方式，按被评价人发起。" style="margin-bottom: 12px" />
          <el-button type="primary" @click="goAutoCreate">前往自动生成</el-button>
        </template>
        <template v-else-if="wizard.source === 'IMPORT'">
          <el-form-item label="导入类型">
            <el-select v-model="wizard.importType" placeholder="请选择导入类型" style="width: 100%">
              <el-option v-for="o in importTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-alert v-if="wizard.importType === 'REWARD'" type="warning" :closable="false" show-icon
            title="奖励分配导入本期暂未开放。" />
          <template v-if="wizard.importType === 'EVAL'">
            <el-form-item label="任务名称" required>
              <el-input v-model="wizard.taskName" placeholder="请输入任务名称" maxlength="64" show-word-limit />
            </el-form-item>
            <el-form-item label="截止时间">
              <el-date-picker v-model="wizard.deadline" type="datetime" placeholder="选择打分截止时间"
                value-format="YYYY-MM-DD HH:mm:ss" :disabled-date="disabledDate" style="width: 100%" />
            </el-form-item>
            <el-form-item label="导入文件">
              <div style="width: 100%">
                <div style="margin-bottom: 8px">
                  <el-button size="small" @click="doDownloadTpl">📥 下载导入模板</el-button>
                  <span class="form-tip">10 列：被打分人 编号/姓名/部门/标签 + 打分人 编号/姓名/标签/部门 + 权重标签 + 评价类型</span>
                </div>
                <el-upload ref="wizardUploaderRef" drag action="#" :auto-upload="false"
                  :show-file-list="true" :limit="1" :on-change="onWizardFilePick" accept=".xlsx">
                  <div class="el-upload__text">点击或拖拽 <em>.xlsx</em> 到此处</div>
                </el-upload>
              </div>
            </el-form-item>
            <div v-if="importErrors.length" class="imp-errors">
              <div class="err-title">导入失败，请修正后重传（共 {{ importErrors.length }} 条问题）：</div>
              <el-table :data="importErrors" size="small" border max-height="220">
                <el-table-column prop="row" label="行号" width="80" />
                <el-table-column prop="message" label="原因" min-width="320" />
              </el-table>
            </div>
          </template>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="wizard.visible = false">取消</el-button>
        <el-button v-if="wizard.source === 'IMPORT' && wizard.importType === 'EVAL'"
          type="primary" :loading="importing" :disabled="!wizardFile || !wizard.deadline || !wizard.taskName" @click="doImportAssign">
          开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- ===== 规则任务详情弹窗 ===== -->
    <el-dialog v-model="ruleDetail.visible" title="任务详情" width="800px" :close-on-click-modal="false">
      <div v-if="ruleDetail.data" v-loading="ruleDetail.loading">
        <el-descriptions :column="2" border class="detail-desc">
          <el-descriptions-item label="任务名称">{{ ruleDetail.data.task.taskName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <span :class="ruleDetail.data.task.status === 0 ? 'tag-success' : 'tag-info'">
              {{ ruleDetail.data.task.status === 0 ? '进行中' : '已结束' }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatDateTime(ruleDetail.data.task.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="截止时间">{{ formatDateTime(ruleDetail.data.task.endTime) }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ ruleDetail.data.task.creatorName }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-section-title">被评价人列表</div>
        <el-table :data="ruleDetail.data.targets" border stripe size="small" style="width: 100%; margin-top: 8px">
          <el-table-column prop="targetId" label="记录ID" width="80" align="center" />
          <el-table-column prop="beEvalUserId" label="被评价人工号" width="120" align="center" />
          <el-table-column prop="beEvalUserName" label="被评价人" min-width="110" />
          <el-table-column prop="ruleId" label="规则ID" width="90" align="center" />
          <el-table-column label="最终得分" width="100" align="center">
            <template #default="{ row: t }">
              <span v-if="t.finalScore !== null && t.finalScore !== undefined">{{ t.finalScore }}</span>
              <span v-else class="score-empty">—</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button v-if="ruleDetail.data && ruleDetail.data.task.status === 0"
          type="danger" :loading="ruleDetail.closing" @click="handleCloseTaskFromDetail">关闭任务</el-button>
        <el-button @click="ruleDetail.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ===== 导入批次详情弹窗 ===== -->
    <el-dialog v-model="batchDetail.visible" title="批次详情" width="900px" :close-on-click-modal="false">
      <div v-if="batchDetail.data" v-loading="batchDetail.loading">
        <el-descriptions :column="2" border class="detail-desc">
          <el-descriptions-item label="批次ID">{{ batchDetail.data.batch.batchId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <span v-if="batchDetail.data.batch.status === 2" class="tag-warning">草稿</span>
            <span v-else-if="batchDetail.data.batch.status === 0" class="tag-success">进行中</span>
            <span v-else-if="batchDetail.data.batch.status === 1" class="tag-info">已结束</span>
          </el-descriptions-item>
          <el-descriptions-item label="任务类型">{{ batchDetail.data.batch.taskType === 'EVAL' ? '评价任务' : batchDetail.data.batch.taskType }}</el-descriptions-item>
          <el-descriptions-item label="截止时间">{{ formatDateTime(batchDetail.data.batch.deadline) }}</el-descriptions-item>
          <el-descriptions-item label="导入人">{{ batchDetail.data.batch.createBy }}</el-descriptions-item>
          <el-descriptions-item label="导入时间">{{ formatDateTime(batchDetail.data.batch.createTime) }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-section-title">评价明细</div>
        <el-table :data="batchDetail.data.items.records" border stripe size="small" style="width: 100%; margin-top: 8px">
          <el-table-column label="打分人工号" width="110" align="center">
            <template #default="{ row }">{{ row.evalUserUsername || row.evalUserId }}</template>
          </el-table-column>
          <el-table-column prop="evalUserName" label="打分人" min-width="100" />
          <el-table-column prop="evalUserDept" label="打分人部门" min-width="120" />
          <el-table-column label="被打分人工号" width="110" align="center">
            <template #default="{ row }">{{ row.beEvalUserUsername || row.beEvalUserId }}</template>
          </el-table-column>
          <el-table-column prop="beEvalUserName" label="被打分人" min-width="100" />
          <el-table-column prop="beEvalDept" label="被打分人部门" min-width="120" />
          <el-table-column prop="weightTag" label="权重" width="70" align="center" />
          <el-table-column label="评价类型" width="90" align="center">
            <template #default="{ row: it }">{{ it.scoreType === 'NUM' ? '数值' : it.scoreType === 'GRADE' ? '等级' : it.scoreType }}</template>
          </el-table-column>
          <el-table-column label="分数" width="80" align="center">
            <template #default="{ row: it }">
              <span v-if="it.submitted === 1" class="score-submitted">{{ it.score }}</span>
              <span v-else class="score-empty">—</span>
            </template>
          </el-table-column>
          <el-table-column label="提交状态" width="90" align="center">
            <template #default="{ row: it }">
              <span :class="it.submitted === 1 ? 'tag-success' : 'tag-info'">{{ it.submitted === 1 ? '已提交' : '未提交' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="submitTime" label="提交时间" width="160" align="center">
            <template #default="{ row: it }">{{ formatDateTime(it.submitTime) }}</template>
          </el-table-column>
        </el-table>
        <div class="pagination-wrap" style="margin-top: 12px">
          <el-pagination
            v-model:current-page="batchDetail.itemPage.pageNo" v-model:page-size="batchDetail.itemPage.pageSize"
            :total="batchDetail.data.items.total" :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper" background small
            @size-change="loadBatchDetailItems" @current-change="loadBatchDetailItems"
          />
        </div>
      </div>
      <template #footer>
        <el-button v-if="batchDetail.data && batchDetail.data.batch.status === 2"
          type="success" :loading="batchDetail.publishing" @click="handlePublishFromDetail">确认发布</el-button>
        <el-button @click="handleExportById(batchDetail.data?.batch?.batchId, 'batch')">导出 Excel</el-button>
        <el-button @click="batchDetail.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  listUnifiedTasks, getTaskDetail, createTask, closeTask,
  downloadAssignTemplate, importAssign,
  getAssignBatchDetail, publishAssignBatch, exportAssignBatchItems, exportRuleTask,
  deleteUnifiedTask
} from '@/api/eval'
import { listUsers } from '@/api/users'
import { useDict } from '@/composables/useDict'

const { options: sourceOptions } = useDict('EVAL_PENDING_SOURCE')
const { options: importTypeOptions } = useDict('EVAL_IMPORT_TYPE')

// ===================== 统一列表 =====================

const filter = reactive({ status: '', keyword: '' })
const pager = reactive({ pageNo: 1, pageSize: 20, total: 0 })
const tableLoading = ref(false)
const tableData = ref([])

async function loadList() {
  tableLoading.value = true
  try {
    const params = { page: pager.pageNo, pageSize: pager.pageSize }
    if (filter.status !== '') params.status = filter.status
    if (filter.keyword) params.keyword = filter.keyword
    const res = await listUnifiedTasks(params)
    tableData.value = res.records || []
    pager.total = res.total || 0
  } catch (e) {
    ElMessage.error('加载任务列表失败：' + (e?.message || '未知错误'))
  } finally {
    tableLoading.value = false
  }
}

function handleFilterChange() {
  pager.pageNo = 1
  loadList()
}

// ===================== 日期格式化 =====================

function formatDateTime(val) {
  if (!val) return '—'
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(val)) return val
  const d = new Date(val)
  if (isNaN(d.getTime())) return val
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

// ===================== 详情弹窗 =====================

/** 规则任务详情 */
const ruleDetail = reactive({ visible: false, loading: false, closing: false, data: null })

/** 导入批次详情 */
const batchDetail = reactive({
  visible: false, loading: false, publishing: false,
  data: null, itemPage: { pageNo: 1, pageSize: 50 }
})

/** 打开详情：按 sourceType 路由到不同详情弹窗 */
async function openDetail(row) {
  if (row.sourceType === 'AUTO' && row.taskType === 'EVAL') {
    ruleDetail.visible = true
    ruleDetail.loading = true
    ruleDetail.data = null
    try {
      ruleDetail.data = await getTaskDetail(row.sourceId)
    } catch (e) {
      ElMessage.error('加载详情失败：' + (e?.message || '未知错误'))
      ruleDetail.visible = false
    } finally {
      ruleDetail.loading = false
    }
  } else {
    batchDetail.visible = true
    batchDetail.loading = true
    batchDetail.data = null
    batchDetail.itemPage.pageNo = 1
    batchDetail.itemPage.pageSize = 50
    try {
      batchDetail.data = await getAssignBatchDetail(row.sourceId, { page: 1, pageSize: 50 })
    } catch (e) {
      ElMessage.error('加载批次详情失败：' + (e?.message || '未知错误'))
      batchDetail.visible = false
    } finally {
      batchDetail.loading = false
    }
  }
}

async function loadBatchDetailItems() {
  if (!batchDetail.data) return
  batchDetail.loading = true
  try {
    batchDetail.data = await getAssignBatchDetail(batchDetail.data.batch.batchId,
      { page: batchDetail.itemPage.pageNo, pageSize: batchDetail.itemPage.pageSize })
  } catch (e) {
    ElMessage.error('加载明细失败：' + (e?.message || '未知错误'))
  } finally {
    batchDetail.loading = false
  }
}

// ===================== 发布 =====================

async function handlePublish(row) {
  try {
    await ElMessageBox.confirm(
      `确认发布「${row.taskName}」？发布后打分人即可看到待处理任务。`,
      '确认发布', { type: 'warning', confirmButtonText: '确认发布', cancelButtonText: '取消' }
    )
    await publishAssignBatch(row.sourceId)
    ElMessage.success('已发布')
    loadList()
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error('发布失败：' + (e?.message || '未知错误'))
  }
}

async function handlePublishFromDetail() {
  if (!batchDetail.data) return
  batchDetail.publishing = true
  try {
    await publishAssignBatch(batchDetail.data.batch.batchId)
    ElMessage.success('批次已发布')
    batchDetail.visible = false
    loadList()
  } catch (e) {
    ElMessage.error('发布失败：' + (e?.message || '未知错误'))
  } finally {
    batchDetail.publishing = false
  }
}

// ===================== 导出 =====================

async function handleExport(row) {
  const type = (row.sourceType === 'AUTO' && row.taskType === 'EVAL') ? 'rule' : 'batch'
  await handleExportById(row.sourceId, type)
}

async function handleExportById(id, type) {
  try {
    const blob = type === 'rule' ? await exportRuleTask(id) : await exportAssignBatchItems(id)
    const filename = type === 'rule'
      ? `规则任务明细_${id}.xlsx`
      : `评价明细_batch_${id}.xlsx`
    saveBlob(blob, filename)
  } catch (e) { /* http.js 已提示 */ }
}

// ===================== 关闭任务 =====================

async function handleCloseTask(row) {
  try {
    await ElMessageBox.confirm(
      `确认关闭任务「${row.taskName}」？关闭后无法重新开启。`,
      '关闭确认', { type: 'warning', confirmButtonText: '确认关闭', cancelButtonText: '取消' }
    )
    await closeTask(row.sourceId)
    ElMessage.success('任务已关闭')
    loadList()
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error('关闭任务失败：' + (e?.message || '未知错误'))
  }
}

async function handleCloseTaskFromDetail() {
  if (!ruleDetail.data) return
  const taskName = ruleDetail.data.task.taskName
  const taskId = ruleDetail.data.task.taskId
  try {
    await ElMessageBox.confirm(
      `确认关闭任务「${taskName}」？关闭后无法重新开启。`,
      '关闭确认', { type: 'warning', confirmButtonText: '确认关闭', cancelButtonText: '取消' }
    )
    ruleDetail.closing = true
    await closeTask(taskId)
    ElMessage.success('任务已关闭')
    ruleDetail.data = await getTaskDetail(taskId)
    loadList()
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error('关闭任务失败：' + (e?.message || '未知错误'))
  } finally {
    ruleDetail.closing = false
  }
}

// ===================== 新建任务弹窗 =====================

const createDialog = reactive({ visible: false, submitting: false })
const createFormRef = ref(null)
const createForm = reactive({ taskName: '', endTime: '', beEvalUserIds: [] })
const createRules = {
  taskName: [
    { required: true, message: '请输入任务名称', trigger: 'blur' },
    { max: 64, message: '名称不超过64个字符', trigger: 'blur' }
  ],
  endTime: [
    { required: true, message: '请选择截止时间', trigger: 'change' },
    {
      validator: (rule, value, callback) => {
        if (!value) { callback(); return }
        if (new Date(value) <= new Date()) { callback(new Error('截止时间必须晚于当前时间')) }
        else { callback() }
      }, trigger: 'change'
    }
  ],
  beEvalUserIds: [{
    validator: (rule, value, callback) => {
      if (!value || value.length === 0) { callback(new Error('请至少选择一位被评价人')) }
      else { callback() }
    }, trigger: 'change'
  }]
}

function disabledDate(time) { return time.getTime() < Date.now() - 86400000 }

const userOptions = ref([])
const userSearchLoading = ref(false)

async function remoteSearchUsers(query) {
  if (!query) { userOptions.value = []; return }
  userSearchLoading.value = true
  try {
    const users = await listUsers({ keyword: query })
    userOptions.value = Array.isArray(users) ? users : (users.records || [])
  } catch (e) {
    ElMessage.error('搜索用户失败：' + (e?.message || '未知错误'))
  } finally { userSearchLoading.value = false }
}

function openCreateDialog() { createDialog.visible = true }

function resetCreateForm() {
  createForm.taskName = ''
  createForm.endTime = ''
  createForm.beEvalUserIds = []
  userOptions.value = []
  createFormRef.value?.clearValidate()
}

async function handleCreateTask() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  createDialog.submitting = true
  try {
    await createTask({ taskName: createForm.taskName, endTime: createForm.endTime, beEvalUserIds: createForm.beEvalUserIds })
    ElMessage.success('任务创建成功')
    createDialog.visible = false
    loadList()
  } catch (e) {
    ElMessage.error('创建失败：' + (e?.message || '未知错误'))
  } finally { createDialog.submitting = false }
}

// ===================== 导入向导 =====================

const wizard = reactive({ visible: false, source: '', importType: '', taskName: '', deadline: '' })
const wizardFile = ref(null)
const wizardUploaderRef = ref(null)
const importErrors = ref([])
const importing = ref(false)

function openWizard() { wizard.visible = true }

function resetWizard() {
  wizard.source = ''
  wizard.importType = ''
  wizard.taskName = ''
  wizard.deadline = ''
  wizardFile.value = null
  importErrors.value = []
  wizardUploaderRef.value?.clearFiles()
}

function onSourceChange() { wizard.importType = ''; wizard.taskName = ''; wizard.deadline = ''; wizardFile.value = null; importErrors.value = [] }

function goAutoCreate() { wizard.visible = false; openCreateDialog() }

async function doDownloadTpl() {
  try { saveBlob(await downloadAssignTemplate(), '评价任务导入模板.xlsx') } catch (e) { /* */ }
}

function onWizardFilePick(uploadFile) { wizardFile.value = uploadFile.raw || null }

async function doImportAssign() {
  if (!wizardFile.value || !wizard.deadline || !wizard.taskName) return
  importing.value = true
  importErrors.value = []
  try {
    const res = await importAssign(wizardFile.value, 'EVAL', wizard.taskName, wizard.deadline)
    // 错误数超过阈值：后端回 CSV 文件流，直接下载给用户查看具体行号与原因
    if (res && res.csv) {
      saveBlob(res.blob, '导入错误明细.csv')
      ElMessage.warning('错误数据较多，已下载「导入错误明细.csv」，请打开查看具体行号与错误原因')
      return
    }
    if (res && res.success) {
      ElMessage.success(`导入成功 ${res.importedCount} 条，已生成草稿批次`)
      wizard.visible = false
      loadList()
    } else {
      importErrors.value = (res && res.errors) || []
      ElMessage.error('导入未通过校验，请查看错误明细')
    }
  } catch (e) { ElMessage.error('导入失败：' + (e?.message || '未知错误')) }
  finally { importing.value = false }
}

// ===================== 删除 =====================

function isDeadlinePassed(row) {
  if (!row.endTime) return false
  return new Date(row.endTime) <= new Date()
}

async function handleDelete(row) {
  const label = row.sourceType === 'AUTO' ? '规则任务' : '导入批次'
  try {
    await ElMessageBox.confirm(
      `确认删除${label}「${row.taskName}」？删除后数据无法恢复，请确认。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
    await deleteUnifiedTask(row.sourceType === 'AUTO' ? 'AUTO' : 'IMPORT', row.sourceId)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error('删除失败：' + (e?.message || '未知错误'))
  }
}

// ===================== 生命周期 =====================

onMounted(() => { loadList() })
</script>

<style lang="scss" scoped>
$text-1: #1a1a2e;
$text-2: #4a5568;
$text-3: #a0aec0;
$border-1: #e2e8f0;
$bg-soft: #f7fafc;
$primary: #4361ee;
$danger: #e53e3e;

.eval-tasks-page {
  padding: 24px;
  background: #fff;
  min-height: 100%;

  .page-h {
    display: flex; align-items: center; margin-bottom: 20px; gap: 12px;
    h1 { font-size: 20px; font-weight: 600; color: $text-1; margin: 0; }
    .desc { font-size: 13px; color: $text-3; }
    .actions { margin-left: auto; }
  }

  .filter-bar {
    display: flex; align-items: center; padding: 12px 16px;
    background: $bg-soft; border-radius: 8px; border: 1px solid $border-1;
  }

  .pagination-wrap { display: flex; justify-content: flex-end; margin-top: 20px; }

  .tag-success {
    display: inline-block; padding: 2px 10px; border-radius: 12px; font-size: 12px;
    background: #e6f4ea; color: #1e7e34; border: 1px solid #b7dfbf;
  }
  .tag-info {
    display: inline-block; padding: 2px 10px; border-radius: 12px; font-size: 12px;
    background: #f0f0f0; color: #666; border: 1px solid #d9d9d9;
  }
  .tag-warning {
    display: inline-block; padding: 2px 10px; border-radius: 12px; font-size: 12px;
    background: #fff7e6; color: #d46b08; border: 1px solid #ffd591;
  }
  .tag-danger {
    display: inline-block; padding: 2px 10px; border-radius: 12px; font-size: 12px;
    background: #fff1f0; color: $danger; border: 1px solid #ffa39e;
  }

  .form-tip { font-size: 12px; color: $text-3; margin-top: 4px; margin-left: 8px; }

  .imp-errors {
    margin-top: 12px;
    .err-title { font-size: 13px; color: $danger; margin-bottom: 6px; }
  }

  .detail-desc { margin-bottom: 16px; }

  .detail-section-title {
    font-size: 14px; font-weight: 600; color: $text-1;
    padding: 8px 0 4px 0; border-bottom: 1px solid $border-1; margin-bottom: 8px;
  }

  .score-empty { color: $text-3; }

  .tag-type {
    display: inline-block; padding: 2px 10px; border-radius: 12px; font-size: 12px;
    background: #eef2ff; color: $primary; border: 1px solid #c7d2fe;
  }

  .score-submitted {
    display: inline-flex; align-items: center; gap: 4px; font-weight: 600; color: #1e7e34;
  }
}

.pagination-wrap :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
</style>
