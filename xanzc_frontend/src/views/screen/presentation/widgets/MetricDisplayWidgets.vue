<template>
  <section class="presentation-metric-widgets" :class="{ 'presentation-metric-widgets--draft': draftOverview }" data-testid="presentation-metric-widgets" aria-label="配置化指标与完成情况">
    <article
      v-for="item in components"
      :key="item.componentId"
      class="presentation-metric-widget"
      :class="[
        `presentation-metric-widget--${String(item.componentType || '').toLowerCase()}`,
        `presentation-metric-widget--${String(item.layoutRegion || '').toLowerCase()}`,
        grouped ? 'presentation-metric-widget--grouped' : '',
        draftOverview ? 'presentation-metric-widget--draft' : '',
        shouldUseCompletionRing(item) ? 'presentation-metric-widget--dial' : '',
        amountUnitClass(item),
        toneClass(item)
      ]"
      :data-component-id="item.componentId"
      :data-component-type="item.componentType"
      :data-layout-region="item.layoutRegion"
      :data-state="item.state"
      :title="grouped && item.metricName ? item.metricName : undefined"
    >
      <div
        v-if="iconFor(item)"
        class="presentation-metric-widget__icon"
        data-testid="presentation-metric-icon"
        :data-icon="iconName(item)"
        aria-hidden="true"
      ><component :is="iconFor(item)" /></div>
      <div class="presentation-metric-widget__content">
        <header class="presentation-metric-widget__header">
          <div>
            <div v-if="isDraftGroupedAmount(item)" class="presentation-metric-widget__header-title-line">
              <h2>{{ item.title || '—' }}</h2>
              <strong class="presentation-metric-widget__value presentation-metric-widget__header-value" data-testid="presentation-metric-value">{{ item.text }}</strong>
            </div>
            <h2 v-else>{{ item.title || '—' }}</h2>
            <p v-if="item.subtitle">{{ item.subtitle }}</p>
          </div>
          <small v-if="item.metricName && !grouped" :title="item.metricName">{{ item.metricName }}</small>
        </header>
        <div
          class="presentation-metric-widget__value-line"
          :class="{
            'presentation-metric-widget__value-line--grouped': grouped,
            'presentation-metric-widget__value-line--ring': shouldUseCompletionRing(item)
          }"
        >
          <CompletionRingGauge
            v-if="shouldUseCompletionRing(item)"
            :value="item.value"
            :text="item.text"
            :label="item.title || '完成率'"
            :accent="ringAccent(item)"
            :compact="draftOverview"
          />
          <strong v-else-if="!isDraftGroupedAmount(item)" class="presentation-metric-widget__value" data-testid="presentation-metric-value">{{ item.text }}</strong>
          <span
            v-if="grouped && comparisonRows(item).length"
            class="presentation-metric-widget__comparisons"
            data-testid="presentation-metric-comparisons"
          >
            <span
              v-for="comparison in comparisonRows(item)"
              :key="comparison.key"
              class="presentation-metric-widget__comparison"
              :data-comparison="comparison.key"
              :title="comparison.referenceDate ? `${comparison.text}（基准日 ${comparison.referenceDate}）` : comparison.text"
            >{{ comparison.text }}</span>
          </span>
          <span
            v-else-if="grouped"
            class="presentation-metric-widget__month-delta"
            data-testid="presentation-metric-month-delta"
          >{{ item.monthDelta?.text || '较上月 暂无数据' }}</span>
        </div>
        <span v-if="item.state !== 'READY' && !(draftOverview && shouldUseCompletionRing(item))" class="presentation-metric-widget__status" data-testid="presentation-metric-status">
          {{ statusText(item.state) }}
        </span>
        <div
          v-if="item.componentType === 'COMPLETION'"
          class="presentation-metric-widget__progress"
          role="progressbar"
          :aria-label="item.title || '完成情况'"
          :aria-valuenow="item.progress === null ? undefined : item.progress"
          aria-valuemin="0"
          aria-valuemax="100"
        ><i :style="{ width: `${item.progress === null ? 0 : item.progress}%` }" aria-hidden="true"></i></div>
        <small v-if="item.description" class="presentation-metric-widget__description">{{ item.description }}</small>
        <ul v-if="item.subFields?.length" class="presentation-metric-widget__sub-fields">
          <li v-for="subField in item.subFields" :key="subField.key">
            <span>{{ subField.label || subField.field || '辅助指标' }}</span><strong>{{ subField.text }}</strong>
          </li>
        </ul>
      </div>
    </article>
  </section>
