// @vitest-environment happy-dom
import { describe, it, expect, vi, beforeAll, afterAll, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick, reactive } from 'vue';
import { createPinia, setActivePinia } from 'pinia';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const routeRef = reactive({
  path: '/workspace',
  fullPath: '/workspace',
  name: 'Workspace',
  meta: { title: '工作台' },
  matched: []
});
const routerMock = { push: vi.fn().mockResolvedValue(undefined) };
const menuTitles = reactive({});
const scrollIntoViewMock = vi.fn();
let originalScrollIntoViewDescriptor;

vi.mock('vue-router', () => ({
  useRoute: () => routeRef,
  useRouter: () => routerMock
}));

vi.mock('@/stores/menu', () => ({
  useMenuStore: () => ({
    resolve: (path) => menuTitles[path] ? { title: menuTitles[path] } : null
  })
}));

import WorkspaceTabs from '../WorkspaceTabs.vue';
import { useWorkspaceTabsStore } from '@/stores/workspaceTabs';

function visit(route) {
  Object.assign(routeRef, {
    path: route.path,
    fullPath: route.fullPath || route.path,
    name: route.name,
    meta: route.meta || {},
    matched: route.matched || []
  });
}

function mountTabs() {
  const wrapper = mount(WorkspaceTabs);
  mountedWrappers.push(wrapper);
  return wrapper;
}

const mountedWrappers = [];

beforeAll(() => {
  originalScrollIntoViewDescriptor = Object.getOwnPropertyDescriptor(
    HTMLElement.prototype,
    'scrollIntoView'
  );
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
    configurable: true,
    value: scrollIntoViewMock
  });
});

afterAll(() => {
  if (originalScrollIntoViewDescriptor) {
    Object.defineProperty(
      HTMLElement.prototype,
      'scrollIntoView',
      originalScrollIntoViewDescriptor
    );
  } else {
    delete HTMLElement.prototype.scrollIntoView;
  }
});

beforeEach(() => {
  setActivePinia(createPinia());
  routerMock.push.mockClear();
  scrollIntoViewMock.mockClear();
  for (const path of Object.keys(menuTitles)) delete menuTitles[path];
  visit({ path: '/workspace', name: 'Workspace', meta: { title: '工作台' } });
});

afterEach(() => {
  mountedWrappers.splice(0).forEach((wrapper) => wrapper.unmount());
});

