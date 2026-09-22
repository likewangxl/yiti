<template>
  <main
    ref="rootRef"
    class="corporate-dashboard"
    tabindex="-1"
    aria-label="对公经营总览"
    @keydown.esc="closeOverlays"
  >
    <header class="corporate-header">
      <div class="corporate-header__context">
        <span class="corporate-eyebrow">对公经营监测</span>
        <strong>{{ today }}</strong>
        <span>数据日期 {{ displayDate }}</span>
      </div>
      <div class="corporate-header__title">
        <span class="corporate-title-kicker">Corporate overview</span>
        <h1>{{ safeModel.title || '对公经营总览' }}</h1>
        <span>{{ safeModel.scopeLabel || '当前大屏授权范围' }}</span>
      </div>
      <div class="corporate-header__actions">
        <span class="corporate-live-state">
          <i :class="{ 'is-loading': loading, 'is-error': error }"></i>
          {{ loading ? '正在取数' : error ? '数据异常' : '经营监测' }}
        </span>
        <button type="button" class="corporate-icon-action" data-action="refresh" aria-label="刷新对公大屏" title="刷新" @click="emit('refresh')"><component :is="Refresh" /></button>
        <button v-if="!demo" type="button" class="corporate-icon-action" data-action="configure" aria-label="配置对公大屏" title="配置" @click="emit('configure')"><component :is="Setting" /></button>
        <button type="button" class="corporate-icon-action" data-action="back" aria-label="返回大屏中心" title="返回" @click="emit('back')"><component :is="Close" /></button>
      </div>
    </header>

    <div v-if="demo" class="corporate-demo-badge" data-testid="corporate-demo-badge">本地演示 · 非业务数据</div>
    <div v-if="loading" class="corporate-loading" role="status">加载中…</div>
    <div v-if="error" class="corporate-error" role="alert">{{ error }}</div>

    <MetricDisplayWidgets v-if="configuredMetrics.enabled" :components="configuredMetrics.components" />
    <section v-else class="corporate-kpi-grid" aria-label="对公核心指标">
      <article
        v-for="(kpi, index) in kpiCards"
        :key="kpi.key"
        class="corporate-kpi"
        data-testid="corporate-kpi"
        :data-kpi-key="kpi.key"
        :title="kpiTitle(kpi)"
      >
        <span class="corporate-kpi__glyph" aria-hidden="true"><component :is="kpiGlyph(kpi.key, index)" /></span>
        <div class="corporate-kpi__copy">
          <span class="corporate-kpi__label">{{ kpi.label }}</span>
          <div class="corporate-kpi__number"><strong :title="metricTitle(kpi.value)">{{ displayKpi(kpi).text }}</strong><small>{{ displayKpi(kpi).unit }}</small><span class="corporate-visually-hidden">原始值 {{ displayKpi(kpi).raw }}</span></div>
        </div>
        <span v-if="formatChange(kpi.change) !== null" class="corporate-kpi__change" :class="changeClass(kpi)">
          {{ changeText(kpi) }}<small>较上期</small>
        </span>
      </article>
    </section>
    <SeriesTableWidgets v-if="configuredSeriesTables.components.length" :components="configuredSeriesTables.components" />

    <section class="corporate-insight-strip" data-testid="corporate-leadership-insights" aria-label="经营观察">
      <div class="corporate-insight-item corporate-insight-item--gap">
        <span>目标缺口</span><strong>{{ targetGapSummary }}</strong>
        <small>{{ targetGapNames }}</small>
      </div>
      <div class="corporate-insight-item">
        <span>负增 / 落后机构</span><strong>{{ growthCountLabel }}</strong>
        <small>{{ growthSampleLabel }} · 负增或当前指标落后</small>
      </div>
      <div class="corporate-insight-item">
        <span>协调事项</span><strong>{{ attentionSummary.count }}</strong>
        <small>{{ attentionSummary.note }}</small>
      </div>
      <div class="corporate-insight-item corporate-insight-item--wide">
        <span>数据缺项</span><strong>{{ missingDataSummary.count }}</strong>
        <small>{{ missingDataSummary.note }}</small>
      </div>
    </section>

    <section class="corporate-main-grid">
      <div class="corporate-column corporate-column--left">
        <article class="corporate-panel corporate-deposit-panel">
          <header class="corporate-panel__heading">
            <div><span class="corporate-kicker">存款经营</span><h2>对公存款经营</h2></div>
            <span>月度口径</span>
          </header>
          <div class="corporate-deposit__body corporate-scroll-region" tabindex="0" role="region" aria-label="对公存款核心指标内容">
            <div class="corporate-deposit__cards">
              <div class="corporate-data-card" data-testid="corporate-deposit-balance">
                <span>对公存款余额</span>
                <strong :class="{ 'is-empty': !hasValue(depositKpi.value) }" :title="metricTitle(depositKpi.value)">{{ displayKpi(depositKpi).text }}</strong>
                <small>时点余额 · {{ displayKpi(depositKpi).unit }}</small>
                <span class="corporate-visually-hidden">原始值 {{ displayKpi(depositKpi).raw }}</span>
              </div>
              <div class="corporate-data-card" data-testid="corporate-deposit-average">
                <span>对公存款月日均</span>
                <strong :class="{ 'is-empty': !hasValue(depositAverage.value) }" :title="metricTitle(depositAverage.value)">{{ displayKpi(depositAverage).text }}</strong>
                <small v-if="hasValue(depositAverage.value)">月内日均 · {{ displayKpi(depositAverage).unit }}</small>
                <small v-else>未绑定 · 亿元</small>
                <span class="corporate-visually-hidden">原始值 {{ displayKpi(depositAverage).raw }}</span>
              </div>
            </div>
            <div class="corporate-deposit__footer">
              <span class="corporate-deposit__signal"><i></i>余额较上期变化</span>
              <strong data-testid="corporate-deposit-change" :class="changeClass(depositKpi)">{{ changeText(depositKpi, '—') }}</strong>
            </div>
          </div>
        </article>

        <article class="corporate-panel corporate-segments-panel" data-testid="corporate-segments">
          <header class="corporate-panel__heading">
            <div><span class="corporate-kicker">重点客群信贷</span><h2>重点客群信贷</h2></div>
            <span>万户 / 亿元 · 万元/户</span>
          </header>
          <p class="corporate-panel__note corporate-segment-scope-note" data-testid="corporate-segment-scope-note">
            客群标签可能交叉，客户数与贷款余额不求和
          </p>
          <div v-if="segmentComparisons.length" class="corporate-segment-list corporate-scroll-region" tabindex="0" aria-label="重点客群信贷列表">
            <div class="corporate-segment-head" aria-hidden="true"><span>客群</span><span>贷款规模（相对展示）</span><span>客户数</span><span>贷款</span><span>户均贷款</span></div>
            <div v-for="(segment, index) in segmentComparisons" :key="`${segment.name || 'segment'}-${index}`" class="corporate-segment-row" data-testid="corporate-segment-row">
              <div class="corporate-segment-row__identity"><i :style="{ backgroundColor: segmentColor(index) }"></i><span>{{ segment.name || '—' }}</span></div>
              <div class="corporate-segment-row__bar" :title="segment.loanScale === null ? '贷款值待更新' : '仅用于当前返回行的视觉比较'"><i :style="{ width: `${shareWidth(segment.loanScale)}%` }"></i></div>
              <strong>{{ formatMetric(segment.customers) }}</strong>
              <small>{{ formatMetric(segment.loan) }}</small>
              <em>{{ formatMetric(segment.averageLoan) }}</em>
            </div>
          </div>
          <div v-else class="corporate-empty">暂无重点客群数据</div>
        </article>

        <article v-if="compositionTabsEnabled" class="corporate-panel corporate-composition-panel" data-testid="corporate-composition">
          <header class="corporate-panel__heading">
            <div><span class="corporate-kicker">业务结构</span><h2>存款 / 贷款 / 收入结构</h2></div>
            <span>公司 / 零售</span>
          </header>
          <CompositionTabsWidget
            class="corporate-composition-content"
            :model="compositionTabsModel"
            @business-line-select="selectCompositionBusinessLine"
          />
        </article>

        <article class="corporate-panel corporate-attention-panel" data-testid="corporate-attention">
          <header class="corporate-panel__heading">
            <div><span class="corporate-kicker">经营协调</span><h2>需要协调的事项</h2></div>
            <span>{{ attentionSourceUnavailable ? '未接入' : safeModel.attention.length ? `${safeModel.attention.length} 条` : '暂无数据' }}</span>
          </header>
          <p class="corporate-panel__note">来源数量、责任归属与跟进时限</p>
          <ul v-if="!attentionSourceUnavailable && safeModel.attention.length" class="corporate-attention-list corporate-scroll-region" tabindex="0" aria-label="对公经营关注事项">
            <li v-for="(item, index) in safeModel.attention" :key="`${item.label || 'attention'}-${index}`">
              <button type="button" class="corporate-attention-row" data-testid="corporate-attention-row" :aria-label="`查看事项详情：${item.label || '—'}`" @click="openAttention(item, $event)">
                <span class="corporate-attention-list__mark" aria-hidden="true">!</span>
                <span class="corporate-attention-list__main"><strong>{{ item.label || '—' }}</strong><small>责任 {{ item.owner || '未提供' }} · 期限 {{ item.deadline || '未提供' }}</small></span>
                <b>{{ formatCount(item.count) }}</b><span class="corporate-attention-row__arrow" aria-hidden="true">›</span>
              </button>
            </li>
          </ul>
          <div v-else class="corporate-empty">{{ attentionSourceUnavailable ? attentionSourceMessage : '暂无来源已确认事项' }}</div>
        </article>
      </div>

      <div class="corporate-column corporate-column--center">
        <article class="corporate-panel corporate-map-panel">
          <header class="corporate-panel__heading corporate-map-panel__heading">
            <div><span class="corporate-kicker">机构视图</span><h2>陕西省机构分布</h2></div>
            <div class="corporate-map-panel__meta"><span>{{ institutionCountLabel }}</span><button type="button" class="corporate-directory-button" data-action="open-corporate-directory" @click="openDirectory()">机构目录</button></div>
          </header>
          <div class="corporate-map-toolbar">
            <span class="corporate-scope-chip" :class="{ 'is-city': selectedCityCode }" data-testid="corporate-scope-chip">{{ selectedCityName || '全辖机构' }}</span>
            <span class="corporate-map-legend"><i class="is-cyan"></i>行政区</span><span class="corporate-map-legend"><i class="is-violet"></i>城市选择</span>
            <button v-if="selectedCityCode" type="button" class="corporate-clear-city" data-action="clear-city" @click="clearCity">显示全部机构</button>
            <span v-else class="corporate-map-hint">点击城市筛选机构排名</span>
          </div>
          <PresentationMapWidget
            v-if="presentationMapEnabled"
            class="corporate-map"
            :presentation="sourcePresentation"
            :model="safeModel"
            :geo-json="provinceGeoJson"
            mode="province"
            :metric-key="activeMapMetricKey"
            :selected-region-code="selectedCityCode"
            :data-date="displayDate"
            :demo="demo"
            @region-select="selectCity"
            @branch-select="openInstitutionFromMap"
            @map-context="emit('map-context', $event)"
          />
          <PanoramaMap v-else class="corporate-map" appearance="relief" label-layout="callout" :city-details="corporateMapCityDetails" :metric-label="rankingMetricInfo.label" :metric-values="corporateMapMetricValues" :data-metric-label="rankingMetricInfo.label" :geo-json="provinceGeoJson" :points="safeModel.institutions" :demo="demo" mode="province" :selected-region-code="selectedCityCode" @region-select="selectCity" />
          <p class="corporate-scope-note" data-testid="corporate-scope-note">{{ safeModel.scopeLabel }} KPI 与趋势不随城市筛选变化；城市选择只影响机构分析。</p>
          <p v-if="selectedCityCode" class="corporate-selected-city" data-testid="corporate-selected-city">当前机构分析：{{ selectedCityName }}（{{ filteredRankings.length }} 家有排名记录）</p>
        </article>
        <div class="corporate-trend-zone">
          <p class="corporate-global-scope" data-testid="corporate-global-trend-scope">全辖趋势 · {{ safeModel.scopeLabel }} · 城市筛选不改变</p>
          <CorporateTrend v-if="!configuredSeriesTables.hasTrend" class="corporate-panel corporate-trend-panel" :trend="safeModel.trend" :data-date="displayDate" :scope-label="safeModel.scopeLabel" />
        </div>
      </div>

      <div class="corporate-column corporate-column--right">
        <InstitutionRankingWidget
          v-if="institutionRankingEnabled"
          class="corporate-panel corporate-ranking-panel"
          :model="institutionRankingModel"
          :title="institutionRankingTitle"
          @metric-change="syncMapMetric"
        />
        <article v-else class="corporate-panel corporate-ranking-panel">
          <header class="corporate-panel__heading">
            <div><span class="corporate-kicker">{{ rankingPartial ? '机构数值对照' : '机构贡献 / 短板' }}</span><h2 data-testid="corporate-ranking-title">{{ rankingMetricInfo.label }}{{ rankingPartial ? '数值对照' : '排名' }}</h2></div>
            <span>{{ rankingPartial ? '混合层级测试对照' : '按同口径机构' }}</span>
          </header>
          <div class="corporate-ranking-toolbar">
            <div class="corporate-segmented" role="group" aria-label="对公机构排名指标"><button v-for="option in rankingMetricOptions" :key="option.key" type="button" :data-ranking-metric="option.key" :disabled="rankingMetricUnavailable(option.key)" :title="rankingMetricUnavailable(option.key) ? rankingMetricStatus(option.key).message : ''" :class="{ active: rankingMetric === option.key }" @click="rankingMetric = option.key">{{ option.label }}</button></div>
            <div v-if="rankingBusinessOrderEnabled" class="corporate-segmented corporate-segmented--order" role="group" aria-label="排名顺序"><button type="button" data-ranking-order="leading" :class="{ active: rankingOrder === 'leading' }" @click="rankingOrder = 'leading'">领先</button><button type="button" data-ranking-order="lagging" :class="{ active: rankingOrder === 'lagging' }" @click="rankingOrder = 'lagging'">短板</button></div>
            <span v-else class="corporate-ranking-sort-note">按数值排序 · 不展示业务名次</span>
            <span class="corporate-ranking-unit" data-testid="corporate-ranking-unit">单位：{{ rankingMetricInfo.unit }}</span>
          </div>
          <p v-if="rankingMetricUnavailable(rankingMetric)" class="corporate-ranking-source-status" data-testid="corporate-ranking-source-status">{{ rankingMetricStatus(rankingMetric).message }}</p>
          <div v-if="filteredRankings.length" class="corporate-ranking-matrix-head" data-testid="corporate-ranking-matrix-head" aria-hidden="true"><span></span><span>机构</span><span>当前排序值<small>{{ rankingMetricInfo.label }} · {{ rankingMetricInfo.unit }}</small></span><span>存款<small>亿元</small></span><span>净增<small>亿元</small></span><span>完成率<small>%</small></span><span>不良率<small>%</small></span></div>
            <ol v-if="filteredRankings.length" class="corporate-ranking-list corporate-scroll-region" tabindex="0" aria-label="对公机构排名列表">
              <li v-for="(item, index) in filteredRankings" :key="item.orgCode || `${item.name}-${index}`" class="corporate-ranking-row" :aria-current="selectedInstitution?.orgCode === item.orgCode ? 'true' : undefined" data-testid="corporate-ranking-row" :data-org-code="item.orgCode || ''" tabindex="0" @click="openInstitution(item)" @keydown.enter="openInstitution(item)" @keydown.space.prevent="openInstitution(item)">
                <span class="corporate-ranking-row__number">{{ rankingPartial ? '·' : (rankingRank(item) ?? '—') }}</span><span class="corporate-ranking-row__name">{{ item.name || item.orgName || '—' }}</span><strong class="corporate-ranking-row__sort-value"><span>{{ formatRankingValue(item) }}</span><i class="corporate-ranking-row__bar" :class="{ 'is-negative': rankingValue(item) !== null && rankingValue(item) < 0 }" :style="rankingBarStyle(item)" aria-hidden="true"></i></strong><span class="corporate-ranking-row__metric" data-testid="corporate-ranking-matrix">{{ formatMatrixValue(item.deposit, '亿元', 'deposit') }}</span><span class="corporate-ranking-row__metric">{{ formatMatrixValue(item.increase, '亿元', 'increase') }}</span><span class="corporate-ranking-row__metric">{{ formatMatrixValue(item.rate, '%', 'rate') }}</span><span class="corporate-ranking-row__metric">{{ formatMatrixValue(item.nplRate, '%', 'nplRate') }}</span>
              </li>
            </ol>
          <p v-if="filteredRankings.length" class="corporate-ranking-matrix-note">{{ rankingPartial ? '混合层级测试对照仅按数值排序，不代表同层绩效排名；指标缺失显示暂无来源。' : '四项指标同屏展示；颜色仅作视觉区分，不代表风险阈值。' }}</p>
          <div v-else class="corporate-empty">暂无机构排名绑定</div>
          <p class="corporate-ranking-hint">点击机构查看目录身份与对应指标</p>
        </article>

        <article class="corporate-panel corporate-target-panel">
          <header class="corporate-panel__heading"><div><span class="corporate-kicker">目标追踪</span><h2>对公经营目标</h2></div><span>金额按量级显示</span></header>
          <div v-if="safeModel.targets.length" class="corporate-target-list corporate-scroll-region" tabindex="0" role="region" aria-label="对公经营目标列表">
            <div v-for="(target, index) in safeModel.targets" :key="`${target.name || 'target'}-${index}`" class="corporate-target-row" :data-target-name="target.name || `目标${index + 1}`">
              <div class="corporate-target-row__top"><strong>{{ target.name || '—' }}</strong><span v-if="!hasValidTarget(target)" class="corporate-target-invalid">无有效目标</span><span v-else-if="!hasValue(target.actual)" class="corporate-target-invalid">实际待更新</span><span v-else :class="{ 'is-negative': targetProgress(target) < 0, 'is-over': targetProgress(target) > 100 }">{{ formatPercent(targetProgress(target)) }}</span></div>
              <div class="corporate-target-row__meta"><span>实际 {{ formatTargetAmount(target.actual) }}</span><span>目标 {{ hasValidTarget(target) ? formatTargetAmount(target.target) : '无有效目标' }}</span><span v-if="hasValidTarget(target) && hasValue(target.actual)">{{ targetGap(target) >= 0 ? '超目标' : '距目标' }} {{ formatTargetAmount(Math.abs(targetGap(target))) }}</span></div>
              <div v-if="hasValidTarget(target) && hasValue(target.actual)" class="corporate-target-bar" aria-hidden="true"><i :style="{ width: `${targetProgressWidth(target)}%` }"></i></div><div v-else class="corporate-target-bar is-empty" aria-hidden="true"><i style="width: 0%"></i></div>
            </div>
          </div>
          <div v-else class="corporate-empty">暂无目标绑定</div>
        </article>
      </div>
    </section>

    <div v-if="selectedAttention" class="corporate-attention-dialog-backdrop" data-testid="corporate-attention-detail" role="dialog" aria-modal="true" aria-label="对公经营事项详情" @click.self="closeAttention">
      <section ref="attentionDialogRef" class="corporate-attention-dialog" tabindex="-1" @keydown="onAttentionKeydown">
        <header><div><span class="corporate-kicker">经营协调事项</span><h2>{{ selectedAttention.label || '—' }}</h2></div><button type="button" data-action="close-corporate-attention" aria-label="关闭事项详情" @click="closeAttention">×</button></header>
        <div class="corporate-attention-dialog__body"><div class="corporate-attention-dialog__demo" v-if="demo">本地演示 · 非业务数据</div><dl><div><dt>数量</dt><dd data-testid="corporate-attention-detail-count">{{ formatCount(selectedAttention.count) }}</dd></div><div><dt>责任</dt><dd>{{ selectedAttention.owner || '未提供' }}</dd></div><div><dt>期限</dt><dd>{{ selectedAttention.deadline || '未提供' }}</dd></div><div><dt>授权范围</dt><dd>{{ safeModel.scopeLabel }}</dd></div><div><dt>数据日期</dt><dd>{{ displayDate }}</dd></div></dl><div class="corporate-attention-description"><h3>事项说明</h3><p>{{ selectedAttention.detail?.description || '未提供事项说明' }}</p><h3>协调要求</h3><p>{{ selectedAttention.detail?.coordination || '未提供协调要求' }}</p><small>来源：{{ selectedAttention.detail?.source || '未提供' }}</small></div></div>
        <footer><button type="button" data-action="close-corporate-attention" @click="closeAttention">关闭</button></footer>
      </section>
    </div>

    <div v-if="directoryOpen" class="corporate-directory-backdrop" data-testid="corporate-directory-dialog" role="dialog" aria-modal="true" aria-label="对公机构目录" @click.self="closeDirectory">
      <section ref="directoryDialogRef" class="corporate-directory-dialog" tabindex="-1" @keydown="onDirectoryKeydown">
        <header class="corporate-directory-dialog__header"><div><span class="corporate-kicker">机构目录</span><h2>授权机构</h2><p>仅展示当前大屏授权范围内的机构身份。</p></div><button type="button" aria-label="关闭机构目录" @click="closeDirectory">×</button></header>
        <div class="corporate-directory-dialog__toolbar"><label for="corporate-directory-search">搜索机构</label><input ref="directorySearchRef" id="corporate-directory-search" v-model="directorySearch" data-testid="corporate-directory-search" type="search" placeholder="输入机构名称或机构号" /><span>{{ filteredInstitutions.length }} 家</span></div>
        <div class="corporate-directory-dialog__body">
          <div class="corporate-directory-list corporate-scroll-region" tabindex="0" aria-label="对公授权机构列表">
            <button v-for="institution in filteredInstitutions" :key="institution.orgCode" type="button" class="corporate-directory-row" data-testid="corporate-directory-row" @click="selectInstitution(institution)"><span><strong>{{ institutionName(institution) }}</strong><small>{{ institution.orgCode }}</small></span><span>{{ institutionCity(institution) }}</span><span :class="{ 'is-unlocated': !institution.located }">{{ institution.located ? '已定位' : '待定位' }}</span></button>
            <div v-if="!filteredInstitutions.length" class="corporate-empty">暂无匹配机构</div>
          </div>
          <aside v-if="selectedInstitution" class="corporate-institution-dialog corporate-institution-profile" data-testid="corporate-institution-dialog"><span class="corporate-kicker">机构画像</span><h3>{{ institutionName(selectedInstitution) }}</h3><dl><div><dt>机构号</dt><dd>{{ selectedInstitution.orgCode || '—' }}</dd></div><div><dt>城市</dt><dd>{{ institutionCity(selectedInstitution) }}</dd></div><div><dt>定位</dt><dd>{{ selectedInstitution.located ? '已定位' : '待定位' }}</dd></div></dl><div v-if="selectedInstitutionRanking" class="corporate-institution-metrics"><h4>对公排名指标</h4><div><span>对公存款</span><strong>{{ formatMatrixValue(selectedInstitutionRanking.deposit, '亿元', 'deposit') }}</strong><small>亿元 · {{ rankingPartial ? '不展示名次' : `排名 ${selectedInstitutionRankLabel}` }}</small></div><div><span>较上期净增</span><strong>{{ formatMatrixValue(selectedInstitutionRanking.increase, '亿元', 'increase') }}</strong><small>亿元</small></div><div><span>完成率</span><strong>{{ formatPercent(selectedInstitutionRanking.rate) }}</strong><small>年度目标</small></div><div><span>不良率</span><strong>{{ formatPercent(selectedInstitutionRanking.nplRate) }}</strong><small>对公贷款</small></div></div><p v-else>暂无该机构的对公排名指标。</p></aside>
          <aside v-else class="corporate-institution-dialog corporate-institution-dialog--empty"><span class="corporate-kicker">本机构身份</span><p>选择机构查看目录身份与已有排名指标。</p></aside>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { Close, Coin, OfficeBuilding, Refresh, Setting, TrendCharts, UserFilled, WarningFilled } from '@element-plus/icons-vue';
