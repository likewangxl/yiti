// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
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
  useRouter: () => ({ push: routerPush }),
  useRoute: () => ({})
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
    template: 'retail-overview-v1', dataMode: 'LIVE'
  }
];

let wrapper;
beforeEach(() => {
  vi.clearAllMocks();
  setActivePinia(createPinia());
  useUserStore().setUser({ empId: 'USER_A' });
  menuStore.loaded = true;
  menuStore.loading = false;
  menuStore.load.mockResolvedValue([]);
  menuStore.hasUrl.mockReturnValue(true);
  listAvailableScreens.mockResolvedValue(catalog);
});
afterEach(() => wrapper?.unmount());

async function mountCenter() {
  wrapper = mount(ScreenCenter);
  await flushPromises();
  return wrapper;
}

describe('ScreenCenter.vue', () => {
  it('对公目录可筛选并导航到独立模板', async () => {
    listAvailableScreens.mockResolvedValue([...catalog, { screenCode: 'SCR_CORP_OVERVIEW', screenName: '对公经营总览', bizLine: 'CORP', template: 'corporate-overview-v1', dataMode: 'LIVE' }]);
    const center = await mountCenter();
    await center.find('button[data-biz-line="CORP"]').trigger('click');
    expect(center.findAll('[data-screen-card]')).toHaveLength(1);
    await center.find('[data-screen-code="SCR_CORP_OVERVIEW"] button').trigger('click');
    expect(routerPush).toHaveBeenCalledWith({ name: 'CodeScreenPage', params: { template: 'corporate-overview-v1' } });
  });

  it('呈现后端目录、接口数据标签并按模板导航到受保护页面', async () => {
    const center = await mountCenter();

    expect(menuStore.load).toHaveBeenCalledTimes(1);
    expect(center.findAll('[data-screen-card]')).toHaveLength(3);
    expect(center.text()).toContain('3个可访问大屏');
    expect(center.text()).toContain('分行经营总览');
    expect(center.text()).toContain('零售经营总览');
    expect(center.text()).toContain('测试库数据');
    expect(center.text()).toContain('已接入数据');
    expect(center.find('[data-screen-kind="personal"]').text()).toContain('我的经营驾驶舱');
    expect(center.find('[data-screen-kind="personal"]').text()).toContain('个人');
    expect(center.find('[data-screen-kind="personal"]').text()).toContain('本人业务数据');
    expect(center.find('[data-screen-kind="personal"]').text()).toContain('个人核心指标');
    expect(center.find('[data-screen-kind="personal"]').text()).toContain('今日优先事项');
    expect(center.find('[data-screen-kind="personal"]').text()).toContain('我的客户');
    expect(center.find('[data-screen-kind="personal"]').text()).toContain('我发起的业务进度');

    await center.find('[data-screen-code="SCR_RETAIL_OVERVIEW"] button').trigger('click');
    expect(routerPush).toHaveBeenCalledWith({
      name: 'CodeScreenPage',
      params: { template: 'retail-overview-v1' }
    });
  });

  it('只有工作台菜单授权且当前用户存在时才显示个人入口', async () => {
    menuStore.hasUrl.mockReturnValue(false);
    const center = await mountCenter();

    expect(center.findAll('[data-screen-card]')).toHaveLength(2);
    expect(center.find('[data-screen-kind="personal"]').exists()).toBe(false);
  });

  it('没有当前用户时不显示个人入口，也不发起菜单授权加载', async () => {
    useUserStore().clear();
    wrapper = mount(ScreenCenter);
    await flushPromises();

    expect(menuStore.load).not.toHaveBeenCalled();
    expect(wrapper.find('[data-screen-kind="personal"]').exists()).toBe(false);
  });

  it('菜单加载失败时清理个人入口，但不把机构目录失败伪装成成功', async () => {
    menuStore.loaded = false;
    menuStore.load.mockRejectedValueOnce(new Error('菜单接口不可用'));
    const center = await mountCenter();

    expect(center.findAll('[data-screen-card]')).toHaveLength(2);
    expect(center.find('[data-screen-kind="personal"]').exists()).toBe(false);

    center.unmount();
    menuStore.load.mockResolvedValueOnce([]);
    listAvailableScreens.mockRejectedValueOnce(new Error('目录接口不可用'));
    wrapper = mount(ScreenCenter);
    await flushPromises();
    expect(wrapper.text()).toContain('大屏目录加载失败');
    expect(wrapper.find('[data-screen-kind="personal"]').exists()).toBe(false);
  });

  it('机构目录为空但个人授权有效时只显示个人卡片，不显示机构空态', async () => {
    listAvailableScreens.mockResolvedValueOnce([]);
    const center = await mountCenter();

    expect(center.findAll('[data-screen-card]')).toHaveLength(1);
    expect(center.find('[data-screen-kind="personal"]').exists()).toBe(true);
    expect(center.text()).not.toContain('当前没有已接入大屏');
  });

  it('机构目录为空且菜单仍 pending 时保持加载态，不闪现机构空态', async () => {
    const pendingMenu = deferred();
    menuStore.loaded = false;
    menuStore.loading = true;
    menuStore.load.mockReturnValueOnce(pendingMenu.promise);
    listAvailableScreens.mockResolvedValueOnce([]);
    wrapper = mount(ScreenCenter);
    await flushPromises();

    expect(wrapper.attributes('aria-busy')).toBe('true');
    expect(wrapper.text()).not.toContain('当前没有已接入大屏');

    menuStore.loaded = true;
    menuStore.loading = false;
    menuStore.hasUrl.mockReturnValue(true);
    pendingMenu.resolve([]);
    await flushPromises();
    expect(wrapper.find('[data-screen-kind="personal"]').exists()).toBe(true);
  });

  it('个人入口参与综合筛选和计数，可用个人关键词搜索，零售筛选不误显示', async () => {
    listAvailableScreens.mockResolvedValueOnce([]);
    const center = await mountCenter();

    expect(center.text()).toContain('1个可访问大屏');
    await center.find('input[aria-label="搜索大屏"]').setValue('个人大屏');
    expect(center.find('[data-screen-kind="personal"]').exists()).toBe(true);
    await center.find('button[data-biz-line="RETAIL"]').trigger('click');
    expect(center.findAll('[data-screen-card]')).toHaveLength(0);
    expect(center.text()).toContain('没有匹配的大屏');
    await center.find('button[data-biz-line="ALL"]').trigger('click');
    await center.find('input[aria-label="搜索大屏"]').setValue('我的经营驾驶舱');
    await center.find('[data-screen-kind="personal"] button').trigger('click');
    expect(routerPush).toHaveBeenCalledWith({ name: 'PersonalDashboard', query: { from: 'screen-center' } });
  });

  it('按条线标签和名称/编码搜索，匹配不到时显示独立空态', async () => {
    const center = await mountCenter();
    await center.find('button[data-biz-line="RETAIL"]').trigger('click');
    expect(center.findAll('[data-screen-card]')).toHaveLength(1);

    const search = center.find('input[aria-label="搜索大屏"]');
    await search.setValue('不存在的屏');
    expect(center.findAll('[data-screen-card]')).toHaveLength(0);
    expect(center.text()).toContain('没有匹配的大屏');
    expect(center.text()).not.toContain('当前没有可访问大屏');
  });

  it('区分真正空目录、失败重试，并在失败时清空旧目录', async () => {
    menuStore.hasUrl.mockReturnValue(false);
    listAvailableScreens.mockResolvedValueOnce([]);
    wrapper = mount(ScreenCenter);
    await flushPromises();
    expect(wrapper.text()).toContain('当前没有已接入大屏');
    wrapper.unmount();

    listAvailableScreens.mockRejectedValueOnce(new Error('目录接口不可用'));
    wrapper = mount(ScreenCenter);
    await flushPromises();
    expect(wrapper.findAll('[data-screen-card]')).toHaveLength(0);
    expect(wrapper.text()).toContain('大屏目录加载失败');
    expect(wrapper.text()).toContain('目录接口不可用');

    listAvailableScreens.mockResolvedValueOnce([catalog[0]]);
    await wrapper.find('button[data-action="retry-screen-catalog"]').trigger('click');
    await flushPromises();
    expect(wrapper.findAll('[data-screen-card]')).toHaveLength(1);
    expect(wrapper.text()).toContain('分行经营总览');
  });

  it('用户切换和卸载后的迟到响应不能恢复上一用户目录', async () => {
    const first = deferred();
    const second = deferred();
    listAvailableScreens.mockReset();
    listAvailableScreens.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    wrapper = mount(ScreenCenter);
    useUserStore().setUser({ empId: 'USER_B' });
    await nextTick();
    expect(listAvailableScreens).toHaveBeenCalledTimes(2);

    second.resolve([catalog[1]]);
    await flushPromises();
    expect(wrapper.text()).toContain('零售经营总览');
    first.resolve([catalog[0]]);
    await flushPromises();
    expect(wrapper.text()).toContain('零售经营总览');
    expect(wrapper.text()).not.toContain('分行经营总览');
  });

  it('用户退出后，迟到的菜单和目录响应不能恢复个人入口', async () => {
    const menu = deferred();
    const screens = deferred();
    menuStore.loaded = false;
    menuStore.load.mockReturnValueOnce(menu.promise);
    listAvailableScreens.mockReturnValueOnce(screens.promise);
    wrapper = mount(ScreenCenter);
    useUserStore().clear();
    await nextTick();

    menuStore.loaded = true;
    menuStore.hasUrl.mockReturnValue(true);
    menu.resolve([]);
    screens.resolve([]);
    await flushPromises();

    expect(wrapper.find('[data-screen-kind="personal"]').exists()).toBe(false);
    expect(wrapper.findAll('[data-screen-card]')).toHaveLength(0);
  });

  it('用户切换后，迟到的旧菜单响应不能重新写入个人入口', async () => {
    const firstMenu = deferred();
    const secondMenu = deferred();
    menuStore.loaded = false;
    menuStore.load.mockReturnValueOnce(firstMenu.promise).mockReturnValueOnce(secondMenu.promise);
    listAvailableScreens.mockResolvedValue([]);
    wrapper = mount(ScreenCenter);
    useUserStore().setUser({ empId: 'USER_B' });
    await nextTick();
    expect(menuStore.load).toHaveBeenCalledTimes(2);

    menuStore.loaded = true;
    menuStore.hasUrl.mockReturnValue(true);
    secondMenu.resolve([]);
    await flushPromises();
    expect(wrapper.find('[data-screen-kind="personal"]').exists()).toBe(true);

    firstMenu.resolve([]);
    await flushPromises();
    expect(wrapper.find('[data-screen-kind="personal"]').exists()).toBe(true);
  });
});

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}
