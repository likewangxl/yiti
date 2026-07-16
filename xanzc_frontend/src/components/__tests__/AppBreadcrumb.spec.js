// @vitest-environment happy-dom
import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const routeRef = { path: '/report/free', matched: [{ meta: { title: '自由报表', group: '报表分析' } }] };
vi.mock('vue-router', () => ({ useRoute: () => routeRef }));

let resolveImpl = () => null;
vi.mock('@/stores/menu', () => ({
  useMenuStore: () => ({ load: vi.fn(), resolve: (p) => resolveImpl(p) })
}));

import AppBreadcrumb from '../AppBreadcrumb.vue';

describe('AppBreadcrumb.vue', () => {
  it('命中菜单：用 DB 名 + 分组，且不再显示旧静态名', () => {
    resolveImpl = () => ({ title: '数据公式', group: '报表分析' });
    const w = mount(AppBreadcrumb);
    const text = w.text();
    expect(text).toContain('数据公式');
    expect(text).toContain('报表分析');
    expect(text).not.toContain('自由报表');
  });

  it('未命中菜单：退回 meta.title / meta.group', () => {
    resolveImpl = () => null;
    const w = mount(AppBreadcrumb);
    const text = w.text();
    expect(text).toContain('自由报表');
    expect(text).toContain('报表分析');
  });

  it('命中菜单但 group 为 null 时只显示标题、不回退 meta.group', () => {
    resolveImpl = () => ({ title: '数据公式', group: null });
    const w = mount(AppBreadcrumb);
    const text = w.text();
    expect(text).toContain('数据公式');
    expect(text).not.toContain('报表分析');
  });
});
