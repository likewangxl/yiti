<template>
  <section
    class="institution-ranking-widget"
    data-testid="institution-ranking-widget"
    :data-pagination-mode="paginate ? 'pages' : 'scroll'"
    :aria-label="title || '机构排名'"
    @mouseenter="hoverPaused = true"
    @mouseleave="hoverPaused = false"
    @focusin="focusPaused = true"
    @focusout="handleFocusOut"
  >
    <header class="institution-ranking-widget__header">
      <div>
        <span class="institution-ranking-widget__kicker">{{ title || '机构经营排名' }}</span>
        <h2>{{ activeMetric?.label || '机构排名' }}</h2>
      </div>
      <div class="institution-ranking-widget__meta">
        <span data-testid="institution-ranking-coverage">{{ activeMetric?.receivedCount || 0 }}/{{ activeMetric?.expectedCount || 0 }} 家</span>
        <button
          type="button"
          data-action="toggle-ranking-carousel"
          :disabled="!canToggleCarousel"
          :aria-pressed="userPaused"
          @click="toggleUserPause"
        >{{ userPaused ? '继续轮播' : '暂停轮播' }}</button>
      </div>
    </header>

    <div v-if="metricOptions.length" class="institution-ranking-widget__metrics" role="tablist" aria-label="排名指标">
      <button
        v-for="metric in metricOptions"
        :key="metric.metricKey"
        type="button"
        role="tab"
        :data-ranking-metric="metric.metricKey"
        :aria-selected="metric.metricKey === activeMetricKey ? 'true' : 'false'"
        :tabindex="metric.metricKey === activeMetricKey ? 0 : -1"
        @click="selectMetric(metric.metricKey)"
      >{{ metric.label }}</button>
    </div>

    <p v-if="!paginate && activeMetric?.incomplete" class="institution-ranking-widget__incomplete" data-testid="institution-ranking-incomplete" role="status">
      {{ activeMetric.summary || '机构数据不完整，未获得机构不会宣称已全部展示' }}
    </p>
    <p v-else-if="!paginate && activeMetric" class="institution-ranking-widget__complete" data-testid="institution-ranking-complete" role="status">
      {{ activeMetric.summary }}
    </p>

    <div v-if="paginate" class="institution-ranking-widget__pagination" role="group" aria-label="机构排名分页">
      <button
        type="button"
        data-action="ranking-page-previous"
        aria-label="上一页机构排名"
        :disabled="pageCount < 2"
        @click="movePage(-1)"
      >上一页</button>
      <span data-testid="institution-ranking-page" aria-live="polite">第 {{ pageIndex + 1 }}/{{ pageCount }} 页</span>
      <span data-testid="institution-ranking-range">{{ pageRangeStart }}–{{ pageRangeEnd }}/{{ totalRowCount }}</span>
      <button
        type="button"
        data-action="ranking-page-next"
        aria-label="下一页机构排名"
        :disabled="pageCount < 2"
        @click="movePage(1)"
      >下一页</button>
    </div>

    <div class="institution-ranking-widget__list" data-testid="institution-ranking-list" tabindex="0" role="region" :aria-label="paginate ? '当前页机构排名列表' : '完整机构排名列表'">
      <table>
        <caption class="institution-ranking-widget__visually-hidden">{{ activeMetric?.label || '机构排名' }}，含未参与排名机构</caption>
        <thead><tr><th scope="col">排名</th><th scope="col">机构</th><th scope="col">{{ displayUnitLabel(activeMetric?.unit) || '指标值' }}</th></tr></thead>
        <tbody>
          <tr
            v-for="row in displayRows"
            :key="`${activeMetricKey}-${row.orgCode}`"
            data-testid="institution-ranking-row"
            :data-org-code="row.orgCode"
            :data-state="row.state"
            tabindex="0"
          >
            <td>{{ row.rank ?? '—' }}</td>
            <td>{{ row.name || row.orgName || row.orgCode || '未命名机构' }}</td>
            <td>{{ row.state === 'MISSING' ? '未参与' : formatValue(row.value) }}</td>
          </tr>
          <tr v-if="!displayRows.length" data-testid="institution-ranking-empty"><td colspan="3">暂无授权机构数据</td></tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { displayUnitLabel } from '../model/displayMetricsModel';

const props = defineProps({
  model: { type: Object, default: () => ({}) },
  title: { type: String, default: '' },
  interval: { type: Number, default: 10000 },
  paginate: { type: Boolean, default: false },
  pageSize: { type: Number, default: 10 },
  pageInterval: { type: Number, default: 5000 },
  metricCarousel: { type: Boolean, default: true }
});
const emit = defineEmits(['metric-change']);

const activeMetricKey = ref('');
const userPaused = ref(false);
const hoverPaused = ref(false);
const focusPaused = ref(false);
const pageHidden = ref(false);
const pageIndex = ref(0);
let metricTimer = null;
let pageTimer = null;
let mounted = false;

