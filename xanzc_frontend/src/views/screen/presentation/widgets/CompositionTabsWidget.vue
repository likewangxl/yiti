<template>
  <section
    ref="rootRef"
    class="composition-tabs-widget"
    data-testid="composition-tabs-root"
    tabindex="0"
    aria-label="业务结构页签"
    @mouseenter="hoverPaused = true"
    @mouseleave="hoverPaused = false"
    @focusin="focusPaused = true"
    @focusout="focusPaused = false"
  >
    <header class="composition-tabs-widget__header">
      <div>
        <h2>{{ activeComponent?.title || '业务结构' }}</h2>
        <p v-if="activeComponent?.subtitle">{{ activeComponent.subtitle }}</p>
      </div>
      <button
        v-if="model.tabs?.length > 1"
        type="button"
        data-testid="composition-tabs-pause"
        :aria-pressed="userPaused"
        @click="userPaused = !userPaused"
      >{{ userPaused ? '继续轮播' : '暂停轮播' }}</button>
    </header>

    <nav v-if="model.tabs?.length" class="composition-tabs-widget__tabs" role="tablist" aria-label="业务结构指标">
      <button
        v-for="tab in model.tabs"
        :key="tab.tabKey"
        type="button"
        role="tab"
        :data-testid="`composition-tab-${tab.tabKey}-select`"
        :aria-selected="tab.tabKey === activeTabKey"
        :class="{ 'is-active': tab.tabKey === activeTabKey }"
        @click="selectTab(tab.tabKey)"
      >{{ tab.label }}</button>
    </nav>

    <article v-if="activeTab" class="composition-tabs-widget__panel" :data-testid="`composition-tab-${activeTab.tabKey}`" :data-state="activeTab.state">
      <div class="composition-tabs-widget__summary">
        <span>核定总量</span>
        <strong>{{ activeTab.total?.text || '—' }}</strong>
        <small>{{ activeTab.total?.unit || activeTab.unit || '单位待补充' }}</small>
      </div>
      <div class="composition-tabs-widget__rows">
        <article class="composition-tabs-widget__row">
          <div><strong>公司</strong><span>{{ valueText(activeTab.corporate) }}</span></div>
          <span class="composition-tabs-widget__share">{{ shareText(activeTab.corporate) }}</span>
          <button type="button" data-testid="business-line-corp" @click="selectBusinessLine('CORP')">查看公司</button>
        </article>
        <article class="composition-tabs-widget__row">
          <div><strong>零售</strong><span>{{ valueText(activeTab.retail) }}</span></div>
          <span class="composition-tabs-widget__share">{{ shareText(activeTab.retail) }}</span>
          <button type="button" data-testid="business-line-retail" @click="selectBusinessLine('RETAIL')">查看零售</button>
        </article>
        <article v-if="activeTab.other" class="composition-tabs-widget__row composition-tabs-widget__row--other">
          <div><strong>其他</strong><span>{{ valueText(activeTab.other) }}</span></div>
          <span class="composition-tabs-widget__share">{{ shareText(activeTab.other) }}</span>
        </article>
        <article v-if="activeTab.gap" class="composition-tabs-widget__row composition-tabs-widget__row--gap">
          <div><strong>缺口</strong><span>{{ valueText(activeTab.gap) }}</span></div>
          <span class="composition-tabs-widget__share">{{ shareText(activeTab.gap) }}</span>
        </article>
      </div>
      <p v-if="activeTab.statusMessage" class="composition-tabs-widget__status" role="status">{{ activeTab.statusMessage }}</p>
    </article>
    <p v-else class="composition-tabs-widget__empty">暂无业务结构页签</p>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = defineProps({
  model: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['business-line-select']);

const rootRef = ref(null);
const activeTabKey = ref('');
const userPaused = ref(false);
const hoverPaused = ref(false);
const focusPaused = ref(false);
let rotationTimer = null;

const tabs = computed(() => Array.isArray(props.model?.tabs) ? props.model.tabs : []);
const activeTab = computed(() => tabs.value.find(tab => tab.tabKey === activeTabKey.value) || tabs.value[0] || null);
const activeComponent = computed(() => (Array.isArray(props.model?.components) ? props.model.components : [])
  .find(component => component.tabs?.some(tab => tab.tabKey === activeTab.value?.tabKey)) || null);
const rotationPaused = computed(() => userPaused.value || hoverPaused.value || focusPaused.value);

function selectTab(tabKey) {
  const key = String(tabKey || '');
  if (!tabs.value.some(tab => tab.tabKey === key)) return;
  activeTabKey.value = key;
  userPaused.value = true;
}

function selectBusinessLine(businessLine) {
  const key = String(activeTab.value?.tabKey || '');
  if (!key || !['CORP', 'RETAIL'].includes(businessLine)) return;
  emit('business-line-select', { businessLine, tabKey: key });
}

function advanceTab() {
  if (rotationPaused.value || tabs.value.length < 2) return;
  const index = tabs.value.findIndex(tab => tab.tabKey === activeTabKey.value);
  activeTabKey.value = tabs.value[(index + 1 + tabs.value.length) % tabs.value.length]?.tabKey || tabs.value[0]?.tabKey || '';
}

function startRotation() {
  if (rotationTimer || !props.model?.rotationEnabled || tabs.value.length < 2) return;
  const interval = Number.isInteger(props.model?.intervalMs) && props.model.intervalMs > 0 ? props.model.intervalMs : 10000;
  rotationTimer = globalThis.setInterval(advanceTab, interval);
}

function stopRotation() {
  if (rotationTimer) {
    globalThis.clearInterval(rotationTimer);
  }
  rotationTimer = null;
}

function valueText(value) {
  if (!value || value.value === null || value.value === undefined) return '—';
  return `${value.text ?? value.value} ${value.unit || ''}`.trim();
}

function shareText(value) {
  return value?.share === null || value?.share === undefined ? '占比不可计算' : (value.shareText || `${value.share}%`);
}

watch(() => tabs.value.map(tab => tab.tabKey).join('|'), () => {
  if (!tabs.value.some(tab => tab.tabKey === activeTabKey.value)) activeTabKey.value = tabs.value[0]?.tabKey || '';
  stopRotation();
  startRotation();
});

onMounted(() => {
  activeTabKey.value = props.model?.activeTabKey || tabs.value[0]?.tabKey || '';
  startRotation();
});

onBeforeUnmount(stopRotation);
</script>

<style scoped>
.composition-tabs-widget { min-width: 0; padding: 14px; color: #eaf2ff; background: rgba(16, 40, 75, .84); border: 1px solid rgba(106, 157, 220, .35); border-radius: 8px; outline: none; }
.composition-tabs-widget__header,.composition-tabs-widget__tabs,.composition-tabs-widget__row,.composition-tabs-widget__summary { display: flex; align-items: center; }
.composition-tabs-widget__header { justify-content: space-between; gap: 12px; }
.composition-tabs-widget__header h2 { margin: 0; font-size: 15px; }
.composition-tabs-widget__header p { margin: 4px 0 0; color: #9fc2df; font-size: 11px; }
.composition-tabs-widget__header button,.composition-tabs-widget__tabs button,.composition-tabs-widget__row button { border: 1px solid rgba(121, 161, 248, .3); border-radius: 4px; color: #bcd5ff; background: rgba(55, 112, 206, .22); font: inherit; font-size: 11px; cursor: pointer; }
.composition-tabs-widget__header button { padding: 5px 8px; }
.composition-tabs-widget__tabs { gap: 6px; margin-top: 12px; }
.composition-tabs-widget__tabs button { padding: 6px 12px; }
.composition-tabs-widget__tabs button.is-active { color: #071a31; background: #42e7ee; }
.composition-tabs-widget__panel { margin-top: 12px; }
.composition-tabs-widget__summary { gap: 8px; color: #9fc2df; font-size: 11px; }
.composition-tabs-widget__summary strong { color: #f5f9ff; font-size: 24px; }
.composition-tabs-widget__rows { display: grid; gap: 7px; margin-top: 10px; }
.composition-tabs-widget__row { justify-content: space-between; gap: 8px; padding: 9px; border: 1px solid rgba(106, 157, 220, .24); border-radius: 5px; }
.composition-tabs-widget__row > div { display: grid; gap: 3px; min-width: 0; }
.composition-tabs-widget__row > div span { color: #bcd5ff; font-size: 12px; }
.composition-tabs-widget__share { color: #42e7ee; font-variant-numeric: tabular-nums; font-size: 13px; }
.composition-tabs-widget__row button { padding: 4px 7px; white-space: nowrap; }
.composition-tabs-widget__row--other { border-color: rgba(244, 189, 91, .38); }
.composition-tabs-widget__row--gap { border-color: rgba(255, 116, 134, .38); }
.composition-tabs-widget__status,.composition-tabs-widget__empty { margin: 10px 0 0; color: #f4bd5b; font-size: 11px; }
</style>
