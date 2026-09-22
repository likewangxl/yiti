<template>
  <section
    ref="containerRef"
    class="panorama-map"
    :data-mode="mode"
    :data-appearance="appearance"
    :data-label-layout="isCalloutLayout ? 'callout' : 'inline'"
    :data-material-ready="surfaceReady ? 'true' : 'false'"
    :data-selected-region="selectedRegionCode || ''"
    :data-hovered-region="hoveredRegionCode"
    :data-zoom="zoom.toFixed(2)"
    :data-pan-enabled="mode !== 'province' && zoom > 1 ? 'true' : 'false'"
    :class="{ 'is-dragging': dragging }"
    @pointerdown="startPan"
    @pointermove="movePan"
    @pointerup="endPan"
    @pointercancel="endPan"
    @pointerleave="leaveHoveredRegion"
    @keydown.esc="setHoveredRegion('')"
    :data-webgl-ready="webglReady ? 'true' : 'false'"
    :data-region-count="renderedRegionCount"
    :aria-label="fallbackActive ? '真实行政区二维地图' : '真实行政区三维地图'"
  >
    <canvas
      ref="canvasRef"
      class="panorama-map__canvas"
      :class="{ 'is-hidden': fallbackActive }"
      aria-hidden="true"
    ></canvas>
    <span v-if="metricLabel" class="panorama-map__metric-heading" data-testid="map-metric-label">{{ metricLabel }}</span>

    <div v-if="fallbackActive" class="panorama-map__fallback" role="region" aria-label="二维真实行政区地图回退">
      <svg
        class="panorama-map__svg"
        viewBox="0 0 100 100"
        preserveAspectRatio="xMidYMid meet"
        role="group"
        aria-label="可选择行政区"
      >
        <g
          v-for="region in fallbackRegions"
          :key="region.key"
          class="panorama-map__region"
          :class="{ 'is-selected': region.code && String(region.code) === String(selectedRegionCode), 'is-hovered': String(region.code) === hoveredRegionCode, 'is-metric-missing': metricState(region) === 'MISSING' }"
        >
          <path
            :d="region.path"
            fill-rule="evenodd"
            :data-region-code="region.code"
            :data-region-name="region.name"
            :data-metric-state="metricState(region)"
            :style="metricStyle(region)"
            role="button"
            tabindex="0"
            :aria-label="`选择${region.name || '行政区'}`"
            @click.stop="selectRegion(region)"
            @pointerenter="setHoveredRegion(region.code)"
            @pointerleave="leaveHoveredRegion"
            @keydown.enter.stop="selectRegion(region)"
            @keydown.space.prevent.stop="selectRegion(region)"
          />
          <circle
            v-if="mode === 'province' && regionLabels.some(item => item.key === region.key)"
            :cx="fallbackRegionMarkerPoint(region).x"
            :cy="fallbackRegionMarkerPoint(region).y"
            r="1.55"
            class="panorama-map__city-halo-svg"
            :class="{ 'is-violet': regionLabels.findIndex(item => item.key === region.key) % 2 === 1 }"
            aria-hidden="true"
          />
        </g>
        <g v-if="!isCalloutLayout" v-for="region in regionLabels" :key="`${region.key}:label`" class="panorama-map__region-label">
          <text :x="region.label.x" :y="region.label.y" role="button" tabindex="0" @click.stop="selectRegion(region)" @keydown.enter.stop="selectRegion(region)">{{ region.name }}</text>
          <text v-if="metricValues[region.code] != null" :x="region.label.x" :y="region.label.y + 3" data-testid="map-region-metric" class="panorama-map__metric-svg">{{ metricValues[region.code] }}</text>
        </g>
      </svg>
    </div>
    <svg
      v-if="isCalloutLayout"
      class="panorama-map__callout-lines"
      viewBox="0 0 100 100"
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      <g v-for="region in regionLabels" :key="`${region.key}:callout`" class="panorama-map__callout" :class="{ 'is-active': String(region.code) === hoveredRegionCode, 'is-missing': metricValues[region.code] == null }" :style="cityAccentStyle(region)" :data-city-code="region.code">
        <path :d="calloutPath(region)" class="panorama-map__callout-glow" />
        <path
          :d="calloutPath(region)"
          data-testid="map-city-callout-line"
          :data-city-code="region.code"
          class="panorama-map__callout-line"
        />
        <line
          :x1="calloutLayout[region.key]?.anchor.x"
          :y1="calloutLayout[region.key]?.anchor.y"
          :x2="calloutLayout[region.key]?.anchor.x"
          :y2="calloutLayout[region.key]?.anchor.y"
          class="panorama-map__callout-anchor"
        />
      </g>
    </svg>
    <div v-if="!fallbackActive || isCalloutLayout" class="panorama-map__region-label-layer" aria-label="可选择城市标签">
      <button
        v-for="region in regionLabels"
        :key="`${region.key}:overlay-label`"
        type="button"
        class="panorama-map__region-label-hit"
        :class="{ 'is-selected': region.code && String(region.code) === String(selectedRegionCode), 'is-hovered': String(region.code) === hoveredRegionCode, 'is-missing': isCalloutLayout && metricValues[region.code] == null }"
        :style="regionLabelStyle(region)"
        :data-city-code="isCalloutLayout ? region.code : undefined"
        :aria-label="`选择${region.name}`"
        :aria-describedby="isCalloutLayout && String(region.code) === hoveredRegionCode ? cityTooltipId : undefined"
        @click.stop="selectRegion(region)"
        @pointerenter="setHoveredRegion(region.code)"
        @pointerleave="leaveHoveredRegion"
        @focus="setHoveredRegion(region.code)"
        @blur="setHoveredRegion('')"
      ><span v-if="isCalloutLayout" class="panorama-map__city-marker" aria-hidden="true"></span><span class="panorama-map__city-name">{{ region.name }}</span><small v-if="isCalloutLayout || metricValues[region.code] != null" data-testid="map-region-metric" class="panorama-map__metric-value">{{ isCalloutLayout ? metricDisplayValue(region) : metricValues[region.code] }}</small></button>
    </div>
    <Teleport to="body">
      <aside ref="cityDetailRef" v-if="activeCityDetail" :data-city-code="activeCityDetail.region.code" :id="cityTooltipId" role="tooltip" class="panorama-map__city-detail" :class="{ 'has-institution-metrics': hasInstitutionMetrics }" :style="cityDetailStyle" @pointerenter="keepCityDetail" @pointerleave="leaveHoveredRegion" @focusin="keepCityDetail" @focusout="leaveHoveredRegion" @keydown.esc="setHoveredRegion('')">
        <header><div><small>地市经营概览</small><h3>{{ activeCityDetail.region.name }}</h3></div><span class="panorama-map__detail-status">{{ activeCityDetail.scopeLabel || '当前授权范围' }}</span></header>
        <div class="panorama-map__detail-counts"><span>机构 <b>{{ activeCityDetail.institutionCount ?? '—' }} 家</b></span><span>已定位 <b>{{ activeCityDetail.locatedCount ?? '—' }} 家</b></span></div>
        <div v-if="!hasInstitutionMetrics || activeCityDetail.metrics.some(metric => metric.value !== '暂无数据')" class="panorama-map__detail-metrics"><div v-for="metric in activeCityDetail.metrics" :key="metric.key"><span>{{ metric.label }}</span><strong :class="{ 'is-empty': metric.value === '暂无数据' }">{{ metric.value }}</strong></div></div>
        <div v-if="hasInstitutionMetrics" class="panorama-map__detail-institution-list" data-testid="map-institution-metrics" tabindex="0" aria-label="地市授权机构业务明细">
          <article v-for="institution in activeCityDetail.institutions" :key="institution.orgCode">
            <h4>{{ institution.orgName || institution.orgCode }}</h4>
            <dl><div v-for="metric in institution.metrics.filter(item => item.value !== '暂无数据')" :key="metric.key"><dt>{{ metric.label }}</dt><dd>{{ metric.value }}</dd></div></dl>
            <p v-if="institution.metrics.every(item => item.value === '暂无数据')">暂无业务数据</p>
            <small>数据日期 {{ institution.dataDate || '暂无' }}</small>
          </article>
        </div>
        <div v-else-if="activeCityDetail.institutions.length" class="panorama-map__detail-institutions"><span>辖内机构</span><p>{{ activeCityDetail.institutions.slice(0, 3).map(item => item.orgName || item.orgCode).join(' · ') }}<template v-if="activeCityDetail.institutions.length > 3"> 等 {{ activeCityDetail.institutions.length }} 家</template></p></div>
        <footer><span>数据日期 {{ activeCityDetail.dataDate || '暂无' }}</span><span>点击城市查看详情 →</span></footer>
      </aside>
    </Teleport>
    <div
      v-if="mode === 'province' && !fallbackActive"
      class="panorama-map__city-halo-layer"
      aria-hidden="true"
    >
      <span
        v-for="(region, index) in regionLabels"
        :key="`${region.key}:halo`"
        class="panorama-map__city-halo"
        :class="{ 'is-violet': index % 2 === 1 }"
        :style="cityHaloStyle(region)"
      ></span>
    </div>
    <p v-if="!projectedRegions.length" class="panorama-map__empty" role="status">
      暂无可用的真实行政区边界数据。
    </p>

    <div v-if="mode !== 'province'" class="panorama-map__point-layer" aria-label="可定位机构">
      <button
        v-for="point in pointClusters"
        :key="point.isCluster ? point.id : point.orgCode"
        class="panorama-map__point-hit"
        :class="{
          'is-cluster': point.isCluster,
          'is-selected': point.isCluster
            ? clusterContainsSelected(point)
            : String(point.orgCode) === String(selectedOrgCode)
        }"
        :style="pointStyle(point)"
        :data-org-code="point.isCluster ? undefined : point.orgCode"
        :data-cluster-id="point.isCluster ? point.id : undefined"
        :aria-label="point.isCluster ? `聚合点，${point.count} 个机构，点击放大` : `${point.orgName || point.orgCode}，点击查看详情`"
        type="button"
        @click.stop="activatePoint(point)"
      >
        <span v-if="point.isCluster" class="panorama-map__cluster-count">{{ point.count }}</span>
        <span v-else class="panorama-map__point-dot" aria-hidden="true"></span>
        <span v-if="showPointLabels && !point.isCluster" class="panorama-map__point-label">{{ point.orgName || point.orgCode }}<small v-if="metricValues[point.orgCode] != null" class="panorama-map__metric-value">{{ metricValues[point.orgCode] }}</small></span>
      </button>
    </div>

    <div
      v-if="activeCluster"
      class="panorama-map__cluster-picker"
      role="dialog"
      aria-modal="false"
      aria-label="选择聚合机构"
    >
      <div class="panorama-map__cluster-picker-head">
        <strong>{{ activeCluster.count }} 个机构</strong>
        <button type="button" aria-label="关闭机构选择器" @click="closeClusterPicker">×</button>
      </div>
      <button
        v-for="member in activeCluster.points"
        :key="member.orgCode"
        type="button"
        class="panorama-map__cluster-member"
        :data-cluster-member="member.orgCode"
        @click="selectPoint(member)"
      >
        <span>{{ member.orgName || member.orgCode }}</span>
        <small>{{ member.orgCode }}</small>
        <small v-if="metricValues[member.orgCode] != null" class="panorama-map__metric-value">{{ metricValues[member.orgCode] }}</small>
      </button>
    </div>

    <div class="panorama-map__controls" role="group" aria-label="地图视图控制">
      <button type="button" aria-label="放大地图" title="放大地图" :disabled="zoom >= MAX_ZOOM" @click="zoomBy(1.35)"><Plus aria-hidden="true" /></button>
      <button type="button" aria-label="缩小地图" title="缩小地图" :disabled="zoom <= MIN_ZOOM" @click="zoomBy(1 / 1.35)"><Minus aria-hidden="true" /></button>
      <button type="button" aria-label="重置地图视图" title="重置地图视图" @click="resetView"><Aim aria-hidden="true" /></button>
    </div>

    <p v-if="fallbackActive" class="panorama-map__fallback-status" role="status" aria-live="polite">
      {{ mode === 'province'
        ? '三维地图暂不可用，已切换为二维地图；可缩放、点击城市查看机构。'
        : '三维地图暂不可用，已切换为二维地图；放大后可拖动地图，点击网点查看详情。' }}
    </p>
    <p v-else-if="webglReady" class="panorama-map__fallback-status" role="status" aria-live="polite">
      {{ mode === 'province'
        ? '三维真实行政区地图，坐标系 GCJ-02；点击城市标签进入市级机构地图，光环为行政中心装饰。'
        : '三维真实行政区地图，坐标系 GCJ-02；放大后可拖动地图。' }}
    </p>

    <p v-if="mode !== 'province' && unmappedPoints.length" class="panorama-map__unmapped" aria-label="未绘制机构状态">
      <strong>未绘制 {{ unmappedPoints.length }} 个机构</strong>
      <span>缺少有效 GCJ-02 坐标或坐标系未确认</span>
    </p>
  </section>