import PanoramaMap from './PanoramaMap.vue';
import { buildCityMapDetails, cityMapMetricValues } from './cityMapDetails.js';
import CorporateTrend from './CorporateTrend.vue';
import { provinceGeo } from './geography.js';
import { buildCorporateLeadershipInsights, buildSegmentComparisons, finiteMetric } from './corporateLeadershipInsights.js';
import { resolveDataStatus } from './sourcePresentation';
import MetricDisplayWidgets from '../presentation/widgets/MetricDisplayWidgets.vue';
import { buildDisplayMetricsModel } from '../presentation/model/displayMetricsModel';
import SeriesTableWidgets from '../presentation/widgets/SeriesTableWidgets.vue';
import { buildDisplaySeriesTableModel } from '../presentation/model/displaySeriesTableModel';
import CompositionTabsWidget from '../presentation/widgets/CompositionTabsWidget.vue';
import InstitutionRankingWidget from '../presentation/widgets/InstitutionRankingWidget.vue';
import PresentationMapWidget from '../presentation/map/PresentationMapWidget.vue';
import { findVisibleMapComponent } from '../presentation/map/mapModel';
import { buildCompositionTabsModel } from '../presentation/model/compositionTabsModel';
import { buildInstitutionRankingModel } from '../presentation/model/institutionRankingModel';

