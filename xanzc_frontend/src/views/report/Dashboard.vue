<!--
  分行行长仪表盘
  数据接口：GET /api/reports/dashboard/president?dataDate=YYYY-MM-DD
  PDF 仍由浏览器端 html2canvas + jsPDF 生成，不调用不存在的导出接口。
-->
<template>
  <main
    class="rpt-dash"
    aria-labelledby="president-dashboard-title"
    :aria-busy="loading ? 'true' : 'false'"
  >
    <header class="page-h">
      <div class="heading-group">
        <PageTitle id="president-dashboard-title" />
        <p class="desc">{{ data.org || '全行' }} · 数据日期 {{ data.date || '—' }}</p>
      </div>
      <div class="actions" aria-label="报表操作">
        <div class="date-field">
          <label class="date-label" for="dashboard-data-date">数据日期</label>
          <el-date-picker
            id="dashboard-data-date"
            v-model="queryDate"
            class="date-picker"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled-date="disabledDate"
            :clearable="false"
            :prefix-icon="Calendar"
            aria-label="数据日期，不能选择未来日期"
            @change="onSwitchDate"
          />
        </div>
        <el-button
          type="primary"
          :icon="Download"
          :loading="exporting"
          :disabled="exporting"
          aria-label="将当前行长仪表盘导出为 PDF"
          @click="onExportPdf"
        >
          导出 PDF
        </el-button>
      </div>
    </header>

    <template v-if="loading">
      <p class="sr-only" role="status">正在加载行长仪表盘数据</p>
      <section class="stats" aria-label="关键经营指标加载中">
        <article
          v-for="slot in KPI_SLOTS"
          :key="slot"
          class="stat stat--skeleton"
          :aria-label="`正在加载第 ${slot} 项关键指标`"
          data-testid="kpi-skeleton"
        >
          <span class="skeleton-line skeleton-line--short" />
          <span class="skeleton-line skeleton-line--value" />
          <span class="skeleton-line skeleton-line--medium" />
        </article>
      </section>
      <section class="cols cols--loading" aria-label="报表明细加载中">
        <section class="card-section chart-card card-section--loading" aria-label="趋势图加载中">
          <div class="card-h"><h2 class="title">经营趋势</h2></div>
          <div class="panel-skeleton" />
        </section>
        <section class="card-section rank-card card-section--loading" aria-label="机构排名加载中">
          <div class="card-h"><h2 class="title">机构存款规模排名</h2></div>
          <div class="panel-skeleton" />
        </section>
      </section>
    </template>

    <section
      v-else-if="loadError"
      class="state-panel state-panel--error"
      role="alert"
      data-testid="dashboard-error"
    >
      <h2>仪表盘加载失败</h2>
      <p>{{ loadError }}</p>
      <el-button type="primary" data-testid="dashboard-retry" @click="retryLoad">重试</el-button>
    </section>

    <section
      v-else-if="isEmpty"
      class="state-panel"
      role="status"
      data-testid="dashboard-empty"
    >
      <h2>暂无仪表盘数据</h2>
      <p>当前日期尚未生成指标数据，或您没有可查看的数据范围。请切换日期后重试。</p>
    </section>

    <template v-else>
      <section class="stats" aria-label="五项关键经营指标">
        <article v-for="(stat, index) in displayStats" :key="stat.metricCode || stat.label || index" class="stat" data-testid="kpi-card">
          <h2 class="label">{{ stat.label || `关键指标 ${index + 1}` }}</h2>
          <p class="value">
            {{ formatNumber(stat.value) }}
            <span v-if="hasValue(stat.value) && stat.unit" class="unit">{{ stat.unit }}</span>
          </p>
          <p :class="['trend', `trend--${stat.trendType || 'flat'}`]">
            <span class="trend-label">环比：</span>{{ stat.trend || '—' }}
          </p>
        </article>
      </section>

      <section class="cols" aria-label="趋势与机构排名">
        <section class="card-section chart-card" aria-labelledby="trend-title">
          <div class="card-h">
            <div>
              <h2 id="trend-title" class="title">经营趋势（近 12 个月）</h2>
              <p v-if="hasTrendData" class="card-meta">{{ trendUnitDescription }}</p>
            </div>
          </div>
          <template v-if="hasTrendData">
            <p id="trend-summary" class="sr-only" data-testid="trend-summary">{{ trendSummary }}</p>
            <v-chart
              class="chart"
              :option="trendOption"
              autoresize
              aria-describedby="trend-summary"
              :aria-label="trendAriaLabel"
            />
          </template>
          <div v-else class="section-empty" data-testid="trend-empty" role="status">
            <strong>暂无趋势数据</strong>
            <span>当前日期未生成趋势序列，请切换日期或稍后重试。</span>
          </div>
        </section>

        <section class="card-section rank-card" aria-labelledby="ranking-title">
          <div class="card-h">
            <div>
              <h2 id="ranking-title" class="title">机构存款规模排名</h2>
              <p class="card-meta">按存款规模排序，单位以接口返回为准</p>
            </div>
          </div>
          <ol v-if="hasRankingData" class="rank-list" aria-label="按存款规模排序的机构排名">
            <li
              v-for="(rank, index) in data.ranking"
              :key="rank.orgId || rank.orgCode || rank.orgName || index"
              class="rank-row"
              :aria-label="rankingAriaLabel(rank, index)"
              data-testid="rank-row"
            >
              <span class="rank-no">第 {{ rank.rank || index + 1 }} 名</span>
              <div class="rank-main">
                <strong class="org-name" :title="rank.orgName">{{ rank.orgName || '未命名机构' }}</strong>
                <div class="rank-progress-track" aria-hidden="true">
                  <span
                    class="rank-progress"
                    :style="{ width: `${rankingProgress(rank)}%` }"
                    data-testid="rank-progress"
                  />
                </div>
              </div>
              <div class="rank-result">
                <strong class="rank-value" data-testid="rank-value">{{ formatAmount(rankingActual(rank), rank.unit) }}</strong>
                <span class="rank-value-label">存款规模</span>
              </div>
            </li>
          </ol>
          <div v-else class="section-empty section-empty--compact" data-testid="ranking-empty" role="status">
            <strong>暂无机构排名数据</strong>
            <span>当前数据日期没有可排名的机构存款规模。</span>
          </div>
        </section>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Calendar, Download } from '@element-plus/icons-vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';
