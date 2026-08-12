// @vitest-environment happy-dom
// 全出血(full-bleed)布局回归——修复大屏设计器高度错位:DesignerV2 原先写死
// calc(100vh - 60px),但 DefaultLayout 实际是 header 52px + 面包屑 40px + 内容区
// padding 16×2,设计器超高 64px 导致整页滚动条。修复契约分两半:
// ①设计器路由声明 meta.fullBleed;②DefaultLayout 对 fullBleed 路由去掉内容区 padding,
// 设计器自身高度改为撑满父容器。本文件锁这两半契约。
import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import DefaultLayout from '../DefaultLayout.vue';

const stubs = {
  AppSidebar: {
    name: 'AppSidebar',
    props: ['collapsed'],
    template: '<aside class="sidebar-stub" :data-collapsed="String(collapsed)" />'
  },
  AppHeader: {
    name: 'AppHeader',
    props: ['sidebarCollapsed'],
    emits: ['toggle-sidebar'],
    template: '<button class="header-stub" :data-collapsed="String(sidebarCollapsed)" @click="$emit(\'toggle-sidebar\')" />'
  },
  AppBreadcrumb: true,
  WorkspaceTabs: { template: '<div class="workspace-tabs-stub" />' },
  'router-view': true
};

function mountWithMeta(meta) {
  return mount(DefaultLayout, {
    global: { stubs, mocks: { $route: { meta, fullPath: '/x' } } }
  });
}

describe('DefaultLayout.vue full-bleed 内容区', () => {
  it('面包屑下渲染工作区页签栏，且页签栏不属于 content padding 契约', () => {
    const wrapper = mountWithMeta({ title: '工作台' });
    expect(wrapper.find('.workspace-tabs-stub').exists()).toBe(true);
    expect(wrapper.find('.content').element.previousElementSibling.className)
      .toBe('workspace-tabs-stub');
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
  it('默认展开，切换时向 Header 与 Sidebar 同步 220px/64px 壳层状态', async () => {
    const wrapper = mountWithMeta({ title: '工作台' });

    expect(wrapper.find('.layout').classes()).not.toContain('layout--sidebar-collapsed');
    expect(wrapper.find('.sidebar-stub').attributes('data-collapsed')).toBe('false');
    expect(wrapper.find('.header-stub').attributes('data-collapsed')).toBe('false');

    await wrapper.find('.header-stub').trigger('click');
    expect(wrapper.find('.layout').classes()).toContain('layout--sidebar-collapsed');
    expect(wrapper.find('.sidebar-stub').attributes('data-collapsed')).toBe('true');
    expect(wrapper.find('.header-stub').attributes('data-collapsed')).toBe('true');
  });

  it('侧栏折叠态仅属于当前 Layout 实例，不会跨重新挂载持久化', async () => {
    const first = mountWithMeta({ title: '工作台' });
    await first.find('.header-stub').trigger('click');
    expect(first.find('.sidebar-stub').attributes('data-collapsed')).toBe('true');
    first.unmount();

    const fresh = mountWithMeta({ title: '工作台' });
    expect(fresh.find('.sidebar-stub').attributes('data-collapsed')).toBe('false');
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
