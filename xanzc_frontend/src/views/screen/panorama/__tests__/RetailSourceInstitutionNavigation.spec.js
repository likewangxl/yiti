// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({ getScreenView: vi.fn(), listAvailableScreens: vi.fn() }));
const router = vi.hoisted(() => ({ push: vi.fn(), back: vi.fn() }));
vi.mock('@/api/screen', () => ({ getScreenView: api.getScreenView, listAvailableScreens: api.listAvailableScreens }));
vi.mock('vue-router', () => ({ useRouter: () => router }));
vi.mock('../PanoramaDashboard.vue', () => ({ default: { template: '<main>综合</main>' } }));
vi.mock('../CorporateDashboard.vue', () => ({ default: { template: '<main>对公</main>' } }));
vi.mock('../RetailDashboard.vue', () => ({
  default: {
    emits: ['branch-select'],
    template: '<main><button data-action="select-branch" @click="$emit(\'branch-select\', \'RETAIL-ORG-1\')">机构</button></main>'
  }
}));
vi.mock('../usePanoramaData', () => ({ usePanoramaData: () => ({
  model: { value: { kpis: [] } }, loading: { value: false }, error: { value: '' }, slotIssues: { value: {} },
  refresh: vi.fn(), selectBranch: vi.fn()
}) }));

import PanoramaRuntime from '../PanoramaRuntime.vue';

const currentView = {
  screenCode: 'SCR_RETAIL_OVERVIEW',
  state: 'published',
  renderPackage: { canvasStyle: { presentation: { template: 'retail-overview-v1' } } },
  institutionRules: { allowedOperatingLevels: ['PRIMARY'], allowedOrgNatures: ['BRANCH'] },
  panoramaInstitutions: [{ orgCode: 'RETAIL-ORG-1', cityCode: '610100' }]
};

const sourceView = {
  screenCode: 'SCR_PROVINCE',
  state: 'draft',
  runtimeSchemaVersion: 2,
  renderPackage: {
    schemaVersion: 2,
    canvasStyle: { presentation: { type: 'CODE', template: 'branch-overview-v1' } },
    components: [],
    bindSnapshots: {}
  },
  institutionRules: { allowedOperatingLevels: ['PRIMARY'], allowedOrgNatures: ['BRANCH'] },
  panoramaInstitutions: [{
    orgCode: 'RETAIL-ORG-1', cityCode: '610100', operatingLevel: 'PRIMARY', orgNature: 'BRANCH'
  }]
};

