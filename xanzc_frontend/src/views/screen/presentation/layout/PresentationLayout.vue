<template>
  <section
    class="presentation-layout"
    data-testid="presentation-layout"
    data-schema-version="1"
    aria-label="配置化大屏"
  >
    <div
      v-for="component in components"
      :key="component.componentId"
      class="presentation-layout__component"
      data-testid="presentation-layout-component"
      :data-component-id="component.componentId"
      :data-component-type="component.componentType"
      :data-layout-region="component.layoutRegion"
      :data-order="component.order"
    >
      <MetricDisplayWidgets
        v-if="['METRIC_CARD', 'COMPLETION'].includes(component.componentType)"
        :components="metricComponents(component)"
      />
      <SeriesTableWidgets
        v-else-if="['TREND', 'DETAIL_TABLE'].includes(component.componentType)"
        :components="seriesComponents(component)"
      />
      <CompositionTabsWidget
        v-else-if="component.componentType === 'COMPOSITION_TABS'"
        :model="compositionComponentModel(component)"
        @business-line-select="onBusinessLineSelect"
      />
      <InstitutionRankingWidget
        v-else-if="component.componentType === 'RANKING'"
        :model="rankingComponentModel(component)"
        :title="componentTitle(component)"
        @metric-change="onMetricChange"
      />
      <PresentationMapWidget
        v-else-if="component.componentType === 'MAP'"
        :presentation="mapPresentation(component)"
        :model="model"
        :geo-json="geoJson"
        :mode="mode"
        :metric-key="metricKey"
        :selected-region-code="selectedRegionCode"
        :selected-org-code="selectedOrgCode"
        :data-date="dataDate"
        :demo="demo"
        @region-select="onRegionSelect"
        @branch-select="onBranchSelect"
        @map-context="onMapContext"
      />
    </div>
    <p v-if="!components.length" class="presentation-layout__empty" data-testid="presentation-layout-empty" role="status">
      暂无已配置的展示组件
    </p>
  </section>
</template>

<script setup>
import { computed } from 'vue';

import MetricDisplayWidgets from '../widgets/MetricDisplayWidgets.vue';
import SeriesTableWidgets from '../widgets/SeriesTableWidgets.vue';
import CompositionTabsWidget from '../widgets/CompositionTabsWidget.vue';
import InstitutionRankingWidget from '../widgets/InstitutionRankingWidget.vue';
import PresentationMapWidget from '../map/PresentationMapWidget.vue';
import { buildDisplayMetricsModel } from '../model/displayMetricsModel';
import { buildDisplaySeriesTableModel } from '../model/displaySeriesTableModel';
import { buildCompositionTabsModel } from '../model/compositionTabsModel';
import { buildInstitutionRankingModel } from '../model/institutionRankingModel';
import {
  componentTitle,
  getDisplayComponents,
  presentationForComponent,
  presentationOf
} from './presentationLayoutModel';

const props = defineProps({
  presentation: { type: Object, default: () => ({}) },
  model: { type: Object, default: () => ({}) },
  geoJson: { type: Object, default: () => ({ type: 'FeatureCollection', features: [] }) },
  mode: { type: String, default: 'province' },
  metricKey: { type: String, default: '' },
  selectedRegionCode: { type: [String, Number], default: '' },
  selectedOrgCode: { type: [String, Number], default: '' },
  dataDate: { type: String, default: '' },
  demo: { type: Boolean, default: false }
});

const emit = defineEmits([
  'region-select',
  'branch-select',
  'map-context',
  'metric-change',
  'business-line-select'
]);

const resolvedPresentation = computed(() => presentationOf(props.presentation));
const components = computed(() => getDisplayComponents(props.presentation));

const metricsModel = computed(() => buildDisplayMetricsModel(resolvedPresentation.value, props.model));
const seriesModel = computed(() => buildDisplaySeriesTableModel(resolvedPresentation.value, props.model));
const compositionModel = computed(() => buildCompositionTabsModel(resolvedPresentation.value, props.model));

const metricById = computed(() => new Map(metricsModel.value.components.map(item => [item.componentId, item])));
const seriesById = computed(() => new Map(seriesModel.value.components.map(item => [item.componentId, item])));
const compositionById = computed(() => new Map(compositionModel.value.components.map(item => [item.componentId, item])));

function metricComponents(component) {
  const item = metricById.value.get(component.componentId);
  return item ? [item] : [];
}

function seriesComponents(component) {
  const item = seriesById.value.get(component.componentId);
  return item ? [item] : [];
}

function compositionComponentModel(component) {
  const item = compositionById.value.get(component.componentId);
  if (!item) return { enabled: true, components: [], tabs: [] };
  return {
    ...compositionModel.value,
    components: [item],
    tabs: item.tabs || [],
    activeTabKey: item.tabs?.[0]?.tabKey || ''
  };
}

function rankingComponentModel(component) {
  const institutions = Array.isArray(props.model?.institutions)
    ? props.model.institutions
    : Array.isArray(props.model?.authorizedDirectory) ? props.model.authorizedDirectory : [];
  const rows = Array.isArray(props.model?.rankings)
    ? props.model.rankings
    : Array.isArray(props.model?.rankingRows) ? props.model.rankingRows : [];
  return buildInstitutionRankingModel({
    institutions,
    sourceAuthorized: true,
    rows,
    rankingMetrics: Array.isArray(component.content?.rankingMetrics) ? component.content.rankingMetrics : [],
    activeMetricKey: props.metricKey
  });
}

function mapPresentation(component) {
  return presentationForComponent(resolvedPresentation.value, component);
}

function onMetricChange(payload) {
  emit('metric-change', payload);
}

function onBusinessLineSelect(payload) {
  emit('business-line-select', payload);
}

function onRegionSelect(payload) {
  emit('region-select', payload);
}

function onBranchSelect(payload) {
  emit('branch-select', payload);
}

function onMapContext(payload) {
  emit('map-context', payload);
}
</script>

<style scoped>
.presentation-layout { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: 12px; margin: 12px 0; min-width: 0; }
.presentation-layout__component { grid-column: span 12; min-width: 0; }
.presentation-layout__component[data-layout-region="HEADER"] { grid-column: span 12; }
.presentation-layout__component[data-layout-region="LEFT"],
.presentation-layout__component[data-layout-region="CENTER"],
.presentation-layout__component[data-layout-region="RIGHT"] { grid-column: span 4; }
.presentation-layout__component[data-layout-region="BOTTOM"],
.presentation-layout__component[data-layout-region="OVERLAY"] { grid-column: span 12; }
.presentation-layout__empty { grid-column: 1 / -1; margin: 0; padding: 28px; border: 1px dashed rgba(106,157,220,.35); border-radius: 8px; color: #9fc2df; text-align: center; }
@media (max-width: 900px) {
  .presentation-layout__component[data-layout-region="LEFT"],
  .presentation-layout__component[data-layout-region="CENTER"],
  .presentation-layout__component[data-layout-region="RIGHT"] { grid-column: span 6; }
}
@media (max-width: 620px) {
  .presentation-layout__component[data-layout-region="LEFT"],
  .presentation-layout__component[data-layout-region="CENTER"],
  .presentation-layout__component[data-layout-region="RIGHT"] { grid-column: 1 / -1; }
}
</style>
