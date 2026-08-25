<template>
  <CommonAttr :element="element">
    <el-form-item label="数据源">
      <el-select v-model="bind.dsId" filterable
                 :aria-describedby="scopeConflict ? 'datasource-scope-conflict' : undefined"
                 @change="syncBind">
        <el-option v-for="d in filteredDs" :key="d.id" :label="d.dsName" :value="d.id" />
        <el-option v-for="d in scopeBlockedDs" :key="`scope-blocked-${d.id}`"
                   data-testid="datasource-scope-blocked" disabled
                   :label="`${d.dsName}（${scopeConflictHint}）`" :value="`__scope_blocked__${d.id}`" />
      </el-select>
      <!-- 联动过滤提示：needTimeseries/needKinds 元数据约束（后端另有 RPT-43005 等校验兜底） -->
      <div v-if="dsHint" class="attr-hint">{{ dsHint }}</div>
      <div v-if="scopeConflict" id="datasource-scope-conflict" class="attr-scope-conflict"
           data-testid="datasource-scope-conflict" role="status" aria-live="polite">
        <strong>当前图表类型与本屏范围没有可用数据源</strong>
        <span>范围冲突：{{ scopeConflictReason }}。</span>
        <span>以下数据源符合图表类型，但当前不可选择：</span>
        <ul>
          <li v-for="d in scopeBlockedDs" :key="`scope-note-${d.id}`">{{ d.dsName }}（{{ scopeConflictHint }}）</li>
        </ul>
        <span>{{ scopeConflictAction }}</span>
      </div>
    </el-form-item>
    <el-form-item label="周期"><el-input v-model="bind.period" placeholder="LATEST / LAST_10D" @input="syncBind" /></el-form-item>

    <!-- 全屏周期过滤器联动豁免(spec §5.3):仅时序数据源会被联动,豁免后维持自身周期 -->
    <el-form-item label="全局周期">
      <el-switch v-model="propValue.ignoreGlobalPeriod" active-text="忽略联动" @change="syncProp" />
      <div class="attr-hint">大屏上的周期过滤器默认联动全部时序(TIMESERIES)图表;开启后本图表维持自身周期不被覆盖</div>
    </el-form-item>

    <el-form-item label="视觉预设">
      <el-select v-model="styleCfg.visualPreset" data-testid="chart-visual-preset" @change="syncStyle">
        <el-option label="Aurora 极光" value="aurora" />
        <el-option label="Graphite 石墨" value="graphite" />
        <el-option label="Vivid 明彩" value="vivid" />
      </el-select>
    </el-form-item>

    <!-- 线/面积/柱图共用的展示开关；值进入 styleJson，旧节点缺省时只在面板内补默认。 -->
    <template v-if="showSeriesVisualControls">
      <el-form-item label="图例">
        <el-switch v-model="styleCfg.showLegend" data-testid="chart-show-legend" @change="syncStyle" />
      </el-form-item>
      <el-form-item label="数据标签">
        <el-switch v-model="styleCfg.showLabels" data-testid="chart-show-labels" @change="syncStyle" />
      </el-form-item>
      <el-form-item label="辅助标记">
        <el-switch v-model="styleCfg.showMarks" data-testid="chart-show-marks" @change="syncStyle" />
      </el-form-item>
      <el-form-item v-if="showSmoothControl" label="曲线平滑">
        <el-switch v-model="styleCfg.smooth" data-testid="chart-smooth" @change="syncStyle" />
      </el-form-item>
    </template>
    <el-form-item v-if="showStandaloneLabelControl" label="数据标签">
      <el-switch v-model="styleCfg.showLabels" data-testid="chart-show-labels" @change="syncStyle" />
    </el-form-item>
    <el-form-item v-if="showSmoothControl && !showSeriesVisualControls" label="曲线平滑">
      <el-switch v-model="styleCfg.smooth" data-testid="chart-smooth" @change="syncStyle" />
    </el-form-item>

    <el-form-item v-if="innerType === 'SPARKLINE_CARD'" label="较上期变化">
      <el-switch v-model="styleCfg.showTrend" data-testid="sparkline-show-trend" @change="syncStyle" />
      <div class="attr-hint">前值为 0 或非数值时自动隐藏百分比，避免 Infinity/NaN</div>
    </el-form-item>

    <el-form-item v-if="innerType === 'PIE_SHARE'" label="饼图形态">
      <el-radio-group v-model="styleCfg.pieShape" data-testid="pie-shape" @change="syncStyle">
        <el-radio-button label="donut">环形</el-radio-button>
        <el-radio-button label="rose">玫瑰</el-radio-button>
        <el-radio-button label="solid">实心</el-radio-button>
      </el-radio-group>
    </el-form-item>

    <el-form-item v-if="innerType === 'METRIC_CARD'" label="指标卡形态">
      <el-radio-group v-model="styleCfg.cardVariant" data-testid="card-variant" @change="syncStyle">
        <el-radio-button label="glow">辉光</el-radio-button>
        <el-radio-button label="glass">玻璃</el-radio-button>
        <el-radio-button label="outline">描边</el-radio-button>
      </el-radio-group>
    </el-form-item>
    <el-form-item v-if="innerType === 'METRIC_CARD'" label="较上期趋势">
      <el-switch v-model="styleCfg.showTrend" data-testid="chart-show-trend" @change="syncStyle" />
      <div class="attr-hint">仅在明确开启时计算最近两行的较上期变化</div>
    </el-form-item>

    <!-- 柱状对比三形态 -->
    <el-form-item v-if="innerType === 'BAR_COMPARE'" label="柱形">
      <el-radio-group v-model="propValue.barMode" @change="syncProp">
        <el-radio-button label="basic">基础</el-radio-button>
        <el-radio-button label="stack">堆叠</el-radio-button>
        <el-radio-button label="horizontal">横向</el-radio-button>
      </el-radio-group>
    </el-form-item>

    <!-- 明细表格自动滚动轮播 -->
    <el-form-item v-if="innerType === 'TABLE_LIST'" label="轮播">
      <el-switch v-model="propValue.carousel" @change="syncProp" />
      <div class="attr-hint">行数超出可视区时自动循环滚动</div>
    </el-form-item>

    <!-- KPI 雷达取值维度 -->
    <el-form-item v-if="innerType === 'KPI_RADAR'" label="取值">
      <el-radio-group v-model="propValue.valueField" @change="syncProp">
        <el-radio-button label="score">得分</el-radio-button>
        <el-radio-button label="rate">完成率</el-radio-button>
      </el-radio-group>
    </el-form-item>

    <!-- 仪表盘/水波球：绑定数值列（默认取名含"完成率"的列；列选项按当前数据源探测，可手输） -->
    <el-form-item v-if="needValueCol" label="数值列">
      <el-select v-model="bind.valueCol" filterable allow-create clearable default-first-option
                 placeholder="默认取含完成率的列" @change="syncBind">
        <el-option v-for="c in colOptions" :key="c" :label="c" :value="c" />
      </el-select>
    </el-form-item>
    <el-form-item v-if="needValueCol" label="列探测">
      <div class="probe-box">
        <el-input v-model="probe.reason" data-testid="datasource-probe-reason" maxlength="500"
                  placeholder="填写列探测原因（高危审计必填）" />
        <el-select v-if="probeRequiresNamedGroup" v-model="probe.testOrgGroupCode" filterable
                   data-testid="datasource-probe-group" placeholder="选择测试机构组">
          <el-option v-for="group in probeOrgGroups" :key="group.groupCode"
                     :label="`${group.groupName} (${group.groupCode})`" :value="group.groupCode" />
        </el-select>
        <el-button :loading="probe.running" data-testid="datasource-probe-submit" @click="probeColumns">探测列</el-button>
        <div class="attr-hint">使用独立高危资源 R_RPT_SCR_DS_PROBE；不调用运行时 /screen/data。命名机构组必须选择测试机构组。</div>
      </div>
    </el-form-item>

    <el-form-item label="标题"><el-input v-model="styleCfg.title" @input="syncStyle" /></el-form-item>
    <el-form-item label="刷新(秒)"><el-input-number v-model="styleCfg.refreshSec" :min="0" @change="syncStyle" /></el-form-item>
    <el-form-item label="钻取"><el-switch v-model="drill.drillEnabled" @change="syncDrill" /></el-form-item>
  </CommonAttr>
