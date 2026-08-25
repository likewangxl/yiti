<template>
  <svg class="chart-type-icon" viewBox="0 0 32 32" width="28" height="28"
       aria-hidden="true" focusable="false" :data-icon-signature="definition.signature">
    <g class="chart-type-icon__base" fill="none" stroke="currentColor"
       stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
      <component v-for="(shape, index) in definition.shapes" :key="`${definition.signature}-${index}`"
                 :is="shape.tag" v-bind="shape.attrs" />
    </g>
  </svg>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  innerType: { type: String, default: '' }
});

// 统一 32×32 画布：轮廓使用 currentColor，填充/辅助线只改变透明度，保证暗色面板上清晰且不引入图片资源。
const ICONS = {
  AREA_STACK: {
    signature: 'area-stack',
    shapes: [
      { tag: 'path', attrs: { d: 'M3 25 9 19 14 21 20 12 29 16V28H3Z', class: 'icon-fill-soft' } },
      { tag: 'polyline', attrs: { points: '3 25 9 19 14 21 20 12 29 16' } },
      { tag: 'polyline', attrs: { points: '3 25 9 22 14 24 20 20 29 23', class: 'icon-aux' } },
      { tag: 'line', attrs: { x1: '3', y1: '28', x2: '29', y2: '28', class: 'icon-aux' } }
    ]
  },
  BAR_COMPARE: {
    signature: 'bar-compare',
    shapes: [
      { tag: 'rect', attrs: { x: '4', y: '18', width: '5', height: '10', rx: '1', class: 'icon-fill' } },
      { tag: 'rect', attrs: { x: '13.5', y: '12', width: '5', height: '16', rx: '1', class: 'icon-fill' } },
      { tag: 'rect', attrs: { x: '23', y: '7', width: '5', height: '21', rx: '1', class: 'icon-fill' } },
      { tag: 'line', attrs: { x1: '3', y1: '28', x2: '29', y2: '28', class: 'icon-aux' } }
    ]
  },
  COMBO_CHART: {
    signature: 'combo-chart',
    shapes: [
      { tag: 'rect', attrs: { x: '4', y: '19', width: '5', height: '9', rx: '1', class: 'icon-fill-soft' } },
      { tag: 'rect', attrs: { x: '12', y: '14', width: '5', height: '14', rx: '1', class: 'icon-fill-soft' } },
      { tag: 'polyline', attrs: { points: '4 15 11 18 18 10 28 13' } },
      { tag: 'circle', attrs: { cx: '4', cy: '15', r: '1.5', class: 'icon-fill' } },
      { tag: 'circle', attrs: { cx: '11', cy: '18', r: '1.5', class: 'icon-fill' } },
      { tag: 'circle', attrs: { cx: '18', cy: '10', r: '1.5', class: 'icon-fill' } },
      { tag: 'circle', attrs: { cx: '28', cy: '13', r: '1.5', class: 'icon-fill' } }
    ]
  },
  FLOW_STATUS: {
    signature: 'flow-status',
    shapes: [
      { tag: 'line', attrs: { x1: '8', y1: '16', x2: '14', y2: '16' } },
      { tag: 'line', attrs: { x1: '18', y1: '16', x2: '24', y2: '16' } },
      { tag: 'circle', attrs: { cx: '5', cy: '16', r: '3', class: 'icon-fill-soft' } },
      { tag: 'circle', attrs: { cx: '16', cy: '16', r: '3', class: 'icon-fill' } },
      { tag: 'circle', attrs: { cx: '27', cy: '16', r: '3', class: 'icon-fill-soft' } },
      { tag: 'polyline', attrs: { points: '12 13 15 16 12 19', class: 'icon-aux' } },
      { tag: 'polyline', attrs: { points: '23 13 26 16 23 19', class: 'icon-aux' } }
    ]
  },
  FUNNEL_CHART: {
    signature: 'funnel-chart',
    shapes: [
      { tag: 'polygon', attrs: { points: '4,5 28,5 24,11 8,11', class: 'icon-fill-soft' } },
      { tag: 'polygon', attrs: { points: '8,13 24,13 20,19 12,19', class: 'icon-fill' } },
      { tag: 'polygon', attrs: { points: '12,21 20,21 18,27 14,27', class: 'icon-fill-soft' } }
    ]
  },
  GAUGE: {
    signature: 'gauge',
    shapes: [
      { tag: 'path', attrs: { d: 'M4 23a12 12 0 0 1 24 0' } },
      { tag: 'line', attrs: { x1: '16', y1: '23', x2: '22', y2: '13' } },
      { tag: 'circle', attrs: { cx: '16', cy: '23', r: '2.5', class: 'icon-fill' } },
      { tag: 'line', attrs: { x1: '7', y1: '22', x2: '7', y2: '19', class: 'icon-aux' } },
      { tag: 'line', attrs: { x1: '25', y1: '22', x2: '25', y2: '19', class: 'icon-aux' } }
    ]
  },
  HEATMAP_MATRIX: {
    signature: 'heatmap-matrix',
    shapes: [
      { tag: 'rect', attrs: { x: '4', y: '4', width: '7', height: '7', rx: '1', class: 'icon-fill-soft' } },
      { tag: 'rect', attrs: { x: '12.5', y: '4', width: '7', height: '7', rx: '1', class: 'icon-fill' } },
      { tag: 'rect', attrs: { x: '21', y: '4', width: '7', height: '7', rx: '1', class: 'icon-fill-soft' } },
      { tag: 'rect', attrs: { x: '4', y: '12.5', width: '7', height: '7', rx: '1', class: 'icon-fill' } },
      { tag: 'rect', attrs: { x: '12.5', y: '12.5', width: '7', height: '7', rx: '1', class: 'icon-fill-soft' } },
      { tag: 'rect', attrs: { x: '21', y: '12.5', width: '7', height: '7', rx: '1', class: 'icon-fill' } },
      { tag: 'rect', attrs: { x: '4', y: '21', width: '7', height: '7', rx: '1', class: 'icon-fill-soft' } },
      { tag: 'rect', attrs: { x: '12.5', y: '21', width: '7', height: '7', rx: '1', class: 'icon-fill' } },
      { tag: 'rect', attrs: { x: '21', y: '21', width: '7', height: '7', rx: '1', class: 'icon-fill-soft' } }
    ]
  },
  KPI_DETAIL_TABLE: {
    signature: 'kpi-detail-table',
    shapes: [
      { tag: 'rect', attrs: { x: '4', y: '5', width: '24', height: '22', rx: '2' } },
      { tag: 'line', attrs: { x1: '4', y1: '11', x2: '28', y2: '11' } },
      { tag: 'line', attrs: { x1: '4', y1: '17', x2: '28', y2: '17', class: 'icon-aux' } },
      { tag: 'line', attrs: { x1: '4', y1: '23', x2: '28', y2: '23', class: 'icon-aux' } },
      { tag: 'line', attrs: { x1: '16', y1: '11', x2: '16', y2: '27', class: 'icon-aux' } },
      { tag: 'circle', attrs: { cx: '9', cy: '8', r: '1', class: 'icon-fill' } }
    ]
  },
  KPI_RADAR: {
    signature: 'kpi-radar',
    shapes: [
      { tag: 'polygon', attrs: { points: '16,3 28,12 23,27 9,27 4,12', class: 'icon-aux' } },
      { tag: 'polygon', attrs: { points: '16,8 23,13 20,22 12,22 9,13', class: 'icon-fill-soft' } },
      { tag: 'polygon', attrs: { points: '16,8 23,13 20,22 12,22 9,13' } },
      { tag: 'circle', attrs: { cx: '16', cy: '8', r: '1.5', class: 'icon-fill' } }
    ]
  },
  LINE_TREND: {
    signature: 'line-trend',
    shapes: [
      { tag: 'polyline', attrs: { points: '3 24 9 18 14 21 20 10 29 14' } },
      { tag: 'circle', attrs: { cx: '3', cy: '24', r: '1.5', class: 'icon-fill' } },
      { tag: 'circle', attrs: { cx: '9', cy: '18', r: '1.5', class: 'icon-fill' } },
      { tag: 'circle', attrs: { cx: '14', cy: '21', r: '1.5', class: 'icon-fill' } },
      { tag: 'circle', attrs: { cx: '20', cy: '10', r: '1.5', class: 'icon-fill' } },
      { tag: 'circle', attrs: { cx: '29', cy: '14', r: '1.5', class: 'icon-fill' } }
    ]
  },
  LIQUID_PROGRESS: {
    signature: 'liquid-progress',
    shapes: [
      { tag: 'circle', attrs: { cx: '16', cy: '16', r: '12' } },
      { tag: 'path', attrs: { d: 'M5 18c4-3 7 3 11 0s7 3 11 0v7H5Z', class: 'icon-fill-soft' } },
      { tag: 'path', attrs: { d: 'M5 18c4-3 7 3 11 0s7 3 11 0', class: 'icon-aux' } }
    ]
  },
  METRIC_CARD: {
    signature: 'metric-card',
    shapes: [
      { tag: 'rect', attrs: { x: '4', y: '6', width: '24', height: '20', rx: '3' } },
      { tag: 'line', attrs: { x1: '8', y1: '12', x2: '17', y2: '12' } },
      { tag: 'line', attrs: { x1: '8', y1: '17', x2: '21', y2: '17', class: 'icon-aux' } },
      { tag: 'line', attrs: { x1: '8', y1: '22', x2: '14', y2: '22', class: 'icon-aux' } },
      { tag: 'circle', attrs: { cx: '24', cy: '12', r: '1.6', class: 'icon-fill' } }
    ]
  },
  PIE_SHARE: {
    signature: 'pie-share',
    shapes: [
      { tag: 'path', attrs: { d: 'M16 4a12 12 0 1 1-8.5 3.5L16 16Z', class: 'icon-fill-soft' } },
      { tag: 'path', attrs: { d: 'M16 4v12h12' } },
      { tag: 'line', attrs: { x1: '16', y1: '4', x2: '16', y2: '16', class: 'icon-aux' } }
    ]
  },
  PROGRESS_LIST: {
    signature: 'progress-list',
    shapes: [
      { tag: 'line', attrs: { x1: '4', y1: '7', x2: '28', y2: '7', class: 'icon-aux' } },
      { tag: 'line', attrs: { x1: '4', y1: '16', x2: '28', y2: '16', class: 'icon-aux' } },
      { tag: 'line', attrs: { x1: '4', y1: '25', x2: '28', y2: '25', class: 'icon-aux' } },
      { tag: 'rect', attrs: { x: '4', y: '5', width: '16', height: '4', rx: '2', class: 'icon-fill' } },
      { tag: 'rect', attrs: { x: '4', y: '14', width: '21', height: '4', rx: '2', class: 'icon-fill-soft' } },
      { tag: 'rect', attrs: { x: '4', y: '23', width: '11', height: '4', rx: '2', class: 'icon-fill' } }
    ]
  },
  RANK_LIST: {
    signature: 'rank-list',
    shapes: [
      { tag: 'circle', attrs: { cx: '6', cy: '7', r: '2.2', class: 'icon-fill' } },
      { tag: 'circle', attrs: { cx: '6', cy: '16', r: '2.2', class: 'icon-fill-soft' } },
      { tag: 'circle', attrs: { cx: '6', cy: '25', r: '2.2', class: 'icon-fill-soft' } },
      { tag: 'line', attrs: { x1: '12', y1: '7', x2: '28', y2: '7' } },
      { tag: 'line', attrs: { x1: '12', y1: '16', x2: '24', y2: '16', class: 'icon-aux' } },
      { tag: 'line', attrs: { x1: '12', y1: '25', x2: '20', y2: '25', class: 'icon-aux' } }
    ]
  },
  SCATTER_BUBBLE: {
    signature: 'scatter-bubble',
    shapes: [
      { tag: 'line', attrs: { x1: '4', y1: '27', x2: '28', y2: '27', class: 'icon-aux' } },
      { tag: 'line', attrs: { x1: '5', y1: '27', x2: '5', y2: '5', class: 'icon-aux' } },
      { tag: 'circle', attrs: { cx: '10', cy: '20', r: '2.5', class: 'icon-fill-soft' } },
      { tag: 'circle', attrs: { cx: '16', cy: '13', r: '4', class: 'icon-fill' } },
      { tag: 'circle', attrs: { cx: '25', cy: '9', r: '2', class: 'icon-fill-soft' } }
    ]
  },
  SPARKLINE_CARD: {
    signature: 'sparkline-card',
    shapes: [
      { tag: 'rect', attrs: { x: '3', y: '4', width: '26', height: '24', rx: '3' } },
      { tag: 'polyline', attrs: { points: '7 22 12 17 16 19 21 11 26 14' } },
      { tag: 'circle', attrs: { cx: '26', cy: '14', r: '1.6', class: 'icon-fill' } }
    ]
  },
  SUNBURST_CHART: {
    signature: 'sunburst-chart',
    shapes: [
      { tag: 'circle', attrs: { cx: '16', cy: '16', r: '4', class: 'icon-fill' } },
      { tag: 'circle', attrs: { cx: '16', cy: '16', r: '9', class: 'icon-aux' } },
      { tag: 'circle', attrs: { cx: '16', cy: '16', r: '13' } },
      { tag: 'line', attrs: { x1: '16', y1: '3', x2: '16', y2: '7', class: 'icon-aux' } },
      { tag: 'line', attrs: { x1: '16', y1: '25', x2: '16', y2: '29', class: 'icon-aux' } },
      { tag: 'line', attrs: { x1: '3', y1: '16', x2: '7', y2: '16', class: 'icon-aux' } },
      { tag: 'line', attrs: { x1: '25', y1: '16', x2: '29', y2: '16', class: 'icon-aux' } }
    ]
  },
  TABLE_LIST: {
    signature: 'table-list',
    shapes: [
      { tag: 'rect', attrs: { x: '4', y: '5', width: '24', height: '22', rx: '2' } },
      { tag: 'line', attrs: { x1: '4', y1: '11', x2: '28', y2: '11' } },
      { tag: 'line', attrs: { x1: '4', y1: '17', x2: '28', y2: '17', class: 'icon-aux' } },
      { tag: 'line', attrs: { x1: '4', y1: '23', x2: '28', y2: '23', class: 'icon-aux' } },
      { tag: 'line', attrs: { x1: '12', y1: '11', x2: '12', y2: '27', class: 'icon-aux' } },
      { tag: 'line', attrs: { x1: '21', y1: '11', x2: '21', y2: '27', class: 'icon-aux' } }
    ]
  }
};

const FALLBACK = {
  signature: 'generic-chart',
  shapes: [
    { tag: 'rect', attrs: { x: '4', y: '4', width: '24', height: '24', rx: '3' } },
    { tag: 'polyline', attrs: { points: '8 22 13 16 17 19 24 10' } }
  ]
};

const definition = computed(() => ICONS[props.innerType] || FALLBACK);
</script>

<style scoped>
.chart-type-icon { display: block; width: 28px; height: 28px; overflow: visible; }
.icon-fill { fill: currentColor; stroke: none; }
.icon-fill-soft { fill: currentColor; stroke: none; opacity: .42; }
.icon-aux { opacity: .5; }
</style>
