<template>
  <aside id="app-sidebar" class="side" :class="{ 'side--collapsed': props.collapsed }">
    <div class="logo" :title="props.collapsed ? '银行营销平台' : undefined">
      <span class="mark">银</span>
      <span class="logo-name" :aria-hidden="props.collapsed ? 'true' : undefined">银行营销平台</span>
    </div>

    <nav class="side-nav" aria-label="主导航" :aria-busy="loading ? 'true' : undefined">
      <div v-loading="loading" class="side-nav__content">
        <template v-for="m in menus" :key="m.resourceId">
          <!-- 顶层叶子菜单（无子节点）—— 直接单项 -->
          <router-link
            v-if="!m.children || !m.children.length"
            :to="m.resourceUrl"
            class="item root-item"
            :class="{ active: isCurrentMenu(m) }"
            :aria-current="isCurrentMenu(m) ? 'page' : undefined"
            :aria-label="m.menuName"
            :title="m.menuName"
          >
            <span class="nav-icon" aria-hidden="true"><svg viewBox="0 0 24 24" focusable="false"><path d="M5 5h14v14H5zM8 9h8M8 13h8" /></svg></span>
            <span class="item-label">{{ m.menuName }}</span>
          </router-link>

          <!-- 分组节点（有 children）—— button 维护展开状态；折叠侧栏时点击后显示可访问的浮出子菜单。 -->
          <div v-else class="menu-group">
            <button
              type="button"
              class="parent"
              :class="{ open: isGroupOpen(m.resourceId) }"
              :data-menu-group="m.resourceId"
              :aria-controls="groupPanelId(m.resourceId)"
              :aria-expanded="isGroupOpen(m.resourceId) ? 'true' : 'false'"
              :aria-label="m.menuName"
              :title="m.menuName"
              @click="toggle(m.resourceId, $event)"
            >
              <span class="nav-icon" aria-hidden="true"><svg viewBox="0 0 24 24" focusable="false"><path d="M4 6h16M4 12h16M4 18h16" /></svg></span>
              <span class="item-label">{{ m.menuName }}</span>
              <span class="chev" aria-hidden="true">▸</span>
            </button>
            <div
              v-if="!props.collapsed"
              v-show="isGroupOpen(m.resourceId)"
              :id="groupPanelId(m.resourceId)"
              class="children"
              role="group"
              :aria-label="`${m.menuName}子菜单`"
            >
              <router-link
                v-for="c in m.children" :key="c.resourceId"
                :to="c.resourceUrl"
                class="item"
                :class="{ active: isCurrentMenu(c) }"
                :aria-current="isCurrentMenu(c) ? 'page' : undefined"
                :aria-label="c.menuName"
                :title="c.menuName"
              >
                <span class="nav-icon" aria-hidden="true"><svg viewBox="0 0 24 24" focusable="false"><circle cx="12" cy="12" r="3" /></svg></span>
                <span class="item-label">{{ c.menuName }}</span>
              </router-link>
            </div>
            <!-- 浮出菜单 Teleport 到 body，避免被侧栏的纵向滚动容器横向裁切。 -->
            <Teleport v-else to="body">
              <div
                v-if="isGroupOpen(m.resourceId)"
                :id="groupPanelId(m.resourceId)"
                :ref="setCollapsedFlyoutElement"
                class="children children--flyout"
                :style="collapsedFlyoutStyle"
                role="group"
                :aria-label="`${m.menuName}子菜单`"
              >
                <router-link
                  v-for="c in m.children" :key="c.resourceId"
                  :to="c.resourceUrl"
                  class="item"
                  :class="{ active: isCurrentMenu(c) }"
                  :aria-current="isCurrentMenu(c) ? 'page' : undefined"
                  :aria-label="c.menuName"
                  :title="c.menuName"
                >
                  <span class="nav-icon" aria-hidden="true"><svg viewBox="0 0 24 24" focusable="false"><circle cx="12" cy="12" r="3" /></svg></span>
                  <span class="item-label">{{ c.menuName }}</span>
                </router-link>
              </div>
            </Teleport>
          </div>
        </template>
      </div>
    </nav>
  </aside>
