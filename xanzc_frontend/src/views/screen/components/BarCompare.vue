<template>
  <v-chart v-if="series.length" class="bc-chart" :option="option" autoresize @click="onChartClick" />
  <div v-else class="scr-block-empty">
    <el-icon class="scr-empty-icon"><DocumentRemove /></el-icon>
    <span>暂无可绘制的数值列</span>
  </div>
</template>

<script setup>
// 柱状对比（BAR_COMPARE）：首列为类目（或 data_date），其余数值列为系列。
// propValue.barMode 三形态：basic 基础分组 | stack 堆叠 | horizontal 横向条形。
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { BarChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { DocumentRemove } from '@element-plus/icons-vue';
import { SCR_PALETTE, scrAxisLabel, scrAxisLine, scrSplitLine, scrTooltipStyle, scrWithAlpha } from '@/styles/screenChartTheme';
import { rowsToSeries, displayName } from './utils/chartData';

use([CanvasRenderer, BarChart, GridComponent, TooltipComponent, LegendComponent]);

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
const parsed = computed(() =>
  rowsToSeries(props.columns, props.rows, (props.bind.items || []).map(i => i.col)));
const series = computed(() => parsed.value.series);

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
  const catAxis = { type: 'category', data: parsed.value.categories, axisLine: scrAxisLine(), axisLabel: scrAxisLabel() };
  const valAxis = { type: 'value', axisLabel: scrAxisLabel(), splitLine: scrSplitLine() };
  const palette = props.styleCfg.colors?.length ? props.styleCfg.colors : SCR_PALETTE;
  return {
    color: palette,
    grid: { top: 34, right: 16, bottom: 26, left: horizontal ? 90 : 56 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...scrTooltipStyle() },
    legend: { top: 4, textStyle: scrAxisLabel() },
    xAxis: horizontal ? valAxis : catAxis,
    yAxis: horizontal ? catAxis : valAxis,
    series: series.value.map((s, i) => ({
      name: displayName(s.name, props.columnsMeta),
      type: 'bar',
      stack: mode.value === 'stack' ? 'total' : undefined,
      barMaxWidth: 26,
      data: s.data,
      itemStyle: barItemStyle(palette[i % palette.length], horizontal)
    }))
  };
});

function onChartClick(p) {
  if (!p || p.componentType !== 'series') return;
  const row = {};
  props.columns.forEach((c, i) => { row[c] = props.rows[p.dataIndex]?.[i]; });
  // 回传原始列名（非别名），保持钻取/跳屏参数与数据契约一致
  const col = series.value[p.seriesIndex]?.name;
  emit('item-click', { col, label: String(parsed.value.categories[p.dataIndex] ?? ''), row });
}
</script>

<style scoped>
.bc-chart { width: 100%; height: 100%; }
</style>
