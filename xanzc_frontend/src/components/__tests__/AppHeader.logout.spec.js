// @vitest-environment happy-dom
// 退出登录必须整页跳转回归测试。
// 根因：logout 用 router.replace('/login')（SPA 内跳转），Pinia 常驻内存，
// menu store 等上一个用户的状态全部残留，下一个用户登录后看到旧菜单/旧状态。
// 修复：与切换角色流程对齐，logout 改 window.location.replace('/login') 整页导航清空内存态。
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick } from 'vue';
import { createPinia, setActivePinia } from 'pinia';

const routerReplace = vi.fn();
vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: routerReplace, push: vi.fn() })
}));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}));
vi.mock('@/api/auth', () => ({
  logout: vi.fn().mockResolvedValue('OK'),
  switchRole: vi.fn(),
  getCurrentUser: vi.fn(),
  getMyMenus: vi.fn()
}));
vi.mock('@/api/workspace', () => ({ getUnreadCount: vi.fn().mockResolvedValue(0) }));
vi.mock('@/api/users', () => ({ changeMyPassword: vi.fn() }));

import { logout } from '@/api/auth';
import { useUserStore } from '@/stores/user';
import AppHeader from '../AppHeader.vue';

const passthrough = (name) => ({ name, template: '<div><slot /></div>' });
const empty = (name) => ({ name, template: '<div />' });
const stubs = {
  'el-dropdown': { name: 'ElDropdown', emits: ['command'], template: '<div><slot /><slot name="dropdown" /></div>' },
  'el-dropdown-menu': passthrough('ElDropdownMenu'),
  'el-dropdown-item': passthrough('ElDropdownItem'),
  'el-dialog': { name: 'ElDialog', template: '<div><slot /><slot name="footer" /></div>' },
  'el-form': passthrough('ElForm'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': empty('ElInput'),
  'el-badge': passthrough('ElBadge'),
  // 必须声明 emits，否则父级 @click 经 attrs 透传到原生 button 后与 $emit 叠加，处理器被调两次
  'el-button': { name: 'ElButton', emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' }
};

let wrapper;
let pinia;
let locationReplace;
beforeEach(() => {
  vi.clearAllMocks();
  pinia = createPinia();
  setActivePinia(pinia);
  // happy-dom 下 stub window.location，捕获整页跳转
  locationReplace = vi.fn();
  Object.defineProperty(window, 'location', {
    value: { href: 'http://localhost:8090/', replace: locationReplace, reload: vi.fn() },
    writable: true,
    configurable: true
  });
});
afterEach(() => { wrapper?.unmount(); });

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

describe('AppHeader 退出登录', () => {
  it('确认退出后清用户态并整页跳转 /login（而非 SPA 内路由跳转）', async () => {
    const userStore = useUserStore();
    userStore.setUser({ empId: 'A001', username: 'userA', displayName: '用户A', roles: [] });

    wrapper = mount(AppHeader, { global: { plugins: [pinia], stubs } });
    await settle();
    wrapper.findComponent({ name: 'ElDropdown' }).vm.$emit('command', 'logout');
    await settle();

    expect(logout).toHaveBeenCalled();
    expect(userStore.user).toBeNull();
    // 修复前：router.replace('/login') SPA 内跳转，Pinia 内存态（菜单等）残留给下一个用户
    expect(locationReplace, '退出必须整页跳转以清空内存态').toHaveBeenCalledWith('/login');
    expect(routerReplace).not.toHaveBeenCalledWith('/login');
  });
});
