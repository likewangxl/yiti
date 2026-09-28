<template>
  <section class="panorama-trend" :class="{ 'is-compact': compact }" :aria-label="title" data-testid="panorama-trend">
    <div class="panorama-panel-heading">
      <h2>{{ title }}</h2>
      <div class="panorama-trend-heading-actions">
        <div v-if="switchable" class="panorama-segmented panorama-trend-switch" role="group" aria-label="趋势指标">
          <button
            type="button"
            data-trend-mode="depositIncrease"
            :class="{ active: metricMode === 'depositIncrease' }"
            :disabled="!canShowDepositIncrease"
            @click="metricMode = 'depositIncrease'"
          >存款净增</button>
          <button
            type="button"
            data-trend-mode="deposit"
            :class="{ active: metricMode === 'deposit' }"
            :disabled="!hasBalance"
            @click="metricMode = 'deposit'"
          >存贷款余额</button>
        </div>
        <span v-if="dataDate" class="panorama-panel-date">{{ dataDate }}</span>
      </div>
    </div>
    <v-chart
      v-if="hasChart"
      class="panorama-trend-chart"
      :option="option"
      autoresize
      aria-label="经营指标趋势图"
    />
    <div v-else class="panorama-empty" data-testid="trend-empty">暂无趋势数据</div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
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
import { defaultTrendMetric, finiteMetric, hasTrendMetric } from './panoramaViewModel.js';

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent, AxisPointerComponent]);

const props = defineProps({
  trend: { type: Array, default: () => [] },
  rows: { type: Array, default: null },
  title: { type: String, default: '主要指标趋势' },
  dataDate: { type: String, default: '' },
  compact: { type: Boolean, default: false },
  switchable: { type: Boolean, default: false },
  amountFriendly: { type: Boolean, default: false },
  series: {
    type: Array,
    default: null
  }
});

const sourceRows = computed(() => (Array.isArray(props.rows) ? props.rows : props.trend));
const metricMode = ref(defaultTrendMetric(sourceRows.value));
const canShowDepositIncrease = computed(() => hasTrendMetric(sourceRows.value, 'depositIncrease'));
const hasBalance = computed(() => hasTrendMetric(sourceRows.value, 'deposit') || hasTrendMetric(sourceRows.value, 'loan'));
watch(sourceRows, rows => {
  if (!hasTrendMetric(rows, metricMode.value)) metricMode.value = defaultTrendMetric(rows);
});

const normalizedSeries = computed(() => {
  const configured = Array.isArray(props.series) && props.series.length ? props.series : null;
  if (!props.switchable) {
    const stableSeries = configured || [
      { key: 'deposit', label: '存款余额', color: '#42e8ef' },
      { key: 'loan', label: '贷款余额', color: '#a77bff' }
    ];
    return stableSeries.map((item, index) => ({
      key: String(item?.key || '').trim(),
      label: String(item?.label || item?.key || '').trim(),
      color: item?.color || (index ? '#a77bff' : '#42e8ef')
    })).filter(item => item.key);
  }

  const defaultDeposit = {
    key: metricMode.value,
    label: metricMode.value === 'depositIncrease' ? '存款净增' : '存款余额',
    color: '#42e8ef'
  };
  const deposit = (configured || []).find(item => item?.key === 'deposit') || defaultDeposit;
  const loan = (configured || []).find(item => item?.key === 'loan') || {
    key: 'loan', label: '贷款余额', color: '#a77bff'
  };
  if (metricMode.value === 'depositIncrease') {
    return [{ key: 'depositIncrease', label: '存款净增', color: deposit.color || '#42e8ef' }];
  }
  return [deposit, loan].map((item, index) => ({
    key: String(item?.key || '').trim(),
    label: String(item?.label || item?.key || '').trim(),
    color: item?.color || (index ? '#a77bff' : '#42e8ef')
  })).filter(item => item.key && hasTrendMetric(sourceRows.value, item.key));
});

const labels = computed(() => sourceRows.value.map(row => String(row?.date ?? row?.label ?? '')));

function finiteValue(value) {
  return finiteMetric(value);
}

function formatPointValue(value) {
  const number = finiteValue(Array.isArray(value) ? value[value.length - 1] : value);
  return number === null ? '' : new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(number);
}

