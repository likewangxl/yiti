<template>
  <CommonAttr :element="element">
    <el-form-item label="图片URL"><el-input v-model="element.propValue.url" placeholder="https://..." @input="touch" /></el-form-item>
    <el-form-item label="填充方式">
      <el-select v-model="element.propValue.fit" @change="touch">
        <el-option label="contain(完整显示)" value="contain" />
        <el-option label="cover(裁切铺满)" value="cover" />
        <el-option label="fill(拉伸铺满)" value="fill" />
      </el-select>
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
