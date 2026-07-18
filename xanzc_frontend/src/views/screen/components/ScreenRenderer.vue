<template>
  <div class="scr-canvas-render" :style="stageCss">
    <div v-for="c in components" :key="c.id" class="scr-abs"
         :style="absStyle(c)" v-show="c.isShow !== false">
      <!-- ChartWidget:注入 bindSnapshot 后复用 BlockContainer 取数;快照缺失(如草稿预览态
           bindSnapshots 恒空,详见后端 composeDraftPreview)不裸传 null 给 BlockContainer(会
           空指针崩溃),渲染中性占位。 -->
      <BlockContainer v-if="c.component === 'ChartWidget' && blockOf(c)" :block="blockOf(c)" :context="context"
                      :prop-value="c.propValue || {}" />
      <div v-else-if="c.component === 'ChartWidget'" class="scr-abs-empty">暂无预览数据</div>
      <!-- MapCenter 走独立分支:其 props 契约是 mapPoints 数组(来自 ScreenRenderRespDTO.mapPoints，
           PROVINCE 屏实时回填)，与素材类 widgets 的 element/propValue 签名不同，不经 widgetOf 通用注册。 -->
      <MapCenter v-else-if="c.component === 'MapCenter'" :map-points="mapPoints" />
      <component v-else :is="widgetOf(c.component)" :element="c" mode="runtime" />
    </div>
  </div>
</template>
<script setup>
// 运行时渲染——读发布态渲染包(canvasStyle + components + bindSnapshots),组件绝对定位铺在
// 1920×1080 舞台(外层 ScreenView 已 transform: scale 整体缩放，这里只按设计态像素绝对定位)。
// 已删除旧「region/row/block flex 布局」分支(3 屏直接切换，无 fallback)。
import { computed } from 'vue';
import BlockContainer from './BlockContainer.vue';
import MapCenter from './MapCenter.vue';
import { findWidget } from '@/views/screen/designer/widgets';
import { canvasBackgroundStyle, componentBackgroundStyle } from '@/views/screen/designer/utils/background';

const props = defineProps({
  renderPackage: { type: Object, default: () => ({ components: [], bindSnapshots: {}, canvasStyle: {} }) },
  mapPoints: { type: Array, default: () => [] },
  context: { type: Object, default: () => ({}) }
});
/**
 * 渲染列表:Group 成组节点(设计器多选成组产物)在运行时只是坐标容器,无自身视觉——
 * 展开为"绝对坐标子节点"(组左上角 + 子相对坐标,透明度相乘)后走既有按 component 分派分支,
 * 模板零改动(最小适配)。组隐藏则子组件整体不渲染;组不嵌套(设计器 makeGroup 已保证)。
 */
const components = computed(() => {
  const out = [];
  for (const c of (props.renderPackage.components || [])) {
    if (c.component === 'Group') {
      if (c.isShow === false) continue;
      const gs = c.style || {};
      for (const ch of (c.children || [])) {
        const cs = ch.style || {};
        out.push({ ...ch, style: { ...cs,
          top: (gs.top ?? 0) + (cs.top ?? 0),
          left: (gs.left ?? 0) + (cs.left ?? 0),
          opacity: (gs.opacity ?? 1) * (cs.opacity ?? 1) } });
      }
    } else {
      out.push(c);
    }
  }
  return out;
});
const stageCss = computed(() => ({
  position: 'relative', width: '1920px', height: '1080px',
  // 背景三选一(纯色/渐变/图片)与设计器画布共用同一纯函数;solid 无色值兜底 transparent(既有契约)
  ...canvasBackgroundStyle(props.renderPackage.canvasStyle || {}, 'transparent')
}));
function absStyle(c) {
  // c.style||{} 兜底:防脏渲染包节点缺 style 时 undefined.top 报错(rev-t10 复审 Minor)
  const s = c.style || {};
  return { position: 'absolute', top: (s.top ?? 0) + 'px', left: (s.left ?? 0) + 'px',
    width: (s.width ?? 0) + 'px', height: (s.height ?? 0) + 'px',
    opacity: s.opacity ?? 1,
    // 组件级背景(CommonAttr 外观区:透明/纯色/渐变),缺省空对象与现状零差异
    ...componentBackgroundStyle(s) };
}
function widgetOf(component) { return findWidget(component); }
/** 从 bindSnapshots 合成 BlockContainer 需要的 block(bindJson/styleJson/drillJson 字符串);
 *  快照缺失时返回 null，由模板 v-if 隔离，不直接传给 BlockContainer。 */
function blockOf(c) {
  const snap = (props.renderPackage.bindSnapshots || {})[String(c.blockId)];
  if (!snap) return null;
  return {
    id: c.blockId,
    componentType: c.innerType || snap.componentType,
    bindJson: JSON.stringify(snap.bind || {}),
    styleJson: JSON.stringify(snap.styleCfg || {}),
    drillJson: JSON.stringify(snap.drill || {})
  };
}
</script>
<style scoped>
.scr-canvas-render { transform-origin: top left; }
.scr-abs { overflow: hidden; }
.scr-abs-empty {
  width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;
  color: var(--scr-text-dim, #7d9bc9); font-size: 13px;
  border: 1px dashed rgba(96, 148, 214, .38); box-sizing: border-box;
}
</style>
