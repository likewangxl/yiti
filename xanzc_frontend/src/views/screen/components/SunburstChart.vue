<template>
  <v-chart v-if="tree.length" class="sbc-chart" :option="option" autoresize @click="onChartClick" />
  <div v-else class="scr-block-empty" data-testid="sunburst-empty"><span>暂无可绘制的层级数据</span></div>
</template>

<script setup>
// 旭日分层：一到两列类目生成层级，数值列在叶子层汇总并向父层回传。
// 同名节点在同一父节点下合并，保证响应数据顺序变化不造成重复扇区。
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { SunburstChart as EchartsSunburstChart } from 'echarts/charts';
import { TooltipComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { resolveChartTheme, scrTooltipStyle, scrWithAlpha } from '@/styles/screenChartTheme';
import { columnIndex, finiteNumber, numericColumnIndexes } from './utils/advancedChartData';

use([CanvasRenderer, EchartsSunburstChart, TooltipComponent]);

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
  const numeric = numericColumnIndexes(props.columns, props.rows, []);
  return numeric.find(index => index > 0) ?? numeric[0] ?? -1;
});
const levelIndexes = computed(() => {
  const explicit = Array.isArray(props.bind.levelCols)
    ? props.bind.levelCols.map(col => columnIndex(props.columns, col)).filter(index => index >= 0)
    : [];
  if (explicit.length) return explicit.slice(0, 2);
  const named = [props.bind.nameCol, props.bind.childCol || props.bind.nameCol2]
    .map(col => columnIndex(props.columns, col)).filter(index => index >= 0);
  if (named.length) return named.slice(0, 2);
  // 常见契约是类目列在数值列之前；这样单类目+多指标时不会把第二个指标误当层级。
  const beforeValue = props.columns.map((_, index) => index)
    .filter(index => index !== valueIndex.value && index < valueIndex.value);
  const fallback = props.columns.map((_, index) => index).filter(index => index !== valueIndex.value);
  return (beforeValue.length ? beforeValue : fallback).slice(0, 2);
});

function addNode(siblings, name, value, depth) {
  let node = siblings.find(item => item.name === name);
  if (!node) {
    node = {
      name,
      value: 0,
      // ECharts click params may expose data/treePathInfo; retaining stable column
      // identity on each node keeps jump parameters correct after aggregation.
      __depth: depth,
      __levelIndex: levelIndexes.value[depth],
      __valueIndex: valueIndex.value
    };
    if (depth < levelIndexes.value.length - 1) node.children = [];
    siblings.push(node);
  }
  node.value += value;
  return node;
}

const tree = computed(() => {
  const roots = [];
  for (const row of Array.isArray(props.rows) ? props.rows : []) {
    const value = finiteNumber(row?.[valueIndex.value]);
    if (value == null || !levelIndexes.value.length) continue;
    let siblings = roots;
    levelIndexes.value.forEach((index, depth) => {
      const node = addNode(siblings, String(row?.[index] ?? '未分类'), value, depth);
      if (depth < levelIndexes.value.length - 1) siblings = node.children;
    });
  }
  return roots;
});

const option = computed(() => ({
  color: palette.value,
  animation: true,
  tooltip: { trigger: 'item', ...scrTooltipStyle(theme.value) },
  series: [{
    type: 'sunburst', radius: ['12%', '88%'], center: ['50%', '54%'],
    sort: undefined, nodeClick: 'rootToNode',
    label: { show: props.styleCfg.showLabels !== false, color: theme.value.tokens.text },
    itemStyle: { borderColor: theme.value.tokens.bgDeep, borderWidth: 2 },
    emphasis: { focus: 'ancestor', itemStyle: { shadowBlur: 18, shadowColor: scrWithAlpha(theme.value.tokens.accent, .6) } },
    levels: [{}, { r0: '12%', r: '52%', label: { rotate: 'tangential' } }, { r0: '52%', r: '88%', label: { rotate: 'radial' } }],
    data: tree.value
  }]
}));

function onChartClick(point) {
  if (!point || point.componentType !== 'series') return;
  const node = point.data && typeof point.data === 'object' ? point.data : {};
  const path = Array.isArray(point.treePathInfo) ? point.treePathInfo : [];
  const current = path[path.length - 1] || point;
  const depth = Number.isInteger(node.__depth)
    ? node.__depth
    : (Number.isInteger(current?.depth) ? Math.max(0, current.depth) : Math.max(0, path.length - 1));
  const levelIndex = Number.isInteger(node.__levelIndex)
    ? node.__levelIndex
    : levelIndexes.value[Math.min(depth, Math.max(0, levelIndexes.value.length - 1))];
  const valueIndexForNode = Number.isInteger(node.__valueIndex) ? node.__valueIndex : valueIndex.value;
  const label = String(node.name ?? current?.name ?? point.name ?? '');
  const aggregateValue = finiteNumber(node.value ?? current?.value);
  const row = {};
  if (levelIndex >= 0 && props.columns[levelIndex]) row[props.columns[levelIndex]] = label;
  if (valueIndexForNode >= 0 && props.columns[valueIndexForNode] && aggregateValue != null) {
    row[props.columns[valueIndexForNode]] = aggregateValue;
  }
  emit('item-click', { col: props.columns[valueIndex.value], label, row });
}
</script>

<style scoped>
.sbc-chart { width: 100%; height: 100%; }
</style>
