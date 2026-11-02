<template>
  <section class="runtime-config-thumbnail" :data-testid="testId" :data-template="template" aria-label="大屏页面缩略图">
    <header><h2>页面缩略图</h2><p>按大屏原始布局等比缩小，点击组件进行配置。</p></header>
    <div class="runtime-config-thumbnail__preview">
      <ScaledRuntimeFrame :width="canvasWidth" :height="canvasHeight" @ready="frameReady" @layout="frameLayout">
        <div data-thumbnail-runtime-content inert :style="contentStyle">
          <component :is="runtimeComponent" :model="runtimeModel" :source-presentation="presentation"
            :draft-overview="presentation?.displaySchemaVersion === 1" :demo="false" :loading="false" error=""
            :show-configure="false" :performance-enabled="false" city-code="610100" />
        </div>
      </ScaledRuntimeFrame>
      <div class="runtime-config-thumbnail__hit-layer" aria-label="画面组件选择">
        <button v-for="target in measuredTargets" :key="target.id" type="button"
          class="runtime-config-thumbnail__hit" :class="{ 'is-active': selectedKey === target.entry.key }" :style="target.style"
          :data-config-key="target.occurrence === 0 ? target.entry.key : undefined"
          :data-linked-config-key="target.occurrence > 0 ? target.entry.key : undefined"
          :data-testid="target.entry.slot && target.occurrence === 0 ? `slot-${target.entry.slot}` : undefined"
          :aria-label="label(target.entry)" :title="label(target.entry)" :aria-pressed="selectedKey === target.entry.key" :disabled="disabled"
          @click="select(target.entry)" @keydown.enter.prevent="select(target.entry)" @keydown.space.prevent="select(target.entry)">
          <span class="runtime-config-thumbnail__accessible-label" :data-full-title="label(target.entry)">{{ label(target.entry) }}</span>
          <span v-if="selectedKey === target.entry.key" class="runtime-config-thumbnail__selected">已选中</span>
        </button>
      </div>
    </div>
    <nav v-if="unplacedEntries.length" class="runtime-config-thumbnail__additional" aria-label="其他配置入口">
      <button v-for="entry in unplacedEntries" :key="entry.key" type="button" :data-config-key="entry.key"
        :data-testid="entry.slot ? `slot-${entry.slot}` : undefined" :class="{ 'is-active': selectedKey === entry.key }"
        :aria-pressed="selectedKey === entry.key" :disabled="disabled"
        @click="select(entry)" @keydown.enter.prevent="select(entry)" @keydown.space.prevent="select(entry)">
        <span :data-full-title="label(entry)">{{ label(entry) }}</span><span v-if="selectedKey === entry.key"> · 已选中</span>
      </button>
    </nav>
    <footer><span>{{ footerText }}</span><span>当前选中：{{ currentLabel || '—' }}</span></footer>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import ScaledRuntimeFrame from './ScaledRuntimeFrame.vue';
import PanoramaDashboard from './PanoramaDashboard.vue';
import CorporateDashboard from './CorporateDashboard.vue';
import RetailDashboard from './RetailDashboard.vue';
import CityPanorama from './CityPanorama.vue';
import BranchOperatingOverview from './BranchOperatingOverview.vue';
import { buildRuntimeThumbnailModel, runtimeThumbnailTargets } from './runtimeThumbnailModel';

const props = defineProps({
  entries: { type: Array, default: () => [] }, template: { type: String, default: 'branch-overview-v1' },
  screenTitle: { type: String, default: '经营大屏' }, selectedKey: { type: String, default: '' }, selectedLabel: { type: String, default: '' },
  disabled: { type: Boolean, default: false }, presentation: { type: Object, default: null }, context: { type: String, default: 'screen' },
  canvasWidth: { type: Number, default: 1920 }, canvasHeight: { type: Number, default: 1080 },
  footerText: { type: String, default: '标题实时展示 · 数据仅占位' }, testId: { type: String, default: 'runtime-config-thumbnail' }
});
const emit = defineEmits(['select']);
const runtimeComponent = computed(() => props.context === 'city' ? CityPanorama : props.context === 'branch' ? BranchOperatingOverview
  : props.template === 'corporate-overview-v1' ? CorporateDashboard : props.template === 'retail-overview-v1' ? RetailDashboard : PanoramaDashboard);
const runtimeModel = computed(() => buildRuntimeThumbnailModel(props));
const targets = computed(() => runtimeThumbnailTargets(props));
const measuredTargets = ref([]);
const contentFit = ref(1);
const contentStyle = computed(() => ({ width: `${props.canvasWidth}px`, minHeight: `${props.canvasHeight}px`, transform: `scale(${contentFit.value})`, transformOrigin: 'top left' }));
const placedKeys = computed(() => new Set(measuredTargets.value.map(target => target.entry.key)));
const unplacedEntries = computed(() => props.entries.filter(entry => !placedKeys.value.has(entry.key)));
const currentLabel = computed(() => props.selectedLabel || label(props.entries.find(entry => entry.key === props.selectedKey)));
function label(entry) { return entry?.displayLabel || entry?.visualLabel || entry?.label || entry?.key || ''; }
function select(entry) { if (!props.disabled) emit('select', entry); }
let activeDocument;
let activeRuntime;
let resizeObserver;
let mutationObserver;
let scheduledFrame;
let stopped = false;

