<template>
  <div class="dsn-attr">
    <el-collapse v-model="open">
      <el-collapse-item title="位置与尺寸" name="pos">
        <el-form label-width="42px" size="small">
          <!-- controls-position=right + 宽度铺满:±横排的默认形态(~150px)在窄栏两列布局下会被截断 -->
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
      <el-collapse-item title="外观" name="look">
        <el-form label-width="52px" size="small">
          <el-form-item label="透明度">
            <el-slider :model-value="opacity" :min="0" :max="100" @change="setOpacity" />
          </el-form-item>
          <!-- 组件级背景:透明(现状缺省)/纯色/线性渐变;CSS 生成走 utils/background.js 纯函数,
               设计态(Shape)与运行时(ScreenRenderer)同一实现,字段落在 style(bgType/bgColor/bgFrom/bgTo/bgAngle) -->
          <el-form-item label="背景">
            <el-radio-group :model-value="bgType" @change="v => setBg({ bgType: v })">
              <el-radio-button label="none">透明</el-radio-button>
              <el-radio-button label="solid">纯色</el-radio-button>
              <el-radio-button label="gradient">渐变</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="bgType === 'solid'" label="颜色">
            <el-color-picker show-alpha :model-value="element.style.bgColor"
                             @change="v => setBg({ bgColor: v })" />
          </el-form-item>
          <template v-if="bgType === 'gradient'">
            <el-form-item label="渐变色">
              <div class="row2" style="align-items:center">
                <el-color-picker show-alpha :model-value="element.style.bgFrom"
                                 @change="v => setBg({ bgFrom: v })" />
                <el-color-picker show-alpha :model-value="element.style.bgTo"
                                 @change="v => setBg({ bgTo: v })" />
              </div>
            </el-form-item>
            <el-form-item label="角度">
              <el-input-number controls-position="right" :model-value="element.style.bgAngle ?? 135"
                               :min="0" :max="360" :step="15" @change="v => setBg({ bgAngle: v })" />
            </el-form-item>
          </template>
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
// 组件级背景类型:缺省 none(透明,与存量组件行为一致,存量画布 JSON 无 bgType 字段读时零迁移)
const bgType = computed(() => props.element.style.bgType || 'none');
function set(key, v) {
  const next = clampRect({ ...props.element.style, [key]: v });
  props.element.style = { ...props.element.style, ...next };
  store.pushSnapshotDebounced();
}
function setOpacity(v) {
  props.element.style = { ...props.element.style, opacity: v / 100 };
  store.pushSnapshotDebounced();
}
/** 背景字段补丁(bgType/bgColor/bgFrom/bgTo/bgAngle);切到渐变时补默认双色,避免空渐变看不出效果 */
function setBg(patch) {
  const merged = { ...props.element.style, ...patch };
  if (merged.bgType === 'gradient') {
    if (!merged.bgFrom) merged.bgFrom = 'rgba(10,32,74,.85)';
    if (!merged.bgTo) merged.bgTo = 'rgba(5,14,43,.35)';
    if (merged.bgAngle === undefined || merged.bgAngle === null) merged.bgAngle = 135;
  }
  props.element.style = merged;
  store.pushSnapshotDebounced();
}
</script>
<style scoped>
.dsn-attr { padding: 8px; } .row2 { display: flex; gap: 8px; }
.row2 .el-form-item { flex: 1; min-width: 0; margin-right: 0; }
/* :deep 同时覆盖本组件与 slot 里各组件私有属性(如图表"刷新(秒)")的数字输入,统一铺满栏宽 */
.dsn-attr :deep(.el-input-number) { width: 100%; }
</style>
