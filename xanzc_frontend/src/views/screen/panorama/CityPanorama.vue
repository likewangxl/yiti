<template>
  <section ref="rootRef" class="city-panorama" aria-label="市级支行经营全景">
    <header class="city-header">
      <div class="city-breadcrumb"><span><component :is="Location" /> 陕西省 / {{ cityName || cityCode || '市级' }}</span><span>数据日期 {{ displayDate }}</span></div>
      <div class="city-title-block">
        <h1>{{ cityTitle }}</h1>
        <span>全市经营口径</span>
      </div>
      <div class="city-header-actions">
        <label v-if="cityOperatingHeaderEnabled" class="city-header-amount-unit-control">
          <span class="panorama-visually-hidden">金额单位</span>
          <select v-model="cityAmountUnit" data-testid="city-header-amount-unit" aria-label="金额单位">
            <option value="YUAN">元</option>
            <option value="TEN_THOUSAND">万元</option>
            <option value="HUNDRED_MILLION">亿元</option>
          </select>
        </label>
        <button type="button" data-action="city-back" @click="backToProvince"><component :is="Back" /> 返回全省</button>
        <button type="button" data-action="city-refresh" aria-label="刷新市级数据" @click="emit('refresh')"><component :is="Refresh" /></button>
        <button type="button" data-action="city-fullscreen" aria-label="市级全屏" @click="requestFullscreen"><component :is="FullScreen" /></button>
        <button type="button" data-action="city-close" aria-label="关闭市级全景" @click="closeCity"><component :is="Close" /></button>
      </div>
    </header>

    <CityOperatingHeader
      v-if="cityOperatingHeaderEnabled"
      :source-presentation="sourcePresentation"
      :city-summary="citySummary"
      :amount-unit="cityAmountUnit"
      :demo="demo"
      @business-line-select="selectCityBusinessLine"
    />
    <section v-else class="city-kpi-grid" aria-label="市级核心指标">
      <article v-for="(kpi, index) in cityKpiCards" :key="kpi.key || index" class="city-kpi" :data-testid="`city-kpi-${kpi.key || index}`">
        <span class="city-kpi-label">{{ kpi.label || '指标' }}</span>
        <strong :title="metricTitle(kpi.value)">{{ displayCityKpi(kpi).text }}</strong>
        <small v-if="displayCityKpi(kpi).unit && !summaryUnbound">{{ displayCityKpi(kpi).unit }}</small>
        <em v-if="!hasMetric(kpi.value)" class="city-kpi-status" :data-testid="`city-kpi-status-${kpi.key}`">{{ cityStatus('citySummary', kpi.key).message }}</em>
        <em v-if="formatChange(kpi.change) !== null" :class="changeClass(kpi.change)">{{ kpi.change >= 0 ? '↑' : '↓' }} {{ Math.abs(Number(kpi.change)).toFixed(1) }}%</em>
      </article>
    </section>
    <div v-if="!cityOperatingHeaderEnabled && summaryUnbound" class="city-summary-unbound" data-testid="city-summary-unbound">市级汇总未绑定，无法据下级机构加总</div>
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
        <PresentationMapWidget
          v-if="mapPresentationEnabled"
          class="city-map"
          :presentation="sourcePresentation"
          :model="safeModel"
          :geo-json="cityGeoJson"
          :mode="'city'"
          :city-code="cityCode"
          :city-name="cityName"
          :metric-key="rankingMetricKey"
          :selected-org-code="selectedOrgCode"
          :selected-region-code="cityCode"
          :data-date="displayDate"
          :demo="demo"
          @branch-select="selectBranch"
          @map-context="emit('map-context', $event)"
        />
        <PanoramaMap
          v-else
          class="city-map"
          appearance="relief"
          :class="{ 'is-attention-filter': attentionOnly }"
          :geo-json="cityGeoJson"
          :points="mapInstitutions"
          :attention-only="attentionOnly"
          :demo="demo"
          color-by-metric
          :region-states="cityDistrictMapState.regionStates"
          :metric-colors="cityDistrictMapState.metricColors"
          :view-fit="cityDistrictMapState.viewFit"
          :selected-org-code="selectedOrgCode"
          mode="city"
          point-label-layout="callout"
          :selected-region-code="cityCode"
          @branch-select="selectBranch"
        />
        <div class="city-map-legend"><span><i class="is-cyan" />支行</span><span><i class="is-amber" />经营关注</span><span><i class="is-ring" />聚合网点</span><span v-if="cityDistrictMapState.hasNoInstitutionRegions">无机构</span><span v-if="cityDistrictMapState.hasUnknownLocations">归属待确认</span></div>
        <div class="city-coordinate-note">已定位 {{ locatedInstitutions.length }} 家 <span>|</span> 待补充坐标 {{ missingCoordinates.length }} 家</div>
        <p v-if="!cityInstitutions.length" class="city-inline-status city-no-visible-institutions" data-testid="city-no-visible-institutions" role="status">当前城市暂无可见机构</p>
      </article>

      <article class="panorama-panel city-list-panel">
        <CityBranchRanking
          :model="cityBranchRankingModel"
          :search="search"
          :page="page"
          :page-size="pageSize"
          @search-change="search = $event"
          @page-change="page = $event"
          @tab-change="selectRankingTab"
          @branch-select="selectBranch"
        />
        <div v-if="missingCoordinates.length" class="city-missing-coordinates" data-testid="branch-missing-coordinates">
          无坐标 {{ missingCoordinates.length }} 家：{{ missingCoordinates.map(item => item.orgName || item.orgCode).join('、') }}
        </div>
      </article>
      <section v-if="selectedBranch" class="panorama-panel city-detail-panel" data-testid="branch-detail" :aria-expanded="String(detailExpanded)">
        <div class="city-detail-heading">
          <div><span>当前支行</span><h2>{{ selectedBranch.orgName || selectedBranch.orgCode }}</h2></div>
          <button type="button" data-action="toggle-detail" @click="detailExpanded = !detailExpanded">{{ detailExpanded ? '收起详情' : '展开详情' }}</button>
        </div>
        <div v-if="detailExpanded" class="city-detail-body">
          <div class="city-detail-metrics">
            <div><span>存款余额</span><strong :title="metricTitle(selectedBranch.metrics?.deposit)">{{ displayCityMetric(selectedBranch.metrics?.deposit, '亿元', 'deposit').text }}<small>{{ displayCityMetric(selectedBranch.metrics?.deposit, '亿元', 'deposit').unit }}</small></strong></div>
            <div><span>贷款余额</span><strong :title="metricTitle(selectedBranch.metrics?.loan)">{{ displayCityMetric(selectedBranch.metrics?.loan, '亿元', 'loan').text }}<small>{{ displayCityMetric(selectedBranch.metrics?.loan, '亿元', 'loan').unit }}</small></strong></div>
            <div><span>营销有效归属客户数</span><strong :title="metricTitle(selectedBranch.metrics?.customers)">{{ displayCityMetric(selectedBranch.metrics?.customers, '万户', 'customers').text }}<small>{{ displayCityMetric(selectedBranch.metrics?.customers, '万户', 'customers').unit }}</small></strong><small v-if="!hasMetric(selectedBranch.metrics?.customers)" class="city-inline-status">{{ cityStatus('branches', 'customers').message }}</small></div>
            <div><span>目标完成率</span><strong>{{ formatPercent(selectedBranch.metrics?.rate) }}</strong><small v-if="!hasMetric(selectedBranch.metrics?.rate)" class="city-inline-status">{{ cityStatus('branches', 'rate').message }}</small></div>
            <div><span>实际目标差（亿元）</span><strong :class="signedClass(selectedBranchInsight?.targetGap)">{{ signedMetricText(selectedBranchInsight?.targetGap) }}</strong><small v-if="selectedBranchInsight?.targetGap == null" class="city-inline-status" :title="cityStatus('branches', 'target').message">{{ cityStatus('branches', 'target').message }}</small></div>
          </div>
          <div class="city-detail-observation" data-testid="branch-observation">
            <div><span>存款余额位次：</span><strong>{{ selectedBranchInsight?.rank ? `第${selectedBranchInsight.rank}名/${selectedBranchInsight.total}家` : '—' }}</strong></div>
            <div><span>中位余额差（亿元）</span><strong :class="signedClass(selectedBranchInsight?.medianDifference)">{{ signedMetricText(selectedBranchInsight?.medianDifference) }}</strong></div>
            <div><span>趋势首末变化</span><strong :class="signedClass(selectedBranchInsight?.trend?.change)">{{ signedMetricText(selectedBranchInsight?.trend?.change) }}</strong></div>
            <div><span>趋势状态</span><strong :class="trendClass(selectedBranchInsight?.trendState)">{{ selectedBranchInsight?.trendState || '—' }}</strong></div>
          </div>
          <PanoramaTrend :trend="selectedBranch.trend" :title="`${selectedBranch.orgName || selectedBranch.orgCode}经营趋势`" compact data-testid="branch-detail-trend" class="city-detail-trend" />
          <ul v-if="selectedBranch.attention?.length" class="city-detail-attention">
            <li v-for="(item, index) in selectedBranch.attention" :key="item.label || index"><span>!</span>{{ item.label || '—' }} <strong>{{ formatMetric(item.count) }}</strong></li>
          </ul>
          <small v-else class="city-inline-status" data-testid="branch-detail-attention-status" :title="cityStatus('attention', '').message">{{ cityStatus('attention', '').message }}</small>
        </div>
      </section>
    </section>
  </section>
