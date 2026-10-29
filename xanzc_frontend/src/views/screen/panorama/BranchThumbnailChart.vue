<template><div ref="chartRef" class="branch-thumbnail-chart" aria-hidden="true" /></template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as echarts from 'echarts/core';
import { BarChart, LineChart, MapChart, PieChart } from 'echarts/charts';
import { GeoComponent, GridComponent, TooltipComponent, VisualMapComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import shaanxiGeoJson from '@/assets/geo/shaanxi.json';

echarts.use([BarChart, LineChart, MapChart, PieChart, GeoComponent, GridComponent, TooltipComponent, VisualMapComponent, CanvasRenderer]);
echarts.registerMap('branch-thumbnail-shaanxi', shaanxiGeoJson);
const props = defineProps({ kind: { type: String, default: 'METRIC_CARD' } });
const chartRef = ref(null);
let chart;
let observer;
let disposed = false;
const palette = ['#35d9ee', '#4d83f2', '#9c7df2', '#f4c34d'];
/** 固定图形仅用于辨识组件类型，不展示坐标值、提示框或任何经营数据。 */
function optionFor(kind) {
  if (kind === 'INSTITUTION_MAP') return { animation: false, tooltip: { show: false }, series: [{ type: 'map', map: 'branch-thumbnail-shaanxi', roam: false, silent: true, selectedMode:false, label:{show:false}, itemStyle:{ areaColor:'#174987', borderColor:'#6e9ad6', borderWidth:1, shadowColor:'#2468c4', shadowBlur:9 }, emphasis:{disabled:true}, data:[] }] };
  if (kind === 'INSTITUTION_RANKING') return { animation:false, grid:{left:0,right:8,top:4,bottom:4}, xAxis:{show:false,type:'value',max:100}, yAxis:{show:false,type:'category',inverse:true,data:['','','','','']}, series:[{type:'bar',silent:true,barWidth:8,showBackground:true,backgroundStyle:{color:'#13375e',borderRadius:4},itemStyle:{borderRadius:4,color:palette[1]},data:[84,70,59,45,32]}] };
  if (kind === 'BUSINESS_GROWTH') return { animation: false, grid: { left: 2, right: 2, top: 4, bottom: 2 }, xAxis: { show: false, type: 'category', data: ['一', '二', '三', '四', '五'] }, yAxis: { show: false, type: 'value' }, series: [{ type: 'line', smooth: true, showSymbol: false, lineStyle: { color: palette[0], width: 2 }, areaStyle: { color: 'rgba(53,217,238,.18)' }, data: [34, 52, 43, 70, 62] }, { type: 'bar', barWidth: 5, itemStyle: { color: palette[1] }, data: [18, 35, 27, 46, 39] }] };
  if (kind === 'COMPOSITION_TAB') return { animation: false, series: [{ type: 'pie', silent: true, radius: ['48%', '72%'], center: ['50%', '52%'], label: { show: false }, data: [{ value: 42, itemStyle: { color: palette[0] } }, { value: 33, itemStyle: { color: palette[2] } }, { value: 25, itemStyle: { color: palette[1] } }] }] };
  if (kind === 'COMPLETION') return { animation: false, series: [{ type: 'pie', silent: true, radius: ['52%', '72%'], center: ['50%', '52%'], label: { show: false }, data: [{ value: 68, itemStyle: { color: palette[0] } }, { value: 32, itemStyle: { color: '#164477' } }] }] };
  return { animation: false, grid: { left: 2, right: 2, top: 4, bottom: 2 }, xAxis: { show: false, type: 'category', data: ['1', '2', '3', '4'] }, yAxis: { show: false, type: 'value' }, series: [{ type: 'bar', silent: true, barWidth: '45%', itemStyle: { color: palette[1], borderRadius: 2 }, data: [46, 72, 58, 86] }] };
}
function render() {
  if (disposed || !chartRef.value) return;
  const canvas = document.createElement('canvas');
  try { if (typeof canvas.getContext !== 'function' || !canvas.getContext('2d')) return; } catch { return; }
  chart ||= echarts.init(chartRef.value);
  chart.setOption(optionFor(props.kind), true);
  chart.resize();
}
onMounted(async () => { await nextTick(); if (disposed) return; render(); observer = typeof ResizeObserver === 'function' ? new ResizeObserver(() => chart?.resize()) : null; if (chartRef.value) observer?.observe(chartRef.value); });
watch(() => props.kind, render);
onBeforeUnmount(() => { disposed = true; observer?.disconnect(); chart?.dispose(); chart = null; });
</script>

<style scoped>
.branch-thumbnail-chart { position:relative; width:100%; height:150px; min-width:0; pointer-events:none; }
</style>
