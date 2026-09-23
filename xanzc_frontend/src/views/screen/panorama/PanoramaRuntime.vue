<template>
  <section class="panorama-runtime panorama-runtime--immersive" data-testid="panorama-runtime">
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
import { buildRuntimePresentation } from '../presentation/runtime/runtimeState';
import { buildInstitutionViewModel } from '../presentation/model/institutionViewModel';

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

const INSTITUTION_NON_METRIC_KEYS = new Set([
  'orgCode', 'org_code', 'orgName', 'org_name', 'cityCode', 'city_code', 'cityName', 'city_name',
  'ownerOperatingOrgCode', 'owner_operating_org_code', 'parentOrgCode', 'parent_org_code',
  'operatingLevel', 'operating_level', 'orgNature', 'org_nature', 'lng', 'longitude', 'lat', 'latitude',
  'coordSys', 'coord_sys', 'located', 'locationSource', 'location_source', 'active', 'authorized',
  'isActive', 'isAuthorized', 'status', 'contributionStatus', 'aggregateAllowed', 'duplicateName',
  'locationStatus', 'name', 'label', 'trend', 'attention', 'metrics', 'metricValues', 'values'
]);

function institutionCode(item) {
  if (!item || typeof item !== 'object') return '';
  return String(item.orgCode ?? item.org_code ?? '').trim();
}

function contributionMetrics(item) {
  const metrics = item && typeof item.metrics === 'object' && !Array.isArray(item.metrics)
    ? { ...item.metrics } : {};
  if (!item || typeof item !== 'object') return metrics;
  for (const [key, value] of Object.entries(item)) {
    if (INSTITUTION_NON_METRIC_KEYS.has(key) || (value !== null && typeof value === 'object')) continue;
    metrics[key] = value;
  }
  return metrics;
}

/**
 * 当前查询模型只提供指标贡献，不提供机构身份。按 orgCode 合并贡献，
 * 防止 ranking/branches 两个槽位把同一机构重复扩成展示节点。
 */
function collectInstitutionContributions(currentModel) {
  const byCode = new Map();
  for (const source of [currentModel?.institutions, currentModel?.rankings]) {
    if (!Array.isArray(source)) continue;
    for (const item of source) {
      const orgCode = institutionCode(item);
      if (!orgCode) continue;
      const previous = byCode.get(orgCode);
      byCode.set(orgCode, {
        orgCode,
        metrics: { ...(previous?.metrics || {}), ...contributionMetrics(item) }
      });
    }
  }
  return [...byCode.values()];
}

function institutionRuntimeIssues(result) {
  if (!result) return [];
  return (Array.isArray(result.issues) ? result.issues : []).map(item => ({
    slot: 'institutions',
    code: item?.code || 'INSTITUTION_UNAVAILABLE',
    field: item?.orgCode ? 'orgCode' : undefined,
    message: item?.orgCode ? `机构 ${item.orgCode}：${item.message || item.code}` : (item?.message || item?.code)
  }));
}

function mergeInstitutionRuntimeModel(currentModel, result) {
  if (!result) return currentModel;
  const previousByCode = new Map((Array.isArray(currentModel?.institutions) ? currentModel.institutions : [])
    .map(item => [institutionCode(item), item]));
  const displayInstitutions = result.displayInstitutions.map(item => {
    const previous = previousByCode.get(item.orgCode);
    return previous ? {
      ...previous,
      ...item,
      // directory identity and the validated view-model flags win; query-only
      // trend/attention fields remain available to the legacy directory UI.
      metrics: { ...(previous.metrics || {}), ...(item.metrics || {}) }
    } : item;
  });
  const displayCodes = new Set(displayInstitutions.map(item => item.orgCode));
  const rankings = Array.isArray(currentModel?.rankings)
    ? currentModel.rankings.filter(item => displayCodes.has(institutionCode(item))) : currentModel?.rankings;
  // 新协议的服务端目录已经按 institutionRules 收窄；旧命名组查询仍可能返回组内
  // 其他合法行，适配器会先拒绝合并并产生 UNAUTHORIZED_ORG。这里把这种“预期过滤”
  // 从页面故障中移除，其他缺列、单位、权限等真实问题继续保留。
  const sourceIssues = (Array.isArray(currentModel?.issues) ? currentModel.issues : [])
    .filter(issue => issue?.code !== 'UNAUTHORIZED_ORG');
  return {
    ...currentModel,
    institutions: displayInstitutions,
    rankings,
    issues: [...sourceIssues, ...institutionRuntimeIssues(result)]
  };
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
const institutionDisplayModel = computed(() => {
  if (displayPresentation.value?.displaySchemaVersion !== 1) return null;
  // The directory and rules must come from the render response. In particular,
  // navigationRules and query rows are not substitutes for this authorization input.
  return buildInstitutionViewModel({
    panoramaInstitutions: props.view?.panoramaInstitutions,
    institutionRules: props.view?.institutionRules,
    contributions: collectInstitutionContributions(model.value)
  });
});
const institutionRuntimeIssueGroups = computed(() => {
  const groups = Object.fromEntries(Object.entries(state.slotIssues.value || {}).map(([slot, issues]) => [
    slot,
    institutionDisplayModel.value && Array.isArray(issues)
      ? issues.filter(issue => issue?.code !== 'UNAUTHORIZED_ORG') : issues
  ]));
  const issues = institutionRuntimeIssues(institutionDisplayModel.value);
  if (issues.length) groups.institutions = issues;
  return groups;
});
const dashboardModel = computed(() => {
  const labelled = applyMetricLabels(model.value, sourcePresentation.value.metricLabels);
  const institutionModel = institutionDisplayModel.value;
  const displayModel = mergeInstitutionRuntimeModel(labelled, institutionModel);
  const title = props.view?.screenName || props.view?.screen_name;
  return title && displayModel && displayModel.title !== String(title)
    ? { ...displayModel, title: String(title) } : displayModel;
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
    runtimeIssues: institutionRuntimeIssueGroups.value,
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
  runtimeIssues: institutionRuntimeIssueGroups.value,
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
  institutions: '机构目录',
  batch: '批次质量'
};
const locallyExplainedNoValueSlots = new Set(Object.keys(slotLabels));

const allIssueEntries = computed(() => Object.entries(institutionRuntimeIssueGroups.value)
  .flatMap(([slot, issues]) => (Array.isArray(issues) ? issues : [])
    .filter(issue => !(issue?.code === 'NO_VALUES' && locallyExplainedNoValueSlots.has(slot)))
    .map((issue, index) => ({
    key: `${slot}:${issue.code || index}:${issue.field || ''}:${issue.message || ''}`,
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
