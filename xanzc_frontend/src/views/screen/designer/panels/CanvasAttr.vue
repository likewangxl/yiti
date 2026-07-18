<template>
  <div class="dsn-attr">
    <el-form label-width="72px" size="small">
      <el-form-item label="背景类型">
        <!-- 三选一:纯色(现状)/线性渐变(双色+角度)/图片 URL;canvas_style_json 读时兼容由
             store.loadFromEditor 的 normalizeCanvasStyle 统一补默认,此处可直接绑定新字段 -->
        <el-radio-group v-model="store.canvasStyle.backgroundType" @change="touch">
          <el-radio-button label="solid">纯色</el-radio-button>
          <el-radio-button label="gradient">渐变</el-radio-button>
          <el-radio-button label="image">图片</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="store.canvasStyle.backgroundType === 'solid'" label="背景色">
        <el-color-picker v-model="store.canvasStyle.background" @change="touch" />
      </el-form-item>
      <template v-if="store.canvasStyle.backgroundType === 'gradient'">
        <el-form-item label="渐变色">
          <div class="row-line">
            <el-color-picker v-model="store.canvasStyle.bgGradient.from" @change="touch" />
            <span class="dim">→</span>
            <el-color-picker v-model="store.canvasStyle.bgGradient.to" @change="touch" />
          </div>
        </el-form-item>
        <el-form-item label="角度(°)">
          <el-input-number v-model="store.canvasStyle.bgGradient.angle" controls-position="right"
                           :min="0" :max="360" :step="15" @change="touch" />
        </el-form-item>
      </template>
      <el-form-item v-if="store.canvasStyle.backgroundType === 'image'" label="图片URL">
        <el-input v-model="store.canvasStyle.bgImage" placeholder="https://..." clearable @change="touch" />
      </el-form-item>
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
// 未选中任何组件时的画布全局设置(背景三选一/适配策略);设计基准 1920×1080 恒定不可改(与 utils/scale.js 的
// DESIGN_W/DESIGN_H 保持唯一来源,此处只展示不作为输入)。背景 CSS 生成统一走 utils/background.js,
// 设计器画布(CanvasCore)/运行时(ScreenRenderer)共用同一纯函数,面板只改 canvasStyle 字段。
import { ADAPTORS } from '@/views/screen/designer/utils/scale';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
const store = useScreenDesignerStore();
function touch() { store.pushSnapshotDebounced(); }
function adaptorLabel(a) {
  return { keep: '不缩放居中', keepProportion: '等比适配', widthFirst: '宽度铺满', heightFirst: '高度铺满' }[a] || a;
}
</script>
<style scoped>
.dsn-attr { padding: 12px; } .dim { color: #7d9bc9; font-size: 12px; }
.row-line { display: flex; align-items: center; gap: 8px; }
.dsn-attr :deep(.el-input-number) { width: 100%; }
</style>
