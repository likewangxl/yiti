<template>
  <section class="personal-config-thumbnail" data-testid="personal-config-thumbnail" aria-label="个人驾驶舱页面缩略图">
    <button type="button" class="personal-config-thumbnail__title" :class="{ 'is-active': selectedKey === 'page-title' }" data-config-key="page-title" :aria-pressed="selectedKey === 'page-title'" @click="select('page-title')">
      <span class="personal-config-thumbnail__eyebrow">PERSONAL COCKPIT</span>
      <strong>{{ settings.title || '个人经营驾驶舱' }}</strong>
      <small>个人运行页 · 数据占位</small>
    </button>
    <div class="personal-config-thumbnail__metrics" :class="{ 'is-active': selectedKey === 'metrics' }" data-config-key="metrics">
      <button type="button" class="personal-config-thumbnail__region-title-button" :aria-pressed="selectedKey === 'metrics'" :disabled="disabled" @click="select('metrics')">{{ settings.metricsTitle || '核心指标' }}</button>
      <span class="personal-config-thumbnail__metric-grid">
        <button v-for="(metric, index) in settings.metrics || []" :key="metric._key || metric.metricCode || index" type="button" class="personal-config-thumbnail__metric" :class="{ 'is-active': selectedKey === metricKey(metric, index) }" :data-config-key="metricKey(metric, index)" :aria-pressed="selectedKey === metricKey(metric, index)" :disabled="disabled" @click="select(metricKey(metric, index))">
          <b>{{ metric.metricName || `指标 ${index + 1}` }}</b><strong>—</strong><small>{{ metric.unit || '单位未配置' }}</small>
        </button>
        <span v-if="!(settings.metrics || []).length" class="personal-config-thumbnail__empty">暂无指标</span>
      </span>
    </div>
    <div class="personal-config-thumbnail__lower">
      <button v-for="region in regions" :key="region.key" type="button" class="personal-config-thumbnail__region" :class="[`is-${region.key}`, { 'is-active': selectedKey === region.key }]" :data-config-key="region.key" :aria-pressed="selectedKey === region.key" :disabled="disabled" @click="select(region.key)">
        <span class="personal-config-thumbnail__region-title">{{ settings[region.titleKey] || region.fallback }}</span>
        <span class="personal-config-thumbnail__placeholder">{{ region.placeholder }}</span>
        <BranchThumbnailChart v-if="region.key !== 'target'" :kind="region.key === 'organization' ? 'INSTITUTION_RANKING' : 'BUSINESS_GROWTH'" class="personal-config-thumbnail__chart" />
        <BranchThumbnailChart v-else kind="PROGRESS" class="personal-config-thumbnail__chart" />
      </button>
    </div>
  </section>
</template>

<script setup>
import BranchThumbnailChart from '../panorama/BranchThumbnailChart.vue';
const props = defineProps({ settings: { type: Object, required: true }, selectedKey: { type: String, default: 'page-title' }, disabled: Boolean });
const emit = defineEmits(['select']);
const regions = [
  { key: 'trend', titleKey: 'trendTitle', fallback: '指标趋势', placeholder: '历史趋势 · —' },
  { key: 'organization', titleKey: 'shareTitle', fallback: '机构贡献', placeholder: '机构对比 · —' },
  { key: 'target', titleKey: 'completionTitle', fallback: '目标完成度', placeholder: '当前值 / 目标值 · —' },
];
function select(key) { if (!key || props.disabled) return; emit('select', key); }
function metricKey(metric, index) { return `metric-${metric?._key || metric?.metricCode || index}`; }
</script>

<style scoped>
.personal-config-thumbnail { display:grid; gap:8px; min-width:0; padding:16px; color:#dceeff; background:linear-gradient(145deg,#071f42,#04162f); border:1px solid #123d69; border-radius:8px; box-shadow:inset 0 0 24px #0d5a8c22; }
.personal-config-thumbnail button { min-width:0; color:inherit; font:inherit; text-align:left; cursor:pointer; }
.personal-config-thumbnail__title,.personal-config-thumbnail__region,.personal-config-thumbnail__metrics { position:relative; border:1px solid #1d5684; border-radius:6px; background:#0a2851; }
.personal-config-thumbnail__title { display:grid; gap:4px; padding:13px 15px; }
.personal-config-thumbnail__title strong { color:#f3f9ff; font-size:22px; }
.personal-config-thumbnail__title small,.personal-config-thumbnail__eyebrow { color:#7da7cf; font-size:10px; }
.personal-config-thumbnail__eyebrow { letter-spacing:.1em; }
.personal-config-thumbnail__region,.personal-config-thumbnail__metrics { padding:10px; }
.personal-config-thumbnail__region-title { display:block; color:#e5f3ff; font-size:13px; font-weight:700; }
.personal-config-thumbnail__region-title-button { display:block; width:100%; padding:0 0 8px; border:0; color:#e5f3ff; background:transparent; font-size:13px; font-weight:700; text-align:left; cursor:pointer; }
.personal-config-thumbnail__metrics { display:grid; gap:8px; }
.personal-config-thumbnail__metric-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:6px; }
.personal-config-thumbnail__metric { display:grid; gap:3px; min-width:0; padding:8px; border:1px solid #235b8c; border-radius:4px; color:inherit; background:#0d315e; text-align:left; cursor:pointer; }
.personal-config-thumbnail__metric b { overflow-wrap:anywhere; color:#bfdcff; font-size:10px; }
.personal-config-thumbnail__metric strong { color:#fff; font-size:20px; line-height:1; }
.personal-config-thumbnail__metric small { color:#78a2c9; font-size:9px; }
.personal-config-thumbnail__empty { color:#78a2c9; font-size:11px; }
.personal-config-thumbnail__lower { display:grid; grid-template-columns:1fr 1fr; gap:8px; }
.personal-config-thumbnail__lower .personal-config-thumbnail__region { min-height:140px; }
.personal-config-thumbnail__lower .is-target { grid-column:1 / -1; min-height:104px; }
.personal-config-thumbnail__placeholder { display:block; margin-top:6px; color:#6f9bc4; font-size:10px; }
.personal-config-thumbnail__chart { height:86px; margin-top:10px; }
.personal-config-thumbnail__title.is-active,.personal-config-thumbnail__region.is-active,.personal-config-thumbnail__metrics.is-active,.personal-config-thumbnail__metric.is-active,.personal-config-thumbnail__metric:hover { border-color:#14dfff; box-shadow:inset 0 0 0 1px #14dfff,0 0 10px #00c8ff33; }
.personal-config-thumbnail button:disabled { cursor:not-allowed; opacity:.65; }
.personal-config-thumbnail button:focus-visible { outline:2px solid #a5f3ff; outline-offset:2px; }
@media(max-width:760px) { .personal-config-thumbnail__metric-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } .personal-config-thumbnail__lower { grid-template-columns:1fr; } .personal-config-thumbnail__lower .personal-config-thumbnail__region { min-height:110px; } }
</style>