import { AriaComponent, GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { getDashboardPresident } from '@/api/report';
import html2canvas from 'html2canvas';
import { jsPDF } from 'jspdf';

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent, AriaComponent]);

const KPI_SLOTS = [1, 2, 3, 4, 5];
const KPI_METADATA = [
  { label: '存款日均' },
  { label: '贷款余额' },
  { label: '不良贷款率' },
  { label: '中间业务收入' },
  { label: '本月新增有效客户' }
];
const CHART_COLOR_TOKENS = ['--color-brand-700', '--color-info-fg', '--color-warning-fg', '--color-success-fg'];

function createEmptyDashboard(date = '') {
  return {
    org: '',
    date,
    stats: [],
    trend: { xAxis: [], series: [] },
    ranking: []
  };
}

const data = ref(createEmptyDashboard());
const queryDate = ref('');
const loading = ref(false);
const exporting = ref(false);
const loadError = ref('');
let requestGeneration = 0;

function hasValue(value) {
  return value !== null && value !== undefined && value !== '';
}

function normalizeDashboard(response, requestedDate = '') {
  const source = response && typeof response === 'object' ? response : {};
  const trend = source.trend && typeof source.trend === 'object' ? source.trend : {};
  return {
    org: source.org || '',
    date: source.date || requestedDate || '',
    stats: Array.isArray(source.stats) ? source.stats.map(item => ({ ...item })) : [],
    trend: {
      xAxis: Array.isArray(trend.xAxis) ? trend.xAxis : (Array.isArray(trend.months) ? trend.months : []),
      series: Array.isArray(trend.series) ? trend.series.map(item => ({ ...item })) : []
    },
    ranking: Array.isArray(source.ranking) ? source.ranking.map(item => ({ ...item })) : []
  };
}

