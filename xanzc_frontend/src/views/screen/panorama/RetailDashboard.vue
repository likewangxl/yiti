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
        <button type="button" class="retail-icon-action" data-action="back" :aria-label="backLabel" title="返回" @click="emit('back')"><component :is="Close" /></button>
      </div>
    </header>

    <div v-if="demo" class="retail-demo-badge" data-testid="retail-demo-badge">本地演示 · 非业务数据</div>
    <div v-if="loading" class="retail-loading" role="status">加载中…</div>
    <div v-if="error" class="retail-error" role="alert">{{ error }}</div>

    <PresentationLayout
      v-if="presentationLayoutEnabled"
      :presentation="sourcePresentation"
      :model="safeModel"
      :geo-json="provinceGeoJson"
      mode="province"
      :metric-key="activeMapMetricKey"
      :selected-region-code="selectedCityCode"
      :selected-org-code="selectedInstitution?.orgCode || ''"
      :data-date="displayDate"
      :demo="demo"
      @region-select="selectCity"
      @branch-select="openInstitutionFromMap"
      @map-context="emit('map-context', $event)"
      @metric-change="syncMapMetric"
      @business-line-select="selectCompositionBusinessLine"
    />
    <template v-else>
    <MetricDisplayWidgets v-if="configuredMetrics.enabled" :components="configuredMetrics.components" />
    <section v-else class="retail-kpi-grid" aria-label="零售核心指标">
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
            <strong :class="{ 'is-empty': !hasValue(kpi.value) }" :title="`${kpi.value ?? '—'} ${kpi.unit}`">{{ displayKpi(kpi).text }}</strong>
            <small>{{ displayKpi(kpi).unit }}</small>
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
    <SeriesTableWidgets v-if="configuredSeriesTables.components.length" :components="configuredSeriesTables.components" />

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
                <strong :class="{ 'is-empty': !hasValue(depositComparison.balance) }">{{ formatAmount(depositComparison.balance, depositComparisonValues).text }}</strong>
                <small>时点余额 · {{ formatAmount(depositComparison.balance, depositComparisonValues).unit }}</small>
              </div>
              <div class="retail-data-card" data-testid="retail-deposit-average">
                <span>月日均余额</span>
                <strong :class="{ 'is-empty': !hasValue(depositComparison.average) }">{{ formatAmount(depositComparison.average, depositComparisonValues).text }}</strong>
                <small v-if="hasValue(depositComparison.average)">月内日均 · {{ formatAmount(depositComparison.average, depositComparisonValues).unit }}</small>
                <small v-else>未绑定 · 待确认</small>
              </div>
            </div>
            <div class="retail-savings__footer">
              <span class="retail-savings__signal"><i></i>余额较上期变化</span>
              <strong data-testid="retail-deposit-change" :class="changeClass(depositKpi)">{{ changeText(depositKpi, '—') }}</strong>
              <span class="retail-savings__difference" data-testid="retail-deposit-difference">
                月日均 − 时点余额 {{ depositComparison.difference === null ? '—' : formatAmount(depositComparison.difference, depositComparisonValues).text }} {{ formatAmount(depositComparison.difference, depositComparisonValues).unit }}
                <small>口径对照，不代表净增</small>
              </span>
            </div>
          </div>
        </article>

        <article class="retail-panel retail-segments-panel" data-testid="retail-segments">
          <header class="retail-panel__heading">
            <div>
              <span class="retail-kicker">客户结构</span>
              <h2>客户分层</h2>
            </div>
            <span>{{ segmentComparisons.length ? '万户 / 亿元 · 万元/户' : '口径待确认' }}</span>
          </header>
          <p v-if="segmentComparisons.length" class="retail-panel__note retail-segment-scope-note" data-testid="retail-segment-scope-note" title="客户占比与资产占比仅使用客户数和资产额均已提供且非负的同一分层分母">
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
          <div v-else class="retail-empty">价值客户口径与客户分层数据源尚未接入</div>
        </article>

        <article v-if="compositionTabsEnabled" class="retail-panel retail-composition-panel" data-testid="retail-composition">
          <header class="retail-panel__heading">
            <div><span class="retail-kicker">业务结构</span><h2>存款 / 贷款 / 收入结构</h2></div>
            <span>公司 / 零售</span>
          </header>
          <CompositionTabsWidget
            class="retail-composition-content"
            :model="compositionTabsModel"
            @business-line-select="selectCompositionBusinessLine"
          />
        </article>

        <article class="retail-panel retail-attention-panel" data-testid="retail-attention">
          <header class="retail-panel__heading">
            <div>
              <span class="retail-kicker">经营关注</span>
              <h2>需要关注 / 数据核验</h2>
            </div>
            <span>{{ attentionItems.length ? `${attentionItems.length} 条` : '暂无数据' }}</span>
          </header>
          <p class="retail-panel__note">责任归属与跟进时限；只展示已有来源事项，缺来源或日期标为核验，不生成客户任务</p>
          <section v-if="hasDepositRankingShape && institutionDifferenceItems.length" class="retail-attention-difference" data-testid="retail-attention-difference-list" aria-label="日均与时点差额关注">
            <header>
              <strong>日均与时点差额关注</strong>
              <small>同日口径对照，不代表净增 / 风险结论</small>
            </header>
            <ul>
              <li v-for="item in institutionDifferenceItems" :key="`difference-${item.orgCode || item.name}`">
                <button type="button" data-testid="retail-attention-difference-row" @click="openInstitution(item)">
                  <span>{{ item.name || item.orgName || item.orgCode || '—' }}</span>
                  <strong>{{ formatAmount(item.difference, institutionDifferenceValues).text }} {{ formatAmount(item.difference, institutionDifferenceValues).unit }}</strong>
                </button>
              </li>
            </ul>
          </section>
          <ul v-if="attentionItems.length" class="retail-attention-list retail-scroll-region" tabindex="0" aria-label="经营关注事项">
            <li v-for="(item, index) in attentionItems" :key="`${item.label || 'attention'}-${item._issueKey || index}`">
              <button
                type="button"
                class="retail-attention-row"
                data-testid="retail-attention-row"
                :aria-label="`查看事项详情：${item.label || '—'}`"
                @click="openAttention(item, $event)"
              >
                <span class="retail-attention-list__mark" :class="{ 'is-verification': item.isVerification }" aria-hidden="true">{{ item.isVerification ? '?' : '!' }}</span>
                <span class="retail-attention-list__main">
                  <strong>{{ item.label || '—' }}</strong>
                  <small>{{ item.verificationSummary || `${item.isVerification ? '核验' : '责任'} ${item.owner || '未提供'} · 日期 ${item.deadline || '未提供'}` }}</small>
                </span>
                <b>{{ formatMetric(item.count) }}</b>
                <span class="retail-attention-row__arrow" aria-hidden="true">›</span>
              </button>
            </li>
          </ul>
          <div v-else-if="!institutionDifferenceItems.length" class="retail-empty">暂无来源已确认事项</div>
        </article>
      </div>

      <RetailAttentionDetails
        v-if="selectedAttention"
        :item="selectedAttention"
        :demo="demo"
        :scope-label="safeModel.scopeLabel"
        :data-date="displayDate"
        @close="closeAttention"
      />

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
          <PresentationMapWidget
            v-if="presentationMapEnabled"
            class="retail-map"
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
          <PanoramaMap
            v-else
            class="retail-map"
            appearance="relief"
            label-layout="callout"
            :city-details="retailMapCityDetails"
            :metric-label="retailMapMetricLabel"
            :metric-values="retailMapMetricValues"
            :region-states="retailMapInstitutionState.regionStates"
            :metric-colors="retailMapInstitutionState.metricColors"
            :color-by-metric="true"
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

        <RetailTrend v-if="!configuredSeriesTables.hasTrend" class="retail-panel retail-trend-panel" :trend="safeModel.trend" :data-date="displayDate" :scope-label="safeModel.scopeLabel" />
      </div>

      <div class="retail-column retail-column--right">
        <InstitutionRankingWidget
          v-if="institutionRankingEnabled"
          class="retail-panel retail-ranking-panel"
          :model="institutionRankingModel"
          :title="institutionRankingTitle"
          @metric-change="syncMapMetric"
        />
        <template v-else>
        <article v-if="hasDepositRankingShape" class="retail-panel retail-institution-comparison" data-testid="retail-institution-comparison">
          <header class="retail-panel__heading">
            <div>
              <span class="retail-kicker">机构存款对照</span>
              <h2>{{ institutionDepositView.title }}</h2>
            </div>
            <span>{{ institutionRows.length }} 家 · {{ rankingDataDate || '日期待确认' }}</span>
          </header>
          <div class="retail-institution-toolbar">
            <div class="retail-segmented" role="group" aria-label="机构类型筛选">
              <button
                v-for="option in institutionDepositView.filterOptions"
                :key="option.key"
                type="button"
                :data-institution-filter="option.key"
                :class="{ active: institutionDepositView.filter === option.key }"
                @click="institutionFilter = option.key; institutionPage = 1"
              >{{ option.label }} {{ option.count }}</button>
            </div>
            <div class="retail-segmented" role="group" aria-label="机构存款指标">
              <button type="button" data-ranking-metric="deposit" :class="{ active: institutionMetric === 'deposit' }" @click="institutionMetric = 'deposit'">余额</button>
              <button type="button" data-ranking-metric="average" :class="{ active: institutionMetric === 'average' }" @click="institutionMetric = 'average'">月日均</button>
            </div>
          </div>
          <p class="retail-panel__note retail-institution-note">{{ institutionDepositView.subtitle }} · 单位 {{ institutionMetricUnit }} · 当前筛选月均低于余额 {{ institutionAverageBelowBalanceCount }} 家 · 口径对照，不代表净增</p>
          <div v-if="institutionRows.length" class="retail-institution-table-head" aria-hidden="true">
            <span>机构</span>
            <span>余额<small>{{ institutionMetricUnit }}</small></span>
            <span>月日均<small>{{ institutionMetricUnit }}</small></span>
            <span>差额<small>月均−余额</small></span>
            <span>日期</span>
          </div>
          <ol v-if="institutionRows.length" class="retail-institution-list retail-scroll-region" tabindex="0" aria-label="机构存款对照列表">
            <li
              v-for="(item, index) in institutionPageRows"
              :key="item.orgCode || `${item.name}-${index}`"
              class="retail-institution-row"
              data-testid="retail-institution-row"
              :data-org-code="item.orgCode || ''"
              tabindex="0"
              @click="openInstitution(item)"
              @keydown.enter="openInstitution(item)"
              @keydown.space.prevent="openInstitution(item)"
            >
              <span class="retail-institution-row__name"><strong>{{ item.name || item.orgName || item.orgCode || '—' }}</strong><small>{{ item.institutionType === 'primary' ? '已分类经营机构' : '其他待分类' }}</small></span>
              <strong :data-testid="institutionMetric === 'deposit' ? 'retail-institution-value' : undefined" :class="{ 'is-active': institutionMetric === 'deposit' }">{{ formatAmount(item.deposit, institutionMetricValues).text }}</strong>
              <strong :data-testid="institutionMetric === 'average' ? 'retail-institution-value' : undefined" :class="{ 'is-active': institutionMetric === 'average' }">{{ formatAmount(item.average, institutionMetricValues).text }}</strong>
              <span data-testid="retail-institution-difference" :class="{ 'is-negative': item.difference < 0, 'is-positive': item.difference > 0 }">{{ formatAmount(item.difference, institutionMetricValues).text }}</span>
              <small>{{ item.date || rankingDataDate || '—' }}</small>
            </li>
          </ol>
          <div v-else class="retail-empty">暂无机构存款余额或月日均数据</div>
          <footer v-if="institutionRows.length" class="retail-institution-footer">
            <span>点击机构查看存款、月日均与差额</span>
            <div v-if="institutionPageCount > 1" class="retail-pagination" role="group" aria-label="机构存款对照分页">
              <button type="button" :disabled="institutionPage <= 1" @click="institutionPage -= 1">上一页</button>
              <span>{{ institutionPage }} / {{ institutionPageCount }}</span>
              <button type="button" :disabled="institutionPage >= institutionPageCount" @click="institutionPage += 1">下一页</button>
            </div>
          </footer>
        </article>

        <article v-else class="retail-panel retail-ranking-panel">
          <header class="retail-panel__heading">
            <div>
              <span class="retail-kicker">机构贡献 / 短板</span>
              <h2>{{ rankingOrder === 'leading' ? '领先机构排名' : '短板机构排名' }}</h2>
            </div>
            <span>按同口径机构</span>
          </header>
          <div v-if="filteredRankings.length || loading" class="retail-ranking-toolbar">
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
          <div v-else class="retail-empty">暂无机构存款排名数据源</div>
          <p v-if="filteredRankings.length" class="retail-ranking-hint">点击机构查看存款、净增和风险指标</p>
        </article>
        </template>

        <article class="retail-panel retail-target-panel" :class="{ 'retail-target-panel--empty': !safeModel.targets.length }" data-testid="retail-target-panel">
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
          <div v-else class="retail-target-empty" data-testid="retail-target-empty">
            <strong>目标未接入</strong>
            <span>暂无可复用的有效目标值，待配置后显示完成情况</span>
          </div>
        </article>
      </div>
    </section>
    </template>

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
            <template v-if="hasDepositRankingShape">
              <section class="retail-directory-detail__metrics" aria-label="本机构存款对照">
                <h4>本机构存款对照</h4>
                <div v-if="selectedInstitutionRanking" class="retail-directory-detail__metric-grid">
                  <div><span>余额</span><strong>{{ formatAmount(selectedInstitutionRanking.deposit, institutionMetricValues).text }}</strong><small>{{ institutionMetricUnit }}</small></div>
                  <div><span>月日均</span><strong>{{ formatAmount(selectedInstitutionRanking.average, institutionMetricValues).text }}</strong><small>{{ institutionMetricUnit }}</small></div>
                  <div><span>月均 − 余额</span><strong>{{ formatAmount(selectedInstitutionRanking.difference, institutionMetricValues).text }}</strong><small>口径差额</small></div>
                  <div><span>数据日期</span><strong>{{ selectedInstitutionRanking.date || rankingDataDate || '—' }}</strong><small>来源日期</small></div>
                </div>
                <p v-else>暂无本机构存款数据</p>
              </section>
              <p class="retail-directory-detail__source-note">数值对照，不代表净增；机构类型混合时不提供业务名次。</p>
            </template>
            <template v-else>
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
            </template>
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
import { buildCityMapDetails, cityMapMetricValues } from './cityMapDetails.js';
import { buildProvinceInstitutionMapState } from './provinceInstitutionMapModel.js';
import RetailTrend from './RetailTrend.vue';
import RetailAttentionDetails from './RetailAttentionDetails.vue';
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
import PresentationLayout from '../presentation/layout/PresentationLayout.vue';
import { isConfiguredPresentation } from '../presentation/layout/presentationLayoutModel';
import { provinceGeo } from './geography.js';
import { buildSegmentComparisons } from './retailLeadershipInsights.js';
import {
  buildDepositComparison,
  buildInstitutionDepositView,
  normalizeInstitutionRow
} from './retailDisplayInsights.js';