const props = defineProps({
  model: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  demo: { type: Boolean, default: false },
  sourcePresentation: { type: Object, default: () => ({}) }
});
const configuredMetrics = computed(() => buildDisplayMetricsModel(
  props.sourcePresentation?.displayPresentation, safeModel.value
));
const configuredSeriesTables = computed(() => buildDisplaySeriesTableModel(
  props.sourcePresentation?.displayPresentation, safeModel.value
));
const emit = defineEmits(['refresh', 'back', 'configure', 'branch-select', 'business-line-select', 'map-context']);

const KPI_DEFINITIONS = Object.freeze([
  { key: 'corpDeposit', label: '对公存款余额', unit: '亿元' },
  { key: 'corpDepositAverage', label: '对公存款月日均', unit: '亿元' },
  { key: 'corpLoan', label: '对公贷款余额', unit: '亿元' },
  { key: 'corpRevenue', label: '对公营业收入', unit: '亿元' },
  { key: 'corpCustomers', label: '有效对公客户', unit: '万户' },
  { key: 'corpNplRate', label: '对公不良率', unit: '%' }
]);
const rankingMetricOptions = Object.freeze([
  { key: 'deposit', label: '对公存款', unit: '亿元' },
  { key: 'increase', label: '较上期净增', unit: '亿元' },
  { key: 'rate', label: '完成率', unit: '%' }
]);
const rankingMetric = ref('deposit');
const selectedMapMetricKey = ref('');
const rankingOrder = ref('leading');
const selectedCityCode = ref('');
const selectedCityName = ref('');
const directoryOpen = ref(false);
const directorySearch = ref('');
const selectedInstitution = ref(null);
const selectedAttention = ref(null);
const rootRef = ref(null);
const attentionDialogRef = ref(null);
const directoryDialogRef = ref(null);
const directorySearchRef = ref(null);
const focusBeforeAttention = ref(null);
const focusBeforeDirectory = ref(null);

