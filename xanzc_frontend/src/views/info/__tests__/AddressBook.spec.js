// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }, ElMessageBox: { alert: vi.fn() } }));
vi.mock('@/api/employees', () => ({ pageEmployees: vi.fn(), updateEmployee: vi.fn(), importEmployeesFile: vi.fn() }));
vi.mock('@/api/products', () => ({ supportAvailableProducts: vi.fn() }));
vi.mock('@/api/orgs', () => ({ getOrgTree: vi.fn() }));

import { importEmployeesFile, pageEmployees } from '@/api/employees';
import { supportAvailableProducts } from '@/api/products';
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
  supportAvailableProducts.mockResolvedValue([]);
  getOrgTree.mockResolvedValue([]);
  importEmployeesFile.mockResolvedValue(1);
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

  it('导入沿用原文件接口，并在上传未完成时拒绝重复提交', async () => {
    const request = deferred();
    importEmployeesFile.mockReturnValueOnce(request.promise);
    wrapper = mountPage();
    await settle();

    const file = { raw: new File(['sheet'], 'employees.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }) };
    const first = wrapper.vm.onImportPick(file);
    const second = wrapper.vm.onImportPick(file);
    expect(importEmployeesFile).toHaveBeenCalledTimes(1);
    expect(importEmployeesFile).toHaveBeenCalledWith(file.raw);

    request.resolve(1);
    await Promise.all([first, second]);
  });
});
