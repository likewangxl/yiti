<template>
  <section class="branch-operating-page">
    <div class="branch-operating-source-bar">
      <strong>支行经营总览 · {{ isTestSource ? 'TEST 测试数据' : (view.screenCode ? '已绑定数据源' : '核对数据来源') }}</strong>
      <span v-if="isTestSource" data-testid="branch-operating-test-banner">测试数据 · 非实际经营数据</span>
      <span v-else>当前接入对公指标与触达汇总</span>
      <span class="branch-operating-unit-note">{{ isTestSource ? '源金额：元 · 展示：万元' : '金额单位沿用来源映射，待业务核验' }}</span>
      <nav class="branch-operating-source-switch" aria-label="数据场景">
        <button type="button" data-testid="branch-operating-source-test" :class="{ 'is-active': isTestSource }" @click="switchSource('test')">测试场景</button>
        <button type="button" data-testid="branch-operating-source-live" :class="{ 'is-active': !isTestSource }" @click="switchSource('live')">系统存量</button>
      </nav>
      <details><summary>来源与口径</summary><div>
        <p v-for="(source, index) in dashboard.sources" :key="index"><strong>{{ source.label }}</strong> {{ source.detail }}</p>
        <p v-if="isTestSource">当前为 TEST 批次，展示内容仅用于接口联调，不代表实际经营数据。</p>
        <template v-else>
          <p>经营关注来自考核目标差距，不代表审批待办。资产项目与团队数据未接入。</p>
          <p>触达统计区间：{{ touchPeriod.startDate }} 至 {{ touchPeriod.endDate }}；经营指标日期以各卡片为准。</p>
        </template>
      </div></details>
    </div>
    <BranchOperatingDashboard :model="dashboard" :source-presentation="branchDisplayPresentation"
      :loading="loading || (!isTestSource && financialLoading)" :error="visibleError"
      @refresh="initialize" @back="router.push('/screens')" @branch-select="selectBranch" />
  </section>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { listAvailableScreens, getScreenView, queryScreenData } from '@/api/screen';
import { getTouchSummary } from '@/api/customerMarketing';
import { useUserStore } from '@/stores/user';
import { usePanoramaData } from './panorama/usePanoramaData';
import { buildBranchOperatingModel, parseBranchSource, toBranchDisplayUnits } from './panorama/branchOperatingModel';
import BranchOperatingDashboard from './panorama/BranchOperatingDashboard.vue';
import { loadBranchDeposit, chooseCoveredBranch } from './panorama/branchOperatingSource';
import {
  BRANCH_TEST_SCREEN_CODE,
  BRANCH_TEST_SOURCE_LABEL,
  branchTestInstitutions,
  buildBranchTestModel,
  buildBranchTestRequest,
  parseBranchTestView
} from './panorama/branchTestDataset';

const router = useRouter(), route = useRoute(), user = useUserStore();
const view = ref({}), context = ref({}), institutions = ref([]), selected = ref('');
const touch = ref(null), touchError = ref(''), loading = ref(false), pageError = ref('');
const testModel = ref(null);
let generation = 0;
const sourceMode = computed(() => String(route.query?.source || '').trim().toLowerCase() === 'live' ? 'live' : 'test');
const isTestSource = computed(() => sourceMode.value === 'test');
let activeSourceMode = '';
const state = usePanoramaData(view, context, { singleOrg: true, autoLoad: false, watch: false });
const financialLoading = state.loading;
const baseFinancial = state.model;
const depositData = ref(null), depositError = ref('');
const financial = computed(() => {
  const base = baseFinancial.value;
  const current = depositData.value?.current;
  return { ...base,
    kpis: [...(base.kpis || []).filter(k => k.key !== 'corpDeposit'), ...(current ? [{ key: 'corpDeposit', value: current.value, unit: '亿元', date: current.date }] : [])],
    trend: depositData.value?.trend || [], comparisonTrend: depositData.value?.comparisonTrend || [],
    sourceQualities: { ...base.sourceQualities, ...(current?.quality ? { corpDeposit: current.quality } : {}) },
    issues: [...(base.issues || []), ...(depositError.value ? [{ slot: 'corpDeposit', code: 'REQUEST_FAILED', message: depositError.value }] : [])]
  };
});
const localDay = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const touchPeriod = ref({ startDate: '', endDate: '' });
function emptyTestModel() {
  return {
    orgCode: '', orgName: '', institutions: [], dataDate: '', sourceLabel: BRANCH_TEST_SOURCE_LABEL,
    kpis: [], targets: [], targetDate: '', trend: [], trendUnit: '亿元', composition: [], marketing: [],
    projects: [], teams: [], attention: [], gaps: {}, sources: []
  };
}
const dashboard = computed(() => {
  if (isTestSource.value) return toBranchDisplayUnits(testModel.value || emptyTestModel());
  return toBranchDisplayUnits(buildBranchOperatingModel({
    orgCode: selected.value, orgName: institutions.value.find(i => String(i.orgCode) === selected.value)?.orgName || '支行经营总览',
    institutions: institutions.value, financial: financial.value, touch: touch.value, touchError: touchError.value, view: view.value, touchPeriod: touchPeriod.value
  }));
});
const branchDisplayPresentation = computed(() => {
  if (isTestSource.value) return null;
  let pkg = view.value?.renderPackage;
  if (!pkg && typeof view.value?.renderPackageJson === 'string') {
    try { pkg = JSON.parse(view.value.renderPackageJson); } catch { pkg = null; }
  }
  return pkg?.canvasStyle?.presentation || null;
});
const visibleError = computed(() => pageError.value || (!isTestSource.value && view.value.screenCode ? state.error.value : '')
  || (!isTestSource.value && (financial.value.issues || []).some(i => i.code === 'REQUEST_FAILED')
    ? '经营数据取数失败，请检查数据源连接后刷新。当前未用模拟数据补齐。' : ''));