</template>

<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue';
import * as VueRouter from 'vue-router';
import { Back, Close, FullScreen, Location, Refresh } from '@element-plus/icons-vue';
import PanoramaMap from './PanoramaMap.vue';
import PresentationMapWidget from '../presentation/map/PresentationMapWidget.vue';
import { findVisibleMapComponent } from '../presentation/map/mapModel';
import PanoramaTrend from './PanoramaTrend.vue';
import CityOperatingHeader from './CityOperatingHeader.vue';
import CityBranchRanking from './CityBranchRanking.vue';
import { cityGeoByCode } from './geography.js';
import { buildCityDistrictMapState } from './cityDistrictMapModel.js';
import { buildCityInsights } from './leadershipInsights.js';
import { resolveDataStatus } from './sourcePresentation';
import { stripTestModifier } from './targetPresentation.js';
import { buildNavigationQuery, parseNavigationQuery } from '../presentation/navigation/navigationModel';
import { isCityOperatingHeaderEnabled } from './cityOperatingHeaderModel.js';
import { buildCityBranchRankingModel } from './cityBranchRankingModel.js';

const props = defineProps({
  model: { type: Object, default: () => ({}) },
  demo: { type: Boolean, default: false },
  cityCode: { type: String, default: '' },
  cityName: { type: String, default: '' },
  initialOrgCode: { type: String, default: '' },
  initialState: { type: Object, default: () => ({}) },
  sourcePresentation: { type: Object, default: () => ({}) },
  rankingMetricKey: { type: String, default: '' },
  navigateOnBranchSelect: { type: Boolean, default: false }
});
const emit = defineEmits(['close', 'back', 'refresh', 'fullscreen', 'branch-select', 'state-change', 'map-context', 'business-line-select']);
const router = VueRouter.routerKey
  ? inject(VueRouter.routerKey, null)
  : (typeof VueRouter.useRouter === 'function' ? VueRouter.useRouter() : null);
