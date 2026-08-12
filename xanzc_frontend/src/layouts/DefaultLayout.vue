<template>
  <div class="layout" :class="{ 'layout--sidebar-collapsed': sidebarCollapsed }">
    <AppSidebar :collapsed="sidebarCollapsed" />
    <div class="main">
      <AppHeader :sidebar-collapsed="sidebarCollapsed" @toggle-sidebar="toggleSidebar" />
      <AppBreadcrumb />
      <WorkspaceTabs />
      <main id="app-main" class="content" :class="{ 'content--full': $route.meta.fullBleed }" tabindex="-1">
        <router-view :key="$route.fullPath" v-slot="{ Component }">
          <transition name="page">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import AppSidebar from '@/components/AppSidebar.vue';
import AppHeader from '@/components/AppHeader.vue';
import AppBreadcrumb from '@/components/AppBreadcrumb.vue';
import WorkspaceTabs from '@/components/WorkspaceTabs.vue';

// 折叠态只属于当前壳层实例，避免不同登录会话或浏览器标签页互相串状态。
const sidebarCollapsed = ref(false);
function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value;
}
</script>

<style lang="scss" scoped>
.layout {
  --layout-sidebar-current-width: var(--layout-sidebar-width);
  display: grid;
  grid-template-columns: var(--layout-sidebar-current-width) minmax(0, 1fr);
  grid-template-rows: 100vh;
  width: 100%;
  min-width: 0;
}
.layout--sidebar-collapsed {
  --layout-sidebar-current-width: var(--layout-sidebar-collapsed-width);
}
.main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
  min-height: 0;
}
.content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  padding: var(--layout-content-gutter);
}
// full-bleed 路由(meta.fullBleed,如大屏设计器)去 padding:页面自身用 height:100% 撑满,
// 避免"页面写死 calc(100vh - Npx) 猜壳层高度"导致整页滚动条
.content--full { padding: 0; }
.page-enter-active { transition: opacity var(--motion-fast) var(--ease-enter); }
.page-enter-from { opacity: 0; }
.page-leave-active { display: none; }

@media (min-width: 2400px) {
  .content:not(.content--full) { padding: var(--layout-content-gutter-wide); }
}

@media (prefers-reduced-motion: reduce) {
  .page-enter-active { transition-duration: 1ms; }
}
</style>
