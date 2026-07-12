<template>
  <CommonAttr :element="element">
    <el-form-item label="格式"><el-input v-model="element.propValue.format" placeholder="YYYY-MM-DD HH:mm:ss" @input="touch" /></el-form-item>
    <el-form-item label="字号"><el-input-number v-model="element.propValue.fontSize" :min="8" :max="200" @change="touch" /></el-form-item>
    <el-form-item label="颜色"><el-color-picker v-model="element.propValue.color" @change="touch" /></el-form-item>
  </CommonAttr>
</template>
<script setup>
// 属性面板直接 mutate 同一份 store.curComponent 引用(照搬 DataEase 共享引用模式),改动防抖记快照。
import CommonAttr from '@/views/screen/designer/panels/CommonAttr.vue';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
defineProps({ element: { type: Object, required: true } });
const store = useScreenDesignerStore();
function touch() { store.pushSnapshotDebounced(); }
</script>
