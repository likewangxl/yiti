<template>
  <section
    ref="containerRef"
    class="panorama-map"
    :data-mode="mode"
    :data-appearance="appearance"
    :data-selected-region="selectedRegionCode || ''"
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
          :class="{ 'is-selected': region.code && String(region.code) === String(selectedRegionCode) }"
        >
          <path
            :d="region.path"
            fill-rule="evenodd"
            :data-region-code="region.code"
            :data-region-name="region.name"
            role="button"
            tabindex="0"
            :aria-label="`选择${region.name || '行政区'}`"
            @click.stop="selectRegion(region)"
            @keydown.enter.stop="selectRegion(region)"
            @keydown.space.prevent.stop="selectRegion(region)"
          />
          <circle
            v-if="mode === 'province' && regionLabels.some(item => item.key === region.key)"
            :cx="region.label.x"
            :cy="region.label.y"
            r="1.55"
            class="panorama-map__city-halo-svg"
            :class="{ 'is-violet': regionLabels.findIndex(item => item.key === region.key) % 2 === 1 }"
            aria-hidden="true"
          />
        </g>
        <g v-for="region in regionLabels" :key="`${region.key}:label`" class="panorama-map__region-label">
          <text :x="region.label.x" :y="region.label.y" role="button" tabindex="0" @click.stop="selectRegion(region)" @keydown.enter.stop="selectRegion(region)">{{ region.name }}</text>
        </g>
      </svg>
    </div>
    <div v-if="!fallbackActive" class="panorama-map__region-label-layer" aria-label="可选择城市标签">
      <button
        v-for="region in regionLabels"
        :key="`${region.key}:overlay-label`"
        type="button"
        class="panorama-map__region-label-hit"
        :class="{ 'is-selected': region.code && String(region.code) === String(selectedRegionCode) }"
        :style="regionLabelStyle(region)"
        :aria-label="`选择${region.name}`"
        @click.stop="selectRegion(region)"
      >{{ region.name }}</button>
    </div>
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

    <div v-if="mode === 'city'" class="panorama-map__point-layer" aria-label="可定位机构">
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
        @click.stop="point.isCluster ? zoomToCluster(point) : selectPoint(point)"
      >
        <span v-if="point.isCluster" class="panorama-map__cluster-count">{{ point.count }}</span>
        <span v-else class="panorama-map__point-dot" aria-hidden="true"></span>
        <span v-if="showPointLabels && !point.isCluster" class="panorama-map__point-label">{{ point.orgName || point.orgCode }}</span>
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
      </button>
    </div>

    <div class="panorama-map__controls" role="group" aria-label="地图视图控制">
      <button type="button" aria-label="放大地图" title="放大地图" @click="zoomBy(1.35)"><Plus aria-hidden="true" /></button>
      <button type="button" aria-label="缩小地图" title="缩小地图" @click="zoomBy(1 / 1.35)"><Minus aria-hidden="true" /></button>
      <button type="button" aria-label="重置地图视图" title="重置地图视图" @click="resetView"><Aim aria-hidden="true" /></button>
    </div>

    <p v-if="fallbackActive" class="panorama-map__fallback-status" role="status" aria-live="polite">
      {{ mode === 'province'
        ? '三维地图暂不可用，已切换为二维地图；可缩放、点击城市查看机构。'
        : '三维地图暂不可用，已切换为二维地图；可缩放、点击网点查看详情。' }}
    </p>
    <p v-else-if="webglReady" class="panorama-map__fallback-status" role="status" aria-live="polite">
      {{ mode === 'province'
        ? '三维真实行政区地图，坐标系 GCJ-02；点击城市标签进入市级机构地图，光环为行政中心装饰。'
        : '三维真实行政区地图，坐标系 GCJ-02。' }}
    </p>

    <p v-if="mode === 'city' && unmappedPoints.length" class="panorama-map__unmapped" aria-label="未绘制机构状态">
      <strong>未绘制 {{ unmappedPoints.length }} 个机构</strong>
      <span>缺少有效 GCJ-02 坐标或坐标系未确认</span>
    </p>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as THREE from 'three';
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
  getReliefSurfaceZ,
  isReliefAppearance
} from './mapReliefGeometry';

