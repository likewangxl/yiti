<template>
  <div class="dsn-attr">
    <el-form label-width="72px" size="small">
      <el-form-item label="背景色"><el-color-picker v-model="store.canvasStyle.background" @change="touch" /></el-form-item>
      <el-form-item label="适配策略">
        <el-select v-model="store.canvasStyle.adaptor" @change="touch">
          <el-option v-for="a in ADAPTORS" :key="a" :label="adaptorLabel(a)" :value="a" />
        </el-select>
      </el-form-item>
      <el-form-item label="设计基准"><span class="dim">1920 × 1080(固定)</span></el-form-item>
    </el-form>
  </div>
</template>
<script setup>
// 未选中任何组件时的画布全局设置(背景色/适配策略);设计基准 1920×1080 恒定不可改(与 utils/scale.js 的
// DESIGN_W/DESIGN_H 保持唯一来源,此处只展示不作为输入)。
import { ADAPTORS } from '@/views/screen/designer/utils/scale';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
const store = useScreenDesignerStore();
function touch() { store.pushSnapshotDebounced(); }
function adaptorLabel(a) {
  return { keep: '不缩放居中', keepProportion: '等比适配', widthFirst: '宽度铺满', heightFirst: '高度铺满' }[a] || a;
}
</script>
<style scoped>.dsn-attr { padding: 12px; } .dim { color: #7d9bc9; font-size: 12px; }</style>
