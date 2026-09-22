// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { reactive, ref } from 'vue';
const api = vi.hoisted(() => ({ catalog: vi.fn(), view: vi.fn(), data: vi.fn(), touch: vi.fn() }));
const routeState = vi.hoisted(() => ({ query: { source: 'live' } }));
const state = { model: ref({}), loading: ref(false), error: ref(''), refresh: vi.fn(async () => ({})) };
vi.mock('@/api/screen', () => ({ listAvailableScreens: api.catalog, getScreenView: api.view, queryScreenData: api.data }));
vi.mock('@/api/customerMarketing', () => ({ getTouchSummary: api.touch }));
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ user: { mainOrgCode: '109' } }) }));
vi.mock('vue-router', () => {
  const currentRoute = reactive(routeState);
  return {
    useRouter: () => ({
      push: vi.fn(),
      replace: vi.fn(({ query }) => { currentRoute.query = query; return Promise.resolve(); })
    }),
    useRoute: () => currentRoute
  };
});
vi.mock('../panorama/usePanoramaData', () => ({ usePanoramaData: () => state }));
vi.mock('../panorama/BranchOperatingDashboard.vue', () => ({ default: { props: ['model', 'error'], template: '<main>{{model.orgName}} {{error}}</main>' } }));
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
  });
  it('拒绝TEST发布包，不以真实接口为由展示模拟源', async () => {
    api.catalog.mockResolvedValue([{ screenCode: 'SCR_CORP_OVERVIEW', template: 'corporate-overview-v1', dataMode: 'TEST' }]);
    api.view.mockResolvedValue({ state: 'published', screenCode: 'SCR_CORP_OVERVIEW', runtimeSchemaVersion: 2, orgScopeMode: 'NAMED_GROUP', renderPackageJson: JSON.stringify({ schemaVersion: 2, components: [], bindSnapshots: {}, canvasStyle: { dataClassification: 'TEST', presentation: { template: 'corporate-overview-v1' } } }) });
    const wrapper = mount(BranchOperatingPage);
    await flushPromises();
    expect(api.touch).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('测试');
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
});
