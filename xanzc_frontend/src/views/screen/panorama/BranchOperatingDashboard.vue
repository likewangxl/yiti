<template>
  <main
    ref="rootRef"
    class="branch-operating-dashboard"
    tabindex="-1"
    aria-label="单支行经营大屏"
  >
    <header class="branch-operating-header">
      <div class="branch-operating-header__identity">
        <span class="branch-operating-eyebrow">Branch operating cockpit</span>
        <h1 data-testid="branch-operating-title">{{ displayName }}</h1>
        <p>
          <span>单支行经营监测</span>
          <span class="branch-operating-header__dot" aria-hidden="true"></span>
          <span>{{ sourceLabel }}</span>
        </p>
      </div>

      <div class="branch-operating-header__context">
        <label class="branch-operating-branch-picker">
          <span class="branch-operating-branch-picker__label">当前支行</span>
          <span class="branch-operating-branch-picker__control">
            <OfficeBuilding aria-hidden="true" />
            <select
              data-testid="branch-operating-branch-select"
              :value="safeModel.orgCode || ''"
              aria-label="选择支行"
              @change="selectBranch($event.target.value)"
            >
              <option v-if="!safeModel.orgCode" value="">请选择支行</option>
              <option v-for="institution in institutions" :key="institution.orgCode" :value="institution.orgCode">
                {{ institution.orgName || institution.orgCode }}
              </option>
            </select>
          </span>
        </label>
        <span class="branch-operating-data-date" data-testid="branch-operating-date">
          数据日期 <strong>{{ displayDate }}</strong>
        </span>
      </div>

      <div class="branch-operating-header__actions">
        <span class="branch-operating-live-state" :class="{ 'is-error': error, 'is-loading': loading }">
          <i aria-hidden="true"></i>
          {{ loading ? '正在取数' : error ? '数据异常' : '经营监测' }}
        </span>
        <button type="button" class="branch-operating-icon-action" data-action="refresh" aria-label="刷新支行经营大屏" title="刷新" @click="emit('refresh')">
          <Refresh aria-hidden="true" />
        </button>
        <button type="button" class="branch-operating-icon-action" data-action="fullscreen" :aria-label="fullscreenLabel" :title="fullscreenLabel" @click="toggleFullscreen">
          <FullScreen aria-hidden="true" />
        </button>
        <button type="button" class="branch-operating-icon-action" data-action="back" aria-label="返回大屏中心" title="返回" @click="emit('back')">
          <Back aria-hidden="true" />
        </button>
      </div>
    </header>

    <div v-if="loading" class="branch-operating-notice branch-operating-notice--loading" role="status">
      <Refresh class="branch-operating-notice__icon" aria-hidden="true" />
      正在刷新经营数据，请稍候
    </div>
    <div v-if="error" class="branch-operating-notice branch-operating-notice--error" role="alert">
      <WarningFilled class="branch-operating-notice__icon" aria-hidden="true" />
      {{ error }}
    </div>

    <section class="branch-operating-kpis" aria-label="支行核心经营指标">
      <article
        v-for="(kpi, index) in kpiCards"
        :key="kpi.key || `kpi-${index}`"
        class="branch-operating-kpi"
        data-testid="branch-operating-kpi"
        :data-kpi-key="kpi.key || `kpi-${index}`"
        :class="`is-${kpiTone(kpi)}`"
      >
        <div class="branch-operating-kpi__topline">
          <span class="branch-operating-kpi__icon" aria-hidden="true">
            <component :is="kpiIcon(kpi.key, index)" />
          </span>
          <span class="branch-operating-kpi__label">{{ displayText(kpi.label, '未命名指标') }}</span>
          <span class="branch-operating-kpi__status" :class="`is-${kpiTone(kpi)}`">{{ statusText(kpi.status) }}</span>
        </div>
        <div class="branch-operating-kpi__value-line">
          <strong :class="{ 'is-empty': finiteMetric(kpi.value) === null }">{{ formatMetric(kpi.value) }}</strong>
          <span>{{ displayText(kpi.unit, '') }}</span>
        </div>
        <div class="branch-operating-kpi__changes">
          <span :class="changeClass(kpi.yoy)">同比 {{ formatChange(kpi.yoy) }}</span>
          <span :class="changeClass(kpi.mom)">环比 {{ formatChange(kpi.mom) }}</span>
        </div>
        <span class="branch-operating-kpi__date">数据日期 {{ metricDate(kpi) }}</span>
      </article>
      <div v-if="!kpiCards.length" class="branch-operating-empty branch-operating-empty--kpis" data-testid="branch-operating-kpis-empty">
        <DataAnalysis aria-hidden="true" />
        <span>暂无核心指标数据</span>
      </div>
    </section>

    <section class="branch-operating-main-grid" aria-label="支行经营分析">
      <div class="branch-operating-column branch-operating-column--left">
        <article class="branch-operating-panel branch-operating-structure-panel" data-testid="branch-operating-composition">
          <header class="branch-operating-panel__heading">
            <div>
              <span class="branch-operating-kicker">Business mix</span>
              <h2>业务结构</h2>
            </div>
            <span class="branch-operating-panel__meta">占比 / 规模</span>
          </header>
          <div v-if="composition.length" class="branch-operating-composition-list" tabindex="0" role="region" aria-label="业务结构列表">
            <div v-for="(item, index) in composition" :key="`${item.name || 'composition'}-${index}`" class="branch-operating-composition-row">
              <div class="branch-operating-composition-row__label">
                <i :style="{ backgroundColor: palette[index % palette.length] }" aria-hidden="true"></i>
                <strong>{{ displayText(item.name) }}</strong>
              </div>
              <div class="branch-operating-composition-row__bar" aria-hidden="true">
                <i :style="{ width: `${barWidth(item.value, compositionMax)}%`, backgroundColor: palette[index % palette.length] }"></i>
              </div>
              <span class="branch-operating-composition-row__value">{{ formatMetric(item.value) }} <small>{{ displayText(item.unit, '') }}</small></span>
            </div>
          </div>
          <div v-else class="branch-operating-empty" data-testid="branch-operating-composition-empty">
            <PieIcon aria-hidden="true" />
            <span>{{ sectionGap('composition', '暂无业务结构数据') }}</span>
          </div>
        </article>

        <article class="branch-operating-panel branch-operating-marketing-panel">
          <header class="branch-operating-panel__heading">
            <div>
              <span class="branch-operating-kicker">Customer growth</span>
              <h2>客户营销</h2>
            </div>
            <span class="branch-operating-panel__meta">来源数量</span>
          </header>
          <div v-if="marketing.length" class="branch-operating-marketing-list" tabindex="0" role="region" aria-label="客户营销列表">
            <div v-for="(item, index) in marketing" :key="`${item.label || 'marketing'}-${index}`" class="branch-operating-marketing-row">
              <span class="branch-operating-marketing-row__icon" aria-hidden="true"><TrendCharts /></span>
              <span class="branch-operating-marketing-row__label">{{ displayText(item.label) }}</span>
              <strong>{{ formatCount(item.count) }}</strong>
            </div>
          </div>
          <div v-else class="branch-operating-empty" data-testid="branch-operating-marketing-empty">
            <UserFilled aria-hidden="true" />
            <span>{{ sectionGap('marketing', '暂无客户营销数据') }}</span>
          </div>
        </article>
      </div>

      <div class="branch-operating-column branch-operating-column--center">
        <article class="branch-operating-panel branch-operating-target-panel" data-testid="branch-operating-targets">
          <header class="branch-operating-panel__heading branch-operating-target-panel__heading">
            <div>
              <span class="branch-operating-kicker">Target pulse</span>
              <h2>目标缺口</h2>
            </div>
            <div class="branch-operating-metric-switch" role="group" aria-label="趋势与目标指标">
              <button
                v-for="metric in metricOptions"
                :key="metric.key"
                type="button"
                :data-trend-key="metric.key"
                :class="{ 'is-active': selectedMetric === metric.key }"
                @click="selectMetric(metric.key)"
              >
                {{ metric.label }}
              </button>
            </div>
          </header>
          <div v-if="largestGap" class="branch-operating-gap-highlight" :class="gapTone(largestGap.gap)">
            <span class="branch-operating-gap-highlight__mark" aria-hidden="true"><WarningFilled /></span>
            <div>
              <span>当前关注缺口 · {{ largestGap.label }}</span>
              <strong>{{ formatMetric(Math.abs(largestGap.gap)) }} <small>{{ displayText(largestGap.unit, '') }}</small></strong>
            </div>
            <em>{{ largestGap.gap > 0 ? '低于目标' : largestGap.gap < 0 ? '超出目标' : '目标持平' }}</em>
          </div>
          <div v-else-if="targets.length" class="branch-operating-empty branch-operating-empty--compact" data-testid="branch-operating-gap-empty">
            <Aim aria-hidden="true" />
            <span>暂无可判断的目标缺口</span>
          </div>
          <div v-if="targets.length" class="branch-operating-target-list" tabindex="0" role="region" aria-label="支行目标列表">
            <p v-if="!hasSelectedTarget" class="branch-operating-target-match-empty" data-testid="branch-operating-target-match-empty">
              暂无{{ selectedMetricShortLabel }}目标（{{ selectedMetricLabel }}），仅展示已有来源目标
            </p>
            <div
              v-for="(target, index) in targets"
              :key="`${target.key || target.label || 'target'}-${index}`"
              class="branch-operating-target-row"
              :class="{ 'is-active': target.key === selectedMetric }"
              :data-target-key="target.key || ''"
            >
              <div class="branch-operating-target-row__topline">
                <strong>{{ displayText(target.label) }}</strong>
                <span v-if="finiteMetric(target.rate) !== null" :class="rateClass(target.rate)">{{ formatRate(target.rate) }}</span>
                <span v-else-if="derivedRate(target) !== null" :class="rateClass(derivedRate(target))">{{ formatRate(derivedRate(target)) }}</span>
                <span v-else class="is-missing">完成率不足</span>
              </div>
              <div class="branch-operating-target-row__numbers">
                <span>实际 <b :class="{ 'is-negative': finiteMetric(target.actual) !== null && finiteMetric(target.actual) < 0 }">{{ formatMetric(target.actual) }}</b> <small>{{ displayText(target.unit, '') }}</small></span>
                <span>目标 <b>{{ formatMetric(target.target, '目标数据不足') }}</b> <small>{{ displayText(target.unit, '') }}</small></span>
                <span>差额 <b :class="gapTone(targetGap(target))">{{ formatGap(target) }}</b></span>
              </div>
              <div class="branch-operating-target-row__bar" aria-hidden="true">
                <i :class="{ 'is-negative': targetRate(target) !== null && targetRate(target) < 0 }" :style="{ width: `${progressWidth(target)}%` }"></i>
              </div>
            </div>
          </div>
          <div v-else class="branch-operating-empty" data-testid="branch-operating-targets-empty">
            <Aim aria-hidden="true" />
            <span>暂无经营目标数据</span>
          </div>
        </article>

        <article class="branch-operating-panel branch-operating-trend-panel" data-testid="branch-operating-trend">
          <header class="branch-operating-panel__heading">
            <div>
              <span class="branch-operating-kicker">Operating trend</span>
              <h2>经营趋势</h2>
            </div>
            <div class="branch-operating-trend__legend" aria-label="趋势图例">
              <span v-for="series in visibleSeries" :key="series.key"><i :style="{ backgroundColor: series.color }"></i>{{ series.label }}</span>
            </div>
          </header>
          <v-chart v-if="hasTrendChart" class="branch-operating-trend__chart" :option="trendOption" autoresize aria-label="存款贷款经营趋势图" />
          <div v-else class="branch-operating-empty" data-testid="branch-operating-trend-empty">
            <TrendCharts aria-hidden="true" />
            <span>暂无经营趋势数据</span>
          </div>
        </article>
      </div>

      <div class="branch-operating-column branch-operating-column--right">
        <article class="branch-operating-panel branch-operating-project-panel" data-testid="branch-operating-projects">
          <header class="branch-operating-panel__heading">
            <div>
              <span class="branch-operating-kicker">Priority projects</span>
              <h2>重点项目</h2>
            </div>
            <span class="branch-operating-panel__meta">{{ projects.length ? `${projects.length} 项` : '未接入' }}</span>
          </header>
          <div v-if="projects.length" class="branch-operating-project-list" tabindex="0" role="region" aria-label="重点项目列表">
            <article v-for="(project, index) in projects" :key="`${project.name || 'project'}-${index}`" class="branch-operating-project" data-testid="branch-operating-project">
              <div class="branch-operating-project__topline">
                <strong>{{ displayText(project.name) }}</strong>
                <span class="branch-operating-project__status">{{ displayText(project.status, '状态未提供') }}</span>
              </div>
              <div class="branch-operating-project__meta">
                <span>金额 <b :class="{ 'is-missing': finiteMetric(project.amount) === null }">{{ formatMetric(project.amount) }}</b> {{ displayText(project.unit, '') }}</span>
                <span>负责人 {{ displayText(project.owner, '未提供') }}</span>
                <span>剩余 {{ formatDays(project.days) }}</span>
              </div>
            </article>
          </div>
          <div v-else class="branch-operating-empty" data-testid="branch-operating-projects-empty">
            <Briefcase aria-hidden="true" />
            <span>{{ sectionGap('projects', '暂无重点项目数据') }}</span>
          </div>
        </article>

        <article class="branch-operating-panel branch-operating-attention-panel" data-testid="branch-operating-attention">
          <header class="branch-operating-panel__heading">
            <div>
              <span class="branch-operating-kicker">Coordination queue</span>
              <h2>经营关注与协调</h2>
            </div>
            <span class="branch-operating-panel__meta">考核关注 · 非流程待办</span>
          </header>
          <div v-if="attention.length" class="branch-operating-attention-list" tabindex="0" role="region" aria-label="待协调事项列表">
            <div v-for="(item, index) in attention" :key="`${item.label || 'attention'}-${index}`" class="branch-operating-attention-row">
              <span class="branch-operating-attention-row__icon" aria-hidden="true"><WarningFilled /></span>
              <span class="branch-operating-attention-row__label">{{ displayText(item.label) }}</span>
              <strong>{{ formatCount(item.count) }}</strong>
            </div>
          </div>
          <div v-else class="branch-operating-empty" data-testid="branch-operating-attention-empty">
            <WarningFilled aria-hidden="true" />
            <span>经营关注数据未接入/暂不可用</span>
          </div>
        </article>
      </div>
    </section>

    <section class="branch-operating-panel branch-operating-team-panel" data-testid="branch-operating-teams">
      <header class="branch-operating-panel__heading">
        <div>
          <span class="branch-operating-kicker">Team contribution</span>
          <h2>团队贡献</h2>
        </div>
        <span class="branch-operating-panel__meta">完成率 · 增长 · 待办</span>
      </header>
      <div v-if="teams.length" class="branch-operating-team-table-wrap" tabindex="0" role="region" aria-label="团队贡献表">
        <table class="branch-operating-team-table">
          <thead>
            <tr><th scope="col">团队</th><th scope="col">完成率</th><th scope="col">增幅</th><th scope="col">待办</th><th scope="col">贡献状态</th></tr>
          </thead>
          <tbody>
            <tr v-for="(team, index) in teams" :key="`${team.name || 'team'}-${index}`" data-testid="branch-operating-team">
              <th scope="row"><span class="branch-operating-team-table__dot" aria-hidden="true"></span>{{ displayText(team.name) }}</th>
              <td :class="rateClass(team.rate)">{{ formatRate(team.rate) }}</td>
              <td :class="changeClass(team.increase)">{{ formatSignedRate(team.increase) }}</td>
              <td>{{ formatCount(team.pending) }}</td>
              <td><span class="branch-operating-team-table__state" :class="teamTone(team)">{{ teamState(team) }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="branch-operating-empty" data-testid="branch-operating-teams-empty">
        <UserFilled aria-hidden="true" />
        <span>{{ sectionGap('teams', '暂无团队贡献数据') }}</span>
      </div>
    </section>

    <footer class="branch-operating-footer">
      <span>数据来源 {{ sourceLabel }}</span>
      <span class="branch-operating-footer__hint">指标空值按“—”展示，历史对比不足时不作推算</span>
    </footer>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { use } from 'echarts/core';
import { LineChart } from 'echarts/charts';
import { CanvasRenderer } from 'echarts/renderers';
import {
  AxisPointerComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent
} from 'echarts/components';
import VChart from 'vue-echarts';
import {
  Aim,
  Back,
  Briefcase,
  Coin,
  DataAnalysis,
  FullScreen,
  OfficeBuilding,
  Refresh,
  TrendCharts,
  UserFilled,
  WarningFilled
} from '@element-plus/icons-vue';

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent, AxisPointerComponent]);

