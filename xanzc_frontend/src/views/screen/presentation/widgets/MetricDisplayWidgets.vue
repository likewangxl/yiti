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
.presentation-metric-widgets { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 12px; margin: 12px 0; }
.presentation-metric-widget { min-width: 0; padding: 14px 16px; color: #eaf2ff; background: rgba(16, 40, 75, .84); border: 1px solid rgba(106, 157, 220, .35); border-radius: 8px; }
.presentation-metric-widget__header { display: flex; justify-content: space-between; gap: 8px; align-items: flex-start; }
.presentation-metric-widget__header h2 { margin: 0; font-size: 14px; }
.presentation-metric-widget__header p,.presentation-metric-widget__header small,.presentation-metric-widget__description { margin: 4px 0 0; color: #9fc2df; font-size: 11px; }
.presentation-metric-widget__value { display: block; margin-top: 12px; font-size: 28px; line-height: 1.1; }
.presentation-metric-widget__status { display: block; margin-top: 7px; color: #f4bd5b; font-size: 11px; }
.presentation-metric-widget__progress { height: 6px; margin-top: 12px; overflow: hidden; background: rgba(255,255,255,.12); border-radius: 4px; }
.presentation-metric-widget__progress i { display: block; height: 100%; background: #42e7ee; border-radius: inherit; }
.presentation-metric-widget__sub-fields { display: grid; gap: 4px; margin: 10px 0 0; padding: 0; list-style: none; color: #bcd5ff; font-size: 11px; }
.presentation-metric-widget__sub-fields li { display: flex; justify-content: space-between; gap: 8px; }
.presentation-metric-widget--completion .presentation-metric-widget__value { color: #42e7ee; }
</style>
