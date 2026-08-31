<template>
  <div class="re-task-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">{{ task?.title || '任务详情' }}</h2>
        <p class="page-desc">查看任务配置、各党支部填报情况并导出任务数据</p>
      </div>
      <div class="header-actions">
        <el-button @click="handleBack">返回任务管理</el-button>
        <el-button type="primary" :loading="exportState.status === 'SUBMITTING'" @click="openExportDialog">
          <el-icon><Download /></el-icon>
          导出任务数据
        </el-button>
      </div>
    </div>

    <el-card v-if="task" class="config-card" shadow="never">
      <template #header>
        <div class="card-header-title">任务配置</div>
      </template>
      <el-descriptions :column="3" border class="task-descriptions">
        <el-descriptions-item label="任务性质">{{ natureLabel(task.nature) }}</el-descriptions-item>
        <el-descriptions-item label="任务类型">{{ task.typeName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="任务对象">{{ audienceLabel(task.audienceType) }}</el-descriptions-item>
        <el-descriptions-item label="周期/时间">{{ formatTaskWindow(task) }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ task.publishedAt || '—' }}</el-descriptions-item>
        <el-descriptions-item label="附件要求">
          {{ task.requiresFile ? `是（${(task.allowedFileTypes || []).join('、') || '按系统限制'}）` : '否' }}
        </el-descriptions-item>
        <el-descriptions-item label="任务说明" :span="3">
          <span v-for="(part, index) in descriptionParts" :key="`${part.type}-${index}`">
            <a
              v-if="part.type === 'link'"
              :href="part.href"
              :target="part.target"
              :rel="part.rel"
              data-test="description-link"
            >{{ part.value }}</a>
            <span v-else>{{ part.value }}</span>
          </span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="assignment-card" shadow="never" v-loading="loading">
      <template #header>
        <div class="table-header">
          <div>
            <span class="card-header-title">党支部填报情况</span>
            <span class="table-count">共 {{ total }} 个任务实例</span>
          </div>
          <div class="assignment-filters">
            <el-input v-model="assignmentQuery.keyword" clearable placeholder="搜索党支部/填报人" style="width: 210px" />
            <el-button type="primary" plain @click="handleAssignmentSearch">查询</el-button>
          </div>
        </div>
      </template>

      <el-table v-if="assignments.length" :data="assignments" stripe style="width: 100%">
        <el-table-column prop="branchName" label="党支部" min-width="150" show-overflow-tooltip />
        <el-table-column label="填报状态" width="110" align="center">
          <template #default="{ row }">
            <span :class="['status-badge', statusClass(row)]">{{ assignmentStatusLabel(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="填报人" width="120">
          <template #default="{ row }">
            <span :data-test="row.isUnreported ? 'unreported-submitter' : undefined">{{ row.submitterName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="填报时间" width="170">
          <template #default="{ row }">
            <span :data-test="row.isUnreported ? 'unreported-time' : undefined">{{ row.submittedAt }}</span>
          </template>
        </el-table-column>
        <el-table-column label="填报内容" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content }}</template>
        </el-table-column>
        <el-table-column label="附件" min-width="180">
          <template #default="{ row }">
            <span v-if="!row.files.length" class="muted-text">—</span>
            <span v-else class="file-list">
              <el-button
                v-for="file in row.files"
                :key="file.fileId || file.fileName"
                link
                type="primary"
                class="file-link"
                @click="downloadAttachment(row, file)"
              >
                <el-icon><Download /></el-icon>{{ file.fileName }}
              </el-button>
            </span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无支部填报数据" />

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
    </el-card>

    <el-card v-if="exportState.status !== 'IDLE'" class="export-state-card" shadow="never">
      <div class="export-state-row">
        <div>
          <span class="card-header-title">导出状态</span>
          <span class="export-state-label">{{ exportStatusLabel }}</span>
          <span v-if="exportState.sheetCount" class="muted-text">
            {{ exportState.totalRows }} 行，{{ exportState.sheetCount }} 个 Sheet（单 Sheet ≤ 5000 行）
          </span>
        </div>
        <div class="export-state-actions">
          <el-button v-if="exportState.status === 'SUCCEEDED'" type="primary" @click="downloadExport">
            下载 ZIP
          </el-button>
          <el-button v-if="exportState.status === 'FAILED'" type="primary" plain @click="handleExport">
            重新导出
          </el-button>
        </div>
      </div>
      <div v-if="exportState.errorMessage" class="field-error">{{ exportState.errorMessage }}</div>
    </el-card>

    <el-dialog v-model="exportDialogVisible" title="选择导出明细项" width="520px">
      <p class="dialog-tip">四大维度任务需要先选择要导出的明细项，导出结果仍为一个 ZIP 文件。</p>
      <el-checkbox-group v-model="selectedDetailCodes" class="detail-options">
        <el-checkbox v-for="item in materialDetailOptions" :key="item.value" :label="item.value">
          {{ item.label }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="exportDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleExport">确认导出</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Download } from '@element-plus/icons-vue';
import {
  createTaskExport,
  downloadTaskAttachment as fetchTaskAttachment,
  downloadTaskExport as fetchTaskExport,
  getTaskDetail,
  getTaskExportStatus,
  listMaterialDetailItems,
  listTaskAssignments
} from '@/api/redengine';
import {
  audienceLabel,
  buildExportRequest,
  formatTaskWindow,
  isFourDimensionTask,
  linkifyDescription,
  natureLabel,
  normalizeAssignmentPage,
  normalizeTask
} from './task-domain';

const route = useRoute();
const router = useRouter();
const task = ref(null);
const assignments = ref([]);
const materialDetailOptions = ref([]);
const loading = ref(false);
const pageNo = ref(1);
const pageSize = ref(10);
const total = ref(0);
const assignmentQuery = reactive({ keyword: '' });
const appliedAssignmentQuery = ref({});
const exportDialogVisible = ref(false);
const selectedDetailCodes = ref([]);
const exportState = reactive({
  exportId: '',
  status: 'IDLE',
  totalRows: 0,
  sheetCount: 0,
  errorMessage: ''
});
let exportTimer = null;

function routeTaskId() {
  const value = route.params.taskId;
  return /^\d+$/.test(String(value)) ? Number(value) : value;
}

function optionFromValue(value) {
  if (typeof value === 'string' || typeof value === 'number') return { value, label: String(value) };
  return {
    value: value?.value ?? value?.code ?? value?.id,
    label: value?.label ?? value?.name ?? value?.itemName ?? String(value?.value ?? '')
  };
}

function optionList(value) {
  const items = Array.isArray(value) ? value : value?.items || value?.records || [];
  return items.map(optionFromValue).filter((item) => item.value !== undefined && item.value !== null);
}

const descriptionParts = computed(() => linkifyDescription(task.value?.description || ''));
const isFourDimensionTaskView = computed(() => isFourDimensionTask(task.value || {}));

const exportStatusLabel = computed(() => ({
  SUBMITTING: '正在创建导出任务…',
  QUEUED: '排队中…',
  RUNNING: '生成中…',
  SUCCEEDED: '导出已完成',
  FAILED: '导出失败',
  CANCELLED: '导出已取消',
  EXPIRED: '导出已过期'
}[exportState.status] || '—'));

async function loadTask() {
  loading.value = true;
  const taskId = routeTaskId();
  try {
    const [detailResult, assignmentResult, detailItemsResult] = await Promise.allSettled([
      getTaskDetail(taskId),
      listTaskAssignments(taskId, { pageNo: pageNo.value, pageSize: pageSize.value, ...appliedAssignmentQuery.value }),
      listMaterialDetailItems()
    ]);
    if (detailResult.status !== 'fulfilled') throw detailResult.reason;
    const detail = detailResult.value?.task || detailResult.value;
    task.value = normalizeTask(detail || {});
    const assignmentPage = assignmentResult.status === 'fulfilled'
      ? normalizeAssignmentPage(assignmentResult.value)
      : { records: [], total: 0 };
    assignments.value = assignmentPage.records;
    total.value = assignmentPage.total;
    materialDetailOptions.value = detailItemsResult.status === 'fulfilled'
      ? optionList(detailItemsResult.value)
      : [];
    if (isFourDimensionTask(task.value) && selectedDetailCodes.value.length === 0) {
      selectedDetailCodes.value = [...(task.value.itemCodes || [])];
    }
  } catch {
    task.value = null;
    assignments.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

async function handleAssignmentSearch() {
  appliedAssignmentQuery.value = { ...assignmentQuery };
  pageNo.value = 1;
  await loadTask();
}

async function handlePageChange(nextPage) {
  pageNo.value = nextPage;
  await loadTask();
}

async function handleSizeChange(nextSize) {
  pageSize.value = nextSize;
  pageNo.value = 1;
  await loadTask();
}

function handleBack() {
  router.push('/redengine/task-management');
}

function statusClass(row) {
  return String(row.status || '').toLowerCase().replace(/_/g, '-') || 'unknown';
}

function assignmentStatusLabel(row) {
  if (row.isUnreported) return '未上报';
  return {
    TODO: '待填报',
    REPORTER_PENDING: '待填报',
    BRANCH_PENDING: '支部审核中',
    BRANCH_APPROVED: '待提交组织',
    ORG_PENDING: '组织审核中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    REJECTED_BY_BRANCH: '支部驳回',
    REJECTED_BY_ORG: '组织驳回',
    SUBMITTED: '已提交',
    COMPLETED: '已完成',
    OVERDUE_UNREPORTED: '逾期未上报',
    CANCELLED: '已取消'
  }[row.status] || row.status || '—';
}

function openExportDialog() {
  if (isFourDimensionTaskView.value) {
    exportDialogVisible.value = true;
    return;
  }
  handleExport();
}

function clearExportTimer() {
  if (exportTimer) {
    clearTimeout(exportTimer);
    exportTimer = null;
  }
}

async function pollExportStatus(exportId) {
  clearExportTimer();
  try {
    const status = await getTaskExportStatus(exportId);
    const rawStatus = status?.status || 'RUNNING';
    exportState.status = {
      PENDING: 'QUEUED',
      SUCCESS: 'SUCCEEDED'
    }[rawStatus] || rawStatus;
    exportState.totalRows = Number(status?.totalRows ?? 0);
    exportState.sheetCount = Number(status?.sheetCount ?? 0);
    exportState.errorMessage = status?.errorMessage || '';
    if (exportState.status === 'QUEUED' || exportState.status === 'RUNNING') {
      exportTimer = setTimeout(() => pollExportStatus(exportId), 1000);
    }
  } catch (error) {
    exportState.status = 'FAILED';
    exportState.errorMessage = error?.message || '导出状态查询失败';
  }
}

async function handleExport() {
  let payload;
  try {
    payload = buildExportRequest(task.value || {}, selectedDetailCodes.value);
  } catch (error) {
    ElMessage.warning(error.message);
    return;
  }
  exportDialogVisible.value = false;
  clearExportTimer();
  exportState.status = 'SUBMITTING';
  exportState.errorMessage = '';
  exportState.totalRows = 0;
  exportState.sheetCount = 0;
  try {
    const result = await createTaskExport(routeTaskId(), payload);
    const exportId = result?.exportId ?? result?.id ?? result?.taskId;
    if (!exportId) throw new Error('导出任务编号缺失');
    exportState.exportId = exportId;
    exportState.status = 'QUEUED';
    await pollExportStatus(exportId);
  } catch (error) {
    exportState.status = 'FAILED';
    exportState.errorMessage = error?.message || '导出失败';
  }
}

function saveBlob(blob, filename) {
  const objectUrl = window.URL?.createObjectURL?.(blob);
  if (!objectUrl) return;
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = filename;
  anchor.click();
  window.setTimeout(() => window.URL?.revokeObjectURL?.(objectUrl), 0);
}

async function downloadExport() {
  if (!exportState.exportId) return;
  try {
    const blob = await fetchTaskExport(exportState.exportId);
    saveBlob(blob, `${task.value?.title || '任务导出'}.zip`);
  } catch {
    // 统一 HTTP 拦截器负责展示下载错误。
  }
}

async function downloadAttachment(row, file) {
  if (!row?.assignmentId || !file?.fileId) return;
  try {
    const blob = await fetchTaskAttachment(routeTaskId(), row.assignmentId, file.fileId);
    saveBlob(blob, file.fileName || '附件');
  } catch {
    // 统一 HTTP 拦截器负责展示下载错误。
  }
}

onMounted(loadTask);
onBeforeUnmount(clearExportTimer);

defineExpose({
  task,
  assignments,
  assignmentQuery,
  exportState,
  selectedDetailCodes,
  materialDetailOptions,
  handleAssignmentSearch,
  handlePageChange,
  handleSizeChange,
  handleExport,
  openExportDialog,
  downloadExport,
  downloadAttachment
});
</script>

<style scoped lang="scss">
.re-task-page { padding: 0; }

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0 0 6px;
  color: #1e293b;
  font-size: 22px;
  font-weight: 700;
}

.page-desc { margin: 0; color: #64748b; font-size: 13px; }

.header-actions { display: flex; gap: 10px; }

.config-card,
.assignment-card,
.export-state-card {
  margin-bottom: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

:deep(.el-card__body) { padding: 16px; }

.card-header-title { color: #1e293b; font-size: 15px; font-weight: 600; }

.table-count { margin-left: 10px; color: #94a3b8; font-size: 12px; }

.task-descriptions { font-size: 13px; }
:deep(.task-descriptions .el-descriptions__label) { color: #475569; background: #f8fafc; }
:deep(.task-descriptions .el-descriptions__content) { color: #334155; }

.task-descriptions a { color: #2563eb; text-decoration: underline; }

.table-header,
.export-state-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.assignment-filters { display: flex; align-items: center; gap: 8px; }

.status-badge {
  display: inline-block;
  padding: 3px 9px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.status-badge.approved,
.status-badge.submitted { color: #166534; background: #dcfce7; }
.status-badge.org-pending,
.status-badge.branch-pending { color: #92400e; background: #fef9c3; }
.status-badge.rejected,
.status-badge.rejected-by-branch,
.status-badge.rejected-by-org { color: #b91c1c; background: #fee2e2; }
.status-badge.unreported,
.status-badge.overdue-unreported { color: #b45309; background: #ffedd5; }
.status-badge.reporter-pending,
.status-badge.branch-approved,
.status-badge.todo,
.status-badge.completed,
.status-badge.cancelled,
.status-badge.unknown { color: #64748b; background: #f1f5f9; }

.file-list { display: inline-flex; flex-wrap: wrap; gap: 4px; }
.file-link { height: auto; padding: 0; font-size: 12px; }
.muted-text { color: #94a3b8; font-size: 12px; }

.pager { display: flex; justify-content: flex-end; padding-top: 14px; }

.export-state-label { margin-left: 12px; color: #2563eb; font-size: 13px; }
.export-state-actions { display: flex; gap: 8px; }

.dialog-tip { margin: 0 0 16px; color: #64748b; font-size: 13px; line-height: 1.6; }
.detail-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.field-error { margin-top: 8px; color: #dc2626; font-size: 12px; }

:deep(.el-table) {
  .el-table__header th { color: #475569; font-size: 13px; font-weight: 600; background: #f8fafc; }
  .el-table__row td { padding: 10px 0; color: #334155; font-size: 13px; }
}
</style>
