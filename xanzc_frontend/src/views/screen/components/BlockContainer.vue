<template>
  <div class="scr-block">
    <div class="scr-block-h">
      <span>{{ drillItem ? `${drillItem.label} · 趋势钻取` : (styleCfg.title || '未命名区块') }}</span>
      <span v-if="drillItem" style="cursor:pointer;font-size:13px" @click="drillItem = null">‹ 返回</span>
    </div>
    <div class="scr-block-body" v-loading="loading" element-loading-background="rgba(5,14,43,.6)">
      <div v-if="error" class="scr-block-err">{{ error }}</div>
      <div v-else-if="guide" class="scr-block-guide">
        <el-icon class="scr-block-guide-icon"><InfoFilled /></el-icon>
        <span>{{ guide }}</span>
      </div>
      <DrillTrend v-else-if="drillItem" :bind="bind"
                  :context="{ ...context, blockId: block.id ?? block.blockId }"
                  :item="drillItem" :periods="drill.drillPeriods || ['LAST_10D']" />
      <component v-else-if="data" :is="componentMap[block.componentType]"
                 :columns="data.columns" :rows="data.rows"
                 :bind="bind" :style-cfg="styleCfg" v-bind="extraProps" @item-click="onItemClick" />
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { InfoFilled } from '@element-plus/icons-vue';
import { queryScreenData } from '@/api/screen';
import { buildScreenDataRequest } from '@/utils/screenScope';
import { GLOBAL_PERIOD_INJECT_KEY, resolveBlockPeriod, shouldApplyGlobalPeriod } from '@/utils/globalPeriod';
import MetricCard from './MetricCard.vue';
import LineTrend from './LineTrend.vue';
import PieShare from './PieShare.vue';
import RankList from './RankList.vue';
import FlowStatus from './FlowStatus.vue';
import DrillTrend from './DrillTrend.vue';
import BarCompare from './BarCompare.vue';
import AreaStack from './AreaStack.vue';
import GaugeDial from './GaugeDial.vue';
import TableList from './TableList.vue';
import KpiDetailTable from './KpiDetailTable.vue';
import KpiRadar from './KpiRadar.vue';
import LiquidProgress from './LiquidProgress.vue';
import ProgressList from './ProgressList.vue';

const props = defineProps({
  block: { type: Object, required: true },
  context: { type: Object, default: () => ({}) },
  // ChartWidget 组件节点的 propValue（barMode/carousel/valueField 等图表形态配置）——
  // 设计态由 chart-widget/Component.vue、运行态由 ScreenRenderer 透传；旧 5 类图表不消费
  propValue: { type: Object, default: () => ({}) }
});

const router = useRouter();
const componentMap = {
  METRIC_CARD: MetricCard,
  LINE_TREND: LineTrend,
  PIE_SHARE: PieShare,
  RANK_LIST: RankList,
  FLOW_STATUS: FlowStatus,
  BAR_COMPARE: BarCompare,
  AREA_STACK: AreaStack,
  GAUGE: GaugeDial,
  TABLE_LIST: TableList,
  KPI_DETAIL_TABLE: KpiDetailTable,
  KPI_RADAR: KpiRadar,
  LIQUID_PROGRESS: LiquidProgress,
  PROGRESS_LIST: ProgressList
};
// 新一代图表额外消费 propValue + columnsMeta（/api/screen/data 可选扩展字段，缺失容错）；
// 旧 5 类图表不声明这两个 props，避免对象透传落成 DOM attribute，按类型白名单条件绑定
const EXTENDED_TYPES = new Set([
  'BAR_COMPARE', 'AREA_STACK', 'GAUGE', 'TABLE_LIST',
  'KPI_DETAIL_TABLE', 'KPI_RADAR', 'LIQUID_PROGRESS', 'PROGRESS_LIST'
]);

function parse(json, fallback = {}) {
  try { return json ? JSON.parse(json) : fallback; } catch { return fallback; }
}
const bind = computed(() => parse(props.block.bindJson));
const styleCfg = computed(() => parse(props.block.styleJson));
const drill = computed(() => parse(props.block.drillJson));

const data = ref(null);
// 扩展 props 仅对新一代图表下发（columnsMeta 来自 /api/screen/data 响应可选字段，后端未上线时为 null）
const extraProps = computed(() =>
  EXTENDED_TYPES.has(props.block.componentType)
    ? { propValue: props.propValue || {}, columnsMeta: data.value?.columnsMeta || null }
    : {});
const loading = ref(false);
const error = ref('');    // 真错误（红字）：周期非法 43011 / 执行失败 43008 / 其他
const guide = ref('');    // 引导态（非报错）：缺必填上下文参数 43010，提示补 orgCode/empId
const drillItem = ref(null); // { col, label } —— 非空即钻取态

// 全屏周期过滤器联动(spec 2026-07-17 §5.3):ScreenView provide 的 screen 级响应式周期。
// 设计器/独立预览未 provide → 兜底 null,取数行为与现状完全一致(零联动)。
const globalPeriod = inject(GLOBAL_PERIOD_INJECT_KEY, null);
/** 联动判定入参快照(是否响应/最终周期两处共用,判定逻辑全在 utils/globalPeriod 纯函数) */
function periodCtx() {
  return {
    globalPeriod: globalPeriod ? globalPeriod.value : null,
    bind: bind.value,
    propValue: props.propValue,
    componentType: props.block.componentType
  };
}

