// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  listProcessDefinitions: vi.fn(),
  listNodeCandidates: vi.fn(), createNodeCandidate: vi.fn(), updateNodeCandidate: vi.fn(),
  listNodeForms: vi.fn(), createNodeForm: vi.fn(), updateNodeForm: vi.fn(),
  listTimeoutRules: vi.fn(), createTimeoutRule: vi.fn(), updateTimeoutRule: vi.fn()
}));

vi.mock('@/api/system', () => api);
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));

import Config from '../Config.vue';

const passthrough = (name) => ({ name, template: '<div v-bind="$attrs"><slot /><slot name="footer" /></div>' });
const stubs = {
  PageTitle: passthrough('PageTitle'),
  'el-form': passthrough('ElForm'),
  'el-form-item': passthrough('ElFormItem'),
  'el-select': passthrough('ElSelect'),
  'el-option': passthrough('ElOption'),
  'el-button': { name: 'ElButton', emits: ['click'], template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>' },
  'el-tabs': passthrough('ElTabs'), 'el-tab-pane': passthrough('ElTabPane'),
  'el-table': passthrough('ElTable'), 'el-table-column': { template: '<div />' },
  'el-tag': passthrough('ElTag'), 'el-dialog': passthrough('ElDialog'),
  'el-input': passthrough('ElInput'), 'el-input-number': passthrough('ElInputNumber')
};

let wrapper;

beforeEach(() => {
  vi.clearAllMocks();
  api.listProcessDefinitions.mockResolvedValue([
    { processDefinitionKey: 'PROC_A', processDefinitionName: '流程 A', version: 2 },
    { processDefinitionKey: 'PROC_B', processDefinitionName: '流程 B', version: 1 }
  ]);
  api.listNodeCandidates.mockResolvedValue([]);
  api.listNodeForms.mockResolvedValue([]);
  api.listTimeoutRules.mockResolvedValue([]);
});

afterEach(() => wrapper?.unmount());

describe('Config.vue 筛选重置', () => {
  it('重置清除当前选择，重拉真实流程定义并按默认定义重新 GET 当前页签', async () => {
    wrapper = mount(Config, { global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } } });
    await flushPromises();
    await flushPromises();
    wrapper.vm.selectedPd = 'PROC_B';
    await wrapper.vm.$nextTick();
    api.listProcessDefinitions.mockClear();
    api.listNodeCandidates.mockClear();

    const reset = wrapper.findAll('button').find((button) => button.text() === '重置');
    expect(reset).toBeTruthy();
    await reset.trigger('click');
    await flushPromises();
    await flushPromises();

    expect(api.listProcessDefinitions).toHaveBeenCalledTimes(1);
    expect(wrapper.vm.selectedPd).toBe('PROC_A');
    expect(api.listNodeCandidates).toHaveBeenCalledTimes(1);
    expect(api.listNodeCandidates).toHaveBeenCalledWith('PROC_A');
  });

  it.each([
    ['candidates', 'listNodeCandidates'],
    ['forms', 'listNodeForms'],
    ['timeout', 'listTimeoutRules']
  ])('重置 %s 页签只重新 GET 当前页签', async (activeTab, expectedMethod) => {
    wrapper = mount(Config, { global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } } });
    await flushPromises();
    await flushPromises();
    wrapper.vm.activeTab = activeTab;
    wrapper.vm.selectedPd = 'PROC_B';
    await wrapper.vm.$nextTick();
    for (const method of ['listNodeCandidates', 'listNodeForms', 'listTimeoutRules']) api[method].mockClear();

    await wrapper.findAll('button').find((button) => button.text() === '重置').trigger('click');
    await flushPromises();
    await flushPromises();

    expect(api[expectedMethod]).toHaveBeenCalledTimes(1);
    expect(api[expectedMethod]).toHaveBeenCalledWith('PROC_A');
    for (const method of ['listNodeCandidates', 'listNodeForms', 'listTimeoutRules']) {
      if (method !== expectedMethod) expect(api[method]).not.toHaveBeenCalled();
    }
  });

  it.each([
    ['无流程定义', []],
    ['流程定义请求失败', new Error('network')]
  ])('%s 时重置不误发任何页签请求', async (_name, result) => {
    wrapper = mount(Config, { global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } } });
    await flushPromises();
    await flushPromises();
    api.listProcessDefinitions.mockReset();
    if (result instanceof Error) api.listProcessDefinitions.mockRejectedValue(result);
    else api.listProcessDefinitions.mockResolvedValue(result);
    for (const method of ['listNodeCandidates', 'listNodeForms', 'listTimeoutRules']) api[method].mockClear();

    await wrapper.findAll('button').find((button) => button.text() === '重置').trigger('click');
    await flushPromises();
    await flushPromises();

    expect(wrapper.vm.selectedPd).toBe('');
    expect(api.listNodeCandidates).not.toHaveBeenCalled();
    expect(api.listNodeForms).not.toHaveBeenCalled();
    expect(api.listTimeoutRules).not.toHaveBeenCalled();
  });

  it('重置加载期间按钮禁用且 handler fail-close，不重复发流程定义请求', async () => {
    wrapper = mount(Config, { global: { stubs, directives: { loading: {}, 'bp-overflow-tooltip': {} } } });
    await flushPromises();
    await flushPromises();
    let resolveDefinitions;
    api.listProcessDefinitions.mockReset();
    api.listProcessDefinitions.mockImplementation(() => new Promise((resolve) => { resolveDefinitions = resolve; }));

    const reset = wrapper.findAll('button').find((button) => button.text() === '重置');
    const first = reset.trigger('click');
    await wrapper.vm.$nextTick();
    expect(reset.attributes('disabled')).toBeDefined();
    const second = wrapper.vm.resetFilters();

    expect(api.listProcessDefinitions).toHaveBeenCalledTimes(1);
    resolveDefinitions([]);
    await Promise.all([first, second]);
    await flushPromises();
    expect(reset.attributes('disabled')).toBeUndefined();
  });
});