</template>

<script setup>
import { reactive, computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useMenuStore } from '@/stores/menu';

const route = useRoute();
const menuStore = useMenuStore();
const props = defineProps({
  collapsed: { type: Boolean, default: false }
});
// 直接消费共享 store 的菜单树；与面包屑/PageTitle 同源，改名 force 刷新后一并更新
const menus = computed(() => menuStore.tree);
const loading = computed(() => menuStore.loading);
const openMap = reactive({});
const collapsedGroupId = ref(null);
const collapsedFlyoutElement = ref(null);
const collapsedFlyoutStyle = ref({});
const collapsedTriggerElement = ref(null);
const FLYOUT_VIEWPORT_GUTTER = 8;

function findCurrentResourceId(nodes) {
  for (const node of nodes || []) {
    const childMatch = findCurrentResourceId(node.children);
    if (childMatch) return childMatch;
    if (node.resourceId != null && node.resourceUrl === route.path) return String(node.resourceId);
  }
  return '';
}

const activeResourceId = computed(() => findCurrentResourceId(menus.value));
function isCurrentMenu(menu) {
  return menu?.resourceId != null && String(menu.resourceId) === activeResourceId.value;
}

function groupPanelId(resourceId) {
  return `sidebar-group-${String(resourceId)}`;
}

// 默认展开全部分组节点（按 resourceId）；tree 变化（首次加载/改名刷新）后重建展开态
function initOpen() {
  for (const m of menus.value || []) {
    // 仅对首次见到的分组设默认展开，避免 tree 刷新时覆盖用户已手动折叠的状态
    if (m.children && m.children.length && !(m.resourceId in openMap)) openMap[m.resourceId] = true;
  }
}
onMounted(() => {
  menuStore.load().catch(() => {
    // store 已清空旧菜单并记录错误；侧栏保持空态，等待下一次显式重试。
  });
  window.addEventListener('resize', updateCollapsedFlyoutPosition);
  // scroll 事件不冒泡，使用捕获阶段可同时响应侧栏与页面上的滚动容器。
  document.addEventListener('scroll', updateCollapsedFlyoutPosition, true);
});
onBeforeUnmount(() => {
  window.removeEventListener('resize', updateCollapsedFlyoutPosition);
  document.removeEventListener('scroll', updateCollapsedFlyoutPosition, true);
});
watch(() => menuStore.tree, () => {
  initOpen();
  nextTick(updateCollapsedFlyoutPosition);
}, { immediate: true });
watch(() => props.collapsed, (collapsed) => {
  if (!collapsed) {
    collapsedGroupId.value = null;
    collapsedTriggerElement.value = null;
    collapsedFlyoutStyle.value = {};
  }
});

function isGroupOpen(id) {
  return props.collapsed ? collapsedGroupId.value === id : Boolean(openMap[id]);
}

function setCollapsedFlyoutElement(element) {
  collapsedFlyoutElement.value = element;
}

function updateCollapsedFlyoutPosition() {
  if (!props.collapsed || collapsedGroupId.value == null || !collapsedTriggerElement.value) return;

  const triggerRect = collapsedTriggerElement.value.getBoundingClientRect();
  const viewportHeight = window.innerHeight;
  const maxHeight = Math.max(0, viewportHeight - FLYOUT_VIEWPORT_GUTTER * 2);
  const flyoutHeight = Math.min(collapsedFlyoutElement.value?.getBoundingClientRect().height || 0, maxHeight);
  const top = Math.max(
    FLYOUT_VIEWPORT_GUTTER,
    Math.min(triggerRect.top, viewportHeight - FLYOUT_VIEWPORT_GUTTER - flyoutHeight)
  );

  collapsedFlyoutStyle.value = {
    top: `${top}px`,
    left: `${Math.max(FLYOUT_VIEWPORT_GUTTER, triggerRect.right + FLYOUT_VIEWPORT_GUTTER)}px`,
    maxHeight: `${maxHeight}px`
  };
}

