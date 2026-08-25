<template>
  <main v-bp-overflow-tooltip class="bp-crud business-application" aria-labelledby="loan-page-title">
    <div class="page-h">
      <PageTitle id="loan-page-title" title="资产立项" />
      <div class="action-group" role="group" aria-label="资产立项操作">
        <el-button type="primary" @click="openCreate">新建申请</el-button>
      </div>
    </div>

    <section class="card-section filter-bar" aria-label="资产立项申请筛选">
      <el-form class="filter-form" inline @submit.prevent>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="申请编号" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 140px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="card-section data-panel" aria-label="资产立项申请列表" :aria-busy="listLoading ? 'true' : 'false'">
      <div class="toolbar">
        <div>
          <h2 class="section-title">资产立项申请列表</h2>
          <p class="hint">申请客户仅可选择已审批通过的公司类客户；金额单位为万元。</p>
        </div>
        <p class="table-state" role="status" aria-live="polite">
          {{ listLoading ? '资产立项申请加载中' : listError || (rows.length ? `共 ${total} 条记录` : '暂无资产立项申请') }}
        </p>
      </div>
      <div v-if="listError" class="error-state" role="alert">
        {{ listError }}
        <el-button link type="primary" @click="loadList">重试</el-button>
      </div>
      <el-table :data="rows" stripe border row-key="id" empty-text="暂无资产立项申请" aria-label="资产立项申请数据">
        <el-table-column label="申请编号" min-width="180">
          <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">{{ row.applyNo || row.id || '-' }}</el-button></template>
        </el-table-column>
        <el-table-column label="客户名称" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ row.custName || row.custId || '-' }}</template>
        </el-table-column>
        <el-table-column label="授信金额（万元）" width="140" align="right">
          <template #default="{ row }">{{ row.creditAmount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="statusType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="165" />
        <el-table-column label="操作" class-name="operation-cell" width="220" fixed="right">
          <template #default="{ row }">
            <BpAdaptiveRowActions>
              <template #primary><el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button></template>
              <template #expanded>
                <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="submitDraft(row)">提交</el-button>
                <el-button v-if="row.status === 'DRAFT'" link type="danger" size="small" @click="removeDraft(row)">删除</el-button>
                <el-button v-if="row.status === 'IN_APPROVAL'" link type="danger" size="small" @click="cancelApplication(row)">撤回</el-button>
              </template>
              <template #compact>
                <el-dropdown trigger="click" popper-class="bp-crud-menu">
                  <el-button link size="small" aria-label="更多资产立项操作">更多</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="row.status === 'DRAFT'" @click="openEdit(row)">编辑</el-dropdown-item>
                      <el-dropdown-item v-if="row.status === 'DRAFT'" @click="submitDraft(row)">提交</el-dropdown-item>
                      <el-dropdown-item v-if="row.status === 'DRAFT'" divided class="danger-item" @click="removeDraft(row)">删除</el-dropdown-item>
                      <el-dropdown-item v-if="row.status === 'IN_APPROVAL'" divided class="danger-item" @click="cancelApplication(row)">撤回</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </BpAdaptiveRowActions>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadList"
          @size-change="onPageSizeChange"
        />
      </div>
    </section>

    <section class="card-section task-panel" aria-label="资产立项审批任务" :aria-busy="taskLoading ? 'true' : 'false'">
      <div class="toolbar task-toolbar">
        <div>
          <h2 class="section-title">资产立项审批任务</h2>
          <p class="hint">待办仅查询 bizType=LOAN；审批驳回会结束流程，操作名称为“驳回结束”。</p>
        </div>
        <el-radio-group v-model="taskTab" size="small" aria-label="资产立项任务页签" @change="loadTasks">
          <el-radio-button value="todo">待办</el-radio-button>
          <el-radio-button value="done">已办</el-radio-button>
        </el-radio-group>
        <el-button link type="primary" :loading="taskLoading" @click="loadTasks">刷新</el-button>
      </div>
      <el-table :data="tasks" stripe border empty-text="暂无资产立项审批任务">
        <el-table-column label="任务 / 业务键" min-width="220">
          <template #default="{ row }">
            <div>{{ row.title || row.taskName || row.processName || '资产立项审批' }}</div>
            <div class="sub-id">{{ row.businessKey || `LOAN:${row.bizId || '-'}` }}</div>
          </template>
        </el-table-column>
        <el-table-column label="当前节点" min-width="160">
          <template #default="{ row }">{{ taskNodeName(row) }}</template>
        </el-table-column>
        <el-table-column label="发起人" prop="startUserName" width="120" />
        <el-table-column label="发起时间" width="165">
          <template #default="{ row }">{{ formatTime(row.startTime || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" class-name="operation-cell" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openTaskDetail(row)">详情</el-button>
            <el-button v-if="taskTab === 'todo' && row.claimable === true" link type="primary" size="small" @click="claimRow(row)">签收</el-button>
            <el-button v-if="taskTab === 'todo' && row.claimable !== true" link type="success" size="small" @click="approveRow(row)">通过</el-button>
            <el-button v-if="taskTab === 'todo' && row.claimable !== true" link type="danger" size="small" @click="rejectRow(row)">驳回结束</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="taskLoading" class="inline-state" role="status">审批任务加载中</div>
      <div v-else-if="!tasks.length" class="inline-state" role="status">{{ taskTab === 'todo' ? '暂无待办任务' : '暂无已办任务' }}</div>
    </section>

    <LoanForm
      v-model="formVisible"
      :loan="editingLoan"
      :source-touch-task-id="sourceTouchTaskId"
      @saved="onFormSaved"
    />
    <LoanDetail
      v-model="detailVisible"
      :loan-id="detailLoanId"
      :process-instance-id="detailProcessInstanceId"
    />
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRoute } from 'vue-router';
import PageTitle from '@/components/PageTitle.vue';
import BpAdaptiveRowActions from '@/components/BpAdaptiveRowActions.vue';
import {
  deleteLoanApplication,
  getLoanApplication,
  listLoanAttachments,
  listLoanApplications,
  submitLoanApplication,
  cancelLoanApplication
} from '@/api/businessApplication';
import {
  approveTask,
  claimTask,
  listDoneTasks,
  listTodoTasks,
  rejectTask
} from '@/api/workflow';
import LoanForm from './LoanForm.vue';
import LoanDetail from './LoanDetail.vue';

