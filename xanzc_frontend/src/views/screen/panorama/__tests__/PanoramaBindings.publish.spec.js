// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  listScreens: vi.fn(),
  getScreenCanvas: vi.fn(),
  listScreenDatasources: vi.fn(),
  listScreenMetricCatalog: vi.fn(async () => []),
  saveScreenCanvas: vi.fn(),
  saveScreenDatasource: vi.fn(),
  publishScreenCanvas: vi.fn()
}));
vi.mock('@/api/screen', () => api);

vi.mock('../PanoramaDataVerification.vue', () => ({ default: {
  name: 'PanoramaDataVerification', props: ['disabled'], template: '<div />'
} }));
vi.mock('../PanoramaDatasourcePicker.vue', () => ({ default: {
  name: 'PanoramaDatasourcePicker', inheritAttrs: false,
  props: ['sources', 'modelValue', 'disabled', 'placeholder'],
  emits: ['update:modelValue', 'change'],
  template: '<select v-bind="$attrs" :value="modelValue" :disabled="disabled"><option value="">请选择</option></select>'
} }));
vi.mock('../MetricBindingComposer.vue', () => ({ default: {
  name: 'MetricBindingComposer', template: '<div />'
} }));

const router = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => router
}));

const { permissionStore } = vi.hoisted(() => ({ permissionStore: {
  loaded: true,
  canAccess: vi.fn(() => true),
  load: vi.fn()
} }));
vi.mock('@/stores/permission', () => ({ usePermissionStore: () => permissionStore }));

import PanoramaBindings from '../PanoramaBindings.vue';

const elementStubs = {
  'el-button': {
    props: ['disabled', 'loading', 'title'], emits: ['click'],
    template: '<button :disabled="disabled" :title="title" :aria-busy="loading" @click="$emit(\'click\', $event)"><slot /></button>'
  },
  'el-dialog': {
    inheritAttrs: false,
    props: ['modelValue', 'title', 'closeOnClickModal', 'closeOnPressEscape', 'showClose', 'beforeClose'],
    template: '<div v-if="modelValue" v-bind="$attrs"><slot name="header" /><slot /><slot name="footer" /></div>'
  }
};

const screenA = {
  id: 9, screenCode: 'SCR_CODE', screenName: '经营总览',
  bizLine: 'COMMON', viewLevel: 'PROVINCE', orgScopeMode: 'LEGACY_CONTEXT', status: 'ACTIVE'
};
const screenB = {
  id: 10, screenCode: 'SCR_OTHER', screenName: '另一张屏',
  bizLine: 'COMMON', viewLevel: 'PROVINCE', orgScopeMode: 'LEGACY_CONTEXT', status: 'ACTIVE'
};
const source = {
  id: 77, dsName: '存款来源', dsType: 'SINGLE', sourceKind: 'WIDE_TABLE', bizLine: 'COMMON', status: 'ACTIVE',
  configJson: JSON.stringify({ fieldMeta: [{ col: 'deposit_raw', alias: '存款余额', role: 'METRIC' }] })
};

