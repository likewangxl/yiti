// @vitest-environment happy-dom
// 红色引擎退出时必须回到独立的红色登录页，并使用整页导航清空前端内存态。
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick } from 'vue';
import { createPinia, setActivePinia } from 'pinia';

const routerPush = vi.fn();
vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/redengine/dashboard' }),
  useRouter: () => ({ push: routerPush })
}));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));
vi.mock('@/api/http', () => ({
  default: { get: vi.fn().mockResolvedValue({ resourceUrls: [] }) }
}));
vi.mock('@/api/auth', () => ({
  logout: vi.fn().mockResolvedValue('OK')
}));

import { logout } from '@/api/auth';
import { useUserStore } from '@/stores/user';
import RedEngineLayout from '../layout/RedEngineLayout.vue';

const passthrough = (name) => ({ name, template: '<div><slot /></div>' });
const stubs = {
  'el-container': passthrough('ElContainer'),
  'el-aside': passthrough('ElAside'),
  'el-menu': passthrough('ElMenu'),
  'el-menu-item': passthrough('ElMenuItem'),
  'el-header': passthrough('ElHeader'),
  'el-main': passthrough('ElMain'),
  'router-view': true,
  'el-button': {
    name: 'ElButton',
    emits: ['click'],
    template: '<button @click="$emit(\'click\')"><slot /></button>'
  }
};

let wrapper;
let pinia;
let locationReplace;

beforeEach(() => {
  vi.clearAllMocks();
  pinia = createPinia();
  setActivePinia(pinia);
  locationReplace = vi.fn();
  Object.defineProperty(window, 'location', {
    value: { href: 'http://localhost:8090/#/redengine/dashboard', replace: locationReplace },
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

describe('红色引擎退出入口', () => {
  it('退出后清理用户态并整页跳转红色引擎登录页', async () => {
    const userStore = useUserStore();
    userStore.setUser({ empId: 'E001', displayName: '党建用户', roles: [] });
    wrapper = mount(RedEngineLayout, { global: { plugins: [pinia], stubs } });

    await wrapper.find('button').trigger('click');
    await settle();

    expect(logout).toHaveBeenCalled();
    expect(userStore.user).toBeNull();
    expect(locationReplace).toHaveBeenCalledWith('/#/redengine/login');
    expect(locationReplace).not.toHaveBeenCalledWith('/login');
    expect(routerPush).not.toHaveBeenCalled();
  });
});