</template>

<script setup>
import { computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as THREE from 'three';
import reliefSurfaceUrl from '@/assets/maps/relief-surface-grain.png';
import { Aim, Minus, Plus } from '@element-plus/icons-vue';
import {
  clusterPoints,
  createProjection,
  filterRenderablePoints,
  projectGeoJson
} from './mapGeometry';
import {
  fitReliefView,
  createReliefGeometryConfig,
  createReliefWallColors,
  smoothReliefWallNormals,
  getReliefSurfaceZ,
  isReliefAppearance
} from './mapReliefGeometry';

import { createMapCalloutPath, layoutMapCallouts, layoutMapLabels } from './mapLabelLayout';
const props = defineProps({
  geoJson: { type: Object, default: () => ({ type: 'FeatureCollection', features: [] }) },
  points: { type: Array, default: () => [] },
  selectedOrgCode: { type: [String, Number], default: null },
  metricLabel: { type: String, default: '' },
  metricValues: { type: Object, default: () => ({}) },
  metricNumericValues: { type: Object, default: () => ({}) },
  metricColors: { type: Object, default: () => ({}) },
  colorByMetric: { type: Boolean, default: false },
  cityDetails: { type: Object, default: () => ({}) },
  mode: { type: String, default: 'province' },
  selectedRegionCode: { type: [String, Number], default: null },
  demo: { type: Boolean, default: false },
  appearance: { type: String, default: 'classic' },
  labelLayout: { type: String, default: 'inline' },
  viewFit: { type: Object, default: () => ({}) }
});

const emit = defineEmits(['region-select', 'branch-select']);

const MIN_ZOOM = 0.65;
const MAX_ZOOM = 12;

const containerRef = ref(null);
const canvasRef = ref(null);
const fallbackActive = ref(false);
const webglReady = ref(false);
const renderedRegionCount = ref(0);
const zoom = ref(1);
const activeCluster = ref(null);
const viewCenter = ref({ x: 0, y: 0 });
const dragging = ref(false);

const overlayRevision = ref(0);
const surfaceReady = ref(false);
const hoveredRegionCode = ref('');
const cityTooltipId = `panorama-city-detail-${getCurrentInstance().uid}`;
const cityDetailRef = ref(null);
const cityDetailHeight = ref(420);
let regionSurfaces = [];
let focusedPoint = false;
let surfaceTexture = null;
let ambientLight = null;
let keyLight = null;
let surfaceLight = null;
let rimLight = null;
let renderer = null;
let scene = null;
let camera = null;
let mapGroup = null;
let pointGroup = null;
let cameraTarget = null;
let resizeObserver = null;
let pointerHandler = null;
let contextLostHandler = null;
let raycaster = null;
let pointer = null;
let disposed = false;
let handlingFailure = false;

const projection = computed(() => createProjection(props.geoJson));
const projectedRegions = computed(() => projectGeoJson(props.geoJson, projection.value));
const appearance = computed(() => (isReliefAppearance(props.appearance) ? 'relief' : 'classic'));
const isCalloutLayout = computed(() => props.mode === 'province' && props.labelLayout === 'callout');
const reliefEnabled = computed(() => appearance.value === 'relief');
const reliefConfig = computed(() => createReliefGeometryConfig({
  worldWidth: projection.value.width,
  worldHeight: projection.value.height
}));
const renderablePoints = computed(() => filterRenderablePoints(props.points, { demo: props.demo }));
const unmappedPoints = computed(() => (Array.isArray(props.points) ? props.points : [])
  .filter(point => !renderablePoints.value.includes(point)));
const drawablePoints = computed(() => props.mode !== 'province' ? renderablePoints.value : []);

const pointClusters = computed(() => clusterPoints(drawablePoints.value, {
  projection: projection.value,
  threshold: 34 / zoom.value,
  width: 1000,
  height: 720,
  demo: props.demo
}));

// A branch name is business information, not optional map decoration. Clustering
// already collapses points that are too close, so every remaining single point
// keeps its label even when the city has more than eight branches.
const showPointLabels = computed(() => props.mode !== 'province');

let panPointerId = null;
let panOrigin = null;
let suppressActivationUntil = 0;

function worldBounds() {
  const bounds = projection.value.bounds;
  if (bounds) {
    const p = projection.value;
    return {
      minX: -p.width / 2,
      maxX: p.width / 2,
      minY: -p.height / 2,
      maxY: p.height / 2,
      width: Math.max(p.width, 0.001),
      height: Math.max(p.height, 0.001)
    };
  }
  return { minX: -5, maxX: 5, minY: -5, maxY: 5, width: 10, height: 10 };
}

function svgPoint(point) {
  const bounds = worldBounds();
  const center = viewCenter.value;
  return {
    x: 50 + ((point.x - center.x - (bounds.minX + bounds.width / 2)) / bounds.width) * 100 * zoom.value,
    y: 50 - ((point.y - center.y - (bounds.minY + bounds.height / 2)) / bounds.height) * 100 * zoom.value
  };
}

// The fallback SVG keeps `preserveAspectRatio="xMidYMid meet"` so a wide map
// does not stretch the province. Convert its square viewBox coordinates back to
// the full container before positioning the shared HTML callout layer.
const fallbackInsets = Object.freeze({ left: 22, right: 52, top: 18, bottom: 24 });

function fallbackOverlayPoint(point) {
  const width = Math.max(1, containerRef.value?.clientWidth || 800);
  const height = Math.max(1, containerRef.value?.clientHeight || 520);
  const svgWidth = Math.max(1, width - fallbackInsets.left - fallbackInsets.right);
  const svgHeight = Math.max(1, height - fallbackInsets.top - fallbackInsets.bottom);
  const scale = Math.min(svgWidth, svgHeight) / 100;
  const offsetX = (svgWidth - scale * 100) / 2;
  const offsetY = (svgHeight - scale * 100) / 2;
  return {
    x: ((fallbackInsets.left + offsetX + point.x * scale) / width) * 100,
    y: ((fallbackInsets.top + offsetY + point.y * scale) / height) * 100
  };
}

function pathForRing(ring) {
  return ring.map((point, index) => {
    const svg = svgPoint(point);
    return `${index === 0 ? 'M' : 'L'} ${svg.x.toFixed(3)} ${svg.y.toFixed(3)}`;
  }).join(' ') + (ring.length ? ' Z' : '');
}

function polygonArea(ring = []) {
  return Math.abs(ring.reduce((area, point, index) => {
    const next = ring[(index + 1) % ring.length] || point;
    return area + point.x * next.y - next.x * point.y;
  }, 0)) / 2;
}

function propertyRegionLabelWorldPoint(region) {
  const candidate = region.properties?.centroid || region.properties?.center;
  if (!Array.isArray(candidate) || candidate.length < 2) return null;
  const lng = Number(candidate[0]);
  const lat = Number(candidate[1]);
  if (!Number.isFinite(lng) || !Number.isFinite(lat)) return null;
  return projection.value.project([lng, lat]);
}

function baseRegionLabelWorldPoint(region) {
  const propertyPoint = propertyRegionLabelWorldPoint(region);
  if (propertyPoint) return propertyPoint;
  return region.outer.reduce((center, point) => ({
    x: center.x + point.x / region.outer.length,
    y: center.y + point.y / region.outer.length
  }), { x: 0, y: 0 });
}

const fallbackRegions = computed(() => projectedRegions.value.map((region, index) => ({
  ...region,
  key: `${region.code || region.name || 'region'}:${region.polygonIndex}:${index}`,
  path: [pathForRing(region.outer), ...region.holes.map(pathForRing)].join(' '),
  label: svgPoint(baseRegionLabelWorldPoint(region))
})));

const regionLabels = computed(() => {
  const grouped = new Map();
  fallbackRegions.value.forEach(region => {
    const key = String(region.code || region.name || '');
    if (!region.name || !key) return;
    const group = grouped.get(key) || [];
    group.push(region);
    grouped.set(key, group);
  });
  // A MultiPolygon may contain a small island before the mainland. Use the
  // largest polygon as the geometry fallback, while center/centroid properties
  // remain the preferred label anchor when supplied by the source GeoJSON.
  const candidates = [...grouped.values()].map(group => group.reduce((largest, region) => (
    polygonArea(region.outer) > polygonArea(largest.outer) ? region : largest
  )));

  // Keep labels legible when adjacent city centroids are close. The first
  // candidate remains on the city, then small deterministic offsets are used
  // only when an estimated text box overlaps a label already placed.
  const placed = [];
  const bounds = worldBounds();
  const stepX = Math.max(bounds.width * 0.055, 0.24);
  const stepY = Math.max(bounds.height * 0.055, 0.24);
  const offsets = [
    [0, 0], [1, 0], [-1, 0], [0, 1], [0, -1],
    [1, 1], [-1, 1], [1, -1], [-1, -1], [2, 0], [-2, 0]
  ];

  return candidates.map(region => {
    const base = baseRegionLabelWorldPoint(region);
    const metricText = props.metricValues[region.code];
    const labelWidth = Math.max(4.5, String(region.name).length * 2.4, metricText == null ? 0 : String(metricText).length * 1.4);
    const labelHeight = metricText == null ? 5 : 8;
    let selected = base;
    let selectedScreen = svgPoint(base);
    for (const [offsetX, offsetY] of offsets) {
      const point = { x: base.x + offsetX * stepX, y: base.y + offsetY * stepY };
      const screen = svgPoint(point);
      const overlaps = placed.some(item => Math.abs(screen.x - item.x) < (labelWidth + item.width) / 2
        && Math.abs(screen.y - item.y) < (labelHeight + item.height) / 2);
      if (!overlaps && screen.x > 3 && screen.x < 97 && screen.y > 3 && screen.y < 97) {
        selected = point;
        selectedScreen = screen;
        break;
      }
    }
    placed.push({ x: selectedScreen.x, y: selectedScreen.y, width: labelWidth, height: labelHeight });
    return {
      ...region,
      // Keep the projected administrative centre immutable for leader lines
      // and halos; `labelWorld` remains the legacy inline-label avoidance point.
      anchorWorld: base,
      anchor: svgPoint(base),
      labelWorld: selected,
      label: selectedScreen
    };
  });
});

function regionLabelWorldPoint(region) {
  return region.labelWorld || baseRegionLabelWorldPoint(region);
}

function regionAnchorWorldPoint(region) {
  return region.anchorWorld || baseRegionLabelWorldPoint(region);
}

function fallbackRegionMarkerPoint(region) {
  const labeledRegion = regionLabels.value.find(item => item.key === region.key);
  if (isCalloutLayout.value) return labeledRegion?.anchor || region.label;
  return region.label;
}

function mapSurfaceZ() {
  return reliefEnabled.value ? getReliefSurfaceZ(reliefConfig.value) : 0.38;
}

function webglOverlayPoint(worldPoint, z = mapSurfaceZ()) {
  void overlayRevision.value;
  if (webglReady.value && camera && mapGroup) {
    mapGroup.updateMatrixWorld(true);
    const point = new THREE.Vector3(worldPoint.x, worldPoint.y, z).applyMatrix4(mapGroup.matrixWorld);
    point.project(camera);
    return { x: (point.x + 1) * 50, y: (1 - point.y) * 50 };
  }
  return svgPoint(worldPoint);
}

function overlayPoint(worldPoint, z = mapSurfaceZ()) {
  const projected = webglOverlayPoint(worldPoint, z);
  return fallbackActive.value ? fallbackOverlayPoint(projected) : projected;
}

const metricLabelPositions = computed(() => {
  void overlayRevision.value;
  if (!Object.keys(props.metricValues).length) return {};
  const width=containerRef.value?.clientWidth || 800;
  const height=containerRef.value?.clientHeight || 520;
  return layoutMapLabels(regionLabels.value.map(region => {
    const point=webglOverlayPoint(regionLabelWorldPoint(region));
    const value=props.metricValues[region.code];
    return {key:region.key,...point,
      width:Math.max(String(region.name).length*12+10,value==null?0:String(value).length*6.5+10)/width*100,
      height:(value==null?20:34)/height*100};
  }));
});

function metricDisplayValue(region) {
  const value = props.metricValues?.[region.code];
  return value == null || String(value).trim() === '' ? '暂无数据' : String(value);
}

function metricState(region) {
  if (!props.colorByMetric) return undefined;
  const value = props.metricNumericValues?.[region.code];
  return value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? 'MISSING' : 'READY';
}

function metricStyle(region) {
  if (!props.colorByMetric) return undefined;
  const color = props.metricColors?.[region.code] || '#65738a';
  return { fill: color };
}

function metricColorForCode(code) {
  return props.metricColors?.[code] || '#65738a';
}

const calloutLayout = computed(() => {
  if (!isCalloutLayout.value) return {};
  void overlayRevision.value;
  const width = Math.max(1, containerRef.value?.clientWidth || 800);
  const height = Math.max(1, containerRef.value?.clientHeight || 520);
  return layoutMapCallouts(regionLabels.value.map(region => {
    const metric = metricDisplayValue(region);
    const widthPixels = Math.max(
      String(region.name || '').length * 12 + 12,
      metric.length * 6.5 + 12
    );
    return {
      key: region.key,
      anchor: overlayPoint(regionAnchorWorldPoint(region)),
      width: (Math.max(86, widthPixels + 14) / width) * 100,
      height: 36 / height * 100
    };
  }), {
    // Reserve the title band and the lower-right zoom controls.
    bounds: { left: 4, right: 87, top: 16, bottom: 87 },
    minGap: 2
  });
});

const cityAccents = ['#70c9ff', '#b6a0ff', '#6cdec5', '#efc58c', '#79b4ff', '#df9fcd', '#91d5b3', '#88baff', '#b5b0f6', '#e7b69a'];
function cityAccent(region) {
  const index = Math.max(0, Math.round((Number(region.code) % 10000) / 100) - 1);
  return cityAccents[index % cityAccents.length];
}
function cityAccentStyle(region) { return { '--city-accent': cityAccent(region) }; }
function calloutPath(region) { return createMapCalloutPath(calloutLayout.value[region.key]); }
const activeCityDetail = computed(() => {
  if (!isCalloutLayout.value || !hoveredRegionCode.value) return null;
  const region = regionLabels.value.find(item => String(item.code) === hoveredRegionCode.value);
  if (!region) return null;
  const detail = props.cityDetails[hoveredRegionCode.value] || {};
  return {
    ...detail, region,
    institutions: Array.isArray(detail.institutions) ? detail.institutions : [],
    metrics: Array.isArray(detail.metrics) && detail.metrics.length ? detail.metrics : [{ key: 'current', label: props.metricLabel || '当前指标', value: metricDisplayValue(region) }]
  };
});
watch(activeCityDetail, async () => {
  await nextTick();
  cityDetailHeight.value = cityDetailRef.value?.offsetHeight || 420;
});
function repositionCityDetail() { if (hoveredRegionCode.value) overlayRevision.value += 1; }
const hasInstitutionMetrics = computed(() => activeCityDetail.value?.institutions.some(item => Array.isArray(item.metrics)) || false);
const cityDetailStyle = computed(() => {
  void overlayRevision.value;
  const rect = containerRef.value?.getBoundingClientRect();
  if (!rect || !activeCityDetail.value) return {};
  const width = Math.min(304, window.innerWidth - 24);
  return {
    ...cityAccentStyle(activeCityDetail.value.region),
    width: `${width}px`,
    left: `${Math.max(12, Math.min(window.innerWidth - width - 12, rect.left + rect.width / 2 - width / 2))}px`,
    top: `${Math.max(12, Math.min(window.innerHeight - cityDetailHeight.value - 12, rect.top + rect.height / 2 - cityDetailHeight.value / 2))}px`
  };
});

function regionLabelStyle(region) {
  const point = isCalloutLayout.value
    ? calloutLayout.value[region.key]?.label
    : metricLabelPositions.value[region.key] || webglOverlayPoint(regionLabelWorldPoint(region));
  return { left: `${point.x}%`, top: `${point.y}%`, ...(isCalloutLayout.value ? cityAccentStyle(region) : {}) };
}

function cityHaloStyle(region) {
  const worldPoint = isCalloutLayout.value ? regionAnchorWorldPoint(region) : regionLabelWorldPoint(region);
  const point = overlayPoint(worldPoint, reliefEnabled.value
    ? reliefConfig.value.depth + reliefConfig.value.contourLift
    : 0.34);
  return { left: `${point.x}%`, top: `${point.y}%`, ...(isCalloutLayout.value ? cityAccentStyle(region) : {}) };
}

function pointStyle(point) {
  const screen = webglOverlayPoint(point, reliefEnabled.value
    ? reliefConfig.value.depth + reliefConfig.value.contourLift + 0.02
    : 0.42);
  return { left: `${screen.x}%`, top: `${screen.y}%` };
}

function selectRegion(region) {
  if (Date.now() < suppressActivationUntil) return;
  emit('region-select', { code: String(region.code || ''), name: region.name || '' });
}

function selectPoint(point) {
  if (Date.now() < suppressActivationUntil) return;
  if (!point?.orgCode) return;
  activeCluster.value = null;
  emit('branch-select', point.orgCode);
}

function activatePoint(point) {
  if (Date.now() < suppressActivationUntil) return;
  if (point?.isCluster) zoomToCluster(point);
  else selectPoint(point);
}

function clusterContainsSelected(cluster) {
  return Boolean(cluster?.isCluster && cluster.points?.some(point => (
    String(point.orgCode) === String(props.selectedOrgCode)
  )));
}

function zoomToCluster(cluster) {
  if (!cluster?.isCluster) return;
  // At the hard zoom limit another zoom has no visual effect. Keep the cluster
  // actionable by opening a keyboard accessible member picker instead.
  if (zoom.value >= MAX_ZOOM - 0.001) {
    activeCluster.value = cluster;
    return;
  }
  focusOnPoint(cluster);
  zoomBy(1.6);
}

function closeClusterPicker() {
  activeCluster.value = null;
}

function clampViewCenter(center) {
  const bounds = worldBounds();
  const scale = Math.max(1, zoom.value);
  const limitX = bounds.width * (1 - 1 / scale) / 2;
  const limitY = bounds.height * (1 - 1 / scale) / 2;
  return {
    x: Math.max(-limitX, Math.min(limitX, center.x)),
    y: Math.max(-limitY, Math.min(limitY, center.y))
  };
}

function startPan(event) {
  if (props.mode === 'province' || zoom.value <= 1 || event.isPrimary === false || event.button > 0) return;
  if (event.target?.closest?.('.panorama-map__controls, .panorama-map__cluster-picker')) return;
  panPointerId = event.pointerId;
  panOrigin = {
    clientX: event.clientX,
    clientY: event.clientY,
    centerX: viewCenter.value.x,
    centerY: viewCenter.value.y,
    moved: false
  };
  dragging.value = true;
  containerRef.value?.setPointerCapture?.(event.pointerId);
}

function movePan(event) {
  if (!dragging.value || event.pointerId !== panPointerId || !panOrigin) return;
  const dx = event.clientX - panOrigin.clientX;
  const dy = event.clientY - panOrigin.clientY;
  if (!panOrigin.moved && Math.hypot(dx, dy) < 4) return;
  panOrigin.moved = true;
  event.preventDefault?.();
  const width = Math.max(1, containerRef.value?.clientWidth || 800);
  const height = Math.max(1, containerRef.value?.clientHeight || 520);
  const bounds = worldBounds();
  viewCenter.value = clampViewCenter({
    x: panOrigin.centerX - (dx / width) * bounds.width / zoom.value,
    y: panOrigin.centerY + (dy / height) * bounds.height / zoom.value
  });
  focusedPoint = true;
  updateCameraPose();
  renderFrame();
}

function endPan(event) {
  if (!dragging.value || event.pointerId !== panPointerId) return;
  if (panOrigin?.moved) suppressActivationUntil = Date.now() + 250;
  try { containerRef.value?.releasePointerCapture?.(panPointerId); } catch { /* pointer capture may already be gone */ }
  dragging.value = false;
  panPointerId = null;
  panOrigin = null;
}

function applyCameraZoom() {
  if (!camera) return;
  camera.zoom = zoom.value;
  camera.updateProjectionMatrix();
  updateCameraPose();
}

function zoomBy(factor) {
  zoom.value = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom.value * Number(factor || 1)));
  if (zoom.value <= 1) {
    focusedPoint = false;
    viewCenter.value = { x: 0, y: 0 };
  } else {
    viewCenter.value = clampViewCenter(viewCenter.value);
  }
  updatePointMarkerScale();
  applyCameraZoom();
  renderFrame();
}

