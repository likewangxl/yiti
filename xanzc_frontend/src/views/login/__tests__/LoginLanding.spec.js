// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { reactive } from 'vue';
const m = vi.hoisted(() => ({ replace: vi.fn(), rules: vi.fn(), catalog: vi.fn(), view: vi.fn(),
  user: null, query: {}, permission: { load: vi.fn(), canAccess: vi.fn() },
  menu: { load: vi.fn(), resolveLandingPath: vi.fn(), hasUrl: vi.fn(), isAuthorizedRedirect: vi.fn() } }));
vi.mock('vue-router', () => ({ useRouter: () => ({ replace: m.replace }), useRoute: () => ({ query: m.query }) }));
vi.mock('@/api/loginScreen', () => ({ getLoginScreenRules: m.rules }));
vi.mock('@/api/screen', () => ({ listAvailableScreens: m.catalog, getScreenView: m.view }));
vi.mock('@/stores/user', () => ({ useUserStore: () => m.user }));
vi.mock('@/stores/menu', () => ({ useMenuStore: () => m.menu }));
vi.mock('@/stores/permission', () => ({ usePermissionStore: () => m.permission }));
import LoginLanding from '../LoginLanding.vue';
beforeEach(() => {
  vi.resetAllMocks(); sessionStorage.clear(); m.query = {};
  m.user = reactive({ user: { empId: 'U1', roles: [{ roleId: 'R1', primary: true }] } });
  m.menu.load.mockResolvedValue(); m.menu.resolveLandingPath.mockReturnValue('/workspace');
  m.menu.hasUrl.mockReturnValue(true); m.menu.isAuthorizedRedirect.mockReturnValue(false);
  m.permission.load.mockResolvedValue(); m.permission.canAccess.mockReturnValue(true);
  m.rules.mockResolvedValue({ rules: [{ roleId: 'R1', target: 'PERSONAL', priority: 100 }] });
});
describe('首屏加载流程', () => {
  it('无首屏资源权限时不发请求，直接回已授权工作台', async () => {
    m.permission.canAccess.mockReturnValue(false);
    const w = mount(LoginLanding); await flushPromises();
    expect(m.rules).not.toHaveBeenCalled(); expect(m.replace).toHaveBeenCalledWith('/workspace'); w.unmount();
  });
  it('query不能绕过菜单授权跳到任意路径', async () => {
    m.query.redirect = '/system/users'; const w = mount(LoginLanding); await flushPromises();
    expect(m.replace).not.toHaveBeenCalledWith('/system/users'); w.unmount();
  });
  it('加载中可以直接进入工作台，迟到响应不再导航', async () => {
    let finish; m.rules.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
    const w = mount(LoginLanding); await flushPromises();
    await w.get('[data-action="login-landing-workspace"]').trigger('click');
    finish({ rules: [{ roleId: 'R1', target: 'PERSONAL', priority: 100 }] }); await flushPromises();
    expect(m.replace).toHaveBeenCalledTimes(1); expect(m.replace).toHaveBeenCalledWith('/workspace'); w.unmount();
  });
  it('卸载后丢弃请求结果', async () => {
    let finish; m.rules.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
    const w = mount(LoginLanding); await flushPromises(); w.unmount();
    finish({ rules: [{ roleId: 'R1', target: 'PERSONAL', priority: 100 }] }); await flushPromises();
    expect(m.replace).not.toHaveBeenCalled();
  });
  it('读取错误自动回工作台，401保持登录失效处理', async () => {
    m.rules.mockRejectedValue(new Error('network')); let w = mount(LoginLanding); await flushPromises();
    expect(m.replace).toHaveBeenCalledWith('/workspace'); w.unmount(); m.replace.mockClear();
    m.rules.mockRejectedValue({ response: { status: 401 } }); w = mount(LoginLanding); await flushPromises();
    expect(m.replace).toHaveBeenCalledWith('/login'); w.unmount();
  });
  it('同工号重新建立身份后也丢弃旧请求', async () => {
    let finish; m.rules.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
    const w = mount(LoginLanding); await flushPromises();
    m.user.user = { empId: 'U1', roles: [] };
    finish({ rules: [{ roleId: 'R1', target: 'PERSONAL', priority: 100 }] }); await flushPromises();
    expect(m.replace).not.toHaveBeenCalled(); w.unmount();
  });
});
