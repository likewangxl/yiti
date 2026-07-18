<template>
  <CommonAttr :element="element">
    <el-form-item label="文本"><el-input v-model="element.propValue.text" type="textarea" :rows="2" @input="touch" /></el-form-item>
    <el-form-item label="速度(px/s)"><el-input-number v-model="element.propValue.speed" :min="10" :max="400" :step="10" @change="touch" /></el-form-item>
    <el-form-item label="方向">
      <el-radio-group v-model="element.propValue.direction" @change="touch">
        <el-radio-button label="left">向左</el-radio-button>
        <el-radio-button label="right">向右</el-radio-button>
      </el-radio-group>
    </el-form-item>
    <el-form-item label="颜色"><el-color-picker v-model="element.propValue.color" @change="touch" /></el-form-item>
    <el-form-item label="字号"><el-input-number v-model="element.propValue.fontSize" :min="12" :max="48" @change="touch" /></el-form-item>
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
