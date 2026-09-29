<template>
  <section
    class="overview-balance-chart"
    data-testid="overview-balance-chart"
    :data-state="chart.state"
    :data-unit="chart.unit"
    aria-label="余额基期与增长量对比"
  >
    <div
      class="overview-balance-chart__bars"
      data-testid="overview-balance-bars"
      role="img"
      :aria-label="chart.bars.map(barAriaLabel).join('；')"
    >
      <div
        v-for="bar in chart.bars"
        :key="bar.key"
        class="overview-balance-chart__bar"
        :data-bar-key="bar.key"
        :data-state="bar.state"
      >
        <div class="overview-balance-chart__bar-label">
          <span>{{ bar.label }}</span>
          <strong data-testid="overview-balance-base">{{ bar.text }}</strong>
        </div>
        <div class="overview-balance-chart__track" aria-hidden="true">
          <i v-if="bar.state === 'READY' && bar.percent > 0" data-testid="overview-balance-fill" :style="{ width: `${bar.percent}%` }" />
        </div>
        <strong class="overview-balance-chart__growth" data-testid="overview-balance-growth">{{ bar.growthText }}</strong>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import { buildOverviewBalanceChartModel } from './overviewBalanceChartModel';

const props = defineProps({
  metric: { type: Object, default: null },
  displayUnit: { type: String, default: '' }
});

const chart = computed(() => buildOverviewBalanceChartModel(props.metric, props.displayUnit));

function barAriaLabel(bar) {
  return `${bar.label} ${bar.text} ${bar.growthText}`;
}
</script>

<style scoped>
.overview-balance-chart {
  display: grid;
  container-type: inline-size;
  container-name: overview-balance-chart;
  min-width: 0;
  gap: 4px;
  color: var(--panorama-text, #eaf2ff);
  font-family: inherit;
  font-size: var(--screen-font-body, var(--screen-font-size-body, 12px));
  font-variant-numeric: tabular-nums;
}
.overview-balance-chart__bars { display: grid; min-width: 0; gap: 3px; }
.overview-balance-chart__bar { display: grid; min-width: 0; grid-template-columns: minmax(52px, .36fr) minmax(50px, 1fr) minmax(92px, auto); align-items: center; gap: 6px; }
.overview-balance-chart__bar-label { display: grid; min-width: 0; gap: 1px; color: var(--panorama-text-dim, #8fa9db); font-size: var(--screen-font-label, var(--screen-font-size-label, 14px)); line-height: 1.1; }
.overview-balance-chart__bar-label span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.overview-balance-chart__bar-label strong,
.overview-balance-chart__growth { min-width: 0; overflow: hidden; color: var(--panorama-text, #eaf2ff); font-size: var(--screen-font-label, var(--screen-font-size-label, 14px)); font-weight: 700; line-height: 1.1; overflow-wrap: anywhere; word-break: break-word; text-align: right; }
.overview-balance-chart__growth { color: var(--panorama-cyan, #4de8ef); }
.overview-balance-chart__track { position: relative; display: block; min-width: 0; height: 7px; overflow: hidden; border: 1px solid rgba(119, 163, 255, .2); border-radius: 5px; background: rgba(116, 151, 211, .16); }
.overview-balance-chart__track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--panorama-cyan, #4de8ef), var(--panorama-blue, #5896ff) 52%, var(--panorama-violet, #a979ff)); box-shadow: 0 0 8px rgba(77, 232, 239, .28); }
.overview-balance-chart__bar[data-state='PENDING'] .overview-balance-chart__track { border-style: dashed; background: rgba(116, 151, 211, .08); }
.overview-balance-chart__bar[data-state='PENDING'] .overview-balance-chart__bar-label strong,
.overview-balance-chart__bar[data-state='PENDING'] .overview-balance-chart__growth { color: var(--panorama-text-dim, #8fa9db); font-size: var(--screen-font-caption, var(--screen-font-size-caption, 11px)); font-weight: 550; }
@media (max-width: 620px) {
  .overview-balance-chart__bar { grid-template-columns: minmax(46px, .3fr) minmax(42px, 1fr) minmax(74px, auto); gap: 4px; }
  .overview-balance-chart__bar-label strong,
  .overview-balance-chart__growth { font-size: var(--screen-font-caption, var(--screen-font-size-caption, 11px)); }
}
@container overview-balance-chart (max-width: 360px) {
  .overview-balance-chart__bar { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); grid-template-areas: 'label value' 'track track'; gap: 3px 6px; }
  .overview-balance-chart__bar-label { display: flex; grid-area: label; align-items: baseline; flex-wrap: wrap; gap: 5px; }
  .overview-balance-chart__bar-label span { flex: 0 0 auto; }
  .overview-balance-chart__bar-label strong { min-width: 0; max-width: 100%; flex: 0 0 auto; overflow: visible; text-overflow: clip; white-space: nowrap; }
  .overview-balance-chart__track { grid-area: track; }
  .overview-balance-chart__growth { grid-area: value; max-width: 100%; overflow: visible; text-overflow: clip; white-space: normal; }
}
@container overview-balance-chart (max-width: 280px) {
  .overview-balance-chart__bar {
    grid-template-columns: minmax(0, 1fr) minmax(0, auto);
    grid-template-areas: 'label value' 'base base' 'track track';
    gap: 3px 6px;
  }
  .overview-balance-chart__bar-label { display: contents; }
  .overview-balance-chart__bar-label span { grid-area: label; }
  .overview-balance-chart__bar-label strong {
    grid-area: base;
    justify-self: start;
    max-width: 100%;
    overflow: visible;
    text-align: left;
    text-overflow: clip;
    white-space: nowrap;
    overflow-wrap: normal;
    word-break: normal;
  }
  .overview-balance-chart__growth { grid-area: value; }
  .overview-balance-chart__track { grid-area: track; }
}
</style>
