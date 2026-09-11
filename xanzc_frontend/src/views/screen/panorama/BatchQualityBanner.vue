<template>
  <section
    v-if="visible"
    class="batch-quality-banner"
    :class="statusClass"
    data-testid="batch-quality-banner"
    aria-label="批次质量"
  >
    <div class="batch-quality-banner__headline">
      <span class="batch-quality-banner__eyebrow">数据质量</span>
      <strong data-testid="batch-quality-status">{{ statusLabel }}</strong>
      <span v-if="effectiveQuality?.dataClassification" class="batch-quality-banner__classification">数据分类 {{ effectiveQuality.dataClassification }}</span>
      <span v-if="effectiveQuality?.batchId" class="batch-quality-banner__batch">批次 {{ effectiveQuality.batchId }}</span>
      <span v-if="effectiveQuality?.version" class="batch-quality-banner__version">版本 {{ effectiveQuality.version }}</span>
    </div>
    <div class="batch-quality-banner__facts">
      <span v-if="effectiveQuality?.dataDate">数据日期 {{ effectiveQuality.dataDate }}</span>
      <span v-if="effectiveQuality?.calculatedAt">绩效计算批次时间 {{ effectiveQuality.calculatedAt }}</span>
      <span v-if="queriedAt">本次查询/刷新时间 {{ queriedAt }}</span>
      <span v-if="coverageSubjects">机构覆盖 {{ coverageSubjects }}</span>
      <span v-if="coverageMetrics">指标格覆盖 {{ coverageMetrics }}</span>
      <span v-if="effectiveQuality?.ageDays !== null && effectiveQuality?.ageDays !== undefined">批次年龄 {{ effectiveQuality.ageDays }} 天</span>
    </div>
    <div v-if="sourceAsOfEntries.length" class="batch-quality-banner__sources">
      <span>数据截至</span>
      <span v-for="item in sourceAsOfEntries" :key="item.key">{{ item.label }} {{ item.value }}</span>
    </div>
    <div v-if="mixedPeriod" class="batch-quality-banner__warning">混合统计期间：请核对数据日期和来源截至时间</div>
    <div v-if="incompleteHistory.length" class="batch-quality-banner__warning" data-testid="batch-quality-history-warning">
      历史不完整日期 {{ summarizeList(incompleteHistory.map(item => item.dataDate || '未知日期')) }}
    </div>
    <details v-if="historyCoverage.length" class="batch-quality-banner__details" data-testid="batch-quality-history-details">
      <summary>历史覆盖（{{ historyCoverage.length }}个数据日期）</summary>
      <ul>
        <li v-for="item in historyCoverage" :key="historyKey(item)">
          {{ item.dataDate || '未知日期' }} {{ coverageText(item) }}<span v-if="item.missingSubjects?.length">，缺失机构 {{ item.missingSubjects.join('、') }}</span><span v-if="item.missing?.length">，缺失指标 {{ item.missing.join('、') }}</span>
        </li>
      </ul>
    </details>
    <div v-if="missingSubjects.length" class="batch-quality-banner__warning">
      缺失机构 {{ summarizeList(missingSubjects) }}
      <details v-if="missingSubjects.length > LIST_PREVIEW_LIMIT" class="batch-quality-banner__details">
        <summary>展开缺失机构明细（{{ missingSubjects.length }}项）</summary>
        <span>{{ missingSubjects.join('、') }}</span>
      </details>
    </div>
    <div v-if="missing.length" class="batch-quality-banner__warning">
      口径/数据缺项 {{ summarizeList(missing) }}
      <details v-if="missing.length > LIST_PREVIEW_LIMIT" class="batch-quality-banner__details">
        <summary>展开口径/数据缺项明细（{{ missing.length }}项）</summary>
        <span>{{ missing.join('、') }}</span>
      </details>
    </div>
    <div v-if="newerIncomplete.length" class="batch-quality-banner__warning">
      较新数据日期不完整 {{ summarizeList(newerIncomplete) }}
      <details v-if="newerIncomplete.length > LIST_PREVIEW_LIMIT" class="batch-quality-banner__details">
        <summary>展开较新不完整日期（{{ newerIncomplete.length }}项）</summary>
        <span>{{ newerIncomplete.join('、') }}</span>
      </details>
    </div>
    <div v-if="message" class="batch-quality-banner__message">{{ message }}</div>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import { batchQualityLabel, normalizeBatchQuality } from './batchQuality';

const props = defineProps({
  quality: { type: Object, default: null },
  qualityGuard: { type: Object, default: null },
  queriedAt: { type: String, default: '' }
});

const quality = computed(() => normalizeBatchQuality(props.quality));
const guard = computed(() => props.qualityGuard && typeof props.qualityGuard === 'object' ? props.qualityGuard : null);
const visible = computed(() => Boolean(quality.value || guard.value));
// A local batch guard means the anchor response is no longer a publishable
// model; hide its identity so a discarded batch cannot look like current data.
const effectiveQuality = computed(() => guard.value ? {} : (quality.value || {}));
const effectiveStatus = computed(() => String(guard.value?.status || quality.value?.status || 'PARTIAL').toUpperCase());
const statusLabel = computed(() => batchQualityLabel(effectiveStatus.value));
const statusClass = computed(() => `is-${effectiveStatus.value.toLowerCase().replace(/_/g, '-')}`);
const message = computed(() => String(guard.value?.message || quality.value?.message || '').trim());

