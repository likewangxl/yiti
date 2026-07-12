<template>
  <div class="scr-block mp-block">
    <v-chart class="mp-chart" :option="option" autoresize @click="onChartClick" />
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { use, registerMap } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { MapChart, EffectScatterChart } from 'echarts/charts';
import { GeoComponent, TooltipComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { useRouter } from 'vue-router';
import shaanxiGeo from '@/assets/geo/shaanxi.json';
import { SCR_COLOR, scrTooltipStyle } from '@/styles/screenChartTheme';

use([CanvasRenderer, MapChart, EffectScatterChart, GeoComponent, TooltipComponent]);
registerMap('shaanxi', shaanxiGeo);

const props = defineProps({
  mapPoints: { type: Array, default: () => [] }
});
const router = useRouter();

const option = computed(() => ({
  tooltip: scrTooltipStyle(),
  geo: {
    map: 'shaanxi',
    roam: false,
    layoutCenter: ['50%', '52%'],
    layoutSize: '92%',
    label: { show: true, color: '#7d9bc9', fontSize: 12 },
    itemStyle: {
      areaColor: 'rgba(13, 40, 96, .8)',
      borderColor: 'rgba(0, 229, 255, .6)',
      borderWidth: 1.2,
      shadowColor: 'rgba(0, 229, 255, .35)',
      shadowBlur: 16
    },
    emphasis: {
      label: { color: '#fff' },
      itemStyle: { areaColor: 'rgba(0, 229, 255, .25)' }
    }
  },
  series: [{
    name: '支行',
    type: 'effectScatter',
    coordinateSystem: 'geo',
    symbolSize: 14,
    rippleEffect: { brushType: 'stroke', scale: 3.2 },
    label: { show: true, position: 'right', color: SCR_COLOR.gold, fontSize: 13,
             formatter: p => p.name },
    itemStyle: { color: SCR_COLOR.gold, shadowColor: 'rgba(255,215,106,.8)', shadowBlur: 10 },
    tooltip: { formatter: p => `${p.name}<br/>点击进入支行大屏` },
    data: props.mapPoints.map(p => ({
      name: p.orgName,
      value: [Number(p.lng), Number(p.lat)],
      orgCode: p.orgCode,
      target: p.targetScreenCode || 'SCR_BRANCH'
    }))
  }]
}));

// 点击支行点位 → 跳对应支行详情屏（需求 3.1）
function onChartClick(p) {
  if (p?.seriesType !== 'effectScatter') return;
  const d = p.data || {};
  if (d.orgCode) {
    router.push({ path: `/screen/${d.target}`, query: { orgCode: d.orgCode } });
  }
}
</script>

<style scoped>
.mp-block { height: 100%; }
.mp-chart { width: 100%; height: 100%; }
</style>
