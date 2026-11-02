// @vitest-environment happy-dom
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const mocks = vi.hoisted(() => ({ route: { name: 'CodeScreenPage', query: { from: 'login' } }, replace: vi.fn(), load: vi.fn(), resolve: vi.fn(), user: { empId: 'E1' } }));
vi.mock('vue-router', () => ({ useRoute: () => mocks.route, useRouter: () => ({ replace: mocks.replace }) }));
vi.mock('@/stores/menu', () => ({ useMenuStore: () => ({ load: mocks.load, resolveLandingPath: mocks.resolve }) }));
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ user: mocks.user }) }));

import LoginScreenExit from '../LoginScreenExit.vue';

describe('LoginScreenExit', () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.route.name = 'CodeScreenPage'; mocks.route.query = { from: 'login' }; mocks.user = { empId: 'E1' }; mocks.load.mockResolvedValue({}); mocks.resolve.mockReturnValue('/workspace'); });
  it('菜单授权读取失败时进入说明页，不猜测工作台权限', async () => {
    mocks.load.mockRejectedValue(new Error('network'));
    const wrapper = mount(LoginScreenExit);
    await wrapper.get('button').trigger('click'); await flushPromises();
    expect(mocks.replace).toHaveBeenCalledWith('/no-access'); wrapper.unmount();
  });
  it('只在登录首屏目标显示并按授权落点关闭', async () => {
    const wrapper = mount(LoginScreenExit);
    expect(wrapper.find('[data-testid="login-screen-exit"]').exists()).toBe(true);
    await wrapper.get('button').trigger('click'); await flushPromises();
    expect(mocks.load).toHaveBeenCalledWith(true);
    expect(mocks.resolve).toHaveBeenCalledWith('/workspace');
    expect(mocks.replace).toHaveBeenCalledWith('/workspace');
  });
  it('非登录来源不显示，账号变化后迟到关闭不导航', async () => {
    let resolveLoad;
    mocks.load.mockReturnValue(new Promise(resolve => { resolveLoad = resolve; }));
    const wrapper = mount(LoginScreenExit);
    await wrapper.get('button').trigger('click');
    wrapper.unmount();
    resolveLoad({});
    await flushPromises();
    expect(mocks.replace).not.toHaveBeenCalled();
    mocks.route.query = {};
    expect(mount(LoginScreenExit).find('button').exists()).toBe(false);
  });
});
