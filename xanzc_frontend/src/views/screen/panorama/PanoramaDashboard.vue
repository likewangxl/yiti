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
        <h1>{{ displayTitle }}</h1>
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
          <strong class="panorama-kpi-value" :title="metricTitle(kpi.value)">{{ displayKpi(kpi).text }}</strong>
        <span v-if="displayKpi(kpi).unit" class="panorama-kpi-unit">{{ displayKpi(kpi).unit }}</span>
        <span class="panorama-visually-hidden">原始值 {{ displayKpi(kpi).raw }}</span>
        <small v-if="!hasMetric(kpi.value)" class="panorama-unbound-label" :data-testid="`kpi-status-${kpi.key}`">{{ sourceStatus('kpi', kpi.key).message }}</small>
        </div>
        <div v-if="formatChange(kpi.change) !== null" class="panorama-kpi-change" :class="changeClass(kpi.change)">
          {{ kpi.change >= 0 ? '↑' : '↓' }} {{ Math.abs(Number(kpi.change)).toFixed(1) }}%
          <small>较上月</small>
        </div>
      </article>
    </section>

    <section class="panorama-diagnostics-strip" data-testid="leadership-diagnostics" aria-label="经营诊断">
      <article
        v-for="card in diagnosticCards"
        :key="card.key"
        class="panorama-diagnostic-card"
        :data-diagnostic="card.key"
        :data-diagnostic-state="card.state"
      >
        <span class="panorama-diagnostic-label">{{ card.label }}</span>
        <strong :class="diagnosticValueClass(card)">{{ card.text }}</strong>
        <small>{{ card.note }}</small>
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
              <strong :class="{ 'is-muted': !hasMetric(item.value) }" :title="metricTitle(item.value)">{{ displayOperation(item).text }}</strong>
              <span v-if="displayOperation(item).unit" class="panorama-deposit-card-unit">{{ displayOperation(item).unit }}</span>
              <span class="panorama-visually-hidden">原始值 {{ displayOperation(item).raw }}</span>
              <small v-if="!hasMetric(item.value)" class="panorama-unbound-label" data-testid="deposit-operation-status">{{ sourceStatus(item.key, 'value').message }}<span class="panorama-visually-hidden">未绑定</span></small>
              <small v-else class="panorama-deposit-card-note">{{ item.note }}</small>
            </article>
          </div>
          <div class="panorama-mini-summary">
            <span><i class="is-cyan"></i>余额基准 {{ displayKpi(findKpi(safeModel.kpis, 'deposit')).text }} {{ displayKpi(findKpi(safeModel.kpis, 'deposit')).unit }}</span>
            <span>{{ displayDate }}</span>
          </div>
        </article>

        <article class="panorama-panel panorama-composition-panel">
          <div class="panorama-panel-heading">
            <div>
              <span class="panorama-section-kicker">业务结构</span>
              <h2>业务构成与占比</h2>
            </div>
            <span>{{ compositionHeadingMeta }}</span>
          </div>
          <CompositionBreakdown class="panorama-composition-content" :items="safeModel.composition" :source-status="sourceStatus('composition', 'value')" />
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
          <div v-else class="panorama-empty" data-testid="attention-status">{{ sourceStatus('attention', '').message }}</div>
        </article>
      </div>

      <div class="panorama-column panorama-center-column">
        <article class="panorama-panel panorama-map-panel">
          <div class="panorama-panel-heading">
            <div>
              <span class="panorama-section-kicker">机构视图</span>
              <h2>辖区机构分布</h2>
            </div>
            <div class="panorama-map-heading-actions">
              <span>{{ institutionCountLabel }}</span>
              <button
                type="button"
                class="panorama-directory-button"
                data-action="open-institution-directory"
                aria-label="打开机构目录"
                @click="directoryOpen = true"
              >机构目录</button>
            </div>
          </div>
          <div class="panorama-panel-subheading">
            <span class="panorama-scope-chip">{{ scopeLabel }}</span>
            <span class="panorama-legend-dot is-cyan" />行政区
            <span class="panorama-legend-dot is-violet" />机构分布
            <span class="panorama-map-hint-inline">点击城市查看下钻</span>
          </div>
          <PanoramaMap
            class="panorama-map"
            appearance="relief"
            label-layout="callout"
            :metric-label="rankingMetricInfo.label"
            :metric-values="provinceMapMetricValues"
            :city-details="provinceMapCityDetails"
            :data-metric-label="rankingMetricInfo.label"
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
              <span data-testid="selected-institution-metric">{{ selectedInstitutionMetric.label }} {{ selectedInstitutionMetric.text }} {{ selectedInstitutionMetric.unit }}</span>
          </div>
        </article>
        <PanoramaTrend :trend="safeModel.trend" :data-date="displayDate" title="主要指标趋势" switchable compact class="panorama-panel panorama-trend-panel" />
      </div>

      <div class="panorama-column panorama-right-column">
          <article class="panorama-panel panorama-leadership-panel">
            <div class="panorama-panel-heading panorama-leadership-heading">
              <div>
              <span class="panorama-section-kicker">经营分析</span>
              <h2>机构经营诊断与矩阵</h2>
            </div>
            <span>共 {{ leadershipTotalCount }} 家</span>
          </div>
          <article class="panorama-panel panorama-target-panel panorama-target-dual">
            <div class="panorama-target-heading"><span>目标进度</span><small>{{ targetPeriodLabel }}</small></div>
            <div class="panorama-target-cards" data-testid="target-cards">
              <article
                v-for="card in targetCards"
                :key="card.key"
                class="panorama-target-card"
                :data-target-key="card.key"
                data-testid="target-card"
              >
                <div class="panorama-target-card-heading">
                  <span>{{ card.label }}</span>
                  <small>{{ card.date }}</small>
                </div>
                <div class="panorama-target-summary panorama-target-water-summary">
                  <CompletionWaterGauge :value="card.value" :label="card.label" :variant="card.variant" />
                  <div class="panorama-target-water-copy">
                    <span class="panorama-target-summary-label">{{ card.label }}</span>
                    <strong class="panorama-target-distance">{{ card.hasData ? card.gapText : card.message }}</strong>
                    <small class="panorama-target-water-reference">目标基准 100% · 水位随完成率变化</small>
                  </div>
                </div>
              </article>
            </div>
          </article>
          <article class="panorama-panel panorama-ranking-panel panorama-ranking-detail-panel">
            <div class="panorama-panel-heading">
              <div><span class="panorama-section-kicker">机构经营矩阵</span><h2>全辖机构对比</h2></div>
              <span>TOP {{ Math.min(10, leadershipMatrixRows.length) }} / 共 {{ leadershipTotalCount }} 家</span>
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
            <div class="panorama-matrix-legend" aria-label="机构矩阵规则">
              <span><i class="is-up" />达标</span><span><i class="is-warning" />未达标</span><span><i class="is-muted" />未知</span>
              <small>目标差 = 存款余额 − 金额目标 · 其余机构请从机构目录查看</small>
            </div>
            <div class="panorama-detail-table-wrap">
            <table class="panorama-detail-table">
              <caption class="panorama-visually-hidden">当前指标前十机构经营矩阵，金额目标差、完成率和趋势均按已有绑定展示</caption>
              <thead><tr><th scope="col">机构</th><th scope="col">经营目标 / {{ rankingMetricInfo.label }}</th><th scope="col">余额 / 趋势</th></tr></thead>
              <tbody>
                <tr v-for="(item, index) in leadershipMatrixRows" :key="item.orgCode || index" data-testid="ranking-row" :data-org-code="item.orgCode || ''" tabindex="0" @click="selectRanking(item)" @keydown.enter="selectRanking(item)">
                  <td :title="item.name || item.orgName || '—'"><span class="panorama-matrix-rank">{{ rankingPosition(item) || '—' }}</span>{{ item.name || item.orgName || '—' }}</td>
                  <td>
                    <span :class="statusClass(item.status)">{{ statusLabel(item.status) }} {{ formatPercent(item.rate) }}</span>
                    <small>目标差 {{ formatSignedMetric(item.targetGap) }} · {{ formatRankingValue(item) }}</small>
                    <span class="panorama-matrix-bar" aria-hidden="true"><i :class="{ 'is-negative': rankingDisplayValue(item) !== null && rankingDisplayValue(item) < 0 }" :style="rankingBarStyle(item)"></i></span>
                  </td>
                  <td>
                    <strong>{{ formatMetric(item.deposit) }}</strong>
                    <small :class="item.trendIssue ? 'is-muted' : trendClass(item.trendState)" :title="item.trendIssue || ''">{{ item.trendIssue || item.trendState || '—' }}<template v-if="!item.trendIssue && item.trend?.change !== null"> · {{ formatSignedMetric(item.trend.change) }}</template></small>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!leadershipMatrixRows.length || (rankingMetric === 'increase' && !topRankings.length)" class="panorama-empty" data-testid="ranking-status">{{ rankingMetric === 'increase' ? sourceStatus('ranking', 'increase').message : '暂无机构经营矩阵绑定' }}</div>
            </div>
          </article>
        </article>
      </div>
    </section>

    <div
      v-if="cityOpen && selectedRegion"
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
        :initial-state="cityStateCache[selectedRegion?.code] || {}"
        :source-presentation="sourcePresentation"
        @close="closeCity"
        @back="closeCity"
        @refresh="emit('refresh')"
        @branch-select="selectInstitution"
        @state-change="saveCityState"
      />
    </div>

    <PanoramaInstitutionDirectory
      v-if="directoryOpen"
      :model="safeModel"
      @close="directoryOpen = false"
      @branch-select="selectInstitution"
    />
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import {
  Aim, Coin, Close, OfficeBuilding, Refresh, Setting, TrendCharts, UserFilled
} from '@element-plus/icons-vue';
import PanoramaMap from './PanoramaMap.vue';
import CityPanorama from './CityPanorama.vue';
import PanoramaInstitutionDirectory from './PanoramaInstitutionDirectory.vue';
import PanoramaTrend from './PanoramaTrend.vue';
import CompositionBreakdown from './CompositionBreakdown.vue';
import CompletionWaterGauge from '../components/CompletionWaterGauge.vue';
import { provinceGeo } from './geography.js';
import {
  RANKING_METRICS,
  coreKpis,
  findKpi,
  finiteMetric,
  rankingValue,
  topRankingRows
} from './panoramaViewModel.js';
import {
  buildProvinceInsights,
  statusLabel,
  summarizeDepositMovement,
  summarizeProvinceTargetStatus,
  summarizeTargetDistance
} from './leadershipInsights.js';
import { buildCityMapDetails } from './cityMapDetails.js';
import { resolveDataStatus } from './sourcePresentation';
import { buildTargetCards, stripTestModifier } from './targetPresentation.js';