function resetView() {
  focusedPoint = false;
  zoom.value = 1;
  activeCluster.value = null;
  viewCenter.value = { x: 0, y: 0 };
  if (cameraTarget) cameraTarget.set(0, 0, 0);
  updatePointMarkerScale();
  applyCameraZoom();
  renderFrame();
}

function updateCameraPose() {
  if (!camera || !cameraTarget) return;
  if (focusedPoint && mapGroup) {
    mapGroup.updateMatrixWorld(true);
    cameraTarget.set(viewCenter.value.x, viewCenter.value.y, mapSurfaceZ()).applyMatrix4(mapGroup.matrixWorld);
  } else cameraTarget.set(0, 0, 0);
  const yOffset = reliefEnabled.value ? reliefConfig.value.cameraOffsetY : -6;
  const zOffset = reliefEnabled.value ? reliefConfig.value.cameraOffsetZ : 14;
  camera.position.set(cameraTarget.x, cameraTarget.y + yOffset, cameraTarget.z + zOffset);
  camera.lookAt(cameraTarget);
  camera.updateMatrixWorld(true);
  overlayRevision.value += 1;
}

function focusOnPoint(point) {
  if (!point) return;
  focusedPoint = true;
  viewCenter.value = { x: Number(point.x) || 0, y: Number(point.y) || 0 };
  if (!cameraTarget || !mapGroup) return;
  mapGroup.updateMatrixWorld(true);
  cameraTarget.set(Number(point.x) || 0, Number(point.y) || 0, mapSurfaceZ()).applyMatrix4(mapGroup.matrixWorld);
  updateCameraPose();
}

