// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

const routerPush = vi.fn();
vi.mock('vue-router', () => ({ useRouter: () => ({ push: routerPush }) }));
vi.mock('@/api/announcement', () => ({ listAnnouncements: vi.fn() }));

import { listAnnouncements } from '@/api/announcement';
import AnnouncementList from '../AnnouncementList.vue';

const passthrough = (name, template = '<div><slot /></div>') => ({ name, template });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  PageTitle: { name: 'PageTitle', inheritAttrs: false, template: '<h1 v-bind="$attrs">公告<slot /></h1>' },
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
  'el-table': {
    name: 'ElTable', inheritAttrs: false, props: { data: Array, emptyText: String },
    template: '<div class="table-stub" v-bind="$attrs"><slot /></div>'
  },
  'el-table-column': empty('ElTableColumn'),
  'el-pagination': passthrough('ElPagination')
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
  return mount(AnnouncementList, {
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
  });
}

let wrapper;

beforeEach(() => {
  vi.clearAllMocks();
  listAnnouncements.mockResolvedValue({ records: [{ id: 7, title: '运营提示' }], total: 1 });
});

afterEach(() => wrapper?.unmount());

describe('AnnouncementList.vue 工作台公告', () => {
  it('加载期间保留公告列表区域，完成空结果后展示可读状态', async () => {
    const request = deferred();
    listAnnouncements.mockReturnValueOnce(request.promise);
    wrapper = mountPage();
    await nextTick();

    const panel = wrapper.get('[aria-label="公告列表"]');
    expect(panel.attributes('aria-busy')).toBe('true');
    expect(wrapper.get('#announcement-list-state').text()).toContain('公告列表加载中');
    expect(listAnnouncements).toHaveBeenCalledWith({ pageNo: 1, pageSize: 20, keyword: undefined });

    request.resolve({ records: [], total: 0 });
    await settle();

    expect(panel.attributes('aria-busy')).toBe('false');
    expect(wrapper.get('#announcement-list-state').text()).toContain('暂无公告数据');
  });

  it('详情入口继续使用原有公告详情路由', async () => {
    wrapper = mountPage();
    await settle();

    wrapper.vm.goDetail({ id: 42 });
    expect(routerPush).toHaveBeenCalledWith('/announcement/42');
  });
});
