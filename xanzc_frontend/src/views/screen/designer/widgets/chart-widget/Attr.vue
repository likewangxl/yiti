<template>
  <CommonAttr :element="element">
    <el-form-item label="数据源">
      <el-select v-model="bind.dsId" filterable @change="syncBind">
        <el-option v-for="d in filteredDs" :key="d.id" :label="d.dsName" :value="d.id" />
      </el-select>
      <!-- 联动过滤提示：needTimeseries/needKinds 元数据约束（后端另有 RPT-43005 等校验兜底） -->
      <div v-if="dsHint" class="attr-hint">{{ dsHint }}</div>
    </el-form-item>
    <el-form-item label="周期"><el-input v-model="bind.period" placeholder="LATEST / LAST_10D" @input="syncBind" /></el-form-item>

    <!-- 全屏周期过滤器联动豁免(spec §5.3):仅时序数据源会被联动,豁免后维持自身周期 -->
    <el-form-item label="全局周期">
      <el-switch v-model="propValue.ignoreGlobalPeriod" active-text="忽略联动" @change="syncProp" />
      <div class="attr-hint">大屏上的周期过滤器默认联动全部时序(TIMESERIES)图表;开启后本图表维持自身周期不被覆盖</div>
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

    <el-form-item label="标题"><el-input v-model="styleCfg.title" @input="syncStyle" /></el-form-item>
    <el-form-item label="刷新(秒)"><el-input-number v-model="styleCfg.refreshSec" :min="0" @change="syncStyle" /></el-form-item>
    <el-form-item label="钻取"><el-switch v-model="drill.drillEnabled" @change="syncDrill" /></el-form-item>
  </CommonAttr>
</template>
<script setup>
import { computed, reactive, ref, onMounted, watch } from 'vue';
import CommonAttr from '@/views/screen/designer/panels/CommonAttr.vue';
import { listScreenDatasources, queryScreenData } from '@/api/screen';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
import { chartMetas } from '@/views/screen/designer/widgets';
import { filterDatasourcesByMeta } from './dsFilter';
const props = defineProps({ element: { type: Object, required: true } });
const store = useScreenDesignerStore();
const datasources = ref([]);
const colOptions = ref([]);   // 数值列下拉候选（按数据源探测，探测失败仍可 allow-create 手输）
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
// 数据源联动过滤:needTimeseries → 仅 TIMESERIES;needKinds → 仅对应 source_kind
const filteredDs = computed(() => filterDatasourcesByMeta(datasources.value, chartMeta.value));
const dsHint = computed(() => {
  const m = chartMeta.value;
  if (!m) return '';
  const parts = [];
  if (m.needTimeseries) parts.push('仅时序型(TIMESERIES)数据源');
  if (Array.isArray(m.needKinds) && m.needKinds.length) parts.push(`仅 ${m.needKinds.join('/')} 类数据源`);
  return parts.join('，');
});
const needValueCol = computed(() => innerType.value === 'GAUGE' || innerType.value === 'LIQUID_PROGRESS');

function parse(j) { try { return j ? JSON.parse(j) : {}; } catch { return {}; } }
function syncBind() {
  // 落 dsType 快照:运行时 BlockContainer 判定"是否响应全屏周期过滤器联动"依赖它
  // (utils/globalPeriod.isTimeseriesBlock;快照缺失的历史区块按时序专属图表类型兜底)。
  // 幂等刷新:数据源列表未加载时保留旧值,不误清
  const d = datasources.value.find(x => x.id === bind.dsId);
  if (d) bind.dsType = d.dsType;
  props.element.bindJson = JSON.stringify(bind);
  store.pushSnapshotDebounced();
}
function syncStyle() { props.element.styleJson = JSON.stringify(styleCfg); store.pushSnapshotDebounced(); }
function syncDrill() { props.element.drillJson = JSON.stringify(drill); store.pushSnapshotDebounced(); }
// propValue 是节点上的响应式对象,mutate 即生效;仅需标脏 + 记快照
function syncProp() { store.pushSnapshotDebounced(); }

/** 探测所选数据源的列名(供数值列下拉)——best effort:缺上下文参数(43010)等失败时静默清空,仍可手输 */
async function probeColumns() {
  colOptions.value = [];
  if (!needValueCol.value || !bind.dsId) return;
  try {
    const d = await queryScreenData({
      dsId: bind.dsId,
      period: bind.period || 'LATEST',
      contextParams: { orgCode: null, empId: null }
    });
    colOptions.value = d?.columns || [];
  } catch { /* 探测失败不打扰配置流程 */ }
}
watch(() => bind.dsId, probeColumns);
onMounted(async () => {
  datasources.value = await listScreenDatasources();
  probeColumns();
});
</script>
<style scoped>
.attr-hint { width: 100%; font-size: 12px; color: #7d9bc9; line-height: 1.5; margin-top: 2px; }
</style>
