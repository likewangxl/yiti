// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() } }));
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ isSystemAdmin: true }) }));
vi.mock('@/api/announcement', () => ({
  listAnnouncementsAdmin: vi.fn(),
  createAnnouncement: vi.fn(),
  uploadAnnouncementFile: vi.fn(),
  togglePinAnnouncement: vi.fn(),
  deleteAnnouncement: vi.fn()
}));

import { createAnnouncement, listAnnouncementsAdmin } from '@/api/announcement';
import Announcements from '../Announcements.vue';

const passthrough = (name, template = '<div><slot /><slot name="footer" /><slot name="tip" /></div>') => ({ name, template });
const empty = (name) => ({ name, template: '<div />' });
const formStub = {
  name: 'ElForm',
  inheritAttrs: false,
  methods: { validate: () => Promise.resolve() },
  template: '<form v-bind="$attrs"><slot /></form>'
};
const stubs = {
  PageTitle: { name: 'PageTitle', inheritAttrs: false, template: '<h1 v-bind="$attrs">公告管理<slot /></h1>' },
  'el-form': formStub,
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
  'el-pagination': passthrough('ElPagination'),
  'el-tag': passthrough('ElTag'),
  'el-dialog': passthrough('ElDialog'),
  'el-upload': passthrough('ElUpload')
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
  return mount(Announcements, {
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } }
  });
}

let wrapper;

beforeEach(() => {
  vi.clearAllMocks();
  listAnnouncementsAdmin.mockResolvedValue({ records: [{ id: 2, title: '运营通知' }], total: 1 });
  createAnnouncement.mockResolvedValue(2);
});

afterEach(() => wrapper?.unmount());

describe('Announcements.vue 公告管理', () => {
  it('管理员列表加载中和空结果均保留可读状态', async () => {
    const request = deferred();
    listAnnouncementsAdmin.mockReturnValueOnce(request.promise);
    wrapper = mountPage();
    await nextTick();

    const panel = wrapper.get('[aria-label="公告管理列表"]');
    expect(panel.attributes('aria-busy')).toBe('true');
    expect(wrapper.get('#admin-announcement-list-state').text()).toContain('公告管理列表加载中');

    request.resolve({ records: [], total: 0 });
    await settle();
    expect(panel.attributes('aria-busy')).toBe('false');
    expect(wrapper.get('#admin-announcement-list-state').text()).toContain('暂无公告数据');
  });

  it('发布公告保持创建接口负载，提交期间不重复创建', async () => {
    const request = deferred();
    createAnnouncement.mockReturnValueOnce(request.promise);
    wrapper = mountPage();
    await settle();
    wrapper.vm.openCreate();
    wrapper.vm.createDlg.form.title = '分行运营提示';
    wrapper.vm.createDlg.form.content = '请及时处理待办事项。';

    const first = wrapper.vm.doCreate();
    const second = wrapper.vm.doCreate();
    await nextTick();
    expect(createAnnouncement).toHaveBeenCalledTimes(1);
    expect(createAnnouncement).toHaveBeenCalledWith({ title: '分行运营提示', content: '请及时处理待办事项。' });

    request.resolve(9);
    await Promise.all([first, second]);
  });
});
