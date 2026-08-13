<template>
  <nav class="workspace-tabs" aria-label="工作区页签">
    <div class="workspace-tabs__scroll">
      <div ref="tabsListRef" class="workspace-tabs__list">
        <div
          v-for="tab in tabs"
          :key="tab.key"
          class="workspace-tabs__tab"
          :class="{ 'workspace-tabs__tab--active': tab.key === activeKey }"
          :data-tab-key="tab.key"
        >
          <button
            type="button"
            class="workspace-tabs__label"
            :title="tab.title"
            :aria-current="tab.key === activeKey ? 'page' : undefined"
            @click="selectTab(tab)"
            @keydown="handleTabKeydown($event, tab)"
          >
            {{ tab.title }}
          </button>
          <button
            v-if="tab.closable"
            type="button"
            class="workspace-tabs__close"
            :aria-label="`关闭 ${tab.title}`"
            @click.stop="closeTab(tab)"
          >
            <Close aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>
  </nav>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Close } from '@element-plus/icons-vue';
import { useMenuStore } from '@/stores/menu';
import { resolveTabKey, useWorkspaceTabsStore } from '@/stores/workspaceTabs';

const route = useRoute();
const router = useRouter();
const menuStore = useMenuStore();
const tabsStore = useWorkspaceTabsStore();
const tabsListRef = ref(null);
const tabs = computed(() => tabsStore.tabs);
const activeKey = computed(() => resolveTabKey(route));

/** 页签较多时保证当前页签始终可见，不干扰页面纵向滚动。 */
function scrollActiveTabIntoView() {
  const activeTab = tabsListRef.value?.querySelector('.workspace-tabs__tab--active');
  activeTab?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
}

// DefaultLayout 挂载期间每次业务路由变化都会登记一次，重复地址由 store 去重。
watch(
  () => [
    route.path,
    route.fullPath,
    route.name,
    route.meta?.title,
    menuStore.resolve(route.path)?.title
  ],
  () => {
    tabsStore.record(route, menuStore.resolve(route.path)?.title);
    nextTick(scrollActiveTabIntoView);
  },
  { immediate: true }
);

/** 切换到目标页签对应的完整地址（保留查询参数与动态参数）。 */
function selectTab(tab) {
  if (!tab || tab.key === activeKey.value) return;
  router.push(tab.fullPath);
}

/**
 * 在路由页签之间提供方向键导航，Home/End 直达首尾页签。
 * 页签仍是普通按钮，不伪装成 tablist；Enter/Space 继续使用按钮原生激活行为。
 */
function handleTabKeydown(event, tab) {
  const key = event.key;
  const currentIndex = tabs.value.findIndex((item) => item.key === tab.key);
  if (currentIndex < 0) return;

  let targetIndex = currentIndex;
  if (key === 'ArrowLeft') targetIndex = Math.max(0, currentIndex - 1);
  if (key === 'ArrowRight') targetIndex = Math.min(tabs.value.length - 1, currentIndex + 1);
  if (key === 'Home') targetIndex = 0;
  if (key === 'End') targetIndex = tabs.value.length - 1;
  if (targetIndex === currentIndex) return;

  event.preventDefault();
  const labels = tabsListRef.value?.querySelectorAll('.workspace-tabs__label');
  labels?.[targetIndex]?.focus();
}

/** 关闭页签后，仅在关闭当前页签时导航到 store 选出的相邻页签。 */
function closeTab(tab) {
  if (!tab?.closable) return;
  const wasActive = tab.key === activeKey.value;
  const fallback = tabsStore.close(tab.key);
  if (wasActive && fallback) router.push(fallback.fullPath);
}
</script>

<style lang="scss" scoped>
.workspace-tabs {
  // 组件局部 token 覆盖历史全局默认值，保证壳层总高稳定为 48px。
  --workspace-tabs-height: 48px;
  height: var(--workspace-tabs-height);
  flex: 0 0 var(--workspace-tabs-height);
  min-width: 0;
  overflow: hidden;
  background: var(--color-workspace-strip);
  border-bottom: 1px solid var(--color-border);
  padding: 4px var(--layout-content-gutter) 3px;
}

.workspace-tabs__scroll {
  width: 100%;
  height: 40px;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
  scrollbar-color: var(--color-border-strong) transparent;

  &::-webkit-scrollbar { height: 6px; }
  &::-webkit-scrollbar-track { background: transparent; }
  &::-webkit-scrollbar-thumb { background: var(--color-border-strong); border-radius: 3px; }
}

.workspace-tabs__list {
  display: flex;
  align-items: stretch;
  gap: var(--space-2);
  width: max-content;
  min-width: 100%;
  height: 100%;
}

.workspace-tabs__tab {
  display: inline-flex;
  align-items: center;
  min-width: 112px;
  max-width: 200px;
  height: 40px;
  padding: 0 var(--space-1) 0 var(--space-3);
  color: var(--color-text);
  font-size: 14px;
  background: color-mix(in srgb, var(--color-surface) 62%, transparent);
  border: 1px solid var(--color-border);
  border-top: 2px solid transparent;
  border-radius: 4px 4px 0 0;
  transition: color var(--motion-fast) var(--ease-enter), background-color var(--motion-fast) var(--ease-enter), border-color var(--motion-fast) var(--ease-enter);

  &:hover {
    color: var(--color-brand-700);
    background: var(--color-surface);
  }
}

.workspace-tabs__tab--active {
  color: var(--color-brand-700);
  background: var(--color-surface);
  border-color: var(--color-border);
  border-top-color: var(--color-brand-700);
  box-shadow: 0 -1px 0 color-mix(in srgb, var(--color-brand-700) 8%, transparent);
}

.workspace-tabs__label {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  padding: 0;
  color: inherit;
  background: transparent;
  border: 0;
  font: inherit;
  line-height: 1.4;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.workspace-tabs__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  margin-left: var(--space-1);
  padding: 0;
  color: var(--color-text-muted);
  background: transparent;
  border: 0;
  border-radius: var(--radius-control);
  cursor: pointer;

  :deep(svg) { width: 13px; height: 13px; }

  &:hover {
    color: var(--color-danger-fg);
    background: var(--color-danger-bg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .workspace-tabs__tab { transition-duration: 1ms; }
}
</style>
