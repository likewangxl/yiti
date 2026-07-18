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
import { SCR_COLOR, scrTooltipStyle, scrWithAlpha } from '@/styles/screenChartTheme';
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

const option = computed(() => ({
  tooltip: { trigger: 'item', ...scrTooltipStyle() },
  radar: {
    indicator: radar.value.indicators,
    radius: '68%',
    center: ['50%', '52%'],
    axisName: { color: SCR_COLOR.textDim, fontSize: 12 },
    axisLine: { lineStyle: { color: 'rgba(125,155,201,.3)' } },
    splitLine: { lineStyle: { color: 'rgba(125,155,201,.2)' } },
    // 深浅交替暗环，替代默认亮色 splitArea，贴合深色大屏
    splitArea: { areaStyle: { color: ['rgba(10,32,74,.3)', 'rgba(5,14,43,.3)'] } }
  },
  series: [{
    type: 'radar',
    symbol: 'circle',
    symbolSize: 5,
    data: [{
      name: seriesName.value,
      value: radar.value.values,
      lineStyle: { color: SCR_COLOR.cyan, width: 2, shadowBlur: 10, shadowColor: scrWithAlpha(SCR_COLOR.cyan, 0.6) },
      itemStyle: { color: SCR_COLOR.cyan },
      areaStyle: {
        color: {
          type: 'radial', x: 0.5, y: 0.5, r: 0.8,
          colorStops: [
            { offset: 0, color: scrWithAlpha(SCR_COLOR.cyan, 0.35) },
            { offset: 1, color: scrWithAlpha(SCR_COLOR.blue, 0.08) }
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
