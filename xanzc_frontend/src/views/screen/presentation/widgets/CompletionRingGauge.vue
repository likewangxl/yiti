<template>
  <div
    class="completion-ring-gauge"
    :class="{
      'completion-ring-gauge--missing': isMissing,
      'completion-ring-gauge--compact': compact
    }"
    data-testid="completion-ring-gauge"
    :data-state="isMissing ? 'MISSING' : 'READY'"
    :data-mode="compact ? 'compact' : 'dashboard'"
    :data-progress="progress"
    role="img"
    :aria-label="ariaLabel"
    :style="{ '--completion-ring-accent': accent }"
  >
    <svg
      v-if="compact"
      class="completion-ring-gauge__svg"
      data-testid="completion-ring-svg"
      viewBox="0 0 80 80"
      aria-hidden="true"
      focusable="false"
    >
      <circle
        class="completion-ring-gauge__track"
        data-testid="completion-ring-track"
        cx="40"
        cy="40"
        r="34"
        pathLength="100"
      />
      <circle
        class="completion-ring-gauge__progress"
        data-testid="completion-ring-progress"
        cx="40"
        cy="40"
        r="34"
        pathLength="100"
        stroke-dasharray="100"
        :class="{ 'completion-ring-gauge__progress--empty': progress === 0 }"
        :stroke-dashoffset="arcOffset"
      />
      <g transform="rotate(90 40 40)">
        <text
          class="completion-ring-gauge__compact-value"
          data-testid="completion-ring-value"
          x="40"
          y="40"
          text-anchor="middle"
          dominant-baseline="central"
          :textLength="isMissing ? undefined : 50"
          lengthAdjust="spacingAndGlyphs"
        >{{ displayText }}</text>
      </g>
    </svg>
    <VChart v-else class="completion-ring-gauge__chart" :option="option" autoresize aria-hidden="true" />
    <span v-if="!compact" class="completion-ring-gauge__value" data-testid="completion-ring-value">{{ displayText }}</span>
    <span v-if="compact && isMissing" class="completion-ring-gauge__status" data-testid="completion-ring-status">待接入</span>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { use } from 'echarts/core';
import { GaugeChart } from 'echarts/charts';
import { CanvasRenderer } from 'echarts/renderers';
import VChart from 'vue-echarts';

use([CanvasRenderer, GaugeChart]);

const props = defineProps({
  text: { type: [String, Number], default: '—' },
  value: { type: [Number, String], default: null },
  accent: { type: String, default: 'var(--metric-accent, var(--panorama-cyan, #4de8ef))' },
  label: { type: String, default: '完成率' },
  compact: { type: Boolean, default: false }
});

function finite(value) {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

const numericValue = computed(() => finite(props.value));
const isMissing = computed(() => numericValue.value === null);
const progress = computed(() => isMissing.value ? 0 : Math.max(0, Math.min(100, numericValue.value)));
const arcOffset = computed(() => 100 - progress.value);
const displayText = computed(() => {
  if (isMissing.value) return '—';
  const text = props.text === null || props.text === undefined ? '' : String(props.text).trim();
  return text || '—';
});
const ariaLabel = computed(() => `${props.label || '完成率'}：${isMissing.value ? '待接入' : displayText.value}`);

// 刻度与指针按百分比绘制；超额或负值只钳制指针，文字始终使用来源值。
const option = computed(() => ({
  animation: false,
  series: [{
    type: 'gauge',
    min: 0,
    max: 100,
    startAngle: 220,
    endAngle: -40,
    center: ['50%', '57%'],
    radius: '90%',
    splitNumber: 10,
    axisLine: {
      roundCap: false,
      lineStyle: {
        width: 10,
        color: [[0.35, '#55d8d0'], [0.75, '#319cde'], [1, '#fb6474']]
      }
    },
    axisTick: {
      show: true,
      splitNumber: 5,
      distance: -10,
      length: 3,
      lineStyle: { color: '#e6f5ff', width: 1 }
    },
    splitLine: {
      show: true,
      distance: -10,
      length: 6,
      lineStyle: { color: '#f2fbff', width: 1 }
    },
    axisLabel: {
      show: true,
      distance: 15,
      color: '#b3d9f6',
      fontSize: 8,
      formatter: value => value % 20 === 0 ? String(value) : ''
    },
    pointer: {
      show: !isMissing.value,
      length: '58%',
      width: 3,
      itemStyle: { color: '#47b6f3' }
    },
    anchor: {
      show: !isMissing.value,
      size: 5,
      itemStyle: { color: '#47b6f3', borderColor: '#c7efff', borderWidth: 1 }
    },
    title: { show: false },
    detail: { show: false },
    data: isMissing.value ? [] : [{ value: progress.value }]
  }]
}));
</script>

<style scoped>
.completion-ring-gauge {
  position: relative;
  display: inline-block;
  width: 150px;
  height: 100px;
  flex: 0 0 150px;
  color: var(--completion-ring-accent);
}
.completion-ring-gauge__chart { display: block; width: 100%; height: 100%; }
.completion-ring-gauge__chart :deep(.vue-echarts-inner) { width: 100% !important; height: 100% !important; }
.completion-ring-gauge__svg { display: block; width: 100%; height: 100%; transform: rotate(-90deg); }
.completion-ring-gauge__track,
.completion-ring-gauge__progress { fill: none; stroke-width: 7; }
.completion-ring-gauge__track { stroke: var(--completion-ring-accent); opacity: .22; }
.completion-ring-gauge__progress { stroke: var(--completion-ring-accent); stroke-linecap: round; }
.completion-ring-gauge__progress--empty { visibility: hidden; }
.completion-ring-gauge__value {
  position: absolute;
  right: 0;
  bottom: 1px;
  left: 0;
  overflow: hidden;
  color: #69caff;
  font-size: 12px;
  font-weight: 750;
  line-height: 1;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
  pointer-events: none;
}
.completion-ring-gauge--compact {
  width: 76px;
  height: 76px;
  flex: 0 0 76px;
}
.completion-ring-gauge__compact-value {
  color: var(--completion-ring-accent);
  fill: var(--completion-ring-accent);
  font-size: 13px;
  font-weight: 750;
  text-anchor: middle;
  pointer-events: none;
}
.completion-ring-gauge__status {
  position: absolute;
  right: 0;
  top: calc(50% + 12px);
  left: 0;
  color: var(--panorama-text-dim, #8fa9db);
  font-size: 10px;
  line-height: 1;
  text-align: center;
  pointer-events: none;
}
.completion-ring-gauge--missing .completion-ring-gauge__value {
  color: var(--panorama-text-dim, #8fa9db);
  font-size: 10px;
}
.completion-ring-gauge--missing .completion-ring-gauge__compact-value {
  fill: var(--panorama-text-dim, #8fa9db);
  font-size: 10px;
}
@media (max-width: 620px) {
  .completion-ring-gauge { width: 124px; height: 88px; flex-basis: 124px; }
  .completion-ring-gauge__value { font-size: 10px; }
  .completion-ring-gauge--compact { width: 72px; height: 72px; flex-basis: 72px; }
  .completion-ring-gauge--compact .completion-ring-gauge__compact-value { font-size: 12px; }
}
</style>