describe('WorkspaceTabs.vue', () => {
  it('以工作台作为首个固定页签且不可关闭', () => {
    const wrapper = mountTabs();

    expect(wrapper.find('nav.workspace-tabs').attributes('aria-label')).toBe('工作区页签');
    expect(wrapper.findAll('[aria-current="page"]')).toHaveLength(1);
    expect(wrapper.find('[aria-current="page"]').text()).toContain('工作台');
    const tabs = wrapper.findAll('[data-tab-key]');
    expect(tabs).toHaveLength(1);
    expect(tabs[0].attributes('data-tab-key')).toBe('/workspace');
    expect(tabs[0].text()).toContain('工作台');
    expect(tabs[0].find('.workspace-tabs__close').exists()).toBe(false);
  });

  it('进入 DefaultLayout 下业务路由时自动记录，标题优先使用 route.meta.title', async () => {
    const wrapper = mountTabs();

    visit({ path: '/report/free', name: 'ReportFree', meta: { title: '动态报表' } });
    await nextTick();

    const tab = wrapper.find('[data-tab-key="/report/free"]');
    expect(tab.exists()).toBe(true);
    expect(tab.text()).toContain('动态报表');
    expect(tab.find('.workspace-tabs__close').exists()).toBe(true);
  });

  it('菜单已加载时优先使用 DB 菜单名，保持与侧边栏和面包屑同源', async () => {
    const wrapper = mountTabs();
    menuTitles['/report/free'] = '自由报表（菜单配置）';

    visit({ path: '/report/free', name: 'ReportFree', meta: { title: '静态路由标题' } });
    await nextTick();

    expect(wrapper.find('[data-tab-key="/report/free"]').text())
      .toContain('自由报表（菜单配置）');
  });

  it('路由切换后将当前页签滚入可视区，避免溢出时选中页签不可见', async () => {
    const wrapper = mountTabs();
    await nextTick();
    scrollIntoViewMock.mockClear();

    visit({ path: '/report/free', name: 'ReportFree', meta: { title: '数据公式' } });
    await nextTick();
    await nextTick();

    const activeTab = wrapper.find('[data-tab-key="/report/free"]');
    expect(scrollIntoViewMock).toHaveBeenCalledWith({ block: 'nearest', inline: 'nearest' });
    expect(scrollIntoViewMock.mock.instances.at(-1)).toBe(activeTab.element);
  });

  it('点击页签切换路由，关闭当前页签后跳转到左侧相邻页签', async () => {
    const wrapper = mountTabs();

    visit({ path: '/perf/metrics', name: 'PerfMetrics', meta: { title: '指标库' } });
    await nextTick();
    visit({ path: '/system/users', name: 'SysUsers', meta: { title: '用户管理' } });
    await nextTick();

    const metricsLabel = wrapper.find('[data-tab-key="/perf/metrics"] .workspace-tabs__label');
    const metricsFocus = vi.spyOn(metricsLabel.element, 'focus');

    await wrapper.find('[data-tab-key="/perf/metrics"] .workspace-tabs__label').trigger('click');
    expect(routerMock.push).toHaveBeenCalledWith('/perf/metrics');

    routerMock.push.mockClear();
    await wrapper.find('[data-tab-key="/system/users"] .workspace-tabs__close').trigger('click');
    await nextTick();
    expect(routerMock.push).toHaveBeenCalledWith('/perf/metrics');
    expect(metricsFocus).toHaveBeenCalledTimes(1);
    expect(wrapper.find('[data-tab-key="/system/users"]').exists()).toBe(false);
  });

  it('关闭后台非当前页签后保留当前路由，并把焦点交给当前页签', async () => {
    const wrapper = mountTabs();

    visit({ path: '/perf/metrics', name: 'PerfMetrics', meta: { title: '指标库' } });
    await nextTick();
    visit({ path: '/system/users', name: 'SysUsers', meta: { title: '用户管理' } });
    await nextTick();

    const currentLabel = wrapper.find('[data-tab-key="/system/users"] .workspace-tabs__label');
    const currentFocus = vi.spyOn(currentLabel.element, 'focus');
    routerMock.push.mockClear();

    await wrapper.find('[data-tab-key="/perf/metrics"] .workspace-tabs__close').trigger('click');
    await nextTick();

    expect(routerMock.push).not.toHaveBeenCalled();
    expect(currentFocus).toHaveBeenCalledTimes(1);
    expect(wrapper.find('[data-tab-key="/perf/metrics"]').exists()).toBe(false);
  });

  it('页签切换保留记录时的完整 fullPath 与查询参数，并且当前项使用页面导航语义', async () => {
    const wrapper = mountTabs();
    visit({
      path: '/report/free',
      fullPath: '/report/free?period=2026-08&orgCode=B001',
      name: 'ReportFree',
      meta: { title: '动态报表' }
    });
    await nextTick();

    const active = wrapper.find('[data-tab-key="/report/free?period=2026-08&orgCode=B001"] .workspace-tabs__label');
    expect(active.attributes('aria-current')).toBe('page');
    expect(wrapper.findAll('[aria-current="page"]')).toHaveLength(1);

    visit({ path: '/workspace', name: 'Workspace', meta: { title: '工作台' } });
    await nextTick();
    await wrapper.find('[data-tab-key="/report/free?period=2026-08&orgCode=B001"] .workspace-tabs__label').trigger('click');
    expect(routerMock.push).toHaveBeenCalledWith('/report/free?period=2026-08&orgCode=B001');
  });

  it('固定页签点击可回到工作台，关闭固定页签不会触发导航', async () => {
    const wrapper = mountTabs();

    visit({ path: '/perf/metrics', name: 'PerfMetrics', meta: { title: '指标库' } });
    await nextTick();
    routerMock.push.mockClear();

    await wrapper.find('[data-tab-key="/workspace"] .workspace-tabs__label').trigger('click');
    expect(routerMock.push).toHaveBeenCalledWith('/workspace');
    expect(wrapper.find('[data-tab-key="/workspace"] .workspace-tabs__close').exists()).toBe(false);
  });

  it('支持用左右方向键在路由页签间移动焦点，并用 Home/End 定位首尾页签', async () => {
    const wrapper = mountTabs();

    visit({ path: '/perf/metrics', name: 'PerfMetrics', meta: { title: '指标库' } });
    await nextTick();
    visit({ path: '/system/users', name: 'SysUsers', meta: { title: '用户管理' } });
    await nextTick();

    const workspaceLabel = wrapper.find('[data-tab-key="/workspace"] .workspace-tabs__label');
    const metricsLabel = wrapper.find('[data-tab-key="/perf/metrics"] .workspace-tabs__label');
    const usersLabel = wrapper.find('[data-tab-key="/system/users"] .workspace-tabs__label');
    const metricsFocus = vi.spyOn(metricsLabel.element, 'focus');
    const workspaceFocus = vi.spyOn(workspaceLabel.element, 'focus');
    const usersFocus = vi.spyOn(usersLabel.element, 'focus');

    await workspaceLabel.trigger('keydown', { key: 'ArrowRight' });
    expect(metricsFocus).toHaveBeenCalledTimes(1);
    await metricsLabel.trigger('keydown', { key: 'ArrowRight' });
    expect(usersFocus).toHaveBeenCalledTimes(1);
    await usersLabel.trigger('keydown', { key: 'ArrowLeft' });
    expect(metricsFocus).toHaveBeenCalledTimes(2);
    await usersLabel.trigger('keydown', { key: 'Home' });
    expect(workspaceFocus).toHaveBeenCalledTimes(1);
    await workspaceLabel.trigger('keydown', { key: 'End' });
    expect(usersFocus).toHaveBeenCalledTimes(2);
  });

  it('四类页签导航键在首尾也阻止默认滚动并阻断 window 快捷键冒泡', async () => {
    const wrapper = mountTabs();
    visit({ path: '/perf/metrics', name: 'PerfMetrics', meta: { title: '指标库' } });
    await nextTick();
    visit({ path: '/system/users', name: 'SysUsers', meta: { title: '用户管理' } });
    await nextTick();

    const firstLabel = wrapper.find('[data-tab-key="/workspace"] .workspace-tabs__label');
    const lastLabel = wrapper.find('[data-tab-key="/system/users"] .workspace-tabs__label');
    const windowKeydown = vi.fn();
    window.addEventListener('keydown', windowKeydown);

    const dispatch = (label, key) => {
      const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
      label.element.dispatchEvent(event);
      return event;
    };

    const firstBoundaryEvents = [
      dispatch(firstLabel, 'ArrowLeft'),
      dispatch(firstLabel, 'Home')
    ];
    const lastBoundaryEvents = [
      dispatch(lastLabel, 'ArrowRight'),
      dispatch(lastLabel, 'End')
    ];

    expect(firstBoundaryEvents.every((event) => event.defaultPrevented)).toBe(true);
    expect(lastBoundaryEvents.every((event) => event.defaultPrevented)).toBe(true);
    expect(windowKeydown).not.toHaveBeenCalled();
    window.removeEventListener('keydown', windowKeydown);
  });

  it('页签嵌入 Header 后横向伸展但不再占独立行，标题和关闭按钮均保持 40px 命中高度', () => {
    const componentSource = readFileSync(resolve(process.cwd(), 'src/components/WorkspaceTabs.vue'), 'utf8');
    const tokenSource = readFileSync(resolve(process.cwd(), 'src/styles/tokens.scss'), 'utf8');

    expect(tokenSource).toMatch(/--layout-workspace-tabs-height:\s*48px;/);
    expect(componentSource).toContain('height: var(--layout-workspace-tabs-height);');
    expect(componentSource).toContain('flex: 1 1 0;');
    expect(componentSource).not.toContain('flex: 0 0 var(--layout-workspace-tabs-height);');
    expect(componentSource).not.toContain('--workspace-tabs-height');
    expect(componentSource).toMatch(/\.workspace-tabs__tab\s*\{[\s\S]*?min-width:\s*112px;[\s\S]*?max-width:\s*200px;[\s\S]*?height:\s*40px;/);
    expect(componentSource).toContain('gap: var(--space-2);');
    expect(componentSource).toContain('font-size: 14px;');
    expect(componentSource).toMatch(/\.workspace-tabs__label\s*\{[\s\S]*?height:\s*40px;/);
    expect(componentSource).toMatch(/\.workspace-tabs__close\s*\{[\s\S]*?width:\s*40px;[\s\S]*?height:\s*40px;/);
  });
});

describe('workspaceTabs store', () => {
  it('同一个 fullPath 只保留一个页签，并安全回退到 route.name/path 作为标题', () => {
    const store = useWorkspaceTabsStore();

    store.record({ path: '/alpha', fullPath: '/alpha?x=1', name: 'Alpha', meta: {} });
    store.record({ path: '/alpha', fullPath: '/alpha?x=1', name: 'Alpha', meta: {} });
    store.record({ path: '/beta', fullPath: '/beta', name: undefined, meta: {} });

    expect(store.tabs).toHaveLength(3);
    expect(store.tabs[1].title).toBe('Alpha');
    expect(store.tabs[2].title).toBe('/beta');
    expect(store.tabs[1].closable).toBe(true);
  });

  it('关闭动态页签返回相邻页签，固定页签始终保留在首位', () => {
    const store = useWorkspaceTabsStore();
    store.record({ path: '/a', fullPath: '/a', name: 'A', meta: { title: 'A' } });
    store.record({ path: '/b', fullPath: '/b', name: 'B', meta: { title: 'B' } });

    expect(store.close('/b').fullPath).toBe('/a');
    expect(store.close('/workspace')).toBeNull();
    expect(store.tabs.map((tab) => tab.fullPath)).toEqual(['/workspace', '/a']);
  });
});
