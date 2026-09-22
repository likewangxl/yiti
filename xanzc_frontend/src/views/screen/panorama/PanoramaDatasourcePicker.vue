<template>
  <div v-bind="rootAttrs" class="panorama-datasource-picker" :class="{ 'is-disabled': disabled }" style="width: 100%">
    <el-select
      :id="$attrs.id"
      :model-value="modelValueAsString"
      :disabled="disabled"
      :placeholder="placeholder"
      filterable
      clearable
      teleported
      popper-class="panorama-datasource-picker__popper"
      style="width: 100%"
      @update:model-value="onUpdate"
      @change="onChange"
    >
      <el-option
        v-for="source in sources"
        :key="sourceKey(source)"
        :value="sourceKey(source)"
        :label="sourceLabel(source)"
        :disabled="isDisabled(source)"
      >
        <div v-if="source.category" class="panorama-datasource-picker__option">
          <strong>{{ source.name || source.dsName || source.ds_name || source.dsCode }}</strong>
          <span>{{ source.category || '受控来源' }} · {{ source.code || source.dsCode || '无编码' }} · {{ source.dimension || 'COMMON' }} / {{ source.shape || source.dsType || 'SINGLE' }}</span>
          <small v-if="source.disabledReason">{{ source.disabledReason }}</small>
          <small v-else-if="source.metrics?.length">{{ source.metrics.map(item => `${item.metricName || item.metricCode}${item.unit ? ` [${item.unit}]` : ''}`).join('、') }}</small>
          <small v-if="source.formula">{{ source.formula }}</small>
        </div>
        <template v-else>{{ sourceLabel(source) }}</template>
      </el-option>
    </el-select>
  </div>
</template>

<script setup>
import { computed, useAttrs } from 'vue';
import { ElOption, ElSelect } from 'element-plus';

defineOptions({ inheritAttrs: false });

const attrs = useAttrs();

const props = defineProps({
  sources: { type: Array, default: () => [] },
  modelValue: { type: [String, Number], default: '' },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '请选择当前屏可用数据源' }
});

const emit = defineEmits(['update:modelValue', 'change']);

const rootAttrs = computed(() => {
  const { id: _inputId, ...rest } = attrs;
  return rest;
});
const modelValueAsString = computed(() => normalizeId(props.modelValue));

function normalizeId(value) {
  return value === null || value === undefined || value === '' ? '' : String(value);
}

function sourceKey(source = {}) {
  return normalizeId(source.id);
}

function sourceLabel(source = {}) {
  const label = source.displayLabel || source.dsName || source.ds_name || source.dsCode || `数据源 #${source.id}`;
  return source.__compositionColumnsUnsupported ? `${label}（当前双列模式不支持）` : label;
}

function isDisabled(source = {}) {
  return Boolean(source.__compositionColumnsUnsupported || source.disabled);
}

function onUpdate(value) {
  emit('update:modelValue', normalizeId(value));
}

function onChange(value) {
  emit('change', normalizeId(value));
}
</script>

<style scoped>
.panorama-datasource-picker__option { display: grid; min-width: 420px; padding: 4px 0; line-height: 1.35; }
.panorama-datasource-picker__option strong { color: #1f2937; font-size: 13px; }
.panorama-datasource-picker__option span { color: #64748b; font-size: 11px; }
.panorama-datasource-picker__option small { color: #8a5b1d; white-space: normal; }
</style>
