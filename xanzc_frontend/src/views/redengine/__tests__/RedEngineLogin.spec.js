// @vitest-environment happy-dom
// Task 15 TDD Step 1（RED）：红色引擎登录页回归测试。
// 覆盖简报要求的两点：①LoginView 挂载后渲染登录按钮；②mock login 成功后 push '/redengine/dashboard'。
// 桩写法照抄 xanzc_frontend/src/views/login/__tests__/LoginMenuReload.spec.js 既有惯例
// （el-form/el-input/el-button 手写桩，避免测试环境需要真实安装 ElementPlus 插件）。
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick } from 'vue';
import { createPinia, setActivePinia } from 'pinia';

const routerPush = vi.fn();
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush })
}));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));
vi.mock('@/api/auth', () => ({
  login: vi.fn()
}));

import { login } from '@/api/auth';
import LoginView from '../login/LoginView.vue';

const passthrough = (name) => ({ name, template: '<div><slot /></div>' });
const stubs = {
  // el-form 桩提供 validate（组件经 ref 调用，LoginView.handleLogin 先 await formRef.value.validate()）
  'el-form': { name: 'ElForm', template: '<form @submit.prevent><slot /></form>', methods: { validate: () => Promise.resolve(true) } },
  'el-form-item': passthrough('ElFormItem'),
  'el-input': {
    name: 'ElInput',
    props: ['modelValue'],
    template: '<input class="inp-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  // 必须声明 emits，否则父级 @click 经 attrs 透传到原生 button 后与 $emit 叠加，处理器被调两次
  'el-button': { name: 'ElButton', emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' }
};

let wrapper;
let pinia;
beforeEach(() => {
  vi.clearAllMocks();
  pinia = createPinia();
  setActivePinia(pinia);
});
afterEach(() => { wrapper?.unmount(); });

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

describe('红色引擎登录页(LoginView)', () => {
  it('挂载后渲染登录按钮', () => {
    wrapper = mount(LoginView, { global: { plugins: [pinia], stubs } });
    const btn = wrapper.findAll('button').find((b) => b.text().includes('登'));
    expect(btn).toBeTruthy();
  });

  it('mock login 成功后 push /redengine/dashboard', async () => {
    login.mockResolvedValue({ empId: 'E001', username: 'admin', displayName: '管理员', roles: [] });
    wrapper = mount(LoginView, { global: { plugins: [pinia], stubs } });

    const inputs = wrapper.findAll('.inp-stub');
    await inputs[0].setValue('admin');
    await inputs[1].setValue('admin123');
    await wrapper.findAll('button').find((b) => b.text().includes('登')).trigger('click');
    await settle();

    expect(login).toHaveBeenCalledWith('admin', 'admin123');
    expect(routerPush).toHaveBeenCalledWith('/redengine/dashboard');
  });
});
