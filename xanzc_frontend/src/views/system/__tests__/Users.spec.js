// @vitest-environment happy-dom
// 用户管理工作区回归：保持既有用户/机构 API 契约，同时覆盖桌面信息层级、可访问状态与批量写防重。
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}));
vi.mock('@element-plus/icons-vue', () => ({ Search: { name: 'Search', template: '<i />' } }));
vi.mock('@/api/users', () => ({
  listUsers: vi.fn(),
  getUser: vi.fn(), createUser: vi.fn(), updateUser: vi.fn(),
  deleteUsers: vi.fn(), resetUsersPassword: vi.fn(), activeUsers: vi.fn(),
  inactiveUsers: vi.fn(), lockUsers: vi.fn(), unlockUsers: vi.fn(),
  getUserRoles: vi.fn(), replaceUserRoles: vi.fn(), bindUserRoles: vi.fn(),
  exportUsersBlob: vi.fn(),
  USER_STATUS_LABEL: { 0: '启用', 1: '停用' },
  USER_LOCK_LABEL: { 0: '正常', 1: '锁定' }
}));
vi.mock('@/api/system', () => ({
  listAllRoles: vi.fn(),
  listDictItems: vi.fn()
}));
vi.mock('@/api/orgs', () => ({
  getOrgTree: vi.fn(),
  listOrgUsers: vi.fn(),
  createOrg: vi.fn(), updateOrg: vi.fn(), deleteOrg: vi.fn()
}));

import { ElMessage, ElMessageBox } from 'element-plus';
import {
  listUsers, deleteUsers, activeUsers,
  getUser, createUser, updateUser, resetUsersPassword, inactiveUsers, lockUsers, unlockUsers,
  getUserRoles, replaceUserRoles, bindUserRoles, exportUsersBlob
} from '@/api/users';
import { listAllRoles, listDictItems } from '@/api/system';
import { getOrgTree, listOrgUsers, createOrg, updateOrg, deleteOrg } from '@/api/orgs';
import Users from '../Users.vue';

