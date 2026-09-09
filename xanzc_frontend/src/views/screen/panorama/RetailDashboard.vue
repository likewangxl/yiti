<template>
  <main
    ref="rootRef"
    class="retail-dashboard"
    tabindex="-1"
    aria-label="零售经营总览"
    @keydown.esc="closeDirectory"
  >
    <header class="retail-header">
      <div class="retail-header__context">
        <span class="retail-eyebrow">零售经营监测</span>
        <strong>{{ today }}</strong>
        <span>数据日期 {{ displayDate }}</span>
      </div>
      <div class="retail-header__title">
        <span class="retail-title-kicker">Retail overview</span>
        <h1>{{ safeModel.title || '零售经营总览' }}</h1>
        <span>{{ safeModel.scopeLabel || '当前大屏授权范围' }}</span>
      </div>
      <div class="retail-header__actions">
        <span class="retail-live-state">
          <i :class="{ 'is-loading': loading, 'is-error': error }"></i>
          {{ loading ? '正在取数' : error ? '数据异常' : '经营监测' }}
        </span>
        <button type="button" class="retail-icon-action" data-action="refresh" aria-label="刷新零售大屏" title="刷新" @click="emit('refresh')"><component :is="Refresh" /></button>
        <button v-if="!demo" type="button" class="retail-icon-action" data-action="configure" aria-label="配置零售大屏" title="配置" @click="emit('configure')"><component :is="Setting" /></button>
        <button type="button" class="retail-icon-action" data-action="back" aria-label="返回分行预览" title="返回" @click="emit('back')"><component :is="Close" /></button>
      </div>
    </header>

    <div v-if="demo" class="retail-demo-badge" data-testid="retail-demo-badge">本地演示 · 非业务数据</div>
    <div v-if="loading" class="retail-loading" role="status">加载中…</div>
    <div v-if="error" class="retail-error" role="alert">{{ error }}</div>

    <section class="retail-kpi-grid" aria-label="零售核心指标">
      <article
        v-for="(kpi, index) in kpiCards"
        :key="kpi.key"
        class="retail-kpi"
        :data-testid="'retail-kpi'"
        :data-kpi-key="kpi.key"
        :title="kpiTitle(kpi)"
      >
        <span class="retail-kpi__glyph" aria-hidden="true"><component :is="kpiGlyph(kpi.key, index)" /></span>
        <div class="retail-kpi__copy">
          <span class="retail-kpi__label">{{ kpi.label }}</span>
          <div class="retail-kpi__number">
            <strong>{{ formatMetric(kpi.value) }}</strong>
            <small>{{ kpi.unit }}</small>
          </div>
        </div>
        <span
          v-if="formatChange(kpi.change) !== null"
          class="retail-kpi__change"
          :class="changeClass(kpi)"
        >
          {{ changeText(kpi) }}
          <small>较上期</small>
        </span>
      </article>
    </section>

    <section class="retail-insight-strip" data-testid="retail-leadership-insights" aria-label="经营观察">
      <div class="retail-insight-item">
        <span>负增机构</span>
        <strong>{{ growthCountLabel }}</strong>
        <small>{{ growthSampleLabel }}</small>
      </div>
      <div class="retail-insight-item">
        <span>已达标目标</span>
        <strong>{{ insightCount(leadershipInsights.achievedTargetCount) }}</strong>
        <small>有效目标 {{ insightCount(leadershipInsights.validTargetCount) }} 项</small>
      </div>
      <div class="retail-insight-item">
        <span>缺指标</span>
        <strong>{{ insightCount(leadershipInsights.missingMetricCount) }}</strong>
        <small>排名矩阵空值单元</small>
      </div>
      <div class="retail-insight-item retail-insight-item--wide">
        <span>目标有效数 / 缺口</span>
        <strong>{{ targetValidityLabel }}</strong>
        <small>{{ targetGapLabel }}</small>
      </div>
    </section>

    <section class="retail-main-grid">
      <div class="retail-column retail-column--left">
        <article class="retail-panel retail-savings-panel">
          <header class="retail-panel__heading">
            <div>
              <span class="retail-kicker">储蓄经营</span>
              <h2>储蓄核心指标</h2>
            </div>
            <span>月度口径</span>
          </header>
          <div class="retail-savings__body retail-scroll-region" tabindex="0" role="region" aria-label="储蓄核心指标内容">
            <div class="retail-savings__cards">
              <div class="retail-data-card" data-testid="retail-deposit-balance">
                <span>储蓄余额</span>
                <strong :class="{ 'is-empty': !hasValue(depositKpi?.value) }">{{ formatMetric(depositKpi?.value) }}</strong>
                <small>时点余额 · 亿元</small>
              </div>
              <div class="retail-data-card" data-testid="retail-deposit-average">
                <span>月日均余额</span>
                <strong :class="{ 'is-empty': !hasValue(depositAverage?.value) }">{{ formatMetric(depositAverage?.value) }}</strong>
                <small v-if="hasValue(depositAverage?.value)">月内日均 · 亿元</small>
                <small v-else>未绑定 · 亿元</small>
              </div>
            </div>
            <div class="retail-savings__footer">
              <span class="retail-savings__signal"><i></i>余额较上期变化</span>
              <strong data-testid="retail-deposit-change" :class="changeClass(depositKpi)">{{ changeText(depositKpi, '—') }}</strong>
            </div>
          </div>
        </article>

        <article class="retail-panel retail-segments-panel" data-testid="retail-segments">
          <header class="retail-panel__heading">
            <div>
              <span class="retail-kicker">客户结构</span>
              <h2>客户分层与资产</h2>
            </div>
            <span>万户 / 亿元 · 万元/户</span>
          </header>
          <p class="retail-panel__note retail-segment-scope-note" data-testid="retail-segment-scope-note" title="客户占比与资产占比仅使用客户数和AUM均已提供且非负的同一分层分母">
            分层内占比 · {{ segmentCoverageLabel }}，非全客群（分层口径以业务定义为准）
          </p>
          <div v-if="segmentComparisons.length" class="retail-segment-list retail-scroll-region" tabindex="0" aria-label="客户分层列表">
            <div class="retail-segment-compare-head" aria-hidden="true">
              <span>客户占比 / 资产占比</span>
              <span>客户数</span>
              <span>AUM</span>
              <span>户均AUM</span>
            </div>
            <div v-for="(segment, index) in segmentComparisons" :key="`${segment.name || 'segment'}-${index}`" class="retail-segment-row" data-testid="retail-segment-row">
              <div class="retail-segment-row__identity">
                <i :style="{ backgroundColor: segmentColor(index) }"></i>
                <span>{{ segment.name || '—' }}</span>
              </div>
              <div class="retail-segment-row__comparison" aria-label="客户占比与资产占比">
                <span class="retail-share retail-share--customers">
                  <em>客</em>
                  <i aria-hidden="true"><b :style="{ width: `${shareWidth(segment.customerShare)}%` }"></b></i>
                  <strong>{{ formatPercent(segment.customerShare) }}</strong>
                </span>
                <span class="retail-share retail-share--aum">
                  <em>资</em>
                  <i aria-hidden="true"><b :style="{ width: `${shareWidth(segment.aumShare)}%` }"></b></i>
                  <strong>{{ formatPercent(segment.aumShare) }}</strong>
                </span>
              </div>
              <strong>{{ formatMetric(segment.customers) }}</strong>
              <small>{{ formatMetric(segment.aum) }}</small>
              <em class="retail-segment-row__average">{{ formatMetric(segment.averageAum) }}</em>
            </div>
          </div>
          <div v-else class="retail-empty">暂无客户分层数据</div>
        </article>

        <article class="retail-panel retail-attention-panel" data-testid="retail-attention">
          <header class="retail-panel__heading">
            <div>
              <span class="retail-kicker">经营关注</span>
              <h2>需要协调的事项</h2>
            </div>
            <span>{{ safeModel.attention.length ? `${safeModel.attention.length} 条` : '暂无数据' }}</span>
          </header>
          <p class="retail-panel__note">责任归属与跟进时限</p>
          <ul v-if="safeModel.attention.length" class="retail-attention-list retail-scroll-region" tabindex="0" aria-label="经营关注事项">
            <li v-for="(item, index) in safeModel.attention" :key="`${item.label || 'attention'}-${index}`">
              <span class="retail-attention-list__mark">!</span>
              <div class="retail-attention-list__main">
                <strong>{{ item.label || '—' }}</strong>
                <small>责任 {{ item.owner || '未提供' }} · 期限 {{ item.deadline || '未提供' }}</small>
              </div>
              <b>{{ formatMetric(item.count) }}</b>
            </li>
          </ul>
          <div v-else class="retail-empty">暂无来源已确认事项</div>
        </article>
      </div>

      <div class="retail-column retail-column--center">
        <article class="retail-panel retail-map-panel">
          <header class="retail-panel__heading retail-map-panel__heading">
            <div>
              <span class="retail-kicker">机构视图</span>
              <h2>陕西省机构分布</h2>
            </div>
            <div class="retail-map-panel__meta">
              <span>{{ institutionCountLabel }}</span>
              <button type="button" class="retail-directory-button" data-action="open-retail-directory" @click="openDirectory()">机构目录</button>
            </div>
          </header>
          <div class="retail-map-toolbar">
            <span class="retail-scope-chip">{{ selectedCityName || '全辖机构' }}</span>
            <span class="retail-map-legend"><i class="is-cyan"></i>行政区</span>
            <span class="retail-map-legend"><i class="is-violet"></i>城市选择</span>
            <button v-if="selectedCityCode" type="button" class="retail-clear-city" data-action="clear-city" @click="clearCity">显示全部机构</button>
            <span v-else class="retail-map-hint">点击城市筛选机构排名</span>
          </div>
          <PanoramaMap
            class="retail-map"
            :geo-json="provinceGeoJson"
            :points="safeModel.institutions"
            :demo="demo"
            mode="province"
            :selected-region-code="selectedCityCode"
            @region-select="selectCity"
          />
          <p class="retail-scope-note" data-testid="retail-scope-note">{{ safeModel.scopeLabel }} KPI 与趋势不随城市筛选变化；城市选择只影响机构分析。</p>
          <p v-if="selectedCityCode" class="retail-selected-city" data-testid="retail-selected-city">当前机构分析：{{ selectedCityName }}（{{ filteredRankings.length }} 家有排名记录）</p>
        </article>

        <RetailTrend class="retail-panel retail-trend-panel" :trend="safeModel.trend" :data-date="displayDate" :scope-label="safeModel.scopeLabel" />
      </div>

      <div class="retail-column retail-column--right">
        <article class="retail-panel retail-ranking-panel">
          <header class="retail-panel__heading">
            <div>
              <span class="retail-kicker">机构贡献 / 短板</span>
              <h2>{{ rankingOrder === 'leading' ? '领先机构排名' : '短板机构排名' }}</h2>
            </div>
            <span>按同口径机构</span>
          </header>
          <div class="retail-ranking-toolbar">
            <div class="retail-segmented" role="group" aria-label="机构排名指标">
              <button v-for="option in rankingMetricOptions" :key="option.key" type="button" :data-ranking-metric="option.key" :class="{ active: rankingMetric === option.key }" @click="rankingMetric = option.key">{{ option.label }}</button>
            </div>
            <div class="retail-segmented retail-segmented--order" role="group" aria-label="排名顺序">
              <button type="button" data-ranking-order="leading" :class="{ active: rankingOrder === 'leading' }" @click="rankingOrder = 'leading'">领先</button>
              <button type="button" data-ranking-order="lagging" :class="{ active: rankingOrder === 'lagging' }" @click="rankingOrder = 'lagging'">短板</button>
            </div>
            <span class="retail-ranking-unit" data-testid="retail-ranking-unit">单位：{{ rankingMetricInfo.unit }}</span>
          </div>
          <div v-if="filteredRankings.length" class="retail-ranking-matrix-head" data-testid="retail-ranking-matrix-head" aria-hidden="true">
            <span aria-hidden="true"></span>
            <span>机构</span>
            <span>当前排序值<small>{{ rankingMetricInfo.label }} · {{ rankingMetricInfo.unit }}</small></span>
            <span>AUM<small>亿元</small></span>
            <span>净增<small>亿元</small></span>
            <span>完成率<small>%</small></span>
            <span>不良率<small>%</small></span>
          </div>
          <ol v-if="filteredRankings.length" class="retail-ranking-list retail-scroll-region" tabindex="0" aria-label="机构排名列表">
            <li
              v-for="(item, index) in filteredRankings"
              :key="item.orgCode || `${item.name}-${index}`"
              class="retail-ranking-row"
              data-testid="retail-ranking-row"
              :data-org-code="item.orgCode || ''"
              tabindex="0"
              @click="openInstitution(item)"
              @keydown.enter="openInstitution(item)"
              @keydown.space.prevent="openInstitution(item)"
            >
              <span class="retail-ranking-row__number">{{ rankingRank(item, index) ?? '—' }}</span>
              <span class="retail-ranking-row__name">{{ item.name || item.orgName || '—' }}</span>
              <strong class="retail-ranking-row__sort-value">{{ formatMetric(rankingValue(item)) }}</strong>
              <span class="retail-ranking-row__metric" data-testid="retail-ranking-matrix">{{ formatMetric(item.aum) }}</span>
              <span class="retail-ranking-row__metric">{{ formatMetric(item.increase) }}</span>
              <span class="retail-ranking-row__metric">{{ formatMetric(item.rate) }}</span>
              <span class="retail-ranking-row__metric">{{ formatMetric(item.nplRate) }}</span>
            </li>
          </ol>
          <p v-if="filteredRankings.length" class="retail-ranking-matrix-note">四项指标同屏展示；颜色仅作视觉区分，不代表风险阈值。</p>
          <div v-else class="retail-empty">暂无机构排名绑定</div>
          <p class="retail-ranking-hint">点击机构查看资产、净增和风险指标</p>
        </article>

        <article class="retail-panel retail-target-panel">
          <header class="retail-panel__heading">
            <div>
              <span class="retail-kicker">目标追踪</span>
              <h2>零售经营目标</h2>
            </div>
            <span>金额：亿元</span>
          </header>
          <div v-if="safeModel.targets.length" class="retail-target-list retail-scroll-region" tabindex="0" role="region" aria-label="零售经营目标列表">
            <div v-for="(target, index) in safeModel.targets" :key="`${target.name || 'target'}-${index}`" class="retail-target-row" :data-target-name="target.name || `目标${index + 1}`">
              <div class="retail-target-row__top">
                <strong>{{ target.name || '—' }}</strong>
                <span v-if="!hasValidTarget(target)" class="retail-target-invalid">无有效目标</span>
                <span v-else-if="!hasValue(target.actual)" class="retail-target-invalid">实际待更新</span>
                <span v-else :class="{ 'is-negative': targetProgress(target) < 0, 'is-over': targetProgress(target) > 100 }">{{ formatPercent(targetProgress(target)) }}</span>
              </div>
              <div class="retail-target-row__meta">
                <span>实际 {{ formatMetric(target.actual) }} 亿元</span>
                <span>目标 {{ hasValidTarget(target) ? `${formatMetric(target.target)} 亿元` : '无有效目标' }}</span>
                <span v-if="hasValidTarget(target) && hasValue(target.actual)">{{ targetGap(target) >= 0 ? '超目标' : '距目标' }} {{ formatMetric(Math.abs(targetGap(target))) }} 亿元</span>
              </div>
              <div v-if="hasValidTarget(target) && hasValue(target.actual)" class="retail-target-bar" aria-hidden="true"><i :style="{ width: `${targetProgressWidth(target)}%` }"></i></div>
              <div v-else class="retail-target-bar is-empty" aria-hidden="true"><i style="width: 0%"></i></div>
            </div>
          </div>
          <div v-else class="retail-empty">暂无目标绑定</div>
        </article>
      </div>
    </section>

    <div
      v-if="directoryOpen"
      ref="directoryDialogRef"
      class="retail-directory-backdrop"
      data-testid="retail-institution-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="retail-directory-title"
      tabindex="-1"
      @click.self="closeDirectory"
      @keydown="onDirectoryKeydown"
    >
      <section class="retail-directory-dialog">
        <header class="retail-directory-dialog__header">
          <div>
            <span class="retail-kicker">{{ demo ? '演示机构' : '授权机构' }}</span>
            <h2 id="retail-directory-title">零售机构目录</h2>
            <p>机构身份来自目录；缺城市资料仍保留在全部机构中。</p>
          </div>
          <button type="button" class="retail-directory-dialog__close" data-action="close-retail-directory" aria-label="关闭机构目录" @click="closeDirectory">×</button>
        </header>
        <div class="retail-directory-dialog__toolbar">
          <label for="retail-directory-search">搜索机构</label>
          <input id="retail-directory-search" ref="directorySearchRef" v-model="directorySearch" data-testid="retail-directory-search" type="search" placeholder="输入机构编码或名称" autocomplete="off">
          <span>{{ filteredInstitutions.length }} / {{ safeModel.institutions.length }} 家</span>
        </div>
        <div class="retail-directory-dialog__body">
          <div class="retail-directory-list retail-scroll-region" tabindex="0" role="region" aria-label="零售机构目录列表">
            <button
              v-for="institution in filteredInstitutions"
              :key="institution.orgCode || institution.name"
              type="button"
              class="retail-directory-row"
              data-testid="retail-directory-row"
              :data-org-code="institution.orgCode || ''"
              @click="openInstitution(institution)"
            >
              <span class="retail-directory-row__identity"><strong>{{ institutionName(institution) }}</strong><small>{{ institution.orgCode || '—' }}</small></span>
              <span>{{ institutionCity(institution) }}</span>
              <span :class="institution.located ? 'is-located' : 'is-unlocated'">{{ institution.located ? '已定位' : '资料待核' }}</span>
            </button>
            <div v-if="!filteredInstitutions.length" class="retail-empty">暂无匹配机构</div>
          </div>
          <aside v-if="selectedInstitution" class="retail-directory-detail" aria-live="polite">
            <span class="retail-kicker">本机构身份</span>
            <h3>{{ institutionName(selectedInstitution) }}</h3>
            <dl>
              <div><dt>机构编码</dt><dd>{{ selectedInstitution.orgCode || '—' }}</dd></div>
              <div><dt>城市</dt><dd>{{ institutionCity(selectedInstitution) }}</dd></div>
              <div><dt>定位</dt><dd>{{ selectedInstitution.located ? '已定位' : '资料待核' }}</dd></div>
            </dl>
            <section class="retail-directory-detail__metrics" aria-label="本机构排名指标">
              <h4>本机构排名指标</h4>
              <div v-if="selectedInstitutionRanking" class="retail-directory-detail__metric-grid">
                <div><span>AUM</span><strong>{{ formatMetric(selectedInstitutionRanking.aum) }}</strong><small>亿元</small></div>
                <div><span>较上月净增</span><strong>{{ formatMetric(selectedInstitutionRanking.increase) }}</strong><small>亿元</small></div>
                <div><span>AUM完成率</span><strong>{{ formatMetric(selectedInstitutionRanking.rate) }}</strong><small>%</small></div>
                <div><span>个贷不良率</span><strong>{{ formatMetric(selectedInstitutionRanking.nplRate) }}</strong><small>%</small></div>
              </div>
              <p v-else>暂无本机构排名数据</p>
            </section>
            <section class="retail-directory-detail__rank" data-testid="retail-directory-ranking-context" aria-label="同口径机构排名">
              <h4>同口径机构排名</h4>
              <div>
                <span>当前口径</span>
                <strong>{{ rankingMetricInfo.label }}</strong>
              </div>
              <div>
                <span>排名</span>
                <strong>{{ selectedInstitutionRankLabel }}</strong>
                <small>可比样本 {{ comparableSampleLabel }} 家</small>
              </div>
            </section>
          </aside>
          <aside v-else class="retail-directory-detail retail-directory-detail--empty">
            <span class="retail-kicker">本机构身份</span>
            <p>选择机构查看目录身份与已有排名指标。</p>
          </aside>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import {
  Close, Coin, OfficeBuilding, Refresh, Setting, TrendCharts, UserFilled, WarningFilled
} from '@element-plus/icons-vue';
import PanoramaMap from './PanoramaMap.vue';
import RetailTrend from './RetailTrend.vue';
import { provinceGeo } from './geography.js';
import {
  buildRetailLeadershipInsights,
  buildSegmentComparisons
} from './retailLeadershipInsights.js';

