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

const pieData = computed(() => {
  const ni = props.columns.indexOf(props.bind.nameCol);
  const vi = props.columns.indexOf(props.bind.valueCol);
  if (ni < 0 || vi < 0) return [];
  return props.rows.slice(0, 10).map(r => ({ name: String(r[ni]), value: Number(r[vi]) || 0 }));
});
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

function onChartClick(p) {
  if (!p || p.componentType !== 'series') return;
  const src = props.rows[props.rows.findIndex(r => String(r[props.columns.indexOf(props.bind.nameCol)]) === p.name)];
  const row = {};
  props.columns.forEach((c, i) => { row[c] = src?.[i]; });
  emit('item-click', { col: props.bind.valueCol, label: p.name, row });
}
</script>

<style scoped>
.ps-chart { width: 100%; height: 100%; }
</style>
