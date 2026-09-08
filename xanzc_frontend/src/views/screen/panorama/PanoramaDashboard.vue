<template>
  <main
    ref="rootRef"
    class="panorama-dashboard"
    tabindex="-1"
    @keydown.esc="closeCity"
  >
    <header class="panorama-header">
      <div class="panorama-breadcrumb">
        <span class="panorama-eyebrow">经营监测</span>
        <strong>{{ today }}</strong>
        <span>数据日期 {{ displayDate }}</span>
      </div>
      <div class="panorama-title-block">
        <h1>{{ safeModel.title || '分行经营总览' }}</h1>
        <span>{{ scopeLabel }}</span>
      </div>
      <div class="panorama-header-actions">
        <span class="panorama-live-state"><i :class="{ 'is-loading': loading, 'is-error': error }"></i>{{ loading ? '正在取数' : error ? '数据异常' : '经营监测' }}</span>
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
          <span class="panorama-kpi-label">{{ kpi.label || kpiLabel(kpi.key) }}</span>
          <strong class="panorama-kpi-value">{{ formatMetric(kpi.value) }}</strong>
          <span v-if="kpi.unit" class="panorama-kpi-unit">{{ kpi.unit }}</span>
        </div>
        <div v-if="formatChange(kpi.change) !== null" class="panorama-kpi-change" :class="changeClass(kpi.change)">
          {{ kpi.change >= 0 ? '↑' : '↓' }} {{ Math.abs(Number(kpi.change)).toFixed(1) }}%
          <small>较上月</small>
        </div>
      </article>
    </section>

    <section class="panorama-workspace">
      <div class="panorama-column panorama-left-column">
        <article class="panorama-panel panorama-deposit-panel">
          <div class="panorama-panel-heading">
            <div>
              <span class="panorama-section-kicker">存款经营</span>
              <h2>存款核心指标</h2>
            </div>
            <span>指标概览</span>
          </div>
          <div class="panorama-deposit-cards">
            <article
              v-for="item in depositOperationCards"
              :key="item.key"
              class="panorama-deposit-card"
              data-testid="deposit-operation-card"
            >
              <span class="panorama-deposit-card-label">{{ item.label }}</span>
              <strong :class="{ 'is-muted': !hasMetric(item.value) }">{{ formatMetric(item.value) }}</strong>
              <span v-if="item.unit" class="panorama-deposit-card-unit">{{ item.unit }}</span>
              <small v-if="!hasMetric(item.value)" class="panorama-unbound-label">未绑定</small>
              <small v-else class="panorama-deposit-card-note">{{ item.note }}</small>
            </article>
          </div>
          <div class="panorama-mini-summary">
            <span><i class="is-cyan"></i>余额基准 {{ formatMetric(findKpi(safeModel.kpis, 'deposit').value) }} 亿元</span>
            <span>{{ displayDate }}</span>
          </div>
        </article>

        <article class="panorama-panel panorama-composition-panel">
          <div class="panorama-panel-heading">
            <div>
              <span class="panorama-section-kicker">业务结构</span>
              <h2>业务构成与贡献</h2>
            </div>
            <span>{{ compositionHeadingMeta }}</span>
          </div>
          <div v-if="compositionItems.length" class="panorama-composition-layout">
            <v-chart class="panorama-composition-chart" :option="compositionOption" autoresize aria-label="业务结构占比图" />
            <div class="panorama-composition-list">
              <div v-for="(item, index) in compositionItems" :key="item.name || index" class="panorama-composition-row">
                <span class="panorama-composition-mark" :style="{ backgroundColor: compositionColor(index) }" />
                <div class="panorama-composition-copy">
                  <span class="panorama-composition-name">{{ item.name || '—' }}</span>
                  <span class="panorama-composition-bar"><i :style="{ width: `${compositionPercent(item.value)}%`, backgroundColor: compositionColor(index) }"></i></span>
                </div>
                <strong>{{ formatMetric(item.value) }}</strong>
                <small>{{ item.unit || '' }}</small>
              </div>
            </div>
          </div>
          <div v-else class="panorama-empty">暂无业务结构数据</div>
        </article>

        <article class="panorama-panel panorama-attention-panel">
          <div class="panorama-panel-heading">
            <div>
              <span class="panorama-section-kicker">执行提醒</span>
              <h2>流程与经营关注</h2>
            </div>
            <span>{{ safeModel.attention.length ? '需跟进' : '暂无数据' }}</span>
          </div>
          <ul v-if="safeModel.attention.length" class="panorama-attention-list">
            <li v-for="(item, index) in safeModel.attention" :key="item.label || index">
              <span class="panorama-attention-mark">!</span>
              <span>{{ item.label || '—' }}</span>
              <strong>{{ formatMetric(item.count) }}</strong>
            </li>
          </ul>
          <div v-else class="panorama-empty">暂无流程与经营关注数据</div>
        </article>
      </div>

      <div class="panorama-column panorama-center-column">
        <article class="panorama-panel panorama-map-panel">
          <div class="panorama-panel-heading">
            <div>
              <span class="panorama-section-kicker">机构视图</span>
              <h2>全辖机构分布</h2>
            </div>
            <span>{{ institutionCountLabel }}</span>
          </div>
          <div class="panorama-panel-subheading">
            <span class="panorama-scope-chip">{{ scopeLabel }}</span>
            <span class="panorama-legend-dot is-cyan" />行政区
            <span class="panorama-legend-dot is-violet" />机构分布
            <span class="panorama-map-hint-inline">点击城市查看下钻</span>
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
        </article>
        <PanoramaTrend :trend="safeModel.trend" :data-date="displayDate" title="主要指标趋势" switchable compact class="panorama-panel panorama-trend-panel" />
      </div>

      <div class="panorama-column panorama-right-column">
        <article class="panorama-panel panorama-ranking-panel">
          <div class="panorama-panel-heading">
            <div>
              <span class="panorama-section-kicker">机构梯队</span>
              <h2>机构{{ rankingMetricInfo.label }}排名</h2>
            </div>
            <span>TOP {{ Math.min(10, topRankings.length) }}</span>
          </div>
          <div class="panorama-panel-toolbar">
            <div class="panorama-segmented" role="group" aria-label="排名指标">
              <button
                v-for="metric in rankingMetricOptions"
                :key="metric.key"
                type="button"
                :data-ranking-mode="metric.key"
                :class="{ active: rankingMetric === metric.key }"
                @click="rankingMetric = metric.key"
              >{{ metric.label.replace('存款', '') }}</button>
            </div>
            <span class="panorama-unit-note">单位：{{ rankingMetricInfo.unit }}</span>
          </div>
          <ol v-if="topRankings.length" class="panorama-ranking-list">
            <li
              v-for="(item, index) in topRankings"
              :key="item.orgCode || index"
              :data-testid="'ranking-row'"
              :data-org-code="item.orgCode || ''"
              tabindex="0"
              @click="selectRanking(item)"
              @keydown.enter="selectRanking(item)"
            >
              <span class="panorama-rank-number">{{ index + 1 }}</span>
              <span class="panorama-rank-name">{{ item.name || item.orgName || '—' }}</span>
              <span class="panorama-rank-bar"><i :class="{ 'is-negative': rankingDisplayValue(item) < 0 }" :style="rankingBarStyle(item)"></i></span>
              <strong>{{ formatMetric(rankingDisplayValue(item)) }}</strong>
              <span v-if="rankingMetric === 'deposit' && formatChange(item.change) !== null" class="panorama-ranking-change" :class="changeClass(item.change)" title="余额较上期变化">{{ item.change >= 0 ? '↑' : '↓' }}{{ Math.abs(Number(item.change)).toFixed(1) }}%</span>
            </li>
          </ol>
          <div v-else class="panorama-empty">暂无机构排名绑定</div>
        </article>

        <article class="panorama-panel panorama-target-panel">
          <div class="panorama-panel-heading">
            <div>
              <span class="panorama-section-kicker">目标追踪</span>
              <h2>目标完成进度</h2>
            </div>
            <span>{{ targetPeriodLabel }}</span>
          </div>
          <div v-if="targetProgressActual !== null" class="panorama-target-content">
            <div class="panorama-target-ring" data-testid="target-progress-ring" :style="{ '--target-progress': `${targetProgressVisual}%` }">
              <strong data-testid="target-progress-value">{{ formatMetric(targetProgressActual) }}<small>%</small></strong>
            </div>
            <div class="panorama-target-meta">
              <span>完成情况</span>
              <small>{{ targetProgressActual > 100 ? '超额完成' : '目标进度' }}</small>
            </div>
            <div class="panorama-target-track" aria-hidden="true"><i :style="{ width: `${targetProgressVisual}%` }"></i></div>
          </div>
          <div v-else class="panorama-unbound" data-testid="target-unbound">目标完成率未绑定</div>
        </article>

        <article class="panorama-panel panorama-ranking-detail-panel">
          <div class="panorama-panel-heading">
            <div>
              <span class="panorama-section-kicker">数据明细</span>
              <h2>排名明细</h2>
            </div>
            <span>共 {{ safeModel.rankings.length }} 家</span>
          </div>
          <div class="panorama-detail-table-wrap">
            <table class="panorama-detail-table">
              <caption class="panorama-visually-hidden">机构排名明细，净增和月均余额单位：亿元</caption>
              <thead><tr><th scope="col">机构</th><th scope="col">净增<br>亿元</th><th scope="col">月均余额<br>亿元</th></tr></thead>
              <tbody>
                <tr v-for="(item, index) in rankingDetailRows" :key="item.orgCode || index" :data-testid="`ranking-detail-${item.orgCode || index}`">
                  <td :title="item.name || item.orgName || '—'">{{ item.name || item.orgName || '—' }}</td>
                  <td>{{ formatMetric(item.increase) }}</td>
                  <td>{{ formatMetric(item.average) }}</td>
                </tr>
              </tbody>
            </table>
            <div v-if="!rankingDetailRows.length" class="panorama-empty">暂无排名明细绑定</div>
          </div>
        </article>
      </div>
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
import {
  RANKING_METRICS,
  clampProgress,
  coreKpis,
  findKpi,
  finiteMetric,
  rankingValue,
  sortRankingRows,
  topRankingRows
} from './panoramaViewModel.js';

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

