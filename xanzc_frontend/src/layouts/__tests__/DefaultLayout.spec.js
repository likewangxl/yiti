// @vitest-environment happy-dom
// 全出血(full-bleed)布局回归——修复大屏设计器高度错位:DesignerV2 原先写死
// calc(100vh - 60px),而 DefaultLayout 的壳层高度由 header、工作区页签和内容区共同决定。
// 设计器超高会导致整页滚动条。修复契约分两半:
// ①设计器路由声明 meta.fullBleed;②DefaultLayout 对 fullBleed 路由去掉内容区 padding,
// 设计器自身高度改为撑满父容器。本文件锁这两半契约。
import { afterEach, describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick, reactive } from 'vue';

const routeState = reactive({ path: '/x', fullPath: '/x', meta: {} });

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router');
  return { ...actual, useRoute: () => routeState };
});

import DefaultLayout from '../DefaultLayout.vue';

const stubs = {
  AppSidebar: {
    name: 'AppSidebar',
    props: ['collapsed'],
    emits: ['toggle-sidebar'],
    template: '<aside class="sidebar-stub" :data-collapsed="String(collapsed)"><button class="sidebar-toggle-stub" @click="$emit(\'toggle-sidebar\')" /></aside>'
  },
  AppHeader: {
    name: 'AppHeader',
    template: '<header class="header-stub" />'
  },
  AppBreadcrumb: { template: '<div class="breadcrumb-stub" />' },
  WorkspaceTabs: { template: '<div class="workspace-tabs-stub" />' },
  'router-view': true
};

const mountedWrappers = [];
function mountWithMeta(meta, options = {}) {
  routeState.path = '/x';
  routeState.fullPath = '/x';
  routeState.meta = meta;
  const wrapper = mount(DefaultLayout, {
    attachTo: options.attachTo ? document.body : undefined,
    global: { stubs, mocks: { $route: routeState } }
  });
  mountedWrappers.push(wrapper);
  return wrapper;
}

function createFocusProbe() {
  const probe = document.createElement('button');
  probe.type = 'button';
  probe.className = 'layout-focus-probe';
  document.body.appendChild(probe);
  probe.focus();
  return probe;
}

async function flushRouteFocus() {
  await nextTick();
  await nextTick();
}

afterEach(() => {
  mountedWrappers.splice(0).forEach(wrapper => wrapper.unmount());
  document.querySelectorAll('.layout-focus-probe').forEach(probe => probe.remove());
});

describe('DefaultLayout.vue full-bleed 内容区', () => {
  it('全局壳层不挂载面包屑或独立页签行，内容区直接跟在内嵌页签的 Header 后', () => {
    const wrapper = mountWithMeta({ title: '工作台' });
    expect(wrapper.find('.breadcrumb-stub').exists()).toBe(false);
    expect(wrapper.find('.workspace-tabs-stub').exists()).toBe(false);
    expect(wrapper.find('.content').element.previousElementSibling.className)
      .toBe('header-stub');
  });

  it('路由声明 meta.fullBleed 时内容区带 content--full(去 padding)', () => {
    const wrapper = mountWithMeta({ fullBleed: true });
    expect(wrapper.find('.content').classes()).toContain('content--full');
  });

  it('普通路由内容区不带 content--full(既有页面 padding 不受影响)', () => {
    const wrapper = mountWithMeta({ title: '工作台' });
    expect(wrapper.find('.content').classes()).not.toContain('content--full');
  });
});

describe('DefaultLayout.vue 侧栏壳层状态', () => {
  it('默认展开，由 Sidebar 常驻按钮切换并同步 220px/64px 壳层状态', async () => {
    const wrapper = mountWithMeta({ title: '工作台' });

    expect(wrapper.find('.layout').classes()).not.toContain('layout--sidebar-collapsed');
    expect(wrapper.find('.sidebar-stub').attributes('data-collapsed')).toBe('false');

    await wrapper.find('.sidebar-toggle-stub').trigger('click');
    expect(wrapper.find('.layout').classes()).toContain('layout--sidebar-collapsed');
    expect(wrapper.find('.sidebar-stub').attributes('data-collapsed')).toBe('true');
  });

  it('侧栏折叠态仅属于当前 Layout 实例，不会跨重新挂载持久化', async () => {
    const first = mountWithMeta({ title: '工作台' });
    await first.find('.sidebar-toggle-stub').trigger('click');
    expect(first.find('.sidebar-stub').attributes('data-collapsed')).toBe('true');
    first.unmount();

    const fresh = mountWithMeta({ title: '工作台' });
    expect(fresh.find('.sidebar-stub').attributes('data-collapsed')).toBe('false');
  });
});

describe('DefaultLayout.vue 路由焦点管理', () => {
  it('仅提供可聚焦的内容容器，不额外创建 main 地标', () => {
    const wrapper = mountWithMeta({ title: '工作台' });
    const content = wrapper.find('#app-main');

    expect(content.element.tagName).toBe('DIV');
    expect(wrapper.find('main#app-main').exists()).toBe(false);
  });

  it('初始挂载不抢占已有焦点', async () => {
    const probe = createFocusProbe();
    const focusSpy = vi.spyOn(HTMLElement.prototype, 'focus');
    focusSpy.mockClear();

    mountWithMeta({ title: '工作台' }, { attachTo: true });
    await nextTick();

    expect(document.activeElement).toBe(probe);
    expect(focusSpy).not.toHaveBeenCalled();
    focusSpy.mockRestore();
  });

  it('path 或 fullPath 变化后聚焦主内容，并保留全出血与侧栏折叠状态', async () => {
    const probe = createFocusProbe();
    const wrapper = mountWithMeta({ title: '工作台' }, { attachTo: true });
    const content = wrapper.find('#app-main').element;
    const focusSpy = vi.spyOn(content, 'focus');

    await wrapper.find('.sidebar-toggle-stub').trigger('click');
    probe.focus();
    focusSpy.mockClear();
    routeState.path = '/screen/admin/designer';
    routeState.fullPath = '/screen/admin/designer?mode=edit';
    routeState.meta = { fullBleed: true };
    await flushRouteFocus();

    expect(focusSpy).toHaveBeenCalledWith({ preventScroll: true });
    expect(document.activeElement).toBe(content);
    expect(wrapper.find('.content').classes()).toContain('content--full');
    expect(wrapper.find('.sidebar-stub').attributes('data-collapsed')).toBe('true');

    probe.focus();
    focusSpy.mockClear();
    routeState.fullPath = '/screen/admin/designer?mode=preview';
    await flushRouteFocus();

    expect(focusSpy).toHaveBeenCalledWith({ preventScroll: true });
    expect(document.activeElement).toBe(content);
    focusSpy.mockRestore();
  });
});

describe('大屏设计器路由 full-bleed 声明', () => {
  it('ScreenAdminDesigner 路由 meta.fullBleed 为 true', async () => {
    const { default: router } = await import('@/router');
    const route = router.getRoutes().find(r => r.name === 'ScreenAdminDesigner');
    expect(route).toBeTruthy();
    expect(route.meta.fullBleed).toBe(true);
  });
});