function clearPageState() {
  baseFinancial.value = {};
  depositData.value = null;
  depositError.value = '';
  touch.value = null;
  touchError.value = '';
  testModel.value = null;
  view.value = {};
  context.value = {};
  institutions.value = [];
  selected.value = '';
}

async function queryTestModel(token) {
  const code = String(selected.value || '').trim();
  if (!code) throw new Error('TEST 屏缺少有效机构号');
  const response = await queryScreenData(buildBranchTestRequest(view.value, code));
  if (token !== generation) return false;
  const model = buildBranchTestModel(response, view.value, code);
  if (token !== generation) return false;
  // 首次无机构请求建立的目录是本页的授权候选；显式机构请求只负责当前模型，
  // 不用单机构响应缩短下拉目录。
  testModel.value = { ...model, institutions: institutions.value };
  return true;
}

async function loadSelected() {
  if (isTestSource.value) {
    const token = ++generation;
    loading.value = true;
    pageError.value = '';
    testModel.value = null;
    context.value = { screenCode: view.value.screenCode, schemaVersion: 2, orgCode: selected.value };
    try {
      await queryTestModel(token);
    } catch (error) {
      if (token === generation) {
        clearPageState();
        pageError.value = error.message || 'TEST 数据请求失败';
      }
    } finally {
      if (token === generation) loading.value = false;
    }
    return;
  }
  const token = ++generation;
  touch.value = null;
  touchError.value = '';
  pageError.value = '';
  baseFinancial.value = {};
  depositData.value = null;
  depositError.value = '';
  loading.value = true;
  context.value = { screenCode: view.value.screenCode, schemaVersion: 2, orgCode: selected.value };
  const today = new Date();
  touchPeriod.value = { startDate: localDay(new Date(today.getFullYear(), today.getMonth(), 1)), endDate: localDay(today) };
  try {
    await Promise.all([
      state.refresh(),
      (view.value.branchDepositBinding ? loadBranchDeposit({ query: queryScreenData, screenCode: view.value.screenCode,
        entry: view.value.branchDepositBinding, orgCode: selected.value, isCurrent: () => token === generation,
        onCurrent: current => { if (token === generation) depositData.value = { current }; }
      }).then(result => { if (token === generation) depositData.value = result; }).catch(error => {
        if ([401, 403].includes(Number(error?.response?.status || error?.status))) throw error;
        if (token === generation) depositError.value = error.message || '支行存款查询失败';
      }) : Promise.resolve()),
      getTouchSummary({ orgCode: selected.value, ...touchPeriod.value }).then(result => {
        if (token === generation) touch.value = result;
      }).catch(error => { if (token === generation) touchError.value = `触达汇总暂不可用：${error.message || '请求失败'}`; })
    ]);
  } catch (error) {
    if (token === generation) {
      pageError.value = error.message || '数据源请求失败';
      if ([401, 403].includes(Number(error?.response?.status || error?.status))) {
        generation += 1;
        depositData.value = null;
        touch.value = null;
        context.value = {};
        await state.refresh();
        loading.value = false;
      }
    }
  } finally { if (token === generation) loading.value = false; }
}

async function selectBranch(value) {
  const code = String(typeof value === 'object' ? value.orgCode || '' : value || '').trim();
  if (!institutions.value.some(i => String(i.orgCode) === code)) return;
  selected.value = code;
  router.replace({ query: { ...route.query, orgCode: code } });
  return loadSelected();
}

function switchSource(mode) {
  const next = mode === 'live' ? 'live' : 'test';
  const previous = sourceMode.value;
  const query = { ...route.query };
  if (next === 'live') query.source = 'live';
  else delete query.source;
  const navigation = router.replace({ query });
  // Vue Router 的正常导航会触发下面的 route watch；保留一个 promise 后备，
  // 让嵌入式宿主使用异步 replace 时也一定重新取数，而不只切换计算模式。
  Promise.resolve(navigation).then(() => {
    if (previous !== next && sourceMode.value === next && activeSourceMode !== next) void initialize();
  });
}

