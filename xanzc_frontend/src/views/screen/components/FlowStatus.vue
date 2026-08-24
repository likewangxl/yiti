<template>
  <div class="fs-wrap" :style="themeVars">
    <div v-for="(t, i) in tiles" :key="i" class="fs-tile" :class="`tone-${t.tone}`"
         role="button" tabindex="0" @click="onClick(t)" @keydown.enter="onClick(t)" @keydown.space.prevent="onClick(t)">
      <span class="fs-status-dot" aria-hidden="true"></span>
      <div class="fs-num">{{ t.value }}</div>
      <div class="fs-name">{{ t.name }}</div>
      <div class="fs-ratio" :aria-label="`占比 ${t.sharePct.toFixed(1)}%`">占比 {{ t.sharePct.toFixed(1) }}%</div>
    </div>
    <div v-if="!tiles.length" class="scr-block-empty">
      <el-icon class="scr-empty-icon"><DocumentRemove /></el-icon>
      <span>暂无流程数据</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { DocumentRemove } from '@element-plus/icons-vue';
import { resolveChartTheme } from '@/styles/screenChartTheme';

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['item-click']);
const theme = computed(() => resolveChartTheme(props.styleCfg));
const themeVars = computed(() => ({
  '--fs-accent': theme.value.tokens.accent,
  '--fs-number': theme.value.tokens.number,
  '--fs-muted': theme.value.tokens.textDim,
  '--fs-border': theme.value.tokens.border,
  '--fs-up': theme.value.tokens.up,
  '--fs-warning': theme.value.tokens.number
}));

const tiles = computed(() => {
  const ni = props.columns.indexOf(props.bind.nameCol);
  const vi = props.columns.indexOf(props.bind.valueCol);
  if (ni < 0 || vi < 0) return [];
  const list = props.rows.map(r => ({ name: String(r[ni]), value: r[vi], numericValue: Number(r[vi]), raw: r }));
  const total = list.reduce((sum, item) => sum + (Number.isFinite(item.numericValue) ? Math.max(0, item.numericValue) : 0), 0);
  return list.map((item, index) => ({
    ...item,
    sharePct: total > 0 && Number.isFinite(item.numericValue)
      ? Math.max(0, item.numericValue) / total * 100 : 0,
    tone: index % 3
  }));
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
.fs-tile { position: relative; text-align: center; padding: 14px 4px 12px; border: 1px solid var(--fs-border, var(--scr-border)); border-radius: 10px;
  cursor: pointer; transition: border-color .2s, transform .2s, box-shadow .2s;
  &:hover, &:focus-visible { border-color: var(--fs-accent, var(--scr-cyan)); transform: translateY(-1px); box-shadow: 0 8px 18px rgba(0, 0, 0, .16); }
  &:focus-visible { outline: 2px solid var(--fs-accent, var(--scr-cyan)); outline-offset: 2px; } }
.fs-status-dot { display: inline-block; width: 7px; height: 7px; margin-bottom: 5px; border-radius: 50%; background: var(--fs-accent, var(--scr-cyan)); box-shadow: 0 0 8px color-mix(in srgb, var(--fs-accent, var(--scr-cyan)) 42%, transparent); }
.tone-1 .fs-status-dot { background: var(--fs-up, var(--scr-up, #7fd39d)); }
.tone-2 .fs-status-dot { background: var(--fs-warning, var(--scr-num, #e8c979)); }
.fs-num { font-size: 26px; font-weight: 700; color: var(--fs-number, var(--scr-cyan));
  text-shadow: 0 0 12px rgba(0, 229, 255, .5); font-variant-numeric: tabular-nums; }
.fs-name { font-size: 13px; color: var(--fs-muted, var(--scr-text-dim)); margin-top: 4px; }
.fs-ratio { margin-top: 5px; color: var(--fs-muted, var(--scr-text-dim)); font-size: 10px; font-variant-numeric: tabular-nums; }
@media (prefers-reduced-motion: reduce) {
  .fs-tile { transition: none; }
}
</style>
