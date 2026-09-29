<template>
  <section
    class="business-growth-widget"
    data-testid="business-growth-widget"
    aria-label="业务增长曲线"
  >
    <header class="business-growth-widget__heading">
      <h2>{{ growth.title }}</h2>
      <small>单位：{{ amountUnitLabel }}</small>
    </header>

    <div class="business-growth-widget__periods" role="tablist" aria-label="业务增长时间范围">
      <button
        v-for="period in periods"
        :id="tabId(period.key)"
        :key="period.key"
        type="button"
        role="tab"
        :data-period="period.key"
        :aria-selected="activePeriod === period.key"
        :aria-controls="panelId"
        :tabindex="activePeriod === period.key ? 0 : -1"
        @click="selectPeriod(period.key)"
        @keydown="handleTabKeydown($event, period.key)"
      >{{ period.label }}</button>
    </div>

    <div
      :id="panelId"
      class="business-growth-widget__charts"
      role="tabpanel"
      :aria-labelledby="tabId(activePeriod)"
      tabindex="0"
    >
      <article
        v-for="group in growth.groups"
        :key="group.businessLine"
        class="business-growth-widget__chart"
        :data-business-line="group.businessLine"
        :aria-label="group.title"
      >
        <h3>{{ group.title }}</h3>
        <PanoramaTrend
          v-if="group.status === 'READY'"
          :rows="group.rows"
          :series="group.series"
          :title="group.title"
          :compact="true"
          :amount-friendly="true"
          :amount-unit="normalizedAmountUnit"
          :variant="stackedVariant"
          :stacked-palette="group.businessLine === 'RETAIL' ? 'WARM' : 'COOL'"
        />
        <div v-else class="business-growth-widget__empty" data-testid="business-growth-empty" role="status">
          {{ group.emptyMessage }}
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import { computed, getCurrentInstance, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';

import PanoramaTrend from '../../panorama/PanoramaTrend.vue';
import { buildBusinessGrowthModel } from '../model/businessGrowthModel.js';
import {
  BUSINESS_GROWTH_PERIODS,
  DEFAULT_BUSINESS_GROWTH_PERIOD,
  buildBusinessGrowthPeriodModel,
  normalizeBusinessGrowthPeriod
} from '../model/businessGrowthPeriods.js';
import { canonicalUnit, unitLabel } from '../model/displayMetricsModel.js';

const AMOUNT_UNITS = new Set(['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION']);

const props = defineProps({
  presentation: { type: Object, default: () => ({}) },
  model: { type: Object, default: () => ({}) },
  amountUnit: { type: String, default: '' },
  stackedVariant: { type: String, default: 'STACKED_GRADIENT' }
});

const periods = BUSINESS_GROWTH_PERIODS;
const activePeriod = ref(DEFAULT_BUSINESS_GROWTH_PERIOD);
const widgetUid = getCurrentInstance()?.uid ?? 'standalone';
const panelId = `business-growth-period-panel-${widgetUid}`;
let rotationTimer = null;

const baseGrowth = computed(() => buildBusinessGrowthModel(props.presentation, props.model));
const modelAnchor = computed(() => String(
  props.model?.dataDate
    ?? props.model?.data_date
    ?? props.model?.sourceQuality?.dataDate
    ?? props.model?.quality?.dataDate
    ?? props.model?.batch?.dataDate
    ?? ''
).trim());
const growth = computed(() => buildBusinessGrowthPeriodModel(baseGrowth.value, activePeriod.value, {
  dataDate: modelAnchor.value
}));

function tabId(period) {
  return `business-growth-tab-${widgetUid}-${String(period).toLowerCase()}`;
}

function clearRotation() {
  if (rotationTimer !== null) {
    const browserClear = typeof window !== 'undefined' ? window.clearInterval : null;
    const clear = typeof browserClear === 'function'
      ? browserClear.bind(window)
      : (typeof globalThis.clearInterval === 'function' ? globalThis.clearInterval : null);
    if (typeof clear === 'function') clear(rotationTimer);
    rotationTimer = null;
  }
}

function startRotation() {
  clearRotation();
  const browserSet = typeof window !== 'undefined' ? window.setInterval : null;
  const set = typeof browserSet === 'function'
    ? browserSet.bind(window)
    : (typeof globalThis.setInterval === 'function' ? globalThis.setInterval : null);
  if (typeof set !== 'function') return;
  rotationTimer = set(() => {
    const index = periods.findIndex(item => item.key === activePeriod.value);
    activePeriod.value = periods[(index + 1 + periods.length) % periods.length]?.key
      || DEFAULT_BUSINESS_GROWTH_PERIOD;
  }, 10000);
}

function selectPeriod(period) {
  const next = normalizeBusinessGrowthPeriod(period);
  if (next === activePeriod.value) {
    // A deliberate click on the already visible tab is still a user interaction:
    // restart the interval so the next automatic change is ten seconds away.
    startRotation();
    return;
  }
  activePeriod.value = next;
  startRotation();
}

function focusPeriod(period) {
  if (typeof document === 'undefined') return;
  nextTick(() => document.getElementById(tabId(period))?.focus());
}

function handleTabKeydown(event, period) {
  const index = periods.findIndex(item => item.key === period);
  if (index < 0) return;
  let targetIndex = index;
  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') targetIndex = (index + 1) % periods.length;
  else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') targetIndex = (index - 1 + periods.length) % periods.length;
  else if (event.key === 'Home') targetIndex = 0;
  else if (event.key === 'End') targetIndex = periods.length - 1;
  else return;

  event.preventDefault();
  const target = periods[targetIndex]?.key || DEFAULT_BUSINESS_GROWTH_PERIOD;
  selectPeriod(target);
  focusPeriod(target);
}

watch([() => props.presentation, () => props.model, modelAnchor], () => {
  activePeriod.value = DEFAULT_BUSINESS_GROWTH_PERIOD;
  startRotation();
});

onMounted(startRotation);
onUnmounted(clearRotation);

const normalizedAmountUnit = computed(() => {
  const unit = canonicalUnit(props.amountUnit);
  return AMOUNT_UNITS.has(unit) ? unit : 'YUAN';
});
const amountUnitLabel = computed(() => unitLabel(normalizedAmountUnit.value));
</script>

<style scoped>
.business-growth-widget {
  display: flex;
  min-width: 0;
  min-height: 0;
  height: 100%;
  flex-direction: column;
  padding: 8px;
  border: 1px solid var(--panorama-border-soft, rgba(119, 163, 255, .16));
  border-radius: 8px;
  background: var(--panorama-panel-deep, rgba(4, 14, 39, .9));
  color: var(--panorama-text, #eaf2ff);
  box-sizing: border-box;
}

.business-growth-widget__heading {
  display: flex;
  min-height: 24px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 2px 5px;
}

.business-growth-widget__heading::before {
  width: 3px;
  height: 17px;
  flex: 0 0 auto;
  margin-right: 7px;
  border-radius: 2px;
  background: var(--panorama-cyan, #4de8ef);
  box-shadow: 0 0 9px var(--panorama-cyan, #4de8ef);
  content: '';
}

.business-growth-widget__heading h2,
.business-growth-widget__chart h3 {
  margin: 0;
  color: var(--panorama-text, #eaf2ff);
  font-size: 15px;
  font-weight: 650;
  line-height: 1.2;
}

.business-growth-widget__heading h2 {
  flex: 1 1 auto;
  text-align: left;
}

.business-growth-widget__heading small {
  color: var(--panorama-text-dim, #8fa9db);
  font-size: 10px;
}

.business-growth-widget__periods {
  display: flex;
  min-height: 24px;
  align-items: center;
  gap: 4px;
  padding: 0 2px 6px;
}

.business-growth-widget__periods button {
  min-width: 52px;
  height: 22px;
  padding: 0 8px;
  border: 1px solid rgba(119, 163, 255, .24);
  border-radius: 4px;
  background: rgba(8, 27, 66, .78);
  color: var(--panorama-text-dim, #8fa9db);
  cursor: pointer;
  font: inherit;
  font-size: 10px;
  line-height: 20px;
}

.business-growth-widget__periods button[aria-selected='true'] {
  border-color: rgba(77, 232, 239, .72);
  background: rgba(36, 155, 184, .22);
  color: var(--panorama-text, #eaf2ff);
}

.business-growth-widget__periods button:focus-visible {
  outline: 2px solid var(--panorama-cyan, #4de8ef);
  outline-offset: 1px;
}

.business-growth-widget__charts {
  display: grid;
  min-width: 0;
  min-height: 0;
  flex: 1 1 auto;
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.business-growth-widget__chart {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  box-sizing: border-box;
  padding: 6px 8px 4px;
  min-height: 200px;
  border: 1px solid var(--panorama-border-soft, rgba(119, 163, 255, .16));
  border-radius: 6px;
  background: rgba(7, 22, 56, .68);
}

.business-growth-widget__chart > :deep(.panorama-trend) {
  min-height: 0;
  flex: 1 1 auto;
}

/* 标题由业务线卡片统一展示，给两张图留下同样的可读高度。 */
.business-growth-widget__chart > :deep(.panorama-trend .panorama-panel-heading) {
  display: none;
}

.business-growth-widget__chart :deep(.panorama-trend.is-compact .panorama-trend-chart) {
  min-height: 90px;
}

.business-growth-widget__empty {
  display: grid;
  min-height: 0;
  flex: 1 1 auto;
  place-items: center;
  padding: 8px;
  color: var(--panorama-text-dim, #8fa9db);
  font-size: 11px;
  text-align: center;
}
</style>