const props = defineProps({
  model: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  demo: { type: Boolean, default: false }
});
const emit = defineEmits(['refresh', 'back', 'configure', 'branch-select']);

const KPI_DEFINITIONS = Object.freeze([
  { key: 'retailAum', label: '零售AUM', unit: '亿元' },
  { key: 'retailDeposit', label: '储蓄余额', unit: '亿元' },
  { key: 'retailRevenue', label: '零售营业收入', unit: '亿元' },
  { key: 'retailValueCustomers', label: '价值客户', unit: '万户' },
  { key: 'retailLoan', label: '个人贷款', unit: '亿元' },
  { key: 'retailNplRate', label: '个贷不良率', unit: '%' }
]);
const rankingMetricOptions = Object.freeze([
  { key: 'aum', label: 'AUM', unit: '亿元' },
  { key: 'increase', label: '较上月净增', unit: '亿元' },
  { key: 'rate', label: '完成率', unit: '%' }
]);
const rankingMetric = ref('aum');
const rankingOrder = ref('leading');
const selectedCityCode = ref('');
const selectedCityName = ref('');
const directoryOpen = ref(false);
const directorySearch = ref('');
const selectedInstitution = ref(null);
const directoryDialogRef = ref(null);
const directorySearchRef = ref(null);
const rootRef = ref(null);
const focusBeforeDirectory = ref(null);