</template>
<script setup>
import { computed, reactive, ref, onMounted, watch } from 'vue';
import { ElMessage } from 'element-plus';
import CommonAttr from '@/views/screen/designer/panels/CommonAttr.vue';
import { listScreenDatasources, listOrgGroups, probeScreenDatasourceColumns } from '@/api/screen';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
import { chartMetas } from '@/views/screen/designer/widgets';
import { filterDatasourcesByMeta } from './dsFilter';
import { buildDatasourceProbeRequest, datasourceTryRunScopeMode } from '@/utils/dsConfig';
import { filterActiveOrgGroups, filterReportScreenOrgGroups } from '@/utils/screenScope';
const props = defineProps({ element: { type: Object, required: true } });
const store = useScreenDesignerStore();
const datasources = ref([]);
const datasourcesLoaded = ref(false);
const colOptions = ref([]);   // 数值列下拉候选（按数据源探测，探测失败仍可 allow-create 手输）
const probeOrgGroups = ref([]);
const probe = reactive({ reason: '', testOrgGroupCode: '', running: false });
const bind = reactive(parse(props.element.bindJson));
const styleCfg = reactive(parse(props.element.styleJson));
const drill = reactive(parse(props.element.drillJson));
// propValue 直接挂在组件节点上（与素材组件同机制,随画布 JSON 持久化）;旧节点缺失时补 {}
if (!props.element.propValue) props.element.propValue = {};
const propValue = reactive(props.element.propValue);
// 面板回显默认值兜底（与渲染组件的运行时默认一致,只补缺失不覆盖,不记快照）
if (props.element.innerType === 'BAR_COMPARE' && propValue.barMode == null) propValue.barMode = 'basic';
if (props.element.innerType === 'KPI_RADAR' && propValue.valueField == null) propValue.valueField = 'score';
if (props.element.innerType === 'TABLE_LIST' && propValue.carousel == null) propValue.carousel = false;
// 全屏周期过滤器联动豁免开关默认关(=响应联动);旧节点缺失时补 false 保持 el-switch 受控
if (propValue.ignoreGlobalPeriod == null) propValue.ignoreGlobalPeriod = false;

