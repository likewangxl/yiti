// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  createLoanApplication: vi.fn(),
  updateLoanApplication: vi.fn(),
  uploadLoanAttachment: vi.fn(),
  listLoanCustomerCandidates: vi.fn()
}));
const message = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn() }));

vi.mock('@/api/businessApplication', () => ({
  createLoanApplication: api.createLoanApplication,
  updateLoanApplication: api.updateLoanApplication,
  uploadLoanAttachment: api.uploadLoanAttachment,
  listLoanCustomerCandidates: api.listLoanCustomerCandidates
}));
vi.mock('@/composables/useDict', () => ({
  useDict: (type) => ({
    options: type === 'PROJECT_TYPE'
      ? [{ value: 'NEW_CREDIT', label: '新增授信' }]
      : type === 'BIZ_TYPE'
        ? [{ value: 'WORKING_CAPITAL', label: '流动资金贷款' }]
        : [{ value: 'MORTGAGE', label: '抵押' }],
    labelOf: value => value,
    loading: false,
    reload: vi.fn()
  })
}));
vi.mock('element-plus', () => ({
  ElMessage: message
}));

import LoanForm from '../LoanForm.vue';

const stubs = {
  'el-dialog': { template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>', props: ['modelValue'] },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
  'el-input-number': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option><slot /></option>' },
  'el-upload': { template: '<div><slot /></div>' },
  'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-alert': { template: '<div><slot /></div>' }
};

function mountForm(props = {}) {
  return mount(LoanForm, {
    props: { modelValue: true, ...props },
    global: { stubs }
  });
}

describe('资产立项表单', () => {
  it('触达任务已带入客户时锁定客户，只有任务号时仍可选择', () => {
    const taskOnly = mountForm({ sourceTouchTaskId: 'T-1' });
    const withCustomer = mountForm({
      sourceTouchTaskId: 'T-1',
      loan: { sourceTouchTaskId: 'T-1', custId: 'C-1', custName: '浦爱科技' }
    });

    expect(taskOnly.vm.customerLocked).toBe(false);
    expect(withCustomer.vm.customerLocked).toBe(true);
  });

  it('阻止授信敞口大于授信金额，并且不伪造保存成功', async () => {
    api.createLoanApplication.mockResolvedValue({ id: 'L-1' });
    const wrapper = mountForm();
    wrapper.vm.form.custId = 'C-1';
    wrapper.vm.form.projectType = 'NEW_CREDIT';
    wrapper.vm.form.bizType = 'WORKING_CAPITAL';
    wrapper.vm.form.guaranteeType = 'MORTGAGE';
    wrapper.vm.form.creditAmount = 80;
    wrapper.vm.form.creditExposureAmount = 100;

    await wrapper.vm.saveDraft();

    expect(api.createLoanApplication).not.toHaveBeenCalled();
    expect(message.warning).toHaveBeenCalledWith('授信敞口金额不能大于授信金额');
  });

  it('客户搜索仅提交选中的客户 id，并支持保存草稿', async () => {
    api.listLoanCustomerCandidates.mockResolvedValue([{ id: 'C-1', name: '浦爱科技', customerType: 'CORP', status: 'ACTIVE' }]);
    api.createLoanApplication.mockResolvedValue({ id: 'L-1', status: 'DRAFT' });
    const wrapper = mountForm();

    await wrapper.vm.searchCustomers('浦爱');
    expect(api.listLoanCustomerCandidates).toHaveBeenCalledWith(expect.objectContaining({ keyword: '浦爱', status: 'ACTIVE', pageNo: 1 }));
    wrapper.vm.selectCustomer({ id: 'C-1', name: '浦爱科技', customerType: 'CORP', status: 'ACTIVE' });
    wrapper.vm.form.projectType = 'NEW_CREDIT';
    wrapper.vm.form.bizType = 'WORKING_CAPITAL';
    wrapper.vm.form.guaranteeType = 'MORTGAGE';
    wrapper.vm.form.creditAmount = 100;
    wrapper.vm.form.creditExposureAmount = 80;

    await wrapper.vm.saveDraft();
    await flushPromises();

    expect(api.createLoanApplication).toHaveBeenCalledWith(expect.objectContaining({ custId: 'C-1', creditAmount: 100, creditExposureAmount: 80 }));
    expect(message.success).toHaveBeenCalledWith('资产立项草稿已保存');
  });

  it('拒绝超过 50MB 的附件', () => {
    const wrapper = mountForm();
    const tooLarge = { uid: 'large', name: 'large.pdf', raw: { name: 'large.pdf', size: 50 * 1024 * 1024 + 1 } };

    wrapper.vm.onFileChange(tooLarge, [tooLarge]);

    expect(wrapper.vm.pendingFiles).toHaveLength(0);
    expect(message.warning).toHaveBeenCalledWith(expect.stringContaining('large.pdf'));
  });

  it('已上传附件不伪装成可从表单删除', () => {
    const wrapper = mountForm();

    expect(wrapper.vm.beforeFileRemove({ uid: 'F-1', name: '已存档附件.pdf', status: 'success' })).toBe(false);
    expect(message.warning).toHaveBeenCalledWith('已上传附件暂不支持在此删除');
  });

  it('附件上传失败后重试只上传未成功文件，避免重复关联', async () => {
    api.createLoanApplication.mockResolvedValue({ id: 'L-2' });
    api.updateLoanApplication.mockResolvedValue({ id: 'L-2' });
    api.uploadLoanAttachment
      .mockResolvedValueOnce({ id: 'F-1' })
      .mockRejectedValueOnce(new Error('upload failed'))
      .mockResolvedValueOnce({ id: 'F-2' });
    const wrapper = mountForm();
    wrapper.vm.form.custId = 'C-1';
    wrapper.vm.form.projectType = 'NEW_CREDIT';
    wrapper.vm.form.bizType = 'WORKING_CAPITAL';
    wrapper.vm.form.guaranteeType = 'MORTGAGE';
    wrapper.vm.form.creditAmount = 100;
    wrapper.vm.form.creditExposureAmount = 80;
    const first = { uid: 'f1', name: 'a.pdf', raw: new File(['a'], 'a.pdf') };
    const second = { uid: 'f2', name: 'b.pdf', raw: new File(['b'], 'b.pdf') };
    wrapper.vm.onFileChange(second, [first, second]);

    await expect(wrapper.vm.saveDraft()).rejects.toThrow('upload failed');
    await wrapper.vm.saveDraft();

    expect(api.uploadLoanAttachment).toHaveBeenCalledTimes(3);
    expect(api.uploadLoanAttachment.mock.calls[0][0]).toBe(first.raw);
    expect(api.uploadLoanAttachment.mock.calls[1][0]).toBe(second.raw);
    expect(api.uploadLoanAttachment.mock.calls[2][0]).toBe(second.raw);
  });
});
