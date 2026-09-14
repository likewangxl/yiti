// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';

const { listAvailableScreens, routerPush } = vi.hoisted(() => ({
  listAvailableScreens: vi.fn(),
  routerPush: vi.fn()
}));
vi.mock('@/api/screen', () => ({ listAvailableScreens }));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush }),
  useRoute: () => ({ params: { template: 'branch-overview-v1' } })
}));

import ScreenCenter from '../ScreenCenter.vue';
import { useUserStore } from '@/stores/user';

const catalog = [
  {
    screenCode: 'SCR_PROVINCE', screenName: '分行经营总览', viewLevel: 'PROVINCE', bizLine: 'COMMON',
    template: 'branch-overview-v1', dataMode: 'TEST'
  },
  {
    screenCode: 'SCR_RETAIL_OVERVIEW', screenName: '零售经营总览', viewLevel: 'BRANCH', bizLine: 'RETAIL',
    template: 'retail-overview-v1', dataMode: 'DEMO'
  },
  {
    screenCode: 'SCR_CORP_OVERVIEW', screenName: '对公经营总览', viewLevel: 'PROVINCE', bizLine: 'CORP',
    template: 'corporate-overview-v1', dataMode: 'LIVE'
  }
];

let wrapper;
beforeEach(() => {
  vi.clearAllMocks();
  setActivePinia(createPinia());
  useUserStore().setUser({ empId: 'USER_A' });
  listAvailableScreens.mockResolvedValue(catalog);
});
afterEach(() => wrapper?.unmount());

describe('ScreenCenter fixed code screens', () => {
  it('renders only registered templates with data mode labels and routes to protected pages', async () => {
    wrapper = mount(ScreenCenter);
    await flushPromises();

    expect(wrapper.findAll('[data-screen-card]')).toHaveLength(3);
    expect(wrapper.text()).toContain('测试库数据');
    expect(wrapper.text()).toContain('演示数据');
    expect(wrapper.text()).toContain('已接入数据');
    expect(wrapper.text()).toContain('统计口径与环境见来源说明');
    expect(wrapper.text()).not.toContain('旧发布屏');

    await wrapper.find('[data-screen-code="SCR_RETAIL_OVERVIEW"] button').trigger('click');
    expect(routerPush).toHaveBeenCalledWith({
      name: 'CodeScreenPage',
      params: { template: 'retail-overview-v1' }
    });
  });
});