const props = defineProps({
  model: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  demo: { type: Boolean, default: false },
  backLabel: { type: String, default: '返回分行预览' },
  sourcePresentation: { type: Object, default: () => ({}) }
});
const configuredMetrics = computed(() => buildDisplayMetricsModel(
  props.sourcePresentation?.displayPresentation, safeModel.value
));
const configuredSeriesTables = computed(() => buildDisplaySeriesTableModel(
  props.sourcePresentation?.displayPresentation, safeModel.value
));
const emit = defineEmits(['refresh', 'back', 'configure', 'branch-select', 'business-line-select', 'map-context']);
const presentationLayoutEnabled = computed(() => isConfiguredPresentation(props.sourcePresentation));

const KPI_DEFINITIONS = Object.freeze([
  { key: 'retailAum', label: '零售AUM', unit: '', emptyText: '暂无数据源', emptyTitle: '当前未接入理财、基金、保险等客户金融资产来源' },
  { key: 'retailDeposit', label: '零售一般性存款余额', unit: '亿元' },
  { key: 'retailRevenue', label: '零售营业收入', unit: '亿元' },
  { key: 'retailValueCustomers', label: '价值客户', unit: '', emptyText: '口径未配置', emptyTitle: '当前没有经业务确认的价值客户门槛和数据源' },
  { key: 'retailLoan', label: '个人贷款', unit: '亿元' },
  { key: 'retailNplRate', label: '个贷不良率', unit: '%' },
  { key: 'retailDepositAverage', label: '零售存款月日均', unit: '亿元', emptyText: '暂无数据源', emptyTitle: '当前未接入零售存款月日均来源' }
]);
const rankingMetricOptions = Object.freeze([
  { key: 'aum', label: 'AUM', unit: '亿元' },
  { key: 'increase', label: '较上月净增', unit: '亿元' },
  { key: 'rate', label: '完成率', unit: '%' }
]);
const rankingMetric = ref('aum');
const selectedMapMetricKey = ref('');
const rankingOrder = ref('leading');
const institutionMetric = ref('deposit');
const institutionFilter = ref('');
const institutionPage = ref(1);
const institutionPageSize = 8;
const selectedCityCode = ref('');
const selectedCityName = ref('');
const directoryOpen = ref(false);
const directorySearch = ref('');
const selectedInstitution = ref(null);
const selectedAttention = ref(null);
const directoryDialogRef = ref(null);
const directorySearchRef = ref(null);
const rootRef = ref(null);
const focusBeforeDirectory = ref(null);
const focusBeforeAttention = ref(null);

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
    sourceQualities: {},
    ...source,
    kpis: Array.isArray(source.kpis) ? source.kpis : [],
    trend: Array.isArray(source.trend) ? source.trend : [],
    segments: Array.isArray(source.segments) ? source.segments : [],
    rankings: Array.isArray(source.rankings) ? source.rankings : [],
    attention: Array.isArray(source.attention) ? source.attention : [],
    targets: Array.isArray(source.targets) ? source.targets : [],
    institutions: Array.isArray(source.institutions) ? source.institutions : [],
    issues: Array.isArray(source.issues) ? source.issues : [],
    sourceQualities: source.sourceQualities && typeof source.sourceQualities === 'object' ? source.sourceQualities : {}
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
    change: bound?.change ?? null,
    isBound: Boolean(bound),
    emptyText: definition.emptyText,
    emptyTitle: definition.emptyTitle
  };
}));
const depositKpi = computed(() => findKpi('retailDeposit') || { key: 'retailDeposit', label: '储蓄余额', unit: '亿元', value: null, change: null });
const depositAverage = computed(() => findKpi('retailDepositAverage') || { key: 'retailDepositAverage', label: '储蓄月日均', unit: '亿元', value: null, change: null });
const displayDate = computed(() => safeModel.value.sourceQualities?.retailRanking?.dataDate
  || safeModel.value.sourceQualities?.rankings?.dataDate
  || safeModel.value.sourceQualities?.retailTrend?.dataDate
  || safeModel.value.dataDate
  || '—');
