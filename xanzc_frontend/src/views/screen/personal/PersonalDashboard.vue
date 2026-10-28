<template>
  <main class="personal-dashboard" aria-label="个人经营驾驶舱">
    <header class="personal-header">
      <div class="personal-header__identity">
        <span class="personal-eyebrow">Personal cockpit</span>
        <h1>{{ settings.title || "我的经营驾驶舱" }}</h1>
        <p>
          <strong>{{ text(identity.name) }}</strong
          ><span aria-hidden="true"> · </span
          ><span>{{ text(identity.orgName) }}</span>
        </p>
      </div>
      <div class="personal-header__status" aria-live="polite">
        <span class="personal-live-state"
          ><i :class="{ 'is-loading': loading }" aria-hidden="true"></i
          >{{ loading ? "正在刷新" : "个人经营概览" }}</span
        ><span>刷新于 {{ text(model.refreshedAt) }}</span>
      </div>
      <div class="personal-header__actions">
        <button
          type="button"
          class="personal-icon-action"
          data-action="refresh"
          aria-label="刷新个人经营驾驶舱"
          @click="emit('refresh')"
        >
          <Refresh /></button
        ><button
          type="button"
          class="personal-icon-action"
          data-action="fullscreen"
          aria-label="全屏显示个人经营驾驶舱"
          @click="emit('fullscreen')"
        >
          <FullScreen /></button
        ><button
          type="button"
          class="personal-icon-action"
          data-action="back"
          :aria-label="backLabel"
          @click="emit('back')"
        >
          <Back />
        </button>
      </div>
    </header>
    <div
      v-if="loading"
      class="personal-loading"
      data-testid="personal-loading"
      role="status"
    >
      <Refresh class="personal-loading__icon" /><span
        >正在加载个人经营数据…</span
      >
    </div>
    <section
      class="personal-panel personal-metrics-panel"
      aria-labelledby="personal-metrics-heading"
    >
      <header class="personal-panel__heading">
        <div>
          <span class="personal-kicker">Core metrics</span>
          <h2 id="personal-metrics-heading">
            {{ settings.metricsTitle || "个人核心指标" }}
          </h2>
        </div>
        <span class="personal-panel__count">{{ metrics.length }} 项</span>
      </header>
      <p v-if="metricsSection.message" class="personal-panel__note">
        {{ metricsSection.message }}
      </p>
      <div class="personal-metrics-grid">
        <button
          v-for="(metric, index) in metrics"
          :key="metric.metricCode || index"
          type="button"
          class="personal-metric"
          :class="{ 'is-selected': selectedMetric === metric.metricCode }"
          data-testid="personal-metric"
          @click="selectMetric(metric.metricCode)"
        >
          <span class="personal-metric__topline"
            ><span
              class="personal-metric__dot"
              :style="{ backgroundColor: color(metric, index) }"
            ></span
            ><span class="personal-metric__name">{{
              text(metric.metricName)
            }}</span></span
          ><span class="personal-metric__value-line"
            ><strong>{{ value(metric.currentValue) }}</strong
            ><span class="personal-metric__unit">{{
              text(metric.unit, "单位未配置")
            }}</span></span
          ><span class="personal-metric__details"
            >{{
              metric.targetValue > 0
                ? `目标 ${value(metric.targetValue)}`
                : "目标未配置"
            }}
            ·
            {{
              metric.targetValue > 0 &&
              metric.achievementRate !== null &&
              metric.achievementRate !== undefined
                ? `完成率 ${rate(metric.achievementRate)}`
                : "完成率未知"
            }}</span
          ><span class="personal-metric__foot">{{
            text(metric.dataDate, "数据日期未提供")
          }}</span>
        </button>
      </div>
      <button
        v-if="selectedMetric"
        type="button"
        class="personal-reset"
        data-action="reset-metric"
        @click="selectMetric('')"
      >
        显示全部指标
      </button>
      <div
        v-if="!metrics.length"
        class="personal-section-state"
        data-testid="personal-metrics-state"
        role="status"
      >
        <DataAnalysis /><strong>暂无指标</strong>
        <p>{{ metricsSection.message || "暂无可展示数据" }}</p>
      </div>
    </section>
    <section class="personal-main-grid" aria-label="个人趋势与机构占比">
      <article class="personal-panel personal-trend-panel">
        <header class="personal-panel__heading">
          <div>
            <span class="personal-kicker">Trend</span>
            <h2>{{ settings.trendTitle || "指标趋势" }}</h2>
          </div>
          <div class="personal-range-tabs" role="tablist">
            <button
              v-for="item in ranges"
              :key="item.code"
              type="button"
              :class="{ active: range === item.code }"
              :aria-selected="range === item.code"
              @click="changeRange(item.code)"
            >
              {{ item.label }}
            </button>
          </div>
        </header>
        <p class="personal-panel__note">
          {{
            trendMode === "actual"
              ? "单项显示实际值/单位"
              : "全部指标按较期内首个有效观测点增长率展示"
          }}
        </p>
        <div v-if="hasTrend" class="personal-chart-wrap">
          <v-chart
            class="personal-trend-chart"
            :option="trendOption"
            autoresize
            @click="onChartClick"
          />
        </div>
        <div
          v-else
          class="personal-section-state"
          data-testid="personal-trend-empty"
          role="status"
        >
          <DataAnalysis /><strong>{{
            trendMode === "growth" &&
            visibleMetrics.some(
              (item) =>
                filterHistory(
                  item.history,
                  range,
                  model.refreshedAt || undefined,
                ).find((point) => finite(point.value) !== null)?.value === 0,
            )
              ? "基期为0，增长率不可计算"
              : "暂无历史数据"
          }}</strong>
          <p>所选范围内没有可展示的真实观测点</p>
        </div>
      </article>
      <article class="personal-panel personal-share-panel">
        <header class="personal-panel__heading">
          <div>
            <span class="personal-kicker">Organization</span>
            <h2>{{ settings.shareTitle || "机构占比" }}</h2>
          </div>
        </header>
        <div class="personal-share-list">
          <div
            v-for="(metric, index) in metrics"
            :key="`share-${metric.metricCode || index}`"
            class="personal-share-row"
          >
            <span
              class="personal-metric__dot"
              :style="{ backgroundColor: color(metric, index) }"
            ></span
            ><span class="personal-share-row__name">{{
              text(metric.metricName)
            }}</span
            ><strong v-if="share(metric) !== null">{{
              rate(share(metric))
            }}</strong
            ><span v-else class="personal-muted">未配置/无数据</span>
          </div>
          <div
            v-if="!metrics.length"
            class="personal-section-state"
            role="status"
          >
            <p>未配置/无数据</p>
          </div>
        </div>
      </article>
    </section>
    <section
      class="personal-panel personal-progress-panel"
      aria-labelledby="personal-progress-heading"
    >
      <header class="personal-panel__heading">
        <div>
          <span class="personal-kicker">Goals</span>
          <h2 id="personal-progress-heading">
            {{ settings.completionTitle || "个人目标完成情况" }}
          </h2>
        </div>
      </header>
      <div class="personal-goals">
        <div
          v-for="(metric, index) in metrics"
          :key="`goal-${metric.metricCode || index}`"
          class="personal-goal"
        >
          <div class="personal-goal__label">
            <span
              >{{ text(metric.metricName) }} ·
              {{ value(metric.currentValue) }} /
              {{ metric.targetValue > 0 ? value(metric.targetValue) : "未知" }}
              {{ text(metric.unit, "") }}</span
            ><strong>{{
              progressRate(metric) === null
                ? "未知"
                : rate(progressRate(metric))
            }}</strong>
          </div>
          <div class="personal-goal__track">
            <span
              v-if="progressRate(metric) !== null"
              :style="{
                width: `${Math.min(100, Math.max(0, progressRate(metric)))}%`,
                backgroundColor: color(metric, index),
              }"
            ></span>
          </div>
        </div>
        <div
          v-if="!metrics.length"
          class="personal-section-state"
          role="status"
        >
          <p>{{ metricsSection.message || "暂无目标数据" }}</p>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { use } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { LineChart } from "echarts/charts";
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from "echarts/components";
import VChart from "vue-echarts";
import {
  Back,
  DataAnalysis,
  FullScreen,
  Refresh,
} from "@element-plus/icons-vue";
import {
  buildTrendOption,
  filterHistory,
  getStableColor,
  metricColors,
  normalizeRange,
  orgShare,
  progressRate,
  finite,
} from "./personalAnalytics";
use([
  CanvasRenderer,
  LineChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
]);
const props = defineProps({
  model: { type: Object, default: () => ({}) },
  loading: Boolean,
  backLabel: { type: String, default: "返回工作台" },
});
const emit = defineEmits([
  "refresh",
  "back",
  "fullscreen",
  "range-change",
  "metric-change",
]);
const model = computed(() =>
  props.model && typeof props.model === "object" ? props.model : {},
);
const identity = computed(() => model.value.identity || {});
const metricsSection = computed(() =>
  model.value.metrics && typeof model.value.metrics === "object"
    ? model.value.metrics
    : {},
);
const metrics = computed(() =>
  ["error", "forbidden"].includes(
    String(metricsSection.value.status || "").toLowerCase(),
  )
    ? []
    : Array.isArray(metricsSection.value.items)
      ? metricsSection.value.items.filter(
          (item) => item && typeof item === "object",
        )
      : [],
);
const settings = computed(() =>
  model.value.settings && typeof model.value.settings === "object"
    ? model.value.settings
    : {},
);
const controlledRange = computed(
  () => model.value.range || settings.value.defaultRange,
);
const range = ref(normalizeRange(controlledRange.value));
const rangeTouched = ref(false);
const selectedMetric = ref("");
const ranges = [
  { code: "7d", label: "近7日" },
  { code: "6m", label: "近6月" },
  { code: "1y", label: "近一年" },
];
const visibleMetrics = computed(() =>
  selectedMetric.value
    ? metrics.value.filter((item) => item.metricCode === selectedMetric.value)
    : metrics.value,
);
const trendMode = computed(() => (selectedMetric.value ? "actual" : "growth"));
const trendOption = computed(() =>
  buildTrendOption(
    visibleMetrics.value.map(metric => ({ ...metric, color: color(metric) })),
    range.value,
    model.value.refreshedAt || undefined,
    trendMode.value,
  ),
);
const hasTrend = computed(() =>
  visibleMetrics.value.some((item) => {
    const points = filterHistory(
      item.history,
      range.value,
      model.value.refreshedAt || undefined,
    );
    if (trendMode.value === "actual")
      return points.some((point) => finite(point.value) !== null);
    const base = points.find((point) => finite(point.value) !== null);
    return (
      base &&
      base.value !== 0 &&
      points.some((point) => finite(point.value) !== null)
    );
  }),
);
const text = (input, fallback = "—") =>
  input === null || input === undefined || input === ""
    ? fallback
    : String(input);
