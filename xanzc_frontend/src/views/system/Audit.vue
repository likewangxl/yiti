<template>
<main v-bp-overflow-tooltip class="bp-crud audit-page" aria-labelledby="audit-page-title" :aria-busy="loading ? 'true' : 'false'">
    <header class="page-h">
      <PageTitle id="audit-page-title"><span class="sub">只读审计流水，保留 TraceId、请求资源、原因和原始错误信息的追溯路径。</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="审计日志快捷筛选">
        <el-button title="沿用 DELETE 动作筛选契约，其他高危动作可从筛选器选择" @click="onlyHighRisk">仅看高危</el-button>
        <el-button @click="onlyPerf">绩效模块</el-button>
        <el-button type="primary" @click="reload">刷新</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="审计日志筛选">
      <el-form class="filter-form" inline size="default" aria-label="审计日志筛选条件" @submit.prevent="onSearch">
        <el-form-item label="关键字"><el-input v-model="f.keyword" clearable placeholder="资源 / TraceId / 原因" aria-label="按关键字筛选审计日志" style="width:230px" @keyup.enter="onSearch" /></el-form-item>
        <el-form-item label="操作人"><el-input v-model="f.empId" clearable placeholder="工号" aria-label="按操作人工号筛选" style="width:140px" @keyup.enter="onSearch" /></el-form-item>
        <el-form-item label="动作"><el-select v-model="f.bizAction" clearable placeholder="全部" aria-label="按审计动作筛选" style="width:190px"><el-option v-for="option in BIZ_ACTIONS" :key="option.value" :value="option.value" :label="`${option.value} · ${option.label}`" /></el-select></el-form-item>
        <el-form-item label="业务域"><el-select v-model="f.bizType" clearable placeholder="全部" aria-label="按业务域筛选" style="width:190px"><el-option v-for="option in BIZ_TYPES" :key="option.value" :value="option.value" :label="`${option.value} · ${option.label}`" /></el-select></el-form-item>
        <el-form-item label="时间范围"><el-date-picker v-model="f.dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始" end-placeholder="结束" style="width:280px" /></el-form-item>
        <el-form-item><el-button type="primary" @click="onSearch">查询</el-button><el-button @click="resetFilter">重置</el-button></el-form-item>
      </el-form>
    </section>

    <section
      class="card-section data-panel audit-table-panel"
      aria-label="审计日志列表"
      aria-labelledby="audit-table-heading"
      aria-describedby="audit-table-state"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <div class="toolbar">
        <div>
          <h2 id="audit-table-heading" class="section-title">操作流水</h2>
          <p class="hint">结果只读；打开详情可查看原始请求参数和后端错误摘要。</p>
        </div>
        <p id="audit-table-state" class="table-state" role="status" aria-live="polite">{{ loading ? '审计日志加载中' : rows.length ? `共 ${total} 条审计记录` : '暂无审计日志' }}</p>
      </div>
      <p v-if="loadError" class="error-state" role="alert">{{ loadError }} <el-button link type="primary" @click="reload">重试</el-button></p>
      <el-table :data="rows" size="default" empty-text="暂无审计日志" v-loading="loading" aria-labelledby="audit-table-heading" aria-describedby="audit-table-state">
        <el-table-column label="时间" width="170"><template #default="{ row }">{{ fmtDateTime(row.createdTime || row.time) }}</template></el-table-column>
        <el-table-column label="操作人" width="130"><template #default="{ row }">{{ row.empName || row.empId || row.who || '-' }}</template></el-table-column>
        <el-table-column label="动作" width="150"><template #default="{ row }"><el-tag :class="actCls(row.bizAction || row.action)" effect="plain">{{ actLabel(row.bizAction || row.action) }}</el-tag></template></el-table-column>
        <el-table-column label="业务域" width="140"><template #default="{ row }">{{ row.bizType || '-' }}</template></el-table-column>
        <el-table-column label="资源" min-width="270" show-overflow-tooltip><template #default="{ row }"><code v-if="row.resourceUrl" class="mono">{{ row.requestMethod || '' }} {{ row.resourceUrl }}</code><span v-else class="dim">{{ row.resource || '-' }}</span></template></el-table-column>
        <el-table-column label="TraceId" width="156"><template #default="{ row }"><code class="mono trace">{{ (row.traceId || '-').slice(0, 16) }}</code></template></el-table-column>
        <el-table-column label="原因" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ row.reason || '-' }}</template></el-table-column>
        <el-table-column label="耗时" width="86" align="right"><template #default="{ row }">{{ row.executionTime != null ? `${row.executionTime} ms` : '-' }}</template></el-table-column>
        <el-table-column label="状态" width="78" align="center"><template #default="{ row }"><el-tag v-if="row.responseStatus == null" class="tag-info" effect="plain" size="small">-</el-tag><el-tag v-else-if="row.responseStatus < 400" class="tag-success" effect="plain" size="small">{{ row.responseStatus }}</el-tag><el-tag v-else class="tag-warning" effect="plain" size="small">{{ row.responseStatus }}</el-tag></template></el-table-column>
        <el-table-column label="操作" class-name="operation-cell" width="80" fixed="right"><template #default="{ row }"><el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button></template></el-table-column>
      </el-table>
      <nav class="pager" aria-label="审计日志分页">
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

    <el-dialog v-model="dt.show" class="bp-crud-dialog" title="审计日志详情" width="780px" top="6vh">
      <p v-if="dt.error" class="error-state" role="alert">{{ dt.error }} <el-button link type="primary" @click="openDetail(dt.row)">重试</el-button></p>
      <el-descriptions v-if="dt.row.id" :column="2" border size="default" v-loading="dt.loading" :aria-busy="dt.loading ? 'true' : 'false'">
        <el-descriptions-item label="时间">{{ fmtDateTime(dt.row.createdTime) }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ dt.row.executionTime != null ? `${dt.row.executionTime} ms` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ dt.row.empName || '-' }} <code class="mono">{{ dt.row.empId || '-' }}</code></el-descriptions-item>
        <el-descriptions-item label="HTTP 状态"><el-tag :class="(dt.row.responseStatus ?? 200) < 400 ? 'tag-success' : 'tag-warning'" effect="plain">{{ dt.row.responseStatus ?? '-' }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="动作"><el-tag :class="actCls(dt.row.bizAction)" effect="plain">{{ actLabel(dt.row.bizAction) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="业务域">{{ dt.row.bizType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="资源 URL" :span="2"><code class="mono">{{ dt.row.requestMethod }} {{ dt.row.resourceUrl }}</code></el-descriptions-item>
        <el-descriptions-item label="TraceId" :span="2"><code class="mono">{{ dt.row.traceId }}</code></el-descriptions-item>
        <el-descriptions-item label="IP 地址">{{ dt.row.ipAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="User-Agent">{{ (dt.row.userAgent || '-').slice(0, 50) }}</el-descriptions-item>
        <el-descriptions-item label="原因" :span="2">{{ dt.row.reason || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template v-if="dt.row.requestParams"><h3 class="block-h">请求参数</h3><pre class="code">{{ formatJson(dt.row.requestParams) }}</pre></template>
      <template v-if="dt.row.errorMsg"><h3 class="block-h err">错误信息</h3><pre class="code err">{{ dt.row.errorMsg }}</pre></template>
      <template #footer><el-button @click="dt.show = false">关闭</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { fmtDateTime } from '@/utils/datetime';
import { listAuditLogs, getAuditLog } from '@/api/system';
import { useDict } from '@/composables/useDict';

const { options: BIZ_ACTIONS, labelOf: dictActLabel } = useDict('AUDIT_BIZ_ACTION');
const { options: BIZ_TYPES } = useDict('AUDIT_BIZ_TYPE');
const HIGH_RISK = new Set(['DELETE', 'EXECUTE', 'IMPORT', 'EXPORT', 'CONFIG', 'RECALC', 'JOB_TRIGGER', 'PERMISSION_CHANGE', 'EXECUTE_SQL', 'TRANSFER', 'METRIC_EXECUTE', 'METRIC_TRIAL_RUN', 'STATUS_CHANGE', 'PUBLISH', 'SLOT_RELEASE']);
const ACTION_LABEL_FALLBACK = {
  READ: '查看', LIST: '查询', WRITE: '新增或修改', CREATE: '新增', UPDATE: '编辑', DELETE: '删除', TRANSFER: '转交他人', APPROVE: '审批通过', REJECT: '审批驳回', IMPORT: '数据导入', EXPORT: '数据导出', EXECUTE: '执行', CONFIG: '修改配置', RECALC: '重新计算', JOB_TRIGGER: '手动触发任务', PERMISSION_CHANGE: '权限变更', EXECUTE_SQL: '执行 SQL 查询', STATUS_CHANGE: '变更状态', METRIC_TRIAL_RUN: '指标试运行', METRIC_EXECUTE: '指标立即计算', SLOT_RELEASE: '释放指标槽位', PUBLISH: '发布上线', ALLOC_ADJUST_CREATE: '发起业绩调整', ALLOC_ADJUST_WITHDRAW: '撤回业绩调整', TARGET_ADJUST_CREATE: '发起目标调整', DASHBOARD_PRESIDENT_VIEW: '查看行长仪表盘', DASHBOARD_ORG_VIEW: '查看机构仪表盘', DASHBOARD_EMP_VIEW: '查看员工仪表盘', PERF_IMPORT_UPLOAD: '上传绩效数据', PERF_RECALC: '绩效重新计算'
};
const actCls = (action) => (['DELETE', 'PERMISSION_CHANGE', 'EXECUTE_SQL', 'METRIC_EXECUTE'].includes(action) ? 'tag-danger' : HIGH_RISK.has(action) ? 'tag-warning' : 'tag-info');
const actLabel = (action) => {
  if (!action) return '-';
  const label = dictActLabel(action);
  return label && label !== action ? label : (ACTION_LABEL_FALLBACK[action] || action);
};

const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const loadError = ref('');
const pageNo = ref(1);
const pageSize = ref(20);
const f = reactive({ keyword: '', empId: '', bizAction: '', bizType: '', dateRange: null });
async function reload() {
  loading.value = true;
  loadError.value = '';
  try {
    const result = await listAuditLogs({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      keyword: f.keyword || undefined,
      empId: f.empId || undefined,
      bizAction: f.bizAction || undefined,
      bizType: f.bizType || undefined,
      startTime: f.dateRange?.[0],
      endTime: f.dateRange?.[1]
    });
    const items = result?.records || (Array.isArray(result) ? result : []);
    rows.value = items;
    total.value = result?.total ?? items.length;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    loadError.value = `审计日志加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    loading.value = false;
  }
}
function onSearch() { pageNo.value = 1; reload(); }
function onSizeChange() { pageNo.value = 1; reload(); }
function resetFilter() {
  Object.assign(f, { keyword: '', empId: '', bizAction: '', bizType: '', dateRange: null });
  pageNo.value = 1;
  reload();
}
function onlyHighRisk() { f.bizAction = 'DELETE'; onSearch(); }
function onlyPerf() { f.bizType = 'PERF_CONFIG'; onSearch(); }

const dt = reactive({ show: false, row: {}, loading: false, error: '' });
async function openDetail(row) {
  dt.show = true;
  dt.row = { ...row };
  dt.error = '';
  if (!row?.id) return;
  dt.loading = true;
  try {
    const result = await getAuditLog(row.id);
    if (result?.id) dt.row = result;
  } catch (error) {
    dt.error = `审计详情加载失败：${error?.message || '请稍后重试'}`;
  } finally {
    dt.loading = false;
  }
}
function formatJson(value) {
  if (!value) return '';
  try { return JSON.stringify(JSON.parse(value), null, 2); }
  catch { return String(value); }
}

const route = useRoute();
onMounted(() => {
  if (route.query.bizType) f.bizType = String(route.query.bizType);
  if (route.query.bizAction) f.bizAction = String(route.query.bizAction);
  if (route.query.keyword) f.keyword = String(route.query.keyword);
  if (route.query.empId) f.empId = String(route.query.empId);
  reload();
});
</script>

<style lang="scss" scoped>
.audit-table-panel { min-width: 0; }
.mono { color: var(--color-text); font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace; font-size: 12px; }
.trace,
.dim { color: var(--color-text-muted); }
.block-h { color: var(--color-text-strong); font-size: 14px; font-weight: 600; margin: var(--space-4) 0 var(--space-2); }
.block-h.err { color: var(--color-danger-fg); }
.code { background: var(--color-surface-soft); border: 1px solid var(--color-border); border-radius: var(--radius-control); color: var(--color-text-strong); font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace; font-size: 12px; line-height: 18px; margin: 0; max-height: 280px; overflow: auto; padding: var(--space-3); white-space: pre-wrap; }
.code.err { background: var(--color-danger-bg); color: var(--color-danger-fg); }
.error-state { background: var(--color-danger-bg); border-left: 3px solid var(--color-danger-fg); color: var(--color-danger-fg); font-size: 12px; line-height: 18px; margin-bottom: var(--space-3); padding: var(--space-2) var(--space-3); }
</style>