async function initializeTest(token) {
  const response = await getScreenView(BRANCH_TEST_SCREEN_CODE);
  if (token !== generation) return;
  view.value = parseBranchTestView(response);
  const listing = await queryScreenData(buildBranchTestRequest(view.value));
  if (token !== generation) return;
  const available = branchTestInstitutions(listing, view.value);
  if (!available.length) throw new Error('TEST 授权目录暂无返回机构');
  institutions.value = available;
  const routeCode = String(route.query?.orgCode || '').trim();
  const preferred = [routeCode, '330'].find(code => available.some(item => String(item.orgCode) === code));
  selected.value = preferred || String(available[0].orgCode);
  await queryTestModel(token);
}

async function initialize() {
  const token = ++generation;
  const mode = sourceMode.value;
  const previousMode = activeSourceMode;
  activeSourceMode = mode;
  loading.value = true;
  pageError.value = '';
  clearPageState();
  try {
    if (mode === 'test') {
      // 首次进入 TEST 不需要刷新空 hook；从 live 切换时要用空上下文推进旧 hook
      // 代际。usePanoramaData 在空视图下不会生成任何数据请求。
      if (previousMode === 'live') {
        await state.refresh();
        if (token !== generation) return;
      }
      await initializeTest(token);
      if (token === generation) loading.value = false;
      return;
    }
    // 立即推进旧 live hook 代际，废弃重验授权之前的所有在途请求。
    await state.refresh();
    if (token !== generation) return;
    const catalog = await listAvailableScreens();
    if (token !== generation) return;
    if (!Array.isArray(catalog) || !catalog.some(i => i.screenCode === 'SCR_CORP_OVERVIEW' && i.template === 'corporate-overview-v1')) throw new Error('当前账号未授权支行经营数据来源');
    const response = await getScreenView('SCR_CORP_OVERVIEW');
    if (token !== generation) return;
    view.value = parseBranchSource(response);
    // 名称筛选只决定本页候选展示，不推定层级/地域，更不增加后端授权范围。
    institutions.value = (view.value.panoramaInstitutions || []).filter(i => i.orgNature === 'LOCAL_BRANCH'
      || (!['DEPARTMENT', 'SECONDARY_BRANCH'].includes(i.orgNature) && /支行$/.test(i.orgName || '')));
    if (!institutions.value.length) throw new Error('已授权目录暂无支行，请完善机构画像或授权范围');
    let coveredCode = '';
    if (!route.query.orgCode && !selected.value && view.value.branchDepositBinding) {
      const target = view.value.renderPackage.components.find(c => c.propValue?.bindingKey === 'corpTargets');
      const request = blockId => queryScreenData({ schemaVersion: 2, screenCode: view.value.screenCode, blockId, period: 'LATEST', contextParams: {} });
      const coverage = await Promise.allSettled([request(view.value.branchDepositBinding.blockId), target ? request(target.blockId) : Promise.resolve(null)]);
      if (token !== generation) return;
      const denied = coverage.find(r => r.status === 'rejected' && [401, 403].includes(Number(r.reason?.response?.status || r.reason?.status)));
      if (denied) throw denied.reason;
      coveredCode = chooseCoveredBranch(coverage[0].value, coverage[1].value, view.value.branchDepositBinding, institutions.value);
    }
    const candidates = [route.query.orgCode, selected.value, coveredCode, user.user?.mainOrgCode, '109'];
    selected.value = String(candidates.find(code => institutions.value.some(i => String(i.orgCode) === String(code))) || institutions.value[0].orgCode);
    await loadSelected();
  } catch (error) {
    if (token !== generation) return;
    clearPageState();
    pageError.value = error.message || '数据来源加载失败';
    loading.value = false;
  }
}
onMounted(initialize);
watch(() => sourceMode.value, (next, previous) => {
  if (next === previous) return;
  void initialize();
});
onBeforeUnmount(() => { generation += 1; });
</script>

<style scoped>
.branch-operating-page { min-height: 100vh; color: #f5f2e9; background: #10131f; }
.branch-operating-source-bar { min-height: 38px; padding: 8px 24px; box-sizing: border-box; display: flex; align-items: center; gap: 22px; font-size: 12px; background: #0b0d16; border-bottom: 1px solid #343647; }
.branch-operating-source-bar > strong { color: #83e5d5; }
.branch-operating-unit-note { color: #ecc18a; }
.branch-operating-source-switch { margin-left: auto; display: inline-flex; overflow: hidden; border: 1px solid #375879; border-radius: 4px; }
.branch-operating-source-switch button { padding: 4px 10px; border: 0; border-right: 1px solid #375879; color: #a7d9f4; background: transparent; cursor: pointer; font: inherit; }
.branch-operating-source-switch button:last-child { border-right: 0; }
.branch-operating-source-switch button.is-active { color: #071a31; background: #83e5d5; }
.branch-operating-source-bar details { margin-left: auto; }
.branch-operating-source-bar summary { cursor: pointer; color: #a7d9f4; }
.branch-operating-source-bar details > div { position: absolute; z-index: 50; right: 24px; top: 38px; width: min(760px, calc(100vw - 60px)); max-height: 60vh; overflow: auto; background: #10283f; border: 1px solid #375879; padding: 18px; box-shadow: 0 18px 40px #0008; line-height: 1.8; }
@media (max-width: 1000px) { .branch-operating-source-bar { flex-wrap: wrap; gap: 8px 16px; } }
</style>
