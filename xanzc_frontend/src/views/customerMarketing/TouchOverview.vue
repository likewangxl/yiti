<template>
  <main class="page bp-crud touch-task-page" v-bp-overflow-tooltip aria-labelledby="touch-overview-title">
    <header class="page-h">
      <div>
        <PageTitle id="touch-overview-title" />
        <span class="sub">机构管理人员查看权限范围内的触达任务、参与人、日志、附件与定位。</span>
      </div>
    </header>

    <section class="summary-grid" aria-label="触达任务一览摘要">
      <article v-for="item in cards" :key="item.key" class="summary-card" :class="`tone-${item.tone}`" role="status">
        <span class="summary-label">{{ item.label }}</span>
        <strong class="summary-value">{{ item.value }}</strong>
        <small>{{ item.hint }}</small>
      </article>
    </section>

    <section class="card-section filter-bar" aria-label="触达任务一览筛选">
      <el-form inline class="filter-form" @submit.prevent>
        <el-form-item label="任务编号 / 客户名称">
          <el-input v-model="q.keyword" clearable placeholder="输入编号或客户名称" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="执行人工号"><el-input v-model="q.assigneeEmpId" clearable placeholder="输入工号" /></el-form-item>
        <el-form-item label="机构代码"><el-input v-model="q.orgId" clearable placeholder="输入机构代码" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.status" clearable placeholder="全部" style="width:140px">
            <el-option v-for="s in statuses" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <el-button @click="download">导出</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="card-section data-panel" aria-label="触达任务一览数据">
      <div class="toolbar">
        <div>
          <h2 class="section-title">触达任务明细</h2>
          <p class="hint">状态标签同时提供文字说明；参与人、日志数量与客户信息不依赖颜色或隐藏字段。</p>
        </div>
        <div class="toolbar-actions">
          <el-button type="primary" :disabled="selectedRows.length === 0" @click="openBatchAssign">批量改派</el-button>
          <span class="result-count">共 {{ total }} 条</span>
        </div>
      </div>
      <p v-if="loadError" class="table-state error-state" role="alert">{{ loadError }}</p>
      <el-table
        v-else
        ref="taskTable"
        class="task-table"
        :data="rows"
        v-loading="loading"
        border
        stripe
        aria-label="触达任务一览列表"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" fixed="left" :selectable="isSelectable" />
        <el-table-column label="任务编号 / 类型" min-width="190" class-name="compact-stack-cell">
          <template #default="{row}">
            <div class="stack"><strong>{{ row.taskNo || row.id || '-' }}</strong><small>{{ taskTypeLabel(row.taskType) }}</small></div>
          </template>
        </el-table-column>
        <el-table-column label="客户" min-width="220" class-name="compact-stack-cell">
          <template #default="{row}">
            <div class="stack"><strong>{{ customerName(row) }}</strong><small>{{ row.custId || row.customerId || '-' }}</small></div>
          </template>
        </el-table-column>
        <el-table-column label="机构" min-width="135"><template #default="{row}">{{ row.orgName || row.orgId || '-' }}</template></el-table-column>
        <el-table-column label="执行人" width="125"><template #default="{row}">{{ row.assigneeName || row.assigneeEmpId || row.assigneeId || '-' }}</template></el-table-column>
        <el-table-column label="参与人" min-width="155"><template #default="{row}"><span :aria-label="`协同人员：${participantText(row)}`">{{ participantText(row) }}</span></template></el-table-column>
        <el-table-column label="状态" width="112" class-name="compact-status-cell">
          <template #default="{row}"><el-tag :type="taskTagType(row.taskStatus)" :aria-label="`任务状态：${taskStatusLabel(row.taskStatus)}`">{{ taskStatusLabel(row.taskStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="SLA" width="90" class-name="compact-status-cell">
          <template #default="{row}"><el-tag :type="slaTagType(row.slaStatus)" :aria-label="`SLA：${slaLabel(row.slaStatus)}`"><span class="sla-dot" :class="String(row.slaStatus || '').toLowerCase()" />{{ slaLabel(row.slaStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="日志" width="80" align="center"><template #default="{row}"><span class="log-count" :aria-label="`历史日志数量：${logCount(row)}`">{{ logCount(row) }}</span></template></el-table-column>
        <el-table-column label="计划完成" width="170"><template #default="{row}">{{ fmt(row.planFinishTime) }}</template></el-table-column>
        <el-table-column label="操作" width="90" fixed="right" class-name="operation-cell"><template #default="{row}"><el-button link type="primary" @click="open(row)">详情</el-button></template></el-table-column>
      </el-table>
    </section>

    <div class="pager" aria-label="触达任务一览分页">
      <el-pagination background layout="total, sizes, prev, pager, next" :total="total" v-model:current-page="pageNo" v-model:page-size="pageSize" :page-sizes="[10,20,50,100]" @change="load" />
    </div>
    <el-dialog v-model="batchAssignDlg.show" title="批量改派触达任务" width="560px" :close-on-click-modal="false">
      <el-alert
        title="仅允许改派待办理或办理中的任务，已完成和已取消任务不会被更新。"
        type="info"
        :closable="false"
        show-icon
        class="batch-assign-hint"
      />
      <el-form label-width="160px" class="batch-assign-form">
        <el-form-item label="已选任务数">{{ selectedRows.length }}</el-form-item>
        <el-form-item label="新办理人员工号" required>
          <el-input v-model="batchAssignDlg.newAssigneeEmpId" clearable maxlength="64" show-word-limit placeholder="请输入员工工号" />
        </el-form-item>
        <el-form-item label="改派原因" required>
          <el-input v-model="batchAssignDlg.reason" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="请填写改派原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="batchAssignDlg.saving" @click="batchAssignDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="batchAssignDlg.saving" @click="submitBatchAssign">确认改派</el-button>
      </template>
    </el-dialog>
    <TouchTaskDetailDialog v-model="detail.show" :task-id="detail.taskId" :allow-write="false" />
  </main>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import TouchTaskDetailDialog from '@/components/TouchTaskDetailDialog.vue';