const provinceGeoJson = provinceGeo || null;
const today = computed(() => {
  const now = new Date();
  return `${now.getFullYear()}.${String(now.getMonth() + 1).padStart(2, '0')}.${String(now.getDate()).padStart(2, '0')}`;
});
const rankingMetricInfo = computed(() => rankingMetricOptions.find(item => item.key === rankingMetric.value) || rankingMetricOptions[0]);
const activeMapMetricKey = computed(() => selectedMapMetricKey.value
  || institutionRankingModel.value.activeMetricKey
  || String(mapComponent.value?.content?.mainField || '').trim());
const segmentComparisons = computed(() => buildSegmentComparisons(safeModel.value.segments));
const segmentCoverageLabel = computed(() => `${segmentComparisons.value.length}组有效`);
const hasDepositRankingShape = computed(() => safeModel.value.rankings.some(row => row
  && typeof row === 'object'
  && ['deposit', 'average', 'depositAverage', 'depositBalance'].some(key => Object.prototype.hasOwnProperty.call(row, key))));
const retailMapCityDetails = computed(() => buildCityMapDetails(safeModel.value, { business: 'retail', geoJson: provinceGeoJson }));
const retailMapMetricKey = computed(() => hasDepositRankingShape.value ? institutionMetric.value : rankingMetric.value);
const retailMapMetricLabel = computed(() => hasDepositRankingShape.value
  ? (institutionMetric.value === 'average' ? '零售存款月日均' : '零售存款余额')
  : `零售${rankingMetricInfo.value.label}`);
