<template>
  <section class="business-metric-picker bp-crud" :class="{ 'is-disabled': disabled }" :aria-disabled="disabled ? 'true' : 'false'">
    <div class="business-metric-picker__head">
      <div>
        <label :id="labelId" class="business-metric-picker__label" data-testid="business-metric-picker-label" :for="searchId">
          {{ label }}
        </label>
        <p v-if="hint" class="business-metric-picker__hint">{{ hint }}</p>
      </div>
      <div class="business-metric-picker__counts" aria-live="polite">
        <span class="business-metric-picker__count" data-testid="business-metric-count-available">可用 {{ availableEntries.length }} 项</span>
        <span class="business-metric-picker__count business-metric-picker__count--muted" data-testid="business-metric-count-unavailable">不可用 {{ unavailableEntries.length }} 项</span>
      </div>
    </div>

    <div class="business-metric-picker__search-row">
      <input
        :id="searchId"
        v-model="searchTerm"
        class="business-metric-picker__search"
        data-testid="business-metric-search"
        type="search"
        :placeholder="placeholder"
        :disabled="disabled"
        aria-label="搜索业务指标"
      >
    </div>

    <button
      v-if="testNamedEntries.length"
      class="business-metric-picker__toggle business-metric-picker__toggle--test"
      data-testid="business-metric-toggle-test"
      type="button"
      :disabled="disabled"
      :aria-expanded="showTestNamed ? 'true' : 'false'"
      @click="showTestNamed = !showTestNamed"
    >
      {{ showTestNamed ? `隐藏测试指标（${testNamedEntries.length}）` : `显示测试指标（${testNamedEntries.length}）` }}
    </button>

    <button
      v-if="otherAvailableEntries.length"
      class="business-metric-picker__toggle"
      data-testid="business-metric-toggle-other"
      type="button"
      :disabled="disabled"
      :aria-expanded="showOther ? 'true' : 'false'"
      @click="showOther = !showOther"
    >
      {{ showOther ? `隐藏其他适配指标（${otherAvailableEntries.length}）` : `查看其他适配指标（${otherAvailableEntries.length}）` }}
    </button>

    <button
      v-if="unavailableEntries.length"
      class="business-metric-picker__toggle"
      data-testid="business-metric-toggle-unavailable"
      type="button"
      :disabled="disabled"
      :aria-expanded="showUnavailable ? 'true' : 'false'"
      @click="showUnavailable = !showUnavailable"
    >
      {{ showUnavailable ? '隐藏不可用项' : `查看不可用项（${unavailableEntries.length}）` }}
    </button>

    <div v-if="currentEntry?.disabled || currentTestHidden || currentOtherHidden" class="business-metric-picker__current" :class="{ 'is-unavailable': currentEntry?.disabled, 'is-hidden-test': currentTestHidden, 'is-other-hidden': currentOtherHidden }" data-testid="business-metric-current" :data-metric-key="currentEntry.key">
      <div class="business-metric-picker__current-head">
        <strong>{{ currentTestHidden ? '当前已保存测试绑定' : '当前已保存绑定' }}</strong>
        <span>{{ currentEntry.metricName || currentEntry.field || '业务指标' }}</span>
      </div>
      <p>来源：{{ currentEntry.sourceDescription || '受控数据来源' }}</p>
      <p v-if="currentTestHidden">该指标名称包含测试标识，当前已隐藏；已保留绑定，不会自动清空或保存。</p>
      <p v-if="currentOtherHidden">该指标当前不属于相关候选；已保留绑定，可展开其他适配指标查看。</p>
      <p>{{ currentEntry.disabledReason || '当前绑定暂不可用，请由技术人员维护。' }}</p>
    </div>

    <p v-if="!visibleEntries.length" class="business-metric-picker__empty" data-testid="business-metric-empty" role="status">
      {{ emptyMessage }}
    </p>

    <ul v-else class="business-metric-picker__list" role="listbox" :aria-labelledby="labelId">
      <li v-for="entry in visibleEntries" :key="entry.key" class="business-metric-picker__item">
        <button
          class="business-metric-picker__option"
          :class="{ 'is-selected': isSelected(entry), 'is-unavailable': entry.disabled }"
          :data-metric-key="entry.key"
          data-metric-option
          type="button"
          role="option"
          :aria-selected="isSelected(entry) ? 'true' : 'false'"
          :aria-disabled="entry.disabled || disabled ? 'true' : 'false'"
          :disabled="disabled || entry.disabled"
          @click="selectEntry(entry)"
        >
          <span class="business-metric-picker__option-main">
            <span class="business-metric-picker__metric-name">{{ entry.metricName || entry.field }}</span>
            <span class="business-metric-picker__source">来源：{{ entry.sourceDescription || '受控数据来源' }}</span>
          </span>
          <span class="business-metric-picker__option-meta">
            <span>原始单位：{{ entry.rawUnitLabel || entry.rawUnit || '待维护' }}</span>
            <span>结果形状：{{ entry.shapeLabel || shapeLabel(entry.shape) }}</span>
            <span v-if="entry.metricCode || entry.field" data-testid="business-metric-identity">
              标识：{{ entry.metricCode || entry.field }}
            </span>
          </span>
          <span v-if="entry.unitEvidenceMessage" class="business-metric-picker__unit-evidence">
            {{ entry.unitEvidenceMessage }}
          </span>
          <span v-if="entry.disabled" class="business-metric-picker__reason">{{ entry.disabledReason || '当前指标暂不可选' }}</span>
        </button>
      </li>
    </ul>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import {
  filterBusinessMetricCatalog,
  selectionFromBusinessMetric
} from './businessMetricCatalog';
import {
  isRelatedBusinessMetric,
  isTestNamedMetric,
  sortBusinessMetricOptions
} from './businessMetricOptions';

