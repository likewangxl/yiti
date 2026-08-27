<template>
  <v-chart class="ps-chart" :option="option" autoresize @click="onChartClick" />
</template>

<script setup>
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { PieChart as EPie } from 'echarts/charts';
import { TooltipComponent, LegendComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { resolveChartTheme, scrAxisLabel, scrTooltipStyle } from '@/styles/screenChartTheme';

use([CanvasRenderer, EPie, TooltipComponent, LegendComponent]);

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['item-click']);

/**
 * 显式指标绑定用于单行聚合结果：每个 items 项是一个扇区，统一取返回数据的最新一行。
 * 只有列真实存在时才启用该模式；旧节点或未配置有效 items 时继续按 nameCol/valueCol 逐行绘制。
 */
const metricEntries = computed(() => {
  const columns = Array.isArray(props.columns) ? props.columns : [];
  const items = Array.isArray(props.bind?.items) ? props.bind.items : [];
  return items.map(item => {
    const col = String(item?.col || '').trim();
    const index = columns.findIndex(column => String(column) === col);
    if (!col || index < 0) return null;
    return {
      col,
      index,
      label: String(item?.label || '').trim() || col
    };
  }).filter(Boolean).slice(0, 10);
});
const useMetricItems = computed(() => metricEntries.value.length > 0);
const latestRow = computed(() => {
  const rows = Array.isArray(props.rows) ? props.rows : [];
  return rows.length ? rows[rows.length - 1] : null;
});
const legacyEntries = computed(() => {
  const columns = Array.isArray(props.columns) ? props.columns : [];
  const rows = Array.isArray(props.rows) ? props.rows : [];
  const ni = columns.indexOf(props.bind?.nameCol);
  const vi = columns.indexOf(props.bind?.valueCol);
  if (ni < 0 || vi < 0) return [];
  return rows.slice(0, 10).map(row => ({
    name: String(row[ni]), value: Number(row[vi]) || 0
  }));
});
const pieEntries = computed(() => {
  if (useMetricItems.value) {
    if (!latestRow.value) return [];
    return metricEntries.value.map(item => ({
      name: item.label,
      value: Number(latestRow.value[item.index]) || 0,
      item
    }));
  }
  return legacyEntries.value;
});
const pieData = computed(() => pieEntries.value.map(({ name, value }) => ({ name, value })));
const theme = computed(() => resolveChartTheme(props.styleCfg));
const palette = computed(() => props.styleCfg.colors?.length ? props.styleCfg.colors : theme.value.palette);
const showLegend = computed(() => props.styleCfg.showLegend !== false);
const showLabels = computed(() => props.styleCfg.showLabels !== false);
const shape = computed(() => ['donut', 'rose', 'solid'].includes(props.styleCfg.pieShape)
  ? props.styleCfg.pieShape : 'donut');
const total = computed(() => pieData.value.reduce((sum, item) => sum + (Number(item.value) || 0), 0));
const radius = computed(() => shape.value === 'rose'
  ? ['18%', '68%']
  : shape.value === 'solid' ? ['0%', '68%'] : ['40%', '68%']);

const option = computed(() => ({
  color: palette.value,
  tooltip: { trigger: 'item', ...scrTooltipStyle(theme.value) },
  legend: { show: showLegend.value, orient: 'vertical', right: 4, top: 'middle', textStyle: { ...scrAxisLabel(theme.value), fontSize: 12 } },
  graphic: shape.value === 'donut' ? [
    { type: 'text', left: '38%', top: '43%', style: { text: '合计', fill: theme.value.tokens.textDim, fontSize: 11, textAlign: 'center' } },
    { type: 'text', left: '38%', top: '50%', style: { text: String(total.value), fill: theme.value.tokens.text, fontSize: 18, fontWeight: 700, textAlign: 'center' } }
  ] : [],
  series: [{
    type: 'pie', radius: radius.value, center: ['38%', '50%'],
    ...(shape.value === 'rose' ? { roseType: 'radius' } : {}),
    label: { show: showLabels.value, color: theme.value.tokens.text, formatter: '{b}\n{d}%' },
    labelLine: { show: showLabels.value, length: 10, length2: 8, lineStyle: { color: theme.value.tokens.textDim } },
    itemStyle: { borderColor: theme.value.tokens.bgDeep, borderWidth: 2 },
    emphasis: {
      scale: true, scaleSize: 5,
      itemStyle: { shadowBlur: 18, shadowColor: theme.value.tokens.accent }
    },
    data: pieData.value
  }]
}));

function rowMap(sourceRow) {
  const row = {};
  const columns = Array.isArray(props.columns) ? props.columns : [];
  columns.forEach((column, index) => { row[column] = sourceRow?.[index]; });
  return row;
}

function onChartClick(p) {
  if (!p || p.componentType !== 'series') return;
  const dataIndex = Number(p.dataIndex);
  if (!Number.isInteger(dataIndex) || dataIndex < 0) return;
  const entry = pieEntries.value[dataIndex];
  if (!entry) return;

  if (useMetricItems.value) {
    emit('item-click', {
      col: entry.item.col,
      label: entry.item.label,
      row: rowMap(latestRow.value)
    });
    return;
  }

  // 旧 nameCol/valueCol 模式按 dataIndex 映射原始行，避免重复名称时 findIndex 错配。
  const sourceRow = Array.isArray(props.rows) ? props.rows[dataIndex] : undefined;
  emit('item-click', { col: props.bind?.valueCol, label: entry.name, row: rowMap(sourceRow) });
}
</script>

<style scoped>
.ps-chart { width: 100%; height: 100%; }
</style>
