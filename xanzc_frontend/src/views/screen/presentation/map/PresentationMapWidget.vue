<template>
  <section class="presentation-map-widget" data-testid="presentation-map-widget" :data-level="mapModel.level" :data-status="mapModel.status">
    <header class="presentation-map-widget__header">
      <div>
        <span class="presentation-map-widget__kicker">地图视图</span>
        <h2>{{ screenDisplayText(mapModel.title) || '地图' }}</h2>
        <p v-if="mapModel.subtitle">{{ screenDisplayText(mapModel.subtitle) }}</p>
      </div>
      <div class="presentation-map-widget__meta">
        <span data-testid="presentation-map-metric">{{ displayMetricLabel || '指标待配置' }}</span>
        <span data-testid="presentation-map-data-date">数据日期 {{ mapModel.dataDate || '—' }}</span>
      </div>
    </header>

    <PanoramaMap
      :geo-json="geoJson"
      :points="mapModel.points"
      :selected-org-code="selectedOrgCode"
      :metric-label="displayMetricLabel"
      :metric-values="mapModel.metricValues"
      :metric-numeric-values="mapModel.metricRawValues"
      :metric-colors="mapModel.metricColors"
      :region-states="mapModel.regionStates"
      :show-region-metrics="!enhancedMap"
      :color-by-metric="true"
      :mode="mode"
      :show-province-points="mode === 'province'"
      :show-province-point-labels="!provinceInlineMap"
      :selected-region-code="selectedRegionCode"
      :demo="demo"
      :view-fit="{ ...(mapModel.viewFit || {}), ...viewFit }"
      appearance="relief"
      :label-layout="provinceInlineMap ? 'inline' : 'callout'"
      :point-label-layout="enhancedMap && mode === 'city' ? 'callout' : 'inline'"
      class="presentation-map-widget__map"
      @region-select="onRegionSelect"
      @branch-select="onBranchSelect"
    />

    <div class="presentation-map-widget__legend" aria-label="地图图例">
      <span v-for="item in mapModel.legend" :key="item.key" :data-testid="`presentation-map-legend-${item.key}`"><i :style="{ backgroundColor: item.color }" aria-hidden="true"></i>{{ item.label }}</span>
      <small>{{ displayMetricLabel || '当前指标' }} · {{ mapModel.metricUnit || '单位待补充' }}</small>
    </div>

    <p v-if="mapModel.noVisibleInstitutions" class="presentation-map-widget__status" data-testid="map-no-visible" role="status">当前城市暂无可见机构</p>
    <aside v-if="mapModel.missingCoordinates.length" class="presentation-map-widget__missing" data-testid="map-missing-coordinates" aria-label="缺少坐标但仍可访问的机构">
      <strong>待定位机构 {{ mapModel.missingCoordinates.length }} 家</strong>
      <button v-for="item in mapModel.missingCoordinates" :key="item.orgCode" type="button" :data-org-code="item.orgCode" @click="selectInstitution(item)">{{ item.orgName || item.name || item.orgCode }}</button>
    </aside>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import PanoramaMap from '../../panorama/PanoramaMap.vue';
import {
  buildMapModel,
  mapContextForCity,
  mapContextForInstitution
} from './mapModel';
import { screenDisplayText } from '../model/screenDisplayText';
import { isBranchMapV2 } from '../../panorama/screenVariant.js';

const props = defineProps({
  presentation: { type: Object, default: () => ({}) },
  model: { type: Object, default: () => ({}) },
  geoJson: { type: Object, default: () => ({ type: 'FeatureCollection', features: [] }) },
  mode: { type: String, default: 'province' },
  cityCode: { type: [String, Number], default: '' },
  cityName: { type: String, default: '' },
  selectedRegionCode: { type: [String, Number], default: '' },
  selectedOrgCode: { type: [String, Number], default: '' },
  metricKey: { type: String, default: '' },
  dataDate: { type: String, default: '' },
  demo: { type: Boolean, default: false },
  viewFit: { type: Object, default: () => ({}) }
});

