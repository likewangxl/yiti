<template>
  <div class="scr-block mp-block" :class="{ 'mp-composite': isComposite || isCityDistrict }" :style="runtimeRootStyle">
    <div v-if="unsupportedSchema" class="mp-config-gap" role="alert">地图配置版本不受支持，已拒绝渲染</div>
    <div v-else class="mp-kpi-toolbar" aria-label="地图经营指标">
      <div class="mp-kpi-tabs">
        <button v-for="metric in MAP_METRIC_OPTIONS" :key="metric.code" type="button"
                :data-metric="metric.code" :class="{ active: selectedMetricCode === metric.code }"
                @click.stop="selectedMetricCode = metric.code">{{ metric.label }}</button>
      </div>
      <div class="mp-kpi-meta">
        <span><i class="legend excellent" />达标</span><span><i class="legend normal" />90%-99.9%</span>
        <span><i class="legend warning" />80%-89.9%</span><span><i class="legend risk" />低于80%</span>
        <span><i class="legend missing" />暂无数据</span>
        <strong v-if="usingSimulatedMetrics" class="mp-demo-badge">模拟演示</strong>
        <em>{{ usingSimulatedMetrics ? '指标为模拟数据' : `数据日期 ${latestDataDate || '--'}` }}</em>
      </div>
    </div>
    <template v-if="!unsupportedSchema && isComposite">
      <div class="mp-title">西安六区经营地图</div>
      <div v-if="configurationGap" class="mp-config-gap" role="alert">{{ configurationGap }}</div>
      <div class="mp-composite-canvas">
        <v-chart class="mp-chart mp-xian-chart" :option="option" autoresize @click="onChartClick" />

        <!-- 地图内只保留真实经纬度点，名称和指标在两侧分栏，并由引导线连接，避免标签遮挡行政区。 -->
        <template v-for="callout in localCallouts" :key="`local-${callout.point.orgCode}`">
          <span class="mp-leader-line" :style="callout.lineStyle" aria-hidden="true" />
          <button class="mp-local-node mp-callout" :class="`side-${callout.side}`"
                :style="callout.labelStyle" :tabindex="isInteractive ? 0 : -1"
                :disabled="!isInteractive" :aria-disabled="String(!isInteractive)"
                :aria-label="localAriaLabel(callout.point, callout.rate)"
                @click="navigate(callout.point)" @keydown.enter.prevent="navigate(callout.point)"
                @keydown.space.prevent="navigate(callout.point)">
          <span class="mp-node-dot" aria-hidden="true" />
          <span class="mp-node-label">{{ callout.point.orgName }}</span>
          <strong class="mp-node-rate">{{ formatMetricValue(callout.rate) }}%</strong>
          </button>
        </template>

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
    <template v-else-if="!unsupportedSchema && isCityDistrict">
      <div class="mp-title">{{ selectedRegion.shortName }}区县经营地图</div>
      <div class="mp-composite-canvas">
        <v-chart class="mp-chart" :option="option" autoresize @click="onChartClick" />
      </div>
      <div class="mp-attribution" role="note">
        <a class="mp-attribution-link" :href="SHAANXI_CITY_BOUNDARY_SOURCE_URL"
           target="_blank" rel="noopener noreferrer">{{ SHAANXI_CITY_BOUNDARY_ATTRIBUTION }}</a>
      </div>
    </template>
    <template v-else-if="!unsupportedSchema">
      <div class="mp-title">陕西省经营指标地图</div>
      <div class="mp-composite-canvas">
        <v-chart class="mp-chart" :option="option" autoresize @click="onChartClick" />
      </div>
    </template>
    <aside v-if="detailOpen" class="mp-region-detail" aria-live="polite">
      <div class="mp-region-detail-head">
        <div><strong>{{ selectedRegionName }}支行指标明细</strong><small>支行名称来自机构表；带“演示”标识的坐标与指标为模拟数据</small></div>
        <button type="button" aria-label="关闭区域明细" @click="detailOpen = false">×</button>
      </div>
      <div class="mp-region-summary">
        <span>支行 {{ branchDetailRows.length }} 家</span>
        <span>{{ selectedMetric.title }}</span>
        <span v-if="branchDetailRows.some(row => row.simulated)" class="mp-demo-text">缺失指标使用模拟值</span>
      </div>
      <div v-if="branchDetailRows.length" class="mp-region-table-wrap">
        <table>
          <thead><tr><th>支行</th><th>位置</th><th>完成率</th><th>实际/目标</th><th>来源</th></tr></thead>
          <tbody>
            <tr v-for="row in branchDetailRows" :key="row.orgCode">
              <td><b>{{ row.orgName }}</b><small>{{ row.orgCode }}</small></td>
              <td>{{ row.location }}</td>
              <td><strong :style="{ color: achievementColor(row.rate) }">{{ formatMetricValue(row.rate) }}%</strong></td>
              <td>{{ formatMetricValue(row.actual) }} / {{ formatMetricValue(row.target) }}{{ selectedMetric.unit || '' }}</td>
              <td><span :class="row.simulated || row.simulatedLocation ? 'source-simulated' : 'source-real'">{{ row.sourceLabel }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="mp-region-empty">数据库中暂无带坐标的支行画像，请先在“机构经营画像”维护经纬度。</div>
    </aside>
  </div>
</template>

<script setup>
import { computed, inject, ref } from 'vue';
import { use, registerMap } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { MapChart, EffectScatterChart } from 'echarts/charts';
import { GeoComponent, TooltipComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { useRouter } from 'vue-router';
import shaanxiGeo from '@/assets/geo/shaanxi.json';
import xianSixDistricts, { XIAN_SIX_DISTRICTS_ATTRIBUTION } from '@/assets/geo/xian-six-districts';
import { SHAANXI_CITY_DISTRICT_GEO, SHAANXI_CITY_BOUNDARY_ATTRIBUTION,
  SHAANXI_CITY_BOUNDARY_SOURCE_URL, cityDistrictMapName } from '@/assets/geo/shaanxi-city-districts';
import { SCR_COLOR, scrTooltipStyle } from '@/styles/screenChartTheme';
import { MAP_ANCHORS, normalizeMapConfig, resolveCompositeMapNodes, resolveXianSecondaryBranches,
  XIAN_SECONDARY_BRANCH_DISCLAIMER, SHAANXI_MAP_REGIONS } from '@/utils/screenScope';
import { MAP_METRIC_OPTIONS, achievementColor, featureContainsPoint, finiteNumber,
  formatMetricValue, simulatedMetric } from '@/utils/mapMetrics';

use([CanvasRenderer, MapChart, EffectScatterChart, GeoComponent, TooltipComponent]);
registerMap('shaanxi', shaanxiGeo);
registerMap('xian-six-districts', xianSixDistricts);
for (const [code, geo] of Object.entries(SHAANXI_CITY_DISTRICT_GEO)) {
  registerMap(cityDistrictMapName(code), geo);
}

// 陕西十地市使用稳定、低饱和的暗色分区。颜色只表达地理区分，不承载业务状态，
// 因此不能按接口返回顺序动态分配，避免同一城市在不同大屏中颜色漂移。
const SHAANXI_REGION_COLORS = [
  ['西安市', '#50406f', '#68548e'],
  ['铜川市', '#316c5a', '#3f8871'],
  ['宝鸡市', '#7a4b45', '#965f57'],
  ['咸阳市', '#305d85', '#3c73a2'],
  ['渭南市', '#766336', '#917b44'],
  ['延安市', '#1d6c76', '#278691'],
  ['汉中市', '#3e6b47', '#50865c'],
  ['榆林市', '#4f568d', '#636caf'],
  ['安康市', '#286a83', '#3383a0'],
  ['商洛市', '#6a436d', '#865589']
];

// 西安六区同样按行政区名固定映射颜色。六种低饱和暗色在深色大屏上容易区分，
// 但不借颜色表达经营好坏，避免与后续业务状态色产生语义冲突。
const XIAN_DISTRICT_COLORS = [
  ['未央区', '#285b73', '#367792'],
  ['莲湖区', '#684d78', '#84639a'],
  ['新城区', '#2f6e5a', '#3d8a70'],
  ['碑林区', '#7a5445', '#986a58'],
  ['雁塔区', '#3f5484', '#516aa3'],
  ['长安区', '#6f6538', '#8b7e49']
];

// 最多覆盖咸阳的 14 个区县；按 GeoJSON 固定顺序配色，同一份边界资产不会因接口数据顺序改变颜色。
const CITY_DISTRICT_PALETTE = [
  ['#285b73', '#367792'], ['#684d78', '#84639a'], ['#2f6e5a', '#3d8a70'],
  ['#7a5445', '#986a58'], ['#3f5484', '#516aa3'], ['#58693e', '#70834f'],
  ['#336a7d', '#42859a'], ['#704663', '#8b5879'], ['#37634b', '#477c5e'],
  ['#745d38', '#907548'], ['#405f78', '#527792'], ['#5b4c78', '#725f94'],
  ['#2d6a69', '#398482'], ['#6d4d45', '#875f56']
];

const props = defineProps({
  // v1 发布包：服务端旧 mapPoints 结构。
  mapPoints: { type: Array, default: () => [] },
  regionMetrics: { type: Array, default: () => [] },
  // v2 组件配置；设计态也可通过 element.propValue 传入。
  mapConfig: { type: Object, default: null },
  profiles: { type: Array, default: () => [] },
  mapPayload: { type: Object, default: null },
  element: { type: Object, default: null },
  mode: { type: String, default: 'runtime' },
  // ScreenRenderer 根据组件在 1920×1080 设计稿中的 top 计算；设计态与陕西 v1 均忽略。
  runtimeHeaderInset: { type: Number, default: 0 }
});
const router = useRouter();
const injectedProfiles = inject('screenProfiles', null);
const injectedRegionMetrics = inject('screenRegionMetrics', null);

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
const selectedMetricCode = ref(MAP_METRIC_OPTIONS.some(item => item.code === mapConfig.value.metricCode)
  ? mapConfig.value.metricCode : MAP_METRIC_OPTIONS[0].code);
const selectedMetric = computed(() => MAP_METRIC_OPTIONS.find(item => item.code === selectedMetricCode.value)
  || MAP_METRIC_OPTIONS[0]);
const selectedRegionName = ref('');
const detailOpen = ref(false);
const isComposite = computed(() => mapConfig.value.schemaVersion === 2 && mapConfig.value.mode === 'XIAN_COMPOSITE');
const selectedRegion = computed(() => SHAANXI_MAP_REGIONS.find(region => region.code === mapConfig.value.regionCode)
  || SHAANXI_MAP_REGIONS[0]);
const selectedCityGeo = computed(() => SHAANXI_CITY_DISTRICT_GEO[selectedRegion.value.code] || null);
const isCityDistrict = computed(() => mapConfig.value.schemaVersion === 1
  && mapConfig.value.mode === 'SHAANXI_LEGACY' && Boolean(selectedCityGeo.value));
const runtimeRootStyle = computed(() => {
  if (props.mode === 'design' || !isComposite.value || props.runtimeHeaderInset <= 0) return {};
  return { paddingTop: `${props.runtimeHeaderInset}px`, boxSizing: 'border-box' };
});
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
const metricRows = computed(() => {
  if (props.regionMetrics.length) return props.regionMetrics;
  const value = injectedRegionMetrics?.value ?? injectedRegionMetrics;
  return Array.isArray(value) ? value : [];
});
const latestDataDate = computed(() => metricRows.value.map(row => row?.dataDate).filter(Boolean).sort().at(-1) || '');
const usingSimulatedMetrics = computed(() => metricRows.value.length === 0);

const mapName = computed(() => {
  if (isComposite.value) return 'xian-six-districts';
  if (isCityDistrict.value) return cityDistrictMapName(selectedRegion.value.code);
  return 'shaanxi';
});
const points = computed(() => isComposite.value ? localNodes.value : legacyPoints.value);
// 复合地图只占中央区域：网点短引导标注贴着地图分布，左右外侧留给其他大屏组件。
const mapLayoutSize = computed(() => isComposite.value ? '46%' : (isCityDistrict.value ? '80%' : '92%'));
const cityDistrictRegions = computed(() => (selectedCityGeo.value?.features || []).map((feature, index) => {
  const [areaColor, hoverColor] = CITY_DISTRICT_PALETTE[index % CITY_DISTRICT_PALETTE.length];
  return [feature.properties?.name, areaColor, hoverColor];
}).filter(([name]) => Boolean(name)));
const featureList = computed(() => (isComposite.value ? xianSixDistricts.features
  : (isCityDistrict.value ? (selectedCityGeo.value?.features || []) : shaanxiGeo.features)) || []);
const pointByOrg = computed(() => new Map(points.value.filter(point => point?.orgCode)
  .map(point => [String(point.orgCode), point])));
const regionMetricMap = computed(() => {
  const rows = new Map();
  for (const row of metricRows.value) {
    let feature = null;
    if (row?.regionCode) {
      feature = featureList.value.find(item => String(item.properties?.adcode || item.properties?.code || '') === String(row.regionCode));
    } else if (!isComposite.value && !isCityDistrict.value) {
      const point = pointByOrg.value.get(String(row?.orgCode));
      const lng = finiteNumber(point?.lng);
      const lat = finiteNumber(point?.lat);
      if (lng !== null && lat !== null) feature = featureList.value.find(item => featureContainsPoint(item, [lng, lat]));
    }
    const name = feature?.properties?.name;
    if (name && !rows.has(name)) rows.set(name, row);
  }
  const ranked = [...rows.entries()].filter(([, row]) => finiteNumber(row?.metricValues?.[selectedMetricCode.value]) !== null)
    .sort((a, b) => Number(b[1].metricValues[selectedMetricCode.value]) - Number(a[1].metricValues[selectedMetricCode.value]));
  const ranks = new Map(ranked.map(([name], index) => [name, index + 1]));
  return new Map([...rows].map(([name, row]) => [name, { row, rank: ranks.get(name) || null }]));
});
function metricDetail(name) {
  const entry = regionMetricMap.value.get(name);
  const values = entry?.row?.metricValues || {};
  const rate = finiteNumber(values[selectedMetricCode.value]);
  const actual = finiteNumber(values[selectedMetric.value.actualCode]);
  if (!entry && usingSimulatedMetrics.value) {
    return { entry: null, ...simulatedMetric(name, selectedMetricCode.value), simulated: true };
  }
  const target = actual !== null && rate !== null && rate > 0 ? actual / (rate / 100) : null;
  return { entry, rate, actual, target, simulated: false,
    gap: actual !== null && target !== null ? Math.max(target - actual, 0) : null,
    yoy: finiteNumber(values[selectedMetric.value.yoyCode]), mom: finiteNumber(values[selectedMetric.value.momCode]) };
}
const mapRegions = computed(() => (isComposite.value
  ? XIAN_DISTRICT_COLORS
  : (isCityDistrict.value ? cityDistrictRegions.value : SHAANXI_REGION_COLORS))
  .map(([name, areaColor, hoverColor]) => ({
  name,
  // 演示态保留每个行政区的独立底色，完成率直接写在标签；真实指标到达后切换为统一状态色。
  itemStyle: { areaColor: metricRows.value.length ? achievementColor(metricDetail(name).rate) : areaColor,
    borderColor: achievementColor(metricDetail(name).rate) },
  emphasis: { itemStyle: { areaColor: metricRows.value.length ? achievementColor(metricDetail(name).rate) : hoverColor } }
})));

const orgMetricMap = computed(() => new Map(metricRows.value.filter(row => row?.orgCode)
  .map(row => [String(row.orgCode), row])));
function localMetric(point) {
  const values = orgMetricMap.value.get(String(point.orgCode))?.metricValues || {};
  const rate = finiteNumber(values[selectedMetricCode.value]);
  return rate ?? simulatedMetric(point.orgCode || point.orgName, selectedMetricCode.value).rate;
}
function geoPercent(point, index) {
  const lng = Number(point.lng);
  const lat = Number(point.lat);
  return {
    left: Number.isFinite(lng) ? Math.max(20, Math.min(80, 50 + (lng - 108.94) * 35)) : 45 + (index % 3) * 5,
    top: Number.isFinite(lat) ? Math.max(22, Math.min(78, 50 - (lat - 34.26) * 35)) : 42 + (index % 3) * 8
  };
}
function calloutTop(index, count) {
  const upperCount = Math.ceil(count / 2);
  if (index < upperCount) return upperCount === 1 ? 30 : 18 + index * (24 / (upperCount - 1));
  const lowerCount = count - upperCount;
  const lowerIndex = index - upperCount;
  return lowerCount <= 1 ? 70 : 58 + lowerIndex * (24 / (lowerCount - 1));
}
function leaderStyle(pointPosition, labelTop, side, color) {
  const targetLeft = side === 'left' ? 39 : 61;
  const deltaX = targetLeft - pointPosition.left;
  const deltaY = labelTop - pointPosition.top;
  // 设计器画布约为 16:9，折算纵向百分比后旋转，缩放时仍能稳定指向两端。
  const canvasAspect = 1.65;
  const width = Math.sqrt(deltaX ** 2 + (deltaY / canvasAspect) ** 2);
  const angle = Math.atan2(deltaY / canvasAspect, deltaX) * 180 / Math.PI;
  return {
    left: `${pointPosition.left}%`, top: `${pointPosition.top}%`, width: `${width}%`,
    transform: `rotate(${angle}deg)`, '--callout-color': color
  };
}
const localCallouts = computed(() => {
  const sorted = [...localNodes.value].sort((left, right) => Number(right.lat) - Number(left.lat));
  const sides = { left: [], right: [] };
  sorted.forEach((point, index) => sides[index % 2 ? 'right' : 'left'].push(point));
  return ['left', 'right'].flatMap(side => sides[side].map((point, index) => {
    const labelTop = calloutTop(index, sides[side].length);
    const sourceIndex = localNodes.value.indexOf(point);
    const pointPosition = geoPercent(point, sourceIndex);
    const rate = localMetric(point);
    const color = achievementColor(rate);
    return {
      point, rate, side,
      labelStyle: { top: `${labelTop}%`, [side]: '24%', '--callout-color': color },
      lineStyle: leaderStyle(pointPosition, labelTop, side, color)
    };
  }));
});
const selectedFeature = computed(() => featureList.value.find(feature => feature.properties?.name === selectedRegionName.value));
const branchDetailRows = computed(() => {
  if (!selectedFeature.value) return [];
  return profileList.value.filter(profile => {
    const status = String(profile?.status ?? profile?.recordStatus ?? 'ACTIVE').toUpperCase();
    const lng = finiteNumber(profile?.lng ?? profile?.longitude);
    const lat = finiteNumber(profile?.lat ?? profile?.latitude);
    const nature = String(profile?.orgNature ?? profile?.org_nature ?? '').toUpperCase();
    return status === 'ACTIVE' && nature !== 'DEPARTMENT' && lng !== null && lat !== null
      && featureContainsPoint(selectedFeature.value, [lng, lat]);
  }).map(profile => {
    const metricRow = orgMetricMap.value.get(String(profile.orgCode));
    const values = metricRow?.metricValues || {};
    const rate = finiteNumber(values[selectedMetricCode.value]);
    const actual = finiteNumber(values[selectedMetric.value.actualCode]);
    const fallback = simulatedMetric(profile.orgCode || profile.orgName, selectedMetricCode.value);
    const resolvedRate = rate ?? fallback.rate;
    const target = actual !== null && resolvedRate > 0 ? actual / (resolvedRate / 100) : fallback.target;
    const simulatedLocation = String(profile.remark || '').includes('SCREEN_MAP_DEMO');
    const simulatedValue = rate === null;
    return {
      orgCode: profile.orgCode,
      orgName: profile.orgName || profile.orgCode,
      location: `${Number(profile.lng ?? profile.longitude).toFixed(6)}, ${Number(profile.lat ?? profile.latitude).toFixed(6)}`,
      rate: resolvedRate,
      actual: actual ?? fallback.actual,
      target,
      simulated: simulatedValue,
      simulatedLocation,
      sourceLabel: simulatedLocation ? '演示坐标/指标' : (simulatedValue ? '模拟指标' : '数据库指标')
    };
  }).sort((left, right) => right.rate - left.rate);
});

// CanvasRenderer 下用同一真实 GeoJSON 的三个静默偏移层模拟地图厚度；不引入 WebGL，
// 也不伪造透视边界，因此在设计器缩放、截图和低配终端上都保持稳定。
const mapDepthSeries = computed(() => ['56%', '55%', '54%'].map((vertical, index) => ({
  name: `地图厚度-${index + 1}`,
  type: 'map',
  map: mapName.value,
  silent: true,
  roam: false,
  layoutCenter: ['50%', vertical],
  layoutSize: mapLayoutSize.value,
  label: { show: false },
  itemStyle: {
    areaColor: index === 0 ? '#07152f' : index === 1 ? '#0a2143' : '#0d2d54',
    borderColor: index === 2 ? 'rgba(0, 183, 229, .42)' : 'rgba(0, 93, 155, .42)',
    borderWidth: 1,
    shadowColor: index === 0 ? 'rgba(0, 0, 0, .72)' : 'transparent',
    shadowBlur: index === 0 ? 22 : 0,
    shadowOffsetY: index === 0 ? 10 : 0
  },
  emphasis: { disabled: true },
  z: index
})));

const option = computed(() => ({
  tooltip: {
    ...scrTooltipStyle(),
    formatter: params => {
      if (params?.seriesType === 'effectScatter') return `${params.name}<br/>点击进入机构详情屏`;
      const detail = metricDetail(params?.name);
      const unit = selectedMetric.value.unit || '';
      const signed = value => value === null ? '--' : `${value >= 0 ? '+' : ''}${formatMetricValue(value)}%`;
      return `<strong>${params?.name || '--'}</strong>${detail.simulated ? '<br/><span>模拟指标，仅用于演示</span>' : ''}<br/>${selectedMetric.value.title}：${formatMetricValue(detail.rate)}%`
        + `<br/>实际值：${formatMetricValue(detail.actual)}${unit}<br/>目标值：${formatMetricValue(detail.target)}${unit}`
        + `<br/>缺口：${formatMetricValue(detail.gap)}${unit}<br/>全省排名：${detail.entry?.rank ? `第 ${detail.entry.rank}` : '--'}`
        + `<br/>同比 ${signed(detail.yoy)}　环比 ${signed(detail.mom)}<br/>数据日期：${detail.entry?.row?.dataDate || '--'}`;
    }
  },
  geo: {
    map: mapName.value,
    roam: false,
    layoutCenter: ['50%', '53%'],
    layoutSize: mapLayoutSize.value,
    z: 4,
    regions: mapRegions.value,
    label: { show: true, color: '#d7e8ff', fontSize: (isComposite.value || isCityDistrict.value) ? 11 : 12,
      formatter: params => `${params.name}\n${formatMetricValue(metricDetail(params.name).rate)}%`,
      lineHeight: 17, textBorderColor: 'rgba(2, 10, 31, .9)', textBorderWidth: 3 },
    itemStyle: {
      areaColor: '#153a63',
      borderColor: 'rgba(0, 229, 255, .82)',
      borderWidth: 1.35,
      shadowColor: 'rgba(0, 229, 255, .3)',
      shadowBlur: 18
    },
    emphasis: {
      label: { color: '#fff' },
      itemStyle: { areaColor: '#245f79', shadowColor: 'rgba(0,229,255,.55)', shadowBlur: 24 }
    }
  },
  series: [...mapDepthSeries.value, {
    name: isComposite.value ? '西安本地一级经营机构' : '支行',
    type: 'effectScatter',
    coordinateSystem: 'geo',
    z: 6,
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

function localAriaLabel(point, rate = localMetric(point)) {
  return isInteractive.value
    ? `${point.orgName}，${selectedMetric.value.label}完成率 ${formatMetricValue(rate)}%，按 Enter 或空格进入机构详情屏`
    : `${point.orgName}，${selectedMetric.value.label}完成率 ${formatMetricValue(rate)}%，设计预览不可钻取`;
}

function navigate(node) {
  if (!isInteractive.value || !node?.orgCode) return;
  router.push({ path: `/screen/${node.targetScreenCode || node.target || 'SCR_BRANCH'}`, query: { orgCode: node.orgCode } });
}

// ECharts 点击仍是旧 v1 和鼠标点击 v2 本地图点的兼容入口。
function onChartClick(params) {
  if ((params?.componentType === 'geo' || params?.seriesType === 'map')
    && featureList.value.some(feature => feature.properties?.name === params?.name)) {
    selectedRegionName.value = params.name;
    detailOpen.value = true;
    return;
  }
  if (params?.seriesType !== 'effectScatter') return;
  const d = params.data || {};
  if (d.orgCode) navigate({ orgCode: d.orgCode, targetScreenCode: d.target || 'SCR_BRANCH' });
}
</script>

<style scoped>
.mp-block { height: 100%; position: relative; overflow: hidden; display: flex; flex-direction: column; }
.mp-chart { width: 100%; height: 100%; }
.mp-title { flex: none; text-align: center; color: #d5e6ff; font-size: 15px; line-height: 28px; letter-spacing: 1px; }
.mp-kpi-toolbar { flex: none; margin: 3px 10px 1px; padding: 7px 10px; border: 1px solid rgba(36, 117, 171, .48); border-radius: 4px; background: rgba(5, 19, 48, .82); }
.mp-kpi-tabs { display: flex; gap: 6px; justify-content: center; }
.mp-kpi-tabs button { padding: 4px 12px; border: 1px solid rgba(77, 143, 193, .55); border-radius: 3px; background: rgba(14, 43, 78, .72); color: #9fb9db; font-size: 12px; cursor: pointer; }
.mp-kpi-tabs button:hover, .mp-kpi-tabs button.active { border-color: #26d8ff; background: rgba(17, 102, 154, .78); color: #fff; box-shadow: inset 0 0 10px rgba(0, 217, 255, .16); }
.mp-kpi-meta { display: flex; gap: 13px; align-items: center; justify-content: center; margin-top: 6px; color: #8faaca; font-size: 10px; }
.mp-kpi-meta span { display: inline-flex; align-items: center; gap: 4px; }
.mp-kpi-meta em { margin-left: 8px; color: #6f8eae; font-style: normal; }
.mp-demo-badge { padding: 2px 7px; border: 1px solid rgba(255, 190, 80, .75); border-radius: 10px; color: #ffd76a; font-size: 10px; font-weight: 600; }
.legend { width: 8px; height: 8px; border-radius: 2px; background: #27364f; }
.legend.excellent { background: #1f8a70; }.legend.normal { background: #236b8e; }.legend.warning { background: #b9852f; }.legend.risk { background: #a94a55; }.legend.missing { background: #27364f; border: 1px solid #50617a; box-sizing: border-box; }
.mp-config-gap { flex: none; margin: 0 10px 4px; padding: 4px 8px; border: 1px dashed rgba(255, 190, 80, .65); color: #ffd76a; font-size: 11px; line-height: 1.4; }
.mp-composite-canvas { position: relative; flex: 1; min-height: 0; overflow: hidden; }
.mp-xian-chart { position: absolute; inset: 0; }
.mp-leader-line {
  position: absolute; z-index: 2; height: 1px; pointer-events: none; transform-origin: 0 50%;
  background: linear-gradient(90deg, var(--callout-color), color-mix(in srgb, var(--callout-color) 45%, transparent));
  box-shadow: 0 0 5px color-mix(in srgb, var(--callout-color) 65%, transparent);
}
.mp-leader-line::before {
  content: ''; position: absolute; left: -2px; top: -2px; width: 5px; height: 5px; border-radius: 50%;
  background: var(--callout-color); box-shadow: 0 0 8px var(--callout-color);
}
.mp-satellite-node, .mp-local-node {
  position: absolute; z-index: 2; display: inline-flex; align-items: center; gap: 5px;
  padding: 3px 6px; border: 1px solid rgba(255, 215, 106, .7); border-radius: 12px;
  background: rgba(5, 14, 43, .86); color: #ffd76a; font: inherit; font-size: 12px;
  cursor: pointer; transform: translate(-50%, -50%); white-space: nowrap;
}
.mp-callout {
  z-index: 3; width: 15%; min-width: 108px; max-width: 174px; justify-content: flex-start;
  border-color: color-mix(in srgb, var(--callout-color) 72%, transparent);
  border-radius: 3px; background: linear-gradient(90deg, rgba(5, 18, 48, .96), rgba(8, 38, 70, .9));
  color: #d9eaff; box-shadow: inset 3px 0 0 var(--callout-color), 0 0 11px rgba(0, 19, 48, .72);
}
.mp-callout.side-left { transform: translate(0, -50%); }
.mp-callout.side-right { transform: translate(0, -50%); flex-direction: row-reverse; text-align: right; box-shadow: inset -3px 0 0 var(--callout-color), 0 0 11px rgba(0, 19, 48, .72); }
.mp-callout .mp-node-dot { background: var(--callout-color); box-shadow: 0 0 8px var(--callout-color); }
.mp-callout .mp-node-label { min-width: 0; flex: 1; }
.mp-node-rate { flex: none; color: var(--callout-color); font-size: 12px; font-variant-numeric: tabular-nums; }
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
.mp-region-detail { position: absolute; z-index: 20; top: 78px; right: 12px; bottom: 18px; width: min(520px, 46%); overflow: hidden; display: flex; flex-direction: column; border: 1px solid rgba(38,216,255,.68); border-radius: 6px; background: rgba(3,14,38,.96); box-shadow: 0 12px 38px rgba(0,0,0,.48), inset 0 0 28px rgba(0,126,196,.08); color: #dbeaff; }
.mp-region-detail-head { display: flex; align-items: flex-start; justify-content: space-between; padding: 13px 15px 10px; border-bottom: 1px solid rgba(55,127,181,.38); }
.mp-region-detail-head strong { display: block; color: #fff; font-size: 15px; }
.mp-region-detail-head small { display: block; margin-top: 4px; color: #7898bd; font-size: 10px; }
.mp-region-detail-head button { width: 26px; height: 26px; border: 1px solid rgba(84,147,191,.55); border-radius: 3px; background: rgba(13,43,77,.78); color: #a8c7e6; cursor: pointer; }
.mp-region-summary { display: flex; gap: 12px; align-items: center; padding: 8px 15px; color: #90b4d8; font-size: 11px; }
.mp-demo-text { color: #ffd76a; }
.mp-region-table-wrap { min-height: 0; overflow: auto; padding: 0 12px 12px; }
.mp-region-detail table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 11px; }
.mp-region-detail th { padding: 7px 6px; border-bottom: 1px solid rgba(68,126,170,.48); color: #7fa7cf; text-align: left; font-weight: 500; }
.mp-region-detail td { padding: 9px 6px; border-bottom: 1px solid rgba(42,89,130,.34); color: #c8dcf2; word-break: break-word; }
.mp-region-detail td:first-child { width: 22%; }.mp-region-detail td:nth-child(2) { width: 27%; }
.mp-region-detail td b, .mp-region-detail td small { display: block; }.mp-region-detail td small { margin-top: 3px; color: #6f91b3; }
.source-real, .source-simulated { display: inline-block; padding: 2px 5px; border-radius: 3px; white-space: nowrap; }
.source-real { color: #6de4b5; background: rgba(31,138,112,.18); }.source-simulated { color: #ffd76a; background: rgba(185,133,47,.18); }
.mp-region-empty { margin: 10px 14px; padding: 18px 12px; border: 1px dashed rgba(77,143,193,.45); color: #7898bd; text-align: center; font-size: 11px; line-height: 1.6; }
</style>
