<template>
  <div class="dsn-attr">
    <el-collapse v-model="open">
      <el-collapse-item title="位置与尺寸" name="pos">
        <el-form label-width="42px" size="small">
          <div class="row2">
            <el-form-item label="X"><el-input-number :model-value="element.style.left" :min="0" @change="v => set('left', v)" /></el-form-item>
            <el-form-item label="Y"><el-input-number :model-value="element.style.top" :min="0" @change="v => set('top', v)" /></el-form-item>
          </div>
          <div class="row2">
            <el-form-item label="宽"><el-input-number :model-value="element.style.width" :min="1" @change="v => set('width', v)" /></el-form-item>
            <el-form-item label="高"><el-input-number :model-value="element.style.height" :min="1" @change="v => set('height', v)" /></el-form-item>
          </div>
        </el-form>
      </el-collapse-item>
      <el-collapse-item title="外观" name="look">
        <el-form label-width="52px" size="small">
          <el-form-item label="透明度">
            <el-slider :model-value="opacity" :min="0" :max="100" @change="setOpacity" />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      <slot />  <!-- 组件私有属性项 -->
    </el-collapse>
  </div>
</template>
<script setup>
// 公共属性基座(位置尺寸/透明度) + slot 承接各素材/图表组件的私有字段。
// 注：本文件按计划 Task 9 §9.2 给定代码原样提前建立，用于满足 Task 8 widgets 的 Attr.vue
// 依赖与 vite build 验收门槛；Task 9 组装阶段应确认本文件已存在，无需重建（见 Task 8 报告）。
import { computed, ref } from 'vue';
import { clampRect } from '@/views/screen/designer/utils/scale';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
const props = defineProps({ element: { type: Object, required: true } });
const store = useScreenDesignerStore();
const open = ref(['pos', 'look']);
const opacity = computed(() => Math.round((props.element.style.opacity ?? 1) * 100));
function set(key, v) {
  const next = clampRect({ ...props.element.style, [key]: v });
  props.element.style = { ...props.element.style, ...next };
  store.pushSnapshotDebounced();
}
function setOpacity(v) {
  props.element.style = { ...props.element.style, opacity: v / 100 };
  store.pushSnapshotDebounced();
}
</script>
<style scoped>
.dsn-attr { padding: 8px; } .row2 { display: flex; gap: 8px; }
</style>
