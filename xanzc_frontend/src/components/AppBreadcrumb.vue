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
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useMenuStore } from '@/stores/menu';

const route = useRoute();
const menuStore = useMenuStore();
onMounted(() => menuStore.load());

const items = computed(() => {
  const matched = route.matched[route.matched.length - 1];
  // 命中菜单 → 用 DB 名 + 分组（分组可能为 null）；未命中 → 整体退回静态 meta
  const db = menuStore.resolve(route.path);
  const title = db ? db.title : matched?.meta?.title;
  const group = db ? db.group : matched?.meta?.group;
  if (!title) return [];
  return group ? [group, title] : [title];
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