/** 从实际运行DOM读取位置；点击层绝不再维护第二份栅格布局。 */
function measure() {
  scheduledFrame = undefined;
  if (stopped || !activeDocument) return;
  const content = activeDocument.querySelector('[data-thumbnail-runtime-content]');
  if (!content) return;
  const runtime = content.firstElementChild;
  if (runtime && runtime !== activeRuntime) {
    if (activeRuntime) resizeObserver?.unobserve(activeRuntime);
    activeRuntime = runtime;
    runtime.style.overflow = 'visible';
    resizeObserver?.observe(runtime);
  }
  // 原运行页可能比设计视口更高，将全部内容一起缩小，避免遗漏最底部组件。
  const fullHeight = Math.max(props.canvasHeight, content.scrollHeight, runtime?.scrollHeight || 0);
  const fullWidth = Math.max(props.canvasWidth, content.scrollWidth);
  const fit = Math.min(1, props.canvasWidth / fullWidth, props.canvasHeight / fullHeight);
  if (Math.abs(contentFit.value - fit) > 0.00001) { contentFit.value = fit; nextTick(scheduleMeasure); return; }
  const occurrences = new Map();
  const next = [];
  for (const target of targets.value) {
    const entry = props.entries.find(item => item.key === target.entryKey);
    if (!entry || !target.selector) continue;
    for (const element of activeDocument.querySelectorAll(target.selector)) {
      const rect = element.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) continue;
      const occurrence = occurrences.get(entry.key) || 0;
      occurrences.set(entry.key, occurrence + 1);
      next.push({ id: `${entry.key}-${occurrence}`, entry, occurrence, style: {
        left: `${rect.left / props.canvasWidth * 100}%`, top: `${rect.top / props.canvasHeight * 100}%`,
        width: `${rect.width / props.canvasWidth * 100}%`, height: `${rect.height / props.canvasHeight * 100}%`
      } });
    }
  }
  measuredTargets.value = next;
}
function scheduleMeasure() { if (!stopped && scheduledFrame === undefined) scheduledFrame = requestAnimationFrame(measure); }
function disconnect() { resizeObserver?.disconnect(); mutationObserver?.disconnect(); activeRuntime = null; }
function frameReady({ document: doc }) {
  disconnect(); activeDocument = doc;
  if (!doc) return;
  doc.documentElement.style.overflow = 'hidden'; doc.body.style.overflow = 'hidden';
  doc.body.style.minWidth = '0'; doc.body.style.width = `${props.canvasWidth}px`;
  doc.body.style.background = '#07102c';
  resizeObserver = new ResizeObserver(scheduleMeasure); resizeObserver.observe(doc.body);
  const runtime = doc.querySelector('[data-thumbnail-runtime-content] > *');
  if (runtime) {
    // 展开运行页外壳的滚动裁剪；保留其固定视口尺寸及所有内部布局，再整体缩小完整内容。
    runtime.style.overflow = 'visible';
    resizeObserver.observe(runtime);
  }
  mutationObserver = new MutationObserver(scheduleMeasure); mutationObserver.observe(doc.body, { childList: true, subtree: true, characterData: true });
  scheduleMeasure();
}
function frameLayout({ document: doc }) { if (doc === activeDocument) scheduleMeasure(); }
watch(() => [props.entries, props.presentation, props.context, props.template, props.canvasWidth, props.canvasHeight], () => nextTick(scheduleMeasure), { deep: true });
onBeforeUnmount(() => { stopped = true; disconnect(); if (scheduledFrame !== undefined) cancelAnimationFrame(scheduledFrame); activeDocument = null; });
</script>

<style scoped>
.runtime-config-thumbnail { min-width:0; padding:20px; border:1px solid #e1e9f4; border-radius:6px; background:#fff; }
.runtime-config-thumbnail header h2 { margin:0; color:#172339; font-size:21px; line-height:28px; }
.runtime-config-thumbnail header p { margin:8px 0 20px; color:#7c8ba4; font-size:14px; }
.runtime-config-thumbnail__preview { position:relative; background:#07102c; }
.runtime-config-thumbnail__preview :deep(iframe) { pointer-events:none; }
.runtime-config-thumbnail__hit-layer { position:absolute; inset:0; pointer-events:none; }
.runtime-config-thumbnail__hit { position:absolute; pointer-events:auto; margin:0; padding:0; border:2px solid transparent; background:transparent; cursor:pointer; }
.runtime-config-thumbnail__hit:hover,.runtime-config-thumbnail__hit:focus-visible,.runtime-config-thumbnail__hit.is-active { border-color:#00dfff; box-shadow:0 0 7px #00c8f3; outline:none; }
.runtime-config-thumbnail__accessible-label { position:absolute; width:1px; height:1px; overflow:hidden; clip-path:inset(50%); white-space:nowrap; }
.runtime-config-thumbnail__selected { position:absolute; right:0; bottom:0; background:#0757ee; color:white; font-size:10px; line-height:15px; padding:0 3px; }
.runtime-config-thumbnail__additional { display:flex; flex-wrap:wrap; gap:6px; margin-top:12px; }
.runtime-config-thumbnail__additional button { border:1px solid #dbe4f0; border-radius:4px; padding:5px 8px; color:#40516d; background:#fff; font:inherit; font-size:12px; }
.runtime-config-thumbnail__additional .is-active { color:#2d63dc; border-color:#2d63dc; background:#edf3ff; }
.runtime-config-thumbnail footer { display:flex; justify-content:space-between; flex-wrap:wrap; gap:8px; margin-top:14px; font-size:12px; color:#8190a7; }
.runtime-config-thumbnail footer span:last-child { color:#2d63dc; }
@media(max-width:600px) { .runtime-config-thumbnail { padding:14px; } }
</style>