function rankingMetricStatus(metric) {
  return resolveDataStatus(props.sourcePresentation, props.sourcePresentation?.runtimeIssues, 'corpRanking', metric);
}
function rankingMetricUnavailable(metric) {
  const source = props.sourcePresentation?.sourceAvailability?.corpRanking;
  const status = String(source?.fields?.[metric]?.status || source?.status || '').toUpperCase();
  return ['NO_SOURCE', 'NO_ROWS', 'NO_VALUES', 'NO_COMPLETE_BATCH'].includes(status)
    && !safeModel.value.rankings.some(row => finiteMetric(row?.[metric]) !== null);
}
const rankingPartial = computed(() => String(
  props.sourcePresentation?.sourceAvailability?.corpRanking?.message || ''
).includes('混合层级'));
const rankingBusinessOrderEnabled = computed(() => !rankingPartial.value);
const attentionSourceEntry = computed(() => props.sourcePresentation?.sourceAvailability?.corpAttention || null);
const attentionSourceUnavailable = computed(() => {
  const status = String(attentionSourceEntry.value?.status || '').toUpperCase();
  return !Array.isArray(props.model?.attention) || ['NO_SOURCE', 'NO_ROWS', 'NO_VALUES', 'NO_COMPLETE_BATCH'].includes(status);
});
const attentionSourceMessage = computed(() => attentionSourceEntry.value?.message || '对公经营关注来源未接入');

