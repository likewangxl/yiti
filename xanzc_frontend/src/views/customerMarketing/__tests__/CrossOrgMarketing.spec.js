// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

const {
  approveCrossOrgMarketingMock,
  createCrossOrgMarketingMock,
  listCrossOrgMarketingMock,
  rejectCrossOrgMarketingMock,
  validateCrossOrgMarketingMock,
  messageMock,
  messageBoxMock,
} = vi.hoisted(() => ({
  approveCrossOrgMarketingMock: vi.fn(),
  createCrossOrgMarketingMock: vi.fn(),
  listCrossOrgMarketingMock: vi.fn(),
  rejectCrossOrgMarketingMock: vi.fn(),
  validateCrossOrgMarketingMock: vi.fn(),
  messageMock: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  messageBoxMock: { confirm: vi.fn(), prompt: vi.fn() },
}));

vi.mock('@/api/customerMarketing', () => ({
  approveCrossOrgMarketing: approveCrossOrgMarketingMock,
  createCrossOrgMarketing: createCrossOrgMarketingMock,
  listCrossOrgMarketing: listCrossOrgMarketingMock,
  rejectCrossOrgMarketing: rejectCrossOrgMarketingMock,
  validateCrossOrgMarketing: validateCrossOrgMarketingMock,
}));
vi.mock('element-plus', () => ({ ElMessage: messageMock, ElMessageBox: messageBoxMock }));

import CrossOrgMarketing from '../CrossOrgMarketing.vue';

const stubs = {
  PageTitle: { template: '<h1>跨机构营销</h1>' },
  'el-button': { template: '<button><slot /></button>' },
  'el-card': { template: '<section><slot /></section>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-radio-group': { template: '<div><slot /></div>' },
  'el-radio-button': { template: '<button><slot /></button>' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<span />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-drawer': { template: '<div><slot /></div>' },
  'el-descriptions': { template: '<div><slot /></div>' },
  'el-descriptions-item': { template: '<div><slot /></div>' },
  'el-icon': { template: '<span><slot /></span>' },
};

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

function mountPage() {
  return mount(CrossOrgMarketing, {
    global: { stubs, directives: { loading: () => {} } },
  });
}

let wrapper;

beforeEach(() => {
  approveCrossOrgMarketingMock.mockReset().mockResolvedValue({});
  createCrossOrgMarketingMock.mockReset();
  listCrossOrgMarketingMock.mockReset().mockResolvedValue([]);
  rejectCrossOrgMarketingMock.mockReset();
  validateCrossOrgMarketingMock.mockReset();
  Object.values(messageMock).forEach((mock) => mock.mockReset());
  Object.values(messageBoxMock).forEach((mock) => mock.mockReset());
});

afterEach(() => wrapper?.unmount());

describe('跨机构营销审批意见', () => {
  it('通过意见为空时不请求审批接口', async () => {
    messageBoxMock.prompt.mockResolvedValue({ value: '  ' });
    wrapper = mountPage();
    await settle();

    await wrapper.vm.approve({ id: 'A1' });

    expect(messageBoxMock.prompt).toHaveBeenCalledWith(
      expect.any(String),
      expect.any(String),
      expect.objectContaining({ inputType: 'textarea', inputAttrs: { maxlength: 500 } }),
    );
    expect(approveCrossOrgMarketingMock).not.toHaveBeenCalled();
    expect(messageMock.warning).toHaveBeenCalledWith('审批意见不能为空');
  });

  it('有效通过意见使用 prompt 并传给审批接口', async () => {
    messageBoxMock.prompt.mockResolvedValue({ value: '同意，继续推进联合营销' });
    wrapper = mountPage();
    await settle();

    await wrapper.vm.approve({ id: 'A1' });

    expect(messageBoxMock.confirm).not.toHaveBeenCalled();
    expect(approveCrossOrgMarketingMock).toHaveBeenCalledWith('A1', '同意，继续推进联合营销');
  });

  it('退回仍要求意见且有效意见传给退回接口', async () => {
    messageBoxMock.prompt.mockResolvedValue({ value: '请补充机构协同方案' });
    wrapper = mountPage();
    await settle();

    await wrapper.vm.reject({ id: 'A1' });

    expect(rejectCrossOrgMarketingMock).toHaveBeenCalledWith('A1', '请补充机构协同方案');
    expect(messageBoxMock.prompt).toHaveBeenCalledWith(
      '请输入退回原因',
      '退回申请',
      expect.objectContaining({ inputValidator: expect.any(Function) }),
    );
  });
});