const coverageSubjects = computed(() => {
  const expected = effectiveQuality.value.expectedSubjects;
  const received = effectiveQuality.value.receivedSubjects;
  return Number.isInteger(expected) || Number.isInteger(received)
    ? `${Number.isInteger(received) ? received : '—'}/${Number.isInteger(expected) ? expected : '—'}` : '';
});
const coverageMetrics = computed(() => {
  const expected = effectiveQuality.value.expected;
  const received = effectiveQuality.value.received;
  return Number.isInteger(expected) || Number.isInteger(received)
    ? `${Number.isInteger(received) ? received : '—'}/${Number.isInteger(expected) ? expected : '—'}` : '';
});

const SOURCE_LABELS = Object.freeze({
  financial: '财务', marketing: '营销', target: '目标', revenue: '收入',
  targetEffectiveDate: '目标生效日', financialCollectedAt: '财务采集',
  marketingCollectedAt: '营销采集', targetCollectedAt: '目标采集', revenueCollectedAt: '收入采集'
});

function displayValue(value) {
  if (value === null || value === undefined || value === '') return '—';
  if (typeof value === 'object') {
    try { return JSON.stringify(value); } catch { return '—'; }
  }
  return String(value);
}

const sourceAsOfEntries = computed(() => {
  const source = effectiveQuality.value.sourceAsOf;
  if (Array.isArray(source)) {
    return source.map((item, index) => {
      const key = String(item?.source || item?.name || item?.key || index);
      return { key, label: SOURCE_LABELS[key] || key, value: displayValue(item?.asOf ?? item?.value ?? item?.sourceAsOf) };
    });
  }
  if (!source || typeof source !== 'object') return [];
  return Object.entries(source).map(([key, value]) => ({ key, label: SOURCE_LABELS[key] || key, value: displayValue(value) }));
});

function displayList(value) {
  return (Array.isArray(value) ? value : []).map(item => {
    if (item === null || item === undefined) return '—';
    if (typeof item === 'object') return String(item.label || item.name || item.code || item.metric || item.slot || displayValue(item));
    return String(item);
  });
}

const missingSubjects = computed(() => displayList(effectiveQuality.value.missingSubjects));
const missing = computed(() => displayList(effectiveQuality.value.missing));
const newerIncomplete = computed(() => displayList(effectiveQuality.value.newerIncomplete));
const mixedPeriod = computed(() => effectiveQuality.value.mixedPeriod === true);
const LIST_PREVIEW_LIMIT = 3;
const historyCoverage = computed(() => Array.isArray(effectiveQuality.value.historyCoverage)
  ? effectiveQuality.value.historyCoverage : []);
const incompleteHistory = computed(() => historyCoverage.value.filter(item => item?.complete === false));

function summarizeList(items) {
  const values = Array.isArray(items) ? items : [];
  if (values.length <= LIST_PREVIEW_LIMIT) return values.join('、');
  return `${values.slice(0, LIST_PREVIEW_LIMIT).join('、')} 等${values.length}项`;
}

function historyKey(item) {
  return `${item?.dataDate || 'unknown'}:${item?.expected ?? ''}:${item?.received ?? ''}`;
}

function coverageText(item) {
  const metrics = Number.isInteger(item?.expected) || Number.isInteger(item?.received)
    ? `指标 ${Number.isInteger(item.received) ? item.received : '—'}/${Number.isInteger(item.expected) ? item.expected : '—'}` : '';
  const subjects = Number.isInteger(item?.expectedSubjects) || Number.isInteger(item?.receivedSubjects)
    ? `机构 ${Number.isInteger(item.receivedSubjects) ? item.receivedSubjects : '—'}/${Number.isInteger(item.expectedSubjects) ? item.expectedSubjects : '—'}` : '';
  const status = item?.complete === true ? '完整' : item?.complete === false ? '不完整' : '状态未知';
  return [status, metrics, subjects].filter(Boolean).join('，');
}
</script>

<style scoped>
.batch-quality-banner { box-sizing: border-box; width: min(100% - 32px, 1180px); margin: 0 auto 10px; padding: 8px 14px; color: #c9ddf4; background: rgba(13, 36, 62, .9); border: 1px solid rgba(116, 151, 188, .42); border-radius: 5px; font-size: 11px; line-height: 1.55; }
.batch-quality-banner.is-stale { color: #ffe1a3; border-color: rgba(246, 191, 73, .72); background: rgba(52, 40, 18, .92); }
.batch-quality-banner.is-partial, .batch-quality-banner.is-no-complete-batch { color: #ffd6d1; border-color: rgba(232, 112, 102, .72); background: rgba(55, 25, 33, .92); }
.batch-quality-banner__headline, .batch-quality-banner__facts, .batch-quality-banner__sources { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 12px; }
.batch-quality-banner__headline { gap: 6px 10px; }
.batch-quality-banner__eyebrow { color: #84d9ff; font-weight: 600; }
.batch-quality-banner__classification { color: #ffd68c; font-weight: 600; }
.batch-quality-banner__headline strong { color: #f4fbff; }
.batch-quality-banner__batch { overflow-wrap: anywhere; color: #b8d6f7; }
.batch-quality-banner__version { color: #8fa9c9; }
.batch-quality-banner__facts, .batch-quality-banner__sources { margin-top: 2px; color: #a9bed8; }
.batch-quality-banner__sources > span:first-child { color: #84d9ff; }
.batch-quality-banner__warning { margin-top: 3px; color: #ffd68c; }
.batch-quality-banner__details { margin-top: 3px; color: #a9bed8; }
.batch-quality-banner__details summary { cursor: pointer; color: #84d9ff; }
.batch-quality-banner__details ul { margin: 3px 0 0; padding-left: 18px; }
.batch-quality-banner__details li + li { margin-top: 2px; }
.batch-quality-banner__message { margin-top: 3px; color: inherit; }
@media (max-width: 720px) { .batch-quality-banner { width: min(100% - 20px, 1180px); margin-bottom: 7px; padding: 7px 10px; } }
</style>
