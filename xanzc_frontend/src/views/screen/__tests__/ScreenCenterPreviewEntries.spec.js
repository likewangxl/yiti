// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';

const { listAvailableScreens, routerPush, menuStore, permissionStore } = vi.hoisted(() => ({
  listAvailableScreens: vi.fn(),
  routerPush: vi.fn(),
  menuStore: {
    loaded: true,
    loading: false,
    load: vi.fn(),
    hasUrl: vi.fn()
  },
  permissionStore: {
    loaded: true,
    loading: false,
    load: vi.fn(),
    canAccess: vi.fn()
  }
}));

vi.mock('@/api/screen', () => ({ listAvailableScreens }));
vi.mock('@/stores/menu', () => ({ useMenuStore: () => menuStore }));
vi.mock('@/stores/permission', () => ({ usePermissionStore: () => permissionStore }));
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
  },
  {
    screenCode: 'SCR_PROVINCE', screenName: '分行经营总览', viewLevel: 'PROVINCE', bizLine: 'COMMON',
    template: 'branch-overview-v1', dataMode: 'TEST'
  }
];

describe('ScreenCenter 已移除新版草稿和支行派生入口', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setActivePinia(createPinia());
    useUserStore().setUser({ empId: 'USER_A' });
    menuStore.loaded = true;
    menuStore.loading = false;
    menuStore.load.mockResolvedValue([]);
    menuStore.hasUrl.mockReturnValue(true);
    permissionStore.loaded = true;
    permissionStore.loading = false;
    permissionStore.load.mockResolvedValue([]);
    permissionStore.canAccess.mockReturnValue(true);
    listAvailableScreens.mockResolvedValue(catalog);
  });

  it('全目录和画布预览权限场景仍只显示四个正式入口，不显示三个草稿按钮或支行卡片', async () => {
    const wrapper = mount(ScreenCenter);
    await flushPromises();

    expect(wrapper.findAll('[data-screen-card]')).toHaveLength(4);
    expect(wrapper.text()).toContain('4个可访问大屏');
    expect(wrapper.text()).toContain('我的经营驾驶舱');
    expect(wrapper.text()).toContain('分行经营总览');
    expect(wrapper.text()).toContain('零售经营总览');
    expect(wrapper.text()).toContain('对公经营总览');
    expect(wrapper.find('[data-screen-kind="branch-operating"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('支行经营总览');
    expect(wrapper.find('[data-testid="screen-center-preview-tools"]').exists()).toBe(false);
    expect(wrapper.findAll('[data-action^="open-"]')).toHaveLength(0);
    expect(permissionStore.load).not.toHaveBeenCalled();
  });

  it('正式目录筛选、搜索和导航继续生效，支行经营总览搜索不到', async () => {
    const wrapper = mount(ScreenCenter);
    await flushPromises();

    await wrapper.get('button[data-biz-line="RETAIL"]').trigger('click');
    expect(wrapper.findAll('[data-screen-card]')).toHaveLength(1);
    expect(wrapper.get('[data-screen-code="SCR_RETAIL_OVERVIEW"]').exists()).toBe(true);
    await wrapper.get('[data-screen-code="SCR_RETAIL_OVERVIEW"] button.screen-card__open').trigger('click');
    expect(routerPush).toHaveBeenLastCalledWith({
      name: 'CodeScreenPage',
      params: { template: 'retail-overview-v1' }
    });

    await wrapper.get('button[data-biz-line="ALL"]').trigger('click');
    await wrapper.get('input[aria-label="搜索大屏"]').setValue('支行经营总览');
    expect(wrapper.findAll('[data-screen-card]')).toHaveLength(0);
    expect(wrapper.text()).toContain('没有匹配的大屏');
  });

  it('目录加载失败时不显示本地草稿或支行演示入口', async () => {
    listAvailableScreens.mockRejectedValueOnce(new Error('目录接口不可用'));

    const wrapper = mount(ScreenCenter);
    await flushPromises();

    expect(wrapper.text()).toContain('大屏目录加载失败');
    expect(wrapper.find('[data-testid="screen-center-preview-tools"]').exists()).toBe(false);
    expect(wrapper.find('[data-screen-kind="branch-operating"]').exists()).toBe(false);
    expect(wrapper.findAll('[data-action^="open-"]')).toHaveLength(0);
  });
});
