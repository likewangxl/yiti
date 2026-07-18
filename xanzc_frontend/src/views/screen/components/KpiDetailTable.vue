<template>
  <div class="kdt-wrap">
    <table v-if="items.length" class="kdt-table">
      <thead>
        <tr>
          <th class="c-name">细项名称</th>
          <th class="c-num">目标值</th>
          <th class="c-num">实际值</th>
          <th class="c-rate">完成率</th>
          <th class="c-gap">缺口</th>
          <th class="c-score">得分</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="it in items" :key="it.code || it.name">
          <td class="c-name" :title="it.name">{{ it.name }}</td>
          <td class="c-num">{{ fmtNum(it.target, numDecimals) }}</td>
          <td class="c-num">{{ fmtNum(it.actual, numDecimals) }}</td>
          <td class="c-rate">
            <!-- 完成率内嵌横向进度条：null（无目标）显示 —；宽度封顶 100 -->
            <div v-if="it.rate != null" class="kdt-bar">
              <div class="kdt-fill" :class="bandOf(it.rate)" :style="{ width: clampPct(it.rate) + '%' }" />
              <span class="kdt-rate-txt">{{ fmtNum(it.rate, 1) }}%</span>
            </div>
            <span v-else class="kdt-none">—</span>
          </td>
          <td class="c-gap" :class="'gap-' + gapOf(it).type">{{ gapOf(it).text }}</td>
          <td class="c-score">{{ fmtNum(it.score, 2) }}</td>
        </tr>
      </tbody>
    </table>
    <div v-else class="scr-block-empty">
      <el-icon class="scr-empty-icon"><DocumentRemove /></el-icon>
      <span>暂无 KPI 细项数据</span>
    </div>
  </div>
</template>

<script setup>
// KPI 细项表（KPI_DETAIL_TABLE）：专为 KPI_DETAIL SNAPSHOT 固定列结构设计——
// 细项名称/目标值/实际值/完成率(内嵌进度条)/缺口(负=绿"已超额"、正=红"还差 X")/得分。
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
// 目标/实际列小数位：columnsMeta.decimals 可用则用，否则默认 2
const numDecimals = computed(() => metaOf('目标值', props.columnsMeta)?.decimals ?? 2);
const gapDecimals = computed(() => metaOf('缺口', props.columnsMeta)?.decimals ?? 2);

function gapOf(it) { return gapText(it.gap, gapDecimals.value); }
/** 完成率分档配色：≥100 达标绿 / ≥60 主题蓝青 / <60 预警橙红 */
function bandOf(rate) {
  if (rate >= 100) return 'ok';
  if (rate >= 60) return 'mid';
  return 'low';
}
</script>

<style lang="scss" scoped>
.kdt-wrap {
  height: 100%;
  overflow-y: auto;
  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-thumb { background: rgba(0, 229, 255, .3); border-radius: 2px; }
}
.kdt-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 13px;

  th {
    height: 32px;
    padding: 0 6px;
    font-weight: 600;
    text-align: left;
    color: var(--scr-cyan);
    letter-spacing: 1px;
    background: linear-gradient(180deg, rgba(0, 229, 255, .14), rgba(0, 229, 255, .04));
    border-bottom: 1px solid var(--scr-border);
  }
  td {
    height: 34px;
    padding: 0 6px;
    color: var(--scr-text);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    border-bottom: 1px solid rgba(125, 155, 201, .12);
  }
  tbody tr:hover { background: rgba(0, 229, 255, .07); }

  .c-name { width: 22%; }
  .c-num { width: 13%; text-align: right; font-variant-numeric: tabular-nums; }
  .c-rate { width: 22%; }
  .c-gap { width: 17%; text-align: right; font-variant-numeric: tabular-nums; }
  .c-score { width: 13%; text-align: right; color: var(--scr-num); font-weight: 700; font-variant-numeric: tabular-nums; }
  th.c-num, th.c-gap, th.c-score { text-align: right; }
}
.kdt-bar {
  position: relative;
  height: 14px;
  background: rgba(125, 155, 201, .15);
  border-radius: 7px;
  overflow: hidden;
}
.kdt-fill {
  height: 100%;
  border-radius: 7px;
  transition: width .6s ease;
  &.ok { background: linear-gradient(90deg, rgba(0, 230, 118, .5), var(--scr-up)); box-shadow: 0 0 8px rgba(0, 230, 118, .5); }
  &.mid { background: linear-gradient(90deg, var(--scr-blue), var(--scr-cyan)); box-shadow: 0 0 8px rgba(0, 229, 255, .4); }
  &.low { background: linear-gradient(90deg, #ff8a65, var(--scr-down)); box-shadow: 0 0 8px rgba(255, 82, 82, .4); }
}
.kdt-rate-txt {
  position: absolute;
  inset: 0;
  text-align: center;
  font-size: 11px;
  line-height: 14px;
  color: #fff;
  text-shadow: 0 0 3px rgba(0, 0, 0, .8);
  font-variant-numeric: tabular-nums;
}
.kdt-none { color: var(--scr-text-dim); }
.gap-lack { color: var(--scr-down); }
.gap-over { color: var(--scr-up); }
.gap-none { color: var(--scr-text-dim); }
</style>
