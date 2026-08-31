<template>
  <v-chart v-if="categories.length && series.length" class="cc-chart" :option="option" autoresize @click="onChartClick" />
  <div v-else class="scr-block-empty" data-testid="combo-empty"><span>暂无可绘制的双轴数据</span></div>
</template>

<script setup>
// 双轴组合图：首列作为类目，前两个可证明为数值的列分别绘制柱/线。
// bind.items 可显式限定列并提供展示 label；columnsMeta 只作为别名回退，均不改变取数列身份。
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { BarChart, LineChart } from 'echarts/charts';
import {
  AxisPointerComponent, GridComponent, LegendComponent, TooltipComponent,
  MarkPointComponent, MarkLineComponent
} from 'echarts/components';
import VChart from 'vue-echarts';
import { resolveChartTheme, scrAxisLabel, scrAxisLine, scrSplitLine, scrTooltipStyle, scrWithAlpha } from '@/styles/screenChartTheme';
import { displayName } from './utils/chartData';
import { columnIndex, finiteNumber, numericColumnIndexes } from './utils/advancedChartData';

use([CanvasRenderer, BarChart, LineChart, AxisPointerComponent, GridComponent, LegendComponent, TooltipComponent,
  MarkPointComponent, MarkLineComponent]);

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
const categoryIndex = computed(() => columnIndex(props.columns, props.bind.categoryCol, 0));
const valueIndexes = computed(() => {
  const bound = Array.isArray(props.bind.items)
    ? props.bind.items.map(item => columnIndex(props.columns, item?.col)).filter(index => index >= 0)
    : [];
  const indexes = bound.length ? bound : numericColumnIndexes(props.columns, props.rows, [categoryIndex.value]);
  return indexes.slice(0, 2);
});
// 先形成有效类目行，再从同一记录集生成 X 轴、series 和点击回传，避免空类目造成 dataIndex 错位。
const records = computed(() => (Array.isArray(props.rows) ? props.rows : [])
  .map((row, sourceIndex) => {
    const category = row?.[categoryIndex.value];
    return category != null && category !== '' ? { category, sourceIndex, sourceRow: row } : null;
  })
  .filter(Boolean));
const categories = computed(() => records.value.map(record => record.category));
function metricLabel(index) {
  const col = props.columns[index];
  const item = Array.isArray(props.bind.items)
    ? props.bind.items.find(candidate => candidate?.col === col)
    : null;
  const label = String(item?.label ?? '').trim();
  if (label) return label;
  return String(displayName(col, props.columnsMeta) ?? '').trim() || col;
}

const series = computed(() => valueIndexes.value.map(index => ({
  index,
  col: props.columns[index],
  name: metricLabel(index),
  data: records.value.map(record => finiteNumber(record.sourceRow?.[index]))
})));

const option = computed(() => {
  const colors = palette.value;
  const first = colors[0];
  const second = colors[1 % colors.length];
  const showMarks = props.styleCfg.showMarks !== false;
  return {
    color: colors,
    animation: true,
    grid: { top: 42, right: 54, bottom: 28, left: 54 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, ...scrTooltipStyle(theme.value) },
    legend: { show: props.styleCfg.showLegend !== false, top: 4, textStyle: scrAxisLabel(theme.value) },
    xAxis: { type: 'category', data: categories.value, axisLine: scrAxisLine(theme.value), axisLabel: scrAxisLabel(theme.value) },
    yAxis: [
      { type: 'value', name: series.value[0] ? series.value[0].name : '',
        axisLabel: scrAxisLabel(theme.value), splitLine: scrSplitLine(theme.value), nameTextStyle: scrAxisLabel(theme.value) },
      { type: 'value', name: series.value[1] ? series.value[1].name : '',
        axisLabel: scrAxisLabel(theme.value), splitLine: { show: false }, nameTextStyle: scrAxisLabel(theme.value) }
    ],
    series: series.value.map((item, index) => ({
      name: item.name,
      type: index === 0 ? 'bar' : 'line',
      yAxisIndex: index === 0 ? 0 : 1,
      data: item.data,
      smooth: index === 1 ? props.styleCfg.smooth !== false : undefined,
      barMaxWidth: 28,
      label: { show: props.styleCfg.showLabels === true, color: theme.value.tokens.text },
      itemStyle: index === 0 ? {
        borderRadius: [4, 4, 0, 0],
        color: { type: 'linear', x: 0, y: 1, x2: 0, y2: 0, colorStops: [
          { offset: 0, color: scrWithAlpha(first, .18) }, { offset: 1, color: first }
        ] }
      } : { color: second },
      lineStyle: index === 1 ? { width: 2, shadowBlur: 8, shadowColor: scrWithAlpha(second, .5) } : undefined,
      emphasis: { focus: 'series', itemStyle: { shadowBlur: 16, shadowColor: scrWithAlpha(index ? second : first, .55) } },
      ...(showMarks && index === 0 ? {
        markPoint: { data: [{ type: 'max', name: '最大' }], label: { color: theme.value.tokens.text } }
      } : {}),
      ...(showMarks && index === 1 ? {
        markLine: {
          silent: true,
          data: [{ type: 'average', name: '均值' }],
          lineStyle: { type: 'dashed', color: theme.value.tokens.textDim },
          label: { color: theme.value.tokens.textDim, formatter: '均值' }
        }
      } : {})
    }))
  };
});

function onChartClick(point) {
  if (!point || point.componentType !== 'series') return;
  const row = {};
  const sourceRow = records.value[point.dataIndex]?.sourceRow;
  props.columns.forEach((column, index) => { row[column] = sourceRow?.[index]; });
  const picked = series.value[point.seriesIndex];
  emit('item-click', { col: picked?.col, label: String(categories.value[point.dataIndex] ?? ''), row });
}
</script>

<style scoped>
.cc-chart { width: 100%; height: 100%; }
</style>
