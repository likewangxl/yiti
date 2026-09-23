<template>
  <section
    class="presentation-layout"
    data-testid="presentation-layout"
    data-schema-version="1"
    aria-label="配置化大屏"
  >
    <section v-if="headerTiers.length" class="presentation-layout__header" data-layout-region="HEADER" aria-label="核心指标">
      <div
        v-for="tier in headerTiers"
        :key="tier.key"
        class="presentation-layout__metric-tier"
        :class="`presentation-layout__metric-tier--${tier.key.toLowerCase()}`"
        :data-layout-tier="tier.key"
      >
        <div
          v-for="component in tier.components"
          :key="component.componentId"
          class="presentation-layout__component"
          :class="`presentation-layout__component--${String(component.componentType || '').toLowerCase()}`"
          data-testid="presentation-layout-component"
          :data-component-id="component.componentId"
          :data-component-type="component.componentType"
          :data-layout-region="component.layoutRegion"
          :data-order="component.order"
        >
          <component
            :is="widgetComponent(component)"
            v-bind="widgetProps(component)"
            @region-select="onRegionSelect"
            @branch-select="onBranchSelect"
            @map-context="onMapContext"
            @metric-change="onMetricChange"
            @business-line-select="onBusinessLineSelect"
          />
        </div>
      </div>
    </section>

    <section v-if="mainComponents" class="presentation-layout__main" data-testid="presentation-layout-main" aria-label="经营分析主体">
      <section
        v-for="column in mainColumns"
        :key="column.key"
        class="presentation-layout__column"
        :class="`presentation-layout__column--${column.key.toLowerCase()}`"
        :data-layout-column="column.key"
        :aria-label="column.label"
      >
        <div
          v-for="component in column.components"
          :key="component.componentId"
          class="presentation-layout__component"
          :class="[
            `presentation-layout__component--${String(component.componentType || '').toLowerCase()}`,
            component.componentType === 'MAP' ? 'presentation-layout__component--map-primary' : ''
          ]"
          data-testid="presentation-layout-component"
          :data-component-id="component.componentId"
          :data-component-type="component.componentType"
          :data-layout-region="component.layoutRegion"
          :data-order="component.order"
        >
          <component
            :is="widgetComponent(component)"
            v-bind="widgetProps(component)"
            @region-select="onRegionSelect"
            @branch-select="onBranchSelect"
            @map-context="onMapContext"
            @metric-change="onMetricChange"
            @business-line-select="onBusinessLineSelect"
          />
        </div>
      </section>
    </section>

    <section
      v-for="region in footerRegions"
      :key="region.key"
      class="presentation-layout__footer"
      :class="`presentation-layout__footer--${region.key.toLowerCase()}`"
      :data-layout-region="region.key"
      :aria-label="region.label"
    >
      <div
        v-for="component in region.components"
        :key="component.componentId"
        class="presentation-layout__component"
        :class="`presentation-layout__component--${String(component.componentType || '').toLowerCase()}`"
        data-testid="presentation-layout-component"
        :data-component-id="component.componentId"
        :data-component-type="component.componentType"
        :data-layout-region="component.layoutRegion"
        :data-order="component.order"
      >
        <component
          :is="widgetComponent(component)"
          v-bind="widgetProps(component)"
          @region-select="onRegionSelect"
          @branch-select="onBranchSelect"
          @map-context="onMapContext"
          @metric-change="onMetricChange"
          @business-line-select="onBusinessLineSelect"
        />
      </div>
    </section>

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

const headerComponents = computed(() => components.value.filter(component => component.layoutRegion === 'HEADER'));
const headerTiers = computed(() => [
  // HEADER 的前四项是首屏重点卡，后续项自动落到次级卡行；不依赖组件类型，
  // 因为同一版本的保存配置允许用 METRIC_CARD 表达两种视觉层级。
  { key: 'PRIMARY', components: headerComponents.value.slice(0, 4) },
  { key: 'SECONDARY', components: headerComponents.value.slice(4) }
].filter(tier => tier.components.length));

