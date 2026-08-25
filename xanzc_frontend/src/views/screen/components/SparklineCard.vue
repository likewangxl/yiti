<template>
  <div v-if="points.length" class="sp-card" role="button" tabindex="0" :style="cardStyle"
       @click="onCardClick" @keydown.enter.prevent="onCardKeydown" @keydown.space.prevent="onCardKeydown">
    <div class="sp-head">
      <span class="sp-title">{{ styleCfg.title || valueLabel }}</span>
      <span class="sp-date">{{ latest?.label || '—' }}</span>
    </div>
    <div class="sp-value" data-testid="sparkline-value">{{ latest ? fmtNum(latest.value, decimals) : '—' }}</div>
    <div v-if="change != null" class="sp-change" data-testid="sparkline-change"
         :class="change >= 0 ? 'is-up' : 'is-down'"
         :aria-label="`较上期${change >= 0 ? '上升' : '下降'}${Math.abs(change).toFixed(1)}%`">
      <span aria-hidden="true">{{ change >= 0 ? '↗' : '↘' }}</span> {{ Math.abs(change).toFixed(1) }}%
      <small>较上期</small>
    </div>
    <!-- 卡片统一处理点击；不再叠加 ECharts click listener，避免一次点击触发两次 item-click。 -->
    <v-chart class="sp-chart" :option="option" autoresize />
  </div>
  <div v-else class="scr-block-empty" data-testid="sparkline-empty"><span>暂无可绘制的趋势数据</span></div>
</template>

<script setup>
// 迷你趋势卡：时序数据源的日期列 + 首个数值列，始终只依据响应行计算最新值。
// 较上期在前值为 0/非数值时隐藏，避免 Infinity/NaN 进入用户界面。
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { resolveChartTheme, scrAxisLabel, scrTooltipStyle, scrWithAlpha } from '@/styles/screenChartTheme';
import { displayName, fmtNum } from './utils/chartData';
import { columnIndex, finiteNumber, numericColumnIndexes } from './utils/advancedChartData';

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent]);

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) },
  propValue: { type: Object, default: () => ({}) },
  columnsMeta: { type: Array, default: null }
});
const emit = defineEmits(['item-click']);
const theme = computed(() => resolveChartTheme(props.styleCfg));
const palette = computed(() => props.styleCfg.colors?.length ? props.styleCfg.colors : theme.value.palette);
const cardStyle = computed(() => {
  const tokens = theme.value.tokens;
  return {
    '--sp-accent': tokens.accent,
    '--sp-accent-strong': tokens.accentStrong,
    '--sp-text': tokens.text,
    '--sp-text-dim': tokens.textDim,
    '--sp-number': tokens.number,
    '--sp-border': tokens.border,
    '--sp-up': tokens.up,
    '--sp-down': tokens.down,
    '--sp-card-background': `linear-gradient(145deg, ${scrWithAlpha(tokens.accent, .22)}, ${tokens.bgDeep})`
  };
});
const dateIndex = computed(() => columnIndex(props.columns, props.bind.dateCol, 0));
const valueIndex = computed(() => {
  const explicit = columnIndex(props.columns, props.bind.valueCol);
  if (explicit >= 0) return explicit;
  return numericColumnIndexes(props.columns, props.rows, [dateIndex.value])[0] ?? -1;
});
const points = computed(() => (Array.isArray(props.rows) ? props.rows : [])
  .map(row => ({ label: String(row?.[dateIndex.value] ?? ''), value: finiteNumber(row?.[valueIndex.value]) }))
  .filter(point => point.label && point.value != null));
const latest = computed(() => points.value[points.value.length - 1] || null);
const previous = computed(() => points.value.length > 1 ? points.value[points.value.length - 2] : null);
const change = computed(() => {
  if (props.styleCfg.showTrend === false || !latest.value || !previous.value || previous.value.value === 0) return null;
  const result = ((latest.value.value - previous.value.value) / Math.abs(previous.value.value)) * 100;
  return Number.isFinite(result) ? result : null;
});
const decimals = computed(() => {
  const value = props.columns[valueIndex.value];
  const meta = Array.isArray(props.columnsMeta) ? props.columnsMeta.find(item => item?.col === value) : null;
  return Number.isInteger(meta?.decimals) ? meta.decimals : 2;
});
const valueLabel = computed(() => displayName(props.columns[valueIndex.value], props.columnsMeta));

const option = computed(() => ({
  animation: true,
  grid: { top: 4, right: 4, bottom: 4, left: 4 },
  tooltip: { trigger: 'axis', ...scrTooltipStyle(theme.value), axisPointer: { type: 'line' } },
  xAxis: { type: 'category', show: false, data: points.value.map(point => point.label) },
  yAxis: { type: 'value', show: false, scale: true },
  series: [{
    type: 'line', name: valueLabel.value, data: points.value.map(point => point.value),
    smooth: props.styleCfg.smooth !== false, showSymbol: false,
    lineStyle: { width: 2, color: palette.value[0], shadowBlur: 8, shadowColor: scrWithAlpha(palette.value[0], .55) },
    itemStyle: { color: palette.value[0] },
    areaStyle: { color: {
      type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
      colorStops: [{ offset: 0, color: scrWithAlpha(palette.value[0], .34) }, { offset: 1, color: scrWithAlpha(palette.value[0], .02) }]
    } },
    emphasis: { focus: 'series', scale: true }
  }]
}));

function onCardClick(point) {
  const index = point?.dataIndex ?? points.value.length - 1;
  const source = props.rows.find(row => String(row?.[dateIndex.value] ?? '') === points.value[index]?.label);
  const row = {};
  props.columns.forEach((column, columnPosition) => { row[column] = source?.[columnPosition]; });
  emit('item-click', { col: props.columns[valueIndex.value], label: points.value[index]?.label || '', row });
}

function onCardKeydown() {
  onCardClick();
}
</script>

<style scoped>
.sp-card { width: 100%; height: 100%; min-height: 90px; box-sizing: border-box; padding: 14px 16px 8px;
  display: grid; grid-template-columns: 1fr auto; grid-template-rows: auto auto 1fr; gap: 2px 10px;
  border: 1px solid var(--sp-border); border-radius: 10px;
  background: var(--sp-card-background);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.04), 0 10px 28px rgba(0,0,0,.14); cursor: pointer; }
.sp-head { display: flex; align-items: center; min-width: 0; gap: 8px; grid-column: 1 / -1; }
.sp-title { overflow: hidden; color: var(--sp-text); font-size: 13px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.sp-date { margin-left: auto; color: var(--sp-text-dim); font-size: 10px; white-space: nowrap; }
.sp-value { color: var(--sp-number); font-size: clamp(22px, 5vw, 34px); font-weight: 700; line-height: 1.15; font-variant-numeric: tabular-nums; }
.sp-change { align-self: end; padding-bottom: 2px; color: var(--sp-up); font-size: 12px; font-variant-numeric: tabular-nums; white-space: nowrap; }
.sp-change.is-down { color: var(--sp-down); }
.sp-change small { display: block; color: var(--sp-text-dim); font-size: 10px; text-align: right; }
.sp-chart { width: 100%; height: 42px; grid-column: 1 / -1; align-self: end; }
</style>
