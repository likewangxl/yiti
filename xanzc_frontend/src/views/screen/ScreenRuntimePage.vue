<template>
  <PersonalDashboardPage v-if="route.params.screenCode === 'PERSONAL_COCKPIT'" />
  <ScreenView v-else />
</template>

<script setup>
import { defineAsyncComponent } from 'vue';
import { useRoute } from 'vue-router';
const route = useRoute();
const PersonalDashboardPage = defineAsyncComponent(() => import('./personal/PersonalDashboardPage.vue').then(module => module.default));
const ScreenView = defineAsyncComponent(() => import('./ScreenView.vue').then(module => module.default));
</script>