const props = defineProps({
  entries: { type: Array, default: () => [] },
  modelValue: { type: [String, Number, Object], default: null },
  disabled: { type: Boolean, default: false },
  hideTestNamed: { type: Boolean, default: true },
  relevanceContext: { type: Object, default: () => ({}) },
  label: { type: String, default: '业务指标' },
  hint: { type: String, default: '请选择一个有明确来源、语义和原始单位的指标。' },
  placeholder: { type: String, default: '搜索指标名称、来源或编码' }
});

const emit = defineEmits(['select', 'update:modelValue']);
const searchTerm = ref('');
const showUnavailable = ref(false);
const showTestNamed = ref(!props.hideTestNamed);
const showOther = ref(false);
const instanceToken = typeof globalThis.crypto?.randomUUID === 'function'
  ? globalThis.crypto.randomUUID()
  : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
const pickerId = `business-metric-picker-${instanceToken.replace(/[^a-zA-Z0-9_-]/g, '')}`;
const labelId = `${pickerId}-label`;
const searchId = `${pickerId}-search`;

watch(() => props.hideTestNamed, value => {
  showTestNamed.value = !value;
});

const entries = computed(() => (Array.isArray(props.entries) ? props.entries : []));
const searchedAllEntries = computed(() => filterBusinessMetricCatalog(entries.value, searchTerm.value));
const testNamedEntries = computed(() => searchedAllEntries.value.filter(isTestNamedMetric));
const modeEntries = computed(() => {
  if (showTestNamed.value) return entries.value;
  return entries.value.filter(entry => !isTestNamedMetric(entry));
});
const effectiveRelevanceContext = computed(() => ({
  ...(props.relevanceContext || {}),
  currentSelection: props.relevanceContext?.currentSelection ?? props.modelValue
}));
const relatedEntries = computed(() => modeEntries.value.filter(entry => isRelatedBusinessMetric(entry, effectiveRelevanceContext.value)));
const otherEntries = computed(() => modeEntries.value.filter(entry => !isRelatedBusinessMetric(entry, effectiveRelevanceContext.value)));
const searchedRelatedEntries = computed(() => filterBusinessMetricCatalog(relatedEntries.value, searchTerm.value));
const searchedOtherEntries = computed(() => filterBusinessMetricCatalog(otherEntries.value, searchTerm.value));
const searchedEntries = computed(() => filterBusinessMetricCatalog(
  showOther.value ? modeEntries.value : relatedEntries.value,
  searchTerm.value
));
const availableEntries = computed(() => searchedEntries.value.filter(entry => !entry?.disabled));
const unavailableEntries = computed(() => searchedEntries.value.filter(entry => entry?.disabled));
const otherAvailableEntries = computed(() => searchedOtherEntries.value.filter(entry => !entry?.disabled));
const currentEntry = computed(() => entries.value.find(entry => isSelected(entry)) || null);
const currentTestHidden = computed(() => Boolean(
  currentEntry.value
  && !showTestNamed.value
  && isTestNamedMetric(currentEntry.value)
));
const currentOtherHidden = computed(() => Boolean(
  currentEntry.value
  && !showOther.value
  && !isRelatedBusinessMetric(currentEntry.value, effectiveRelevanceContext.value)
));
const visibleEntries = computed(() => {
  const source = showUnavailable.value ? searchedEntries.value : availableEntries.value;
  return sortBusinessMetricOptions(source, effectiveRelevanceContext.value);
});
const emptyMessage = computed(() => {
  if (!entries.value.length) return '当前配置项暂无适用指标';
  if (searchTerm.value.trim() && !visibleEntries.value.length) {
    if (!showOther.value && otherAvailableEntries.value.length) {
      return '没有匹配的相关业务指标，可查看其他适配指标';
    }
    return !showTestNamed.value && testNamedEntries.value.length
      ? '没有匹配的业务指标（测试指标已隐藏）'
      : '没有匹配的业务指标';
  }
  if (!showTestNamed.value && modeEntries.value.length === 0) {
    return '当前配置项仅有测试指标，已按默认规则隐藏；请显示测试指标查看';
  }
  if (!showOther.value && !availableEntries.value.length && otherAvailableEntries.value.length) {
    return '当前暂无相关候选，可展开查看其他适配指标';
  }
  if (!showUnavailable.value && !availableEntries.value.length && unavailableEntries.value.length) {
    return '当前配置项暂无可用业务指标，请查看不可用项';
  }
  return '当前配置项暂无适用指标';
});

