<template>
  <section class="panorama-runtime panorama-runtime--immersive" data-testid="panorama-runtime">
    <RuntimeStatusBanner :runtime="runtimePresentation" />

    <component :is="isCorporate ? CorporateDashboard : isRetail ? RetailDashboard : PanoramaDashboard"
      :model="dashboardModel"
      :source-presentation="dashboardSourcePresentation"
      :loading="loading"
      :error="error"
      :demo="false"
      @refresh="onRefresh"
      @branch-select="onBranchSelect"
      @business-line-select="onBusinessLineSelect"
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
    <aside v-if="navigationError" class="panorama-runtime__navigation-error" data-testid="panorama-navigation-error" role="alert">
      {{ navigationError }}
    </aside>
  </section>
</template>

<script setup>
import { computed, ref, toRef } from 'vue';
import { useRouter } from 'vue-router';
import { getScreenView, listAvailableScreens } from '@/api/screen';
import PanoramaDashboard from './PanoramaDashboard.vue';
import CorporateDashboard from './CorporateDashboard.vue';
import RetailDashboard from './RetailDashboard.vue';
import { BINDING_SLOTS } from './bindings';
import { usePanoramaData } from './usePanoramaData';
import { applyMetricLabels, resolveSourcePresentation } from './sourcePresentation';
import {
  BUSINESS_LINE_TARGETS,
  buildNavigationQuery,
  parseNavigationQuery,
  resolveAuthorizedScreen,
  resolveInstitution,
  routeForBusinessLine,
  routeForInstitution
} from '../presentation/navigation/navigationModel';
import RuntimeStatusBanner from '../presentation/runtime/RuntimeStatusBanner.vue';
import { buildRuntimePresentation } from '../presentation/runtime/runtimeState';

const props = defineProps({
  view: { type: Object, default: () => ({}) },
  context: { type: Object, default: () => ({}) },
  backPath: { type: String, default: '' },
  batchRequired: { type: Boolean, default: false }
});
const emit = defineEmits(['back', 'configure', 'refresh', 'branch-select', 'business-line-select']);
const router = useRouter();
const navigationError = ref('');
const navigationPending = ref(false);
let navigationGeneration = 0;

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
const sourcePresentation = computed(() => resolveSourcePresentation(props.view));
function parseObject(value) {
  if (value && typeof value === 'object' && !Array.isArray(value)) return value;
  if (typeof value !== 'string' || !value.trim()) return null;
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}
const displayPresentation = computed(() => {
  const pkg = parseObject(props.view?.renderPackage ?? props.view?.render_package
    ?? props.view?.renderPackageJson ?? props.view?.render_package_json) || {};
  const style = parseObject(pkg.canvasStyle ?? pkg.canvas_style) || {};
  const presentation = parseObject(style.presentation) || parseObject(props.view?.canvasStyle?.presentation) || null;
  if (!presentation) return null;
  const staticAvailability = parseObject(style.sourceAvailability) || parseObject(presentation.sourceAvailability);
  return staticAvailability ? { ...presentation, sourceAvailability: staticAvailability } : presentation;
});
const dashboardModel = computed(() => {
  const labelled = applyMetricLabels(model.value, sourcePresentation.value.metricLabels);
  const title = props.view?.screenName || props.view?.screen_name;
  return title && labelled && labelled.title !== String(title)
    ? { ...labelled, title: String(title) } : labelled;
});
const runtimePresentation = computed(() => {
  const sourceMetadata = model.value?.sourceMetadata || {};
  const valuePresence = Object.fromEntries(Object.entries(sourceMetadata).map(([slot, metadata]) => [slot, metadata?.hasRows === true]));
  for (const item of Array.isArray(model.value?.kpis) ? model.value.kpis : []) {
    if (!Object.prototype.hasOwnProperty.call(valuePresence, item?.key)) valuePresence[item.key] = item?.value !== null && item?.value !== undefined;
  }
  return buildRuntimePresentation({
    enabled: displayPresentation.value?.displaySchemaVersion === 1,
    configuredSlots: model.value?.configuredSlots || [],
    runtimeIssues: state.slotIssues.value || {},
    sourceQualities: model.value?.sourceQualities || {},
    sourceDates: model.value?.sourceDates || {},
    staticAvailability: displayPresentation.value?.sourceAvailability || sourcePresentation.value.sourceAvailability || {},
    valuePresence,
    quality: model.value?.quality || null,
    qualityGuard: model.value?.qualityGuard || null,
    permissionStatus: model.value?.permissionStatus || null,
    error: error.value,
    loading: loading.value,
    queriedAt: model.value?.queriedAt || state.lastQueriedAt?.value || ''
  });
});
const dashboardSourcePresentation = computed(() => ({
  ...sourcePresentation.value,
  displayPresentation: props.view?.renderPackage?.canvasStyle?.presentation || null,
  scopeIdentity: [props.view?.screenCode, props.view?.orgScopeMode, props.view?.orgGroupCode, props.context?.orgCode].map(v => v || '').join('|'),
  runtimeIssues: state.slotIssues.value || {},
  runtimeQuality: model.value?.qualityGuard || model.value?.quality || null,
  runtimeState: runtimePresentation.value
}));

