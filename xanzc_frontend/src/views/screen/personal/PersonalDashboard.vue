<template>
  <main class="personal-dashboard" aria-label="个人经营驾驶舱">
    <header class="personal-header">
      <div class="personal-header__identity">
        <span class="personal-eyebrow">Personal cockpit</span>
        <h1>我的经营驾驶舱</h1>
        <p class="personal-header__person">
          <strong>{{ displayText(identity.name) }}</strong>
          <span aria-hidden="true">·</span>
          <span>{{ displayText(identity.orgName) }}</span>
        </p>
      </div>

      <div class="personal-header__status" aria-live="polite">
        <span class="personal-live-state">
          <i :class="{ 'is-loading': loading }" aria-hidden="true"></i>
          {{ loading ? '正在刷新' : '个人经营概览' }}
        </span>
        <span class="personal-refreshed">刷新于 {{ displayText(model.refreshedAt) }}</span>
      </div>

      <div class="personal-header__actions">
        <button type="button" class="personal-icon-action" data-action="refresh" aria-label="刷新个人经营驾驶舱" title="刷新" @click="emit('refresh')">
          <Refresh aria-hidden="true" />
        </button>
        <button type="button" class="personal-icon-action" data-action="fullscreen" aria-label="全屏显示个人经营驾驶舱" title="全屏" @click="emit('fullscreen')">
          <FullScreen aria-hidden="true" />
        </button>
        <button type="button" class="personal-icon-action" data-action="back" :aria-label="backLabel" :title="backLabel" @click="emit('back')">
          <Back aria-hidden="true" />
        </button>
      </div>
    </header>

    <div v-if="loading" class="personal-loading" data-testid="personal-loading" role="status">
      <Refresh class="personal-loading__icon" aria-hidden="true" />
      <span>正在加载个人经营数据…</span>
    </div>

    <section class="personal-panel personal-metrics-panel" aria-labelledby="personal-metrics-heading">
      <header class="personal-panel__heading">
        <div>
          <span class="personal-kicker">Core metrics</span>
          <h2 id="personal-metrics-heading">个人核心指标</h2>
          <p class="personal-metrics-panel__scope">{{ metricScopeLabel(metrics) }}</p>
        </div>
        <span class="personal-panel__count">{{ metricCountLabel(metrics) }}</span>
      </header>

      <p v-if="metrics.message" class="personal-panel__note">{{ metrics.message }}</p>
      <div v-if="hasRenderableItems(metrics)" class="personal-metrics-grid">
        <article v-for="(metric, index) in itemsOf(metrics)" :key="metric.metricCode || `${metric.metricName || 'metric'}-${index}`" class="personal-metric" data-testid="personal-metric">
          <div class="personal-metric__topline">
            <span class="personal-metric__icon" aria-hidden="true"><component :is="metricIcon(metric.metricCode, index)" /></span>
            <span class="personal-metric__name" :title="displayText(metric.metricName)">{{ displayText(metric.metricName) }}</span>
          </div>
          <div class="personal-metric__value-line">
            <strong :title="displayText(metric.currentValue)">{{ formatValue(metric.currentValue) }}</strong>
            <span>{{ displayText(metric.unit, '单位未配置') }}</span>
          </div>
          <div class="personal-metric__details">
            <span v-if="hasValue(metric.previousValue)" class="personal-metric__previous">{{ previousLabel(metric) }} {{ formatValue(metric.previousValue) }}</span>
            <span v-if="hasValue(metric.targetValue)" class="personal-metric__target">目标 {{ formatValue(metric.targetValue) }}</span>
            <span v-if="hasValue(metric.achievementRate)" class="personal-metric__achievement">完成率 {{ formatRate(metric.achievementRate) }}</span>
            <span v-if="!hasValue(metric.targetValue) && !hasValue(metric.achievementRate)" class="personal-metric__target-note">未关联考核目标</span>
          </div>
          <div class="personal-metric__foot">
            <span v-if="showMetricChange(metric)">{{ changeLabel(metric) }} {{ formatRate(metric.mom) }}</span>
            <span>{{ formatDataDate(metric.dataDate) }}</span>
          </div>
        </article>
      </div>
      <div v-else class="personal-section-state" :class="`is-${sectionStatus(metrics)}`" data-testid="personal-metrics-state" :role="stateRole(metrics)">
        <component :is="stateIcon(metrics)" aria-hidden="true" />
        <strong>{{ stateTitle(metrics) }}</strong>
        <p>{{ stateMessage(metrics) }}</p>
      </div>
    </section>

    <section class="personal-main-grid" aria-label="个人经营事项">
      <article class="personal-panel personal-list-panel personal-list-panel--priorities" aria-labelledby="personal-priorities-heading">
        <header class="personal-panel__heading">
          <div>
            <span class="personal-kicker">Today focus</span>
            <h2 id="personal-priorities-heading">今日优先事项</h2>
          </div>
          <span class="personal-panel__count">{{ listCountLabel(priorities, '条') }}</span>
        </header>

        <p v-if="priorities.message" class="personal-panel__note">{{ priorities.message }}</p>
        <div v-if="hasRenderableItems(priorities)" class="personal-list-wrap" tabindex="0" role="region" aria-label="今日优先事项清单">
          <ul class="personal-list" aria-label="今日优先事项清单">
            <li v-for="item in itemsOf(priorities)" :key="`${item.target?.kind || item.typeLabel || 'todo'}:${item.id || item.title}`" class="personal-list__item">
              <button type="button" class="personal-list-row personal-list-row--priority" data-testid="personal-priority" :data-id="item.id || ''" @click="navigateToItem(item, 'todos')">
                <span class="personal-list-row__marker" :class="`is-${urgency(item.urgency)}`" aria-hidden="true"><WarningFilled /></span>
                <span class="personal-list-row__body">
                  <strong :title="displayText(item.title)">{{ displayText(item.title) }}</strong>
                  <span class="personal-list-row__subline"><span>{{ displayText(item.customerName) }}</span><span>{{ displayText(item.typeLabel) }} · {{ displayText(item.nodeLabel) }}</span></span>
                  <span class="personal-list-row__reason">{{ displayText(item.reason) }}</span>
                </span>
                <span class="personal-list-row__aside">
                  <span class="personal-urgency" :class="`is-${urgency(item.urgency)}`">{{ urgencyLabel(item.urgency) }}</span>
                  <span class="personal-list-row__deadline">{{ displayDeadline(item.deadline) }}</span>
                  <span class="personal-list-row__action">{{ displayText(item.actionLabel, '查看') }} <ArrowRight aria-hidden="true" /></span>
                </span>
              </button>
            </li>
          </ul>
        </div>
        <div v-else class="personal-section-state" :class="`is-${sectionStatus(priorities)}`" data-testid="personal-priorities-state" :role="stateRole(priorities)">
          <component :is="stateIcon(priorities)" aria-hidden="true" />
          <strong>{{ stateTitle(priorities) }}</strong>
          <p>{{ stateMessage(priorities) }}</p>
        </div>
        <footer v-if="sectionStatus(priorities) === 'ready'" class="personal-panel__footer">
          <button type="button" class="personal-more-action" data-action="view-todos" @click="navigateToCollection('todos')">查看更多 <ArrowRight aria-hidden="true" /></button>
        </footer>
      </article>

      <article class="personal-panel personal-list-panel personal-list-panel--customers" aria-labelledby="personal-customers-heading">
        <header class="personal-panel__heading">
          <div>
            <span class="personal-kicker">My relationship</span>
            <h2 id="personal-customers-heading">我的客户</h2>
          </div>
          <span class="personal-panel__count">{{ listCountLabel(customers, '户') }}</span>
        </header>

        <p v-if="customers.message" class="personal-panel__note">{{ customers.message }}</p>
        <div v-if="hasRenderableItems(customers)" class="personal-list-wrap" tabindex="0" role="region" aria-label="我的客户清单">
          <ul class="personal-list" aria-label="我的客户清单">
            <li v-for="item in itemsOf(customers)" :key="`${item.target?.kind || 'customer'}:${item.id || item.name}`" class="personal-list__item">
              <button type="button" class="personal-list-row personal-list-row--customer" data-testid="personal-customer" :data-id="item.id || ''" @click="navigateToItem(item, 'customers')">
                <span class="personal-list-row__marker personal-list-row__marker--customer" aria-hidden="true"><UserFilled /></span>
                <span class="personal-list-row__body">
                  <strong :title="displayText(item.name)">{{ displayText(item.name) }}</strong>
                  <span class="personal-list-row__subline"><span :class="{ 'is-key': item.isKey === true }">{{ item.isKey === true ? '重点客户' : item.isKey === false ? '普通客户' : '重点标识未知' }}</span><span>{{ item.opened === true ? '已开户' : item.opened === false ? '未开户' : '开户状态未知' }}</span></span>
                </span>
                <span class="personal-list-row__aside">
                  <span class="personal-list-row__touch">{{ item.touchRestricted ? '触达受限 · ' : '' }}最近触达 {{ displayText(item.lastTouchTime) }}</span>
                  <ArrowRight aria-hidden="true" />
                </span>
              </button>
            </li>
          </ul>
        </div>
        <div v-else class="personal-section-state" :class="`is-${sectionStatus(customers)}`" data-testid="personal-customers-state" :role="stateRole(customers)">
          <component :is="stateIcon(customers)" aria-hidden="true" />
          <strong>{{ stateTitle(customers) }}</strong>
          <p>{{ stateMessage(customers) }}</p>
        </div>
        <footer v-if="sectionStatus(customers) === 'ready'" class="personal-panel__footer">
          <button type="button" class="personal-more-action" data-action="view-customers" @click="navigateToCollection('customers')">查看更多 <ArrowRight aria-hidden="true" /></button>
        </footer>
      </article>
    </section>

    <section class="personal-panel personal-progress-panel" aria-labelledby="personal-progress-heading">
      <header class="personal-panel__heading">
        <div>
          <span class="personal-kicker">Work in progress</span>
          <h2 id="personal-progress-heading">我发起的业务进度</h2>
        </div>
        <span class="personal-panel__count">{{ listCountLabel(progress, '条') }}</span>
      </header>

      <p v-if="progress.message" class="personal-panel__note">{{ progress.message }}</p>
      <div v-if="hasRenderableItems(progress)" class="personal-progress-wrap" tabindex="0" role="region" aria-label="我发起的业务进度清单">
        <ul class="personal-progress-list" aria-label="我发起的业务进度清单">
          <li v-for="item in itemsOf(progress)" :key="`${item.target?.kind || item.typeLabel || 'progress'}:${item.id || item.title}`" class="personal-progress-row" data-testid="personal-progress" :data-id="item.id || ''" @click="navigateToItem(item, 'progress')">
            <button type="button" class="personal-progress-row__button">
              <span class="personal-progress-row__status"><Briefcase aria-hidden="true" /></span>
              <span class="personal-progress-row__body">
                <strong :title="displayText(item.title)">{{ displayText(item.title) }}</strong>
                <span>{{ displayText(item.customerName) }} · {{ displayText(item.typeLabel) }}</span>
              </span>
              <span class="personal-progress-row__node"><span>{{ displayText(item.statusLabel) }}</span><small>{{ displayText(item.nodeLabel) }}</small></span>
              <span class="personal-progress-row__date">{{ displayText(item.timeLabel, '发起时间') }} {{ displayText(item.submittedTime) }}</span>
              <ArrowRight class="personal-progress-row__arrow" aria-hidden="true" />
            </button>
          </li>
        </ul>
      </div>
      <div v-else class="personal-section-state" :class="`is-${sectionStatus(progress)}`" data-testid="personal-progress-state" :role="stateRole(progress)">
        <component :is="stateIcon(progress)" aria-hidden="true" />
        <strong>{{ stateTitle(progress) }}</strong>
        <p>{{ stateMessage(progress) }}</p>
      </div>
      <footer v-if="sectionStatus(progress) === 'ready'" class="personal-panel__footer">
        <button type="button" class="personal-more-action" data-action="view-progress" @click="navigateToCollection('progress')">查看更多 <ArrowRight aria-hidden="true" /></button>
      </footer>
    </section>
  </main>
