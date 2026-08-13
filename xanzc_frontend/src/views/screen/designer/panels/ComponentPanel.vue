<template>
  <div class="dsn-comp-panel">
    <div class="grp-title">素材组件</div>
    <div class="grp">
      <div v-for="m in materialMetas" :key="m.component" class="cell" draggable="true"
           @dragstart="onDrag($event, m.component)">
        <span class="ic">{{ m.icon }}</span><span>{{ m.label }}</span>
      </div>
    </div>
    <div class="grp-title">地图组件</div>
    <div class="grp">
      <!-- MapCenter 的运行时 props 与普通素材不同，但仍支持从面板拖入；配置由属性面板写入组件树。 -->
      <div class="cell" draggable="true" @dragstart="onDrag($event, 'MapCenter')">
        <span class="ic">{{ mapCenterMeta.icon }}</span><span>{{ mapCenterMeta.label }}</span>
      </div>
    </div>
    <div class="grp-title">图表组件</div>
    <div class="grp">
      <div v-for="c in enabledCharts" :key="c.innerType" class="cell" draggable="true"
           @dragstart="onDrag($event, 'ChartWidget', c.innerType)">
        <span class="ic">▤</span><span>{{ c.label }}</span>
      </div>
    </div>
  </div>
</template>
<script setup>
// 组件面板:素材(TextLabel/ImageBox/...)与图表(ChartWidget+innerType)两组,HTML5 拖拽塞 dataTransfer。
// 图表分组按 enabled 过滤——未接入渲染的占位图表类型(见 chart-widget/charts/*.js)不出现在面板,
// 拖拽入口从源头收窄;registry.newComponentFromMeta 的 enabled:false 直接 throw 只是兜底防线(面板/注册表两头防漏)。
import { computed } from 'vue';
import { materialMetas, chartMetas, mapCenterMeta } from '@/views/screen/designer/widgets';
const enabledCharts = computed(() => chartMetas.filter(c => c.enabled !== false));
function onDrag(e, component, innerType = '') {
  e.dataTransfer.setData('component', component);
  e.dataTransfer.setData('innerType', innerType);
  e.dataTransfer.effectAllowed = 'copy';
}
</script>
<style scoped>
.dsn-comp-panel { padding: 8px; } .grp-title { color: #00e5ff; font-size: 13px; margin: 10px 4px 6px; }
.grp { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.cell { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 12px 4px;
  background: rgba(10,32,74,.5); border: 1px solid rgba(0,229,255,.18); border-radius: 4px;
  cursor: grab; color: #d5e6ff; font-size: 12px; }
.cell:hover { border-color: #00e5ff; } .ic { font-size: 20px; color: #00e5ff; }
</style>
