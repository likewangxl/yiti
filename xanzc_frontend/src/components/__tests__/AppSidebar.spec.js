// @vitest-environment happy-dom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick, reactive, ref } from 'vue';

const { errorMessage } = vi.hoisted(() => ({ errorMessage: vi.fn() }));
vi.mock('element-plus', () => ({ ElMessage: { error: errorMessage } }));

const routeRef = reactive({ path: '/customer/leads' });
const menuTree = ref([]);
const load = vi.fn().mockResolvedValue(undefined);
const routerLinkNavigate = vi.fn();

vi.mock('vue-router', () => ({ useRoute: () => routeRef }));
vi.mock('@/stores/menu', () => ({
  // Pinia setup store 对外会解包 ref；测试 mock 保持同一读写形态。
  useMenuStore: () => ({
    get tree() { return menuTree.value; },
    get loading() { return false; },
    load
  })
}));

import AppSidebar from '../AppSidebar.vue';

const RouterLinkStub = {
  name: 'RouterLink',
  props: ['to'],
  template: '<a :href="to" @click="navigate"><slot /></a>',
  methods: {
    navigate(event) {
      if (!event.defaultPrevented) routerLinkNavigate(this.to);
    }
  }
};

function buildTree(groupName = '客户营销') {
  return [
    { resourceId: 'M_WORKSPACE', resourceUrl: '/workspace', menuName: '工作台', children: [] },
    {
      resourceId: 'M_CUSTOMER',
      resourceUrl: '/customer',
      menuName: groupName,
      children: [
        { resourceId: 'M_CUSTOMER_LEADS', resourceUrl: '/customer/leads', menuName: '线索管理', children: [] },
        { resourceId: 'M_CUSTOMER_POOL', resourceUrl: '/customer/pool', menuName: '客户池', children: [] }
      ]
    },
    { resourceId: 'M_SCREEN_DESIGNER', resourceUrl: '/screen-admin/designer', menuName: '大屏设计器', children: [] }
  ];
}

let wrapper;
function mountSidebar(props = {}) {
  wrapper = mount(AppSidebar, {
    props,
    global: { stubs: { RouterLink: RouterLinkStub }, directives: { loading: {} } }
  });
  return wrapper;
}

beforeEach(() => {
  vi.clearAllMocks();
  load.mockClear();
  routeRef.path = '/customer/leads';
  menuTree.value = buildTree();
});
afterEach(() => {
  wrapper?.unmount();
  if (typeof window.open?.mockRestore === 'function') window.open.mockRestore();
});

