// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  listOrgGroups: vi.fn(),
  listOrgProfiles: vi.fn(),
  listScreenRoles: vi.fn(),
  createOrgGroup: vi.fn(),
  saveOrgGroupMembers: vi.fn(),
  saveOrgGroupRoles: vi.fn()
}));
const messageBox = vi.hoisted(() => ({
  prompt: vi.fn().mockResolvedValue({ value: '季度机构调整' })
}));

vi.mock('@/api/screen', () => api);
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: messageBox
}));

import OrgGroups from '../OrgGroups.vue';

const groups = [{
  groupCode: 'G1', groupName: '全辖一级经营机构', groupPurpose: 'REPORT_SCREEN',
  status: 'ACTIVE', version: 3, memberOrgCodes: ['X1'], roleCodes: ['R1']
}];
const profiles = [
  { orgCode: 'X1', orgName: '西安机构', orgNature: 'LOCAL_BRANCH', operatingLevel: 'PRIMARY' },
  { orgCode: 'X2', orgName: '宝鸡机构', orgNature: 'SECONDARY_BRANCH', operatingLevel: 'PRIMARY' }
];
const roles = [
  { roleCode: 'R1', roleChName: '大屏查看角色' },
  { roleCode: 'R2', roleChName: '零售查看角色' }
];

const passthrough = { template: '<div><slot /></div>' };
const stubs = {
  PageTitle: passthrough,
  'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-input': { props: ['modelValue'], template: '<input :value="modelValue" />' },
  'el-select': passthrough,
  'el-option': { template: '<option><slot /></option>' },
  'el-form': passthrough,
  'el-form-item': passthrough,
  'el-tag': passthrough,
  'el-table': { props: ['data'], template: '<div class="table"><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' }
};

let wrapper;
async function settle() {
  await flushPromises();
  await flushPromises();
}
async function mountPage() {
  api.listOrgGroups.mockResolvedValue(groups);
  api.listOrgProfiles.mockResolvedValue(profiles);
  api.listScreenRoles.mockResolvedValue(roles);
  wrapper = mount(OrgGroups, { global: { stubs, directives: { loading: {} } } });
  await settle();
  await wrapper.find('.group-item').trigger('click');
  await settle();
}

beforeEach(() => {
  vi.clearAllMocks();
  messageBox.prompt.mockResolvedValue({ value: '季度机构调整' });
  wrapper?.unmount();
});

describe('OrgGroups.vue 覆盖保存契约', () => {
  it('覆盖保存成员携带当前版本和变更原因，避免后端乐观锁拒绝', async () => {
    await mountPage();
    await wrapper.findAll('button').find(button => button.text().includes('覆盖保存成员')).trigger('click');
    await settle();

    expect(api.saveOrgGroupMembers).toHaveBeenCalledWith('G1', {
      orgCodes: ['X1'], version: 3, reason: '季度机构调整'
    });
  });

  it('覆盖保存角色携带当前版本和变更原因，避免后端乐观锁拒绝', async () => {
    await mountPage();
    await wrapper.findAll('button').find(button => button.text().includes('覆盖保存角色')).trigger('click');
    await settle();

    expect(api.saveOrgGroupRoles).toHaveBeenCalledWith('G1', {
      roleCodes: ['R1'], version: 3, reason: '季度机构调整'
    });
  });

  it('覆盖前差异预览列出具体成员和角色，而不只显示数量', async () => {
    await mountPage();
    wrapper.vm.selectedCodes = ['X2'];
    wrapper.vm.roleCodes = ['R2'];
    await wrapper.vm.$nextTick();

    expect(wrapper.text()).toContain('新增：宝鸡机构（X2）');
    expect(wrapper.text()).toContain('移除：西安机构（X1）');
    expect(wrapper.text()).toContain('新增：零售查看角色（R2）');
    expect(wrapper.text()).toContain('移除：大屏查看角色（R1）');
  });
});
