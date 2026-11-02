<template>
  <div ref="rootRef" class="scaled-runtime-frame" data-testid="scaled-runtime-frame" :style="rootStyle">
    <iframe ref="iframeRef" title="运行态大屏缩略图" tabindex="-1" aria-hidden="true" :width="width" :height="height" src="about:blank" @load="onFrameLoad" />
    <Teleport v-if="frameDocument" :key="frameGeneration" :to="frameDocument.body">
      <slot />
    </Teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = defineProps({ width: { type: Number, default: 1920 }, height: { type: Number, default: 1080 } });
const emit = defineEmits(['ready', 'layout']);
const rootRef = ref(null); const iframeRef = ref(null); const frameDocument = ref(null); const scale = ref(1); const hostWidth = ref(0); const frameGeneration = ref(0);
let resizeObserver; let headObserver; let frameWindow; let disposed = false; let loadGeneration = 0;
const rootStyle = computed(() => ({ '--frame-scale': String(scale.value), '--frame-ratio': `${props.width} / ${props.height}`, '--frame-width': `${props.width}px`, '--frame-height': `${props.height}px`, height: `${hostWidth.value ? hostWidth.value * props.height / props.width : 0}px` }));

async function copyHead() {
  const documentTarget = iframeRef.value?.contentDocument;
  if (!documentTarget || disposed) return;
  documentTarget.documentElement.style.margin = '0';
  if (!documentTarget.documentElement.style.overflow) documentTarget.documentElement.style.overflow = 'auto';
  documentTarget.body.style.margin = '0';
  if (!documentTarget.body.style.overflow) documentTarget.body.style.overflow = 'auto';
  documentTarget.head.querySelectorAll('[data-scaled-runtime-clone]').forEach(node => node.remove());
  const base = documentTarget.createElement('base'); base.href = document.baseURI; base.setAttribute('data-scaled-runtime-clone', 'true'); documentTarget.head.appendChild(base);
  document.head.querySelectorAll('style, link[rel="stylesheet"]').forEach(source => {
    const clone = source.cloneNode(true); clone.setAttribute('data-scaled-runtime-clone', 'true');
    if (clone.tagName === 'LINK' && source.href) {
      clone.href = source.href;
      // 生产CSS可能尚未从缓存载入；载入后重新测量，不把初次ready当作最终布局。
      clone.addEventListener('load', () => { if (!disposed) nextTick(updateLayout); }, { once: true });
    }
    documentTarget.head.appendChild(clone);
  });
}
function updateLayout() {
  if (disposed) return;
  const width = rootRef.value?.getBoundingClientRect().width || rootRef.value?.clientWidth || 0;
  hostWidth.value = width;
  scale.value = props.width > 0 ? width / props.width : 0;
  emit('layout', { document: iframeRef.value?.contentDocument, scale: scale.value });
}
async function onFrameLoad() {
  const generation = ++loadGeneration;
  frameDocument.value = null;
  await nextTick();
  if (disposed || generation !== loadGeneration) return;
  frameWindow = iframeRef.value?.contentWindow;
  await copyHead();
  if (disposed || generation !== loadGeneration) return;
  frameDocument.value = iframeRef.value?.contentDocument || null;
  frameGeneration.value += 1;
  await nextTick();
  if (frameDocument.value?.fonts?.ready) await frameDocument.value.fonts.ready.catch(() => {});
  if (disposed || generation !== loadGeneration) return;
  emit('ready', { document: frameDocument.value, window: frameWindow });
  updateLayout();
}
watch(() => [props.width, props.height], () => { updateLayout(); nextTick(copyHead); });
onMounted(() => {
  resizeObserver = new ResizeObserver(updateLayout); resizeObserver.observe(rootRef.value); updateLayout();
  headObserver = new MutationObserver(() => { copyHead().then(updateLayout); }); headObserver.observe(document.head, { childList: true, subtree: true, characterData: true, attributes: true });
});
onBeforeUnmount(() => { disposed = true; ++loadGeneration; resizeObserver?.disconnect(); headObserver?.disconnect(); frameDocument.value = null; frameWindow = null; });
</script>

<style scoped>
.scaled-runtime-frame { position:relative; width:100%; aspect-ratio:var(--frame-ratio); overflow:hidden; background:#07102c; }
.scaled-runtime-frame > iframe { position:absolute; top:0; left:0; width:var(--frame-width, 1920px); height:var(--frame-height, 1080px); border:0; transform:scale(var(--frame-scale, 1)); transform-origin:top left; }
</style>
