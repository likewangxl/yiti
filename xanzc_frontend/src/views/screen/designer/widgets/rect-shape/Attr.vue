<template>
  <CommonAttr :element="element">
    <el-form-item label="背景色"><el-color-picker v-model="element.propValue.bg" show-alpha @change="touch" /></el-form-item>
    <el-form-item label="边框色"><el-color-picker v-model="element.propValue.borderColor" show-alpha @change="touch" /></el-form-item>
    <el-form-item label="边框宽"><el-input-number v-model="element.propValue.borderWidth" :min="0" :max="20" @change="touch" /></el-form-item>
    <el-form-item label="圆角"><el-input-number v-model="element.propValue.radius" :min="0" :max="100" @change="touch" /></el-form-item>
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