function modelSourceId(value) {
  if (!value || typeof value !== 'object') return null;
  return value.datasourceId ?? value.sourceId ?? value.dsId ?? null;
}

function modelField(value) {
  if (!value || typeof value !== 'object') return '';
  return String(value.field ?? value.column ?? '').trim();
}

function isSelected(entry) {
  const value = props.modelValue;
  if (value && typeof value === 'object') {
    if (value.key && String(value.key) === String(entry.key)) return true;
    return String(modelSourceId(value)) === String(entry.sourceId)
      && modelField(value) === String(entry.field);
  }
  if (value === null || value === undefined || value === '') return false;
  return String(value) === String(entry.key);
}

function shapeLabel(shape) {
  const labels = { SINGLE: '单值', TIMESERIES: '时序', TABLE: '明细表' };
  return labels[String(shape || '').toUpperCase()] || shape || '待确认';
}

function selectEntry(entry) {
  if (props.disabled || entry?.disabled) return;
  const selection = selectionFromBusinessMetric(entry);
  emit('update:modelValue', selection);
  emit('select', selection);
}
</script>

<style scoped>
.business-metric-picker {
  display: grid;
  gap: 12px;
  padding: 16px;
  color: var(--color-text, #303133);
  border: 1px solid var(--color-border, #dcdfe6);
  border-radius: var(--radius-control, 6px);
  background: var(--color-surface, #fff);
}

.business-metric-picker__head,
.business-metric-picker__option,
.business-metric-picker__option-meta {
  display: flex;
  align-items: center;
}

.business-metric-picker__head {
  justify-content: space-between;
  gap: 12px;
}

.business-metric-picker__counts {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px 10px;
}

.business-metric-picker__label {
  display: inline-block;
  font-size: 14px;
  font-weight: 600;
}

.business-metric-picker__hint,
.business-metric-picker__source,
.business-metric-picker__reason,
.business-metric-picker__option-meta,
.business-metric-picker__count {
  color: var(--color-text-secondary, #909399);
  font-size: 12px;
}

.business-metric-picker__count--muted {
  color: var(--color-warning, #e6a23c);
}

.business-metric-picker__hint {
  margin: 4px 0 0;
}

.business-metric-picker__search-row {
  display: flex;
}

.business-metric-picker__search {
  box-sizing: border-box;
  width: 100%;
  min-height: 34px;
  padding: 7px 10px;
  color: inherit;
  border: 1px solid var(--color-border, #dcdfe6);
  border-radius: var(--radius-control, 6px);
  background: var(--color-surface, #fff);
  outline: none;
}

.business-metric-picker__search:focus {
  border-color: var(--color-primary, #409eff);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary, #409eff) 16%, transparent);
}

.business-metric-picker__toggle {
  justify-self: start;
  padding: 0;
  color: var(--color-primary, #409eff);
  font-size: 12px;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.business-metric-picker__toggle:disabled {
  color: var(--color-text-placeholder, #c0c4cc);
  cursor: not-allowed;
}

.business-metric-picker__toggle--test {
  color: var(--color-text-secondary, #606266);
}

.business-metric-picker__current {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  color: var(--color-text-secondary, #606266);
  border: 1px solid var(--color-warning-light-5, #fdf6ec);
  border-radius: var(--radius-control, 6px);
  background: var(--color-warning-light-9, #fdf6ec);
  font-size: 12px;
  line-height: 1.5;
}

.business-metric-picker__current p {
  margin: 0;
}

.business-metric-picker__current-head {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  color: var(--color-text, #303133);
}

.business-metric-picker__current.is-hidden-test {
  border-color: var(--color-border, #dcdfe6);
  background: var(--color-fill-lighter, #fafafa);
}

.business-metric-picker__current.is-other-hidden {
  border-color: var(--color-border, #dcdfe6);
  background: var(--color-fill-lighter, #fafafa);
}

.business-metric-picker__list {
  display: grid;
  gap: 8px;
  max-height: 360px;
  margin: 0;
  padding: 0;
  overflow: auto;
  list-style: none;
}

.business-metric-picker__option {
  position: relative;
  width: 100%;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px 16px;
  padding: 11px 12px;
  color: inherit;
  text-align: left;
  border: 1px solid var(--color-border, #ebeef5);
  border-radius: var(--radius-control, 6px);
  background: var(--color-surface, #fff);
  cursor: pointer;
}

.business-metric-picker__option:hover:not(:disabled),
.business-metric-picker__option.is-selected {
  border-color: var(--color-primary, #409eff);
  background: var(--color-primary-light-9, #ecf5ff);
}

.business-metric-picker__option:focus-visible {
  outline: 2px solid var(--color-primary, #409eff);
  outline-offset: 1px;
}

.business-metric-picker__option:disabled {
  color: var(--color-text-secondary, #909399);
  cursor: not-allowed;
}

.business-metric-picker__option.is-unavailable {
  background: var(--color-fill-light, #f5f7fa);
}

.business-metric-picker__option-main {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.business-metric-picker__metric-name {
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.business-metric-picker__option-meta {
  flex-wrap: wrap;
  gap: 8px 14px;
}

.business-metric-picker__reason {
  flex-basis: 100%;
  color: var(--color-warning, #e6a23c);
  line-height: 1.5;
}

.business-metric-picker__unit-evidence {
  flex-basis: 100%;
  color: var(--color-text-secondary, #909399);
  font-size: 12px;
  line-height: 1.5;
}

.business-metric-picker__empty {
  margin: 4px 0;
  padding: 18px 12px;
  color: var(--color-text-secondary, #909399);
  text-align: center;
  border: 1px dashed var(--color-border, #dcdfe6);
  border-radius: var(--radius-control, 6px);
  background: var(--color-fill-lighter, #fafafa);
}

.business-metric-picker.is-disabled {
  opacity: 0.75;
}
</style>
