<template>
  <div
    ref="host"
    class="bp-adaptive-row-actions"
    data-bp-row-actions-host
    :data-mode="mode"
    :data-measured="measured ? 'true' : 'false'"
  >
    <div
      class="bp-adaptive-row-actions__group bp-adaptive-row-actions__visible"
      data-bp-row-actions-visible
    >
      <slot name="primary" />
      <slot v-if="mode === 'expanded'" name="expanded" />
      <slot v-else name="compact" />
    </div>

    <div
      ref="probe"
      class="bp-adaptive-row-actions__group bp-adaptive-row-actions__probe"
      data-bp-row-actions-probe
      aria-hidden="true"
      inert
    >
      <slot name="primary" />
      <slot name="expanded" />
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';

const host = ref(null);
const probe = ref(null);
const mode = ref('compact');
const measured = ref(typeof ResizeObserver !== 'function');
let observer;

function measure() {
  const availableWidth = host.value?.clientWidth;
  const requiredWidth = probe.value?.scrollWidth;

  // 尺寸未知时保持“主操作 + 更多”，避免把操作误判为可直出。
  mode.value = Number.isFinite(availableWidth)
    && Number.isFinite(requiredWidth)
    && availableWidth > 0
    && requiredWidth > 0
    && requiredWidth <= availableWidth
    ? 'expanded'
    : 'compact';
  measured.value = true;
}

onMounted(() => {
  if (typeof ResizeObserver !== 'function') return;
  observer = new ResizeObserver(measure);
  if (host.value) observer.observe(host.value);
  if (probe.value) observer.observe(probe.value);
  // mounted 钩子内同步完成首次测量，浏览器首次绘制即为最终形态，避免先闪现紧凑态。
  measure();
});

onBeforeUnmount(() => observer?.disconnect());
</script>

<style scoped>
.bp-adaptive-row-actions {
  display: block;
  min-height: 32px;
  min-width: 0;
  position: relative;
  width: 100%;
}

.bp-adaptive-row-actions__group {
  align-items: center;
  display: inline-flex;
  gap: var(--space-2, 8px);
  min-height: 32px;
  white-space: nowrap;
  width: max-content;
}

.bp-adaptive-row-actions__visible {
  max-width: 100%;
}

.bp-adaptive-row-actions__probe {
  inset-block-start: 0;
  inset-inline-start: 0;
  pointer-events: none;
  position: absolute;
  visibility: hidden;
}

.bp-adaptive-row-actions__group :deep(.el-button + .el-button) {
  margin-inline-start: 0;
}
</style>