function updatePointMarkerScale() {
  if (!pointGroup) return;
  // Keep the Three.js halo near a fixed 8–18px screen size while zooming.
  const markerScale = 0.62 / zoom.value;
  pointGroup.children.forEach(marker => marker.scale.setScalar(markerScale));
}

function disposeMaterial(material) {
  const materials = Array.isArray(material) ? material : [material];
  materials.filter(Boolean).forEach(item => {
    if (item.map?.dispose && item.map !== surfaceTexture) item.map.dispose();
    item.dispose?.();
  });
}

function disposeObject(root) {
  if (!root) return;
  root.traverse(object => {
    object.geometry?.dispose?.();
    disposeMaterial(object.material);
  });
}

function clearMapGroup() {
  hoveredRegionCode.value = '';
  regionSurfaces = [];
  if (mapGroup) {
    disposeObject(mapGroup);
    scene?.remove(mapGroup);
  }
  if (pointGroup) {
    disposeObject(pointGroup);
    scene?.remove(pointGroup);
  }
  mapGroup = new THREE.Group();
  pointGroup = new THREE.Group();
  if (reliefEnabled.value) {
    mapGroup.rotation.x = reliefConfig.value.rotationX;
    mapGroup.rotation.z = reliefConfig.value.rotationZ;
  } else {
    mapGroup.rotation.x = props.mode === 'province' ? -0.30 : -0.24;
    mapGroup.rotation.z = props.mode === 'province' ? 0.025 : 0.018;
  }
  // Relief points must sit on the same tilted top plane as their source map.
  // Keep the existing classic point group untouched for retail compatibility.
  if (reliefEnabled.value) pointGroup.rotation.copy(mapGroup.rotation);
  scene?.add(mapGroup);
  scene?.add(pointGroup);
}

function shapeFromPolygon(polygon) {
  const shape = new THREE.Shape();
  polygon.outer.forEach((point, index) => {
    if (index === 0) shape.moveTo(point.x, point.y);
    else shape.lineTo(point.x, point.y);
  });
  polygon.holes.forEach(ring => {
    if (!ring.length) return;
    const hole = new THREE.Path();
    ring.forEach((point, index) => {
      if (index === 0) hole.moveTo(point.x, point.y);
      else hole.lineTo(point.x, point.y);
    });
    shape.holes.push(hole);
  });
  return shape;
}

function addReliefContour(ring, material, z, userData, scale = 1) {
  if (!Array.isArray(ring) || ring.length < 2) return;
  const points = ring.map(point => new THREE.Vector3(point.x * scale, point.y * scale, z));
  const geometry = new THREE.BufferGeometry().setFromPoints(points);
  const line = new THREE.LineLoop(geometry, material);
  line.userData = userData;
  mapGroup.add(line);
}

function addReliefGroundShadow(polygon, material, z, userData, spread) {
  if (!polygon.outer.length) return;
  const geometry = new THREE.ShapeGeometry(shapeFromPolygon(polygon));
  const shadow = new THREE.Mesh(geometry, material);
  shadow.position.z = z;
  // GeoJSON is projected around the province center, so a small uniform expansion
  // keeps the shadow attached to its source polygon while softening the footprint.
  shadow.scale.setScalar(1 + (spread / Math.max(projection.value.width, projection.value.height, 1)));
  shadow.userData = { ...userData, decorative: true };
  mapGroup.add(shadow);
}

function buildThreeMap() {
  try {
    buildThreeMapUnsafe();
  } catch (error) {
    activateFallback('rebuild', error);
  }
}

function ensureSurfaceTexture() {
  if (!reliefEnabled.value || surfaceTexture) return;
  surfaceTexture = new THREE.TextureLoader().load(reliefSurfaceUrl, texture => {
    if (disposed || !renderer) { texture.dispose(); return; }
    surfaceReady.value = true;
    renderFrame();
  });
  surfaceTexture.wrapS = surfaceTexture.wrapT = THREE.RepeatWrapping;
  surfaceTexture.repeat.set(.055, .055);
  surfaceTexture.colorSpace = THREE.NoColorSpace;
}