async function load() {
  // FIX-4: 未选数据源（新建区块 bindJson='{}' → dsId undefined）时不发请求。
  // 否则 dsId 缺失会被后端 @NotNull 拦成 400，用户看到与业务无关的"请求失败(400)"；直接渲染引导占位。
  // 服务端响应协议值必须保持原生 JSON 整数；这里禁止 Number('2') 这类宽松转换，
  // 否则脏响应会被误当成可信 v2 发布包继续取数。
  const schemaVersion = props.context?.schemaVersion ?? props.context?.runtimeSchemaVersion ?? 1;
  const blockId = props.block.id ?? props.block.blockId;
  if (![1, 2].includes(schemaVersion)) {
    loading.value = false;
    data.value = null;
    guide.value = '';
    error.value = '大屏运行协议版本不受支持，已拒绝取数';
    return;
  }
  if (schemaVersion < 2 && !bind.value.dsId) {
    loading.value = false;
    error.value = '';
    data.value = null;
    guide.value = '请先选择数据源';
    return;
  }
  if (schemaVersion === 2 && (!String(props.context?.screenCode || '').trim()
    || !Number.isSafeInteger(blockId) || blockId <= 0)) {
    loading.value = false;
    data.value = null;
    guide.value = '';
    error.value = '大屏运行协议缺少 screenCode 或 blockId，已拒绝取数';
    return;
  }
  loading.value = true;
  error.value = '';
  guide.value = '';
  try {
    data.value = await queryScreenData(buildScreenDataRequest({
      schemaVersion,
      screenCode: props.context.screenCode,
      blockId,
      dsId: bind.value.dsId,
      // 时序数据源且未豁免时被全局周期覆盖,否则维持自身 bind.period(缺省 LATEST,现状不变)
      period: resolveBlockPeriod(periodCtx()),
      dateFrom: props.context.dateFrom,
      dateTo: props.context.dateTo,
      contextParams: { orgCode: props.context.orgCode || null, empId: props.context.empId || null }
    }));
  } catch (e) {
    // 缺必填上下文参数（RPT-43010）不是真错误，是"还没给取数条件"，渲染引导占位而非红字报错；
    // 周期非法(43011)/SQL 执行失败(43008)/其他仍按错误态红字展示
    if (e?.code === 'RPT-43010') {
      data.value = null;
      guide.value = '请提供 orgCode / empId 后查看数据';
    } else if (e?.code === 'RPT-43023') {
      // v1 只能使用当前可信已发布 bindSnapshots；缺失时严禁根据 dsId 自动降级探测。
      data.value = null;
      guide.value = '';
      error.value = '当前发布绑定快照不可用，需受控迁移或重新发布';
    } else {
      error.value = e?.message || '取数失败';
    }
  } finally {
    loading.value = false;
  }
}

// 点击数据项：优先区块内钻取（时序数据源），否则按 jump 配置跳屏
function onItemClick({ col, label, row }) {
  if (drill.value.drillEnabled) {
    drillItem.value = { col, label };
    return;
  }
  const jump = drill.value.jump;
  if (!jump?.targetScreenCode) return;
  const query = {};
  for (const [k, v] of Object.entries(jump.params || {})) {
    if (typeof v === 'string' && v.startsWith('$col:')) query[k] = row?.[v.slice(5)];
    else if (typeof v === 'string' && v.startsWith('$ctx:')) query[k] = props.context[v.slice(5)];
    else query[k] = v;
  }
  router.push({ path: `/screen/${jump.targetScreenCode}`, query });
}

// 全局周期变化 → 仅"应响应联动"的区块重新取数(SINGLE 数据源/显式豁免组件不动);
// 未 provide(设计器态)时 globalPeriod 为 null,不注册 watch,零行为差异
if (globalPeriod) {
  watch(globalPeriod, val => {
    const currentSchema = props.context?.schemaVersion ?? props.context?.runtimeSchemaVersion ?? 1;
    if (![1, 2].includes(currentSchema)) return; // 协议非法时保持 Fail Close，不因筛选器再次尝试。
    if (currentSchema === 1 && !bind.value.dsId) return; // 未绑数据源的引导态区块无数可取
    if (shouldApplyGlobalPeriod({ ...periodCtx(), globalPeriod: val })) load();
  });
}

// 区块级轮询（refreshSec，0=不刷新；页面隐藏时跳过）
let timer = null;
onMounted(() => {
  load();
  const sec = Number(styleCfg.value.refreshSec ?? 60);
  if (sec > 0) {
    timer = setInterval(() => { if (!document.hidden && !drillItem.value) load(); }, sec * 1000);
  }
});
onBeforeUnmount(() => { if (timer) clearInterval(timer); });
</script>

<style scoped>
/* 引导占位态（缺必填上下文参数）——柔和青灰、非红字，与 .scr-block-err 错误态视觉区分；
   scoped 随组件 chunk 生效，设计器预览与全屏大屏两个入口均可读，不依赖 screen.scss 是否被引入 */
.scr-block-guide {
  height: 100%;
  min-height: 48px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  text-align: center;
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.6;
  color: #7f9bc4;
  background: rgba(64, 158, 255, .05);
  border: 1px dashed rgba(96, 148, 214, .38);
  border-radius: 6px;
}
.scr-block-guide-icon { font-size: 18px; color: #5c8fd6; opacity: .85; }
</style>
