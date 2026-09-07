<template>
  <section ref="rootRef" class="city-panorama" aria-label="市级支行经营全景">
    <header class="city-header">
      <div class="city-breadcrumb"><span><component :is="Location" /> 陕西省 / {{ cityName || cityCode || '市级' }}</span><span>数据日期 {{ displayDate }}</span></div>
      <div class="city-title-block">
        <h1>{{ cityTitle }}</h1>
        <span>全市经营口径</span>
      </div>
      <div class="city-header-actions">
        <button type="button" data-action="city-back" @click="emit('back')"><component :is="Back" /> 返回全省</button>
        <button type="button" data-action="city-refresh" aria-label="刷新市级数据" @click="emit('refresh')"><component :is="Refresh" /></button>
        <button type="button" data-action="city-fullscreen" aria-label="市级全屏" @click="requestFullscreen"><component :is="FullScreen" /></button>
        <button type="button" data-action="city-close" aria-label="关闭市级全景" @click="emit('close')"><component :is="Close" /></button>
      </div>
    </header>

    <section class="city-kpi-grid" aria-label="市级核心指标">
      <article v-for="(kpi, index) in cityKpiCards" :key="kpi.key || index" class="city-kpi" :data-testid="`city-kpi-${kpi.key || index}`">
        <span class="city-kpi-label">{{ kpi.label || '指标' }}</span>
        <strong>{{ cityMetricText(kpi) }}</strong>
        <small v-if="kpi.unit && !summaryUnbound">{{ kpi.unit }}</small>
        <em v-if="formatChange(kpi.change) !== null" :class="changeClass(kpi.change)">{{ kpi.change >= 0 ? '↑' : '↓' }} {{ Math.abs(Number(kpi.change)).toFixed(1) }}%</em>
      </article>
    </section>
    <div v-if="summaryUnbound" class="city-summary-unbound" data-testid="city-summary-unbound">市级汇总未绑定，无法据下级机构加总</div>
    <span class="panorama-visually-hidden" data-testid="selected-org-code">{{ selectedOrgCode }}</span>

    <section class="city-workspace">
      <article class="panorama-panel city-map-panel">
        <div class="city-map-toolbar">
          <div class="panorama-panel-heading"><h2>支行网点分布</h2><span>{{ cityInstitutions.length }} 家</span></div>
          <div class="city-filter-tabs">
            <button type="button" :class="{ active: !attentionOnly }" @click="attentionOnly = false">全部支行</button>
            <button type="button" data-testid="attention-filter" :class="{ active: attentionOnly }" @click="attentionOnly = !attentionOnly">经营关注</button>
          </div>
        </div>
        <PanoramaMap
          class="city-map"
          :class="{ 'is-attention-filter': attentionOnly }"
          :geo-json="cityGeoJson"
          :points="mapInstitutions"
          :attention-only="attentionOnly"
          :demo="demo"
          :selected-org-code="selectedOrgCode"
          mode="city"
          :selected-region-code="cityCode"
          @branch-select="selectBranch"
        />
        <div class="city-map-legend"><span><i class="is-cyan" />支行</span><span><i class="is-amber" />经营关注</span><span><i class="is-ring" />聚合网点</span></div>
        <div class="city-coordinate-note">已定位 {{ locatedInstitutions.length }} 家 <span>|</span> 待补充坐标 {{ missingCoordinates.length }} 家</div>
      </article>

      <article class="panorama-panel city-list-panel">
        <div class="panorama-panel-heading"><h2>支行列表 <small>{{ filteredInstitutions.length }} 家</small></h2><button type="button" data-testid="deposit-sort" class="city-sort-button" @click="toggleSort">按存款余额 {{ sortDescending ? '↓' : '↑' }}</button></div>
        <div class="city-search-wrap"><label for="city-branch-search">搜索支行名称</label><input id="city-branch-search" data-testid="branch-search" v-model="search" type="search" placeholder="搜索支行名称" /></div>
        <div v-if="pagedInstitutions.length" class="city-branch-list">
          <button v-for="(branch, index) in pagedInstitutions" :key="branch.orgCode" type="button" class="city-branch-row" data-testid="branch-row" :class="{ selected: selectedOrgCode === branch.orgCode }" @click="selectBranch(branch.orgCode)">
            <span class="city-branch-rank">{{ (page - 1) * pageSize + index + 1 }}</span>
            <span class="city-branch-name">{{ branch.orgName || branch.orgCode || '—' }}</span>
            <strong>{{ formatMetric(branch.metrics?.deposit) }}</strong>
            <span :class="rateClass(branch.metrics?.rate)">{{ formatPercent(branch.metrics?.rate) }}</span>
          </button>
        </div>
        <div v-else class="panorama-empty">暂无匹配支行</div>
        <div v-if="missingCoordinates.length" class="city-missing-coordinates" data-testid="branch-missing-coordinates">
          无坐标 {{ missingCoordinates.length }} 家：{{ missingCoordinates.map(item => item.orgName || item.orgCode).join('、') }}
        </div>
        <div class="city-pagination"><button type="button" data-testid="branch-page-prev" :disabled="page <= 1" @click="page -= 1">‹</button><span>{{ page }} / {{ totalPages }}</span><button type="button" data-testid="branch-page-next" :disabled="page >= totalPages" @click="page += 1">›</button></div>
      </article>
      <section v-if="selectedBranch" class="panorama-panel city-detail-panel" data-testid="branch-detail" :aria-expanded="String(detailExpanded)">
        <div class="city-detail-heading">
          <div><span>当前支行</span><h2>{{ selectedBranch.orgName || selectedBranch.orgCode }}</h2></div>
          <button type="button" data-action="toggle-detail" @click="detailExpanded = !detailExpanded">{{ detailExpanded ? '收起详情' : '展开详情' }}</button>
        </div>
        <div v-if="detailExpanded" class="city-detail-body">
          <div class="city-detail-metrics">
            <div><span>存款余额（亿元）</span><strong>{{ formatMetric(selectedBranch.metrics?.deposit) }}</strong></div>
            <div><span>贷款余额（亿元）</span><strong>{{ formatMetric(selectedBranch.metrics?.loan) }}</strong></div>
            <div><span>客户总量（万户）</span><strong>{{ formatMetric(selectedBranch.metrics?.customers) }}</strong></div>
            <div><span>目标完成率</span><strong>{{ formatPercent(selectedBranch.metrics?.rate) }}</strong></div>
          </div>
          <PanoramaTrend :trend="selectedBranch.trend" :title="`${selectedBranch.orgName || selectedBranch.orgCode}经营趋势`" compact data-testid="branch-detail-trend" class="city-detail-trend" />
          <ul v-if="selectedBranch.attention?.length" class="city-detail-attention">
            <li v-for="(item, index) in selectedBranch.attention" :key="item.label || index"><span>!</span>{{ item.label || '—' }} <strong>{{ formatMetric(item.count) }}</strong></li>
          </ul>
        </div>
      </section>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { Back, Close, FullScreen, Location, Refresh } from '@element-plus/icons-vue';