function syncLighting() {
  if (!scene) return;
  if (!ambientLight) { ambientLight = new THREE.AmbientLight(0xaac7ff); scene.add(ambientLight); }
  if (!keyLight) {
    keyLight = new THREE.DirectionalLight(0xb8dfff);
    keyLight.position.set(-3, -4, 10); scene.add(keyLight);
  }
  ambientLight.intensity = reliefEnabled.value ? .7 : 1.65;
  keyLight.intensity = reliefEnabled.value ? 3.1 : 2.3;
  keyLight.color.setHex(reliefEnabled.value ? 0xd4efff : 0x9ec7ff);
  if (reliefEnabled.value) {
    if (!surfaceLight) {
      surfaceLight = new THREE.PointLight(0x79dfff, 38, 30, 2);
      surfaceLight.position.set(-4, 3, 7); scene.add(surfaceLight);
      rimLight = new THREE.DirectionalLight(0x688eff, .9);
      rimLight.position.set(5, 3, 2); scene.add(rimLight);
    }
  } else {
    for (const light of [surfaceLight, rimLight]) { if (light) { scene.remove(light); light.dispose?.(); } }
    surfaceLight = rimLight = null;
  }
}

function buildThreeMapUnsafe() {
  if (!scene) {
    renderedRegionCount.value = 0;
    return;
  }
  syncLighting();
  ensureSurfaceTexture();
  clearMapGroup();
  const polygons = projectGeoJson(props.geoJson, projection.value);
  renderedRegionCount.value = polygons.length;
  const relief = reliefEnabled.value;
  const config = reliefConfig.value;
  const topMaterial = relief ? new THREE.MeshStandardMaterial({
    color: 0x304c9c,
    emissive: 0x172451,
    emissiveIntensity: .24,
    metalness: .16,
    roughness: .62,
    map: surfaceTexture,
    bumpMap: surfaceTexture,
    bumpScale: .024
  }) : new THREE.MeshPhongMaterial({
    color: relief ? 0x1057b9 : 0x304ea4,
    emissive: relief ? 0x09235e : 0x101a58,
    emissiveIntensity: relief ? 0.10 : 0.65,
    shininess: relief ? 48 : 70,
    map: relief ? surfaceTexture : null,
    bumpMap: relief ? surfaceTexture : null,
    bumpScale: relief ? .075 : 1,
    specular: relief ? 0x579bdf : 0x111111,
    transparent: true,
    opacity: relief ? 0.98 : 0.93
  });
  const selectedTopMaterial = relief ? new THREE.MeshStandardMaterial({
    color: 0x7256bd,
    emissive: 0x392369,
    emissiveIntensity: .4,
    metalness: .12,
    roughness: .48,
    map: surfaceTexture,
    bumpMap: surfaceTexture,
    bumpScale: .024
  }) : new THREE.MeshPhongMaterial({
    color: relief ? 0x536bff : 0x6f43ba,
    emissive: relief ? 0x17136a : 0x31135f,
    emissiveIntensity: relief ? 0.72 : 0.88,
    shininess: relief ? 75 : 85,
    map: relief ? surfaceTexture : null,
    bumpMap: relief ? surfaceTexture : null,
    bumpScale: relief ? .075 : 1,
    transparent: true,
    opacity: relief ? 0.99 : 0.96
  });
  const sideMaterial = relief ? new THREE.MeshStandardMaterial({
    color: 0xffffff,
    vertexColors: true,
    metalness: .12,
    roughness: .58
  }) : new THREE.MeshPhongMaterial({
    color: relief ? 0x0a255f : (props.mode === 'province' ? 0x1d3479 : 0x152763),
    emissive: relief ? 0x061638 : (props.mode === 'province' ? 0x10245d : 0x090f35),
    emissiveIntensity: relief ? 0.12 : (props.mode === 'province' ? 0.58 : 0.35),
    shininess: relief ? 36 : (props.mode === 'province' ? 36 : 25),
    transparent: true,
    opacity: relief ? 0.98 : (props.mode === 'province' ? 0.96 : 0.92)
  });
  if (relief) {
    const baseTopMaterial = new THREE.MeshPhongMaterial({
      color: 0x152954,
      emissive: 0x050d2e,
      emissiveIntensity: 0.15,
      shininess: 25,
      transparent: true,
      opacity: 0.96
    });
    const baseSideMaterial = new THREE.MeshPhongMaterial({
      color: 0x0b1634,
      emissive: 0x030b24,
      emissiveIntensity: 0.22,
      shininess: 18,
      transparent: true,
      opacity: 0.94
    });
    const topContourMaterial = new THREE.LineBasicMaterial({
      color: 0x8aa8f0,
      transparent: true,
      opacity: 0.72
    });
    const selectedContourMaterial = new THREE.LineBasicMaterial({
      color: 0xd1efff,
      transparent: true,
      opacity: 0.99
    });
    const bottomContourMaterial = new THREE.LineBasicMaterial({
      color: 0x7166be,
      transparent: true,
      opacity: 0.45
    });
    const shadowMaterial = new THREE.MeshBasicMaterial({
      color: 0x02091e,
      transparent: true,
      opacity: 0.27,
      depthWrite: false
    });
    const shadowGlowMaterial = new THREE.MeshBasicMaterial({
      color: 0x071c52,
      transparent: true,
      opacity: 0.13,
      depthWrite: false
    });

    polygons.forEach(polygon => {
      if (!polygon.outer.length) return;
      const userData = { type: 'region', code: polygon.code, name: polygon.name };
      const selected = polygon.code && String(polygon.code) === String(props.selectedRegionCode);
      addReliefGroundShadow(
        polygon,
        shadowMaterial,
        -config.baseDepth * 1.8 - config.shadowGap,
        userData,
        config.shadowSpread * 1.35
      );
      addReliefGroundShadow(
        polygon,
        shadowGlowMaterial,
        -config.baseDepth * 1.8 - config.shadowGap * 0.58,
        userData,
        config.shadowSpread * 0.58
      );

      const baseGeometry = new THREE.ExtrudeGeometry(shapeFromPolygon(polygon), {
        depth: config.baseDepth,
        bevelEnabled: false,
        steps: 1,
        curveSegments: 1
      });
      const baseMesh = new THREE.Mesh(baseGeometry, [baseTopMaterial, baseSideMaterial]);
      baseMesh.position.z = -config.baseDepth;
      baseMesh.scale.set(1.018, 1.018, 1);
      baseMesh.userData = userData;
      mapGroup.add(baseMesh);

      const geometry = new THREE.ExtrudeGeometry(shapeFromPolygon(polygon), {
        depth: config.depth,
        bevelEnabled: true,
        bevelThickness: 0.012,
        bevelSize: 0.012,
        bevelSegments: 1,
        steps: 1,
        curveSegments: 1
      });
      const regionTopMaterial = (selected ? selectedTopMaterial : topMaterial).clone();
      if (props.colorByMetric) regionTopMaterial.color.set(metricColorForCode(polygon.code));
      const mesh = new THREE.Mesh(geometry, [regionTopMaterial, sideMaterial.clone()]);
      geometry.setAttribute('color', new THREE.BufferAttribute(
        createReliefWallColors(geometry.attributes.position.array, config.depth), 3
      ));
      geometry.setAttribute('normal', new THREE.BufferAttribute(
        smoothReliefWallNormals(geometry.attributes.position.array, geometry.attributes.normal.array), 3
      ));
      mesh.userData = userData;
      mesh.userData.restColor = mesh.material[0].color.getHex();
      mesh.userData.restEmissive = mesh.material[0].emissive.getHex();
      regionSurfaces.push(mesh);
      mapGroup.add(mesh);
      const topMaterialForPolygon = selected ? selectedContourMaterial : topContourMaterial;
      addReliefContour(polygon.outer, topMaterialForPolygon, config.depth + config.contourLift, userData);
      polygon.holes.forEach(ring => addReliefContour(ring, topMaterialForPolygon, config.depth + config.contourLift, userData));
      addReliefContour(polygon.outer, bottomContourMaterial, -config.baseDepth - config.contourLift, userData, 1.018);
      polygon.holes.forEach(ring => addReliefContour(ring, bottomContourMaterial, -config.baseDepth - config.contourLift, userData, 1.018));
    });
  } else {
    const edgeMaterial = new THREE.LineBasicMaterial({
      color: props.mode === 'province' ? 0xb8d4ff : 0x83b9ff,
      transparent: true,
      opacity: 0.96
    });
    const sideGlowMaterial = props.mode === 'province'
      ? new THREE.LineBasicMaterial({ color: 0xa979ff, transparent: true, opacity: 0.42 })
      : null;

    polygons.forEach(polygon => {
      if (!polygon.outer.length) return;
      const geometry = new THREE.ExtrudeGeometry(shapeFromPolygon(polygon), {
        depth: props.mode === 'province' ? 0.28 : 0.18,
        bevelEnabled: false,
        steps: 1,
        curveSegments: 1
      });
      const selected = polygon.code && String(polygon.code) === String(props.selectedRegionCode);
      const regionTopMaterial = (selected ? selectedTopMaterial : topMaterial).clone();
      if (props.colorByMetric) regionTopMaterial.color.set(metricColorForCode(polygon.code));
      const mesh = new THREE.Mesh(geometry, [regionTopMaterial, sideMaterial.clone()]);
      mesh.userData = { type: 'region', code: polygon.code, name: polygon.name };
      mapGroup.add(mesh);
      const edges = new THREE.LineSegments(new THREE.EdgesGeometry(geometry), edgeMaterial);
      edges.userData = mesh.userData;
      mapGroup.add(edges);
      if (sideGlowMaterial) {
        const glowEdges = new THREE.LineSegments(new THREE.EdgesGeometry(geometry), sideGlowMaterial);
        glowEdges.position.z = -0.022;
        glowEdges.scale.setScalar(1.004);
        glowEdges.userData = mesh.userData;
        mapGroup.add(glowEdges);
      }
    });
  }

  drawablePoints.value.forEach(point => {
    const projected = projection.value.project([point.lng, point.lat]);
    const group = new THREE.Group();
    group.position.set(projected.x, projected.y, relief
      ? config.depth + config.contourLift + 0.02
      : 0.32);
    group.userData = { type: 'point', point };
    const selected = String(point.orgCode) === String(props.selectedOrgCode);
    const dotMaterial = new THREE.MeshBasicMaterial({ color: selected ? 0xf2c7ff : 0x56edee });
    const ringMaterial = new THREE.MeshBasicMaterial({
      color: selected ? 0xdb7cff : 0x75e9ff,
      transparent: true,
      opacity: selected ? 0.98 : 0.7,
      side: THREE.DoubleSide
    });
    group.add(new THREE.Mesh(new THREE.SphereGeometry(selected ? 0.1 : 0.075, 16, 12), dotMaterial));
    group.add(new THREE.Mesh(new THREE.RingGeometry(selected ? 0.17 : 0.13, selected ? 0.2 : 0.16, 32), ringMaterial));
    pointGroup.add(group);
  });
  updatePointMarkerScale();
}

