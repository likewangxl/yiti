// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { reactive, ref } from 'vue';
const api = vi.hoisted(() => ({ catalog: vi.fn(), view: vi.fn(), data: vi.fn(), touch: vi.fn() }));
const routeState = vi.hoisted(() => ({ query: { source: 'live' } }));
const hookHarness = vi.hoisted(() => ({ view: null, context: null, options: null }));
const routerHarness = vi.hoisted(() => ({ router: null }));
const state = { model: ref({}), loading: ref(false), error: ref(''), refresh: vi.fn(async () => ({})) };
vi.mock('@/api/screen', () => ({ listAvailableScreens: api.catalog, getScreenView: api.view, queryScreenData: api.data }));
vi.mock('@/api/customerMarketing', () => ({ getTouchSummary: api.touch }));
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ user: { mainOrgCode: '109' } }) }));
vi.mock('vue-router', () => {
  const currentRoute = reactive(routeState);
  const router = {
    push: vi.fn(),
    replace: vi.fn(({ query }) => { currentRoute.query = query; return Promise.resolve(); })
  };
  routerHarness.router = router;
  return {
    useRouter: () => router,
    useRoute: () => currentRoute
  };
});
vi.mock('../panorama/usePanoramaData', () => ({ usePanoramaData: (view, context, options) => {
  hookHarness.view = view;
  hookHarness.context = context;
  hookHarness.options = options;
  return state;
} }));
vi.mock('../panorama/BranchOperatingDashboard.vue', () => ({ default: {
  props: ['model', 'error', 'sourcePresentation'],
  template: '<main data-testid="branch-dashboard-probe" :data-source-presentation="sourcePresentation ? \'present\' : \'null\'">{{model.orgName}} {{model.kpis?.find(item => item.key === \'deposit\')?.value}} {{model.institutions?.map(item => item.orgCode).join(\',\')}} {{error}}</main>'
} }));
import BranchOperatingPage from '../BranchOperatingPage.vue';
import { BRANCH_TEST_COLUMNS, BRANCH_TEST_SCREEN_CODE } from '../panorama/branchTestDataset';

const testInstitutions = [
  { orgCode: '330', orgName: '测试三三〇支行', orgNature: 'BRANCH', operatingLevel: 'PRIMARY_BRANCH' },
  { orgCode: '331', orgName: '测试三三一支行', orgNature: 'BRANCH', operatingLevel: 'PRIMARY_BRANCH' }
];
const testView = {
  state: 'published', screenCode: BRANCH_TEST_SCREEN_CODE, runtimeSchemaVersion: 2, orgScopeMode: 'NAMED_GROUP',
  navigationRules: { allowedOperatingLevels: ['PRIMARY_BRANCH'], allowedOrgNatures: ['BRANCH'] },
  panoramaInstitutions: testInstitutions,
  renderPackageJson: JSON.stringify({
    schemaVersion: 2,
    canvasStyle: { dataClassification: 'TEST', presentation: { type: 'CODE', template: 'branch-overview-v1' } },
    components: [{ component: 'ChartWidget', innerType: 'TABLE_LIST', blockId: 9330, propValue: { bindingKey: 'attention' } }],
    bindSnapshots: { 9330: { componentType: 'TABLE_LIST', bind: { dsId: 9331, period: 'LATEST' } } }
  })
};
const testQuality = { dataClassification: 'TEST', version: 'TEST_BRANCH_OPERATING_20260921', batchId: 'TEST_BRANCH_OPERATING_20260921', dataDate: '2026-09-21' };
function testRow(orgCode, key, value) {
  const row = Object.fromEntries(BRANCH_TEST_COLUMNS.map(column => [column, null]));
  Object.assign(row, { org_code: orgCode, kind: 'kpi', key, name: key, data_date: '2026-09-21' });
  if (key === 'customers') row.unit = 'COUNT';
  else if (key === 'rate') row.unit = 'PERCENT';
  else row.unit = 'YUAN';
  row.value = value;
  return BRANCH_TEST_COLUMNS.map(column => row[column]);
}
function testResponse(orgCodes = testInstitutions.map(item => item.orgCode)) {
  const rows = orgCodes.flatMap(code => [
    testRow(code, 'deposit', 100000000), testRow(code, 'depositAverage', 90000000),
    testRow(code, 'loan', 80000000), testRow(code, 'revenue', 125000),
    testRow(code, 'customers', 2), testRow(code, 'rate', 50)
  ]);
  return { columns: BRANCH_TEST_COLUMNS, rows, quality: { ...testQuality } };
}