const innerType = computed(() => props.element.innerType);
const chartMeta = computed(() => chartMetas.find(c => c.innerType === innerType.value) || null);
const showSeriesVisualControls = computed(() => ['LINE_TREND', 'AREA_STACK', 'BAR_COMPARE', 'COMBO_CHART'].includes(innerType.value));
const showStandaloneLabelControl = computed(() => ['FUNNEL_CHART', 'SCATTER_BUBBLE', 'HEATMAP_MATRIX', 'SUNBURST_CHART'].includes(innerType.value));
const showSmoothControl = computed(() => ['LINE_TREND', 'AREA_STACK', 'COMBO_CHART', 'SPARKLINE_CARD'].includes(innerType.value));

// 只给面板回显补默认，不立即改写 styleJson；用户第一次修改任一视觉项时才整体持久化，
// 这样旧节点的未知字段和已有标题/刷新配置都不会被覆盖。
const visualDefaults = {
  visualPreset: 'aurora', showLegend: true, showLabels: false, showMarks: true, smooth: true,
  pieShape: 'donut', cardVariant: 'glow', showTrend: false
};
if (styleCfg.visualPreset == null) styleCfg.visualPreset = visualDefaults.visualPreset;
if (showSeriesVisualControls.value) {
  for (const key of ['showLegend', 'showLabels', 'showMarks']) {
    if (styleCfg[key] == null) styleCfg[key] = visualDefaults[key];
  }
}
if (showSmoothControl.value && styleCfg.smooth == null) styleCfg.smooth = visualDefaults.smooth;
if (showStandaloneLabelControl.value && styleCfg.showLabels == null) {
  // 漏斗/旭日的运行时默认显示标签，散点/热力矩阵默认关闭，保持面板与组件一致。
  styleCfg.showLabels = ['FUNNEL_CHART', 'SUNBURST_CHART'].includes(innerType.value);
}
if (innerType.value === 'PIE_SHARE' && styleCfg.pieShape == null) styleCfg.pieShape = visualDefaults.pieShape;
if (innerType.value === 'METRIC_CARD') {
  if (styleCfg.cardVariant == null) styleCfg.cardVariant = visualDefaults.cardVariant;
  if (styleCfg.showTrend == null) styleCfg.showTrend = visualDefaults.showTrend;
}
if (innerType.value === 'SPARKLINE_CARD' && styleCfg.showTrend == null) styleCfg.showTrend = true;
// 数据源联动过滤:needTimeseries → 仅 TIMESERIES;needKinds → 仅对应 source_kind
const screenScope = computed(() => {
  const scope = {};
  const bizLine = readStoreValue(store.bizLine);
  const orgScopeMode = readStoreValue(store.orgScopeMode);
  if (bizLine !== undefined && bizLine !== null && bizLine !== '') scope.bizLine = bizLine;
  if (orgScopeMode !== undefined && orgScopeMode !== null && orgScopeMode !== '') {
    scope.orgScopeMode = orgScopeMode;
  }
  return scope;
});
const filteredDs = computed(() => filterDatasourcesByMeta(datasources.value, chartMeta.value, screenScope.value));
// 先按图表类型计算候选，再与本屏范围求差集；只在交集为空时展示差集，避免改变有可用候选时的原有行为。
const typeCompatibleDs = computed(() => filterDatasourcesByMeta(datasources.value, chartMeta.value));
const scopeBlockedDs = computed(() => {
  if (!datasourcesLoaded.value || filteredDs.value.length || !typeCompatibleDs.value.length) return [];
  const candidateIds = new Set(filteredDs.value.map(d => String(d.id)));
  return typeCompatibleDs.value.filter(d => !candidateIds.has(String(d.id)));
});
const scopeConflict = computed(() => scopeBlockedDs.value.length > 0);
const scopeConflictReason = computed(() => {
  if (isNamedGroupScope.value) return '命名机构组仅允许机构宽表（ORG_INDEX_RESULT / org_code）';
  const bizLine = readStoreValue(store.bizLine);
  if (bizLine) return `本屏业务条线为 ${bizLine}，数据源不在当前范围内`;
  return '数据源不在当前屏范围内';
});
const scopeConflictHint = computed(() =>
  isNamedGroupScope.value ? '需切换为传统上下文' : '不符合当前屏范围');
