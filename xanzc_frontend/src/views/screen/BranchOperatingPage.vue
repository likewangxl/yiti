<template>
  <section class="branch-operating-page">
    <div class="branch-operating-source-bar">
      <strong>支行经营总览 · {{ view.screenCode ? '已绑定数据源' : '核对数据来源' }}</strong>
      <span>当前接入对公指标与触达汇总</span>
      <span class="branch-operating-unit-note">金额单位沿用来源映射，待业务核验</span>
      <details><summary>来源与口径</summary><div>
        <p v-for="(source, index) in dashboard.sources" :key="index"><strong>{{ source.label }}</strong> {{ source.detail }}</p>
        <p>经营关注来自考核目标差距，不代表审批待办。资产项目与团队数据未接入。</p>
        <p>触达统计区间：{{ touchPeriod.startDate }} 至 {{ touchPeriod.endDate }}；经营指标日期以各卡片为准。</p>
      </div></details>
    </div>
    <BranchOperatingDashboard :model="dashboard" :loading="loading || financialLoading" :error="visibleError"
      @refresh="initialize" @back="router.push('/screens')" @branch-select="selectBranch" />
  </section>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { listAvailableScreens, getScreenView, queryScreenData } from '@/api/screen';
import { getTouchSummary } from '@/api/customerMarketing';
import { useUserStore } from '@/stores/user';
import { usePanoramaData } from './panorama/usePanoramaData';
import { buildBranchOperatingModel, parseBranchSource, toBranchDisplayUnits } from './panorama/branchOperatingModel';
import BranchOperatingDashboard from './panorama/BranchOperatingDashboard.vue';
import { loadBranchDeposit, chooseCoveredBranch } from './panorama/branchOperatingSource';

const router = useRouter(), route = useRoute(), user = useUserStore();
const view = ref({}), context = ref({}), institutions = ref([]), selected = ref('');
const touch = ref(null), touchError = ref(''), loading = ref(false), pageError = ref('');
let generation = 0;
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
const dashboard = computed(() => toBranchDisplayUnits(buildBranchOperatingModel({
  orgCode: selected.value, orgName: institutions.value.find(i => String(i.orgCode) === selected.value)?.orgName || '支行经营总览',
  institutions: institutions.value, financial: financial.value, touch: touch.value, touchError: touchError.value, view: view.value, touchPeriod: touchPeriod.value
})));
const visibleError = computed(() => pageError.value || (view.value.screenCode ? state.error.value : '')
  || ((financial.value.issues || []).some(i => i.code === 'REQUEST_FAILED') ? '经营数据取数失败，请检查数据源连接后刷新。当前未用模拟数据补齐。' : ''));

async function loadSelected() {
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
  const code = String(typeof value === 'object' ? value.orgCode || '' : value || '');
  if (!institutions.value.some(i => String(i.orgCode) === code)) return;
  selected.value = code;
  router.replace({ query: { ...route.query, orgCode: code } });
  return loadSelected();
}

async function initialize() {
  const token = ++generation;
  loading.value = true;
  pageError.value = '';
  baseFinancial.value = {};
  depositData.value = null;
  depositError.value = '';
  touch.value = null;
  view.value = {};
  context.value = {};
  // 立即推进hook代际，废弃重验授权之前的所有在途请求。
  await state.refresh();
  try {
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
    view.value = {};
    institutions.value = [];
    selected.value = '';
    pageError.value = error.message || '数据来源加载失败';
    loading.value = false;
  }
}
onMounted(initialize);
onBeforeUnmount(() => { generation += 1; });
</script>

<style scoped>
.branch-operating-page { min-height: 100vh; color: #f5f2e9; background: #10131f; }
.branch-operating-source-bar { min-height: 38px; padding: 8px 24px; box-sizing: border-box; display: flex; align-items: center; gap: 22px; font-size: 12px; background: #0b0d16; border-bottom: 1px solid #343647; }
.branch-operating-source-bar > strong { color: #83e5d5; }
.branch-operating-unit-note { color: #ecc18a; }
.branch-operating-source-bar details { margin-left: auto; }
.branch-operating-source-bar summary { cursor: pointer; color: #a7d9f4; }
.branch-operating-source-bar details > div { position: absolute; z-index: 50; right: 24px; top: 38px; width: min(760px, calc(100vw - 60px)); max-height: 60vh; overflow: auto; background: #10283f; border: 1px solid #375879; padding: 18px; box-shadow: 0 18px 40px #0008; line-height: 1.8; }
@media (max-width: 1000px) { .branch-operating-source-bar { flex-wrap: wrap; gap: 8px 16px; } }
</style>
