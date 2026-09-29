<template>
  <section
    ref="rootRef"
    class="composition-tabs-widget"
    :class="{ 'composition-tabs-widget--compact': compact, 'composition-tabs-widget--single': rings.length === 1, 'composition-tabs-widget--no-total-caption': !showTotalCaption }"
    data-testid="composition-tabs-root"
    :data-compact="compact ? 'true' : 'false'"
    :data-ring-count="rings.length"
    tabindex="0"
    aria-label="业务结构"
  >
    <header class="composition-tabs-widget__header">
      <div>
        <h2>{{ title }}</h2>
        <p v-if="subtitle">{{ subtitle }}</p>
      </div>
    </header>

    <div class="composition-tabs-widget__rings" data-testid="composition-rings">
      <article
        v-for="ring in rings"
        :key="ring.ringKey"
        class="composition-ring-card"
        :class="{ 'is-pending': !ring.ready }"
        :data-testid="`composition-ring-${ring.ringKey}`"
        :data-state="ring.ready ? 'READY' : 'PENDING'"
      >
        <div class="composition-ring-card__heading">
          <strong>{{ ring.label }}</strong>
          <span v-if="ring.unit">{{ ring.unit }}</span>
        </div>

        <div
          class="composition-ring"
          :class="{ 'is-pending': !ring.ready }"
          :style="ringStyle(ring)"
          role="img"
          :aria-label="ringAriaLabel(ring)"
          :data-testid="`composition-ring-${ring.ringKey}-visual`"
        >
          <div class="composition-ring__center">
            <strong>{{ ring.totalText }}</strong>
            <small v-if="showTotalCaption">{{ ring.hasTotal ? '核定总量' : '核定总量待接入' }}</small>
          </div>
        </div>

        <div class="composition-ring-card__legend" :aria-label="`${ring.label}公司零售构成`">
          <button
            type="button"
            :data-testid="`business-line-${ring.ringKey}-corp`"
            @click="selectBusinessLine('CORP', ring.tabKey)"
          >
            <i class="composition-ring-card__dot composition-ring-card__dot--corp" aria-hidden="true" />
            <span>公司 {{ valueText(ring.corporate, false) }}</span>
            <strong>{{ ring.ready ? shareText(ring.corporate) : '待接入' }}</strong>
          </button>
          <button
            type="button"
            :data-testid="`business-line-${ring.ringKey}-retail`"
            @click="selectBusinessLine('RETAIL', ring.tabKey)"
          >
            <i class="composition-ring-card__dot composition-ring-card__dot--retail" aria-hidden="true" />
            <span>零售 {{ valueText(ring.retail, false) }}</span>
            <strong>{{ ring.ready ? shareText(ring.retail) : '待接入' }}</strong>
          </button>
        </div>

        <p v-if="!ring.ready" class="composition-ring-card__status" role="status">
          {{ ring.statusMessage || '占比待接入' }}
        </p>
        <p v-if="ring.other || ring.gap" class="composition-ring-card__status composition-ring-card__status--remainder">
          <span v-if="ring.other">其他 {{ valueText(ring.other) }}<template v-if="ring.otherShareText">（{{ ring.otherShareText }}）</template></span>
          <span v-if="ring.gap">缺口 {{ valueText(ring.gap) }}<template v-if="ring.gapShareText">（{{ ring.gapShareText }}）</template></span>
        </p>
      </article>
    </div>

    <p v-if="!rings.length" class="composition-tabs-widget__empty">暂无业务结构数据</p>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue';
import { screenDisplayText } from '../model/screenDisplayText';

const props = defineProps({
  model: { type: Object, default: () => ({}) },
  // 页面草稿顶部只需要提升指定业务线；未传时维持原三环展示。
  ringKeys: { type: Array, default: null },
  compact: { type: Boolean, default: false },
  showTotalCaption: { type: Boolean, default: true }
});
const emit = defineEmits(['business-line-select']);

const rootRef = ref(null);
const RING_DEFINITIONS = Object.freeze([
  { ringKey: 'deposit', label: '存款' },
  { ringKey: 'loan', label: '贷款' },
  { ringKey: 'income', label: '收入' }
]);