const props = defineProps({
  geoJson: { type: Object, default: () => ({ type: 'FeatureCollection', features: [] }) },
  points: { type: Array, default: () => [] },
  selectedOrgCode: { type: [String, Number], default: null },
  mode: { type: String, default: 'province' },
  selectedRegionCode: { type: [String, Number], default: null },
  demo: { type: Boolean, default: false },
  appearance: { type: String, default: 'classic' }
});

const emit = defineEmits(['region-select', 'branch-select']);

const containerRef = ref(null);
const canvasRef = ref(null);
const fallbackActive = ref(false);
const webglReady = ref(false);
const renderedRegionCount = ref(0);
const zoom = ref(1);
const activeCluster = ref(null);
const viewCenter = ref({ x: 0, y: 0 });

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
const reliefEnabled = computed(() => appearance.value === 'relief');
const reliefConfig = computed(() => createReliefGeometryConfig({
  worldWidth: projection.value.width,
  worldHeight: projection.value.height
}));
const renderablePoints = computed(() => filterRenderablePoints(props.points, { demo: props.demo }));
const unmappedPoints = computed(() => (Array.isArray(props.points) ? props.points : [])
  .filter(point => !renderablePoints.value.includes(point)));
const drawablePoints = computed(() => props.mode === 'city' ? renderablePoints.value : []);

const pointClusters = computed(() => clusterPoints(drawablePoints.value, {
  projection: projection.value,
  threshold: 34 / zoom.value,
  width: 1000,
  height: 720,
  demo: props.demo
}));

const showPointLabels = computed(() => pointClusters.value.length <= 8);

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
    const labelWidth = Math.max(4.5, String(region.name).length * 2.4);
    const labelHeight = 5;
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
    return { ...region, labelWorld: selected, label: selectedScreen };
  });
});

function regionLabelWorldPoint(region) {
  return region.labelWorld || baseRegionLabelWorldPoint(region);
}

function mapSurfaceZ() {
  return reliefEnabled.value ? getReliefSurfaceZ(reliefConfig.value) : 0.38;
}

function webglOverlayPoint(worldPoint, z = mapSurfaceZ()) {
  if (webglReady.value && camera && mapGroup) {
    mapGroup.updateMatrixWorld(true);
    const point = new THREE.Vector3(worldPoint.x, worldPoint.y, z).applyMatrix4(mapGroup.matrixWorld);
    point.project(camera);
    return { x: (point.x + 1) * 50, y: (1 - point.y) * 50 };
  }
  return svgPoint(worldPoint);
}

function regionLabelStyle(region) {
  const point = webglOverlayPoint(regionLabelWorldPoint(region));
  return { left: `${point.x}%`, top: `${point.y}%` };
}

function cityHaloStyle(region) {
  const point = webglOverlayPoint(regionLabelWorldPoint(region), reliefEnabled.value
    ? reliefConfig.value.depth + reliefConfig.value.contourLift
    : 0.34);
  return { left: `${point.x}%`, top: `${point.y}%` };
}

function pointStyle(point) {
  const screen = webglOverlayPoint(point, reliefEnabled.value
    ? reliefConfig.value.depth + reliefConfig.value.contourLift + 0.02
    : 0.42);
  return { left: `${screen.x}%`, top: `${screen.y}%` };
}

function selectRegion(region) {
  emit('region-select', { code: String(region.code || ''), name: region.name || '' });
}

function selectPoint(point) {
  if (!point?.orgCode) return;
  activeCluster.value = null;
  emit('branch-select', point.orgCode);
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
  if (zoom.value >= 4 - 0.001) {
    activeCluster.value = cluster;
    return;
  }
  focusOnPoint(cluster);
  zoomBy(1.6);
}

function closeClusterPicker() {
  activeCluster.value = null;
}

function applyCameraZoom() {
  if (!camera) return;
  camera.zoom = zoom.value;
  camera.updateProjectionMatrix();
  updateCameraPose();
}

function zoomBy(factor) {
  zoom.value = Math.min(4, Math.max(0.65, zoom.value * Number(factor || 1)));
  updatePointMarkerScale();
  applyCameraZoom();
  renderFrame();
}

