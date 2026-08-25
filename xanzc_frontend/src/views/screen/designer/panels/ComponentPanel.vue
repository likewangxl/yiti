<template>
  <div class="dsn-comp-panel">
    <div class="panel-intro">
      <div>
        <span class="panel-kicker">组件库</span>
        <h3>添加到画布</h3>
      </div>
      <span class="panel-hint">拖拽添加</span>
    </div>
    <label class="component-search">
      <span class="search-icon" aria-hidden="true">⌕</span>
      <input v-model="searchTerm" type="search" placeholder="搜索组件" aria-label="搜索组件" />
      <button v-if="searchTerm" type="button" class="search-clear" aria-label="清除组件搜索" @click="searchTerm = ''">×</button>
    </label>

    <template v-for="group in filteredGroups" :key="group.key">
      <section class="component-group" :aria-labelledby="`component-group-${group.key}`">
        <header class="group-heading">
          <h4 :id="`component-group-${group.key}`">{{ group.title }}</h4>
          <span class="group-count" :data-testid="`component-count-${group.key}`">{{ group.items.length }}</span>
        </header>
        <div v-if="group.items.length" class="grp">
          <div v-for="item in group.items" :key="item.key" class="cell" draggable="true"
               :data-component="item.dataComponent" :data-inner-type="item.innerType || undefined"
               :aria-label="`拖拽添加${item.label}`"
               @dragstart="onDrag($event, item.component, item.innerType)">
            <span class="ic" aria-hidden="true">
              <ChartTypeIcon v-if="item.innerType" :inner-type="item.innerType" />
              <template v-else>{{ item.icon }}</template>
            </span>
            <span class="cell-label">{{ item.label }}</span>
          </div>
        </div>
      </section>
    </template>

    <div v-if="!visibleCount" class="component-empty" data-testid="component-empty" role="status">
      <span class="empty-icon" aria-hidden="true">⌕</span>
      <strong>没有找到匹配组件</strong>
      <span>试试组件名称或类型</span>
    </div>
  </div>
