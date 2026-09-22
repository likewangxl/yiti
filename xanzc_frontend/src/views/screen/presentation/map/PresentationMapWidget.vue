<template>
  <section class="presentation-map-widget" data-testid="presentation-map-widget" :data-level="mapModel.level" :data-status="mapModel.status">
    <header class="presentation-map-widget__header">
      <div>
        <span class="presentation-map-widget__kicker">地图视图</span>
        <h2>{{ mapModel.title || '地图' }}</h2>
        <p v-if="mapModel.subtitle">{{ mapModel.subtitle }}</p>
      </div>
      <div class="presentation-map-widget__meta">
        <span data-testid="presentation-map-metric">{{ mapModel.metricLabel || '指标待配置' }}</span>
        <span data-testid="presentation-map-data-date">数据日期 {{ mapModel.dataDate || '—' }}</span>
      </div>
    </header>

    <PanoramaMap
      :geo-json="geoJson"
      :points="mapModel.points"
      :selected-org-code="selectedOrgCode"
      :metric-label="mapModel.metricLabel"
      :metric-values="mapModel.metricValues"
      :metric-numeric-values="mapModel.metricRawValues"
      :metric-colors="mapModel.metricColors"
      :color-by-metric="true"
      :mode="mode"
      :selected-region-code="selectedRegionCode"
      :demo="demo"
      :view-fit="{ ...(mapModel.viewFit || {}), ...viewFit }"
      appearance="relief"
      label-layout="callout"
      class="presentation-map-widget__map"
      @region-select="onRegionSelect"
      @branch-select="onBranchSelect"
    />

    <div class="presentation-map-widget__legend" aria-label="地图图例">
      <span v-for="item in mapModel.legend" :key="item.key" :data-testid="`presentation-map-legend-${item.key}`"><i :style="{ backgroundColor: item.color }" aria-hidden="true"></i>{{ item.label }}</span>
      <small>{{ mapModel.metricLabel || '当前指标' }} · {{ mapModel.metricUnit || '单位待补充' }}</small>
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
const mapModel = computed(() => buildMapModel(props.presentation, props.model, {
  level: props.mode,
  cityCode: String(props.cityCode || ''),
  selectedOrgCode: String(props.selectedOrgCode || ''),
  metricKey: props.metricKey,
  dataDate: props.dataDate,
  geoJson: props.geoJson
}));

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
.presentation-map-widget { min-width: 0; color: #eaf2ff; background: rgba(7, 24, 62, .88); border: 1px solid rgba(106, 157, 220, .35); border-radius: 8px; }
.presentation-map-widget__header { display: flex; justify-content: space-between; gap: 12px; padding: 12px 14px; border-bottom: 1px solid rgba(106, 157, 220, .2); }
.presentation-map-widget__kicker, .presentation-map-widget__header p, .presentation-map-widget__meta { color: #9fc2df; font-size: 11px; }
.presentation-map-widget h2 { margin: 3px 0 0; font-size: 15px; }
.presentation-map-widget__header p { margin: 4px 0 0; }
.presentation-map-widget__meta { display: grid; gap: 4px; text-align: right; }
.presentation-map-widget__map { min-height: 320px; border: 0; border-radius: 0; }
.presentation-map-widget__legend { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; padding: 8px 14px; border-top: 1px solid rgba(106, 157, 220, .18); color: #bcd5ff; font-size: 11px; }
.presentation-map-widget__legend span { display: inline-flex; align-items: center; gap: 4px; }
.presentation-map-widget__legend i { width: 10px; height: 10px; border-radius: 50%; }
.presentation-map-widget__legend small { margin-left: auto; color: #9fc2df; }
.presentation-map-widget__status, .presentation-map-widget__missing { margin: 8px 14px 12px; color: #f4bd5b; font-size: 11px; }
.presentation-map-widget__missing { display: flex; flex-wrap: wrap; gap: 5px; align-items: center; }
.presentation-map-widget__missing strong { width: 100%; }
.presentation-map-widget__missing button { padding: 4px 7px; border: 1px solid rgba(127, 199, 255, .35); border-radius: 4px; color: #cfe5ff; background: rgba(25, 67, 121, .56); cursor: pointer; font: inherit; }
.presentation-map-widget__missing button:focus-visible { outline: 2px solid #42e7ee; outline-offset: 2px; }
</style>