function resizeRenderer() {
  if (!containerRef.value) return;
  // The SVG fallback still needs a reactive revision: its `meet` letterbox is
  // converted to container percentages for the shared HTML callout layer.
  overlayRevision.value += 1;
  if (!renderer || !camera) return;
  try {
    const width = Math.max(1, containerRef.value.clientWidth || 800);
    const height = Math.max(1, containerRef.value.clientHeight || 520);
    renderer.setSize(width, height, false);
    const aspect = width / height;
    updateCameraPose();
    camera.updateMatrixWorld(true);

    if (reliefEnabled.value && mapGroup) {
      // Project actual region vertices, excluding oversized decorative shadows.
      // Projecting an already-rotated world AABB adds empty corners twice and
      // makes the map much smaller than the available canvas.
      mapGroup.updateMatrixWorld(true);
      const vertices = [];
      const vertex = new THREE.Vector3();
      mapGroup.traverse(object => {
        const position = object.geometry?.attributes?.position;
        if (!object.isMesh || object.userData?.type !== 'region' || object.userData?.decorative || !position) return;
        for (let index = 0; index < position.count; index += 1) {
          vertex.fromBufferAttribute(position, index)
            .applyMatrix4(object.matrixWorld).applyMatrix4(camera.matrixWorldInverse);
          vertices.push({ x: vertex.x, y: vertex.y });
        }
      });
      const configuredFitHeight = Number(props.viewFit?.reliefFitHeight);
      const fit = fitReliefView(vertices, {
        aspect,
        fitHeight: Number.isFinite(configuredFitHeight) && configuredFitHeight > 0 ? configuredFitHeight : reliefConfig.value.fitHeight
      });
      if (fit) Object.assign(camera, fit);
    } else {
      const map = worldBounds();
      // Preserve the classic camera fit for retail and other existing callers.
      const configuredPadding = Number(props.viewFit?.classicPadding);
      const fitPadding = Number.isFinite(configuredPadding) && configuredPadding > 0
        ? configuredPadding
        : props.mode === 'province' ? 1.02 : 1.0;
      const cameraHeight = Math.max(map.height * fitPadding, map.width / aspect * fitPadding, 5);
      const cameraWidth = cameraHeight * aspect;
      camera.left = -cameraWidth / 2;
      camera.right = cameraWidth / 2;
      camera.top = cameraHeight / 2;
      camera.bottom = -cameraHeight / 2;
    }
    camera.zoom = zoom.value;
    camera.updateProjectionMatrix();
    renderFrame();
  } catch (error) {
    activateFallback('resize', error);
  }
}

function renderFrame() {
  if (!renderer || !scene || !camera || disposed || fallbackActive.value) return;
  try {
    renderer.render(scene, camera);
  } catch (error) {
    activateFallback('render', error);
  }
}

function pointerPosition(event) {
  const rect = renderer.domElement.getBoundingClientRect();
  pointer.set(
    ((event.clientX - rect.left) / rect.width) * 2 - 1,
    -((event.clientY - rect.top) / rect.height) * 2 + 1
  );
}

function onThreePointer(event) {
  if (Date.now() < suppressActivationUntil || panOrigin?.moved) return;
  if (!raycaster || !pointer || !renderer || !camera) return;
  pointerPosition(event);
  raycaster.setFromCamera(pointer, camera);
  const hits = raycaster.intersectObjects([mapGroup, pointGroup], true);
  const hit = hits.find(item => item.object.userData?.type && !item.object.userData.decorative);
  if (!hit) return;
  const target = hit.object.userData;
  if (target.type === 'region') selectRegion(target);
  if (target.type === 'point') selectPoint(target.point);
}

/** Highlight all polygons of one city without changing the business selection or rebuilding geometry. */
let cityLeaveTimer = null;
function keepCityDetail() { clearTimeout(cityLeaveTimer); }
function leaveHoveredRegion() {
  keepCityDetail();
  if (hasInstitutionMetrics.value) cityLeaveTimer = setTimeout(() => setHoveredRegion(''), 350);
  else setHoveredRegion('');
}
function setHoveredRegion(code) {
  keepCityDetail();
  const next = (reliefEnabled.value || isCalloutLayout.value) && code ? String(code) : '';
  if (next === hoveredRegionCode.value) return;
  hoveredRegionCode.value = next;
  regionSurfaces.forEach(mesh => {
    const active = next && String(mesh.userData.code) === next;
    mesh.material[0].color.setHex(active ? 0xa27aeb : mesh.userData.restColor);
    mesh.material[0].emissive.setHex(active ? 0x4b2b82 : mesh.userData.restEmissive);
    mesh.material[1].color.setHex(active ? 0xb9a5ff : 0xffffff);
  });
  if (renderer?.domElement) renderer.domElement.style.cursor = next ? 'pointer' : '';
  renderFrame();
}

function onThreeHover(event) {
  if (!reliefEnabled.value || !renderer || !camera || !raycaster || !pointer) return;
  pointerPosition(event);
  raycaster.setFromCamera(pointer, camera);
  const hit = raycaster.intersectObjects(regionSurfaces, false)[0];
  if (hit) setHoveredRegion(hit.object.userData.code);
  else leaveHoveredRegion();
}

function activateFallback(reason, error) {
  if (disposed) return;
  const firstFailure = !fallbackActive.value;
  fallbackActive.value = true;
  webglReady.value = false;
  if (firstFailure) {
    console.warn(`[PanoramaMap] WebGL ${reason} failed, using SVG fallback.`, error);
  }
  if (handlingFailure) return;
  handlingFailure = true;
  try {
    disposeThree();
  } finally {
    handlingFailure = false;
  }
  renderedRegionCount.value = projectedRegions.value.length;
}

function onWebGLContextLost(event) {
  event?.preventDefault?.();
  activateFallback('context lost');
}

function setupThree() {
  if (!canvasRef.value || !containerRef.value) return;
  let context = null;
  try {
    // Three.js r185 requires WebGL2; trying WebGL1 first can create a renderer
    // that later fails during the first draw and leaves a white canvas covering
    // the real SVG fallback.
    context = canvasRef.value.getContext?.('webgl2');
  } catch {
    context = null;
  }
  if (typeof window === 'undefined' || typeof window.WebGL2RenderingContext === 'undefined' || !context) {
    fallbackActive.value = true;
    return;
  }
  try {
    renderer = new THREE.WebGLRenderer({ canvas: canvasRef.value, alpha: true, antialias: true });
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.NoToneMapping;
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.setClearColor(0x07163d, 0);
    scene = new THREE.Scene();
    camera = new THREE.OrthographicCamera(-5, 5, 5, -5, 0.1, 100);
    cameraTarget = new THREE.Vector3(0, 0, 0);
    updateCameraPose();
    raycaster = new THREE.Raycaster();
    pointer = new THREE.Vector2();
    syncLighting();
    contextLostHandler = onWebGLContextLost;
    renderer.domElement.addEventListener('webglcontextlost', contextLostHandler, false);
    buildThreeMap();
    if (fallbackActive.value || !renderer) return;
    pointerHandler = onThreePointer;
    renderer.domElement.addEventListener('pointerup', pointerHandler);
    renderer.domElement.addEventListener('pointermove', onThreeHover);
    resizeRenderer();
    if (fallbackActive.value || !renderer) return;
    webglReady.value = true;
  } catch (error) {
    // 创建上下文失败是普通低配浏览器情况，保留 SVG 真实几何回退并释放半成品。
    activateFallback('setup', error);
  }
}

function disposeThree() {
  renderer?.domElement?.removeEventListener('pointermove', onThreeHover);
  regionSurfaces = [];
  if (renderer?.domElement && pointerHandler) renderer.domElement.removeEventListener('pointerup', pointerHandler);
  if (renderer?.domElement && contextLostHandler) {
    renderer.domElement.removeEventListener('webglcontextlost', contextLostHandler);
  }
  pointerHandler = null;
  contextLostHandler = null;
  disposeObject(scene);
  const activeRenderer = renderer;
  try { activeRenderer?.forceContextLoss?.(); } catch { /* context may already be lost */ }
  try { activeRenderer?.dispose?.(); } catch { /* disposal must not block SVG fallback */ }
  surfaceTexture?.dispose?.();
  surfaceTexture = null;
  surfaceReady.value = false;
  ambientLight = keyLight = surfaceLight = rimLight = null;
  renderer = null;
  scene = null;
  camera = null;
  cameraTarget = null;
  mapGroup = null;
  pointGroup = null;
  raycaster = null;
  pointer = null;
  webglReady.value = false;
  renderedRegionCount.value = 0;
}

function rebuildThreeMap() {
  if (!webglReady.value || fallbackActive.value) return;
  buildThreeMap();
  if (fallbackActive.value || !renderer) return;
  resizeRenderer();
}

