<template>
  <section
    class="retail-trend"
    data-testid="retail-trend"
    :data-point-count="rows.length"
    aria-label="零售授权范围资产趋势"
  >
    <header class="retail-trend__heading">
      <div>
        <span class="retail-kicker">{{ scopeLabel }}</span>
        <h2>{{ title }}</h2>
      </div>
      <div class="retail-trend__meta">
        <span class="retail-trend__legend"><i class="is-cyan"></i>零售AUM</span>
        <span class="retail-trend__legend"><i class="is-violet"></i>储蓄余额</span>
        <span v-if="dataDate">{{ dataDate }}</span>
      </div>
    </header>

    <v-chart
      v-if="hasChart"
      class="retail-trend__chart"
      :option="option"
      autoresize
      aria-label="零售AUM与储蓄余额趋势图"
    />
    <div v-else class="retail-empty" data-testid="retail-trend-empty">暂无趋势数据</div>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';
import {
  AxisPointerComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent
} from 'echarts/components';
import VChart from 'vue-echarts';

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent, AxisPointerComponent]);

const props = defineProps({
  trend: { type: Array, default: () => [] },
  title: { type: String, default: '零售资产趋势' },
  dataDate: { type: String, default: '' },
  scopeLabel: { type: String, default: '全辖经营' }
});

const rows = computed(() => (Array.isArray(props.trend) ? props.trend : [])
  .filter(item => item && typeof item === 'object'));

function finiteValue(value) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

const labels = computed(() => rows.value.map(row => String(row.date ?? row.label ?? '')));
const seriesDefinitions = Object.freeze([
  { key: 'aum', name: '零售AUM', color: '#47e9ef' },
  { key: 'deposit', name: '储蓄余额', color: '#a77bff' }
]);
const series = computed(() => seriesDefinitions.map(item => ({
  name: item.name,
  type: 'line',
  smooth: true,
  connectNulls: false,
  showSymbol: true,
  symbol: 'circle',
  symbolSize: 5,
  itemStyle: { color: item.color },
  lineStyle: { color: item.color, width: 2, shadowBlur: 9, shadowColor: item.color },
  areaStyle: {
    color: {
      type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
      colorStops: [{ offset: 0, color: `${item.color}40` }, { offset: 1, color: `${item.color}00` }]
    }
  },
  data: rows.value.map(row => finiteValue(row[item.key]))
})));

const hasChart = computed(() => labels.value.some(Boolean)
  && series.value.some(item => item.data.some(value => value !== null)));

const option = computed(() => ({
  animation: true,
  color: seriesDefinitions.map(item => item.color),
  grid: { top: 29, right: 26, bottom: 26, left: 46, containLabel: true },
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'line' },
    backgroundColor: 'rgba(7, 18, 53, .96)',
    borderColor: 'rgba(117, 158, 255, .38)',
    textStyle: { color: '#e8efff', fontSize: 12 }
  },
  legend: {
    show: false,
    data: seriesDefinitions.map(item => item.name)
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: labels.value,
    axisLine: { lineStyle: { color: 'rgba(130, 165, 235, .24)' } },
    axisTick: { show: false },
    axisLabel: { color: '#8ea5d2', fontSize: 10 }
  },
  yAxis: {
    type: 'value',
    name: '亿元',
    nameTextStyle: { color: '#7e9bce', fontSize: 10, padding: [0, 0, 0, -28] },
    splitNumber: 3,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: '#8ea5d2', fontSize: 10 },
    splitLine: { lineStyle: { color: 'rgba(104, 143, 217, .12)' } }
  },
  series: series.value.map(item => ({
    ...item,
    label: {
      show: true,
      position: 'top',
      color: item.itemStyle.color,
      fontSize: 10,
      formatter: params => {
        const index = Number(params?.dataIndex);
        if (rows.value.length > 4 && index !== 0 && index !== rows.value.length - 1) return '';
        const value = finiteValue(params?.value);
        return value === null ? '' : Number(value).toFixed(2);
      }
    }
  }))
}));
</script>

<style scoped>
.retail-trend { display: flex; min-height: 0; flex-direction: column; }
.retail-trend__heading { display: flex; align-items: center; justify-content: space-between; gap: 14px; min-height: 52px; padding: 10px 15px; border-bottom: 1px solid rgba(121, 161, 248, .17); }
.retail-trend__heading h2 { margin: 4px 0 0; color: #eef4ff; font-size: 18px; }
.retail-trend__meta { display: flex; align-items: center; flex-wrap: wrap; justify-content: flex-end; gap: 11px; color: #8fa9db; font-size: 10px; white-space: nowrap; }
.retail-trend__legend { display: inline-flex; align-items: center; gap: 5px; }
.retail-trend__legend i { width: 16px; height: 3px; display: inline-block; border-radius: 2px; background: #47e9ef; }
.retail-trend__legend i.is-violet { background: #a77bff; }
.retail-trend__chart { width: 100%; min-height: 205px; flex: 1 1 auto; }
.retail-empty { display: grid; min-height: 205px; place-items: center; color: #8fa9db; font-size: 12px; }
@media (max-width: 760px) {
  .retail-trend__heading { align-items: flex-start; flex-direction: column; gap: 7px; }
  .retail-trend__meta { justify-content: flex-start; white-space: normal; }
}
</style>
