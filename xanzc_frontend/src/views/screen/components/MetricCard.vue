<template>
  <div class="mc-wrap" :style="themeVars">
    <div v-for="it in items" :key="it.col" class="mc-item" :class="`variant-${cardVariant}`"
         role="button" tabindex="0" @click="onClick(it)" @keydown.enter="onClick(it)" @keydown.space.prevent="onClick(it)">
      <div class="mc-label">{{ it.label || it.col }}</div>
      <div class="mc-value">
        {{ fmt(valueOf(it.col), it.col) }}<span class="mc-unit">{{ unitOf(it.col) }}</span>
      </div>
      <span v-if="trendOf(it.col)" class="mc-trend" :class="`trend-${trendOf(it.col).direction}`"
            data-testid="metric-trend" :aria-label="trendOf(it.col).ariaLabel">
        {{ trendOf(it.col).icon }} {{ trendOf(it.col).text }}
      </span>
    </div>
    <div v-if="!items.length" class="scr-block-empty">
      <el-icon class="scr-empty-icon"><Warning /></el-icon>
      <span>未绑定数据项</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { Warning } from '@element-plus/icons-vue';
import { resolveChartTheme } from '@/styles/screenChartTheme';
import { metaOf } from './utils/chartData';

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  columnsMeta: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['item-click']);

const items = computed(() => props.bind.items || []);
const lastRow = computed(() => props.rows.length ? props.rows[props.rows.length - 1] : null);
const showTrend = computed(() => props.styleCfg.showTrend === true);
const cardVariant = computed(() => ['glow', 'glass', 'outline'].includes(props.styleCfg.cardVariant)
  ? props.styleCfg.cardVariant : 'glow');
const theme = computed(() => resolveChartTheme(props.styleCfg));
const themeVars = computed(() => ({
  '--mc-accent': theme.value.tokens.accent,
  '--mc-number': theme.value.tokens.number,
  '--mc-border': theme.value.tokens.border,
  '--mc-muted': theme.value.tokens.textDim,
  '--mc-up': theme.value.tokens.up,
  '--mc-down': theme.value.tokens.down
}));

function valueOf(col) {
  if (!lastRow.value) return null;
  const idx = props.columns.indexOf(col);
  return idx >= 0 ? lastRow.value[idx] : null;
}
function metaFor(col) {
  return metaOf(col, props.columnsMeta);
}
function unitOf(col) {
  return metaFor(col)?.unit || props.styleCfg.unit || '';
}
function decimalsOf(col) {
  return metaFor(col)?.decimals ?? props.styleCfg.decimals ?? 2;
}
function fmt(v, col) {
  if (v == null || v === '') return '—';
  const n = Number(v);
  if (Number.isNaN(n)) return String(v);
  const d = decimalsOf(col);
  return n.toLocaleString('zh-CN', { minimumFractionDigits: d, maximumFractionDigits: d });
}
function trendOf(col) {
  if (!showTrend.value || props.rows.length < 2) return null;
  const idx = props.columns.indexOf(col);
  if (idx < 0) return null;
  const rawCurrent = props.rows[props.rows.length - 1]?.[idx];
  const rawPrevious = props.rows[props.rows.length - 2]?.[idx];
  if (rawCurrent == null || rawCurrent === '' || rawPrevious == null || rawPrevious === '') return null;
  const current = Number(rawCurrent);
  const previous = Number(rawPrevious);
  // previous=0 时百分比没有安全定义；只展示可解释的趋势，避免误导用户。
  if (!Number.isFinite(current) || !Number.isFinite(previous) || previous === 0) return null;
  const delta = current - previous;
  const pct = Math.abs(delta / Math.abs(previous) * 100);
  const direction = delta > 0 ? 'up' : delta < 0 ? 'down' : 'flat';
  const directionLabel = direction === 'up' ? '上升' : direction === 'down' ? '下降' : '持平';
  return {
    direction, icon: direction === 'up' ? '↑' : direction === 'down' ? '↓' : '→',
    text: `较上期${directionLabel} ${pct.toFixed(1)}%`,
    ariaLabel: `较上期${directionLabel} ${pct.toFixed(1)}%`
  };
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
  border: 1px solid var(--mc-border, var(--scr-border));
  border-radius: 10px;
  cursor: pointer;
  transition: box-shadow .2s, border-color .2s, transform .2s;
  &:hover { border-color: var(--mc-accent, var(--scr-cyan)); transform: translateY(-1px); }
  &:focus-visible { outline: 2px solid var(--mc-accent, var(--scr-cyan)); outline-offset: 2px; }
  &.variant-glow { box-shadow: inset 0 0 24px color-mix(in srgb, var(--mc-accent, var(--scr-cyan)) 8%, transparent); }
  &.variant-glass { background: color-mix(in srgb, var(--scr-bg, #0a1f4e) 58%, white 5%); backdrop-filter: blur(8px); }
  &.variant-outline { background: transparent; box-shadow: none; }
}
.mc-label { font-size: 14px; color: var(--mc-muted, var(--scr-text-dim)); margin-bottom: 6px; }
.mc-value {
  font-size: 30px;
  font-weight: 700;
  color: var(--mc-number, var(--scr-num));
  font-variant-numeric: tabular-nums;
  text-shadow: 0 0 14px rgba(255, 215, 106, .45);
}
.mc-unit { font-size: 13px; color: var(--mc-muted, var(--scr-text-dim)); margin-left: 4px; }
.mc-trend { display: inline-flex; align-items: center; gap: 3px; margin-top: 3px; font-size: 11px; font-variant-numeric: tabular-nums; }
.mc-trend.trend-up { color: var(--mc-up, var(--scr-up, #7fd39d)); }
.mc-trend.trend-down { color: var(--mc-down, var(--scr-down, #e59a91)); }
.mc-trend.trend-flat { color: var(--mc-muted, var(--scr-text-dim)); }
@media (prefers-reduced-motion: reduce) {
  .mc-item { transition: none; }
}
</style>
