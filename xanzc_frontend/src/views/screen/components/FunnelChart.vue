<template>
  <v-chart v-if="data.length" class="fc-chart" :option="option" autoresize @click="onChartClick" />
  <div v-else class="scr-block-empty" data-testid="funnel-empty"><span>暂无可绘制的转化数据</span></div>
</template>

<script setup>
// 漏斗转化：优先使用 bind.nameCol/valueCol，缺省时自动取首个文本列与首个数值列。
// ECharts 的 {d} 占位符负责按最大值计算百分比，避免前端复制一套百分比算法。
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { FunnelChart } from 'echarts/charts';
import { LegendComponent, TooltipComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { resolveChartTheme, scrAxisLabel, scrTooltipStyle, scrWithAlpha } from '@/styles/screenChartTheme';
import { columnIndex, finiteNumber, firstCategoryIndex, numericColumnIndexes } from './utils/advancedChartData';

use([CanvasRenderer, FunnelChart, LegendComponent, TooltipComponent]);

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
  if (explicit >= 0) return explicit;
  return numericColumnIndexes(props.columns, props.rows, []).find(index => index !== 0)
    ?? numericColumnIndexes(props.columns, props.rows, [])[0] ?? -1;
});
const nameIndex = computed(() => {
  const explicit = columnIndex(props.columns, props.bind.nameCol);
  if (explicit >= 0) return explicit;
  return firstCategoryIndex(props.columns, props.rows, [valueIndex.value], 0);
});
const data = computed(() => (Array.isArray(props.rows) ? props.rows : [])
  .map((row, sourceIndex) => ({
    name: String(row?.[nameIndex.value] ?? ''),
    value: finiteNumber(row?.[valueIndex.value]),
    sourceIndex,
    sourceRow: row
  }))
  .filter(item => item.name && item.value != null)
  .sort((left, right) => right.value - left.value)
  .map((item, index) => ({
    ...item,
    itemStyle: {
      color: { type: 'linear', x: 0, y: 0, x2: 1, y2: 0, colorStops: [
        { offset: 0, color: scrWithAlpha(palette.value[index % palette.value.length], .35) },
        { offset: 1, color: palette.value[index % palette.value.length] }
      ] }
    }
  })));

const option = computed(() => ({
  color: palette.value,
  animation: true,
  tooltip: { trigger: 'item', ...scrTooltipStyle(theme.value) },
  legend: { show: props.styleCfg.showLegend !== false, top: 4, textStyle: scrAxisLabel(theme.value) },
  series: [{
    type: 'funnel',
    left: '8%', right: '8%', top: 34, bottom: 12,
    min: 0, max: data.value.reduce((max, item) => Math.max(max, item.value), 0),
    minSize: '12%', maxSize: '92%', sort: 'descending', gap: 3,
    label: { show: props.styleCfg.showLabels !== false, position: 'inside', color: theme.value.tokens.text, formatter: '{b} {d}%' },
    labelLine: { show: true, lineStyle: { color: theme.value.tokens.textDim } },
    itemStyle: { borderColor: theme.value.tokens.bgDeep, borderWidth: 1 },
    emphasis: { label: { fontWeight: 700 }, itemStyle: { shadowBlur: 18, shadowColor: scrWithAlpha(theme.value.tokens.accent, .55) } },
    data: data.value
  }]
}));

function onChartClick(point) {
  if (!point || point.componentType !== 'series') return;
  const row = {};
  const selected = data.value[point.dataIndex];
  props.columns.forEach((column, index) => { row[column] = selected?.sourceRow?.[index]; });
  emit('item-click', { col: props.columns[valueIndex.value], label: String(selected?.name || ''), row });
}

</script>

<style scoped>
.fc-chart { width: 100%; height: 100%; }
</style>