const safeModel = computed(() => {
  const source = props.model && typeof props.model === 'object' ? props.model : {};
  return {
    title: '', scopeLabel: '当前大屏授权范围', dataDate: '', kpis: [], trend: [], segments: [], rankings: [], attention: [], targets: [], institutions: [], issues: [], citySummaries: {}, ...source,
    kpis: Array.isArray(source.kpis) ? source.kpis : [], trend: Array.isArray(source.trend) ? source.trend : [], segments: Array.isArray(source.segments) ? source.segments : [], rankings: Array.isArray(source.rankings) ? source.rankings : [], attention: Array.isArray(source.attention) ? source.attention : [], targets: Array.isArray(source.targets) ? source.targets : [], institutions: Array.isArray(source.institutions) ? source.institutions : [], issues: Array.isArray(source.issues) ? source.issues : [], citySummaries: source.citySummaries && typeof source.citySummaries === 'object' ? source.citySummaries : {}
  };
});
function presentationOf(source) {
  if (!source || typeof source !== 'object') return {};
  if (source.displaySchemaVersion !== undefined || source.display) return source;
  if (source.presentation && typeof source.presentation === 'object') return source.presentation;
  return source.canvasStyle?.presentation || source.renderPackage?.canvasStyle?.presentation || {};
}
const rankingComponent = computed(() => {
  const presentation = presentationOf(props.sourcePresentation?.displayPresentation || props.sourcePresentation);
  if (presentation.displaySchemaVersion !== 1) return null;
  const components = Array.isArray(presentation.display?.components) ? presentation.display.components : [];
  return components.find(component => component?.componentType === 'RANKING' && component.visible !== false) || null;
});
const mapComponent = computed(() => findVisibleMapComponent(props.sourcePresentation?.displayPresentation || props.sourcePresentation));
const presentationMapEnabled = computed(() => Boolean(mapComponent.value));
const institutionRankingEnabled = computed(() => Boolean(rankingComponent.value));
const institutionRankingModel = computed(() => {
  const component = rankingComponent.value;
  if (!component) return { enabled: false, metrics: [], rows: [], expected: [], rankable: [], missing: [] };
  return buildInstitutionRankingModel({
    institutions: safeModel.value.institutions,
    sourceAuthorized: true,
    rows: safeModel.value.rankings,
    rankingMetrics: Array.isArray(component.content?.rankingMetrics) ? component.content.rankingMetrics : [],
    activeMetricKey: selectedMapMetricKey.value
  });
});
const institutionRankingTitle = computed(() => {
  const component = rankingComponent.value;
  const title = component?.text?.titleMode === 'CUSTOM' ? component?.text?.title : component?.title;
  return String(title || '机构排名').trim() || '机构排名';
});

