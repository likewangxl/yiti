<template>
  <div class="w-chart">
    <!-- 复用运行时 BlockContainer:把 ChartWidget 的 blockId 映射到 block 行(bind/style/drill JSON) -->
    <BlockContainer v-if="block" :block="block" :context="ctx" :prop-value="element.propValue || {}" />
    <div v-else-if="hasDatasource" class="w-chart-empty">已选择数据源，保存后预览</div>
    <div v-else class="w-chart-empty">图表(未绑定数据源)</div>
  </div>
</template>
<script setup>
// 图表容器:一期图表统一挂此容器,innerType 区分;内部复用 BlockContainer 取数逻辑
// (43010 引导态/静默 toast 契约不变)。设计态取数通过草稿端点复核当前屏 block 绑定。
import { computed, inject, isRef } from 'vue';
import BlockContainer from '@/views/screen/components/BlockContainer.vue';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
import { runtimeSchemaVersion } from '@/utils/screenScope';
const props = defineProps({ element: { type: Object, required: true }, mode: { type: String, default: 'design' } });
const store = useScreenDesignerStore();
const previewContext = inject('previewContext', { orgCode: '', empId: '' });

function parse(json, fallback = {}) {
  if (json && typeof json === 'object') return json;
  try { return json ? JSON.parse(json) : fallback; } catch { return fallback; }
}

// 仅比较 JSON 语义，忽略属性顺序/空白差异；绑定变更尚未保存时禁止复用旧 block 数据。
function stableJson(value) {
  if (Array.isArray(value)) return value.map(stableJson);
  if (value && typeof value === 'object') {
    return Object.keys(value).sort().reduce((out, key) => {
      out[key] = stableJson(value[key]);
      return out;
    }, {});
  }
  return value;
}
function sameBind(left, right) {
  return JSON.stringify(stableJson(parse(left))) === JSON.stringify(stableJson(parse(right)));
}

const bind = computed(() => parse(props.element.bindJson));
const hasDatasource = computed(() => Number.isSafeInteger(bind.value?.dsId) && bind.value.dsId > 0);
// 设计态:从 store.blocks 按 blockId 找 block 行;运行态:element.__block 已内嵌可信 bindSnapshot
const block = computed(() => {
  if (props.element.__block) return props.element.__block; // 运行态渲染包注入
  if (!hasDatasource.value || !Number.isSafeInteger(props.element.blockId) || props.element.blockId <= 0) return null;
  const b = store.blocks.find(x => x.id === props.element.blockId);
  if (!b || !sameBind(props.element.bindJson, b.bindJson)) return null;
  return b ? { ...b, componentType: props.element.innerType } : null;
});
const ctx = computed(() => {
  const base = isRef(previewContext) ? previewContext.value : (previewContext || {});
  if (props.mode !== 'design') return base;
  // 命名组由机构范围固定走 v2；传统屏仅在草稿根版本已校验为数字 2 时沿用 v2，
  // 避免把字符串或未知画布版本宽松升级成运行时协议版本。
  const draftSchemaVersion = store.draftSchemaVersion === 2 ? 2 : 1;
  const schemaVersion = runtimeSchemaVersion({
    orgScopeMode: store.orgScopeMode,
    ...(draftSchemaVersion === 2 ? { runtimeSchemaVersion: 2 } : {})
  });
  return {
    ...base,
    screenCode: String(store.screenCode || ''),
    previewState: 'draft',
    schemaVersion
  };
});
</script>
<style scoped>
.w-chart { width: 100%; height: 100%; }
.w-chart-empty { width: 100%; height: 100%; display: flex; align-items: center;
  justify-content: center; color: #7d9bc9; font-size: 13px; border: 1px dashed rgba(96,148,214,.38); }
</style>