const metricOptions = computed(() => Array.isArray(props.model?.metrics) ? props.model.metrics.filter(item => item && item.metricKey) : []);
const activeMetric = computed(() => metricOptions.value.find(item => item.metricKey === activeMetricKey.value) || metricOptions.value[0] || props.model?.metric || null);
const interactionPaused = computed(() => hoverPaused.value || focusPaused.value);
const allRows = computed(() => [
  ...(activeMetric.value?.rankable || []),
  ...(activeMetric.value?.missing || [])
]);
const normalizedPageSize = computed(() => {
  const number = Number(props.pageSize);
  if (!Number.isFinite(number) || number <= 0) return 10;
  return Math.min(10, Math.floor(number));
});
const totalRowCount = computed(() => allRows.value.length);
const pageCount = computed(() => Math.max(1, Math.ceil(totalRowCount.value / normalizedPageSize.value)));
const displayRows = computed(() => props.paginate
  ? allRows.value.slice(pageIndex.value * normalizedPageSize.value, (pageIndex.value + 1) * normalizedPageSize.value)
  : allRows.value);
const pageRangeStart = computed(() => totalRowCount.value ? pageIndex.value * normalizedPageSize.value + 1 : 0);
const pageRangeEnd = computed(() => totalRowCount.value ? Math.min((pageIndex.value + 1) * normalizedPageSize.value, totalRowCount.value) : 0);
const canToggleCarousel = computed(() => props.paginate ? pageCount.value > 1 : props.metricCarousel && metricOptions.value.length > 1);

function normalizeInterval(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : 10000;
}

function normalizePageInterval(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : 5000;
}

function selectMetric(metricKey) {
  if (!metricOptions.value.some(item => item.metricKey === metricKey)) return;
  activeMetricKey.value = metricKey;
  pageIndex.value = 0;
  if (!props.paginate) userPaused.value = true;
  emit('metric-change', { metricKey });
  syncCarousel();
}

function advanceMetric() {
  if (userPaused.value || interactionPaused.value || pageHidden.value || metricOptions.value.length < 2) return;
  const index = metricOptions.value.findIndex(item => item.metricKey === activeMetricKey.value);
  const metricKey = metricOptions.value[(index + 1) % metricOptions.value.length].metricKey;
  activeMetricKey.value = metricKey;
  emit('metric-change', { metricKey });
}

function advancePage() {
  if (userPaused.value || interactionPaused.value || pageHidden.value || pageCount.value < 2) return;
  pageIndex.value = (pageIndex.value + 1) % pageCount.value;
}

function movePage(offset) {
  if (!props.paginate || pageCount.value < 2) return;
  pageIndex.value = (pageIndex.value + offset + pageCount.value) % pageCount.value;
  userPaused.value = true;
  syncCarousel();
}

function stopMetricCarousel() {
  if (metricTimer) window.clearInterval(metricTimer);
  metricTimer = null;
}

function stopPageCarousel() {
  if (pageTimer) window.clearInterval(pageTimer);
  pageTimer = null;
}

function stopCarousel() {
  stopMetricCarousel();
  stopPageCarousel();
}

function syncCarousel() {
  stopCarousel();
  if (!mounted || userPaused.value || pageHidden.value) return;
  if (props.paginate) {
    if (pageCount.value > 1) pageTimer = window.setInterval(advancePage, normalizePageInterval(props.pageInterval));
    return;
  }
  if (props.metricCarousel && metricOptions.value.length > 1) {
    metricTimer = window.setInterval(advanceMetric, normalizeInterval(props.interval));
  }
}

function toggleUserPause() {
  if (!canToggleCarousel.value) return;
  userPaused.value = !userPaused.value;
  syncCarousel();
}

function handleFocusOut(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) focusPaused.value = false;
}

function handleVisibilityChange() {
  pageHidden.value = document.visibilityState === 'hidden';
  syncCarousel();
}

function formatValue(value) {
  if (value === null || value === undefined || value === '') return '—';
  const number = Number(value);
  if (!Number.isFinite(number)) return '—';
  return `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 2, minimumFractionDigits: Number.isInteger(number) ? 0 : 2 }).format(number)}${displayUnitLabel(activeMetric.value?.unit)}`;
}

watch(metricOptions, options => {
  if (!options.length) {
    activeMetricKey.value = '';
    pageIndex.value = 0;
    syncCarousel();
    return;
  }
  if (!options.some(item => item.metricKey === activeMetricKey.value)) {
    activeMetricKey.value = props.model?.activeMetricKey && options.some(item => item.metricKey === props.model.activeMetricKey)
      ? props.model.activeMetricKey : options[0].metricKey;
  }
  pageIndex.value = 0;
  syncCarousel();
}, { immediate: true });

watch(() => props.model?.metrics, () => {
  pageIndex.value = 0;
  syncCarousel();
}, { deep: true });

watch([pageCount, () => props.paginate], () => {
  pageIndex.value = Math.min(pageIndex.value, pageCount.value - 1);
  syncCarousel();
});

watch(() => props.model?.activeMetricKey, value => {
  if (!userPaused.value && metricOptions.value.some(item => item.metricKey === value)) {
    activeMetricKey.value = value;
    pageIndex.value = 0;
  }
});

