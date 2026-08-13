<template>
  <!-- 评价任务管理页面（管理端：统一列表 = 规则任务 + 导入批次） -->
<main v-bp-overflow-tooltip class="bp-crud eval-tasks-page" aria-labelledby="eval-tasks-page-title" :aria-busy="tableLoading ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="eval-tasks-page-title"><span class="sub">发起评价活动并管理任务生命周期</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="评价任务操作">
        <el-button :loading="tableLoading" @click="loadList">刷新</el-button>
        <el-button type="primary" @click="openWizard">新增待处理任务</el-button>
      </div>
    </header>

    <!-- 筛选栏 -->
    <section class="card-section filter-bar" aria-label="评价任务筛选">
      <el-form class="filter-form" inline aria-label="评价任务筛选">
        <el-form-item label="状态">
          <el-select v-model="filter.status" aria-label="按任务状态筛选" placeholder="全部状态" clearable class="status-filter" @change="handleFilterChange">
        <el-option label="全部" value="" />
        <el-option label="进行中" :value="0" />
        <el-option label="已结束" :value="1" />
        <el-option label="草稿" :value="2" />
        <el-option label="处理中" :value="3" />
        <el-option label="导入失败" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filter.keyword" aria-label="按任务名称批次或创建人筛选" placeholder="任务名称 / 批次 / 创建人" clearable class="keyword-filter"
            @keyup.enter="handleFilterChange" @clear="handleFilterChange">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="handleFilterChange">查询</el-button></el-form-item>
      </el-form>
    </section>

    <!-- 统一任务列表 -->
    <section class="card-section data-panel" aria-label="统一评价任务列表" aria-describedby="eval-tasks-table-state">
      <div class="toolbar">
        <div>
          <h2 id="eval-tasks-table-heading" class="section-title">统一评价任务列表</h2>
          <p class="hint">规则任务和导入批次统一展示；发布、关闭和导出均保留原任务契约。</p>
        </div>
        <p id="eval-tasks-table-state" class="table-state" role="status" aria-live="polite">
          {{ tableLoading ? '评价任务列表加载中' : loadError || (tableData.length ? `共 ${pager.total} 条任务` : '暂无评价任务数据') }}
        </p>
      </div>
      <el-table v-loading="tableLoading" :data="tableData" border stripe empty-text="暂无评价任务数据"
        aria-labelledby="eval-tasks-table-heading" aria-describedby="eval-tasks-table-state">
      <el-table-column label="来源" width="100" align="center">
        <template #default="{ row }">
          <span class="status-badge tag-info">{{ row.sourceType === 'AUTO' ? '自动生成' : '手工导入' }}</span>
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
          <span v-if="row.status === 2" class="status-badge tag-warning">草稿</span>
          <span v-else-if="row.status === 0" class="status-badge tag-success">进行中</span>
          <span v-else-if="row.status === 1" class="status-badge tag-info">已结束</span>
          <span v-else-if="row.status === 3" class="status-badge tag-warning">处理中</span>
          <span v-else-if="row.status === 4" class="status-badge tag-danger">导入失败</span>
          <span v-else class="status-badge tag-info">{{ row.status }}</span>
        </template>
      </el-table-column>
      <el-table-column label="明细数" width="80" align="center">
        <template #default="{ row }">{{ row.itemCount != null ? row.itemCount : '—' }}</template>
      </el-table-column>
      <el-table-column prop="createBy" label="创建人" width="110" align="center" />
      <el-table-column label="操作" class-name="operation-cell" width="240" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openDetail(row)">详情</el-button>
          <el-dropdown trigger="click" popper-class="bp-crud-menu">
            <el-button link aria-label="更多评价任务操作">更多</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="row.status === 2" class="success-item" :disabled="isPending('publish', row.sourceId)" @click="handlePublish(row)">发布</el-dropdown-item>
                <el-dropdown-item v-if="row.sourceType === 'AUTO' && row.status === 0" divided class="danger-item" :disabled="isPending('close', row.sourceId)" @click="handleCloseTask(row)">关闭</el-dropdown-item>
                <el-dropdown-item :disabled="isPending('export', row.sourceId)" @click="handleExport(row)">导出</el-dropdown-item>
                <el-dropdown-item v-if="row.status === 0 || row.status === 2 || row.status === 4 || isDeadlinePassed(row)" divided class="danger-item" :disabled="isPending('delete', row.sourceId)" @click="handleDelete(row)">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
      </el-table>

      <nav class="pager" aria-label="统一评价任务列表分页">
      <el-pagination
        v-model:current-page="pager.pageNo" v-model:page-size="pager.pageSize"
        :total="pager.total" :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper" background
        @size-change="loadList" @current-change="loadList"
      />
      </nav>
    </section>

    <!-- ===== 新建任务弹窗 ===== -->
    <el-dialog v-model="createDialog.visible" class="bp-crud-dialog" title="新建评价任务" width="680px"
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
        <el-button type="primary" :loading="createDialog.submitting" :disabled="createDialog.submitting" @click="handleCreateTask">保存</el-button>
      </template>
    </el-dialog>

    <!-- ===== 新增待处理任务向导 ===== -->
    <el-dialog v-model="wizard.visible" class="bp-crud-dialog" title="新增待处理任务" width="660px"
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
          <template v-if="wizard.importType === 'EVAL' || wizard.importType === 'REWARD'">
            <el-form-item label="任务名称" required>
              <el-input v-model="wizard.taskName" placeholder="请输入任务名称" maxlength="64" show-word-limit />
            </el-form-item>
            <el-form-item label="截止时间">
              <el-date-picker v-model="wizard.deadline" type="datetime"
                :placeholder="wizard.importType === 'REWARD' ? '选择分配截止时间' : '选择打分截止时间'"
                value-format="YYYY-MM-DD HH:mm:ss" :disabled-date="disabledDate" style="width: 100%" />
            </el-form-item>
            <el-form-item label="导入文件">
              <div style="width: 100%">
                <div style="margin-bottom: 8px">
                  <el-button size="small" @click="doDownloadTpl">下载导入模板</el-button>
                  <span v-if="wizard.importType === 'REWARD'" class="form-tip">8 列：被分配人工号/姓名 + 部门名称 + 原始值 + 分配值(留空) + 兑现值 + 分配人工号 + 分配合计</span>
                  <span v-else class="form-tip">11 列：被打分人 编号/姓名/部门 + 分组部门 + 被打分人标签 + 打分人 编号/姓名/标签/部门 + 权重标签 + 评价类型</span>
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
        <el-button v-if="wizard.source === 'IMPORT' && (wizard.importType === 'EVAL' || wizard.importType === 'REWARD')"
          type="primary" :loading="importing" :disabled="importing || !wizardFile || !wizard.deadline || !wizard.taskName" @click="doImportAssign">
          开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- ===== 规则任务详情弹窗 ===== -->
    <el-dialog v-model="ruleDetail.visible" class="bp-crud-dialog" title="任务详情" width="800px" :close-on-click-modal="false">
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
          type="danger" :loading="ruleDetail.closing" :disabled="ruleDetail.closing" @click="handleCloseTaskFromDetail">关闭任务</el-button>
        <el-button @click="ruleDetail.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ===== 导入批次详情弹窗 ===== -->
    <el-dialog v-model="batchDetail.visible" class="bp-crud-dialog" title="批次详情" width="900px" :close-on-click-modal="false">
      <div v-if="batchDetail.data" v-loading="batchDetail.loading">
        <el-descriptions :column="2" border class="detail-desc">
          <el-descriptions-item label="批次ID">{{ batchDetail.data.batch.batchId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <span v-if="batchDetail.data.batch.status === 2" class="tag-warning">草稿</span>
            <span v-else-if="batchDetail.data.batch.status === 0" class="tag-success">进行中</span>
            <span v-else-if="batchDetail.data.batch.status === 1" class="tag-info">已结束</span>
            <span v-else-if="batchDetail.data.batch.status === 3" class="tag-warning">处理中</span>
            <span v-else-if="batchDetail.data.batch.status === 4" class="tag-danger">导入失败</span>
          </el-descriptions-item>
          <el-descriptions-item label="任务类型">{{ batchDetail.data.batch.taskType === 'EVAL' ? '评价任务' : (batchDetail.data.batch.taskType === 'REWARD' ? '奖励分配' : batchDetail.data.batch.taskType) }}</el-descriptions-item>
          <el-descriptions-item label="截止时间">{{ formatDateTime(batchDetail.data.batch.deadline) }}</el-descriptions-item>
          <el-descriptions-item label="导入人">{{ batchDetail.data.batch.createBy }}</el-descriptions-item>
          <el-descriptions-item label="导入时间">{{ formatDateTime(batchDetail.data.batch.createTime) }}</el-descriptions-item>
        </el-descriptions>
        <!-- 导入失败(4)：明细未入库、评价明细为空，改为展示行级错误明细 -->
        <div v-if="batchDetail.data.batch.status === 4">
          <div class="detail-section-title">导入错误明细<span v-if="batchErrorInfo.truncated" class="batch-error-hint">（仅显示前 {{ batchErrorInfo.errors.length }} 条，共 {{ batchErrorInfo.total }} 条，修正后请重新导入）</span></div>
          <el-table :data="batchErrorInfo.errors" border stripe size="small" max-height="320" style="width: 100%; margin-top: 8px">
            <el-table-column prop="row" label="行号" width="100" align="center" />
            <el-table-column prop="message" label="错误信息" min-width="320" show-overflow-tooltip />
          </el-table>
        </div>
        <!-- 其它状态 + REWARD：展示奖励分配明细 -->
        <div v-else-if="batchDetail.data.batch.taskType === 'REWARD'">
        <div class="detail-section-title">奖励分配明细</div>
        <el-table :data="batchDetail.data.items.records" border stripe size="small" style="width: 100%; margin-top: 8px">
          <el-table-column label="被分配人工号" width="120" align="center">
            <template #default="{ row: it }">{{ it.beAssignedUserId }}</template>
          </el-table-column>
          <el-table-column prop="beAssignedUserName" label="被分配人" min-width="100" />
          <el-table-column prop="deptName" label="部门" min-width="120" />
          <el-table-column label="分配人工号" width="110" align="center">
            <template #default="{ row: it }">{{ it.assignUserUsername || it.assignUserId }}</template>
          </el-table-column>
          <el-table-column label="原始值" width="90" align="right">
            <template #default="{ row: it }">{{ it.originalValue ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="兑现值" width="90" align="right">
            <template #default="{ row: it }">{{ it.cashValue ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="分配合计" width="90" align="right">
            <template #default="{ row: it }">{{ it.assignTotal ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="分配值" width="90" align="right">
            <template #default="{ row: it }">
              <span v-if="it.submitted === 1" class="score-submitted">{{ it.assignValue }}</span>
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
        <!-- 其它状态：展示评价明细 -->
        <div v-else>
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
          <el-table-column prop="groupDept" label="分组部门" min-width="120" />
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
      </div>
      <template #footer>
          <el-button v-if="batchDetail.data && batchDetail.data.batch.status === 2"
          type="success" :loading="batchDetail.publishing" :disabled="batchDetail.publishing" @click="handlePublishFromDetail">确认发布</el-button>
        <el-button @click="handleExportById(batchDetail.data?.batch?.batchId, batchDetail.data?.batch?.taskType === 'REWARD' ? 'reward' : 'batch')">导出 Excel</el-button>
        <el-button @click="batchDetail.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  listUnifiedTasks, getTaskDetail, createTask, closeTask,
  downloadAssignTemplate, importAssign,
  getAssignBatchDetail, publishAssignBatch, exportAssignBatchItems, exportRuleTask,
  deleteUnifiedTask,
  downloadRewardTemplate, importReward, getRewardBatchDetail, exportRewardBatchItems
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
const loadError = ref('')
const pendingActions = reactive({
  publish: new Set(),
  close: new Set(),
  export: new Set(),
  delete: new Set()
})

function isPending(type, id) {
  return id != null && pendingActions[type]?.has(id)
}

async function loadList() {
  tableLoading.value = true
  loadError.value = ''
  try {
    const params = { page: pager.pageNo, pageSize: pager.pageSize }
    if (filter.status !== '') params.status = filter.status
    if (filter.keyword) params.keyword = filter.keyword
    const res = await listUnifiedTasks(params)
    tableData.value = res.records || []
    pager.total = res.total || 0
  } catch (e) {
    tableData.value = []
    pager.total = 0
    loadError.value = '评价任务加载失败，请刷新重试'
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
      // 按任务类型路由：REWARD 走奖励分配批次详情（明细在 EVAL_REWARD_ITEM），其余走评价批次详情
      batchDetail.data = row.taskType === 'REWARD'
        ? await getRewardBatchDetail(row.sourceId, { page: 1, pageSize: 50 })
        : await getAssignBatchDetail(row.sourceId, { page: 1, pageSize: 50 })
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
    const isReward = batchDetail.data.batch.taskType === 'REWARD'
    const fetch = isReward ? getRewardBatchDetail : getAssignBatchDetail
    batchDetail.data = await fetch(batchDetail.data.batch.batchId,
      { page: batchDetail.itemPage.pageNo, pageSize: batchDetail.itemPage.pageSize })
  } catch (e) {
    ElMessage.error('加载明细失败：' + (e?.message || '未知错误'))
  } finally {
    batchDetail.loading = false
  }
}

// ===================== 发布 =====================

async function handlePublish(row) {
  if (!row?.sourceId || isPending('publish', row.sourceId)) return
  try {
    await ElMessageBox.confirm(
      `确认发布「${row.taskName}」？发布后打分人即可看到待处理任务。`,
      '确认发布', { type: 'warning', confirmButtonText: '确认发布', cancelButtonText: '取消' }
    )
    if (isPending('publish', row.sourceId)) return
    pendingActions.publish.add(row.sourceId)
    try {
      await publishAssignBatch(row.sourceId)
      ElMessage.success('已发布')
      loadList()
    } finally {
      pendingActions.publish.delete(row.sourceId)
    }
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error('发布失败：' + (e?.message || '未知错误'))
  }
}

async function handlePublishFromDetail() {
  if (!batchDetail.data) return
  const batchId = batchDetail.data.batch.batchId
  if (!batchId || isPending('publish', batchId)) return
  pendingActions.publish.add(batchId)
  batchDetail.publishing = true
  try {
    await publishAssignBatch(batchId)
    ElMessage.success('批次已发布')
    batchDetail.visible = false
    loadList()
  } catch (e) {
    ElMessage.error('发布失败：' + (e?.message || '未知错误'))
  } finally {
    batchDetail.publishing = false
    pendingActions.publish.delete(batchId)
  }
}

// ===================== 导出 =====================

async function handleExport(row) {
  let type = 'batch'
  if (row.sourceType === 'AUTO' && row.taskType === 'EVAL') type = 'rule'
  else if (row.taskType === 'REWARD') type = 'reward'
  await handleExportById(row.sourceId, type)
}

async function handleExportById(id, type) {
  if (!id || isPending('export', id)) return
  pendingActions.export.add(id)
  try {
    let blob
    let filename
    if (type === 'rule') {
      blob = await exportRuleTask(id)
      filename = `规则任务明细_${id}.xlsx`
    } else if (type === 'reward') {
      blob = await exportRewardBatchItems(id)
      filename = `奖励分配明细_batch_${id}.xlsx`
    } else {
      blob = await exportAssignBatchItems(id)
      filename = `评价明细_batch_${id}.xlsx`
    }
    saveBlob(blob, filename)
  } catch (e) { /* http.js 已提示 */
  } finally {
    pendingActions.export.delete(id)
  }
}

// ===================== 关闭任务 =====================

async function handleCloseTask(row) {
  if (!row?.sourceId || isPending('close', row.sourceId)) return
  try {
    await ElMessageBox.confirm(
      `确认关闭任务「${row.taskName}」？关闭后无法重新开启。`,
      '关闭确认', { type: 'warning', confirmButtonText: '确认关闭', cancelButtonText: '取消' }
    )
    if (isPending('close', row.sourceId)) return
    pendingActions.close.add(row.sourceId)
    try {
      await closeTask(row.sourceId)
      ElMessage.success('任务已关闭')
      loadList()
    } finally {
      pendingActions.close.delete(row.sourceId)
    }
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error('关闭任务失败：' + (e?.message || '未知错误'))
  }
}

async function handleCloseTaskFromDetail() {
  if (!ruleDetail.data) return
  const taskName = ruleDetail.data.task.taskName
  const taskId = ruleDetail.data.task.taskId
  if (!taskId || isPending('close', taskId)) return
  try {
    await ElMessageBox.confirm(
      `确认关闭任务「${taskName}」？关闭后无法重新开启。`,
      '关闭确认', { type: 'warning', confirmButtonText: '确认关闭', cancelButtonText: '取消' }
    )
    if (isPending('close', taskId)) return
    pendingActions.close.add(taskId)
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
    pendingActions.close.delete(taskId)
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
  if (createDialog.submitting) return
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

// 轮询定时器（向导关闭 / 组件卸载时必须清理，避免内存泄漏与重复轮询）
let pollTimer = null

function clearPollTimer() {
  if (pollTimer !== null) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

function openWizard() { wizard.visible = true }

function resetWizard() {
  // 轮询已与弹窗解耦（受理成功即关窗，结果在后台轮询并消息通知），关窗不再取消轮询
  importing.value = false
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
  try {
    if (wizard.importType === 'REWARD') {
      saveBlob(await downloadRewardTemplate(), '奖励分配导入模板.xlsx')
    } else {
      saveBlob(await downloadAssignTemplate(), '评价任务导入模板.xlsx')
    }
  } catch (e) { /* */ }
}

function onWizardFilePick(uploadFile) { wizardFile.value = uploadFile.raw || null }

async function doImportAssign() {
  if (importing.value || !wizardFile.value || !wizard.deadline || !wizard.taskName) return
  // 捕获当前导入类型（轮询期间向导可能已重置），后续按类型路由到对应批次详情接口
  const importType = wizard.importType
  importing.value = true
  importErrors.value = []
  clearPollTimer()

  let batchId
  try {
    const res = importType === 'REWARD'
      ? await importReward(wizardFile.value, wizard.taskName, wizard.deadline)
      : await importAssign(wizardFile.value, 'EVAL', wizard.taskName, wizard.deadline)
    batchId = res && res.batchId
    if (!batchId) {
      ElMessage.error('导入请求未返回批次ID，请刷新重试')
      importing.value = false
      return
    }
  } catch (e) {
    ElMessage.error('导入提交失败：' + (e?.message || '未知错误'))
    importing.value = false
    return
  }

  // 后端已受理（status=3=IMPORTING），接口已返回 → 立即关闭向导：
  // 校验/入库在后台异步进行，结果由下方轮询用全局消息通知，并刷新列表（先显示「处理中」批次）。
  importing.value = false
  wizard.visible = false
  loadList()
  ElMessage.info('导入已受理，正在后台处理，结果将自动通知')

  // 后台轮询该批次状态（与弹窗解耦）：每 2s 一次，上限 5 分钟
  const startTime = Date.now()
  const MAX_POLL_MS = 5 * 60 * 1000

  async function poll() {
    try {
      const detail = importType === 'REWARD'
        ? await getRewardBatchDetail(batchId)
        : await getAssignBatchDetail(batchId)
      const batch = detail && detail.batch
      const status = batch && batch.status

      if (status === 2) {
        // 导入成功 → 草稿，待人工确认发布
        ElMessage.success(`导入成功，共 ${batch.importedCount ?? 0} 条，请在批次中确认发布`)
        loadList()
        return
      }
      if (status === 4) {
        // 导入失败：提示错误条数，行级明细在「批次详情」查看
        const { total } = parseErrorSummary(batch.errorSummary)
        ElMessage.error(`导入失败，共 ${total} 条错误，请在批次详情查看明细后重传`)
        loadList()
        return
      }
      // 处理中：超时则停止，否则 2s 后再查
      if (Date.now() - startTime >= MAX_POLL_MS) {
        ElMessage.warning('导入仍在处理，请稍后在批次列表查看结果')
        loadList()
        return
      }
      pollTimer = setTimeout(poll, 2000)
    } catch (e) {
      ElMessage.error('查询导入进度失败：' + (e?.message || '未知错误'))
    }
  }

  // 首次轮询延迟 2s，等待后端异步线程启动
  pollTimer = setTimeout(poll, 2000)
}

// 解析后端 ERROR_SUMMARY（形如 {total, truncated, errors:[{row,message}]}；兼容纯数组 / 非 JSON 串）
function parseErrorSummary(errorSummary) {
  if (!errorSummary) return { total: 0, truncated: false, errors: [] }
  try {
    const obj = JSON.parse(errorSummary)
    if (Array.isArray(obj)) return { total: obj.length, truncated: false, errors: obj }
    const errors = Array.isArray(obj.errors) ? obj.errors : []
    return { total: obj.total ?? errors.length, truncated: !!obj.truncated, errors }
  } catch {
    return { total: 1, truncated: false, errors: [{ row: '—', message: errorSummary }] }
  }
}

// 批次详情：导入失败(4)时的行级错误明细（解析 batch.errorSummary）
const batchErrorInfo = computed(() => parseErrorSummary(batchDetail.data?.batch?.errorSummary))

// ===================== 删除 =====================

function isDeadlinePassed(row) {
  if (!row.endTime) return false
  return new Date(row.endTime) <= new Date()
}

async function handleDelete(row) {
  const label = row.sourceType === 'AUTO' ? '规则任务' : '导入批次'
  if (!row?.sourceId || isPending('delete', row.sourceId)) return
  try {
    await ElMessageBox.confirm(
      `确认删除${label}「${row.taskName}」？删除后数据无法恢复，请确认。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
    if (isPending('delete', row.sourceId)) return
    pendingActions.delete.add(row.sourceId)
    try {
      await deleteUnifiedTask(row.sourceType === 'AUTO' ? 'AUTO' : 'IMPORT', row.sourceId)
      ElMessage.success('已删除')
      loadList()
    } finally {
      pendingActions.delete.delete(row.sourceId)
    }
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error('删除失败：' + (e?.message || '未知错误'))
  }
}

// ===================== 生命周期 =====================

onMounted(() => { loadList() })

// 组件卸载时清理轮询定时器，防止内存泄漏
onUnmounted(() => { clearPollTimer() })
</script>

<style lang="scss" scoped>
.eval-tasks-page {
  .status-filter { width: 140px; }
  .keyword-filter { width: 260px; }

  .status-badge {
    display: inline-flex;
    align-items: center;
    min-height: 24px;
    padding: 2px var(--space-2);
    border: 1px solid transparent;
    border-radius: var(--radius-control);
    font-size: 12px;
    line-height: 18px;
  }
  .tag-success { color: var(--color-success-fg); background: var(--color-success-bg); border-color: var(--color-success-fg); }
  .tag-info { color: var(--color-info-fg); background: var(--color-info-bg); border-color: var(--color-info-fg); }
  .tag-warning { color: var(--color-warning-fg); background: var(--color-warning-bg); border-color: var(--color-warning-fg); }
  .tag-danger { color: var(--color-danger-fg); background: var(--color-danger-bg); border-color: var(--color-danger-fg); }

  .form-tip { font-size: 12px; color: var(--color-text-muted); margin-top: var(--space-1); margin-left: var(--space-2); }

  .imp-errors {
    margin-top: var(--space-3);
    .err-title { font-size: 14px; color: var(--color-danger-fg); margin-bottom: var(--space-1); }
  }

  .detail-desc { margin-bottom: var(--space-4); }

  .detail-section-title {
    font-size: 16px; font-weight: 600; color: var(--color-text-strong);
    padding: var(--space-2) 0 var(--space-1); border-bottom: 1px solid var(--color-border); margin-bottom: var(--space-2);
  }

  .score-empty { color: var(--color-text-muted); }
  .batch-error-hint { font-weight: 400; color: var(--color-text-muted); font-size: 12px; margin-left: var(--space-2); }

  .tag-type {
    display: inline-flex; padding: 2px var(--space-2); border-radius: var(--radius-control); font-size: 12px;
    background: var(--color-brand-100); color: var(--color-brand-700); border: 1px solid var(--color-brand-500);
  }

  .score-submitted {
    display: inline-flex; align-items: center; gap: var(--space-1); font-weight: 600; color: var(--color-success-fg);
  }
}
</style>