const component = computed(() => (Array.isArray(props.model?.components) ? props.model.components[0] : null) || {});
const title = computed(() => screenDisplayText(props.model?.title || component.value.title || '业务结构') || '业务结构');
const subtitle = computed(() => screenDisplayText(props.model?.subtitle || component.value.subtitle || ''));

function finite(value) {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : null;
}

function readableNumber(value, digits = 2) {
  const number = finite(value);
  return number === null ? '' : new Intl.NumberFormat('zh-CN', { maximumFractionDigits: digits }).format(number);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function normalizedKey(value) {
  return text(value).toLowerCase().replace(/[\s_-]+/g, '');
}

function findByKey(items, key) {
  if (!Array.isArray(items)) return null;
  const target = normalizedKey(key);
  return items.find(item => [item?.ringKey, item?.tabKey, item?.key, item?.metricKey]
    .some(candidate => normalizedKey(candidate) === target)) || null;
}

function valueNode(value, fallback = null) {
  if (value && typeof value === 'object' && !Array.isArray(value)) return value;
  if (value === null || value === undefined || value === '') return fallback;
  return { value, text: String(value) };
}

function firstDefined(...values) {
  return values.find(value => value !== undefined && value !== null) ?? null;
}

function sectionItem(section, key) {
  if (!section || !Array.isArray(section.items)) return null;
  return section.items.find(item => normalizedKey(item?.tabKey || item?.key || item?.label) === normalizedKey(key)) || null;
}

function sourceFromSections(key) {
  const sections = Array.isArray(props.model?.sections) ? props.model.sections : [];
  const corporateSection = sections.find(section => normalizedKey(section?.sectionKey || section?.businessLine) === 'corporate' || section?.businessLine === 'CORP');
  const retailSection = sections.find(section => normalizedKey(section?.sectionKey || section?.businessLine) === 'retail' || section?.businessLine === 'RETAIL');
  const incomeSection = sections.find(section => normalizedKey(section?.sectionKey || section?.businessLine) === 'income');

  if (key === 'income') {
    const item = sectionItem(incomeSection, 'income');
    if (item?.corporate || item?.retail || item?.company || item?.corp) {
      return { ...item, corporate: item.corporate || item.company || item.corp, retail: item.retail || item.consumer };
    }
    return null;
  }

  const corporate = sectionItem(corporateSection, key);
  const retail = sectionItem(retailSection, key);
  if (!corporate && !retail) return null;
  return {
    ringKey: key,
    tabKey: key,
    label: key === 'deposit' ? '存款' : '贷款',
    corporate: firstDefined(corporate?.value, corporate?.corporate),
    retail: firstDefined(retail?.value, retail?.retail),
    total: corporate?.total || retail?.total || null,
    other: corporate?.other || retail?.other || null,
    gap: corporate?.gap || retail?.gap || null,
    otherShareText: corporate?.otherShareText || retail?.otherShareText || '',
    gapShareText: corporate?.gapShareText || retail?.gapShareText || '',
    state: corporate?.state || retail?.state || 'PENDING',
    statusMessage: corporate?.statusMessage || retail?.statusMessage || ''
  };
}

function sourceForRing(key) {
  const configured = findByKey(props.model?.rings, key)
    || findByKey(props.model?.compositionRings, key)
    || findByKey(component.value.rings, key);
  if (configured) return configured;

  const tab = findByKey(props.model?.tabs, key) || findByKey(component.value.tabs, key);
  if (tab) return tab;

  // 兼容旧草稿中唯一的业务结构 tab：它实际只绑定了存款公司/零售字段。
  // 只按精确 tabKey 和字段配置映射，避免把任意未知 tab 猜成贷款或收入。
  if (key === 'deposit') {
    const legacyTab = [...(Array.isArray(props.model?.tabs) ? props.model.tabs : []), ...(Array.isArray(component.value.tabs) ? component.value.tabs : [])]
      .find(item => normalizedKey(item?.tabKey) === 'businessstructure'
        && item?.corporateField === '测试_直营对公存款'
        && item?.retailField === '测试_直营零售存款');
    if (legacyTab) return { ...legacyTab, ringKey: 'deposit', tabKey: 'deposit', label: '存款' };
  }

  if (key === 'income') {
    const income = component.value.income || component.value.incomeComposition || component.value.intermediaryIncome;
    if (income?.corporate || income?.retail || income?.company || income?.corp) {
      return { ...income, corporate: income.corporate || income.company || income.corp, retail: income.retail || income.consumer, tabKey: 'income', ringKey: 'income', label: '收入' };
    }
  }

  return sourceFromSections(key);
}

function normalizedRing(raw, definition) {
  const source = raw || {};
  const corporate = valueNode(source.corporate || source.company || source.corp, null);
  const retail = valueNode(source.retail || source.consumer, null);
  const total = valueNode(source.total || source.denominator, null);
  const corporateShare = finite(corporate?.share);
  const retailShare = finite(retail?.share);
  const denominator = finite(total?.value);
  const hasTotal = denominator !== null && denominator > 0;
  const exceedsTotal = source.gap || (corporateShare !== null && retailShare !== null && corporateShare + retailShare > 100 + 1e-6);
  const ready = hasTotal
    && corporateShare !== null
    && corporateShare >= 0
    && retailShare !== null
    && retailShare >= 0
    && !exceedsTotal
    && text(source.state).toUpperCase() !== 'PENDING'
    && text(source.state).toUpperCase() !== 'NO_TOTAL'
    && text(source.state).toUpperCase() !== 'MISSING_SIDE'
    && text(source.state).toUpperCase() !== 'NO_SOURCE';
  const unit = screenDisplayText(total?.unit || corporate?.unit || retail?.unit || source.unit);
  const totalText = denominator === null
    ? screenDisplayText(total?.text || '—')
    : readableNumber(denominator);
  return {
    ...source,
    ringKey: definition.ringKey,
    tabKey: text(source.tabKey || definition.ringKey) || definition.ringKey,
    label: screenDisplayText(source.label) || definition.label,
    corporate,
    retail,
    total,
    unit,
    totalText: totalText || '—',
    hasTotal,
    ready,
    statusMessage: exceedsTotal ? '构成合计超过核定总量，无法绘制占比'
      : screenDisplayText(source.statusMessage) || (ready ? '' : '占比待接入'),
    other: valueNode(source.other, null),
    gap: valueNode(source.gap, null),
    otherShareText: screenDisplayText(source.otherShareText || source.other?.shareText),
    gapShareText: screenDisplayText(source.gapShareText || source.gap?.shareText)
  };
}

const selectedRingDefinitions = computed(() => {
  if (!Array.isArray(props.ringKeys)) return RING_DEFINITIONS;
  const keys = new Set(props.ringKeys.map(normalizedKey));
  return RING_DEFINITIONS.filter(definition => keys.has(normalizedKey(definition.ringKey)));
});

const rings = computed(() => selectedRingDefinitions.value
  .map(definition => normalizedRing(sourceForRing(definition.ringKey), definition)));

function shareText(value) {
  const share = finite(value?.share);
  if (share !== null && share >= 0) return `${readableNumber(share, 1)}%`;
  return '占比不可计算';
}

function valueText(value, includeUnit = true) {
  if (!value || value.value === null || value.value === undefined || value.value === '') return '—';
  return `${readableNumber(value.value) || screenDisplayText(value.text || value.value)}${includeUnit && value.unit ? ` ${screenDisplayText(value.unit)}` : ''}`.trim();
}

function ringStyle(ring) {
  if (!ring.ready) return {};
  const corporate = Math.min(100, Math.max(0, finite(ring.corporate?.share) || 0));
  const retail = Math.min(100 - corporate, Math.max(0, finite(ring.retail?.share) || 0));
  const end = corporate + retail;
  return {
    background: `conic-gradient(var(--composition-corp, #4de8ef) 0 ${corporate}%, var(--composition-retail, #9670ff) ${corporate}% ${end}%, var(--composition-track, rgba(116, 151, 211, .22)) ${end}% 100%)`
  };
}

function ringAriaLabel(ring) {
  const company = ring.ready ? shareText(ring.corporate) : '待接入';
  const retail = ring.ready ? shareText(ring.retail) : '待接入';
  return `${ring.label}，公司${company}，零售${retail}`;
}

function selectBusinessLine(businessLine, tabKey) {
  const key = text(tabKey);
  if (!key || !['CORP', 'RETAIL'].includes(businessLine)) return;
  emit('business-line-select', { businessLine, tabKey: key });
}
</script>

<style scoped>
.composition-tabs-widget { display: flex; min-width: 0; min-height: 220px; height: 100%; padding: 14px; flex-direction: column; color: var(--panorama-text, #eaf2ff); background: var(--panorama-panel, rgba(8, 24, 61, .86)); border: 1px solid var(--panorama-border, rgba(119, 163, 255, .3)); border-radius: 8px; outline: none; box-shadow: inset 0 1px 0 rgba(201, 231, 255, .05), 0 8px 22px rgba(0, 0, 0, .12); }
.composition-tabs-widget__header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.composition-tabs-widget__header h2 { margin: 0; color: var(--panorama-text, #eaf2ff); font-size: 16px; font-weight: 650; }
.composition-tabs-widget__header h2::before { display: inline-block; width: 3px; height: 16px; margin-right: 8px; border-radius: 1px; background: var(--panorama-cyan, #4de8ef); vertical-align: -2px; content: ''; }
.composition-tabs-widget__header p { margin: 4px 0 0 11px; color: var(--panorama-text-dim, #8fa9db); font-size: 10px; }
.composition-tabs-widget__rings { display: grid; min-height: 0; margin-top: 12px; flex: 1 1 auto; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.composition-ring-card { display: flex; min-width: 0; padding: 9px 7px 8px; flex-direction: column; align-items: stretch; gap: 7px; border: 1px solid rgba(106, 157, 220, .24); border-radius: 6px; background: rgba(3, 12, 31, .22); }
.composition-ring-card.is-pending { border-color: rgba(119, 163, 255, .25); }
.composition-ring-card__heading { display: flex; align-items: baseline; justify-content: space-between; gap: 4px; }
.composition-ring-card__heading strong { color: #f5f9ff; font-size: 13px; }
.composition-ring-card__heading span { color: var(--panorama-text-dim, #8fa9db); font-size: 10px; }
.composition-ring { position: relative; width: clamp(64px, 7vw, 88px); height: clamp(64px, 7vw, 88px); margin: 2px auto 0; flex: 0 0 auto; border-radius: 50%; background: var(--composition-track, rgba(116, 151, 211, .22)); box-shadow: 0 0 16px rgba(77, 232, 239, .1); }
.composition-ring::after { position: absolute; inset: 10px; border-radius: 50%; background: var(--panorama-panel, #08183d); box-shadow: inset 0 0 0 1px rgba(157, 193, 255, .1); content: ''; }
.composition-ring.is-pending { background: var(--composition-track, rgba(116, 151, 211, .22)); box-shadow: none; }
.composition-ring__center { position: absolute; z-index: 1; inset: 0; display: flex; align-items: center; justify-content: center; flex-direction: column; text-align: center; }
.composition-ring__center strong { max-width: 80%; overflow: hidden; color: #f5f9ff; font-size: 14px; font-variant-numeric: tabular-nums; text-overflow: ellipsis; white-space: nowrap; }
.composition-ring__center small { margin-top: 2px; color: var(--panorama-text-dim, #8fa9db); font-size: 9px; }
.composition-ring-card__legend { display: grid; gap: 4px; }
.composition-ring-card__legend button { display: grid; min-width: 0; padding: 4px 5px; grid-template-columns: 7px minmax(0, 1fr) auto; align-items: center; gap: 4px; border: 1px solid rgba(121, 161, 248, .24); border-radius: 4px; color: #bcd5ff; background: rgba(55, 112, 206, .16); font: inherit; font-size: 10px; cursor: pointer; text-align: left; }
.composition-ring-card__legend button:hover,.composition-ring-card__legend button:focus-visible { border-color: var(--panorama-cyan, #4de8ef); outline: none; }
.composition-ring-card__legend button strong { color: var(--panorama-cyan, #4de8ef); font-size: 10px; font-variant-numeric: tabular-nums; }
.composition-ring-card__dot { width: 6px; height: 6px; border-radius: 50%; }
.composition-ring-card__dot--corp { background: var(--composition-corp, #4de8ef); }
.composition-ring-card__dot--retail { background: var(--composition-retail, #9670ff); }
.composition-ring-card__status { min-height: 14px; margin: 0; color: var(--panorama-amber, #ffc45e); font-size: 9px; line-height: 1.4; }
.composition-ring-card__status--remainder { display: grid; gap: 2px; color: var(--panorama-text-dim, #8fa9db); }
.composition-tabs-widget__empty { margin: 12px 0 0; color: var(--panorama-amber, #ffc45e); font-size: 11px; }
.composition-tabs-widget--single .composition-tabs-widget__rings { grid-template-columns: minmax(0, 1fr); }
.composition-tabs-widget--no-total-caption .composition-ring__center strong { max-width: 100%; overflow: visible; text-overflow: clip; }
.composition-tabs-widget--compact { min-height: 0; height: auto; padding: 7px; }
.composition-tabs-widget--compact .composition-tabs-widget__header h2 { font-size: 12px; }
.composition-tabs-widget--compact .composition-tabs-widget__header h2::before { width: 2px; height: 12px; margin-right: 5px; vertical-align: -2px; }
.composition-tabs-widget--compact .composition-tabs-widget__header p { display: none; }
.composition-tabs-widget--compact .composition-tabs-widget__rings { margin-top: 5px; grid-template-columns: minmax(0, 1fr); gap: 0; }
.composition-tabs-widget--compact .composition-ring-card { display: grid; padding: 5px; grid-template-columns: 64px minmax(0, 1fr); grid-template-rows: auto minmax(0, 1fr) auto; align-items: center; column-gap: 8px; row-gap: 3px; }
.composition-tabs-widget--compact .composition-ring-card__heading { grid-column: 2; }
.composition-tabs-widget--compact .composition-ring-card__heading strong { font-size: 12px; }
.composition-tabs-widget--compact .composition-ring-card__heading span { font-size: 10px; }
.composition-tabs-widget--compact .composition-ring { width: 64px; height: 64px; margin: 0; grid-column: 1; grid-row: 1 / span 3; }
.composition-tabs-widget--compact .composition-ring::after { inset: 9px; }
.composition-tabs-widget--compact .composition-ring__center strong { font-size: 12px; }
.composition-tabs-widget--compact .composition-ring__center small { font-size: 8px; }
.composition-tabs-widget--compact .composition-ring-card__legend { grid-column: 2; gap: 3px; }
.composition-tabs-widget--compact .composition-ring-card__legend button { padding: 3px 4px; font-size: 11px; }
.composition-tabs-widget--compact .composition-ring-card__legend button strong { font-size: 11px; }
.composition-tabs-widget--compact .composition-ring-card__status { grid-column: 2; min-height: 0; font-size: 11px; }
.composition-tabs-widget--compact .composition-tabs-widget__empty { margin-top: 4px; font-size: 9px; }
@media (max-width: 620px) {
  .composition-tabs-widget { min-height: 200px; padding: 12px; }
  .composition-tabs-widget__header h2 { font-size: 14px; }
  .composition-tabs-widget__rings { gap: 5px; }
  .composition-ring-card { padding: 7px 5px; }
  .composition-ring-card__heading strong { font-size: 12px; }
  .composition-tabs-widget--compact { min-height: 0; padding: 6px; }
  .composition-tabs-widget--compact .composition-ring-card { grid-template-columns: 56px minmax(0, 1fr); }
  .composition-tabs-widget--compact .composition-ring { width: 56px; height: 56px; }
}
@media (min-width: 621px) and (max-width: 1500px) {
  .composition-ring-card__legend button { grid-template-columns: 7px minmax(0, 1fr); }
  .composition-ring-card__legend button strong { grid-column: 2; }
  .composition-tabs-widget--compact .composition-ring-card__legend button { grid-template-columns: 7px minmax(0, 1fr) auto; }
  .composition-tabs-widget--compact .composition-ring-card__legend button strong { grid-column: auto; }
}
</style>
