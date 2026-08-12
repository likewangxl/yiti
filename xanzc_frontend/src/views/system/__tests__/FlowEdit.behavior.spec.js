// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const { messageBox } = vi.hoisted(() => ({
  messageBox: { alert: vi.fn() }
}));

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: messageBox
}));
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: 'FLOW_1' } }),
  useRouter: () => ({ push: vi.fn() })
}));
vi.mock('@/api/flowDesign', () => ({
  getFlow: vi.fn(),
  saveFlow: vi.fn(),
  publishFlow: vi.fn(),
  listFlowVariables: vi.fn(),
  listApproverVariables: vi.fn()
}));

import { getFlow, listApproverVariables, listFlowVariables, publishFlow, saveFlow } from '@/api/flowDesign';
import FlowEdit from '../FlowEdit.vue';

const passthrough = (name) => ({ name, template: '<div><slot /><slot name="footer" /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  PageTitle: passthrough('PageTitle'),
  'el-button': {
    name: 'ElButton', props: ['loading', 'disabled'], emits: ['click'],
    template: '<button :disabled="loading || disabled" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-input': empty('ElInput'),
  'el-alert': passthrough('ElAlert'),
  FlowCanvas: empty('FlowCanvas'),
  FlowPalette: empty('FlowPalette'),
  FlowNodePanel: empty('FlowNodePanel'),
  FlowEdgePanel: empty('FlowEdgePanel')
};

const validModel = () => ({
  name: '业绩调整流程',
  bizType: 'ALLOC_ADJUST',
  nodes: [
    { nodeKey: 'start', nodeType: 'START', name: '开始', sortNo: 0, posX: 10.4, posY: 20.6, approvers: [] },
    { nodeKey: 'end', nodeType: 'END', name: '结束', sortNo: 1, posX: 110.2, posY: 220.8, approvers: [] }
  ],
  edges: []
});

function mountPage() {
  return mount(FlowEdit, {
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
  });
}

async function settle() {
  await flushPromises();
  await flushPromises();
}

let wrapper;

beforeEach(() => {
  vi.clearAllMocks();
  getFlow.mockResolvedValue(validModel());
  saveFlow.mockResolvedValue({ ok: true });
  publishFlow.mockResolvedValue({ ok: true });
  listFlowVariables.mockResolvedValue([]);
  listApproverVariables.mockResolvedValue([]);
  messageBox.alert.mockResolvedValue({});
});

afterEach(() => wrapper?.unmount());

describe('流程编辑加载与写入门禁', () => {
  it('getFlow 失败后保存和发布都不发起写请求', async () => {
    getFlow.mockRejectedValueOnce(new Error('流程读取失败'));
    wrapper = mountPage();
    await settle();

    await wrapper.vm.doSave();
    await wrapper.vm.doPublish();

    expect(wrapper.vm.loadError).toContain('流程模型加载失败');
    expect(saveFlow).not.toHaveBeenCalled();
    expect(publishFlow).not.toHaveBeenCalled();
  });

  it('流程模型校验失败时保存立即返回，不提交不完整模型', async () => {
    wrapper = mountPage();
    await settle();
    wrapper.vm.graph.nodes = [{ nodeKey: 'start', nodeType: 'START', name: '开始', approvers: [] }];

    await wrapper.vm.doSave();

    expect(saveFlow).not.toHaveBeenCalled();
  });

  it('重新加载成功后恢复编辑，并保持草稿 payload 语义', async () => {
    getFlow.mockRejectedValueOnce(new Error('首次读取失败')).mockResolvedValueOnce(validModel());
    wrapper = mountPage();
    await settle();

    await wrapper.vm.loadGraph();
    await settle();
    await wrapper.vm.doSave();

    expect(wrapper.vm.loadError).toBe('');
    expect(saveFlow).toHaveBeenCalledWith('FLOW_1', {
      name: '业绩调整流程',
      bizType: 'ALLOC_ADJUST',
      nodes: [
        { nodeKey: 'start', nodeType: 'START', name: '开始', approveMode: null, sortNo: 0, posX: 10, posY: 21, approvers: [] },
        { nodeKey: 'end', nodeType: 'END', name: '结束', approveMode: null, sortNo: 1, posX: 110, posY: 221, approvers: [] }
      ],
      edges: []
    });
  });
});