const compositionTabsModel = computed(() => buildCompositionTabsModel(
  props.sourcePresentation?.displayPresentation || props.sourcePresentation,
  safeModel.value
));
const compositionTabsEnabled = computed(() => compositionTabsModel.value.enabled
  && compositionTabsModel.value.components.length > 0);
const institutionContext = computed(() => ({
  orgCode: String(safeModel.value.orgCode || safeModel.value.institution?.orgCode || '').trim(),
  orgName: String(safeModel.value.orgName || safeModel.value.institution?.orgName || '').trim(),
  cityCode: String(safeModel.value.cityCode || safeModel.value.institution?.cityCode || '').trim(),
  cityName: String(safeModel.value.cityName || safeModel.value.institution?.cityName || '').trim()
}));
function selectCompositionBusinessLine(payload = {}) {
  const businessLine = String(payload.businessLine || '').trim().toUpperCase();
  const tabKey = String(payload.tabKey || '').trim();
  if (!['CORP', 'RETAIL'].includes(businessLine) || !tabKey) return;
  emit('business-line-select', { businessLine, tabKey, context: institutionContext.value });
}

function findKpi(key) { return safeModel.value.kpis.find(item => String(item?.key || '') === key) || null; }
const kpiCards = computed(() => KPI_DEFINITIONS.map(definition => {
  const bound = findKpi(definition.key);
  return { ...definition, ...(bound || {}), key: definition.key, label: bound?.label || definition.label, unit: bound?.unit || definition.unit, value: bound?.value ?? null, change: bound?.change ?? null };
}));
const depositKpi = computed(() => findKpi('corpDeposit') || { key: 'corpDeposit', label: '对公存款余额', unit: '亿元', value: null, change: null });
const depositAverage = computed(() => findKpi('corpDepositAverage') || { key: 'corpDepositAverage', label: '对公存款月日均', unit: '亿元', value: null, change: null });
const displayDate = computed(() => safeModel.value.dataDate || '—');
const provinceGeoJson = provinceGeo || null;
const today = computed(() => { const now = new Date(); return `${now.getFullYear()}.${String(now.getMonth() + 1).padStart(2, '0')}.${String(now.getDate()).padStart(2, '0')}`; });
const rankingMetricInfo = computed(() => rankingMetricOptions.find(item => item.key === rankingMetric.value) || rankingMetricOptions[0]);
const activeMapMetricKey = computed(() => selectedMapMetricKey.value
  || institutionRankingModel.value.activeMetricKey
  || String(mapComponent.value?.content?.mainField || '').trim());
const segmentComparisons = computed(() => buildSegmentComparisons(safeModel.value.segments));
const leadershipInsights = computed(() => buildCorporateLeadershipInsights(safeModel.value));
const growthCountLabel = computed(() => leadershipInsights.value.growthComparableCount > 0 ? String(leadershipInsights.value.negativeGrowthCount) : '—');
const growthSampleLabel = computed(() => `可判断 ${leadershipInsights.value.growthComparableCount}/${leadershipInsights.value.growthSampleCount} 家`);
const targetValidityLabel = computed(() => leadershipInsights.value.targetCount > 0 ? `${leadershipInsights.value.validTargetCount}/${leadershipInsights.value.targetCount}` : '—');
const targetGapLabel = computed(() => leadershipInsights.value.targetCount > 0 ? `有缺口 ${insightCount(leadershipInsights.value.targetGapCount)} 项 · 金额按指标分列` : '未绑定目标');
const targetGapSummary = computed(() => leadershipInsights.value.targetGapCount === null ? '—' : `有缺口 ${leadershipInsights.value.targetGapCount} 项`);
const targetGapNames = computed(() => leadershipInsights.value.targetGapCount === null
  ? '目标来源暂无有效值'
  : leadershipInsights.value.targetGapNames.join('、') || '当前没有未达标目标');
