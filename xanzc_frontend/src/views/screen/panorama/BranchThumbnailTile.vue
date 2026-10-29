<template>
  <button type="button" class="branch-thumbnail-tile" :class="{ 'is-active': selected, 'is-analysis': analysis }"
    :data-config-key="entry.key" :data-component-id="entry.sourceComponentId || undefined"
    :aria-label="`选择${entry.label}`" :aria-pressed="selected" :disabled="disabled"
    @click="select" @keydown.enter.prevent="select" @keydown.space.prevent="select">
    <span class="branch-config-thumbnail__component-title" :data-full-title="entry.label">{{ entry.label }}</span>
    <BranchThumbnailChart v-if="chartKind" :kind="chartKind" :class="{ 'is-mini': !analysis }" />
    <div v-else class="branch-thumbnail-tile__value"><component :is="entry.kind === 'DERIVED_TOTAL' ? Coin : DataLine" aria-hidden="true" /><strong>—</strong><small>{{ unit }}</small></div>
    <small v-if="!analysis && !chartKind" class="branch-thumbnail-tile__comparison">较上期 —</small>
    <span v-if="chartKind && !analysis" class="branch-thumbnail-tile__empty">—</span>
    <span v-if="selected" class="branch-config-thumbnail__selected">已选中</span>
  </button>
</template>

<script setup>
import { computed } from 'vue';
import { Coin, DataLine } from '@element-plus/icons-vue';
import BranchThumbnailChart from './BranchThumbnailChart.vue';
const props = defineProps({ entry: { type:Object, required:true }, selected:Boolean, disabled:Boolean, analysis:Boolean });
const emit = defineEmits(['select']);
const chartKind = computed(() => {
  if (['INSTITUTION_MAP','INSTITUTION_RANKING','BUSINESS_GROWTH','COMPOSITION_TAB'].includes(props.entry.kind)) return props.entry.kind;
  if (props.entry.key.endsWith('-rate') || props.entry.component?.componentType === 'COMPLETION') return 'COMPLETION';
  return '';
});
const unit = computed(() => {
  const format = props.entry.component?.format?.displayUnit;
  const key = format && format !== 'AUTO' ? format : props.entry.component?.dataRefs?.[0]?.unit;
  return ({ YUAN:'元', TEN_THOUSAND:'万元', HUNDRED_MILLION:'亿元', PERCENT:'%', COUNT:'个', TEN_THOUSAND_COUNT:'万户' })[key] || '';
});
/** 使用原生按钮支持鼠标和键盘，禁止写入期间改变配置对象。 */
function select() { if (!props.disabled) emit('select', props.entry); }
</script>

<style scoped>
.branch-thumbnail-tile { position:relative; display:flex; flex-direction:column; align-items:stretch; justify-content:flex-start; min-width:0; min-height:82px; box-sizing:border-box; padding:8px 7px 18px; border:1px solid #245680; border-radius:4px; background:#0a2449; color:#f0f8ff; text-align:left; font:inherit; cursor:pointer; }
.branch-config-thumbnail__component-title { flex:none; color:#e8f5ff; font-size:12px; font-weight:600; line-height:17px; overflow-wrap:anywhere; }
.branch-thumbnail-tile__value { display:flex; align-items:center; gap:5px; margin:5px 0 3px; min-width:0; }
.branch-thumbnail-tile__value svg { width:18px; height:18px; flex:none; color:#37cde5; }
.branch-thumbnail-tile__value strong { color:#f3f8ff; font-size:20px; line-height:21px; }
.branch-thumbnail-tile__value small { margin-left:auto; font-size:10px; color:#88adc8; }
.branch-thumbnail-tile__comparison { color:#82aac7; font-size:10px; line-height:13px; }
.branch-thumbnail-tile .branch-thumbnail-chart { height:150px; min-height:0; margin-top:10px; flex:1; }
.branch-thumbnail-tile .branch-thumbnail-chart.is-mini { height:34px; min-height:34px; max-height:34px; margin-top:4px; }
.branch-thumbnail-tile__empty { position:absolute; left:50%; bottom:27px; transform:translateX(-50%); color:#d9f4ff; font-size:12px; pointer-events:none; }
.branch-thumbnail-tile.is-analysis { min-height:190px; padding-bottom:23px; background:#081d3d; }
.branch-thumbnail-tile:hover:not(:disabled) { border-color:#38c6e6; background:#0d2b50; }
.branch-thumbnail-tile:focus-visible { outline:2px solid #9feeff; outline-offset:3px; }
.branch-thumbnail-tile.is-active { border-color:#10dcff; box-shadow:inset 0 0 0 1px #10dcff,0 0 10px #0ac7ea38; }
.branch-thumbnail-tile:disabled { opacity:.65; cursor:not-allowed; }
.branch-config-thumbnail__selected { position:absolute; right:5px; bottom:4px; padding:2px 5px; border-radius:3px; background:#0dd4f3; color:#063650; font-size:10px; line-height:13px; }
</style>