const safeModel = computed(() => {
  const source = props.model && typeof props.model === 'object' ? props.model : {};
  return {
    title: '',
    scopeLabel: '当前大屏授权范围',
    dataDate: '',
    kpis: [],
    trend: [],
    segments: [],
    rankings: [],
    attention: [],
    targets: [],
    institutions: [],
    issues: [],
    ...source,
    kpis: Array.isArray(source.kpis) ? source.kpis : [],
    trend: Array.isArray(source.trend) ? source.trend : [],
    segments: Array.isArray(source.segments) ? source.segments : [],
    rankings: Array.isArray(source.rankings) ? source.rankings : [],
    attention: Array.isArray(source.attention) ? source.attention : [],
    targets: Array.isArray(source.targets) ? source.targets : [],
    institutions: Array.isArray(source.institutions) ? source.institutions : [],
    issues: Array.isArray(source.issues) ? source.issues : []
  };
});

function findKpi(key) {
  return safeModel.value.kpis.find(item => String(item?.key || '') === key) || null;
}

const kpiCards = computed(() => KPI_DEFINITIONS.map(definition => {
  const bound = findKpi(definition.key);
  return {
    ...definition,
    ...(bound || {}),
    key: definition.key,
    label: bound?.label || definition.label,
    unit: bound?.unit || definition.unit,
    value: bound?.value ?? null,
    change: bound?.change ?? null
  };
}));
const depositKpi = computed(() => findKpi('retailDeposit') || { key: 'retailDeposit', label: '储蓄余额', unit: '亿元', value: null, change: null });
const depositAverage = computed(() => findKpi('retailDepositAverage') || { key: 'retailDepositAverage', label: '储蓄月日均', unit: '亿元', value: null, change: null });
const displayDate = computed(() => safeModel.value.dataDate || '—');
const provinceGeoJson = provinceGeo || null;
const today = computed(() => {
  const now = new Date();
  return `${now.getFullYear()}.${String(now.getMonth() + 1).padStart(2, '0')}.${String(now.getDate()).padStart(2, '0')}`;
});
const rankingMetricInfo = computed(() => rankingMetricOptions.find(item => item.key === rankingMetric.value) || rankingMetricOptions[0]);
const segmentComparisons = computed(() => buildSegmentComparisons(safeModel.value.segments));
const segmentCoverageLabel = computed(() => `${segmentComparisons.value.length}组有效`);
const leadershipInsights = computed(() => buildRetailLeadershipInsights(safeModel.value));
const growthCountLabel = computed(() => leadershipInsights.value.growthComparableCount > 0
  ? String(leadershipInsights.value.negativeGrowthCount)
  : '—');