const safeModel = computed(() => {
  const source = props.model && typeof props.model === 'object' ? props.model : {};
  return {
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
    ...source,
    kpis: Array.isArray(source.kpis) ? source.kpis : [],
    trend: Array.isArray(source.trend) ? source.trend : [],
    composition: Array.isArray(source.composition) ? source.composition : [],
    rankings: Array.isArray(source.rankings) ? source.rankings : [],
    attention: Array.isArray(source.attention) ? source.attention : [],
    institutions: Array.isArray(source.institutions) ? source.institutions : [],
    issues: Array.isArray(source.issues) ? source.issues : [],
    citySummaries: source.citySummaries && typeof source.citySummaries === 'object' ? source.citySummaries : {}
  };
});
const kpiCards = computed(() => coreKpis(safeModel.value.kpis));
const depositOperationCards = computed(() => [
  { ...findKpi(safeModel.value.kpis, 'depositIncrease'), label: '较上月净增', note: '月度变动' },
  { ...findKpi(safeModel.value.kpis, 'depositAverage'), label: '月均余额', note: '周期平均' }
]);
const displayDate = computed(() => safeModel.value.dataDate || '—');
const scopeLabel = computed(() => String(
  safeModel.value.scopeLabel
    || safeModel.value.scopeName
    || safeModel.value.regionName
    || safeModel.value.orgScopeName
    || '全辖机构'
));
const today = computed(() => {
  const now = new Date();
  return `${now.getFullYear()}.${String(now.getMonth() + 1).padStart(2, '0')}.${String(now.getDate()).padStart(2, '0')}`;
});
const provinceGeoJson = provinceGeo || null;
const selectedInstitution = computed(() => safeModel.value.institutions.find(item => item?.orgCode === selectedOrgCode.value) || null);
const compositionItems = computed(() => safeModel.value.composition.filter(item => item && typeof item === 'object'));
const compositionHeadingMeta = computed(() => {
  const units = [...new Set(compositionItems.value.map(item => String(item?.unit || '').trim()).filter(Boolean))];
  if (!units.length) return '指标口径';
  if (units.length === 1 && units[0] === '%') return '比例构成';
  if (units.length === 1) return units[0];
  return '指标口径';
});
const institutionCountLabel = computed(() => {
  const located = safeModel.value.institutions.filter(item => item?.located && item?.lng != null && item?.lat != null).length;
  const total = safeModel.value.institutions.length;
  const cityCount = new Set(safeModel.value.institutions
    .map(item => String(item?.cityCode || '').trim())
    .filter(Boolean)).size;
  return `共 ${cityCount}个地市 / ${total}家机构 / 已定位 ${located}家`;
});
const targetKpi = computed(() => safeModel.value.kpis.find(item => [
  'rate', 'targetRate', 'completionRate', 'targetCompletionRate', 'target'
].includes(item?.key)) || null);
const targetProgressActual = computed(() => finiteMetric(targetKpi.value?.value));
const targetProgressVisual = computed(() => clampProgress(targetProgressActual.value) ?? 0);
const targetPeriodLabel = computed(() => {
  const period = String(targetKpi.value?.periodLabel || targetKpi.value?.periodName || targetKpi.value?.period || '').trim();
  return ({ LATEST: '最新数据', LAST_10D: '近10天', LAST_1M: '近1个月', LAST_6M_EOM: '近6个月月末' }[period] || period || '统计周期');
});
const rankingMetric = ref('deposit');
const rankingMetricOptions = RANKING_METRICS;
const rankingMetricInfo = computed(() => rankingMetricOptions.find(item => item.key === rankingMetric.value) || rankingMetricOptions[0]);
const rankingDetailRows = computed(() => sortRankingRows(safeModel.value.rankings, rankingMetric.value));
const topRankings = computed(() => topRankingRows(safeModel.value.rankings, rankingMetric.value, 10));
const rankingMax = computed(() => {
  const values = topRankings.value.map(item => Math.abs(rankingValue(item, rankingMetric.value) ?? 0));
  return Math.max(0, ...values);
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
  return finiteMetric(value);
}

function hasMetric(value) {
  return finiteMetric(value) !== null;
}

function kpiLabel(key) {
  return {
    deposit: '存款余额',
    loan: '贷款余额',
    customers: '客户总量',
    revenue: '营收',
    rate: '目标完成率'
  }[key] || '指标';
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

function rankingDisplayValue(item) {
  return rankingValue(item, rankingMetric.value);
}

function rankingBarStyle(item) {
  const value = rankingDisplayValue(item);
  if (value === null || rankingMax.value <= 0) return { width: '0%' };
  return { width: `${Math.min(100, (Math.abs(value) / rankingMax.value) * 100)}%` };
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
