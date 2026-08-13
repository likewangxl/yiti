<template>
  <div class="scr-block mp-block" :class="{ 'mp-composite': isComposite }">
    <div v-if="unsupportedSchema" class="mp-config-gap" role="alert">地图配置版本不受支持，已拒绝渲染</div>
    <template v-else-if="isComposite">
      <div class="mp-title">西安六区经营地图</div>
      <div v-if="configurationGap" class="mp-config-gap" role="alert">{{ configurationGap }}</div>
      <div class="mp-composite-canvas">
        <v-chart class="mp-chart mp-xian-chart" :option="option" autoresize @click="onChartClick" />

        <!-- ECharts canvas 无法自然获得焦点，本地机构补充可读的键盘入口；经纬度仍只进入 geo 数据。 -->
        <button v-for="(point, index) in localNodes" :key="`local-${point.orgCode}`"
                class="mp-local-node" :style="localStyle(point, index)" :tabindex="isInteractive ? 0 : -1"
                :disabled="!isInteractive" :aria-disabled="String(!isInteractive)"
                :aria-label="localAriaLabel(point)"
                @click="navigate(point)" @keydown.enter.prevent="navigate(point)"
                @keydown.space.prevent="navigate(point)">
          <span class="mp-node-dot" aria-hidden="true" />
          <span class="mp-node-label">{{ point.orgName }}</span>
        </button>

        <!-- 示意节点使用 anchor 绝对布局，不把锚点转换成伪造经纬度。 -->
        <button v-for="node in satelliteNodes" :key="`sat-${node.orgCode}-${node.anchor}`"
                class="mp-satellite-node" :data-anchor="node.anchor" :style="node.position" :tabindex="isInteractive ? 0 : -1"
                :disabled="!isInteractive" :aria-disabled="String(!isInteractive)"
                :aria-label="satelliteAriaLabel(node)"
                @click="navigate(node)" @keydown.enter.prevent="navigate(node)"
                @keydown.space.prevent="navigate(node)">
          <span class="mp-node-dot" aria-hidden="true" />
          <span class="mp-node-label">{{ node.orgName }}</span>
        </button>
      </div>
      <!-- 该声明是复合地图契约的一部分，不能由配置隐藏。 -->
      <div class="mp-disclaimer" role="note">{{ XIAN_SECONDARY_BRANCH_DISCLAIMER }}</div>
      <div class="mp-attribution" role="note">
        <a class="mp-attribution-link" href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">{{ XIAN_SIX_DISTRICTS_ATTRIBUTION }}</a>
      </div>
    </template>
    <v-chart v-else class="mp-chart" :option="option" autoresize @click="onChartClick" />
  </div>
</template>

