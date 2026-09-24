<template>
  <section v-if="components.length" class="presentation-series-table" :class="{ 'presentation-series-table--tabbed': tabbedTrendMode }" data-testid="presentation-series-table">
    <div v-if="tabbedTrendMode" ref="trendTabsRef" class="presentation-series-table__tabs" role="tablist" aria-label="趋势图页签">
      <button
        v-for="item in trendComponents"
        :key="item.componentId"
        type="button"
        role="tab"
        data-trend-tab
        :id="`presentation-trend-tab-${item.componentId}`"
        :aria-selected="item.componentId === activeTrendId ? 'true' : 'false'"
        :aria-controls="`presentation-trend-panel-${item.componentId}`"
        :tabindex="item.componentId === activeTrendId ? 0 : -1"
        @click="activeTrendId = item.componentId"
        @keydown="onTrendTabKeydown($event, trendComponents.findIndex(trend => trend.componentId === item.componentId))"
      >{{ item.title }}</button>
    </div>
    <template v-for="item in components" :key="item.componentId">
      <article
        v-if="!tabbedTrendMode || item.componentType !== 'TREND' || item.componentId === activeTrendId"
        class="presentation-series-table__panel"
        :data-component-id="item.componentId"
        :id="tabbedTrendMode && item.componentType === 'TREND' ? `presentation-trend-panel-${item.componentId}` : undefined"
        :role="tabbedTrendMode && item.componentType === 'TREND' ? 'tabpanel' : undefined"
        :tabindex="tabbedTrendMode && item.componentType === 'TREND' ? 0 : undefined"
        :aria-labelledby="tabbedTrendMode && item.componentType === 'TREND' ? `presentation-trend-tab-${item.componentId}` : undefined"
      >
      <header v-if="item.componentType !== 'TREND' || item.state !== 'READY'"><div><h2>{{ item.title }}</h2><p v-if="item.subtitle">{{ item.subtitle }}</p></div><small v-if="item.state !== 'READY'">{{ item.issues.join('；') }}</small></header>
      <PanoramaTrend v-if="item.componentType === 'TREND' && item.state === 'READY'" :rows="item.rows" :series="item.series" :title="item.title" :amount-friendly="trendDisplayMode === 'branch'" />
      <div v-else-if="item.componentType === 'TREND'" class="presentation-series-table__empty">暂无可用趋势</div>
      <div v-else class="presentation-series-table__table-wrap">
        <table><thead><tr><th v-for="column in item.columns" :key="column.columnKey">{{ column.label }}</th></tr></thead>
          <tbody><tr v-for="row in item.rows" :key="row.key"><td v-for="cell in row.cells" :key="cell.key">{{ cell.text }}</td></tr></tbody></table>
        <p v-if="!item.rows.length">暂无明细数据</p>
      </div>
      </article>
    </template>
  </section>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue';
import PanoramaTrend from '../../panorama/PanoramaTrend.vue';

const props = defineProps({
  components: { type: Array, default: () => [] },
  tabbed: { type: Boolean, default: false },
  trendDisplayMode: { type: String, default: 'default' }
});

const trendComponents = computed(() => props.components.filter(item => item?.componentType === 'TREND'));
const tabbedTrendMode = computed(() => props.tabbed && trendComponents.value.length > 1);
const activeTrendId = ref('');
const trendTabsRef = ref(null);

watch(trendComponents, items => {
  if (!items.some(item => item.componentId === activeTrendId.value)) activeTrendId.value = items[0]?.componentId || '';
}, { immediate: true });

function onTrendTabKeydown(event, index) {
  if (!trendComponents.value.length) return;
  let nextIndex = index;
  if (event.key === 'ArrowRight') nextIndex = (index + 1) % trendComponents.value.length;
  else if (event.key === 'ArrowLeft') nextIndex = (index - 1 + trendComponents.value.length) % trendComponents.value.length;
  else if (event.key === 'Home') nextIndex = 0;
  else if (event.key === 'End') nextIndex = trendComponents.value.length - 1;
  else return;
  event.preventDefault();
  activeTrendId.value = trendComponents.value[nextIndex].componentId;
  nextTick(() => {
    trendTabsRef.value?.querySelector('[data-trend-tab][aria-selected="true"]')?.focus();
  });
}
</script>

<style scoped>
.presentation-series-table { display: block; width: 100%; height: 100%; min-width: 0; margin: 0; }
.presentation-series-table--tabbed { display: flex; flex-direction: column; }
.presentation-series-table__tabs { display: flex; min-width: 0; flex: 0 0 auto; gap: 6px; padding: 6px 10px 0; overflow-x: auto; }
.presentation-series-table__tabs button { flex: 0 0 auto; padding: 5px 10px; border: 1px solid var(--panorama-border, rgba(119, 163, 255, .3)); border-radius: 4px; color: var(--panorama-text-dim, #8fa9db); background: rgba(25, 67, 121, .42); cursor: pointer; font: inherit; font-size: 11px; }
.presentation-series-table__tabs button[aria-selected="true"] { color: #071a31; border-color: var(--panorama-cyan, #4de8ef); background: var(--panorama-cyan, #4de8ef); }
.presentation-series-table__tabs button:focus-visible { outline: 2px solid var(--panorama-cyan, #4de8ef); outline-offset: 2px; }
.presentation-series-table__panel { display: flex; min-width: 0; min-height: 0; height: 100%; flex-direction: column; overflow: hidden; border: 1px solid var(--panorama-border, rgba(119, 163, 255, .3)); border-radius: 8px; background: var(--panorama-panel-deep, rgba(4, 14, 39, .9)); color: var(--panorama-text, #eaf2ff); box-shadow: inset 0 1px 0 rgba(201, 231, 255, .05), 0 8px 22px rgba(0, 0, 0, .12); }
.presentation-series-table--tabbed > .presentation-series-table__panel { height: auto; flex: 1 1 auto; }
.presentation-series-table--tabbed .presentation-series-table__panel > :deep(.panorama-trend) .panorama-panel-heading { display: none; }
.presentation-series-table--tabbed .presentation-series-table__panel > :deep(.panorama-trend) { min-height: 0; flex: 1 1 auto; }
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