const scopeConflictAction = computed(() => isNamedGroupScope.value
  ? '请通过顶部“编辑范围”切换为传统上下文，或者换用机构宽表组件。'
  : '请通过顶部“编辑范围”调整当前屏范围，或者换用符合范围的数据源。');
const dsHint = computed(() => {
  const m = chartMeta.value;
  const parts = [];
  if (m?.needTimeseries) parts.push('仅时序型(TIMESERIES)数据源');
  if (Array.isArray(m?.needKinds) && m.needKinds.length) parts.push(`仅 ${m.needKinds.join('/')} 类数据源`);
  if (isNamedGroupScope.value) {
    parts.push('命名机构组仅允许机构宽表（ORG_INDEX_RESULT / org_code）');
  }
  return parts.join('，');
});
const needValueCol = computed(() => innerType.value === 'GAUGE' || innerType.value === 'LIQUID_PROGRESS');
const selectedDatasource = computed(() => datasources.value.find(item => item.id === bind.dsId) || null);
const probeRequiresNamedGroup = computed(() => {
  try { return datasourceTryRunScopeMode(selectedDatasource.value?.configJson) === 'NAMED_GROUP'; }
  catch { return false; }
});

function parse(j) { try { return j ? JSON.parse(j) : {}; } catch { return {}; } }
function readStoreValue(value) { return value?.value ?? value; }
const isNamedGroupScope = computed(() => String(readStoreValue(store.orgScopeMode) || '').toUpperCase() === 'NAMED_GROUP');

function syncBind() {
  // 落 dsType 快照:运行时 BlockContainer 判定"是否响应全屏周期过滤器联动"依赖它
  // (utils/globalPeriod.isTimeseriesBlock;快照缺失的历史区块按时序专属图表类型兜底)。
  // 幂等刷新:数据源列表未加载时保留旧值,不误清
  const d = datasources.value.find(x => x.id === bind.dsId);
  if (d) bind.dsType = d.dsType;
  else if (datasourcesLoaded.value) delete bind.dsType;
  props.element.bindJson = JSON.stringify(bind);
  store.pushSnapshotDebounced();
}
function syncStyle() { props.element.styleJson = JSON.stringify(styleCfg); store.pushSnapshotDebounced(); }
function syncDrill() { props.element.drillJson = JSON.stringify(drill); store.pushSnapshotDebounced(); }
// propValue 是节点上的响应式对象,mutate 即生效;仅需标脏 + 记快照
function syncProp() { store.pushSnapshotDebounced(); }

