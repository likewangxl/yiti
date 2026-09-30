<template>
  <section
    class="presentation-layout"
    :class="{ 'presentation-layout--branch-overview': isBranchOverview, 'presentation-layout--draft-overview': draftOverviewEnabled }"
    data-testid="presentation-layout"
    data-schema-version="1"
    aria-label="配置化大屏"
  >
    <section
      v-if="draftOverviewEnabled && overviewSummary"
      class="presentation-layout__overview-summary"
      data-testid="draft-overview-summary"
      aria-label="存款贷款总览摘要"
    >
      <article
        v-for="card in overviewSummary.cards"
        :key="card.key"
        class="presentation-layout__overview-summary-card"
        :class="{ 'presentation-layout__overview-summary-card--composition': card.kind === 'COMPOSITION' }"
        data-testid="draft-overview-summary-card"
        :data-summary-key="card.key"
        :data-config-key="card.configKey || card.key"
        :data-summary-kind="card.kind || 'TOTAL'"
      >
        <div
          v-if="draftOverviewEnabled && overviewCardIcon(card)"
          class="presentation-layout__overview-summary-card-icon"
          data-testid="draft-overview-card-icon"
          :data-icon="card.key"
          aria-hidden="true"
        ><component :is="overviewCardIcon(card)" /></div>
        <div class="presentation-layout__overview-summary-card-content">
          <div v-if="card.kind === 'COMPOSITION'" class="presentation-layout__overview-summary-composition" data-testid="draft-overview-composition">
            <CompositionTabsWidget
              :model="card.model"
              :ring-keys="[card.ringKey]"
              :compact="true"
              :show-total-caption="false"
              @business-line-select="onBusinessLineSelect"
            />
          </div>
          <template v-else>
            <div class="presentation-layout__overview-summary-card-heading">
              <span>{{ card.label }}</span>
              <strong>{{ card.text }}</strong>
            </div>
            <OverviewBalanceChart :metric="card.metric" :display-unit="overviewSummary.displayUnit" />
          </template>
        </div>
      </article>
    </section>
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
              :draft-overview="draftOverviewEnabled"
            />
          </div>
          <div
            v-for="component in displayGroupComponents(group)"
            :key="component.componentId"
            class="presentation-layout__component"
            :class="`presentation-layout__component--${String(component.componentType || '').toLowerCase()}`"
            data-testid="presentation-layout-component"
            :data-component-id="component.componentId"
            :data-config-key="componentConfigKey(component)"
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
            data-config-key="business-growth"
            :data-component-id="branchTrendComponents[0]?.componentId || 'legacy-trend-57'"
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
            v-else-if="isBranchMapComponent(column, component) && ($slots['branch-map'] || $slots.map)"
            class="presentation-layout__component presentation-layout__component--map presentation-layout__component--map-primary"
            data-testid="presentation-layout-component"
            data-branch-slot="map"
            :data-component-id="component.componentId"
            data-config-key="institution-map"
            :data-component-type="component.componentType"
            :data-layout-region="component.layoutRegion"
            :data-order="component.order"
          >
            <slot v-if="$slots['branch-map']" name="branch-map" :component="component" />
            <slot v-else name="map" :component="component" />
          </div>
          <div
            v-else-if="isBranchRankingComponent(column, component) && ($slots['branch-ranking'] || $slots.ranking)"
            class="presentation-layout__component presentation-layout__component--ranking"
            data-testid="presentation-layout-component"
            data-branch-slot="ranking"
            :data-component-id="component.componentId"
            data-config-key="institution-ranking"
            :data-component-type="component.componentType"
            :data-layout-region="component.layoutRegion"
            :data-order="component.order"
            data-visible-rows="10"
          >
            <slot v-if="$slots['branch-ranking']" name="branch-ranking" :component="component" />
            <slot v-else name="ranking" :component="component" />
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
            :data-config-key="componentConfigKey(component)"
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
          :data-config-key="componentConfigKey(component)"
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
import { Coin, DataAnalysis, Money, PieChart, Wallet } from '@element-plus/icons-vue';

