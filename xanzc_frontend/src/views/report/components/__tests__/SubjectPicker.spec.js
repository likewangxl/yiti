// @vitest-environment happy-dom
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('@/api/orgs', () => ({ getOrgTree: vi.fn().mockResolvedValue([]), listOrgUsers: vi.fn().mockResolvedValue([]) }));
vi.mock('@/api/report', () => ({
  getPickerScope: vi.fn().mockResolvedValue({ mode: 'ALL', orgCodes: [] }),
  searchReportEmployees: vi.fn().mockResolvedValue([]),
  searchReportCustomers: vi.fn().mockResolvedValue([])
}));

import SubjectPicker from '../SubjectPicker.vue';

const stubs = {
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-tree': true, 'el-input': true, 'el-checkbox': true,
  'el-button': { template: '<button @click="$emit(\'click\')"><slot/></button>' },
  'el-tag': { template: '<span><slot/></span>' }
};

describe('SubjectPicker.vue', () => {
  beforeEach(() => vi.clearAllMocks());

  it('打开时用 modelValue 初始化已选,确定回传 update:modelValue 并关闭', async () => {
    const wrapper = mount(SubjectPicker, {
      props: { visible: true, modelValue: [{ id: 'E1', name: '张三', org: '' }], dim: 'EMP' },
      global: { stubs }
    });
    await wrapper.vm.$nextTick();
    await wrapper.vm.confirmSubjects();
    const emitted = wrapper.emitted('update:modelValue');
    expect(emitted).toBeTruthy();
    expect(emitted[0][0]).toEqual([{ id: 'E1', name: '张三', org: '' }]);
    expect(wrapper.emitted('update:visible')[0][0]).toBe(false);
  });
});
