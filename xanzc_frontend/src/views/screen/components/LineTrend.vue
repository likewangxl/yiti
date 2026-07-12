<template>
  <v-chart class="lt-chart" :option="option" autoresize @click="onChartClick" />
</template>

<script setup>
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components';
import VChart from 'vue-echarts';

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent]);

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['item-click']);

const PALETTE = ['#00e5ff', '#ffd76a', '#3d7eff', '#00e676', '#ff8a65'];

const seriesCols = computed(() => {
  const its = props.bind.items || [];
  return its.length ? its.map(i => i.col) : props.columns.slice(1);
});

const option = computed(() => ({
  color: props.styleCfg.colors?.length ? props.styleCfg.colors : PALETTE,
  grid: { top: 34, right: 16, bottom: 26, left: 56 },
  tooltip: { trigger: 'axis', backgroundColor: 'rgba(5,14,43,.9)', borderColor: 'rgba(0,229,255,.4)',
             textStyle: { color: '#d5e6ff' } },
  legend: { top: 4, textStyle: { color: '#7d9bc9' } },
  xAxis: { type: 'category', data: props.rows.map(r => r[0]),
           axisLine: { lineStyle: { color: 'rgba(125,155,201,.4)' } },
           axisLabel: { color: '#7d9bc9' } },
  yAxis: { type: 'value', axisLabel: { color: '#7d9bc9' },
           splitLine: { lineStyle: { color: 'rgba(125,155,201,.15)' } } },
  series: seriesCols.value.map(col => {
    const idx = props.columns.indexOf(col);
    return {
      name: col, type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
      data: props.rows.map(r => (idx >= 0 ? r[idx] : null))
    };
  })
}));

function onChartClick(p) {
  if (!p || p.componentType !== 'series') return;
  const row = {};
  props.columns.forEach((c, i) => { row[c] = props.rows[p.dataIndex]?.[i]; });
  emit('item-click', { col: p.seriesName, label: p.seriesName, row });
}
</script>

<style scoped>
.lt-chart { width: 100%; height: 100%; }
</style>