<script setup>
import { computed, inject } from 'vue';
import { use, registerMap } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { MapChart, EffectScatterChart } from 'echarts/charts';
import { GeoComponent, TooltipComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { useRouter } from 'vue-router';
import shaanxiGeo from '@/assets/geo/shaanxi.json';
import xianSixDistricts, { XIAN_SIX_DISTRICTS_ATTRIBUTION } from '@/assets/geo/xian-six-districts';
import { SCR_COLOR, scrTooltipStyle } from '@/styles/screenChartTheme';
import { MAP_ANCHORS, normalizeMapConfig, resolveCompositeMapNodes, resolveXianSecondaryBranches,
  XIAN_SECONDARY_BRANCH_DISCLAIMER } from '@/utils/screenScope';

use([CanvasRenderer, MapChart, EffectScatterChart, GeoComponent, TooltipComponent]);
registerMap('shaanxi', shaanxiGeo);
registerMap('xian-six-districts', xianSixDistricts);

const props = defineProps({
  // v1 发布包：服务端旧 mapPoints 结构。
  mapPoints: { type: Array, default: () => [] },
  // v2 组件配置；设计态也可通过 element.propValue 传入。
  mapConfig: { type: Object, default: null },
  profiles: { type: Array, default: () => [] },
  mapPayload: { type: Object, default: null },
  element: { type: Object, default: null },
  mode: { type: String, default: 'runtime' }
});
const router = useRouter();
const injectedProfiles = inject('screenProfiles', null);

const rawConfig = computed(() => {
  // 运行态一旦收到授权 mapPayload，就必须按该包自身的 schema/mode 判定，不能由画布配置掩盖缺字段。
  if (props.mode !== 'design' && props.mapPayload) return props.mapPayload;
  if (props.mapConfig) return props.mapConfig;
  if (props.mapPayload?.mapConfig) return props.mapPayload.mapConfig;
  if (props.mapPayload?.config) return props.mapPayload.config;
  // 后端 ScreenMapRenderPackageDTO 必须自行携带原生 schemaVersion=2 与显式 mode；不从 mode 推断版本。
  if (props.mapPayload) return props.mapPayload;
  return props.element?.propValue || {};
});
const mapConfig = computed(() => normalizeMapConfig(rawConfig.value));
const isComposite = computed(() => mapConfig.value.schemaVersion === 2 && mapConfig.value.mode === 'XIAN_COMPOSITE');
const runtimePayloadContractValid = computed(() => props.mode === 'design' || !props.mapPayload
  || (props.mapPayload.schemaVersion === 2 && props.mapPayload.mode === 'XIAN_COMPOSITE'));
const unsupportedSchema = computed(() => !runtimePayloadContractValid.value || mapConfig.value.mode === 'UNSUPPORTED');
const isInteractive = computed(() => props.mode !== 'design');
const profileList = computed(() => {
  if (props.profiles.length) return props.profiles;
  const fromPayload = props.mapPayload?.profiles || props.mapPayload?.orgProfiles || props.mapPayload?.localPoints;
  if (Array.isArray(fromPayload) && fromPayload.length) return fromPayload;
  if (Array.isArray(injectedProfiles)) return injectedProfiles;
  return Array.isArray(injectedProfiles?.value) ? injectedProfiles.value : [];
});
const resolved = computed(() => resolveCompositeMapNodes(mapConfig.value, profileList.value));
// 后端 mapPackage 的 localPoints 已完成机构组/画像授权，字段没有 selector 元数据，直接展示。
const localNodes = computed(() => {
  if (props.mapPayload?.mode === 'XIAN_COMPOSITE' && Array.isArray(props.mapPayload.localPoints)) {
    return props.mapPayload.localPoints.filter(point => point?.orgCode && point?.lng != null && point?.lat != null);
  }
  return resolved.value.local;
});
const satelliteNodes = computed(() => {
  if (props.mapPayload?.mode === 'XIAN_COMPOSITE' && Array.isArray(props.mapPayload.satelliteNodes)) {
    return resolveXianSecondaryBranches(props.mapPayload.satelliteNodes);
  }
  return resolveXianSecondaryBranches(resolved.value.satellite);
});
const configurationGap = computed(() => {
  if (props.mode !== 'design' || !isComposite.value) return '';
  if (!profileList.value.length) return '设计态未加载机构画像，暂不生成假点位；请先配置并授权机构画像。';
  const selector = mapConfig.value.localSelector || {};
  const missing = profileList.value.filter(profile => {
    const status = profile.status ?? profile.recordStatus;
    if (!(status === undefined || status === null || status === ''
      || status === 'ACTIVE' || status === 0 || status === '0' || status === true)) return false;
    const cityCode = String(profile.cityCode ?? profile.city_code ?? '');
    const level = String(profile.operatingLevel ?? profile.operating_level ?? '');
    const local = cityCode === String(selector.cityCode || '610100')
      && level.toUpperCase() === String(selector.operatingLevel || 'PRIMARY').toUpperCase();
    if (!local) return false;
    const lng = Number(profile.lng ?? profile.longitude);
    const lat = Number(profile.lat ?? profile.latitude);
    return String(profile.coordSys ?? profile.coord_sys ?? '').toUpperCase() !== 'GCJ02'
      || !Number.isFinite(lng) || !Number.isFinite(lat);
  });
  if (missing.length) {
    return `以下本地一级经营机构缺少合法 GCJ-02 坐标：${missing.map(x => x.orgName || x.orgCode).join('、')}`;
  }
  const missingAnchors = MAP_ANCHORS.filter(anchor => !(mapConfig.value.satelliteNodes || []).some(node => node.anchor === anchor));
  if (missingAnchors.length) return `请配置示意节点：${missingAnchors.join('、')}`;
  return '';
});

const legacyPoints = computed(() => {
  if (Array.isArray(props.mapPayload?.mapPoints)) return props.mapPayload.mapPoints;
  return props.mapPoints;
});

const mapName = computed(() => isComposite.value ? 'xian-six-districts' : 'shaanxi');
const points = computed(() => isComposite.value ? localNodes.value : legacyPoints.value);

const option = computed(() => ({
  tooltip: scrTooltipStyle(),
  geo: {
    map: mapName.value,
    roam: false,
    layoutCenter: ['50%', '53%'],
    layoutSize: isComposite.value ? '72%' : '92%',
    label: { show: true, color: '#a9c7f5', fontSize: isComposite.value ? 11 : 12 },
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
    name: isComposite.value ? '西安本地一级经营机构' : '支行',
    type: 'effectScatter',
    coordinateSystem: 'geo',
    symbolSize: isComposite.value ? 12 : 14,
    rippleEffect: { brushType: 'stroke', scale: 3.2 },
    label: { show: !isComposite.value, position: 'right', color: SCR_COLOR.gold, fontSize: 13,
      formatter: p => p.name },
    itemStyle: { color: SCR_COLOR.gold, shadowColor: 'rgba(255,215,106,.8)', shadowBlur: 10 },
    tooltip: { formatter: p => `${p.name}<br/>点击进入机构详情屏` },
    data: points.value.map(p => ({
      name: p.orgName,
      value: [Number(p.lng), Number(p.lat)],
      orgCode: p.orgCode,
      target: p.targetScreenCode || 'SCR_BRANCH'
    }))
  }]
}));

