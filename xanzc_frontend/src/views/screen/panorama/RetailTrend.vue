<template>
  <section
    class="retail-trend"
    data-testid="retail-trend"
    :data-point-count="rows.length"
    aria-label="零售授权范围存款趋势"
  >
    <header class="retail-trend__heading">
      <div>
        <span class="retail-kicker">{{ scopeLabel }}</span>
        <h2>{{ title }}</h2>
      </div>
      <div class="retail-trend__meta">
        <span v-for="item in activeSeriesDefinitions" :key="item.key" class="retail-trend__legend"><i :style="{ backgroundColor: item.color }"></i>{{ item.name }}</span>
        <span v-if="dataDate">数据日期 {{ dataDate }}</span>
      </div>
    </header>

    <v-chart
      v-if="hasChart"
      class="retail-trend__chart"
      :option="option"
      autoresize
      :aria-label="chartAriaLabel"
    />
    <div v-else class="retail-empty" data-testid="retail-trend-empty">暂无趋势数据</div>

    <div v-if="hasTrendSummary" class="retail-trend-summary" data-testid="retail-trend-summary" aria-label="存款趋势观察摘要">
      <span class="retail-trend-summary__range">{{ trendSummary.observationLabel }}</span>
      <span>区间变动 <strong :class="changeClass">{{ formatScaled(trendSummary.depositChange) }}</strong> {{ unit }}</span>
      <span v-if="trendSummary.peak">峰值 <strong>{{ formatScaled(trendSummary.peak.value) }}</strong> {{ unit }} · {{ trendSummary.peak.date || '日期待确认' }}</span>
      <span v-if="trendSummary.trough">谷值 <strong>{{ formatScaled(trendSummary.trough.value) }}</strong> {{ unit }} · {{ trendSummary.trough.date || '日期待确认' }}</span>
    </div>
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
import { buildTrendSummary, finiteMetric } from './retailDisplayInsights.js';
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
  title: { type: String, default: '零售存款余额与月日均趋势' },
  dataDate: { type: String, default: '' },
  scopeLabel: { type: String, default: '当前授权范围' }
});

const rows = computed(() => (Array.isArray(props.trend) ? props.trend : [])
  .filter(item => item && typeof item === 'object'));

function labelsFor(row) {
  return String(row.date ?? row.label ?? row.dataDate ?? row.periodDate ?? '');
}

const labels = computed(() => rows.value.map(labelsFor));
const seriesDefinitions = Object.freeze([
  { key: 'aum', name: '零售AUM', color: '#47e9ef' },
  { key: 'deposit', name: '零售一般性存款余额', color: '#a77bff' },
  { key: 'depositAverage', name: '零售存款月日均', color: '#ffc45e' }
]);
const isDepositMode = computed(() => rows.value.some(row => Object.prototype.hasOwnProperty.call(row, 'depositAverage')
  || Object.prototype.hasOwnProperty.call(row, 'average')));
const activeSeriesDefinitions = computed(() => {
  const source = isDepositMode.value
    ? seriesDefinitions.filter(item => item.key !== 'aum')
    : seriesDefinitions.filter(item => item.key !== 'depositAverage');
  return source.filter(item => rows.value.some(row => finiteMetric(row[item.key]) !== null));
});
const chartValues = computed(() => activeSeriesDefinitions.value.flatMap(item => rows.value
  .map(row => finiteMetric(row[item.key]))
  .filter(value => value !== null)));
const unit = computed(() => {
  const max = chartValues.value.length ? Math.max(...chartValues.value.map(value => Math.abs(value))) : 0;
  return max > 0 && max < 1 ? '万元' : '亿元';
});
const scale = computed(() => unit.value === '万元' ? 10000 : 1);
const scaledValue = value => {
  const number = finiteMetric(value);
  return number === null ? null : number * scale.value;
};
const formatScaled = value => {
  const number = scaledValue(value);
  if (number === null) return '—';
  return new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(number);
};
const trendSummary = computed(() => buildTrendSummary(rows.value));
const hasTrendSummary = computed(() => Boolean(trendSummary.value?.peak));
const changeClass = computed(() => {
  const change = finiteMetric(trendSummary.value?.depositChange);
  return change === null ? '' : (change < 0 ? 'is-down' : 'is-up');
});
const chartAriaLabel = computed(() => `${activeSeriesDefinitions.value.map(item => item.name).join('与') || '零售存款'}趋势图，单位${unit.value}`);
const series = computed(() => activeSeriesDefinitions.value.map(item => ({
  name: item.name,
  type: 'line',
  smooth: false,
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
  data: rows.value.map(row => scaledValue(row[item.key]))
})));

