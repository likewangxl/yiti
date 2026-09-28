<template>
  <section class="branch-core-metrics" data-testid="branch-core-metrics" aria-label="支行核心经营指标">
    <article
      v-for="metric in metrics"
      :key="metric.key"
      class="branch-core-metric"
      data-testid="branch-core-metric"
      :data-metric-key="metric.key"
      :data-state="metric.value === null ? 'missing' : 'available'"
    >
      <div class="branch-core-metric__heading">
        <span class="branch-core-metric__mark" aria-hidden="true"></span>
        <h3>{{ metric.title }}</h3>
        <span class="branch-core-metric__status">{{ metric.status }}</span>
      </div>
      <div class="branch-core-metric__value-line">
        <strong :class="{ 'is-missing': metric.value === null }">{{ formatValue(metric.value) }}</strong>
        <span>{{ metric.unit || (metric.value === null ? '—' : '') }}</span>
      </div>
      <div class="branch-core-metric__meta">
        <span>数据日期</span>
        <b>{{ metric.date || '—' }}</b>
      </div>
    </article>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import { buildBranchCoreMetrics } from './branchAchievementModel.js';

const props = defineProps({ model: { type: Object, default: () => ({}) } });

const DEFINITIONS = Object.freeze([
  ['corpDeposit', '对公存款'],
  ['retailDeposit', '对私存款'],
  ['corpLoan', '对公贷款'],
  ['retailLoan', '对私贷款'],
  ['revenue', '营业收入'],
  ['intermediaryIncome', '中间业务收入']
]);

const metrics = computed(() => {
  const source = buildBranchCoreMetrics(props.model && typeof props.model === 'object' ? props.model : {});
  const sourceList = Array.isArray(source) ? source : [];
  return DEFINITIONS.map(([key, title]) => {
    const item = sourceList.find(candidate => candidate?.key === key) || {};
    const value = finiteValue(item.value);
    return {
      key,
      title,
      value,
      unit: item.unit == null ? '' : String(item.unit),
      date: item.date ?? item.dataDate ?? item.data_date ?? '',
      status: item.status || (value === null ? '数据不足' : '已提供')
    };
  });
});

function finiteValue(value) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function formatValue(value) {
  const number = finiteValue(value);
  return number === null ? '—' : new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(number);
}
</script>

<style scoped>
.branch-core-metrics {
  --branch-core-bg: rgba(4, 17, 47, .9);
  --branch-core-border: rgba(87, 170, 244, .32);
  --branch-core-border-soft: rgba(115, 157, 231, .16);
  --branch-core-text: #edf6ff;
  --branch-core-soft: #b4caed;
  --branch-core-muted: #8098c6;
  --branch-core-cyan: #4de8ef;
  --branch-core-purple: #a979ff;
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--branch-core-border);
  border-radius: 10px;
  color: var(--branch-core-text);
  background: radial-gradient(circle at 80% 0, rgba(65, 128, 216, .2), transparent 34%), var(--branch-core-bg);
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
  font-variant-numeric: tabular-nums;
}

.branch-core-metrics *, .branch-core-metrics *::before, .branch-core-metrics *::after { box-sizing: border-box; }
.branch-core-metric { min-width: 0; padding: 13px 12px 12px; border-right: 1px solid var(--branch-core-border-soft); }
.branch-core-metric:last-child { border-right: 0; }
.branch-core-metric__heading { display: flex; min-width: 0; align-items: center; gap: 7px; }
.branch-core-metric__mark { width: 8px; height: 8px; flex: 0 0 auto; border-radius: 2px; background: linear-gradient(135deg, var(--branch-core-cyan), var(--branch-core-purple)); transform: rotate(45deg); }
.branch-core-metric:nth-child(even) .branch-core-metric__mark { background: linear-gradient(135deg, var(--branch-core-purple), var(--branch-core-cyan)); }
.branch-core-metric h3 { min-width: 0; margin: 0; overflow: hidden; color: var(--branch-core-soft); font-size: 12px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.branch-core-metric__status { max-width: 78px; margin-left: auto; overflow: hidden; color: var(--branch-core-cyan); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.branch-core-metric[data-state="missing"] .branch-core-metric__status { color: var(--branch-core-muted); }
.branch-core-metric__value-line { display: flex; min-width: 0; align-items: baseline; gap: 5px; margin-top: 13px; }
.branch-core-metric__value-line strong { overflow: hidden; color: var(--branch-core-text); font-size: clamp(18px, 1.65vw, 29px); font-weight: 800; letter-spacing: -.02em; line-height: 1.1; text-overflow: ellipsis; }
.branch-core-metric__value-line strong.is-missing { color: var(--branch-core-muted); }
.branch-core-metric__value-line span { flex: 0 0 auto; color: var(--branch-core-muted); font-size: 10px; }
.branch-core-metric__meta { display: flex; min-width: 0; align-items: baseline; gap: 5px; margin-top: 9px; color: var(--branch-core-muted); font-size: 9px; }
.branch-core-metric__meta b { overflow: hidden; color: var(--branch-core-soft); font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }

@media (max-width: 1180px) {
  .branch-core-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .branch-core-metric:nth-child(3n) { border-right: 0; }
  .branch-core-metric:nth-child(n + 4) { border-top: 1px solid var(--branch-core-border-soft); }
}

@media (max-width: 620px) {
  .branch-core-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .branch-core-metric:nth-child(3n) { border-right: 1px solid var(--branch-core-border-soft); }
  .branch-core-metric:nth-child(2n) { border-right: 0; }
  .branch-core-metric:nth-child(n + 3) { border-top: 1px solid var(--branch-core-border-soft); }
  .branch-core-metric__status { display: none; }
}
</style>