</template>

<script setup>
import { computed } from 'vue';
import {
  ArrowRight,
  Back,
  Briefcase,
  Coin,
  DataAnalysis,
  FullScreen,
  List,
  OfficeBuilding,
  Refresh,
  TrendCharts,
  UserFilled,
  WarningFilled
} from '@element-plus/icons-vue';

const props = defineProps({
  model: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  backLabel: { type: String, default: '返回工作台' }
});

const emit = defineEmits(['refresh', 'back', 'navigate', 'fullscreen']);

const model = computed(() => (props.model && typeof props.model === 'object' ? props.model : {}));
const identity = computed(() => (model.value.identity && typeof model.value.identity === 'object' ? model.value.identity : {}));
const metrics = computed(() => normalizeSection(model.value.metrics));
const priorities = computed(() => normalizeSection(model.value.priorities));
const customers = computed(() => normalizeSection(model.value.customers));
const progress = computed(() => normalizeSection(model.value.progress));
const backLabel = computed(() => props.backLabel || '返回工作台');

function normalizeSection(section) {
  return section && typeof section === 'object' ? section : {};
}

function itemsOf(section) {
  return Array.isArray(section?.items) ? section.items.filter(item => item && typeof item === 'object') : [];
}

function sectionStatus(section) {
  const status = String(section?.status || '').trim().toLowerCase();
  if (['ready', 'empty', 'error', 'forbidden'].includes(status)) return status;
  return itemsOf(section).length ? 'ready' : 'empty';
}

