// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';

const { listAvailableScreens, routerPush, menuStore } = vi.hoisted(() => ({
  listAvailableScreens: vi.fn(),
  routerPush: vi.fn(),
  menuStore: {
    loaded: true,
    loading: false,
    load: vi.fn(),
    hasUrl: vi.fn()
  }
}));

vi.mock('@/api/screen', () => ({ listAvailableScreens }));
vi.mock('@/stores/menu', () => ({ useMenuStore: () => menuStore }));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush })
}));

import ScreenCenter from '../ScreenCenter.vue';
import { useUserStore } from '@/stores/user';

const catalog = [
  {
    screenCode: 'SCR_CORP_OVERVIEW', screenName: '对公经营总览', viewLevel: 'PROVINCE', bizLine: 'CORP',
    template: 'corporate-overview-v1', dataMode: 'LIVE'
  },
  {
    screenCode: 'SCR_RETAIL_OVERVIEW', screenName: '零售经营总览', viewLevel: 'PROVINCE', bizLine: 'RETAIL',
    template: 'retail-overview-v1', dataMode: 'LIVE'
  }
];

describe('ScreenCenter 本地演示入口', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setActivePinia(createPinia());
    useUserStore().setUser({ empId: 'USER_A' });
    menuStore.loaded = true;
    menuStore.loading = false;
    menuStore.load.mockResolvedValue([]);
    menuStore.hasUrl.mockReturnValue(false);
  });

  it('目录加载失败时仍显示本地演示按钮，并保持目录错误状态', async () => {
    listAvailableScreens.mockRejectedValueOnce(new Error('目录接口不可用'));

    const wrapper = mount(ScreenCenter);
    await flushPromises();

    expect(wrapper.text()).toContain('大屏目录加载失败');
    expect(wrapper.find('[data-testid="screen-center-preview-tools"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-action="open-corporate-preview"]')).toHaveLength(1);
    expect(wrapper.findAll('[data-action="open-retail-preview"]')).toHaveLength(1);
    expect(wrapper.findAll('[data-action="open-branch-preview"]')).toHaveLength(1);
    expect(wrapper.find('[data-testid="screen-center-preview-tools"]').text()).toContain('本地演示 · 非业务数据');
  });

  it('两个演示按钮使用独立预览路由并带回到大屏中心的来源参数', async () => {
    listAvailableScreens.mockResolvedValueOnce(catalog);

    const wrapper = mount(ScreenCenter);
    await flushPromises();

    expect(wrapper.findAll('[data-screen-card]')).toHaveLength(2);
    expect(wrapper.text()).toContain('2个可访问大屏');

    await wrapper.get('[data-action="open-corporate-preview"]').trigger('click');
    expect(routerPush).toHaveBeenLastCalledWith({
      name: 'CorporateScreenPreview',
      query: { from: 'screen-center' }
    });

    await wrapper.get('[data-action="open-retail-preview"]').trigger('click');
    expect(routerPush).toHaveBeenLastCalledWith({
      name: 'RetailScreenPreview',
      query: { from: 'screen-center' }
    });

    await wrapper.get('[data-action="open-branch-preview"]').trigger('click');
    expect(routerPush).toHaveBeenLastCalledWith({
      name: 'ScreenPreview',
      query: { from: 'screen-center' }
    });
  });
});
