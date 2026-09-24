// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const { routerPush, route, dashboardStub } = vi.hoisted(() => ({
  routerPush: vi.fn(),
  route: { query: {} },
  dashboardStub: {
    name: 'OverviewDashboardStub',
    props: ['model', 'loading', 'error', 'demo', 'backLabel'],
    emits: ['back', 'refresh'],
    template: '<div><button data-action="dashboard-back" @click="$emit(\'back\')">返回</button></div>'
  }
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush }),
  useRoute: () => route
}));

vi.mock('../CorporateDashboard.vue', () => ({ default: dashboardStub }));
vi.mock('../RetailDashboard.vue', () => ({ default: dashboardStub }));

import CorporatePreview from '../CorporatePreview.vue';
import RetailPreview from '../RetailPreview.vue';

describe('对公和零售经营总览本地预览返回路径', () => {
  beforeEach(() => {
    routerPush.mockReset();
    route.query = {};
  });

  it.each([
    ['对公', CorporatePreview],
    ['零售', RetailPreview]
  ])('%s预览直接访问时返回原本地演示入口', async (_label, Component) => {
    const wrapper = mount(Component);

    if (Component === RetailPreview) {
      expect(wrapper.findComponent({ name: 'OverviewDashboardStub' }).props('backLabel')).toBe('返回分行预览');
    }
    await wrapper.get('[data-action="dashboard-back"]').trigger('click');

    expect(routerPush).toHaveBeenLastCalledWith('/screen-preview');
  });

  it.each([
    ['对公', CorporatePreview],
    ['零售', RetailPreview]
  ])('%s预览从大屏中心进入时返回大屏中心', async (_label, Component) => {
    route.query = { from: 'screen-center' };
    const wrapper = mount(Component);

    if (Component === RetailPreview) {
      expect(wrapper.findComponent({ name: 'OverviewDashboardStub' }).props('backLabel')).toBe('返回大屏中心');
    }
    await wrapper.get('[data-action="dashboard-back"]').trigger('click');

    expect(routerPush).toHaveBeenLastCalledWith('/screens');
  });
});
