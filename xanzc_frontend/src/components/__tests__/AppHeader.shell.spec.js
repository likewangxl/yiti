// @vitest-environment happy-dom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}));
vi.mock('@/api/auth', () => ({ logout: vi.fn().mockResolvedValue(undefined) }));
vi.mock('@/api/workspace', () => ({ getUnreadCount: vi.fn().mockResolvedValue(0) }));
vi.mock('@/api/users', () => ({ changeMyPassword: vi.fn().mockResolvedValue(undefined) }));

import AppHeader from '../AppHeader.vue';

const passthrough = (name) => ({ name, template: '<div><slot /></div>' });
const stubs = {
  WorkspaceTabs: { name: 'WorkspaceTabs', template: '<nav class="workspace-tabs-stub" aria-label="工作区页签" />' },
  'el-dropdown': { name: 'ElDropdown', template: '<div><slot /><slot name="dropdown" /></div>' },
  'el-dropdown-menu': passthrough('ElDropdownMenu'),
  'el-dropdown-item': passthrough('ElDropdownItem'),
  'el-badge': passthrough('ElBadge'),
  'el-dialog': passthrough('ElDialog'),
  'el-form': passthrough('ElForm'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': {
    name: 'ElInput',
    props: ['placeholder'],
    template: '<input class="input-stub" :data-placeholder="placeholder" />'
  },
  'el-button': { name: 'ElButton', template: '<button><slot /></button>' }
};

let wrapper;

function mountHeader(props = {}) {
  wrapper = mount(AppHeader, {
    props,
    global: { plugins: [createPinia()], stubs, mocks: { $router: { push: vi.fn() } } }
  });
  return wrapper;
}

beforeEach(() => setActivePinia(createPinia()));
afterEach(() => wrapper?.unmount());

describe('AppHeader 导航壳层', () => {
  it('在原汉堡区域只挂载一份工作区页签，并让账户操作保持在其右侧', async () => {
    const header = mountHeader();
    await flushPromises();

    expect(header.find('[data-testid="sidebar-toggle"]').exists()).toBe(false);
    expect(header.findAll('.workspace-tabs-stub')).toHaveLength(1);
    const children = Array.from(header.find('header.hdr').element.children);
    expect(children.indexOf(header.find('.workspace-tabs-stub').element))
      .toBeLessThan(children.indexOf(header.find('.account-pick').element.parentElement));
  });

  it('不渲染没有业务逻辑的顶栏搜索入口或占位控件', async () => {
    const header = mountHeader();
    await flushPromises();

    expect(header.find('.search').exists()).toBe(false);
    expect(header.find('[data-placeholder="搜索客户 / 线索 / 任务编号 / 报表..."]').exists()).toBe(false);
    expect(header.text()).not.toContain('搜索客户');
  });
});
