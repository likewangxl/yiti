<template>
  <!-- 根节点为 <h1>：DOM 结构与原页面一致，各页 .page-h h1 的 scoped 样式经父作用域仍作用于此根节点 -->
  <h1 class="page-title">{{ display }}<slot /></h1>
</template>

<script setup>
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useMenuStore } from '@/stores/menu';

// title：页面显式覆盖（动态/记录级标题用）
const props = defineProps({
  title: { type: String, default: '' }
});

const route = useRoute();
const menuStore = useMenuStore();
onMounted(() => menuStore.load());

// 优先级：显式覆盖 > DB 菜单名 > 静态 meta.title
const display = computed(() => {
  if (props.title) return props.title;
  const db = menuStore.resolve(route.path);
  if (db) return db.title;
  const matched = route.matched[route.matched.length - 1];
  return matched?.meta?.title || '';
});
</script>
