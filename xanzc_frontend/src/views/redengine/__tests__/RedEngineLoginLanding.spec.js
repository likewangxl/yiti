// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { createPinia, setActivePinia } from 'pinia';

const testState = vi.hoisted(() => ({ routeQuery: {} }));
const routerReplace = vi.fn();
vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: routerReplace, push: vi.fn() }),
  useRoute: () => ({ query: testState.routeQuery })
}));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));
vi.mock('@/api/auth', () => ({
  login: vi.fn().mockResolvedValue({ empId: 'E001', displayName: '党建用户', roles: [] }),
  getMyMenus: vi.fn()
}));

import { getMyMenus } from '@/api/auth';
import LoginView from '../login/LoginView.vue';

const passthrough = (name) => ({ name, template: '<div><slot /></div>' });
const stubs = {
  'el-form': { name: 'ElForm', template: '<form><slot /></form>', methods: { validate: () => Promise.resolve(true) } },
  'el-form-item': passthrough('ElFormItem'),
  'el-input': {
    name: 'ElInput',
    props: ['modelValue'],
    template: '<input class="inp-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-button': { name: 'ElButton', emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  Star: true
};

let wrapper;
let pinia;

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  testState.routeQuery = {};
  pinia = createPinia();
  setActivePinia(pinia);
});
afterEach(() => wrapper?.unmount());

async function loginWithMenus(menus) {
  getMyMenus.mockResolvedValue(menus);
  wrapper = mount(LoginView, { global: { plugins: [pinia], stubs } });
  const inputs = wrapper.findAll('.inp-stub');
  await inputs[0].setValue('user001');
  await inputs[1].setValue('pwd123');
  await wrapper.findAll('button').find((button) => button.text().includes('登')).trigger('click');
  await flushPromises();
  await nextTick();
}

describe('红色引擎专属登录后的授权落点', () => {
  it('同时拥有平台工作台和红色引擎时优先进入红色工作台', async () => {
    await loginWithMenus([
      { resourceId: 'M_WORKSPACE', resourceUrl: '/workspace', menuName: '工作台', children: [] },
      { resourceId: 'M_RE_ENGINE', resourceUrl: '/redengine/dashboard', menuName: '红色引擎', children: [] }
    ]);
    expect(routerReplace).toHaveBeenCalledWith('/redengine/dashboard');
  });

  it('仅有红色引擎入口时进入红色工作台', async () => {
    await loginWithMenus([
      { resourceId: 'M_RE_ENGINE', resourceUrl: '/redengine/dashboard', menuName: '红色引擎', children: [] }
    ]);
    expect(routerReplace).toHaveBeenCalledWith('/redengine/dashboard');
  });

  it('已授权的红色引擎 redirect 优先，未授权 redirect 回落红色工作台', async () => {
    testState.routeQuery = { redirect: '/redengine/review?task=42' };
    await loginWithMenus([
      { resourceId: 'M_WORKSPACE', resourceUrl: '/workspace', menuName: '工作台', children: [] },
      { resourceId: 'M_RE_ENGINE', resourceUrl: '/redengine/dashboard', menuName: '红色引擎', children: [] }
    ]);
    expect(routerReplace).toHaveBeenLastCalledWith('/redengine/review?task=42');

    wrapper.unmount();
    testState.routeQuery = { redirect: '/system/users' };
    await loginWithMenus([
      { resourceId: 'M_WORKSPACE', resourceUrl: '/workspace', menuName: '工作台', children: [] },
      { resourceId: 'M_RE_ENGINE', resourceUrl: '/redengine/dashboard', menuName: '红色引擎', children: [] }
    ]);
    expect(routerReplace).toHaveBeenLastCalledWith('/redengine/dashboard');
  });
});