const route = VueRouter.routeLocationKey
  ? inject(VueRouter.routeLocationKey, null)
  : (typeof VueRouter.useRoute === 'function' ? VueRouter.useRoute() : null);

const initialState = props.initialState && typeof props.initialState === 'object' ? props.initialState : {};
const search = ref(String(initialState.search || ''));
const attentionOnly = ref(Boolean(initialState.attentionOnly));
const sortDescending = ref(initialState.sortDescending === undefined ? true : Boolean(initialState.sortDescending));
const page = ref(Math.max(1, Number(initialState.page) || 1));
const pageSize = 5;
const rankingTabKey = ref(String(initialState.rankingTabKey || 'retailDepositRate'));
const selectedOrgCode = ref(props.initialOrgCode || String(initialState.selectedOrgCode || ''));
const detailExpanded = ref(initialState.detailExpanded === undefined ? true : Boolean(initialState.detailExpanded));
const cityAmountUnit = ref('TEN_THOUSAND');
const rootRef = ref(null);
let navigationQuerySyncReady = false;
let initialNavigationSnapshot = '';

const routeNavigation = computed(() => parseNavigationQuery(route?.query || {}));

function navigationStateSnapshot() {
  return {
    cityCode: props.cityCode,
    search: search.value,
    attentionOnly: attentionOnly.value,
    sortDescending: sortDescending.value,
    page: page.value,
    selectedOrgCode: selectedOrgCode.value,
    detailExpanded: detailExpanded.value,
    rankingTabKey: rankingTabKey.value
  };
}

