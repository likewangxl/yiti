<template>
  <div ref="wrapEl" class="rl-wrap" :class="{ 'rl-static': !carouselOn }" :style="themeVars">
    <div v-if="metricDefs.length" class="rl-metric-bar" data-testid="rank-metric-bar" role="toolbar" aria-label="排行榜指标切换">
      <button v-for="metric in metricDefs" :key="metric.col" type="button"
              class="rl-metric-button" :class="{ active: metric.col === activeMetricCol }"
              data-testid="rank-metric-button" :aria-pressed="metric.col === activeMetricCol"
              :aria-label="`${metric.label}，点击${metric.col === activeMetricCol ? (sortDesc ? '切换正序' : '切换倒序') : '切换并按倒序排序'}`"
              @click="selectMetric(metric.col)">
        {{ metric.label }}
      </button>
    </div>
    <div v-if="metricDefs.length" class="rl-sort-status" data-testid="rank-sort-status" role="status" aria-live="polite">
      当前排序：{{ activeMetricLabel }}（{{ sortDesc ? '倒序' : '正序' }}）
    </div>
    <div v-for="r in shownRanked" :key="r.rowKey" class="rl-row" role="button" tabindex="0"
         @click="onClick(r)" @keydown.enter="onClick(r)" @keydown.space.prevent="onClick(r)">
      <span class="rl-no" :class="{ top: r.rank <= 3 }" :aria-label="`第${r.rank}名`">
        <span v-if="r.rank <= 3" class="rl-rank-medal" aria-hidden="true">{{ medals[r.rank - 1] }}</span>
        <span v-else>{{ r.rank }}</span>
      </span>
      <span class="rl-name">{{ r.name }}</span>
      <div class="rl-values" data-testid="rank-metric-values">
        <span v-for="metric in metricDefs" :key="metric.col"
              class="rl-metric-value" :class="{ 'rl-val': metric.col === activeMetricCol }"
              data-testid="rank-metric-value" :aria-label="`${metric.label}：${fmtMetric(r, metric)}`">
          {{ fmtMetric(r, metric) }}
        </span>
      </div>
    </div>
    <div v-if="!ranked.length" class="scr-block-empty">
      <el-icon class="scr-empty-icon"><DocumentRemove /></el-icon>
      <span>暂无数据</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { DocumentRemove } from '@element-plus/icons-vue';
import { resolveChartTheme } from '@/styles/screenChartTheme';
import { displayName, metaOf } from './utils/chartData';
import { visibleCount, shouldCarousel, nextStart, windowIndices } from './utils/carousel';

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) },
  propValue: { type: Object, default: () => ({}) },
  columnsMeta: { type: Array, default: null }
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

const ROW_H = 30;
// 指标按钮栏 24px + 排序注释 14px + 容器 gap 6px，避免自动轮播高估可视行数。
const HEADER_H = 44;
const wrapEl = ref(null);
const containerH = ref(0);
const start = ref(0);
const activeMetricCol = ref('');
const sortDesc = ref(true);
const visible = computed(() => visibleCount(containerH.value, HEADER_H, ROW_H));
// 排行榜旧节点没有 propValue.carousel 时默认开启；显式 false 才关闭自动轮播。
const carouselOn = computed(() => shouldCarousel(
  props.rows.length, visible.value, props.propValue?.carousel !== false
));

function hasMetricRole(col) {
  const metas = Array.isArray(props.columnsMeta) ? props.columnsMeta : [];
  const hasRoles = metas.some(meta => meta && meta.role != null && String(meta.role).trim() !== '');
  if (!hasRoles) return true;
  return String(metaOf(col, metas)?.role || '').toUpperCase() === 'METRIC';
}

const metricDefs = computed(() => {
  const columns = Array.isArray(props.columns) ? props.columns : [];
  const indexByCol = new Map(columns.map((col, index) => [col, index]));
  const items = Array.isArray(props.bind?.items) ? props.bind.items : [];
  const out = [];
  const seen = new Set();
  for (const item of items) {
    const col = String(item?.col || '').trim();
    if (!col || seen.has(col) || !indexByCol.has(col) || !hasMetricRole(col)) continue;
    seen.add(col);
    out.push({
      col,
      label: String(item?.label || '').trim() || displayName(col, props.columnsMeta) || col
    });
  }
  // 兼容历史排行榜 bind.valueCol；有有效 items 时以多指标配置为准。
  if (!out.length) {
    const col = String(props.bind?.valueCol || '').trim();
    if (col && indexByCol.has(col) && hasMetricRole(col)) {
      out.push({ col, label: displayName(col, props.columnsMeta) || col });
    }
  }
  return out;
});

const activeMetricLabel = computed(() =>
  metricDefs.value.find(metric => metric.col === activeMetricCol.value)?.label
  || metricDefs.value[0]?.label || '未选择指标');

watch(metricDefs, metrics => {
  if (!metrics.some(metric => metric.col === activeMetricCol.value)) {
    activeMetricCol.value = metrics[0]?.col || '';
    sortDesc.value = true;
  }
  start.value = 0;
}, { immediate: true });

function selectMetric(col) {
  if (activeMetricCol.value === col) sortDesc.value = !sortDesc.value;
  else {
    activeMetricCol.value = col;
    sortDesc.value = true;
  }
  start.value = 0;
}