import PanoramaMap from './PanoramaMap.vue';
import PanoramaTrend from './PanoramaTrend.vue';
import { cityGeoByCode } from './geography.js';

const props = defineProps({
  model: { type: Object, default: () => ({}) },
  demo: { type: Boolean, default: false },
  cityCode: { type: String, default: '' },
  cityName: { type: String, default: '' },
  initialOrgCode: { type: String, default: '' }
});
const emit = defineEmits(['close', 'back', 'refresh', 'fullscreen', 'branch-select']);

const search = ref('');
const attentionOnly = ref(false);
const sortDescending = ref(true);
const page = ref(1);
const pageSize = 5;
const selectedOrgCode = ref(props.initialOrgCode || '');
const detailExpanded = ref(true);
const rootRef = ref(null);

const safeModel = computed(() => ({
  title: '', dataDate: '', kpis: [], trend: [], institutions: [], citySummaries: {},
  ...(props.model && typeof props.model === 'object' ? props.model : {})
}));
const citySummary = computed(() => {
  const map = safeModel.value.citySummaries;
  if (!map || typeof map !== 'object' || !props.cityCode) return null;
  return map[props.cityCode] || null;
});
const summaryUnbound = computed(() => !citySummary.value || !Array.isArray(citySummary.value.kpis));
const cityKpiCards = computed(() => {
  if (summaryUnbound.value) {
    return ['deposit', 'loan', 'customers', 'target'].map(key => ({ key, label: ({ deposit: '存款余额', loan: '贷款余额', customers: '客户总量', target: '目标完成率' })[key], value: null, unit: key === 'customers' ? '万户' : key === 'target' ? '%' : '亿元', change: null }));
  }
  return citySummary.value.kpis.slice(0, 4);
});
const cityTitle = computed(() => props.cityName ? `${props.cityName} · 支行经营全景` : (citySummary.value?.title || safeModel.value.title || '市级支行经营全景'));
const displayDate = computed(() => citySummary.value?.dataDate || safeModel.value.dataDate || '—');
const cityGeoJson = computed(() => cityGeoByCode?.[props.cityCode] || null);
const cityInstitutions = computed(() => {
  if (!props.cityCode) return [];
  return safeModel.value.institutions.filter(item => item && String(item.cityCode || '') === String(props.cityCode));
});
const locatedInstitutions = computed(() => cityInstitutions.value.filter(item => item.located && item.lng != null && item.lat != null));
const missingCoordinates = computed(() => cityInstitutions.value.filter(item => !(item.located && item.lng != null && item.lat != null)));
const normalizedSearch = computed(() => search.value.trim().toLocaleLowerCase());
const filteredInstitutions = computed(() => {
  const list = cityInstitutions.value.filter(branch => {
    if (attentionOnly.value && (!Array.isArray(branch.attention) || branch.attention.length === 0)) return false;
    if (!normalizedSearch.value) return true;
    return String(branch.orgName || '').toLocaleLowerCase().includes(normalizedSearch.value);
  });
  return [...list].sort((a, b) => {
    const left = finiteValue(a.metrics?.deposit);
    const right = finiteValue(b.metrics?.deposit);
    if (left === null && right === null) return 0;
    if (left === null) return 1;
    if (right === null) return -1;
    return sortDescending.value ? right - left : left - right;
  });
});
const totalPages = computed(() => Math.max(1, Math.ceil(filteredInstitutions.value.length / pageSize)));
const pagedInstitutions = computed(() => filteredInstitutions.value.slice((page.value - 1) * pageSize, page.value * pageSize));
// 地图跟随当前搜索与经营关注筛选展示全部匹配机构，分页只限制右侧列表。
const mapInstitutions = computed(() => filteredInstitutions.value);
const selectedBranch = computed(() => cityInstitutions.value.find(item => item.orgCode === selectedOrgCode.value) || null);

