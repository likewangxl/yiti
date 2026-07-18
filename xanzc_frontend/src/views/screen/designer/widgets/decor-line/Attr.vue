<template>
  <CommonAttr :element="element">
    <el-form-item label="方向">
      <el-radio-group v-model="element.propValue.direction" @change="touch">
        <el-radio-button label="h">横线</el-radio-button>
        <el-radio-button label="v">竖线</el-radio-button>
      </el-radio-group>
    </el-form-item>
    <el-form-item label="颜色预设">
      <el-radio-group v-model="element.propValue.preset" @change="touch">
        <el-radio-button label="cyan">科技青</el-radio-button>
        <el-radio-button label="blue">深空蓝</el-radio-button>
        <el-radio-button label="gold">数值金</el-radio-button>
      </el-radio-group>
    </el-form-item>
    <el-form-item label="粗细"><el-input-number v-model="element.propValue.thickness" :min="1" :max="12" @change="touch" /></el-form-item>
    <el-form-item label="两端箭头"><el-switch v-model="element.propValue.arrow" @change="touch" /></el-form-item>
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
