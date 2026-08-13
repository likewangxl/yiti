// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

const routerPush = vi.fn();
vi.mock('vue-router', () => ({ useRouter: () => ({ push: routerPush }) }));
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() } }));
vi.mock('@/api/system', () => ({
  listRoles: vi.fn(),
  createRole: vi.fn(),
  updateRole: vi.fn(),
  deleteRole: vi.fn(),
  listRoleUsers: vi.fn(),
  getMenuTree: vi.fn(),
  getRoleMenuIds: vi.fn(),
  replaceRoleMenus: vi.fn()
}));

import {
  createRole,
  deleteRole,
  getMenuTree,
  getRoleMenuIds,
  listRoleUsers,
  listRoles,
  replaceRoleMenus,
  updateRole
} from '@/api/system';
import Roles from '../Roles.vue';

const passthrough = (name) => ({
  name,
  template: '<div><slot /><slot name="header" /><slot name="footer" /><slot name="reference" /></div>'
});
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  PageTitle: {
    name: 'PageTitle',
    inheritAttrs: false,
    template: '<h1 class="page-title" v-bind="$attrs">角色管理<slot /></h1>'
  },
  'el-button': {
    name: 'ElButton',
    props: { disabled: Boolean, loading: Boolean },
    emits: ['click'],
    template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-form': {
    name: 'ElForm',
    inheritAttrs: false,
    template: '<form v-bind="$attrs"><slot /></form>'
  },
  'el-form-item': { name: 'ElFormItem', props: ['label'], template: '<div :data-label="label || \'\'"><slot /></div>' },
  'el-input': {
    name: 'ElInput',
    inheritAttrs: false,
    props: { modelValue: [String, Number], placeholder: String, disabled: Boolean },
    emits: ['update:modelValue'],
    template: '<input :value="modelValue" :placeholder="placeholder" :disabled="disabled" v-bind="$attrs" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-select': {
    name: 'ElSelect',
    inheritAttrs: false,
    props: { modelValue: [String, Number] },
    emits: ['update:modelValue'],
    template: '<select v-bind="$attrs"><slot /></select>'
  },
  'el-option': { name: 'ElOption', props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-table': {
    name: 'ElTable',
    inheritAttrs: false,
    props: { data: { type: Array, default: () => [] }, emptyText: String },
    template: '<div class="table-stub" v-bind="$attrs"><slot /></div>'
  },
  'el-table-column': { name: 'ElTableColumn', props: ['label', 'prop'], template: '<div />' },
  'el-pagination': empty('ElPagination'),
  'el-dialog': {
    name: 'ElDialog',
    props: { modelValue: Boolean, title: String },
    template: '<div v-if="modelValue" class="dialog-stub"><slot /><slot name="footer" /></div>'
  },
  'el-radio': passthrough('ElRadio'),
  'el-radio-group': passthrough('ElRadioGroup'),
  'el-tree': {
    name: 'ElTree',
    props: ['data'],
    methods: { getCheckedKeys: () => [] },
    template: '<div><slot /></div>'
  },
  'el-tag': passthrough('ElTag'),
  'el-popconfirm': passthrough('ElPopconfirm')
};

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

function mountPage() {
  return mount(Roles, {
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
  });
}

let wrapper;

beforeEach(() => {
  vi.clearAllMocks();
  listRoles.mockResolvedValue({ records: [{ roleId: 1, roleCode: 'R_TEST', roleChName: '测试角色' }], total: 1 });
  createRole.mockResolvedValue({ ok: true });
  updateRole.mockResolvedValue({ ok: true });
  deleteRole.mockResolvedValue({ ok: true });
  listRoleUsers.mockResolvedValue({ records: [], total: 0 });
  getMenuTree.mockResolvedValue([]);
  getRoleMenuIds.mockResolvedValue([]);
  replaceRoleMenus.mockResolvedValue({ ok: true });
});

afterEach(() => wrapper?.unmount());

describe('Roles.vue 角色管理工作区', () => {
  it('以 bp-crud 主区域组织页头、筛选、数据表和分页语义', async () => {
    wrapper = mountPage();
    await settle();

    expect(wrapper.find('main.bp-crud[aria-labelledby="roles-page-title"]').exists()).toBe(true);
    expect(wrapper.find('h1#roles-page-title').text()).toContain('角色管理');
    expect(wrapper.find('form[aria-label="角色筛选"]').exists()).toBe(true);
    expect(wrapper.find('input[aria-label="按角色名称或编码筛选"]').exists()).toBe(true);
    expect(wrapper.find('select[aria-label="按角色状态筛选"]').exists()).toBe(true);
    expect(wrapper.find('[aria-label="角色列表"]').exists()).toBe(true);
    expect(wrapper.find('nav[aria-label="角色列表分页"]').exists()).toBe(true);
  });

  it('加载中标记 aria-busy，成功空结果保留可辨识的空态', async () => {
    const request = deferred();
    listRoles.mockReturnValueOnce(request.promise);
    wrapper = mountPage();
    await nextTick();

    const panel = wrapper.get('[aria-label="角色列表"]');
    expect(panel.attributes('aria-busy')).toBe('true');
    expect(wrapper.get('#roles-table-state').text()).toContain('角色列表加载中');

    request.resolve({ records: [], total: 0 });
    await settle();

    expect(panel.attributes('aria-busy')).toBe('false');
    expect(wrapper.get('#roles-table-state').text()).toContain('暂无角色数据');
  });

  it('顶部操作保持原有权限配置跳转和新增角色弹窗入口', async () => {
    wrapper = mountPage();
    await settle();

    const actions = wrapper.findAll('.action-group button');
    await actions[1].trigger('click');
    expect(routerPush).toHaveBeenCalledWith('/system/permission');

    await actions[2].trigger('click');
    await nextTick();
    expect(wrapper.find('.dialog-stub').exists()).toBe(true);
    expect(createRole).not.toHaveBeenCalled();
  });

  it('重置筛选仍按既有列表请求契约重新查询', async () => {
    wrapper = mountPage();
    await settle();
    wrapper.vm.filters.keyword = '管理员';
    wrapper.vm.filters.recordStatus = 1;

    const reset = wrapper.findAll('button').find(button => button.text() === '重置');
    await reset.trigger('click');
    await settle();

    expect(listRoles).toHaveBeenLastCalledWith({
      pageNo: 1,
      pageSize: 20,
      keyword: undefined,
      recordStatus: undefined
    });
  });
});
