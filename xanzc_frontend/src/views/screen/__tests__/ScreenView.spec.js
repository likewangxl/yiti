// @vitest-environment happy-dom
// ScreenView 运行时外壳——改调新渲染契约(getScreenView 支持 preview),把渲染包/地图点位透传给
// ScreenRenderer。renderPackageJson=null(屏从未发布,已知后端行为本期不改)必须判空渲染
// "该大屏尚未发布"引导态，不得裸 JSON.parse(null)（简报原样代码用 `{components:[]}` 兜底会
// 吞掉这个引导态语义，属真实 bug，已修复，见 impl-t10 报告）。
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick, reactive } from 'vue';

const routeState = reactive({ params: { screenCode: 'SCR_TEST' }, query: {} });
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
      + ':data-points="JSON.stringify(mapPoints)" :data-context="JSON.stringify(context)" />',
    props: ['renderPackage', 'mapPoints', 'context']
  },
  PanoramaRuntime: {
    name: 'PanoramaRuntime',
    template: '<div class="stub-panorama-runtime" :data-screen="view?.screenName" '
      + ':data-context="JSON.stringify(context)" />',
    props: ['view', 'context']
  }
};

let wrapper;
describe('ScreenView.vue', () => {
  beforeEach(() => {
    getScreenViewMock.mockReset();
    routeState.params.screenCode = 'SCR_TEST';
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

  it('只有后端确认 state=draft 时才向区块传草稿取数身份', async () => {
    routeState.query = { preview: 'draft' };
    getScreenViewMock.mockResolvedValue({
      screenName: 'X', state: 'draft', runtimeSchemaVersion: 1,
      renderPackageJson: '{"components":[]}', mapPoints: []
    });
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    expect(JSON.parse(wrapper.find('.stub-renderer').attributes('data-context')).previewState).toBe('draft');
  });

  it('后端确认 state=draft 时隐藏顶部标题，但保留返回按钮和时钟', async () => {
    routeState.query = { preview: 'draft' };
    getScreenViewMock.mockResolvedValue({
      screenName: '草稿大屏', state: 'draft',
      renderPackageJson: '{"components":[]}', mapPoints: []
    });
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    expect(wrapper.find('.scr-title').exists()).toBe(false);
    expect(wrapper.find('button.scr-back').exists()).toBe(true);
    expect(wrapper.find('.scr-clock').exists()).toBe(true);
  });

  it('后端返回正式态时即使 URL 带 preview=draft 也继续显示标题', async () => {
    routeState.query = { preview: 'draft' };
    getScreenViewMock.mockResolvedValue({
      screenName: '正式大屏', state: 'published',
      renderPackageJson: '{"components":[]}', mapPoints: []
    });
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    expect(wrapper.find('.scr-title').text()).toBe('正式大屏');
    expect(wrapper.find('button.scr-back').exists()).toBe(true);
    expect(wrapper.find('.scr-clock').exists()).toBe(true);
  });

  it('返回入口是真实 button，具备键盘原生语义而不是可点击 span', async () => {
    getScreenViewMock.mockResolvedValue({ screenName: 'X', renderPackageJson: null, mapPoints: [] });
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    const back = wrapper.find('button.scr-back');
    expect(back.exists()).toBe(true);
    expect(back.attributes('type')).toBe('button');
    expect(back.attributes('aria-label')).toBe('返回上一页');
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

  it('CODE branch-overview-v1 脱离旧舞台并只渲染 PanoramaRuntime，透传 view/context', async () => {
    getScreenViewMock.mockResolvedValue({
      screenName: '代码化分行总览',
      orgScopeMode: 'NAMED_GROUP',
      renderPackageJson: JSON.stringify({
        canvasStyle: { presentation: { type: 'CODE', template: 'branch-overview-v1' } },
        components: [{ id: 'legacy-must-not-render' }]
      }),
      mapPoints: [{ orgCode: 'O1' }]
    });
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    expect(wrapper.find('.stub-panorama-runtime').exists()).toBe(true);
    expect(wrapper.find('.stub-renderer').exists()).toBe(false);
    expect(wrapper.find('.scr-stage').exists()).toBe(false);
    expect(wrapper.find('.stub-panorama-runtime').attributes('data-screen')).toBe('代码化分行总览');
    expect(JSON.parse(wrapper.find('.stub-panorama-runtime').attributes('data-context'))).toMatchObject({
      screenCode: 'SCR_TEST', schemaVersion: 2
    });
  });

  it('零售模板使用独立全景运行入口，保留 RETAIL 与已发布数据身份', async () => {
    getScreenViewMock.mockResolvedValue({
      screenName: '零售经营总览', bizLine: 'RETAIL',
      renderPackageJson: JSON.stringify({
        canvasStyle: { presentation: { type: 'CODE', template: 'retail-overview-v1' } },
        components: []
      })
    });
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    expect(wrapper.find('.stub-panorama-runtime').exists()).toBe(true);
    expect(wrapper.find('.stub-renderer').exists()).toBe(false);
    expect(wrapper.find('.screen-presentation-unsupported').exists()).toBe(false);
  });

  it('presentation=null 兼容旧画布，继续走旧 ScreenRenderer', async () => {
    getScreenViewMock.mockResolvedValue({
      screenName: '旧画布兼容屏',
      renderPackageJson: JSON.stringify({ canvasStyle: { presentation: null }, components: [] })
    });
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    expect(wrapper.find('.stub-renderer').exists()).toBe(true);
    expect(wrapper.find('.scr-stage').exists()).toBe(true);
    expect(wrapper.find('.stub-panorama-runtime').exists()).toBe(false);
    expect(wrapper.find('.screen-presentation-unsupported').exists()).toBe(false);
  });

  it('未知 presentation 明确报不支持，不能静默回退旧 ScreenRenderer', async () => {
    getScreenViewMock.mockResolvedValue({
      screenName: '未知模板屏',
      renderPackageJson: JSON.stringify({
        canvasStyle: { presentation: { type: 'CODE', template: 'future-template-v9' } },
        components: [{ id: 'legacy-must-not-render' }]
      })
    });
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    expect(wrapper.find('.stub-panorama-runtime').exists()).toBe(false);
    expect(wrapper.find('.stub-renderer').exists()).toBe(false);
    expect(wrapper.text()).toContain('暂不支持');
    expect(wrapper.text()).toContain('future-template-v9');
  });

  it('路由切换后旧请求迟到不能覆盖新屏响应', async () => {
    let resolveFirst;
    let resolveSecond;
    getScreenViewMock
      .mockImplementationOnce(() => new Promise(resolve => { resolveFirst = resolve; }))
      .mockImplementationOnce(() => new Promise(resolve => { resolveSecond = resolve; }));
    wrapper = mount(ScreenView, { global: { stubs } });
    expect(getScreenViewMock).toHaveBeenCalledWith('SCR_TEST', undefined);

    routeState.params.screenCode = 'SCR_NEXT';
    await nextTick();
    expect(getScreenViewMock).toHaveBeenCalledWith('SCR_NEXT', undefined);

    resolveSecond({ screenName: '新屏', renderPackageJson: '{"components":[]}' });
    await flushPromises();
    expect(wrapper.find('.scr-title').text()).toBe('新屏');

    resolveFirst({ screenName: '旧屏', renderPackageJson: '{"components":[]}' });
    await flushPromises();
    expect(wrapper.find('.scr-title').text()).toBe('新屏');
  });

  it('无地图的 NAMED_GROUP 屏仍向区块传 schemaVersion=2', async () => {
    getScreenViewMock.mockResolvedValue({
      screenName: '无地图范围屏', orgScopeMode: 'NAMED_GROUP',
      renderPackageJson: '{"components":[]}', mapPoints: []
    });
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    expect(JSON.parse(wrapper.find('.stub-renderer').attributes('data-context')).schemaVersion).toBe(2);
  });

  it('接口失败时展示 loadError 文案而非白屏', async () => {
    getScreenViewMock.mockRejectedValue(new Error('网络异常'));
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    expect(wrapper.find('.stub-renderer').exists()).toBe(false);
    expect(wrapper.text()).toContain('网络异常');
  });

  // rev-t10 复审 Important-1:简报 Interfaces 声明消费 utils/scale.stageStyle,但示例代码硬编码
  // Math.min(始终按 keepProportion),canvasStyle.adaptor 四策略从未接线。用不同宽高比的视口验证
  // 每种策略算出的 scale 与"若仍是旧 Math.min 硬编码"会得到的值不同,证明真正切换了公式来源。
  function setViewport(w, h) {
    Object.defineProperty(window, 'innerWidth', { value: w, configurable: true });
    Object.defineProperty(window, 'innerHeight', { value: h, configurable: true });
  }
  function stageScale(w) {
    const style = w.find('.scr-stage').attributes('style');
    const m = /scale\(([\d.]+)\)/.exec(style);
    return m ? Number(m[1]) : null;
  }

  it('canvasStyle.adaptor="keep" 时舞台不缩放(scale=1),不随视口变化', async () => {
    setViewport(800, 600); // 800/1920≈.417、600/1080≈.556——若仍走旧 Math.min 会得到 ≈.417，非 1
    getScreenViewMock.mockResolvedValue({
      screenName: 'X', renderPackageJson: '{"canvasStyle":{"adaptor":"keep"},"components":[]}', mapPoints: []
    });
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    expect(stageScale(wrapper)).toBe(1);
  });

  it('canvasStyle.adaptor="widthFirst" 时按宽度比铺满(scale=视口宽/1920)', async () => {
    setViewport(1920, 600); // sw=1、sh≈.556——旧 Math.min 会取较小的 sh≈.556，widthFirst 应为 1
    getScreenViewMock.mockResolvedValue({
      screenName: 'X', renderPackageJson: '{"canvasStyle":{"adaptor":"widthFirst"},"components":[]}', mapPoints: []
    });
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    expect(stageScale(wrapper)).toBe(1);
  });

  it('canvasStyle.adaptor="heightFirst" 时按高度比铺满(scale=视口高/1080)', async () => {
    setViewport(600, 1080); // sw≈.3125、sh=1——旧 Math.min 会取较小的 sw≈.3125，heightFirst 应为 1
    getScreenViewMock.mockResolvedValue({
      screenName: 'X', renderPackageJson: '{"canvasStyle":{"adaptor":"heightFirst"},"components":[]}', mapPoints: []
    });
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    expect(stageScale(wrapper)).toBe(1);
  });

  it('canvasStyle.adaptor 缺省(或 keepProportion)时保持原 Math.min 等比适配行为(回归)', async () => {
    setViewport(960, 1080); // sw=.5、sh=1 → min=.5
    getScreenViewMock.mockResolvedValue({
      screenName: 'X', renderPackageJson: '{"canvasStyle":{},"components":[]}', mapPoints: []
    });
    wrapper = mount(ScreenView, { global: { stubs } });
    await flushPromises();
    expect(stageScale(wrapper)).toBe(0.5);
  });
});
