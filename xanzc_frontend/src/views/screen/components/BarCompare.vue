<template>
  <div ref="chartWrapEl" class="bc-chart-wrap">
    <v-chart v-if="series.length" class="bc-chart" :option="option" autoresize @click="onChartClick" />
    <div v-else class="scr-block-empty">
      <el-icon class="scr-empty-icon"><DocumentRemove /></el-icon>
      <span>暂无可绘制的数值列</span>
    </div>
  </div>
</template>

<script setup>
// 柱状对比（BAR_COMPARE）：通常首列为类目（或 data_date），其余数值列为系列。
// groupBy=NONE 的单行多指标响应没有独立类目列，此时按 bind.items 转置为指标类目。
// propValue.barMode 三形态：basic 基础分组 | stack 堆叠 | horizontal 横向条形。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
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
import { resolveBarLayout } from './utils/barLayout';

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
const requestedCategoryCol = computed(() => String(props.bind?.categoryCol || '').trim());
const hasExplicitCategoryCol = computed(() => {
  const columns = Array.isArray(props.columns) ? props.columns : [];
  return requestedCategoryCol.value !== '' && columns.includes(requestedCategoryCol.value);
});
const categoryCol = computed(() => {
  const columns = Array.isArray(props.columns) ? props.columns : [];
  return hasExplicitCategoryCol.value ? requestedCategoryCol.value : columns[0];
});
const itemCandidates = computed(() => {
  const columns = Array.isArray(props.columns) ? props.columns : [];
  return (Array.isArray(props.bind?.items) ? props.bind.items : [])
    .filter(item => item?.col != null && columns.includes(item.col));
});
const hasExplicitItems = computed(() => Array.isArray(props.bind?.items) && props.bind.items.length > 0);
const boundCols = computed(() => itemCandidates.value
  .map(item => item.col)
  .filter(col => col !== categoryCol.value));
const metricItems = computed(() => hasExplicitCategoryCol.value
  ? itemCandidates.value.filter(item => item.col !== categoryCol.value)
  : itemCandidates.value);
// groupBy=NONE 的聚合结果为单行，首列若本身就是绑定指标，说明响应没有独立维度列。
const isSingleRowMetricData = computed(() => {
  const columns = Array.isArray(props.columns) ? props.columns : [];
  const rows = Array.isArray(props.rows) ? props.rows : [];
  return !hasExplicitCategoryCol.value
    && rows.length === 1 && columns.length > 0
    && itemCandidates.value.some(item => item.col === columns[0]);
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

function seriesLabel(col) {
  const item = itemCandidates.value.find(candidate => candidate.col === col);
  return metricLabel(item || { col });
}

const parsed = computed(() => {
  // 显式 items 只绑定类目列时，不能把空系列误判成自动模式。
  if (hasExplicitItems.value && boundCols.value.length === 0) {
    const columns = Array.isArray(props.columns) ? props.columns : [];
    const rows = Array.isArray(props.rows) ? props.rows : [];
    const index = columns.indexOf(categoryCol.value);
    const categoryIndex = index >= 0 ? index : 0;
    return { categories: rows.map(row => row?.[categoryIndex]), series: [] };
  }
  if (!isSingleRowMetricData.value) {
    return rowsToSeries(props.columns, props.rows, boundCols.value, categoryCol.value);
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

// 组件实际尺寸由外层容器观察，避免只依赖 ECharts autoresize 而无法同步柱宽布局。
const chartWrapEl = ref(null);
const chartSize = ref({ width: 640, height: 360 });
let resizeObserver = null;

function updateChartSize(entry) {
  const rect = entry?.contentRect;
  const width = Number(rect?.width) || Number(chartWrapEl.value?.clientWidth) || 0;
  const height = Number(rect?.height) || Number(chartWrapEl.value?.clientHeight) || 0;
  if (width <= 0 && height <= 0) return;
  chartSize.value = {
    width: width > 0 ? width : chartSize.value.width,
    height: height > 0 ? height : chartSize.value.height
  };
}

onMounted(() => {
  updateChartSize();
  if (typeof ResizeObserver === 'undefined' || !chartWrapEl.value) return;
  resizeObserver = new ResizeObserver(entries => updateChartSize(entries?.[0]));
  resizeObserver.observe(chartWrapEl.value);
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
});

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

const grid = computed(() => ({ top: 34, right: 16, bottom: 26, left: mode.value === 'horizontal' ? 90 : 56 }));
const barLayout = computed(() => {
  const chartGrid = grid.value;
  return resolveBarLayout({
    width: Math.max(0, chartSize.value.width - chartGrid.left - chartGrid.right),
    height: Math.max(0, chartSize.value.height - chartGrid.top - chartGrid.bottom),
    categoryCount: parsed.value.categories.length,
    seriesCount: series.value.length,
    horizontal: mode.value === 'horizontal',
    stacked: mode.value === 'stack'
  });
});

const option = computed(() => {
  const horizontal = mode.value === 'horizontal';
  const catAxis = { type: 'category', data: parsed.value.categories, axisLine: scrAxisLine(theme.value), axisLabel: scrAxisLabel(theme.value) };
  const valAxis = { type: 'value', axisLabel: scrAxisLabel(theme.value), splitLine: scrSplitLine(theme.value) };
  return {
    color: palette.value,
    animation: true,
    grid: grid.value,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...scrTooltipStyle(theme.value) },
    legend: { show: showLegend.value, top: 4, textStyle: scrAxisLabel(theme.value) },
    xAxis: horizontal ? valAxis : catAxis,
    yAxis: horizontal ? catAxis : valAxis,
    series: series.value.map((s, i) => ({
      name: seriesLabel(s.name),
      type: 'bar',
      stack: mode.value === 'stack' ? 'total' : undefined,
      barWidth: barLayout.value.barWidth,
      barGap: barLayout.value.barGap,
      barCategoryGap: barLayout.value.barCategoryGap,
      // 横向模式逐柱配色；单行多指标转置后也按指标逐柱配色。
      data: horizontal || isSingleRowMetricData.value
        ? s.data.map((value, dataIndex) => ({
          value,
          itemStyle: barItemStyle(
            palette.value[(horizontal ? i + dataIndex : dataIndex) % palette.value.length],
            horizontal
          )
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
.bc-chart-wrap { width: 100%; height: 100%; min-width: 0; min-height: 0; }
.bc-chart { width: 100%; height: 100%; }
</style>
