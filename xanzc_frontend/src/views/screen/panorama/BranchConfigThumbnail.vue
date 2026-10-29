<template>
  <section class="branch-config-thumbnail" data-testid="branch-config-thumbnail" aria-label="分行经营总览缩略图">
    <header><h2>页面缩略图</h2><p>点击画面中的组件进行配置。</p></header>
    <div class="branch-config-thumbnail__screen" aria-label="分行经营总览页面缩略图">
      <h3 class="branch-config-thumbnail__screen-title">{{ screenTitle }}</h3>
      <div class="branch-config-thumbnail__top-row">
        <BranchThumbnailTile v-for="entry in overviewEntries" :key="entry.key" :entry="visualEntry(entry)" variant="overview" :selected="selectedKey === entry.key" :disabled="disabled" @select="select(entry)" />
      </div>
      <div class="branch-config-thumbnail__business" data-layout="2-2-1">
        <section v-for="group in businessGroups" :key="group.key" class="branch-config-thumbnail__group" :class="`branch-config-thumbnail__group--${group.key}`" :data-columns="group.key === 'revenue' ? '1' : '2'">
          <h4>{{ group.label }}</h4>
          <div class="branch-config-thumbnail__group-grid">
            <BranchThumbnailTile v-for="entry in group.entries" :key="entry.key" :entry="visualEntry(entry)" :selected="selectedKey === entry.key" :disabled="disabled" @select="select(entry)" />
          </div>
        </section>
      </div>
      <div class="branch-config-thumbnail__analysis" :class="{ 'is-pair': analysisEntries.length === 2 }">
        <BranchThumbnailTile v-for="entry in analysisEntries" :key="entry.key" :entry="visualEntry(entry)" variant="analysis" :selected="selectedKey === entry.key" :disabled="disabled" @select="select(entry)" />
      </div>
      <div v-if="extraEntries.length" class="branch-config-thumbnail__extras">
        <BranchThumbnailTile v-for="entry in extraEntries" :key="entry.key" :entry="visualEntry(entry)" :selected="selectedKey === entry.key" :disabled="disabled" @select="select(entry)" />
      </div>
    </div>
    <footer class="branch-config-thumbnail__caption"><span>{{ footerText }}</span><span>当前选中：{{ currentLabel || '—' }}</span></footer>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import BranchThumbnailTile from './BranchThumbnailTile.vue';
const props = defineProps({
  entries: { type: Array, default: () => [] }, selectedKey: { type: String, default: '' },
  screenTitle: { type: String, default: '分行经营总览' }, selectedLabel: { type: String, default: '' },
  footerText: { type: String, default: '标题实时展示 · 数据仅占位' }, disabled: { type: Boolean, default: false }
});
const emit = defineEmits(['select']);
const overviewKeys = ['overview-deposit', 'overview-deposit-composition', 'overview-loan', 'overview-loan-composition', 'overview-settlementDeposit'];
const analysisKeys = ['business-growth', 'institution-map', 'institution-ranking'];
const overviewEntries = computed(() => props.entries.some(entry=>entry.layoutGroup)
  ? props.entries.filter(entry=>entry.layoutGroup==='OVERVIEW') : overviewKeys.map(key => props.entries.find(item => item.key === key)).filter(Boolean));
const businessGroups = computed(() => [
  { key: 'retail', label: '零售业务', entries: props.entries.filter(item => item.layoutGroup==='RETAIL' || item.key.startsWith('business-retail-')) },
  { key: 'corp', label: '对公业务', entries: props.entries.filter(item => item.layoutGroup==='CORP' || item.key.startsWith('business-corp-')) },
  { key: 'revenue', label: '收入', entries: props.entries.filter(item => item.layoutGroup==='REVENUE' || item.key.startsWith('business-revenue-')) }
].filter(group => group.entries.length));
const analysisEntries = computed(() => props.entries.some(entry=>entry.layoutGroup)
  ? props.entries.filter(entry=>entry.layoutGroup==='ANALYSIS') : analysisKeys.map(key => props.entries.find(item => item.key === key)).filter(Boolean));
