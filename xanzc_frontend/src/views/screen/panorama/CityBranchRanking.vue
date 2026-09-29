<template>
  <section class="city-branch-ranking" data-testid="city-branch-ranking"
    @mouseenter="hoverPaused = true; syncTimer()"
    @mouseleave="hoverPaused = false; syncTimer()"
    @focusin="focusPaused = true; syncTimer()"
    @focusout="focusPaused = false; syncTimer()">
    <header class="city-branch-ranking__header">
      <div><h2>支行指标排名 <small>{{ filteredRows.length + filteredMissingRows.length }} 家</small></h2><p>完成率降序 · 不良率升序 · 同分并列</p></div>
      <button type="button" data-testid="city-branch-ranking-pause" @click="togglePause">{{ paused ? '继续' : '暂停' }}</button>
    </header>
    <nav class="city-branch-ranking__tabs" role="tablist" aria-label="支行排名指标">
      <button v-for="tab in tabs" :key="tab.key" :id="`city-branch-tab-${tab.key}`" role="tab" :aria-selected="activeTabKey === tab.key" :tabindex="activeTabKey === tab.key ? 0 : -1" type="button" data-testid="city-branch-ranking-tab" :data-tab-key="tab.key" :class="{ active: activeTabKey === tab.key }" @click="selectTab(tab.key)" @keydown="onTabKey($event, tab.key)" @focus="focusPaused = true; syncTimer()" @blur="focusPaused = false; syncTimer()">{{ tab.label }}</button>
    </nav>
    <label class="city-branch-ranking__search">搜索支行<input data-testid="branch-search" :value="search" type="search" placeholder="搜索支行名称" @input="emit('search-change', $event.target.value)" /></label>
    <div v-if="pageRows.length" class="city-branch-ranking__list" role="list">
      <button v-for="row in pageRows" :key="row.orgCode" type="button" class="city-branch-ranking__row city-branch-row" data-testid="branch-row" data-ranking-row="city-branch-ranking-row" :data-org-code="row.orgCode" :data-missing="row.missing ? 'true' : undefined" @click="emit('branch-select', row.orgCode)">
        <span class="city-branch-ranking__rank">{{ row.rank }}</span><span class="city-branch-ranking__name">{{ row.orgName }}</span>
        <template v-if="!row.missing"><span class="city-branch-ranking__track" role="progressbar" :aria-label="`${row.orgName} ${tabLabel}`" :aria-valuenow="row.value" aria-valuemin="0" :aria-valuemax="barMaximum"><i :style="{ width: `${barWidth(row.value)}%` }" aria-hidden="true"></i></span><strong class="city-branch-ranking__value">{{ formatValue(row.value) }}{{ row.unit || '%' }}</strong></template>
        <template v-else><span class="city-branch-ranking__missing-value">未提供</span><strong class="city-branch-ranking__value">—</strong></template>
      </button>
    </div>
    <div v-else class="city-branch-ranking__empty" data-testid="city-branch-ranking-empty">暂无该指标有效数据</div>
    <div v-if="missingCount" class="city-branch-ranking__missing" data-testid="city-branch-ranking-missing" role="status">
      <button type="button" data-testid="city-branch-ranking-missing-toggle" :aria-expanded="showMissing" @click="showMissing = !showMissing">{{ missingCount }} 家支行暂无该指标有效数据{{ showMissing ? '（收起）' : '（查看）' }}</button>
      <div v-if="showMissing" class="city-branch-ranking__missing-list"><button v-for="row in filteredMissingRows" :key="`missing-${row.orgCode}`" type="button" data-testid="branch-row" data-missing="true" class="city-branch-ranking__missing-row" @click="emit('branch-select', row.orgCode)">{{ row.orgName }}<span>未提供</span></button></div>
    </div>
    <div class="city-pagination city-branch-ranking__pagination"><button type="button" data-testid="branch-page-prev" data-ranking-pagination="city-branch-ranking-prev" :disabled="page <= 1" @click="changePage(-1)">‹</button><span>{{ page }} / {{ totalPages }}</span><button type="button" data-testid="branch-page-next" data-ranking-pagination="city-branch-ranking-next" :disabled="page >= totalPages" @click="changePage(1)">›</button></div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = defineProps({
  model: { type: Object, default: () => ({ tabs: [], rows: [], missingRows: [], missingCount: 0 }) },
  search: { type: String, default: '' },
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 8 },
  interval: { type: Number, default: 10000 }
});
const emit = defineEmits(['tab-change', 'search-change', 'branch-select', 'page-change']);
const tabs = computed(() => Array.isArray(props.model?.tabs) ? props.model.tabs : []);
const activeTabKey = ref(props.model?.activeTabKey || tabs.value[0]?.key || '');
const page = ref(Math.max(1, Number(props.page) || 1));
const paused = ref(false);
const hoverPaused = ref(false);
const focusPaused = ref(false);
const hidden = ref(false);
const showMissing = ref(false);
let timer = null;

