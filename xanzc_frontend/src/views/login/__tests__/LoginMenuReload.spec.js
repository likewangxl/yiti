// @vitest-environment happy-dom
// 换用户重新登录后左侧导航残留回归测试。
// 根因：logout 走 SPA 内跳转不刷新页面，menu store 的 loaded=true 与旧菜单树常驻内存；
// 下一个用户登录后 AppSidebar 的 menuStore.load()（非 force）被 loaded 拦下，
// 渲染的仍是上一个用户的菜单。修复：登录成功后强制 menuStore.load(true) 重拉。
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick } from 'vue';
import { createPinia, setActivePinia } from 'pinia';

const routerReplace = vi.fn();
vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: routerReplace, push: vi.fn() }),
  // ?normal → 普通账号密码登录模式
  useRoute: () => ({ query: { normal: '' } })
}));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));
vi.mock('@/api/http', () => ({ USE_MOCK: false }));
vi.mock('@/api/auth', () => ({
  login: vi.fn().mockResolvedValue({ empId: 'B001', username: 'userB', displayName: '用户B', roles: [] }),
  uniAuthLogin: vi.fn(),
  getMyMenus: vi.fn()
}));

import { getMyMenus } from '@/api/auth';
import { useMenuStore } from '@/stores/menu';
import Login from '../Index.vue';

const passthrough = (name) => ({ name, template: '<div><slot /></div>' });
const stubs = {
  // el-form 桩提供 validate（组件经 ref 调用）
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

describe('登录成功后菜单强制重拉（换用户菜单残留回归）', () => {
  it('上一用户菜单已缓存(loaded=true)时，新用户登录成功即重拉出新菜单', async () => {
    const menuStore = useMenuStore();
    // 模拟上一个用户 A 的会话残留：菜单已加载
    getMyMenus.mockResolvedValueOnce([{ menuName: 'A的菜单', resourceUrl: '/a' }]);
    await menuStore.load();
    expect(menuStore.loaded).toBe(true);
    expect(menuStore.tree[0].menuName).toBe('A的菜单');

    // 后端按新用户 B 的角色返回新菜单
    getMyMenus.mockResolvedValue([{ menuName: 'B的菜单', resourceUrl: '/b' }]);

    wrapper = mount(Login, { global: { plugins: [pinia], stubs } });
    const inputs = wrapper.findAll('.inp-stub');
    await inputs[0].setValue('userB');
    await inputs[1].setValue('pwd123456');
    await wrapper.findAll('button').find(b => b.text().includes('登')).trigger('click');
    await settle();

    // 修复前：load() 无 force 被 loaded=true 拦下，tree 仍是 A 的菜单
    expect(menuStore.tree[0].menuName, '登录成功后应强制重拉当前用户菜单').toBe('B的菜单');
    expect(routerReplace).toHaveBeenCalledWith('/workspace');
  });
});
