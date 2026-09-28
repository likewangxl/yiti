<template>
  <section
    class="presentation-layout"
    :class="{ 'presentation-layout--branch-overview': isBranchOverview }"
    data-testid="presentation-layout"
    data-schema-version="1"
    aria-label="配置化大屏"
  >
    <section
      v-if="headerGroups.length"
      class="presentation-layout__header presentation-layout__header--grouped"
      data-layout-region="HEADER"
      data-layout-mode="grouped"
      aria-label="核心指标分组"
    >
      <section
        v-for="group in headerGroups"
        :key="group.key"
        class="presentation-layout__metric-group"
        :class="`presentation-layout__metric-group--${group.key.toLowerCase()}`"
        :data-layout-group="group.key"
        :data-group-prefix="group.prefix"
        :aria-label="group.label"
      >
        <header class="presentation-layout__metric-group-header">
          <h2>{{ group.label }}</h2>
          <button
            v-if="isBranchOverview && ['RETAIL', 'CORP'].includes(group.key)"
            type="button"
            class="presentation-layout__metric-group-more"
            data-action="business-line-more"
            :data-business-line="group.key"
            @click="onBusinessLineMore(group.key)"
          >查看更多</button>
          <small v-else>{{ group.components.length }}项</small>
        </header>
        <div class="presentation-layout__metric-group-grid" :data-layout-tier="group.key">
          <div v-if="isRevenuePair(group)" class="presentation-layout__component presentation-layout__component--revenue-share" data-testid="presentation-layout-component" data-component-id="business-revenue-share" data-component-type="REVENUE_SHARE" data-layout-region="HEADER">
            <RevenueShareWidget
              :operating="metricById.get('business-revenue-operating')"
              :intermediary="metricById.get('business-revenue-fee')"
            />
          </div>
          <div
            v-for="component in displayGroupComponents(group)"
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
    </section>

    <section v-else-if="headerTiers.length" class="presentation-layout__header" data-layout-region="HEADER" data-layout-mode="generic" aria-label="核心指标">
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

    <section
      v-if="mainComponents"
      class="presentation-layout__main"
      :class="{ 'presentation-layout__main--branch-overview': isBranchOverview }"
      data-testid="presentation-layout-main"
      aria-label="经营分析主体"
    >
      <section
        v-for="column in mainColumns"
        :key="column.key"
        class="presentation-layout__column"
        :class="[
          `presentation-layout__column--${column.key.toLowerCase()}`,
          isBranchOverview && column.key === 'LEFT' ? 'presentation-layout__column--branch-overview' : ''
        ]"
        :data-layout-column="column.key"
        :aria-label="column.label"
      >
        <template v-for="component in column.components" :key="component.componentId">
          <div
            v-if="isBranchTrendGroup(column, component)"
            class="presentation-layout__component presentation-layout__component--trend presentation-layout__component--trend-tabs"
            data-testid="presentation-layout-trend-group"
            data-component-type="TREND"
            data-layout-region="CENTER"
            :data-trend-count="branchTrendComponents.length"
          >
            <BusinessGrowthWidget
              :presentation="resolvedPresentation"
              :model="props.model"
              :amount-unit="props.amountUnit"
            />
          </div>
          <div
            v-else-if="!isBranchTrendComponent(column, component)"
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
            :data-visible-rows="isBranchOverview && component.componentType === 'RANKING' ? 10 : undefined"
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
        </template>
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
import RevenueShareWidget from '../widgets/RevenueShareWidget.vue';
import SeriesTableWidgets from '../widgets/SeriesTableWidgets.vue';
import BusinessGrowthWidget from '../widgets/BusinessGrowthWidget.vue';
import CompositionTabsWidget from '../widgets/CompositionTabsWidget.vue';
import InstitutionRankingWidget from '../widgets/InstitutionRankingWidget.vue';
import PresentationMapWidget from '../map/PresentationMapWidget.vue';
import { buildDisplayMetricsModel } from '../model/displayMetricsModel';
import { buildDisplaySeriesTableModel } from '../model/displaySeriesTableModel';
import { buildCompositionTabsModel } from '../model/compositionTabsModel';
import { buildInstitutionRankingModel } from '../model/institutionRankingModel';
import { screenDisplayText } from '../model/screenDisplayText';
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
  demo: { type: Boolean, default: false },
  amountUnit: { type: String, default: '' }
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
const isBranchOverview = computed(() => resolvedPresentation.value?.template === 'branch-overview-v1');

