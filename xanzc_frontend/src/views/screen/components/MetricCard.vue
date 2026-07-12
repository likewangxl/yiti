<template>
  <div class="mc-wrap">
    <div v-for="it in items" :key="it.col" class="mc-item" @click="onClick(it)">
      <div class="mc-label">{{ it.label || it.col }}</div>
      <div class="mc-value">{{ fmt(valueOf(it.col)) }}<span class="mc-unit">{{ styleCfg.unit || '' }}</span></div>
    </div>
    <div v-if="!items.length" class="scr-block-err">未绑定数据项</div>
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

const items = computed(() => props.bind.items || []);
const lastRow = computed(() => props.rows.length ? props.rows[props.rows.length - 1] : null);

function valueOf(col) {
  if (!lastRow.value) return null;
  const idx = props.columns.indexOf(col);
  return idx >= 0 ? lastRow.value[idx] : null;
}
function fmt(v) {
  if (v == null || v === '') return '—';
  const n = Number(v);
  if (Number.isNaN(n)) return String(v);
  const d = props.styleCfg.decimals ?? 2;
  return n.toLocaleString('zh-CN', { minimumFractionDigits: d, maximumFractionDigits: d });
}
function rowMap() {
  const m = {};
  if (lastRow.value) props.columns.forEach((c, i) => { m[c] = lastRow.value[i]; });
  return m;
}
function onClick(it) {
  emit('item-click', { col: it.col, label: it.label || it.col, row: rowMap() });
}
</script>

<style lang="scss" scoped>
.mc-wrap { display: flex; flex-wrap: wrap; gap: 10px; height: 100%; align-content: center; }
.mc-item {
  flex: 1 1 40%;
  min-width: 120px;
  text-align: center;
  padding: 10px 6px;
  border: 1px solid var(--scr-border);
  border-radius: 6px;
  cursor: pointer;
  transition: box-shadow .2s;
  &:hover { box-shadow: 0 0 12px rgba(0, 229, 255, .35); }
}
.mc-label { font-size: 14px; color: var(--scr-text-dim); margin-bottom: 6px; }
.mc-value {
  font-size: 30px;
  font-weight: 700;
  color: var(--scr-num);
  font-variant-numeric: tabular-nums;
  text-shadow: 0 0 14px rgba(255, 215, 106, .45);
}
.mc-unit { font-size: 13px; color: var(--scr-text-dim); margin-left: 4px; }
</style>