const PieIcon = DataAnalysis;
const palette = Object.freeze(['#49e5eb', '#a886ff', '#f7c75d', '#558fff', '#65e5b9']);

const props = defineProps({
  model: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
});

const emit = defineEmits(['refresh', 'back', 'branch-select']);
const rootRef = ref(null);
const selectedMetric = ref('deposit');
const trendMode = ref('all');
const isFullscreen = ref(false);

const safeModel = computed(() => (props.model && typeof props.model === 'object' ? props.model : {}));

function listOf(key) {
  return computed(() => (Array.isArray(safeModel.value[key])
    ? safeModel.value[key].filter(item => item && typeof item === 'object')
    : []));
}

const institutions = listOf('institutions');
const composition = listOf('composition');
const marketing = listOf('marketing');
const targets = listOf('targets');
const projects = listOf('projects');
const attention = listOf('attention');
const teams = listOf('teams');

const kpiCards = computed(() => (Array.isArray(safeModel.value.kpis)
  ? safeModel.value.kpis.filter(item => item && typeof item === 'object').slice(0, 6)
  : []));

const displayName = computed(() => displayText(safeModel.value.orgName, '支行经营大屏'));
const displayDate = computed(() => displayText(safeModel.value.dataDate, '未提供'));
const sourceLabel = computed(() => displayText(safeModel.value.sourceLabel, '数据来源未提供'));
const gaps = computed(() => (safeModel.value.gaps && typeof safeModel.value.gaps === 'object' ? safeModel.value.gaps : {}));
const metricLabels = computed(() => (safeModel.value.metricLabels && typeof safeModel.value.metricLabels === 'object'
  ? safeModel.value.metricLabels
  : {}));