const value = (input) => {
  const number = finite(input);
  return number === null
    ? "—"
    : new Intl.NumberFormat("zh-CN", { maximumFractionDigits: 2 }).format(
        number,
      );
};
const rate = (input) => {
  const number = finite(input);
  return number === null ? "未知" : `${number.toFixed(2)}%`;
};
const color = (metric, index) =>
  metricColors(metrics.value)[metrics.value.indexOf(metric)] ||
  getStableColor(metric.metricCode || index, metric.color);
const share = (metric) => orgShare(metric);
function selectMetric(code) {
  selectedMetric.value = code || "";
  emit("metric-change", selectedMetric.value);
}
function changeRange(code) {
  rangeTouched.value = true;
  range.value = normalizeRange(code);
  emit("range-change", range.value);
}
function onChartClick(params) {
  if (params?.seriesName) {
    const metric = metrics.value.find(
      (item) => (item.metricName || item.metricCode) === params.seriesName,
    );
    if (metric) selectMetric(metric.metricCode || "");
  }
}
watch(controlledRange, (value) => {
  if (!rangeTouched.value) range.value = normalizeRange(value);
});
watch(
  () => identity.value.empId,
  (next, previous) => {
    if (next && next !== previous) {
      selectedMetric.value = "";
      rangeTouched.value = false;
      range.value = normalizeRange(controlledRange.value);
    }
  },
);
</script>
<style src="./personal.scss" lang="scss"></style>