const displayStats = computed(() => KPI_METADATA.map((metadata, index) => ({
  ...metadata,
  ...(data.value.stats[index] || {})
})));

const hasStatsData = computed(() => data.value.stats.some(stat => hasValue(stat?.value)));
const trendXAxis = computed(() => data.value.trend?.xAxis || []);
const trendSeries = computed(() => (data.value.trend?.series || [])
  .filter(series => Array.isArray(series?.data) && series.data.length > 0));
const hasTrendData = computed(() => trendXAxis.value.length > 0 && trendSeries.value.length > 0);
const hasRankingData = computed(() => data.value.ranking.length > 0);
const isEmpty = computed(() => !hasStatsData.value && !hasTrendData.value && !hasRankingData.value);

const trendUnitDescription = computed(() => trendSeries.value
  .map(series => series.unit ? `${series.name || '未命名系列'}（${series.unit}）` : (series.name || '未命名系列'))
  .join('、'));

const trendSummary = computed(() => {
  if (!hasTrendData.value) return '暂无趋势数据。';
  const latestIndex = trendXAxis.value.length - 1;
  const latestDate = trendXAxis.value[latestIndex] || '最新日期';
  const values = trendSeries.value.map(series => {
    const value = series.data?.[latestIndex];
    return `${series.name || '未命名系列'}${series.unit ? `（${series.unit}）` : ''}为 ${formatAmount(value, series.unit)}`;
  });
  return `经营趋势图，最新数据日期 ${latestDate}，${values.join('；')}。`;
});

const trendAriaLabel = computed(() => `经营趋势图：${trendUnitDescription.value || '暂无可读系列'}`);

function resolveColorToken(token) {
  if (typeof window === 'undefined' || typeof document === 'undefined') return `var(${token})`;
  return getComputedStyle(document.documentElement).getPropertyValue(token).trim() || `var(${token})`;
}

function chartColor(index) {
  return resolveColorToken(CHART_COLOR_TOKENS[index % CHART_COLOR_TOKENS.length]);
}

const trendOption = computed(() => ({
  aria: { enabled: true, description: trendSummary.value },
  color: trendSeries.value.map((_, index) => chartColor(index)),
  grid: { top: 36, right: 24, bottom: 44, left: 56, containLabel: true },
  tooltip: { trigger: 'axis' },
  legend: {
    show: true,
    data: trendSeries.value.map(series => series.name || '未命名系列'),
    bottom: 0
  },
  xAxis: {
    type: 'category',
    name: '月份',
    data: trendXAxis.value,
    boundaryGap: false,
    axisLine: { lineStyle: { color: resolveColorToken('--color-border') } },
    axisLabel: { color: resolveColorToken('--color-text-muted') }
  },
  yAxis: {
    type: 'value',
    name: trendSeries.value.length === 1 ? (trendSeries.value[0].unit || '数值') : '数值',
    axisLine: { show: false },
    axisLabel: { color: resolveColorToken('--color-text-muted') },
    splitLine: { lineStyle: { color: resolveColorToken('--color-border') } }
  },
  series: trendSeries.value.map((series, index) => ({
    name: series.name || `系列 ${index + 1}`,
    type: 'line',
    smooth: true,
    data: series.data,
    lineStyle: { width: 2 },
    symbol: 'circle',
    symbolSize: 6
  }))
}));

function disabledDate(date) {
  if (!date) return false;
  const endOfToday = new Date();
  endOfToday.setHours(23, 59, 59, 999);
  return date.getTime() > endOfToday.getTime();
}

async function loadDashboard(date = '') {
  const requestedDate = date || '';
  const generation = ++requestGeneration;
  loading.value = true;
  loadError.value = '';
  data.value = createEmptyDashboard(requestedDate);
  if (requestedDate) queryDate.value = requestedDate;

  try {
    // API adapter 统一把前端 date 映射为后端 dataDate，页面不复制 URL 契约。
    const response = await getDashboardPresident(requestedDate ? { date: requestedDate } : {});
    if (generation !== requestGeneration) return;
    data.value = normalizeDashboard(response, requestedDate);
    queryDate.value = data.value.date || requestedDate;
  } catch (error) {
    if (generation !== requestGeneration) return;
    data.value = createEmptyDashboard(requestedDate);
    queryDate.value = requestedDate;
    loadError.value = error?.message || '暂时无法获取仪表盘数据，请稍后重试。';
  } finally {
    // 旧请求结束时不能把新请求的加载态提前关闭。
    if (generation === requestGeneration) loading.value = false;
  }
}

