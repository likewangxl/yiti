<template>
  <!-- 评价任务管理页面（管理端） -->
  <div class="eval-tasks-page">
    <!-- 页头 -->
    <div class="page-h">
      <h1>评价任务</h1>
      <span class="desc">发起评价活动 · 管理任务生命周期</span>
      <div class="actions">
        <el-button type="primary" @click="openWizard">新增待处理任务</el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select
        v-model="filter.status"
        placeholder="全部状态"
        clearable
        style="width: 140px"
        @change="handleFilterChange"
      >
        <el-option label="全部" value="" />
        <el-option label="进行中" :value="0" />
        <el-option label="已结束" :value="1" />
      </el-select>
      <el-input
        v-model="filter.keyword"
        placeholder="搜索任务名称"
        clearable
        style="width: 220px; margin-left: 12px"
        @keyup.enter="handleFilterChange"
        @clear="handleFilterChange"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button style="margin-left: 8px" @click="handleFilterChange">搜索</el-button>
    </div>

    <!-- 任务列表表格 -->
    <el-table
      v-loading="tableLoading"
      :data="tableData"
      border
      stripe
      style="width: 100%; margin-top: 16px"
    >
      <el-table-column prop="taskId" label="任务ID" width="90" align="center" />
      <el-table-column prop="taskName" label="任务名称" min-width="160" />
      <el-table-column prop="startTime" label="开始时间" width="170" align="center">
        <template #default="{ row }">
          {{ formatDateTime(row.startTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="endTime" label="截止时间" width="170" align="center">
        <template #default="{ row }">
          {{ formatDateTime(row.endTime) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <span :class="row.status === 0 ? 'tag-success' : 'tag-info'">
            {{ row.status === 0 ? '进行中' : '已结束' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="creatorName" label="创建人" width="110" align="center" />
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDetailDialog(row)">详情</el-button>
          <el-button
            v-if="row.status === 0"
            type="danger"
            link
            @click="handleCloseTask(row)"
          >关闭</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="pager.pageNo"
        v-model:page-size="pager.pageSize"
        :total="pager.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="loadList"
        @current-change="loadList"
      />
    </div>

    <!-- ===== 新建任务弹窗 ===== -->
    <el-dialog
      v-model="createDialog.visible"
      title="新建评价任务"
      width="680px"
      :close-on-click-modal="false"
      @closed="resetCreateForm"
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="110px"
        label-position="right"
      >
        <!-- 任务名称 -->
        <el-form-item label="任务名称" prop="taskName">
          <el-input
            v-model="createForm.taskName"
            placeholder="请输入任务名称"
            maxlength="64"
            show-word-limit
          />
        </el-form-item>

        <!-- 截止时间 -->
        <el-form-item label="截止时间" prop="endTime">
          <el-date-picker
            v-model="createForm.endTime"
            type="datetime"
            placeholder="请选择截止时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled-date="disabledDate"
            style="width: 100%"
          />
        </el-form-item>

        <!-- 被评价人 -->
        <el-form-item label="被评价人" prop="beEvalUserIds">
          <el-select
            v-model="createForm.beEvalUserIds"
            multiple
            filterable
            remote
            reserve-keyword
            placeholder="输入姓名或账号搜索用户"
            :remote-method="remoteSearchUsers"
            :loading="userSearchLoading"
            style="width: 100%"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.userId"
              :label="`${user.userchnname}（${user.username}）`"
              :value="user.userId"
            />
          </el-select>
          <div class="form-tip">已选 {{ createForm.beEvalUserIds.length }} 人</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="createDialog.submitting" @click="handleCreateTask">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- ===== 新增待处理任务向导 ===== -->
    <el-dialog
      v-model="wizard.visible"
      title="新增待处理任务"
      width="660px"
      :close-on-click-modal="false"
      @closed="resetWizard"
    >
      <el-form label-width="100px" label-position="right">
        <el-form-item label="任务来源">
          <el-select v-model="wizard.source" placeholder="请选择来源" style="width: 100%" @change="onSourceChange">
            <el-option v-for="o in sourceOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>

        <!-- 自动生成：沿用现有规则驱动 -->
        <template v-if="wizard.source === 'AUTO'">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="自动生成沿用现有规则驱动方式，按被评价人发起。"
            style="margin-bottom: 12px"
          />
          <el-button type="primary" @click="goAutoCreate">前往自动生成</el-button>
        </template>

        <!-- 手工导入 -->
        <template v-else-if="wizard.source === 'IMPORT'">
          <el-form-item label="导入类型">
            <el-select v-model="wizard.importType" placeholder="请选择导入类型" style="width: 100%">
              <el-option v-for="o in importTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>

          <el-alert
            v-if="wizard.importType === 'REWARD'"
            type="warning"
            :closable="false"
            show-icon
            title="奖励分配导入本期暂未开放。"
          />

          <!-- 评价任务导入 -->
          <template v-if="wizard.importType === 'EVAL'">
            <el-form-item label="截止时间">
              <el-date-picker
                v-model="wizard.deadline"
                type="datetime"
                placeholder="选择打分截止时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                :disabled-date="disabledDate"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="导入文件">
              <div style="width: 100%">
                <div style="margin-bottom: 8px">
                  <el-button size="small" @click="doDownloadTpl">📥 下载导入模板</el-button>
                  <span class="form-tip">10 列：被打分人 编号/姓名/部门/标签 + 打分人 编号/姓名/标签/部门 + 权重标签 + 评价类型</span>
                </div>
                <el-upload
                  ref="wizardUploaderRef"
                  drag
                  action="#"
                  :auto-upload="false"
                  :show-file-list="true"
                  :limit="1"
                  :on-change="onWizardFilePick"
                  accept=".xlsx"
                >
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
        <el-button
          v-if="wizard.source === 'IMPORT' && wizard.importType === 'EVAL'"
          type="primary"
          :loading="importing"
          :disabled="!wizardFile || !wizard.deadline"
          @click="doImportAssign"
        >开始导入</el-button>
      </template>
    </el-dialog>

    <!-- ===== 任务详情弹窗 ===== -->
    <el-dialog
      v-model="detailDialog.visible"
      title="任务详情"
      width="800px"
      :close-on-click-modal="false"
    >
      <div v-if="detailData" v-loading="detailDialog.loading">
        <!-- 任务基本信息 -->
        <el-descriptions :column="2" border class="detail-desc">
          <el-descriptions-item label="任务名称">
            {{ detailData.task.taskName }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <span :class="detailData.task.status === 0 ? 'tag-success' : 'tag-info'">
              {{ detailData.task.status === 0 ? '进行中' : '已结束' }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">
            {{ formatDateTime(detailData.task.startTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="截止时间">
            {{ formatDateTime(detailData.task.endTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="创建人">
            {{ detailData.task.creatorName }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 被评价人列表 -->
        <div class="detail-section-title">被评价人列表</div>
        <el-table
          :data="detailData.targets"
          border
          stripe
          size="small"
          style="width: 100%; margin-top: 8px"
        >
          <el-table-column prop="targetId" label="记录ID" width="80" align="center" />
          <el-table-column prop="beEvalUserId" label="被评价人ID" width="110" align="center" />
          <el-table-column prop="beEvalUserName" label="被评价人" width="120" />
          <el-table-column prop="ruleId" label="规则ID" width="90" align="center" />
          <el-table-column label="最终得分" width="100" align="center">
            <template #default="{ row }">
              <span v-if="row.finalScore !== null && row.finalScore !== undefined">
                {{ row.finalScore }}
              </span>
              <span v-else class="score-empty">—</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button
          v-if="detailData && detailData.task.status === 0"
          type="danger"
          :loading="detailDialog.closing"
          @click="handleCloseTaskFromDetail"
        >
          关闭任务
        </el-button>
        <el-button @click="detailDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { listTasks, getTaskDetail, createTask, closeTask, downloadAssignTemplate, importAssign } from '@/api/eval'
import { listUsers } from '@/api/users'
import { useDict } from '@/composables/useDict'

// 字典下拉：任务来源 / 导入类型
const { options: sourceOptions } = useDict('EVAL_PENDING_SOURCE')
const { options: importTypeOptions } = useDict('EVAL_IMPORT_TYPE')

// ===================== 列表数据 =====================

/** 筛选条件 */
const filter = reactive({
  status: '',
  keyword: ''
})

/** 分页参数 */
const pager = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0
})

const tableLoading = ref(false)
const tableData = ref([])

/** 加载任务列表 */
async function loadList() {
  tableLoading.value = true
  try {
    const params = {
      page: pager.pageNo,
      pageSize: pager.pageSize,
      keyword: filter.keyword || undefined
    }
    // status 为空字符串时不传
    if (filter.status !== '') {
      params.status = filter.status
    }
    const res = await listTasks(params)
    tableData.value = res.records || []
    pager.total = res.total || 0
  } catch (e) {
    ElMessage.error('加载任务列表失败：' + (e?.message || '未知错误'))
  } finally {
    tableLoading.value = false
  }
}

/** 筛选变化时重置到第一页并重新加载 */
function handleFilterChange() {
  pager.pageNo = 1
  loadList()
}

// ===================== 日期格式化工具 =====================

/**
 * 格式化日期时间显示
 * 后端可能返回 yyyy-MM-dd HH:mm:ss 字符串或 ISO 字符串，均做兼容处理
 */
function formatDateTime(val) {
  if (!val) return '—'
  // 已是标准格式直接返回
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(val)) return val
  // ISO 格式转换
  const d = new Date(val)
  if (isNaN(d.getTime())) return val
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ===================== 关闭任务 =====================

/**
 * 列表行内关闭任务
 */
async function handleCloseTask(row) {
  try {
    await ElMessageBox.confirm(
      `确认关闭任务「${row.taskName}」？关闭后无法重新开启。`,
      '关闭确认',
      { type: 'warning', confirmButtonText: '确认关闭', cancelButtonText: '取消' }
    )
    await closeTask(row.taskId)
    ElMessage.success('任务已关闭')
    loadList()
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error('关闭任务失败：' + (e?.message || '未知错误'))
  }
}

// ===================== 新建任务弹窗 =====================

const createDialog = reactive({ visible: false, submitting: false })
const createFormRef = ref(null)

/** 新建表单数据 */
const createForm = reactive({
  taskName: '',
  endTime: '',
  beEvalUserIds: []
})

/** 新建表单校验规则 */
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
        if (new Date(value) <= new Date()) {
          callback(new Error('截止时间必须晚于当前时间'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  beEvalUserIds: [
    {
      validator: (rule, value, callback) => {
        if (!value || value.length === 0) {
          callback(new Error('请至少选择一位被评价人'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

/** 禁用今天之前的日期 */
function disabledDate(time) {
  return time.getTime() < Date.now() - 86400000
}

/** 用户搜索相关 */
const userOptions = ref([])
const userSearchLoading = ref(false)

/** 远程搜索用户 */
async function remoteSearchUsers(query) {
  if (!query) {
    userOptions.value = []
    return
  }
  userSearchLoading.value = true
  try {
    const users = await listUsers({ keyword: query })
    userOptions.value = Array.isArray(users) ? users : (users.records || [])
  } catch (e) {
    ElMessage.error('搜索用户失败：' + (e?.message || '未知错误'))
  } finally {
    userSearchLoading.value = false
  }
}

/** 打开新建弹窗 */
function openCreateDialog() {
  createDialog.visible = true
}

/** 重置新建表单 */
function resetCreateForm() {
  createForm.taskName = ''
  createForm.endTime = ''
  createForm.beEvalUserIds = []
  userOptions.value = []
  createFormRef.value?.clearValidate()
}

/** 提交新建任务 */
async function handleCreateTask() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  createDialog.submitting = true
  try {
    await createTask({
      taskName: createForm.taskName,
      endTime: createForm.endTime,
      beEvalUserIds: createForm.beEvalUserIds
    })
    ElMessage.success('任务创建成功')
    createDialog.visible = false
    loadList()
  } catch (e) {
    ElMessage.error('创建失败：' + (e?.message || '未知错误'))
  } finally {
    createDialog.submitting = false
  }
}

// ===================== 新增待处理任务向导 =====================

const wizard = reactive({
  visible: false,
  source: '',
  importType: '',
  deadline: ''
})
const wizardFile = ref(null)
const wizardUploaderRef = ref(null)
const importErrors = ref([])
const importing = ref(false)

/** 打开向导 */
function openWizard() {
  wizard.visible = true
}

/** 重置向导状态 */
function resetWizard() {
  wizard.source = ''
  wizard.importType = ''
  wizard.deadline = ''
  wizardFile.value = null
  importErrors.value = []
  wizardUploaderRef.value?.clearFiles()
}

/** 来源切换时清理下游选择 */
function onSourceChange() {
  wizard.importType = ''
  wizard.deadline = ''
  wizardFile.value = null
  importErrors.value = []
}

/** 自动生成：沿用现有规则驱动新建弹窗 */
function goAutoCreate() {
  wizard.visible = false
  openCreateDialog()
}

/** Blob 另存为文件 */
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

/** 下载评价任务导入模板 */
async function doDownloadTpl() {
  try {
    const blob = await downloadAssignTemplate()
    saveBlob(blob, '评价任务导入模板.xlsx')
  } catch (e) { /* http.js 已提示 */ }
}

/** 选择导入文件 */
function onWizardFilePick(uploadFile) {
  wizardFile.value = uploadFile.raw || null
}

/** 执行评价任务导入 */
async function doImportAssign() {
  if (!wizardFile.value || !wizard.deadline) return
  importing.value = true
  importErrors.value = []
  try {
    const res = await importAssign(wizardFile.value, 'EVAL', wizard.deadline)
    if (res && res.success) {
      ElMessage.success(`导入成功 ${res.importedCount} 条，已分发到各打分人的待处理任务`)
      wizard.visible = false
      loadList()
    } else {
      importErrors.value = (res && res.errors) || []
      ElMessage.error('导入未通过校验，请查看错误明细')
    }
  } catch (e) {
    // http.js 已弹错误消息
  } finally {
    importing.value = false
  }
}

// ===================== 任务详情弹窗 =====================

const detailDialog = reactive({ visible: false, loading: false, closing: false })
const detailData = ref(null)

/** 打开详情弹窗 */
async function openDetailDialog(row) {
  detailDialog.visible = true
  detailDialog.loading = true
  detailData.value = null
  try {
    const res = await getTaskDetail(row.taskId)
    detailData.value = res
  } catch (e) {
    ElMessage.error('加载详情失败：' + (e?.message || '未知错误'))
    detailDialog.visible = false
  } finally {
    detailDialog.loading = false
  }
}

/** 从详情弹窗关闭任务 */
async function handleCloseTaskFromDetail() {
  if (!detailData.value) return
  const taskName = detailData.value.task.taskName
  const taskId = detailData.value.task.taskId
  try {
    await ElMessageBox.confirm(
      `确认关闭任务「${taskName}」？关闭后无法重新开启。`,
      '关闭确认',
      { type: 'warning', confirmButtonText: '确认关闭', cancelButtonText: '取消' }
    )
    detailDialog.closing = true
    await closeTask(taskId)
    ElMessage.success('任务已关闭')
    // 刷新详情和列表
    const res = await getTaskDetail(taskId)
    detailData.value = res
    loadList()
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error('关闭任务失败：' + (e?.message || '未知错误'))
  } finally {
    detailDialog.closing = false
  }
}

// ===================== 生命周期 =====================

onMounted(() => {
  loadList()
})
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

  /* 页头 */
  .page-h {
    display: flex;
    align-items: center;
    margin-bottom: 20px;
    gap: 12px;

    h1 {
      font-size: 20px;
      font-weight: 600;
      color: $text-1;
      margin: 0;
    }

    .desc {
      font-size: 13px;
      color: $text-3;
    }

    .actions {
      margin-left: auto;
    }
  }

  /* 筛选栏 */
  .filter-bar {
    display: flex;
    align-items: center;
    padding: 12px 16px;
    background: $bg-soft;
    border-radius: 8px;
    border: 1px solid $border-1;
  }

  /* 分页 */
  .pagination-wrap {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }

  /* 状态标签 */
  .tag-success {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 12px;
    font-size: 12px;
    background: #e6f4ea;
    color: #1e7e34;
    border: 1px solid #b7dfbf;
  }

  .tag-info {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 12px;
    font-size: 12px;
    background: #f0f0f0;
    color: #666;
    border: 1px solid #d9d9d9;
  }

  .tag-warning {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 12px;
    font-size: 12px;
    background: #fff7e6;
    color: #d46b08;
    border: 1px solid #ffd591;
  }

  .tag-danger {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 12px;
    font-size: 12px;
    background: #fff1f0;
    color: $danger;
    border: 1px solid #ffa39e;
  }

  /* 表单提示文字 */
  .form-tip {
    font-size: 12px;
    color: $text-3;
    margin-top: 4px;
    margin-left: 8px;
  }

  /* 导入错误明细 */
  .imp-errors {
    margin-top: 12px;

    .err-title {
      font-size: 13px;
      color: $danger;
      margin-bottom: 6px;
    }
  }

  /* 详情弹窗 */
  .detail-desc {
    margin-bottom: 16px;
  }

  .detail-section-title {
    font-size: 14px;
    font-weight: 600;
    color: $text-1;
    padding: 8px 0 4px 0;
    border-bottom: 1px solid $border-1;
    margin-bottom: 8px;
  }

  .score-empty {
    color: $text-3;
  }
}
.pagination-wrap :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
</style>