function toggle(id, event) {
  if (props.collapsed) {
    if (collapsedGroupId.value === id) {
      collapsedGroupId.value = null;
      collapsedTriggerElement.value = null;
      collapsedFlyoutStyle.value = {};
      return;
    }
    collapsedGroupId.value = id;
    collapsedTriggerElement.value = event.currentTarget;
    nextTick(updateCollapsedFlyoutPosition);
    return;
  }
  openMap[id] = !openMap[id];
}
</script>

<style lang="scss" scoped>
.side {
  width: 100%;
  background: var(--color-sidebar-bg);
  color: var(--color-sidebar-text);
  overflow-y: auto;
  overflow-x: visible;
  border-right: 1px solid var(--color-sidebar-border);
  transition: none;
  &::-webkit-scrollbar { width: 8px; }
  &::-webkit-scrollbar-track { background: rgba(255,255,255,.05); }
  &::-webkit-scrollbar-thumb { background: rgba(255,255,255,.3); border-radius: 4px; &:hover { background: rgba(255,255,255,.5); } }
}
.logo {
  height: var(--layout-header-height);
  display: flex; align-items: center; gap: 10px;
  padding: 0 var(--space-4);
  color: var(--color-surface); font-weight: 600; font-size: 14px;
  border-bottom: 1px solid var(--color-sidebar-border);
  letter-spacing: .3px;
  position: sticky; top: 0; background: var(--color-sidebar-bg); z-index: var(--z-sticky);
  .mark {
    width: 26px; height: 26px; border-radius: 4px;
    background: linear-gradient(135deg, var(--color-brand-500), var(--color-brand-700));
    display: grid; place-items: center;
    color: var(--color-surface); font-size: 13px; font-weight: 700;
    flex-shrink: 0;
  }
}
.side-nav { min-height: 0; }
.side-nav__content { padding: var(--space-2) 0; }
.menu-group { position: relative; }
.parent {
  width: 100%;
  min-height: 40px;
  padding: var(--space-2) var(--space-4);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  color: var(--color-sidebar-text);
  background: transparent;
  border: 0;
  display: flex; align-items: center; gap: var(--space-2);
  text-align: left;
  &:hover { color: var(--color-surface); background: var(--color-sidebar-hover); }
  .chev { margin-left: auto; font-size: 10px; opacity: .75; transition: transform var(--motion-fast) var(--ease-enter); }
  &.open .chev { transform: rotate(90deg); }
}
.item {
  min-height: 40px;
  padding: var(--space-2) var(--space-4) var(--space-2) 30px;
  font-size: 14px;
  cursor: pointer;
  display: flex; align-items: center; gap: var(--space-2);
  border-left: 2px solid transparent;
  white-space: nowrap;
  text-decoration: none;
  color: var(--color-sidebar-text);
  &:hover { background: var(--color-sidebar-hover); color: var(--color-surface); }
  &.active {
    background: linear-gradient(90deg, var(--color-sidebar-active) 0%, var(--color-sidebar-hover) 100%);
    color: var(--color-surface);
    border-left-color: var(--color-brand-500);
  }
}
.root-item { padding-left: var(--space-4); }
.nav-icon {
  display: inline-grid;
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  place-items: center;
  opacity: .9;
  svg { width: 16px; height: 16px; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
}
.children--flyout {
  position: fixed;
  z-index: var(--z-popover);
  width: var(--layout-sidebar-width);
  padding: var(--space-2) 0;
  overflow-x: hidden;
  overflow-y: auto;
  background: var(--color-sidebar-bg);
  border: 1px solid var(--color-sidebar-border);
  box-shadow: var(--shadow-popover);
}
.side--collapsed {
  .logo,
  .parent,
  .root-item { justify-content: center; padding-right: var(--space-2); padding-left: var(--space-2); }
  .logo-name,
  .item-label,
  .chev { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .parent .chev { transition-duration: 1ms; }
}
</style>