const retailMapMetricValues = computed(() => cityMapMetricValues(retailMapCityDetails.value, retailMapMetricKey.value));
const retailMapInstitutionState = computed(() => buildProvinceInstitutionMapState(
  provinceGeoJson,
  safeModel.value.institutions
));
const depositComparison = computed(() => buildDepositComparison({
  balance: depositKpi.value?.value,
  average: depositAverage.value?.value
}));
const depositComparisonValues = computed(() => [depositComparison.value.balance, depositComparison.value.average]);
const rankingDataDate = computed(() => {
  const qualities = safeModel.value.sourceQualities || {};
  return qualities?.retailRanking?.dataDate
    || qualities?.rankings?.dataDate
    || qualities?.retailDeposit?.dataDate
    || safeModel.value.dataDate
    || '';
});
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
const institutionDepositView = computed(() => {
  const rows = safeModel.value.rankings.filter(row => !selectedCityCode.value
    || String(row?.cityCode || row?.city_code || '') === selectedCityCode.value);
  return buildInstitutionDepositView(rows, institutionFilter.value || undefined);
});
const institutionRows = computed(() => institutionDepositView.value.filteredRows);
const institutionPageCount = computed(() => Math.max(1, Math.ceil(institutionRows.value.length / institutionPageSize)));
const institutionPageRows = computed(() => {
  const page = Math.min(Math.max(institutionPage.value, 1), institutionPageCount.value);
  const start = (page - 1) * institutionPageSize;
  return institutionRows.value.slice(start, start + institutionPageSize);
});
const institutionMetricValues = computed(() => institutionRows.value
  .map(row => institutionMetric.value === 'average' ? finiteValue(row?.average) : finiteValue(row?.deposit))
  .filter(value => value !== null));
