// @vitest-environment happy-dom
// 挂载冒烟测试——回归 Task 9 复审 Critical 缺陷:删 `import { useRouter } from 'vue-router'` 时
// 漏删了调用行 `const router = useRouter();`,导致 <script setup> 顶层执行时 ReferenceError,
// 组件一挂载就崩溃。纯逻辑单测(store/registry/utils)测不到这类"setup 顶层执行期"错误,
// 必须真实 mount 才能捕获,故补这个最小冒烟测试。
// element-plus 组件与本页四个子组件(CanvasCore/ComponentPanel/LayerPanel/CanvasAttr)全部 stub,
// 只关心 DesignerV2 自身能否正常挂载,不关心子组件内部渲染细节(那些各自模块已有/未来有专属测试)。
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { inject, isReactive, nextTick } from 'vue';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const routerReplace = vi.hoisted(() => vi.fn());
const findAttrMock = vi.hoisted(() => vi.fn());

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: routerReplace })
}));

vi.mock('@/views/screen/designer/widgets', () => ({
  findAttr: findAttrMock,
  chartMetas: [
    { innerType: 'BAR_COMPARE', label: '柱状对比' },
    { innerType: 'LINE_TREND', label: '趋势折线' }
  ]
}));

vi.mock('@/api/screen', () => ({
  listScreens: vi.fn().mockResolvedValue([]),
  getScreenCanvas: vi.fn(),
  saveScreenMetadata: vi.fn(),
  listScreenAccessRoles: vi.fn(),
  saveScreenAccessRoles: vi.fn(),
  listScreenRoles: vi.fn().mockResolvedValue([]),
  listOrgGroups: vi.fn().mockResolvedValue([]),
  listOrgProfiles: vi.fn().mockResolvedValue([]),
  listScreenMapRegionMetrics: vi.fn().mockResolvedValue([]),
  saveScreenCanvas: vi.fn(),
  publishScreenCanvas: vi.fn(),
  rollbackScreenCanvas: vi.fn(),
  discardScreenCanvas: vi.fn(),
  listScreenPublishLogs: vi.fn()
}));

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue() }
}));

import {
  listScreens, getScreenCanvas, saveScreenMetadata,
  listScreenAccessRoles, saveScreenAccessRoles, discardScreenCanvas,
  saveScreenCanvas, publishScreenCanvas, rollbackScreenCanvas, listScreenPublishLogs
} from '@/api/screen';
import { ElMessageBox } from 'element-plus';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
import DesignerV2 from '../DesignerV2.vue';

let capturedPreviewContext = null;
const CanvasCoreContextStub = {
  name: 'CanvasCoreContextStub',
  setup() {
    capturedPreviewContext = inject('previewContext');
    return {};
  },
  template: '<div class="canvas-core-context-stub" />'
};

// 只在 setup 中读取一次绑定值，模拟真实 ChartWidgetAttr 对旧 props 的初始化行为。
// 如果同类型动态属性组件被 Vue 复用，这里会继续显示上一个节点的数据源。
const TestChartAttr = {
  name: 'TestChartAttr',
  props: { element: { type: Object, required: true } },
  setup(props) {
    const bind = JSON.parse(props.element.bindJson || '{}');
    return {
      initialDsId: bind.dsId,
      initialMetricCols: Array.isArray(bind.items) ? bind.items.map(item => item.col).join(',') : '',
      initialMetricLabels: Array.isArray(bind.items) ? bind.items.map(item => item.label).join(',') : ''
    };
  },
  template: '<div><div data-testid="test-datasource">{{ initialDsId }}</div><div data-testid="test-metric-columns">{{ initialMetricCols }}</div><div data-testid="test-metric-labels">{{ initialMetricLabels }}</div></div>'
};