function mountRuntime(context = {}) {
  return mount(PanoramaRuntime, {
    props: {
      view: currentView,
      context: {
        businessLine: 'RETAIL', cityCode: '610100', metricKey: 'deposit',
        ...context
      }
    }
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  api.getScreenView.mockResolvedValue(sourceView);
  api.listAvailableScreens.mockResolvedValue([]);
});

describe('PanoramaRuntime 零售机构源屏层级复核', () => {
  it('当前零售目录只有城市且层级未知时，按白名单源屏复核后进入固定支行路由', async () => {
    const wrapper = mountRuntime({ sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' });

    await wrapper.get('[data-action="select-branch"]').trigger('click');
    await flushPromises();

    expect(api.getScreenView).toHaveBeenCalledWith('SCR_PROVINCE', 'draft');
    expect(router.push).toHaveBeenCalledWith({
      name: 'BranchOperatingPage',
      query: {
        cityCode: '610100', orgCode: 'RETAIL-ORG-1', businessLine: 'RETAIL', metricKey: 'deposit',
        sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft'
      }
    });
    wrapper.unmount();
  });

  it('无源屏上下文时仍拒绝未知层级，不猜测机构经营层级', async () => {
    const wrapper = mountRuntime();

    await wrapper.get('[data-action="select-branch"]').trigger('click');
    await flushPromises();

    expect(api.getScreenView).not.toHaveBeenCalled();
    expect(router.push).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="panorama-navigation-error"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('源屏响应身份、状态、规则或目录不匹配时 fail-close', async () => {
    const cases = [
      { response: { ...sourceView, screenCode: 'SCR_OTHER' } },
      { response: { ...sourceView, state: 'published' } },
      { response: { ...sourceView, institutionRules: [] } },
      { response: { ...sourceView, panoramaInstitutions: [] } }
    ];

    for (const entry of cases) {
      api.getScreenView.mockResolvedValueOnce(entry.response);
      const wrapper = mountRuntime({ sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' });
      await wrapper.get('[data-action="select-branch"]').trigger('click');
      await flushPromises();
      expect(router.push).not.toHaveBeenCalled();
      wrapper.unmount();
    }
  });

  it('源屏 403、明确排除当前机构或层级仍未知时 fail-close', async () => {
    const cases = [
      { rejected: new Error('403 Forbidden') },
      {
        response: {
          ...sourceView,
          institutionRules: { ...sourceView.institutionRules, displayOrgCodes: ['OTHER-ORG'] }
        }
      },
      {
        response: {
          ...sourceView,
          panoramaInstitutions: [{ orgCode: 'RETAIL-ORG-1', cityCode: '610100' }]
        }
      }
    ];

    for (const entry of cases) {
      if (entry.rejected) api.getScreenView.mockRejectedValueOnce(entry.rejected);
      else api.getScreenView.mockResolvedValueOnce(entry.response);
      const wrapper = mountRuntime({ sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' });
      await wrapper.get('[data-action="select-branch"]').trigger('click');
      await flushPromises();
      expect(router.push).not.toHaveBeenCalled();
      wrapper.unmount();
    }
  });

  it('重复点击时只允许最新源屏复核完成后导航，迟到响应不能再次 push', async () => {
    let release;
    const firstRequest = new Promise(resolve => { release = resolve; });
    api.getScreenView.mockImplementationOnce(() => firstRequest).mockResolvedValueOnce(sourceView);
    const wrapper = mountRuntime({ sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' });

    await wrapper.get('[data-action="select-branch"]').trigger('click');
    await wrapper.get('[data-action="select-branch"]').trigger('click');
    await flushPromises();
    expect(router.push).toHaveBeenCalledTimes(1);

    release(sourceView);
    await flushPromises();
    expect(router.push).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('源屏复核期间上下文变化或组件卸载都会丢弃迟到路由', async () => {
    let release;
    const pending = new Promise(resolve => { release = resolve; });
    api.getScreenView.mockReturnValue(pending);
    const wrapper = mountRuntime({ sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' });

    await wrapper.get('[data-action="select-branch"]').trigger('click');
    await wrapper.setProps({ context: {
      businessLine: 'RETAIL', cityCode: '610500', metricKey: 'loan',
      sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft'
    } });
    release(sourceView);
    await flushPromises();
    expect(router.push).not.toHaveBeenCalled();
    wrapper.unmount();

    let releaseAfterUnmount;
    const afterUnmount = new Promise(resolve => { releaseAfterUnmount = resolve; });
    api.getScreenView.mockReturnValue(afterUnmount);
    const disposed = mountRuntime({ sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' });
    await disposed.get('[data-action="select-branch"]').trigger('click');
    disposed.unmount();
    releaseAfterUnmount(sourceView);
    await flushPromises();
    expect(router.push).not.toHaveBeenCalled();
  });

  it('当前零售目录不授权时不读取源屏、不进入路由', async () => {
    const wrapper = mount(PanoramaRuntime, {
      props: {
        view: { ...currentView, panoramaInstitutions: [] },
        context: { businessLine: 'RETAIL', sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' }
      }
    });

    await wrapper.get('[data-action="select-branch"]').trigger('click');
    await flushPromises();

    expect(api.getScreenView).not.toHaveBeenCalled();
    expect(router.push).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="panorama-navigation-error"]').text()).toContain('不在本屏授权目录');
    wrapper.unmount();
  });
});
