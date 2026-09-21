<template>
  <main ref="rootRef" class="branch-operating-dashboard" tabindex="-1" aria-label="单支行经营大屏">
    <header class="branch-operating-header">
      <div class="branch-operating-header__identity">
        <span class="branch-operating-eyebrow">Branch operating cockpit</span>
        <h1 data-testid="branch-operating-title">{{ displayName }}</h1>
        <p><span>单支行经营监测</span><span class="branch-operating-header__dot" aria-hidden="true"></span><span>{{ sourceLabel }}</span></p>
      </div>

      <div class="branch-operating-header__context">
        <label class="branch-operating-branch-picker">
          <span class="branch-operating-branch-picker__label">当前支行</span>
          <span class="branch-operating-branch-picker__control">
            <OfficeBuilding aria-hidden="true" />
            <select data-testid="branch-operating-branch-select" :value="safeModel.orgCode || ''" aria-label="选择支行" @change="selectBranch($event.target.value)">
              <option v-if="!safeModel.orgCode" value="">请选择支行</option>
              <option v-for="institution in institutions" :key="institution.orgCode" :value="institution.orgCode">{{ institution.orgName || institution.orgCode }}</option>
            </select>
          </span>
        </label>
        <span class="branch-operating-data-date" data-testid="branch-operating-date">数据日期 <strong>{{ displayDate }}</strong></span>
      </div>

      <div class="branch-operating-header__actions">
        <span class="branch-operating-live-state" :class="{ 'is-error': error, 'is-loading': loading }"><i aria-hidden="true"></i>{{ loading ? '正在取数' : error ? '数据异常' : '经营监测' }}</span>
        <button type="button" class="branch-operating-icon-action" data-action="refresh" aria-label="刷新支行经营大屏" title="刷新" @click="emit('refresh')"><Refresh aria-hidden="true" /></button>
        <button type="button" class="branch-operating-icon-action" data-action="fullscreen" :aria-label="fullscreenLabel" :title="fullscreenLabel" @click="toggleFullscreen"><FullScreen aria-hidden="true" /></button>
        <button type="button" class="branch-operating-icon-action" data-action="back" aria-label="返回大屏中心" title="返回" @click="emit('back')"><Back aria-hidden="true" /></button>
      </div>
    </header>

    <div v-if="loading" class="branch-operating-notice branch-operating-notice--loading" role="status"><Refresh class="branch-operating-notice__icon" aria-hidden="true" />正在刷新经营数据，请稍候</div>
    <div v-if="error" class="branch-operating-notice branch-operating-notice--error" role="alert"><WarningFilled class="branch-operating-notice__icon" aria-hidden="true" />{{ error }}</div>

    <section class="branch-operating-kpis" aria-label="支行核心经营指标">
      <article v-for="(kpi, index) in kpiCards" :key="kpi.key || `kpi-${index}`" class="branch-operating-kpi" data-testid="branch-operating-kpi" :data-kpi-key="kpi.key || `kpi-${index}`" :class="`is-${kpiTone(kpi)}`" :style="{ '--kpi-accent': kpiAccent(index, kpi) }">
        <div class="branch-operating-kpi__topline"><span class="branch-operating-kpi__mark" aria-hidden="true"></span><span class="branch-operating-kpi__label">{{ displayText(kpi.label, '未命名指标') }}</span><span class="branch-operating-kpi__status">{{ statusText(kpi.status) }}</span></div>
        <div class="branch-operating-kpi__value-line"><strong :class="{ 'is-empty': finiteMetric(kpi.value) === null }">{{ ['项', '户', '人'].includes(kpi.unit) ? formatCount(kpi.value) : formatMetric(kpi.value) }}</strong><span>{{ displayText(kpi.unit, '') }}</span></div>
        <div class="branch-operating-kpi__changes"><span :class="changeClass(kpi.yoy)">同比 {{ formatChange(kpi.yoy, kpi) }}</span><span :class="changeClass(kpi.mom)">环比 {{ formatChange(kpi.mom, kpi) }}</span></div>
        <span class="branch-operating-kpi__date">数据日期 {{ metricDate(kpi) }}</span>
      </article>
      <div v-if="!kpiCards.length" class="branch-operating-empty branch-operating-empty--kpis" data-testid="branch-operating-kpis-empty"><DataAnalysis aria-hidden="true" /><span>暂无核心指标数据</span></div>
    </section>

    <section class="branch-operating-main-grid" aria-label="支行经营分析">
      <article class="branch-operating-panel branch-operating-target-panel" data-testid="branch-operating-targets">
        <header class="branch-operating-panel__heading branch-operating-target-panel__heading">
          <div><span class="branch-operating-kicker">Target pulse</span><h2>目标攻坚</h2></div>
          <div class="branch-operating-metric-switch" role="group" aria-label="趋势与目标指标">
            <button v-for="metric in metricOptions" :key="metric.key" type="button" :data-trend-key="metric.key" :class="{ 'is-active': selectedMetric === metric.key }" :disabled="metric.key === 'loan' && !hasLoanTrend && !targets.some(item => normalizedMetric(item.key) === 'loan')" @click="selectMetric(metric.key)">{{ metric.label }}</button>
          </div>
        </header>

        <p v-if="safeModel.targetDate" class="branch-operating-target-date">考核截至 {{ safeModel.targetDate }} · 实际值与目标同口径</p>
        <div class="branch-operating-target-body">
          <div class="branch-operating-target-focus-column">
            <div v-if="targetFocus" class="branch-operating-target-focus" data-testid="branch-operating-target-focus">
              <div class="branch-operating-target-ring" data-testid="branch-operating-target-ring" :style="{ '--ring-progress': `${focusProgress}deg`, '--ring-color': focusColor }" :aria-label="`完成率 ${focusRate === null ? '暂无' : formatRate(focusRate)}`"><strong>{{ focusRate === null ? '—' : formatRate(focusRate) }}</strong><span>完成率</span></div>
              <div class="branch-operating-target-focus__copy">
                <span class="branch-operating-target-focus__eyebrow">当前指标 · {{ displayText(targetFocus.label) }}</span>
                <div class="branch-operating-target-focus__numbers"><span><small>实际</small><b>{{ formatMetric(targetFocus.actual) }}</b><em>{{ displayText(targetFocus.unit, '') }}</em></span><span><small>目标</small><b>{{ formatMetric(targetFocus.target, '目标数据不足') }}</b><em>{{ displayText(targetFocus.unit, '') }}</em></span></div>
                <div class="branch-operating-target-focus__gap" :class="gapTone(focusGap)"><span>缺口</span><strong>{{ formatGap(targetFocus) }}</strong><em>{{ gapText(focusGap) }}</em></div>
              </div>
            </div>
            <div v-else-if="targets.length" class="branch-operating-empty branch-operating-empty--compact" data-testid="branch-operating-gap-empty"><Aim aria-hidden="true" /><span>暂无{{ selectedMetricShortLabel }}目标（{{ selectedMetricLabel }}），仅展示已有来源目标</span></div>
            <div v-if="targetFocus" class="branch-operating-target-bullet" aria-label="实际与目标对比"><span>实际进度</span><i class="branch-operating-target-bullet__track" aria-hidden="true"><b :style="{ width: `${focusBarWidth}%` }"></b><em aria-hidden="true"></em></i><strong>目标 100%</strong></div>
            <p v-if="targets.length && !hasSelectedTarget" class="branch-operating-target-match-empty" data-testid="branch-operating-target-match-empty">暂无{{ selectedMetricShortLabel }}目标（{{ selectedMetricLabel }}），仅展示已有来源目标</p>
            <div v-if="!targets.length" class="branch-operating-empty" data-testid="branch-operating-targets-empty"><Aim aria-hidden="true" /><span>暂无经营目标数据</span></div>
          </div>
          <div v-if="targets.length > 1" class="branch-operating-target-list" tabindex="0" role="region" aria-label="支行目标列表">
            <div v-for="(target, index) in targets" :key="`${target.key || target.label || 'target'}-${index}`" class="branch-operating-target-row" :class="{ 'is-active': targetMatches(target) }" :data-target-key="target.key || ''">
              <div class="branch-operating-target-row__topline"><strong>{{ displayText(target.label) }}</strong><span v-if="targetRate(target) !== null" :class="rateClass(targetRate(target))">{{ formatRate(targetRate(target)) }}</span><span v-else class="is-missing">完成率不足</span></div>
              <div class="branch-operating-target-row__numbers"><span>实际 <b :class="{ 'is-negative': finiteMetric(target.actual) !== null && finiteMetric(target.actual) < 0 }">{{ formatMetric(target.actual) }}</b> <small>{{ displayText(target.unit, '') }}</small></span><span>目标 <b>{{ formatMetric(target.target, '目标数据不足') }}</b> <small>{{ displayText(target.unit, '') }}</small></span><span>差额 <b :class="gapTone(targetGap(target))">{{ formatGap(target) }}</b></span></div>
              <div class="branch-operating-target-row__bar" aria-hidden="true"><i :class="{ 'is-negative': targetRate(target) !== null && targetRate(target) < 0 }" :style="{ width: `${progressWidth(target)}%` }"></i></div>
            </div>
          </div>
        </div>
      </article>

      <article class="branch-operating-panel branch-operating-composition-panel" data-testid="branch-operating-composition">
        <header class="branch-operating-panel__heading"><div><span class="branch-operating-kicker">{{ compositionRows.length ? 'Business mix' : 'Point vs average' }}</span><h2 data-testid="branch-operating-composition-title">{{ compositionRows.length ? '业务构成' : periodRows.length ? '时点与月均对照' : '业务构成' }}</h2></div><span class="branch-operating-panel__meta">{{ compositionRows.length ? '来源构成 · 按值展示' : '同日金额 · 非增量' }}</span></header>
        <div v-if="compositionRows.length" class="branch-operating-composition-list" tabindex="0" role="region" aria-label="业务结构列表">
          <div v-for="(item, index) in compositionRows" :key="`${item.name || 'composition'}-${index}`" class="branch-operating-composition-row"><div class="branch-operating-composition-row__label"><i :style="{ backgroundColor: palette[index % palette.length] }" aria-hidden="true"></i><strong>{{ displayText(item.name) }}</strong></div><div class="branch-operating-composition-row__bar" aria-hidden="true"><i :style="{ width: `${barWidth(item.value, compositionMax)}%`, backgroundColor: palette[index % palette.length] }"></i></div><span class="branch-operating-composition-row__value">{{ formatMetric(item.value) }} <small>{{ displayText(item.unit, '') }}</small></span></div>
        </div>
        <div v-if="periodRows.length" class="branch-operating-period-compare" :class="{ 'branch-operating-period-compare--secondary': compositionRows.length }" data-testid="branch-operating-period-compare">
          <p class="branch-operating-period-compare__note">时点余额与月均金额同一日期对照，不能相减为净增</p>
          <div v-for="row in periodRows" :key="row.key" class="branch-operating-period-row">
            <div class="branch-operating-period-row__head"><strong>{{ row.label }}</strong><span>{{ row.date }}</span></div>
            <div class="branch-operating-period-row__track" aria-hidden="true"><i class="is-point" :style="{ width: `${periodWidth(row.point)}%` }"></i><b class="is-average" :style="{ left: `${periodWidth(row.average)}%` }"></b></div>
            <div class="branch-operating-period-row__values"><span><i class="branch-operating-period-dot is-point"></i>时点 <b>{{ formatMetric(row.point) }}</b> {{ row.unit }}</span><span><i class="branch-operating-period-dot is-average"></i>月均 <b>{{ formatMetric(row.average) }}</b> {{ row.unit }}</span></div>
          </div>
        </div>
        <div v-if="!compositionRows.length && !periodRows.length" class="branch-operating-empty branch-operating-empty--compact" data-testid="branch-operating-composition-empty"><DataAnalysis aria-hidden="true" /><span>{{ sectionGap('comparison', sectionGap('composition', '暂无业务结构数据')) }}</span></div>
      </article>

      <article class="branch-operating-panel branch-operating-trend-panel" data-testid="branch-operating-trend">
        <header class="branch-operating-panel__heading"><div><span class="branch-operating-kicker">Balance history</span><h2>{{ validTrendPeriods < 2 ? '历史余额快照' : '余额趋势' }}</h2></div><div class="branch-operating-trend__legend" aria-label="趋势图例"><span v-for="series in visibleSeries" :key="series.key"><i :style="{ backgroundColor: series.color }"></i>{{ series.label }}</span><em>单位：{{ trendUnit }} · 可用 {{ validTrendPeriods }}/{{ trendRows.length }} 期</em></div></header>
        <p v-if="hasTrendChart && validTrendPeriods < 2" class="branch-operating-history-note">仅有1期余额，暂不能判断趋势；缺失期不补零。</p>
        <div v-if="hasTrendChart" class="branch-operating-trend__chart-wrap"><v-chart class="branch-operating-trend__chart" :option="trendOption" autoresize aria-label="存款贷款余额趋势图" /></div>
        <div v-else class="branch-operating-empty branch-operating-empty--compact" data-testid="branch-operating-trend-empty"><TrendCharts aria-hidden="true" /><span>暂无经营趋势数据</span></div>
      </article>

      <article class="branch-operating-panel branch-operating-marketing-panel" data-testid="branch-operating-marketing">
        <header class="branch-operating-panel__heading"><div><span class="branch-operating-kicker">Customer pipeline</span><h2>客户营销</h2></div><span class="branch-operating-panel__meta">状态互斥 · SLA独立</span></header>
        <div v-if="marketingRows.length" class="branch-operating-marketing-layout" :class="{ 'is-bars-only': !marketingDonutRows.length }">
          <div v-if="marketingDonutRows.length" class="branch-operating-marketing-chart" data-testid="branch-operating-marketing-chart"><v-chart :option="marketingOption" autoresize aria-label="客户营销任务状态分布环图" /></div>
          <div class="branch-operating-marketing-bars" data-testid="branch-operating-marketing-bars" tabindex="0" role="region" aria-label="客户营销数量条">
            <div v-for="item in marketingBarRows" :key="item.id" class="branch-operating-marketing-row" :class="{ 'is-sla': item.isSla }"><span class="branch-operating-marketing-row__dot" :style="{ backgroundColor: item.color }" aria-hidden="true"></span><span class="branch-operating-marketing-row__label">{{ item.label }}</span><div class="branch-operating-marketing-row__track" aria-hidden="true"><i :style="{ width: `${countWidth(item.count, marketingBarMax)}%`, backgroundColor: item.color }"></i></div><strong>{{ formatCount(item.count) }}</strong></div>
          </div>
        </div>
        <div v-else class="branch-operating-empty branch-operating-empty--compact" data-testid="branch-operating-marketing-empty"><UserFilled aria-hidden="true" /><span>{{ sectionGap('marketing', '暂无客户营销数据') }}</span></div>
      </article>
    </section>

    <section class="branch-operating-support-grid" aria-label="支行协同事项">
      <article class="branch-operating-support-card" data-testid="branch-operating-projects">
        <header class="branch-operating-panel__heading"><div><span class="branch-operating-kicker">Priority projects</span><h2>重点项目</h2></div><span class="branch-operating-panel__meta">{{ projects.length ? `${projects.length} 项` : '未接入' }}</span></header>
        <div v-if="projects.length" class="branch-operating-project-list" data-testid="branch-operating-project-table" tabindex="0" role="region" aria-label="重点项目列表"><div class="branch-operating-project-table-head" aria-hidden="true"><span>项目</span><span>状态</span><span>金额</span><span>负责人</span><span>剩余</span></div><article v-for="(project, index) in projects" :key="`${project.name || 'project'}-${index}`" class="branch-operating-project" data-testid="branch-operating-project"><div class="branch-operating-project__topline"><strong>{{ displayText(project.name) }}</strong><span>{{ displayText(project.status, '状态未提供') }}</span></div><div class="branch-operating-project__meta"><span><b :class="{ 'is-missing': finiteMetric(project.amount) === null }">{{ formatMetric(project.amount) }}</b> {{ displayText(project.unit, '') }}</span><span>{{ displayText(project.owner, '未提供') }}</span><span>{{ formatDays(project.days) }}</span></div></article></div>
        <div v-else class="branch-operating-empty branch-operating-empty--compact" data-testid="branch-operating-projects-empty"><Briefcase aria-hidden="true" /><span>{{ sectionGap('projects', '暂无重点项目数据') }}</span></div>
      </article>

      <article class="branch-operating-support-card" data-testid="branch-operating-attention">
        <header class="branch-operating-panel__heading"><div><span class="branch-operating-kicker">Coordination queue</span><h2>经营关注与协调</h2></div><span class="branch-operating-panel__meta">考核关注</span></header>
        <div v-if="attention.length" class="branch-operating-attention-list" tabindex="0" role="region" aria-label="待协调事项列表"><div v-for="(item, index) in attention" :key="`${item.label || 'attention'}-${index}`" class="branch-operating-attention-row"><WarningFilled aria-hidden="true" /><span>{{ displayText(item.label) }}</span><strong>{{ formatCount(item.count) }}</strong></div></div>
        <div v-else class="branch-operating-empty branch-operating-empty--compact" data-testid="branch-operating-attention-empty"><WarningFilled aria-hidden="true" /><span>经营关注数据未接入/暂不可用</span></div>
      </article>

      <article class="branch-operating-support-card" data-testid="branch-operating-teams">
        <header class="branch-operating-panel__heading"><div><span class="branch-operating-kicker">Team contribution</span><h2>团队贡献</h2></div><span class="branch-operating-panel__meta">{{ teams.length ? `${teams.length} 组` : '未接入' }}</span></header>
        <div v-if="teams.length" class="branch-operating-team-table-wrap" tabindex="0" role="region" aria-label="团队贡献表"><table class="branch-operating-team-table"><thead><tr><th scope="col">团队</th><th scope="col">完成率</th><th scope="col">{{ teamIncreaseLabel }}</th><th scope="col">待办</th><th scope="col">贡献状态</th></tr></thead><tbody><tr v-for="(team, index) in teams" :key="`${team.name || 'team'}-${index}`" data-testid="branch-operating-team"><th scope="row"><span class="branch-operating-team-table__dot" aria-hidden="true"></span>{{ displayText(team.name) }}</th><td :class="rateClass(team.rate)">{{ formatRate(team.rate) }}</td><td :class="changeClass(team.increase)">{{ formatTeamIncrease(team) }}</td><td>{{ formatCount(team.pending) }}</td><td><span class="branch-operating-team-table__state" :class="teamTone(team)">{{ teamState(team) }}</span></td></tr></tbody></table></div>
        <div v-else class="branch-operating-empty branch-operating-empty--compact" data-testid="branch-operating-teams-empty"><UserFilled aria-hidden="true" /><span>{{ sectionGap('teams', '暂无团队贡献数据') }}</span></div>
      </article>
    </section>

    <footer class="branch-operating-footer"><span>数据来源 {{ sourceLabel }}</span><span class="branch-operating-footer__hint">指标空值按“—”展示，历史对比不足时不作推算</span></footer>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { use } from 'echarts/core';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import { CanvasRenderer } from 'echarts/renderers';
