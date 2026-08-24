<template>
  <div class="rl-wrap" :style="themeVars">
    <div v-for="(r, i) in ranked" :key="i" class="rl-row" role="button" tabindex="0"
         @click="onClick(r)" @keydown.enter="onClick(r)" @keydown.space.prevent="onClick(r)">
      <span class="rl-no" :class="{ top: i < 3 }" :aria-label="`第${i + 1}名`">
        <span v-if="i < 3" class="rl-rank-medal" aria-hidden="true">{{ medals[i] }}</span>
        <span v-else>{{ i + 1 }}</span>
      </span>
      <span class="rl-name">{{ r.name }}</span>
      <div class="rl-bar"><div class="rl-fill" :style="{ width: r.pct + '%', '--rank-color': r.color }" /></div>
      <span class="rl-share" :aria-label="`占比 ${r.sharePct.toFixed(1)}%`">占比 {{ r.sharePct.toFixed(1) }}%</span>
      <span class="rl-val">{{ fmt(r.value) }}</span>
    </div>
    <div v-if="!ranked.length" class="scr-block-empty">
      <el-icon class="scr-empty-icon"><DocumentRemove /></el-icon>
      <span>暂无数据</span>
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
const medals = ['🥇', '🥈', '🥉'];
const theme = computed(() => resolveChartTheme(props.styleCfg));
const themeVars = computed(() => ({
  '--rl-accent': theme.value.tokens.accent,
  '--rl-number': theme.value.tokens.number,
  '--rl-muted': theme.value.tokens.textDim,
  '--rl-border': theme.value.tokens.border
}));

const ranked = computed(() => {
  const ni = props.columns.indexOf(props.bind.nameCol);
  const vi = props.columns.indexOf(props.bind.valueCol);
  if (ni < 0 || vi < 0) return [];
  const list = props.rows.map(r => ({ name: String(r[ni]), value: Number(r[vi]) || 0, raw: r }))
      .sort((a, b) => b.value - a.value);
  const max = list[0]?.value || 1;
  const total = list.reduce((sum, x) => sum + Math.max(0, x.value), 0);
  const colors = props.styleCfg.colors?.length ? props.styleCfg.colors : theme.value.palette;
  return list.map((x, index) => ({
    ...x,
    pct: Math.max(4, Math.round((x.value / max) * 100)),
    sharePct: total > 0 ? Math.max(0, x.value) / total * 100 : 0,
    color: colors[index % colors.length]
  }));
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
.rl-row { display: flex; align-items: center; gap: 7px; cursor: pointer; padding: 4px 2px;
  &:hover { background: color-mix(in srgb, var(--rl-accent, var(--scr-cyan)) 9%, transparent); border-radius: 6px; } }
.rl-no { width: 22px; height: 22px; border-radius: 4px; text-align: center; line-height: 22px;
  font-size: 13px; background: rgba(125, 155, 201, .2); color: var(--rl-muted, var(--scr-text-dim)); flex: none;
  &.top { background: color-mix(in srgb, var(--rl-accent, var(--scr-cyan)) 25%, transparent); color: var(--rl-number, var(--scr-num)); font-weight: 700; } }
.rl-rank-medal { font-size: 15px; line-height: 1; }
.rl-name { width: 82px; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: none; }
.rl-bar { flex: 1; height: 9px; background: rgba(125, 155, 201, .15); border-radius: 5px; overflow: hidden; }
.rl-fill { height: 100%; background: linear-gradient(90deg, color-mix(in srgb, var(--rank-color, var(--scr-blue)) 70%, var(--rl-accent, var(--scr-cyan))), var(--rank-color, var(--rl-accent, var(--scr-cyan)))); border-radius: 5px; transition: width .28s ease; }
.rl-share { width: 60px; color: var(--rl-muted, var(--scr-text-dim)); font-size: 10px; font-variant-numeric: tabular-nums; text-align: right; flex: none; }
.rl-val { width: 78px; text-align: right; font-size: 13px; color: var(--rl-number, var(--scr-num));
  font-variant-numeric: tabular-nums; flex: none; }
@media (prefers-reduced-motion: reduce) {
  .rl-fill { transition: none; }
}
</style>
