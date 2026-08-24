<template>
  <v-chart class="lt-chart" :option="option" autoresize @click="onChartClick" />
</template>

<script setup>
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';
import {
  GridComponent, TooltipComponent, LegendComponent, AxisPointerComponent,
  MarkPointComponent, MarkLineComponent
} from 'echarts/components';
import VChart from 'vue-echarts';
import {
  resolveChartTheme, scrAxisLabel, scrAxisLine, scrSplitLine, scrTooltipStyle, scrWithAlpha
} from '@/styles/screenChartTheme';

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent, AxisPointerComponent,
  MarkPointComponent, MarkLineComponent]);

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['item-click']);

const seriesCols = computed(() => {
  const its = props.bind.items || [];
  return its.length ? its.map(i => i.col) : props.columns.slice(1);
});
const theme = computed(() => resolveChartTheme(props.styleCfg));
const palette = computed(() => props.styleCfg.colors?.length ? props.styleCfg.colors : theme.value.palette);
const showLegend = computed(() => props.styleCfg.showLegend !== false);
const showLabels = computed(() => props.styleCfg.showLabels === true);
const showMarks = computed(() => props.styleCfg.showMarks !== false);
const smooth = computed(() => props.styleCfg.smooth !== false);

function valuesFor(col) {
  const idx = props.columns.indexOf(col);
  return props.rows.map(r => (idx >= 0 ? r[idx] : null));
}

const option = computed(() => ({
  color: palette.value,
  animation: true,
  grid: { top: 34, right: 16, bottom: 26, left: 56 },
  tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, ...scrTooltipStyle(theme.value) },
  legend: { show: showLegend.value, top: 4, textStyle: scrAxisLabel(theme.value) },
  xAxis: { type: 'category', data: props.rows.map(r => r[0]),
           axisLine: scrAxisLine(theme.value),
           axisLabel: scrAxisLabel(theme.value) },
  yAxis: { type: 'value', axisLabel: scrAxisLabel(theme.value),
           splitLine: scrSplitLine(theme.value) },
  series: seriesCols.value.map(col => {
    const color = palette.value[seriesCols.value.indexOf(col) % palette.value.length];
    return {
      name: col, type: 'line', smooth: smooth.value, symbol: 'circle', symbolSize: 6,
      showSymbol: showLabels.value,
      lineStyle: { width: 2, shadowBlur: 7, shadowColor: scrWithAlpha(color, .3) },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: scrWithAlpha(color, .28) },
            { offset: 1, color: scrWithAlpha(color, .025) }
          ]
        }
      },
      endLabel: { show: showLabels.value, color: theme.value.tokens.text, formatter: '{a}' },
      emphasis: { focus: 'series', lineStyle: { width: 2.5 } },
      ...(showMarks.value ? {
        markPoint: {
          symbol: 'circle', symbolSize: 8,
          label: { color: theme.value.tokens.text, fontSize: 10 },
          data: [{ type: 'max', name: '最大' }, { type: 'min', name: '最小' }]
        },
        markLine: {
          silent: true,
          lineStyle: { type: 'dashed', color: theme.value.tokens.textDim },
          label: { color: theme.value.tokens.textDim, formatter: '均值' },
          data: [{ type: 'average', name: '均值' }]
        }
      } : {}),
      data: valuesFor(col)
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
