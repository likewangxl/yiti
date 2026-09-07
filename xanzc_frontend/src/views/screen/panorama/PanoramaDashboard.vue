<template>
  <main
    ref="rootRef"
    class="panorama-dashboard"
    tabindex="-1"
    @keydown.esc="closeCity"
  >
    <header class="panorama-header">
      <div class="panorama-breadcrumb">
        <span>经营监测</span>
        <strong>{{ today }}</strong>
        <span>数据日期 {{ displayDate }}</span>
      </div>
      <div class="panorama-title-block">
        <h1>{{ safeModel.title || '分行经营总览' }}</h1>
        <span>陕西省分行</span>
      </div>
      <div class="panorama-header-actions">
        <button
          type="button"
          class="panorama-icon-action"
          data-action="refresh"
          aria-label="刷新大屏"
          title="刷新"
          @click="emit('refresh')"
        ><component :is="Refresh" /></button>
        <button
          v-if="!demo"
          type="button"
          class="panorama-icon-action"
          data-action="configure"
          aria-label="配置大屏"
          title="配置"
          @click="emit('configure')"
        ><component :is="Setting" /></button>
        <button
          type="button"
          class="panorama-icon-action"
          data-action="back"
          aria-label="返回"
          title="返回"
          @click="emit('back')"
        ><component :is="Close" /></button>
      </div>
    </header>

    <div v-if="demo" class="panorama-demo-badge" data-testid="panorama-demo-badge">演示数据 · 仅视觉预览</div>
    <div v-if="loading" class="panorama-loading" role="status">加载中…</div>
    <div v-if="error" class="panorama-error" role="alert">{{ error }}</div>

    <section class="panorama-kpi-grid" aria-label="核心指标">
      <article v-for="(kpi, index) in kpiCards" :key="kpi.key || index" class="panorama-kpi" data-testid="panorama-kpi">
        <div class="panorama-kpi-icon" aria-hidden="true"><component :is="kpiIcon(kpi.key, index)" /></div>
        <div class="panorama-kpi-body">
          <span class="panorama-kpi-label">{{ kpi.label || '指标' }}</span>
          <strong class="panorama-kpi-value">{{ formatMetric(kpi.value) }}</strong>
          <span v-if="kpi.unit" class="panorama-kpi-unit">{{ kpi.unit }}</span>
        </div>
        <div v-if="formatChange(kpi.change) !== null" class="panorama-kpi-change" :class="changeClass(kpi.change)">
          {{ kpi.change >= 0 ? '↑' : '↓' }} {{ Math.abs(Number(kpi.change)).toFixed(1) }}%
          <small>较上月</small>
        </div>
      </article>
    </section>

    <section class="panorama-main-grid">
      <article class="panorama-panel panorama-map-panel">
        <div class="panorama-panel-heading">
          <h2>陕西省分行机构分布</h2>
          <span>{{ institutionCountLabel }}</span>
        </div>
        <div class="panorama-panel-subheading">
          <span class="panorama-legend-dot is-cyan" />行政区
          <span class="panorama-legend-dot is-violet" />机构分布
        </div>
        <PanoramaMap
          class="panorama-map"
          :geo-json="provinceGeoJson"
          :points="safeModel.institutions"
          :demo="demo"
          :selected-org-code="selectedOrgCode"
          mode="province"
          :selected-region-code="selectedRegionCode"
          @region-select="openCity"
          @branch-select="selectInstitution"
        />
        <div v-if="selectedInstitution" class="panorama-selected-institution" data-testid="selected-institution">
          <div>
            <small>当前机构</small>
            <strong>{{ selectedInstitution.orgName || selectedInstitution.orgCode }}</strong>
          </div>
          <span>{{ formatMetric(selectedInstitution.metrics?.deposit) }} 亿元</span>
        </div>
        <div v-else class="panorama-map-hint">点击地图行政区查看市级经营全景</div>
      </article>

      <article class="panorama-panel panorama-composition-panel">
        <div class="panorama-panel-heading">
          <h2>业务结构与贡献</h2>
          <span>金额占比</span>
        </div>
        <div v-if="compositionItems.length" class="panorama-composition-layout">
          <v-chart class="panorama-composition-chart" :option="compositionOption" autoresize aria-label="业务结构占比图" />
          <div class="panorama-composition-list">
            <div v-for="(item, index) in compositionItems" :key="item.name || index" class="panorama-composition-row">
              <span class="panorama-composition-mark" :style="{ backgroundColor: compositionColor(index) }" />
              <span class="panorama-composition-name">{{ item.name || '—' }}</span>
              <strong>{{ formatMetric(item.value) }}</strong>
              <small>{{ item.unit || '' }}</small>
            </div>
          </div>
        </div>
        <div v-else class="panorama-empty">暂无业务结构数据</div>
      </article>

      <article class="panorama-panel panorama-target-panel">
        <div class="panorama-panel-heading">
          <h2>目标完成进度</h2>
          <span v-if="targetProgress !== null">年度目标</span>
        </div>
        <div v-if="targetProgress !== null" class="panorama-target-content">
          <div class="panorama-target-ring" :style="{ '--target-progress': `${targetProgress}%` }">
            <strong>{{ formatMetric(targetProgress) }}<small>%</small></strong>
            <span>年度目标完成率</span>
          </div>
          <div class="panorama-target-meta">
            <span>目标完成率</span>
            <strong>{{ formatMetric(targetProgress) }}%</strong>
          </div>
        </div>
        <div v-else class="panorama-unbound" data-testid="target-unbound">目标完成总体 KPI 未绑定</div>
      </article>
    </section>

    <section class="panorama-lower-grid">
      <PanoramaTrend :trend="safeModel.trend" title="主要指标趋势" class="panorama-panel panorama-trend-panel" />
      <article class="panorama-panel panorama-ranking-panel">
        <div class="panorama-panel-heading">
          <h2>机构存款余额排名</h2>
          <span>按存款余额</span>
        </div>
        <ol v-if="safeModel.rankings.length" class="panorama-ranking-list">
          <li v-for="(item, index) in safeModel.rankings.slice(0, 5)" :key="item.orgCode || index" tabindex="0" @click="selectRanking(item)" @keydown.enter="selectRanking(item)">
            <span class="panorama-rank-number">{{ index + 1 }}</span>
            <span class="panorama-rank-name">{{ item.name || item.orgName || '—' }}</span>
            <strong>{{ formatMetric(item.deposit) }}</strong>
            <span v-if="formatChange(item.change) !== null" :class="changeClass(item.change)">{{ item.change >= 0 ? '↑' : '↓' }}{{ Math.abs(Number(item.change)).toFixed(1) }}%</span>
          </li>
        </ol>
        <div v-else class="panorama-empty">暂无机构排名绑定</div>
      </article>
      <article class="panorama-panel panorama-attention-panel">
        <div class="panorama-panel-heading">
          <h2>经营关注</h2>
          <span>需跟进</span>
        </div>
        <ul v-if="safeModel.attention.length" class="panorama-attention-list">
          <li v-for="(item, index) in safeModel.attention.slice(0, 6)" :key="item.label || index">
            <span class="panorama-attention-mark">!</span>
            <span>{{ item.label || '—' }}</span>
            <strong>{{ formatMetric(item.count) }}</strong>
          </li>
        </ul>
        <div v-else class="panorama-empty">暂无经营关注事项</div>
      </article>
    </section>

    <div
      v-if="cityOpen"
      ref="cityDialogRef"
      class="panorama-city-modal"
      data-testid="city-panorama-modal"
      role="dialog"
      aria-modal="true"
      :aria-label="`${selectedRegion?.name || ''}市级经营全景`"
      tabindex="-1"
      @click.self="closeCity"
      @keydown="onCityDialogKeydown"
    >
      <CityPanorama
        :model="safeModel"
        :demo="demo"
        :city-code="selectedRegion?.code || ''"
        :city-name="selectedRegion?.name || ''"
        :initial-org-code="cityInitialOrgCode"
        @close="closeCity"
        @back="closeCity"
        @refresh="emit('refresh')"
        @branch-select="selectInstitution"
      />
    </div>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { PieChart } from 'echarts/charts';