const attentionSummary = computed(() => {
  if (attentionSourceUnavailable.value) return { count: '—', note: attentionSourceMessage.value };
  const values = safeModel.value.attention.map(item => finiteMetric(item?.count)).filter(value => value !== null && value >= 0);
  const total = values.length ? values.reduce((sum, value) => sum + value, 0) : null;
  return {
    count: safeModel.value.attention.length ? `${safeModel.value.attention.length}项` : (props.sourcePresentation?.sourceAvailability?.corpAttention?.status === 'NO_SOURCE' ? '—' : '0项'),
    note: total === null ? '未提供事项数量' : `数量合计 ${formatCount(total)} · 逐项可查看`
  };
});
const missingDataSummary = computed(() => {
  const count = leadershipInsights.value.missingMetricCount;
  const issueCount = safeModel.value.issues.length;
  return {
    count: count === null && !issueCount ? '—' : String((count || 0) + issueCount),
    note: `${count === null ? '排名指标暂无完整样本' : `排名空值单元 ${count} 个`} · 运行说明 ${issueCount} 条`
  };
});
const corporateMapRankingUnavailable = computed(() => ['NO_SOURCE', 'NO_ROWS', 'NO_VALUES', 'NO_COMPLETE_BATCH'].includes(String(props.sourcePresentation?.sourceAvailability?.corpRanking?.status || '').toUpperCase()));
const corporateMapCityDetails = computed(() => buildCityMapDetails(
  corporateMapRankingUnavailable.value ? { ...safeModel.value, rankings: [] } : safeModel.value,
  {
    business: 'corporate',
    geoJson: provinceGeoJson,
    allowCityMetrics: !rankingPartial.value && !corporateMapRankingUnavailable.value,
    scopeLabel: rankingPartial.value && !corporateMapRankingUnavailable.value ? '混合层级 · 机构原值，不作汇总' : ''
  }
));
const corporateMapMetricValues = computed(() => rankingPartial.value || corporateMapRankingUnavailable.value ? {} : cityMapMetricValues(corporateMapCityDetails.value, rankingMetric.value));
const filteredRankings = computed(() => {
  const source = safeModel.value.rankings.filter(row => !selectedCityCode.value || String(row?.cityCode || '') === selectedCityCode.value);
  return [...source].sort((left, right) => {
    const a = rankingValue(left); const b = rankingValue(right);
    if (a === null && b === null) return 0; if (a === null) return 1; if (b === null) return -1;
    return rankingOrder.value === 'leading' ? b - a : a - b;
  });
});
const comparableRankings = computed(() => filteredRankings.value.filter(item => rankingValue(item) !== null));
const filteredInstitutions = computed(() => {
  const query = directorySearch.value.trim().toLocaleLowerCase();
  return safeModel.value.institutions.filter(item => !query || [item?.orgCode, item?.name, item?.orgName].some(value => String(value || '').toLocaleLowerCase().includes(query)));
});
const selectedInstitutionRanking = computed(() => {
  const code = String(selectedInstitution.value?.orgCode || '');
  return code ? safeModel.value.rankings.find(item => String(item?.orgCode || '') === code) || null : null;
});
const selectedInstitutionRank = computed(() => {
  const value = rankingValue(selectedInstitutionRanking.value); if (value === null) return null;
  return comparableRankings.value.filter(row => rankingOrder.value === 'leading' ? rankingValue(row) > value : rankingValue(row) < value).length + 1;
});
const selectedInstitutionRankLabel = computed(() => selectedInstitutionRank.value === null ? '—' : `${selectedInstitutionRank.value}/${comparableRankings.value.length}`);
const institutionCountLabel = computed(() => `${safeModel.value.institutions.length} 家机构 · 已定位 ${safeModel.value.institutions.filter(item => item?.located).length} 家`);