const filteredRows = computed(() => {
  const keyword = String(props.search || '').trim().toLocaleLowerCase();
  const rows = Array.isArray(props.model?.rows) ? props.model.rows : [];
  return keyword ? rows.filter(row => String(row.orgName || row.orgCode || '').toLocaleLowerCase().includes(keyword)) : rows;
});
const filteredMissingRows = computed(() => {
  const keyword = String(props.search || '').trim().toLocaleLowerCase();
  const rows = Array.isArray(props.model?.missingRows) ? props.model.missingRows : [];
  return keyword ? rows.filter(row => String(row.orgName || row.orgCode || '').toLocaleLowerCase().includes(keyword)) : rows;
});
const displayRows = computed(() => {
  if (filteredRows.value.length) return filteredRows.value;
  return filteredMissingRows.value.map(row => ({ ...row, missing: true }));
});
const totalPages = computed(() => Math.max(1, Math.ceil(displayRows.value.length / Math.max(1, props.pageSize))));
const pageRows = computed(() => displayRows.value.slice((page.value - 1) * Math.max(1, props.pageSize), page.value * Math.max(1, props.pageSize)));
const missingCount = computed(() => Number(props.model?.missingCount) || 0);
const tabLabel = computed(() => tabs.value.find(tab => tab.key === activeTabKey.value)?.label || '指标');
const barMaximum = computed(() => {
  const maximum = filteredRows.value.reduce((current, row) => Math.max(current, Number(row.value)), 0);
  return tabs.value.find(tab => tab.key === activeTabKey.value)?.kind === 'npl' ? Math.max(maximum, 0.000001) : Math.max(maximum, 100);
});
const interactionPaused = computed(() => paused.value || hoverPaused.value || focusPaused.value || hidden.value);