const headerComponents = computed(() => components.value.filter(component => component.layoutRegion === 'HEADER'));
const HEADER_GROUP_DEFINITIONS = Object.freeze([
  { key: 'RETAIL', prefix: 'business-retail-', label: '零售业务' },
  { key: 'CORP', prefix: 'business-corp-', label: '对公业务' },
  { key: 'REVENUE', prefix: 'business-revenue-', label: '营业收入' }
]);

function headerGroupFor(component) {
  const componentId = String(component?.componentId || '');
  return HEADER_GROUP_DEFINITIONS.find(group => componentId.startsWith(group.prefix)) || null;
}

const headerGroups = computed(() => {
  // Grouping is opt-in through the stable componentId prefixes. A mixed
  // configuration stays on the generic layout so an unrecognised card is
  // never silently dropped from the header.
  if (!headerComponents.value.length
    || !headerComponents.value.every(component => headerGroupFor(component)
      && ['METRIC_CARD', 'COMPLETION'].includes(component.componentType))) return [];
  return HEADER_GROUP_DEFINITIONS
    .map(group => ({ ...group, components: headerComponents.value.filter(component => headerGroupFor(component)?.key === group.key) }))
    .filter(group => group.components.length);
});

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

const branchTrendComponents = computed(() => isBranchOverview.value
  ? components.value.filter(component => component.layoutRegion === 'CENTER' && component.componentType === 'TREND')
  : []);
const mainColumns = computed(() => {
  const leftComponents = components.value.filter(component => component.layoutRegion === 'LEFT');
  const centerComponents = components.value.filter(component => component.layoutRegion === 'CENTER');
  return [
    {
      key: 'LEFT',
      label: '左侧业务结构',
      components: [...leftComponents, ...branchTrendComponents.value]
    },
    {
      key: 'CENTER',
      label: isBranchOverview.value ? '中央地图' : '中央地图与趋势',
      components: sortCenterComponents(centerComponents.filter(component => !branchTrendComponents.value.includes(component)))
    },
    { key: 'RIGHT', label: '右侧机构排名', components: components.value.filter(component => component.layoutRegion === 'RIGHT') }
  ];
});
const mainComponents = computed(() => mainColumns.value.some(column => column.components.length));
const footerRegions = computed(() => [
  { key: 'BOTTOM', label: '明细数据', components: components.value.filter(component => component.layoutRegion === 'BOTTOM') },
  { key: 'OVERLAY', label: '叠加内容', components: components.value.filter(component => component.layoutRegion === 'OVERLAY') }
].filter(region => region.components.length));

function isBranchTrendGroup(column, component) {
  return isBranchOverview.value
    && column.key === 'LEFT'
    && component.componentType === 'TREND'
    && component.componentId === branchTrendComponents.value[0]?.componentId;
}

function isBranchTrendComponent(column, component) {
  return isBranchOverview.value
    && column.key === 'LEFT'
    && component.componentType === 'TREND'
    && branchTrendComponents.value.some(item => item.componentId === component.componentId);
}

const metricsModel = computed(() => buildDisplayMetricsModel(resolvedPresentation.value, props.model, {
  amountUnit: props.amountUnit
}));
const seriesModel = computed(() => buildDisplaySeriesTableModel(resolvedPresentation.value, props.model));
const compositionModel = computed(() => buildCompositionTabsModel(resolvedPresentation.value, props.model));