function centerOrder(component) {
  return ({ MAP: 0, TREND: 1, COMPOSITION_TABS: 2, RANKING: 3, DETAIL_TABLE: 4 }[component.componentType] ?? 9);
}

function sortCenterComponents(items) {
  return [...items].sort((left, right) => centerOrder(left) - centerOrder(right)
    || (Number.isInteger(left.order) ? left.order : 0) - (Number.isInteger(right.order) ? right.order : 0));
}

const mainColumns = computed(() => [
  { key: 'LEFT', label: '左侧业务结构', components: components.value.filter(component => component.layoutRegion === 'LEFT') },
  { key: 'CENTER', label: '中央地图与趋势', components: sortCenterComponents(components.value.filter(component => component.layoutRegion === 'CENTER')) },
  { key: 'RIGHT', label: '右侧机构排名', components: components.value.filter(component => component.layoutRegion === 'RIGHT') }
]);
const mainComponents = computed(() => mainColumns.value.some(column => column.components.length));
const footerRegions = computed(() => [
  { key: 'BOTTOM', label: '明细数据', components: components.value.filter(component => component.layoutRegion === 'BOTTOM') },
  { key: 'OVERLAY', label: '叠加内容', components: components.value.filter(component => component.layoutRegion === 'OVERLAY') }
].filter(region => region.components.length));

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

function widgetComponent(component) {
  if (['METRIC_CARD', 'COMPLETION'].includes(component.componentType)) return MetricDisplayWidgets;
  if (['TREND', 'DETAIL_TABLE'].includes(component.componentType)) return SeriesTableWidgets;
  if (component.componentType === 'COMPOSITION_TABS') return CompositionTabsWidget;
  if (component.componentType === 'RANKING') return InstitutionRankingWidget;
  if (component.componentType === 'MAP') return PresentationMapWidget;
  return null;
}