const treeFilter = vi.fn();
const treeSetCurrentKey = vi.fn();
const passthrough = (name) => ({ name, template: '<div><slot /><slot name="header" /><slot name="footer" /><slot name="reference" /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  PageTitle: {
    name: 'PageTitle',
    inheritAttrs: false,
    props: ['title'],
    template: '<h1 class="page-title" v-bind="$attrs">{{ title }}<slot /></h1>'
  },
  // 必须声明 emits，否则父级 @click 经 attrs 透传到原生 button 后与 $emit 叠加，处理器被调两次。
  'el-button': {
    name: 'ElButton',
    props: { disabled: Boolean, loading: Boolean },
    emits: ['click'],
    template: '<button :disabled="disabled || loading" :data-loading="loading ? \'true\' : \'false\'" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-dialog': {
    name: 'ElDialog',
    props: { modelValue: Boolean },
    template: '<div v-if="modelValue" class="dialog-stub"><slot /><slot name="footer" /></div>'
  },
  'el-form': { name: 'ElForm', inheritAttrs: false, template: '<form v-bind="$attrs"><slot /></form>' },
  'el-form-item': { name: 'ElFormItem', props: ['label'], template: '<div class="form-item-stub" :data-label="label || \'\'"><slot /></div>' },
  'el-input': {
    name: 'ElInput',
    inheritAttrs: false,
    props: { modelValue: [String, Number], placeholder: String, maxlength: [String, Number], disabled: Boolean },
    emits: ['update:modelValue'],
    template: '<input class="input-stub" :value="modelValue" :placeholder="placeholder" :maxlength="maxlength" :disabled="disabled" v-bind="$attrs" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-select': { name: 'ElSelect', inheritAttrs: false, template: '<select class="select-stub" v-bind="$attrs"><slot /></select>' },
  'el-option': { name: 'ElOption', props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-tag': passthrough('ElTag'),
  'el-tree': {
    name: 'ElTree',
    inheritAttrs: false,
    props: ['data', 'nodeKey'],
    emits: ['node-click'],
    methods: {
      filter(value) { treeFilter(value); },
      setCurrentKey(value) { treeSetCurrentKey(value); }
    },
    template: '<div class="tree-stub" v-bind="$attrs"><slot /></div>'
  },
  'el-tree-select': empty('ElTreeSelect'),
  'el-transfer': empty('ElTransfer'),
  'el-radio': passthrough('ElRadio'),
  'el-radio-group': passthrough('ElRadioGroup'),
  'el-popconfirm': passthrough('ElPopconfirm'),
  'el-pagination': empty('ElPagination'),
  'el-table': {
    name: 'ElTable',
    inheritAttrs: false,
    props: { data: { type: Array, default: () => [] }, emptyText: String },
    template: '<div class="tbl-stub" v-bind="$attrs"><slot /><slot v-if="!data.length" name="empty" /></div>'
  },
  'el-table-column': {
    name: 'ElTableColumn',
    props: ['type', 'label', 'prop'],
    template: '<div class="col-stub" :data-type="type || \'\'" :data-label="label || \'\'" :data-prop="prop || \'\'" />'
  },
  'el-dropdown': passthrough('ElDropdown'),
  'el-dropdown-menu': passthrough('ElDropdownMenu'),
  'el-dropdown-item': passthrough('ElDropdownItem')
};

const defaultRows = () => ({
  records: [{ userId: 'E001', username: '10086', userchnname: '张三', deptName: '公司客户一部', remark: '备注X', isEnabled: 0, isLocked: 0 }],
  total: 1
});

let wrapper;
function mountPage() {
  wrapper = mount(Users, {
    global: {
      stubs,
      // v-loading 是 Element Plus 指令；测试替身须注册以避免无关 warning 干扰断言。
      directives: { loading: { mounted() {}, updated() {} }, 'bp-overflow-tooltip': {} }
    }
  });
  return wrapper;
}
async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}
function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

beforeEach(() => {
  vi.clearAllMocks();
  treeFilter.mockClear();
  treeSetCurrentKey.mockClear();
  ElMessageBox.confirm.mockResolvedValue('confirm');
  listUsers.mockResolvedValue(defaultRows());
  getUser.mockResolvedValue({});
  createUser.mockResolvedValue({ ok: true });
  updateUser.mockResolvedValue({ ok: true });
  deleteUsers.mockResolvedValue({ ok: true });
  resetUsersPassword.mockResolvedValue({ ok: true });
  activeUsers.mockResolvedValue({ ok: true });
  inactiveUsers.mockResolvedValue({ ok: true });
  lockUsers.mockResolvedValue({ ok: true });
  unlockUsers.mockResolvedValue({ ok: true });
  getUserRoles.mockResolvedValue([]);
  replaceUserRoles.mockResolvedValue({ added: 0, removed: 0 });
  bindUserRoles.mockResolvedValue({ ok: true });
  exportUsersBlob.mockResolvedValue(new Blob());
  listAllRoles.mockResolvedValue([]);
  listDictItems.mockResolvedValue([]);
  getOrgTree.mockResolvedValue([]);
  listOrgUsers.mockResolvedValue({ records: [], total: 0 });
  createOrg.mockResolvedValue({ ok: true });
  updateOrg.mockResolvedValue({ ok: true });
  deleteOrg.mockResolvedValue({ ok: true });
});
afterEach(() => { wrapper?.unmount(); });

describe('Users.vue 用户管理工作区', () => {
  it('列表在「备注」前包含「部门」列（prop=deptName）', async () => {
    mountPage();
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

  it('以 main/heading 关联页面，并为机构搜索和列表筛选提供可访问名称', async () => {
    mountPage();
    await settle();

    expect(wrapper.find('main.bp-crud[aria-labelledby="users-page-title"]').exists()).toBe(true);
    expect(wrapper.find('h1#users-page-title').text()).toContain('用户管理');
    expect(wrapper.find('input[aria-label="搜索机构"]').exists()).toBe(true);
    expect(wrapper.find('form[aria-label="用户筛选"]').exists()).toBe(true);
    expect(wrapper.find('input[aria-label="按工号筛选"]').exists()).toBe(true);
    expect(wrapper.find('input[aria-label="按姓名筛选"]').exists()).toBe(true);
    expect(wrapper.find('select[aria-label="按启用状态筛选"]').exists()).toBe(true);
    expect(wrapper.find('select[aria-label="按锁定状态筛选"]').exists()).toBe(true);
    expect(wrapper.find('.selection-count').attributes('aria-live')).toBe('polite');
  });

  it('列表加载期间标记 aria-busy，成功返回空记录后展示准确空态', async () => {
    const request = deferred();
    listUsers.mockReturnValueOnce(request.promise);
    mountPage();
    await nextTick();

    const listPanel = wrapper.find('[aria-label="用户列表"]');
    expect(listPanel.attributes('aria-busy')).toBe('true');
    expect(listPanel.text()).toContain('用户列表加载中');
    expect(listPanel.text()).not.toContain('暂无用户数据');

    request.resolve({ records: [], total: 0 });
    await settle();

    expect(listPanel.attributes('aria-busy')).toBe('false');
    expect(listPanel.text()).toContain('暂无用户数据');
    expect(listPanel.text()).not.toContain('用户列表加载中');
  });

  it('仅在可捕获的列表异常时展示带重试入口的错误区', async () => {
    listUsers.mockRejectedValueOnce(new Error('连接中断'));
    mountPage();
    await settle();

    const alert = wrapper.find('[role="alert"]');
    expect(alert.exists()).toBe(true);
    expect(alert.text()).toContain('用户列表暂时无法加载');
    expect(alert.find('button').text()).toContain('重试');
  });

  it('未选择机构时保持全量 listUsers 的既有参数契约', async () => {
    mountPage();
    await settle();

    expect(listOrgUsers).not.toHaveBeenCalled();
    expect(listUsers).toHaveBeenCalledTimes(1);
    expect(listUsers).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 20,
      username: undefined,
      userchnname: undefined,
      isEnabled: undefined,
      isLocked: undefined
    });
  });

  it('选择机构后按现有契约查询并归一用户字段，清除时同步清空树当前项', async () => {
    getOrgTree.mockResolvedValueOnce([{ code: 'ORG_A', name: '城东支行', deptNo: '0101' }]);
    listOrgUsers.mockResolvedValueOnce({
      records: [{ empId: 'E900', displayName: '机构用户', isEnabled: 0, isLocked: 1, remark: '机构备注', deptName: '城东支行' }],
      total: 1
    });
    mountPage();
    await settle();

    const tree = wrapper.findComponent({ name: 'ElTree' });
    tree.vm.$emit('node-click', { code: 'ORG_A', name: '城东支行', deptNo: '0101' });
    await settle();

    expect(listOrgUsers).toHaveBeenCalledWith('ORG_A', {
      pageNo: 1,
      pageSize: 20,
      keyword: undefined,
      isEnabled: undefined,
      isLocked: undefined
    });
    expect(wrapper.findComponent({ name: 'ElTable' }).props('data')[0]).toMatchObject({
      userId: 'E900',
      userchnname: '机构用户',
      isEnabled: 0,
      isLocked: 1,
      remark: '机构备注',
      deptName: '城东支行'
    });

    wrapper.vm.clearOrg();
    await settle();

    expect(treeSetCurrentKey).toHaveBeenCalledWith(null);
    expect(listUsers).toHaveBeenLastCalledWith({
      pageNo: 1,
      pageSize: 20,
      username: undefined,
      userchnname: undefined,
      isEnabled: undefined,
      isLocked: undefined
    });
  });

  it('批量操作无选择时不发写请求，且单条覆盖 ID 保持精确并刷新列表', async () => {
    mountPage();
    await settle();

    await wrapper.vm.batch('active');
    expect(ElMessage.warning).toHaveBeenCalledWith('请先勾选用户');
    expect(activeUsers).not.toHaveBeenCalled();

    await wrapper.vm.batch('delete', ['E-ONLY']);
    await settle();
    expect(deleteUsers).toHaveBeenCalledWith(['E-ONLY']);
    expect(listUsers).toHaveBeenCalledTimes(2);
  });

  it('批量写操作函数级防重，进行时全部批量按钮 loading/disabled，成功后仅刷新一次', async () => {
    const writeRequest = deferred();
    activeUsers.mockReturnValueOnce(writeRequest.promise);
    mountPage();
    await settle();
    wrapper.vm.selection = [{ userId: 'E001' }, { userId: 'E002' }];
    await nextTick();

    const first = wrapper.vm.batch('active');
    await nextTick();
    const pendingButtons = wrapper.findAll('.batch-bar button');
    const second = wrapper.vm.batch('active');
    const callsWhilePending = activeUsers.mock.calls.length;
    const allBlockedWhilePending = pendingButtons.every(button =>
      button.attributes('disabled') !== undefined && button.attributes('data-loading') === 'true');

    writeRequest.resolve({ ok: true });
    await Promise.all([first, second]);
    await settle();

    expect(activeUsers).toHaveBeenCalledWith(['E001', 'E002']);
    expect(callsWhilePending).toBe(1);
    expect(allBlockedWhilePending).toBe(true);
    expect(listUsers).toHaveBeenCalledTimes(2);
  });

  it('用户备注输入与校验规则均与后端 256 字上限对齐', async () => {
    mountPage();
    await settle();
    wrapper.vm.dlg.show = true;
    await nextTick();

    expect(wrapper.vm.dlg.rules.remark).toContainEqual(expect.objectContaining({ max: 256 }));
    const remarkInput = wrapper.find('.form-item-stub[data-label="备注"] input');
    expect(remarkInput.attributes('maxlength')).toBe('256');
    expect(remarkInput.attributes('placeholder')).toContain('256');
  });
});
