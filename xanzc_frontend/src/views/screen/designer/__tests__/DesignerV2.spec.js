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
  saveScreenMetadata: vi.fn(),
  listScreenAccessRoles: vi.fn(),
  saveScreenAccessRoles: vi.fn(),
  listScreenRoles: vi.fn().mockResolvedValue([]),
  listOrgGroups: vi.fn().mockResolvedValue([]),
  listOrgProfiles: vi.fn().mockResolvedValue([]),
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
    listScreenPublishLogs.mockResolvedValueOnce([{ id: 99, publishedAt: '2026-08-12 09:00:00' }]);
    rollbackScreenCanvas.mockRejectedValueOnce({ code: 'RPT-43012' });
    const wrapper = mount(DesignerV2, { global: { stubs } });
    await flushPromises();

    await wrapper.vm.onRollback();
    expect(wrapper.vm.rollbackDialog).toMatchObject({ show: true, publishLogId: 99, expectedVersion: 4, reason: '' });
    expect(rollbackScreenCanvas).not.toHaveBeenCalled();

    wrapper.vm.rollbackDialog.reason = '回退异常发布';
    await wrapper.vm.confirmRollback();
    expect(rollbackScreenCanvas).toHaveBeenCalledWith({ screenId: 7, publishLogId: 99, expectedVersion: 4, reason: '回退异常发布' });
    expect(getScreenCanvas).toHaveBeenCalledTimes(2);
    expect(useScreenDesignerStore().canvasVersion).toBe(5);
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