function replaceNavigationQuery(overrides = {}) {
  if (props.navigateOnBranchSelect || !router?.replace || !navigationQuerySyncReady) return;
  const context = {
    ...routeNavigation.value,
    cityCode: props.cityCode,
    orgCode: selectedOrgCode.value,
    ...overrides
  };
  const query = buildNavigationQuery({
    ...context,
    state: overrides.state === null ? null : (overrides.state || navigationStateSnapshot())
  });
  return router.replace({ query });
}

function clearNavigationContext() {
  return replaceNavigationQuery({ cityCode: '', orgCode: '', state: null });
}

function backToProvince() {
  if (props.navigateOnBranchSelect) {
    emit('back');
    return;
  }
  navigationQuerySyncReady = true;
  void clearNavigationContext();
  emit('back');
}

function closeCity() {
  if (props.navigateOnBranchSelect) {
    emit('close');
    return;
  }
  navigationQuerySyncReady = true;
  void clearNavigationContext();
  emit('close');
}

function cityStatus(slot, semantic = '') {
  return resolveDataStatus(props.sourcePresentation, props.sourcePresentation?.runtimeIssues, slot, semantic);
}

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
const cityOperatingHeaderEnabled = computed(() => isCityOperatingHeaderEnabled(props.sourcePresentation));
const cityKpiCards = computed(() => {
  const source = new Map((Array.isArray(citySummary.value?.kpis) ? citySummary.value.kpis : []).map(item => [item?.key, item]));
  return ['deposit', 'loan', 'customers', 'revenue'].map(key => source.get(key) || {
    key,
    label: ({ deposit: '存款余额', loan: '贷款余额', customers: '营销有效归属客户数', revenue: '手工测试收入' })[key],
    value: null,
    unit: key === 'customers' ? '万户' : '亿元',
    change: null
  });
});
const cityTitle = computed(() => {
  const title = props.cityName ? `${props.cityName} · 支行经营全景` : (citySummary.value?.title || safeModel.value.title || '市级支行经营全景');
  return stripTestModifier(title);
});
const displayDate = computed(() => citySummary.value?.dataDate || safeModel.value.dataDate || '—');
const cityGeoJson = computed(() => cityGeoByCode?.[props.cityCode] || null);
const mapPresentationEnabled = computed(() => Boolean(findVisibleMapComponent(props.sourcePresentation)));
const cityInstitutions = computed(() => {
  if (!props.cityCode) return [];
  return safeModel.value.institutions.filter(item => item && String(item.cityCode || '') === String(props.cityCode));
});
const cityDistrictMapState = computed(() => {
  const state = buildCityDistrictMapState(cityGeoJson.value, cityInstitutions.value, { demo: props.demo });
  return {
    ...state,
    hasNoInstitutionRegions: Object.values(state.regionStates || {}).includes('NO_INSTITUTION')
  };
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
// 地图跟随当前搜索与经营关注筛选展示全部匹配机构，分页只限制右侧列表。
const mapInstitutions = computed(() => filteredInstitutions.value);
const cityBranchRankingModel = computed(() => buildCityBranchRankingModel({
  cityCode: props.cityCode,
  institutions: filteredInstitutions.value,
  sourcePresentation: props.sourcePresentation,
  blockResults: safeModel.value.blockResults,
  activeTabKey: rankingTabKey.value
}));
const rankingTotalPages = computed(() => {
  const count = cityBranchRankingModel.value.rows.length || cityBranchRankingModel.value.missingRows.length;
  return Math.max(1, Math.ceil(count / pageSize));
});
const selectedBranch = computed(() => cityInstitutions.value.find(item => item.orgCode === selectedOrgCode.value) || null);
const cityInsights = computed(() => buildCityInsights({
  cityCode: props.cityCode,
  citySummary: citySummary.value,
  institutions: safeModel.value.institutions
}));
const selectedBranchInsight = computed(() => cityInsights.value.selected(selectedOrgCode.value));

function finiteValue(value) {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value === 'boolean' || (typeof value !== 'number' && typeof value !== 'string')) return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}
function formatMetric(value) {
  const number = finiteValue(value);
  if (number === null) return '—';
  if (number !== 0 && Math.abs(number) < 0.01) {
    return new Intl.NumberFormat('en-US', { maximumFractionDigits: 8, minimumFractionDigits: 4 }).format(number);
  }
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 2, minimumFractionDigits: Number.isInteger(number) ? 0 : 2 }).format(number);
}
function formatDisplayMetric(value, unit = '', key = '') {
  const number = finiteValue(value);
  const raw = value === null || value === undefined ? '' : String(value);
  if (number === null) return { text: '—', unit: unit || '', raw };
  const normalizedUnit = String(unit || '').trim();
  const customerMetric = normalizedUnit === '万户' || key === 'customers';
  const amountMetric = normalizedUnit === '亿元' || ['deposit', 'loan', 'revenue'].includes(key);
  if (customerMetric && number !== 0 && Math.abs(number) < 1) {
    const households = Math.round(number * 10000);
    return { text: households === 0 ? '<1' : new Intl.NumberFormat('en-US').format(households), unit: '户', raw };
  }
  if (amountMetric && normalizedUnit !== '%' && number !== 0 && Math.abs(number) < 1) {
    const wanYuan = number * 10000;
    const digits = Math.abs(wanYuan) >= 100 ? 2 : 4;
    return { text: new Intl.NumberFormat('en-US', { maximumFractionDigits: digits, minimumFractionDigits: digits }).format(wanYuan), unit: '万元', raw };
  }
  return { text: formatMetric(number), unit: normalizedUnit || (customerMetric ? '万户' : amountMetric ? '亿元' : ''), raw };
}
function displayCityKpi(kpi) {
  return formatDisplayMetric(kpi?.value, kpi?.unit || (kpi?.key === 'customers' ? '万户' : '亿元'), kpi?.key);
}
function displayCityMetric(value, unit, key) {
  return formatDisplayMetric(value, unit, key);
}
function metricTitle(value) {
  const number = finiteValue(value);
  return number === null ? '' : `原始值：${String(value)}`;
}
function formatPercent(value) {
  const number = finiteValue(value);
  return number === null ? '—' : `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 1, minimumFractionDigits: 1 }).format(number)}%`;
}
function signedMetricText(value) {
  const number = finiteValue(value);
  if (number === null) return '—';
  const prefix = number > 0 ? '+' : '';
  return `${prefix}${formatMetric(number)}`;
}
function signedClass(value) {
  const number = finiteValue(value);
  if (number === null) return 'is-muted';
  return number < 0 ? 'is-down' : 'is-up';
}
function trendClass(state) {
  return state === '连续下降' || state === '最新回落' ? 'is-down' : state === '最新回升' ? 'is-up' : 'is-muted';
}
function formatChange(value) {
  return finiteValue(value);
}
function hasMetric(value) {
  return finiteValue(value) !== null;
}
function changeClass(value) {
  const number = finiteValue(value);
  return number !== null && number < 0 ? 'is-down' : 'is-up';
}
function setSelectedBranch(orgCode) {
  const code = String(orgCode || '');
  if (!code || !cityInstitutions.value.some(item => item.orgCode === code)) return false;
  selectedOrgCode.value = code;
  detailExpanded.value = true;
  return true;
}