function satelliteAriaLabel(node) {
  return isInteractive.value
    ? `${node.orgName}，${XIAN_SECONDARY_BRANCH_DISCLAIMER}，按 Enter 或空格进入机构详情屏`
    : `${node.orgName}，${XIAN_SECONDARY_BRANCH_DISCLAIMER}，设计预览不可钻取`;
}

function localAriaLabel(point) {
  return isInteractive.value
    ? `${point.orgName}，西安本地一级经营机构，按 Enter 或空格进入机构详情屏`
    : `${point.orgName}，西安本地一级经营机构，设计预览不可钻取`;
}

// 仅用于键盘入口的视觉定位；真实经纬度仍保存在 ECharts geo value。
function localStyle(point, index) {
  const lng = Number(point.lng);
  const lat = Number(point.lat);
  // 机构坐标可能跨越较窄范围，使用稳定的弹性网格避免节点全部重叠。
  const left = Number.isFinite(lng) ? Math.max(20, Math.min(80, 50 + (lng - 108.94) * 35)) : 45 + (index % 3) * 5;
  const top = Number.isFinite(lat) ? Math.max(22, Math.min(78, 50 - (lat - 34.26) * 35)) : 42 + (index % 3) * 8;
  return { left: `${left}%`, top: `${top}%` };
}

function navigate(node) {
  if (!isInteractive.value || !node?.orgCode) return;
  router.push({ path: `/screen/${node.targetScreenCode || node.target || 'SCR_BRANCH'}`, query: { orgCode: node.orgCode } });
}

// ECharts 点击仍是旧 v1 和鼠标点击 v2 本地图点的兼容入口。
function onChartClick(params) {
  if (params?.seriesType !== 'effectScatter') return;
  const d = params.data || {};
  if (d.orgCode) navigate({ orgCode: d.orgCode, targetScreenCode: d.target || 'SCR_BRANCH' });
}
</script>

<style scoped>
.mp-block { height: 100%; position: relative; overflow: hidden; }
.mp-chart { width: 100%; height: 100%; }
.mp-composite { display: flex; flex-direction: column; }
.mp-title { flex: none; text-align: center; color: #d5e6ff; font-size: 15px; line-height: 28px; letter-spacing: 1px; }
.mp-config-gap { flex: none; margin: 0 10px 4px; padding: 4px 8px; border: 1px dashed rgba(255, 190, 80, .65); color: #ffd76a; font-size: 11px; line-height: 1.4; }
.mp-composite-canvas { position: relative; flex: 1; min-height: 0; overflow: hidden; }
.mp-xian-chart { position: absolute; inset: 0; }
.mp-satellite-node, .mp-local-node {
  position: absolute; z-index: 2; display: inline-flex; align-items: center; gap: 5px;
  padding: 3px 6px; border: 1px solid rgba(255, 215, 106, .7); border-radius: 12px;
  background: rgba(5, 14, 43, .86); color: #ffd76a; font: inherit; font-size: 12px;
  cursor: pointer; transform: translate(-50%, -50%); white-space: nowrap;
}
.mp-satellite-node { transform: translate(-50%, -50%); }
.mp-satellite-node[data-anchor="LEFT"] { transform: translate(0, -50%); }
.mp-satellite-node[data-anchor="RIGHT"] { transform: translate(-100%, -50%); }
.mp-satellite-node[data-anchor="TOP"], .mp-satellite-node[data-anchor="FAR_TOP"] { transform: translate(-50%, 0); }
.mp-satellite-node:focus-visible, .mp-local-node:focus-visible { outline: 2px solid #00e5ff; outline-offset: 2px; }
.mp-node-dot { width: 7px; height: 7px; flex: none; border-radius: 50%; background: #ffd76a; box-shadow: 0 0 8px rgba(255,215,106,.9); }
.mp-node-label { max-width: 128px; overflow: hidden; text-overflow: ellipsis; }
.mp-disclaimer, .mp-attribution { flex: none; text-align: center; color: #9bb6df; font-size: 11px; letter-spacing: .5px; }
.mp-disclaimer { line-height: 20px; }
.mp-attribution { color: #7898c4; line-height: 16px; padding-bottom: 3px; }
.mp-attribution-link { color: inherit; text-decoration: underline; text-underline-offset: 2px; }
.mp-satellite-node:disabled, .mp-local-node:disabled { cursor: default; opacity: .82; }
</style>
