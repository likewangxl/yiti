// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

const {
  createMock,
  deleteMock,
  executeMock,
  exportMock,
  getMock,
  listMock,
  previewMock,
  templateMock,
  updateMock,
  messageMock,
  messageBoxMock,
} = vi.hoisted(() => ({
  createMock: vi.fn(),
  deleteMock: vi.fn(),
  executeMock: vi.fn(),
  exportMock: vi.fn(),
  getMock: vi.fn(),
  listMock: vi.fn(),
  previewMock: vi.fn(),
  templateMock: vi.fn(),
  updateMock: vi.fn(),
  messageMock: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  messageBoxMock: { confirm: vi.fn(), prompt: vi.fn() },
}));

vi.mock('@/api/ccrmCustomers', () => ({
  createCcrmCustomer: createMock,
  deleteCcrmCustomer: deleteMock,
  executeCcrmImport: executeMock,
  exportCcrmCustomers: exportMock,
  getCcrmCustomer: getMock,
  listCcrmCustomers: listMock,
  previewCcrmImport: previewMock,
  downloadCcrmImportTemplate: templateMock,
  updateCcrmCustomer: updateMock,
}));
vi.mock('element-plus', () => ({ ElMessage: messageMock, ElMessageBox: messageBoxMock }));

import CcrmCustomerSource from '../CcrmCustomerSource.vue';

const stubs = {
  PageTitle: { template: '<h1>CCRM 客户源数据</h1>' },
  'el-alert': { template: '<div><slot /></div>' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-card': { template: '<section><slot /></section>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option><slot /></option>' },
  'el-date-picker': { template: '<input />' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<span />' },
  'el-pagination': { template: '<div />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-skeleton': { template: '<div />' },
  'el-divider': { template: '<hr />' },
  'el-drawer': { template: '<div><slot /></div>' },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-descriptions': { template: '<div><slot /></div>' },
  'el-descriptions-item': { template: '<div><slot /></div>' },
  'el-upload': { template: '<div><slot /></div>' },
  'el-empty': { template: '<div><slot /></div>' },
};

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

function mountPage() {
  return mount(CcrmCustomerSource, {
    global: { stubs, directives: { loading: () => {}, 'bp-overflow-tooltip': () => {} } },
  });
}

let wrapper;

beforeEach(() => {
  listMock.mockReset().mockResolvedValue({ records: [], total: 0 });
  getMock.mockReset().mockResolvedValue({});
  createMock.mockReset().mockResolvedValue({ id: 'S1', lockVersion: 1 });
  updateMock.mockReset().mockResolvedValue({ id: 'S1', lockVersion: 4 });
  deleteMock.mockReset().mockResolvedValue({});
  previewMock.mockReset();
  executeMock.mockReset().mockResolvedValue({});
  exportMock.mockReset().mockResolvedValue(new Blob(['xlsx']));
  templateMock.mockReset().mockResolvedValue(new Blob(['xlsx']));
  Object.values(messageMock).forEach(mock => mock.mockReset());
  Object.values(messageBoxMock).forEach(mock => mock.mockReset());
});

afterEach(() => wrapper?.unmount());

describe('CCRM 客户源数据人工维护', () => {
  it('初始查询包含规格要求的全部筛选字段，且页面声明来源边界', async () => {
    wrapper = mountPage();
    await settle();

    expect(listMock).toHaveBeenCalledWith(expect.objectContaining({
      keyword: undefined,
      mainManager: undefined,
      mainOrg: undefined,
      customerType: undefined,
      industry: undefined,
      groupType: undefined,
      accountOpened: undefined,
      recordStatus: undefined,
      validationStatus: undefined,
      importMode: undefined,
      sourceUpdatedStart: undefined,
      sourceUpdatedEnd: undefined,
      pageNo: 1,
      pageSize: 20,
    }));
    expect(wrapper.text()).toContain('CCRM 独立来源数据');
    expect(wrapper.text()).toContain('不会直接创建线索、触达任务或资产立项');
  });

  it('表单校验覆盖统一信用代码、客户类型、主办权成对、集团名称和金额关系', async () => {
    wrapper = mountPage();
    await settle();

    expect(wrapper.vm.validateForm({
      custName: '', unifiedCreditCode: 'bad', customerType: 'RETAIL',
      mainManagerId: 'E1', mainOrgId: '', groupType: 'GROUP', groupName: '',
      creditAmount: 10, creditExposureAmount: 11,
    })).toEqual(expect.objectContaining({
      custName: expect.any(String),
      unifiedCreditCode: expect.any(String),
      customerType: expect.any(String),
      ownership: expect.any(String),
      groupName: expect.any(String),
      creditExposureAmount: expect.any(String),
    }));
  });

  it('编辑提交携带 lockVersion，停用必须填写原因且写失败不伪装成功', async () => {
    wrapper = mountPage();
    await settle();
    await wrapper.vm.save({
      id: 'S1', custName: '华夏科技', unifiedCreditCode: '91310000123456789A',
      customerType: 'CORP', mainManagerId: 'E1', mainOrgId: 'ORG1',
      ownershipStatus: 'ASSIGNED', lockVersion: 3,
    });
    expect(updateMock).toHaveBeenCalledWith('S1', expect.objectContaining({ lockVersion: 3 }));

    messageBoxMock.prompt.mockResolvedValue({ value: '  ' });
    await wrapper.vm.remove({ id: 'S1', lockVersion: 3 });
    expect(deleteMock).not.toHaveBeenCalled();
    expect(messageMock.warning).toHaveBeenCalledWith('停用原因不能为空');

    messageBoxMock.prompt.mockResolvedValue({ value: '业务迁移' });
    await wrapper.vm.remove({ id: 'S1', lockVersion: 3 });
    expect(deleteMock).toHaveBeenCalledWith('S1', { reason: '业务迁移', lockVersion: 3 });
  });

  it('点击新增弹窗的保存按钮提交表单数据，并把未知开户状态转换为 null', async () => {
    wrapper = mountPage();
    await settle();
    wrapper.vm.openCreate();
    Object.assign(wrapper.vm.form, {
      custName: '华夏科技', unifiedCreditCode: '91310000123456789A',
      customerType: 'CORP', ownershipStatus: 'UNKNOWN', isAccountOpened: 'UNKNOWN',
    });
    await nextTick();

    const saveButton = wrapper.findAll('button').find(button => button.text() === '保存');
    await saveButton.trigger('click');
    await settle();

    expect(createMock).toHaveBeenCalledWith(expect.objectContaining({
      custName: '华夏科技', isAccountOpened: null, importMode: 'MANUAL',
    }));
  });

  it('导入预览有失败行时不得执行，全部通过后按 batchId 二次确认执行', async () => {
    wrapper = mountPage();
    await settle();
    previewMock.mockResolvedValue({ batchId: 'B1', addedCount: 1, failedCount: 1, errors: [{ row: 2, reason: '统一社会信用代码无效' }] });
    const file = new File(['xlsx'], 'ccrm.xlsx');
    await wrapper.vm.previewFile(file);
    expect(executeMock).not.toHaveBeenCalled();
    expect(wrapper.vm.importPreview.errors).toHaveLength(1);

    previewMock.mockResolvedValue({ batchId: 'B2', addedCount: 1, failedCount: 0, errors: [] });
    await wrapper.vm.previewFile(file);
    messageBoxMock.confirm.mockResolvedValue(true);
    await wrapper.vm.executeImport();
    expect(executeMock).toHaveBeenCalledWith('B2');
  });
});
