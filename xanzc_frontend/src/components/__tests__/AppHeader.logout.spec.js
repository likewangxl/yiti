// @vitest-environment happy-dom
// 退出登录必须整页跳转回归测试。
// 根因：logout 用 router.replace('/login')（SPA 内跳转），Pinia 常驻内存，
// menu store 等上一个用户的状态全部残留，下一个用户登录后看到旧菜单/旧状态。
// logout 使用 window.location.replace('/#/login') 整页导航清空内存态。
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
  getMyMenus: vi.fn(),
  getMyPermissions: vi.fn()
}));
vi.mock('@/api/workspace', () => ({ getUnreadCount: vi.fn().mockResolvedValue(0) }));
vi.mock('@/api/users', () => ({ changeMyPassword: vi.fn().mockResolvedValue(undefined) }));

import { logout, switchRole } from '@/api/auth';
import { useUserStore } from '@/stores/user';
import { useMenuStore } from '@/stores/menu';
import { usePermissionStore } from '@/stores/permission';
import AppHeader from '../AppHeader.vue';

const passthrough = (name) => ({ name, template: '<div><slot /></div>' });
const stubs = {
  'el-dropdown': { name: 'ElDropdown', emits: ['command'], template: '<div><slot /><slot name="dropdown" /></div>' },
  'el-dropdown-menu': passthrough('ElDropdownMenu'),
  'el-dropdown-item': passthrough('ElDropdownItem'),
  'el-dialog': { name: 'ElDialog', template: '<div><slot /><slot name="footer" /></div>' },
  'el-form': passthrough('ElForm'),
  'el-form-item': passthrough('ElFormItem'),
  'el-input': {
    name: 'ElInput',
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<input class="input-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
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
  it('确认退出后清用户与授权快照，并按 hash 地址整页跳转登录页', async () => {
    const userStore = useUserStore();
    const menuStore = useMenuStore();
    const permissionStore = usePermissionStore();
    userStore.setUser({ empId: 'A001', username: 'userA', displayName: '用户A', roles: [] });
    await menuStore.load();
    await permissionStore.load();
    expect(menuStore.loaded).toBe(true);
    expect(permissionStore.loaded).toBe(true);

    wrapper = mount(AppHeader, { global: { plugins: [pinia], stubs } });
    await settle();
    wrapper.findComponent({ name: 'ElDropdown' }).vm.$emit('command', 'logout');
    await settle();

    expect(logout).toHaveBeenCalled();
    expect(userStore.user).toBeNull();
    // 修复前：router.replace('/login') SPA 内跳转，Pinia 内存态（菜单等）残留给下一个用户
    expect(menuStore.loaded).toBe(false);
    expect(permissionStore.loaded).toBe(false);
    expect(locationReplace, '退出必须整页跳转以清空内存态').toHaveBeenCalledWith('/#/login');
    expect(routerReplace).not.toHaveBeenCalledWith('/login');
  });

  it('修改密码成功后同样清理授权快照并整页重登', async () => {
    vi.useFakeTimers();
    try {
      const userStore = useUserStore();
      const menuStore = useMenuStore();
      const permissionStore = usePermissionStore();
      userStore.setUser({ empId: 'A001', username: 'userA', displayName: '用户A', roles: [] });
      await menuStore.load();
      await permissionStore.load();
      wrapper = mount(AppHeader, { global: { plugins: [pinia], stubs } });
      wrapper.findComponent({ name: 'ElDropdown' }).vm.$emit('command', 'changePassword');
      await nextTick();

      const inputs = wrapper.findAll('.input-stub').slice(-3);
      await inputs[0].setValue('old-pass');
      await inputs[1].setValue('new-pass');
      await inputs[2].setValue('new-pass');
      await wrapper.findAll('button').find((button) => button.text() === '确认').trigger('click');
      await flushPromises();
      await vi.advanceTimersByTimeAsync(600);
      await flushPromises();

      expect(userStore.user).toBeNull();
      expect(menuStore.loaded).toBe(false);
      expect(permissionStore.loaded).toBe(false);
      expect(locationReplace).toHaveBeenCalledWith('/#/login');
    } finally {
      vi.useRealTimers();
    }
  });
});

describe('AppHeader 多角色只读展示', () => {
  it('展示全部已分配角色，但不再允许切换角色或刷新页面', async () => {
    const userStore = useUserStore();
    userStore.setUser({
      empId: 'A001',
      username: 'userA',
      displayName: '用户A',
      roles: [
        { roleId: 'R1', roleCode: 'NORMAL', roleChName: '普通用户' },
        { roleId: 'R2', roleCode: 'R_RE_REPORT', roleChName: '党建报送员' }
      ]
    });

    wrapper = mount(AppHeader, { global: { plugins: [pinia], stubs } });
    await settle();

    expect(wrapper.text()).toContain('已分配角色');
    expect(wrapper.text()).not.toContain('切换角色');
    expect(wrapper.text()).toContain('普通用户');
    expect(wrapper.text()).toContain('党建报送员');

    // 即使旧调用方仍发出历史 role:* command，也必须被忽略。
    wrapper.findComponent({ name: 'ElDropdown' }).vm.$emit('command', 'role:R2');
    await settle();
    expect(switchRole).not.toHaveBeenCalled();
    expect(window.location.reload).not.toHaveBeenCalled();
  });
});
