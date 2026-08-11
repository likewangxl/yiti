<template>
  <div class="layout">
    <AppSidebar />
    <div class="main">
      <AppHeader />
      <AppBreadcrumb />
      <WorkspaceTabs />
      <div class="content" :class="{ 'content--full': $route.meta.fullBleed }">
        <router-view :key="$route.fullPath" v-slot="{ Component }">
          <transition name="page">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup>
import AppSidebar from '@/components/AppSidebar.vue';
import AppHeader from '@/components/AppHeader.vue';
import AppBreadcrumb from '@/components/AppBreadcrumb.vue';
import WorkspaceTabs from '@/components/WorkspaceTabs.vue';
</script>

<style lang="scss" scoped>
.layout {
  display: grid;
  grid-template-columns: $side-w 1fr;
  grid-template-rows: 100vh;
  width: 100%;
}
.main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}
.content {
  flex: 1;
  min-width: 0;
  overflow: auto;
  padding: 16px 20px;
}
// full-bleed 路由(meta.fullBleed,如大屏设计器)去 padding:页面自身用 height:100% 撑满,
// 避免"页面写死 calc(100vh - Npx) 猜壳层高度"导致整页滚动条
.content--full { padding: 0; }
.page-enter-active { transition: opacity .15s ease; }
.page-enter-from { opacity: 0; }
.page-leave-active { display: none; }
</style>
