<template>
  <div class="rl-wrap">
    <div v-for="(r, i) in ranked" :key="i" class="rl-row" @click="onClick(r)">
      <span class="rl-no" :class="{ top: i < 3 }">{{ i + 1 }}</span>
      <span class="rl-name">{{ r.name }}</span>
      <div class="rl-bar"><div class="rl-fill" :style="{ width: r.pct + '%' }" /></div>
      <span class="rl-val">{{ fmt(r.value) }}</span>
    </div>
    <div v-if="!ranked.length" class="scr-block-err">暂无数据</div>
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

const ranked = computed(() => {
  const ni = props.columns.indexOf(props.bind.nameCol);
  const vi = props.columns.indexOf(props.bind.valueCol);
  if (ni < 0 || vi < 0) return [];
  const list = props.rows.map(r => ({ name: String(r[ni]), value: Number(r[vi]) || 0, raw: r }))
      .sort((a, b) => b.value - a.value);
  const max = list[0]?.value || 1;
  return list.map(x => ({ ...x, pct: Math.max(4, Math.round((x.value / max) * 100)) }));
});

function fmt(v) {
  const d = props.styleCfg.decimals ?? 2;
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: d, maximumFractionDigits: d });
}
function onClick(r) {
  const row = {};
  props.columns.forEach((c, i) => { row[c] = r.raw[i]; });
  emit('item-click', { col: props.bind.valueCol, label: r.name, row });
}
</script>

<style lang="scss" scoped>
.rl-wrap { height: 100%; overflow-y: auto; display: flex; flex-direction: column; gap: 6px;
  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-thumb { background: rgba(0, 229, 255, .3); border-radius: 2px; } }
.rl-row { display: flex; align-items: center; gap: 8px; cursor: pointer; padding: 3px 2px;
  &:hover { background: rgba(0, 229, 255, .08); border-radius: 4px; } }
.rl-no { width: 22px; height: 22px; border-radius: 4px; text-align: center; line-height: 22px;
  font-size: 13px; background: rgba(125, 155, 201, .2); color: var(--scr-text-dim); flex: none;
  &.top { background: linear-gradient(135deg, #ffd76a, #ff8a65); color: #1b1b1b; font-weight: 700; } }
.rl-name { width: 96px; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: none; }
.rl-bar { flex: 1; height: 8px; background: rgba(125, 155, 201, .15); border-radius: 4px; overflow: hidden; }
.rl-fill { height: 100%; background: linear-gradient(90deg, #3d7eff, #00e5ff); border-radius: 4px; }
.rl-val { width: 90px; text-align: right; font-size: 14px; color: var(--scr-num);
  font-variant-numeric: tabular-nums; flex: none; }
</style>
