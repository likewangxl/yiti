<template>
  <section class="revenue-share-widget" data-testid="revenue-share-chart" :data-state="chartState">
    <div
      class="revenue-share-widget__section revenue-share-widget__section--operating"
      data-testid="revenue-share-operating-section"
      aria-label="营业收入"
      :data-display-unit="amountUnitToken(operating)"
    >
      <MetricDisplayWidgets
        class="revenue-share-widget__metric-card"
        :components="[revenueMetricCards.operating]"
        grouped
      />
    </div>
    <div
      class="revenue-share-widget__section revenue-share-widget__section--intermediary"
      data-testid="revenue-share-intermediary-section"
      aria-label="中间业务收入"
      :data-display-unit="amountUnitToken(intermediary)"
    >
      <MetricDisplayWidgets
        class="revenue-share-widget__metric-card"
        :components="[revenueMetricCards.intermediary]"
        grouped
      />
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import MetricDisplayWidgets from './MetricDisplayWidgets.vue';

const props = defineProps({
  operating: { type: Object, default: () => ({}) },
  intermediary: { type: Object, default: () => ({}) }
});
const chartState = computed(() => (
  props.operating?.state === 'READY' && props.intermediary?.state === 'READY' ? 'READY' : 'PENDING'
));
const revenueMetricCards = computed(() => ({
  operating: metricCard(props.operating, 'business-revenue-operating', '营业收入', 0),
  intermediary: metricCard(props.intermediary, 'business-revenue-fee', '中间业务收入', 1)
}));

function metricCard(item, componentId, title, order) {
  const source = item && typeof item === 'object' ? item : {};
  return {
    ...source,
    componentId,
    componentType: 'METRIC_CARD',
    layoutRegion: 'HEADER',
    order,
    title,
    unit: source.unit || source.sourceUnit || '',
    text: source.text || '—',
    state: source.state || 'NO_VALUE',
    subFields: Array.isArray(source.subFields) ? source.subFields : []
  };
}

function amountUnitToken(item) {
  const unit = String(item?.unit || item?.sourceUnit || '').trim().toUpperCase();
  return ({ 元: 'YUAN', YUAN: 'YUAN', 万元: 'TEN_THOUSAND', TEN_THOUSAND: 'TEN_THOUSAND', 亿元: 'HUNDRED_MILLION', HUNDRED_MILLION: 'HUNDRED_MILLION' })[unit] || '';
}
</script>

<style scoped>
.revenue-share-widget { box-sizing: border-box; display: grid; min-width: 0; min-height: 0; height: 100%; padding: 0; grid-template-rows: repeat(2, minmax(0, 1fr)); gap: 8px; color: #eaf2ff; }
.revenue-share-widget__section { box-sizing: border-box; display: block; min-width: 0; min-height: 0; overflow: visible; background: transparent; border: 0; border-radius: 0; }
.revenue-share-widget__metric-card.presentation-metric-widgets { width: 100%; height: 100%; min-height: 0; }
</style>