import { AxisPointerComponent, GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { Aim, Back, Briefcase, DataAnalysis, FullScreen, OfficeBuilding, Refresh, TrendCharts, UserFilled, WarningFilled } from '@element-plus/icons-vue';

use([CanvasRenderer, BarChart, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, AxisPointerComponent]);

const palette = Object.freeze(['#47e9ef', '#a77bff', '#ff7486', '#ffc45e', '#5896ff']);
const statusPalette = Object.freeze({ PENDING: '#ffc45e', IN_PROGRESS: '#47e9ef', SUCCESS: '#a77bff', CANCELLED: '#ff7486' });
const statusLabels = Object.freeze({ PENDING: '待处理', IN_PROGRESS: '进行中', SUCCESS: '已完成', CANCELLED: '已取消' });
const props = defineProps({ model: { type: Object, default: () => ({}) }, loading: { type: Boolean, default: false }, error: { type: String, default: '' } });
const emit = defineEmits(['refresh', 'back', 'branch-select']);
const rootRef = ref(null); const selectedMetric = ref('deposit'); const trendMode = ref('all'); const isFullscreen = ref(false);
const safeModel = computed(() => (props.model && typeof props.model === 'object' ? props.model : {}));
function listOf(key) { return computed(() => (Array.isArray(safeModel.value[key]) ? safeModel.value[key].filter(item => item && typeof item === 'object') : [])); }
const institutions = listOf('institutions'); const composition = listOf('composition'); const marketing = listOf('marketing'); const targets = listOf('targets'); const projects = listOf('projects'); const attention = listOf('attention'); const teams = listOf('teams');
const teamIncreaseUnit = computed(() => teams.value.map(team => normalizeIncreaseUnit(team?.increaseUnit)).find(Boolean) || '');
const teamIncreaseLabel = computed(() => teamIncreaseUnit.value ? '存款增量' : '增幅');
const kpiCards = computed(() => (Array.isArray(safeModel.value.kpis) ? safeModel.value.kpis.filter(item => item && typeof item === 'object').slice(0, 6) : []));
const allKpis = computed(() => (Array.isArray(safeModel.value.kpis) ? safeModel.value.kpis.filter(item => item && typeof item === 'object') : []));
const displayName = computed(() => displayText(safeModel.value.orgName, '支行经营大屏')); const displayDate = computed(() => displayText(safeModel.value.dataDate, '未提供')); const sourceLabel = computed(() => displayText(safeModel.value.sourceLabel, '数据来源未提供')); const isTestSource = computed(() => /TEST|测试/i.test(String(safeModel.value.sourceLabel || '')));
const gaps = computed(() => (safeModel.value.gaps && typeof safeModel.value.gaps === 'object' ? safeModel.value.gaps : {})); const metricLabels = computed(() => (safeModel.value.metricLabels && typeof safeModel.value.metricLabels === 'object' ? safeModel.value.metricLabels : {})); const fullscreenLabel = computed(() => (isFullscreen.value ? '退出全屏' : '进入全屏'));
const metricOptions = computed(() => [{ key: 'deposit', label: displayText(metricLabels.value.deposit, '存款余额') }, { key: 'loan', label: displayText(metricLabels.value.loan, '贷款余额') }]); const selectedMetricLabel = computed(() => metricOptions.value.find(item => item.key === selectedMetric.value)?.label || selectedMetric.value); const selectedMetricShortLabel = computed(() => selectedMetric.value === 'loan' ? '贷款' : '存款');
const trendUnit = computed(() => safeModel.value.trendUnit || '亿元');
const trendRows = computed(() => (Array.isArray(safeModel.value.trend) ? safeModel.value.trend.filter(item => item && typeof item === 'object') : []));
const hasDepositTrend = computed(() => trendRows.value.some(row => finiteMetric(row.deposit) !== null)); const hasLoanTrend = computed(() => trendRows.value.some(row => finiteMetric(row.loan) !== null));
const seriesDefinitions = computed(() => { const definitions = []; if (hasDepositTrend.value) definitions.push({ key: 'deposit', label: displayText(metricLabels.value.deposit, '存款余额'), color: '#47e9ef', type: hasLoanTrend.value ? 'bar' : 'line', area: !hasLoanTrend.value }); if (hasLoanTrend.value) definitions.push({ key: 'loan', label: displayText(metricLabels.value.loan, '贷款余额'), color: '#a77bff', type: 'line', area: false }); return definitions; });
const visibleSeries = computed(() => { const definitions = trendMode.value === 'all' ? seriesDefinitions.value : seriesDefinitions.value.filter(item => item.key === trendMode.value); return definitions.filter(item => trendRows.value.some(row => finiteMetric(row[item.key]) !== null)); });
const validTrendPeriods = computed(() => trendRows.value.filter(row => visibleSeries.value.some(series => finiteMetric(row[series.key]) !== null)).length);
const hasTrendChart = computed(() => trendRows.value.some(row => String(row.date ?? '').trim()) && visibleSeries.value.length > 0);
const trendOption = computed(() => ({
  animation: true,
  color: visibleSeries.value.map(item => item.color),
  grid: { top: 28, right: 16, bottom: 26, left: 40, containLabel: true },
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'line' },
    backgroundColor: '#07183e',
    borderColor: 'rgba(71, 233, 239, .36)',
    textStyle: { color: '#edf4ff', fontSize: 12 },
    formatter: params => {
      const items = Array.isArray(params) ? params : [params];
      const date = items[0]?.axisValue || '';
      return [date, ...items.map(item => `${item.seriesName}：${item.value == null ? '—' : formatMetric(item.value)} ${trendUnit.value}`)].join('<br/>');
    }
  },
  legend: { show: false },
  xAxis: {
    type: 'category', boundaryGap: true, data: trendRows.value.map(row => String(row.date ?? '')),
    axisLine: { lineStyle: { color: 'rgba(145, 169, 216, .34)' } }, axisTick: { show: false }, axisLabel: { color: '#91a9d8', fontSize: 10 }
  },
  yAxis: {
    type: 'value', name: trendUnit.value,
    nameTextStyle: { color: '#91a9d8', fontSize: 10, padding: [0, 0, 0, -22] },
    axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#91a9d8', fontSize: 10 }, splitNumber: 3,
    splitLine: { lineStyle: { color: 'rgba(119, 163, 255, .13)' } }
  },
  series: visibleSeries.value.map(item => ({
    name: item.label, type: item.type, smooth: false, connectNulls: false, showSymbol: item.type === 'line', symbol: 'circle', symbolSize: 5, barMaxWidth: 22,
    itemStyle: { color: item.color, borderRadius: item.type === 'bar' ? [4, 4, 0, 0] : 0 }, lineStyle: { color: item.color, width: 2 },
    areaStyle: item.area ? { color: `${item.color}24` } : undefined, data: trendRows.value.map(row => finiteMetric(row[item.key]))
  }))
}));

