<template>
  <section class="operating-config-thumbnail" data-testid="operating-config-thumbnail" aria-label="经营大屏页面缩略图">
    <header class="operating-config-thumbnail__header">
      <h2>页面缩略图</h2><p>点击画面中的组件进行配置。</p>
    </header>
    <div class="operating-config-thumbnail__screen" :class="`is-${model.mode.toLowerCase()}`" :data-template="template" :data-mode="model.mode">
      <h3>{{ screenTitle }}</h3>
      <div v-for="region in model.regions" :key="region.key" class="operating-config-thumbnail__region" :class="`is-${region.key}`" :data-region="region.key">
        <h4 v-if="region.key === 'left'">{{ template === 'corporate-overview-v1' ? '对公存款经营' : template === 'retail-overview-v1' ? '储蓄核心指标' : '存款核心指标' }}</h4>
        <div class="operating-config-thumbnail__tiles" :style="{ '--tile-count': Math.max(1, region.items.length) }">
          <template v-for="entry in region.items" :key="entry.displayKey || entry.key">
            <h4 v-if="entry.sourceKey === 'retailLoan' && entry.occurrence > 0" class="operating-config-thumbnail__shared-heading">信贷规模与质量</h4>
            <BranchThumbnailTile :entry="visualEntry(entry)" :variant="variantFor(entry,region)" :data-config-key="entry.dataConfigKey" :data-linked-config-key="entry.dataLinkedConfigKey"
              :data-testid="entry.dataConfigKey && entry.originalEntry?.slot ? `slot-${entry.originalEntry.slot}` : undefined"
              :selected="selectedKey === entry.sourceKey || selectedKey === entry.key" :disabled="disabled" @select="select(entry)" />
          </template>
        </div>
      </div>
      <div v-if="unplacedEntries.length" class="operating-config-thumbnail__extras" data-region="extras">
        <BranchThumbnailTile v-for="entry in unplacedEntries" :key="entry.displayKey || entry.key" :entry="visualEntry(entry)" :data-config-key="entry.dataConfigKey || entry.key" :data-linked-config-key="entry.dataLinkedConfigKey" :data-testid="entry.originalEntry?.slot ? `slot-${entry.originalEntry.slot}` : undefined" :selected="selectedKey === entry.key" :disabled="disabled" @select="select(entry)" />
      </div>
    </div>
    <footer><span>{{ footerText }}</span><span>当前选中：{{ currentLabel || '—' }}</span></footer>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import BranchThumbnailTile from './BranchThumbnailTile.vue';
import { buildOperatingThumbnailModel } from './operatingThumbnailModel';

const props = defineProps({
  entries: { type: Array, default: () => [] }, template: { type: String, default: '' }, screenTitle: { type: String, default: '经营大屏' },
  selectedKey: { type: String, default: '' }, disabled: { type: Boolean, default: false }, viewLevel: { type: String, default: '' },
  presentation: { type: Object, default: null }, footerText: { type: String, default: '标题实时展示 · 数据仅占位' }
});
const emit = defineEmits(['select']);
const model = computed(() => buildOperatingThumbnailModel(props));
const placedKeys = computed(() => new Set(model.value.regions.flatMap(region => region.items.map(item => item.sourceKey || item.key))));
const unplacedEntries = computed(() => model.value.entries.filter(entry => !placedKeys.value.has(entry.sourceKey || entry.key)));
const currentLabel = computed(() => props.entries.find(entry => entry.key === props.selectedKey)?.displayLabel
  || props.entries.find(entry => entry.key === props.selectedKey)?.label || '');
function select(displayEntry) { if (!props.disabled) emit('select', displayEntry.originalEntry || displayEntry); }
function visualEntry(entry) { return entry.visualKind && entry.kind !== entry.visualKind ? { ...entry, kind: entry.visualKind } : entry; }
function variantFor(entry, region) {
  if (['BUSINESS_GROWTH', 'INSTITUTION_MAP', 'INSTITUTION_RANKING','LIST','PROGRESS'].includes(entry.visualKind)) return 'analysis';
  if (region.key === 'top' || region.key === 'overview') return 'overview';
  return 'metric';
}
</script>

