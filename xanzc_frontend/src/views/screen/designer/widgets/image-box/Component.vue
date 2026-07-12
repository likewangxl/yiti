<template>
  <div class="w-image">
    <img v-if="p.url" :src="p.url" :style="{ width: '100%', height: '100%', objectFit: p.fit || 'contain' }" />
    <div v-else class="w-image-empty">图片(未设置 URL)</div>
  </div>
</template>
<script setup>
import { computed } from 'vue';
const props = defineProps({ element: { type: Object, required: true } });
const p = computed(() => props.element.propValue || {});
</script>
<style scoped>
.w-image { width: 100%; height: 100%; }
.w-image-empty { width: 100%; height: 100%; display: flex; align-items: center;
  justify-content: center; color: #7d9bc9; font-size: 13px; border: 1px dashed rgba(96,148,214,.38); }
</style>