import { batchAssignTouchTasks, exportTouchOverview, listTouchOverview } from '@/api/customerMarketing';
import { slaLabel, slaTagType, taskStatusLabel, taskTagType } from '@/utils/touchViewModel';

const statuses = [
  { label: '待办理', value: 'PENDING' },
  { label: '办理中', value: 'IN_PROGRESS' },
  { label: '已完成', value: 'SUCCESS' },
  { label: '已取消', value: 'CANCELLED' }
];
const q = reactive({ keyword: '', assigneeEmpId: '', orgId: '', status: '' });
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const loadError = ref('');
const pageNo = ref(1);
const pageSize = ref(20);
const detail = reactive({ show: false, taskId: '' });
const taskTable = ref(null);
const selectedRows = ref([]);
const batchAssignDlg = reactive({ show: false, newAssigneeEmpId: '', reason: '', saving: false });
const fmt = value => value ? String(value).replace('T', ' ').slice(0, 19) : '-';

const cards = computed(() => [
  { key: 'all', label: '当前页任务', value: rows.value.length, hint: `当前页 / 共${total.value}条`, tone: 'primary' },
  { key: 'progress', label: '在途任务', value: rows.value.filter(row => inFlight(row)).length, hint: '待办理或办理中', tone: 'warning' },
  { key: 'red', label: '红灯', value: rows.value.filter(row => row.slaStatus === 'RED').length, hint: '需要及时关注', tone: 'danger' },
  { key: 'success', label: '已完成', value: rows.value.filter(row => row.taskStatus === 'SUCCESS').length, hint: '已闭环任务', tone: 'success' },
  { key: 'logs', label: '触达日志', value: rows.value.reduce((sum, row) => sum + logCount(row), 0), hint: '当前页累计', tone: 'info' }
]);

function recordsOf(result) {
  if (Array.isArray(result)) return result;
  if (Array.isArray(result?.records)) return result.records;
  if (Array.isArray(result?.data?.records)) return result.data.records;
  if (Array.isArray(result?.data)) return result.data;
  return [];
}

function customerName(row) {
  return row?.custName || row?.customerName || row?.customerDisplayName || row?.custId || row?.customerId || '-';
}

function participantValues(row) {
  const value = row?.participantEmpIds ?? row?.participants ?? row?.participantNames;
  if (Array.isArray(value)) return value;
  if (value === undefined || value === null || value === '') return [];
  const text = String(value).trim();
  try {
    const parsed = JSON.parse(text);
    if (Array.isArray(parsed)) return parsed;
  } catch (_) { /* 兼容历史逗号分隔字段。 */ }
  return text.split(/[,，]/).map(item => item.trim()).filter(Boolean);
}

function participantText(row) {
  const values = participantValues(row).map(item => item?.name || item?.empId || item).filter(Boolean);
  return values.length ? values.join('、') : '无';
}

function logCount(row) {
  const count = Number(row?.logCount);
  if (Number.isFinite(count) && count >= 0) return count;
  return Array.isArray(row?.logs) ? row.logs.length : 0;
}

function taskTypeLabel(value) {
  return value === 'FIRST_TOUCH' ? '首次触达' : value === 'FOLLOW_UP' ? '再次触达' : value || '触达任务';
}

function inFlight(row) {
  return ['PENDING', 'IN_PROGRESS'].includes(String(row?.taskStatus || '').toUpperCase());
}

function isSelectable(row) {
  return inFlight(row);
}

function handleSelectionChange(selection) {
  selectedRows.value = selection.filter(row => isSelectable(row));
}

function clearSelection() {
  selectedRows.value = [];
  taskTable.value?.clearSelection?.();
}

function params() {
  return {
    keyword: q.keyword || undefined,
    assigneeEmpId: q.assigneeEmpId || undefined,
    orgId: q.orgId || undefined,
    status: q.status || undefined,
    pageNo: pageNo.value,
    pageSize: pageSize.value
  };
}

