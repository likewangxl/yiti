// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

const { listMock, updateMock, messageMock } = vi.hoisted(() => ({
  listMock: vi.fn(),
  updateMock: vi.fn(),
  messageMock: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}));

vi.mock('@/api/customerMarketing', () => ({
  listTouchLimitRules: listMock,
  updateTouchLimitRule: updateMock,
}));
vi.mock('element-plus', () => ({ ElMessage: messageMock }));

import TouchLimitManagement from '../TouchLimitManagement.vue';

const stubs = {
  PageTitle: { template: '<h1><slot>客户触达周期管理</slot></h1>' },
  'el-alert': { template: '<div><slot /></div>' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-card': { template: '<section><slot /></section>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option><slot /></option>' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<span />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-pagination': { template: '<div />' },
  'el-input-number': { template: '<input />' },
};

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

let wrapper;

beforeEach(() => {
  listMock.mockReset().mockResolvedValue({
    records: [{
      id: 'TAG-1',
      tagName: '重点客户',
      tagStatus: 'ACTIVE',
      approvalStatus: 'APPROVED',
      cycleUnit: null,
      maxTouches: null,
    }],
    total: 1,
  });
  updateMock.mockReset().mockResolvedValue({});
  Object.values(messageMock).forEach(mock => mock.mockReset());
});

afterEach(() => wrapper?.unmount());

describe('客户触达周期管理页面', () => {
  it('初始查询并把缺省规则展示为月度 5 次', async () => {
    wrapper = mount(TouchLimitManagement, {
      global: { stubs, directives: { loading: () => {}, 'bp-overflow-tooltip': () => {} } },
    });
    await settle();

    expect(listMock).toHaveBeenCalledWith({ keyword: undefined, pageNo: 1, pageSize: 20 });
    expect(wrapper.vm.rows[0]).toEqual(expect.objectContaining({
      cycleUnit: 'MONTH',
      maxTouches: 5,
      tagStatus: 'ACTIVE',
      approvalStatus: 'APPROVED',
    }));
    expect(wrapper.text()).toContain('客户触达周期管理');
    expect(wrapper.vm.openEdit).toBeTypeOf('function');
  });

  it('修改对话框只提交允许的周期和 1 至 9999 次上限', async () => {
    wrapper = mount(TouchLimitManagement, {
      global: { stubs, directives: { loading: () => {}, 'bp-overflow-tooltip': () => {} } },
    });
    await settle();

    wrapper.vm.openEdit(wrapper.vm.rows[0]);
    wrapper.vm.form.cycleUnit = 'YEAR';
    wrapper.vm.form.maxTouches = 9999;
    await wrapper.vm.onSubmit();

    expect(updateMock).toHaveBeenCalledWith('TAG-1', { cycleUnit: 'YEAR', maxTouches: 9999 });
    expect(messageMock.success).toHaveBeenCalled();

    wrapper.vm.form.maxTouches = 10000;
    expect(wrapper.vm.validateForm()).toContain('1-9999');
  });
});
