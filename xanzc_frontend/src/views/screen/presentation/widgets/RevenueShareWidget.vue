<template>
  <section class="revenue-share-widget" data-testid="revenue-share-chart" :data-state="share.ready ? 'READY' : 'PENDING'">
    <div class="revenue-share-widget__headline">
      <span>中间业务收入占比</span>
      <strong>{{ share.shareText }}</strong>
    </div>
    <div
      class="revenue-share-widget__track"
      role="img"
      :aria-label="share.ready ? `中间业务收入占营业收入 ${share.shareText}` : '中间业务收入占营业收入，占比待核对'"
    >
      <template v-if="share.ready">
        <i data-testid="revenue-share-part" :style="{ width: `${share.share}%` }" aria-hidden="true"></i>
        <b data-testid="revenue-share-rest" :style="{ width: `${share.remaining}%` }" aria-hidden="true"></b>
      </template>
    </div>
    <div class="revenue-share-widget__amounts">
      <div :data-display-unit="amountUnitToken(operating)"><span>营业收入</span><strong>{{ operating.text || '—' }}</strong><small>{{ operating.monthDelta?.text || '较上月 暂无数据' }}</small></div>
      <div :data-display-unit="amountUnitToken(intermediary)"><span>其中 · 中间业务收入</span><strong>{{ intermediary.text || '—' }}</strong><small>{{ intermediary.monthDelta?.text || '较上月 暂无数据' }}</small></div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import { buildRevenueShareModel } from '../model/revenueShareModel';

const props = defineProps({
  operating: { type: Object, default: () => ({}) },
  intermediary: { type: Object, default: () => ({}) }
});
const share = computed(() => buildRevenueShareModel(props.operating, props.intermediary));

function amountUnitToken(item) {
  const unit = String(item?.unit || '').trim().toUpperCase();
  return ({ 元: 'YUAN', YUAN: 'YUAN', 万元: 'TEN_THOUSAND', TEN_THOUSAND: 'TEN_THOUSAND', 亿元: 'HUNDRED_MILLION', HUNDRED_MILLION: 'HUNDRED_MILLION' })[unit] || '';
}
</script>

<style scoped>
.revenue-share-widget { box-sizing: border-box; display: flex; min-width: 0; height: 100%; padding: 10px 12px; flex-direction: column; justify-content: center; gap: 10px; color: #eaf2ff; background: rgba(8, 27, 72, .88); border: 1px solid rgba(88, 150, 255, .48); border-radius: 8px; }
.revenue-share-widget__headline { display: flex; min-width: 0; justify-content: space-between; align-items: baseline; gap: 5px; font-size: 11px; }
.revenue-share-widget__headline strong { flex: 0 0 auto; color: #75e0ff; font-size: clamp(14px, 1.15vw, 22px); font-variant-numeric: tabular-nums; }
.revenue-share-widget__track { display: flex; height: 11px; overflow: hidden; background: rgba(133, 164, 222, .24); border-radius: 8px; }
.revenue-share-widget__track i { display: block; background: #56d8f5; box-shadow: 0 0 8px #56d8f5; }
.revenue-share-widget__track b { display: block; background: #5378d4; }
.revenue-share-widget__amounts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.revenue-share-widget__amounts > div { display: grid; min-width: 0; gap: 2px; }
.revenue-share-widget__amounts span { color: #a9c1ed; font-size: 10px; }
.revenue-share-widget__amounts strong { display: block; min-width: 0; color: #f4f8ff; font-size: clamp(10px, .85vw, 15px); line-height: 1.15; overflow-wrap: anywhere; word-break: break-word; }
.revenue-share-widget__amounts small { display: block; min-width: 0; color: #8fa9db; font-size: 9px; line-height: 1.2; overflow-wrap: anywhere; word-break: break-word; }
.revenue-share-widget__amounts > div[data-display-unit="YUAN"] strong { font-size: clamp(8px, .7vw, 13px); }
.revenue-share-widget__amounts > div[data-display-unit="TEN_THOUSAND"] strong { font-size: clamp(10px, .85vw, 15px); }
.revenue-share-widget__amounts > div[data-display-unit="HUNDRED_MILLION"] strong { font-size: clamp(12px, 1vw, 17px); }
@media (max-width: 1500px) {
  .revenue-share-widget__amounts { grid-template-columns: minmax(0, 1fr); gap: 4px; }
}
</style>
