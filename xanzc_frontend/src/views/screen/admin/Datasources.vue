<template>
  <div>
    <div class="page-h">
      <PageTitle />
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
    <el-dialog v-model="dlg.show" :title="dlg.editing ? '编辑数据源' : '新建数据源'" width="760px" top="4vh">
      <el-form label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="dlg.dsName" maxlength="100" />
        </el-form-item>
        <el-form-item label="来源类型" required>
          <el-radio-group v-model="dlg.m.sourceKind" :disabled="!!dlg.editing">
            <el-radio-button label="WIDE_TABLE">指标宽表(引导式)</el-radio-button>
            <el-radio-button label="KPI_RESULT">KPI结果(引导式)</el-radio-button>
            <el-radio-button label="KPI_DETAIL">KPI细项(引导式)</el-radio-button>
            <el-radio-button label="CUSTOM_SQL">自定义 SQL</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 宽表引导式 -->
        <template v-if="dlg.m.sourceKind === 'WIDE_TABLE'">
          <el-form-item label="宽表" required>
            <el-select v-model="dlg.m.wide.table" style="width:100%" @change="onWideTableChange">
              <el-option label="员工指标宽表 (EMP_INDEX_RESULT)" value="EMP_INDEX_RESULT" />
              <el-option label="机构指标宽表 (ORG_INDEX_RESULT)" value="ORG_INDEX_RESULT" />
            </el-select>
          </el-form-item>
          <el-form-item label="指标（多选，保存时自动绑定宽表字段槽位）" required>
            <el-select v-model="dlg.m.wide.metricCodes" multiple filterable style="width:100%">
              <el-option v-for="m in wideMetricOptions" :key="m.metricCode"
                         :label="`${m.metricName} (${m.metricCode})`" :value="m.metricCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="允许的预设周期（存 timeParamJson，供设计器周期下拉参考）">
            <el-select v-model="dlg.m.wide.timeParams" multiple style="width:100%">
              <el-option v-for="p in TIME_PARAM_PRESETS" :key="p" :label="p" :value="p" />
            </el-select>
          </el-form-item>

          <!-- 聚合配置（仅宽表，可选开启；开启后能力标签随 groupBy 联动） -->
          <el-form-item>
            <template #label>
              聚合配置（可选）
              <el-switch v-model="dlg.m.aggEnabled" size="small" style="margin-left:8px" />
            </template>
            <div v-if="dlg.m.aggEnabled" class="agg-box">
              <div class="agg-row">
                <span class="agg-label">分组维度</span>
                <el-select v-model="dlg.m.aggregation.groupBy" style="width:180px">
                  <el-option label="不分组（全量聚合为一行）" value="NONE" />
                  <el-option label="按主体（emp/org）" value="SUBJECT" />
                  <el-option label="按日期（时序）" value="DATE" />
                </el-select>
                <span class="agg-label">聚合函数</span>
                <el-select v-model="dlg.m.aggregation.agg" style="width:120px">
                  <el-option v-for="f in AGG_FUNCS" :key="f" :label="f" :value="f" />
                </el-select>
              </div>
              <div v-for="(f, i) in dlg.m.aggregation.filters" :key="i" class="agg-row">
                <el-select v-model="f.col" filterable allow-create default-first-option
                           placeholder="过滤列" style="width:180px">
                  <el-option v-for="c in filterColOptions" :key="c" :label="c" :value="c" />
                </el-select>
                <el-select v-model="f.op" style="width:90px">
                  <el-option v-for="op in FILTER_OPS" :key="op" :label="op" :value="op" />
                </el-select>
                <el-input v-model="f.value" style="width:220px"
                          :placeholder="f.op === 'IN' ? '多值用英文逗号分隔，如 A,B,C' : '值'" />
                <el-button link type="danger" @click="dlg.m.aggregation.filters.splice(i, 1)">删除</el-button>
              </div>
              <div class="agg-row">
                <el-button size="small" @click="dlg.m.aggregation.filters.push({ col: '', op: 'EQ', value: '' })">
                  + 添加过滤条件
                </el-button>
                <span class="hint">过滤列仅允许主体列/data_date/已绑定槽位列（val_N，保存后编辑可下拉选）</span>
              </div>
            </div>
            <span v-else class="hint">开启后按分组维度聚合明细行；分组维度为「按日期」时能力标签为时序型，否则为单值型</span>
          </el-form-item>
        </template>

        <!-- KPI 结果引导式 -->
        <template v-else-if="dlg.m.sourceKind === 'KPI_RESULT'">
          <el-form-item label="周期类型" required>
            <el-radio-group v-model="dlg.m.kpi.cycleType">
              <el-radio label="MONTHLY">月度</el-radio>
              <el-radio label="QUARTERLY">季度</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>

        <!-- KPI 细项引导式（spec 2026-07-17 §3.1） -->
        <template v-else-if="dlg.m.sourceKind === 'KPI_DETAIL'">
          <el-form-item label="KPI 方案" required>
            <el-select v-model="dlg.m.kpiDetail.schemeCode" filterable style="width:100%"
                       placeholder="选择 ACTIVE 状态的 KPI 方案">
              <el-option v-for="s in schemes" :key="s.schemeCode"
                         :label="`${s.schemeName} (${s.schemeCode})`" :value="s.schemeCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="主体类型" required>
            <el-radio-group v-model="dlg.m.kpiDetail.subjectType" @change="dlg.m.kpiDetail.metrics = []">
              <el-radio label="EMP">员工（个人屏 empId）</el-radio>
              <el-radio label="ORG">机构（支行屏 orgCode）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="模式" required>
            <el-radio-group v-model="dlg.m.kpiDetail.mode">
              <el-radio label="SNAPSHOT">细项快照（最新一日全部细项：目标/实际/完成率/缺口/得分）</el-radio>
              <el-radio label="TREND">细项趋势（按日期返回所选细项的得分或完成率）</el-radio>
            </el-radio-group>
          </el-form-item>
          <template v-if="dlg.m.kpiDetail.mode === 'TREND'">
            <el-form-item label="指标（细项，多选；保存时携带名称快照）" required>
              <el-select v-model="kpiMetricCodes" multiple filterable style="width:100%">
                <el-option v-for="m in kpiMetricOptions" :key="m.metricCode"
                           :label="`${m.metricName} (${m.metricCode})`" :value="m.metricCode" />
              </el-select>
            </el-form-item>
            <el-form-item label="取值列" required>
              <el-radio-group v-model="dlg.m.kpiDetail.valueCol">
                <el-radio label="score">得分（score）</el-radio>
                <el-radio label="completeRate">完成率（completeRate）</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="允许的预设周期（存 timeParamJson，供设计器周期下拉参考）">
              <el-select v-model="dlg.m.kpiDetail.timeParams" multiple style="width:100%">
                <el-option v-for="p in TIME_PARAM_PRESETS" :key="p" :label="p" :value="p" />
              </el-select>
            </el-form-item>
          </template>
        </template>

        <!-- 自定义 SQL -->
        <template v-else>
          <el-form-item label="SELECT 语句（占位参数：#{orgCode} #{empId} #{dateFrom} #{dateTo}；仅白名单表）" required>
            <el-input v-model="dlg.m.sql.text" type="textarea" :rows="6"
                      placeholder="SELECT org_name, cnt FROM ... WHERE org_code = #{orgCode}" />
          </el-form-item>
          <el-form-item label="能力标签" required>
            <el-radio-group v-model="dlg.m.dsType">
              <el-radio label="SINGLE">单值型（仅最新统计结果）</el-radio>
              <el-radio label="TIMESERIES">时序型（须声明日期列）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="dlg.m.dsType === 'TIMESERIES'" label="日期列名" required>
            <el-input v-model="dlg.m.sql.dateCol" placeholder="如 stat_date" />
          </el-form-item>
          <el-form-item label="操作原因（高危审计必填）" required>
            <el-input v-model="dlg.reason" maxlength="200" />
          </el-form-item>
        </template>

        <!-- 引导式类型：能力标签由配置推导，只读展示（后端保存时强制一致） -->
        <el-form-item v-if="dlg.m.sourceKind !== 'CUSTOM_SQL'" label="能力标签（按配置自动推导，保存时后端强制）">
          <el-tag :type="derivedDsType === 'TIMESERIES' ? 'success' : 'info'">
            {{ derivedDsType === 'TIMESERIES' ? '时序型 TIMESERIES' : '单值型 SINGLE' }}
          </el-tag>
        </el-form-item>

        <!-- 字段元数据（全类型通用可选段，spec §3.2） -->
        <el-form-item>
          <template #label>
            字段元数据（可选：别名/角色/单位/小数位，组件展示时套用）
            <el-button size="small" style="margin-left:8px"
                       @click="dlg.m.fieldMeta.push({ col: '', alias: '', role: 'METRIC', unit: '', decimals: null })">
              + 加一行
            </el-button>
          </template>
          <el-table v-if="dlg.m.fieldMeta.length" :data="dlg.m.fieldMeta" size="small" border>
            <el-table-column label="列名(col)" min-width="150">
              <template #default="{ row }">
                <el-select v-model="row.col" filterable allow-create default-first-option
                           placeholder="试跑后可下拉选，也可手输" style="width:100%">
                  <el-option v-for="c in lastTryCols" :key="c" :label="c" :value="c" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="别名(alias)" min-width="120">
              <template #default="{ row }"><el-input v-model="row.alias" /></template>
            </el-table-column>
            <el-table-column label="角色" width="110">
              <template #default="{ row }">
                <el-select v-model="row.role">
                  <el-option label="维度 DIM" value="DIM" />
                  <el-option label="度量 METRIC" value="METRIC" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="单位" width="90">
              <template #default="{ row }"><el-input v-model="row.unit" /></template>
            </el-table-column>
            <el-table-column label="小数位" width="80">
              <template #default="{ row }"><el-input v-model="row.decimals" placeholder="如 2" /></template>
            </el-table-column>
            <el-table-column label="" width="60">
              <template #default="{ $index }">
                <el-button link type="danger" @click="dlg.m.fieldMeta.splice($index, 1)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
          <span v-else class="hint">未配置时组件按原始列名展示；先在列表页「试跑」一次可让列名支持下拉选择</span>
        </el-form-item>

        <!-- 数据范围模式（spec §4） -->
        <el-form-item label="数据范围模式">
          <el-radio-group v-model="dlg.m.scopeMode">
            <el-radio label="SUBJECT">主体范围（按 empId/orgCode 校验查看者数据范围，默认）</el-radio>
            <el-radio label="GLOBAL">全省聚合（无主体参数的全省类数据；查看需 ALL/省级数据范围权限）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.show = false">取消</el-button>
        <el-button :loading="dlg.saving" type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 试跑预览 -->
    <el-dialog v-model="tr.show" title="试跑预览（前 10 行）" width="760px">
      <el-form inline>
        <el-form-item label="orgCode"><el-input v-model="tr.orgCode" style="width:140px" /></el-form-item>
        <el-form-item label="empId"><el-input v-model="tr.empId" style="width:140px" /></el-form-item>
        <el-form-item label="周期">
          <el-select v-model="tr.period" style="width:140px">
            <el-option v-for="p in TIME_PARAM_PRESETS" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-button type="primary" :loading="tr.running" @click="runTry">执行</el-button>
      </el-form>
      <!-- 列头套用 columnsMeta（别名+单位）；单元格 null → "—"（如完成率 target=0） -->
      <el-table v-if="tr.result" :data="trRows" size="small" border max-height="360">
        <el-table-column v-for="(c, i) in previewCols" :key="c.col" :label="c.label">
          <template #default="{ row }">{{ formatPreviewCell(row[i], c.meta) }}</template>
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
  deleteScreenDatasource, tryRunScreenDatasource, listKpiSchemes
} from '@/api/screen';
import { listMetrics } from '@/api/metrics';
import {
  defaultDsModel, deriveDsType, buildConfigJson, buildTimeParamJson,
  parseConfigJson, validateDsModel, buildPreviewColumns, formatPreviewCell,
  AGG_FUNCS, FILTER_OPS, TIME_PARAM_PRESETS
} from '@/utils/dsConfig';