onMounted(() => {
  window.addEventListener('scroll', repositionCityDetail, { capture: true, passive: true });
  setupThree();
  if (typeof ResizeObserver !== 'undefined' && containerRef.value) {
    resizeObserver = new ResizeObserver(resizeRenderer);
    resizeObserver.observe(containerRef.value);
  } else if (typeof window !== 'undefined') {
    window.addEventListener('resize', resizeRenderer);
  }
});

watch(() => [
  props.geoJson, props.points, props.selectedOrgCode, props.selectedRegionCode, props.mode,
  props.demo, props.appearance, props.metricNumericValues, props.metricColors, props.colorByMetric,
  props.viewFit
], () => {
  activeCluster.value = null;
  rebuildThreeMap();
}, { deep: true });

onBeforeUnmount(() => {
  keepCityDetail();
  window.removeEventListener('scroll', repositionCityDetail, true);
  disposed = true;
  resizeObserver?.disconnect?.();
  resizeObserver = null;
  if (typeof window !== 'undefined') window.removeEventListener('resize', resizeRenderer);
  disposeThree();
});
</script>

<style scoped>
.panorama-map {
  position: relative;
  min-width: 240px;
  min-height: 260px;
  width: 100%;
  height: 100%;
  overflow: hidden;
  border: 1px solid rgba(88, 137, 238, .35);
  border-radius: 12px;
  background: radial-gradient(circle at 52% 44%, rgba(46, 72, 157, .38), rgba(4, 14, 47, .98) 70%);
  color: #d8e8ff;
}

.panorama-map[data-mode='city'][data-pan-enabled='true'] { cursor: grab; touch-action: none; }
.panorama-map[data-mode='city'][data-pan-enabled='true'].is-dragging { cursor: grabbing; user-select: none; }

.panorama-map[data-appearance='relief'] {
  border-color: rgba(74, 164, 255, .56);
  background:
    radial-gradient(circle at 52% 41%, rgba(24, 100, 224, .34), transparent 46%),
    radial-gradient(circle at 50% 68%, rgba(97, 47, 188, .18), transparent 52%),
    linear-gradient(180deg, rgba(4, 20, 62, .94), rgba(2, 9, 31, .99));
  box-shadow: inset 0 0 30px rgba(39, 109, 228, .12), 0 0 22px rgba(24, 89, 213, .16);
}

.panorama-map[data-appearance='relief']::after {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  content: '';
  background: radial-gradient(ellipse at 50% 84%, rgba(38, 79, 178, .2), transparent 58%);
}

.panorama-map::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image: linear-gradient(rgba(91, 142, 249, .08) 1px, transparent 1px), linear-gradient(90deg, rgba(91, 142, 249, .08) 1px, transparent 1px);
  background-size: 32px 32px;
  mask-image: linear-gradient(to bottom, transparent, #000 30%, transparent);
}