function hasValue(value) { return finiteMetric(value) !== null; }
function formatMetric(value) { const number = finiteMetric(value); return number === null ? '—' : new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(number); }
function formatCount(value) { const number = finiteMetric(value); return number === null ? '—' : new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(number); }
function formatChange(value) { return finiteMetric(value); }
function formatPercent(value) { const number = finiteMetric(value); return number === null ? '—' : `${number.toFixed(2)}%`; }
function formatDisplayMetric(value, unit = '', key = '') {
  const number = finiteMetric(value);
  const raw = value === null || value === undefined ? '' : String(value);
  if (number === null) return { text: '—', unit: unit || '', raw };
  const normalizedUnit = String(unit || '').trim();
  const customerMetric = normalizedUnit === '万户' || key === 'corpCustomers' || key === 'customers';
  const amountMetric = normalizedUnit === '亿元' || ['corpDeposit', 'corpDepositAverage', 'corpLoan', 'corpRevenue', 'deposit', 'increase'].includes(key);
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
function displayKpi(kpi) {
  return formatDisplayMetric(kpi?.value, kpi?.unit || (kpi?.key === 'corpCustomers' ? '万户' : '亿元'), kpi?.key);
}
function metricTitle(value) {
  const number = finiteMetric(value);
  return number === null ? '' : `原始值：${String(value)}`;
}
function formatMatrixValue(value, unit, key) {
  return formatMetric(value);
}
function formatTargetAmount(value) {
  const display = formatDisplayMetric(value, '亿元', 'target');
  return display.text === '—' ? '—' : `${display.text} ${display.unit}`;
}
function changeText(kpi, empty = '') { const number = formatChange(kpi?.change); if (number === null) return empty; const suffix = kpi?.key === 'corpNplRate' ? 'pp' : '%'; return `${number >= 0 ? '↑' : '↓'} ${Math.abs(number).toFixed(2)}${suffix}`; }
function changeClass(kpi) { const number = formatChange(kpi?.change); if (number === null) return 'is-empty'; if (kpi?.key === 'corpNplRate') return number > 0 ? 'is-risk' : 'is-favorable'; return number < 0 ? 'is-down' : 'is-up'; }
function kpiGlyph(key, index) { return ({ corpDeposit: OfficeBuilding, corpDepositAverage: TrendCharts, corpLoan: Coin, corpRevenue: TrendCharts, corpCustomers: UserFilled, corpNplRate: WarningFilled }[key] || [OfficeBuilding, Coin, TrendCharts, UserFilled][index % 4]); }
function kpiTitle(kpi) { const date = kpi?.date || kpi?.dataDate || kpi?.periodDate; return date ? `${kpi.label} · 数据日期 ${date}` : ''; }
function insightCount(value) { const number = finiteMetric(value); return number === null ? '—' : String(number); }
function segmentColor(index) { return ['#42e7ee', '#a77bff', '#548dff', '#f4bd5b'][index % 4]; }
function shareWidth(value) { const number = finiteMetric(value); return number === null ? 0 : Math.max(0, Math.min(100, number)); }
function rankingValue(item) { return finiteMetric(item?.[rankingMetric.value]); }
const rankingMax = computed(() => Math.max(0, ...comparableRankings.value.map(item => Math.abs(rankingValue(item) ?? 0))));
function formatRankingValue(item) { return formatMetric(rankingValue(item)); }
function rankingBarStyle(item) {
  const value = rankingValue(item);
  return value === null || rankingMax.value <= 0 ? { width: '0%' } : { width: `${Math.min(100, Math.abs(value) / rankingMax.value * 100)}%` };
}
function rankingRank(item) { const value = rankingValue(item); if (value === null) return null; return comparableRankings.value.filter(row => rankingOrder.value === 'leading' ? rankingValue(row) > value : rankingValue(row) < value).length + 1; }
function hasValidTarget(target) { const value = finiteMetric(target?.target); return value !== null && value > 0; }
function targetProgress(target) { const actual = finiteMetric(target?.actual); return hasValidTarget(target) && actual !== null ? actual / Number(target.target) * 100 : null; }
function targetProgressWidth(target) { const value = targetProgress(target); return value === null ? 0 : Math.max(0, Math.min(100, value)); }
function targetGap(target) { const actual = finiteMetric(target?.actual); const expected = finiteMetric(target?.target); return actual === null || expected === null ? null : actual - expected; }
function selectCity(region) { const code = String(region?.code || '').trim(); if (!code) return; selectedCityCode.value = code; selectedCityName.value = String(region?.name || code); }
function clearCity() { selectedCityCode.value = ''; selectedCityName.value = ''; }
function syncMapMetric(payload = {}) {
  const key = String(payload?.metricKey || '').trim();
  if (!key) return;
  const options = institutionRankingModel.value.metrics || [];
  if (options.length && !options.some(item => item.metricKey === key)) return;
  selectedMapMetricKey.value = key;
}
function institutionName(institution) { return institution?.name || institution?.orgName || institution?.orgCode || '未命名机构'; }
function institutionCity(institution) { return institution?.cityName || String(institution?.cityCode || '').trim() || '城市待维护'; }
function openInstitution(item) { const code = String(item?.orgCode || '').trim(); if (!code) return; focusBeforeDirectory.value = document.activeElement; const identity = safeModel.value.institutions.find(entry => String(entry?.orgCode || '') === code); selectedInstitution.value = identity || { orgCode: code, name: item?.name || item?.orgName || code, cityCode: item?.cityCode || null, cityName: item?.cityName || null, located: false }; emit('branch-select', code); directoryOpen.value = true; lockBodyScroll(); nextTick(() => directorySearchRef.value?.focus?.()); }
function openInstitutionFromMap(orgCode) { openInstitution({ orgCode: String(orgCode || '') }); }
function selectInstitution(institution) { selectedInstitution.value = institution; emit('branch-select', institution.orgCode); }
function lockBodyScroll() { document.body.style.overflow = 'hidden'; }
function unlockBodyScroll() { if (!selectedAttention.value && !directoryOpen.value) document.body.style.overflow = ''; }
function openDirectory() { focusBeforeDirectory.value = document.activeElement; directoryOpen.value = true; lockBodyScroll(); nextTick(() => directorySearchRef.value?.focus?.()); }
function closeDirectory({ restoreFocus = true } = {}) { if (!directoryOpen.value) return; directoryOpen.value = false; unlockBodyScroll(); const target = focusBeforeDirectory.value; focusBeforeDirectory.value = null; if (restoreFocus) nextTick(() => target?.focus?.()); }
function openAttention(item, event) { focusBeforeAttention.value = event?.currentTarget || document.activeElement; selectedAttention.value = item || null; lockBodyScroll(); nextTick(() => attentionDialogRef.value?.focus?.()); }
function closeAttention({ restoreFocus = true } = {}) { if (!selectedAttention.value) return; selectedAttention.value = null; unlockBodyScroll(); const target = focusBeforeAttention.value; focusBeforeAttention.value = null; if (restoreFocus) nextTick(() => target?.focus?.()); }
function closeOverlays() { if (selectedAttention.value) closeAttention(); else if (directoryOpen.value) closeDirectory(); }
function focusablesIn(container) { return container ? [...container.querySelectorAll('button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])')] : []; }
function trapTab(event, container) { if (event.key !== 'Tab') return; const focusables = focusablesIn(container); if (!focusables.length) return; const first = focusables[0]; const last = focusables[focusables.length - 1]; const active = document.activeElement; const activeInside = active && container?.contains(active) && active !== container; if (event.shiftKey && (!activeInside || active === first)) { event.preventDefault(); last.focus(); } else if (!event.shiftKey && (!activeInside || active === last)) { event.preventDefault(); first.focus(); } }
function onDirectoryKeydown(event) { if (event.key === 'Escape') { event.preventDefault(); closeDirectory(); return; } trapTab(event, directoryDialogRef.value); }
function onAttentionKeydown(event) { if (event.key === 'Escape') { event.preventDefault(); closeAttention(); return; } trapTab(event, attentionDialogRef.value); }
function onDocumentKeydown(event) { if (event.key === 'Escape') { event.preventDefault(); closeOverlays(); } }
function clearTransientState() { clearCity(); directorySearch.value = ''; selectedInstitution.value = null; selectedMapMetricKey.value = ''; rankingMetric.value = 'deposit'; rankingOrder.value = 'leading'; closeAttention({ restoreFocus: false }); closeDirectory({ restoreFocus: false }); }

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
watch(() => [props.model, props.loading, props.sourcePresentation?.scopeIdentity], ([model, loading, identity]) => {
  if (props.error) { clearTransientState(); return; }
  if (loading) return;
  const nextSignature = `${scopeSignature(model)}|${identity || ''}`;
  const scopeChanged = nextSignature !== lastScopeSignature.value;
  lastScopeSignature.value = nextSignature;
  if (props.error || scopeChanged) {
    clearTransientState();
    return;
  }
  // A refresh can change values while retaining a valid filter. Remove only
  // selections that no longer exist in the newly authorized payload.
  if (selectedCityCode.value) {
    const cityStillPresent = safeModel.value.rankings.some(row => String(row?.cityCode || row?.city_code || '') === selectedCityCode.value)
      || safeModel.value.institutions.some(row => String(row?.cityCode || row?.city_code || '') === selectedCityCode.value);
    if (!cityStillPresent) clearCity();
  }
  if (selectedInstitution.value) {
    const code = String(selectedInstitution.value.orgCode || '');
    if (!safeModel.value.institutions.some(row => String(row?.orgCode || '') === code)
      && !safeModel.value.rankings.some(row => String(row?.orgCode || '') === code)) selectedInstitution.value = null;
  }
}, { deep: true });
watch(() => props.error, error => { if (error) clearTransientState(); });
watch(() => [directoryOpen.value, selectedAttention.value], ([openDirectoryState, openAttentionState]) => {
  if (openDirectoryState || openAttentionState) document.addEventListener('keydown', onDocumentKeydown);
  else document.removeEventListener('keydown', onDocumentKeydown);
});
onBeforeUnmount(() => { document.removeEventListener('keydown', onDocumentKeydown); document.body.style.overflow = ''; });
</script>

<style src="./corporate.scss" lang="scss"></style>