const metricById = computed(() => new Map(metricsModel.value.components.map(item => [item.componentId, item])));
function isRevenuePair(group) {
  return group.key === 'REVENUE'
    && group.components.some(component => component.componentId === 'business-revenue-operating')
    && group.components.some(component => component.componentId === 'business-revenue-fee');
}
function displayGroupComponents(group) {
  if (!isRevenuePair(group)) return group.components;
  return group.components.filter(component => !['business-revenue-operating', 'business-revenue-fee'].includes(component.componentId));
}
const seriesById = computed(() => new Map(seriesModel.value.components.map(item => [item.componentId, item])));
const compositionById = computed(() => new Map(compositionModel.value.components.map(item => [item.componentId, item])));

function metricComponents(component) {
  const item = metricById.value.get(component.componentId);
  return item ? [{
    ...item,
    title: screenDisplayText(item.title),
    subtitle: screenDisplayText(item.subtitle),
    description: screenDisplayText(item.description),
    metricName: screenDisplayText(item.metricName),
    subFields: item.subFields?.map(field => ({ ...field, label: screenDisplayText(field.label) }))
  }] : [];
}

function seriesComponents(component) {
  const item = seriesById.value.get(component.componentId);
  return item ? [{
    ...item,
    title: screenDisplayText(item.title),
    subtitle: screenDisplayText(item.subtitle),
    series: item.series?.map(series => ({ ...series, label: screenDisplayText(series.label) })),
    columns: item.columns?.map(column => ({ ...column, label: screenDisplayText(column.label) }))
  }] : [];
}