const props = defineProps({
  model: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  demo: { type: Boolean, default: false },
  sourcePresentation: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['refresh', 'back', 'configure', 'branch-select']);

function sourceStatus(slot, semantic = '') {
  const mappedSlot = slot === 'kpi'
    ? ({ customers: 'customers', revenue: 'revenue', rate: 'rate', deposit: 'deposit', loan: 'loan' }[semantic] || semantic)
    : slot;
  const mappedSemantic = semantic;
  return resolveDataStatus(props.sourcePresentation, props.sourcePresentation?.runtimeIssues, mappedSlot, mappedSemantic);
}

const rootRef = ref(null);
const cityDialogRef = ref(null);
const cityOpen = ref(false);
const selectedRegion = ref(null);
const selectedRegionCode = ref('');
const selectedOrgCode = ref('');
const directoryOpen = ref(false);
const cityInitialOrgCode = ref('');
const cityStateCache = ref({});
const focusBeforeCity = ref(null);
const overflowBeforeCity = ref('');

function scopeSignature(model = {}) {
  const source = model && typeof model === 'object' ? model : {};
  return JSON.stringify([
    source.scopeCode,
    source.scopeId,
    source.scopeVersion,
    source.scopeKey,
    source.authorizedScope,
    source.permissionVersion,
    source.dataScopeKey,
    source.scopeLabel
  ].map(value => value == null ? '' : String(value)));
}
const lastScopeSignature = ref(`${scopeSignature(props.model)}|${props.sourcePresentation?.scopeIdentity || ''}`);

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
const displayTitle = computed(() => stripTestModifier(safeModel.value.title) || '分行经营总览');
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
const provinceMapCityDetails = computed(() => buildCityMapDetails(safeModel.value, {
  cityCodes: Array.isArray(provinceGeo?.features)
    ? provinceGeo.features.map(feature => feature?.properties?.adcode ?? feature?.properties?.cityCode)
    : []
}));
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
  const unassigned = safeModel.value.institutions.filter(item => !String(item?.cityCode || item?.city_code || '').trim()).length;
  const cityCount = new Set(safeModel.value.institutions
    .map(item => String(item?.cityCode || item?.city_code || '').trim())
    .filter(Boolean)).size;
  const locationLabel = unassigned ? `待确认归属 ${unassigned}家 / 已定位 ${located}家` : `已定位 ${located}家`;
  return `共 ${cityCount}个地市 / ${total}家机构 / ${locationLabel}`;
});
const targetCards = computed(() => buildTargetCards(safeModel.value));
const depositTargetCard = computed(() => targetCards.value[0]);
const targetMetricLabel = computed(() => depositTargetCard.value?.label || '零售存款目标完成率');
const targetPeriodLabel = computed(() => {
  const period = String(depositTargetCard.value?.period || '').trim();
  return ({ LATEST: '最新数据', LAST_10D: '近10天', LAST_1M: '近1个月', LAST_6M_EOM: '近6个月月末' }[period]
    || period
    || (depositTargetCard.value?.bound ? depositTargetCard.value.date : '')
    || '统计周期');
});
const rankingMetric = ref('deposit');
const rankingMetricOptions = RANKING_METRICS;
const rankingMetricInfo = computed(() => rankingMetricOptions.find(item => item.key === rankingMetric.value) || rankingMetricOptions[0]);
const provinceMapMetricValues = computed(() => {
  const values = {};
  const summaries = safeModel.value.citySummaries && typeof safeModel.value.citySummaries === 'object'
    ? safeModel.value.citySummaries
    : {};
  const summaryKeys = {
    deposit: ['deposit'],
    increase: ['depositIncrease', 'increase'],
    average: ['depositAverage', 'average']
  };
  Object.entries(summaries).forEach(([cityCode, summary]) => {
    const rows = Array.isArray(summary?.kpis) ? summary.kpis : [];
    const source = rows.find(item => summaryKeys[rankingMetric.value]?.includes(item?.key));
    const metric = finiteValue(source?.value);
    if (metric !== null) values[String(cityCode)] = formatRankingValueWithUnit({ [rankingMetric.value]: metric });
  });
  const candidates = new Map();
  safeModel.value.rankings.forEach(row => {
    const cityCode = String(row?.cityCode || row?.city_code || '').trim();
    const metric = rankingValue(row, rankingMetric.value);
    if (!cityCode || metric === null || values[cityCode] !== undefined) return;
    const rows = candidates.get(cityCode) || [];
    rows.push(metric);
    candidates.set(cityCode, rows);
  });
  candidates.forEach((rows, cityCode) => {
    // Several branch rows are ambiguous on the province map; never sum them.
    if (rows.length === 1) values[cityCode] = formatRankingValueWithUnit({ [rankingMetric.value]: rows[0] });
  });
  return values;
});
const topRankings = computed(() => topRankingRows(safeModel.value.rankings, rankingMetric.value, 10));
const rankingMax = computed(() => {
  const values = topRankings.value.map(item => Math.abs(rankingValue(item, rankingMetric.value) ?? 0));
  return Math.max(0, ...values);
});
const leadershipInsights = computed(() => buildProvinceInsights(safeModel.value));
const leadershipTotalCount = computed(() => safeModel.value.rankings.length || leadershipInsights.value.sampleSize);
const visibleRankingRows = computed(() => {
  if (safeModel.value.rankings.length) return topRankings.value;
  return topRankingRows(leadershipInsights.value.rows, rankingMetric.value, 10);
});
const underperformingRows = computed(() => leadershipInsights.value.rows.filter(row => row.status === 'below'));
const underperformingCountLabel = computed(() => leadershipInsights.value.coverage.rate.available
  ? `${underperformingRows.value.length}家`
  : '—');