function formatRawValue(value) {
  const number = finiteValue(Array.isArray(value) ? value[value.length - 1] : value);
  return number === null ? '—' : new Intl.NumberFormat('en-US', { maximumFractionDigits: 20 }).format(number);
}

function escapeTooltipText(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[character]));
}

const amountScale = computed(() => {
  const values = optionSeries.value.flatMap(item => item.data)
    .map(value => finiteValue(value))
    .filter(value => value !== null);
  const maxAbs = values.reduce((max, value) => Math.max(max, Math.abs(value)), 0);
  if (maxAbs >= 100000000) return { divisor: 100000000, suffix: '亿' };
  if (maxAbs >= 10000) return { divisor: 10000, suffix: '万' };
  return { divisor: 1, suffix: '' };
});

function formatCompactValue(value) {
  const number = finiteValue(value);
  if (number === null) return '';
  const scaled = number / amountScale.value.divisor;
  const digits = Math.abs(scaled) >= 100 ? 0 : 2;
  return `${Number(scaled.toFixed(digits))}${amountScale.value.suffix}`;
}

function tooltipFormatter(params) {
  const items = Array.isArray(params) ? params : [params];
  const axisLabel = escapeTooltipText(items[0]?.axisValueLabel ?? items[0]?.axisValue ?? '');
  return [axisLabel, ...items.map(item => `${item?.marker || ''}${escapeTooltipText(item?.seriesName || '')}: ${formatRawValue(item?.value)}`)].join('<br/>');
}

const optionSeries = computed(() => normalizedSeries.value.map(item => {
  const pointCount = sourceRows.value.length;
  return {
    name: item.label,
    type: 'line',
    smooth: true,
    connectNulls: false,
    showSymbol: true,
    symbol: 'circle',
    symbolSize: 5,
    label: {
      show: props.amountFriendly ? false : true,
      position: 'top',
      color: item.color,
      fontSize: 10,
      formatter: params => {
        // Keep the chart readable while retaining the reference's visible endpoints.
        const showLabel = pointCount <= 4 || params.dataIndex === 0 || params.dataIndex === pointCount - 1;
        return showLabel ? formatPointValue(params.value) : '';
      }
    },
    lineStyle: { color: item.color, width: 2, shadowBlur: 8, shadowColor: item.color },
    itemStyle: { color: item.color },
    areaStyle: {
      color: {
        type: 'linear',
        x: 0,
        y: 0,
        x2: 0,
        y2: 1,
        colorStops: [
          { offset: 0, color: `${item.color}55` },
          { offset: 1, color: `${item.color}00` }
        ]
      }
    },
    data: sourceRows.value.map(row => finiteValue(row?.[item.key]))
  };
}));

const hasChart = computed(() => labels.value.some(Boolean)
  && optionSeries.value.some(item => item.data.some(value => value !== null)));

const option = computed(() => ({
  animation: true,
  color: normalizedSeries.value.map(item => item.color),
  grid: props.compact
    ? { top: 21, right: 44, bottom: 21, left: 40, containLabel: true }
    : { top: 34, right: 44, bottom: 26, left: 48, containLabel: true },
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'line' },
    backgroundColor: 'rgba(7, 18, 53, .96)',
    borderColor: 'rgba(117, 158, 255, .38)',
    textStyle: { color: '#e8efff', fontSize: 12 },
    ...(props.amountFriendly ? {
      // 使用 body 浮层避开业务卡片 overflow:hidden 对日期和数值的裁剪。
      renderMode: 'html',
      appendTo: 'body',
      confine: true,
      className: 'panorama-trend-tooltip',
      formatter: tooltipFormatter
    } : {})
  },
  legend: {
    show: normalizedSeries.value.length > 1,
    top: 4,
    left: 'center',
    itemWidth: 18,
    itemHeight: 3,
    textStyle: { color: '#9fb2da', fontSize: 11 }
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
    splitNumber: 3,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: {
      color: '#8ea5d2',
      fontSize: 10,
      ...(props.amountFriendly ? { formatter: formatCompactValue } : {})
    },
    splitLine: { lineStyle: { color: 'rgba(104, 143, 217, .12)' } }
  },
  series: optionSeries.value
}));
</script>

<style scoped>
.panorama-trend { display: flex; min-height: 0; flex-direction: column; }
.panorama-trend-chart { width: 100%; height: auto; min-height: 170px; flex: 1 1 auto; }
.panorama-trend.is-compact .panorama-trend-chart { min-height: 170px; }
</style>
