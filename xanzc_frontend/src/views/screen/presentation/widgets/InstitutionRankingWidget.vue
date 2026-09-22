<template>
  <section
    class="institution-ranking-widget"
    data-testid="institution-ranking-widget"
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
          :disabled="metricOptions.length < 2"
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

    <p v-if="activeMetric?.incomplete" class="institution-ranking-widget__incomplete" data-testid="institution-ranking-incomplete" role="status">
      {{ activeMetric.summary || '机构数据不完整，未获得机构不会宣称已全部展示' }}
    </p>
    <p v-else-if="activeMetric" class="institution-ranking-widget__complete" data-testid="institution-ranking-complete" role="status">
      {{ activeMetric.summary }}
    </p>

    <div class="institution-ranking-widget__list" data-testid="institution-ranking-list" tabindex="0" role="region" aria-label="完整机构排名列表">
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
  interval: { type: Number, default: 10000 }
});
const emit = defineEmits(['metric-change']);

const activeMetricKey = ref('');
const userPaused = ref(false);
const hoverPaused = ref(false);
const focusPaused = ref(false);
const pageHidden = ref(false);
let carouselTimer = null;
let mounted = false;

const metricOptions = computed(() => Array.isArray(props.model?.metrics) ? props.model.metrics.filter(item => item && item.metricKey) : []);
const activeMetric = computed(() => metricOptions.value.find(item => item.metricKey === activeMetricKey.value) || metricOptions.value[0] || props.model?.metric || null);
const interactionPaused = computed(() => hoverPaused.value || focusPaused.value);
const displayRows = computed(() => [
  ...(activeMetric.value?.rankable || []),
  ...(activeMetric.value?.missing || [])
]);

function normalizeInterval(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : 10000;
}

function selectMetric(metricKey) {
  if (!metricOptions.value.some(item => item.metricKey === metricKey)) return;
  activeMetricKey.value = metricKey;
  userPaused.value = true;
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

function stopCarousel() {
  if (carouselTimer) window.clearInterval(carouselTimer);
  carouselTimer = null;
}

function startCarousel() {
  stopCarousel();
  if (!mounted || userPaused.value || pageHidden.value || metricOptions.value.length < 2) return;
  carouselTimer = window.setInterval(advanceMetric, normalizeInterval(props.interval));
}

function syncCarousel() {
  if (!mounted || userPaused.value || pageHidden.value || metricOptions.value.length < 2) {
    stopCarousel();
    return;
  }
  if (!carouselTimer) startCarousel();
}

function toggleUserPause() {
  if (metricOptions.value.length < 2) return;
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
    syncCarousel();
    return;
  }
  if (!options.some(item => item.metricKey === activeMetricKey.value)) {
    activeMetricKey.value = props.model?.activeMetricKey && options.some(item => item.metricKey === props.model.activeMetricKey)
      ? props.model.activeMetricKey : options[0].metricKey;
  }
  syncCarousel();
}, { immediate: true });

watch(() => props.model?.activeMetricKey, value => {
  if (!userPaused.value && metricOptions.value.some(item => item.metricKey === value)) activeMetricKey.value = value;
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
.institution-ranking-widget { min-width: 0; color: #eaf2ff; background: rgba(7, 24, 62, .88); border: 1px solid rgba(106, 157, 220, .35); border-radius: 8px; }
.institution-ranking-widget__header, .institution-ranking-widget__meta, .institution-ranking-widget__metrics { display: flex; align-items: center; }
.institution-ranking-widget__header { justify-content: space-between; gap: 12px; padding: 12px 14px; border-bottom: 1px solid rgba(106, 157, 220, .2); }
.institution-ranking-widget__kicker { color: #9fc2df; font-size: 11px; }
.institution-ranking-widget h2 { margin: 3px 0 0; font-size: 15px; }
.institution-ranking-widget__meta { flex: 0 0 auto; gap: 8px; color: #9fc2df; font-size: 11px; }
.institution-ranking-widget__meta button, .institution-ranking-widget__metrics button { color: #cfe5ff; background: rgba(25, 67, 121, .56); border: 1px solid rgba(127, 199, 255, .35); border-radius: 4px; cursor: pointer; font: inherit; }
.institution-ranking-widget__meta button { padding: 4px 7px; }
.institution-ranking-widget__meta button:disabled { cursor: default; opacity: .55; }
.institution-ranking-widget__metrics { flex-wrap: wrap; gap: 6px; padding: 9px 14px 0; }
.institution-ranking-widget__metrics button { padding: 4px 8px; font-size: 11px; }
.institution-ranking-widget__metrics button[aria-selected="true"] { color: #071a31; background: #42e7ee; border-color: #42e7ee; }
.institution-ranking-widget__meta button:focus-visible, .institution-ranking-widget__metrics button:focus-visible, .institution-ranking-widget__list:focus-visible, .institution-ranking-widget__list tr:focus-visible { outline: 2px solid #42e7ee; outline-offset: 2px; }
.institution-ranking-widget__incomplete, .institution-ranking-widget__complete { margin: 8px 14px 0; color: #f4bd5b; font-size: 11px; }
.institution-ranking-widget__complete { color: #82dbd7; }
.institution-ranking-widget__list { max-height: 320px; margin: 8px 0 0; overflow: auto; scrollbar-width: thin; }
.institution-ranking-widget table { width: 100%; border-collapse: collapse; }
.institution-ranking-widget th, .institution-ranking-widget td { padding: 8px 14px; border-bottom: 1px solid rgba(106, 157, 220, .16); text-align: left; white-space: nowrap; }
.institution-ranking-widget th { color: #9fc2df; font-size: 11px; }
.institution-ranking-widget td { font-size: 12px; }
.institution-ranking-widget td:first-child { width: 52px; color: #82dbd7; font-variant-numeric: tabular-nums; }
.institution-ranking-widget tr[data-state="MISSING"] td { color: #8ca0bd; }
.institution-ranking-widget__visually-hidden { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
</style>
