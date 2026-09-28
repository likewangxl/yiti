// @vitest-environment happy-dom
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({ listAvailableScreens: vi.fn(), getScreenView: vi.fn() }));
const router = vi.hoisted(() => ({ push: vi.fn(), back: vi.fn() }));
vi.mock('@/api/screen', () => api);
vi.mock('vue-router', () => ({ useRouter: () => router }));
vi.mock('../PanoramaDashboard.vue', () => ({
  default: {
    emits: ['branch-select', 'business-line-select', 'back'],
    template: '<main><button data-action="select-branch" @click="$emit(\'branch-select\', \'ORG-1\')">机构</button><button data-action="select-corp" @click="$emit(\'business-line-select\', { businessLine: \'CORP\', tabKey: \'deposit\' })">公司</button><button data-action="select-evil" @click="$emit(\'business-line-select\', { businessLine: \'CORP\', tabKey: \'../../evil\' })">非法</button></main>'
  }
}));
vi.mock('../CorporateDashboard.vue', () => ({ default: { template: '<main>对公</main>' } }));
vi.mock('../RetailDashboard.vue', () => ({ default: { template: '<main>零售</main>' } }));
vi.mock('../usePanoramaData', () => ({ usePanoramaData: () => ({
  model: { value: { kpis: [] } }, loading: { value: false }, error: { value: '' }, slotIssues: { value: {} },
  refresh: vi.fn(), selectBranch: vi.fn()
}) }));

import PanoramaRuntime from '../PanoramaRuntime.vue';

const view = {
  screenCode: 'SCR_PROVINCE',
  renderPackage: { canvasStyle: { presentation: { template: 'branch-overview-v1' } } },
  navigationRules: { allowedOperatingLevels: ['PRIMARY_BRANCH'], allowedOrgNatures: ['BRANCH'] },
  panoramaInstitutions: [{ orgCode: 'ORG-1', cityCode: '610100', operatingLevel: 'PRIMARY_BRANCH', orgNature: 'BRANCH' }]
};

beforeEach(() => {
  vi.clearAllMocks();
  api.listAvailableScreens.mockResolvedValue([
    { screenCode: 'SCR_CORP_OVERVIEW', template: 'corporate-overview-v1', dataMode: 'LIVE' }
  ]);
  api.getScreenView.mockResolvedValue({ screenCode: 'SCR_CORP_OVERVIEW', navigationRules: view.navigationRules, panoramaInstitutions: view.panoramaInstitutions });
});

describe('PanoramaRuntime S13 navigation', () => {
  it('机构事件进入固定受保护支行路由并携带当前上下文', async () => {
    const wrapper = mount(PanoramaRuntime, { props: { view, context: { screenCode: 'SCR_PROVINCE', businessLine: 'COMMON', period: 'LATEST', metricKey: 'deposit', cityCode: '610100' } } });
    await wrapper.get('[data-action="select-branch"]').trigger('click');
    expect(router.push).toHaveBeenCalledWith({
      name: 'BranchOperatingPage',
      query: { cityCode: '610100', orgCode: 'ORG-1', businessLine: 'COMMON', period: 'LATEST', metricKey: 'deposit' }
    });
  });

  it('草稿省级源屏机构事件只携带固定 sourceScreenCode/sourcePreview 白名单', async () => {
    const draftView = { ...view, state: 'draft', screenCode: 'SCR_PROVINCE' };
    const wrapper = mount(PanoramaRuntime, {
      props: {
        view: draftView,
        context: {
          screenCode: 'SCR_PROVINCE', businessLine: 'COMMON', period: 'LATEST', metricKey: 'deposit',
          cityCode: '610100'
        }
      }
    });
    await wrapper.get('[data-action="select-branch"]').trigger('click');
    expect(router.push).toHaveBeenCalledWith({
      name: 'BranchOperatingPage',
      query: expect.objectContaining({
        cityCode: '610100', orgCode: 'ORG-1', businessLine: 'COMMON',
        sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft'
      })
    });
  });

  it('条线切换先复核目标目录/视图/机构交集，再进入固定模板路由', async () => {
    const wrapper = mount(PanoramaRuntime, { props: { view, context: { screenCode: 'SCR_PROVINCE', businessLine: 'COMMON', orgCode: 'ORG-1', cityCode: '610100' } } });
    await wrapper.get('[data-action="select-corp"]').trigger('click');
    await flushPromises();
    expect(api.listAvailableScreens).toHaveBeenCalledTimes(1);
    expect(api.getScreenView).toHaveBeenCalledWith('SCR_CORP_OVERVIEW');
    expect(router.push).toHaveBeenCalledWith({
      name: 'CodeScreenPage', params: { template: 'corporate-overview-v1' },
      query: { cityCode: '610100', orgCode: 'ORG-1', businessLine: 'CORP', metricKey: 'deposit' }
    });
  });

  it('目标屏机构不交集或动作令牌非法时 fail-close，不沿用当前屏或拼任意地址', async () => {
    api.getScreenView.mockResolvedValueOnce({ screenCode: 'SCR_CORP_OVERVIEW', panoramaInstitutions: [] });
    const wrapper = mount(PanoramaRuntime, { props: { view, context: { screenCode: 'SCR_PROVINCE', businessLine: 'COMMON', orgCode: 'ORG-1' } } });
    await wrapper.get('[data-action="select-corp"]').trigger('click');
    await flushPromises();
    expect(router.push).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="panorama-navigation-error"]').text()).toContain('目标屏');
    await wrapper.get('[data-action="select-evil"]').trigger('click');
    expect(router.push).not.toHaveBeenCalled();
  });
});
