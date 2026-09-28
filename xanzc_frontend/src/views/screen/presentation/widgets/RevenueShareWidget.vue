<template>
  <section class="revenue-share-widget" data-testid="revenue-share-chart" :data-state="share.ready ? 'READY' : 'PENDING'">
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
      <div class="revenue-share-widget__headline">
        <span class="revenue-share-widget__label" data-testid="revenue-share-ratio-label">占营业收入</span>
        <strong data-testid="revenue-share-percentage">{{ share.shareText }}</strong>
      </div>
      <div
        class="revenue-share-widget__track"
        role="img"
        :aria-label="share.ready ? `中间业务收入占营业收入 ${share.shareText}` : '中间业务收入占营业收入，占比待核对'"
      >
        <template v-if="share.ready">
          <i data-testid="revenue-share-part" :style="{ width: `${share.share}%` }" aria-hidden="true"></i>
          <b data-testid="revenue-share-rest" :style="{ width: `${share.remaining}%` }" aria-hidden="true"></b>
        </template>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import { buildRevenueShareModel } from '../model/revenueShareModel';
import MetricDisplayWidgets from './MetricDisplayWidgets.vue';

const props = defineProps({
  operating: { type: Object, default: () => ({}) },
  intermediary: { type: Object, default: () => ({}) }
});
const share = computed(() => buildRevenueShareModel(props.operating, props.intermediary));
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
.revenue-share-widget { box-sizing: border-box; display: grid; min-width: 0; min-height: 0; height: 100%; padding: 8px; grid-template-rows: minmax(0, .85fr) minmax(0, 1.15fr); gap: 8px; color: #eaf2ff; }
.revenue-share-widget__section { box-sizing: border-box; display: flex; min-width: 0; min-height: 0; overflow: hidden; flex-direction: column; gap: 8px; background: transparent; border: 0; border-radius: 0; }
.revenue-share-widget__metric-card.presentation-metric-widgets { width: 100%; height: auto; min-height: 0; flex: 1 1 0; }
.revenue-share-widget__section--operating .revenue-share-widget__metric-card.presentation-metric-widgets { height: 100%; }
.revenue-share-widget__section--intermediary .revenue-share-widget__metric-card.presentation-metric-widgets { flex-basis: 0; }
.revenue-share-widget__headline { display: flex; min-width: 0; justify-content: space-between; align-items: baseline; gap: 5px; }
.revenue-share-widget__headline strong { flex: 0 0 auto; color: #75e0ff; font-size: clamp(14px, 1.15vw, 22px); font-variant-numeric: tabular-nums; }
.revenue-share-widget__label { min-width: 0; color: #a9c1ed; font-size: clamp(10px, .72vw, 13px); line-height: 1.2; overflow-wrap: anywhere; word-break: break-word; }
.revenue-share-widget__track { display: flex; min-width: 0; width: 100%; height: 11px; overflow: hidden; background: rgba(133, 164, 222, .24); border-radius: 8px; }
.revenue-share-widget__track i { display: block; background: #56d8f5; box-shadow: 0 0 8px #56d8f5; }
.revenue-share-widget__track b { display: block; background: #5378d4; }
</style>
