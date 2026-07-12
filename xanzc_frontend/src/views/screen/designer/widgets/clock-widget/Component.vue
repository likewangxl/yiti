<template>
  <div class="w-clock" :style="clockStyle">{{ text }}</div>
</template>
<script setup>
// 每秒刷新的时钟——setInterval 驱动 now 更新，onBeforeUnmount 清理防内存泄漏
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import dayjs from 'dayjs';
const props = defineProps({ element: { type: Object, required: true } });
const p = computed(() => props.element.propValue || {});
const now = ref(dayjs());
let timer = null;
onMounted(() => { timer = setInterval(() => { now.value = dayjs(); }, 1000); });
onBeforeUnmount(() => { if (timer) clearInterval(timer); });
const text = computed(() => now.value.format(p.value.format || 'YYYY-MM-DD HH:mm:ss'));
const clockStyle = computed(() => ({
  width: '100%', height: '100%', display: 'flex', alignItems: 'center',
  fontSize: (p.value.fontSize || 18) + 'px', color: p.value.color || '#7d9bc9',
  fontVariantNumeric: 'tabular-nums', overflow: 'hidden'
}));
</script>