function makeCanvas(screen = screenA, canvasVersion = 4) {
  return {
    screenId: screen.id,
    screenCode: screen.screenCode,
    canvasVersion,
    canvasStyleJson: JSON.stringify({ presentation: { type: 'CODE', template: 'branch-overview-v1' } }),
    canvasDraftJson: JSON.stringify({ components: [{
      id: 'deposit', component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 41,
      propValue: { bindingKey: 'deposit' },
      bindJson: JSON.stringify({ dsId: 77, period: 'LATEST', fields: { value: 'deposit_raw' }, units: { value: 'YUAN' } })
    }] })
  };
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

async function mountPage({ screens = [screenA], selectedScreen = screenA, version = 4,
  permissionLoaded = true, publishAllowed = true } = {}) {
  api.listScreens.mockResolvedValue(screens);
  api.getScreenCanvas.mockImplementation(async id => {
    const screen = screens.find(item => Number(item.id) === Number(id)) || selectedScreen;
    return makeCanvas(screen, version);
  });
  api.listScreenDatasources.mockResolvedValue([source]);
  api.listScreenMetricCatalog.mockResolvedValue([]);
  api.saveScreenCanvas.mockResolvedValue({ canvasVersion: version + 1 });
  api.publishScreenCanvas.mockResolvedValue(undefined);
  permissionStore.loaded = permissionLoaded;
  permissionStore.canAccess.mockReturnValue(publishAllowed);
  permissionStore.load.mockReset();
  const wrapper = mount(PanoramaBindings, {
    props: { screenId: selectedScreen.id },
    global: {
      directives: { 'bp-overflow-tooltip': {} },
      stubs: elementStubs
    }
  });
  await flushPromises();
  return wrapper;
}

async function openPublish(wrapper, reason = '月度版本核对完成') {
  await requirePublishButton(wrapper).trigger('click');
  const reasonInput = wrapper.get('[data-testid="publish-reason"]');
  if (reason !== undefined) await reasonInput.setValue(reason);
  await wrapper.get('[data-testid="publish-confirmation"] input').setValue(true);
  return wrapper.get('[data-testid="publish-confirm"]');
}

function requirePublishButton(wrapper) {
  const button = wrapper.find('[data-testid="binding-publish"]');
  expect(button.exists()).toBe(true);
  return button;
}

describe('PanoramaBindings publish flow', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    permissionStore.loaded = true;
    permissionStore.canAccess.mockReturnValue(true);
    api.listScreenMetricCatalog.mockResolvedValue([]);
    router.push.mockReset();
  });

  afterEach(() => vi.restoreAllMocks());

  it('权限未加载或没有独立发布资源时 fail-close 禁用发布，并说明原因', async () => {
    const wrapper = await mountPage({ permissionLoaded: false });
    const button = requirePublishButton(wrapper);
    expect(button.element.disabled).toBe(true);
    expect(wrapper.text()).toContain('当前账号没有发布大屏的权限');
    expect(permissionStore.canAccess).not.toHaveBeenCalled();
    expect(permissionStore.load).not.toHaveBeenCalled();

    wrapper.unmount();

    const unauthorized = await mountPage({ publishAllowed: false });
    expect(requirePublishButton(unauthorized).element.disabled).toBe(true);
    expect(permissionStore.canAccess).toHaveBeenCalledWith('/api/screen/admin/canvas/publish');
    expect(unauthorized.text()).toContain('当前账号没有发布大屏的权限');
    unauthorized.unmount();
  });

  it('空白原因不能发布，取消确认不会调用发布接口', async () => {
    const wrapper = await mountPage();
    await requirePublishButton(wrapper).trigger('click');
    expect(wrapper.text()).toContain('经营总览');
    expect(wrapper.text()).toContain('正式生效');
    await wrapper.get('[data-testid="publish-reason"]').setValue('   ');
    await wrapper.get('[data-testid="publish-confirmation"] input').setValue(true);
    const confirm = wrapper.get('[data-testid="publish-confirm"]');
    expect(confirm.element.disabled).toBe(true);
    await confirm.trigger('click');
    expect(api.publishScreenCanvas).not.toHaveBeenCalled();

    await wrapper.get('[data-testid="publish-cancel"]').trigger('click');
    expect(wrapper.find('[data-testid="publish-confirmation-dialog"]').exists()).toBe(false);
    expect(api.publishScreenCanvas).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('发布使用最近一次保存返回的版本，并在成功后提供正式大屏入口', async () => {
    const wrapper = await mountPage({ version: 4 });
    api.saveScreenCanvas.mockResolvedValue({ canvasVersion: 12 });
    await wrapper.get('[data-testid="component-title"]').setValue('最新标题');
    await wrapper.get('[data-testid="binding-save"]').trigger('click');
    await vi.waitFor(() => expect(api.saveScreenCanvas).toHaveBeenCalledTimes(1));

    const confirm = await openPublish(wrapper);
    await confirm.trigger('click');
    await vi.waitFor(() => expect(api.publishScreenCanvas).toHaveBeenCalledTimes(1));
    expect(api.publishScreenCanvas).toHaveBeenCalledWith({
      screenId: 9,
      expectedVersion: 12,
      reason: '月度版本核对完成'
    });
    expect(api.saveScreenCanvas).toHaveBeenCalledTimes(1);
    await vi.waitFor(() => expect(wrapper.find('[data-testid="binding-published"]').exists()).toBe(true));
    const viewButton = wrapper.get('[data-testid="view-published-screen"]');
    expect(viewButton.text()).toContain('查看正式大屏');
    await viewButton.trigger('click');
    expect(router.push).toHaveBeenCalledWith({ name: 'ScreenView', params: { screenCode: 'SCR_CODE' } });
    wrapper.unmount();
  });

  it('发布中阻止重复请求和切换大屏，切换后清空旧结果与发布原因', async () => {
    const wrapper = await mountPage({ screens: [screenA, screenB] });
    const pending = deferred();
    api.publishScreenCanvas.mockReturnValue(pending.promise);
    const confirm = await openPublish(wrapper, '已核对屏 A');
    await confirm.trigger('click');

    await vi.waitFor(() => expect(api.publishScreenCanvas).toHaveBeenCalledTimes(1));
    expect(requirePublishButton(wrapper).element.disabled).toBe(true);
    expect(wrapper.get('[data-testid="screen-select"]').element.disabled).toBe(true);
    await wrapper.get('[data-testid="publish-confirm"]').trigger('click');
    await wrapper.setProps({ screenId: 10 });
    await flushPromises();
    expect(api.publishScreenCanvas).toHaveBeenCalledTimes(1);
    expect(api.getScreenCanvas).not.toHaveBeenCalledWith(10);
    expect(wrapper.get('[data-testid="screen-select"]').element.value).toBe('9');

    pending.resolve(undefined);
    await vi.waitFor(() => expect(wrapper.find('[data-testid="binding-published"]').exists()).toBe(true));
    await vi.waitFor(() => expect(wrapper.get('[data-testid="screen-select"]').element.disabled).toBe(false));
    await wrapper.get('[data-testid="screen-select"]').setValue('10');
    await vi.waitFor(() => expect(api.getScreenCanvas).toHaveBeenCalledWith(10));
    await vi.waitFor(() => expect(wrapper.get('[data-testid="screen-select"]').element.disabled).toBe(false));
    expect(wrapper.find('[data-testid="binding-published"]').exists()).toBe(false);

    expect(requirePublishButton(wrapper).element.disabled).toBe(false);
    await requirePublishButton(wrapper).trigger('click');
    expect(wrapper.get('[data-testid="publish-reason"]').element.value).toBe('');
    wrapper.unmount();
  });

  it.each([
    ['403', Object.assign(new Error('Forbidden'), { response: { status: 403 } })],
    ['版本冲突', Object.assign(new Error('版本冲突'), { code: 'RPT-43012' })]
  ])('发布遇到%s时保留已加载的草稿，不自动保存或清空编辑状态', async (_label, publishError) => {
    const wrapper = await mountPage();
    api.publishScreenCanvas.mockRejectedValue(publishError);
    const titleBefore = wrapper.get('[data-testid="component-title"]').element.value;
    const confirm = await openPublish(wrapper);
    await confirm.trigger('click');
    await vi.waitFor(() => expect(api.publishScreenCanvas).toHaveBeenCalled());
    await vi.waitFor(() => expect(wrapper.find('[data-testid="binding-publish-error"]').exists()
      || wrapper.find('[data-testid="binding-conflict"]').exists()).toBe(true));
    expect(wrapper.get('[data-testid="component-title"]').element.value).toBe(titleBefore);
    expect(wrapper.get('[data-testid="screen-select"]').element.value).toBe('9');
    expect(wrapper.find('[data-testid="binding-published"]').exists()).toBe(false);
    expect(api.saveScreenCanvas).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('发布成功但画布版本刷新失败时仍明确报告成功，不显示发布失败', async () => {
    const wrapper = await mountPage();
    api.getScreenCanvas.mockRejectedValueOnce(new Error('读取超时'));
    const confirm = await openPublish(wrapper);
    await confirm.trigger('click');

    await vi.waitFor(() => expect(wrapper.find('[data-testid="binding-published"]').exists()).toBe(true));
    expect(wrapper.get('[data-testid="binding-published"]').text()).toContain('已发布到正式大屏');
    await vi.waitFor(() => expect(wrapper.find('[data-testid="publish-refresh-warning"]').exists()).toBe(true));
    expect(wrapper.get('[data-testid="publish-refresh-warning"]').text()).toContain('发布已成功');
    expect(wrapper.text()).not.toContain('发布失败');
    expect(api.getScreenCanvas).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });

  it('dirty、loading 或版本无效时都不能开始发布', async () => {
    const wrapper = await mountPage({ version: null });
    expect(requirePublishButton(wrapper).element.disabled).toBe(true);
    wrapper.unmount();

    const loadingCanvas = deferred();
    api.listScreens.mockResolvedValue([screenA]);
    api.getScreenCanvas.mockReturnValue(loadingCanvas.promise);
    api.listScreenDatasources.mockResolvedValue([source]);
    permissionStore.loaded = true;
    permissionStore.canAccess.mockReturnValue(true);
    const loadingWrapper = mount(PanoramaBindings, {
      props: { screenId: 9 },
      global: {
        directives: { 'bp-overflow-tooltip': {} },
        stubs: elementStubs
      }
    });
    expect(requirePublishButton(loadingWrapper).element.disabled).toBe(true);
    loadingCanvas.resolve(makeCanvas());
    await flushPromises();
    await loadingWrapper.get('[data-testid="component-title"]').setValue('未保存标题');
    expect(requirePublishButton(loadingWrapper).element.disabled).toBe(true);
    loadingWrapper.unmount();
  });
});
