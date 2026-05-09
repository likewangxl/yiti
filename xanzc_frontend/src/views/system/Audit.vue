<template>
  <div>
    <div class="page-h">
      <h1>审计日志</h1>
      <span class="desc">高危动作 · 操作流水 · 完整可追溯</span>
      <div class="actions">
        <el-button @click="onlyHighRisk">仅看高危</el-button>
        <el-button @click="onlyPerf">绩效模块</el-button>
        <el-button type="primary" @click="reload">刷新</el-button>
      </div>
    </div>

    <div class="card-section">
      <el-form :inline="true" size="default">
        <el-form-item label="关键字">
          <el-input v-model="f.keyword" clearable placeholder="资源 / TraceId / 原因" style="width:220px" @keyup.enter="reload" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="f.empId" clearable placeholder="工号" style="width:120px" @keyup.enter="reload" />
        </el-form-item>
        <el-form-item label="动作">
          <el-select v-model="f.bizAction" clearable placeholder="全部" style="width:170px" @change="reload">
            <el-option v-for="o in BIZ_ACTIONS" :key="o.v" :value="o.v" :label="`${o.v} · ${o.l}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="BizType">
          <el-select v-model="f.bizType" clearable placeholder="全部" style="width:170px" @change="reload">
            <el-option v-for="o in BIZ_TYPES" :key="o.v" :value="o.v" :label="`${o.v} · ${o.l}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker v-model="f.dateRange" type="daterange" value-format="YYYY-MM-DD"
            range-separator="~" start-placeholder="开始" end-placeholder="结束" style="width:280px" @change="reload" />
        </el-form-item>
        <el-form-item><el-button type="primary" @click="reload">查询</el-button></el-form-item>
        <el-form-item><el-button @click="resetFilter">重置</el-button></el-form-item>
      </el-form>
    </div>

    <div class="card-section table">
      <el-table :data="rows" size="default" empty-text="暂无审计日志" v-loading="loading">
        <el-table-column label="时间" width="170">
          <template #default="{row}">{{ row.createdTime || row.time || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="120">
          <template #default="{row}">{{ row.empName || row.empId || row.who || '—' }}</template>
        </el-table-column>
        <el-table-column label="动作" width="140">
          <template #default="{row}">
            <el-tag :class="actCls(row.bizAction || row.action)" effect="plain">
              {{ actLabel(row.bizAction || row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="BizType" width="130">
          <template #default="{row}">{{ row.bizType || '—' }}</template>
        </el-table-column>
        <el-table-column label="资源" min-width="260" show-overflow-tooltip>
          <template #default="{row}">
            <code v-if="row.resourceUrl" class="mono">{{ row.requestMethod || '' }} {{ row.resourceUrl }}</code>
            <span v-else class="dim">{{ row.resource || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="TraceId" width="140">
          <template #default="{row}">
            <code class="mono trace">{{ (row.traceId || '—').slice(0, 16) }}</code>
          </template>
        </el-table-column>
        <el-table-column label="原因" min-width="160" show-overflow-tooltip>
          <template #default="{row}">{{ row.reason || '—' }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="80" align="right">
          <template #default="{row}">
            <span v-if="row.executionTime != null">{{ row.executionTime }} ms</span>
            <span v-else class="dim">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="70" align="center">
          <template #default="{row}">
            <el-tag v-if="row.responseStatus == null" class="tag-info" effect="plain" size="small">—</el-tag>
            <el-tag v-else-if="row.responseStatus < 400" class="tag-success" effect="plain" size="small">{{ row.responseStatus }}</el-tag>
            <el-tag v-else class="tag-warning" effect="plain" size="small">{{ row.responseStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="reload"
          @size-change="reload"
        />
      </div>
    </div>

    <!-- 详情弹框 -->
    <el-dialog v-model="dt.show" title="审计日志详情" width="780px" top="6vh">
      <el-descriptions :column="2" border size="default" v-if="dt.row.id">
        <el-descriptions-item label="时间">{{ dt.row.createdTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ dt.row.executionTime != null ? dt.row.executionTime + ' ms' : '—' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">
          {{ dt.row.empName || '—' }} <code class="mono">{{ dt.row.empId }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="HTTP 状态">
          <el-tag :class="(dt.row.responseStatus ?? 200) < 400 ? 'tag-success' : 'tag-warning'" effect="plain">
            {{ dt.row.responseStatus ?? '—' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="动作">
          <el-tag :class="actCls(dt.row.bizAction)" effect="plain">{{ actLabel(dt.row.bizAction) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="BizType">{{ dt.row.bizType || '—' }}</el-descriptions-item>
        <el-descriptions-item label="资源 URL" :span="2">
          <code class="mono">{{ dt.row.requestMethod }} {{ dt.row.resourceUrl }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="TraceId" :span="2">
          <code class="mono">{{ dt.row.traceId }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="IP 地址">{{ dt.row.ipAddress || '—' }}</el-descriptions-item>
        <el-descriptions-item label="User-Agent">{{ (dt.row.userAgent || '—').slice(0, 50) }}</el-descriptions-item>
        <el-descriptions-item label="原因" :span="2">{{ dt.row.reason || '—' }}</el-descriptions-item>
      </el-descriptions>

      <template v-if="dt.row.requestParams">
        <div class="block-h">请求参数</div>
        <pre class="code">{{ formatJson(dt.row.requestParams) }}</pre>
      </template>
      <template v-if="dt.row.errorMsg">
        <div class="block-h err">错误信息</div>
        <pre class="code err">{{ dt.row.errorMsg }}</pre>
      </template>

      <template #footer>
        <el-button @click="dt.show = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { listAuditLogs, getAuditLog } from '@/api/system';

// === 字典（与后端 BizAction / BizType 枚举对齐） ===
const BIZ_ACTIONS = [
  { v: 'READ',              l: '查看详情' },
  { v: 'LIST',              l: '查询列表' },
  { v: 'WRITE',             l: '新增/编辑' },
  { v: 'DELETE',            l: '删除' },
  { v: 'TRANSFER',          l: '转移' },
  { v: 'APPROVE',           l: '审批通过' },
  { v: 'REJECT',            l: '审批驳回' },
  { v: 'IMPORT',            l: '数据导入' },
  { v: 'EXPORT',            l: '数据导出' },
  { v: 'EXECUTE',           l: '执行操作' },
  { v: 'CONFIG',            l: '配置管理' },
  { v: 'RECALC',            l: '重新计算' },
  { v: 'JOB_TRIGGER',       l: '触发定时任务' },
  { v: 'PERMISSION_CHANGE', l: '权限变更' },
  { v: 'EXECUTE_SQL',       l: '执行 SQL' }
];
const BIZ_TYPES = [
  { v: 'PERF_CONFIG', l: '绩效配置' },
  { v: 'SYS_CONFIG',  l: '系统配置' },
  { v: 'CUSTOMER',    l: '客户管理' },
  { v: 'LEAD',        l: '线索管理' },
  { v: 'CLAIM',       l: '认领管理' },
  { v: 'TOUCH_TASK',  l: '触达任务' },
  { v: 'LOAN',        l: '贷款业务' },
  { v: 'SUPPORT',     l: '支撑业务' },
  { v: 'REPORT',      l: '报表分析' },
  { v: 'PRODUCT',     l: '产品管理' },
  { v: 'DOC',         l: '文档管理' },
  { v: 'ORG',         l: '组织机构' }
];
// 高危动作集合（按 4.6.1.5：必审计）
const HIGH_RISK = new Set([
  'DELETE', 'EXECUTE', 'IMPORT', 'EXPORT', 'CONFIG', 'RECALC',
  'JOB_TRIGGER', 'PERMISSION_CHANGE', 'EXECUTE_SQL', 'TRANSFER',
  'METRIC_EXECUTE', 'METRIC_TRIAL_RUN', 'STATUS_CHANGE', 'PUBLISH', 'SLOT_RELEASE'
]);
const actCls = (a) => {
  if (a === 'DELETE' || a === 'PERMISSION_CHANGE' || a === 'EXECUTE_SQL' || a === 'METRIC_EXECUTE') return 'tag-danger';
  if (HIGH_RISK.has(a)) return 'tag-warning';
  return 'tag-info';
};
// 动作中文化（与 Metrics.vue 版本历史保持一致）
const ACTION_LABEL = {
  READ: '查看', LIST: '查询', WRITE: '新增/编辑', CREATE: '新增', UPDATE: '编辑',
  DELETE: '删除', TRANSFER: '转移', APPROVE: '审批通过', REJECT: '审批驳回',
  IMPORT: '数据导入', EXPORT: '数据导出', EXECUTE: '执行操作', CONFIG: '配置管理',
  RECALC: '重新计算', JOB_TRIGGER: '触发定时任务', PERMISSION_CHANGE: '权限变更',
  EXECUTE_SQL: '执行 SQL', STATUS_CHANGE: '状态变更',
  METRIC_TRIAL_RUN: '试运行', METRIC_EXECUTE: '立即执行', SLOT_RELEASE: '释放槽位', PUBLISH: '发布'
};
const actLabel = (a) => ACTION_LABEL[a] || a || '—';

// === 列表 ===
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const pageNo = ref(1);
const pageSize = ref(20);
const f = reactive({ keyword: '', empId: '', bizAction: '', bizType: '', dateRange: null });

async function reload() {
  loading.value = true;
  try {
    const params = {
      pageNo: pageNo.value, pageSize: pageSize.value,
      keyword:   f.keyword   || undefined,
      empId:     f.empId     || undefined,
      bizAction: f.bizAction || undefined,
      bizType:   f.bizType   || undefined,
      startTime: f.dateRange?.[0],
      endTime:   f.dateRange?.[1]
    };
    const r = await listAuditLogs(params);
    if (Array.isArray(r)) {
      rows.value = r;
      total.value = Math.max(total.value, (pageNo.value - 1) * pageSize.value + r.length);
    }
  } catch {} finally { loading.value = false; }
}

function resetFilter() {
  f.keyword = ''; f.empId = ''; f.bizAction = ''; f.bizType = ''; f.dateRange = null;
  pageNo.value = 1;
  reload();
}
function onlyHighRisk() {
  // 后端无"高危合集"参数；DELETE 是最常见高危。其它高危靠下拉手动叠加。
  f.bizAction = 'DELETE'; reload();
}
function onlyPerf() {
  f.bizType = 'PERF_CONFIG'; reload();
}

// === 详情 ===
const dt = reactive({ show: false, row: {} });
async function openDetail(row) {
  dt.show = true;
  dt.row = { ...row };
  if (!row.id) return;
  try {
    const r = await getAuditLog(row.id);
    if (r && r.id) dt.row = r;
  } catch {}
}
function formatJson(s) {
  if (!s) return '';
  try { return JSON.stringify(JSON.parse(s), null, 2); }
  catch { return String(s); }
}

// === 路由 query 联动：从指标库点"查看审计"过来时预填筛选 ===
const route = useRoute();
onMounted(() => {
  if (route.query.bizType)   f.bizType   = String(route.query.bizType);
  if (route.query.bizAction) f.bizAction = String(route.query.bizAction);
  if (route.query.keyword)   f.keyword   = String(route.query.keyword);
  if (route.query.empId)     f.empId     = String(route.query.empId);
  reload();
});
</script>

<style lang="scss" scoped>
.table { padding: 0; padding-bottom: 12px; }
.pager { display: flex; justify-content: flex-end; padding: 12px 14px; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
.trace { color: $text-3; }
.dim { color: $text-3; }
.block-h {
  font-size: 13px; font-weight: 600; margin: 18px 0 10px; color: $text-1;
  &.err { color: $danger; }
}
.code {
  background: #1e293b; color: #f1f5f9; border-radius: 4px; padding: 12px 14px;
  font-family: ui-monospace, monospace; font-size: 12.5px; line-height: 1.6;
  white-space: pre-wrap; overflow: auto; margin: 0;
  &.err { background: #4c0519; color: #fecaca; }
}
</style>
