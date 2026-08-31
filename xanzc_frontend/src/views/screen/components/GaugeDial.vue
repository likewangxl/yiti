<template>
  <v-chart v-if="colIdx >= 0" class="gd-chart" :option="option" autoresize />
  <div v-else class="scr-block-empty">
    <el-icon class="scr-empty-icon"><Warning /></el-icon>
    <span>未找到可用数值列</span>
  </div>
</template>

<script setup>
// 仪表盘（GAUGE）：绑定单行数据某一数值列——bind.valueCol 指定优先，
// 默认取名（或 columnsMeta 别名）含"完成率"的列，再退首个数值列。
// 0-100 刻度；超 100 时表盘指针/进度封顶 100，中央数字仍显示真实值（不丢超额信息）。
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { GaugeChart } from 'echarts/charts';
import VChart from 'vue-echarts';
import { Warning } from '@element-plus/icons-vue';
import { resolveChartTheme, scrWithAlpha } from '@/styles/screenChartTheme';
import { pickValueCol, displayName, metaOf, fmtNum, clampPct } from './utils/chartData';

use([CanvasRenderer, GaugeChart]);

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) },
  propValue: { type: Object, default: () => ({}) },
  columnsMeta: { type: Array, default: null }
});

const colIdx = computed(() => pickValueCol(props.columns, props.rows, props.bind.valueCol, props.columnsMeta));
const colName = computed(() => (colIdx.value >= 0 ? props.columns[colIdx.value] : ''));
const meta = computed(() => metaOf(colName.value, props.columnsMeta));
// 单行契约：SNAPSHOT 单值数据源恒 1 行；多行时取末行（与 MetricCard 口径一致）
const rawValue = computed(() => {
  const last = props.rows.length ? props.rows[props.rows.length - 1] : null;
  const v = last && colIdx.value >= 0 ? Number(last[colIdx.value]) : NaN;
  return Number.isNaN(v) ? null : v;
});
const dialValue = computed(() => clampPct(rawValue.value)); // 表盘封顶 0-100
const unit = computed(() => meta.value?.unit || '%');
const decimals = computed(() => meta.value?.decimals ?? props.styleCfg.decimals ?? 1);
const theme = computed(() => resolveChartTheme(props.styleCfg));
const palette = computed(() => props.styleCfg.colors?.length ? props.styleCfg.colors : theme.value.palette);

const option = computed(() => ({
  color: palette.value,
  series: [
    // 底层：暗色刻度环 + 发光进度弧
    {
      type: 'gauge',
      startAngle: 210, endAngle: -30, min: 0, max: 100,
      radius: '96%', center: ['50%', '58%'],
      progress: {
        show: true, roundCap: true, width: 12,
        itemStyle: {
          shadowBlur: 12, shadowColor: scrWithAlpha(palette.value[0], 0.6),
          color: {
            type: 'linear', x: 0, y: 1, x2: 1, y2: 0,
            colorStops: [
              { offset: 0, color: palette.value[1] },
              { offset: 1, color: palette.value[0] }
            ]
          }
        }
      },
      axisLine: { roundCap: true, lineStyle: { width: 12, color: [[1, theme.value.tokens.border]] } },
      axisTick: { distance: -22, length: 4, lineStyle: { color: theme.value.tokens.border, width: 1 } },
      splitLine: { distance: -26, length: 8, lineStyle: { color: theme.value.tokens.textDim, width: 2 } },
      axisLabel: { distance: -14, color: theme.value.tokens.textDim, fontSize: 10 },
      pointer: { show: true, length: '58%', width: 4, offsetCenter: [0, 0],
                 itemStyle: { color: palette.value[0], shadowBlur: 8, shadowColor: scrWithAlpha(palette.value[0], 0.8) } },
      anchor: { show: true, size: 8, showAbove: true,
                itemStyle: { color: theme.value.tokens.bgDeep, borderColor: palette.value[0], borderWidth: 2 } },
      // 中央数字显示真实值（可超 100），单位来自 columnsMeta.unit（缺省 %）
      detail: {
        valueAnimation: true, offsetCenter: [0, '38%'],
        formatter: () => `${fmtNum(rawValue.value, decimals.value)}${unit.value}`,
        color: theme.value.tokens.number, fontSize: 26, fontWeight: 700, fontFamily: 'inherit'
      },
      title: { offsetCenter: [0, '68%'], color: theme.value.tokens.textDim, fontSize: 13 },
      data: [{ value: dialValue.value, name: displayName(colName.value, props.columnsMeta) }]
    }
  ]
}));
</script>

<style scoped>
.gd-chart { width: 100%; height: 100%; }
</style>
