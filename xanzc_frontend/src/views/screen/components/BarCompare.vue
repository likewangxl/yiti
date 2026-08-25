<template>
  <v-chart v-if="series.length" class="bc-chart" :option="option" autoresize @click="onChartClick" />
  <div v-else class="scr-block-empty">
    <el-icon class="scr-empty-icon"><DocumentRemove /></el-icon>
    <span>暂无可绘制的数值列</span>
  </div>
</template>

<script setup>
// 柱状对比（BAR_COMPARE）：通常首列为类目（或 data_date），其余数值列为系列。
// groupBy=NONE 的单行多指标响应没有独立类目列，此时按 bind.items 转置为指标类目。
// propValue.barMode 三形态：basic 基础分组 | stack 堆叠 | horizontal 横向条形。
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { BarChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, LegendComponent, MarkPointComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { DocumentRemove } from '@element-plus/icons-vue';
import {
  resolveChartTheme, scrAxisLabel, scrAxisLine, scrSplitLine, scrTooltipStyle, scrWithAlpha,
  SCR_MORANDI_PALETTE
} from '@/styles/screenChartTheme';
import { rowsToSeries, displayName } from './utils/chartData';

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent, LegendComponent, MarkPointComponent]);

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) },
  propValue: { type: Object, default: () => ({}) },
  columnsMeta: { type: Array, default: null }
});
const emit = defineEmits(['item-click']);

const mode = computed(() => props.propValue?.barMode || 'basic');
const boundCols = computed(() =>
  Array.isArray(props.bind?.items) ? props.bind.items.map(item => item?.col) : []);
const metricItems = computed(() => {
  const columns = Array.isArray(props.columns) ? props.columns : [];
  return (Array.isArray(props.bind?.items) ? props.bind.items : [])
    .filter(item => item?.col != null && columns.includes(item.col));
});
// groupBy=NONE 的聚合结果为单行，首列若本身就是绑定指标，说明响应没有独立维度列。
const isSingleRowMetricData = computed(() => {
  const columns = Array.isArray(props.columns) ? props.columns : [];
  const rows = Array.isArray(props.rows) ? props.rows : [];
  return rows.length === 1 && columns.length > 0 && metricItems.value.some(item => item.col === columns[0]);
});

function chartNumber(value) {
  if (value == null || value === '') return null;
  const number = Number(value);
  return Number.isNaN(number) ? null : number;
}

function metricLabel(item) {
  const label = String(item?.label ?? '').trim();
  return label || displayName(item.col, props.columnsMeta) || item.col;
}

const parsed = computed(() => {
  if (!isSingleRowMetricData.value) {
    return rowsToSeries(props.columns, props.rows, boundCols.value);
  }
  const row = props.rows[0] || [];
  return {
    categories: metricItems.value.map(metricLabel),
    series: [{
      name: '指标值',
      data: metricItems.value.map(item => chartNumber(row[props.columns.indexOf(item.col)]))
    }]
  };
});
const series = computed(() => parsed.value.series);
const theme = computed(() => resolveChartTheme(props.styleCfg));
const palette = computed(() => props.styleCfg.colors?.length ? props.styleCfg.colors : SCR_MORANDI_PALETTE);
const showLegend = computed(() => props.styleCfg.showLegend !== false);
const showLabels = computed(() => props.styleCfg.showLabels === true);
const showMarks = computed(() => props.styleCfg.showMarks !== false);

/** 渐变柱体：沿柱体方向由主色渐隐（横向模式渐变轴转 90°），发光描边呼应深色大屏风格 */
function barItemStyle(color, horizontal) {
  return {
    borderRadius: horizontal ? [0, 4, 4, 0] : [4, 4, 0, 0],
    shadowBlur: 8,
    shadowColor: scrWithAlpha(color, 0.35),
    color: {
      type: 'linear',
      x: 0, y: horizontal ? 0 : 1, x2: horizontal ? 1 : 0, y2: 0,
      colorStops: [
        { offset: 0, color: scrWithAlpha(color, 0.25) },
        { offset: 1, color }
      ]
    }
  };
}

const option = computed(() => {
  const horizontal = mode.value === 'horizontal';
  const catAxis = { type: 'category', data: parsed.value.categories, axisLine: scrAxisLine(theme.value), axisLabel: scrAxisLabel(theme.value) };
  const valAxis = { type: 'value', axisLabel: scrAxisLabel(theme.value), splitLine: scrSplitLine(theme.value) };
  return {
    color: palette.value,
    animation: true,
    grid: { top: 34, right: 16, bottom: 26, left: horizontal ? 90 : 56 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...scrTooltipStyle(theme.value) },
    legend: { show: showLegend.value, top: 4, textStyle: scrAxisLabel(theme.value) },
    xAxis: horizontal ? valAxis : catAxis,
    yAxis: horizontal ? catAxis : valAxis,
    series: series.value.map((s, i) => ({
      name: displayName(s.name, props.columnsMeta),
      type: 'bar',
      stack: mode.value === 'stack' ? 'total' : undefined,
      barMaxWidth: 26,
      // 单行多指标转置后只有一个 series，逐数据项覆盖颜色以区分每个指标。
      data: isSingleRowMetricData.value
        ? s.data.map((value, dataIndex) => ({
          value,
          itemStyle: barItemStyle(palette.value[dataIndex % palette.value.length], horizontal)
        }))
        : s.data,
      label: { show: showLabels.value, color: theme.value.tokens.text, position: horizontal ? 'right' : 'top' },
      emphasis: { focus: 'series', itemStyle: { shadowBlur: 14, shadowColor: scrWithAlpha(palette.value[i % palette.value.length], .45) } },
      ...(showMarks.value ? { markPoint: { data: [{ type: 'max', name: '最大' }], label: { color: theme.value.tokens.text } } } : {}),
      itemStyle: barItemStyle(palette.value[i % palette.value.length], horizontal)
    }))
  };
});

function onChartClick(p) {
  if (!p || p.componentType !== 'series') return;
  const row = {};
  const sourceRow = isSingleRowMetricData.value ? props.rows[0] : props.rows[p.dataIndex];
  props.columns.forEach((c, i) => { row[c] = sourceRow?.[i]; });
  // 回传原始列名（非别名），保持钻取/跳屏参数与数据契约一致
  const col = isSingleRowMetricData.value
    ? metricItems.value[p.dataIndex]?.col
    : series.value[p.seriesIndex]?.name;
  emit('item-click', { col, label: String(parsed.value.categories[p.dataIndex] ?? ''), row });
}
</script>

<style scoped>
.bc-chart { width: 100%; height: 100%; }
</style>