const navigationContext = computed(() => {
  const context = props.context && typeof props.context === 'object' ? props.context : {};
  const template = props.view?.renderPackage?.canvasStyle?.presentation?.template;
  const fallbackLine = Object.values(BUSINESS_LINE_TARGETS).find(item => item.template === template)?.businessLine || '';
  return parseNavigationQuery(buildNavigationQuery({
    cityCode: context.cityCode,
    orgCode: context.orgCode,
    businessLine: context.businessLine || fallbackLine,
    period: context.period,
    metricKey: context.metricKey,
    view: context.navigationView,
    state: context.navigationState,
    source: context.source
  }));
});

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
const issueEntries = computed(() => allIssueEntries.value);

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
  if (!code) return;
  navigationError.value = '';
  const resolved = resolveInstitution(props.view, code);
  if (!resolved.authorized) {
    navigationError.value = '当前机构不在本屏授权目录中，已拒绝进入。';
    return;
  }
  if (!resolved.layer.known) {
    navigationError.value = '当前机构经营层级待确认，暂不进入机构主路径。';
    return;
  }
  if (!resolved.layer.displayable) {
    navigationError.value = '当前机构不属于允许展示的经营层级，已拒绝进入。';
    return;
  }
  const target = routeForInstitution({
    ...navigationContext.value,
    orgCode: code,
    cityCode: resolved.institution?.cityCode || navigationContext.value.cityCode,
    businessLine: navigationContext.value.businessLine || 'COMMON'
  });
  if (!target || !router?.push) {
    navigationError.value = '机构导航目标不可用，已拒绝进入。';
    return;
  }
  emit('branch-select', code);
  return router.push(target);
}

async function onBusinessLineSelect(payload) {
  const businessLine = String(payload?.businessLine || '').trim().toUpperCase();
  const tabKey = String(payload?.tabKey || '').trim();
  if (!['COMMON', 'CORP', 'RETAIL'].includes(businessLine) || !/^[A-Za-z0-9_-]{1,64}$/.test(tabKey)) {
    navigationError.value = '条线导航动作无效，已拒绝进入。';
    return;
  }
  const token = ++navigationGeneration;
  navigationError.value = '';
  navigationPending.value = true;
  const payloadContext = payload?.context && typeof payload.context === 'object' ? payload.context : {};
  const orgCode = String(payloadContext.orgCode || navigationContext.value.orgCode || '').trim();
  try {
    await resolveAuthorizedScreen({
      businessLine,
      orgCode,
      listAvailableScreens,
      getScreenView
    });
    if (token !== navigationGeneration) return;
    const target = routeForBusinessLine(businessLine, {
      ...navigationContext.value,
      ...payloadContext,
      businessLine,
      orgCode,
      metricKey: tabKey
    });
    if (!target || !router?.push) throw new Error('固定条线目标不可用');
    await router.push(target);
    if (token !== navigationGeneration) return;
    emit('business-line-select', { businessLine, tabKey, context: payloadContext });
  } catch (error) {
    if (token !== navigationGeneration) return;
    navigationError.value = error?.message || '目标条线授权确认失败，已拒绝进入。';
  } finally {
    if (token === navigationGeneration) navigationPending.value = false;
  }
}

defineExpose({ ...state, refresh: state.refresh, selectBranch: state.selectBranch });
</script>

<style scoped>
.panorama-runtime {
  --cockpit-chrome-height: 0px;
  min-height: 100vh;
  position: relative;
  box-sizing: border-box;
  color: #dce8f5;
  background: #071a31;
}
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