import { LegendComponent, TooltipComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import {
  Aim, Coin, Close, OfficeBuilding, Refresh, Setting, TrendCharts, UserFilled
} from '@element-plus/icons-vue';
import PanoramaMap from './PanoramaMap.vue';
import CityPanorama from './CityPanorama.vue';
import PanoramaTrend from './PanoramaTrend.vue';
import { provinceGeo } from './geography.js';

use([CanvasRenderer, PieChart, TooltipComponent, LegendComponent]);

const props = defineProps({
  model: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  demo: { type: Boolean, default: false }
});
const emit = defineEmits(['refresh', 'back', 'configure', 'branch-select']);

const rootRef = ref(null);
const cityDialogRef = ref(null);
const cityOpen = ref(false);
const selectedRegion = ref(null);
const selectedRegionCode = ref('');
const selectedOrgCode = ref('');
const cityInitialOrgCode = ref('');
const focusBeforeCity = ref(null);
const overflowBeforeCity = ref('');

const safeModel = computed(() => ({
  title: '',
  dataDate: '',
  kpis: [],
  trend: [],
  composition: [],
  rankings: [],
  attention: [],
  institutions: [],
  issues: [],
  citySummaries: {},
  ...(props.model && typeof props.model === 'object' ? props.model : {})
}));
const kpiCards = computed(() => safeModel.value.kpis.slice(0, 4));
const displayDate = computed(() => safeModel.value.dataDate || '—');
const today = computed(() => {
  const now = new Date();
  return `${now.getFullYear()}.${String(now.getMonth() + 1).padStart(2, '0')}.${String(now.getDate()).padStart(2, '0')}`;
});
const provinceGeoJson = provinceGeo || null;
const selectedInstitution = computed(() => safeModel.value.institutions.find(item => item?.orgCode === selectedOrgCode.value) || null);
const compositionItems = computed(() => safeModel.value.composition.filter(item => item && typeof item === 'object'));
const institutionCountLabel = computed(() => {
  const located = safeModel.value.institutions.filter(item => item?.located && item?.lng != null && item?.lat != null).length;
  const total = safeModel.value.institutions.length;
  const cityCount = new Set(safeModel.value.institutions
    .map(item => String(item?.cityCode || '').trim())
    .filter(Boolean)).size;
  return `共 ${cityCount}个地市 / ${total}家机构 / 已定位 ${located}家`;
});
const targetProgress = computed(() => {
  const kpi = safeModel.value.kpis.find(item => ['target', 'targetRate', 'completionRate', 'rate', 'targetCompletionRate'].includes(item?.key));
  if (!kpi || kpi.value === null || kpi.value === undefined || kpi.value === '') return null;
  const value = Number(kpi.value);
  return Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : null;
});
const compositionChartData = computed(() => compositionItems.value
  .map((item, index) => ({ name: item.name || `业务${index + 1}`, value: finiteValue(item.value), itemStyle: { color: compositionColor(index) } }))
  .filter(item => item.value !== null));
const compositionOption = computed(() => ({
  animation: true,
  tooltip: { trigger: 'item', formatter: '{b}: {c}' },
  legend: { show: false },
  series: [{
    type: 'pie',
    radius: ['47%', '74%'],
    center: ['50%', '50%'],
    label: { show: false },
    labelLine: { show: false },
    itemStyle: { borderColor: '#07183e', borderWidth: 2 },
    data: compositionChartData.value
  }]
}));

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

function formatChange(value) {
  const number = finiteValue(value);
  return number === null ? null : number;
}

function changeClass(value) {
  const number = finiteValue(value);
  return number !== null && number < 0 ? 'is-down' : 'is-up';
}

function kpiIcon(key, index) {
  const icons = { deposit: OfficeBuilding, loan: Coin, customers: UserFilled, revenue: TrendCharts, target: Aim };
  return icons[key] || [OfficeBuilding, Coin, UserFilled, TrendCharts][index % 4];
}

function compositionColor(index) {
  return ['#38d9ef', '#8559e6', '#4c86ff', '#f6b849'][index % 4];
}

function compositionPercent(value) {
  const values = compositionItems.value.map(item => finiteValue(item.value)).filter(item => item !== null);
  const total = values.reduce((sum, item) => sum + item, 0);
  const number = finiteValue(value);
  if (total <= 0 || number === null) return 0;
  return Math.max(0, Math.min(100, (number / total) * 100));
}

function selectInstitution(orgCode) {
  const code = String(orgCode || '');
  if (!code || !safeModel.value.institutions.some(item => item?.orgCode === code)) return;
  selectedOrgCode.value = code;
  emit('branch-select', code);
}

function selectRanking(item) {
  const code = String(item?.orgCode || '');
  const institution = safeModel.value.institutions.find(entry => entry?.orgCode === code);
  if (institution) selectInstitution(code);
  const cityCode = item?.cityCode || institution?.cityCode;
  if (cityCode) {
    openCity({
      code: cityCode,
      name: item?.cityName || institution?.cityName || item?.name || cityCode
    }, institution ? code : '');
  }
}

async function openCity(region, initialOrgCode = '') {
  const code = String(region?.code || '');
  if (!code) return;
  selectedRegion.value = { code, name: String(region?.name || code) };
  selectedRegionCode.value = code;
  cityInitialOrgCode.value = String(initialOrgCode || '');
  focusBeforeCity.value = document.activeElement;
  overflowBeforeCity.value = document.body.style.overflow;
  document.body.style.overflow = 'hidden';
  cityOpen.value = true;
  document.addEventListener('keydown', onDocumentEscape);
  await nextTick();
  cityDialogRef.value?.focus();
}

function restoreCityState() {
  document.removeEventListener('keydown', onDocumentEscape);
  document.body.style.overflow = overflowBeforeCity.value;
  const target = focusBeforeCity.value;
  focusBeforeCity.value = null;
  if (target && typeof target.focus === 'function') target.focus();
}

function closeCity() {
  if (!cityOpen.value) return;
  cityOpen.value = false;
  selectedRegion.value = null;
  selectedRegionCode.value = '';
  cityInitialOrgCode.value = '';
  restoreCityState();
}

// 分页按钮被禁用时浏览器可能把焦点移到 body，Escape 仍应关闭当前模态层。
function onDocumentEscape(event) {
  if (cityOpen.value && event.key === 'Escape') {
    event.preventDefault();
    closeCity();
  }
}

function onCityDialogKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault();
    closeCity();
    return;
  }
  if (event.key !== 'Tab') return;
  const container = cityDialogRef.value;
  const focusables = container
    ? [...container.querySelectorAll('button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])')]
    : [];
  if (!focusables.length) {
    event.preventDefault();
    container?.focus();
    return;
  }
  const first = focusables[0];
  const last = focusables[focusables.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

onBeforeUnmount(() => {
  if (cityOpen.value) restoreCityState();
});
</script>

<style src="./panorama.scss" lang="scss"></style>
