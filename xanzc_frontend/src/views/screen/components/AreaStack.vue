<template>
  <v-chart v-if="series.length" class="as-chart" :option="option" autoresize @click="onChartClick" />
  <div v-else class="scr-block-empty">
    <el-icon class="scr-empty-icon"><DocumentRemove /></el-icon>
    <span>暂无可绘制的数值列</span>
  </div>
</template>

<script setup>
// 堆叠面积图（AREA_STACK）：时序数据源专用，首列 data_date 为 X 轴，其余数值列堆叠；
// 面积用主色 45%→4% 垂直渐变填充，贴合深色大屏发光风格。
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';
import {
  GridComponent, TooltipComponent, LegendComponent, AxisPointerComponent,
  MarkPointComponent, MarkLineComponent
} from 'echarts/components';
import VChart from 'vue-echarts';
import { DocumentRemove } from '@element-plus/icons-vue';
import { resolveChartTheme, scrAxisLabel, scrAxisLine, scrSplitLine, scrTooltipStyle, scrWithAlpha } from '@/styles/screenChartTheme';
import { rowsToSeries, displayName } from './utils/chartData';

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent, AxisPointerComponent,
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

const parsed = computed(() =>
  rowsToSeries(props.columns, props.rows, (props.bind.items || []).map(i => i.col)));
const series = computed(() => parsed.value.series);
const theme = computed(() => resolveChartTheme(props.styleCfg));
const palette = computed(() => props.styleCfg.colors?.length ? props.styleCfg.colors : theme.value.palette);
const showLegend = computed(() => props.styleCfg.showLegend !== false);
const showLabels = computed(() => props.styleCfg.showLabels === true);
const showMarks = computed(() => props.styleCfg.showMarks !== false);
const smooth = computed(() => props.styleCfg.smooth !== false);

const option = computed(() => {
  return {
    color: palette.value,
    animation: true,
    grid: { top: 34, right: 16, bottom: 26, left: 56 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, ...scrTooltipStyle(theme.value) },
    legend: { show: showLegend.value, top: 4, textStyle: scrAxisLabel(theme.value) },
    xAxis: { type: 'category', boundaryGap: false, data: parsed.value.categories,
             axisLine: scrAxisLine(theme.value), axisLabel: scrAxisLabel(theme.value) },
    yAxis: { type: 'value', axisLabel: scrAxisLabel(theme.value), splitLine: scrSplitLine(theme.value) },
    series: series.value.map((s, i) => {
      const c = palette.value[i % palette.value.length];
      return {
        name: displayName(s.name, props.columnsMeta),
        type: 'line',
        stack: 'total',
        smooth: smooth.value,
        showSymbol: showLabels.value,
        label: { show: showLabels.value, color: theme.value.tokens.text },
        emphasis: { focus: 'series', lineStyle: { width: 2.5 } },
        lineStyle: { width: 1.5, shadowBlur: 6, shadowColor: scrWithAlpha(c, 0.4) },
        ...(showMarks.value ? {
          markPoint: { data: [{ type: 'max', name: '最大' }], label: { color: theme.value.tokens.text } },
          markLine: {
            silent: true,
            lineStyle: { type: 'dashed', color: theme.value.tokens.textDim },
            label: { color: theme.value.tokens.textDim, formatter: '均值' },
            data: [{ type: 'average', name: '均值' }]
          }
        } : {}),
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: scrWithAlpha(c, 0.45) },
              { offset: 1, color: scrWithAlpha(c, 0.04) }
            ]
          }
        },
        data: s.data
      };
    })
  };
});

function onChartClick(p) {
  if (!p || p.componentType !== 'series') return;
  const row = {};
  props.columns.forEach((c, i) => { row[c] = props.rows[p.dataIndex]?.[i]; });
  const col = series.value[p.seriesIndex]?.name; // 原始列名
  emit('item-click', { col, label: String(parsed.value.categories[p.dataIndex] ?? ''), row });
}
</script>

<style scoped>
.as-chart { width: 100%; height: 100%; }
</style>
