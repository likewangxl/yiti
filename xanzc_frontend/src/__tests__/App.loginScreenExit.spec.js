// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('@/components/LoginScreenExit.vue', () => ({ default: { name: 'LoginScreenExit', template: '<div data-testid="exit-stub" />' } }));
import App from '../App.vue';

describe('App login screen exit integration', () => {
  it('全局壳挂载登录首屏关闭组件', () => {
    const wrapper = mount(App, { global: { stubs: { 'router-view': { template: '<div />' } } } });
    expect(wrapper.find('[data-testid="exit-stub"]').exists()).toBe(true);
  });
});
