// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { createPinia, setActivePinia } from 'pinia';

const { listAvailableScreens, routerPush } = vi.hoisted(() => ({
  listAvailableScreens: vi.fn(),
  routerPush: vi.fn()
}));
vi.mock('@/api/screen', () => ({ listAvailableScreens }));
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
    template: 'retail-overview-v1', dataMode: 'DEMO'
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

async function mountCenter() {
  wrapper = mount(ScreenCenter);
  await flushPromises();
  return wrapper;
}

describe('ScreenCenter.vue', () => {
  it('对公目录可筛选并导航到独立模板', async () => {
    listAvailableScreens.mockResolvedValue([...catalog, { screenCode: 'SCR_CORP_OVERVIEW', screenName: '对公经营总览', bizLine: 'CORP', template: 'corporate-overview-v1', dataMode: 'DEMO' }]);
    const center = await mountCenter();
    await center.find('button[data-biz-line="CORP"]').trigger('click');
    expect(center.findAll('[data-screen-card]')).toHaveLength(1);
    await center.find('[data-screen-code="SCR_CORP_OVERVIEW"] button').trigger('click');
    expect(routerPush).toHaveBeenCalledWith({ name: 'CodeScreenPage', params: { template: 'corporate-overview-v1' } });
  });

  it('呈现后端目录、演示标签并按模板导航到受保护页面', async () => {
    const center = await mountCenter();

    expect(center.findAll('[data-screen-card]')).toHaveLength(2);
    expect(center.text()).toContain('2个可访问大屏');
    expect(center.text()).toContain('分行经营总览');
    expect(center.text()).toContain('零售经营总览');
    expect(center.text()).toContain('测试库数据');
    expect(center.text()).toContain('演示数据');

    await center.find('[data-screen-code="SCR_RETAIL_OVERVIEW"] button').trigger('click');
    expect(routerPush).toHaveBeenCalledWith({
      name: 'CodeScreenPage',
      params: { template: 'retail-overview-v1' }
    });
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
    listAvailableScreens.mockResolvedValueOnce([]);
    wrapper = mount(ScreenCenter);
    await flushPromises();
    expect(wrapper.text()).toContain('当前没有可访问大屏');
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