// el-button/el-dialog/el-input 用渲染 slot 的自定义 stub:新建大屏流程测试需要按钮文本可寻、
// 弹框内容可见、输入框可 setValue;其余 element-plus 组件保持哑 stub(不关心内部渲染)。
const ElButtonStub = {
  name: 'ElButton',
  props: ['disabled', 'loading'],
  emits: ['click'],
  template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
};
const ElDialogStub = {
  name: 'ElDialog', props: ['modelValue', 'title'], emits: ['update:modelValue', 'opened'],
  template: '<div v-if="modelValue" class="dlg-stub" role="dialog" :aria-label="title"><slot /><slot name="footer" /></div>'
};
const ElInputStub = {
  name: 'ElInput', props: ['modelValue'], emits: ['update:modelValue'],
  template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
};
const ElSelectStub = {
  name: 'ElSelect', props: ['modelValue', 'multiple'], emits: ['update:modelValue', 'change'],
  methods: {
    update(event) {
      const value = this.multiple
        ? Array.from(event.target.selectedOptions).map(option => option.value)
        : event.target.value;
      this.$emit('update:modelValue', value);
      this.$emit('change', value);
    }
  },
  template: '<select :multiple="multiple" :value="modelValue" @change="update"><slot /></select>'
};
const ElOptionStub = {
  name: 'ElOption', props: ['value', 'label'],
  template: '<option :value="value">{{ label }}</option>'
};
const stubs = {
  'el-select': ElSelectStub, 'el-option': ElOptionStub,
  'el-button-group': { template: '<div><slot /></div>' },
  'el-button': ElButtonStub,
  'el-dialog': ElDialogStub,
  'el-input': ElInputStub,
  'el-form': { template: '<form @submit.prevent><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-slider': true, 'el-tabs': true, 'el-tab-pane': true,
  CanvasCore: true, ComponentPanel: true, LayerPanel: true, CanvasAttr: true
};

/** 最小合法编辑器快照(loadFromEditor 消费的字段全带,JSON 字段留 null 走缺省分支) */
function editorResp(id, code) {
  return { screenId: id, screenCode: code, screenName: code, viewLevel: 'BRANCH',
    canvasStyleJson: null, canvasDraftJson: null, canvasVersion: 0, publishStatus: 0, blocks: [] };
}

function findButton(wrapper, text) {
  return wrapper.findAll('button').find(b => b.text() === text);
}

function markCanvasDirty(background) {
  useScreenDesignerStore().canvasStyle.background = background;
}

describe('DesignerV2.vue 挂载冒烟测试', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('mount 不抛错', () => {
    expect(() => mount(DesignerV2, { global: { stubs } })).not.toThrow();
  });

  it('挂载后根节点带 scr-surface-host(画布根主题变量挂载点,附加验收 #1)', () => {
    const wrapper = mount(DesignerV2, { global: { stubs } });
    expect(wrapper.classes()).toContain('scr-surface-host');
  });

  it('加载当前大屏后向画布提供响应式草稿预览上下文', async () => {
    capturedPreviewContext = null;
    listScreens.mockResolvedValueOnce([{
      id: 1, screenCode: 'SCR_PROVINCE', screenName: '省分行经营总览', viewLevel: 'PROVINCE'
    }]);
    getScreenCanvas.mockResolvedValueOnce(editorResp(1, 'SCR_PROVINCE'));

    mount(DesignerV2, { global: { stubs: { ...stubs, CanvasCore: CanvasCoreContextStub } } });
    await flushPromises();

    expect(isReactive(capturedPreviewContext)).toBe(true);
    expect(capturedPreviewContext).toEqual({
      schemaVersion: 1, screenCode: 'SCR_PROVINCE', orgCode: '', empId: ''
    });
  });

  it('右侧属性检查器使用与左栏一致的深色字号和控件主题', () => {
    const wrapper = mount(DesignerV2, { global: { stubs } });
    expect(wrapper.find('.dsn2-right').classes()).toContain('dsn2-inspector');

    const source = readFileSync(resolve(process.cwd(), 'src/views/screen/designer/DesignerV2.vue'), 'utf8');
    expect(source).toContain('--dsn-inspector-font-size: 12px');
    expect(source).toContain(':deep(.el-collapse-item__header)');
    expect(source).toContain(':deep(.el-form-item__label)');
    expect(source).toContain(':deep(.el-input__wrapper)');
  });

  it('卸载不抛错(keydown 监听器能正常移除)', () => {
    const wrapper = mount(DesignerV2, { global: { stubs } });
    expect(() => wrapper.unmount()).not.toThrow();
  });

  it('保留全部现有设计器工具，并新增键盘可达的返回入口', async () => {
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();

    for (const label of ['新建', '编辑范围', '管理查看角色', '撤销', '重做', '适应窗口', '预览草稿', '放弃草稿', '回滚', '保存', '发布', '返回']) {
      expect(findButton(wrapper, label), label).toBeTruthy();
    }
    expect(findButton(wrapper, '返回').attributes('aria-label')).toBe('返回工作区');
  });

  it('现代工作台壳层提供产品标识、当前大屏上下文和分组后的操作区', async () => {
    listScreens.mockResolvedValueOnce([{ id: 9, screenName: '省分行经营总览', viewLevel: 'PROVINCE' }]);
    getScreenCanvas.mockResolvedValueOnce(editorResp(9, 'SCR_PROVINCE'));
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();

    expect(wrapper.find('[data-testid="dsn2-product"]').text()).toContain('大屏设计器');
    expect(wrapper.find('[data-testid="dsn2-screen-context"]').text()).toContain('省分行经营总览');
    expect(wrapper.find('[data-testid="dsn2-screen-actions"]').text()).toContain('编辑范围');
    expect(wrapper.find('[data-testid="dsn2-history-actions"]').text()).toContain('撤销');
    expect(wrapper.find('[data-testid="dsn2-draft-actions"]').text()).toContain('预览草稿');
    expect(wrapper.find('[data-testid="dsn2-publish-actions"]').text()).toContain('发布');
  });

  it('三栏工作区有语义标题、保存状态 badge 和右栏三态说明', async () => {
    listScreens.mockResolvedValueOnce([{ id: 9, screenName: '省分行经营总览', viewLevel: 'PROVINCE' }]);
    getScreenCanvas.mockResolvedValueOnce(editorResp(9, 'SCR_PROVINCE'));
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();

    expect(wrapper.find('[data-testid="dsn2-left-heading"]').text()).toContain('组件与图层');
    expect(wrapper.find('[data-testid="dsn2-left-subtitle"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="dsn2-right-heading"]').text()).toContain('画布设置');
    expect(wrapper.find('[data-testid="dsn2-right-subtitle"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="dsn2-save-state"]').attributes('role')).toBe('status');
    expect(wrapper.find('[data-testid="dsn2-save-state"]').classes()).toContain('is-saved');
  });

  it('现代视觉令牌和键盘/动效降级约束仅作用于 dsn2 域', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/views/screen/designer/DesignerV2.vue'), 'utf8');
    expect(source).toContain('--dsn2-bg');
    expect(source).toContain('--dsn2-accent');
    expect(source).toContain(':focus-visible');
    expect(source).toContain('prefers-reduced-motion');
    expect(source).toContain('.dsn2');
  });

  it('切换同类型图表后属性面板重建并回显当前数据源', async () => {
    findAttrMock.mockReturnValue(TestChartAttr);
    const wrapper = mount(DesignerV2, { global: { stubs } });
    const designerStore = useScreenDesignerStore();
    designerStore.componentData = [
      {
        id: 'chart-a', component: 'ChartWidget',
        bindJson: JSON.stringify({ dsId: 101, items: [{ col: 'metric-a', label: '指标 A' }] })
      },
      {
        id: 'chart-b', component: 'ChartWidget',
        bindJson: JSON.stringify({ dsId: 202, items: [{ col: 'metric-b', label: '指标 B' }] })
      }
    ];

    designerStore.selectComponent('chart-a');
    await nextTick();
    expect(wrapper.find('[data-testid="test-datasource"]').text()).toBe('101');
    expect(wrapper.find('[data-testid="test-metric-columns"]').text()).toBe('metric-a');
    expect(wrapper.find('[data-testid="test-metric-labels"]').text()).toBe('指标 A');

    designerStore.selectComponent('chart-b');
    await nextTick();
    expect(wrapper.find('[data-testid="test-datasource"]').text()).toBe('202');
    expect(wrapper.find('[data-testid="test-metric-columns"]').text()).toBe('metric-b');
    expect(wrapper.find('[data-testid="test-metric-labels"]').text()).toBe('指标 B');
  });

  it('检查器单选图表显示注册表类型并随图表切换更新，未知类型安全兜底', async () => {
    const wrapper = mount(DesignerV2, { global: { stubs } });
    const designerStore = useScreenDesignerStore();
    designerStore.componentData = [
      { id: 'bar-chart', component: 'ChartWidget', innerType: 'BAR_COMPARE' },
      { id: 'line-chart', component: 'ChartWidget', innerType: 'LINE_TREND' },
      { id: 'unknown-chart', component: 'ChartWidget', innerType: 'UNREGISTERED_CHART' },
      { id: 'text-label', component: 'TextLabel' }
    ];

    designerStore.selectComponent('bar-chart');
    await nextTick();
    expect(wrapper.find('[data-testid="dsn2-right-subtitle"]').text()).toBe('柱状对比（BAR_COMPARE）');

    designerStore.selectComponent('line-chart');
    await nextTick();
    expect(wrapper.find('[data-testid="dsn2-right-subtitle"]').text()).toBe('趋势折线（LINE_TREND）');

    designerStore.selectComponent('unknown-chart');
    await nextTick();
    expect(wrapper.find('[data-testid="dsn2-right-subtitle"]').text()).toBe('图表（UNREGISTERED_CHART）');

    designerStore.selectComponent('text-label');
    await nextTick();
    expect(wrapper.find('[data-testid="dsn2-right-subtitle"]').text()).toBe('TextLabel');
  });
});

