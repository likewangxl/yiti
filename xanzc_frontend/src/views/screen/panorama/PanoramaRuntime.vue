<template>
  <section class="panorama-runtime panorama-runtime--immersive" data-testid="panorama-runtime">
    <details class="panorama-runtime__source" data-testid="runtime-source-details">
      <summary data-testid="runtime-source-summary">
        <strong>{{ sourceClassification }}</strong>
        <span>数据日期 {{ sourceDates }}</span>
        <span :class="{ 'is-warning': model.qualityGuard || model.quality?.status !== 'COMPLETE' }">{{ qualitySummary }}</span>
        <span v-if="dataNotice?.includes('单位未登记')" class="is-warning">金额单位待核验 · 仅测试映射</span>
        <span v-if="dateIssues.length" class="is-warning">统计日期不同 · 按来源查看</span>
        <span class="panorama-runtime__source-link">来源与口径</span>
      </summary>
      <div class="panorama-runtime__source-panel">
        <div v-if="dateIssues.length" class="panorama-runtime__date-notes" role="note">
          <strong>统计日期说明</strong>
          <p v-for="item in dateIssues" :key="item.key">{{ item.label }}：{{ item.message }}</p>
        </div>
    <div
      v-if="dataNotice"
      class="panorama-runtime__data-notice panorama-runtime__data-notice--muted"
      data-testid="panorama-data-notice"
      role="note"
      aria-label="数据来源说明"
    >{{ dataNotice }}</div>

    <BatchQualityBanner
      :quality="model.quality"
      :quality-guard="model.qualityGuard"
      :queried-at="model.queriedAt || lastQueriedAt"
    />
    <template v-if="!model.quality && !model.qualityGuard">
      <div v-for="(quality, slot) in model.sourceQualities || {}" :key="slot">
        <strong>{{ BINDING_SLOTS[slot]?.label || slot }}</strong>
        <BatchQualityBanner :quality="quality" :queried-at="lastQueriedAt" />
      </div>
    </template>
      </div>
    </details>

    <component :is="isCorporate ? CorporateDashboard : isRetail ? RetailDashboard : PanoramaDashboard"
      :model="dashboardModel"
      :source-presentation="dashboardSourcePresentation"
      :loading="loading"
      :error="error"
      :demo="false"
      @refresh="onRefresh"
      @branch-select="onBranchSelect"
      @back="onBack"
      @configure="onConfigure"
    />

    <aside v-if="issueEntries.length" class="panorama-runtime__issues" data-testid="panorama-slot-issues" aria-live="polite">
      <strong>部分数据暂不可用</strong>
      <ul>
        <li v-for="item in issueEntries" :key="item.key">
          <span>{{ item.label }}：</span>{{ item.message }}
        </li>
      </ul>
    </aside>
  </section>
</template>

<script setup>
import { computed, toRef } from 'vue';
import { useRouter } from 'vue-router';
import PanoramaDashboard from './PanoramaDashboard.vue';
import CorporateDashboard from './CorporateDashboard.vue';
import RetailDashboard from './RetailDashboard.vue';
import BatchQualityBanner from './BatchQualityBanner.vue';
import { BINDING_SLOTS } from './bindings';
import { usePanoramaData } from './usePanoramaData';
import { applyMetricLabels, resolveSourcePresentation } from './sourcePresentation';
import { batchQualityLabel } from './batchQuality';

const props = defineProps({
  view: { type: Object, default: () => ({}) },
  context: { type: Object, default: () => ({}) },
  backPath: { type: String, default: '' },
  batchRequired: { type: Boolean, default: false }
});
const emit = defineEmits(['back', 'configure', 'refresh', 'branch-select']);
const router = useRouter();

const isCorporate = computed(() => props.view?.renderPackage?.canvasStyle?.presentation?.template === 'corporate-overview-v1');
const isRetail = computed(() => props.view?.renderPackage?.canvasStyle?.presentation?.template === 'retail-overview-v1');

const state = usePanoramaData(toRef(props, 'view'), toRef(props, 'context'), {
  batchRequired: props.batchRequired
});
// Pull refs to the script top level so Vue's template ref unwrapping passes
// plain model/loading/error values to the presentational Dashboard.
const model = state.model;
const loading = state.loading;
const error = state.error;
const lastQueriedAt = computed(() => state.lastQueriedAt?.value || '');
const sourcePresentation = computed(() => resolveSourcePresentation(props.view));
const dataNotice = computed(() => sourcePresentation.value.dataNotice);
const sourceClassification = computed(() => model.value?.quality?.dataClassification
  || props.view?.renderPackage?.canvasStyle?.dataClassification || '接口数据');
const sourceDates = computed(() => {
  if (model.value?.qualityGuard) return '校验未通过';
  if (model.value?.quality?.dataDate) return model.value.quality.dataDate;
  const dates = [...new Set(Object.values(model.value?.sourceQualities || {}).map(q => q.dataDate).filter(Boolean))];
  return dates.length > 1 ? `${dates.sort()[0]} 至 ${dates.at(-1)}（不同来源）` : dates[0] || model.value?.dataDate || '未提供';
});
const qualitySummary = computed(() => {
  const guard = model.value?.qualityGuard;
  if (guard) return batchQualityLabel(guard.status) || '数据校验未通过';
  if (model.value?.quality) return batchQualityLabel(model.value.quality.status);
  const statuses = [...new Set(Object.values(model.value?.sourceQualities || {}).map(q => batchQualityLabel(q.status)).filter(Boolean))];
  return statuses.join(' / ') || '以来源说明为准';
});
const dashboardModel = computed(() => applyMetricLabels(
  model.value,
  sourcePresentation.value.metricLabels
));
const dashboardSourcePresentation = computed(() => ({
  ...sourcePresentation.value,
  scopeIdentity: [props.view?.screenCode, props.view?.orgScopeMode, props.view?.orgGroupCode, props.context?.orgCode].map(v => v || '').join('|'),
  runtimeIssues: state.slotIssues.value || {},
  runtimeQuality: model.value?.qualityGuard || model.value?.quality || null
}));