</template>
<script setup>
// 组件面板:素材(TextLabel/ImageBox/...)与图表(ChartWidget+innerType)两组,HTML5 拖拽塞 dataTransfer。
// 图表分组按 enabled 过滤——未接入渲染的占位图表类型(见 chart-widget/charts/*.js)不出现在面板,
// 拖拽入口从源头收窄;registry.newComponentFromMeta 的 enabled:false 直接 throw 只是兜底防线(面板/注册表两头防漏)。
import { computed, ref } from 'vue';
import { materialMetas, chartMetas, mapCenterMeta } from '@/views/screen/designer/widgets';
import ChartTypeIcon from './ChartTypeIcon.vue';
const searchTerm = ref('');
const enabledCharts = computed(() => chartMetas.filter(c => c.enabled !== false));
const groups = computed(() => [
  {
    key: 'material', title: '基础组件',
    items: materialMetas.map(meta => ({
      key: meta.component, component: meta.component, dataComponent: meta.component,
      label: meta.label, icon: meta.icon || '◇'
    }))
  },
  {
    key: 'map', title: '地图组件',
    // MapCenter 的运行时 props 与普通素材不同，保留独立分组与拖拽入口。
    items: [{ key: mapCenterMeta.component, component: 'MapCenter', dataComponent: 'MapCenter',
      label: mapCenterMeta.label, icon: mapCenterMeta.icon || '◇' }]
  },
  {
    key: 'chart', title: '图表组件',
    items: enabledCharts.value.map(meta => ({
      key: meta.innerType, component: 'ChartWidget', dataComponent: meta.innerType,
      innerType: meta.innerType, label: meta.label, icon: meta.icon || '▤'
    }))
  }
]);
function normalize(value) { return String(value || '').trim().toLocaleLowerCase(); }
const filteredGroups = computed(() => {
  const query = normalize(searchTerm.value);
  return groups.value.map(group => ({
    ...group,
    items: query
      ? group.items.filter(item => [item.label, item.component, item.innerType].some(value => normalize(value).includes(query)))
      : group.items
  }));
});
const visibleCount = computed(() => filteredGroups.value.reduce((sum, group) => sum + group.items.length, 0));
function onDrag(e, component, innerType = '') {
  e.dataTransfer.setData('component', component);
  e.dataTransfer.setData('innerType', innerType);
  e.dataTransfer.effectAllowed = 'copy';
}
</script>
<style scoped>
.dsn-comp-panel {
  --panel-bg: var(--dsn2-bg-panel, #0d1628);
  --panel-elevated: var(--dsn2-bg-elevated, #111a2e);
  --panel-border: var(--dsn2-border-soft, rgba(124, 154, 190, .2));
  --panel-text: var(--dsn2-text, #e8eef8);
  --panel-muted: var(--dsn2-muted, #8797ae);
  --panel-accent: var(--dsn2-accent, #52c7c3);
  padding: 4px 10px 16px;
  color: var(--panel-text);
}
.panel-intro {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 2px 12px;
}
.panel-kicker, .dsn-comp-panel h4 {
  color: var(--panel-muted);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: .08em;
  text-transform: uppercase;
}
.panel-intro h3 {
  margin: 3px 0 0;
  color: var(--panel-text);
  font-size: 15px;
  font-weight: 650;
  letter-spacing: .01em;
}
.panel-hint { color: var(--panel-muted); font-size: 11px; padding-top: 4px; white-space: nowrap; }
.component-search {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 9px;
  border: 1px solid var(--panel-border);
  border-radius: 9px;
  background: color-mix(in srgb, var(--panel-bg) 92%, white 8%);
  transition: border-color .16s ease, box-shadow .16s ease;
}
.component-search:focus-within {
  border-color: color-mix(in srgb, var(--panel-accent) 70%, transparent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--panel-accent) 16%, transparent);
}
.search-icon { color: var(--panel-muted); font-size: 17px; line-height: 1; }
.component-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--panel-text);
  font: inherit;
  font-size: 12px;
}
/* 保留 type=search 语义，但只显示面板自定义的清除按钮，避免 Chromium/WebKit 双重 ×。 */
.component-search input::-webkit-search-cancel-button,
.component-search input::-webkit-search-decoration {
  -webkit-appearance: none;
  appearance: none;
  display: none;
}
.component-search input::placeholder { color: var(--panel-muted); }
.search-clear {
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--panel-muted);
  cursor: pointer;
  font-size: 16px;
  line-height: 18px;
}
.search-clear:hover { color: var(--panel-text); background: rgba(255,255,255,.06); }
.search-clear:focus-visible, .cell:focus-visible { outline: 2px solid var(--panel-accent); outline-offset: 2px; }
.component-group { margin-top: 17px; }
.group-heading { display: flex; align-items: center; justify-content: space-between; margin: 0 2px 7px; }
.group-heading h4 { margin: 0; color: var(--panel-muted); font-size: 11px; font-weight: 600; letter-spacing: .08em; }
.group-count {
  display: inline-flex;
  min-width: 20px;
  height: 20px;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  border: 1px solid var(--panel-border);
  border-radius: 99px;
  color: var(--panel-muted);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}
.grp { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px; }
.cell {
  display: flex;
  min-width: 0;
  min-height: 76px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 9px 5px 7px;
  border: 1px solid var(--panel-border);
  border-radius: 9px;
  background: linear-gradient(145deg, color-mix(in srgb, var(--panel-elevated) 94%, white 6%), var(--panel-bg));
  color: var(--panel-text);
  cursor: grab;
  font-size: 12px;
  text-align: center;
  transition: border-color .16s ease, background .16s ease, transform .16s ease, box-shadow .16s ease;
  user-select: none;
}
.cell:hover { border-color: color-mix(in srgb, var(--panel-accent) 62%, transparent); background: var(--panel-elevated); box-shadow: 0 8px 18px rgba(0,0,0,.16); transform: translateY(-1px); }
.cell:active { cursor: grabbing; transform: translateY(0); }
.ic { display: inline-flex; width: 28px; height: 28px; align-items: center; justify-content: center;
  color: var(--panel-accent); font-size: 20px; line-height: 1; }
.cell-label { overflow: hidden; max-width: 100%; text-overflow: ellipsis; white-space: nowrap; }
.component-empty {
  display: flex;
  min-height: 150px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: var(--panel-muted);
  font-size: 11px;
  text-align: center;
}
.component-empty strong { color: var(--panel-text); font-size: 12px; font-weight: 600; }
.empty-icon { color: var(--panel-accent); font-size: 23px; opacity: .8; }
@media (prefers-reduced-motion: reduce) {
  .dsn-comp-panel *, .dsn-comp-panel *::before, .dsn-comp-panel *::after {
    animation-duration: .01ms !important;
    transition-duration: .01ms !important;
  }
}
</style>