async function load() {
  clearSelection();
  loading.value = true;
  loadError.value = '';
  try {
    const result = await listTouchOverview(params());
    rows.value = recordsOf(result);
    total.value = Number(result?.total ?? result?.data?.total ?? rows.value.length) || 0;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    loadError.value = error?.message || '触达任务一览加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

function search() { pageNo.value = 1; void load(); }
function reset() { Object.assign(q, { keyword: '', assigneeEmpId: '', orgId: '', status: '' }); search(); }
function open(row) { detail.taskId = row.id; detail.show = true; }

function openBatchAssign() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先选择待办理或办理中的触达任务');
    return;
  }
  Object.assign(batchAssignDlg, { show: true, newAssigneeEmpId: '', reason: '', saving: false });
}

async function submitBatchAssign() {
  const taskIds = selectedRows.value.map(row => row.id).filter(Boolean);
  const newAssigneeEmpId = batchAssignDlg.newAssigneeEmpId.trim();
  const reason = batchAssignDlg.reason.trim();
  if (!taskIds.length) {
    ElMessage.warning('请先选择待办理或办理中的触达任务');
    return;
  }
  if (!newAssigneeEmpId) {
    ElMessage.warning('请输入新办理人员工号');
    return;
  }
  if (!reason) {
    ElMessage.warning('请填写改派原因');
    return;
  }
  batchAssignDlg.saving = true;
  try {
    const result = await batchAssignTouchTasks(taskIds, newAssigneeEmpId, reason);
    const updated = Number(result?.data ?? result);
    ElMessage.success(Number.isFinite(updated) ? `已成功改派 ${updated} 条触达任务` : '触达任务改派成功');
    batchAssignDlg.show = false;
    clearSelection();
    await load();
  } catch (error) {
    ElMessage.error(`触达任务改派失败：${error?.message || '请稍后重试'}`);
  } finally {
    batchAssignDlg.saving = false;
  }
}

async function download() {
  try {
    const blob = await exportTouchOverview(params());
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `触达任务_${Date.now()}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
    ElMessage.success('导出已开始');
  } catch (error) {
    ElMessage.error(error?.message || '导出失败，请稍后重试');
  }
}

void load();
</script>

<style scoped lang="scss">
.touch-task-page { --touch-soft: var(--color-surface-soft, #f8fafc); }
.page-h { justify-content: space-between; }
.page-h .sub { display: block; color: var(--color-text-muted, #909399); font-size: 12px; margin-top: 4px; }
.summary-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: var(--space-3, 12px); }
.summary-card { display: grid; gap: 4px; min-height: 98px; padding: 15px 16px; border: 1px solid var(--color-border, #ebeef5); border-radius: var(--radius-control, 4px); background: var(--color-surface, #fff); box-shadow: var(--shadow-surface, none); }
.summary-label { color: var(--color-text-muted, #909399); font-size: 12px; }
.summary-value { color: var(--color-text-strong, #303133); font-size: 25px; line-height: 1.1; font-variant-numeric: tabular-nums; }
.summary-card small { color: var(--color-text-muted, #909399); font-size: 11px; }
.summary-card.tone-primary { border-top: 3px solid var(--color-brand-700); }
.summary-card.tone-info { border-top: 3px solid var(--color-info-fg); }
.summary-card.tone-warning { border-top: 3px solid var(--color-warning-fg); }
.summary-card.tone-success { border-top: 3px solid var(--color-success-fg); }
.summary-card.tone-danger { border-top: 3px solid var(--color-danger-fg); }
.filter-bar { padding: var(--space-3, 12px) var(--space-4, 16px); }
.data-panel { padding: var(--space-4, 16px); }
.toolbar { align-items: flex-end; }
.toolbar-actions { display: flex; align-items: center; gap: var(--space-3, 12px); }
.section-title { margin: 0 0 3px; }
.hint, .result-count { color: var(--color-text-muted, #909399); font-size: 12px; }
.result-count { white-space: nowrap; }
.stack { display: grid; gap: 2px; min-width: 0; }
.stack strong { overflow: hidden; color: var(--color-text-strong, #303133); text-overflow: ellipsis; white-space: nowrap; }
.stack small { overflow: hidden; color: var(--color-text-muted, #909399); text-overflow: ellipsis; white-space: nowrap; }
.sla-dot { display: inline-block; width: 6px; height: 6px; margin-right: 5px; border-radius: 50%; background: currentColor; vertical-align: middle; }
.log-count { font-variant-numeric: tabular-nums; }
.table-state { padding: 20px; border: 1px solid var(--color-border, #ebeef5); background: var(--touch-soft); text-align: center; }
.error-state { color: var(--color-danger-fg, #b42318); background: var(--color-danger-bg, #fff1f2); }
.batch-assign-hint { margin-bottom: 16px; }
.batch-assign-form :deep(.el-form-item) { margin-bottom: 14px; }
.pager { display: flex; justify-content: flex-end; }
@media (max-width: 1000px) { .summary-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 650px) { .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .toolbar { align-items: flex-start; flex-direction: column; } }
</style>
