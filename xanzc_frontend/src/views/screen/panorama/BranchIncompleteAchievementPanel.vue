<template>
  <section class="branch-incomplete-achievement" data-testid="branch-incomplete-chart" aria-label="未完成指标横向图">
    <header class="branch-incomplete-achievement__header">
      <div><span class="branch-incomplete-achievement__kicker">Target gap</span><h2>未完成指标</h2><p>按完成率统一比较，实际、目标和缺口沿用来源单位</p></div>
      <span class="branch-incomplete-achievement__meta">{{ items.length }} 项</span>
    </header>

    <div v-if="items.length" class="branch-incomplete-achievement__list" data-testid="branch-incomplete-list" tabindex="0" role="list" aria-label="未完成指标列表">
      <article v-for="item in items" :key="item.id" class="branch-incomplete-row" data-testid="branch-incomplete-row" :data-item-id="item.id" :data-rate="item.rate" role="listitem">
        <div class="branch-incomplete-row__heading"><strong>{{ item.label || item.key || '未命名指标' }}</strong><span>{{ formatRate(item.rate) }}</span></div>
        <div class="branch-incomplete-row__bar" role="progressbar" :aria-label="`${item.label || item.key || '指标'}完成率`" :aria-valuenow="item.rate" aria-valuemin="0" aria-valuemax="100">
          <i :style="{ width: `${progressWidth(item.rate)}%` }" aria-hidden="true"></i>
        </div>
        <div class="branch-incomplete-row__values"><span>实际 <b>{{ formatMetric(item.actual) }}</b> {{ item.unit || '' }}</span><span>目标 <b>{{ formatMetric(item.target) }}</b> {{ item.unit || '' }}</span><span>缺口 <b>{{ formatMetric(item.gap) }}</b> {{ item.unit || '' }}</span></div>
      </article>
    </div>
    <p v-else class="branch-incomplete-achievement__empty" data-testid="branch-incomplete-empty" role="status">{{ targets.length ? '暂无可展示的未完成指标' : '暂无经营目标数据' }}</p>

    <div v-if="missingItems.length" class="branch-incomplete-achievement__missing" data-testid="branch-incomplete-missing" role="note">
      <strong>数据不足，未纳入完成率横图</strong>
      <p v-for="item in missingItems" :key="item.id" class="branch-incomplete-missing-row" data-testid="branch-incomplete-missing-row" :data-item-id="item.id">{{ item.label || item.key || '未命名指标' }}：{{ item.missingReason || '缺少有效实际值或目标值' }}</p>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import { buildBranchAchievementModel } from './branchAchievementModel.js';

const props = defineProps({ targets: { type: Array, default: () => [] } });
const achievement = computed(() => buildBranchAchievementModel(props.targets));
const items = computed(() => achievement.value.items.filter(item => item.state === 'incomplete'));
const missingItems = computed(() => achievement.value.items.filter(item => item.state === 'missing'));

function finite(value) {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}
function formatMetric(value) {
  const number = finite(value);
  return number === null ? '—' : new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(number);
}
function formatRate(value) {
  const number = finite(value);
  return number === null ? '—' : `${number.toFixed(2)}%`;
}
function progressWidth(value) {
  const number = finite(value);
  return number === null ? 0 : Math.max(0, Math.min(100, number));
}
</script>

<style scoped>
.branch-incomplete-achievement{display:flex;min-width:0;min-height:0;height:100%;flex-direction:column;color:var(--panorama-text,#eaf2ff);background:var(--panorama-panel-deep,rgba(4,14,39,.9));border:1px solid var(--panorama-border,rgba(119,163,255,.3));border-radius:8px;box-shadow:inset 0 1px 0 rgba(201,231,255,.05),0 8px 22px rgba(0,0,0,.12)}
.branch-incomplete-achievement__header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:11px 14px;border-bottom:1px solid var(--panorama-border-soft,rgba(119,163,255,.16))}.branch-incomplete-achievement__kicker{color:#72b9ed;font-size:10px;letter-spacing:.08em}.branch-incomplete-achievement h2{margin:3px 0 0;font-size:16px}.branch-incomplete-achievement p{margin:4px 0 0;color:#8fa9db;font-size:10px;line-height:1.5}.branch-incomplete-achievement__meta{color:#8fa9db;font-size:11px;white-space:nowrap}.branch-incomplete-achievement__list{min-height:0;overflow:auto;padding:9px 14px;flex:1 1 auto}.branch-incomplete-row{padding:9px 0;border-bottom:1px solid rgba(119,163,255,.12)}.branch-incomplete-row__heading,.branch-incomplete-row__values{display:flex;align-items:center;justify-content:space-between;gap:8px;font-size:11px}.branch-incomplete-row__heading strong{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.branch-incomplete-row__heading span{color:#ffcf80;font-variant-numeric:tabular-nums;white-space:nowrap}.branch-incomplete-row__bar{height:8px;margin:7px 0;border-radius:5px;background:rgba(119,163,255,.13);overflow:hidden}.branch-incomplete-row__bar i{display:block;height:100%;min-width:0;border-radius:5px;background:linear-gradient(90deg,#ff7486,#ffc45e)}.branch-incomplete-row__values{color:#8fa9db;font-size:10px}.branch-incomplete-row__values b{color:#eaf2ff;font-weight:600}.branch-incomplete-achievement__empty{padding:28px 14px;text-align:center}.branch-incomplete-achievement__missing{padding:9px 14px;border-top:1px solid rgba(255,196,94,.2);color:#ffc45e;font-size:10px}.branch-incomplete-achievement__missing p{color:#ffc45e}.branch-incomplete-achievement :focus-visible{outline:2px solid #4de8ef;outline-offset:3px}
</style>
