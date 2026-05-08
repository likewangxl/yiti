<template>
  <nav class="crumb">
    <span>🏠</span>
    <template v-for="(c, i) in items" :key="i">
      <span class="sep">/</span>
      <span :class="{ last: i === items.length - 1 }">{{ c }}</span>
    </template>
  </nav>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const items = computed(() => {
  const r = route.matched[route.matched.length - 1];
  if (!r?.meta?.title) return [];
  return r.meta.group ? [r.meta.group, r.meta.title] : [r.meta.title];
});
</script>

<style lang="scss" scoped>
.crumb {
  background: #fff;
  height: $crumb-h;
  border-bottom: 1px solid $border-1;
  display: flex; align-items: center;
  padding: 0 20px;
  font-size: 12px;
  color: $text-3;
  gap: 6px;
  flex-shrink: 0;
  .sep { color: $text-4; }
  .last { color: $text-1; font-weight: 500; }
}
</style>