const underperformingNames = computed(() => underperformingRows.value.slice(0, 3).map(row => row.name).join('、') || (
  leadershipInsights.value.coverage.rate.available ? '暂无未达标机构' : '完成率来源暂无有效值'
));
const coordinationSummary = computed(() => {
  if (!Array.isArray(props.model?.attention)) return { total: null, organizations: 0, names: '待跟进任务来源暂无数据' };
  const rows = safeModel.value.attention;
  const counts = rows.map(item => finiteValue(item?.count)).filter(value => value !== null && value >= 0);
  const total = counts.length ? counts.reduce((sum, value) => sum + value, 0) : null;
  const activeRows = rows.filter(item => {
    const count = finiteValue(item?.count);
    return count !== null && count > 0;
  });
  return {
    total,
    organizations: activeRows.length,
    names: activeRows.slice(0, 3).map(item => String(item?.label || '—')).join('、') || '当前返回没有待跟进任务'
  };
});
const coordinationCountLabel = computed(() => coordinationSummary.value.total === null
  ? '—'
  : `待跟进${formatMetric(coordinationSummary.value.total)}项`);
const coordinationNames = computed(() => coordinationSummary.value.total === null
  ? coordinationSummary.value.names
  : `涉及${coordinationSummary.value.organizations}家机构${coordinationSummary.value.names === '当前返回没有待跟进任务' ? '' : ` · ${coordinationSummary.value.names}`}`);