const fullscreenLabel = computed(() => (isFullscreen.value ? '退出全屏' : '进入全屏'));

const metricOptions = computed(() => [
  { key: 'deposit', label: displayText(metricLabels.value.deposit, '存款') },
  { key: 'loan', label: displayText(metricLabels.value.loan, '贷款') }
]);
const selectedMetricLabel = computed(() => metricOptions.value.find(item => item.key === selectedMetric.value)?.label || selectedMetric.value);
const selectedMetricShortLabel = computed(() => selectedMetric.value === 'loan' ? '贷款' : '存款');
const hasSelectedTarget = computed(() => targets.value.some(target => String(target.key || '') === selectedMetric.value));

const seriesDefinitions = computed(() => [
  { key: 'deposit', label: displayText(metricLabels.value.deposit, '存款'), color: '#49e5eb' },
  { key: 'loan', label: displayText(metricLabels.value.loan, '贷款'), color: '#a886ff' }
]);

const trendRows = computed(() => (Array.isArray(safeModel.value.trend)
  ? safeModel.value.trend.filter(item => item && typeof item === 'object')
  : []));

const visibleSeries = computed(() => {
  const definitions = trendMode.value === 'all'
    ? seriesDefinitions.value
    : seriesDefinitions.value.filter(item => item.key === trendMode.value);
  return definitions.filter(item => trendRows.value.some(row => finiteMetric(row[item.key]) !== null));
});

