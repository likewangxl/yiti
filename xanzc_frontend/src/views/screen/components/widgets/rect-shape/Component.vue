<template>
  <div class="w-rect" :style="rectStyle" />
</template>
<script setup>
import { computed } from 'vue';
const props = defineProps({ element: { type: Object, required: true } });
const p = computed(() => props.element.propValue || {});
const rectStyle = computed(() => ({
  width: '100%', height: '100%', boxSizing: 'border-box',
  background: p.value.bg || 'rgba(10,32,74,.55)',
  border: `${p.value.borderWidth ?? 1}px solid ${p.value.borderColor || 'rgba(0,229,255,.25)'}`,
  borderRadius: (p.value.radius ?? 6) + 'px'
}));
</script>
