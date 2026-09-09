<template>
  <section
    class="composition-breakdown"
    data-testid="composition-breakdown"
    aria-label="业务构成比例"
  >
    <header class="composition-breakdown__header">
      <div class="composition-breakdown__total-block">
        <span class="composition-breakdown__eyebrow">构成合计</span>
        <div class="composition-breakdown__total-line">
          <strong data-testid="composition-total">{{ breakdown.totalText }}</strong>
          <span data-testid="composition-total-unit">{{ breakdown.unitLabel }}</span>
        </div>
      </div>
      <span v-if="breakdown.baseNote" class="composition-breakdown__base-note">{{ breakdown.baseNote }}</span>
    </header>

    <div class="composition-breakdown__band-block">
      <div class="composition-breakdown__band-heading">
        <span>{{ breakdown.trackState === 'valid' ? '100%基准' : '比例带待核对' }}</span>
      </div>
      <div
        class="composition-breakdown__track"
        data-testid="composition-track"
        :data-state="breakdown.trackState"
        role="img"
        :aria-label="breakdown.trackState === 'valid' ? '100%构成比例带' : breakdown.statusMessage"
      >
        <template v-if="breakdown.trackState === 'valid'">
          <span
            v-for="(item, index) in breakdown.items"
            :key="`${item.name}-${item.index}`"
            class="composition-breakdown__segment"
            data-testid="composition-segment"
            role="img"
            tabindex="0"
            :style="{ width: `${item.share}%`, backgroundColor: segmentColor(index) }"
            :title="item.segmentTitle"
            :aria-label="item.segmentAriaLabel"
          />
        </template>
        <span v-else class="composition-breakdown__track-empty" aria-hidden="true" />
      </div>
      <div v-if="breakdown.trackState === 'valid'" class="composition-breakdown__scale" aria-hidden="true">
        <span>0%</span>
        <span data-testid="composition-scale-50">50%</span>
        <span data-testid="composition-scale-100">100%</span>
      </div>
    </div>

    <p
      v-if="breakdown.statusMessage"
      class="composition-breakdown__status"
      :data-state="breakdown.trackState"
      role="status"
    >
      {{ breakdown.statusMessage }}
    </p>

    <div
      v-if="breakdown.items.length"
      class="composition-breakdown__details"
      data-testid="composition-details"
    >
      <article
        v-for="(item, index) in breakdown.items"
        :key="`${item.name}-${item.index}`"
        class="composition-breakdown__detail"
        data-testid="composition-detail"
      >
        <span
          class="composition-breakdown__swatch"
          aria-hidden="true"
          :style="{ backgroundColor: segmentColor(index) }"
        />
        <div class="composition-breakdown__detail-copy">
          <div class="composition-breakdown__detail-heading">
            <span class="composition-breakdown__detail-name" :title="item.name">{{ item.name }}</span>
            <strong class="composition-breakdown__detail-share" :aria-label="`占比 ${formatCompositionPercent(item.share)}`">{{ formatCompositionPercent(item.share) }}</strong>
          </div>
          <div class="composition-breakdown__detail-value">
            <template v-if="breakdown.shareMode === 'percent'">
              <span>来源比例</span>
            </template>
            <template v-else>
              <span>{{ item.valueText }}</span>
              <small>{{ item.unit || '单位缺失' }}</small>
            </template>
          </div>
        </div>
      </article>
    </div>
    <p v-else class="composition-breakdown__empty">暂无构成数据</p>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import { buildCompositionBreakdown, COMPOSITION_COLORS, formatCompositionPercent } from './compositionBreakdown.js';

const props = defineProps({
  items: { type: Array, default: () => [] }
});

const breakdown = computed(() => buildCompositionBreakdown(props.items));

function segmentColor(index) {
  return COMPOSITION_COLORS[index % COMPOSITION_COLORS.length];
}
</script>

