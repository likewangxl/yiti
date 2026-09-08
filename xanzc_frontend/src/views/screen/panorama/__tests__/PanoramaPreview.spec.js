// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const push = vi.fn();
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }));

const { dashboardStub } = vi.hoisted(() => ({
  dashboardStub: {
    name: 'PanoramaDashboard',
    props: ['model', 'loading', 'error', 'demo'],
    emits: ['back', 'refresh'],
    template: '<div class="stub-dashboard" :data-demo="String(demo)" :data-title="model.title">'
      + '<button data-action="dashboard-back" @click="$emit(\'back\')">返回</button>'
      + '<button data-action="dashboard-refresh" @click="$emit(\'refresh\')">刷新</button>'
      + '</div>'
  }
}));
vi.mock('../PanoramaDashboard.vue', () => ({ default: dashboardStub }));

import PanoramaPreview from '../PanoramaPreview.vue';
import { demoModel } from '../demoModel.js';

describe('PanoramaPreview 本地演示入口', () => {
  beforeEach(() => {
    push.mockReset();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-07T09:00:00+08:00'));
  });

  afterEach(() => vi.useRealTimers());

  it('直接消费确定性 demoModel，明确标注本地演示且不进入网络运行时', () => {
    const wrapper = mount(PanoramaPreview);
    expect(wrapper.find('.stub-dashboard').attributes('data-demo')).toBe('true');
    expect(wrapper.find('.stub-dashboard').attributes('data-title')).toBe(demoModel.title);
    expect(wrapper.text()).toContain('本地演示 · 非业务数据');
    expect(wrapper.find('[data-testid="preview-source-top"]').text()).toContain('本地演示 · 非业务数据');
    expect(wrapper.find('[data-testid="demo-updated-at"]').text()).toContain('本地演示更新时间');
  });

  it('刷新只更新时间提示，不声称后端成功', async () => {
    const wrapper = mount(PanoramaPreview);
    const before = wrapper.find('[data-testid="demo-updated-at"]').text();
    vi.setSystemTime(new Date('2026-09-07T09:01:00+08:00'));
    await wrapper.get('[data-action="dashboard-refresh"]').trigger('click');
    const after = wrapper.find('[data-testid="demo-updated-at"]').text();
    expect(after).not.toBe(before);
    expect(wrapper.text()).toContain('仅更新本地演示时间');
    expect(wrapper.text()).not.toContain('后端刷新成功');
  });

  it('返回按钮进入工作区安全入口', async () => {
    const wrapper = mount(PanoramaPreview);
    await wrapper.get('[data-action="dashboard-back"]').trigger('click');
    expect(push).toHaveBeenCalledWith('/workspace');
  });

  it('演示标识旁提供配置真实数据入口，点击后交给现有路由守卫', async () => {
    const wrapper = mount(PanoramaPreview);
    expect(wrapper.get('[data-action="configure-real-data"]').text()).toContain('配置真实数据');
    await wrapper.get('[data-action="configure-real-data"]').trigger('click');
    expect(push).toHaveBeenCalledWith('/screen-admin/designer');
  });
});
