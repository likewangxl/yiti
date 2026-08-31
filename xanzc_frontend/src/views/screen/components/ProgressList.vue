<template>
  <div class="pl-wrap" :style="themeVars">
    <div v-for="it in items" :key="it.code || it.name" class="pl-row">
      <span class="pl-name" :title="it.name">{{ it.name }}</span>
      <div class="pl-bar">
        <div v-if="it.rate != null" class="pl-fill" :class="bandOf(it.rate)" :style="{ width: clampPct(it.rate) + '%' }" />
        <span class="pl-rate">{{ it.rate != null ? fmtNum(it.rate, 1) + '%' : '—' }}</span>
      </div>
      <span class="pl-gap" :class="'gap-' + gapOf(it).type">{{ gapOf(it).text }}</span>
    </div>
    <div v-if="!items.length" class="scr-block-empty">
      <el-icon class="scr-empty-icon"><DocumentRemove /></el-icon>
      <span>暂无 KPI 细项数据</span>
    </div>
  </div>
</template>

<script setup>
// 进度条列表（PROGRESS_LIST）：每细项一行——名称 + 完成率横向进度条（封顶 100 宽）
// + 右侧缺口文案（正=红"还差 X"、负=绿"已超额 X"，X 按 columnsMeta.decimals 或默认 2 位格式化）。
import { computed } from 'vue';
import { DocumentRemove } from '@element-plus/icons-vue';
import { resolveChartTheme } from '@/styles/screenChartTheme';
import { fmtNum, clampPct, metaOf } from './utils/chartData';
import { parseKpiRows, gapText } from './utils/kpiDetail';

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) },
  propValue: { type: Object, default: () => ({}) },
  columnsMeta: { type: Array, default: null }
});

const items = computed(() => parseKpiRows(props.columns, props.rows));
const gapDecimals = computed(() => metaOf('缺口', props.columnsMeta)?.decimals ?? 2);
const theme = computed(() => resolveChartTheme(props.styleCfg));
const themeVars = computed(() => ({
  '--pl-accent': theme.value.tokens.accent,
  '--pl-accent-strong': theme.value.tokens.accentStrong,
  '--pl-number': theme.value.tokens.number,
  '--pl-muted': theme.value.tokens.textDim,
  '--pl-border': theme.value.tokens.border,
  '--pl-up': theme.value.tokens.up,
  '--pl-down': theme.value.tokens.down,
  '--pl-bg': theme.value.tokens.bgDeep
}));

function gapOf(it) { return gapText(it.gap, gapDecimals.value); }
/** 完成率分档配色（与 KPI 细项表同口径）：≥100 绿 / ≥60 蓝青 / <60 橙红 */
function bandOf(rate) {
  if (rate >= 100) return 'ok';
  if (rate >= 60) return 'mid';
  return 'low';
}
</script>

<style lang="scss" scoped>
.pl-wrap {
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 2px 0;
  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-thumb { background: color-mix(in srgb, var(--pl-accent, var(--scr-cyan)) 30%, transparent); border-radius: 2px; }
}
.pl-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 3px 2px;
  border-radius: 4px;
  &:hover { background: color-mix(in srgb, var(--pl-accent, var(--scr-cyan)) 7%, transparent); }
}
.pl-name {
  width: 110px;
  flex: none;
  font-size: 13px;
  color: var(--pl-muted, var(--scr-text));
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pl-bar {
  position: relative;
  flex: 1;
  height: 16px;
  background: color-mix(in srgb, var(--pl-border, var(--scr-border)) 55%, transparent);
  border: 1px solid var(--pl-border, var(--scr-border));
  border-radius: 8px;
  overflow: hidden;
}
.pl-fill {
  height: 100%;
  border-radius: 8px;
  transition: width .6s ease;
  &.ok { background: linear-gradient(90deg, color-mix(in srgb, var(--pl-up, var(--scr-up)) 50%, transparent), var(--pl-up, var(--scr-up))); box-shadow: 0 0 10px color-mix(in srgb, var(--pl-up, var(--scr-up)) 50%, transparent); }
  &.mid { background: linear-gradient(90deg, var(--pl-accent, var(--scr-blue)), var(--pl-accent-strong, var(--scr-cyan))); box-shadow: 0 0 10px color-mix(in srgb, var(--pl-accent, var(--scr-cyan)) 40%, transparent); }
  &.low { background: linear-gradient(90deg, color-mix(in srgb, var(--pl-down, var(--scr-down)) 55%, transparent), var(--pl-down, var(--scr-down))); box-shadow: 0 0 10px color-mix(in srgb, var(--pl-down, var(--scr-down)) 45%, transparent); }
}
.pl-rate {
  position: absolute;
  inset: 0;
  text-align: center;
  font-size: 11px;
  line-height: 16px;
  color: var(--scr-text, #fff);
  text-shadow: 0 0 3px rgba(0, 0, 0, .8);
  font-variant-numeric: tabular-nums;
}
.pl-gap {
  width: 130px;
  flex: none;
  text-align: right;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  &.gap-lack { color: var(--pl-down, var(--scr-down)); }
  &.gap-over { color: var(--pl-up, var(--scr-up)); }
  &.gap-none { color: var(--pl-muted, var(--scr-text-dim)); }
}
@media (prefers-reduced-motion: reduce) {
  .pl-fill { transition: none; }
}
</style>