function compositionComponentModel(component) {
  const item = compositionById.value.get(component.componentId);
  if (!item) return { enabled: true, components: [], tabs: [] };
  return {
    ...compositionModel.value,
    components: [item],
    tabs: item.tabs || [],
    sections: item.sections || compositionModel.value.sections || [],
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
  const result = buildInstitutionRankingModel({
    institutions,
    sourceAuthorized: true,
    rows,
    rankingMetrics: Array.isArray(component.content?.rankingMetrics) ? component.content.rankingMetrics : [],
    activeMetricKey: props.metricKey
  });
  const metrics = result.metrics.map(metric => ({ ...metric, label: screenDisplayText(metric.label) }));
  return { ...result, metrics, metric: metrics.find(metric => metric.metricKey === result.activeMetricKey) || null };
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
  if (['METRIC_CARD', 'COMPLETION'].includes(component.componentType)) return {
    components: metricComponents(component),
    grouped: headerGroups.value.length > 0 && component.layoutRegion === 'HEADER'
  };
  if (['TREND', 'DETAIL_TABLE'].includes(component.componentType)) return { components: seriesComponents(component) };
  if (component.componentType === 'COMPOSITION_TABS') return { model: compositionComponentModel(component) };
  if (component.componentType === 'RANKING') return {
    model: rankingComponentModel(component),
    title: screenDisplayText(componentTitle(component)),
    paginate: isBranchOverview.value,
    pageSize: 10,
    pageInterval: 5000,
    metricCarousel: !isBranchOverview.value
  };
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

function onBusinessLineMore(businessLine) {
  if (!['RETAIL', 'CORP'].includes(businessLine)) return;
  emit('business-line-select', { businessLine, tabKey: 'deposit' });
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

.presentation-layout__header--grouped {
  grid-template-columns: minmax(0, 2fr) minmax(0, 2fr) minmax(0, 1fr);
  align-items: stretch;
}

.presentation-layout__metric-group {
  display: flex;
  min-width: 0;
  padding: 8px;
  flex-direction: column;
  gap: 7px;
  background: rgba(7, 22, 56, .68);
  border: 1px solid var(--presentation-border-soft);
  border-radius: 9px;
}

.presentation-layout__metric-group--retail { --group-accent: var(--presentation-cyan); }
.presentation-layout__metric-group--corp { --group-accent: var(--presentation-violet); }
.presentation-layout__metric-group--revenue { --group-accent: var(--presentation-blue); }
.presentation-layout__metric-group--revenue .presentation-layout__metric-group-header { min-height: 20px; }

.presentation-layout__metric-group-header {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 2px;
}

.presentation-layout__metric-group-header::before {
  width: 3px;
  height: 16px;
  flex: 0 0 auto;
  border-radius: 2px;
  background: var(--group-accent, var(--presentation-cyan));
  box-shadow: 0 0 9px var(--group-accent, var(--presentation-cyan));
  content: '';
}

.presentation-layout__metric-group-header h2 {
  min-width: 0;
  margin: 0 auto 0 0;
  overflow: hidden;
  color: var(--presentation-text);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.presentation-layout__metric-group-header small {
  flex: 0 0 auto;
  color: var(--presentation-text-dim);
  font-size: 10px;
}

.presentation-layout__metric-group-more {
  flex: 0 0 auto;
  padding: 3px 7px;
  border: 1px solid color-mix(in srgb, var(--group-accent, var(--presentation-cyan)) 55%, transparent);
  border-radius: 4px;
  color: var(--presentation-text-dim);
  background: rgba(22, 66, 132, .28);
  font: inherit;
  font-size: 10px;
  line-height: 1.2;
  cursor: pointer;
}

.presentation-layout__metric-group-more:hover,
.presentation-layout__metric-group-more:focus-visible {
  border-color: var(--group-accent, var(--presentation-cyan));
  color: var(--presentation-cyan);
  outline: none;
}

.presentation-layout__metric-group-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.presentation-layout__metric-group--revenue .presentation-layout__metric-group-grid {
  grid-template-columns: minmax(0, 1fr);
}

.presentation-layout__metric-group-grid > .presentation-layout__component {
  min-width: 0;
  min-height: 0;
}

.presentation-layout__header--grouped :deep(.presentation-metric-widget) {
  box-sizing: border-box;
  min-height: 74px;
  padding: 10px 12px;
}

.presentation-layout__header--grouped :deep(.presentation-metric-widget__value) {
  font-size: clamp(13px, 1.1vw, 22px);
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
  align-items: stretch;
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
  align-self: stretch;
}

.presentation-layout__column--left > .presentation-layout__component,
.presentation-layout__column--right > .presentation-layout__component {
  flex: 0 0 auto;
}

.presentation-layout__column--left > .presentation-layout__component:first-child {
  min-height: 220px;
  flex: 1 1 auto;
  overflow: auto;
}

.presentation-layout__column--right > .presentation-layout__component:first-child {
  min-height: 420px;
  height: auto;
  flex: 1 1 auto;
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

/* 分行总览把趋势放到业务结构下方，主区按排名表头加十行可视高度规划。
   排名组件收到分页模式后只保留当前十条 DOM 行，页面高度不会随总数增长。 */
.presentation-layout--branch-overview .presentation-layout__main,
.presentation-layout__main--branch-overview {
  height: 580px;
  min-height: 580px;
  max-height: 580px;
}

.presentation-layout--branch-overview .presentation-layout__column--branch-overview {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: 230px minmax(0, 340px);
  align-items: stretch;
  gap: 10px;
}

.presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component:first-child {
  min-height: 0;
  overflow: visible;
  grid-column: 1 / -1;
  grid-row: 1;
}

.presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component--trend {
  min-height: 340px;
  height: 340px;
  flex: none;
  grid-row: 2;
}

.presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component--trend-tabs {
  grid-column: 1 / -1;
}

/* 三个业务结构环在固定主区里压缩装饰间距，保留名称、数值和图例的完整可读内容。 */
.presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component:first-child :deep(.composition-tabs-widget) {
  box-sizing: border-box;
  min-height: 0;
  height: 100%;
  padding: 9px;
}

.presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component:first-child :deep(.composition-tabs-widget__rings) {
  margin-top: 7px;
  gap: 4px;
}

.presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component:first-child :deep(.composition-ring-card) {
  padding: 3px 4px;
  gap: 4px;
}

.presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component:first-child :deep(.composition-ring) {
  width: 80px;
  height: 80px;
  margin-top: 0;
}

.presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component:first-child :deep(.composition-ring::after) {
  inset: 12px;
}

.presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component:first-child :deep(.composition-ring-card__legend) {
  gap: 4px;
}

.presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component:first-child :deep(.composition-ring-card__legend button) {
  padding: 3px 4px;
  gap: 4px;
  font-size: 11px;
}

.presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component:first-child :deep(.composition-ring-card__heading strong) {
  font-size: 14px;
}

.presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component:first-child :deep(.composition-ring-card__heading span) {
  font-size: 11px;
}

.presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component:first-child :deep(.composition-ring__center strong) {
  font-size: 18px;
}

.presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component:first-child :deep(.composition-ring__center small) {
  font-size: 10px;
}

.presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component:first-child :deep(.composition-ring-card__legend button strong) {
  font-size: 11px;
}

.presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component:first-child :deep(.composition-ring-card__status) {
  min-height: 0;
  font-size: 10px;
}

@media (max-width: 1440px) {
  .presentation-layout--branch-overview .presentation-layout__column--branch-overview {
    grid-template-rows: 260px minmax(0, 310px);
  }

  .presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component--trend {
    min-height: 310px;
    height: 310px;
  }
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
  .presentation-layout__header--grouped { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .presentation-layout__metric-group--revenue { grid-column: 1 / -1; }
  .presentation-layout__metric-group--revenue .presentation-layout__metric-group-grid { grid-template-columns: minmax(0, 1fr); }
  .presentation-layout__column { gap: 8px; }
  .presentation-layout__column--center > .presentation-layout__component--map-primary { min-height: 350px; }
}

@media (max-width: 900px) {
  .presentation-layout__header--grouped { grid-template-columns: minmax(0, 1fr); }
  .presentation-layout__metric-group--revenue { grid-column: auto; }
  .presentation-layout__main { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .presentation-layout--branch-overview .presentation-layout__main,
  .presentation-layout__main--branch-overview { height: auto; min-height: 0; max-height: none; }
  .presentation-layout__column--center { grid-column: 1 / -1; grid-row: 1; }
  .presentation-layout__column--left { grid-column: 1; grid-row: 2; }
  .presentation-layout__column--right { grid-column: 2; grid-row: 2; }
  .presentation-layout__column--right > .presentation-layout__component:first-child { height: auto; min-height: 380px; }
  .presentation-layout__column--center > .presentation-layout__component--map-primary { min-height: 360px; }
  .presentation-layout__footer--bottom { grid-template-columns: 1fr; }
}

@media (max-width: 620px) {
  .presentation-layout { margin-right: 12px; margin-left: 12px; }
  .presentation-layout__metric-tier,
  .presentation-layout__metric-tier--secondary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .presentation-layout__main { display: flex; min-height: 0; flex-direction: column; }
  .presentation-layout--branch-overview .presentation-layout__main,
  .presentation-layout__main--branch-overview { height: auto; min-height: 0; max-height: none; }
  .presentation-layout__column--center { order: 1; }
  .presentation-layout__column--left { order: 2; }
  .presentation-layout__column--right { order: 3; }
  .presentation-layout__column--right > .presentation-layout__component:first-child { height: auto; max-height: 600px; min-height: 360px; }
  .presentation-layout__column--center > .presentation-layout__component--map-primary { min-height: 300px; }
  .presentation-layout__column--center > .presentation-layout__component--trend { min-height: 180px; }
  .presentation-layout--branch-overview .presentation-layout__column--branch-overview {
    display: flex;
    gap: 10px;
  }
  .presentation-layout--branch-overview .presentation-layout__column--branch-overview > .presentation-layout__component--trend {
    height: 310px;
    min-height: 310px;
    flex: 0 0 310px;
  }
  .presentation-layout__footer--bottom > .presentation-layout__component { min-height: 210px; }
}
</style>