function onSwitchDate(date) {
  if (date) loadDashboard(date);
}

function retryLoad() {
  loadDashboard(queryDate.value);
}

function formatNumber(value) {
  if (!hasValue(value)) return '—';
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return String(value);
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(numeric);
}

function formatAmount(value, unit = '') {
  const formatted = formatNumber(value);
  return formatted === '—' ? formatted : (unit ? `${formatted} ${unit}` : formatted);
}

function numericRankingValue(value) {
  if (!hasValue(value)) return null;
  const numeric = Number(String(value).replace(/,/g, '').trim());
  return Number.isFinite(numeric) ? numeric : null;
}

function rankingActual(rank) {
  return hasValue(rank?.actual) ? rank.actual : rank?.achievementRate;
}

const maxRankingValue = computed(() => Math.max(
  0,
  ...data.value.ranking
    .map(rank => numericRankingValue(rankingActual(rank)))
    .filter(value => value !== null)
));

function rankingProgress(rank) {
  const value = numericRankingValue(rankingActual(rank));
  if (value === null || maxRankingValue.value <= 0) return 0;
  return Math.min(100, Math.max(0, (value / maxRankingValue.value) * 100));
}

function rankingAriaLabel(rank, index) {
  const name = rank.orgName || '未命名机构';
  const position = rank.rank || index + 1;
  return `${name}，第 ${position} 名，存款规模 ${formatAmount(rankingActual(rank), rank.unit)}`;
}

async function onExportPdf() {
  exporting.value = true;
  try {
    const element = document.querySelector('.rpt-dash');
    if (!element) {
      ElMessage.warning('页面未渲染');
      return;
    }
    const canvas = await html2canvas(element, {
      scale: 2,
      useCORS: true,
      backgroundColor: resolveColorToken('--color-surface')
    });
    const imageData = canvas.toDataURL('image/png');
    const pdf = new jsPDF('l', 'mm', 'a4');
    const pdfWidth = pdf.internal.pageSize.getWidth();
    const pdfHeight = (canvas.height * pdfWidth) / canvas.width;
    pdf.addImage(imageData, 'PNG', 0, 0, pdfWidth, pdfHeight);
    pdf.save(`行长仪表盘_${queryDate.value || 'export'}.pdf`);
    ElMessage.success('PDF 已导出');
  } catch (error) {
    ElMessage.error(`导出失败：${error?.message || '未知错误'}`);
  } finally {
    exporting.value = false;
  }
}

onMounted(() => loadDashboard());
onBeforeUnmount(() => { requestGeneration += 1; });
</script>