describe('DesignerV2.vue 未保存保护与返回', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    listScreens.mockResolvedValue([{ id: 1, screenName: 'A屏', viewLevel: 'BRANCH' }]);
    getScreenCanvas.mockResolvedValue(editorResp(1, 'SCR_A'));
    routerReplace.mockResolvedValue();
  });

  it('无未保存修改时直接尝试关闭，受限则回到 workspace', async () => {
    const closeSpy = vi.spyOn(window, 'close').mockImplementation(() => {});
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();

    await findButton(wrapper, '返回').trigger('click');
    await flushPromises();

    expect(closeSpy).toHaveBeenCalledTimes(1);
    expect(saveScreenCanvas).not.toHaveBeenCalled();
    expect(wrapper.find('[aria-label="未保存修改"]').exists()).toBe(false);
    expect(routerReplace).toHaveBeenCalledWith('/workspace');
    closeSpy.mockRestore();
  });

  it('初始加载不脏，用户改变布局后变脏，保存成功重置基线', async () => {
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();
    expect(wrapper.vm.isDirty).toBe(false);

    markCanvasDirty('#111111');
    await flushPromises();
    expect(wrapper.vm.isDirty).toBe(true);

    saveScreenCanvas.mockResolvedValue({ canvasVersion: 1 });
    await wrapper.vm.onSave();
    await flushPromises();
    expect(wrapper.vm.isDirty).toBe(false);
  });

  it('保存失败保持脏状态，不关闭也不返回工作区', async () => {
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();
    markCanvasDirty('#222222');
    await flushPromises();
    await findButton(wrapper, '返回').trigger('click');

    saveScreenCanvas.mockRejectedValueOnce(new Error('network failed'));
    await findButton(wrapper, '保存并关闭').trigger('click');
    await flushPromises();

    expect(wrapper.vm.isDirty).toBe(true);
    expect(routerReplace).not.toHaveBeenCalled();
    expect(wrapper.find('[aria-label="未保存修改"]').exists()).toBe(true);
  });

  it('有未保存修改时展示三个明确分支；取消不写请求并恢复返回按钮焦点', async () => {
    const wrapper = mount(DesignerV2, { attachTo: document.body, global: { stubs } });
    await flushPromises();
    markCanvasDirty('#333333');
    await flushPromises();
    await findButton(wrapper, '返回').trigger('click');

    const dialog = wrapper.find('[aria-label="未保存修改"]');
    expect(dialog.exists()).toBe(true);
    for (const label of ['保存并关闭', '放弃并关闭', '取消']) {
      expect(findButton(wrapper, label), label).toBeTruthy();
    }

    await findButton(wrapper, '取消').trigger('click');
    await flushPromises();
    expect(saveScreenCanvas).not.toHaveBeenCalled();
    expect(routerReplace).not.toHaveBeenCalled();
    expect(document.activeElement).toBe(findButton(wrapper, '返回').element);
    wrapper.unmount();
  });

  it('放弃并关闭不保存，窗口无法关闭时降级返回 workspace', async () => {
    const closeSpy = vi.spyOn(window, 'close').mockImplementation(() => {});
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();
    markCanvasDirty('#444444');
    await flushPromises();
    await findButton(wrapper, '返回').trigger('click');
    await findButton(wrapper, '放弃并关闭').trigger('click');
    await flushPromises();

    expect(saveScreenCanvas).not.toHaveBeenCalled();
    expect(closeSpy).toHaveBeenCalledTimes(1);
    expect(routerReplace).toHaveBeenCalledWith('/workspace');
    closeSpy.mockRestore();
  });

  it('保存并关闭成功才尝试关闭，并在关闭受限时降级', async () => {
    const closeSpy = vi.spyOn(window, 'close').mockImplementation(() => {});
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();
    markCanvasDirty('#555555');
    await flushPromises();
    await findButton(wrapper, '返回').trigger('click');

    saveScreenCanvas.mockResolvedValueOnce({ canvasVersion: 1 });
    await findButton(wrapper, '保存并关闭').trigger('click');
    await flushPromises();

    expect(saveScreenCanvas).toHaveBeenCalledTimes(1);
    expect(closeSpy).toHaveBeenCalledTimes(1);
    expect(routerReplace).toHaveBeenCalledWith('/workspace');
    closeSpy.mockRestore();
  });

  it('保存并关闭遇 CAS 立即停止：仅写一次、不进入强制覆盖/重载且保留可恢复状态', async () => {
    const closeSpy = vi.spyOn(window, 'close').mockImplementation(() => {});
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();
    markCanvasDirty('#666666');
    await flushPromises();
    await findButton(wrapper, '返回').trigger('click');

    saveScreenCanvas.mockRejectedValueOnce({ code: 'RPT-43012' });
    await findButton(wrapper, '保存并关闭').trigger('click');
    await flushPromises();

    expect(saveScreenCanvas).toHaveBeenCalledTimes(1);
    expect(getScreenCanvas).toHaveBeenCalledTimes(1); // 仅初始加载，不读最新版本进入强制覆盖
    expect(ElMessageBox.confirm).not.toHaveBeenCalled();
    expect(wrapper.vm.isDirty).toBe(true);
    expect(wrapper.vm.exitDialog).toMatchObject({ show: true, saving: false });
    expect(wrapper.find('[aria-label="未保存修改"]').exists()).toBe(true);
    expect(closeSpy).not.toHaveBeenCalled();
    expect(routerReplace).not.toHaveBeenCalled();

    // 冲突后可取消回到画布，也可再次打开退出选择；不会偷偷重试写入。
    await findButton(wrapper, '取消').trigger('click');
    await flushPromises();
    expect(wrapper.find('[aria-label="未保存修改"]').exists()).toBe(false);
    await findButton(wrapper, '返回').trigger('click');
    expect(wrapper.find('[aria-label="未保存修改"]').exists()).toBe(true);
    expect(saveScreenCanvas).toHaveBeenCalledTimes(1);
    closeSpy.mockRestore();
  });

  it('工具栏普通保存遇 CAS 仍保留既有强制覆盖流程', async () => {
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();
    markCanvasDirty('#686868');
    await flushPromises();

    saveScreenCanvas
      .mockRejectedValueOnce({ code: 'RPT-43012' })
      .mockResolvedValueOnce({ canvasVersion: 2 });
    getScreenCanvas.mockResolvedValueOnce({ ...editorResp(1, 'SCR_A'), canvasVersion: 1 });
    await wrapper.vm.onSave();
    await flushPromises();

    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1);
    expect(saveScreenCanvas).toHaveBeenCalledTimes(2);
    expect(saveScreenCanvas.mock.calls[1][0].expectedVersion).toBe(1);
    expect(wrapper.vm.isDirty).toBe(false);
  });

  it('beforeunload 仅在脏状态阻止离开，卸载时清理监听', async () => {
    const addSpy = vi.spyOn(window, 'addEventListener');
    const removeSpy = vi.spyOn(window, 'removeEventListener');
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();
    const handler = addSpy.mock.calls.find(([type]) => type === 'beforeunload')?.[1];
    expect(handler).toBeTypeOf('function');

    const cleanEvent = { preventDefault: vi.fn(), returnValue: undefined };
    handler(cleanEvent);
    expect(cleanEvent.preventDefault).not.toHaveBeenCalled();

    markCanvasDirty('#777777');
    await flushPromises();
    const dirtyEvent = { preventDefault: vi.fn(), returnValue: undefined };
    handler(dirtyEvent);
    expect(dirtyEvent.preventDefault).toHaveBeenCalledTimes(1);
    expect(dirtyEvent.returnValue).toBe('');

    wrapper.unmount();
    expect(removeSpy).toHaveBeenCalledWith('beforeunload', handler);
    addSpy.mockRestore();
    removeSpy.mockRestore();
  });
});

