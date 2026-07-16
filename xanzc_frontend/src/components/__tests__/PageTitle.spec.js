// @vitest-environment happy-dom
import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const routeRef = { path: '/report/free', matched: [{ meta: { title: '自由报表' } }] };
vi.mock('vue-router', () => ({ useRoute: () => routeRef }));

let resolveImpl = () => null;
vi.mock('@/stores/menu', () => ({
  useMenuStore: () => ({ load: vi.fn(), resolve: (p) => resolveImpl(p) })
}));

import PageTitle from '../PageTitle.vue';

describe('PageTitle.vue', () => {
  it('显示 DB 菜单名（优先于 meta.title）', () => {
    resolveImpl = () => ({ title: '数据公式', group: '报表分析' });
    const w = mount(PageTitle);
    expect(w.find('h1').text()).toBe('数据公式');
  });

  it('无菜单命中时退回 meta.title', () => {
    resolveImpl = () => null;
    const w = mount(PageTitle);
    expect(w.find('h1').text()).toBe('自由报表');
  });

  it('title 属性覆盖一切', () => {
    resolveImpl = () => ({ title: '数据公式', group: null });
    const w = mount(PageTitle, { props: { title: '报表详情' } });
    expect(w.find('h1').text()).toBe('报表详情');
  });

  it('默认插槽渲染副标题、且保留菜单名', () => {
    resolveImpl = () => ({ title: '菜单管理', group: null });
    const w = mount(PageTitle, { slots: { default: '<span class="sub">副标题</span>' } });
    expect(w.find('h1 .sub').text()).toBe('副标题');
    expect(w.find('h1').text()).toContain('菜单管理');
  });
});
