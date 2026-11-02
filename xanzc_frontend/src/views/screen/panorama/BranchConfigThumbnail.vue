<template>
  <RuntimeConfigThumbnail v-bind="$props" template="branch-overview-v1" test-id="branch-config-thumbnail" @select="$emit('select', $event)" />
</template>
<script setup>
import RuntimeConfigThumbnail from './RuntimeConfigThumbnail.vue';
defineProps({
  entries: { type: Array, default: () => [] }, screenTitle: { type: String, default: '分行经营总览' },
  selectedKey: { type: String, default: '' }, selectedLabel: { type: String, default: '' }, disabled: { type: Boolean, default: false },
  presentation: { type: Object, default: null }, context: { type: String, default: 'screen' },
  canvasWidth: { type: Number, default: 1920 }, canvasHeight: { type: Number, default: 1080 },
  footerText: { type: String, default: '标题实时展示 · 数据仅占位' }
});
defineEmits(['select']);
</script>