const hasTrendChart = computed(() => trendRows.value.some(row => String(row.date ?? '').trim()) && visibleSeries.value.length > 0);

const trendOption = computed(() => ({
  animation: true,
  color: visibleSeries.value.map(item => item.color),
  grid: { top: 24, right: 20, bottom: 30, left: 44, containLabel: true },
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'line' },
    backgroundColor: 'rgba(5, 17, 43, .96)',
    borderColor: 'rgba(73, 229, 235, .35)',
    textStyle: { color: '#e8f4ff', fontSize: 12 },
    formatter: params => {
      const items = Array.isArray(params) ? params : [params];
      const date = items[0]?.axisValue || '';
      return [date, ...items.map(item => `${item.seriesName}：${item.value == null ? '—' : formatMetric(item.value)}`)].join('<br/>');
    }
  },
  legend: { show: false },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: trendRows.value.map(row => String(row.date ?? '')),
    axisLine: { lineStyle: { color: 'rgba(130, 165, 235, .24)' } },
    axisTick: { show: false },
    axisLabel: { color: '#8ea5d2', fontSize: 10 }
  },
  yAxis: {
    type: 'value',
    name: '亿元',
    nameTextStyle: { color: '#7e9bce', fontSize: 10, padding: [0, 0, 0, -26] },
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: '#8ea5d2', fontSize: 10 },
    splitNumber: 3,
    splitLine: { lineStyle: { color: 'rgba(104, 143, 217, .13)' } }
  },
  series: visibleSeries.value.map(item => ({
    name: item.label,
    type: 'line',
    smooth: false,
    connectNulls: false,
    showSymbol: true,
    symbol: 'circle',
    symbolSize: 5,
    itemStyle: { color: item.color },
    lineStyle: { color: item.color, width: 2, shadowBlur: 8, shadowColor: item.color },
    areaStyle: {
      color: {
        type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [{ offset: 0, color: `${item.color}36` }, { offset: 1, color: `${item.color}00` }]
      }
    },
    data: trendRows.value.map(row => finiteMetric(row[item.key]))
  }))
}));

