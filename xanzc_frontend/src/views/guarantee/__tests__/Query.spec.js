// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

const { confirm } = vi.hoisted(() => ({ confirm: vi.fn() }));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm }
}));
vi.mock('@/api/guarantee', () => ({
  listGuarantees: vi.fn(),
  getGuarantee: vi.fn(),
  createGuarantee: vi.fn(),
  updateGuarantee: vi.fn(),
  batchDeleteGuarantees: vi.fn(),
  exportGuarantees: vi.fn()
}));

import {
  batchDeleteGuarantees,
  createGuarantee,
  exportGuarantees,
  getGuarantee,
  listGuarantees,
  updateGuarantee
} from '@/api/guarantee';
import Query from '../Query.vue';

const passthrough = (name) => ({
  name,
  inheritAttrs: false,
  template: '<div v-bind="$attrs"><slot /><slot name="header" /><slot name="footer" /></div>'
});
const stubs = {
  PageTitle: {
    name: 'PageTitle',
    inheritAttrs: false,
    template: '<h1 class="page-title" v-bind="$attrs">担保信息查询<slot /></h1>'
  },
  'el-button': {
    name: 'ElButton',
    props: { disabled: Boolean, loading: Boolean },
    emits: ['click'],
    template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-form': passthrough('ElForm'),
  'el-form-item': { name: 'ElFormItem', props: ['label'], template: '<label><span>{{ label }}</span><slot /></label>' },
  'el-input': {
    name: 'ElInput',
    inheritAttrs: false,
    props: { modelValue: [String, Number], placeholder: String },
    emits: ['update:modelValue'],
    template: '<input v-bind="$attrs" :value="modelValue" :placeholder="placeholder" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-date-picker': passthrough('ElDatePicker'),
  'el-table': {
    name: 'ElTable',
    inheritAttrs: false,
    props: { data: { type: Array, default: () => [] } },
    template: '<div class="table-stub" v-bind="$attrs"><slot /></div>'
  },
  'el-table-column': { name: 'ElTableColumn', template: '<div />' },
  'el-pagination': passthrough('ElPagination'),
  'el-dialog': {
    name: 'ElDialog',
    props: { modelValue: Boolean, title: String },
    template: '<div v-if="modelValue" class="dialog-stub"><slot /><slot name="footer" /></div>'
  }
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
  return mount(Query, {
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
  });
}

let wrapper;

beforeEach(() => {
  vi.clearAllMocks();
  listGuarantees.mockResolvedValue({ records: [], total: 0 });
  getGuarantee.mockResolvedValue({});
  createGuarantee.mockResolvedValue({ ok: true });
  updateGuarantee.mockResolvedValue({ ok: true });
  batchDeleteGuarantees.mockResolvedValue({ ok: true });
  exportGuarantees.mockResolvedValue(undefined);
  confirm.mockResolvedValue(undefined);
});

afterEach(() => wrapper?.unmount());

describe('担保信息查询工作区', () => {
  it('加载中保留列表状态，成功空结果显示可辨识空态', async () => {
    const request = deferred();
    listGuarantees.mockReturnValueOnce(request.promise);
    wrapper = mountPage();
    await nextTick();

    const panel = wrapper.get('[aria-label="担保信息列表"]');
    expect(panel.attributes('aria-busy')).toBe('true');
    expect(wrapper.get('#guarantee-table-state').text()).toContain('列表加载中');

    request.resolve({ records: [], total: 0 });
    await settle();

    expect(panel.attributes('aria-busy')).toBe('false');
    expect(wrapper.get('#guarantee-table-state').text()).toContain('暂无担保信息');
  });

  it('未选择记录时批量删除按钮禁用并明确选择数量', async () => {
    wrapper = mountPage();
    await settle();

    const deleteButton = wrapper.findAll('button').find((button) => button.text().includes('删除'));
    expect(deleteButton.attributes('disabled')).toBeDefined();
    expect(deleteButton.text()).toContain('已选 0 条');
  });

  it('取消批量删除确认不会调用删除接口', async () => {
    wrapper = mountPage();
    await settle();
    wrapper.vm.onSelectionChange([{ id: 7, clientName: '测试客户' }]);
    confirm.mockRejectedValueOnce(new Error('cancelled'));

    await wrapper.vm.onBatchDelete();
    await settle();

    expect(confirm).toHaveBeenCalled();
    expect(batchDeleteGuarantees).not.toHaveBeenCalled();
  });
});