function selectBranch(orgCode) {
  const code = String(orgCode || '');
  if (!cityInstitutions.value.some(item => item.orgCode === code)) return;
  if (props.navigateOnBranchSelect) {
    emit('branch-select', code);
    return;
  }
  setSelectedBranch(code);
  navigationQuerySyncReady = true;
  void replaceNavigationQuery({ orgCode: code });
  emit('branch-select', code);
}
function selectCityBusinessLine(payload = {}) {
  const event = payload && typeof payload === 'object' && !Array.isArray(payload) ? { ...payload } : {};
  delete event.context;
  emit('business-line-select', {
    ...event,
    context: { cityCode: String(props.cityCode || ''), cityName: String(props.cityName || '') }
  });
}
function selectRankingTab(key) {
  rankingTabKey.value = String(key || 'retailDepositRate');
  page.value = 1;
}

function emitState() {
  const state = navigationStateSnapshot();
  if (props.navigateOnBranchSelect) {
    emit('state-change', state);
    return;
  }
  if (!navigationQuerySyncReady && JSON.stringify(state) === initialNavigationSnapshot) {
    emit('state-change', state);
    return;
  }
  navigationQuerySyncReady = true;
  void replaceNavigationQuery({ state });
  emit('state-change', state);
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
    setSelectedBranch(cityInstitutions.value[0].orgCode);
  }
  initialNavigationSnapshot = JSON.stringify(navigationStateSnapshot());
});
watch(cityInstitutions, list => {
  if (!list.length) return;
  if (!selectedOrgCode.value || !list.some(item => item.orgCode === selectedOrgCode.value)) {
    setSelectedBranch(list[0].orgCode);
    if (!navigationQuerySyncReady) initialNavigationSnapshot = JSON.stringify(navigationStateSnapshot());
  }
});
watch([search, attentionOnly], () => { page.value = 1; });
watch(rankingTotalPages, value => { if (page.value > value) page.value = value; });
watch([search, attentionOnly, sortDescending, page, selectedOrgCode, detailExpanded, rankingTabKey], emitState, { flush: 'post' });
</script>

