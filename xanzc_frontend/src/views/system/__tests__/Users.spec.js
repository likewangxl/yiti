// @vitest-environment happy-dom
// 用户管理「部门」列回归测试。
// 需求：列表在「备注」列前新增「部门」列（prop=deptName），
// 值由后端 EXT_USER_ORG ⋈ EXT_ORG_INFO 联查返回（ORG_NAME）。
import { describe, it, expect, vi, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}));
vi.mock('@element-plus/icons-vue', () => ({ Search: { name: 'Search', template: '<i />' } }));
vi.mock('@/api/users', () => ({
  listUsers: vi.fn().mockResolvedValue({
    records: [{ userId: 'E001', username: '10086', userchnname: '张三', deptName: '公司客户一部', remark: '备注X', isEnabled: 0, isLocked: 0 }],
    total: 1
  }),
  getUser: vi.fn(), createUser: vi.fn(), updateUser: vi.fn(),
  deleteUsers: vi.fn(), resetUsersPassword: vi.fn(), activeUsers: vi.fn(),
  inactiveUsers: vi.fn(), lockUsers: vi.fn(), unlockUsers: vi.fn(),
  getUserRoles: vi.fn().mockResolvedValue([]), replaceUserRoles: vi.fn(), bindUserRoles: vi.fn(),
  exportUsersBlob: vi.fn(),
  USER_STATUS_LABEL: { 0: '启用', 1: '停用' },
  USER_LOCK_LABEL: { 0: '正常', 1: '锁定' }
}));
vi.mock('@/api/system', () => ({
  listAllRoles: vi.fn().mockResolvedValue([]),
  listDictItems: vi.fn().mockResolvedValue([])
}));
vi.mock('@/api/orgs', () => ({
  getOrgTree: vi.fn().mockResolvedValue([]),
  listOrgUsers: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  createOrg: vi.fn(), updateOrg: vi.fn(), deleteOrg: vi.fn()
}));

import Users from '../Users.vue';

const passthrough = (name) => ({ name, template: '<div><slot /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  PageTitle: passthrough('PageTitle'),
  // 必须声明 emits，否则父级 @click 经 attrs 透传到原生 button 后与 $emit 叠加，处理器被调两次
  'el-button': { name: 'ElButton', emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-dialog': { name: 'ElDialog', template: '<div><slot /><slot name="footer" /></div>' },
  'el-form': passthrough('ElForm'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': empty('ElInput'),
  'el-select': empty('ElSelect'),
  'el-option': empty('ElOption'),
  'el-tag': passthrough('ElTag'),
  'el-tree': empty('ElTree'),
  'el-tree-select': empty('ElTreeSelect'),
  'el-transfer': empty('ElTransfer'),
  'el-radio': passthrough('ElRadio'),
  'el-radio-group': passthrough('ElRadioGroup'),
  'el-popconfirm': passthrough('ElPopconfirm'),
  'el-pagination': empty('ElPagination'),
  'el-table': { name: 'ElTable', props: ['data'], template: '<div class="tbl-stub"><slot /></div>' },
  'el-table-column': {
    name: 'ElTableColumn',
    props: ['type', 'label', 'prop'],
    template: '<div class="col-stub" :data-type="type || \'\'" :data-label="label || \'\'" :data-prop="prop || \'\'" />'
  }
};

let wrapper;
afterEach(() => { wrapper?.unmount(); });

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

describe('Users.vue 部门列', () => {
  it('列表在「备注」前包含「部门」列（prop=deptName）', async () => {
    wrapper = mount(Users, { global: { stubs } });
    await settle();

    const cols = wrapper.findAll('.tbl-stub .col-stub');
    const labels = cols.map(c => c.attributes('data-label'));
    const deptIdx = labels.indexOf('部门');
    const remarkIdx = labels.indexOf('备注');

    expect(deptIdx, '应存在「部门」列').toBeGreaterThan(-1);
    expect(remarkIdx, '应存在「备注」列').toBeGreaterThan(-1);
    expect(deptIdx, '「部门」列应在「备注」列之前').toBeLessThan(remarkIdx);
    expect(cols[deptIdx].attributes('data-prop'), '部门列取行数据 deptName 字段').toBe('deptName');
  });
});
