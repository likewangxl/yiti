<template>
  <div class="layout">
    <AppSidebar />
    <div class="main">
      <AppHeader />
      <AppBreadcrumb />
      <div class="content">
        <!-- :key 强制每次切换 fullPath 都重新 mount，避免某些 view 状态残留挡住下次渲染 -->
        <router-view :key="$route.fullPath" />
      </div>
    </div>
  </div>
</template>

<script setup>
import AppSidebar from '@/components/AppSidebar.vue';
import AppHeader from '@/components/AppHeader.vue';
import AppBreadcrumb from '@/components/AppBreadcrumb.vue';
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
}
.content {
  flex: 1;
  overflow: auto;
  padding: 16px 20px;
}
</style>
