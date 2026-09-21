<template>
  <div
    class="completion-water-gauge"
    :class="`tone-${tone}`"
    :data-level="levelAttribute"
    :data-tone="tone"
    data-testid="completion-water-gauge"
    role="img"
    :aria-label="ariaLabel"
  >
    <svg
      class="completion-water-gauge__svg"
      viewBox="0 0 100 100"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient :id="gradientId('front')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" :stop-color="toneColor" stop-opacity="0.94" />
          <stop offset="1" :stop-color="toneColor" stop-opacity="0.62" />
        </linearGradient>
        <linearGradient :id="gradientId('back')" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" :stop-color="toneColor" stop-opacity="0.38" />
          <stop offset="1" :stop-color="toneColor" stop-opacity="0.18" />
        </linearGradient>
        <clipPath :id="clipId">
          <circle cx="50" cy="50" r="44" />
        </clipPath>
      </defs>

      <circle class="completion-water-gauge__halo" cx="50" cy="50" r="48" />
      <circle class="completion-water-gauge__track" cx="50" cy="50" r="45" />

      <g v-if="hasValue" :clip-path="`url(#${clipId})`">
        <rect v-if="level === 100" x="6" y="6" width="88" height="88" :fill="`url(#${gradientId('front')})`" />
        <g v-else-if="level > 0"
          class="completion-water-gauge__level"
          :style="{ transform: `translateY(${levelOffset}px)` }"
        >
          <path
            class="completion-water-gauge__wave completion-water-gauge__wave--back"
            :d="wavePathBack"
            :fill="`url(#${gradientId('back')})`"
          />
          <path
            class="completion-water-gauge__wave completion-water-gauge__wave--front"
            :d="wavePathFront"
            :fill="`url(#${gradientId('front')})`"
          />
        </g>
      </g>

      <circle
        class="completion-water-gauge__edge"
        cx="50"
        cy="50"
        r="44"
        :stroke="toneColor"
      />
      <circle class="completion-water-gauge__tick" cx="50" cy="50" r="41" />
    </svg>

    <div class="completion-water-gauge__copy">
      <span
        class="completion-water-gauge__value"
        data-testid="target-progress-value"
      >{{ displayValue }}<small v-if="hasValue">%</small></span>
      <span class="completion-water-gauge__label">{{ safeLabel }}</span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'CompletionWaterGauge'
};
</script>

<script setup>
import { computed, getCurrentInstance } from 'vue';

const DEFAULT_LABEL = '目标完成率';
const TONE_COLORS = {
  coral: '#ff827f',
  gold: '#f1c96b',
  cyan: '#45d7e7',
  green: '#6fdb95',
  unknown: '#3d6b9f'
};

const props = defineProps({
  value: { type: Number, default: null },
  label: { type: String, default: '目标完成率' }
});

// 每个组件实例独立生成 defs id，避免同屏多个 SVG 相互引用 clip/渐变。
const instanceId = getCurrentInstance().uid;
const gradientId = name => `completion-water-gauge-${instanceId}-${name}`;
const clipId = `completion-water-gauge-${instanceId}-clip`;

const rawValue = computed(() => (Number.isFinite(props.value) ? props.value : null));
const hasValue = computed(() => rawValue.value !== null);
const level = computed(() => {
  if (!hasValue.value) return null;
  return Math.min(100, Math.max(0, rawValue.value));
});
const levelAttribute = computed(() => (level.value === null ? 'unknown' : String(level.value)));
const tone = computed(() => {
  if (!hasValue.value) return 'unknown';
  if (rawValue.value < 60) return 'coral';
  if (rawValue.value < 80) return 'gold';
  if (rawValue.value < 100) return 'cyan';
  return 'green';
});
const toneColor = computed(() => TONE_COLORS[tone.value]);
const safeLabel = computed(() => {
  const label = props.label.trim();
  return label || DEFAULT_LABEL;
});
const displayValue = computed(() => (hasValue.value ? rawValue.value.toFixed(2) : '—'));
const ariaLabel = computed(() => (
  hasValue.value ? `${safeLabel.value} ${displayValue.value}%` : `${safeLabel.value} 暂无数据`
));

// 44px 圆形内容区的底部/顶部约对应 0/100%，超额仍只把水面封顶。
const levelOffset = computed(() => 94 - ((level.value ?? 0) / 100) * 88);
const wavePathFront = 'M-12,0 Q0,-3 12,0 T36,0 T60,0 T84,0 T108,0 T132,0 T156,0 V112 H-12 Z';
const wavePathBack = 'M-12,0 Q0,3 12,0 T36,0 T60,0 T84,0 T108,0 T132,0 T156,0 V112 H-12 Z';
</script>

<style scoped>
.completion-water-gauge {
  position: relative;
  display: inline-block;
  flex: 0 1 100px;
  width: 100px;
  height: 100px;
  max-width: 100%;
  max-height: 100%;
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: 50%;
  background: #061c46;
  color: #ecfbff;
  isolation: isolate;
}

.completion-water-gauge__svg {
  display: block;
  width: 100%;
  height: 100%;
}

.completion-water-gauge__halo {
  fill: none;
  stroke: color-mix(in srgb, #45d7e7 30%, transparent);
  stroke-width: 1;
}

.completion-water-gauge__track {
  fill: #09285a;
  stroke: rgba(97, 206, 236, 0.18);
  stroke-width: 1;
}

.completion-water-gauge__level {
  transition: transform 0.7s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.completion-water-gauge__wave {
  transform-box: fill-box;
  transform-origin: center;
}

.completion-water-gauge__wave--front {
  animation: completion-water-gauge-wave 8s linear infinite;
}

.completion-water-gauge__wave--back {
  animation: completion-water-gauge-wave 12s linear infinite reverse;
}

.completion-water-gauge__edge {
  fill: none;
  stroke-width: 1.3;
  opacity: 0.75;
}

.completion-water-gauge__tick {
  fill: none;
  stroke: rgba(188, 237, 247, 0.22);
  stroke-width: 0.6;
  stroke-dasharray: 0.7 4.5;
}

.completion-water-gauge__copy {
  position: absolute;
  inset: 20%;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border-radius: 50%;
  background: rgba(3, 18, 48, 0.2);
  box-shadow: none;
  pointer-events: none;
  text-align: center;
}

.completion-water-gauge__value {
  color: #f2fdff;
  font-size: 17px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
  text-shadow: 0 1px 3px #052446, 0 0 7px #052446;
  white-space: nowrap;
}

.completion-water-gauge__value small {
  margin-left: 1px;
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0;
}

.completion-water-gauge__label {
  max-width: 100%;
  overflow: hidden;
  color: rgba(205, 239, 247, 0.78);
  font-size: 8px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@keyframes completion-water-gauge-wave {
  from { transform: translateX(0); }
  to { transform: translateX(-48px); }
}

@media (prefers-reduced-motion: reduce) {
  .completion-water-gauge__level {
    transition: none;
  }

  .completion-water-gauge__wave {
    animation: none;
  }
}
</style>