const growthSampleLabel = computed(() => `可判断 ${leadershipInsights.value.growthComparableCount}/${leadershipInsights.value.growthSampleCount} 家`);
const targetValidityLabel = computed(() => leadershipInsights.value.targetCount > 0
  ? `${leadershipInsights.value.validTargetCount}/${leadershipInsights.value.targetCount}`
  : '—');
const targetGapLabel = computed(() => leadershipInsights.value.targetCount > 0
  ? `有缺口 ${insightCount(leadershipInsights.value.targetGapCount)} 项 · 金额按指标分列`
  : '未绑定目标');
const filteredRankings = computed(() => {
  const source = safeModel.value.rankings.filter(row => !selectedCityCode.value || String(row?.cityCode || '') === selectedCityCode.value);
  return [...source].sort((left, right) => {
    const a = finiteValue(left?.[rankingMetric.value]);
    const b = finiteValue(right?.[rankingMetric.value]);
    if (a === null && b === null) return 0;
    if (a === null) return 1;
    if (b === null) return -1;
    return rankingOrder.value === 'leading' ? b - a : a - b;
  });
});
const rankingMax = computed(() => Math.max(0, ...filteredRankings.value.map(row => Math.abs(finiteValue(row?.[rankingMetric.value]) ?? 0))));
const filteredInstitutions = computed(() => {
  const query = directorySearch.value.trim().toLocaleLowerCase();
  return safeModel.value.institutions.filter(item => {
    if (!query) return true;
    return [item?.orgCode, item?.name, item?.orgName]
      .some(value => String(value || '').toLocaleLowerCase().includes(query));
  });
});
const selectedInstitutionRanking = computed(() => {
  const code = String(selectedInstitution.value?.orgCode || '');
  if (!code) return null;
  return safeModel.value.rankings.find(item => String(item?.orgCode || '') === code) || null;
});
const comparableRankings = computed(() => filteredRankings.value.filter(item => rankingValue(item) !== null));
const comparableSampleLabel = computed(() => String(comparableRankings.value.length));
const selectedInstitutionRank = computed(() => {
  const code = String(selectedInstitution.value?.orgCode || '');
  if (!code) return null;
  const selected = comparableRankings.value.find(item => String(item?.orgCode || '') === code);
  if (!selected) return null;
  return rankingRank(selected, 0);
});
const selectedInstitutionRankLabel = computed(() => {
  const rank = selectedInstitutionRank.value;
  return rank === null ? '—' : `${rank}/${comparableSampleLabel.value}`;
});
const institutionCountLabel = computed(() => {
  const total = safeModel.value.institutions.length;
  const located = safeModel.value.institutions.filter(item => item?.located).length;
  return `${total} 家机构 · 已定位 ${located} 家`;
});
const segmentMaxAum = computed(() => Math.max(0, ...safeModel.value.segments.map(item => finiteValue(item?.aum) ?? 0)));