</template>

<script setup>
import {
  Coin,
  CreditCard,
  DataAnalysis,
  Histogram,
  Money,
  OfficeBuilding,
  PieChart,
  Tickets,
  TrendCharts,
  Wallet
} from '@element-plus/icons-vue';
import CompletionRingGauge from './CompletionRingGauge.vue';

const props = defineProps({
  components: { type: Array, default: () => [] },
  grouped: { type: Boolean, default: false },
  draftOverview: { type: Boolean, default: false }
});

const METRIC_ICONS = Object.freeze({
  'business-retail-deposit-balance': ['Wallet', Wallet],
  'business-retail-deposit-rate': ['TrendCharts', TrendCharts],
  'business-retail-loan-balance': ['Coin', Coin],
  'business-retail-loan-rate': ['DataAnalysis', DataAnalysis],
  'business-corp-deposit-balance': ['OfficeBuilding', OfficeBuilding],
  'business-corp-deposit-rate': ['Histogram', Histogram],
  'business-corp-loan-balance': ['Money', Money],
  'business-corp-loan-rate': ['PieChart', PieChart],
  'business-revenue-operating': ['Tickets', Tickets],
  'business-revenue-fee': ['CreditCard', CreditCard]
});

const COMPLETION_RING_IDS = Object.freeze([
  'business-retail-deposit-rate',
  'business-retail-loan-rate',
  'business-corp-deposit-rate',
  'business-corp-loan-rate'
]);

// 圆环颜色按业务 ID 固定，配置顺序变化时仍能区分零售/对公与存款/贷款。
const COMPLETION_RING_ACCENTS = Object.freeze({
  'business-retail-deposit-rate': 'var(--panorama-cyan, #4de8ef)',
  'business-retail-loan-rate': 'var(--panorama-blue, #5896ff)',
  'business-corp-deposit-rate': 'var(--panorama-violet, #a979ff)',
  'business-corp-loan-rate': 'var(--panorama-up, #58e4b5)'
});

function iconEntry(item) {
  return METRIC_ICONS[String(item?.componentId || '')] || null;
}

function iconFor(item) {
  return iconEntry(item)?.[1] || null;
}

function iconName(item) {
  return iconEntry(item)?.[0] || '';
}

function shouldUseCompletionRing(item) {
  return props.grouped
    && item?.componentType === 'METRIC_CARD'
    && COMPLETION_RING_IDS.includes(String(item?.componentId || ''));
}

function isDraftGroupedAmount(item) {
  return props.draftOverview
    && props.grouped
    && item?.componentType === 'METRIC_CARD'
    && !shouldUseCompletionRing(item);
}

function ringAccent(item) {
  return COMPLETION_RING_ACCENTS[String(item?.componentId || '')]
    || 'var(--metric-accent, var(--panorama-cyan, #4de8ef))';
}

function statusText(state) {
  return ({ NO_SOURCE: '待接入', NO_VALUE: '暂无有效值' }[state] || '暂不可用');
}

function toneClass(item) {
  const order = Number(item?.order);
  if (!Number.isInteger(order) || order < 0) return '';
  return `presentation-metric-widget--${['cyan', 'violet', 'blue', 'green'][order % 4]}`;
}