import MetricDisplayWidgets from '../widgets/MetricDisplayWidgets.vue';
import RevenueShareWidget from '../widgets/RevenueShareWidget.vue';
import SeriesTableWidgets from '../widgets/SeriesTableWidgets.vue';
import BusinessGrowthWidget from '../widgets/BusinessGrowthWidget.vue';
import CompositionTabsWidget from '../widgets/CompositionTabsWidget.vue';
import OverviewBalanceChart from '../widgets/OverviewBalanceChart.vue';
import InstitutionRankingWidget from '../widgets/InstitutionRankingWidget.vue';
import PresentationMapWidget from '../map/PresentationMapWidget.vue';
import { buildDisplayMetricsModel, canonicalUnit, formatDisplayMetric } from '../model/displayMetricsModel';
import { buildDisplaySeriesTableModel } from '../model/displaySeriesTableModel';
import { buildCompositionTabsModel } from '../model/compositionTabsModel';
import { buildInstitutionRankingModel } from '../model/institutionRankingModel';
import { screenDisplayText } from '../model/screenDisplayText';
import { buildSettlementDepositMetric } from '../../panorama/settlementDepositMapping';
import { resolveConfiguredMetricLabel } from '../editor/runtimeComponentCatalog';
import { computeExplicitComparison } from '../model/explicitComparisons';
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
  amountUnit: { type: String, default: '' },
  draftOverview: { type: Boolean, default: false }
});

const emit = defineEmits([
  'region-select',
  'branch-select',
  'map-context',
  'metric-change',
  'business-line-select'
]);

const resolvedPresentation = computed(() => {
  const presentation = presentationOf(props.presentation);
  const rootLabels = props.presentation?.metricLabels || props.presentation?.displayPresentation?.metricLabels;
  return rootLabels ? { ...presentation, metricLabels: rootLabels } : presentation;
});
const components = computed(() => getDisplayComponents(props.presentation));
const isBranchOverview = computed(() => resolvedPresentation.value?.template === 'branch-overview-v1');

const headerComponents = computed(() => components.value.filter(component => component.layoutRegion === 'HEADER'));
const HEADER_GROUP_DEFINITIONS = Object.freeze([
  { key: 'RETAIL', prefix: 'business-retail-', label: '零售业务' },
  { key: 'CORP', prefix: 'business-corp-', label: '对公业务' },
  { key: 'REVENUE', prefix: 'business-revenue-', label: '营业收入' }
]);
const OVERVIEW_CARD_ICONS = Object.freeze({
  deposit: Wallet,
  'deposit-composition': PieChart,
  loan: Coin,
  'loan-composition': DataAnalysis,
  settlementDeposit: Money
});

