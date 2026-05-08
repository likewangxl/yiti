<template>
  <div class="layout">
    <AppSidebar />
    <div class="main">
      <AppHeader />
      <AppBreadcrumb />
      <div class="content">
        <router-view />
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