const emit = defineEmits(['region-select', 'branch-select', 'map-context']);
const enhancedMap = computed(() => isBranchMapV2(props.presentation));
const legacyBranchProvinceMap = computed(() => {
  const screenCode = String(props.presentation?.screenCode ?? props.presentation?.screen_code ?? '').trim();
  const template = String(props.presentation?.template ?? '').trim();
  return props.mode === 'province' && (screenCode === 'SCR_PROVINCE' || template === 'branch-overview-v1');
});
const provinceInlineMap = computed(() => enhancedMap.value || legacyBranchProvinceMap.value);
const mapModel = computed(() => buildMapModel(props.presentation, props.model, {
  level: props.mode,
  cityCode: String(props.cityCode || ''),
  selectedOrgCode: String(props.selectedOrgCode || ''),
  metricKey: props.metricKey,
  dataDate: props.dataDate,
  geoJson: props.geoJson,
  distinguishNoInstitution: enhancedMap.value
}));
const displayMetricLabel = computed(() => screenDisplayText(mapModel.value.metricLabel));

function contextMeta() {
  return { metricKey: mapModel.value.metricKey, dataDate: mapModel.value.dataDate };
}

function onRegionSelect(region) {
  const payload = { code: String(region?.code || ''), name: String(region?.name || '') };
  if (!payload.code) return;
  emit('region-select', payload);
  emit('map-context', mapContextForCity(payload, contextMeta()));
}

function onBranchSelect(orgCode) {
  const code = String(orgCode || '');
  const institution = mapModel.value.institutions.find(item => String(item?.orgCode || '') === code);
  if (!institution) return;
  emit('branch-select', code);
  emit('map-context', mapContextForInstitution(institution, contextMeta()));
}

function selectInstitution(institution) {
  onBranchSelect(institution?.orgCode);
}
</script>

<style scoped>
.presentation-map-widget { display: flex; min-width: 0; min-height: 390px; height: 100%; flex-direction: column; color: var(--panorama-text, #eaf2ff); background: var(--panorama-panel-deep, rgba(4, 14, 39, .9)); border: 1px solid var(--panorama-border-strong, rgba(96, 214, 255, .54)); border-radius: 8px; box-shadow: inset 0 1px 0 rgba(201, 231, 255, .06), 0 10px 28px rgba(0, 0, 0, .15); }
.presentation-map-widget__header { display: flex; min-height: 47px; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 14px; border-bottom: 1px solid var(--panorama-border-soft, rgba(119, 163, 255, .16)); }
.presentation-map-widget__kicker, .presentation-map-widget__header p, .presentation-map-widget__meta { color: var(--panorama-text-dim, #8fa9db); font-size: 10px; }
.presentation-map-widget h2 { margin: 3px 0 0; color: var(--panorama-text, #eaf2ff); font-size: 16px; font-weight: 650; }
.presentation-map-widget h2::before { display: inline-block; width: 3px; height: 16px; margin-right: 8px; border-radius: 1px; background: var(--panorama-cyan, #4de8ef); vertical-align: -2px; content: ''; }
.presentation-map-widget__header p { margin: 4px 0 0 11px; }
.presentation-map-widget__meta { display: grid; gap: 4px; text-align: right; }
.presentation-map-widget__map { width: 100%; min-height: 0; border: 0; border-radius: 0; flex: 1 1 auto; }
.presentation-map-widget__legend { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; min-height: 34px; padding: 7px 14px; border-top: 1px solid var(--panorama-border-soft, rgba(119, 163, 255, .16)); color: #bcd5ff; font-size: 10px; }
.presentation-map-widget__legend span { display: inline-flex; align-items: center; gap: 4px; }
.presentation-map-widget__legend i { width: 10px; height: 10px; border-radius: 50%; }
.presentation-map-widget__legend small { margin-left: auto; color: var(--panorama-text-dim, #8fa9db); }
.presentation-map-widget__status, .presentation-map-widget__missing { margin: 8px 14px 12px; color: var(--panorama-amber, #ffc45e); font-size: 10px; }
.presentation-map-widget__missing { display: flex; flex-wrap: wrap; gap: 5px; align-items: center; }
.presentation-map-widget__missing strong { width: 100%; }
.presentation-map-widget__missing button { padding: 4px 7px; border: 1px solid rgba(127, 199, 255, .35); border-radius: 4px; color: #cfe5ff; background: rgba(25, 67, 121, .56); cursor: pointer; font: inherit; }
.presentation-map-widget__missing button:focus-visible { outline: 2px solid var(--panorama-cyan, #4de8ef); outline-offset: 2px; }
@media (max-width: 1180px) {
  .presentation-map-widget { min-height: 350px; }
}
@media (max-width: 620px) {
  .presentation-map-widget { min-height: 300px; }
  .presentation-map-widget__header { padding-right: 10px; padding-left: 10px; }
  .presentation-map-widget__header h2 { font-size: 14px; }
  .presentation-map-widget__legend { padding-right: 10px; padding-left: 10px; }
}
</style>
