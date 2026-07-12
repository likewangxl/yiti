// @vitest-environment happy-dom
// ScreenView 运行时外壳——改调新渲染契约(getScreenView 支持 preview),把渲染包/地图点位透传给
// ScreenRenderer。renderPackageJson=null(屏从未发布,已知后端行为本期不改)必须判空渲染
// "该大屏尚未发布"引导态，不得裸 JSON.parse(null)（简报原样代码用 `{components:[]}` 兜底会
// 吞掉这个引导态语义，属真实 bug，已修复，见 impl-t10 报告）。
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

const routeState = { params: { screenCode: 'SCR_TEST' }, query: {} };
vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ back: vi.fn(), push: vi.fn() })
}));

const getScreenViewMock = vi.fn();
vi.mock('@/api/screen', () => ({ getScreenView: (...args) => getScreenViewMock(...args) }));

import ScreenView from '../ScreenView.vue';

const stubs = {
  ScreenRenderer: {
    template: '<div class="stub-renderer" :data-pkg="JSON.stringify(renderPackage)" '
      + ':data-points="JSON.stringify(mapPoints)" />',
    props: ['renderPackage', 'mapPoints', 'context']
  }
};

let wrapper;
describe('ScreenView.vue', () => {
  beforeEach(() => {
    getScreenViewMock.mockReset();
    routeState.query = {};
  });
  afterEach(() => { wrapper?.unmount(); });

  it('load() 把 route.query.preview 透传给 getScreenView', async () => {
    routeState.query = { preview: 'draft' };
    getScreenViewMock.mockResolvedValue({ screenName: 'X', renderPackageJson: '{"components":[]}', mapPoints: [] });
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    expect(getScreenViewMock).toHaveBeenCalledWith('SCR_TEST', 'draft');
  });

  it('renderPackageJson 有值时 JSON.parse 后连同 mapPoints 一并传给 ScreenRenderer', async () => {
    getScreenViewMock.mockResolvedValue({
      screenName: 'X', renderPackageJson: '{"components":[{"id":"a"}]}', mapPoints: [{ orgCode: 'O1' }]
    });
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    const el = wrapper.find('.stub-renderer');
    expect(el.exists()).toBe(true);
    expect(JSON.parse(el.attributes('data-pkg'))).toEqual({ components: [{ id: 'a' }] });
    expect(JSON.parse(el.attributes('data-points'))).toEqual([{ orgCode: 'O1' }]);
  });

  it('renderPackageJson 为 null(屏从未发布)时不渲染 ScreenRenderer,展示"该大屏尚未发布"引导态', async () => {
    getScreenViewMock.mockResolvedValue({ screenName: 'X', renderPackageJson: null, mapPoints: [] });
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    expect(wrapper.find('.stub-renderer').exists()).toBe(false);
    expect(wrapper.text()).toContain('该大屏尚未发布');
  });

  it('接口失败时展示 loadError 文案而非白屏', async () => {
    getScreenViewMock.mockRejectedValue(new Error('网络异常'));
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    expect(wrapper.find('.stub-renderer').exists()).toBe(false);
    expect(wrapper.text()).toContain('网络异常');
  });
});