function amountUnitClass(item) {
  const unit = String(item?.unit || '').trim().toUpperCase();
  const token = ({
    元: 'yuan', YUAN: 'yuan',
    万元: 'ten-thousand', TEN_THOUSAND: 'ten-thousand',
    亿元: 'hundred-million', HUNDRED_MILLION: 'hundred-million'
  })[unit];
  return token ? `presentation-metric-widget--unit-${token}` : '';
}

const COMPARISON_LABELS = Object.freeze({ year: '较上年', month: '较上月', day: '较上日' });

function comparisonRows(item) {
  const comparisons = item?.comparisons;
  if (!comparisons || typeof comparisons !== 'object') return [];
  return ['year', 'month', 'day'].map(key => ({
    key,
    referenceDate: comparisons[key]?.referenceDate || '',
    text: comparisons[key]?.text || `${COMPARISON_LABELS[key]} 暂无数据`
  }));
}
</script>

<style scoped>
.presentation-metric-widgets { display: block; width: 100%; height: 100%; min-width: 0; margin: 0; }
.presentation-metric-widget {
  --metric-accent: var(--panorama-cyan, #4de8ef);
  --metric-border: rgba(77, 232, 239, .42);
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 82px;
  height: 100%;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  overflow: hidden;
  color: var(--panorama-text, #eaf2ff);
  background: var(--panorama-panel, rgba(8, 24, 61, .86));
  border: 1px solid var(--metric-border);
  border-radius: 8px;
  box-shadow: inset 0 1px 0 rgba(201, 231, 255, .06), 0 8px 22px rgba(0, 0, 0, .14);
}
.presentation-metric-widget::before { width: 3px; height: 32px; position: absolute; top: 50%; left: 0; border-radius: 0 2px 2px 0; background: var(--metric-accent); box-shadow: 0 0 12px var(--metric-accent); content: ''; transform: translateY(-50%); }
.presentation-metric-widget__icon {
  display: inline-flex;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  align-items: center;
  justify-content: center;
  color: var(--metric-accent);
  font-size: 22px;
  background: rgba(55, 115, 205, .2);
  border: 1px solid var(--metric-border);
  border-radius: 50%;
  box-shadow: 0 0 12px rgba(77, 232, 239, .12);
}
.presentation-metric-widget__content { display: grid; min-width: 0; width: 100%; align-content: center; gap: 4px; }
.presentation-metric-widget--dial { min-height: 136px; padding-top: 7px; padding-bottom: 7px; }
.presentation-metric-widget--dial .presentation-metric-widget__content { gap: 0; }
.presentation-metric-widget--completion { --metric-accent: var(--panorama-violet, #a979ff); --metric-border: rgba(169, 121, 255, .48); background: rgba(19, 24, 74, .88); }
.presentation-metric-widget--cyan { --metric-accent: var(--panorama-cyan, #4de8ef); --metric-border: rgba(77, 232, 239, .42); }
.presentation-metric-widget--violet { --metric-accent: var(--panorama-violet, #a979ff); --metric-border: rgba(169, 121, 255, .48); background: rgba(19, 24, 74, .88); }
.presentation-metric-widget--blue { --metric-accent: var(--panorama-blue, #5896ff); --metric-border: rgba(88, 150, 255, .48); background: rgba(8, 27, 72, .88); }
.presentation-metric-widget--green { --metric-accent: #58e4b5; --metric-border: rgba(88, 228, 181, .44); background: rgba(7, 39, 61, .88); }
.presentation-metric-widget__header { display: flex; justify-content: space-between; gap: 8px; align-items: flex-start; min-width: 0; }
.presentation-metric-widget__header > div { min-width: 0; }
.presentation-metric-widget__header-title-line { display: flex; min-width: 0; align-items: baseline; flex-wrap: wrap; gap: 3px 8px; }
.presentation-metric-widget__header-title-line h2 { min-width: 0; max-width: 100%; flex: 0 0 auto; }
.presentation-metric-widget__header-value { display: inline; min-width: 0; max-width: 100%; flex: 0 1 auto; overflow-wrap: anywhere; white-space: normal; }
.presentation-metric-widget__header h2 { margin: 0; overflow: hidden; color: var(--panorama-text, #eaf2ff); font-size: 13px; font-weight: 600; line-height: 1.25; text-overflow: ellipsis; white-space: nowrap; }
.presentation-metric-widget--grouped .presentation-metric-widget__header h2 { max-width: 100%; overflow: visible; text-overflow: clip; }
.presentation-metric-widget__header p,.presentation-metric-widget__header small,.presentation-metric-widget__description { margin: 3px 0 0; overflow: hidden; color: var(--panorama-text-dim, #8fa9db); font-size: 10px; line-height: 1.3; text-overflow: ellipsis; white-space: nowrap; }
.presentation-metric-widget__header small { flex: 0 0 auto; }
.presentation-metric-widget__value-line { min-width: 0; max-width: 100%; margin-top: 2px; }
.presentation-metric-widget__value-line--grouped { display: flex; align-items: baseline; flex-wrap: wrap; gap: 2px 8px; }
.presentation-metric-widget__value-line--ring { align-items: center; flex-wrap: nowrap; gap: 8px; min-height: 100px; margin-top: 0; }
.presentation-metric-widget__value-line--ring :deep(.completion-ring-gauge) { width: min(150px, 58%); flex-basis: min(150px, 58%); }
.presentation-metric-widget__value-line--ring .presentation-metric-widget__month-delta {
  flex: 1 1 0;
  width: auto;
  margin-top: 0;
  font-size: 10px;
}
.presentation-metric-widget__value-line--ring .presentation-metric-widget__comparisons {
  flex: 1 1 0;
  width: auto;
}
.presentation-metric-widget--dial .presentation-metric-widget__status { position: static; margin-top: 0; }
.presentation-metric-widget__value { display: block; min-width: 0; max-width: 100%; margin: 0; color: #f4f8ff; font-size: clamp(18px, 1.55vw, 30px); font-weight: 750; line-height: 1.12; overflow-wrap: anywhere; word-break: break-word; }
.presentation-metric-widget__value-line--grouped .presentation-metric-widget__value { display: inline; }
.presentation-metric-widget--grouped.presentation-metric-widget--unit-yuan .presentation-metric-widget__value { font-size: clamp(12px, .9vw, 18px); }
.presentation-metric-widget--grouped.presentation-metric-widget--unit-ten-thousand .presentation-metric-widget__value { font-size: clamp(14px, 1.15vw, 22px); }
.presentation-metric-widget--grouped.presentation-metric-widget--unit-hundred-million .presentation-metric-widget__value { font-size: clamp(16px, 1.35vw, 26px); }
.presentation-metric-widget__month-delta { min-width: 0; max-width: 100%; color: var(--panorama-text-dim, #8fa9db); font-size: 10px; font-weight: 550; line-height: 1.25; overflow-wrap: anywhere; word-break: break-word; }
.presentation-metric-widget__comparisons { display: grid; width: 100%; min-width: 0; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 2px 7px; color: var(--panorama-text-dim, #8fa9db); font-size: 9px; font-weight: 550; line-height: 1.25; }
.presentation-metric-widget__comparison { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.presentation-metric-widgets--draft .presentation-metric-widget--grouped { box-sizing: border-box; min-height: 112px; height: auto; container-type: inline-size; container-name: presentation-metric-widget; padding: 6px 8px; overflow: visible; gap: 8px; }
.presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__icon { width: 24px; height: 24px; flex-basis: 24px; font-size: 16px; }
.presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__content { gap: 2px; }
.presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__header h2 { font-size: 12px; }
.presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value-line { display: grid; grid-template-columns: minmax(0, 1fr); gap: 2px; margin-top: 0; }
.presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value-line--ring { grid-template-columns: 76px minmax(0, 1fr); align-items: center; column-gap: 8px; row-gap: 3px; min-height: 76px; }
.presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value-line--ring :deep(.completion-ring-gauge) { width: 76px; flex-basis: 76px; }
.presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value-line--ring .presentation-metric-widget__comparisons,
.presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value-line--ring .presentation-metric-widget__month-delta { grid-column: 2; width: auto; }
.presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value { font-size: clamp(18px, 1.1vw, 21px); line-height: 1.25; }
.presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__comparisons { grid-template-columns: 1fr; gap: 1px; font-size: 11px; line-height: 1.15; }
.presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__comparison { overflow: visible; overflow-wrap: anywhere; text-overflow: clip; white-space: normal; }
.presentation-metric-widgets--draft .presentation-metric-widget--dial { min-height: 112px; }
.presentation-metric-widget__status { display: block; margin-top: 2px; color: var(--panorama-amber, #ffc45e); font-size: 10px; line-height: 1.25; }
.presentation-metric-widget__progress { height: 7px; margin-top: 4px; overflow: hidden; background: rgba(2, 9, 22, .72); border: 1px solid rgba(133, 164, 222, .42); border-radius: 4px; }
.presentation-metric-widget__progress i { display: block; height: 100%; background: var(--metric-accent); border-radius: inherit; box-shadow: 0 0 8px var(--metric-accent); }
.presentation-metric-widget__description { margin-top: 2px; }
.presentation-metric-widget__sub-fields { display: grid; gap: 3px; margin: 5px 0 0; padding: 0; overflow: auto; list-style: none; color: #bcd5ff; font-size: 10px; }
.presentation-metric-widget__sub-fields li { display: flex; justify-content: space-between; gap: 8px; }
.presentation-metric-widget__sub-fields strong { color: #f2f6ff; font-weight: 600; }
.presentation-metric-widget--completion .presentation-metric-widget__value { color: var(--metric-accent); }
@media (max-width: 1366px) {
  .presentation-metric-widget { padding-right: 12px; padding-left: 12px; }
  .presentation-metric-widget__value { font-size: clamp(17px, 1.65vw, 24px); }
}
@media (max-width: 620px) {
  .presentation-metric-widget { min-height: 76px; padding: 10px 12px; }
  .presentation-metric-widget__icon { width: 28px; height: 28px; flex-basis: 28px; font-size: 19px; }
  .presentation-metric-widget__header h2 { font-size: 12px; }
  .presentation-metric-widget__value { font-size: clamp(16px, 5.2vw, 22px); }
  .presentation-metric-widgets--draft .presentation-metric-widget__icon { display: none; }
  .presentation-metric-widgets--draft .presentation-metric-widget--grouped { min-height: 112px; padding-right: 8px; padding-left: 8px; }
  .presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value-line--ring { grid-template-columns: minmax(0, 1fr); row-gap: 4px; }
  .presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value-line--ring :deep(.completion-ring-gauge) { justify-self: start; }
  .presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value-line--ring .presentation-metric-widget__comparisons,
  .presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value-line--ring .presentation-metric-widget__month-delta { grid-column: 1; }
  .presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value-line--ring .presentation-metric-widget__comparisons { grid-template-columns: 1fr; font-size: 11px; }
  .presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value { font-size: 18px; }
}
@container presentation-metric-widget (max-width: 300px) {
  .presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value-line--ring { grid-template-columns: minmax(0, 1fr); }
  .presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value-line--ring :deep(.completion-ring-gauge) { justify-self: start; }
  .presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value-line--ring .presentation-metric-widget__comparisons,
  .presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value-line--ring .presentation-metric-widget__month-delta { grid-column: 1; }
  .presentation-metric-widgets--draft .presentation-metric-widget--grouped .presentation-metric-widget__value-line--ring .presentation-metric-widget__comparisons { grid-template-columns: 1fr; }
}
</style>
