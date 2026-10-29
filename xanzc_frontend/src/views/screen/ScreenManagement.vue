<template>
  <div class="screen-management">
    <nav class="screen-management__tabs" aria-label="大屏管理分类">
      <button type="button" data-tab="business" :aria-pressed="activeTab === 'business'" @click="activeTab = 'business'">机构经营大屏</button>
      <button type="button" data-tab="personal" :aria-pressed="activeTab === 'personal'" @click="activeTab = 'personal'">个人经营驾驶舱</button>
    </nav>
    <KeepAlive>
      <component :is="activeTab === 'personal' ? PersonalDashboardSettings : PanoramaBindings" />
    </KeepAlive>
  </div>
</template>

<script setup>
import { defineAsyncComponent, ref } from 'vue';
import { useRoute } from 'vue-router';

const PanoramaBindings = defineAsyncComponent(() => import('./panorama/PanoramaBindings.vue').then(module => module.default));
const PersonalDashboardSettings = defineAsyncComponent(() => import('./personal/PersonalDashboardSettings.vue').then(module => module.default));
const route = useRoute();
const activeTab = ref(route.query?.tab === 'personal' ? 'personal' : 'business');
</script>

<style scoped>
.screen-management { min-height: 100vh; background: #f4f7fb; }
.screen-management__tabs { display: flex; gap: 8px; flex-wrap: wrap; padding: 16px 24px 0; }
.screen-management__tabs button { padding: 10px 20px; border: 1px solid #d3dfed; border-radius: 8px; color: #40536c; background: #fff; cursor: pointer; font: inherit; }
.screen-management__tabs button[aria-pressed="true"] { border-color: #2d65c8; background: #eaf2ff; color: #2458b3; font-weight: 600; }
.screen-management__tabs button:focus-visible { outline: 2px solid #2d65c8; outline-offset: 3px; }
.screen-management:has(.panorama-bindings--visual) .screen-management__tabs { padding:8px 20px 0; gap:16px; }
.screen-management:has(.panorama-bindings--visual) .screen-management__tabs button { padding:5px 0; border:0; border-bottom:2px solid transparent; border-radius:0; background:transparent; font-size:13px; line-height:20px; }
.screen-management:has(.panorama-bindings--visual) .screen-management__tabs button[aria-pressed="true"] { border-bottom-color:#2d63dc; color:#2d63dc; }
</style>
