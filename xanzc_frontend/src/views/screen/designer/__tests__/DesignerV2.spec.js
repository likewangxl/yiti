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

vi.mock('@/api/screen', () => ({
  listScreens: vi.fn().mockResolvedValue([]),
  getScreenCanvas: vi.fn(),
  saveScreen: vi.fn(),
  saveScreenCanvas: vi.fn(),
  publishScreenCanvas: vi.fn(),
  rollbackScreenCanvas: vi.fn(),
  discardScreenCanvas: vi.fn(),
  listScreenPublishLogs: vi.fn()
}));

import { listScreens, getScreenCanvas, saveScreen } from '@/api/screen';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
import DesignerV2 from '../DesignerV2.vue';

// el-button/el-dialog/el-input 用渲染 slot 的自定义 stub:新建大屏流程测试需要按钮文本可寻、
// 弹框内容可见、输入框可 setValue;其余 element-plus 组件保持哑 stub(不关心内部渲染)。
const ElButtonStub = { name: 'ElButton', emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' };
const ElDialogStub = {
  name: 'ElDialog', props: ['modelValue'], emits: ['update:modelValue'],
  template: '<div v-if="modelValue" class="dlg-stub"><slot /><slot name="footer" /></div>'
};
const ElInputStub = {
  name: 'ElInput', props: ['modelValue'], emits: ['update:modelValue'],
  template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
};
const stubs = {
  'el-select': true, 'el-option': true,
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

describe('DesignerV2.vue 挂载冒烟测试', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('mount 不抛错', () => {
    expect(() => mount(DesignerV2, { global: { stubs } })).not.toThrow();
  });

  it('挂载后根节点带 scr-surface-host(画布根主题变量挂载点,附加验收 #1)', () => {
    const wrapper = mount(DesignerV2, { global: { stubs } });
    expect(wrapper.classes()).toContain('scr-surface-host');
  });

  it('卸载不抛错(keydown 监听器能正常移除)', () => {
    const wrapper = mount(DesignerV2, { global: { stubs } });
    expect(() => wrapper.unmount()).not.toThrow();
  });
});

describe('DesignerV2.vue 新建大屏', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('工具条有「新建」按钮,提交后调 saveScreen 并刷新列表加载新屏画布', async () => {
    listScreens.mockResolvedValueOnce([]); // 初始空列表
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();

    const createBtn = findButton(wrapper, '新建');
    expect(createBtn).toBeTruthy();
    await createBtn.trigger('click');

    // 弹框出现,填屏名(viewLevel 走表单默认 BRANCH)后确定
    const dlg = wrapper.find('.dlg-stub');
    expect(dlg.exists()).toBe(true);
    await dlg.find('input').setValue('测试新屏');

    saveScreen.mockResolvedValueOnce(99); // 后端返回新屏 id
    listScreens.mockResolvedValueOnce([{ id: 99, screenName: '测试新屏', viewLevel: 'BRANCH' }]);
    getScreenCanvas.mockResolvedValueOnce(editorResp(99, 'SCR_NEW'));
    await findButton(wrapper, '确定').trigger('click');
    await flushPromises();

    expect(saveScreen).toHaveBeenCalledWith({ screenName: '测试新屏', viewLevel: 'BRANCH' });
    expect(getScreenCanvas).toHaveBeenCalledWith(99); // 新建后自动选中并加载新屏
    expect(wrapper.find('.dlg-stub').exists()).toBe(false); // 成功后弹框关闭
  });

  it('屏名为空时不提交(saveScreen 不被调用,弹框保持打开)', async () => {
    listScreens.mockResolvedValueOnce([]);
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();

    await findButton(wrapper, '新建').trigger('click');
    await findButton(wrapper, '确定').trigger('click');
    await flushPromises();

    expect(saveScreen).not.toHaveBeenCalled();
    expect(wrapper.find('.dlg-stub').exists()).toBe(true);
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
