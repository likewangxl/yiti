<template>
  <CommonAttr :element="element">
    <el-form-item label="文本"><el-input v-model="element.propValue.text" @input="touch" /></el-form-item>
    <el-form-item label="预设样式">
      <el-radio-group v-model="element.propValue.preset" @change="touch">
        <el-radio-button label="glow">发光线</el-radio-button>
        <el-radio-button label="slash">斜切块</el-radio-button>
        <el-radio-button label="underline">下划光条</el-radio-button>
      </el-radio-group>
    </el-form-item>
    <el-form-item label="字号"><el-input-number v-model="element.propValue.fontSize" :min="12" :max="80" @change="touch" /></el-form-item>
    <el-form-item label="对齐">
      <el-radio-group v-model="element.propValue.align" @change="touch">
        <el-radio-button label="left">左</el-radio-button>
        <el-radio-button label="center">中</el-radio-button>
        <el-radio-button label="right">右</el-radio-button>
      </el-radio-group>
    </el-form-item>
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