function resetView() {
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
  const yOffset = reliefEnabled.value ? reliefConfig.value.cameraOffsetY : -6;
  const zOffset = reliefEnabled.value ? reliefConfig.value.cameraOffsetZ : 14;
  camera.position.set(cameraTarget.x, cameraTarget.y + yOffset, cameraTarget.z + zOffset);
  camera.lookAt(cameraTarget);
}

function focusOnPoint(point) {
  if (!point) return;
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
    if (item.map?.dispose) item.map.dispose();
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

function addReliefContour(ring, material, z, userData) {
  if (!Array.isArray(ring) || ring.length < 2) return;
  const points = ring.map(point => new THREE.Vector3(point.x, point.y, z));
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

function buildThreeMapUnsafe() {
  if (!scene) {
    renderedRegionCount.value = 0;
    return;
  }
  clearMapGroup();
  const polygons = projectGeoJson(props.geoJson, projection.value);
  renderedRegionCount.value = polygons.length;
  const relief = reliefEnabled.value;
  const config = reliefConfig.value;
  const topMaterial = new THREE.MeshPhongMaterial({
    color: relief ? 0x125ccc : 0x304ea4,
    emissive: relief ? 0x09235e : 0x101a58,
    emissiveIntensity: relief ? 0.24 : 0.65,
    shininess: relief ? 65 : 70,
    specular: relief ? 0x579bdf : 0x111111,
    transparent: true,
    opacity: relief ? 0.98 : 0.93
  });
  const selectedTopMaterial = new THREE.MeshPhongMaterial({
    color: relief ? 0x536bff : 0x6f43ba,
    emissive: relief ? 0x17136a : 0x31135f,
    emissiveIntensity: relief ? 0.72 : 0.88,
    shininess: relief ? 100 : 85,
    transparent: true,
    opacity: relief ? 0.99 : 0.96
  });
  const sideMaterial = new THREE.MeshPhongMaterial({
    color: relief ? 0x0a255f : (props.mode === 'province' ? 0x1d3479 : 0x152763),
    emissive: relief ? 0x061638 : (props.mode === 'province' ? 0x10245d : 0x090f35),
    emissiveIntensity: relief ? 0.42 : (props.mode === 'province' ? 0.58 : 0.35),
    shininess: relief ? 36 : (props.mode === 'province' ? 36 : 25),
    transparent: true,
    opacity: relief ? 0.98 : (props.mode === 'province' ? 0.96 : 0.92)
  });
  if (relief) {
    const baseTopMaterial = new THREE.MeshPhongMaterial({
      color: 0x0e347b,
      emissive: 0x050d2e,
      emissiveIntensity: 0.32,
      shininess: 25,
      transparent: true,
      opacity: 0.96
    });
    const baseSideMaterial = new THREE.MeshPhongMaterial({
      color: 0x07183f,
      emissive: 0x030b24,
      emissiveIntensity: 0.22,
      shininess: 18,
      transparent: true,
      opacity: 0.94
    });
    const topContourMaterial = new THREE.LineBasicMaterial({
      color: 0x6cefff,
      transparent: true,
      opacity: 0.98
    });
    const selectedContourMaterial = new THREE.LineBasicMaterial({
      color: 0xe0b6ff,
      transparent: true,
      opacity: 0.99
    });
    const bottomContourMaterial = new THREE.LineBasicMaterial({
      color: 0xb86dff,
      transparent: true,
      opacity: 0.88
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
        -config.baseDepth - config.shadowGap,
        userData,
        config.shadowSpread * 1.35
      );
      addReliefGroundShadow(
        polygon,
        shadowGlowMaterial,
        -config.baseDepth - config.shadowGap * 0.58,
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
      const mesh = new THREE.Mesh(geometry, [selected ? selectedTopMaterial : topMaterial, sideMaterial]);
      mesh.userData = userData;
      mapGroup.add(mesh);
      const topMaterialForPolygon = selected ? selectedContourMaterial : topContourMaterial;
      addReliefContour(polygon.outer, topMaterialForPolygon, config.depth + config.contourLift, userData);
      polygon.holes.forEach(ring => addReliefContour(ring, topMaterialForPolygon, config.depth + config.contourLift, userData));
      addReliefContour(polygon.outer, bottomContourMaterial, -config.baseDepth - config.contourLift, userData);
      polygon.holes.forEach(ring => addReliefContour(ring, bottomContourMaterial, -config.baseDepth - config.contourLift, userData));
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
      const mesh = new THREE.Mesh(geometry, [selected ? selectedTopMaterial : topMaterial, sideMaterial]);
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
  if (!renderer || !camera || !containerRef.value) return;
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
      const fit = fitReliefView(vertices, { aspect, fitHeight: reliefConfig.value.fitHeight });
      if (fit) Object.assign(camera, fit);
    } else {
      const map = worldBounds();
      // Preserve the classic camera fit for retail and other existing callers.
      const fitPadding = props.mode === 'province' ? 1.02 : 1.0;
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
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.setClearColor(0x07163d, 0);
    scene = new THREE.Scene();
    camera = new THREE.OrthographicCamera(-5, 5, 5, -5, 0.1, 100);
    cameraTarget = new THREE.Vector3(0, 0, 0);
    updateCameraPose();
    raycaster = new THREE.Raycaster();
    pointer = new THREE.Vector2();
    scene.add(new THREE.AmbientLight(0xaac7ff, reliefEnabled.value ? 0.8 : 1.65));
    const light = new THREE.DirectionalLight(0x9ec7ff, reliefEnabled.value ? 1.6 : 2.3);
    light.position.set(-3, -4, 10);
    scene.add(light);
    if (reliefEnabled.value) {
      // A local light adds material depth without inventing terrain or data shading.
      const surfaceLight = new THREE.PointLight(0x75bdff, 65, 30, 2);
      surfaceLight.position.set(-3, 3, 6);
      scene.add(surfaceLight);
    }
    contextLostHandler = onWebGLContextLost;
    renderer.domElement.addEventListener('webglcontextlost', contextLostHandler, false);
    buildThreeMap();
    if (fallbackActive.value || !renderer) return;
    pointerHandler = onThreePointer;
    renderer.domElement.addEventListener('pointerup', pointerHandler);
    resizeRenderer();
    if (fallbackActive.value || !renderer) return;
    webglReady.value = true;
  } catch (error) {
    // 创建上下文失败是普通低配浏览器情况，保留 SVG 真实几何回退并释放半成品。
    activateFallback('setup', error);
  }
}

function disposeThree() {
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
  setupThree();
  if (typeof ResizeObserver !== 'undefined' && containerRef.value) {
    resizeObserver = new ResizeObserver(resizeRenderer);
    resizeObserver.observe(containerRef.value);
  } else if (typeof window !== 'undefined') {
    window.addEventListener('resize', resizeRenderer);
  }
});

watch(() => [props.geoJson, props.points, props.selectedOrgCode, props.selectedRegionCode, props.mode, props.demo, props.appearance], () => {
  activeCluster.value = null;
  rebuildThreeMap();
}, { deep: true });

onBeforeUnmount(() => {
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
.panorama-map__region.is-selected path { fill: rgba(131, 84, 217, .9); stroke: #f3c8ff; outline: none; }
.panorama-map__region-label text { fill: rgba(227, 239, 255, .88); font-size: 2.1px; text-anchor: middle; pointer-events: auto; cursor: pointer; }
.panorama-map__city-halo-svg { fill: rgba(75, 233, 255, .78); stroke: rgba(151, 249, 255, .98); stroke-width: .28; pointer-events: none; filter: drop-shadow(0 0 1.1px rgba(73, 235, 255, .95)); }
.panorama-map__city-halo-svg.is-violet { fill: rgba(188, 116, 255, .78); stroke: rgba(237, 190, 255, .98); filter: drop-shadow(0 0 1.1px rgba(205, 130, 255, .95)); }
.panorama-map__city-halo-layer { position: absolute; z-index: 2; inset: 0; pointer-events: none; }
.panorama-map__city-halo { position: absolute; width: 18px; height: 18px; transform: translate(-50%, -50%); border: 1px solid rgba(132, 247, 255, .94); border-radius: 50%; background: radial-gradient(circle, rgba(112, 249, 255, .94) 0 2px, rgba(50, 211, 237, .34) 3px 5px, rgba(50, 211, 237, 0) 72%); box-shadow: 0 0 7px rgba(76, 233, 255, .88), inset 0 0 7px rgba(104, 242, 255, .66); color: #68efff; opacity: .9; }
.panorama-map__city-halo::after { content: ''; position: absolute; inset: -4px; border: 1px solid currentColor; border-radius: 50%; opacity: .38; }
.panorama-map__city-halo.is-violet { border-color: rgba(226, 173, 255, .98); background: radial-gradient(circle, rgba(231, 176, 255, .96) 0 2px, rgba(172, 96, 247, .4) 3px 5px, rgba(172, 96, 247, 0) 72%); box-shadow: 0 0 7px rgba(197, 119, 255, .92), inset 0 0 7px rgba(214, 143, 255, .68); color: #d28cff; }
.panorama-map__region-label-layer { position: absolute; z-index: 4; inset: 0; pointer-events: none; }
.panorama-map__region-label-hit { position: absolute; transform: translate(-50%, -50%); padding: 1px 3px; border: 1px solid transparent; border-radius: 3px; color: rgba(227, 239, 255, .82); background: transparent; text-shadow: 0 1px 3px #05133b, 0 0 5px #05133b; font-size: 11px; white-space: nowrap; cursor: pointer; pointer-events: auto; }
.panorama-map__region-label-hit:hover, .panorama-map__region-label-hit:focus-visible, .panorama-map__region-label-hit.is-selected { border-color: #f0caff; color: #fff1ff; background: rgba(101, 61, 175, .88); outline: 2px solid rgba(210, 160, 255, .32); }
.panorama-map[data-appearance='relief'] .panorama-map__region-label-hit { color: rgba(237, 247, 255, .94); font-weight: 700; text-shadow: 0 1px 4px #03133d, 0 0 8px #03133d; }

.panorama-map__point-layer { position: absolute; z-index: 3; inset: 0; pointer-events: none; }
.panorama-map__point-hit { position: absolute; transform: translate(-50%, -50%); min-width: 22px; min-height: 22px; padding: 0; border: 0; border-radius: 999px; color: #fff; background: transparent; cursor: pointer; pointer-events: auto; }
.panorama-map__point-dot { display: block; width: 10px; height: 10px; margin: auto; border: 2px solid #83fbff; border-radius: 50%; background: #37dce1; box-shadow: 0 0 8px #37dce1, 0 0 22px rgba(55, 220, 225, .8); }
.panorama-map__point-hit.is-selected .panorama-map__point-dot { width: 13px; height: 13px; border-color: #f4d5ff; background: #d783ff; box-shadow: 0 0 9px #d783ff, 0 0 28px rgba(215, 131, 255, .95); }
.panorama-map__point-label { display: block; position: absolute; top: 18px; left: 50%; transform: translateX(-50%); white-space: nowrap; padding: 2px 6px; border: 1px solid rgba(112, 192, 255, .5); border-radius: 4px; background: rgba(10, 27, 73, .9); font-size: 11px; }
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
.panorama-map__fallback-status { position: absolute; z-index: 4; left: 14px; bottom: 12px; max-width: calc(100% - 100px); margin: 0; color: rgba(194, 221, 255, .78); font-size: 11px; pointer-events: none; }
.panorama-map__empty { position: absolute; z-index: 4; inset: 50% auto auto 50%; transform: translate(-50%, -50%); margin: 0; color: rgba(194, 221, 255, .82); font-size: 13px; white-space: nowrap; }
.panorama-map__unmapped { position: absolute; z-index: 6; bottom: 42px; left: 12px; max-width: min(245px, 50%); margin: 0; padding: 8px 10px; border: 1px solid rgba(255, 181, 79, .5); border-radius: 6px; background: rgba(28, 25, 57, .86); color: #ffe2a8; font-size: 11px; }
.panorama-map__unmapped strong, .panorama-map__unmapped span { display: block; }
.panorama-map__unmapped span { margin-top: 2px; color: rgba(255, 226, 168, .72); }
.panorama-map__unmapped ul { max-height: 80px; margin: 4px 0 0; padding-left: 15px; overflow: auto; }

@media (prefers-reduced-motion: reduce) {
  .panorama-map__region path { transition: none; }
}
</style>