const selectedInstitutionMetric = computed(() => {
  const institution = selectedInstitution.value;
  const ranking = institution
    ? safeModel.value.rankings.find(item => String(item?.orgCode || '') === String(institution.orgCode || ''))
    : null;
  const raw = ranking?.[rankingMetric.value]
    ?? institution?.metrics?.[rankingMetric.value]
    ?? institution?.[rankingMetric.value]
    ?? null;
  return { label: rankingMetricInfo.value.label, ...formatDisplayMetric(raw, rankingMetricInfo.value.unit, rankingMetric.value) };
});
const diagnosticCards = computed(() => {
  const diagnostics = leadershipInsights.value.diagnostics;
  const movement = summarizeDepositMovement(diagnostics.depositIncrease);
  const distance = summarizeTargetDistance(diagnostics.targetRate);
  const targetSource = sourceStatus('rate', 'value');
  const targetStatus = summarizeProvinceTargetStatus(leadershipInsights.value);
  const targetStatusState = !targetStatus.hasData
    ? 'unknown'
    : leadershipInsights.value.statusCounts.below > 0
      ? 'below'
      : leadershipInsights.value.statusCounts.unknown > 0
        ? 'neutral'
        : 'achieved';
  const targetStatusDetail = targetStatus.hasData
    ? `已提供${targetMetricLabel.value}${leadershipInsights.value.coverage.rate.available}家 / 未提供${Math.max(0, leadershipInsights.value.coverage.rate.total - leadershipInsights.value.coverage.rate.available)}家`
    : targetStatus.detail;
  return [
    {
      key: 'targetDistance',
      label: '目标缺口',
      text: distance.state === 'unknown' && targetSource.status !== 'UNAVAILABLE' ? targetSource.message : distance.text,
      state: distance.state,
      note: `${movement.text} · ${targetMetricLabel.value}`
    },
    {
      key: 'targetStatus',
      label: '未达标机构',
      text: targetStatus.headline,
      state: targetStatusState,
      note: targetStatusDetail
    },
    {
      key: 'coordination',
      label: '协调事项',
      text: coordinationCountLabel.value === '—'
        ? sourceStatus('attention', '').message
        : coordinationSummary.value.total === 0 ? '暂无待跟进任务' : coordinationCountLabel.value,
      state: coordinationCountLabel.value === '—' ? 'unknown' : coordinationSummary.value.total === 0 ? 'neutral' : 'below',
      note: coordinationNames.value
    }
  ];
});
const leadershipMatrixRows = computed(() => {
  const rowsByCode = new Map(leadershipInsights.value.rows.map(item => [String(item.orgCode), item]));
  return visibleRankingRows.value.map((item, index) => {
    const insight = rowsByCode.get(String(item?.orgCode || '')) || {};
    const institution = safeModel.value.institutions.find(entry => String(entry?.orgCode || '') === String(item?.orgCode || '')) || {};
    return {
      ...item,
      ...insight,
      orgCode: item?.orgCode || insight.orgCode || `row-${index}`,
      name: item?.name || item?.orgName || insight.name || '—',
      deposit: insight.deposit ?? rankingValue(item, 'deposit'),
      increase: insight.increase ?? finiteValue(item?.increase),
      average: finiteValue(item?.average),
      targetGap: insight.targetGap ?? null,
      trend: insight.trend || { state: '无趋势数据', change: null },
      trendState: insight.trendState || '无趋势数据',
      trendIssue: institution.trendIssue || item?.trendIssue || '',
      status: insight.status || 'unknown',
      rate: insight.rate ?? null
    };
  });
});