const institutionMetricUnit = computed(() => chooseAmountUnit(institutionMetricValues.value));
const institutionAverageBelowBalanceCount = computed(() => institutionRows.value
  .filter(row => finiteValue(row?.average) !== null
    && finiteValue(row?.deposit) !== null
    && finiteValue(row.average) < finiteValue(row.deposit)).length);
const institutionDifferenceItems = computed(() => institutionRows.value
  .filter(row => finiteValue(row?.difference) !== null && finiteValue(row.difference) < 0)
  .sort((left, right) => left.difference - right.difference)
  .slice(0, 3));
const institutionDifferenceValues = computed(() => institutionDifferenceItems.value.map(row => row.difference));
const attentionItems = computed(() => {
  const sourceItems = safeModel.value.attention.map(item => ({ ...item, isVerification: Boolean(item?.isVerification || item?.type === 'verification' || item?.kind === 'issue') }));
  const dateIssueCodes = new Set(['MIXED_DATES', 'DATE_MISMATCH']);
  const slotLabels = {
    retailDeposit: '零售一般性存款余额',
    retailDepositAverage: '零售存款月日均',
    retailRevenue: '零售营业收入',
    retailLoan: '个人贷款',
    retailNplRate: '个贷不良率',
    retailAum: '零售AUM',
    retailTrend: '零售存款趋势',
    retailRanking: '机构存款对比'
  };
  const issueGroups = new Map();
  safeModel.value.issues
    .filter(item => item && typeof item === 'object')
    .forEach((item, index) => {
      const kpi = safeModel.value.kpis.find(entry => String(entry?.key || '') === String(item.slot || ''));
      const isDateIssue = dateIssueCodes.has(String(item.code || '').toUpperCase());
      const baseLabel = isDateIssue ? '指标统计日期不一致' : (item.label || item.title || item.message || '数据来源待核验');
      const metric = item.metricLabel || item.metricName || item.fieldLabel
        || (isDateIssue ? (kpi?.label || slotLabels[item.slot] || item.slot || '相关指标') : item.field)
        || item.code || '相关指标';
      const actualDate = item.actualDate || item.dataDate || item.date || item.periodDate || kpi?.date || kpi?.dataDate || '';
      const key = String(baseLabel);
      const group = issueGroups.get(key) || {
        first: item,
        label: baseLabel,
        lines: [],
        dates: [],
        index
      };
      const line = `${metric}：${actualDate || '日期待确认'}`;
      if (!group.lines.includes(line)) group.lines.push(line);
      if (actualDate && !group.dates.includes(String(actualDate))) group.dates.push(String(actualDate));
      issueGroups.set(key, group);
    });
  const issueItems = [...issueGroups.values()].map(group => {
    const item = group.first;
    const description = item.detail?.description || item.message || item.description || '来源字段或数据日期需要核对';
    return {
      ...item,
      label: group.lines.length > 1 ? `${group.label}（${group.lines.length}项）` : group.label,
      count: item.count ?? null,
      owner: item.owner || '数据核验',
      deadline: item.deadline || group.dates.join('、') || '待确认',
      verificationSummary: dateIssueCodes.has(String(item.code || '').toUpperCase()) ? group.lines.join('；') : '',
      isVerification: true,
      detail: {
        ...(item.detail || {}),
        description: `${description}；${group.lines.join('；')}`,
        source: item.detail?.source || item.source || '数据质量提示'
      },
      _issueKey: item.key || group.index
    };
  });
  return [...sourceItems, ...issueItems];
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
  const row = safeModel.value.rankings.find(item => String(item?.orgCode || '') === code) || null;
  return hasDepositRankingShape.value && row ? normalizeInstitutionRow(row) : row;
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

function chooseAmountUnit(values = []) {
  const numbers = values.map(finiteValue).filter(value => value !== null);
  const max = numbers.length ? Math.max(...numbers.map(value => Math.abs(value))) : 0;
  return max > 0 && max < 1 ? '万元' : '亿元';
}

function formatAmount(value, values = []) {
  const number = finiteValue(value);
  const unit = chooseAmountUnit(values);
  if (number === null) return { text: '—', unit };
  const scaled = unit === '万元' ? number * 10000 : number;
  return {
    text: new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(scaled),
    unit
  };
}

function displayKpi(kpi) {
  let value=finiteValue(kpi.value),unit=kpi.unit;
  if(value===null)return {text:kpi.isBound ? '暂无有效值' : (kpi.emptyText || '—'),unit:''};
  if(unit==='亿元' && value!==0 && Math.abs(value)<1) {
    value*=10000;unit='万元';
    if(Math.abs(value)<1) {value*=10000;unit='元';}
  } else if(unit==='万户' && value!==0 && Math.abs(value)<1) {value*=10000;unit='户';}
  const text=new Intl.NumberFormat('en-US',{minimumFractionDigits:unit==='户'?0:2,maximumFractionDigits:value!==0&&Math.abs(value)<0.01?8:2}).format(value);
  return {text,unit};
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
    retailNplRate: WarningFilled,
    retailDepositAverage: OfficeBuilding
  }[key] || [Coin, OfficeBuilding, TrendCharts, UserFilled][index % 4]);
}