function finiteValue(value) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}
function formatMetric(value) {
  const number = finiteValue(value);
  if (number === null) return '—';
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 2, minimumFractionDigits: Number.isInteger(number) ? 0 : 2 }).format(number);
}
function formatPercent(value) {
  const number = finiteValue(value);
  return number === null ? '—' : `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 1, minimumFractionDigits: 1 }).format(number)}%`;
}
function formatChange(value) {
  return finiteValue(value);
}
function changeClass(value) {
  const number = finiteValue(value);
  return number !== null && number < 0 ? 'is-down' : 'is-up';
}
function rateClass(value) {
  const number = finiteValue(value);
  return number !== null && number < 80 ? 'is-warning' : 'is-up';
}
function cityMetricText(kpi) {
  return summaryUnbound.value ? '未绑定' : formatMetric(kpi?.value);
}
function selectBranch(orgCode) {
  const code = String(orgCode || '');
  if (!code || !cityInstitutions.value.some(item => item.orgCode === code)) return;
  selectedOrgCode.value = code;
  detailExpanded.value = true;
  emit('branch-select', code);
}
function toggleSort() {
  sortDescending.value = !sortDescending.value;
  page.value = 1;
}
function requestFullscreen() {
  emit('fullscreen');
  const element = rootRef.value;
  if (element?.requestFullscreen && !document.fullscreenElement) {
    element.requestFullscreen().catch(() => {});
  }
}
onMounted(() => {
  if ((!selectedOrgCode.value || !cityInstitutions.value.some(item => item.orgCode === selectedOrgCode.value)) && cityInstitutions.value.length) {
    selectBranch(cityInstitutions.value[0].orgCode);
  }
});
watch(cityInstitutions, list => {
  if (!list.length) return;
  if (!selectedOrgCode.value || !list.some(item => item.orgCode === selectedOrgCode.value)) {
    selectBranch(list[0].orgCode);
  }
});
watch([search, attentionOnly], () => { page.value = 1; });
watch(totalPages, value => { if (page.value > value) page.value = value; });
</script>

<style src="./panorama.scss" lang="scss"></style>
