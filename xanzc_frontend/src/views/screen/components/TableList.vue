<template>
  <div ref="wrapEl" class="tl-wrap" :class="{ 'tl-static': !carouselOn }" :style="themeVars">
    <table v-if="rows.length" class="tl-table">
      <thead>
        <tr>
          <th v-for="column in visibleColumns" :key="column.col">{{ headerOf(column.col, column.label) }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="ri in shownIndices" :key="ri">
          <td v-for="column in visibleColumns" :key="column.col" :class="{ num: isNum(rows[ri][column.index]) }">
            {{ cellText(column.col, rows[ri][column.index]) }}
          </td>
        </tr>
      </tbody>
    </table>
    <div v-else class="scr-block-empty">
      <el-icon class="scr-empty-icon"><DocumentRemove /></el-icon>
      <span>暂无数据</span>
    </div>
  </div>
</template>

<script setup>
// 明细表格（TABLE_LIST）：深色表格，列名=columns（columnsMeta 有别名则用别名，单位并入表头）；
// propValue.carousel 开关 = 自动滚动轮播——行数超出可视区时按环形窗口逐行推进（纯自研，零新增依赖）。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { DocumentRemove } from '@element-plus/icons-vue';
import { resolveChartTheme } from '@/styles/screenChartTheme';
import { displayName, metaOf, fmtNum } from './utils/chartData';
import { visibleCount, shouldCarousel, nextStart, windowIndices } from './utils/carousel';

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  bind: { type: Object, default: () => ({}) },
  styleCfg: { type: Object, default: () => ({}) },
  propValue: { type: Object, default: () => ({}) },
  columnsMeta: { type: Array, default: null }
});

// 与样式表行高保持一致（改 CSS 行高必须同步这两个常量，轮播可视行数按此计算）
const HEADER_H = 34;
const ROW_H = 30;

const wrapEl = ref(null);
const containerH = ref(0);
const start = ref(0);

const visible = computed(() => visibleCount(containerH.value, HEADER_H, ROW_H));
const carouselOn = computed(() => shouldCarousel(props.rows.length, visible.value, !!props.propValue?.carousel));
// 轮播态取环形窗口；非轮播态全量渲染（容器 overflow-y 自然滚动）
const shownIndices = computed(() =>
  carouselOn.value
    ? windowIndices(start.value, visible.value, props.rows.length)
    : props.rows.map((_, i) => i));
const theme = computed(() => resolveChartTheme(props.styleCfg));
const themeVars = computed(() => ({
  '--tl-accent': theme.value.tokens.accent,
  '--tl-accent-strong': theme.value.tokens.accentStrong,
  '--tl-text': theme.value.tokens.text,
  '--tl-number': theme.value.tokens.number,
  '--tl-muted': theme.value.tokens.textDim,
  '--tl-border': theme.value.tokens.border,
  '--tl-up': theme.value.tokens.up,
  '--tl-down': theme.value.tokens.down,
  '--tl-bg': theme.value.tokens.bgDeep
}));

/**
 * 绑定 items 只影响当前表格的展示列；历史配置没有有效绑定时回退全部响应列。
 * 通过响应列下标读取值，避免按选中顺序调整表头后错位取值。
 */
const visibleColumns = computed(() => {
  const responseColumns = Array.isArray(props.columns) ? props.columns : [];
  const indexByColumn = new Map(responseColumns.map((col, index) => [col, index]));
  const items = Array.isArray(props.bind?.items) ? props.bind.items : [];
  const selected = [];
  const seen = new Set();

  for (const item of items) {
    const col = String(item?.col || '').trim();
    if (!col || seen.has(col) || !indexByColumn.has(col)) continue;
    seen.add(col);
    selected.push({
      col,
      index: indexByColumn.get(col),
      label: String(item?.label || '').trim()
    });
  }

  if (selected.length) return selected;
  return responseColumns.map((col, index) => ({ col, index, label: '' }));
});

/** 表头：别名替换 + 单位并入（如 "一般性存款(万元)"） */
function headerOf(col, itemLabel = '') {
  const name = itemLabel || displayName(col, props.columnsMeta);
  const unit = metaOf(col, props.columnsMeta)?.unit;
  return unit ? `${name}(${unit})` : name;
}
function isNum(v) {
  return v != null && v !== '' && !Number.isNaN(Number(v));
}
/** 单元格：columnsMeta.decimals 存在且值为数值时格式化，否则原样（null → —） */
function cellText(col, v) {
  if (v == null || v === '') return '—';
  const d = metaOf(col, props.columnsMeta)?.decimals;
  return d != null && isNum(v) ? fmtNum(v, d) : String(v);
}

let timer = null;
let resizeOb = null;
onMounted(() => {
  containerH.value = wrapEl.value?.clientHeight || 0;
  if (typeof ResizeObserver !== 'undefined' && wrapEl.value) {
    resizeOb = new ResizeObserver(() => { containerH.value = wrapEl.value?.clientHeight || 0; });
    resizeOb.observe(wrapEl.value);
  }
  // 2 秒一拍逐行推进；页面隐藏时暂停（与 BlockContainer 轮询同口径省资源）
  timer = setInterval(() => {
    if (!document.hidden && carouselOn.value) start.value = nextStart(start.value, props.rows.length);
  }, 2000);
});
onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
  if (resizeOb) resizeOb.disconnect();
});
// 数据刷新后从头开始轮播，避免窗口越界
watch(() => props.rows, () => { start.value = 0; });
</script>

<style lang="scss" scoped>
.tl-wrap {
  height: 100%;
  overflow: hidden;
  &.tl-static { overflow-y: auto; }
  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-thumb { background: color-mix(in srgb, var(--tl-accent, var(--scr-cyan)) 30%, transparent); border-radius: 2px; }
}
.tl-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 13px;

  th {
    height: 34px; // = HEADER_H
    padding: 0 8px;
    font-weight: 600;
    text-align: left;
    color: var(--tl-accent, var(--scr-cyan));
    letter-spacing: 1px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    background: linear-gradient(180deg, color-mix(in srgb, var(--tl-accent, var(--scr-cyan)) 14%, transparent), color-mix(in srgb, var(--tl-accent, var(--scr-cyan)) 4%, transparent));
    border-bottom: 1px solid var(--tl-border, var(--scr-border));
  }
  td {
    height: 30px; // = ROW_H
    padding: 0 8px;
    color: var(--tl-text, var(--tl-muted, var(--scr-text)));
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    border-bottom: 1px solid color-mix(in srgb, var(--tl-border, var(--scr-border)) 45%, transparent);
    &.num { text-align: right; font-variant-numeric: tabular-nums; color: var(--tl-number, var(--scr-num)); }
  }
  tbody tr:nth-child(even) { background: rgba(10, 32, 74, .35); }
  tbody tr:hover { background: color-mix(in srgb, var(--tl-accent, var(--scr-cyan)) 8%, transparent); }
}
</style>
