<template>
  <section class="presentation-metric-widgets" data-testid="presentation-metric-widgets" aria-label="配置化指标与完成情况">
    <article
      v-for="item in components"
      :key="item.componentId"
      class="presentation-metric-widget"
      :class="[`presentation-metric-widget--${String(item.componentType || '').toLowerCase()}`, `presentation-metric-widget--${String(item.layoutRegion || '').toLowerCase()}`]"
      :data-component-id="item.componentId"
      :data-component-type="item.componentType"
      :data-layout-region="item.layoutRegion"
      :data-state="item.state"
    >
      <header class="presentation-metric-widget__header">
        <div>
          <h2>{{ item.title || '—' }}</h2>
          <p v-if="item.subtitle">{{ item.subtitle }}</p>
        </div>
        <small v-if="item.metricName">{{ item.metricName }}</small>
      </header>
      <strong class="presentation-metric-widget__value" data-testid="presentation-metric-value">{{ item.text }}</strong>
      <span v-if="item.state !== 'READY'" class="presentation-metric-widget__status" data-testid="presentation-metric-status">
        {{ statusText(item.state) }}
      </span>
      <div
        v-if="item.componentType === 'COMPLETION'"
        class="presentation-metric-widget__progress"
        role="progressbar"
        :aria-label="item.title || '完成情况'"
        :aria-valuenow="item.progress === null ? undefined : item.progress"
        aria-valuemin="0"
        aria-valuemax="100"
      ><i :style="{ width: `${item.progress === null ? 0 : item.progress}%` }" aria-hidden="true"></i></div>
      <small v-if="item.description" class="presentation-metric-widget__description">{{ item.description }}</small>
      <ul v-if="item.subFields?.length" class="presentation-metric-widget__sub-fields">
        <li v-for="subField in item.subFields" :key="subField.key">
          <span>{{ subField.label || subField.field || '辅助指标' }}</span><strong>{{ subField.text }}</strong>
        </li>
      </ul>
    </article>
  </section>
</template>

<script setup>
defineProps({
  components: { type: Array, default: () => [] }
});

function statusText(state) {
  return ({ NO_SOURCE: '待接入', NO_VALUE: '暂无有效值' }[state] || '暂不可用');
}
</script>

<style scoped>
.presentation-metric-widgets { display: block; width: 100%; height: 100%; min-width: 0; margin: 0; }
.presentation-metric-widget {
  --metric-accent: var(--panorama-cyan, #4de8ef);
  --metric-border: rgba(77, 232, 239, .42);
  position: relative;
  display: grid;
  min-width: 0;
  min-height: 82px;
  height: 100%;
  align-content: center;
  gap: 4px;
  padding: 12px 16px;
  overflow: hidden;
  color: var(--panorama-text, #eaf2ff);
  background: var(--panorama-panel, rgba(8, 24, 61, .86));
  border: 1px solid var(--metric-border);
  border-radius: 8px;
  box-shadow: inset 0 1px 0 rgba(201, 231, 255, .06), 0 8px 22px rgba(0, 0, 0, .14);
}
.presentation-metric-widget::before { width: 3px; height: 32px; position: absolute; top: 50%; left: 0; border-radius: 0 2px 2px 0; background: var(--metric-accent); box-shadow: 0 0 12px var(--metric-accent); content: ''; transform: translateY(-50%); }
.presentation-metric-widget--completion { --metric-accent: var(--panorama-violet, #a979ff); --metric-border: rgba(169, 121, 255, .48); background: rgba(19, 24, 74, .88); }
.presentation-metric-widget__header { display: flex; justify-content: space-between; gap: 8px; align-items: flex-start; min-width: 0; }
.presentation-metric-widget__header > div { min-width: 0; }
.presentation-metric-widget__header h2 { margin: 0; overflow: hidden; color: var(--panorama-text, #eaf2ff); font-size: 13px; font-weight: 600; line-height: 1.25; text-overflow: ellipsis; white-space: nowrap; }
.presentation-metric-widget__header p,.presentation-metric-widget__header small,.presentation-metric-widget__description { margin: 3px 0 0; overflow: hidden; color: var(--panorama-text-dim, #8fa9db); font-size: 10px; line-height: 1.3; text-overflow: ellipsis; white-space: nowrap; }
.presentation-metric-widget__header small { flex: 0 0 auto; }
.presentation-metric-widget__value { display: block; min-width: 0; max-width: 100%; margin-top: 2px; color: #f4f8ff; font-size: clamp(18px, 1.55vw, 30px); font-weight: 750; line-height: 1.12; overflow-wrap: anywhere; word-break: break-word; }
.presentation-metric-widget__status { display: block; margin-top: 2px; color: var(--panorama-amber, #ffc45e); font-size: 10px; line-height: 1.25; }
.presentation-metric-widget__progress { height: 7px; margin-top: 4px; overflow: hidden; background: rgba(2, 9, 22, .72); border: 1px solid rgba(133, 164, 222, .42); border-radius: 4px; }
.presentation-metric-widget__progress i { display: block; height: 100%; background: var(--metric-accent); border-radius: inherit; box-shadow: 0 0 8px var(--metric-accent); }
.presentation-metric-widget__description { margin-top: 2px; }
.presentation-metric-widget__sub-fields { display: grid; gap: 3px; margin: 5px 0 0; padding: 0; overflow: auto; list-style: none; color: #bcd5ff; font-size: 10px; }
.presentation-metric-widget__sub-fields li { display: flex; justify-content: space-between; gap: 8px; }
.presentation-metric-widget__sub-fields strong { color: #f2f6ff; font-weight: 600; }
.presentation-metric-widget--completion .presentation-metric-widget__value { color: var(--metric-accent); }
@media (max-width: 1366px) {
  .presentation-metric-widget { padding-right: 12px; padding-left: 12px; }
  .presentation-metric-widget__value { font-size: clamp(17px, 1.65vw, 24px); }
}
@media (max-width: 620px) {
  .presentation-metric-widget { min-height: 76px; padding: 10px 12px; }
  .presentation-metric-widget__header h2 { font-size: 12px; }
  .presentation-metric-widget__value { font-size: clamp(16px, 5.2vw, 22px); }
}
</style>
