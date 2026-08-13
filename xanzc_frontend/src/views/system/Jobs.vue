<template>
<main v-bp-overflow-tooltip class="bp-crud jobs-page" aria-labelledby="jobs-page-title" :aria-busy="loading ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="jobs-page-title"><span class="sub">查看调度状态与执行日志；暂停、恢复和手动触发均直接影响线上调度。</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="任务调度操作">
        <el-button @click="reload">刷新</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="调度任务筛选">
      <el-form class="filter-form" inline size="default" aria-label="调度任务筛选条件" @submit.prevent="onSearch">
        <el-form-item label="关键字">
          <el-input v-model="keyword" clearable placeholder="任务 Key / 名称" aria-label="按任务名称或 Key 搜索" style="width:260px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section
      class="card-section data-panel jobs-table-panel"
      aria-label="调度任务列表"
      aria-labelledby="jobs-table-heading"
      aria-describedby="jobs-table-state"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <div class="toolbar">
        <div>
          <h2 id="jobs-table-heading" class="section-title">调度任务</h2>
          <p class="hint">手动触发必须填写数据日期和原因；任务运行中禁止重复暂停或恢复。</p>
        </div>
        <p id="jobs-table-state" class="table-state" role="status" aria-live="polite">{{ loading ? '调度任务加载中' : rows.length ? `共 ${total} 个任务` : '暂无调度任务' }}</p>
      </div>
      <p v-if="loadError" class="error-state" role="alert">{{ loadError }} <el-button link type="primary" @click="reload">重试</el-button></p>
      <el-table :data="rows" size="default" empty-text="暂无调度任务" v-loading="loading" aria-labelledby="jobs-table-heading" aria-describedby="jobs-table-state">
        <el-table-column label="任务 Key" min-width="230"><template #default="{ row }"><code class="mono">{{ row.jobKey }}</code></template></el-table-column>
        <el-table-column prop="jobName" label="任务名称" min-width="190" show-overflow-tooltip />
        <el-table-column label="Cron" width="170"><template #default="{ row }"><code class="mono">{{ row.cronExpr }}</code></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :class="statusCls(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="允许手动" width="104" align="center"><template #default="{ row }">{{ row.allowManualTrigger ? '允许' : '不允许' }}</template></el-table-column>
        <el-table-column prop="lastRunTime" label="上次执行" width="170" :formatter="fmtDateTimeCol" />
        <el-table-column prop="nextFireTime" label="下次执行" width="170" :formatter="fmtDateTimeCol" />
        <el-table-column label="操作" width="258" fixed="right">
          <template #default="{ row }">
            <div class="row-actions" role="group" :aria-label="`${row.jobName || row.jobKey} 操作`">
              <el-button link type="primary" size="small" @click="openLogs(row)">日志</el-button>
              <el-button
                v-if="row.status === 'ACTIVE'"
                link
                type="primary"
                size="small"
                :loading="isJobPending(row.id)"
                :disabled="isJobPending(row.id)"
                @click="onPause(row)"
              >暂停</el-button>
              <el-button
                v-else
                link
                type="primary"
                size="small"
                :loading="isJobPending(row.id)"
                :disabled="isJobPending(row.id)"
                @click="onResume(row)"
              >恢复</el-button>
              <el-button v-if="row.allowManualTrigger" link type="warning" size="small" @click="onTrigger(row)">手动触发</el-button>
              <span v-else class="disabled-op">不允许手动触发</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <nav class="pager" aria-label="调度任务分页">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="reload"
          @size-change="onSizeChange"
        />
      </nav>
    </section>

    <el-dialog v-model="logDlg.show" class="bp-crud-dialog" :title="`执行日志 · ${logDlg.jobName}`" width="900px" top="5vh" @closed="onLogsClosed">
      <div class="dialog-toolbar">
        <p class="hint">任务日志按触发时间倒序展示，失败原因以原始审计信息为准。</p>
        <p class="table-state" role="status" aria-live="polite">{{ logDlg.loading ? '日志加载中' : logDlg.rows.length ? `共 ${logDlg.total} 条` : '暂无日志' }}</p>
      </div>
      <p v-if="logDlg.error" class="error-state" role="alert">{{ logDlg.error }} <el-button link type="primary" @click="loadLogs">重试</el-button></p>
      <el-table :data="logDlg.rows" size="default" empty-text="暂无日志" v-loading="logDlg.loading" :aria-busy="logDlg.loading ? 'true' : 'false'">
        <el-table-column label="触发类型" width="100"><template #default="{ row }"><el-tag :class="row.triggerType === 'MANUAL' ? 'tag-warning' : 'tag-info'" effect="plain" size="small">{{ row.triggerType === 'MANUAL' ? '手动' : '定时' }}</el-tag></template></el-table-column>
        <el-table-column label="执行人" width="140"><template #default="{ row }"><span>{{ row.triggerType === 'MANUAL' ? (row.operatorName || row.createdBy || '-') : '系统' }}</span></template></el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="170" :formatter="fmtDateTimeCol" />
        <el-table-column prop="endTime" label="结束时间" width="170" :formatter="fmtDateTimeCol" />
        <el-table-column label="状态" width="104"><template #default="{ row }"><el-tag :class="logStatusCls(row.status)" effect="plain" size="small">{{ row.status || '-' }}</el-tag></template></el-table-column>
        <el-table-column label="处理状态" width="112"><template #default="{ row }"><el-tag v-if="row.processStatus" :class="logStatusCls(row.processStatus)" effect="plain" size="small">{{ procStatusLabel(row.processStatus) }}</el-tag><span v-else>-</span></template></el-table-column>
        <el-table-column prop="errorMsg" label="错误原因" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ row.errorMsg || '-' }}</template></el-table-column>
      </el-table>
      <nav class="pager" aria-label="执行日志分页">
        <el-pagination
          v-model:current-page="logDlg.pageNo"
          v-model:page-size="logDlg.pageSize"
          :total="logDlg.total"
          :page-sizes="[10, 20, 50]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadLogs"
          @size-change="onLogSizeChange"
        />
      </nav>
      <template #footer><el-button @click="logDlg.show = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="trgDlg.show" class="bp-crud-dialog" title="手动触发确认" width="500px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon title="手动触发属于高危操作；计算类任务会按所选数据日期启动计算。" />
      <el-form label-width="110px" size="default" class="trigger-form">
        <el-form-item label="任务"><el-input :model-value="trgDlg.jobKey" disabled /></el-form-item>
        <el-form-item label="数据日期" required><el-date-picker v-model="trgDlg.dataDate" type="date" value-format="YYYY-MM-DD" style="width:100%" placeholder="选择不晚于今天的日期" :disabled-date="trgDlg.disabledDate" /></el-form-item>
        <el-form-item v-if="trgDlg.jobKey === 'LEVEL1_METRIC_CALC'" label="业绩分配日期"><el-date-picker v-model="trgDlg.allocDate" type="date" value-format="YYYY-MM-DD" style="width:100%" clearable placeholder="可选，留空默认使用数据日期" :disabled-date="trgDlg.disabledDate" /></el-form-item>
        <el-form-item label="触发原因" required><el-input v-model="trgDlg.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="必填，将写入审计日志" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="trgDlg.saving" @click="trgDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="trgDlg.saving" :disabled="trgDlg.saving" @click="confirmTrigger">确认触发</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { fmtDateTimeCol } from '@/utils/datetime';
