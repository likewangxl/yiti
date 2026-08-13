// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { createPinia, setActivePinia } from 'pinia';

const mocks = vi.hoisted(() => ({
  routeQuery: {},
  routerReplace: vi.fn(),
  login: vi.fn(),
  uniAuthLogin: vi.fn(),
  getMyMenus: vi.fn(),
  messageSuccess: vi.fn(),
  messageError: vi.fn()
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: mocks.routerReplace }),
  useRoute: () => ({ query: mocks.routeQuery })
}));

vi.mock('element-plus', () => ({
  ElMessage: {
    success: mocks.messageSuccess,
    error: mocks.messageError
  }
}));

vi.mock('@/api/http', () => ({ USE_MOCK: false }));
vi.mock('@/api/auth', () => ({
  login: (...args) => mocks.login(...args),
  uniAuthLogin: (...args) => mocks.uniAuthLogin(...args),
  getMyMenus: (...args) => mocks.getMyMenus(...args)
}));

import Login from '../Index.vue';

const ElFormStub = {
  name: 'ElForm',
  template: '<form v-bind="$attrs" @submit.prevent><slot /></form>',
  methods: { validate: () => Promise.resolve(true) }
};

const ElFormItemStub = {
  name: 'ElFormItem',
  template: '<div v-bind="$attrs"><slot /></div>'
};

const ElInputStub = {
  name: 'ElInput',
  inheritAttrs: false,
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
};

const ElButtonStub = {
  name: 'ElButton',
  inheritAttrs: false,
  props: ['disabled', 'loading'],
  emits: ['click'],
  template: '<button v-bind="$attrs" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
};

const stubs = {
  'el-form': ElFormStub,
  'el-form-item': ElFormItemStub,
  'el-input': ElInputStub,
  'el-button': ElButtonStub
};

let wrapper;
let pinia;
let originalLocationDescriptor;

function mountLogin(query = {}) {
  mocks.routeQuery = query;
  wrapper = mount(Login, {
    global: { plugins: [pinia], stubs }
  });
  return wrapper;
}

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

async function fillNormalCredentials(loginWrapper = wrapper) {
  await loginWrapper.find('#login-username').setValue('userB');
  await loginWrapper.find('#login-password').setValue('pwd123456');
}

beforeEach(() => {
  vi.clearAllMocks();
  pinia = createPinia();
  setActivePinia(pinia);
  mocks.routeQuery = {};
  mocks.login.mockResolvedValue({
    empId: 'B001', username: 'userB', displayName: '用户B', roles: []
  });
  mocks.getMyMenus.mockResolvedValue([]);

  originalLocationDescriptor = Object.getOwnPropertyDescriptor(window, 'location');
  Object.defineProperty(window, 'location', {
    configurable: true,
    value: { href: '' }
  });
});

afterEach(() => {
  wrapper?.unmount();
  if (originalLocationDescriptor) {
    Object.defineProperty(window, 'location', originalLocationDescriptor);
  }
});

describe('平台登录页结构与认证入口', () => {
  it('使用双栏语义结构，并为品牌与登录主标题提供稳定可访问名称', () => {
    const loginWrapper = mountLogin();

    expect(loginWrapper.find('aside.brand').exists()).toBe(true);
    expect(loginWrapper.find('h1.brand-title').attributes('id')).toBe('brand-title');
    expect(loginWrapper.find('main.form-wrap').attributes('aria-labelledby')).toBe('login-title');
    expect(loginWrapper.find('h2.login-title').attributes('id')).toBe('login-title');
  });

  it.each([
    [{ normal: '' }, '?normal'],
    [{ mode: 'normal' }, '?mode=normal']
  ])('普通模式 %s 展示可见标签、稳定 id、autocomplete 与密码显示开关', (query) => {
    const loginWrapper = mountLogin(query);

    expect(loginWrapper.find('.normal-login').exists()).toBe(true);
    expect(loginWrapper.find('.uias-login').exists()).toBe(false);

    const username = loginWrapper.find('#login-username');
    const password = loginWrapper.find('#login-password');
    expect(loginWrapper.find('label[for="login-username"]').text()).toContain('用户名');
    expect(loginWrapper.find('label[for="login-password"]').text()).toContain('密码');
    expect(username.attributes('autocomplete')).toBe('username');
    expect(password.attributes('autocomplete')).toBe('current-password');
    expect(password.attributes('show-password')).toBe('');
  });

  it('默认入口只展示 UIAS，普通账号入口与 UIAS 互斥，且页面不使用 emoji 结构图标', () => {
    const loginWrapper = mountLogin();

    expect(loginWrapper.find('.uias-login').exists()).toBe(true);
    expect(loginWrapper.find('.normal-login').exists()).toBe(false);
    expect(loginWrapper.find('#login-username').exists()).toBe(false);
    expect(loginWrapper.find('#login-password').exists()).toBe(false);
    expect(loginWrapper.text()).not.toMatch(/[\u{1F300}-\u{1FAFF}]/u);
    expect(loginWrapper.findAll('[aria-hidden="true"]').length).toBeGreaterThan(0);
  });

  it('UIAS 按钮直接跳转后端 redirect，不调用普通 UIAS 登录 API', async () => {
    const loginWrapper = mountLogin();

    await loginWrapper.find('button.btn-uniauth').trigger('click');

    expect(mocks.uniAuthLogin).not.toHaveBeenCalled();
    expect(window.location.href).toBe('/api/auth/uniauth/redirect');
  });
});

describe('普通登录提交状态与错误恢复', () => {
  it('提交中防重复、按钮 disabled，401 错误后恢复并可再次提交', async () => {
    let resolveLogin;
    const pendingLogin = new Promise((resolve) => { resolveLogin = resolve; });
    mocks.login.mockReturnValueOnce(pendingLogin)
      .mockResolvedValueOnce({
        empId: 'B001', username: 'userB', displayName: '用户B', roles: []
      });
    const loginWrapper = mountLogin({ normal: '' });
    await fillNormalCredentials(loginWrapper);

    const submit = loginWrapper.find('button.btn-login');
    await submit.trigger('click');
    await settle();
    expect(mocks.login).toHaveBeenCalledTimes(1);
    expect(submit.attributes('disabled')).toBeDefined();

    await submit.trigger('click');
    expect(mocks.login).toHaveBeenCalledTimes(1);

    resolveLogin(Promise.reject({ response: { status: 401 }, message: '用户名或密码错误' }));
    await settle();
    expect(mocks.messageError).toHaveBeenCalledWith('用户名或密码错误');
    expect(submit.attributes('disabled')).toBeUndefined();

    await submit.trigger('click');
    await settle();
    expect(mocks.login).toHaveBeenCalledTimes(2);
    expect(mocks.routerReplace).toHaveBeenCalledWith('/no-access');
  });
});