function finiteValue(value) {
  if (typeof value === 'boolean' || (typeof value !== 'number' && typeof value !== 'string')) return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  return finiteMetric(value);
}

function hasMetric(value) {
  return finiteMetric(value) !== null;
}

function kpiLabel(key) {
  return {
    deposit: '存款余额',
    loan: '贷款余额',
    customers: '营销有效归属客户数',
    revenue: '手工测试收入',
    rate: '目标完成率'
  }[key] || '指标';
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
  const amountMetric = normalizedUnit === '亿元' || ['deposit', 'loan', 'revenue', 'depositIncrease', 'depositAverage', 'increase', 'average'].includes(key);
  if (customerMetric && number !== 0 && Math.abs(number) < 1) {
    const households = Math.round(number * 10000);
    return { text: households === 0 ? '<1' : new Intl.NumberFormat('en-US').format(households), unit: '户', raw };
  }
  if (amountMetric && normalizedUnit !== '%' && number !== 0 && Math.abs(number) < 1) {
    const wanYuan = number * 10000;
    const digits = Math.abs(wanYuan) >= 100 ? 2 : 4;
    return {
      text: new Intl.NumberFormat('en-US', { maximumFractionDigits: digits, minimumFractionDigits: digits }).format(wanYuan),
      unit: '万元',
      raw
    };
  }
  const inferredUnit = normalizedUnit || (customerMetric ? '万户' : amountMetric ? '亿元' : '');
  return { text: formatMetric(number), unit: inferredUnit, raw };
}