function hasRenderableItems(section) {
  return sectionStatus(section) === 'ready' && itemsOf(section).length > 0;
}

function displayText(value, fallback = '—') {
  if (value === null || value === undefined || value === '') return fallback;
  return String(value);
}

function finiteNumber(value) {
  if (value === null || value === undefined || value === '') return null;
  if (typeof value === 'boolean') return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : null;
}

function hasValue(value) {
  return finiteNumber(value) !== null;
}

function formatValue(value) {
  const number = finiteNumber(value);
  if (number === null) return '—';
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(number);
}

function formatRate(value) {
  const number = finiteNumber(value);
  if (number === null) return '—';
  return `${number.toFixed(2)}%`;
}

function formatDataDate(value) {
  if (value === null || value === undefined || value === '') return '数据日期未提供';
  return `数据日期 ${String(value).slice(0, 10)}`;
}

function displayDeadline(value) {
  return value === null || value === undefined || value === '' ? '期限未提供' : `截止 ${String(value)}`;
}

function metricCountLabel(section) {
  const count = itemsOf(section).length;
  return `${count} 项已展示`;
}

function metricScopeLabel(section) {
  return String(section?.sourceType || '').toUpperCase() === 'EMP_LATEST_IMPORT'
    ? '本人经营指标 · 最新导入快照（非考核结算）'
    : '本人经营指标 · 按有效数据日期';
}