const targetMatches = target => normalizedMetric(target?.key) === selectedMetric.value;
const hasSelectedTarget = computed(() => targets.value.some(target => targetMatches(target)));
const targetFocus = computed(() => targets.value.find(target => targetMatches(target)) || null);
const focusRate = computed(() => targetFocus.value ? targetRate(targetFocus.value) : null);
const focusGap = computed(() => targetFocus.value ? targetGap(targetFocus.value) : null);
const focusProgress = computed(() => focusRate.value === null ? 0 : Math.max(0, Math.min(100, focusRate.value)) * 3.6);
const focusBarWidth = computed(() => focusRate.value === null ? 0 : Math.max(0, Math.min(100, focusRate.value)));
const focusColor = computed(() => gapTone(focusGap.value) === 'is-shortfall' ? '#ff7486' : gapTone(focusGap.value) === 'is-over' ? '#47e9ef' : '#ffc45e');
const periodRows = computed(() => {
  const pairs = [
    { pointKey: 'corpDeposit', averageKey: 'corpDepositAverage', label: '对公存款' },
    { pointKey: 'deposit', averageKey: 'depositAverage', label: '存款' }
  ];
  for (const pair of pairs) {
    const point = findKpi([pair.pointKey]);
    const average = findKpi([pair.averageKey]);
    if (!point || !average) continue;
    const pointValue = finiteMetric(point.value);
    const averageValue = finiteMetric(average.value);
    const pointDate = metricDate(point);
    const averageDate = metricDate(average);
    const pointUnit = String(point.unit ?? '').trim();
    const averageUnit = String(average.unit ?? '').trim();
    if (pointValue === null || averageValue === null || !pointDate || pointDate === '未提供' || pointDate !== averageDate || !pointUnit || pointUnit !== averageUnit) continue;
    return [{ key: pair.pointKey, label: pair.label, point: pointValue, average: averageValue, unit: pointUnit, date: pointDate }];
  }
  return [];
});
const periodMax = computed(() => Math.max(0, ...periodRows.value.flatMap(row => [Math.abs(row.point), Math.abs(row.average)])));
const compositionRows = computed(() => composition.value.filter(item => finiteMetric(item.value) !== null));
const compositionMax = computed(() => Math.max(0, ...compositionRows.value.map(item => Math.abs(finiteMetric(item.value) ?? 0))));
const marketingRows = computed(() => marketing.value.map((item, index) => {
  const key = marketingStatusKey(item);
  const isSla = key === 'SLA_WARNING';
  return { ...item, id: `${key || 'other'}-${index}`, key, isSla, label: displayText(item.label, key === 'SLA_WARNING' ? 'SLA预警' : key ? statusLabels[key] : '营销事项'), count: finiteMetric(item.count), color: isSla ? '#ff7486' : statusPalette[key] || palette[index % palette.length] };
}));
const marketingStatusRows = computed(() => marketingRows.value.filter(item => Object.prototype.hasOwnProperty.call(statusLabels, item.key) && item.count !== null));
const marketingDonutRows = computed(() => { const rows = marketingStatusRows.value; const complete = Object.keys(statusLabels).every(key => rows.some(item => item.key === key)); return complete && rows.every(item => item.count >= 0) && rows.reduce((sum, item) => sum + item.count, 0) > 0 ? rows : []; });
const marketingBarRows = computed(() => marketingRows.value.filter(item => item.count !== null || item.isSla)); const marketingBarMax = computed(() => Math.max(0, ...marketingBarRows.value.map(item => Math.abs(item.count ?? 0))));
const marketingOption = computed(() => ({
  animation: true,
  tooltip: { trigger: 'item', backgroundColor: '#07183e', borderColor: 'rgba(167, 123, 255, .4)', textStyle: { color: '#edf4ff', fontSize: 12 }, formatter: params => `${params.name}：${formatCount(params.value)}` },
  series: [{
    name: '营销状态', type: 'pie', stillShowZeroSum: false, radius: ['54%', '76%'], center: ['50%', '50%'], avoidLabelOverlap: true,
    label: { color: '#edf4ff', fontSize: 10, formatter: '{b}' }, labelLine: { lineStyle: { color: 'rgba(237, 244, 255, .4)' } }, itemStyle: { borderColor: '#07183e', borderWidth: 3 },
    data: marketingDonutRows.value.map(item => ({ name: item.label, value: item.count, itemStyle: { color: item.color } }))
  }]
}));