function widgetProps(component) {
  if (['METRIC_CARD', 'COMPLETION'].includes(component.componentType)) return { components: metricComponents(component) };
  if (['TREND', 'DETAIL_TABLE'].includes(component.componentType)) return { components: seriesComponents(component) };
  if (component.componentType === 'COMPOSITION_TABS') return { model: compositionComponentModel(component) };
  if (component.componentType === 'RANKING') return { model: rankingComponentModel(component), title: componentTitle(component) };
  if (component.componentType === 'MAP') return {
    presentation: mapPresentation(component), model: props.model, geoJson: props.geoJson, mode: props.mode,
    metricKey: props.metricKey, selectedRegionCode: props.selectedRegionCode, selectedOrgCode: props.selectedOrgCode,
    dataDate: props.dataDate, demo: props.demo
  };
  return {};
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
.presentation-layout {
  --presentation-bg: #07183b;
  --presentation-bg-deep: #020916;
  --presentation-panel: rgba(8, 24, 61, .86);
  --presentation-border: rgba(119, 163, 255, .3);
  --presentation-border-soft: rgba(119, 163, 255, .16);
  --presentation-text: #eaf2ff;
  --presentation-text-dim: #8fa9db;
  --presentation-cyan: #4de8ef;
  --presentation-violet: #a979ff;
  --presentation-blue: #5896ff;
  display: flex;
  min-width: 0;
  margin: 10px clamp(16px, 2vw, 30px) 18px;
  flex-direction: column;
  gap: 10px;
  color: var(--panorama-text, var(--presentation-text));
  font-variant-numeric: tabular-nums;
}

.presentation-layout__header,
.presentation-layout__main,
.presentation-layout__footer {
  min-width: 0;
}

.presentation-layout__header {
  display: grid;
  gap: 10px;
}

.presentation-layout__metric-tier {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  min-width: 0;
}

.presentation-layout__metric-tier--secondary {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.presentation-layout__metric-tier--secondary :deep(.presentation-metric-widget) {
  min-height: 72px;
  padding-top: 10px;
  padding-bottom: 10px;
}

.presentation-layout__main {
  display: grid;
  grid-template-columns: minmax(0, .95fr) minmax(0, 1.4fr) minmax(0, .95fr);
  align-items: start;
  gap: 12px;
  min-height: 620px;
}

.presentation-layout__column {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  gap: 10px;
}

.presentation-layout__column--left,
.presentation-layout__column--right {
  align-self: start;
  height: fit-content;
}

.presentation-layout__column--left > .presentation-layout__component,
.presentation-layout__column--right > .presentation-layout__component {
  flex: 0 0 auto;
}

.presentation-layout__column--left > .presentation-layout__component:first-child {
  min-height: 220px;
  max-height: min(680px, calc(100vh - 240px));
  overflow: auto;
}

.presentation-layout__column--right > .presentation-layout__component:first-child {
  min-height: 420px;
  height: clamp(420px, calc(100vh - 292px), 820px);
}

.presentation-layout__column--center > .presentation-layout__component {
  flex: 0 1 auto;
}

.presentation-layout__column--center > .presentation-layout__component--map-primary {
  min-height: 390px;
  flex: 1 1 450px;
}

.presentation-layout__column--center > .presentation-layout__component--trend {
  min-height: 205px;
  flex: 0 1 270px;
}

.presentation-layout__component {
  display: flex;
  min-width: 0;
  min-height: 0;
}

.presentation-layout__component > * {
  width: 100%;
  min-width: 0;
}

.presentation-layout__footer {
  display: grid;
  gap: 10px;
}

.presentation-layout__footer--bottom {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  padding-top: 2px;
}

.presentation-layout__footer--bottom > .presentation-layout__component {
  min-height: 230px;
}

.presentation-layout__footer--overlay {
  position: relative;
  z-index: 2;
}

.presentation-layout__empty {
  margin: 0;
  padding: 28px;
  border: 1px dashed var(--presentation-border);
  border-radius: 8px;
  color: var(--presentation-text-dim);
  text-align: center;
}

@media (max-width: 1180px) {
  .presentation-layout__main { grid-template-columns: minmax(0, .9fr) minmax(0, 1.25fr) minmax(0, .9fr); gap: 9px; }
  .presentation-layout__metric-tier { gap: 8px; }
  .presentation-layout__column { gap: 8px; }
  .presentation-layout__column--center > .presentation-layout__component--map-primary { min-height: 350px; }
}

@media (max-width: 900px) {
  .presentation-layout__main { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .presentation-layout__column--center { grid-column: 1 / -1; grid-row: 1; }
  .presentation-layout__column--left { grid-column: 1; grid-row: 2; }
  .presentation-layout__column--right { grid-column: 2; grid-row: 2; }
  .presentation-layout__column--right > .presentation-layout__component:first-child { height: clamp(380px, calc(100vh - 240px), 680px); min-height: 380px; }
  .presentation-layout__column--center > .presentation-layout__component--map-primary { min-height: 360px; }
  .presentation-layout__footer--bottom { grid-template-columns: 1fr; }
}

@media (max-width: 620px) {
  .presentation-layout { margin-right: 12px; margin-left: 12px; }
  .presentation-layout__metric-tier,
  .presentation-layout__metric-tier--secondary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .presentation-layout__main { display: flex; min-height: 0; flex-direction: column; }
  .presentation-layout__column--center { order: 1; }
  .presentation-layout__column--left { order: 2; }
  .presentation-layout__column--right { order: 3; }
  .presentation-layout__column--right > .presentation-layout__component:first-child { height: auto; max-height: 600px; min-height: 360px; }
  .presentation-layout__column--center > .presentation-layout__component--map-primary { min-height: 300px; }
  .presentation-layout__column--center > .presentation-layout__component--trend { min-height: 180px; }
  .presentation-layout__footer--bottom > .presentation-layout__component { min-height: 210px; }
}
</style>