function numericValue(value) {
  if (value == null || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function categoryIndex(columns, valueIndex) {
  const category = String(props.bind?.categoryCol || '').trim();
  const categoryIndex = category ? columns.indexOf(category) : -1;
  if (categoryIndex >= 0) return categoryIndex;
  const legacyName = String(props.bind?.nameCol || '').trim();
  const legacyIndex = legacyName ? columns.indexOf(legacyName) : -1;
  if (legacyIndex >= 0) return legacyIndex;
  const metricCols = new Set(metricDefs.value.map(metric => metric.col));
  return columns.findIndex((column, index) => {
    if (index === valueIndex || metricCols.has(column)) return false;
    const role = String(metaOf(column, props.columnsMeta)?.role || '').toUpperCase();
    return role !== 'METRIC';
  });
}

const ranked = computed(() => {
  const columns = Array.isArray(props.columns) ? props.columns : [];
  const vi = columns.indexOf(activeMetricCol.value);
  const ni = categoryIndex(columns, vi);
  if (ni < 0 || vi < 0) return [];
  const list = props.rows.map((r, originalIndex) => {
    const value = numericValue(r?.[vi]);
    return {
      name: String(r?.[ni] ?? ''), value, numericValue: value, raw: r, originalIndex
    };
  }).sort((a, b) => {
    // 非数字稳定置后；数字相等时保持接口原始顺序。
    if (a.numericValue == null || b.numericValue == null) {
      if (a.numericValue == null && b.numericValue == null) return a.originalIndex - b.originalIndex;
      return a.numericValue == null ? 1 : -1;
    }
    const diff = sortDesc.value ? b.numericValue - a.numericValue : a.numericValue - b.numericValue;
    return diff || a.originalIndex - b.originalIndex;
  });
  return list.map((x, index) => ({
    ...x,
    rowKey: x.originalIndex,
    rank: index + 1
  }));
});

const shownRanked = computed(() => {
  const indexes = carouselOn.value
    ? windowIndices(start.value, visible.value, ranked.value.length)
    : ranked.value.map((_, index) => index);
  return indexes.map(index => ranked.value[index]).filter(Boolean);
});

function fmtMetric(row, metric) {
  const index = props.columns.indexOf(metric.col);
  const value = index >= 0 ? row.raw?.[index] : null;
  if (value == null || value === '') return '—';
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return '—';
  const d = metaOf(metric.col, props.columnsMeta)?.decimals ?? props.styleCfg.decimals ?? 2;
  return numeric.toLocaleString('zh-CN', { minimumFractionDigits: d, maximumFractionDigits: d });
}
function onClick(r) {
  const row = {};
  props.columns.forEach((c, i) => { row[c] = r.raw[i]; });
  emit('item-click', { col: activeMetricCol.value, label: r.name, row });
}

let timer = null;
let resizeOb = null;
onMounted(() => {
  containerH.value = wrapEl.value?.clientHeight || 0;
  if (typeof ResizeObserver !== 'undefined' && wrapEl.value) {
    resizeOb = new ResizeObserver(() => { containerH.value = wrapEl.value?.clientHeight || 0; });
    resizeOb.observe(wrapEl.value);
  }
  timer = setInterval(() => {
    if (!document.hidden && carouselOn.value) start.value = nextStart(start.value, ranked.value.length);
  }, 2000);
});
onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
  if (resizeOb) resizeOb.disconnect();
});
// 每次接口刷新都从全量排序后的第一名开始，避免新数据沿用旧窗口位置。
watch(() => props.rows, () => { start.value = 0; });
</script>

<style lang="scss" scoped>
.rl-wrap { height: 100%; overflow: hidden; display: flex; flex-direction: column; gap: 6px;
  &.rl-static { overflow-y: auto; }
  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-thumb { background: rgba(0, 229, 255, .3); border-radius: 2px; } }
.rl-metric-bar { display: flex; align-items: center; gap: 5px; flex: none; min-height: 24px; overflow-x: auto;
  &::-webkit-scrollbar { height: 2px; } }
.rl-metric-button { border: 1px solid var(--rl-border, var(--scr-border)); border-radius: 4px; padding: 3px 8px;
  color: var(--rl-muted, var(--scr-text-dim)); background: rgba(125, 155, 201, .08); cursor: pointer; font-size: 11px;
  white-space: nowrap; &:hover, &:focus-visible { border-color: var(--rl-accent, var(--scr-cyan)); color: var(--rl-accent, var(--scr-cyan)); }
  &.active { border-color: var(--rl-accent, var(--scr-cyan)); color: var(--rl-accent, var(--scr-cyan)); background: color-mix(in srgb, var(--rl-accent, var(--scr-cyan)) 14%, transparent); } }
.rl-sort-status { flex: none; min-height: 14px; color: var(--rl-muted, var(--scr-text-dim)); font-size: 10px; line-height: 14px; }
.rl-row { display: flex; align-items: center; gap: 7px; cursor: pointer; padding: 4px 2px;
  &:hover { background: color-mix(in srgb, var(--rl-accent, var(--scr-cyan)) 9%, transparent); border-radius: 6px; } }
.rl-no { width: 22px; height: 22px; border-radius: 4px; text-align: center; line-height: 22px;
  font-size: 13px; background: rgba(125, 155, 201, .2); color: var(--rl-muted, var(--scr-text-dim)); flex: none;
  &.top { background: color-mix(in srgb, var(--rl-accent, var(--scr-cyan)) 25%, transparent); color: var(--rl-number, var(--scr-num)); font-weight: 700; } }
.rl-rank-medal { font-size: 15px; line-height: 1; }
.rl-name { width: 140px; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: none; }
.rl-values { display: flex; align-items: baseline; justify-content: flex-end; gap: 8px; min-width: 0; margin-left: auto; flex: none; }
.rl-metric-value { min-width: 78px; text-align: right; font-size: 12px; color: var(--rl-muted, var(--scr-text-dim));
  font-variant-numeric: tabular-nums; white-space: nowrap; }
.rl-val { color: var(--rl-number, var(--scr-num)); font-size: 13px;
  font-variant-numeric: tabular-nums; flex: none; }
</style>