const KIND_LABELS = { WIDE_TABLE: '指标宽表', KPI_RESULT: 'KPI结果', KPI_DETAIL: 'KPI细项', CUSTOM_SQL: '自定义SQL' };

const list = ref([]);
const loading = ref(false);
const metrics = ref([]);
const schemes = ref([]);       // KPI 方案下拉（/screen/admin/kpi-schemes）
const lastTryCols = ref([]);   // 最近一次试跑返回的列名 → fieldMeta 的 col 下拉候选

const dlg = reactive({
  show: false, editing: null, saving: false,
  dsName: '', reason: '',
  m: defaultDsModel()   // 表单模型（config_json 双向转换见 utils/dsConfig.js）
});

// 能力标签联动展示：宽表随聚合 groupBy、KPI 细项随模式（与后端保存强制规则一致）
const derivedDsType = computed(() => deriveDsType(dlg.m));

// 宽表指标下拉：按所选宽表的维度过滤（EMP 表→EMP 指标）
const wideMetricOptions = computed(() => {
  const dim = dlg.m.wide.table.startsWith('EMP') ? 'EMP' : 'ORG';
  return metrics.value.filter(m => m.baseDim === dim);
});

// KPI 细项指标下拉：按主体类型过滤（EMP/ORG 与指标 baseDim 对应）
const kpiMetricOptions = computed(() =>
  metrics.value.filter(m => m.baseDim === dlg.m.kpiDetail.subjectType));

