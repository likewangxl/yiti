<template>
  <div class="fs-wrap">
    <div v-for="(t, i) in tiles" :key="i" class="fs-tile" @click="onClick(t)">
      <div class="fs-num">{{ t.value }}</div>
      <div class="fs-name">{{ t.name }}</div>
    </div>
    <div v-if="!tiles.length" class="scr-block-err">暂无流程数据</div>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['item-click']);

const tiles = computed(() => {
  const ni = props.columns.indexOf(props.bind.nameCol);
  const vi = props.columns.indexOf(props.bind.valueCol);
  if (ni < 0 || vi < 0) return [];
  return props.rows.map(r => ({ name: String(r[ni]), value: r[vi], raw: r }));
});

function onClick(t) {
  const row = {};
  props.columns.forEach((c, i) => { row[c] = t.raw[i]; });
  emit('item-click', { col: props.bind.valueCol, label: t.name, row });
}
</script>

<style lang="scss" scoped>
.fs-wrap { display: grid; grid-template-columns: repeat(auto-fit, minmax(110px, 1fr)); gap: 10px;
  height: 100%; align-content: center; }
.fs-tile { text-align: center; padding: 12px 4px; border: 1px solid var(--scr-border); border-radius: 6px;
  cursor: pointer;
  &:hover { box-shadow: 0 0 12px rgba(0, 229, 255, .35); } }
.fs-num { font-size: 26px; font-weight: 700; color: var(--scr-cyan);
  text-shadow: 0 0 12px rgba(0, 229, 255, .5); font-variant-numeric: tabular-nums; }
.fs-name { font-size: 13px; color: var(--scr-text-dim); margin-top: 4px; }
</style>
