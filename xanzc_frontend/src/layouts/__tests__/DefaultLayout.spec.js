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
  AppSidebar: true, AppHeader: true, AppBreadcrumb: true, 'router-view': true
};

function mountWithMeta(meta) {
  return mount(DefaultLayout, {
    global: { stubs, mocks: { $route: { meta, fullPath: '/x' } } }
  });
}

describe('DefaultLayout.vue full-bleed 内容区', () => {
  it('路由声明 meta.fullBleed 时内容区带 content--full(去 padding)', () => {
    const wrapper = mountWithMeta({ fullBleed: true });
    expect(wrapper.find('.content').classes()).toContain('content--full');
  });

  it('普通路由内容区不带 content--full(既有页面 padding 不受影响)', () => {
    const wrapper = mountWithMeta({ title: '工作台' });
    expect(wrapper.find('.content').classes()).not.toContain('content--full');
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
