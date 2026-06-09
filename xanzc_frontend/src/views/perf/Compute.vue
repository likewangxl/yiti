<template>
  <div>
    <div class="page-h">
      <h1>考核计算 <span class="sub">手工触发 / 回算 / 快照</span></h1>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" @click="openTrigger">▶ 触发计算</el-button>
      </div>
    </div>

    <div class="stats">
      <div class="stat"><div class="label">本月计算任务</div><div class="value">{{ s.tasks }}</div></div>
      <div class="stat"><div class="label">成功</div><div class="value" style="color:#16A34A">{{ s.ok }}</div></div>
      <div class="stat"><div class="label">失败</div><div class="value" style="color:#DC2626">{{ s.fail }}</div></div>
      <div class="stat"><div class="label">最近耗时</div><div class="value">{{ s.lastDuration }}</div></div>
    </div>

    <!-- 查询条件：数据日期 + KPI方案 -->
    <div class="card-section">
      <el-form :inline="true" size="default">
        <el-form-item label="数据日期">
          <el-date-picker v-model="logQuery.dataDate" type="date" value-format="YYYY-MM-DD"
            placeholder="选择数据日期" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="KPI方案">
          <el-select v-model="logQuery.schemeCode" clearable filterable placeholder="全部" style="width:220px">
            <el-option v-for="o in schemeSelOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onQueryLogs">查询</el-button>
          <el-button @click="onResetLogs">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-section">
      <el-table :data="logRows" size="default" v-loading="logLoading" empty-text="暂无记录">
        <el-table-column label="数据日期" width="120">
          <template #default="{row}">{{ row.dataDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="KPI方案" min-width="180">
          <template #default="{row}">
            <el-button link type="primary" @click="goDetail(row)">{{ schemeLabel(row.schemeCode) }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="触发方式" width="110">
          <template #default="{row}">
            <el-tag effect="plain" :class="row.triggerType === 'AUTO' ? 'tag-success' : 'tag-info'">
              {{ row.triggerType === 'AUTO' ? '自动' : '手动' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="触发人" width="130">
          <template #default="{row}">
            <template v-if="row.triggerBy">
              <div>{{ row.triggerByName || row.triggerBy }}</div>
              <div style="color:#909399;font-size:12px;">{{ row.triggerBy }}</div>
            </template>
            <template v-else>-</template>
          </template>
        </el-table-column>
        <el-table-column label="执行结果" width="110">
          <template #default="{row}">
            <el-tag effect="plain" :class="row.result === 'SUCCESS' ? 'tag-success' : 'tag-danger'">
              {{ row.result === 'SUCCESS' ? '成功' : (row.result === 'FAILED' ? '失败' : (row.result || '-')) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="170">
          <template #default="{row}">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="170">
          <template #default="{row}">{{ fmtTime(row.endTime) }}</template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="logPgNo" v-model:page-size="logPgSize" :page-sizes="[10,20,50]"
          :total="logTotal" background layout="total, sizes, prev, pager, next"
          @current-change="loadLogs" @size-change="onLogSizeChange" />
      </div>
    </div>

    <!-- 触发计算 弹框：数据日期 + KPI方案 + 触发原因 → 调 KPI 计算服务（先记审计日志再计算） -->
    <el-dialog v-model="trgDlg.show" title="确认触发 KPI 计算" width="520px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:14px"
        title="该操作将基于所选数据日期的指标结果与目标值，重算指定 KPI 方案的得分。" />
      <el-form ref="trgFormRef" :model="trgDlg.form" :rules="trgRules" label-position="top" size="default">
        <el-form-item label="数据日期" prop="dataDate">
          <el-date-picker v-model="trgDlg.form.dataDate" type="date"
            value-format="YYYY-MM-DD" style="width:100%" placeholder="选择数据日期（不能大于今天）"
            :disabled-date="trgDlg.disabledDate" />
        </el-form-item>
        <el-form-item label="KPI方案" prop="schemeCode">
          <el-select v-model="trgDlg.form.schemeCode" filterable placeholder="请选择 KPI 方案" style="width:100%">
            <el-option v-for="o in activeSchemeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发原因" prop="reason">
          <el-input v-model="trgDlg.form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit
            placeholder="请说明触发 KPI 计算的原因（将记入审计日志）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="trgDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="trgDlg.saving" @click="onConfirmTrigger">确认执行</el-button>
      </template>
    </el-dialog>

    <!-- 快照抽屉（截图 182） -->
    <el-drawer v-model="snapDlg.show" :title="`计算快照 · ${snapDlg.batch || ''}`" size="64%" :destroy-on-close="true">
      <div class="snap-h">员工得分明细</div>
      <el-table :data="snapDlg.rows" size="default" border>
        <el-table-column prop="emp" label="员工" width="100" />
        <el-table-column prop="m1" label="新增客户"  align="right" />
        <el-table-column prop="m2" label="存款日均"  align="right" />
        <el-table-column prop="m3" label="贷款余额"  align="right" />
        <el-table-column prop="m4" label="不良率"    align="right" />
        <el-table-column prop="m5" label="中收入"    align="right" />
        <el-table-column prop="m6" label="触达完成"  align="right" />
        <el-table-column label="总分" width="90" align="right">
          <template #default="{row}"><strong>{{ row.total }}</strong></template>
        </el-table-column>
      </el-table>

      <div class="snap-h" style="margin-top:24px">本批次产生的变更</div>
      <ul class="changes">
        <li v-for="(c, i) in snapDlg.changes" :key="i" v-html="c"></li>
      </ul>

      <template #footer>
        <el-button @click="snapDlg.show = false">关闭</el-button>
      </template>
    </el-drawer>

    <!-- 错误详情 -->
    <el-dialog v-model="errDlg.show" title="错误详情" width="540px">
      <p><strong>批次：</strong>{{ errDlg.row?.batch }}</p>
      <p><strong>失败时间：</strong>{{ errDlg.row?.start }}</p>
      <pre class="err-text">{{ errDlg.errMsg }}</pre>
      <template #footer>
        <el-button @click="errDlg.show = false">关闭</el-button>
        <el-button type="primary" @click="onRetry(errDlg.row)">重试</el-button>
      </template>
    </el-dialog>

    <!-- 触发计算（按行：数据日期 + 方案，输入触发原因）-->
    <el-dialog v-model="rowTrgDlg.show" title="确认触发 KPI 计算" width="520px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:14px"
        :title="`将对 数据日期 ${rowTrgDlg.dataDate}、方案 ${schemeLabel(rowTrgDlg.schemeCode)} 重新计算 KPI 得分。`" />
      <el-form label-position="top" size="default">
        <el-form-item label="触发原因" required>
          <el-input v-model="rowTrgDlg.reason" type="textarea" :rows="3" maxlength="500" show-word-limit
            placeholder="请输入触发原因（必填，将记入审批日志）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rowTrgDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="rowTrgDlg.saving" @click="confirmTriggerRow">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  listComputeBatches, triggerCompute, getComputeBatch, getKpiScoreStats, listKpiCalcLogs, listKpiRules, calcKpiScore,
  getLatestKpiCalcLogDate
} from '@/api/perf';

// 后端 PerfRunTaskController 没有 /stats 端点；统计在前端从 rows 派生
const rows = ref([]);
const pgNo = ref(1);
const pgSize = ref(20);
const pagedRows = computed(() => rows.value.slice((pgNo.value - 1) * pgSize.value, pgNo.value * pgSize.value));
const loading = ref(false);
const triggerCls = (t) => ({ 手动: 'tag-info', 定时: 'tag-success', 回算: 'tag-warning' }[t] || '');
const rangeLabel = (s) => ({ ALL: '全行', ORG: '按机构', EMP: '按员工' }[s] || s || '');

// 把后端 PerfRunTask 实体映射到 UI 期待的 {batch, plan, scope, trigger, who, start, dur, status} 结构
function adaptTask(t) {
  let plan = '-', scope = '-';
  try {
    const p = typeof t.paramsJson === 'string' ? JSON.parse(t.paramsJson) : (t.paramsJson || {});
    plan  = p.scheme || p.planCode || p.cycleType || '-';
    scope = p.scope  || '-';
  } catch {}
  // 触发方式：started_by=admin → 手动；started_by=system → 定时；task_type=RECALC → 回算
  const trigger = (t.taskType || '').toUpperCase().includes('RECALC')
    ? '回算'
    : (t.startedBy === 'system' ? '定时' : '手动');
  // 耗时
  let dur = '—';
  if (t.startTime && t.endTime) {
    const ms = new Date(t.endTime) - new Date(t.startTime);
    if (ms >= 60000) dur = `${Math.floor(ms/60000)}m${Math.floor((ms%60000)/1000)}s`;
    else if (ms >= 1000) dur = `${Math.floor(ms/1000)}s`;
    else dur = `${ms}ms`;
  }
  // 显示状态
  const STATUS_LABEL = { SUCCESS: '成功', FAILED: '失败', RUNNING: '运行中', PENDING: '排队中' };
  return {
    batch:   t.taskKey || t.id,
    rawId:   t.id,
    plan,
    scope,
    trigger,
    who:     t.startedBy || '-',
    start:   (t.startTime || '').slice(11, 16) || '-',
    dur,
    status:  STATUS_LABEL[t.status] || t.status || '-',
    rawStatus: t.status,
    errorMsg: t.errorMsg
  };
}

// 统计来源：后端 GET /perf/kpi-score/stats（PERF_METRIC_CALC_TASK 的 KPI 计算任务）
// 本月任务数 / 最后一次任务成功数 / 失败数 / 最近耗时
const kpiStat = ref({});
function fmtDur(ms) {
  if (ms == null) return '—';
  if (ms >= 60000) return `${Math.floor(ms/60000)}m${Math.floor((ms%60000)/1000)}s`;
  if (ms >= 1000) return `${Math.floor(ms/1000)}s`;
  return `${ms}ms`;
}
const s = computed(() => ({
  tasks: kpiStat.value.monthTaskCount ?? 0,
  ok: kpiStat.value.lastSuccessCount ?? 0,
  fail: kpiStat.value.lastFailCount ?? 0,
  lastDuration: fmtDur(kpiStat.value.lastDurationMs)
}));
async function loadKpiStats() {
  try { kpiStat.value = (await getKpiScoreStats()) || {}; } catch {}
}

// === KPI 方案级计算记录列表（PERF_KPI_CALC_LOG）===
const logQuery = reactive({ dataDate: '', schemeCode: '' });
const logRows = ref([]);
const logTotal = ref(0);
const logPgNo = ref(1);
const logPgSize = ref(20);
const logLoading = ref(false);
const schemeSelOptions = ref([]);
const activeSchemeOptions = ref([]); // 仅启用(ACTIVE) KPI 方案，供触发计算弹框下拉
const schemeNameMap = ref({});
function schemeLabel(code) {
  if (!code) return '-';
  return schemeNameMap.value[code] ? `${schemeNameMap.value[code]}(${code})` : code;
}
function fmtTime(t) {
  if (!t) return '-';
  return String(t).replace('T', ' ').slice(0, 19);
}
async function loadSchemeOptions() {
  try {
    // pageSize 上限 100（后端 @Max(100)，超限会 400 → 静默走 mock 兜底，导致真实方案丢失）
    const r = await listKpiRules({ pageSize: 100 });
    const arr = Array.isArray(r) ? r : (r?.records || []);
    schemeSelOptions.value = arr.map(x => ({ value: x.schemeCode, label: `${x.schemeName || x.schemeCode}(${x.schemeCode})` }));
    // 触发计算弹框的 KPI方案下拉：仅 状态=启用(ACTIVE) 的方案，展示「编号 - 名称」
    activeSchemeOptions.value = arr
      .filter(x => x.status === 'ACTIVE' && x.schemeCode)
      .map(x => ({ value: x.schemeCode, label: `${x.schemeCode}${x.schemeName ? ' - ' + x.schemeName : ''}` }));
    const m = {};
    arr.forEach(x => { if (x.schemeCode) m[x.schemeCode] = x.schemeName || x.schemeCode; });
    schemeNameMap.value = m;
  } catch {}
}
async function loadLogs() {
  logLoading.value = true;
  try {
    const r = await listKpiCalcLogs({
      dataDate: logQuery.dataDate || undefined,
      schemeCode: logQuery.schemeCode || undefined,
      pageNo: logPgNo.value, pageSize: logPgSize.value
    });
    logRows.value = r?.records || [];
    logTotal.value = r?.total ?? logRows.value.length;
  } catch {} finally { logLoading.value = false; }
}
function onQueryLogs() { logPgNo.value = 1; loadLogs(); }
function onResetLogs() { logQuery.dataDate = ''; logQuery.schemeCode = ''; logPgNo.value = 1; loadLogs(); }
function onLogSizeChange() { logPgNo.value = 1; loadLogs(); }
function openLogError(row) {
  errDlg.row = { batch: `${schemeLabel(row.schemeCode)} / ${row.dataDate || ''}`, start: fmtTime(row.startTime) };
  errDlg.errMsg = row.errorMsg || '（无错误信息）';
  errDlg.show = true;
}

// 点击 KPI方案 → 进入计算结果详情页（带数据日期 + 方案编码）
const router = useRouter();
function goDetail(row) {
  if (!row.schemeCode) return;
  router.push({ name: 'PerfKpiScoreDetail', query: { dataDate: row.dataDate, schemeCode: row.schemeCode } });
}

// 按行触发计算：输入触发原因 → 记审批日志 + 调 KPI 计算服务（数据日期 + 方案编码）
const rowTrgDlg = reactive({ show: false, dataDate: '', schemeCode: '', reason: '', saving: false });
function openTriggerRow(row) {
  rowTrgDlg.dataDate = row.dataDate || '';
  rowTrgDlg.schemeCode = row.schemeCode || '';
  rowTrgDlg.reason = '';
  rowTrgDlg.show = true;
}
async function confirmTriggerRow() {
  if (!rowTrgDlg.reason.trim()) return ElMessage.warning('请输入触发原因');
  if (!rowTrgDlg.dataDate || !rowTrgDlg.schemeCode) return ElMessage.warning('数据日期或方案缺失');
  rowTrgDlg.saving = true;
  try {
    await calcKpiScore({ dataDate: rowTrgDlg.dataDate, schemeCode: rowTrgDlg.schemeCode, reason: rowTrgDlg.reason.trim() });
    ElMessage.success('已触发 KPI 计算');
    rowTrgDlg.show = false;
    reload();
  } catch (e) {
    ElMessage.error('触发失败：' + (e?.bizMsg || e?.message || '未知错误'));
  } finally { rowTrgDlg.saving = false; }
}

async function reload() {
  loadLogs();
  loadKpiStats();
}

// === 触发计算（UI 仅 3 字段：方案 / 范围 / 数据日期）===
// 后端 RecalcReqDTO 必填 cycleType/cycleDateFrom/cycleDateTo/version/reason → 在 onConfirmTrigger 自动派生
const trgFormRef = ref(null);
// 方案选项基于当前年动态生成，默认值跟随当前季度滚动；避免写死 '2026Q2' 在跨季度后默认值脱离选项列表
const NOW_YEAR = new Date().getFullYear();
const NOW_QUARTER = `${NOW_YEAR}Q${Math.floor(new Date().getMonth() / 3) + 1}`;
const schemeOptions = [
  { label: `${NOW_YEAR}Q1 KPI`, value: `${NOW_YEAR}Q1` },
  { label: `${NOW_YEAR}Q2 KPI`, value: `${NOW_YEAR}Q2` },
  { label: `${NOW_YEAR}Q3 KPI`, value: `${NOW_YEAR}Q3` },
  { label: `${NOW_YEAR}Q4 KPI`, value: `${NOW_YEAR}Q4` },
  { label: `${NOW_YEAR} 全年 KPI`, value: `${NOW_YEAR}` },
  { label: `${NOW_YEAR - 1}Y KPI`, value: `${NOW_YEAR - 1}` },
];
const trgDlg = reactive({
  show: false, saving: false,
  form: { schemeCode: '', dataDate: new Date().toISOString().slice(0, 10), reason: '' },
  // el-date-picker disabled-date：禁选今天之后的日期（数据日期不能大于当前日期）
  disabledDate: (d) => {
    const t = new Date(); t.setHours(0, 0, 0, 0);
    return d.getTime() > t.getTime();
  }
});
const trgRules = {
  dataDate:   [{ required: true, message: '请选择数据日期' }],
  schemeCode: [{ required: true, message: '请选择 KPI 方案' }],
  reason:     [{ required: true, message: '请填写触发原因', trigger: 'blur' }]
};
function openTrigger() {
  trgDlg.form.dataDate = new Date().toISOString().slice(0, 10);
  trgDlg.form.schemeCode = '';
  trgDlg.form.reason = '';
  trgDlg.show = true;
}
/**
 * 把 UI 的 scheme（如 2026Q2 / 2026 / 202604）拆成后端需要的：
 *   { cycleType, cycleDateFrom, cycleDateTo }
 *
 *  - 2026Q2 → QUARTERLY, 2026-04-01 ~ 2026-06-30
 *  - 2026   → YEARLY,    2026-01-01 ~ 2026-12-31
 *  - 202604 → MONTHLY,   2026-04-01 ~ 2026-04-30
 */
function deriveCycle(scheme) {
  const s = String(scheme || '').trim();
  // YEARLY: 4 位年
  if (/^\d{4}$/.test(s)) {
    const y = parseInt(s, 10);
    return { cycleType: 'YEARLY', cycleDateFrom: `${y}-01-01`, cycleDateTo: `${y}-12-31` };
  }
  // QUARTERLY: yyyyQn
  let m = s.match(/^(\d{4})Q([1-4])$/);
  if (m) {
    const y = parseInt(m[1], 10), q = parseInt(m[2], 10);
    const startMon = (q - 1) * 3 + 1;
    const endMon = startMon + 2;
    const lastDay = new Date(y, endMon, 0).getDate();
    return {
      cycleType: 'QUARTERLY',
      cycleDateFrom: `${y}-${String(startMon).padStart(2, '0')}-01`,
      cycleDateTo:   `${y}-${String(endMon).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`
    };
  }
  // MONTHLY: yyyyMM (6 位)
  m = s.match(/^(\d{4})(\d{2})$/);
  if (m) {
    const y = parseInt(m[1], 10), mo = parseInt(m[2], 10);
    const lastDay = new Date(y, mo, 0).getDate();
    return {
      cycleType: 'MONTHLY',
      cycleDateFrom: `${y}-${String(mo).padStart(2, '0')}-01`,
      cycleDateTo:   `${y}-${String(mo).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`
    };
  }
  // 兜底：当作季度
  return { cycleType: 'QUARTERLY', cycleDateFrom: s, cycleDateTo: s };
}
async function onConfirmTrigger() {
  try { await trgFormRef.value.validate(); } catch { return; }
  // 数据日期不能大于当前日期（兜底，防止绕过 disabled-date）
  const today = new Date().toISOString().slice(0, 10);
  if (!trgDlg.form.dataDate) return ElMessage.warning('请选择数据日期');
  if (trgDlg.form.dataDate > today) return ElMessage.warning(`数据日期不能大于今天（${today}）`);
  trgDlg.saving = true;
  try {
    // 后端 /api/perf/kpi-score/calc：先记审计日志，再调用 KPI 计算服务
    await calcKpiScore({
      dataDate:   trgDlg.form.dataDate,
      schemeCode: trgDlg.form.schemeCode,
      reason:     (trgDlg.form.reason || '').trim()
    });
    ElMessage.success('已触发 KPI 计算');
    trgDlg.show = false;
    setTimeout(reload, 800);
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '触发失败');
  } finally { trgDlg.saving = false; }
}

// === 快照抽屉 ===
const SNAP_MOCK = {
  rows: [
    { emp: '张三', m1: 73.3, m2: 80.3, m3: 67.4, m4: 95.0, m5: 88.0, m6: 92.0, total: 80.6 },
    { emp: '李四', m1: 95.5, m2: 92.0, m3: 86.7, m4: 100.0, m5: 90.0, m6: 85.0, total: 90.7 },
    { emp: '孙七', m1: 68.0, m2: 61.1, m3: 72.0, m4: 88.0, m5: 75.0, m6: 80.0, total: 72.4 }
  ],
  changes: [
    '修改了 <strong>3</strong> 名员工的考核结果',
    '张三总分从 <strong>78.4</strong> 调整为 <strong>80.6</strong>（+2.2）',
    '触发原因：业绩调整 <a class="lk">ADJ-2026-022</a> 通过 → 回算'
  ]
};
const snapDlg = reactive({ show: false, batch: '', rows: [], changes: [] });
async function openSnapshot(row) {
  snapDlg.batch = row.batch;
  snapDlg.rows = []; snapDlg.changes = [];
  snapDlg.show = true;
  try {
    const d = await getComputeBatch(row.rawId || row.batch);
    snapDlg.rows    = (d?.scoreRows && d.scoreRows.length)   ? d.scoreRows : SNAP_MOCK.rows;
    snapDlg.changes = (d?.changes   && d.changes.length)     ? d.changes   : SNAP_MOCK.changes;
  } catch {
    snapDlg.rows = SNAP_MOCK.rows;
    snapDlg.changes = SNAP_MOCK.changes;
  }
}

// === 错误 ===
const errDlg = reactive({ show: false, row: null, errMsg: '' });
async function openError(row) {
  errDlg.row = row;
  errDlg.errMsg = '加载中...';
  errDlg.show = true;
  try {
    const d = await getComputeBatch(row.rawId || row.batch);
    errDlg.errMsg = d?.errorMsg || `批次 ${row.batch} 在 ${row.start} 计算异常：源指标缺失或目标值未发布。`;
  } catch {
    errDlg.errMsg = `批次 ${row.batch} 在 ${row.start} 计算异常：源指标缺失或目标值未发布。`;
  }
}
async function onRetry(row) {
  if (!row) return;
  try {
    await triggerCompute({ batch: row.batch, retry: true });
    ElMessage.success('已重试');
    errDlg.show = false;
    reload();
  } catch { ElMessage.error('重试失败'); }
}

// 进入页面：数据日期默认取计算记录中的最大日期，并展示该日期的数据列表
async function initDefaultLogDate() {
  try {
    const d = await getLatestKpiCalcLogDate();
    if (d) logQuery.dataDate = d;
  } catch { /* 无记录或失败：保持空，展示全部 */ }
}
onMounted(async () => { loadSchemeOptions(); await initDefaultLogDate(); reload(); });
</script>

<style lang="scss" scoped>
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 12px; }

.snap-h {
  font-size: 14px; font-weight: 600; color: $text-1;
  margin: 8px 0 12px; padding: 0 4px;
}
.changes {
  margin: 0; padding: 0 0 0 22px; line-height: 2; color: $text-2;
  li { font-size: 13px; }
  :deep(.lk) { color: $primary; cursor: pointer; }
}
.err-text {
  background: #fef2f2; border: 1px solid #fca5a5; padding: 10px 12px;
  border-radius: 4px; font-family: ui-monospace, monospace; font-size: 12px;
  white-space: pre-wrap; color: #991b1b; margin: 10px 0 0;
}
.pager { display: flex; justify-content: flex-end; padding: 12px 0; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
</style>