function finiteValue(value) {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value === 'boolean' || typeof value === 'object') return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  try {
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  } catch {
    return null;
  }
}

function hasValue(value) {
  return finiteValue(value) !== null;
}

function formatMetric(value) {
  const number = finiteValue(value);
  if (number === null) return '—';
  return new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(number);
}

function formatChange(value) {
  return finiteValue(value);
}

function formatPercent(value) {
  const number = finiteValue(value);
  return number === null ? '—' : `${number.toFixed(2)}%`;
}

function changeText(kpi, empty = '') {
  const number = formatChange(kpi?.change);
  if (number === null) return empty;
  const suffix = kpi?.key === 'retailNplRate' ? 'pp' : '%';
  return `${number >= 0 ? '↑' : '↓'} ${Math.abs(number).toFixed(2)}${suffix}`;
}

function changeClass(kpi) {
  const number = formatChange(kpi?.change);
  if (number === null) return 'is-empty';
  if (kpi?.key === 'retailNplRate') return number > 0 ? 'is-risk' : 'is-favorable';
  return number < 0 ? 'is-down' : 'is-up';
}

function kpiGlyph(key, index) {
  return ({
    retailAum: Coin,
    retailDeposit: OfficeBuilding,
    retailRevenue: TrendCharts,
    retailValueCustomers: UserFilled,
    retailLoan: Coin,
    retailNplRate: WarningFilled
  }[key] || [Coin, OfficeBuilding, TrendCharts, UserFilled][index % 4]);
}