watch(() => safeModel.value, () => { if (!targets.value.some(target => targetMatches(target))) selectedMetric.value = 'deposit'; trendMode.value = 'all'; }, { deep: true });
function finiteMetric(value) { if (value === null || value === undefined || value === '' || typeof value === 'boolean' || (typeof value === 'string' && value.trim() === '')) return null; const parsed = typeof value === 'number' ? value : Number(value); return Number.isFinite(parsed) ? parsed : null; }
function displayText(value, fallback = '—') { return value === null || value === undefined || value === '' ? fallback : String(value); }
function metricDate(kpi) { return displayText(kpi?.dataDate ?? kpi?.date ?? kpi?.periodDate ?? safeModel.value.dataDate, '未提供'); }
function findKpi(keys) { return allKpis.value.find(item => keys.includes(String(item.key))); }
function formatMetric(value, missing = '—') { const number = finiteMetric(value); if (number === null) return missing; const small = number !== 0 && Math.abs(number) < 0.01; return new Intl.NumberFormat('zh-CN', { minimumFractionDigits: small ? 4 : 2, maximumFractionDigits: small ? 4 : 2 }).format(number); }
function formatCount(value) { const number = finiteMetric(value); return number === null ? '—' : new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 }).format(number); }
function formatRate(value) { const number = finiteMetric(value); return number === null ? '—' : `${number.toFixed(2)}%`; }
function formatSignedRate(value) { const number = finiteMetric(value); return number === null ? '—' : `${number > 0 ? '+' : ''}${number.toFixed(2)}%`; }
function normalizeIncreaseUnit(value) { const unit = String(value ?? '').trim(); return ['', '%', '％', '百分比', '百分点'].includes(unit) ? '' : unit; }
function formatSignedMetric(value, unit) { const number = finiteMetric(value); return number === null ? '—' : `${number > 0 ? '+' : ''}${formatMetric(number)}${unit ? ` ${unit}` : ''}`; }
function formatTeamIncrease(team) { const unit = normalizeIncreaseUnit(team?.increaseUnit) || teamIncreaseUnit.value; return unit ? formatSignedMetric(team?.increase, unit) : formatSignedRate(team?.increase); }
function isRatioMetric(kpi) { return String(kpi?.unit || '').includes('%') || ['rate', 'quality', 'marketing'].includes(String(kpi?.key)); }
function formatChange(value, kpi) { const number = finiteMetric(value); if (number === null) return '历史数据不足'; return `${number > 0 ? '+' : ''}${number.toFixed(2)}${isRatioMetric(kpi) ? ' 个百分点' : '%'}`; }
function changeClass(value) { const number = finiteMetric(value); return number === null ? 'is-missing' : number > 0 ? 'is-positive' : number < 0 ? 'is-negative' : 'is-flat'; }
function statusText(value) { const text = String(value ?? '').trim(); if (!text) return isTestSource.value ? '测试数据' : '状态未提供'; return { healthy: '正常', normal: '正常', warning: '需关注', risk: '风险', missing: '数据缺失', positive: '增长', negative: '下降' }[text.toLowerCase()] || text; }
function kpiTone(kpi) { const status = String(kpi?.status ?? '').toLowerCase(); if (status.includes('风险') || status.includes('关注') || status === 'warning' || status === 'risk') return 'warning'; return finiteMetric(kpi?.value) === null ? 'missing' : 'ready'; }
function kpiAccent(index, kpi) { return kpiTone(kpi) === 'warning' ? '#ff7486' : palette[index % palette.length]; }
function sectionGap(key, fallback) { const value = gaps.value[key]; return typeof value === 'string' && value.trim() ? value : fallback; }
function barWidth(value, max) { const number = finiteMetric(value); return number === null || max <= 0 ? 0 : Math.max(0, Math.min(100, Math.abs(number) / max * 100)); }
function countWidth(value, max) { return barWidth(value, max); } function periodWidth(value) { return barWidth(value, periodMax.value); }
function selectMetric(key) { selectedMetric.value = key; trendMode.value = key; }
function normalizedMetric(key) { const value = String(key || '').toLowerCase(); return value.includes('loan') ? 'loan' : value.includes('deposit') ? 'deposit' : value; }
function targetRate(target) { const actual = finiteMetric(target?.actual); const goal = finiteMetric(target?.target); if (actual !== null && goal !== null && goal !== 0) return actual / goal * 100; return finiteMetric(target?.rate); }
function targetGap(target) { const actual = finiteMetric(target?.actual); const goal = finiteMetric(target?.target); if (actual !== null && goal !== null) return goal - actual; return finiteMetric(target?.gap); }
function formatGap(target) { const gap = targetGap(target); const unit = displayText(target?.unit, ''); return gap === null ? '差额不足' : `${formatMetric(gap)} ${unit}`.trim(); }
function gapTone(value) { const gap = finiteMetric(value); return gap === null || gap === 0 ? 'is-neutral' : gap > 0 ? 'is-shortfall' : 'is-over'; }
function gapText(value) { const gap = finiteMetric(value); return gap === null ? '目标数据不足' : gap > 0 ? '低于目标' : gap < 0 ? '超额完成' : '目标持平'; }
function progressWidth(target) { const rate = targetRate(target); return rate === null ? 0 : Math.max(0, Math.min(100, rate)); }
function rateClass(value) { const number = finiteMetric(value); return number === null ? 'is-missing' : number < 0 ? 'is-negative' : number >= 100 ? 'is-positive' : 'is-neutral'; }
function formatDays(value) { const number = finiteMetric(value); return number === null ? '未提供' : `${formatCount(number)} 天`; }
function teamTone(team) { const rate = finiteMetric(team?.rate); return rate === null ? 'is-missing' : rate >= 100 ? 'is-positive' : rate < 60 ? 'is-warning' : 'is-neutral'; }
function teamState(team) { const rate = finiteMetric(team?.rate); return rate === null ? '完成率未提供' : rate >= 100 ? '达成' : rate < 60 ? '需协调' : '推进中'; }
function marketingStatusKey(item) { const raw = String(item?.key ?? item?.status ?? item?.label ?? '').trim().toUpperCase(); if (raw.includes('SLA') || raw.includes('预警')) return 'SLA_WARNING'; if (raw === 'PENDING' || raw.includes('待处理') || raw.includes('待触达')) return 'PENDING'; if (raw === 'IN_PROGRESS' || raw.includes('进行中')) return 'IN_PROGRESS'; if (raw === 'SUCCESS' || raw.includes('完成') || raw.includes('成功')) return 'SUCCESS'; if (raw === 'CANCELLED' || raw.includes('取消')) return 'CANCELLED'; return ''; }
function selectBranch(value) { const code = String(value || '').trim(); if (code) emit('branch-select', code); }
async function toggleFullscreen() { try { if (document.fullscreenElement) await document.exitFullscreen?.(); else await rootRef.value?.requestFullscreen?.(); } catch { /* fullscreen is optional */ } }
function onFullscreenChange() { isFullscreen.value = Boolean(document.fullscreenElement); }
onMounted(() => document.addEventListener('fullscreenchange', onFullscreenChange)); onBeforeUnmount(() => document.removeEventListener('fullscreenchange', onFullscreenChange));
</script>

<style src="./branchOperating.scss" lang="scss"></style>
