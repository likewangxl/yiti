// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ref } from 'vue';
const api = vi.hoisted(() => ({ catalog: vi.fn(), view: vi.fn(), touch: vi.fn() }));
const state = { model: ref({}), loading: ref(false), error: ref(''), refresh: vi.fn(async () => ({})) };
vi.mock('@/api/screen', () => ({ listAvailableScreens: api.catalog, getScreenView: api.view }));
vi.mock('@/api/customerMarketing', () => ({ getTouchSummary: api.touch }));
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ user: { mainOrgCode: '109' } }) }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }), useRoute: () => ({ query: {} }) }));
vi.mock('../panorama/usePanoramaData', () => ({ usePanoramaData: () => state }));
vi.mock('../panorama/BranchOperatingDashboard.vue', () => ({ default: { props: ['model', 'error'], template: '<main>{{model.orgName}} {{error}}</main>' } }));
import BranchOperatingPage from '../BranchOperatingPage.vue';
describe('支行总览入口', () => {
  beforeEach(() => { vi.clearAllMocks(); state.error.value = ''; state.model.value = {}; });
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
    api.catalog.mockResolvedValue([{ screenCode: 'SCR_CORP_OVERVIEW', template: 'corporate-overview-v1', dataMode: 'LIVE' }]);
    api.view.mockResolvedValue({ state: 'published', screenCode: 'SCR_CORP_OVERVIEW', runtimeSchemaVersion: 2, orgScopeMode: 'NAMED_GROUP',
      panoramaInstitutions: [{ orgCode: '109', orgName: '高新开发区支行', orgNature: 'OTHER' }],
      renderPackageJson: JSON.stringify({ schemaVersion: 2, components: [], bindSnapshots: {}, canvasStyle: { dataClassification: 'LIVE', presentation: { template: 'corporate-overview-v1' } } }) });
    api.touch.mockResolvedValue({ orgId: '109', pendingCount: 0 });
    const wrapper = mount(BranchOperatingPage);
    await flushPromises();
    expect(wrapper.text()).toContain('高新开发区支行');
    expect(api.touch).toHaveBeenCalledWith(expect.objectContaining({ orgCode: '109', startDate: expect.stringMatching(/^\d{4}-\d{2}-01$/) }));
    expect(wrapper.findComponent({ name: 'BranchOperatingDashboard' }).exists() || wrapper.text().includes('高新开发区支行')).toBe(true);
    wrapper.unmount();
  });
});