function kpiTitle(kpi) {
  const date = kpi?.date || kpi?.dataDate || kpi?.periodDate;
  return date ? `${kpi.label} · 数据日期 ${date}` : '';
}

function segmentColor(index) {
  return ['#42e7ee', '#a77bff', '#548dff', '#f4bd5b'][index % 4];
}

function segmentBarWidth(value) {
  const number = finiteValue(value);
  if (number === null || segmentMaxAum.value <= 0) return 0;
  return Math.max(0, Math.min(100, number / segmentMaxAum.value * 100));
}

function shareWidth(value) {
  const number = finiteValue(value);
  return number === null ? 0 : Math.max(0, Math.min(100, number));
}

function insightCount(value) {
  const number = finiteValue(value);
  return number === null ? '—' : String(number);
}

function rankingValue(item) {
  return finiteValue(item?.[rankingMetric.value]);
}

function rankingBarWidth(item) {
  const value = rankingValue(item);
  if (value === null || rankingMax.value <= 0) return 0;
  return Math.min(100, Math.abs(value) / rankingMax.value * 100);
}

function rankingRank(item) {
  const value = rankingValue(item);
  if (value === null) return null;
  const betterCount = comparableRankings.value.filter(row => {
    const rowValue = rankingValue(row);
    return rankingOrder.value === 'leading' ? rowValue > value : rowValue < value;
  }).length;
  return betterCount + 1;
}