const route = useRoute();
const query = reactive({ keyword: '', status: '', pageNo: 1, pageSize: 20 });
const rows = ref([]);
const total = ref(0);
const listLoading = ref(false);
const listError = ref('');
const tasks = ref([]);
const taskTab = ref('todo');
const taskLoading = ref(false);
const formVisible = ref(false);
const editingLoan = ref(null);
const sourceTouchTaskId = ref('');
const detailVisible = ref(false);
const detailLoanId = ref('');
const detailProcessInstanceId = ref('');

const statusOptions = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'IN_APPROVAL', label: '审批中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'REJECTED', label: '驳回结束' },
  { value: 'CANCELLED', label: '已撤回' }
];
const statusMap = Object.fromEntries(statusOptions.map(item => [item.value, item]));
const statusLabel = status => statusMap[status]?.label || status || '-';
const statusType = status => ({ DRAFT: 'info', IN_APPROVAL: 'warning', COMPLETED: 'success', REJECTED: 'danger', CANCELLED: 'info' }[status] || 'info');
const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 19) : '-';
const taskNodeName = row => row?.taskName || row?.nodeName || row?.nodeKey || '-';

async function loadList() {
  listLoading.value = true;
  listError.value = '';
  try {
    const result = await listLoanApplications({
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize
    });
    rows.value = result?.records || [];
    total.value = Number(result?.total) || 0;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    listError.value = error?.message || '资产立项申请加载失败，请重试';
  } finally {
    listLoading.value = false;
  }
}

function search() {
  query.pageNo = 1;
  loadList();
}
function resetFilters() {
  Object.assign(query, { keyword: '', status: '', pageNo: 1 });
  loadList();
}
function onPageSizeChange() {
  query.pageNo = 1;
  loadList();
}

async function loadTasks() {
  taskLoading.value = true;
  try {
    const loader = taskTab.value === 'todo' ? listTodoTasks : listDoneTasks;
    const result = await loader({ bizType: 'LOAN', pageNo: 1, pageSize: 20 });
    tasks.value = Array.isArray(result) ? result : (result?.records || []);
  } catch {
    tasks.value = [];
  } finally {
    taskLoading.value = false;
  }
}

function openCreate() {
  sourceTouchTaskId.value = String(route.query?.sourceTouchTaskId || '');
  const custId = String(route.query?.custId || '');
  editingLoan.value = sourceTouchTaskId.value && custId
    ? {
        sourceTouchTaskId: sourceTouchTaskId.value,
        custId,
        custName: String(route.query?.custName || custId)
      }
    : null;
  formVisible.value = true;
}

async function openEdit(row) {
  formVisible.value = true;
  editingLoan.value = row;
  sourceTouchTaskId.value = '';
  try {
    const [loan, attachments] = await Promise.all([
      getLoanApplication(row.id),
      listLoanAttachments(row.id)
    ]);
    const attachmentRows = Array.isArray(attachments) ? attachments : (attachments?.records || []);
    editingLoan.value = { ...(loan || row), attachments: attachmentRows };
  } catch {
    // 详情失败时保留列表行，表单仍可让用户重试保存；不替换成 mock。
  }
}

function loanIdOf(row) {
  if (row?.bizId || row?.loanId) return row.bizId || row.loanId;
  const key = String(row?.businessKey || '');
  return key.startsWith('LOAN:') ? key.slice('LOAN:'.length) : row?.id || '';
}

function openDetail(row) {
  detailLoanId.value = String(loanIdOf(row) || '');
  detailProcessInstanceId.value = row?.processInstanceId || '';
  detailVisible.value = Boolean(detailLoanId.value);
}

function openTaskDetail(row) {
  openDetail(row);
}

async function onFormSaved() {
  await loadList();
}

