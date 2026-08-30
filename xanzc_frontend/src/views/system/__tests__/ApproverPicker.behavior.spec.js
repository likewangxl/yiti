// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('@/api/system', () => ({
  listAllRoles: vi.fn().mockResolvedValue([]),
}));
vi.mock('@/api/orgs', () => ({
  getOrgTree: vi.fn().mockResolvedValue([]),
}));
vi.mock('@/api/userDirectory', () => ({
  searchEmployees: vi.fn().mockResolvedValue([]),
  getEmployee: vi.fn().mockResolvedValue(null),
}));

import ApproverPicker from '../flow/ApproverPicker.vue';

const stubs = {
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<option><slot /></option>' },
  'el-button': { template: '<button><slot /></button>' },
};

describe('ApproverPicker 初始化', () => {
  it('点击审批节点首次挂载时 immediate watch 不抛初始化顺序异常', () => {
    expect(() => mount(ApproverPicker, {
      props: { modelValue: [] },
      global: { stubs },
    })).not.toThrow();
  });
});
