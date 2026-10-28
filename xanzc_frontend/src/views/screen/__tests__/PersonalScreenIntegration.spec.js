// @vitest-environment happy-dom
import { mount, flushPromises } from '@vue/test-utils';
import { describe, it, expect, vi } from 'vitest';
import { reactive } from 'vue';

const route = reactive({ params: {}, query: {} });
vi.mock('vue-router', () => ({ useRoute: () => route }));
vi.mock('../panorama/PanoramaBindings.vue', () => ({ default: { template: '<div data-testid="business-settings" />' } }));
vi.mock('../personal/PersonalDashboardSettings.vue', () => ({ default: { template: '<div data-testid="personal-settings" />' } }));
vi.mock('../personal/PersonalDashboardPage.vue', () => ({ default: { template: '<div data-testid="personal-page" />' } }));
vi.mock('../ScreenView.vue', () => ({ default: { template: '<div data-testid="generic-screen" />' } }));

describe('个人驾驶舱管理和运行入口', () => {
  it('大屏管理提供个人配置入口，切回经营大屏保留组件实例', async () => {
    const { default: ScreenManagement } = await import('../ScreenManagement.vue');
    const wrapper = mount(ScreenManagement);
    await flushPromises();
    expect(wrapper.find('[data-testid="business-settings"]').exists()).toBe(true);
    await wrapper.get('[data-tab="personal"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="personal-settings"]').isVisible()).toBe(true);
    await wrapper.get('[data-tab="business"]').trigger('click');
    expect(wrapper.find('[data-testid="business-settings"]').isVisible()).toBe(true);
    wrapper.unmount();
  });

  it('个人发布和草稿入口使用个人运行页，其它屏继续沿用原运行页', async () => {
    const { default: ScreenRuntimePage } = await import('../ScreenRuntimePage.vue');
    route.params = { screenCode: 'PERSONAL_COCKPIT' };
    const wrapper = mount(ScreenRuntimePage);
    await flushPromises();
    expect(wrapper.find('[data-testid="personal-page"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="generic-screen"]').exists()).toBe(false);
    route.params = { screenCode: 'OTHER' };
    await flushPromises();
    expect(wrapper.find('[data-testid="generic-screen"]').exists()).toBe(true);
    wrapper.unmount();
  });
});
