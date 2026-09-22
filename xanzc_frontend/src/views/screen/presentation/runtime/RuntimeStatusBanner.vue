<template>
  <section
    v-if="runtime?.enabled"
    class="presentation-runtime-status"
    :class="`is-${String(runtime.status || 'READY').toLowerCase()}`"
    data-testid="presentation-runtime-status"
    :data-state="runtime.status || 'READY'"
    aria-live="polite"
  >
    <header class="presentation-runtime-status__header">
      <div>
        <span class="presentation-runtime-status__eyebrow">数据质量</span>
        <strong>{{ runtime.statusLabel || runtime.status || '可用' }}</strong>
      </div>
      <span v-if="runtime.message" class="presentation-runtime-status__message">{{ runtime.message }}</span>
    </header>

    <div v-if="runtime.batch" class="presentation-runtime-status__batch" data-testid="presentation-runtime-batch">
      <span v-if="runtime.batch.batchId">批次 {{ runtime.batch.batchId }}</span>
      <span v-if="runtime.batch.version">版本 {{ runtime.batch.version }}</span>
      <span v-if="runtime.batch.dataDate">批次日期 {{ runtime.batch.dataDate }}</span>
      <span v-if="runtime.batch.explanation">{{ runtime.batch.explanation }}</span>
    </div>

    <div v-if="sourceDateEntries.length || sourceAsOfEntries.length || runtime.queriedAt" class="presentation-runtime-status__facts">
      <span v-for="item in sourceDateEntries" :key="item.slot" data-testid="presentation-runtime-source-date">
        {{ item.label }}：{{ item.value }}
      </span>
      <span v-for="item in sourceAsOfEntries" :key="item.key" data-testid="presentation-runtime-source-asof">
        {{ item.label }}：{{ item.value }}
      </span>
      <span v-if="runtime.queriedAt">本次刷新：{{ runtime.queriedAt }}</span>
    </div>

    <ul v-if="attentionEntries.length" class="presentation-runtime-status__slots">
      <li v-for="item in attentionEntries" :key="item.slot" :data-slot="item.slot" :data-state="item.status">
        <span>{{ item.label }}：{{ item.statusLabel }}</span><small v-if="item.message">{{ item.message }}</small>
      </li>
    </ul>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import { BINDING_SLOTS } from '../../panorama/bindings';

const props = defineProps({
  runtime: { type: Object, default: () => ({ enabled: false }) }
});

const fallbackLabels = Object.freeze({
  customers: '营销有效归属客户数',
  revenue: '手工测试收入',
  batch: '批次质量'
});
const sourceLabels = Object.freeze({
  financial: '财务', marketing: '营销', target: '目标', revenue: '收入',
  targetEffectiveDate: '目标生效日', financialCollectedAt: '财务采集',
  marketingCollectedAt: '营销采集', targetCollectedAt: '目标采集', revenueCollectedAt: '收入采集'
});

const sourceDateEntries = computed(() => Object.entries(props.runtime?.sourceDates || {})
  .filter(([, value]) => String(value || '').trim())
  .map(([slot, value]) => ({
    slot,
    value: String(value),
    label: BINDING_SLOTS[slot]?.label || fallbackLabels[slot] || slot
  })));

const sourceAsOfEntries = computed(() => Object.entries(props.runtime?.sourceAsOf || {})
  .flatMap(([slot, value]) => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return [];
    return Object.entries(value).filter(([, item]) => item !== null && item !== undefined && item !== '')
      .map(([source, item]) => ({
        key: `${slot}:${source}`,
        label: `${BINDING_SLOTS[slot]?.label || fallbackLabels[slot] || slot}/${sourceLabels[source] || source}`,
        value: displaySourceValue(item)
      }));
  }));

function displaySourceValue(value) {
  if (value === null || value === undefined || value === '') return '—';
  if (typeof value !== 'object') return String(value);
  try { return JSON.stringify(value); } catch { return '—'; }
}

const attentionEntries = computed(() => Object.values(props.runtime?.slots || {})
  .filter(item => item && item.status && item.status !== 'READY' && item.status !== 'LOADING')
  .map(item => ({
    ...item,
    label: BINDING_SLOTS[item.slot]?.label || fallbackLabels[item.slot] || item.slot
  })));
</script>

<style scoped>
.presentation-runtime-status { box-sizing: border-box; width: min(100% - 32px, 1180px); margin: 0 auto 10px; padding: 8px 14px; color: #c9ddf4; background: rgba(13, 36, 62, .9); border: 1px solid rgba(116, 151, 188, .42); border-radius: 5px; font-size: 11px; line-height: 1.55; }
.presentation-runtime-status.is-stale { color: #ffe1a3; border-color: rgba(246, 191, 73, .72); background: rgba(52, 40, 18, .92); }
.presentation-runtime-status.is-error, .presentation-runtime-status.is-no_data, .presentation-runtime-status.is-permission_denied { color: #ffd6d1; border-color: rgba(232, 112, 102, .72); background: rgba(55, 25, 33, .92); }
.presentation-runtime-status__header, .presentation-runtime-status__facts, .presentation-runtime-status__batch { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 12px; }
.presentation-runtime-status__header { justify-content: space-between; gap: 8px; }
.presentation-runtime-status__header > div { display: flex; align-items: baseline; gap: 8px; }
.presentation-runtime-status__eyebrow { color: #84d9ff; font-weight: 600; }
.presentation-runtime-status__header strong { color: #f4fbff; }
.presentation-runtime-status__message, .presentation-runtime-status__facts, .presentation-runtime-status__batch { color: #a9bed8; }
.presentation-runtime-status__batch, .presentation-runtime-status__facts { margin-top: 2px; }
.presentation-runtime-status__slots { display: grid; gap: 2px; margin: 3px 0 0; padding-left: 18px; }
.presentation-runtime-status__slots li { display: flex; gap: 8px; }
.presentation-runtime-status__slots small { color: inherit; opacity: .82; }
@media (max-width: 720px) { .presentation-runtime-status { width: min(100% - 20px, 1180px); margin-bottom: 7px; padding: 7px 10px; } }
</style>