async function submitDraft(row) {
  try {
    await ElMessageBox.confirm(`确认提交资产立项申请“${row.applyNo || row.custName || row.id}”？`, '提交审批', { type: 'warning' });
    await submitLoanApplication(row.id);
    ElMessage.success('资产立项申请已提交');
    await loadList();
  } catch {
    // 用户取消或后端失败时维持原列表；真实错误由 HTTP 层反馈。
  }
}

async function removeDraft(row) {
  try {
    await ElMessageBox.confirm(`确认删除草稿“${row.applyNo || row.custName || row.id}”？`, '删除草稿', { type: 'warning' });
    await deleteLoanApplication(row.id);
    ElMessage.success('资产立项草稿已删除');
    await loadList();
  } catch {
    // 不伪造删除结果。
  }
}

async function cancelApplication(row) {
  try {
    const result = await ElMessageBox.prompt('请填写撤回理由（必填）', '撤回资产立项申请', {
      type: 'warning', inputPattern: /\S+/, inputErrorMessage: '撤回理由必填'
    });
    const reason = String(result?.value || '').trim();
    if (!reason) return;
    await cancelLoanApplication(row.id, reason);
    ElMessage.success('资产立项申请已撤回');
    await loadList();
  } catch {
    // 用户取消或后端失败均保留原状态。
  }
}

const claimedTaskIds = new Set();
const claimInFlight = new Map();

/** 未明确为 false 的任务不能被当成已签收，审批/驳回前统一先认领。 */
async function ensureClaimed(row) {
  const taskId = row?.taskId || row?.id;
  if (!taskId || row?.claimable === false || claimedTaskIds.has(taskId)) return;
  if (!claimInFlight.has(taskId)) {
    const request = claimTask(taskId)
      .then(result => {
        claimedTaskIds.add(taskId);
        row.claimable = false;
        return result;
      })
      .finally(() => claimInFlight.delete(taskId));
    claimInFlight.set(taskId, request);
  }
  return claimInFlight.get(taskId);
}

async function claimRow(row) {
  if (!row?.taskId && !row?.id) return;
  try {
    await claimTask(row.taskId || row.id);
    row.claimable = false;
    claimedTaskIds.add(row.taskId || row.id);
    ElMessage.success('任务已签收');
    await loadTasks();
  } catch {
    // 保留原状态。
  }
}

async function taskOpinion(title) {
  const result = await ElMessageBox.prompt('请填写审批意见（必填）', title, {
    type: 'warning', inputPattern: /\S+/, inputErrorMessage: '审批意见必填'
  });
  return String(result?.value || '').trim();
}

async function approveRow(row) {
  try {
    const opinion = await taskOpinion('审批通过');
    await ensureClaimed(row);
    await approveTask(row.taskId || row.id, opinion);
    ElMessage.success('审批已通过');
    await loadTasks();
  } catch {
    // 用户取消或写失败都不改变任务状态。
  }
}

async function rejectRow(row) {
  try {
    const opinion = await taskOpinion('驳回结束');
    await ensureClaimed(row);
    await rejectTask(row.taskId || row.id, opinion);
    ElMessage.success('申请已驳回结束');
    await loadTasks();
    await loadList();
  } catch {
    // 不将驳回伪装为退回发起人。
  }
}

function handleRouteEntry() {
  if (route.query?.tab === 'done' || route.query?.tab === 'todo') {
    taskTab.value = route.query.tab;
  }
  const queryLoanId = String(route.query?.loanId || '');
  const pathId = route.params?.id ? String(route.params.id) : '';
  if (String(route.path || '').endsWith('/new')) {
    openCreate();
    return;
  }
  if (queryLoanId || pathId) {
    openDetail({ id: queryLoanId || pathId, bizId: queryLoanId || undefined, processInstanceId: route.query?.processInstanceId });
  }
}

onMounted(() => {
  handleRouteEntry();
  loadList();
  loadTasks();
});

defineExpose({
  query,
  rows,
  total,
  tasks,
  editingLoan,
  formVisible,
  taskTab,
  loadList,
  loadTasks,
  search,
  openCreate,
  openEdit,
  openDetail,
  openTaskDetail,
  cancelApplication,
  claimRow,
  approveRow,
  rejectRow,
  taskNodeName,
  handleRouteEntry
});
</script>

<style scoped lang="scss">
.business-application { gap: var(--space-3); }
.task-panel { margin-top: 0; }
.task-toolbar { align-items: flex-start; }
.task-toolbar :deep(.el-radio-group) { margin-left: auto; }
.sub-id { overflow: hidden; color: var(--color-text-muted); font-size: 12px; line-height: 18px; text-overflow: ellipsis; white-space: nowrap; }
.inline-state { padding: 12px; color: var(--color-text-muted); text-align: center; font-size: 13px; }
.error-state { padding: 8px 16px; color: var(--color-danger-fg); font-size: 13px; }
@media (max-width: 800px) { .task-toolbar { flex-wrap: wrap; } .task-toolbar :deep(.el-radio-group) { margin-left: 0; } }
</style>