function selectTab(key) {
  if (!tabs.value.some(tab => tab.key === key)) return;
  activeTabKey.value = key;
  page.value = 1;
  emit('tab-change', key);
  syncTimer();
}
function changePage(delta) {
  const next = Math.max(1, Math.min(totalPages.value, page.value + delta));
  if (next === page.value) return;
  page.value = next;
  emit('page-change', next);
}
function onTabKey(event, key) {
  const index = tabs.value.findIndex(tab => tab.key === key);
  if (index < 0 || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
  event.preventDefault();
  const nextIndex = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.value.length - 1 : (index + (event.key === 'ArrowLeft' ? -1 : 1) + tabs.value.length) % tabs.value.length;
  selectTab(tabs.value[nextIndex].key);
  document.getElementById(`city-branch-tab-${tabs.value[nextIndex].key}`)?.focus();
}
function togglePause() { paused.value = !paused.value; syncTimer(); }
function advanceTab() {
  if (interactionPaused.value || tabs.value.length < 2) return;
  const index = tabs.value.findIndex(tab => tab.key === activeTabKey.value);
  selectTab(tabs.value[(index + 1) % tabs.value.length].key);
}
function stopTimer() { if (timer) window.clearInterval(timer); timer = null; }
function syncTimer() {
  stopTimer();
  if (!interactionPaused.value && tabs.value.length > 1) timer = window.setInterval(advanceTab, Math.max(1000, Number(props.interval) || 10000));
}
function barWidth(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return 0;
  return Math.max(0, Math.min(100, (number / barMaximum.value) * 100));
}
function formatValue(value) {
  const number = Number(value);
  return Number.isFinite(number) ? `${number.toFixed(2)}` : '—';
}
function onVisibilityChange() { hidden.value = document.visibilityState === 'hidden'; syncTimer(); }
watch(() => props.model?.activeTabKey, value => {
  if (value && tabs.value.some(tab => tab.key === value)) { activeTabKey.value = value; page.value = 1; }
  syncTimer();
});
watch(() => props.page, value => {
  const next = Math.max(1, Math.min(totalPages.value, Number(value) || 1));
  if (page.value !== next) page.value = next;
});
watch([filteredRows, () => props.search], () => { page.value = 1; });
onMounted(() => { document.addEventListener('visibilitychange', onVisibilityChange); syncTimer(); });
onBeforeUnmount(() => { document.removeEventListener('visibilitychange', onVisibilityChange); stopTimer(); });
</script>

<style scoped>
.city-branch-ranking{display:flex;min-width:0;min-height:420px;height:100%;padding:12px;flex-direction:column;gap:8px;color:var(--panorama-text,#eaf2ff);background:var(--panorama-panel-deep,rgba(4,14,39,.9));border:1px solid var(--panorama-border,rgba(119,163,255,.3));border-radius:8px}.city-branch-ranking__header{display:flex;align-items:flex-start;justify-content:space-between;gap:8px}.city-branch-ranking h2{margin:0;font-size:18px}.city-branch-ranking h2 small{color:var(--panorama-text-dim,#8fa9db);font-size:11px;font-weight:400}.city-branch-ranking p{margin:3px 0 0;color:var(--panorama-text-dim,#8fa9db);font-size:11px}.city-branch-ranking__header button{padding:4px 8px;border:1px solid rgba(119,163,255,.3);border-radius:4px;color:#cfe5ff;background:#0b204b;font:inherit;font-size:11px}.city-branch-ranking__tabs{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:4px}.city-branch-ranking__tabs button{min-width:0;padding:5px 4px;overflow:hidden;border:1px solid rgba(119,163,255,.2);border-radius:4px;color:#9fb7df;background:rgba(13,38,86,.7);font:inherit;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.city-branch-ranking__tabs button.active{border-color:#4de8ef;color:#eaf2ff;background:rgba(50,126,156,.35)}.city-branch-ranking__search{display:grid;gap:4px;color:#a9bfe3;font-size:11px}.city-branch-ranking__search input{padding:6px 8px;border:1px solid rgba(119,163,255,.28);border-radius:4px;color:#eaf2ff;background:#0b204b;font:inherit}.city-branch-ranking__list{min-height:0;overflow:auto;flex:1 1 auto}.city-branch-ranking__row{display:grid;width:100%;min-height:36px;grid-template-columns:24px minmax(72px,1.05fr) minmax(50px,1fr) 58px;align-items:center;gap:6px;padding:5px 3px;border:0;border-bottom:1px solid rgba(119,163,255,.12);color:#eaf2ff;background:transparent;text-align:left;font:inherit;cursor:pointer}.city-branch-ranking__rank{color:#ffc45e;font-size:12px}.city-branch-ranking__name{overflow:hidden;font-size:13px;text-overflow:ellipsis;white-space:nowrap}.city-branch-ranking__track{height:7px;overflow:hidden;border-radius:4px;background:rgba(119,163,255,.14)}.city-branch-ranking__track i{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,#4de8ef,#a979ff)}.city-branch-ranking__value{font-size:13px;text-align:right;white-space:nowrap}.city-branch-ranking__missing-value{color:#ffc45e;font-size:12px}.city-branch-ranking__empty,.city-branch-ranking__missing{padding:10px;color:#ffc45e;font-size:11px}.city-branch-ranking__missing > button{padding:4px 7px;border:1px solid rgba(255,196,94,.4);border-radius:4px;color:#ffd98d;background:rgba(89,58,10,.55);font:inherit;font-size:11px}.city-branch-ranking__missing-list{display:grid;gap:3px;margin-top:6px}.city-branch-ranking__missing-row{display:flex;justify-content:space-between;gap:8px;padding:4px 7px;border:1px solid rgba(255,196,94,.2);border-radius:4px;color:#eaf2ff;background:rgba(14,32,70,.75);font:inherit;font-size:11px;text-align:left}.city-branch-ranking__missing-row span{color:#ffc45e}.city-branch-ranking__pagination{display:flex;align-items:center;justify-content:flex-end;gap:8px;flex:0 0 auto;color:#9fb7df;font-size:11px}.city-branch-ranking__pagination button{min-width:24px;padding:4px;border:1px solid rgba(119,163,255,.28);border-radius:4px;color:#cfe5ff;background:#0b204b}.city-branch-ranking__pagination button:disabled{opacity:.4}
</style>

<style scoped>
/* 常规单行页签：桌面自然排列，窄屏只允许页签条横向滚动。 */
.city-branch-ranking__tabs {
  display: flex;
  min-width: 0;
  min-height: 32px;
  align-items: stretch;
  gap: 18px;
  overflow-x: auto;
  overflow-y: hidden;
  border-bottom: 1px solid rgba(119, 163, 255, .2);
  scrollbar-width: thin;
}

.city-branch-ranking__tabs button {
  flex: 0 0 auto;
  min-width: auto;
  padding: 0 0 7px;
  overflow: visible;
  border: 0;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  color: #9fb7df;
  background: transparent;
  font: inherit;
  font-size: 12px;
  line-height: 1.5;
  text-overflow: clip;
  white-space: nowrap;
}

.city-branch-ranking__tabs button.active {
  border-bottom-color: #4de8ef;
  color: #eaf2ff;
  background: transparent;
}

@media (max-width: 620px) {
  .city-branch-ranking__tabs {
    gap: 16px;
  }
}
</style>