function hasValidTarget(target) {
  const value = finiteValue(target?.target);
  return value !== null && value > 0;
}

function targetProgress(target) {
  const actual = finiteValue(target?.actual);
  if (!hasValidTarget(target) || actual === null) return null;
  return actual / Number(target.target) * 100;
}

function targetProgressWidth(target) {
  const value = targetProgress(target);
  if (value === null) return 0;
  return Math.max(0, Math.min(100, value));
}

function targetGap(target) {
  const actual = finiteValue(target?.actual);
  const expected = finiteValue(target?.target);
  if (actual === null || expected === null) return null;
  return actual - expected;
}

function selectCity(region) {
  const code = String(region?.code || '').trim();
  if (!code) return;
  selectedCityCode.value = code;
  selectedCityName.value = String(region?.name || code);
}

function clearCity() {
  selectedCityCode.value = '';
  selectedCityName.value = '';
}

function institutionName(institution) {
  return institution?.name || institution?.orgName || institution?.orgCode || '未命名机构';
}

function institutionCity(institution) {
  const code = String(institution?.cityCode || '').trim();
  return institution?.cityName || code || '城市待维护';
}

function openInstitution(item) {
  const code = String(item?.orgCode || '').trim();
  if (!code) return;
  const identity = safeModel.value.institutions.find(entry => String(entry?.orgCode || '') === code);
  selectedInstitution.value = identity || {
    orgCode: code,
    name: item?.name || item?.orgName || code,
    cityCode: item?.cityCode || null,
    cityName: item?.cityName || null,
    located: false
  };
  emit('branch-select', code);
  openDirectory(false);
}

