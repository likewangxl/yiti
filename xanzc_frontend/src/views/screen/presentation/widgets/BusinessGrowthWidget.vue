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

    <div class="business-growth-widget__charts">
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
        />
        <div v-else class="business-growth-widget__empty" data-testid="business-growth-empty" role="status">
          {{ group.emptyMessage }}
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue';

import PanoramaTrend from '../../panorama/PanoramaTrend.vue';
import { buildBusinessGrowthModel } from '../model/businessGrowthModel.js';
import { canonicalUnit, unitLabel } from '../model/displayMetricsModel.js';

const AMOUNT_UNITS = new Set(['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION']);

const props = defineProps({
  presentation: { type: Object, default: () => ({}) },
  model: { type: Object, default: () => ({}) },
  amountUnit: { type: String, default: '' }
});

const growth = computed(() => buildBusinessGrowthModel(props.presentation, props.model));
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
