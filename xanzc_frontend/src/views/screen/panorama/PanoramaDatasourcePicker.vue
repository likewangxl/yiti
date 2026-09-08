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
      />
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
  const label = source.dsName || source.ds_name || source.dsCode || `数据源 #${source.id}`;
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