const compositionMax = computed(() => Math.max(0, ...composition.value.map(item => Math.abs(finiteMetric(item.value) ?? 0))));

const largestGap = computed(() => targets.value
  .map((target, index) => ({
    ...target,
    index,
    gap: targetGap(target)
  }))
  .filter(target => String(target.key || '') === selectedMetric.value)
  .filter(target => target.gap !== null)
  .sort((left, right) => Math.abs(right.gap) - Math.abs(left.gap))[0] || null);

watch(() => safeModel.value, () => {
  if (!targets.value.some(target => target.key === selectedMetric.value)) selectedMetric.value = 'deposit';
  trendMode.value = 'all';
}, { deep: true });

function finiteMetric(value) {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : null;
}

function displayText(value, fallback = '—') {
  return value === null || value === undefined || value === '' ? fallback : String(value);
}

function metricDate(kpi) {
  return displayText(kpi?.dataDate ?? kpi?.date ?? kpi?.periodDate ?? safeModel.value.dataDate, '未提供');
}

function formatMetric(value, missing = '—') {
  const number = finiteMetric(value);
  if (number === null) return missing;
  const small = number !== 0 && Math.abs(number) < 0.01;
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: small ? 4 : 2,
    maximumFractionDigits: small ? 4 : 2
  }).format(number);
}

