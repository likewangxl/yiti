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
  it('提供真实的侧栏切换按钮，并把展开状态与 ARIA 同步', async () => {
    const expanded = mountHeader();
    await flushPromises();

    const expandedToggle = expanded.find('[data-testid="sidebar-toggle"]');
    expect(expandedToggle.exists()).toBe(true);
    expect(expandedToggle.element.tagName).toBe('BUTTON');
    expect(expandedToggle.attributes('aria-controls')).toBe('app-sidebar');
    expect(expandedToggle.attributes('aria-expanded')).toBe('true');
    await expandedToggle.trigger('click');
    expect(expanded.emitted('toggle-sidebar')).toHaveLength(1);

    expanded.unmount();
    wrapper = undefined;
    const collapsed = mountHeader({ sidebarCollapsed: true });
    await flushPromises();
    expect(collapsed.find('[data-testid="sidebar-toggle"]').attributes('aria-expanded')).toBe('false');
  });

  it('不渲染没有业务逻辑的顶栏搜索入口或占位控件', async () => {
    const header = mountHeader();
    await flushPromises();

    expect(header.find('.search').exists()).toBe(false);
    expect(header.find('[data-placeholder="搜索客户 / 线索 / 任务编号 / 报表..."]').exists()).toBe(false);
    expect(header.text()).not.toContain('搜索客户');
  });
});
