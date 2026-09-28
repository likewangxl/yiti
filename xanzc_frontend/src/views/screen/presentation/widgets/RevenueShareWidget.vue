<template>
  <section class="revenue-share-widget" data-testid="revenue-share-chart" :data-state="share.ready ? 'READY' : 'PENDING'">
    <div
      class="revenue-share-widget__section revenue-share-widget__section--operating"
      data-testid="revenue-share-operating-section"
      aria-label="营业收入"
      :data-display-unit="amountUnitToken(operating)"
    >
      <span class="revenue-share-widget__label">营业收入</span>
      <strong class="revenue-share-widget__value">{{ operating.text || '—' }}</strong>
      <small class="revenue-share-widget__delta">{{ operating.monthDelta?.text || '较上月 暂无数据' }}</small>
    </div>
    <div
      class="revenue-share-widget__section revenue-share-widget__section--intermediary"
      data-testid="revenue-share-intermediary-section"
      aria-label="中间业务收入"
      :data-display-unit="amountUnitToken(intermediary)"
    >
      <span class="revenue-share-widget__label">中间业务收入</span>
      <strong class="revenue-share-widget__value">{{ intermediary.text || '—' }}</strong>
      <small class="revenue-share-widget__delta">{{ intermediary.monthDelta?.text || '较上月 暂无数据' }}</small>
      <div class="revenue-share-widget__headline">
        <span class="revenue-share-widget__label" data-testid="revenue-share-ratio-label">占营业收入</span>
        <strong data-testid="revenue-share-percentage">{{ share.shareText }}</strong>
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
.revenue-share-widget { box-sizing: border-box; display: grid; min-width: 0; min-height: 0; height: 100%; padding: 8px; grid-template-rows: minmax(0, .85fr) minmax(0, 1.15fr); gap: 8px; color: #eaf2ff; }
.revenue-share-widget__section { box-sizing: border-box; display: grid; min-width: 0; min-height: 0; overflow: hidden; padding: 8px 10px; background: rgba(8, 27, 72, .88); border: 1px solid rgba(88, 150, 255, .48); border-radius: 8px; }
.revenue-share-widget__section--operating { grid-template-rows: auto minmax(0, 1fr) auto; gap: 3px; }
.revenue-share-widget__section--intermediary { grid-template-rows: auto minmax(0, 1fr) auto auto auto; gap: 3px; }
.revenue-share-widget__headline { display: flex; min-width: 0; justify-content: space-between; align-items: baseline; gap: 5px; }
.revenue-share-widget__headline strong { flex: 0 0 auto; color: #75e0ff; font-size: clamp(14px, 1.15vw, 22px); font-variant-numeric: tabular-nums; }
.revenue-share-widget__label { min-width: 0; color: #a9c1ed; font-size: clamp(10px, .72vw, 13px); line-height: 1.2; overflow-wrap: anywhere; word-break: break-word; }
.revenue-share-widget__value { align-self: center; display: block; min-width: 0; color: #f4f8ff; font-size: clamp(17px, 2vw, 34px); font-weight: 700; line-height: 1.05; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; word-break: break-word; }
.revenue-share-widget__delta { display: block; min-width: 0; color: #8fa9db; font-size: clamp(9px, .62vw, 12px); line-height: 1.2; overflow-wrap: anywhere; word-break: break-word; }
.revenue-share-widget__track { display: flex; min-width: 0; width: 100%; height: 11px; overflow: hidden; background: rgba(133, 164, 222, .24); border-radius: 8px; }
.revenue-share-widget__track i { display: block; background: #56d8f5; box-shadow: 0 0 8px #56d8f5; }
.revenue-share-widget__track b { display: block; background: #5378d4; }
.revenue-share-widget__section[data-display-unit="YUAN"] .revenue-share-widget__value { font-size: clamp(15px, 1.75vw, 30px); }
.revenue-share-widget__section[data-display-unit="TEN_THOUSAND"] .revenue-share-widget__value { font-size: clamp(17px, 2vw, 34px); }
.revenue-share-widget__section[data-display-unit="HUNDRED_MILLION"] .revenue-share-widget__value { font-size: clamp(19px, 2.2vw, 38px); }
</style>
