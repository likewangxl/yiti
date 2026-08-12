// @vitest-environment happy-dom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick, reactive, ref } from 'vue';

const routeRef = reactive({ path: '/customer/leads' });
const menuTree = ref([]);
const load = vi.fn().mockResolvedValue(undefined);

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
  template: '<a :href="to"><slot /></a>'
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
    }
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
  load.mockClear();
  routeRef.path = '/customer/leads';
  menuTree.value = buildTree();
});
afterEach(() => wrapper?.unmount());

describe('AppSidebar 主导航', () => {
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

  it('折叠状态保留每个已授权入口的可访问名称和原生 tooltip', () => {
    const sidebar = mountSidebar({ collapsed: true });

    expect(sidebar.find('aside').classes()).toContain('side--collapsed');
    for (const label of ['工作台', '客户营销', '线索管理', '客户池']) {
      const item = sidebar.find(`[aria-label="${label}"]`);
      expect(item.exists(), `${label} 入口应保留`).toBe(true);
      expect(item.attributes('title')).toBe(label);
    }
  });
});
