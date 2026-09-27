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

describe('ScreenCenter 新版草稿预览入口', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setActivePinia(createPinia());
    useUserStore().setUser({ empId: 'USER_A' });
    menuStore.loaded = true;
    menuStore.loading = false;
    menuStore.load.mockResolvedValue([]);
    menuStore.hasUrl.mockReturnValue(false);
    permissionStore.loaded = true;
    permissionStore.loading = false;
    permissionStore.load.mockResolvedValue([]);
    permissionStore.canAccess.mockReturnValue(true);
  });

  it('目录加载失败时 fail-close，不显示旧本地演示按钮', async () => {
    listAvailableScreens.mockRejectedValueOnce(new Error('目录接口不可用'));

    const wrapper = mount(ScreenCenter);
    await flushPromises();

    expect(wrapper.text()).toContain('大屏目录加载失败');
    expect(wrapper.find('[data-testid="screen-center-preview-tools"]').exists()).toBe(false);
    expect(wrapper.findAll('[data-action^="open-"]')).toHaveLength(0);
  });

  it('目录和画布预览权限有效时按固定编码生成三个新版草稿入口', async () => {
    listAvailableScreens.mockResolvedValueOnce(catalog);

    const wrapper = mount(ScreenCenter);
    await flushPromises();

    expect(wrapper.findAll('[data-screen-card]')).toHaveLength(4);
    expect(wrapper.text()).toContain('4个可访问大屏');
    expect(wrapper.find('[data-testid="screen-center-preview-tools"]').text()).toContain('新版草稿预览');
    expect(wrapper.find('[data-testid="screen-center-preview-tools"]').text()).toContain('未发布草稿预览');

    await wrapper.get('[data-action="open-corporate-preview"]').trigger('click');
    expect(routerPush).toHaveBeenLastCalledWith({
      name: 'ScreenView',
      params: { screenCode: 'SCR_CORP_OVERVIEW' },
      query: { preview: 'draft', from: 'screen-center' }
    });

    await wrapper.get('[data-action="open-retail-preview"]').trigger('click');
    expect(routerPush).toHaveBeenLastCalledWith({
      name: 'ScreenView',
      params: { screenCode: 'SCR_RETAIL_OVERVIEW' },
      query: { preview: 'draft', from: 'screen-center' }
    });

    await wrapper.get('[data-action="open-branch-preview"]').trigger('click');
    expect(routerPush).toHaveBeenLastCalledWith({
      name: 'ScreenView',
      params: { screenCode: 'SCR_PROVINCE' },
      query: { preview: 'draft', from: 'screen-center' }
    });
  });

  it('画布预览权限未加载或无权时 fail-close，不展示任何草稿入口', async () => {
    permissionStore.loaded = true;
    permissionStore.canAccess.mockReturnValue(false);
    listAvailableScreens.mockResolvedValueOnce(catalog);

    const wrapper = mount(ScreenCenter);
    await flushPromises();

    expect(wrapper.find('[data-testid="screen-center-preview-tools"]').exists()).toBe(false);

    wrapper.unmount();
    permissionStore.loaded = false;
    permissionStore.canAccess.mockReturnValue(true);
    listAvailableScreens.mockResolvedValueOnce(catalog);
    const pendingWrapper = mount(ScreenCenter);
    await flushPromises();
    expect(pendingWrapper.find('[data-testid="screen-center-preview-tools"]').exists()).toBe(false);
  });

  it('固定编码和模板出现重复匹配时不生成该草稿入口', async () => {
    listAvailableScreens.mockResolvedValueOnce([...catalog, { ...catalog[0] }]);

    const wrapper = mount(ScreenCenter);
    await flushPromises();

    expect(wrapper.find('[data-action="open-corporate-preview"]').exists()).toBe(false);
    expect(wrapper.find('[data-action="open-retail-preview"]').exists()).toBe(true);
    expect(wrapper.find('[data-action="open-branch-preview"]').exists()).toBe(true);
  });

  it('目录加载后权限快照失效时点击也 fail-close，不导航到草稿', async () => {
    listAvailableScreens.mockResolvedValueOnce(catalog);
    const wrapper = mount(ScreenCenter);
    await flushPromises();

    permissionStore.loaded = false;
    await wrapper.get('[data-action="open-corporate-preview"]').trigger('click');

    expect(routerPush).not.toHaveBeenCalled();
  });
});