<style lang="scss" scoped>
.rpt-dash {
  color: var(--color-text-strong);
  font-variant-numeric: tabular-nums;

  .page-h {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: var(--space-4);
    margin-bottom: var(--space-4);
  }

  .heading-group {
    min-width: 0;
  }

  .desc,
  .card-meta {
    margin: var(--space-1) 0 0;
    color: var(--color-text-muted);
    font-size: 12px;
    line-height: 18px;
  }

  .actions,
  .date-field {
    display: flex;
    align-items: center;
    gap: var(--space-2);
  }

  .date-label {
    color: var(--color-text);
    font-size: 14px;
    line-height: 22px;
    white-space: nowrap;
  }

  .date-picker {
    width: 180px;
  }

  .stats {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: var(--space-3);
    margin-bottom: var(--space-3);
  }

  .stat,
  .card-section,
  .state-panel {
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-control);
    box-shadow: var(--shadow-surface);
  }

  .stat {
    box-sizing: border-box;
    min-height: 126px;
    padding: var(--space-4);
  }

  .label,
  .title {
    margin: 0;
    color: var(--color-text-strong);
    font-size: 14px;
    font-weight: 600;
    line-height: 22px;
  }

  .value {
    display: flex;
    align-items: baseline;
    gap: var(--space-1);
    min-height: 32px;
    margin: var(--space-2) 0;
    color: var(--color-text-strong);
    font-size: 24px;
    font-weight: 600;
    line-height: 32px;
  }

  .unit {
    color: var(--color-text-muted);
    font-size: 13px;
    font-weight: 400;
  }

  .trend {
    margin: 0;
    color: var(--color-text-muted);
    font-size: 12px;
    line-height: 18px;
  }

  .trend-label {
    color: var(--color-text-muted);
  }

  .trend--up { color: var(--color-success-fg); }
  .trend--down { color: var(--color-danger-fg); }
  .trend--flat { color: var(--color-text-muted); }

  .stat--skeleton {
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    background: var(--color-surface);
  }

  .skeleton-line,
  .panel-skeleton {
    display: block;
    background: var(--color-surface-soft);
  }

  .skeleton-line {
    height: 12px;
    border-radius: var(--radius-control);
  }

  .skeleton-line--short { width: 42%; }
  .skeleton-line--value { width: 68%; height: 28px; }
  .skeleton-line--medium { width: 54%; }

  .cols {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(360px, 420px);
    gap: var(--space-3);
  }

  .card-section {
    box-sizing: border-box;
    min-height: 396px;
    padding: var(--space-4);
  }

  .card-h {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    min-height: 42px;
    padding-bottom: var(--space-3);
    border-bottom: 1px solid var(--color-border);
  }

  .chart {
    height: 320px;
  }

  .panel-skeleton {
    height: 320px;
    margin-top: var(--space-3);
  }

  .section-empty {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: flex-start;
    gap: var(--space-2);
    min-height: 320px;
    color: var(--color-text);
    font-size: 14px;
    line-height: 22px;
  }

  .section-empty strong {
    color: var(--color-text-strong);
    font-weight: 600;
  }

  .section-empty--compact {
    min-height: 300px;
  }

  .rank-list {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
    margin: var(--space-3) 0 0;
    padding: 0;
    list-style: none;
  }

  .rank-row {
    display: grid;
    grid-template-columns: 44px minmax(0, 1fr) auto;
    gap: var(--space-2);
    align-items: center;
    padding: var(--space-2) 0;
    border-bottom: 1px solid var(--color-border);
  }

  .rank-row:last-child {
    border-bottom: 0;
  }

  .rank-no,
  .rank-value-label {
    color: var(--color-text-muted);
    font-size: 12px;
    line-height: 18px;
  }

  .rank-main {
    min-width: 0;
  }

  .org-name {
    display: block;
    overflow: hidden;
    color: var(--color-text-strong);
    font-size: 14px;
    font-weight: 500;
    line-height: 22px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .rank-progress-track {
    height: 6px;
    margin-top: var(--space-1);
    overflow: hidden;
    background: var(--color-surface-soft);
    border-radius: var(--radius-control);
  }

  .rank-progress {
    display: block;
    height: 100%;
    border-radius: inherit;
    background: var(--color-brand-700);
  }

  .rank-result {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: var(--space-1);
    text-align: right;
  }

  .rank-value {
    color: var(--color-text-strong);
    font-size: 18px;
    font-weight: 600;
    line-height: 24px;
  }

  .state-panel {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-2);
    min-height: 220px;
    padding: var(--space-6);
  }

  .state-panel h2,
  .state-panel p {
    margin: 0;
  }

  .state-panel h2 {
    color: var(--color-text-strong);
    font-size: 16px;
    line-height: 24px;
  }

  .state-panel p {
    max-width: 560px;
    color: var(--color-text);
    font-size: 14px;
    line-height: 22px;
  }

  .state-panel--error {
    border-color: var(--color-danger-fg);
    background: var(--color-danger-bg);
  }

  .sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
  }

  @media (prefers-reduced-motion: reduce) {
    .rank-progress {
      transition: none;
    }
  }
}
</style>
