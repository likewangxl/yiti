<template>
  <div class="branch-thumbnail-chart" :class="{ 'is-map': kind === 'INSTITUTION_MAP' }" aria-hidden="true">
    <img v-if="kind === 'INSTITUTION_MAP'" :src="mapArtwork" alt="" data-testid="branch-thumbnail-map-art" draggable="false" />
    <div v-else ref="chartRef" class="branch-thumbnail-chart__canvas" />
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as echarts from 'echarts/core';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import mapArtwork from '@/assets/panorama/branch-thumbnail-map.png';

echarts.use([BarChart, LineChart, PieChart, GridComponent, TooltipComponent, CanvasRenderer]);
const props = defineProps({ kind: { type: String, default: 'METRIC_CARD' } });
const chartRef = ref(null);
let chart, observer;
let disposed = false;
const ramp = (first, last, vertical = false) => ({ type:'linear', x:0, y:0, x2:vertical ? 0 : 1, y2:vertical ? 1 : 0, colorStops:[{offset:0,color:first},{offset:1,color:last}] });

/** 静态轮廓只表达组件视觉类型，所有提示与经营数值均隐藏。 */
function optionFor(kind) {
  const base = { animation:false, tooltip:{show:false} };
  if (['LIST','PROGRESS'].includes(kind)) return {...base,
    grid:{left:5,right:10,top:8,bottom:8},xAxis:{show:false,type:'value',max:100},
    yAxis:{show:false,type:'category',inverse:true,data:['','','','']},
    series:[{type:'bar',silent:true,barWidth:kind==='LIST'?7:10,showBackground:true,backgroundStyle:{color:'#17385e',borderRadius:3},
      itemStyle:{borderRadius:3,color:ramp('#36e2e9','#9a7aef')},label:{show:true,position:'right',formatter:'—',color:'#8eb5d9'},data:[78,62,50,34]}]};
  if (kind === 'INSTITUTION_RANKING') return { ...base,
    grid:{left:30,right:18,top:12,bottom:9}, xAxis:{show:false,type:'value',max:100},
    yAxis:{type:'category',inverse:true,data:['1','2','3','4','5'],axisLine:{show:false},axisTick:{show:false},axisLabel:{color:'#b9d9ff',fontSize:10,margin:10,formatter:value=>`{rank|${value}}`,rich:{rank:{width:17,height:17,borderWidth:1,borderColor:'#5489bc',borderRadius:12,align:'center',color:'#a3ccfa'}}}},
    series:[{type:'bar',silent:true,barWidth:8,showBackground:true,backgroundStyle:{color:'#17385e',borderRadius:4},itemStyle:{borderRadius:4,color:ramp('#3ee3ef','#347ef1')},label:{show:true,position:'right',formatter:'—',color:'#a8cde8',fontSize:10},data:[78,64,51,39,29]}]
  };
  if (kind === 'BUSINESS_GROWTH') return { ...base,
    grid:{left:5,right:5,top:18,bottom:18},
    xAxis:{type:'category',data:['','','','','','','',''],axisLine:{lineStyle:{color:'#315179'}},axisTick:{show:false},axisLabel:{color:'#5b779c',fontSize:9,formatter:()=> '—'}},
    yAxis:{type:'value',max:100,splitNumber:4,axisLine:{show:false},axisTick:{show:false},axisLabel:{show:false},splitLine:{lineStyle:{color:'#183657',width:1}}},
    series:[
      {type:'bar',silent:true,barMaxWidth:13,itemStyle:{color:ramp('#427af0','#163768',true),borderRadius:[2,2,0,0]},data:[20,34,27,48,38,55,45,66]},
      {type:'line',silent:true,smooth:true,showSymbol:false,lineStyle:{color:'#37dfed',width:1.7},areaStyle:{color:ramp('#1b798b44','#07224300',true)},data:[47,60,53,65,55,74,78,88]},
      {type:'line',silent:true,smooth:true,showSymbol:false,lineStyle:{color:'#9c80f4',width:1.6},data:[25,31,42,39,48,46,64,70]}
    ]
  };
  const composition = kind === 'COMPOSITION_TAB';
  return { ...base, series:[{type:'pie',silent:true,radius:['61%','81%'],center:['50%','50%'],label:{show:false},labelLine:{show:false},itemStyle:{borderWidth:0},data:composition
    ? [{value:58,itemStyle:{color:ramp('#2ae8e6','#4290f5')}},{value:42,itemStyle:{color:ramp('#7772ed','#b78bff')}}]
    : [{value:72,itemStyle:{color:ramp('#1ce4e6','#3797e6')}},{value:28,itemStyle:{color:'#143d66'}}]}] };
}
function release() { observer?.disconnect(); chart?.dispose(); observer = null; chart = null; }
async function render() {
  await nextTick();
  if (disposed || !chartRef.value || props.kind === 'INSTITUTION_MAP') return;
  const canvas = document.createElement('canvas');
  try { if (!canvas.getContext?.('2d')) return; } catch { return; }
  chart = echarts.init(chartRef.value);
  chart.setOption(optionFor(props.kind));
  observer = typeof ResizeObserver === 'function' ? new ResizeObserver(() => chart?.resize()) : null;
  observer?.observe(chartRef.value);
}
onMounted(render);
watch(() => props.kind, () => { release(); render(); });
onBeforeUnmount(() => { disposed = true; release(); });
</script>

<style scoped>
.branch-thumbnail-chart { position:relative; width:100%; height:180px; min-width:0; min-height:0; pointer-events:none; }
.branch-thumbnail-chart__canvas { position:absolute; inset:0; }
.branch-thumbnail-chart img { position:absolute; inset:0; width:100%; height:100%; object-fit:contain; display:block; filter:saturate(.85) brightness(.9); }
</style>
