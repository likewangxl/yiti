<template>
  <nav class="crumb" aria-label="面包屑">
    <ol class="crumb-list">
      <li class="crumb-home" aria-hidden="true">
        <svg viewBox="0 0 24 24" focusable="false"><path d="m4 11 8-7 8 7v9h-5v-6H9v6H4z" /></svg>
      </li>
      <li
        v-for="(c, i) in items"
        :key="`${c}-${i}`"
        class="crumb-item"
        :class="{ last: i === items.length - 1 }"
        :aria-current="i === items.length - 1 ? 'page' : undefined"
      >
        {{ c }}
      </li>
    </ol>
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
  background: var(--color-surface);
  height: var(--layout-breadcrumb-height);
  border-bottom: 1px solid var(--color-border);
  display: flex; align-items: center;
  padding: 0 var(--layout-content-gutter);
  font-size: 12px;
  color: var(--color-text-muted);
  flex-shrink: 0;
}
.crumb-list {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  list-style: none;
}
.crumb-home {
  display: inline-grid;
  width: 16px;
  height: 16px;
  place-items: center;
  color: var(--color-text-muted);
  svg { width: 14px; height: 14px; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
}
.crumb-item {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  white-space: nowrap;
  &::before { padding-right: 6px; color: var(--color-text-muted); content: '/'; }
  &.last { overflow: hidden; color: var(--color-text-strong); font-weight: 500; text-overflow: ellipsis; }
}
</style>
