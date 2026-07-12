<template>
  <div>
    <div class="page-h">
      <h1>大屏数据源</h1>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" @click="openCreate">+ 新建数据源</el-button>
      </div>
    </div>

    <div class="card-section" v-loading="loading">
      <el-table :data="list" size="default" stripe border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="dsCode" label="编码" width="140" />
        <el-table-column prop="dsName" label="名称" min-width="160" />
        <el-table-column label="能力标签" width="100">
          <template #default="{ row }">
            <el-tag :type="row.dsType === 'TIMESERIES' ? 'success' : 'info'" size="small">
              {{ row.dsType === 'TIMESERIES' ? '时序型' : '单值型' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">{{ KIND_LABELS[row.sourceKind] || row.sourceKind }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link @click="openTryRun(row)">试跑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新建/编辑 -->
    <el-dialog v-model="dlg.show" :title="dlg.editing ? '编辑数据源' : '新建数据源'" width="720px" top="5vh">
      <el-form :model="dlg.form" label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="dlg.form.dsName" maxlength="100" />
        </el-form-item>
        <el-form-item label="来源类型" required>
          <el-radio-group v-model="dlg.form.sourceKind" :disabled="!!dlg.editing">
            <el-radio-button label="WIDE_TABLE">指标宽表(引导式)</el-radio-button>
            <el-radio-button label="KPI_RESULT">KPI结果(引导式)</el-radio-button>
            <el-radio-button label="CUSTOM_SQL">自定义 SQL</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 宽表引导式 -->
        <template v-if="dlg.form.sourceKind === 'WIDE_TABLE'">
          <el-form-item label="宽表" required>
            <el-select v-model="dlg.wide.table" style="width:100%" @change="dlg.wide.metricCodes = []">
              <el-option label="员工指标宽表 (EMP_INDEX_RESULT)" value="EMP_INDEX_RESULT" />
              <el-option label="机构指标宽表 (ORG_INDEX_RESULT)" value="ORG_INDEX_RESULT" />
            </el-select>
          </el-form-item>
          <el-form-item label="指标（多选，保存时自动绑定宽表字段槽位）" required>
            <el-select v-model="dlg.wide.metricCodes" multiple filterable style="width:100%">
              <el-option v-for="m in metricOptions" :key="m.metricCode"
                         :label="`${m.metricName} (${m.metricCode})`" :value="m.metricCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="允许的预设周期（存 timeParamJson，供设计器周期下拉参考）">
            <el-select v-model="dlg.wide.timeParams" multiple style="width:100%">
              <el-option v-for="p in ['LATEST','LAST_10D','LAST_1M','LAST_6M_EOM']" :key="p" :label="p" :value="p" />
            </el-select>
          </el-form-item>
          <el-alert type="info" :closable="false"
                    title="宽表数据源自动标记为【时序型】，支持趋势/钻取组件" />
        </template>

        <!-- KPI 引导式 -->
        <template v-else-if="dlg.form.sourceKind === 'KPI_RESULT'">
          <el-form-item label="周期类型" required>
            <el-radio-group v-model="dlg.kpi.cycleType">
              <el-radio label="MONTHLY">月度</el-radio>
              <el-radio label="QUARTERLY">季度</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>

        <!-- 自定义 SQL -->
        <template v-else>
          <el-form-item label="SELECT 语句（占位参数：#{orgCode} #{empId} #{dateFrom} #{dateTo}；仅白名单表）" required>
            <el-input v-model="dlg.sql.text" type="textarea" :rows="6"
                      placeholder="SELECT org_name, cnt FROM ... WHERE org_code = #{orgCode}" />
          </el-form-item>
          <el-form-item label="能力标签" required>
            <el-radio-group v-model="dlg.form.dsType">
              <el-radio label="SINGLE">单值型（仅最新统计结果）</el-radio>
              <el-radio label="TIMESERIES">时序型（须声明日期列）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="dlg.form.dsType === 'TIMESERIES'" label="日期列名" required>
            <el-input v-model="dlg.sql.dateCol" placeholder="如 stat_date" />
          </el-form-item>
          <el-form-item label="操作原因（高危审计必填）" required>
            <el-input v-model="dlg.form.reason" maxlength="200" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dlg.show = false">取消</el-button>
        <el-button :loading="dlg.saving" type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 试跑预览 -->
    <el-dialog v-model="tr.show" title="试跑预览（前 10 行）" width="720px">
      <el-form inline>
        <el-form-item label="orgCode"><el-input v-model="tr.orgCode" style="width:140px" /></el-form-item>
        <el-form-item label="empId"><el-input v-model="tr.empId" style="width:140px" /></el-form-item>
        <el-form-item label="周期">
          <el-select v-model="tr.period" style="width:140px">
            <el-option v-for="p in ['LATEST','LAST_10D','LAST_1M','LAST_6M_EOM']" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-button type="primary" :loading="tr.running" @click="runTry">执行</el-button>
      </el-form>
      <el-table v-if="tr.result" :data="trRows" size="small" border max-height="360">
        <el-table-column v-for="(c, i) in tr.result.columns" :key="c" :label="c">
          <template #default="{ row }">{{ row[i] }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  listScreenDatasources, saveScreenDatasource, updateScreenDatasource,
  deleteScreenDatasource, tryRunScreenDatasource
} from '@/api/screen';
import { listMetrics } from '@/api/metrics';

const KIND_LABELS = { WIDE_TABLE: '指标宽表', KPI_RESULT: 'KPI结果', CUSTOM_SQL: '自定义SQL' };

const list = ref([]);
const loading = ref(false);
const metrics = ref([]);

const dlg = reactive({
  show: false, editing: null, saving: false,
  form: { dsName: '', sourceKind: 'WIDE_TABLE', dsType: 'SINGLE', reason: '' },
  wide: { table: 'EMP_INDEX_RESULT', metricCodes: [], timeParams: ['LATEST', 'LAST_10D', 'LAST_1M', 'LAST_6M_EOM'] },
  kpi: { cycleType: 'MONTHLY' },
  sql: { text: '', dateCol: '' }
});

// 宽表指标下拉：按所选宽表的维度过滤（EMP 表→EMP 指标）
const metricOptions = computed(() => {
  const dim = dlg.wide.table.startsWith('EMP') ? 'EMP' : 'ORG';
  return metrics.value.filter(m => m.baseDim === dim);
});

async function reload() {
  loading.value = true;
  try {
    list.value = await listScreenDatasources();
  } finally {
    loading.value = false;
  }
}

function buildConfigJson() {
  if (dlg.form.sourceKind === 'WIDE_TABLE') {
    // 只传 metricCode，槽位翻译由后端完成
    return JSON.stringify({
      table: dlg.wide.table,
      metrics: dlg.wide.metricCodes.map(c => ({ metricCode: c }))
    });
  }
  if (dlg.form.sourceKind === 'KPI_RESULT') {
    return JSON.stringify({ cycleType: dlg.kpi.cycleType });
  }
  return JSON.stringify({ sql: dlg.sql.text, dateCol: dlg.sql.dateCol || null });
}

function openCreate() {
  dlg.editing = null;
  Object.assign(dlg.form, { dsName: '', sourceKind: 'WIDE_TABLE', dsType: 'SINGLE', reason: '' });
  Object.assign(dlg.wide, { table: 'EMP_INDEX_RESULT', metricCodes: [], timeParams: ['LATEST', 'LAST_10D', 'LAST_1M', 'LAST_6M_EOM'] });
  Object.assign(dlg.kpi, { cycleType: 'MONTHLY' });
  Object.assign(dlg.sql, { text: '', dateCol: '' });
  dlg.show = true;
}

// 安全解析 JSON：解析失败时提示并回退到默认值，避免弹窗因脏数据无法打开
function safeParse(str, fallback) {
  try {
    return JSON.parse(str);
  } catch {
    ElMessage.warning('配置 JSON 解析失败，已按空配置打开');
    return fallback;
  }
}

function openEdit(row) {
  dlg.editing = row.id;
  const cfg = safeParse(row.configJson || '{}', {});
  Object.assign(dlg.form, {
    dsName: row.dsName, sourceKind: row.sourceKind, dsType: row.dsType, reason: ''
  });
  if (row.sourceKind === 'WIDE_TABLE') {
    Object.assign(dlg.wide, { table: cfg.table, metricCodes: (cfg.metrics || []).map(m => m.metricCode),
      timeParams: safeParse(row.timeParamJson || '[]', []) });
  } else if (row.sourceKind === 'KPI_RESULT') {
    Object.assign(dlg.kpi, { cycleType: cfg.cycleType || 'MONTHLY' });
  } else {
    Object.assign(dlg.sql, { text: cfg.sql || '', dateCol: cfg.dateCol || '' });
  }
  dlg.show = true;
}

async function onSave() {
  if (!dlg.form.dsName) { ElMessage.warning('名称必填'); return; }
  if (dlg.form.sourceKind === 'WIDE_TABLE' && dlg.wide.metricCodes.length === 0) {
    ElMessage.warning('请至少选择一个指标'); return;
  }
  if (dlg.form.sourceKind === 'CUSTOM_SQL') {
    if (!dlg.sql.text.trim()) { ElMessage.warning('SQL 语句必填'); return; }
    if (dlg.form.dsType === 'TIMESERIES' && !dlg.sql.dateCol.trim()) {
      ElMessage.warning('时序型必须声明日期列'); return;
    }
    if (!dlg.form.reason.trim()) { ElMessage.warning('操作原因必填（高危审计）'); return; }
  }
  dlg.saving = true;
  try {
    const body = {
      dsName: dlg.form.dsName,
      sourceKind: dlg.form.sourceKind,
      dsType: dlg.form.dsType,
      configJson: buildConfigJson(),
      timeParamJson: dlg.form.sourceKind === 'WIDE_TABLE' ? JSON.stringify(dlg.wide.timeParams) : null,
      reason: dlg.form.reason
    };
    if (dlg.editing) await updateScreenDatasource(dlg.editing, body);
    else await saveScreenDatasource(body);
    ElMessage.success('已保存');
    dlg.show = false;
    reload();
  } catch (e) {
    // http.js 已弹错误 toast
  } finally {
    dlg.saving = false;
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除数据源「${row.dsName}」？被大屏区块引用时将拒绝删除。`, '删除确认', { type: 'warning' });
  } catch { return; }
  await deleteScreenDatasource(row.id);
  ElMessage.success('已删除');
  reload();
}

// 试跑
const tr = reactive({ show: false, row: null, orgCode: '', empId: '', period: 'LATEST', running: false, result: null });
const trRows = computed(() => tr.result?.rows || []);

function openTryRun(row) {
  Object.assign(tr, { show: true, row, result: null });
}
async function runTry() {
  tr.running = true;
  try {
    tr.result = await tryRunScreenDatasource({
      sourceKind: tr.row.sourceKind,
      dsType: tr.row.dsType,
      configJson: tr.row.configJson,
      period: tr.period,
      contextParams: { orgCode: tr.orgCode || null, empId: tr.empId || null },
      reason: '配置态试跑'
    });
  } finally {
    tr.running = false;
  }
}

onMounted(async () => {
  reload();
  const r = await listMetrics({ pageSize: 100 });
  metrics.value = Array.isArray(r) ? r : (r?.records || []);
});
</script>