/**
 * 探测已保存数据源的列名（供数值列下拉）。
 *
 * 设计器没有发布包 block 身份，绝不能借 /screen/data schemaVersion=1 读取列；统一走
 * 已保存数据源的独立高危 probe-columns 端点，并把原因/测试组交给服务端审计与范围校验。
 */
async function probeColumns() {
  colOptions.value = [];
  if (!needValueCol.value || !bind.dsId) return;
  const datasource = selectedDatasource.value;
  if (!datasource) { ElMessage.warning('请先选择已保存的数据源'); return; }
  let body;
  try {
    body = buildDatasourceProbeRequest(datasource, {
      period: bind.period || 'LATEST',
      dateFrom: null,
      dateTo: null,
      contextParams: { orgCode: null, empId: null },
      testOrgGroupCode: probe.testOrgGroupCode,
      reason: probe.reason
    });
  } catch (error) {
    ElMessage.warning(error?.message || '列探测参数不合法');
    return;
  }
  probe.running = true;
  try {
    const d = await probeScreenDatasourceColumns(bind.dsId, body);
    colOptions.value = d?.columns || [];
  } finally {
    probe.running = false;
  }
}
watch(() => bind.dsId, () => {
  // 切换数据源后既不能复用另一个数据源的列，也不能复用另一个范围模式的测试组。
  colOptions.value = [];
  probe.testOrgGroupCode = '';
});

/** 数据源列表/屏范围变化后，清理已不再属于候选集合的历史绑定。 */
function reconcileBinding() {
  // listScreenDatasources 的 GET 失败会以 [] fallback；空目录不等价于“无候选”，
  // 避免暂时不可用/无权限时破坏已有绑定。只有非空目录才执行失效绑定清理。
  if (!datasourcesLoaded.value || !datasources.value.length
      || bind.dsId === undefined || bind.dsId === null || bind.dsId === '') return;
  const stillCandidate = filteredDs.value.some(d => String(d.id) === String(bind.dsId));
  if (stillCandidate) return;

  delete bind.dsId;
  delete bind.dsType;
  props.element.bindJson = JSON.stringify(bind);
  store.pushSnapshotDebounced();
  const message = isNamedGroupScope.value
    ? '当前绑定数据源不符合命名机构组安全范围，已清除绑定；请重新选择机构宽表（ORG_INDEX_RESULT / org_code）。'
    : '当前绑定数据源不符合本屏业务条线或图表类型，已清除绑定；请重新选择候选数据源。';
  ElMessage.warning(message);
}

watch([
  () => readStoreValue(store.bizLine),
  () => readStoreValue(store.orgScopeMode),
  datasources
], reconcileBinding);

onMounted(async () => {
  const result = await listScreenDatasources();
  const rows = Array.isArray(result) ? result : result?.records;
  datasources.value = Array.isArray(rows) ? rows : [];
  datasourcesLoaded.value = true;
  reconcileBinding();
  try {
    const groups = await listOrgGroups({ status: 'ACTIVE', purpose: 'REPORT_SCREEN' });
    const rows = Array.isArray(groups) ? groups : (groups?.records || []);
    probeOrgGroups.value = filterActiveOrgGroups(filterReportScreenOrgGroups(rows));
  } catch { probeOrgGroups.value = []; }
});
</script>
<style scoped>
.attr-hint { width: 100%; font-size: 12px; color: #7d9bc9; line-height: 1.5; margin-top: 2px; }
.probe-box { width: 100%; display: flex; flex-direction: column; gap: 6px; }
.attr-scope-conflict { display: flex; width: 100%; flex-direction: column; gap: 3px; margin-top: 7px;
  padding: 8px 10px; border: 1px solid rgba(229, 154, 145, .34); border-radius: 7px;
  background: rgba(229, 154, 145, .08); color: #c7d5ea; font-size: 12px; line-height: 1.5; }
.attr-scope-conflict strong { color: #f0b2a9; font-weight: 600; }
.attr-scope-conflict ul { margin: 2px 0; padding-left: 18px; color: #e8c979; }
</style>