.panorama-map__canvas,
.panorama-map__fallback {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.panorama-map__canvas { z-index: 1; }
.panorama-map__canvas.is-hidden { display: none; }
.panorama-map__fallback { z-index: 1; padding: 18px 52px 24px 22px; }
.panorama-map__svg { display: block; width: 100%; height: 100%; overflow: visible; }
.panorama-map__region path { fill: rgba(58, 83, 177, .76); stroke: #83b9ff; stroke-width: .24; vector-effect: non-scaling-stroke; cursor: pointer; transition: fill .2s ease; }
.panorama-map__region path:hover,
.panorama-map__region path:focus-visible,
.panorama-map__region.is-selected path,
.panorama-map__region.is-hovered path { fill: rgba(131, 84, 217, .9); stroke: #f3c8ff; outline: none; }
.panorama-map__region-label text { fill: rgba(227, 239, 255, .88); font-size: 2.1px; text-anchor: middle; pointer-events: auto; cursor: pointer; }
.panorama-map__city-halo-svg { fill: rgba(75, 233, 255, .78); stroke: rgba(151, 249, 255, .98); stroke-width: .28; pointer-events: none; filter: drop-shadow(0 0 1.1px rgba(73, 235, 255, .95)); }
.panorama-map__city-halo-svg.is-violet { fill: rgba(188, 116, 255, .78); stroke: rgba(237, 190, 255, .98); filter: drop-shadow(0 0 1.1px rgba(205, 130, 255, .95)); }
.panorama-map__city-halo-layer { position: absolute; z-index: 2; inset: 0; pointer-events: none; }
.panorama-map__city-halo { position: absolute; width: 18px; height: 18px; transform: translate(-50%, -50%); border: 1px solid rgba(132, 247, 255, .94); border-radius: 50%; background: radial-gradient(circle, rgba(112, 249, 255, .94) 0 2px, rgba(50, 211, 237, .34) 3px 5px, rgba(50, 211, 237, 0) 72%); box-shadow: 0 0 7px rgba(76, 233, 255, .88), inset 0 0 7px rgba(104, 242, 255, .66); color: #68efff; opacity: .9; }
.panorama-map__city-halo::after { content: ''; position: absolute; inset: -4px; border: 1px solid currentColor; border-radius: 50%; opacity: .38; }
.panorama-map__city-halo.is-violet { border-color: rgba(226, 173, 255, .98); background: radial-gradient(circle, rgba(231, 176, 255, .96) 0 2px, rgba(172, 96, 247, .4) 3px 5px, rgba(172, 96, 247, 0) 72%); box-shadow: 0 0 7px rgba(197, 119, 255, .92), inset 0 0 7px rgba(214, 143, 255, .68); color: #d28cff; }
.panorama-map__callout-lines { position: absolute; z-index: 3; inset: 0; width: 100%; height: 100%; overflow: visible; pointer-events: none; }
.panorama-map__callout-line { fill: none; stroke: rgba(102, 226, 239, .94); stroke-width: 1.2px; vector-effect: non-scaling-stroke; stroke-linecap: round; stroke-linejoin: round; filter: drop-shadow(0 0 1px rgba(53, 205, 229, .6)); }
.panorama-map__callout-anchor { fill: none; stroke: rgba(202, 255, 255, .98); stroke-width: 2.4px; vector-effect: non-scaling-stroke; stroke-linecap: round; }
.panorama-map__region-label-layer { position: absolute; z-index: 4; inset: 0; pointer-events: none; }
.panorama-map__metric-heading { position: absolute; z-index: 4; top: 10px; left: 12px; padding: 4px 8px; border-radius: 4px; color: #8ce6e1; background: #07182ccc; font-size: 11px; pointer-events: none; }
.panorama-map__metric-value { display: block; font-size: 10px; color: #96eee6; font-variant-numeric: tabular-nums; line-height: 1.25; }
.panorama-map__metric-svg { fill: #96eee6 !important; font-size: 2px !important; pointer-events: none; }
.panorama-map__region-label-hit { position: absolute; transform: translate(-50%, -50%); padding: 1px 3px; border: 1px solid transparent; border-radius: 3px; color: rgba(227, 239, 255, .82); background: transparent; text-shadow: 0 1px 3px #05133b, 0 0 5px #05133b; font-size: 11px; white-space: nowrap; cursor: pointer; pointer-events: auto; }
.panorama-map__region-label-hit:hover, .panorama-map__region-label-hit:focus-visible, .panorama-map__region-label-hit.is-selected, .panorama-map__region-label-hit.is-hovered { border-color: #f0caff; color: #fff1ff; background: rgba(101, 61, 175, .88); outline: 2px solid rgba(210, 160, 255, .32); }
.panorama-map[data-appearance='relief'] .panorama-map__region-label-hit { color: rgba(237, 247, 255, .94); font-weight: 700; text-shadow: 0 1px 4px #03133d, 0 0 8px #03133d; }
.panorama-map[data-label-layout='callout'] .panorama-map__region-label-hit { min-width: 42px; padding: 2px 4px; color: rgba(232, 247, 255, .96); font-weight: 700; line-height: 1.2; text-align: center; text-shadow: 0 1px 4px #03133d, 0 0 8px #03133d; }
.panorama-map[data-label-layout='callout'] .panorama-map__metric-value { color: #8cf3e8; font-size: 10px; font-weight: 600; }

.panorama-map__point-layer { position: absolute; z-index: 3; inset: 0; pointer-events: none; }
.panorama-map__point-hit { position: absolute; transform: translate(-50%, -50%); min-width: 22px; min-height: 22px; padding: 0; border: 0; border-radius: 999px; color: #fff; background: transparent; cursor: pointer; pointer-events: auto; }
.panorama-map__point-dot { display: block; width: 10px; height: 10px; margin: auto; border: 2px solid #83fbff; border-radius: 50%; background: #37dce1; box-shadow: 0 0 8px #37dce1, 0 0 22px rgba(55, 220, 225, .8); }
.panorama-map__point-hit.is-selected .panorama-map__point-dot { width: 13px; height: 13px; border-color: #f4d5ff; background: #d783ff; box-shadow: 0 0 9px #d783ff, 0 0 28px rgba(215, 131, 255, .95); }
.panorama-map__point-label { display: block; position: absolute; z-index: 1; top: 18px; left: 50%; transform: translateX(-50%); max-width: 180px; white-space: nowrap; padding: 2px 6px; border: 1px solid rgba(112, 192, 255, .5); border-radius: 4px; color: #eef7ff; background: rgba(6, 22, 62, .94); box-shadow: 0 2px 8px rgba(0, 7, 28, .46); font-size: 11px; line-height: 1.35; text-shadow: 0 1px 2px #020918; }
.panorama-map__point-hit:hover .panorama-map__point-label,
.panorama-map__point-hit:focus-visible .panorama-map__point-label,
.panorama-map__point-hit.is-selected .panorama-map__point-label { z-index: 2; border-color: #b6f7ff; background: rgba(17, 47, 104, .98); }
.panorama-map__cluster-count { display: grid; place-items: center; width: 25px; height: 25px; border: 2px solid #83fbff; border-radius: 50%; background: rgba(25, 103, 182, .9); box-shadow: 0 0 12px rgba(71, 229, 255, .85); font-size: 11px; }
.panorama-map__cluster-picker { position: absolute; z-index: 7; top: 50%; right: 58px; min-width: 170px; max-width: min(250px, calc(100% - 72px)); max-height: min(260px, calc(100% - 32px)); padding: 8px; overflow: auto; border: 1px solid rgba(128, 221, 255, .72); border-radius: 8px; background: rgba(7, 22, 61, .94); box-shadow: 0 8px 28px rgba(0, 0, 0, .38), 0 0 18px rgba(73, 209, 255, .2); transform: translateY(-50%); }
.panorama-map__cluster-picker-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 5px; color: #e7f5ff; font-size: 12px; }
.panorama-map__cluster-picker-head button { width: 22px; height: 22px; padding: 0; border: 0; border-radius: 4px; color: #d9edff; background: transparent; font-size: 18px; line-height: 1; cursor: pointer; }
.panorama-map__cluster-picker-head button:hover, .panorama-map__cluster-picker-head button:focus-visible { background: rgba(107, 183, 255, .22); outline: 1px solid rgba(157, 228, 255, .72); }
.panorama-map__cluster-member { display: flex; align-items: baseline; justify-content: space-between; width: 100%; gap: 8px; margin-top: 4px; padding: 7px 8px; border: 1px solid rgba(115, 174, 255, .28); border-radius: 5px; color: #ddedff; background: rgba(28, 58, 130, .64); text-align: left; cursor: pointer; }
.panorama-map__cluster-member:hover, .panorama-map__cluster-member:focus-visible { border-color: #a1f5ff; background: rgba(70, 101, 184, .82); outline: 2px solid rgba(137, 237, 255, .24); }
.panorama-map__cluster-member small { color: rgba(197, 225, 255, .68); font-size: 10px; }

.panorama-map__controls { position: absolute; z-index: 5; right: 14px; bottom: 14px; display: grid; gap: 5px; }
.panorama-map__controls button { width: 32px; height: 32px; border: 1px solid rgba(138, 193, 255, .6); border-radius: 5px; background: rgba(12, 33, 89, .88); color: #dbeaff; font-size: 20px; line-height: 1; cursor: pointer; }
.panorama-map__controls button:hover, .panorama-map__controls button:focus-visible { border-color: #82f4ff; outline: 2px solid rgba(130, 244, 255, .5); }
.panorama-map__controls button:disabled { cursor: not-allowed; opacity: .42; }
.panorama-map__fallback-status { position: absolute; z-index: 4; left: 14px; bottom: 12px; max-width: calc(100% - 100px); margin: 0; color: rgba(194, 221, 255, .78); font-size: 11px; pointer-events: none; }
.panorama-map__empty { position: absolute; z-index: 4; inset: 50% auto auto 50%; transform: translate(-50%, -50%); margin: 0; color: rgba(194, 221, 255, .82); font-size: 13px; white-space: nowrap; }
.panorama-map__unmapped { position: absolute; z-index: 6; bottom: 42px; left: 12px; max-width: min(245px, 50%); margin: 0; padding: 8px 10px; border: 1px solid rgba(255, 181, 79, .5); border-radius: 6px; background: rgba(28, 25, 57, .86); color: #ffe2a8; font-size: 11px; }
.panorama-map__unmapped strong, .panorama-map__unmapped span { display: block; }
.panorama-map__unmapped span { margin-top: 2px; color: rgba(255, 226, 168, .72); }
.panorama-map__unmapped ul { max-height: 80px; margin: 4px 0 0; padding-left: 15px; overflow: auto; }

/* City accents connect the label, curve and geographic marker as one visual unit. */
.panorama-map__callout-line { stroke: var(--city-accent); stroke-width: 1.35px; opacity: .72; filter: none; transition: opacity .18s, stroke-width .18s; }
.panorama-map__callout-glow { fill: none; stroke: var(--city-accent); stroke-width: 5px; vector-effect: non-scaling-stroke; opacity: .05; }
.panorama-map__callout-anchor { stroke: var(--city-accent); stroke-width: 4px; }
.panorama-map__callout.is-missing .panorama-map__callout-line { stroke-dasharray: 3 5; opacity: .38; }
.panorama-map__callout.is-active .panorama-map__callout-line { stroke-width: 2px; opacity: 1; stroke-dasharray: 9 4; animation: city-leader-flow 1.8s linear infinite; }
.panorama-map__callout.is-active .panorama-map__callout-glow { opacity: .18; }
.panorama-map[data-label-layout='callout'] .panorama-map__region-label-hit {
  width: 86px; min-height: 36px; padding: 4px 8px 4px 12px; border: 1px solid rgba(124, 169, 226, .14); border-radius: 7px;
  background: linear-gradient(115deg, rgba(20, 40, 76, .85), rgba(8, 22, 52, .54));
  text-align: left; line-height: 1.25; box-shadow: 0 4px 12px rgba(0, 7, 27, .12); font-size: 11px; font-weight: 600;
  transition: border-color .18s, background .18s, box-shadow .18s;
}
.panorama-map__city-marker { position: absolute; left: 0; top: 9px; bottom: 9px; width: 2px; border-radius: 2px; background: var(--city-accent); }
.panorama-map[data-label-layout='callout'] .panorama-map__metric-value { color: var(--city-accent); margin-top: 2px; font-size: 10px; font-weight: 650; }
.panorama-map[data-label-layout='callout'] .panorama-map__region-label-hit.is-missing .panorama-map__metric-value { color: #879bb9; font-weight: 400; }
.panorama-map[data-label-layout='callout'] .panorama-map__region-label-hit:is(:hover,:focus-visible,.is-hovered,.is-selected) { border-color: var(--city-accent); background: #142b52; outline: none; box-shadow: 0 0 0 2px color-mix(in srgb,var(--city-accent) 12%,transparent), 0 4px 16px #020b24; }
.panorama-map[data-label-layout='callout'] .panorama-map__city-halo { color: var(--city-accent); border-color: var(--city-accent); background: radial-gradient(circle,var(--city-accent) 0 2px,color-mix(in srgb,var(--city-accent) 28%,transparent) 3px 5px,transparent 72%); box-shadow: 0 0 7px color-mix(in srgb,var(--city-accent) 45%,transparent), inset 0 0 7px color-mix(in srgb,var(--city-accent) 25%,transparent); }
.panorama-map__city-detail { position: fixed; z-index: 3100; pointer-events: none; box-sizing: border-box; padding: 16px; border: 1px solid color-mix(in srgb,var(--city-accent) 55%,#203658); border-radius: 12px; background: linear-gradient(145deg,rgba(18,38,73,.98),rgba(5,17,41,.98)); color: #eaf2ff; box-shadow: 0 18px 50px rgba(0,4,20,.55), inset 0 1px 0 rgba(210,230,255,.08); font-family: 'PingFang SC','Microsoft YaHei',sans-serif; }
.panorama-map__city-detail.has-institution-metrics { pointer-events: auto; max-height: calc(100vh - 24px); overflow-y: auto; }
.panorama-map__detail-institution-list { margin-top: 12px; max-height: 220px; overflow-y: auto; overscroll-behavior: contain; }
.panorama-map__detail-institution-list article { padding: 8px 0; border-top: 1px solid #304966; }
.panorama-map__detail-institution-list h4 { margin: 0 0 6px; font-size: 12px; color: #d4eaff; }
.panorama-map__detail-institution-list dl { margin: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 6px 10px; }
.panorama-map__detail-institution-list dt { color: #91a8ca; font-size: 10px; }
.panorama-map__detail-institution-list dd { margin: 2px 0 0; font-size: 12px; }
.panorama-map__detail-institution-list small { display: block; color: #91a8ca; font-size: 9px; margin-top: 6px; }
.panorama-map__city-detail header { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.panorama-map__city-detail header small { color: #91a8ca; font-size: 10px; letter-spacing: .08em; }
.panorama-map__city-detail h3 { margin: 3px 0 0; font-size: 19px; color: #f4f8ff; }
.panorama-map__detail-status { padding: 4px 7px; border-radius: 4px; background: rgba(116,153,207,.12); color: var(--city-accent); font-size: 10px; }
.panorama-map__detail-counts { display: flex; gap: 18px; padding: 11px 0; color: #9bb0ce; font-size: 11px; }
.panorama-map__detail-counts b { margin-left: 4px; color: #edf5ff; font-weight: 600; }
.panorama-map__detail-metrics { display: grid; grid-template-columns: 1fr 1fr; gap: 1px; overflow: hidden; border: 1px solid rgba(128,169,223,.13); border-radius: 7px; background: rgba(128,169,223,.13); }
.panorama-map__detail-metrics > div { padding: 7px 9px; background: #0c203f; min-width: 0; }
.panorama-map__detail-metrics span { display: block; color: #8fa8c9; font-size: 10px; }
.panorama-map__detail-metrics strong { display: block; color: #e6f2ff; font-size: 13px; margin-top: 3px; overflow-wrap: anywhere; }
.panorama-map__detail-metrics strong.is-empty { color: #7489a8; font-size: 11px; font-weight: 400; }
.panorama-map__detail-institutions { margin-top: 11px; font-size: 10px; color: #819bbf; }
.panorama-map__detail-institutions p { margin: 4px 0 0; color: #c5d5eb; line-height: 1.5; }
.panorama-map__city-detail footer { display: flex; justify-content: space-between; gap: 6px; margin-top: 12px; padding-top: 9px; border-top: 1px solid rgba(128,169,223,.14); color: #8ba4c7; font-size: 9px; }
.panorama-map__city-detail footer span:last-child { color: var(--city-accent); }
@keyframes city-leader-flow { to { stroke-dashoffset: -26; } }

@media (prefers-reduced-motion: reduce) {
  .panorama-map__region path { transition: none; }
  .panorama-map__callout.is-active .panorama-map__callout-line { animation: none; }
}
</style>