function displayKpi(kpi) {
  return formatDisplayMetric(kpi?.value, kpi?.unit || (kpi?.key === 'customers' ? '万户' : '亿元'), kpi?.key);
}

function displayOperation(item) {
  return formatDisplayMetric(item?.value, item?.unit || '亿元', item?.key);
}

function metricTitle(value) {
  const number = finiteValue(value);
  return number === null ? '' : `原始值：${String(value)}`;
}

function formatRankingValue(item) {
  return formatMetric(rankingDisplayValue(item));
}

function formatRankingValueWithUnit(item) {
  const display = formatDisplayMetric(rankingDisplayValue(item), rankingMetricInfo.value.unit, rankingMetric.value);
  return display.text === '—' ? '—' : `${display.text}${display.unit}`;
}

function formatPercent(value) {
  const number = finiteValue(value);
  return number === null ? '—' : `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 1, minimumFractionDigits: 1 }).format(number)}%`;
}

function formatSignedMetric(value) {
  const number = finiteValue(value);
  if (number === null) return '—';
  const prefix = number > 0 ? '+' : '';
  return `${prefix}${formatMetric(number)}`;
}

function diagnosticValueClass(card) {
  if (card.state === 'unknown') return 'is-muted';
  if (card.state === 'down' || card.state === 'below') return 'is-down';
  if (card.state === 'above' || card.state === 'up' || card.state === 'achieved') return 'is-up';
  return 'is-muted';
}

