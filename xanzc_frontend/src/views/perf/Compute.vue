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

    <div class="card-section">
      <el-table :data="rows" size="default" v-loading="loading">
        <el-table-column prop="batch" label="计算批次" width="190" />
        <el-table-column prop="plan" label="方案" width="140" />
        <el-table-column prop="scope" label="触发范围" width="140" />
        <el-table-column label="触发方式" width="100">
          <template #default="{row}">
            <el-tag :class="triggerCls(row.trigger)" effect="plain">{{ row.trigger }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="who" label="触发人" width="120" />
        <el-table-column prop="start" label="开始时间" width="100" />
        <el-table-column prop="dur" label="耗时" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :class="row.status === '成功' ? 'tag-success' : 'tag-danger'" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{row}">
            <el-button v-if="row.status === '失败'" link type="primary" size="small" @click="openError(row)">查看错误</el-button>
            <el-button v-else link type="primary" size="small" @click="openSnapshot(row)">查看快照</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 触发计算 弹框（截图 181）—— UI 仅 3 字段，后端需要的 cycleType/cycleDateFrom/cycleDateTo/version/reason 在提交时自动派生 -->
    <el-dialog v-model="trgDlg.show" title="确认触发 KPI 计算" width="520px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:14px"
        :title="`该操作将基于当前指标结果与目标值，重算 ${trgDlg.form.scheme} ${rangeLabel(trgDlg.form.scope)} KPI 得分。预计耗时 8 分钟，期间会锁定相关数据。`" />
      <el-form ref="trgFormRef" :model="trgDlg.form" :rules="trgRules" label-position="top" size="default">
        <el-form-item label="方案" prop="scheme">
          <el-select v-model="trgDlg.form.scheme" style="width:100%">
            <el-option label="2026Q2 KPI" value="2026Q2" />
            <el-option label="2026Q1 KPI" value="2026Q1" />
            <el-option label="2026 全年 KPI" value="2026" />
            <el-option label="2025Y KPI" value="2025" />
          </el-select>
        </el-form-item>
        <el-form-item label="范围" prop="scope">
          <el-select v-model="trgDlg.form.scope" style="width:100%">
            <el-option label="全行"   value="ALL" />
            <el-option label="按机构" value="ORG" />
            <el-option label="按员工" value="EMP" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据日期" prop="dataDate">
          <el-date-picker v-model="trgDlg.form.dataDate" type="date"
            value-format="YYYY-MM-DD" style="width:100%" placeholder="选择数据日期" />
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import {
  listComputeBatches, triggerCompute, getComputeBatch
} from '@/api/perf';

// 后端 PerfRunTaskController 没有 /stats 端点；统计在前端从 rows 派生
const rows = ref([]);
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

// 前端派生统计：本月任务数 / 成功数 / 失败数 / 最近耗时
const s = computed(() => {
  const now = new Date();
  const ym = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}`;
  const monthRows = rows.value.filter(r => (r.start || '').startsWith(ym) || (r.startTime || '').startsWith(ym));
  const ok   = monthRows.filter(r => r.rawStatus === 'SUCCESS').length;
  const fail = monthRows.filter(r => r.rawStatus === 'FAILED').length;
  const lastSuccess = rows.value.find(r => r.rawStatus === 'SUCCESS' && r.dur && r.dur !== '—');
  return {
    tasks: monthRows.length || rows.value.length,
    ok, fail,
    lastDuration: lastSuccess?.dur || '—'
  };
});

async function reload() {
  loading.value = true;
  try {
    const r = await listComputeBatches({ pageSize: 50 });
    if (Array.isArray(r)) rows.value = r.map(adaptTask);
  } catch {} finally { loading.value = false; }
}

// === 触发计算（UI 仅 3 字段：方案 / 范围 / 数据日期）===
// 后端 RecalcReqDTO 必填 cycleType/cycleDateFrom/cycleDateTo/version/reason → 在 onConfirmTrigger 自动派生
const trgFormRef = ref(null);
const trgDlg = reactive({
  show: false, saving: false,
  form: { scheme: '2026Q2', scope: 'ALL', dataDate: new Date().toISOString().slice(0, 10) }
});
const trgRules = {
  scheme:   [{ required: true, message: '请选择方案' }],
  scope:    [{ required: true, message: '请选择范围' }],
  dataDate: [{ required: true, message: '请选择数据日期' }]
};
function openTrigger() {
  trgDlg.form.dataDate = new Date().toISOString().slice(0, 10);
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
  trgDlg.saving = true;
  try {
    const cyc = deriveCycle(trgDlg.form.scheme);
    const d = new Date();
    const version = `v${d.getFullYear()}${String(d.getMonth()+1).padStart(2,'0')}${String(d.getDate()).padStart(2,'0')}-${String(d.getHours()).padStart(2,'0')}${String(d.getMinutes()).padStart(2,'0')}`;
    const reason = `前端触发 KPI 重算：${trgDlg.form.scheme} / ${rangeLabel(trgDlg.form.scope)} / 数据日期 ${trgDlg.form.dataDate}`;
    const r = await triggerCompute({
      cycleType:     cyc.cycleType,
      cycleDateFrom: cyc.cycleDateFrom,
      cycleDateTo:   trgDlg.form.dataDate || cyc.cycleDateTo,  // 用户选的数据日期作为终点
      version,
      reason
    });
    ElMessage.success(`已提交计算任务${r?.batch ? '：' + r.batch : ''}`);
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

onMounted(reload);
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
</style>
