<template>
  <div class="pl-wrap">
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
  &::-webkit-scrollbar-thumb { background: rgba(0, 229, 255, .3); border-radius: 2px; }
}
.pl-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 3px 2px;
  border-radius: 4px;
  &:hover { background: rgba(0, 229, 255, .07); }
}
.pl-name {
  width: 110px;
  flex: none;
  font-size: 13px;
  color: var(--scr-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pl-bar {
  position: relative;
  flex: 1;
  height: 16px;
  background: rgba(125, 155, 201, .15);
  border: 1px solid rgba(125, 155, 201, .2);
  border-radius: 8px;
  overflow: hidden;
}
.pl-fill {
  height: 100%;
  border-radius: 8px;
  transition: width .6s ease;
  &.ok { background: linear-gradient(90deg, rgba(0, 230, 118, .5), var(--scr-up)); box-shadow: 0 0 10px rgba(0, 230, 118, .5); }
  &.mid { background: linear-gradient(90deg, var(--scr-blue), var(--scr-cyan)); box-shadow: 0 0 10px rgba(0, 229, 255, .4); }
  &.low { background: linear-gradient(90deg, #ff8a65, var(--scr-down)); box-shadow: 0 0 10px rgba(255, 82, 82, .4); }
}
.pl-rate {
  position: absolute;
  inset: 0;
  text-align: center;
  font-size: 11px;
  line-height: 16px;
  color: #fff;
  text-shadow: 0 0 3px rgba(0, 0, 0, .8);
  font-variant-numeric: tabular-nums;
}
.pl-gap {
  width: 130px;
  flex: none;
  text-align: right;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  &.gap-lack { color: var(--scr-down); }
  &.gap-over { color: var(--scr-up); }
  &.gap-none { color: var(--scr-text-dim); }
}
</style>