import { listJobs, pauseJob, resumeJob, triggerJob, listJobLogs } from '@/api/system';

const statusCls = (status) => ({ ACTIVE: 'tag-success', PAUSED: 'tag-warning', DISABLED: 'tag-danger' }[status] || 'tag-info');
const statusLabel = (status) => ({ ACTIVE: '运行中', PAUSED: '已暂停', DISABLED: '已禁用' }[status] || status || '-');
const procStatusLabel = (status) => ({ SUCCESS: '成功', FAILED: '失败', PARTIAL_FAILED: '部分失败', RUNNING: '执行中' }[status] || status);
const logStatusCls = (status) => ({ SUCCESS: 'tag-success', FAILED: 'tag-danger', PARTIAL_FAILED: 'tag-warning', RUNNING: 'tag-warning' }[status] || 'tag-info');

const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const loadError = ref('');
const pageNo = ref(1);
const pageSize = ref(20);
const keyword = ref('');

async function reload() {
  loading.value = true;
  loadError.value = '';
  try {
    const result = await listJobs({ pageNo: pageNo.value, pageSize: pageSize.value, keyword: keyword.value || undefined });
    const items = result?.records || (Array.isArray(result) ? result : []);
    rows.value = items;
    total.value = result?.total ?? items.length;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    loadError.value = `调度任务加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    loading.value = false;
  }
}
function onSearch() {
  pageNo.value = 1;
  reload();
}
function resetFilters() {
  keyword.value = '';
  pageNo.value = 1;
  reload();
}
function onSizeChange() {
  pageNo.value = 1;
  reload();
}

const pendingJobIds = ref(new Set());
const isJobPending = (id) => pendingJobIds.value.has(String(id));
function setJobPending(id, value) {
  const next = new Set(pendingJobIds.value);
  if (value) next.add(String(id));
  else next.delete(String(id));
  pendingJobIds.value = next;
}
async function runJobAction(row, action, successMessage) {
  if (row?.id == null || isJobPending(row.id)) return;
  setJobPending(row.id, true);
  try {
    await action(row.id);
    ElMessage.success(successMessage);
    await reload();
  } catch (error) {
    ElMessage.error(`${successMessage}失败：${error?.message || error}`);
  } finally {
    setJobPending(row.id, false);
  }
}
async function onPause(row) {
  await runJobAction(row, pauseJob, '任务已暂停');
}
async function onResume(row) {
  await runJobAction(row, resumeJob, '任务已恢复');
}

const trgDlg = reactive({
  show: false, saving: false, jobId: '', jobKey: '',
  dataDate: new Date().toISOString().slice(0, 10), allocDate: '', reason: '',
  disabledDate: (date) => { const today = new Date(); today.setHours(0, 0, 0, 0); return date.getTime() > today.getTime(); }
});
function onTrigger(row) {
  if (trgDlg.saving) return;
  trgDlg.jobId = row.id;
  trgDlg.jobKey = row.jobKey;
  trgDlg.dataDate = new Date().toISOString().slice(0, 10);
  trgDlg.allocDate = '';
  trgDlg.reason = '';
  trgDlg.show = true;
}
async function confirmTrigger() {
  if (trgDlg.saving) return;
  const today = new Date().toISOString().slice(0, 10);
  if (!trgDlg.dataDate) return ElMessage.warning('请选择数据日期');
  if (trgDlg.dataDate > today) return ElMessage.warning(`数据日期不能大于今天（${today}）`);
  if (trgDlg.allocDate && trgDlg.allocDate > today) return ElMessage.warning(`业绩分配日期不能大于今天（${today}）`);
  if (!trgDlg.reason.trim()) return ElMessage.warning('请填写触发原因');
  trgDlg.saving = true;
  try {
    const allocDate = trgDlg.jobKey === 'LEVEL1_METRIC_CALC' ? (trgDlg.allocDate || undefined) : undefined;
    await triggerJob(trgDlg.jobId, trgDlg.reason.trim(), trgDlg.dataDate, allocDate);
    ElMessage.success('任务已触发');
    trgDlg.show = false;
    await reload();
  } catch (error) {
    ElMessage.error(`触发失败：${error?.message || error}`);
  } finally {
    trgDlg.saving = false;
  }
}

const logDlg = reactive({ show: false, jobId: null, jobName: '', rows: [], total: 0, loading: false, error: '', pageNo: 1, pageSize: 10 });
async function openLogs(row) {
  logDlg.jobId = row.id;
  logDlg.jobName = row.jobName || row.jobKey || '';
  logDlg.pageNo = 1;
  logDlg.total = 0;
  logDlg.rows = [];
  logDlg.error = '';
  logDlg.show = true;
  await loadLogs();
}
async function loadLogs() {
  if (!logDlg.jobId) return;
  logDlg.loading = true;
  logDlg.error = '';
  try {
    const result = await listJobLogs(logDlg.jobId, { pageNo: logDlg.pageNo, pageSize: logDlg.pageSize });
    const items = result?.records || (Array.isArray(result) ? result : []);
    logDlg.rows = items;
    logDlg.total = result?.total ?? items.length;
  } catch (error) {
    logDlg.rows = [];
    logDlg.total = 0;
    logDlg.error = `执行日志加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    logDlg.loading = false;
  }
}
function onLogSizeChange() {
  logDlg.pageNo = 1;
  loadLogs();
}
function onLogsClosed() {
  logDlg.rows = [];
  logDlg.error = '';
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.jobs-table-panel { min-width: 0; }
.row-actions { align-items: center; display: flex; flex-wrap: wrap; gap: var(--space-1); }
.mono { background: var(--color-surface-soft); border: 1px solid var(--color-border); border-radius: var(--radius-control); color: var(--color-text); font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace; font-size: 12px; padding: 2px var(--space-2); }
.disabled-op { color: var(--color-text-muted); font-size: 12px; }
.dialog-toolbar { align-items: flex-start; display: flex; gap: var(--space-3); justify-content: space-between; margin-bottom: var(--space-3); }
.dialog-toolbar .hint { margin: 0; }
.trigger-form { margin-top: var(--space-4); }
.error-state {
  background: var(--color-danger-bg);
  border-left: 3px solid var(--color-danger-fg);
  color: var(--color-danger-fg);
  font-size: 12px;
  line-height: 18px;
  margin-bottom: var(--space-3);
  padding: var(--space-2) var(--space-3);
}
</style>