const hasChart = computed(() => labels.value.some(Boolean)
  && series.value.some(item => item.data.some(value => value !== null)));

const option = computed(() => ({
  animation: true,
  color: activeSeriesDefinitions.value.map(item => item.color),
  grid: { top: 34, right: 48, bottom: 30, left: 54, containLabel: true },
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'line' },
    backgroundColor: 'rgba(7, 18, 53, .96)',
    borderColor: 'rgba(117, 158, 255, .38)',
    textStyle: { color: '#e8efff', fontFamily: SCREEN_CHART_FONT_FAMILY, fontSize: SCREEN_CHART_TOOLTIP_FONT_SIZE },
    formatter: params => {
      const items = Array.isArray(params) ? params : [params];
      const date = items[0]?.axisValue || '';
      return [date, ...items.map(item => `${item.seriesName}：${item.value == null ? '—' : Number(item.value).toFixed(2)} ${unit.value}`)].join('<br/>');
    }
  },
  legend: {
    show: false,
    data: activeSeriesDefinitions.value.map(item => item.name),
    textStyle: { color: '#9fb2da', fontFamily: SCREEN_CHART_FONT_FAMILY, fontSize: SCREEN_CHART_LEGEND_FONT_SIZE }
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: labels.value,
    axisLine: { lineStyle: { color: 'rgba(130, 165, 235, .24)' } },
    axisTick: { show: false },
    axisLabel: { color: '#8ea5d2', fontFamily: SCREEN_CHART_FONT_FAMILY, fontSize: SCREEN_CHART_AXIS_FONT_SIZE }
  },
  yAxis: {
    type: 'value',
    name: unit.value,
    nameTextStyle: { color: '#7e9bce', fontFamily: SCREEN_CHART_FONT_FAMILY, fontSize: SCREEN_CHART_AXIS_FONT_SIZE, padding: [0, 0, 0, -28] },
    splitNumber: 3,
    axisLine: { show: false },
    axisTick: { show: false },
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
        const value = finiteMetric(params?.value);
        return value === null ? '' : value.toFixed(2);
      }
    }
  }))
}));
</script>

<style scoped>
.retail-trend { display: flex; min-height: 0; flex-direction: column; }
.retail-trend__heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex: 0 0 auto; min-height: 48px; padding: 10px 15px; border-bottom: 1px solid rgba(121, 161, 248, .17); }
.retail-trend__heading h2 { margin: 4px 0 0; color: #eef4ff; font-size: 18px; line-height: 1.1; }
.retail-trend__meta { display: flex; align-items: center; flex-wrap: wrap; justify-content: flex-end; gap: 11px; color: #8fa9db; font-size: 10px; white-space: nowrap; }
.retail-trend__legend { display: inline-flex; align-items: center; gap: 5px; }
.retail-trend__legend i { width: 16px; height: 3px; display: inline-block; border-radius: 2px; background: #47e9ef; }
.retail-trend__chart { width: 100%; height: 0; min-width: 0; min-height: 0; flex: 1 1 auto; }
.retail-trend-summary { display: flex; align-items: center; flex-wrap: wrap; gap: 6px 14px; flex: 0 0 auto; padding: 6px 14px 8px; border-top: 1px solid rgba(121, 161, 248, .14); color: #88a4d3; font-size: 9px; line-height: 1.4; }
.retail-trend-summary__range { color: #bad0f5; }
.retail-trend-summary strong { color: #dce8ff; font-size: 10px; }
.retail-trend-summary strong.is-up { color: #58e4b5; }
.retail-trend-summary strong.is-down { color: #ff7486; }
.retail-empty { display: grid; min-height: 0; flex: 1 1 auto; place-items: center; color: #8fa9db; font-size: 12px; }
@media (min-width: 1100px) and (max-height: 900px) {
  .retail-trend__heading { padding: 6px 10px; min-height: 44px; }
  .retail-trend__heading h2 { font-size: 15px; }
  .retail-trend__meta { gap: 6px; font-size: 9px; }
  .retail-trend-summary { padding: 4px 10px 6px; font-size: 8px; }
}
@media (max-width: 1099px) {
  .retail-trend__chart, .retail-empty { min-height: 205px; }
}
@media (max-width: 760px) {
  .retail-trend__heading { align-items: flex-start; flex-direction: column; gap: 7px; }
  .retail-trend__meta { justify-content: flex-start; white-space: normal; }
}
</style>