function overviewCardIcon(card) {
  return OVERVIEW_CARD_ICONS[card?.key] || null;
}

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
const branchMapComponent = computed(() => components.value.find(component => component.layoutRegion === 'CENTER' && component.componentType === 'MAP') || null);
const branchRankingComponent = computed(() => components.value.find(component => component.layoutRegion === 'RIGHT' && component.componentType === 'RANKING') || null);
const mainColumns = computed(() => {
  const leftComponents = components.value.filter(component => component.layoutRegion === 'LEFT'
    && !isDraftOverviewPrimaryComposition(component));
  const centerComponents = components.value.filter(component => component.layoutRegion === 'CENTER');
  return [
    {
      key: 'LEFT',
      label: draftOverviewEnabled.value ? '左侧业务增长曲线' : '左侧业务结构',
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

function isBranchMapComponent(column, component) {
  return isBranchOverview.value && column.key === 'CENTER' && component.componentType === 'MAP'
    && component.componentId === branchMapComponent.value?.componentId;
}

function isBranchRankingComponent(column, component) {
  return isBranchOverview.value && column.key === 'RIGHT' && component.componentType === 'RANKING'
    && component.componentId === branchRankingComponent.value?.componentId;
}

function componentConfigKey(component) {
  if (isBranchOverview.value && component?.componentType === 'MAP') return 'institution-map';
  if (isBranchOverview.value && component?.componentType === 'RANKING') return 'institution-ranking';
  return component?.componentId || '';
}

const metricsModel = computed(() => buildDisplayMetricsModel(resolvedPresentation.value, props.model, {
  amountUnit: props.amountUnit,
  draftOverview: props.draftOverview
}));
const seriesModel = computed(() => buildDisplaySeriesTableModel(resolvedPresentation.value, props.model));
const compositionModel = computed(() => buildCompositionTabsModel(resolvedPresentation.value, props.model));

const AMOUNT_SCALES = Object.freeze({ YUAN: 1, TEN_THOUSAND: 1e4, HUNDRED_MILLION: 1e8 });
const SUMMARY_COMPARISONS = Object.freeze([
  { key: 'year', label: '较上年' }, { key: 'month', label: '较上月' }, { key: 'day', label: '较上日' }
]);

function finiteSummary(value) {
  if (value === null || value === undefined || typeof value === 'boolean' || Array.isArray(value)
    || (typeof value === 'object' && value !== null) || (typeof value === 'string' && value.trim() === '')) return null;
  if (!['string', 'number'].includes(typeof value)) return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function sourceDateInfo(value) {
  const explicit = value?.dataDate ?? value?.sourceDate ?? value?.date ?? value?.periodDate;
  if (explicit !== undefined && explicit !== null && String(explicit).trim()) {
    const date = String(explicit).trim();
    return { date: /^\d{4}-\d{2}-\d{2}$/.test(date) ? date : '', valid: /^\d{4}-\d{2}-\d{2}$/.test(date) };
  }
  const fallback = String(props.model?.dataDate || '').trim();
  return { date: /^\d{4}-\d{2}-\d{2}$/.test(fallback) ? fallback : '', valid: /^\d{4}-\d{2}-\d{2}$/.test(fallback) };
}

function sourceAmount(source) {
  const value = finiteSummary(source?.value ?? source?.rawValue);
  const unit = canonicalUnit(source?.unit ?? source?.sourceUnit);
  if (value === null || !Object.prototype.hasOwnProperty.call(AMOUNT_SCALES, unit)) return null;
  const sourceDate = sourceDateInfo(source);
  return { baseValue: value * AMOUNT_SCALES[unit], date: sourceDate.date, dateValid: sourceDate.valid, unit };
}

function sameAmount(left, right) {
  const scale = Math.max(1, Math.abs(left), Math.abs(right));
  return Math.abs(left - right) <= Number.EPSILON * scale * 4;
}

function comparisonAmount(comparison, source) {
  if (!comparison) return null;
  const rawValue = finiteSummary(comparison.rawValue);
  const value = rawValue === null ? finiteSummary(comparison.value) : rawValue;
  const unit = canonicalUnit(rawValue === null ? (comparison.unit || source?.unit || source?.sourceUnit) : (source?.sourceUnit || source?.unit));
  if (value === null || !Object.prototype.hasOwnProperty.call(AMOUNT_SCALES, unit)) return null;
  return { baseValue: value * AMOUNT_SCALES[unit], referenceDate: comparison.referenceDate || '' };
}

function summaryFromComponents(componentIds) {
  if (!Array.isArray(componentIds) || componentIds.length === 0) return null;
  const components = componentIds.map(id => metricsModel.value.components.find(item => item.componentId === id));
  if (components.some(item => !item)) return null;
  const values = components.map(sourceAmount);
  if (values.some(item => !item || !item.dateValid) || values.some(item => item.date !== values[0].date)) return null;
  const comparisons = {};
  for (const item of SUMMARY_COMPARISONS) {
    const entries = components.map(component => comparisonAmount(component?.comparisons?.[item.key], component));
    if (entries.every(Boolean) && entries.every(entry => entry.referenceDate && entry.referenceDate === entries[0].referenceDate)) {
      comparisons[item.key] = { baseValue: entries.reduce((sum, entry) => sum + entry.baseValue, 0), referenceDate: entries[0].referenceDate };
    }
  }
  return {
    baseValue: values.reduce((sum, item) => sum + item.baseValue, 0),
    date: values[0].date,
    dateValid: values.every(item => item.dateValid && item.date === values[0].date),
    unit: 'YUAN',
    comparisons
  };
}

function summaryFromKpi(key, componentIds) {
  const kpis = Array.isArray(props.model?.kpis) ? props.model.kpis : [];
  const explicit = kpis.find(item => String(item?.key || '') === key);
  if (explicit) {
    const metric = sourceAmount(explicit);
    if (!metric) return null;
    const fallback = summaryFromComponents(componentIds);
    const fallbackMatches = fallback && metric.dateValid && fallback.dateValid && metric.date === fallback.date
      && sameAmount(metric.baseValue, fallback.baseValue);
    const comparisons = fallbackMatches ? { ...fallback.comparisons } : {};
    for (const item of SUMMARY_COMPARISONS) {
      const source = explicit.comparisons?.[item.key];
      const hasExplicitComparison = explicit.comparisons
        && Object.prototype.hasOwnProperty.call(explicit.comparisons, item.key);
      if (!hasExplicitComparison) continue;
      const normalized = comparisonAmount(source, explicit);
      if (normalized) comparisons[item.key] = normalized;
      else delete comparisons[item.key];
    }
    return {
      ...metric,
      date: metric.date || fallback?.date || '',
      comparisons
    };
  }

  if (key === 'settlementDeposit') return buildSettlementDepositMetric(props.model);
  return summaryFromComponents(componentIds);
}

function displaySummaryValue(metric, displayUnit) {
  if (!metric) return '—';
  return formatDisplayMetric(metric.baseValue / AMOUNT_SCALES.YUAN, { displayUnit, decimals: 2, thousandsSeparator: true }, 'YUAN').text;
}

function summaryCardComparisons(metric, displayUnit) {
  return SUMMARY_COMPARISONS.map(item => {
    const source = metric?.comparisons?.[item.key];
    if (!source || source.state === 'NO_VALUE' || finiteSummary(source.baseValue) === null) {
      return { key: item.key, referenceDate: '', text: `${item.label} 暂无数据` };
    }
    const formatted = formatDisplayMetric(source.baseValue / AMOUNT_SCALES.YUAN,
      { displayUnit, decimals: 2, thousandsSeparator: true, negativeStyle: 'SIGNED' }, 'YUAN');
    return {
      key: item.key,
      referenceDate: source.referenceDate || '',
      text: `${item.label} ${formatted.value > 0 ? '+' : ''}${formatted.text}`
    };
  });
}

function explicitSummaryComparisons(key, metric, displayUnit) {
  const config = resolvedPresentation.value?.display?.comparisons?.[key];
  if (!config) return { configured: false, comparisons: metric?.comparisons || {} };
  if (config.enabled === false) return { configured: true, comparisons: null };
  const source = props.model?.comparisonResults?.[String(config.historyBlockId)] || props.model?.comparisonResults?.[config.historyBlockId];
  const rows = Array.isArray(source?.rows) ? source.rows : [];
  const format = { displayUnit, decimals: 2, thousandsSeparator: true, negativeStyle: 'SIGNED' };
  const mainValue = metric?.baseValue;
  // summaryFromKpi/sourceAmount 已把主值统一换算为元；metric.unit 仅保留原始来源单位，
  // 不能再拿它解释 baseValue，否则亿元/万元主值会被重复放大。
  const mainUnit = 'YUAN';
  const build = (period, label) => {
    const result = computeExplicitComparison({ currentDate: props.model?.dataDate, period, mainValue, mainUnit, rows,
      unitByField: source?.unitByField, valueFields: config.valueFields, dateField: config.dateField, sourceUnit: config.sourceUnit });
    if (result.state !== 'READY') return { state: 'NO_VALUE', value: null, baseValue: null, text: `${label} 暂无数据` };
    const formatted = formatDisplayMetric(result.value, format, mainUnit);
    const resultUnit = canonicalUnit(result.unit || mainUnit);
    const baseValue = Object.prototype.hasOwnProperty.call(AMOUNT_SCALES, resultUnit)
      ? result.value * AMOUNT_SCALES[resultUnit] : null;
    return { state: 'READY', value: formatted.value, rawValue: result.value, baseValue, unit: resultUnit, referenceDate: result.referenceDate,
      text: `${label} ${formatted.value > 0 ? '+' : ''}${formatted.text}` };
  };
  return {
    configured: true,
    comparisons: { year: build('year', '较上年'), month: build('month', '较上月'), day: build('day', '较上日') }
  };
}

const draftOverviewEnabled = computed(() => props.draftOverview === true && isBranchOverview.value);
const draftOverviewCompositionComponent = computed(() => draftOverviewEnabled.value
  ? components.value.find(component => component.layoutRegion === 'LEFT' && component.componentType === 'COMPOSITION_TABS') || null
  : null);

function overviewCompositionCard(ringKey, label) {
  const component = draftOverviewCompositionComponent.value;
  if (!component) return null;
  const model = compositionComponentModel(component);
  const tab = model.tabs?.find(item => item.tabKey === ringKey);
  const tabLabel = String(tab?.label || '').trim();
  const defaultLabel = ringKey === 'deposit' ? '存款业务分布' : ringKey === 'loan' ? '贷款业务分布' : label;
  const heading = tabLabel && !['存款', '贷款', '业务结构'].includes(tabLabel) ? tabLabel : defaultLabel;
  return {
    key: `${ringKey}-composition`,
    configKey: `overview-${ringKey}-composition`,
    kind: 'COMPOSITION',
    label: heading,
    ringKey,
    // 标题来自当前有效 tab；components/tabs/sections 继续来自同一个真实组件模型。
    model: { ...model, title: heading, subtitle: '' }
  };
}

const overviewSummary = computed(() => {
  if (!draftOverviewEnabled.value) return null;
  const deposit = summaryFromKpi('deposit', ['business-retail-deposit-balance', 'business-corp-deposit-balance']);
  const loan = summaryFromKpi('loan', ['business-retail-loan-balance', 'business-corp-loan-balance']);
  const settlementDeposit = summaryFromKpi('settlementDeposit', []);
  const sourceUnit = deposit?.unit || loan?.unit || settlementDeposit?.unit || '';
  const displayUnit = canonicalUnit(props.amountUnit) && Object.prototype.hasOwnProperty.call(AMOUNT_SCALES, canonicalUnit(props.amountUnit))
    ? canonicalUnit(props.amountUnit) : sourceUnit;
  const depositComposition = overviewCompositionCard('deposit', '存款业务分布');
  const loanComposition = overviewCompositionCard('loan', '贷款业务分布');
  const depositComparisonState = explicitSummaryComparisons('overview-deposit', deposit, displayUnit);
  const loanComparisonState = explicitSummaryComparisons('overview-loan', loan, displayUnit);
  const settlementComparisonState = explicitSummaryComparisons('overview-settlementDeposit', settlementDeposit, displayUnit);
  const depositWithComparisons = deposit ? { ...deposit, comparisonConfigured: depositComparisonState.configured, comparisons: depositComparisonState.comparisons } : deposit;
  const loanWithComparisons = loan ? { ...loan, comparisonConfigured: loanComparisonState.configured, comparisons: loanComparisonState.comparisons } : loan;
  const settlementWithComparisons = settlementDeposit ? { ...settlementDeposit, comparisonConfigured: settlementComparisonState.configured, comparisons: settlementComparisonState.comparisons } : settlementDeposit;
  return {
    displayUnit,
    cards: [
      { key: 'deposit', configKey: 'overview-deposit', label: resolveConfiguredMetricLabel(resolvedPresentation.value, 'deposit', '存款总额'), text: displaySummaryValue(depositWithComparisons, displayUnit), metric: depositWithComparisons, comparisons: summaryCardComparisons(depositWithComparisons, displayUnit) },
      ...(depositComposition ? [depositComposition] : []),
      { key: 'loan', configKey: 'overview-loan', label: resolveConfiguredMetricLabel(resolvedPresentation.value, 'loan', '贷款总额'), text: displaySummaryValue(loanWithComparisons, displayUnit), metric: loanWithComparisons, comparisons: summaryCardComparisons(loanWithComparisons, displayUnit) },
      ...(loanComposition ? [loanComposition] : []),
      { key: 'settlementDeposit', configKey: 'overview-settlementDeposit', label: '结算性存款', text: displaySummaryValue(settlementWithComparisons, displayUnit), metric: settlementWithComparisons, comparisons: summaryCardComparisons(settlementWithComparisons, displayUnit) }
    ]
  };
});

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

function isDraftOverviewPrimaryComposition(component) {
  return draftOverviewEnabled.value
    && component?.layoutRegion === 'LEFT'
    && component?.componentType === 'COMPOSITION_TABS'
    && component.componentId === draftOverviewCompositionComponent.value?.componentId;
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
    grouped: headerGroups.value.length > 0 && component.layoutRegion === 'HEADER',
    draftOverview: draftOverviewEnabled.value
  };
  if (['TREND', 'DETAIL_TABLE'].includes(component.componentType)) return { components: seriesComponents(component) };
  if (component.componentType === 'COMPOSITION_TABS') {
    const model = compositionComponentModel(component);
    return isDraftOverviewPrimaryComposition(component)
      ? { model, ringKeys: ['income'], showTotalCaption: false }
      : { model };
  }
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

.presentation-layout__overview-summary {
  display: grid;
  min-width: 0;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  padding: 7px 8px 6px;
  border: 1px solid var(--presentation-border-soft);
  border-radius: 9px;
  background: rgba(7, 22, 56, .68);
}
.presentation-layout__overview-summary-card {
  display: grid;
  container-type: inline-size;
  container-name: overview-summary-card;
  min-width: 0;
  min-height: 112px;
  align-content: center;
  grid-template-columns: minmax(0, 1fr);
  gap: 5px;
  padding: 7px 11px;
  border: 1px solid var(--presentation-border-soft);
  border-radius: 7px;
  background: rgba(8, 24, 61, .7);
}
.presentation-layout__overview-summary-card-heading { display: flex; min-width: 0; align-items: baseline; justify-content: space-between; gap: 10px; }
.presentation-layout__overview-summary-card-heading span { min-width: 0; overflow: hidden; color: var(--presentation-text-dim); font-size: var(--screen-font-label, var(--screen-font-size-label, 14px)); text-overflow: ellipsis; white-space: nowrap; }
.presentation-layout__overview-summary-card-heading strong { min-width: 0; overflow: hidden; color: var(--presentation-text); font-size: var(--screen-font-value, var(--screen-font-size-value, clamp(16px, 1.25vw, 22px))); line-height: 1.1; overflow-wrap: anywhere; text-align: right; text-overflow: ellipsis; white-space: nowrap; }
.presentation-layout__overview-summary-card-comparisons { display: grid; min-width: 0; grid-column: 1 / -1; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 3px; color: var(--presentation-text-dim); font-family: inherit; font-size: var(--screen-font-caption, 11px); line-height: 1.2; }
.presentation-layout__overview-summary-card-comparisons span { min-width: 0; overflow: visible; text-overflow: clip; white-space: normal; overflow-wrap: anywhere; }
.presentation-layout__overview-summary-card--composition { display: flex; min-height: 104px; padding: 0; }
.presentation-layout__overview-summary-composition { display: flex; min-width: 0; width: 100%; }
.presentation-layout__overview-summary-composition > * { width: 100%; }
@container overview-summary-card (max-width: 360px) {
  .presentation-layout__overview-summary-card-heading { align-items: flex-start; flex-direction: column; gap: 2px; }
  .presentation-layout__overview-summary-card-heading strong { max-width: 100%; overflow: visible; text-align: left; text-overflow: clip; white-space: normal; }
}
.presentation-layout--draft-overview .presentation-layout__metric-group { min-height: 0; padding: 4px; gap: 4px; }
.presentation-layout--draft-overview .presentation-layout__metric-group-grid { align-content: start; grid-auto-rows: minmax(90px, auto); }
.presentation-layout--draft-overview .presentation-layout__metric-group--revenue .presentation-layout__metric-group-grid { grid-auto-rows: auto; }
.presentation-layout--draft-overview .presentation-layout__header--grouped :deep(.presentation-metric-widget) { min-height: 90px; padding: 6px 8px; }
.presentation-layout--draft-overview .presentation-layout__header--grouped :deep(.presentation-metric-widget__icon) { width: 25px; height: 25px; flex-basis: 25px; font-size: 17px; }
.presentation-layout--draft-overview .presentation-layout__header--grouped :deep(.presentation-metric-widget__comparisons) { gap: 1px 4px; font-size: 11px; }
.presentation-layout--draft-overview .presentation-layout__main--branch-overview { height: auto; min-height: 580px; max-height: none; }
.presentation-layout--draft-overview .presentation-layout__column--left > .presentation-layout__component:first-child { height: auto; min-height: 0; flex: 0 0 auto; }
.presentation-layout--draft-overview .presentation-layout__column--left > .presentation-layout__component:first-child :deep(.composition-tabs-widget) { height: auto; }

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
  .presentation-layout__overview-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
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
  .presentation-layout--draft-overview .presentation-layout__overview-summary { grid-template-columns: minmax(0, 1fr); }
  .presentation-layout--draft-overview .presentation-layout__overview-summary-card-comparisons { grid-template-columns: minmax(0, 1fr); }
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
  .presentation-layout__column--right > .presentation-layout__component[data-branch-slot="ranking"] { height: auto; max-height: none; min-height: 0; flex: 0 0 auto; }
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

/* 草稿分行统一五列网格；发布态继续使用保存的原布局。 */
.presentation-layout--draft-overview {
  --draft-layout-gap: 8px;
  --draft-card-padding: 12px;
  --draft-amount-size: clamp(14px, .9375vw, 18px);
  --draft-caption-size: 12px;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--draft-layout-gap);
  padding: 0;
  border: 0;
  background: transparent;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card {
  min-width: 0;
  padding: var(--draft-card-padding);
  gap: var(--draft-layout-gap);
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card-heading {
  align-items: baseline;
  justify-content: flex-start;
  flex-direction: row;
  flex-wrap: wrap;
  gap: var(--draft-layout-gap);
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card-heading > span,
.presentation-layout--draft-overview .presentation-layout__overview-summary-card-heading > strong {
  min-width: 0;
  max-width: 100%;
  color: var(--presentation-text);
  font-size: var(--draft-amount-size);
  font-weight: 600;
  line-height: 1.35;
  overflow: visible;
  text-align: left;
  text-overflow: clip;
  white-space: normal;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card--composition {
  display: flex;
  min-height: 112px;
  padding: var(--draft-card-padding);
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card--composition :deep(.composition-tabs-widget) {
  box-sizing: border-box;
  min-height: 0;
  height: 100%;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-reserved-slot {
  display: block;
  min-width: 0;
  grid-column: 5;
  border: 0;
  background: transparent;
}
.presentation-layout--draft-overview .presentation-layout__header--grouped {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--draft-layout-gap);
}
.presentation-layout--draft-overview .presentation-layout__metric-group {
  min-width: 0;
  padding: 0;
  gap: var(--draft-layout-gap);
  border: 0;
  border-radius: 0;
  background: transparent;
}
.presentation-layout--draft-overview .presentation-layout__metric-group--retail { grid-column: span 2; }
.presentation-layout--draft-overview .presentation-layout__metric-group--corp { grid-column: span 2; }
.presentation-layout--draft-overview .presentation-layout__metric-group--revenue { grid-column: span 1; }
.presentation-layout--draft-overview .presentation-layout__metric-group-header {
  min-height: 28px;
  padding: 0;
}
.presentation-layout--draft-overview .presentation-layout__metric-group-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--draft-layout-gap);
}
.presentation-layout--draft-overview .presentation-layout__metric-group--revenue .presentation-layout__metric-group-grid {
  grid-template-columns: minmax(0, 1fr);
  align-content: stretch;
  grid-template-rows: minmax(0, 1fr);
}
.presentation-layout--draft-overview .presentation-layout__header--grouped :deep(.presentation-metric-widget) {
  box-sizing: border-box;
  min-height: 90px;
  height: 100%;
  padding: var(--draft-card-padding);
  align-items: flex-start;
}
.presentation-layout--draft-overview .presentation-layout__header--grouped :deep(.presentation-metric-widget__content) {
  align-content: start;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card-comparisons,
.presentation-layout--draft-overview .presentation-layout__header--grouped :deep(.presentation-metric-widget__comparisons) {
  font-size: var(--draft-caption-size);
  line-height: 1.35;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card--composition :deep(.composition-ring-card) {
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card--composition :deep(.composition-tabs-widget--compact .composition-tabs-widget__header h2) {
  font-size: var(--draft-amount-size);
  font-weight: 600;
  line-height: 1.35;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card--composition :deep(.composition-tabs-widget--compact .composition-tabs-widget__header h2::before) {
  display: none;
  width: 0;
  margin: 0;
}
@media (max-width: 1180px) {
  .presentation-layout--draft-overview .presentation-layout__overview-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .presentation-layout--draft-overview .presentation-layout__overview-summary-reserved-slot { display: none; }
}
@media (max-width: 900px) {
  .presentation-layout--draft-overview .presentation-layout__header--grouped {
    grid-template-columns: minmax(0, 1fr);
  }
  .presentation-layout--draft-overview .presentation-layout__metric-group--retail,
  .presentation-layout--draft-overview .presentation-layout__metric-group--corp,
  .presentation-layout--draft-overview .presentation-layout__metric-group--revenue {
    grid-column: auto;
  }
}
@media (max-width: 620px) {
  .presentation-layout--draft-overview .presentation-layout__overview-summary {
    grid-template-columns: minmax(0, 1fr);
  }
}

/* 局部压过 screenTypography 的金额字号规则，只作用于草稿分组卡。 */
.presentation-layout--draft-overview .presentation-layout__header--grouped :deep(.presentation-metric-widgets .presentation-metric-widget.presentation-metric-widget--grouped[data-component-type] .presentation-metric-widget__header h2),
.presentation-layout--draft-overview .presentation-layout__header--grouped :deep(.presentation-metric-widgets .presentation-metric-widget.presentation-metric-widget--grouped[data-component-type] .presentation-metric-widget__header-value),
.presentation-layout--draft-overview .presentation-layout__header--grouped :deep(.presentation-metric-widgets .presentation-metric-widget.presentation-metric-widget--grouped[data-component-type] .presentation-metric-widget__value) {
  font-size: var(--draft-amount-size);
  font-weight: 600;
  line-height: 1.35;
}
.presentation-layout--draft-overview .presentation-layout__header--grouped :deep(.presentation-metric-widgets .presentation-metric-widget.presentation-metric-widget--grouped[data-component-type] .presentation-metric-widget__comparisons) {
  font-size: var(--draft-caption-size);
  line-height: 1.35;
}

.presentation-layout--draft-overview .presentation-layout__header--grouped :deep(.presentation-metric-widgets .presentation-metric-widget.presentation-metric-widget--grouped[data-component-type] .presentation-metric-widget__header > div) {
  width: 100%;
  min-width: 0;
  flex: 1 1 auto;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card {
  display: grid;
  grid-template-columns: 25px minmax(0, 1fr);
  align-items: flex-start;
  align-content: start;
  column-gap: var(--draft-layout-gap);
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card-icon {
  display: flex;
  width: 25px;
  height: 25px;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  border: 1px solid var(--presentation-border);
  border-radius: 50%;
  color: var(--overview-icon-accent, var(--presentation-cyan));
  background: var(--overview-icon-background, rgba(55, 115, 205, .2));
  font-size: 17px;
  line-height: 1;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card-icon[data-icon="deposit"] {
  --overview-icon-accent: var(--presentation-cyan);
  --overview-icon-background: rgba(55, 115, 205, .2);
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card-icon[data-icon="deposit-composition"] {
  --overview-icon-accent: var(--presentation-violet);
  --overview-icon-background: rgba(19, 24, 74, .55);
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card-icon[data-icon="loan"] {
  --overview-icon-accent: var(--presentation-blue);
  --overview-icon-background: rgba(8, 27, 72, .55);
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card-icon[data-icon="loan-composition"] {
  --overview-icon-accent: #58e4b5;
  --overview-icon-background: rgba(7, 39, 61, .55);
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card-content {
  display: grid;
  min-width: 0;
  width: 100%;
  align-content: start;
  gap: var(--draft-layout-gap);
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card-heading {
  width: 100%;
  align-items: baseline;
  justify-content: flex-start;
  flex-wrap: wrap;
  gap: var(--draft-layout-gap);
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card-heading span {
  min-width: 0;
  flex: 1 1 auto;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card-heading strong {
  min-width: 0;
  max-width: 100%;
  margin-left: auto;
  flex: 0 1 auto;
  overflow: visible;
  text-align: right;
  text-overflow: clip;
  white-space: normal;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card--composition {
  display: grid;
  grid-template-columns: 25px minmax(0, 1fr);
  align-items: flex-start;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card--composition .presentation-layout__overview-summary-composition {
  width: 100%;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card--composition :deep(.composition-tabs-widget--compact .composition-ring-card) {
  grid-template-columns: 76px minmax(0, 1fr);
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card--composition :deep(.composition-tabs-widget--compact .composition-ring) {
  width: 76px;
  height: 76px;
}
.presentation-layout--draft-overview .presentation-layout__header--grouped :deep(.presentation-metric-widget) {
  gap: var(--draft-layout-gap);
}
.presentation-layout--draft-overview .presentation-layout__header--grouped :deep(.presentation-metric-widgets .presentation-metric-widget.presentation-metric-widget--grouped[data-component-type] .presentation-metric-widget__header-title-line) {
  width: 100%;
  flex-wrap: wrap;
  gap: var(--draft-layout-gap);
}
.presentation-layout--draft-overview .presentation-layout__header--grouped :deep(.presentation-metric-widgets .presentation-metric-widget.presentation-metric-widget--grouped[data-component-type] .presentation-metric-widget__header-title-line h2) {
  min-width: 0;
  flex: 1 1 auto;
}
.presentation-layout--draft-overview .presentation-layout__header--grouped :deep(.presentation-metric-widgets .presentation-metric-widget.presentation-metric-widget--grouped[data-component-type] .presentation-metric-widget__header-value--right) {
  margin-left: auto;
  text-align: right;
  white-space: normal;
}

/* 标题下方垂直居中，围绕完成率的原水平中心放大业务分布圆环。 */
.presentation-layout--draft-overview .presentation-layout__overview-summary-card--composition {
  align-content: stretch;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card--composition .presentation-layout__overview-summary-card-content {
  align-self: stretch;
  align-content: stretch;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card--composition :deep(.composition-tabs-widget__rings) {
  align-items: center;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card--composition :deep(.composition-tabs-widget--compact .composition-ring-card) {
  grid-template-columns: 76px minmax(0, 1fr);
  grid-template-rows: auto auto auto;
  column-gap: 20px;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card--composition :deep(.composition-tabs-widget--compact .composition-ring) {
  width: 96px;
  height: 96px;
  justify-self: center;
}
.presentation-layout--draft-overview .presentation-layout__overview-summary-card--composition :deep(.composition-tabs-widget--compact .composition-ring::after) {
  inset: 12px;
}
</style>