function formatCount(value) {
  const number = finiteMetric(value);
  return number === null ? '—' : new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 }).format(number);
}

function formatRate(value) {
  const number = finiteMetric(value);
  return number === null ? '—' : `${number.toFixed(2)}%`;
}

function formatSignedRate(value) {
  const number = finiteMetric(value);
  if (number === null) return '—';
  return `${number > 0 ? '+' : ''}${number.toFixed(2)}%`;
}

function formatChange(value) {
  const number = finiteMetric(value);
  if (number === null) return '历史数据不足';
  return `${number > 0 ? '+' : ''}${number.toFixed(2)}%`;
}

function changeClass(value) {
  const number = finiteMetric(value);
  if (number === null) return 'is-missing';
  return number > 0 ? 'is-positive' : number < 0 ? 'is-negative' : 'is-flat';
}

function statusText(value) {
  const text = String(value ?? '').trim();
  if (!text) return '状态未提供';
  const statuses = {
    healthy: '正常',
    normal: '正常',
    warning: '需关注',
    risk: '风险',
    missing: '数据缺失',
    positive: '增长',
    negative: '下降'
  };
  return statuses[text.toLowerCase()] || text;
}

function kpiTone(kpi) {
  const status = String(kpi?.status ?? '').toLowerCase();
  if (status.includes('风险') || status.includes('关注') || status === 'warning' || status === 'risk') return 'warning';
  const value = finiteMetric(kpi?.value);
  return value === null ? 'missing' : 'ready';
}