function listCountLabel(section, suffix) {
  const total = finiteNumber(section?.total);
  return total === null ? '近期清单' : `${formatValue(total)} ${suffix}`;
}

function stateMessage(section) {
  if (section?.message) return String(section.message);
  const status = sectionStatus(section);
  return {
    ready: '暂无可展示数据',
    empty: '暂无可展示数据',
    error: '暂时无法获取数据，请稍后重试',
    forbidden: '当前账号暂无权限查看此区域'
  }[status] || '暂无可展示数据';
}

function stateTitle(section) {
  const status = sectionStatus(section);
  return {
    ready: '暂无可展示数据',
    empty: '暂无数据',
    error: '数据加载失败',
    forbidden: '暂无查看权限'
  }[status] || '暂无数据';
}

function stateRole(section) {
  return sectionStatus(section) === 'error' ? 'alert' : 'status';
}

function stateIcon(section) {
  return sectionStatus(section) === 'error' || sectionStatus(section) === 'forbidden' ? WarningFilled : DataAnalysis;
}

function metricIcon(code, index) {
  const key = String(code || '').toLowerCase();
  if (key.includes('customer') || key.includes('client')) return UserFilled;
  if (key.includes('deposit') || key.includes('balance') || key.includes('amount')) return Coin;
  if (key.includes('todo') || key.includes('task') || key.includes('process')) return List;
  if (key.includes('business') || key.includes('application')) return Briefcase;
  if (key.includes('trend') || key.includes('rate') || key.includes('achievement')) return TrendCharts;
  return [DataAnalysis, OfficeBuilding, TrendCharts, Briefcase][index % 4];
}

function showMetricChange(metric) {
  return hasValue(metric?.mom);
}

function isPreviousMonthEnd(metric) {
  return String(metric?.comparisonType || '').toUpperCase() === 'PREVIOUS_MONTH_END';
}

function previousLabel(metric) {
  return isPreviousMonthEnd(metric) ? '上月末值' : '上期值';
}

function changeLabel(metric) {
  return isPreviousMonthEnd(metric) ? '较上月末' : '较上期';
}

function urgency(value) {
  const normalized = String(value || '').toLowerCase();
  return ['overdue', 'warn', 'normal'].includes(normalized) ? normalized : 'unknown';
}

function urgencyLabel(value) {
  return { overdue: '已逾期', warn: '即将到期', normal: '正常', unknown: '时效待确认' }[urgency(value)];
}

function navigationTarget(item, fallbackKind) {
  const target = item?.target && typeof item.target === 'object' ? { ...item.target } : {};
  if (!target.kind) target.kind = fallbackKind;
  if (target.id === null || target.id === undefined || target.id === '') target.id = item?.id;
  return target;
}

function navigateToItem(item, fallbackKind) {
  emit('navigate', navigationTarget(item, fallbackKind));
}

function navigateToCollection(kind) {
  emit('navigate', { kind });
}
</script>

<style src="./personal.scss" lang="scss"></style>
