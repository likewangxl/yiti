<template>
  <v-chart v-if="radar.indicators.length" class="kr-chart" :option="option" autoresize />
  <div v-else class="scr-block-empty">
    <el-icon class="scr-empty-icon"><DocumentRemove /></el-icon>
    <span>暂无 KPI 细项数据</span>
  </div>
</template>

<script setup>
// KPI 细项雷达（KPI_RADAR）：维度=细项名称，值=propValue.valueField 选 得分(score,默认)|完成率(rate)。
// 得分模式各维 max=权重（满分口径）；完成率模式 max=120 封顶（spec §5.1）。
import { computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { RadarChart } from 'echarts/charts';
import { RadarComponent, TooltipComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { DocumentRemove } from '@element-plus/icons-vue';
import { resolveChartTheme, scrTooltipStyle, scrWithAlpha } from '@/styles/screenChartTheme';
import { parseKpiRows, kpiRadarData } from './utils/kpiDetail';

use([CanvasRenderer, RadarChart, RadarComponent, TooltipComponent]);

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) },
  propValue: { type: Object, default: () => ({}) },
  columnsMeta: { type: Array, default: null }
});

const valueField = computed(() => (props.propValue?.valueField === 'rate' ? 'rate' : 'score'));
const radar = computed(() => kpiRadarData(parseKpiRows(props.columns, props.rows), valueField.value));
const seriesName = computed(() => (valueField.value === 'rate' ? '完成率' : '得分'));
const theme = computed(() => resolveChartTheme(props.styleCfg));
const palette = computed(() => props.styleCfg.colors?.length ? props.styleCfg.colors : theme.value.palette);

const option = computed(() => ({
  color: palette.value,
  tooltip: { trigger: 'item', ...scrTooltipStyle(theme.value) },
  radar: {
    indicator: radar.value.indicators,
    radius: '68%',
    center: ['50%', '52%'],
    axisName: { color: theme.value.tokens.textDim, fontSize: 12 },
    axisLine: { lineStyle: { color: theme.value.tokens.border } },
    splitLine: { lineStyle: { color: theme.value.tokens.grid } },
    // 深浅交替暗环，替代默认亮色 splitArea，贴合深色大屏
    splitArea: { areaStyle: { color: [scrWithAlpha(theme.value.tokens.bgDeep, .3), scrWithAlpha(theme.value.tokens.bgDeep, .55)] } }
  },
  series: [{
    type: 'radar',
    symbol: 'circle',
    symbolSize: 5,
    data: [{
      name: seriesName.value,
      value: radar.value.values,
      lineStyle: { color: palette.value[0], width: 2, shadowBlur: 10, shadowColor: scrWithAlpha(palette.value[0], 0.6) },
      itemStyle: { color: palette.value[0] },
      areaStyle: {
        color: {
          type: 'radial', x: 0.5, y: 0.5, r: 0.8,
          colorStops: [
            { offset: 0, color: scrWithAlpha(palette.value[0], 0.35) },
            { offset: 1, color: scrWithAlpha(palette.value[1] || palette.value[0], 0.08) }
          ]
        }
      }
    }]
  }]
}));
</script>

<style scoped>
.kr-chart { width: 100%; height: 100%; }
</style>
