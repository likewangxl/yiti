// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() } }));
vi.mock('@/api/workspace', () => ({ listNotifications: vi.fn(), markAllRead: vi.fn() }));

import { listNotifications, markAllRead } from '@/api/workspace';
import NotificationList from '../NotificationList.vue';

const passthrough = (name, template = '<div><slot /></div>') => ({ name, template });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  PageTitle: { name: 'PageTitle', inheritAttrs: false, template: '<h1 v-bind="$attrs">通知<slot /></h1>' },
  'el-button': {
    name: 'ElButton', inheritAttrs: false, props: { disabled: Boolean, loading: Boolean }, emits: ['click'],
    template: '<button v-bind="$attrs" :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-table': {
    name: 'ElTable', inheritAttrs: false, props: { data: Array, emptyText: String },
    template: '<div class="table-stub" v-bind="$attrs"><slot /></div>'
  },
  'el-table-column': empty('ElTableColumn'),
  'el-pagination': passthrough('ElPagination'),
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
  return mount(NotificationList, {
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
  });
}

let wrapper;

beforeEach(() => {
  vi.clearAllMocks();
  listNotifications.mockResolvedValue({ records: [{ id: 8, content: '审批提醒', isRead: false }], total: 1 });
  markAllRead.mockResolvedValue({ ok: true });
});

afterEach(() => wrapper?.unmount());

describe('NotificationList.vue 工作台通知', () => {
  it('加载和空数据分别有稳定的列表状态文本', async () => {
    const request = deferred();
    listNotifications.mockReturnValueOnce(request.promise);
    wrapper = mountPage();
    await nextTick();

    const panel = wrapper.get('[aria-label="通知列表"]');
    expect(panel.attributes('aria-busy')).toBe('true');
    expect(wrapper.get('#notification-list-state').text()).toContain('通知列表加载中');

    request.resolve({ records: [], total: 0 });
    await settle();

    expect(panel.attributes('aria-busy')).toBe('false');
    expect(wrapper.get('#notification-list-state').text()).toContain('暂无通知数据');
  });

  it('全部标记已读沿用原接口且请求进行中不重复提交', async () => {
    const request = deferred();
    markAllRead.mockReturnValueOnce(request.promise);
    wrapper = mountPage();
    await settle();

    const first = wrapper.vm.doMarkAllRead();
    const second = wrapper.vm.doMarkAllRead();
    expect(markAllRead).toHaveBeenCalledTimes(1);
    expect(markAllRead).toHaveBeenCalledWith();

    request.resolve({ ok: true });
    await Promise.all([first, second]);
    await settle();

    expect(wrapper.vm.rows.every((row) => row.isRead)).toBe(true);
  });
});