<style scoped>
.composition-breakdown {
  --composition-text: #ecf4ff;
  --composition-text-dim: #8fa8cf;
  --composition-line: rgba(125, 168, 233, .24);
  --composition-track: rgba(6, 19, 49, .92);
  width: 100%;
  min-width: 0;
  min-height: 0;
  height: 100%;
  box-sizing: border-box;
  padding: 10px 12px 12px;
  overflow: hidden;
  color: var(--composition-text);
  background: transparent;
  font-family: "DIN Alternate", "SFMono-Regular", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.composition-breakdown *,
.composition-breakdown *::before,
.composition-breakdown *::after {
  box-sizing: border-box;
}

.composition-breakdown__header,
.composition-breakdown__total-line,
.composition-breakdown__band-heading,
.composition-breakdown__scale,
.composition-breakdown__detail-heading,
.composition-breakdown__detail-value {
  display: flex;
  align-items: center;
}

.composition-breakdown__header {
  min-width: 0;
  justify-content: space-between;
  gap: 12px;
}

.composition-breakdown__total-block,
.composition-breakdown__detail-copy {
  min-width: 0;
}

.composition-breakdown__eyebrow {
  display: block;
  color: var(--composition-text-dim);
  font-size: 11px;
  letter-spacing: .13em;
  line-height: 1.2;
}

.composition-breakdown__total-line {
  gap: 8px;
  margin-top: 5px;
  min-width: 0;
}

.composition-breakdown__total-line strong {
  min-width: 0;
  overflow: hidden;
  color: #f5f9ff;
  font-size: clamp(22px, 4vw, 26px);
  font-variant-numeric: tabular-nums;
  line-height: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composition-breakdown__total-line > span {
  flex: 0 0 auto;
  color: #a9c6ed;
  font-size: 12px;
  white-space: nowrap;
}

.composition-breakdown__base-note {
  max-width: 46%;
  color: var(--composition-text-dim);
  font-size: 11px;
  line-height: 1.35;
  text-align: right;
}

.composition-breakdown__band-block {
  margin-top: 12px;
}

.composition-breakdown__band-heading {
  gap: 8px;
  color: #cce6ff;
  font-size: 11px;
  line-height: 1.2;
}

.composition-breakdown__band-heading > span:first-child {
  font-weight: 700;
  letter-spacing: .08em;
}

.composition-breakdown__track {
  display: flex;
  width: 100%;
  height: 24px;
  margin-top: 8px;
  overflow: hidden;
  border: 1px solid rgba(122, 176, 245, .32);
  border-radius: 5px;
  background: var(--composition-track);
  box-shadow: inset 0 0 0 1px rgba(3, 11, 28, .72);
}

.composition-breakdown__segment {
  display: block;
  flex: 0 0 auto;
  height: 100%;
  outline: none;
  cursor: pointer;
}

.composition-breakdown__segment + .composition-breakdown__segment {
  box-shadow: inset 2px 0 0 rgba(2, 14, 37, .62);
}

.composition-breakdown__segment:hover,
.composition-breakdown__segment:focus-visible {
  filter: brightness(1.16);
  outline: 2px solid rgba(213, 245, 255, .84);
  outline-offset: -2px;
}

.composition-breakdown__track-empty {
  display: block;
  width: 100%;
  height: 100%;
  background: repeating-linear-gradient(135deg, rgba(126, 157, 205, .08) 0 7px, transparent 7px 14px);
}

.composition-breakdown__scale {
  justify-content: space-between;
  margin-top: 5px;
  color: #7590b8;
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.composition-breakdown__status {
  min-height: 16px;
  margin: 10px 0 9px;
  color: #82dbd7;
  font-size: 11px;
  line-height: 1.45;
}

.composition-breakdown__status[data-state="unavailable"] {
  color: #e5c77c;
}

.composition-breakdown__details {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 8px;
  min-width: 0;
  max-height: 124px;
  padding-right: 2px;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-color: rgba(116, 163, 233, .45) transparent;
  scrollbar-width: thin;
}

.composition-breakdown__detail {
  display: grid;
  grid-template-columns: 7px minmax(0, 1fr);
  gap: 9px;
  min-width: 0;
  padding: 8px 9px;
  border: 1px solid var(--composition-line);
  border-radius: 6px;
  background: rgba(14, 38, 87, .48);
}

.composition-breakdown__swatch {
  display: block;
  width: 7px;
  height: 7px;
  margin-top: 3px;
  border-radius: 50%;
  box-shadow: 0 0 8px rgba(92, 224, 239, .28);
}

.composition-breakdown__detail-heading {
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.composition-breakdown__detail-name {
  min-width: 0;
  overflow: hidden;
  color: #dbeaff;
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composition-breakdown__detail-share {
  flex: 0 0 auto;
  color: #f4fbff;
  font-size: 24px;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  white-space: nowrap;
}

.composition-breakdown__detail-value {
  gap: 4px;
  margin-top: 4px;
  color: #a9c6ed;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.composition-breakdown__detail-value > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composition-breakdown__detail-value small {
  flex: 0 0 auto;
  color: #718cb6;
  font-size: 10px;
}

.composition-breakdown__empty {
  margin: 16px 0 0;
  color: var(--composition-text-dim);
  font-size: 12px;
}

@media (max-width: 360px) {
  .composition-breakdown {
    padding: 8px 10px;
  }

  .composition-breakdown__header {
    align-items: flex-start;
    flex-direction: column;
    gap: 5px;
  }

  .composition-breakdown__base-note {
    max-width: 100%;
    text-align: left;
  }

  .composition-breakdown__band-block {
    margin-top: 10px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .composition-breakdown *,
  .composition-breakdown *::before,
  .composition-breakdown *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
