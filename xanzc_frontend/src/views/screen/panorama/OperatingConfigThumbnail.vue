<template>
  <RuntimeConfigThumbnail v-bind="$props" test-id="operating-config-thumbnail" @select="$emit('select', $event)" />
</template>
<script setup>
import RuntimeConfigThumbnail from './RuntimeConfigThumbnail.vue';
defineProps({
  entries: { type: Array, default: () => [] }, template: { type: String, default: 'branch-overview-v1' },
  screenTitle: { type: String, default: '经营大屏' }, selectedKey: { type: String, default: '' },
  disabled: { type: Boolean, default: false }, presentation: { type: Object, default: null }, context: { type: String, default: 'screen' },
  canvasWidth: { type: Number, default: 1920 }, canvasHeight: { type: Number, default: 1080 },
  viewLevel: { type: String, default: '' }, footerText: { type: String, default: '标题实时展示 · 数据仅占位' }
});
defineEmits(['select']);
</script>