describe('DesignerV2.vue 新建大屏', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('工具条有「新建」按钮，必须显式选择业务条线和机构范围后才提交元数据', async () => {
    listScreens.mockResolvedValueOnce([]); // 初始空列表
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();

    const createBtn = findButton(wrapper, '新建');
    expect(createBtn).toBeTruthy();
    await createBtn.trigger('click');

    // 弹框出现，除查看视角外的范围字段没有静默默认值，必须由操作者选择。
    const dlg = wrapper.find('.dlg-stub');
    expect(dlg.exists()).toBe(true);
    await dlg.find('input').setValue('测试新屏');
    const selects = dlg.findAll('select');
    await selects[1].setValue('COMMON');
    await selects[2].setValue('LEGACY_CONTEXT');

    saveScreenMetadata.mockResolvedValueOnce(99); // 后端返回新屏 id
    listScreens.mockResolvedValueOnce([{ id: 99, screenName: '测试新屏', viewLevel: 'BRANCH' }]);
    getScreenCanvas.mockResolvedValueOnce(editorResp(99, 'SCR_NEW'));
    await findButton(wrapper, '确定').trigger('click');
    await flushPromises();

    expect(saveScreenMetadata).toHaveBeenCalledWith({
      screenName: '测试新屏', viewLevel: 'BRANCH', bizLine: 'COMMON',
      orgScopeMode: 'LEGACY_CONTEXT', orgGroupCode: null
    });
    expect(getScreenCanvas).toHaveBeenCalledWith(99); // 新建后自动选中并加载新屏
    expect(wrapper.find('.dlg-stub').exists()).toBe(false); // 成功后弹框关闭
  });

  it('屏名为空时不提交(saveScreenMetadata 不被调用,弹框保持打开)', async () => {
    listScreens.mockResolvedValueOnce([]);
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();

    await findButton(wrapper, '新建').trigger('click');
    await findButton(wrapper, '确定').trigger('click');
    await flushPromises();

    expect(saveScreenMetadata).not.toHaveBeenCalled();
    expect(wrapper.find('.dlg-stub').exists()).toBe(true);
  });

  it('编辑范围只提交元数据/范围的 CAS+原因，不夹带区块或角色白名单', async () => {
    const existing = {
      id: 7, screenCode: 'SCR_EXISTING', screenName: '现有屏', viewLevel: 'PROVINCE',
      bizLine: 'RETAIL', orgScopeMode: 'NAMED_GROUP', orgGroupCode: 'G1',
      allowedRoleCodes: ['R1'], themeJson: '{"theme":"dark"}', status: 'ACTIVE'
    };
    const blocks = [{ id: 11, region: 'LEFT', rowNo: 1, colNo: 1, widthPct: 100, heightPct: 100,
      componentType: 'METRIC_CARD', bindJson: '{"dsId":2}', styleJson: '{}', drillJson: '{}' }];
    listScreens.mockResolvedValueOnce([existing]);
    getScreenCanvas.mockResolvedValueOnce({ ...editorResp(7, 'SCR_EXISTING'), blocks });
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();
    await findButton(wrapper, '编辑范围').trigger('click');
    const dlg = wrapper.find('.dlg-stub');
    await dlg.findAll('input')[1].setValue('调整命名机构组范围');
    saveScreenMetadata.mockResolvedValueOnce(7);
    listScreens.mockResolvedValueOnce([existing]);
    getScreenCanvas.mockResolvedValueOnce({ ...editorResp(7, 'SCR_EXISTING'), blocks });
    await findButton(wrapper, '确定').trigger('click');
    await flushPromises();
    expect(saveScreenMetadata).toHaveBeenCalledWith({
      id: 7, screenCode: 'SCR_EXISTING', screenName: '现有屏', viewLevel: 'PROVINCE',
      bizLine: 'RETAIL', orgScopeMode: 'NAMED_GROUP', orgGroupCode: 'G1',
      themeJson: '{"theme":"dark"}', status: 'ACTIVE', expectedVersion: 0, reason: '调整命名机构组范围'
    });
  });

  it('放弃草稿必须显示版本与原因，提交独立 discard CAS 请求', async () => {
    const existing = { id: 7, screenCode: 'SCR_EXISTING', screenName: '现有屏', viewLevel: 'BRANCH' };
    listScreens.mockResolvedValueOnce([existing]);
    getScreenCanvas
      .mockResolvedValueOnce({ ...editorResp(7, 'SCR_EXISTING'), canvasVersion: 4 })
      .mockResolvedValueOnce({ ...editorResp(7, 'SCR_EXISTING'), canvasVersion: 5 });
    discardScreenCanvas.mockResolvedValueOnce();
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();
    await findButton(wrapper, '放弃草稿').trigger('click');
    const dlg = wrapper.find('.dlg-stub');
    expect(dlg.text()).toContain('版本 4');
    await dlg.find('input').setValue('撤回误改草稿');
    await findButton(wrapper, '确认放弃草稿').trigger('click');
    await flushPromises();
    expect(discardScreenCanvas).toHaveBeenCalledWith(7, { expectedVersion: 4, reason: '撤回误改草稿' });
  });

  it('放弃草稿遇到 CAS 冲突时不重试旧版本，而是重载最新画布', async () => {
    const existing = { id: 7, screenCode: 'SCR_EXISTING', screenName: '现有屏', viewLevel: 'BRANCH' };
    listScreens.mockResolvedValueOnce([existing]);
    getScreenCanvas
      .mockResolvedValueOnce({ ...editorResp(7, 'SCR_EXISTING'), canvasVersion: 4 })
      .mockResolvedValueOnce({ ...editorResp(7, 'SCR_EXISTING'), canvasVersion: 5 });
    discardScreenCanvas.mockRejectedValueOnce({ code: 'RPT-43012' });
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();
    await findButton(wrapper, '放弃草稿').trigger('click');
    const dlg = wrapper.find('.dlg-stub');
    await dlg.find('input').setValue('并发保存后放弃');
    await findButton(wrapper, '确认放弃草稿').trigger('click');
    await flushPromises();

    expect(discardScreenCanvas).toHaveBeenCalledTimes(1);
    expect(getScreenCanvas).toHaveBeenCalledTimes(2);
    expect(useScreenDesignerStore().canvasVersion).toBe(5);
  });

  it('角色白名单只通过高危独立端点提交真实 added/removed、reason 和 canvas 版本', async () => {
    const existing = {
      id: 7, screenCode: 'SCR_EXISTING', screenName: '现有屏', viewLevel: 'PROVINCE',
      bizLine: 'RETAIL', orgScopeMode: 'NAMED_GROUP', orgGroupCode: 'G1'
    };
    listScreens.mockResolvedValueOnce([existing]);
    getScreenCanvas
      .mockResolvedValueOnce({ ...editorResp(7, 'SCR_EXISTING'), canvasVersion: 4 })
      .mockResolvedValueOnce({ ...editorResp(7, 'SCR_EXISTING'), canvasVersion: 4 })
      .mockResolvedValueOnce({ ...editorResp(7, 'SCR_EXISTING'), canvasVersion: 5 });
    listScreenAccessRoles.mockResolvedValueOnce(['R_OLD']);
    // 前端候选只是操作体验，保存仍通过服务端 PERMISSION_CHANGE 二次校验。
    const screenApi = await import('@/api/screen');
    screenApi.listScreenRoles.mockResolvedValue([{ roleCode: 'R_NEW', roleChName: '新角色' }]);
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();
    await findButton(wrapper, '管理查看角色').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('版本 4');
    const roleDialog = wrapper.find('.dlg-stub');
    // 直接发出组件 v-model 事件，避免 happy-dom 对 multiple select 的 selectedOptions 实现差异。
    roleDialog.findComponent(ElSelectStub).vm.$emit('update:modelValue', ['R_NEW']);
    await flushPromises();
    expect(wrapper.text()).toContain('新增 1：R_NEW');
    expect(wrapper.text()).toContain('移除 1：R_OLD');
    await roleDialog.find('input').setValue('职责调整');
    saveScreenAccessRoles.mockResolvedValueOnce();
    await findButton(wrapper, '保存角色变更').trigger('click');
    await flushPromises();
    expect(saveScreenAccessRoles).toHaveBeenCalledWith(7, {
      roleCodes: ['R_NEW'], reason: '职责调整', expectedVersion: 4
    });
  });

  it('发布必须先打开独立原因对话框，确认后只提交 screenId/expectedVersion/reason', async () => {
    const existing = { id: 7, screenCode: 'SCR_EXISTING', screenName: '现有屏', viewLevel: 'BRANCH' };
    listScreens.mockResolvedValueOnce([existing]);
    getScreenCanvas.mockResolvedValueOnce({ ...editorResp(7, 'SCR_EXISTING'), canvasVersion: 4 });
    saveScreenCanvas.mockResolvedValueOnce({ canvasVersion: 4 });
    publishScreenCanvas.mockResolvedValueOnce();
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();

    await wrapper.vm.onPublish();
    expect(wrapper.vm.publishDialog).toMatchObject({ show: true, expectedVersion: 4, reason: '' });
    expect(publishScreenCanvas).not.toHaveBeenCalled();

    wrapper.vm.publishDialog.reason = '月末版本发布';
    await wrapper.vm.confirmPublish();
    expect(publishScreenCanvas).toHaveBeenCalledWith({ screenId: 7, expectedVersion: 4, reason: '月末版本发布' });
  });

  it('回滚必须先打开独立原因对话框，CAS 冲突时重载而不以旧版本重试', async () => {
    const existing = { id: 7, screenCode: 'SCR_EXISTING', screenName: '现有屏', viewLevel: 'BRANCH' };
    listScreens.mockResolvedValueOnce([existing]);
    getScreenCanvas
      .mockResolvedValueOnce({ ...editorResp(7, 'SCR_EXISTING'), canvasVersion: 4 })
      .mockResolvedValueOnce({ ...editorResp(7, 'SCR_EXISTING'), canvasVersion: 5 });
    listScreenPublishLogs.mockResolvedValueOnce([
      { id: 99, publishedAt: '2026-08-12 09:00:00', publishedBy: 'alice' },
      { id: 98, publishedAt: '2026-08-11 09:00:00', publishedBy: 'bob' }
    ]);
    rollbackScreenCanvas.mockRejectedValueOnce({ code: 'RPT-43012' });
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();

    await wrapper.vm.onRollback();
    expect(wrapper.vm.rollbackDialog).toMatchObject({ show: true, publishLogId: null, expectedVersion: 4, reason: '' });
    expect(wrapper.vm.rollbackDialog.archives).toHaveLength(2);
    expect(wrapper.text()).toContain('2026-08-12 09:00:00');
    expect(wrapper.text()).toContain('2026-08-11 09:00:00');
    wrapper.vm.rollbackDialog.publishLogId = 99;
    expect(rollbackScreenCanvas).not.toHaveBeenCalled();

    wrapper.vm.rollbackDialog.reason = '回退异常发布';
    await wrapper.vm.confirmRollback();
    expect(rollbackScreenCanvas).toHaveBeenCalledWith({ screenId: 7, publishLogId: 99, expectedVersion: 4, reason: '回退异常发布' });
    expect(getScreenCanvas).toHaveBeenCalledTimes(2);
    expect(useScreenDesignerStore().canvasVersion).toBe(5);
  });

  it('没有显式选择归档时不能提交回滚', async () => {
    const existing = { id: 7, screenCode: 'SCR_EXISTING', screenName: '现有屏', viewLevel: 'BRANCH' };
    listScreens.mockResolvedValueOnce([existing]);
    getScreenCanvas.mockResolvedValueOnce({ ...editorResp(7, 'SCR_EXISTING'), canvasVersion: 4 });
    listScreenPublishLogs.mockResolvedValueOnce([{ id: 99, publishedAt: '2026-08-12 09:00:00' }]);
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();

    await wrapper.vm.onRollback();
    wrapper.vm.rollbackDialog.reason = '误操作回退';
    await wrapper.vm.confirmRollback();
    expect(rollbackScreenCanvas).not.toHaveBeenCalled();
    expect(wrapper.vm.rollbackDialog.show).toBe(true);
  });

  it('归档候选最多展示最近十条且保留后端顺序', async () => {
    const existing = { id: 7, screenCode: 'SCR_EXISTING', screenName: '现有屏', viewLevel: 'BRANCH' };
    listScreens.mockResolvedValueOnce([existing]);
    getScreenCanvas.mockResolvedValueOnce({ ...editorResp(7, 'SCR_EXISTING'), canvasVersion: 4 });
    listScreenPublishLogs.mockResolvedValueOnce(Array.from({ length: 12 }, (_, index) => ({
      id: 120 - index, publishedAt: `2026-08-${String(12 - index).padStart(2, '0')} 09:00:00`
    })));
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();
    await wrapper.vm.onRollback();
    expect(wrapper.vm.rollbackDialog.archives).toHaveLength(10);
    expect(wrapper.vm.rollbackDialog.archives.map(item => item.id)).toEqual(
      Array.from({ length: 10 }, (_, index) => 120 - index)
    );
  });
});

describe('DesignerV2.vue 首屏自动适应窗口', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('加载完首屏后自动执行适应窗口(测试环境视口尺寸为 0,fitScale 兜底 1,可与默认 0.5 区分)', async () => {
    listScreens.mockResolvedValueOnce([{ id: 1, screenName: 'A屏', viewLevel: 'PERSON' }]);
    getScreenCanvas.mockResolvedValueOnce(editorResp(1, 'SCR_A'));
    // fitWindow 用 document.querySelector 找画布容器,必须真实挂到 document 上
    const wrapper = mount(DesignerV2, { attachTo: document.body, global: { stubs } });
    await flushPromises();

    const store = useScreenDesignerStore();
    expect(store.scale).toBe(1); // 未自动适应时会停留在默认 0.5
    wrapper.unmount();
  });
});
