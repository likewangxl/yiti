<template>
  <div>
    <div class="page-h">
      <h1>任务监控 <span class="sub">指标重算任务 · 按指标汇总 / 执行 / 历史</span></h1>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" @click="openAdd">新增</el-button>
        <el-button type="warning" :disabled="selected.length === 0" @click="openBatch">
          批量执行{{ selected.length ? `（${selected.length}）` : '' }}
        </el-button>
      </div>
    </div>

    <!-- 过滤：任务类型（字典）+ 指标关键字 -->
    <div class="card-section">
      <el-form :inline="true" size="default">
        <el-form-item label="任务类型">
          <el-select v-model="query.taskType" style="width:160px">
            <el-option v-for="t in taskTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="指标">
          <el-input v-model="query.keyword" clearable placeholder="指标编码/名称" style="width:200px"
            @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-section table">
      <el-table ref="tableRef" :data="rows" size="default" v-loading="loading" empty-text="暂无任务记录"
        @selection-change="onSelectionChange" row-key="metricCode">
        <el-table-column type="selection" width="46" reserve-selection />
        <el-table-column label="指标" min-width="280" show-overflow-tooltip>
          <template #default="{row}">
            <code class="mono">{{ row.metricCode || '-' }}</code>
            <span v-if="row.metricName" class="sub-name"> · {{ row.metricName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{row}">{{ fmtTime(row.firstCreatedTime) }}</template>
        </el-table-column>
        <el-table-column label="计算次数" width="110" align="right">
          <template #default="{row}">{{ row.runCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openExecute(row)">执行</el-button>
            <el-button link type="primary" size="small" @click="openHistory(row)">历史</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="reload"
          @size-change="() => { pageNo = 1; reload(); }"
        />
      </div>
    </div>

    <!-- 新增 / 执行 共用对话框 -->
    <el-dialog v-model="execDlg.show" :title="execDlg.lockMetric ? '执行' : '新增'"
      width="520px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="任务类型">
          <el-select v-model="execDlg.taskType" :disabled="execDlg.lockMetric" style="width:100%">
            <el-option v-for="t in taskTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="指标" required>
          <el-input v-if="execDlg.lockMetric" :model-value="execDlg.metricCode" readonly />
          <el-select v-else v-model="execDlg.metricCode" filterable clearable
            placeholder="输入编码/名称模糊搜索" style="width:100%">
            <el-option v-for="m in metricOptions" :key="m.metricCode"
              :label="`${m.metricCode} · ${m.metricName || ''}`" :value="m.metricCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行时间" required>
          <el-date-picker v-model="execDlg.dataDate" type="date" value-format="YYYY-MM-DD"
            placeholder="选择数据日期（不能大于今天）" :disabled-date="disabledFuture" style="width:100%" />
        </el-form-item>
        <el-form-item label="原因" required>
          <el-input v-model="execDlg.reason" type="textarea" :rows="3" placeholder="高危操作，必填原因" />
        </el-form-item>
        <div class="audit-hint">⚠ 将写入 run_task 并记入审计日志</div>
      </el-form>
      <template #footer>
        <el-button @click="execDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="execDlg.submitting" @click="confirmExecute">确认执行</el-button>
      </template>
    </el-dialog>

    <!-- 批量执行对话框 -->
    <el-dialog v-model="batchDlg.show" title="批量执行" width="520px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="已选指标">
          <span>{{ batchDlg.metricCodes.length }} 个：{{ batchDlg.metricCodes.join('、') }}</span>
        </el-form-item>
        <el-form-item label="数据日期" required>
          <el-date-picker v-model="batchDlg.dataDate" type="date" value-format="YYYY-MM-DD"
            placeholder="选择数据日期（不能大于今天）" :disabled-date="disabledFuture" style="width:100%" />
        </el-form-item>
        <el-form-item label="原因" required>
          <el-input v-model="batchDlg.reason" type="textarea" :rows="3" placeholder="一条原因套用整批，高危必填" />
        </el-form-item>
        <div class="audit-hint">⚠ 逐指标同步执行，整批记入一条审计</div>
      </el-form>
      <template #footer>
        <el-button @click="batchDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="batchDlg.submitting" @click="confirmBatch">确认执行</el-button>
      </template>
    </el-dialog>

    <!-- 历史抽屉（右侧）-->
    <el-drawer v-model="hist.show" :title="`执行历史 · ${hist.metricCode || ''}`" size="52%"
      :destroy-on-close="true">
      <el-table :data="hist.rows" size="default" v-loading="hist.loading" empty-text="暂无执行记录">
        <el-table-column label="执行时间" width="170">
          <template #default="{row}">{{ fmtTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="170">
          <template #default="{row}">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="170">
          <template #default="{row}">{{ fmtTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="计算结果" min-width="160">
          <template #default="{row}">
            <el-tag :class="statusCls(row.status)" effect="plain" size="small">{{ statusLabel(row.status) }}</el-tag>
            <div v-if="row.errorMsg" class="err-inline" :title="row.errorMsg">{{ row.errorMsg }}</div>
          </template>
        </el-table-column>
        <el-table-column label="发起人" width="150">
          <template #default="{row}">{{ row.startedByName || row.startedBy || '-' }}</template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="hist.pageNo"
          v-model:page-size="hist.pageSize"
          :total="hist.total"
          :page-sizes="[20, 50]"
          background
          layout="total, sizes, prev, pager, next"
          @current-change="loadHistory"
          @size-change="() => { hist.pageNo = 1; loadHistory(); }"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listMetricSummary, batchExecuteMetrics, executeMetric, listMetrics, listRunTasks } from '@/api/perf';
import { listDictItems } from '@/api/system';

// 状态字典 + badge 配色
const STATUS_MAP = {
  PENDING: { label: '待执行', cls: 'tag-info' },
  RUNNING: { label: '执行中', cls: 'tag-warning' },
  SUCCESS: { label: '完成', cls: 'tag-success' },
  PARTIAL: { label: '部分成功', cls: 'tag-warning' },
  PARTIAL_FAILED: { label: '部分失败', cls: 'tag-warning' },
  FAILED: { label: '失败', cls: 'tag-danger' },
  CANCELLED: { label: '已取消', cls: 'tag-info' }
};
const statusCls = (s) => STATUS_MAP[s]?.cls || 'tag-info';
const statusLabel = (s) => STATUS_MAP[s]?.label || s || '-';

function fmtTime(t) {
  if (!t) return '-';
  return String(t).replace('T', ' ').slice(0, 19);
}
const today = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
const disabledFuture = (d) => { const t = new Date(); t.setHours(0, 0, 0, 0); return d.getTime() > t.getTime(); };

// 任务类型字典（默认兜底一项，字典拉到后覆盖）
const taskTypes = ref([{ value: 'METRIC_RECALC', label: '指标重算' }]);
// 指标类型 → 后端 task_type 的映射（目前仅指标重算 → METRIC_RUN 日志类型）
const TASK_TYPE_TO_BACKEND = { METRIC_RECALC: 'METRIC_RUN' };

// 过滤 + 列表
const query = reactive({ taskType: 'METRIC_RECALC', keyword: '' });
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const pageNo = ref(1);
const pageSize = ref(20);
const selected = ref([]);
const tableRef = ref(null);

function onSelectionChange(sel) { selected.value = sel; }

async function reload() {
  loading.value = true;
  try {
    const r = await listMetricSummary({
      taskType: TASK_TYPE_TO_BACKEND[query.taskType] || 'METRIC_RUN',
      metricKeyword: query.keyword?.trim() || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value
    });
    rows.value = r?.records || [];
    total.value = r?.total ?? rows.value.length;
  } catch { /* http 拦截器已提示 */ } finally { loading.value = false; }
}
function onSearch() { pageNo.value = 1; reload(); }
function onReset() { query.taskType = 'METRIC_RECALC'; query.keyword = ''; pageNo.value = 1; reload(); }

// 指标下拉（新增用）
const metricOptions = ref([]);
async function loadMetricOptions() {
  try {
    const list = await listMetrics();
    metricOptions.value = (Array.isArray(list) ? list : []).map(m => ({
      metricCode: m.metricCode, metricName: m.metricName
    }));
  } catch { metricOptions.value = []; }
}

// 新增 / 执行 对话框
const execDlg = reactive({ show: false, lockMetric: false, taskType: 'METRIC_RECALC',
  metricCode: '', dataDate: '', reason: '', submitting: false });

function openAdd() {
  Object.assign(execDlg, { show: true, lockMetric: false, taskType: 'METRIC_RECALC',
    metricCode: '', dataDate: '', reason: '', submitting: false });
}
function openExecute(row) {
  Object.assign(execDlg, { show: true, lockMetric: true, taskType: 'METRIC_RECALC',
    metricCode: row.metricCode, dataDate: '', reason: '', submitting: false });
}
async function confirmExecute() {
  if (!execDlg.metricCode) return ElMessage.warning('请选择指标');
  if (!execDlg.dataDate) return ElMessage.warning('执行时间必填');
  if (execDlg.dataDate > today()) return ElMessage.warning(`执行时间不能大于今天（${today()}）`);
  if (!execDlg.reason || !execDlg.reason.trim()) return ElMessage.warning('原因必填');
  execDlg.submitting = true;
  try {
    await executeMetric(execDlg.metricCode, {
      dataDate: execDlg.dataDate, cascade: true, async: true, reason: execDlg.reason.trim()
    });
    ElMessage.success('已触发执行，已记入审计');
    execDlg.show = false;
    reload();
  } catch { /* executeMetric 内部已提示 */ } finally { execDlg.submitting = false; }
}

// 批量执行对话框
const batchDlg = reactive({ show: false, metricCodes: [], dataDate: '', reason: '', submitting: false });
function openBatch() {
  batchDlg.metricCodes = selected.value.map(r => r.metricCode);
  batchDlg.dataDate = ''; batchDlg.reason = ''; batchDlg.submitting = false; batchDlg.show = true;
}
async function confirmBatch() {
  if (!batchDlg.metricCodes.length) return ElMessage.warning('未选择指标');
  if (!batchDlg.dataDate) return ElMessage.warning('数据日期必填');
  if (batchDlg.dataDate > today()) return ElMessage.warning(`数据日期不能大于今天（${today()}）`);
  if (!batchDlg.reason || !batchDlg.reason.trim()) return ElMessage.warning('原因必填');
  batchDlg.submitting = true;
  try {
    const r = await batchExecuteMetrics({
      metricCodes: batchDlg.metricCodes, dataDate: batchDlg.dataDate, reason: batchDlg.reason.trim()
    });
    ElMessage.success(`批量执行完成：成功 ${r?.success ?? 0} / 失败 ${r?.failed ?? 0}`);
    if (r?.failed) {
      const bad = (r.results || []).filter(x => x.status === 'FAILED')
        .map(x => `${x.metricCode}: ${x.errorMsg || ''}`).join('；');
      if (bad) ElMessage.warning(`失败明细：${bad}`);
    }
    batchDlg.show = false;
    // 成功后清空选中态：避免 reserve-selection 残留同名指标勾选，误用同一批 metricCodes 重复提交高危批量执行
    selected.value = [];
    tableRef.value?.clearSelection();
    reload();
  } catch { /* 拦截器已提示 */ } finally { batchDlg.submitting = false; }
}

// 历史抽屉
const hist = reactive({ show: false, metricCode: '', rows: [], total: 0, loading: false, pageNo: 1, pageSize: 20 });
function openHistory(row) {
  hist.metricCode = row.metricCode; hist.pageNo = 1; hist.show = true;
  // 打开抽屉先清空上一个指标的历史数据，避免慢网络下短暂残留
  hist.rows = []; hist.total = 0;
  loadHistory();
}
async function loadHistory() {
  hist.loading = true;
  try {
    // 复用既有 GET /perf/run-tasks?taskKey=
    const r = await listRunTasks({
      taskType: 'METRIC_RUN', taskKey: hist.metricCode,
      pageNo: hist.pageNo, pageSize: hist.pageSize
    });
    hist.rows = r?.records || [];
    hist.total = r?.total ?? hist.rows.length;
  } catch { hist.rows = []; hist.total = 0; } finally { hist.loading = false; }
}

onMounted(async () => {
  // 加载任务类型字典（失败保留兜底项）
  try {
    const items = await listDictItems('PERF_TASK_TYPE');
    if (Array.isArray(items) && items.length) {
      taskTypes.value = items.map(it => ({ value: it.dictValue || it.dictCode, label: it.dictLabel }));
      query.taskType = taskTypes.value[0].value;
    }
  } catch { /* 保留兜底 */ }
  loadMetricOptions();
  reload();
});
</script>

<style lang="scss" scoped>
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
.table { padding: 0; padding-bottom: 12px; }
.pager { display: flex; justify-content: flex-end; padding: 12px 14px; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; background: $bg-soft; padding: 2px 6px; border-radius: 3px; }
.sub-name { color: $text-2; font-size: 13px; }
.audit-hint { font-size: 12px; color: #999; margin-left: 100px; }
.err-inline { color: #991b1b; font-size: 12px; margin-top: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
</style>
