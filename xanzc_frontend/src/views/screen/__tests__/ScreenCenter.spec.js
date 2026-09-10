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

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

const catalog = [
  { screenCode: 'SCR_COMMON', screenName: '综合经营全景', viewLevel: 'PROVINCE', bizLine: 'COMMON' },
  { screenCode: 'SCR_CORP', screenName: '对公经营总览', viewLevel: 'BRANCH', bizLine: 'CORP' },
  { screenCode: 'SCR_RETAIL', screenName: '零售经营总览', viewLevel: 'PERSON', bizLine: 'RETAIL' },
  { screenCode: 'SCR_UNKNOWN', screenName: '专项协同视图', viewLevel: 'BRANCH', bizLine: 'NEW_LINE' }
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
  it('呈现后端目录、中文条线/视角且只通过 ScreenView 参数导航', async () => {
    const center = await mountCenter();

    expect(center.findAll('[data-screen-card]')).toHaveLength(4);
    expect(center.text()).toContain('4个可访问大屏');
    expect(center.text()).toContain('综合经营全景');
    expect(center.text()).toContain('对公');
    expect(center.text()).toContain('零售');
    expect(center.text()).toContain('其他条线');
    expect(center.text()).toContain('全辖');
    expect(center.text()).not.toContain('指标');

    await center.find('[data-screen-code="SCR_RETAIL"] button').trigger('click');
    expect(routerPush).toHaveBeenCalledWith({
      name: 'ScreenView',
      params: { screenCode: 'SCR_RETAIL' }
    });
  });

  it('按条线标签和名称/编码搜索，匹配不到时显示独立空态', async () => {
    const center = await mountCenter();
    await center.find('button[data-biz-line="CORP"]').trigger('click');
    expect(center.findAll('[data-screen-card]')).toHaveLength(1);
    expect(center.text()).toContain('对公经营总览');

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
    expect(wrapper.text()).not.toContain('没有匹配的大屏');
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
    expect(wrapper.text()).toContain('综合经营全景');
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
    expect(wrapper.text()).toContain('对公经营总览');
    first.resolve([catalog[0]]);
    await flushPromises();
    expect(wrapper.text()).toContain('对公经营总览');
    expect(wrapper.text()).not.toContain('综合经营全景');

    const late = deferred();
    listAvailableScreens.mockReturnValueOnce(late.promise);
    useUserStore().setUser({ empId: 'USER_C' });
    await nextTick();
    wrapper.unmount();
    late.resolve([catalog[2]]);
    await flushPromises();
  });
});