const slotLabels = {
  deposit: '存款余额',
  loan: '贷款余额',
  customers: '营销有效归属客户数',
  revenue: '手工测试收入',
  rate: '目标完成率',
  trend: '经营趋势',
  composition: '业务构成',
  ranking: '机构排名',
  attention: '经营关注',
  branches: '支行机构',
  branchTrend: '支行趋势',
  citySummary: '城市汇总',
  depositIncrease: '存款较上月净增',
  depositAverage: '存款月均余额',
  batch: '批次质量'
};
const locallyExplainedNoValueSlots = new Set(Object.keys(slotLabels));

const allIssueEntries = computed(() => Object.entries(state.slotIssues.value || {})
  .flatMap(([slot, issues]) => (Array.isArray(issues) ? issues : [])
    .filter(issue => !(issue?.code === 'NO_VALUES' && locallyExplainedNoValueSlots.has(slot)))
    .map((issue, index) => ({
    key: `${slot}:${issue.code || index}`,
    code: issue.code,
    label: sourcePresentation.value.metricLabels?.[slot] || BINDING_SLOTS[slot]?.label || slotLabels[slot] || slot,
    message: issue.message || issue.code || '取数失败'
  }))));
const dateIssues = computed(() => allIssueEntries.value.filter(item => item.code === 'MIXED_DATES'));
const issueEntries = computed(() => allIssueEntries.value.filter(item => item.code !== 'MIXED_DATES'));

function onRefresh() {
  emit('refresh');
  return state.refresh();
}

function onBack() {
  emit('back');
  if (props.backPath) {
    if (router?.push) router.push(props.backPath);
    return;
  }
  if (router?.back && window.history.length > 1) router.back();
  else if (router?.push) router.push('/workspace');
  else if (window.history.length > 1) window.history.back();
}

function onConfigure() {
  emit('configure');
  const screenId = Number(props.view?.screenId || props.view?.screen_id);
  const query = Number.isSafeInteger(screenId) && screenId > 0 ? { screenId: String(screenId) } : {};
  if (router?.push) router.push({ path: '/screen-admin/designer', query });
}

function onBranchSelect(payload) {
  const orgCode = typeof payload === 'object' ? payload?.orgCode : payload;
  const code = String(orgCode || '').trim();
  if (!code) return state.model.value;
  emit('branch-select', code);
  return state.selectBranch(code);
}

defineExpose({ ...state, refresh: state.refresh, selectBranch: state.selectBranch });
</script>

<style scoped>
.panorama-runtime {
  --cockpit-chrome-height: 40px;
  min-height: 100vh;
  position: relative;
  box-sizing: border-box;
  color: #dce8f5;
  background: #071a31;
}
.panorama-runtime__source { position: relative; z-index: 30; height: 40px; background: #091629; border-bottom: 1px solid #28435b; }
.panorama-runtime__source > summary { display: flex; align-items: center; gap: 20px; height: 40px; padding: 0 24px; color: #abc1d8; font-size: 12px; cursor: pointer; list-style: none; }
.panorama-runtime__source > summary strong { color: #78e2d3; }
.panorama-runtime__source > summary .is-warning { color: #ffcc83; }
.panorama-runtime__source > summary:focus-visible { outline: 2px solid #6ce5e1; outline-offset: -3px; }
.panorama-runtime__source-link { margin-left: auto; color: #99d7ff; }
.panorama-runtime__source-panel { position: absolute; top: 40px; right: 16px; width: min(920px, calc(100vw - 32px)); max-height: 65vh; overflow: auto; padding: 14px; background: #0a1930; border: 1px solid #365a7a; border-radius: 0 0 12px 12px; box-shadow: 0 20px 60px #0008; }
@media (max-width: 720px) { .panorama-runtime__source { height: auto; min-height: 40px; } .panorama-runtime__source > summary { gap: 8px; flex-wrap: wrap; height: auto; min-height: 40px; padding: 8px 12px; } }
.panorama-runtime__data-notice {
  position: relative;
  z-index: 3;
  box-sizing: border-box;
  width: min(100% - 32px, 1180px);
  margin: 0 auto 10px;
  padding: 8px 14px;
  color: #b8c9dc;
  background: rgba(13, 36, 62, .88);
  border: 1px solid rgba(116, 151, 188, .38);
  border-radius: 5px;
  box-shadow: 0 5px 18px rgba(0, 0, 0, .18);
  font-size: 12px;
  line-height: 1.5;
}
.panorama-runtime__data-notice--muted { letter-spacing: .01em; }
.panorama-runtime__issues {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 20;
  max-width: 360px;
  padding: 10px 14px;
  color: #ffe9bd;
  background: rgba(30, 27, 18, .94);
  border: 1px solid rgba(246, 191, 73, .65);
  border-radius: 6px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, .24);
  font-size: 12px;
}
.panorama-runtime__issues strong { display: block; margin-bottom: 4px; }
.panorama-runtime__issues ul { margin: 0; padding-left: 18px; }
.panorama-runtime__issues li + li { margin-top: 3px; }
</style>
