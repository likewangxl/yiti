<template>
  <CommonAttr :element="element">
    <el-form-item label="样式">
      <!-- 7 种边框样式,radio 排不下改下拉;选项清单与渲染类名同源 variants.js,防两处漂移 -->
      <el-select v-model="element.propValue.variant" @change="touch">
        <el-option v-for="v in BORDER_VARIANTS" :key="v.value" :label="v.label" :value="v.value" />
      </el-select>
    </el-form-item>
  </CommonAttr>
</template>
<script setup>
// 属性面板直接 mutate 同一份 store.curComponent 引用(照搬 DataEase 共享引用模式),改动防抖记快照。
import CommonAttr from '@/views/screen/designer/panels/CommonAttr.vue';
import { BORDER_VARIANTS } from './variants';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
defineProps({ element: { type: Object, required: true } });
const store = useScreenDesignerStore();
function touch() { store.pushSnapshotDebounced(); }
</script>