<style scoped>
.city-header-amount-unit-control { display: inline-flex; align-items: center; }
.city-header-amount-unit-control select {
  min-width: 76px;
  min-height: 32px;
  padding: 4px 8px;
  border: 1px solid rgba(125, 161, 232, .22);
  border-radius: 5px;
  color: var(--panorama-text, #eaf2ff);
  background: var(--panorama-panel, rgba(8, 24, 61, .78));
  font: inherit;
  font-size: 13px;
  color-scheme: dark;
}

/* 城市右栏同时承载六个指标页签、搜索、完整分页和支行详情；让排名行按内容占位，
   详情自然落在排名下方，城市弹层本身负责纵向滚动。 */
@media (min-width: 1100px) {
  :global(.city-panorama .city-workspace) {
    grid-template-rows: minmax(420px, auto) minmax(360px, auto);
    height: auto;
    min-height: 0;
    align-items: stretch;
  }
  :global(.city-panorama .city-list-panel) {
    min-height: 420px;
    overflow: visible;
  }
  :global(.city-panorama .city-list-panel > .city-branch-ranking) {
    height: auto;
    min-height: 420px;
  }
  :global(.city-panorama .city-detail-panel) {
    min-height: 430px;
    overflow: visible;
  }
}
</style>

<style src="./panorama.scss" lang="scss"></style>
