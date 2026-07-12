<template>
  <CommonAttr :element="element">
    <el-form-item label="样式">
      <el-radio-group v-model="element.propValue.variant" @change="touch">
        <el-radio-button label="tech-a">描边发光</el-radio-button>
        <el-radio-button label="tech-b">四角装饰</el-radio-button>
        <el-radio-button label="tech-c">双线描边</el-radio-button>
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
