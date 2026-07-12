<template>
  <div class="dt-wrap">
    <!-- 未勾选任何钻取周期时不渲染空 tab 栏，避免出现无切换入口的空白条（fe Minor-7） -->
    <div v-if="periods && periods.length" class="dt-tabs">
      <span v-for="p in periods" :key="p" class="dt-tab" :class="{ on: p === period }"
            @click="switchPeriod(p)">{{ PERIOD_LABELS[p] || p }}</span>
    </div>
    <div class="dt-chart-wrap" v-loading="loading" element-loading-background="rgba(5,14,43,.6)">
      <div v-if="error" class="scr-block-err">{{ error }}</div>
      <v-chart v-else class="dt-chart" :option="option" autoresize />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { queryScreenData } from '@/api/screen';
import { SCR_COLOR, scrAxisLabel, scrAxisLine, scrSplitLine, scrTooltipStyle } from '@/styles/screenChartTheme';

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent]);

const props = defineProps({
  bind: { type: Object, required: true },      // 原区块 bind（含 dsId）
  context: { type: Object, default: () => ({}) },
  item: { type: Object, required: true },       // { col, label } 被钻取指标
  periods: { type: Array, default: () => ['LAST_10D'] }
});

const PERIOD_LABELS = { LAST_10D: '近10天', LAST_1M: '近1个月', LAST_6M_EOM: '近6个月末', LATEST: '最新' };

const period = ref(props.periods[0] || 'LAST_10D');
const data = ref(null);
const loading = ref(false);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    data.value = await queryScreenData({
      dsId: props.bind.dsId,
      period: period.value,
      contextParams: { orgCode: props.context.orgCode || null, empId: props.context.empId || null }
    });
  } catch (e) {
    error.value = e?.message || '取数失败';
  } finally {
    loading.value = false;
  }
}
function switchPeriod(p) { period.value = p; load(); }
onMounted(load);

const option = computed(() => {
  const cols = data.value?.columns || [];
  const rows = data.value?.rows || [];
  const idx = cols.indexOf(props.item.col);
  return {
    grid: { top: 20, right: 16, bottom: 26, left: 56 },
    tooltip: { trigger: 'axis', ...scrTooltipStyle() },
    xAxis: { type: 'category', data: rows.map(r => r[0]),
             axisLabel: scrAxisLabel(), axisLine: scrAxisLine() },
    yAxis: { type: 'value', axisLabel: scrAxisLabel(),
             splitLine: scrSplitLine() },
    series: [{
      name: props.item.label, type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
      lineStyle: { color: SCR_COLOR.cyan, width: 2 }, itemStyle: { color: SCR_COLOR.cyan },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [
        { offset: 0, color: 'rgba(0,229,255,.35)' }, { offset: 1, color: 'rgba(0,229,255,0)' }] } },
      data: rows.map(r => (idx >= 0 ? r[idx] : null))
    }]
  };
});
</script>

<style lang="scss" scoped>
.dt-wrap { height: 100%; display: flex; flex-direction: column; }
.dt-tabs { display: flex; gap: 8px; flex: none; padding-bottom: 4px; }
.dt-tab { font-size: 13px; color: var(--scr-text-dim); cursor: pointer; padding: 2px 10px;
  border: 1px solid transparent; border-radius: 10px;
  &.on { color: var(--scr-cyan); border-color: var(--scr-border); } }
.dt-chart-wrap { flex: 1; min-height: 0; position: relative; }
.dt-chart { width: 100%; height: 100%; }
</style>
