// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }, ElMessageBox: { alert: vi.fn() } }));
vi.mock('@/api/employees', () => ({ pageEmployees: vi.fn(), updateMyEmployee: vi.fn() }));
vi.mock('@/api/products', () => ({ listActiveProducts: vi.fn() }));
vi.mock('@/api/orgs', () => ({ getOrgTree: vi.fn() }));
const currentUser = vi.hoisted(() => ({ user: { empId: 'E001', username: 'zhangsan', displayName: '张三' } }));
vi.mock('@/stores/user', () => ({ useUserStore: vi.fn(() => currentUser) }));

import { pageEmployees, updateMyEmployee } from '@/api/employees';
import { listActiveProducts } from '@/api/products';
import { getOrgTree } from '@/api/orgs';
import AddressBook from '../AddressBook.vue';

const passthrough = (name, template = '<div><slot /></div>') => ({ name, template });
const empty = (name) => ({ name, template: '<div />' });
const uploadStub = {
  name: 'ElUpload',
  methods: { clearFiles() {} },
  template: '<div><slot /></div>'
};
const stubs = {
  PageTitle: { name: 'PageTitle', inheritAttrs: false, template: '<h1 v-bind="$attrs">通讯录<slot /></h1>' },
  'el-form': { name: 'ElForm', inheritAttrs: false, template: '<form v-bind="$attrs"><slot /></form>' },
  'el-form-item': passthrough('ElFormItem'),
  'el-input': {
    name: 'ElInput', inheritAttrs: false, props: { modelValue: String }, emits: ['update:modelValue'],
    template: '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-button': {
    name: 'ElButton', inheritAttrs: false, props: { disabled: Boolean, loading: Boolean }, emits: ['click'],
    template: '<button v-bind="$attrs" :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-upload': uploadStub,
  'el-tree-select': passthrough('ElTreeSelect'),
  'el-select': passthrough('ElSelect'),
  'el-option': passthrough('ElOption'),
  'el-checkbox': passthrough('ElCheckbox'),
  'el-checkbox-group': passthrough('ElCheckboxGroup'),
  'el-table': {
    name: 'ElTable', inheritAttrs: false, props: { data: Array, emptyText: String },
    template: '<div class="table-stub" v-bind="$attrs"><slot /></div>'
  },
  'el-table-column': empty('ElTableColumn'),
  'el-pagination': passthrough('ElPagination'),
  'el-drawer': passthrough('ElDrawer'),
  'el-alert': passthrough('ElAlert'),
  'el-descriptions': passthrough('ElDescriptions'),
  'el-descriptions-item': passthrough('ElDescriptionsItem'),
  'el-row': passthrough('ElRow'),
  'el-col': passthrough('ElCol'),
  'el-tag': passthrough('ElTag')
};

function deferred() {
  let resolve;
  const promise = new Promise((res) => { resolve = res; });
  return { promise, resolve };
}

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

function mountPage() {
  return mount(AddressBook, {
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
  });
}

let wrapper;

beforeEach(() => {
  vi.clearAllMocks();
  pageEmployees.mockResolvedValue({ records: [{ empId: 'E001', empName: '张三' }], total: 1 });
  updateMyEmployee.mockResolvedValue({ ok: true });
  listActiveProducts.mockResolvedValue([]);
  getOrgTree.mockResolvedValue([]);
  currentUser.user = { empId: 'E001', username: 'zhangsan', displayName: '张三' };
});

afterEach(() => wrapper?.unmount());

describe('AddressBook.vue 通讯录', () => {
  it('保留员工分页请求契约，并在加载和空数据间更新状态', async () => {
    const request = deferred();
    pageEmployees.mockReturnValueOnce(request.promise);
    wrapper = mountPage();
    await nextTick();

    const panel = wrapper.get('[aria-label="通讯录列表"]');
    expect(panel.attributes('aria-busy')).toBe('true');
    expect(wrapper.get('#address-book-state').text()).toContain('通讯录列表加载中');
    expect(pageEmployees).toHaveBeenCalledWith({ keyword: undefined, orgCode: undefined, position: undefined, pageNo: 1, pageSize: 20 });

    request.resolve({ records: [], total: 0 });
    await settle();
    expect(panel.attributes('aria-busy')).toBe('false');
    expect(wrapper.get('#address-book-state').text()).toContain('暂无员工数据');
  });

  it('删除导入、导出和模板操作，只保留查询列表', async () => {
    wrapper = mountPage();
    await settle();

    expect(wrapper.text()).not.toContain('导入');
    expect(wrapper.text()).not.toContain('导出');
    expect(wrapper.text()).not.toContain('下载模板');
  });

  it('仅允许当前登录员工打开维护入口，其他员工不可编辑', async () => {
    const self = { empId: 'E001', empName: '张三', mobile: '13800000000', email: 'old@example.com', responsibleProductIds: ['P1'] };
    const other = { empId: 'E002', empName: '李四', mobile: '13900000000' };
    pageEmployees.mockResolvedValueOnce({ records: [self, other], total: 2 });
    listActiveProducts.mockResolvedValueOnce([{ id: 'P1', productName: '产品一', status: 'ACTIVE' }]);
    wrapper = mountPage();
    await settle();

    expect(wrapper.vm.canEditRow(self)).toBe(true);
    expect(wrapper.vm.canEditRow(other)).toBe(false);
    wrapper.vm.openEdit(other);
    expect(wrapper.vm.drawer).toBe(false);
    wrapper.vm.openEdit(self);
    expect(wrapper.vm.drawer).toBe(true);
    expect(wrapper.vm.ef).toMatchObject({ mobile: self.mobile, email: self.email, responsibleProductIds: ['P1'] });
  });

  it('自助保存只提交电话、邮箱和负责产品，不提交员工主数据字段', async () => {
    const self = { empId: 'E001', empName: '张三', position: '岗位', selfDesc: '旧描述', mobile: '13800000000', email: 'old@example.com', responsibleProductIds: ['P1'] };
    pageEmployees.mockResolvedValue({ records: [self], total: 1 });
    wrapper = mountPage();
    await settle();
    wrapper.vm.openEdit(self);
    wrapper.vm.ef.mobile = '18600000000';
    wrapper.vm.ef.email = 'new@example.com';
    wrapper.vm.ef.responsibleProductIds = ['P2'];

    await wrapper.vm.save();

    expect(updateMyEmployee).toHaveBeenCalledWith({
      mobile: '18600000000',
      email: 'new@example.com',
      responsibleProductIds: ['P2']
    });
    expect(updateMyEmployee.mock.calls[0][0]).not.toHaveProperty('position');
    expect(updateMyEmployee.mock.calls[0][0]).not.toHaveProperty('selfDesc');
  });

  it('人员主数据保持只读，并移除无权威来源的岗位字段', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/views/info/AddressBook.vue'), 'utf8');
    expect(source).not.toContain('label="岗位"');
    expect(source).not.toContain('filters.position');
    expect(source).not.toContain('v-model="ef.selfDesc"');
    expect(source).not.toMatch(/position:\s*cur\.value\.position/);
  });
});