describe('支行总览入口', () => {
  beforeEach(() => { vi.clearAllMocks(); routeState.query = { source: 'live' }; state.error.value = ''; state.model.value = {}; });
  it('目录没有授权对公来源时不请求发布包和业务数据', async () => {
    api.catalog.mockResolvedValue([]);
    const wrapper = mount(BranchOperatingPage);
    await flushPromises();
    expect(api.view).not.toHaveBeenCalled();
    expect(api.touch).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('未授权');
    wrapper.unmount();
  });
  it('拒绝TEST发布包，不以真实接口为由展示模拟源', async () => {
    api.catalog.mockResolvedValue([{ screenCode: 'SCR_CORP_OVERVIEW', template: 'corporate-overview-v1', dataMode: 'TEST' }]);
    api.view.mockResolvedValue({ state: 'published', screenCode: 'SCR_CORP_OVERVIEW', runtimeSchemaVersion: 2, orgScopeMode: 'NAMED_GROUP', renderPackageJson: JSON.stringify({ schemaVersion: 2, components: [], bindSnapshots: {}, canvasStyle: { dataClassification: 'TEST', presentation: { template: 'corporate-overview-v1' } } }) });
    const wrapper = mount(BranchOperatingPage);
    await flushPromises();
    expect(api.touch).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('测试');
    wrapper.unmount();
  });
  it('使用授权支行编码查询本月触达，真实空值保留缺失', async () => {
    routeState.query = { source: 'live', orgCode: '109' };
    api.catalog.mockResolvedValue([{ screenCode: 'SCR_CORP_OVERVIEW', template: 'corporate-overview-v1', dataMode: 'LIVE' }]);
    api.view.mockResolvedValue({ state: 'published', screenCode: 'SCR_CORP_OVERVIEW', runtimeSchemaVersion: 2, orgScopeMode: 'NAMED_GROUP',
      navigationRules: { allowedOperatingLevels: ['PRIMARY_BRANCH'], allowedOrgNatures: ['BRANCH'] },
      panoramaInstitutions: [{ orgCode: '109', orgName: '高新开发区支行', orgNature: 'BRANCH', operatingLevel: 'PRIMARY_BRANCH' }],
      renderPackageJson: JSON.stringify({ schemaVersion: 2, components: [], bindSnapshots: {}, canvasStyle: { dataClassification: 'LIVE', presentation: { template: 'corporate-overview-v1' } } }) });
    api.touch.mockResolvedValue({ orgId: '109', pendingCount: 0 });
    const wrapper = mount(BranchOperatingPage);
    await flushPromises();
    expect(wrapper.text()).toContain('高新开发区支行');
    expect(api.touch).toHaveBeenCalledWith(expect.objectContaining({ orgCode: '109', startDate: expect.stringMatching(/^\d{4}-\d{2}-01$/) }));
    expect(wrapper.findComponent({ name: 'BranchOperatingDashboard' }).exists() || wrapper.text().includes('高新开发区支行')).toBe(true);
    wrapper.unmount();
  });

  it('默认进入 TEST 屏，先列举机构再按默认 330 查询模型，且不调用触达或旧 live hook', async () => {
    routeState.query = { orgCode: '330' };
    api.view.mockResolvedValue(testView);
    api.data.mockImplementation(async request => request.contextParams?.orgCode
      ? testResponse([request.contextParams.orgCode]) : testResponse());
    const wrapper = mount(BranchOperatingPage);
    await flushPromises();
    expect(api.view).toHaveBeenCalledWith(BRANCH_TEST_SCREEN_CODE);
    expect(api.data).toHaveBeenNthCalledWith(1, expect.objectContaining({ screenCode: BRANCH_TEST_SCREEN_CODE, contextParams: {} }));
    expect(api.data).toHaveBeenNthCalledWith(2, expect.objectContaining({ screenCode: BRANCH_TEST_SCREEN_CODE, contextParams: { orgCode: '330' } }));
    expect(api.touch).not.toHaveBeenCalled();
    expect(state.refresh).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('测试数据 · 非实际经营数据');
    expect(wrapper.text()).toContain('测试三三〇支行');
    wrapper.unmount();
  });

  it('TEST 机构范围尊重已有 route orgCode，并拒绝把其他机构数据串入当前模型', async () => {
    routeState.query = { orgCode: '331' };
    api.view.mockResolvedValue(testView);
    api.data.mockImplementation(async request => request.contextParams?.orgCode
      ? testResponse([request.contextParams.orgCode]) : testResponse());
    const wrapper = mount(BranchOperatingPage);
    await flushPromises();
    expect(api.data).toHaveBeenNthCalledWith(2, expect.objectContaining({ contextParams: { orgCode: '331' } }));
    expect(wrapper.text()).toContain('测试三三一支行');
    expect(wrapper.text()).not.toContain('测试三三〇支行');
    wrapper.unmount();
  });

  it('TEST 显式机构查询失败时清空视图、机构和旧模型，不回退到存量数据', async () => {
    routeState.query = { orgCode: '330' };
    api.view.mockResolvedValue(testView);
    api.data.mockImplementation(async request => request.contextParams?.orgCode
      ? Promise.reject(new Error('TEST 查询失败')) : testResponse());
    const wrapper = mount(BranchOperatingPage);
    await flushPromises();
    expect(wrapper.text()).toContain('TEST 查询失败');
    expect(wrapper.text()).not.toContain('测试三三〇支行');
    expect(api.touch).not.toHaveBeenCalled();
    expect(state.refresh).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('场景按钮切换会重新加载另一套视图，不把 TEST 机构模型带入系统存量', async () => {
    routeState.query = { orgCode: '330' };
    api.view.mockImplementation(async screenCode => screenCode === BRANCH_TEST_SCREEN_CODE ? testView : {
      state: 'published', screenCode: 'SCR_CORP_OVERVIEW', runtimeSchemaVersion: 2, orgScopeMode: 'NAMED_GROUP',
      navigationRules: { allowedOperatingLevels: ['PRIMARY_BRANCH'], allowedOrgNatures: ['BRANCH'] },
      panoramaInstitutions: [{ orgCode: '330', orgName: '系统存量支行', orgNature: 'BRANCH', operatingLevel: 'PRIMARY_BRANCH' }],
      renderPackageJson: JSON.stringify({ schemaVersion: 2, components: [], bindSnapshots: {}, canvasStyle: { dataClassification: 'LIVE', presentation: { template: 'corporate-overview-v1' } } })
    });
    api.catalog.mockResolvedValue([{ screenCode: 'SCR_CORP_OVERVIEW', template: 'corporate-overview-v1', dataMode: 'LIVE' }]);
    api.data.mockImplementation(async request => request.contextParams?.orgCode
      ? testResponse([request.contextParams.orgCode]) : testResponse());
    api.touch.mockResolvedValue({ orgId: '330', pendingCount: 0 });
    const wrapper = mount(BranchOperatingPage);
    await flushPromises();
    expect(wrapper.get('[data-testid="branch-operating-test-banner"]').exists()).toBe(true);
    await wrapper.get('[data-testid="branch-operating-source-live"]').trigger('click');
    await flushPromises();
    expect(api.view).toHaveBeenCalledWith('SCR_CORP_OVERVIEW');
    expect(api.touch).toHaveBeenCalledWith(expect.objectContaining({ orgCode: '330' }));
    expect(wrapper.find('[data-testid="branch-operating-test-banner"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('系统存量支行');
    expect(wrapper.text()).not.toContain('测试三三〇支行');
    const touchCallsAfterLive = api.touch.mock.calls.length;
    await wrapper.get('[data-testid="branch-operating-source-test"]').trigger('click');
    await flushPromises();
    expect(api.view).toHaveBeenCalledWith(BRANCH_TEST_SCREEN_CODE);
    expect(api.touch).toHaveBeenCalledTimes(touchCallsAfterLive);
    expect(state.refresh).toHaveBeenCalled();
    expect(wrapper.get('[data-testid="branch-operating-test-banner"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('测试三三〇支行');
    expect(wrapper.text()).not.toContain('系统存量支行');
    wrapper.unmount();
  });

  it('旧链接缺少机构上下文时显示待确认，不从目录第一家或示例编码回退', async () => {
    routeState.query = {};
    api.view.mockResolvedValue(testView);
    api.data.mockResolvedValue(testResponse());
    const wrapper = mount(BranchOperatingPage);
    await flushPromises();
    expect(wrapper.find('[data-testid="branch-operating-navigation-blocked"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('旧链接必须携带已授权机构号');
    expect(api.data).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('机构名称带支行后缀但没有服务端层级规则时拒绝主路径', async () => {
    routeState.query = { source: 'live', orgCode: 'ORG-NAME-ONLY' };
    api.catalog.mockResolvedValue([{ screenCode: 'SCR_CORP_OVERVIEW', template: 'corporate-overview-v1', dataMode: 'LIVE' }]);
    api.view.mockResolvedValue({ state: 'published', screenCode: 'SCR_CORP_OVERVIEW', runtimeSchemaVersion: 2, orgScopeMode: 'NAMED_GROUP',
      panoramaInstitutions: [{ orgCode: 'ORG-NAME-ONLY', orgName: '名称支行' }],
      renderPackageJson: JSON.stringify({ schemaVersion: 2, components: [], bindSnapshots: {}, canvasStyle: { dataClassification: 'LIVE', presentation: { template: 'corporate-overview-v1' } } }) });
    const wrapper = mount(BranchOperatingPage);
    await flushPromises();
    expect(wrapper.find('[data-testid="branch-operating-navigation-blocked"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('层级规则待确认');
    expect(api.touch).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('省级 draft 源屏进入 parent 模式，按单机构上下文刷新且隐藏旧 TEST/LIVE 切换', async () => {
    const parentView = {
      state: 'draft', screenCode: 'SCR_PROVINCE', runtimeSchemaVersion: 2, orgScopeMode: 'NAMED_GROUP',
      institutionRules: { allowedOperatingLevels: ['NONE'], allowedOrgNatures: ['OTHER'], displayOrgCodes: ['105'] },
      panoramaInstitutions: [{ orgCode: '105', orgName: '延兴门西路支行', cityCode: '610100', operatingLevel: 'NONE', orgNature: 'OTHER' }],
      renderPackageJson: JSON.stringify({ schemaVersion: 2, canvasStyle: {
        dataClassification: 'TEST', presentation: { type: 'CODE', template: 'branch-overview-v1' }
      }, components: [], bindSnapshots: {} })
    };
    routeState.query = { orgCode: '105', cityCode: '610100', sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' };
    api.view.mockResolvedValue(parentView);
    state.refresh.mockImplementation(async () => {
      state.model.value = {
        orgCode: '105', institutions: parentView.panoramaInstitutions,
        kpis: [{ key: 'deposit', value: 12, unit: '亿元' }], trend: [], targets: [], attention: []
      };
    });
    state.model.value = {
      orgCode: '105', orgName: '延兴门西路支行', cityCode: '610100', dataDate: '2026-09-21',
      kpis: [{ key: 'deposit', value: 12, unit: '亿元' }], trend: [], targets: [], attention: [],
      institutions: parentView.panoramaInstitutions, citySummaries: { '610100': { kpis: [{ key: 'deposit', value: 999 }] } }
    };
    const wrapper = mount(BranchOperatingPage);
    await flushPromises();
    expect(api.view).toHaveBeenCalledWith('SCR_PROVINCE', 'draft');
    expect(hookHarness.options).toMatchObject({ singleOrg: true, autoLoad: false, watch: false });
    expect(hookHarness.context.value).toMatchObject({ screenCode: 'SCR_PROVINCE', schemaVersion: 2, previewState: 'draft', orgCode: '105' });
    expect(state.refresh).toHaveBeenCalled();
    expect(api.catalog).not.toHaveBeenCalled();
    expect(api.touch).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="branch-operating-source-switch"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('TEST');
    expect(wrapper.get('[data-testid="branch-dashboard-probe"]').attributes('data-source-presentation')).toBe('null');
    expect(wrapper.get('[data-testid="branch-dashboard-probe"]').text()).toContain('12');
    wrapper.unmount();
  });

  it('parent 路由手动切换机构会重新读取同源屏并按新机构刷新，不保留旧机构模型', async () => {
    const parentView = {
      state: 'draft', screenCode: 'SCR_PROVINCE', runtimeSchemaVersion: 2, orgScopeMode: 'NAMED_GROUP',
      institutionRules: { allowedOperatingLevels: ['NONE'], allowedOrgNatures: ['OTHER'], displayOrgCodes: ['105', '110'] },
      panoramaInstitutions: [
        { orgCode: '105', orgName: '延兴门西路支行', cityCode: '610100', operatingLevel: 'NONE', orgNature: 'OTHER' },
        { orgCode: '110', orgName: '未央路支行', cityCode: '610100', operatingLevel: 'NONE', orgNature: 'OTHER' }
      ],
      renderPackageJson: JSON.stringify({ schemaVersion: 1, canvasStyle: {
        dataClassification: 'TEST', presentation: { type: 'CODE', template: 'branch-overview-v1' }
      }, components: [], bindSnapshots: {} })
    };
    routeState.query = { orgCode: '105', cityCode: '610100', sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' };
    api.view.mockResolvedValue(parentView);
    state.refresh.mockImplementation(async () => {
      const code = hookHarness.context.value.orgCode;
      const institution = parentView.panoramaInstitutions.find(item => item.orgCode === code);
      state.model.value = {
        orgCode: code, institutions: [institution], kpis: [{ key: 'deposit', value: code === '105' ? 12 : 22, unit: '亿元' }],
        trend: [], targets: [], attention: []
      };
    });
    const wrapper = mount(BranchOperatingPage);
    await flushPromises();
    expect(wrapper.get('[data-testid="branch-dashboard-probe"]').text()).toContain('延兴门西路支行');
    expect(wrapper.get('[data-testid="branch-dashboard-probe"]').text()).toContain('105,110');
    await routerHarness.router.replace({ query: { ...routeState.query, orgCode: '110' } });
    await flushPromises();
    expect(api.view).toHaveBeenCalledTimes(2);
    expect(hookHarness.context.value.orgCode).toBe('110');
    expect(wrapper.get('[data-testid="branch-dashboard-probe"]').text()).toContain('未央路支行');
    expect(wrapper.get('[data-testid="branch-dashboard-probe"]').text()).not.toContain('延兴门西路支行');
    expect(wrapper.get('[data-testid="branch-dashboard-probe"]').text()).toContain('22');
    wrapper.unmount();
  });
});
