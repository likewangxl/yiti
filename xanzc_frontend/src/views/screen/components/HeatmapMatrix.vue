<template>
  <v-chart v-if="cells.length" class="hm-chart" :option="option" autoresize @click="onChartClick" />
  <div v-else class="scr-block-empty" data-testid="heatmap-empty"><span>暂无可绘制的矩阵数据</span></div>
</template>

<script setup>
// 热力矩阵：前两个类目列作为 Y/X 轴，第三个可证明为数值的列作为值。
// bind.yCol/xCol/valueCol 可在设计器中把自动识别结果固定下来。
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { HeatmapChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, VisualMapComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { resolveChartTheme, scrAxisLabel, scrAxisLine, scrSplitLine, scrTooltipStyle, scrWithAlpha } from '@/styles/screenChartTheme';
import { displayName } from './utils/chartData';
import { columnIndex, finiteNumber, numericColumnIndexes, uniqueValues } from './utils/advancedChartData';

use([CanvasRenderer, HeatmapChart, GridComponent, TooltipComponent, VisualMapComponent]);

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
const valueIndex = computed(() => {
  const explicit = columnIndex(props.columns, props.bind.valueCol);
  return explicit >= 0 ? explicit : numericColumnIndexes(props.columns, props.rows, []).find(index => index >= 2)
    ?? numericColumnIndexes(props.columns, props.rows, [])[0] ?? -1;
});
const yIndex = computed(() => columnIndex(props.columns, props.bind.yCol, 0));
const xIndex = computed(() => columnIndex(props.columns, props.bind.xCol, 1));
const xValues = computed(() => uniqueValues(props.rows, xIndex.value));
const yValues = computed(() => uniqueValues(props.rows, yIndex.value));
const cells = computed(() => (Array.isArray(props.rows) ? props.rows : [])
  .map(row => {
    const x = xValues.value.findIndex(value => String(value) === String(row?.[xIndex.value]));
    const y = yValues.value.findIndex(value => String(value) === String(row?.[yIndex.value]));
    const value = finiteNumber(row?.[valueIndex.value]);
    return x >= 0 && y >= 0 && value != null ? [x, y, value] : null;
  })
  .filter(Boolean));
const range = computed(() => {
  const values = cells.value.map(cell => cell[2]);
  return { min: values.length ? Math.min(...values) : 0, max: values.length ? Math.max(...values) : 1 };
});

const option = computed(() => ({
  animation: true,
  grid: { top: 44, right: 20, bottom: 34, left: 62 },
  tooltip: { trigger: 'item', ...scrTooltipStyle(theme.value) },
  visualMap: {
    min: range.value.min, max: range.value.max, calculable: true, orient: 'horizontal', left: 'center', bottom: 0,
    textStyle: scrAxisLabel(theme.value), inRange: { color: [scrWithAlpha(palette.value[0], .18), palette.value[0], palette.value[2 % palette.value.length]] }
  },
  xAxis: { type: 'category', data: xValues.value, name: displayName(props.columns[xIndex.value], props.columnsMeta), axisLine: scrAxisLine(theme.value), axisLabel: scrAxisLabel(theme.value) },
  yAxis: { type: 'category', data: yValues.value, name: displayName(props.columns[yIndex.value], props.columnsMeta), axisLine: scrAxisLine(theme.value), axisLabel: scrAxisLabel(theme.value) },
  series: [{
    type: 'heatmap', data: cells.value,
    label: { show: props.styleCfg.showLabels === true, color: theme.value.tokens.text },
    emphasis: { itemStyle: { shadowBlur: 10, shadowColor: theme.value.tokens.accent, borderColor: theme.value.tokens.text, borderWidth: 1 } }
  }]
}));

function onChartClick(point) {
  if (!point || point.componentType !== 'series') return;
  const source = props.rows.find(row => String(row?.[xIndex.value]) === String(xValues.value[point.value?.[0]])
    && String(row?.[yIndex.value]) === String(yValues.value[point.value?.[1]]));
  const row = {};
  props.columns.forEach((column, index) => { row[column] = source?.[index]; });
  emit('item-click', { col: props.columns[valueIndex.value], label: String(yValues.value[point.value?.[1]] ?? ''), row });
}
</script>

<style scoped>
.hm-chart { width: 100%; height: 100%; }
</style>
