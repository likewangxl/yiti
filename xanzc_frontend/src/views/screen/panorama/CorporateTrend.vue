<template>
  <section
    class="corporate-trend"
    data-testid="corporate-trend"
    :data-point-count="rows.length"
    aria-label="对公授权范围存贷款趋势"
  >
    <header class="corporate-trend__heading">
      <div>
        <span class="corporate-kicker">{{ scopeLabel }}</span>
        <h2>{{ title }}</h2>
      </div>
      <div class="corporate-trend__meta">
        <span class="corporate-trend__legend"><i class="is-cyan"></i>对公存款</span>
        <span class="corporate-trend__legend"><i class="is-violet"></i>对公贷款</span>
        <span v-if="dataDate">{{ dataDate }}</span>
      </div>
    </header>

    <v-chart
      v-if="hasChart"
      class="corporate-trend__chart"
      :option="option"
      autoresize
      aria-label="对公存款与对公贷款趋势图"
    />
    <div v-else class="corporate-empty" data-testid="corporate-trend-empty">暂无趋势数据</div>
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
import {
  SCREEN_CHART_AXIS_FONT_SIZE,
  SCREEN_CHART_FONT_FAMILY,
  SCREEN_CHART_LABEL_FONT_SIZE,
  SCREEN_CHART_LEGEND_FONT_SIZE,
  SCREEN_CHART_TOOLTIP_FONT_SIZE
} from './screenChartTypography.js';

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent, AxisPointerComponent]);

const props = defineProps({
  trend: { type: Array, default: () => [] },
  title: { type: String, default: '对公存贷款趋势' },
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
  { key: 'deposit', name: '对公存款', color: '#47e9ef' },
  { key: 'loan', name: '对公贷款', color: '#a77bff' }
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
  grid: { top: 34, right: 48, bottom: 30, left: 54, containLabel: true },
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'line' },
    backgroundColor: 'rgba(7, 18, 53, .96)',
    borderColor: 'rgba(117, 158, 255, .38)',
    textStyle: { color: '#e8efff', fontFamily: SCREEN_CHART_FONT_FAMILY, fontSize: SCREEN_CHART_TOOLTIP_FONT_SIZE }
  },
  legend: { show: false, data: seriesDefinitions.map(item => item.name), textStyle: { color: '#9fb2da', fontFamily: SCREEN_CHART_FONT_FAMILY, fontSize: SCREEN_CHART_LEGEND_FONT_SIZE } },
  xAxis: {
    type: 'category', boundaryGap: false, data: labels.value,
    axisLine: { lineStyle: { color: 'rgba(130, 165, 235, .24)' } },
    axisTick: { show: false }, axisLabel: { color: '#8ea5d2', fontFamily: SCREEN_CHART_FONT_FAMILY, fontSize: SCREEN_CHART_AXIS_FONT_SIZE }
  },
  yAxis: {
    type: 'value', name: '亿元',
    nameTextStyle: { color: '#7e9bce', fontFamily: SCREEN_CHART_FONT_FAMILY, fontSize: SCREEN_CHART_AXIS_FONT_SIZE, padding: [0, 0, 0, -28] },
    splitNumber: 3, axisLine: { show: false }, axisTick: { show: false },
    axisLabel: { color: '#8ea5d2', fontFamily: SCREEN_CHART_FONT_FAMILY, fontSize: SCREEN_CHART_AXIS_FONT_SIZE },
    splitLine: { lineStyle: { color: 'rgba(104, 143, 217, .12)' } }
  },
  series: series.value.map(item => ({
    ...item,
    label: {
      show: true,
      position: 'top',
      color: item.itemStyle.color,
      fontFamily: SCREEN_CHART_FONT_FAMILY,
      fontSize: SCREEN_CHART_LABEL_FONT_SIZE,
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