const extraEntries = computed(() => {
  const used = new Set([...overviewEntries.value, ...businessGroups.value.flatMap(group => group.entries), ...analysisEntries.value].map(entry => entry.key));
  return props.entries.filter(entry => !used.has(entry.key));
});
const currentLabel = computed(() => props.selectedLabel || props.entries.find(entry => entry.key === props.selectedKey)?.label || '');
/** 仅切换编辑目标，数据查询和草稿写入继续由配置页负责。 */
function select(entry) { if (!props.disabled) emit('select', entry); }
function visualEntry(entry) { return {...entry,kind:entry.visualKind || entry.kind}; }
</script>

<style scoped>
.branch-config-thumbnail { min-width:0; padding:20px; background:#fff; border:1px solid #e1e9f4; border-radius:6px; }
.branch-config-thumbnail header h2 { margin:0; color:#172339; font-size:21px; line-height:28px; font-weight:700; }
.branch-config-thumbnail header p { margin:8px 0 20px; color:#7c8ba4; font-size:14px; line-height:20px; }
.branch-config-thumbnail__screen { min-height:560px; padding:12px; border:1px solid #174a7b; border-radius:5px; color:#e8f7ff; background:linear-gradient(145deg,#03132b,#071c3d 58%,#03152f); box-shadow:inset 0 0 0 1px rgba(40,205,255,.1),0 8px 20px rgba(3,19,48,.25); }
.branch-config-thumbnail__screen-title { position:relative; margin:0 0 10px; padding:4px 0 10px; border-bottom:1px solid #1d557a; color:#f4faff; font-size:21px; font-weight:700; text-align:center; letter-spacing:2px; line-height:28px; text-shadow:0 0 14px #3182c355; background:linear-gradient(90deg,transparent,#17407145,transparent); }
.branch-config-thumbnail__screen-title::after { content:''; display:block; position:absolute; bottom:-2px; left:calc(50% - 45px); width:90px; height:3px; background:#269cdd; box-shadow:0 0 12px #1ecafa66; }
.branch-config-thumbnail__top-row { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:7px; }
.branch-config-thumbnail__business { display:grid; grid-template-columns:2fr 2fr 1.25fr; gap:8px; margin-top:10px; }
.branch-config-thumbnail__group--revenue :deep(.branch-thumbnail-tile__header) { gap:5px; }
.branch-config-thumbnail__group--revenue :deep(.branch-thumbnail-tile__icon-base) { width:24px; height:24px; }
.branch-config-thumbnail__group { min-width:0; padding:7px; border:1px solid #20456e; border-radius:4px; background:linear-gradient(150deg,#0d254c,#071a37); }
.branch-config-thumbnail__group h4 { margin:-7px -7px 7px; padding:7px 8px; border-bottom:1px solid #284d7255; color:#dfedff; font-size:13px; font-weight:600; line-height:17px; }
.branch-config-thumbnail__group-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:7px; }
.branch-config-thumbnail__group--revenue .branch-config-thumbnail__group-grid { grid-template-columns:1fr; }
.branch-config-thumbnail__analysis { display:grid; grid-template-columns:1fr 1.3fr 1fr; gap:8px; margin-top:10px; }
.branch-config-thumbnail__analysis.is-pair { grid-template-columns:1.35fr 1fr; }
.branch-config-thumbnail__extras { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:8px; margin-top:10px; }
.branch-config-thumbnail__caption { display:flex; justify-content:space-between; gap:12px; flex-wrap:wrap; margin-top:16px; color:#8190a7; font-size:12px; line-height:18px; }
.branch-config-thumbnail__caption span:last-child { color:#2d63dc; }
@media(max-width:600px) {
  .branch-config-thumbnail { padding:14px; }
  .branch-config-thumbnail__screen { padding:8px; }
  .branch-config-thumbnail__screen-title { font-size:17px; letter-spacing:0; }
  .branch-config-thumbnail__top-row { grid-template-columns:repeat(2,minmax(0,1fr)); }
  .branch-config-thumbnail__business { grid-template-columns:1fr; }
  .branch-config-thumbnail__group--revenue .branch-config-thumbnail__group-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }
  .branch-config-thumbnail__analysis, .branch-config-thumbnail__extras { grid-template-columns:1fr; }
}
</style>
