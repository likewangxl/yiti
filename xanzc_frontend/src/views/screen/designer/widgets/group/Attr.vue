<template>
  <div class="dsn-attr">
    <el-collapse v-model="open">
      <el-collapse-item title="位置与尺寸" name="pos">
        <el-form label-width="42px" size="small">
          <div class="row2">
            <el-form-item label="X"><el-input-number controls-position="right" :model-value="element.style.left" :min="0" @change="v => set('left', v)" /></el-form-item>
            <el-form-item label="Y"><el-input-number controls-position="right" :model-value="element.style.top" :min="0" @change="v => set('top', v)" /></el-form-item>
          </div>
          <div class="row2">
            <el-form-item label="宽"><el-input-number controls-position="right" :model-value="element.style.width" :min="1" @change="v => set('width', v)" /></el-form-item>
            <el-form-item label="高"><el-input-number controls-position="right" :model-value="element.style.height" :min="1" @change="v => set('height', v)" /></el-form-item>
          </div>
        </el-form>
      </el-collapse-item>
    </el-collapse>
    <div class="grp-info">组内 {{ (element.children || []).length }} 个组件,改宽高时按比例联动缩放</div>
    <el-button size="small" style="margin-top:8px" @click="store.ungroupSelected()">解组</el-button>
  </div>
</template>
<script setup>
// Group 专属属性面板——不复用 CommonAttr 的位置尺寸编辑:改组宽/高必须同步按比例换算
// children(否则 children 相对坐标与组尺寸失配,视觉漂移),CommonAttr.set 无此语义。
import { ref } from 'vue';
import { clampRect } from '@/views/screen/designer/utils/scale';
import { scaleGroupChildren } from '@/views/screen/designer/utils/group';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
const props = defineProps({ element: { type: Object, required: true } });
const store = useScreenDesignerStore();
const open = ref(['pos']);
function set(key, v) {
  const prev = { ...props.element.style };
  const next = clampRect({ ...prev, [key]: v });
  if ((key === 'width' || key === 'height')
      && (next.width !== prev.width || next.height !== prev.height)) {
    props.element.children = scaleGroupChildren(props.element.children,
      { width: prev.width, height: prev.height },
      { width: next.width, height: next.height });
  }
  props.element.style = { ...props.element.style, ...next };
  store.pushSnapshotDebounced();
}
</script>
<style scoped>
.dsn-attr { padding: 8px; } .row2 { display: flex; gap: 8px; }
.row2 .el-form-item { flex: 1; min-width: 0; margin-right: 0; }
.dsn-attr :deep(.el-input-number) { width: 100%; }
.grp-info { margin-top: 8px; color: #7d9bc9; font-size: 12px; }
</style>
