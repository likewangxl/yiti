<template>
  <div
    class="completion-ring-gauge"
    :class="{ 'completion-ring-gauge--empty': progress === 0, 'completion-ring-gauge--missing': isMissing }"
    data-testid="completion-ring-gauge"
    :data-state="isMissing ? 'MISSING' : 'READY'"
    :data-progress="progress"
    role="img"
    :aria-label="ariaLabel"
    :style="{ '--completion-ring-accent': accent }"
  >
    <svg
      class="completion-ring-gauge__svg"
      viewBox="0 0 72 72"
      aria-hidden="true"
      focusable="false"
    >
      <circle
        class="completion-ring-gauge__track"
        cx="36"
        cy="36"
        r="29"
      />
      <circle
        class="completion-ring-gauge__progress"
        data-testid="completion-ring-progress"
        cx="36"
        cy="36"
        r="29"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="dashOffset"
      />
    </svg>
    <span class="completion-ring-gauge__value" data-testid="completion-ring-value">{{ displayText }}</span>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const CIRCUMFERENCE = 2 * Math.PI * 29;

const props = defineProps({
  text: { type: [String, Number], default: '—' },
  value: { type: [Number, String], default: null },
  accent: { type: String, default: 'var(--metric-accent, var(--panorama-cyan, #4de8ef))' },
  label: { type: String, default: '完成率' }
});

function finite(value) {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

const numericValue = computed(() => finite(props.value));
const isMissing = computed(() => numericValue.value === null);
const progress = computed(() => {
  if (numericValue.value === null) return 0;
  return Math.max(0, Math.min(100, numericValue.value));
});
const circumference = CIRCUMFERENCE;
const dashOffset = computed(() => CIRCUMFERENCE * (1 - progress.value / 100));
const displayText = computed(() => {
  const text = props.text === null || props.text === undefined ? '' : String(props.text).trim();
  if (!text) return '—';
  // 缺数时拒绝把旧的数值文本当作当前完成率，避免空值被伪装成0%或其他指标。
  if (isMissing.value && /\d/.test(text)) return '—';
  return text;
});
const ariaLabel = computed(() => `${props.label || '完成率'}：${displayText.value}`);
</script>

<style scoped>
.completion-ring-gauge {
  position: relative;
  display: inline-grid;
  width: 64px;
  height: 64px;
  flex: 0 0 64px;
  place-items: center;
  color: var(--completion-ring-accent);
}
.completion-ring-gauge__svg {
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
  transform: rotate(-90deg);
}
.completion-ring-gauge__track,
.completion-ring-gauge__progress {
  fill: none;
  stroke-width: 7;
}
.completion-ring-gauge__track { stroke: rgba(133, 164, 222, .24); }
.completion-ring-gauge__progress {
  stroke: var(--completion-ring-accent);
  stroke-linecap: round;
  filter: drop-shadow(0 0 4px var(--completion-ring-accent));
  transition: stroke-dashoffset .25s ease;
}
.completion-ring-gauge--empty .completion-ring-gauge__progress { opacity: 0; }
.completion-ring-gauge__value {
  position: absolute;
  max-width: 56px;
  overflow: hidden;
  color: #f4f8ff;
  font-size: 10px;
  font-weight: 750;
  line-height: 1;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.completion-ring-gauge--missing .completion-ring-gauge__value {
  color: var(--panorama-text-dim, #8fa9db);
  font-size: 9px;
}
@media (max-width: 620px) {
  .completion-ring-gauge { width: 58px; height: 58px; flex-basis: 58px; }
  .completion-ring-gauge__value { max-width: 50px; font-size: 9px; }
}
</style>
