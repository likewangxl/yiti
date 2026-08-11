<template>
  <section class="workspace-tabs" aria-label="工作区页签">
    <div class="workspace-tabs__scroll">
      <div ref="tabsListRef" class="workspace-tabs__list" role="tablist">
        <div
          v-for="tab in tabs"
          :key="tab.key"
          class="workspace-tabs__tab"
          :class="{ 'workspace-tabs__tab--active': tab.key === activeKey }"
          :data-tab-key="tab.key"
          role="tab"
          :aria-selected="tab.key === activeKey"
        >
          <button
            type="button"
            class="workspace-tabs__label"
            :title="tab.title"
            @click="selectTab(tab)"
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
  </section>
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
  height: 76px;
  flex: 0 0 76px;
  min-width: 0;
  overflow: hidden;
  background: #eef2f6;
  border-bottom: 1px solid $border-1;
  // 对齐截图：页签本体 44px，下方留出更宽的工作区分隔带。
  padding: 9px 20px 22px;
}

.workspace-tabs__scroll {
  width: 100%;
  height: 44px;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
  scrollbar-color: $border-2 transparent;

  &::-webkit-scrollbar { height: 6px; }
  &::-webkit-scrollbar-track { background: transparent; }
  &::-webkit-scrollbar-thumb { background: $border-2; border-radius: 3px; }
}

.workspace-tabs__list {
  display: flex;
  align-items: stretch;
  gap: 6px;
  width: max-content;
  min-width: 100%;
  height: 100%;
}

.workspace-tabs__tab {
  display: inline-flex;
  align-items: center;
  min-width: 116px;
  max-width: 220px;
  height: 100%;
  padding: 0 9px 0 14px;
  color: $text-3;
  font-size: 16px;
  background: rgba(255, 255, 255, .62);
  border: 1px solid $border-1;
  border-top: 2px solid transparent;
  border-radius: 4px 4px 0 0;
  transition: color .15s ease, background-color .15s ease, border-color .15s ease;

  &:hover {
    color: $primary;
    background: #fff;
  }
}

.workspace-tabs__tab--active {
  color: $primary;
  background: #fff;
  border-color: $border-1;
  border-top-color: $primary;
  box-shadow: 0 -1px 0 rgba(0, 61, 122, .08);
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
  width: 20px;
  height: 20px;
  margin-left: 5px;
  padding: 2px;
  color: $text-4;
  background: transparent;
  border: 0;
  border-radius: 50%;
  cursor: pointer;

  :deep(svg) { width: 13px; height: 13px; }

  &:hover {
    color: $danger;
    background: #fef2f2;
  }
}
</style>