async function openDirectory(resetSearch = true) {
  focusBeforeDirectory.value = document.activeElement;
  if (resetSearch) {
    directorySearch.value = '';
    selectedInstitution.value = null;
  }
  directoryOpen.value = true;
  document.body.style.overflow = 'hidden';
  document.addEventListener('keydown', onDocumentKeydown);
  await nextTick();
  directorySearchRef.value?.focus();
}

function closeDirectory() {
  if (!directoryOpen.value) return;
  directoryOpen.value = false;
  directorySearch.value = '';
  selectedInstitution.value = null;
  document.body.style.overflow = '';
  document.removeEventListener('keydown', onDocumentKeydown);
  const target = focusBeforeDirectory.value;
  focusBeforeDirectory.value = null;
  if (target && typeof target.focus === 'function') target.focus();
}

function onDocumentKeydown(event) {
  if (directoryOpen.value && event.key === 'Escape') {
    event.preventDefault();
    closeDirectory();
  }
}

function onDirectoryKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault();
    closeDirectory();
    return;
  }
  if (event.key !== 'Tab') return;
  const container = directoryDialogRef.value;
  const focusables = container
    ? [...container.querySelectorAll('button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])')]
    : [];
  if (!focusables.length) return;
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
  document.removeEventListener('keydown', onDocumentKeydown);
  if (directoryOpen.value) document.body.style.overflow = '';
});

function clearTransientState() {
  selectedCityCode.value = '';
  selectedCityName.value = '';
  directorySearch.value = '';
  selectedInstitution.value = null;
  rankingMetric.value = 'aum';
  rankingOrder.value = 'leading';
  closeDirectory();
}

watch(() => [props.model, props.error], ([model, error], previous = []) => {
  if (error || model !== previous[0]) clearTransientState();
}, { deep: true });
</script>

<style src="./retail.scss" lang="scss"></style>
