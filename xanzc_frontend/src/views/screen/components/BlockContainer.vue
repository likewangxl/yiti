<template>
  <div class="scr-block">
    <div class="scr-block-h">
      <span>{{ drillItem ? `${drillItem.label} · 趋势钻取` : (styleCfg.title || '未命名区块') }}</span>
      <span v-if="drillItem" style="cursor:pointer;font-size:13px" @click="drillItem = null">‹ 返回</span>
    </div>
    <div class="scr-block-body" v-loading="loading" element-loading-background="rgba(5,14,43,.6)">
      <div v-if="error" class="scr-block-err">{{ error }}</div>
      <div v-else-if="guide" class="scr-block-guide">{{ guide }}</div>
      <DrillTrend v-else-if="drillItem" :bind="bind" :context="context"
                  :item="drillItem" :periods="drill.drillPeriods || ['LAST_10D']" />
      <component v-else-if="data" :is="componentMap[block.componentType]"
                 :columns="data.columns" :rows="data.rows"
                 :bind="bind" :style-cfg="styleCfg" @item-click="onItemClick" />
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { queryScreenData } from '@/api/screen';
import MetricCard from './MetricCard.vue';
import LineTrend from './LineTrend.vue';
import PieShare from './PieShare.vue';
import RankList from './RankList.vue';
import FlowStatus from './FlowStatus.vue';
import DrillTrend from './DrillTrend.vue';

const props = defineProps({
  block: { type: Object, required: true },
  context: { type: Object, default: () => ({}) }
});

const router = useRouter();
const componentMap = {
  METRIC_CARD: MetricCard,
  LINE_TREND: LineTrend,
  PIE_SHARE: PieShare,
  RANK_LIST: RankList,
  FLOW_STATUS: FlowStatus
};

function parse(json, fallback = {}) {
  try { return json ? JSON.parse(json) : fallback; } catch { return fallback; }
}
const bind = computed(() => parse(props.block.bindJson));
const styleCfg = computed(() => parse(props.block.styleJson));
const drill = computed(() => parse(props.block.drillJson));

const data = ref(null);
const loading = ref(false);
const error = ref('');    // 真错误（红字）：周期非法 43011 / 执行失败 43008 / 其他
const guide = ref('');    // 引导态（非报错）：缺必填上下文参数 43010，提示补 orgCode/empId
const drillItem = ref(null); // { col, label } —— 非空即钻取态

async function load() {
  // FIX-4: 未选数据源（新建区块 bindJson='{}' → dsId undefined）时不发请求。
  // 否则 dsId 缺失会被后端 @NotNull 拦成 400，用户看到与业务无关的"请求失败(400)"；直接渲染引导占位。
  if (!bind.value.dsId) {
    loading.value = false;
    error.value = '';
    data.value = null;
    guide.value = '请先选择数据源';
    return;
  }
  loading.value = true;
  error.value = '';
  guide.value = '';
  try {
    data.value = await queryScreenData({
      dsId: bind.value.dsId,
      period: bind.value.period || 'LATEST',
      contextParams: { orgCode: props.context.orgCode || null, empId: props.context.empId || null }
    });
  } catch (e) {
    // 缺必填上下文参数（RPT-43010）不是真错误，是"还没给取数条件"，渲染引导占位而非红字报错；
    // 周期非法(43011)/SQL 执行失败(43008)/其他仍按错误态红字展示
    if (e?.code === 'RPT-43010') {
      data.value = null;
      guide.value = '请提供 orgCode / empId 后查看数据';
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
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.6;
  color: #7f9bc4;
  background: rgba(64, 158, 255, .05);
  border: 1px dashed rgba(96, 148, 214, .38);
  border-radius: 6px;
}
</style>
