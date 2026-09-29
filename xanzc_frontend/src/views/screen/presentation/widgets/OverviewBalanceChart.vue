<template>
  <section
    class="overview-balance-chart"
    data-testid="overview-balance-chart"
    :data-state="chart.state"
    :data-unit="chart.unit"
    :aria-label="`余额对比，${chart.currentDate ? `当前数据日 ${chart.currentDate}` : '待接入'}`"
  >
    <div class="overview-balance-chart__header">
      <span>余额对比</span>
      <small>{{ chart.currentDate || '数据日待接入' }}</small>
    </div>
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
          <small>{{ bar.referenceDate || '待接入' }}</small>
        </div>
        <div class="overview-balance-chart__track" aria-hidden="true">
          <i v-if="bar.state === 'READY' && bar.percent > 0" data-testid="overview-balance-fill" :style="{ width: `${bar.percent}%` }" />
        </div>
        <strong>{{ bar.text }}</strong>
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
  return `${bar.label} ${bar.referenceDate || '日期待接入'} ${bar.text}`;
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
.overview-balance-chart__header { display: flex; min-width: 0; align-items: baseline; justify-content: space-between; gap: 8px; color: var(--panorama-text-dim, #8fa9db); }
.overview-balance-chart__header span { min-width: 0; font-size: var(--screen-font-label, var(--screen-font-size-label, 14px)); font-weight: 600; }
.overview-balance-chart__header small { min-width: 0; overflow: hidden; color: var(--panorama-text-dim, #8fa9db); font-size: var(--screen-font-caption, var(--screen-font-size-caption, 11px)); text-overflow: ellipsis; white-space: nowrap; }
.overview-balance-chart__bars { display: grid; min-width: 0; gap: 3px; }
.overview-balance-chart__bar { display: grid; min-width: 0; grid-template-columns: minmax(52px, .36fr) minmax(50px, 1fr) minmax(92px, auto); align-items: center; gap: 6px; }
.overview-balance-chart__bar-label { display: grid; min-width: 0; gap: 1px; color: var(--panorama-text-dim, #8fa9db); font-size: var(--screen-font-label, var(--screen-font-size-label, 14px)); line-height: 1.1; }
.overview-balance-chart__bar-label span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.overview-balance-chart__bar-label small { overflow: hidden; color: color-mix(in srgb, var(--panorama-text-dim, #8fa9db) 78%, transparent); font-size: var(--screen-font-caption, var(--screen-font-size-caption, 11px)); text-overflow: ellipsis; white-space: nowrap; }
.overview-balance-chart__track { position: relative; display: block; min-width: 0; height: 7px; overflow: hidden; border: 1px solid rgba(119, 163, 255, .2); border-radius: 5px; background: rgba(116, 151, 211, .16); }
.overview-balance-chart__track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--panorama-cyan, #4de8ef), var(--panorama-blue, #5896ff) 52%, var(--panorama-violet, #a979ff)); box-shadow: 0 0 8px rgba(77, 232, 239, .28); }
.overview-balance-chart__bar strong { min-width: 0; overflow: hidden; color: var(--panorama-text, #eaf2ff); font-size: var(--screen-font-label, var(--screen-font-size-label, 14px)); font-weight: 700; line-height: 1.1; overflow-wrap: anywhere; word-break: break-word; text-align: right; }
.overview-balance-chart__bar[data-state='PENDING'] .overview-balance-chart__track { border-style: dashed; background: rgba(116, 151, 211, .08); }
.overview-balance-chart__bar[data-state='PENDING'] strong { color: var(--panorama-text-dim, #8fa9db); font-size: var(--screen-font-caption, var(--screen-font-size-caption, 11px)); font-weight: 550; }
@media (max-width: 620px) {
  .overview-balance-chart__bar { grid-template-columns: minmax(46px, .3fr) minmax(42px, 1fr) minmax(74px, auto); gap: 4px; }
  .overview-balance-chart__bar strong { font-size: var(--screen-font-caption, var(--screen-font-size-caption, 11px)); }
}
@container overview-balance-chart (max-width: 360px) {
  .overview-balance-chart__bar { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); grid-template-areas: 'label value' 'track track'; gap: 3px 6px; }
  .overview-balance-chart__bar-label { display: flex; grid-area: label; align-items: baseline; gap: 5px; }
  .overview-balance-chart__bar-label span { flex: 0 0 auto; }
  .overview-balance-chart__bar-label small { min-width: 0; overflow: visible; text-overflow: clip; white-space: nowrap; }
  .overview-balance-chart__track { grid-area: track; }
  .overview-balance-chart__bar strong { grid-area: value; max-width: 100%; overflow: visible; text-overflow: clip; white-space: normal; }
}
</style>
