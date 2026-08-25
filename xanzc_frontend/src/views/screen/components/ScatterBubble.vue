<template>
  <v-chart v-if="points.length" class="sb-chart" :option="option" autoresize @click="onChartClick" />
  <div v-else class="scr-block-empty" data-testid="scatter-empty"><span>暂无可绘制的散点数据</span></div>
</template>

<script setup>
// 散点气泡：标签列 + 两个坐标数值列，第三个数值列（若存在）控制气泡大小。
// 没有第三列时用坐标绝对值的稳定派生值，不生成随机/业务假数据。
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { ScatterChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, VisualMapComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { resolveChartTheme, scrAxisLabel, scrAxisLine, scrSplitLine, scrTooltipStyle, scrWithAlpha } from '@/styles/screenChartTheme';
import { displayName } from './utils/chartData';
import { columnIndex, finiteNumber, firstCategoryIndex, numericColumnIndexes } from './utils/advancedChartData';

use([CanvasRenderer, ScatterChart, GridComponent, TooltipComponent, VisualMapComponent]);

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

const labelIndex = computed(() => {
  const explicit = columnIndex(props.columns, props.bind.nameCol);
  return explicit >= 0 ? explicit : firstCategoryIndex(props.columns, props.rows, [], 0);
});
const numericIndexes = computed(() => numericColumnIndexes(props.columns, props.rows, [labelIndex.value]).slice(0, 3));
const xIndex = computed(() => columnIndex(props.columns, props.bind.xCol, numericIndexes.value[0] ?? -1));
const yIndex = computed(() => columnIndex(props.columns, props.bind.yCol, numericIndexes.value[1] ?? -1));
const sizeIndex = computed(() => columnIndex(props.columns, props.bind.sizeCol, numericIndexes.value[2] ?? -1));
const points = computed(() => (Array.isArray(props.rows) ? props.rows : [])
  .map(row => {
    const x = finiteNumber(row?.[xIndex.value]);
    const y = finiteNumber(row?.[yIndex.value]);
    if (x == null || y == null) return null;
    const derivedSize = Math.max(1, Math.abs(x * y));
    const size = sizeIndex.value >= 0 ? (finiteNumber(row?.[sizeIndex.value]) ?? derivedSize) : derivedSize;
    return [x, y, size, String(row?.[labelIndex.value] ?? '')];
  })
  .filter(Boolean));
const sizeRange = computed(() => {
  const values = points.value.map(point => point[2]);
  return { min: values.length ? Math.min(...values) : 0, max: values.length ? Math.max(...values) : 1 };
});

const option = computed(() => ({
  color: palette.value,
  animation: true,
  grid: { top: 34, right: 22, bottom: 32, left: 56 },
  tooltip: { trigger: 'item', ...scrTooltipStyle(theme.value) },
  visualMap: {
    show: false, dimension: 2, min: sizeRange.value.min, max: sizeRange.value.max,
    inRange: { color: [scrWithAlpha(palette.value[0], .42), palette.value[0]] }
  },
  xAxis: { type: 'value', name: displayName(props.columns[xIndex.value], props.columnsMeta), axisLine: scrAxisLine(theme.value), axisLabel: scrAxisLabel(theme.value), splitLine: scrSplitLine(theme.value) },
  yAxis: { type: 'value', name: displayName(props.columns[yIndex.value], props.columnsMeta), axisLine: scrAxisLine(theme.value), axisLabel: scrAxisLabel(theme.value), splitLine: scrSplitLine(theme.value) },
  series: [{
    type: 'scatter',
    data: points.value,
    symbolSize: value => Math.max(8, Math.min(44, Math.sqrt(Math.abs(Number(value?.[2]) || 1)) * 4)),
    label: { show: props.styleCfg.showLabels === true, formatter: params => params.data?.[3], color: theme.value.tokens.text },
    itemStyle: { opacity: .88, shadowBlur: 10, shadowColor: scrWithAlpha(palette.value[0], .5) },
    emphasis: { focus: 'series', label: { show: true }, itemStyle: { borderColor: theme.value.tokens.text, borderWidth: 1, shadowBlur: 18 } }
  }]
}));

function onChartClick(point) {
  if (!point || point.componentType !== 'series') return;
  const row = {};
  props.columns.forEach((column, index) => { row[column] = props.rows[point.dataIndex]?.[index]; });
  const selected = points.value[point.dataIndex];
  emit('item-click', { col: props.columns[yIndex.value], label: selected?.[3] || '', row });
}
</script>

<style scoped>
.sb-chart { width: 100%; height: 100%; }
</style>
