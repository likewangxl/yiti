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

const option = computed(() => ({
  color: ['#00e5ff', '#3d7eff', '#ffd76a', '#00e676', '#ff8a65', '#ba68c8', '#4dd0e1', '#fff176', '#90caf9', '#a5d6a7'],
  tooltip: { trigger: 'item', backgroundColor: 'rgba(5,14,43,.9)', textStyle: { color: '#d5e6ff' } },
  legend: { orient: 'vertical', right: 4, top: 'middle', textStyle: { color: '#7d9bc9', fontSize: 12 } },
  series: [{
    type: 'pie', radius: ['38%', '68%'], center: ['38%', '50%'],
    label: { color: '#d5e6ff', formatter: '{b}\n{d}%' },
    itemStyle: { borderColor: '#050e2b', borderWidth: 2 },
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
