<template>
  <section v-if="components.length" class="presentation-series-table" data-testid="presentation-series-table">
    <article v-for="item in components" :key="item.componentId" class="presentation-series-table__panel" :data-component-id="item.componentId">
      <header v-if="item.componentType !== 'TREND' || item.state !== 'READY'"><div><h2>{{ item.title }}</h2><p v-if="item.subtitle">{{ item.subtitle }}</p></div><small v-if="item.state !== 'READY'">{{ item.issues.join('；') }}</small></header>
      <PanoramaTrend v-if="item.componentType === 'TREND' && item.state === 'READY'" :rows="item.rows" :series="item.series" :title="item.title" />
      <div v-else-if="item.componentType === 'TREND'" class="presentation-series-table__empty">暂无可用趋势</div>
      <div v-else class="presentation-series-table__table-wrap">
        <table><thead><tr><th v-for="column in item.columns" :key="column.columnKey">{{ column.label }}</th></tr></thead>
          <tbody><tr v-for="row in item.rows" :key="row.key"><td v-for="cell in row.cells" :key="cell.key">{{ cell.text }}</td></tr></tbody></table>
        <p v-if="!item.rows.length">暂无明细数据</p>
      </div>
    </article>
  </section>
</template>

<script setup>
import PanoramaTrend from '../../panorama/PanoramaTrend.vue';
defineProps({ components: { type: Array, default: () => [] } });
</script>

<style scoped>
.presentation-series-table { display: block; width: 100%; height: 100%; min-width: 0; margin: 0; }
.presentation-series-table__panel { display: flex; min-width: 0; min-height: 0; height: 100%; flex-direction: column; overflow: hidden; border: 1px solid var(--panorama-border, rgba(119, 163, 255, .3)); border-radius: 8px; background: var(--panorama-panel-deep, rgba(4, 14, 39, .9)); color: var(--panorama-text, #eaf2ff); box-shadow: inset 0 1px 0 rgba(201, 231, 255, .05), 0 8px 22px rgba(0, 0, 0, .12); }
.presentation-series-table__panel > header { display: flex; min-height: 44px; align-items: center; justify-content: space-between; gap: 10px; padding: 8px 14px; border-bottom: 1px solid var(--panorama-border-soft, rgba(119, 163, 255, .16)); }
.presentation-series-table h2,.presentation-series-table p { margin: 0; }
.presentation-series-table h2 { color: var(--panorama-text, #eaf2ff); font-size: 15px; font-weight: 650; }
.presentation-series-table header p,.presentation-series-table header small { color: var(--panorama-text-dim, #8fa9db); font-size: 10px; }
.presentation-series-table__panel > :deep(.panorama-trend) { min-height: 0; flex: 1 1 auto; }
.presentation-series-table__panel > :deep(.panorama-trend) .panorama-panel-heading { min-height: 40px; padding: 7px 14px; }
.presentation-series-table__panel > :deep(.panorama-trend) .panorama-panel-heading h2 { color: var(--panorama-text, #eaf2ff); font-size: 15px; }
.presentation-series-table__panel > :deep(.panorama-trend-chart) { min-height: 170px; }
.presentation-series-table__table-wrap { min-height: 0; flex: 1 1 auto; overflow: auto; padding: 0 14px 10px; }
.presentation-series-table table { width: 100%; border-collapse: collapse; table-layout: fixed; }
.presentation-series-table th,.presentation-series-table td { padding: 9px 8px; overflow: hidden; border-bottom: 1px solid var(--panorama-border-soft, rgba(119, 163, 255, .16)); text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.presentation-series-table th { position: sticky; top: 0; z-index: 1; color: var(--panorama-text-dim, #8fa9db); background: #0b2454; font-size: 11px; font-weight: 550; }
.presentation-series-table td { color: #dce9ff; font-size: 12px; }
.presentation-series-table__table-wrap > p { padding: 24px 0; color: var(--panorama-text-dim, #8fa9db); text-align: center; }
.presentation-series-table__empty { display: grid; flex: 1; min-height: 120px; place-items: center; padding: 24px; color: var(--panorama-text-dim, #8fa9db); }
@media (max-width: 620px) {
  .presentation-series-table__panel > header { padding-right: 10px; padding-left: 10px; }
  .presentation-series-table h2 { font-size: 13px; }
  .presentation-series-table__table-wrap { padding-right: 8px; padding-left: 8px; }
}
</style>