function kpiIcon(key, index) {
  return {
    deposit: Coin,
    loan: TrendCharts,
    customer: UserFilled,
    marketing: TrendCharts,
    profit: DataAnalysis,
    quality: Aim
  }[String(key)] || [Coin, TrendCharts, UserFilled, DataAnalysis, Aim, Briefcase][index % 6];
}

function sectionGap(key, fallback) {
  const value = gaps.value[key];
  return typeof value === 'string' && value.trim() ? value : fallback;
}

function barWidth(value, max) {
  const number = finiteMetric(value);
  if (number === null || max <= 0) return 0;
  return Math.max(0, Math.min(100, Math.abs(number) / max * 100));
}

function selectMetric(key) {
  selectedMetric.value = key;
  trendMode.value = key;
}

function targetRate(target) {
  const provided = finiteMetric(target?.rate);
  return provided !== null ? provided : derivedRate(target);
}

function derivedRate(target) {
  const actual = finiteMetric(target?.actual);
  const goal = finiteMetric(target?.target);
  return actual !== null && goal !== null && goal !== 0 ? actual / goal * 100 : null;
}

function targetGap(target) {
  const given = finiteMetric(target?.gap);
  if (given !== null) return given;
  const actual = finiteMetric(target?.actual);
  const goal = finiteMetric(target?.target);
  // 目标缺口沿用模型语义：正数代表距目标仍有缺口，负数代表超额完成。
  return actual !== null && goal !== null ? goal - actual : null;
}

function formatGap(target) {
  const gap = targetGap(target);
  const unit = displayText(target?.unit, '');
  return gap === null ? '差额不足' : `${formatMetric(gap)} ${unit}`.trim();
}

function gapTone(value) {
  const gap = finiteMetric(value);
  if (gap === null || gap === 0) return 'is-neutral';
  return gap > 0 ? 'is-shortfall' : 'is-over';
}

function progressWidth(target) {
  const rate = targetRate(target);
  return rate === null ? 0 : Math.max(0, Math.min(100, rate));
}

function rateClass(value) {
  const number = finiteMetric(value);
  if (number === null) return 'is-missing';
  return number < 0 ? 'is-negative' : number >= 100 ? 'is-positive' : 'is-neutral';
}

function formatDays(value) {
  const number = finiteMetric(value);
  return number === null ? '未提供' : `${formatCount(number)} 天`;
}

function teamTone(team) {
  const rate = finiteMetric(team?.rate);
  if (rate === null) return 'is-missing';
  return rate >= 100 ? 'is-positive' : rate < 60 ? 'is-warning' : 'is-neutral';
}

function teamState(team) {
  const rate = finiteMetric(team?.rate);
  if (rate === null) return '完成率未提供';
  if (rate >= 100) return '达成';
  if (rate < 60) return '需协调';
  return '推进中';
}

function selectBranch(value) {
  const code = String(value || '').trim();
  if (code) emit('branch-select', code);
}

async function toggleFullscreen() {
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen?.();
    } else {
      await rootRef.value?.requestFullscreen?.();
    }
  } catch {
    // 浏览器拒绝全屏时保持展示页可用，不阻断返回和刷新操作。
  }
}

function onFullscreenChange() {
  isFullscreen.value = Boolean(document.fullscreenElement);
}

onMounted(() => document.addEventListener('fullscreenchange', onFullscreenChange));
onBeforeUnmount(() => document.removeEventListener('fullscreenchange', onFullscreenChange));
</script>

<style src="./branchOperating.scss" lang="scss"></style>