function statusClass(status) {
  return ({ achieved: 'is-up', below: 'is-warning', unknown: 'is-muted' })[status] || 'is-muted';
}

function trendClass(state) {
  return state === '连续下降' || state === '最新回落' ? 'is-down' : state === '最新回升' ? 'is-up' : 'is-muted';
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

function rankingDisplayValue(item) {
  return rankingValue(item, rankingMetric.value);
}

function rankingPosition(item) {
  const index = visibleRankingRows.value.findIndex(entry => String(entry?.orgCode || '') === String(item?.orgCode || ''));
  if (index === -1) return null;
  const currentValue = rankingValue(item, rankingMetric.value);
  if (currentValue === null) return null;
  let rank = 1;
  for (let cursor = 0; cursor < index; cursor += 1) {
    const previousValue = rankingValue(visibleRankingRows.value[cursor], rankingMetric.value);
    if (previousValue > currentValue) rank = cursor + 2;
  }
  return rank;
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
  cityInitialOrgCode.value = String(initialOrgCode || cityStateCache.value[code]?.selectedOrgCode || '');
  focusBeforeCity.value = document.activeElement;
  overflowBeforeCity.value = document.body.style.overflow;
  document.body.style.overflow = 'hidden';
  cityOpen.value = true;
  document.addEventListener('keydown', onDocumentEscape);
  await nextTick();
  cityDialogRef.value?.focus();
}

function saveCityState(state = {}) {
  const code = String(selectedRegion.value?.code || '');
  if (!code || !state || typeof state !== 'object') return;
  cityStateCache.value = { ...cityStateCache.value, [code]: { ...state } };
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
  cityInitialOrgCode.value = '';
  restoreCityState();
}

function clearScopeState() {
  closeCity();
  directoryOpen.value = false;
  selectedOrgCode.value = '';
  selectedRegionCode.value = '';
  selectedRegion.value = null;
  cityInitialOrgCode.value = '';
  cityStateCache.value = {};
}
watch(() => [props.error, props.loading, props.model, props.sourcePresentation?.scopeIdentity], ([error, loading, model, identity]) => {
  if (error) { clearScopeState(); return; }
  if (loading) return;
  const signature = `${scopeSignature(model)}|${identity || ''}`;
  if (lastScopeSignature.value !== signature) clearScopeState();
  lastScopeSignature.value = signature;
  const allowed = new Set(safeModel.value.institutions.map(item => String(item.orgCode)));
  const cities = new Set(safeModel.value.institutions.map(item => String(item.cityCode || '')));
  if (selectedOrgCode.value && !allowed.has(selectedOrgCode.value)) selectedOrgCode.value = '';
  if (selectedRegionCode.value && !cities.has(selectedRegionCode.value)) { closeCity(); selectedRegionCode.value=''; selectedRegion.value=null; }
  cityStateCache.value = Object.fromEntries(Object.entries(cityStateCache.value).filter(([city])=>cities.has(city)).map(([city,state])=>[
    city, {...state, selectedOrgCode: allowed.has(String(state.selectedOrgCode)) ? state.selectedOrgCode : ''}
  ]));
}, { deep: true });

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

<style scoped>
.panorama-map-heading-actions { display: flex; align-items: center; justify-content: flex-end; gap: 8px; }
.panorama-map-heading-actions > span { color: var(--panorama-text-dim); font-size: 11px; white-space: nowrap; }
.panorama-directory-button { padding: 5px 8px; border: 1px solid rgba(121, 161, 248, .3); border-radius: 5px; color: #bcd5ff; background: rgba(55, 112, 206, .22); font: inherit; font-size: 11px; cursor: pointer; }
.panorama-directory-button:hover,
.panorama-directory-button:focus-visible { border-color: var(--panorama-border-strong); color: var(--panorama-cyan); outline: none; }
</style>
