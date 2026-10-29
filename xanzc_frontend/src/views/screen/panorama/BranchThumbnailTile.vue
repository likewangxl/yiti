<template>
  <button type="button" class="branch-thumbnail-tile" :class="[{ 'is-active': selected, 'has-ring': hasRing }, `is-${variant}`]"
    :data-config-key="entry.key" :data-component-id="entry.sourceComponentId || undefined" :aria-label="`选择${entry.label}`"
    :aria-pressed="selected" :disabled="disabled" @click="select" @keydown.enter.prevent="select" @keydown.space.prevent="select">
    <span class="branch-thumbnail-tile__header">
      <span v-if="variant === 'overview'" class="branch-thumbnail-tile__icon-base" aria-hidden="true"><component :is="icon" /></span>
      <span class="branch-thumbnail-tile__title branch-config-thumbnail__component-title" :data-full-title="entry.label">{{ entry.label }}</span>
      <span v-if="variant === 'analysis' && !selected" class="branch-thumbnail-tile__header-mark" aria-hidden="true"></span>
    </span>
    <BranchThumbnailChart v-if="variant === 'analysis'" :kind="entry.kind" class="branch-thumbnail-tile__analysis-chart" />
    <span v-else class="branch-thumbnail-tile__metric-body">
      <span><strong>—</strong><small v-if="unit">{{ unit }}</small><span class="branch-thumbnail-tile__comparison">较上期 <b>—</b></span></span>
      <BranchThumbnailChart v-if="hasRing" :kind="ringKind" class="branch-thumbnail-tile__ring-chart" />
    </span>
    <span v-if="selected" class="branch-thumbnail-tile__selected">已选中</span>
  </button>
</template>

<script setup>
import { computed } from 'vue';
import { Coin, DataLine, DataAnalysis, Histogram, PieChart } from '@element-plus/icons-vue';
import BranchThumbnailChart from './BranchThumbnailChart.vue';
const props = defineProps({ entry:{type:Object,required:true}, selected:Boolean, disabled:Boolean, variant:{type:String,default:'metric'} });
const emit = defineEmits(['select']);
const ringKind = computed(() => props.entry.kind === 'COMPOSITION_TAB' ? 'COMPOSITION_TAB' : 'COMPLETION');
const hasRing = computed(() => props.entry.kind === 'COMPOSITION_TAB' || props.entry.kind === 'COMPLETION' || props.entry.component?.componentType === 'COMPLETION' || props.entry.key.endsWith('-rate'));
const icon = computed(() => props.entry.kind === 'COMPOSITION_TAB' ? PieChart : hasRing.value ? DataAnalysis : props.entry.key.includes('deposit') ? Coin : props.entry.key.includes('loan') ? DataLine : Histogram);
const unit = computed(() => {
  const format = props.entry.component?.format?.displayUnit;
  const key = format && format !== 'AUTO' ? format : props.entry.component?.dataRefs?.[0]?.unit;
  return ({YUAN:'元',TEN_THOUSAND:'万元',HUNDRED_MILLION:'亿元',PERCENT:'%',COUNT:'个',TEN_THOUSAND_COUNT:'万户'})[key] || '';
});
/** 鼠标和键盘共用选择入口，写入过程中保持编辑对象不变。 */
function select() { if (!props.disabled) emit('select',props.entry); }
</script>

<style scoped>
.branch-thumbnail-tile { position:relative; display:flex; flex-direction:column; align-items:stretch; min-width:0; width:100%; min-height:78px; box-sizing:border-box; padding:9px 8px; border:1px solid #1c4e7c; border-radius:5px; color:#d9ecff; background:linear-gradient(135deg,#0b2a55 0%,#081c3e 100%); box-shadow:inset 0 1px 0 #49bfff16; text-align:left; font:inherit; cursor:pointer; }
.branch-thumbnail-tile__header { display:flex; align-items:center; gap:7px; min-width:0; }
.branch-thumbnail-tile__icon-base { display:grid; flex:none; place-items:center; width:28px; height:28px; color:#41e4f3; border:1px solid #44bccc80; border-radius:6px; background:linear-gradient(135deg,#42e0f344,#1b478a44); box-shadow:inset 0 0 0 2px #041c3a80,0 0 9px #23d3ef29,2px 2px 0 #0a447b; }
.branch-thumbnail-tile__icon-base :deep(svg) { width:19px; height:19px; }
.branch-thumbnail-tile__title { min-width:0; color:#dbeeff; font-size:12px; font-weight:600; line-height:16px; overflow-wrap:anywhere; }
.branch-thumbnail-tile__metric-body { display:flex; align-items:center; justify-content:space-between; gap:3px; padding-left:35px; margin-top:2px; min-width:0; }
.is-metric .branch-thumbnail-tile__metric-body { padding-left:0; margin-top:6px; }
.is-metric .branch-thumbnail-tile__title { font-size:12px; line-height:18px; }
.is-metric .branch-thumbnail-tile__ring-chart { width:36px; height:36px; }
.branch-thumbnail-tile__metric-body strong { display:inline-block; color:#f1f8ff; font-size:22px; line-height:23px; }
.branch-thumbnail-tile__metric-body small { margin-left:5px; color:#83a8ce; font-size:10px; }
.branch-thumbnail-tile__comparison { display:block; color:#7598bf; font-size:9px; line-height:13px; white-space:nowrap; }
.branch-thumbnail-tile__comparison b { color:#62bdd7; margin-left:5px; font-weight:400; }
.branch-thumbnail-tile__ring-chart { flex:none; width:30px; height:30px; }
.has-ring .branch-thumbnail-tile__metric-body { padding-left:0; }
.branch-thumbnail-tile.is-analysis { height:250px; min-height:250px; padding:0; background:linear-gradient(150deg,#0b2349 0%,#04152e 100%); }
.is-analysis .branch-thumbnail-tile__header { padding:10px 9px; align-items:flex-start; border-bottom:1px solid #2a568052; min-height:37px; box-sizing:border-box; }
.is-analysis .branch-thumbnail-tile__title { color:#e9f5ff; font-size:13px; line-height:17px; }
.is-analysis.is-active .branch-thumbnail-tile__title { padding-right:42px; }
.branch-thumbnail-tile__header-mark { flex:none; width:20px; height:4px; margin:6px 0 0 auto; background:#2f5b82; opacity:.75; border-radius:1px; }
.branch-thumbnail-tile__analysis-chart { margin:7px 7px 8px; width:calc(100% - 14px); height:0; min-height:0; flex:1; }
.branch-thumbnail-tile:hover:not(:disabled) { border-color:#3a9cc8; background:linear-gradient(140deg,#10335c,#082442); }
.branch-thumbnail-tile:focus-visible { outline:2px solid #99efff; outline-offset:2px; }
.branch-thumbnail-tile.is-active { border-color:#12dcff; box-shadow:inset 0 0 0 1px #12dcff,inset 0 0 20px #1ddaff14,0 0 9px #00c8f340; }
.branch-thumbnail-tile__selected { position:absolute; right:5px; bottom:3px; padding:2px 5px; color:#fff; background:linear-gradient(#2686ff,#0757ee); border:1px solid #4aa9ff; border-radius:3px; font-size:10px; line-height:13px; box-shadow:0 0 7px #126efb66; }
.is-analysis .branch-thumbnail-tile__selected { bottom:auto; top:7px; }
.branch-thumbnail-tile:disabled { cursor:not-allowed; opacity:.65; }
@media(max-width:600px) { .branch-thumbnail-tile { min-height:84px; } .branch-thumbnail-tile.is-analysis { min-height:235px; } }
</style>
