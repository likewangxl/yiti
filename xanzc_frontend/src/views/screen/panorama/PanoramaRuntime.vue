<template>
  <section class="panorama-runtime panorama-runtime--immersive" data-testid="panorama-runtime">
    <div
      v-if="dataNotice"
      class="panorama-runtime__data-notice panorama-runtime__data-notice--muted"
      data-testid="panorama-data-notice"
      role="note"
      aria-label="数据来源说明"
    >{{ dataNotice }}</div>

    <component :is="isRetail ? RetailDashboard : PanoramaDashboard"
      :model="dashboardModel"
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
import RetailDashboard from './RetailDashboard.vue';
import { BINDING_SLOTS } from './bindings';
import { usePanoramaData } from './usePanoramaData';
import { applyMetricLabels, resolveSourcePresentation } from './sourcePresentation';

const props = defineProps({
  view: { type: Object, default: () => ({}) },
  context: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['back', 'configure', 'refresh', 'branch-select']);
const router = useRouter();

const isRetail = computed(() => props.view?.renderPackage?.canvasStyle?.presentation?.template === 'retail-overview-v1');

const state = usePanoramaData(toRef(props, 'view'), toRef(props, 'context'));
// Pull refs to the script top level so Vue's template ref unwrapping passes
// plain model/loading/error values to the presentational Dashboard.
const model = state.model;
const loading = state.loading;
const error = state.error;
const sourcePresentation = computed(() => resolveSourcePresentation(props.view));
const dataNotice = computed(() => sourcePresentation.value.dataNotice);
const dashboardModel = computed(() => applyMetricLabels(
  model.value,
  sourcePresentation.value.metricLabels
));

const slotLabels = {
  deposit: '存款余额',
  loan: '贷款余额',
  customers: '客户总量',
  revenue: '营收',
  rate: '目标完成率',
  trend: '经营趋势',
  composition: '业务构成',
  ranking: '机构排名',
  attention: '经营关注',
  branches: '支行机构',
  branchTrend: '支行趋势',
  citySummary: '城市汇总',
  depositIncrease: '存款较上月净增',
  depositAverage: '存款月均余额'
};

const issueEntries = computed(() => Object.entries(state.slotIssues.value || {})
  .flatMap(([slot, issues]) => (Array.isArray(issues) ? issues : []).map((issue, index) => ({
    key: `${slot}:${issue.code || index}`,
    label: BINDING_SLOTS[slot]?.label || slotLabels[slot] || slot,
    message: issue.message || issue.code || '取数失败'
  }))));

function onRefresh() {
  emit('refresh');
  return state.refresh();
}

function onBack() {
  emit('back');
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
  min-height: 100vh;
  position: relative;
  box-sizing: border-box;
  color: #dce8f5;
  background: #071a31;
}
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