function kpiTitle(kpi) {
  const date = kpi?.date || kpi?.dataDate || kpi?.periodDate;
  if (date) return `${kpi.label} · 数据日期 ${date}`;
  return kpi?.emptyTitle || '';
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

function syncMapMetric(payload = {}) {
  const key = String(payload?.metricKey || '').trim();
  if (!key) return;
  const options = institutionRankingModel.value.metrics || [];
  if (options.length && !options.some(item => item.metricKey === key)) return;
  selectedMapMetricKey.value = key;
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

function openInstitutionFromMap(orgCode) {
  openInstitution({ orgCode: String(orgCode || '') });
}

function openAttention(item, event) {
  focusBeforeAttention.value = event?.currentTarget || document.activeElement;
  selectedAttention.value = item || null;
}

function closeAttention({ restoreFocus = true } = {}) {
  selectedAttention.value = null;
  const target = focusBeforeAttention.value;
  focusBeforeAttention.value = null;
  if (restoreFocus) nextTick(() => target?.focus?.());
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
  selectedMapMetricKey.value = '';
  rankingMetric.value = 'aum';
  rankingOrder.value = 'leading';
  institutionMetric.value = 'deposit';
  institutionFilter.value = '';
  institutionPage.value = 1;
  closeAttention({ restoreFocus: false });
  closeDirectory();
}

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

const lastScopeSignature = ref(scopeSignature(props.model));
watch(() => props.model, model => {
  const nextSignature = scopeSignature(model);
  const scopeChanged = nextSignature !== lastScopeSignature.value;
  lastScopeSignature.value = nextSignature;
  if (props.error || scopeChanged) {
    clearTransientState();
    return;
  }
  // 运行时刷新会先短暂返回空模型；在 loading 期间保留用户筛选，避免界面闪退。
  if (props.loading) return;
  // 同一授权范围刷新后，只清理已经从新结果中消失的选择。
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
  if (selectedAttention.value) {
    const label = String(selectedAttention.value.label || '');
    if (!attentionItems.value.some(item => String(item?.label || '') === label)) {
      closeAttention({ restoreFocus: false });
    }
  }
  if (institutionPage.value > institutionPageCount.value) institutionPage.value = institutionPageCount.value;
}, { deep: true });
watch(() => props.error, error => { if (error) clearTransientState(); });
</script>

<style src="./retail.scss" lang="scss"></style>