describe('AppSidebar 主导航', () => {
  it('Logo 行右侧常驻折叠按钮，并按展开/折叠两态同步 ARIA 与可读名称', async () => {
    const sidebar = mountSidebar();
    const logo = sidebar.find('.logo');
    const toggle = logo.find('[data-testid="sidebar-toggle"]');

    expect(toggle.exists()).toBe(true);
    expect(toggle.element.tagName).toBe('BUTTON');
    expect(toggle.attributes('aria-controls')).toBe('app-sidebar');
    expect(toggle.attributes('aria-expanded')).toBe('true');
    expect(toggle.attributes('aria-label')).toBe('折叠侧边导航');
    await toggle.trigger('click');
    expect(sidebar.emitted('toggle-sidebar')).toHaveLength(1);

    sidebar.unmount();
    wrapper = undefined;
    const collapsed = mountSidebar({ collapsed: true });
    const collapsedToggle = collapsed.find('.logo [data-testid="sidebar-toggle"]');
    expect(collapsedToggle.attributes('aria-expanded')).toBe('false');
    expect(collapsedToggle.attributes('aria-label')).toBe('展开侧边导航');
    expect(collapsed.find('.logo').classes()).toContain('logo--collapsed');
  });

  it('使用侧栏与导航语义，并且当前路由只标记一个菜单项', () => {
    const sidebar = mountSidebar();

    expect(sidebar.find('aside#app-sidebar').exists()).toBe(true);
    expect(sidebar.find('nav').attributes('aria-label')).toBe('主导航');
    const current = sidebar.findAll('[aria-current="page"]');
    expect(current).toHaveLength(1);
    expect(current[0].attributes('aria-label')).toBe('线索管理');
  });

  it('分组使用 button 与动态 ARIA，并在同一 resourceId 的菜单树刷新后保留用户折叠选择', async () => {
    const sidebar = mountSidebar();
    const group = sidebar.find('[data-menu-group="M_CUSTOMER"]');

    expect(group.element.tagName).toBe('BUTTON');
    expect(group.attributes('aria-controls')).toBe('sidebar-group-M_CUSTOMER');
    expect(group.attributes('aria-expanded')).toBe('true');

    await group.trigger('click');
    expect(group.attributes('aria-expanded')).toBe('false');
    expect(sidebar.find('#sidebar-group-M_CUSTOMER').element.style.display).toBe('none');

    menuTree.value = buildTree('客户经营');
    await nextTick();
    expect(sidebar.find('[data-menu-group="M_CUSTOMER"]').attributes('aria-expanded')).toBe('false');
  });

  it('折叠状态将打开的二级菜单渲染到侧栏滚动容器之外，保留可访问入口', async () => {
    const sidebar = mountSidebar({ collapsed: true });

    expect(sidebar.find('aside').classes()).toContain('side--collapsed');
    for (const label of ['工作台', '客户营销']) {
      const item = sidebar.find(`[aria-label="${label}"]`);
      expect(item.exists(), `${label} 入口应保留`).toBe(true);
      expect(item.attributes('title')).toBe(label);
    }

    const group = sidebar.find('[data-menu-group="M_CUSTOMER"]');
    await group.trigger('click');
    await nextTick();

    expect(group.attributes('aria-expanded')).toBe('true');
    const flyout = document.body.querySelector('#sidebar-group-M_CUSTOMER');
    expect(flyout).not.toBeNull();
    expect(flyout.closest('#app-sidebar')).toBeNull();
    expect(flyout.getAttribute('role')).toBe('group');

    for (const [label, href] of [['线索管理', '/customer/leads'], ['客户池', '/customer/pool']]) {
      const item = flyout.querySelector(`[aria-label="${label}"]`);
      expect(item, `${label} 入口应显示在浮出菜单中`).not.toBeNull();
      expect(item.tagName).toBe('A');
      expect(item.getAttribute('href')).toBe(href);
      expect(item.getAttribute('title')).toBe(label);
    }
    expect(flyout.querySelector('[aria-current="page"]')?.getAttribute('aria-label')).toBe('线索管理');
  });

  it('点击大屏设计器时阻止当前工作区导航，使用固定命名窗口并聚焦', async () => {
    const focus = vi.fn();
    const open = vi.spyOn(window, 'open').mockReturnValue({ focus });
    const sidebar = mountSidebar();
    const designer = sidebar.find('[aria-label="大屏设计器"]');
    const event = new MouseEvent('click', { bubbles: true, cancelable: true });

    designer.element.dispatchEvent(event);
    await nextTick();

    expect(event.defaultPrevented).toBe(true);
    expect(open).toHaveBeenCalledWith('/#/screen-admin/designer', 'yiti-screen-designer');
    expect(open.mock.calls[0]).toHaveLength(2);
    expect(focus).toHaveBeenCalledTimes(1);
    expect(routerLinkNavigate).not.toHaveBeenCalled();
    expect(errorMessage).not.toHaveBeenCalled();
  });

  it('设计器弹窗被阻止时给出明确错误且不降级为当前页打开', async () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    const sidebar = mountSidebar();
    const designer = sidebar.find('[aria-label="大屏设计器"]');
    const event = new MouseEvent('click', { bubbles: true, cancelable: true });

    designer.element.dispatchEvent(event);
    await nextTick();

    expect(event.defaultPrevented).toBe(true);
    expect(open).toHaveBeenCalledTimes(1);
    expect(errorMessage).toHaveBeenCalledWith('大屏设计器窗口打开失败，请允许浏览器弹出窗口后重试');
    expect(routerLinkNavigate).not.toHaveBeenCalled();
  });

  it('其他菜单保持普通路由链接行为，不调用新窗口', async () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    const sidebar = mountSidebar();
    const workspace = sidebar.find('[aria-label="工作台"]');
    const event = new MouseEvent('click', { bubbles: true, cancelable: true });

    workspace.element.dispatchEvent(event);
    await nextTick();

    expect(event.defaultPrevented).toBe(false);
    expect(open).not.toHaveBeenCalled();
    expect(routerLinkNavigate).toHaveBeenCalledWith('/workspace');
    expect(errorMessage).not.toHaveBeenCalled();
  });
});