// KPI 细项多选：v-model 绑 code 数组，写入时同步组装 {metricCode, metricName} 名称快照
// （后端 TREND 校验 code+name 均非空；编辑回填的已下架指标保留原快照名称）
const kpiMetricCodes = computed({
  get: () => dlg.m.kpiDetail.metrics.map(m => m.metricCode),
  set: (codes) => {
    dlg.m.kpiDetail.metrics = codes.map(c => {
      const found = metrics.value.find(mm => mm.metricCode === c);
      const prev = dlg.m.kpiDetail.metrics.find(mm => mm.metricCode === c);
      return { metricCode: c, metricName: found?.metricName || prev?.metricName || c };
    });
  }
});

// 聚合过滤列候选：主体列 + data_date + 已绑定槽位列（val_N 仅编辑已保存的数据源时已知）
const filterColOptions = computed(() => {
  const subject = dlg.m.wide.table === 'ORG_INDEX_RESULT' ? 'org_code' : 'emp_id';
  return [subject, 'data_date', ...dlg.m.wide.slotCols];
});

function onWideTableChange() {
  // 换表后指标维度变化，清空已选指标与槽位缓存
  dlg.m.wide.metricCodes = [];
  dlg.m.wide.slotCols = [];
}

async function reload() {
  loading.value = true;
  try {
    list.value = await listScreenDatasources();
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  dlg.editing = null;
  dlg.dsName = '';
  dlg.reason = '';
  dlg.m = defaultDsModel();
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
  dlg.dsName = row.dsName;
  dlg.reason = '';
  const cfg = safeParse(row.configJson || '{}', {});
  // 先重置默认再打补丁（读时兼容 v1 旧数据：缺 fieldMeta/aggregation/scopeMode 补默认）
  const fresh = defaultDsModel();
  Object.assign(fresh, parseConfigJson(row.sourceKind, cfg, row.timeParamJson),
    { sourceKind: row.sourceKind, dsType: row.dsType || fresh.dsType });
  dlg.m = fresh;
  dlg.show = true;
}

async function onSave() {
  if (!dlg.dsName) { ElMessage.warning('名称必填'); return; }
  if (dlg.m.sourceKind === 'CUSTOM_SQL' && !dlg.reason.trim()) {
    ElMessage.warning('操作原因必填（高危审计）'); return;
  }
  // config 相关校验集中在纯函数（与后端 43009 规则对齐），只提示第一条
  const errors = validateDsModel(dlg.m);
  if (errors.length) { ElMessage.warning(errors[0]); return; }
  dlg.saving = true;
  try {
    const body = {
      dsName: dlg.dsName,
      sourceKind: dlg.m.sourceKind,
      // 引导式类型提交推导值（KPI_DETAIL 显式传不一致会被后端 43009 拒绝）
      dsType: derivedDsType.value,
      configJson: JSON.stringify(buildConfigJson(dlg.m)),
      timeParamJson: buildTimeParamJson(dlg.m),
      reason: dlg.reason
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
// 列头套用 columnsMeta（可空——旧数据源无 fieldMeta 时退化为原始列名）
const previewCols = computed(() => buildPreviewColumns(tr.result?.columns, tr.result?.columnsMeta));

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
    // 记录返回列名，供编辑弹窗的字段元数据 col 下拉候选
    if (Array.isArray(tr.result?.columns) && tr.result.columns.length) {
      lastTryCols.value = tr.result.columns;
    }
  } finally {
    tr.running = false;
  }
}

onMounted(async () => {
  reload();
  const r = await listMetrics({ pageSize: 100 });
  metrics.value = Array.isArray(r) ? r : (r?.records || []);
  schemes.value = await listKpiSchemes();
});
</script>

<style scoped>
.agg-box { width: 100%; }
.agg-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.agg-label { color: var(--el-text-color-secondary); font-size: 13px; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