onMounted(() => {
  mounted = true;
  pageHidden.value = document.visibilityState === 'hidden';
  document.addEventListener('visibilitychange', handleVisibilityChange);
  syncCarousel();
});
onBeforeUnmount(() => {
  mounted = false;
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  stopCarousel();
});
</script>

<style scoped>
.institution-ranking-widget { display: flex; min-width: 0; min-height: 0; height: 100%; flex-direction: column; color: var(--panorama-text, #eaf2ff); background: var(--panorama-panel-deep, rgba(4, 14, 39, .9)); border: 1px solid var(--panorama-border, rgba(119, 163, 255, .3)); border-radius: 8px; box-shadow: inset 0 1px 0 rgba(201, 231, 255, .05), 0 8px 22px rgba(0, 0, 0, .12); }
.institution-ranking-widget__header, .institution-ranking-widget__meta, .institution-ranking-widget__metrics { display: flex; align-items: center; }
.institution-ranking-widget__header { justify-content: space-between; gap: 12px; padding: 11px 14px; border-bottom: 1px solid var(--panorama-border-soft, rgba(119, 163, 255, .16)); }
.institution-ranking-widget__kicker { color: #72b9ed; font-size: 10px; letter-spacing: .08em; }
.institution-ranking-widget h2 { margin: 3px 0 0; color: var(--panorama-text, #eaf2ff); font-size: 16px; font-weight: 650; }
.institution-ranking-widget h2::before { display: inline-block; width: 3px; height: 16px; margin-right: 8px; border-radius: 1px; background: var(--panorama-violet, #a979ff); vertical-align: -2px; content: ''; }
.institution-ranking-widget__meta { flex: 0 0 auto; gap: 8px; color: var(--panorama-text-dim, #8fa9db); font-size: 10px; }
.institution-ranking-widget__meta button, .institution-ranking-widget__metrics button { color: #cfe5ff; background: rgba(25, 67, 121, .56); border: 1px solid rgba(127, 199, 255, .35); border-radius: 4px; cursor: pointer; font: inherit; }
.institution-ranking-widget__meta button { padding: 4px 7px; }
.institution-ranking-widget__meta button:disabled { cursor: default; opacity: .55; }
.institution-ranking-widget__metrics { flex-wrap: wrap; gap: 6px; padding: 9px 14px 0; }
.institution-ranking-widget__metrics button { padding: 4px 8px; font-size: 11px; }
.institution-ranking-widget__metrics button[aria-selected="true"] { color: #071a31; background: var(--panorama-cyan, #4de8ef); border-color: var(--panorama-cyan, #4de8ef); }
.institution-ranking-widget__meta button:focus-visible, .institution-ranking-widget__metrics button:focus-visible, .institution-ranking-widget__list:focus-visible, .institution-ranking-widget__list tr:focus-visible { outline: 2px solid var(--panorama-cyan, #4de8ef); outline-offset: 2px; }
.institution-ranking-widget__incomplete, .institution-ranking-widget__complete { margin: 8px 14px 0; color: var(--panorama-amber, #ffc45e); font-size: 10px; line-height: 1.35; }
.institution-ranking-widget__complete { color: #82dbd7; }
.institution-ranking-widget__pagination { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin: 8px 14px 0; color: var(--panorama-text-dim, #8fa9db); font-size: 10px; }
.institution-ranking-widget__pagination button { padding: 3px 7px; color: #cfe5ff; background: rgba(25, 67, 121, .56); border: 1px solid rgba(127, 199, 255, .35); border-radius: 4px; cursor: pointer; font: inherit; }
.institution-ranking-widget__pagination button:disabled { cursor: default; opacity: .5; }
.institution-ranking-widget__pagination button:focus-visible { outline: 2px solid var(--panorama-cyan, #4de8ef); outline-offset: 2px; }
.institution-ranking-widget__list { min-height: 0; margin: 8px 0 0; flex: 1 1 auto; overflow: auto; scrollbar-width: thin; }
.institution-ranking-widget table { width: 100%; border-collapse: collapse; table-layout: fixed; }
.institution-ranking-widget th, .institution-ranking-widget td { padding: 8px 12px; overflow: hidden; border-bottom: 1px solid var(--panorama-border-soft, rgba(119, 163, 255, .16)); text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.institution-ranking-widget th { color: var(--panorama-text-dim, #8fa9db); background: #0b2454; font-size: 10px; font-weight: 550; }
.institution-ranking-widget td { color: #dce9ff; font-size: 12px; }
.institution-ranking-widget th:first-child, .institution-ranking-widget td:first-child { width: 54px; }
.institution-ranking-widget th:last-child, .institution-ranking-widget td:last-child { width: 86px; text-align: right; }
.institution-ranking-widget td:first-child { color: #82dbd7; font-variant-numeric: tabular-nums; }
.institution-ranking-widget tr[data-state="MISSING"] td { color: #8ca0bd; }
.institution-ranking-widget__visually-hidden { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 620px) {
  .institution-ranking-widget__header { padding-right: 10px; padding-left: 10px; }
  .institution-ranking-widget__header h2 { font-size: 14px; }
  .institution-ranking-widget th, .institution-ranking-widget td { padding-right: 8px; padding-left: 8px; }
}
</style>
