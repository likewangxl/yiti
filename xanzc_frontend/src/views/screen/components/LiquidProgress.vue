<template>
  <div class="lp-wrap" :style="themeVars">
    <div v-if="colIdx >= 0" class="lp-ball">
      <svg class="lp-svg" viewBox="0 0 200 200">
        <defs>
          <!-- 双层波浪渐变：主青 + 辅蓝，同源主题色不同透明度 -->
          <linearGradient :id="gid('a')" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" :stop-color="scrWithAlpha(theme.tokens.accentStrong, .65)" />
            <stop offset="1" :stop-color="scrWithAlpha(theme.palette[1], .35)" />
          </linearGradient>
          <linearGradient :id="gid('b')" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" :stop-color="scrWithAlpha(theme.tokens.accent, .35)" />
            <stop offset="1" :stop-color="scrWithAlpha(theme.palette[1], .15)" />
          </linearGradient>
          <clipPath :id="gid('clip')"><circle cx="100" cy="100" r="86" /></clipPath>
        </defs>
        <!-- 外发光环 -->
        <circle cx="100" cy="100" r="92" fill="none" :stroke="scrWithAlpha(theme.tokens.accent, .25)" stroke-width="2" />
        <circle cx="100" cy="100" r="96" fill="none" :stroke="scrWithAlpha(theme.tokens.accent, .1)" stroke-width="1" />
        <circle cx="100" cy="100" r="86" :fill="scrWithAlpha(theme.tokens.bgDeep, .6)" />
        <!-- 水位组：translateY 定水位（CSS transition 平滑升降），组内两条波横向循环平移 -->
        <g :clip-path="`url(#${gid('clip')})`">
          <g class="lp-level" :style="{ transform: `translateY(${levelY}px)` }">
            <path class="lp-wave lp-wave-b" :fill="`url(#${gid('b')})`" :d="wavePath" />
            <path class="lp-wave lp-wave-a" :fill="`url(#${gid('a')})`" :d="wavePath" />
          </g>
        </g>
      </svg>
      <!-- 中央大数字：显示真实值（可超 100），水位按封顶值绘制 -->
      <div class="lp-text">
        <div class="lp-value">{{ fmtNum(rawValue, decimals) }}<span class="lp-unit">{{ unit }}</span></div>
        <div class="lp-label">{{ label }}</div>
      </div>
    </div>
    <div v-else class="scr-block-empty">
      <el-icon class="scr-empty-icon"><Warning /></el-icon>
      <span>未找到可用数值列</span>
    </div>
  </div>
</template>

<script setup>
// 水波完成度球（LIQUID_PROGRESS）：自绘 SVG 双层波浪动画，零新增依赖（不引 echarts-liquidfill）。
// 取列口径同仪表盘：bind.valueCol 指定 > 名含"完成率" > 首个数值列；水位 0-100 封顶，中央数字显示真实值。
import { computed } from 'vue';
import { Warning } from '@element-plus/icons-vue';
import { resolveChartTheme, scrWithAlpha } from '@/styles/screenChartTheme';
import { pickValueCol, displayName, metaOf, fmtNum, clampPct } from './utils/chartData';

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) },
  propValue: { type: Object, default: () => ({}) },
  columnsMeta: { type: Array, default: null }
});

// SVG <defs> id 全局唯一化：同屏多个水波球时 clipPath/渐变 id 冲突会串染
const uid = Math.random().toString(36).slice(2, 8);
const gid = suffix => `lp-${uid}-${suffix}`;

const colIdx = computed(() => pickValueCol(props.columns, props.rows, props.bind.valueCol, props.columnsMeta));
const colName = computed(() => (colIdx.value >= 0 ? props.columns[colIdx.value] : ''));
const meta = computed(() => metaOf(colName.value, props.columnsMeta));
const rawValue = computed(() => {
  const last = props.rows.length ? props.rows[props.rows.length - 1] : null;
  const v = last && colIdx.value >= 0 ? Number(last[colIdx.value]) : NaN;
  return Number.isNaN(v) ? null : v;
});
const unit = computed(() => meta.value?.unit || '%');
const decimals = computed(() => meta.value?.decimals ?? props.styleCfg.decimals ?? 1);
const label = computed(() => props.styleCfg.title || displayName(colName.value, props.columnsMeta));
const theme = computed(() => resolveChartTheme(props.styleCfg));
const themeVars = computed(() => ({
  '--lp-accent': theme.value.tokens.accent,
  '--lp-accent-strong': theme.value.tokens.accentStrong,
  '--lp-number': theme.value.tokens.number,
  '--lp-muted': theme.value.tokens.textDim,
  '--lp-border': theme.value.tokens.border,
  '--lp-up': theme.value.tokens.up,
  '--lp-down': theme.value.tokens.down,
  '--lp-bg': theme.value.tokens.bgDeep
}));

// 水位：pct 0 → 波峰线贴球底(y=186)，100 → 贴球顶(y=14)；波 path 以 y=0 为基线，整组 translateY 定位
const levelY = computed(() => 186 - (clampPct(rawValue.value) / 100) * 172);
// 两个完整波周期宽 400（视口 200 的两倍），水平循环平移 200 即无缝衔接；下探 220 盖满球体
const wavePath = 'M0,0 Q25,-9 50,0 T100,0 T150,0 T200,0 T250,0 T300,0 T350,0 T400,0 V220 H0 Z';
</script>

<style lang="scss" scoped>
.lp-wrap { height: 100%; display: flex; align-items: center; justify-content: center; }
.lp-ball {
  position: relative;
  height: 100%;
  aspect-ratio: 1;
  max-width: 100%;
  filter: drop-shadow(0 0 12px color-mix(in srgb, var(--lp-accent, var(--scr-cyan)) 25%, transparent));
}
.lp-svg { width: 100%; height: 100%; }
.lp-level { transition: transform 1s ease; }
.lp-wave {
  animation: lp-wave-move linear infinite;
  &.lp-wave-a { animation-duration: 5s; }
  &.lp-wave-b { animation-duration: 8s; animation-direction: reverse; }
}
@keyframes lp-wave-move {
  from { transform: translateX(0); }
  to { transform: translateX(-200px); }
}
.lp-text {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  pointer-events: none;
}
.lp-value {
  font-size: 30px;
  font-weight: 700;
  color: var(--lp-number, var(--scr-num));
  font-variant-numeric: tabular-nums;
  text-shadow: 0 0 14px rgba(255, 215, 106, .45);
}
.lp-unit { font-size: 14px; margin-left: 2px; color: var(--lp-muted, var(--scr-text-dim)); }
.lp-label {
  max-width: 76%;
  font-size: 13px;
  color: var(--lp-muted, var(--scr-text-dim));
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (prefers-reduced-motion: reduce) {
  .lp-level { transition: none; }
  .lp-wave { animation: none; }
}
</style>