<style scoped>
.operating-config-thumbnail { min-width:0; padding:20px; background:#fff; border:1px solid #e1e9f4; border-radius:6px; }
.operating-config-thumbnail__header h2 { margin:0; color:#172339; font-size:21px; line-height:28px; }
.operating-config-thumbnail__header p { margin:8px 0 20px; color:#7c8ba4; font-size:14px; }
.operating-config-thumbnail__screen { min-height:560px; padding:12px; overflow:hidden; border:1px solid #174a7b; border-radius:5px; color:#e8f7ff; background:linear-gradient(145deg,#03132b,#071c3d 58%,#03152f); }
.operating-config-thumbnail__screen > h3 { margin:0 0 12px; padding:8px 0 15px; border-bottom:1px solid #1d557a; color:#f4faff; font-size:18px; text-align:center; letter-spacing:2px; }
.operating-config-thumbnail__region { min-width:0; margin-top:9px; }
.operating-config-thumbnail__region h4 { margin:0 0 6px; color:#c7e9fc; font-size:13px; }
.operating-config-thumbnail__tiles { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:7px; }
.operating-config-thumbnail__region.is-top .operating-config-thumbnail__tiles { grid-template-columns:repeat(var(--tile-count),minmax(0,1fr)); }
.operating-config-thumbnail__region.is-center .operating-config-thumbnail__tiles, .operating-config-thumbnail__region.is-right .operating-config-thumbnail__tiles, .operating-config-thumbnail__region.is-bottom .operating-config-thumbnail__tiles { grid-template-columns:repeat(2,minmax(0,1fr)); }
.operating-config-thumbnail__region.is-top + .operating-config-thumbnail__region { margin-top:10px; }
.operating-config-thumbnail__extras { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:7px; margin-top:9px; }
.operating-config-thumbnail__screen { display:grid; grid-template-columns:1fr 1.3fr 1fr; grid-template-areas:'heading heading heading' 'top top top' 'summary summary summary' 'left center right' 'bottom bottom bottom'; gap:8px; align-items:start; }
.operating-config-thumbnail__screen > h3 { grid-area:heading; }
.operating-config-thumbnail__screen > .is-top { grid-area:top; }
.operating-config-thumbnail__screen > .is-summary { grid-area:summary; }
.operating-config-thumbnail__screen > .is-summary .operating-config-thumbnail__tiles { grid-template-columns:repeat(4,minmax(0,1fr)); }
.operating-config-thumbnail__shared-heading { grid-column:1/-1; }
.operating-config-thumbnail__screen > .is-left { grid-area:left; }
.operating-config-thumbnail__screen > .is-center { grid-area:center; }
.operating-config-thumbnail__screen > .is-right { grid-area:right; }
.operating-config-thumbnail__screen > .is-bottom { grid-area:bottom; }
.operating-config-thumbnail__extras { grid-column:1/-1; }
.operating-config-thumbnail__screen.is-legacy .is-left .operating-config-thumbnail__tiles { grid-template-columns:repeat(2,minmax(0,1fr)); }
.operating-config-thumbnail__screen .is-center .operating-config-thumbnail__tiles, .operating-config-thumbnail__screen .is-right .operating-config-thumbnail__tiles { grid-template-columns:1fr; }
.operating-config-thumbnail__screen .is-left :deep(.branch-thumbnail-tile.is-analysis) { grid-column:1/-1; height:170px; min-height:170px; }
.operating-config-thumbnail__screen .is-center :deep(.branch-thumbnail-tile.is-analysis) { height:230px; min-height:230px; }
.operating-config-thumbnail__screen .is-right :deep(.branch-thumbnail-tile.is-analysis) { height:230px; min-height:230px; }
.operating-config-thumbnail__screen .is-top :deep(.branch-thumbnail-tile__icon-base) { display:none; }
.operating-config-thumbnail__screen .is-top :deep(.branch-thumbnail-tile__metric-body) { padding-left:0; }
.operating-config-thumbnail__region h4 { font-size:11px; font-weight:400; color:#7c9ab9; }
.operating-config-thumbnail footer { display:flex; justify-content:space-between; gap:12px; flex-wrap:wrap; margin-top:14px; color:#8190a7; font-size:12px; }
.operating-config-thumbnail footer span:last-child { color:#2d63dc; }
@media(max-width:760px) { .operating-config-thumbnail { padding:14px; } .operating-config-thumbnail__screen { min-height:520px; padding:8px; display:block !important; } .operating-config-thumbnail__region.is-top .operating-config-thumbnail__tiles { grid-template-columns:repeat(2,minmax(0,1fr)); } .operating-config-thumbnail__extras { grid-template-columns:1fr; } }
</style>
