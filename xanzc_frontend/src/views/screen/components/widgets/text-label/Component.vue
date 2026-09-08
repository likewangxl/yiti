<template>
  <div class="w-text" :style="textStyle">{{ p.text }}</div>
</template>
<script setup>
import { computed } from 'vue';
const props = defineProps({ element: { type: Object, required: true } });
const p = computed(() => props.element.propValue || {});
const textStyle = computed(() => ({
  width: '100%', height: '100%', display: 'flex', alignItems: 'center',
  justifyContent: p.value.align === 'center' ? 'center' : p.value.align === 'right' ? 'flex-end' : 'flex-start',
  fontSize: (p.value.fontSize || 22) + 'px', color: p.value.color || '#d5e6ff',
  fontWeight: p.value.bold ? 700 : 400, overflow: 'hidden'
}));
</script>
