<template>
  <div class="w-chart">
    <!-- 复用运行时 BlockContainer:把 ChartWidget 的 blockId 映射到 block 行(bind/style/drill JSON) -->
    <BlockContainer v-if="block" :block="block" :context="ctx" />
    <div v-else class="w-chart-empty">图表(未绑定数据源)</div>
  </div>
</template>
<script setup>
// 图表容器:一期图表统一挂此容器,innerType 区分;内部复用 BlockContainer 取数逻辑
// (43010 引导态/静默 toast 契约不变)。设计态 context 用空 orgCode/empId,靠 43010 引导态占位。
import { computed, inject } from 'vue';
import BlockContainer from '@/views/screen/components/BlockContainer.vue';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
const props = defineProps({ element: { type: Object, required: true }, mode: { type: String, default: 'design' } });
const store = useScreenDesignerStore();
// 设计态:从 store.blocks 按 blockId 找 block 行;运行态:element.propValue 已内嵌 bindSnapshot(见 Task 10)
const block = computed(() => {
  if (props.element.__block) return props.element.__block; // 运行态渲染包注入
  const b = store.blocks.find(x => x.id === props.element.blockId);
  return b ? { ...b, componentType: props.element.innerType } : null;
});
const ctx = inject('previewContext', { orgCode: '', empId: '' });
</script>
<style scoped>
.w-chart { width: 100%; height: 100%; }
.w-chart-empty { width: 100%; height: 100%; display: flex; align-items: center;
  justify-content: center; color: #7d9bc9; font-size: 13px; border: 1px dashed rgba(96,148,214,.38); }
</style>
