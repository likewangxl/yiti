// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick, reactive } from 'vue';

const http = vi.hoisted(() => ({ get: vi.fn() }));
const api = vi.hoisted(() => ({
  loadPersonalDashboard: vi.fn(),
  buildPersonalDashboardModel: vi.fn()
}));
const router = vi.hoisted(() => ({ push: vi.fn(), back: vi.fn() }));
const store = vi.hoisted(() => ({
  user: { empId: 'E-1' }, displayName: '旧用户', orgName: '旧机构',
  setUser: vi.fn()
}));
const ui = vi.hoisted(() => ({ info: vi.fn(), warning: vi.fn(), error: vi.fn() }));
const authStore = vi.hoisted(() => ({ load: vi.fn().mockResolvedValue([]), hasUrl: vi.fn().mockReturnValue(true) }));

vi.mock('@/api/http', () => ({ API_BASE: '/api', default: http }));
vi.mock('@/api/personalDashboard', () => api);
vi.mock('@/stores/user', () => ({ useUserStore: () => store }));
vi.mock('@/stores/menu', () => ({ useMenuStore: () => authStore }));
vi.mock('@/stores/permission', () => ({ usePermissionStore: () => authStore }));
vi.mock('vue-router', () => ({ useRouter: () => router, useRoute: () => ({ fullPath: '/personal-dashboard' }) }));
vi.mock('element-plus', () => ({ ElMessage: ui }));
vi.mock('@/components/MarketingCustomerDetailDrawer.vue', () => ({
  default: { name: 'MarketingCustomerDetailDrawer', props: ['modelValue', 'customer', 'loading'], template: '<aside data-testid="customer-drawer" />' }
}));
vi.mock('@/components/TouchTaskDetailDialog.vue', () => ({
  default: { name: 'TouchTaskDetailDialog', props: ['modelValue', 'taskId', 'mode'], template: '<aside data-testid="touch-dialog" />' }
}));
vi.mock('../PersonalDashboard.vue', () => ({
  default: {
    name: 'PersonalDashboard',
    props: ['model', 'loading'],
    emits: ['refresh', 'back', 'navigate', 'fullscreen'],
    template: '<main data-testid="personal-dashboard-stub"><button data-action="refresh" @click="$emit(\'refresh\')">刷新</button><button data-action="back" @click="$emit(\'back\')">返回</button><button data-action="fullscreen" @click="$emit(\'fullscreen\')">全屏</button><button data-action="customer" @click="$emit(\'navigate\', { kind: \'customer\', id: \'C-1\' })">客户</button><button data-action="touch" @click="$emit(\'navigate\', { kind: \'touch\', id: \'T-1\', mode: \'supplement\' })">触达</button><button data-action="asset" @click="$emit(\'navigate\', { kind: \'asset\', id: \'A-1\' })">资产</button><button data-action="unknown" @click="$emit(\'navigate\', { kind: \'todo\', id: \'W-1\', bizType: \'UNKNOWN\' })">未知待办</button><button data-action="progress" @click="$emit(\'navigate\', { kind: \'progress\' })">进度</button></main>'
  }
}));

import PersonalDashboardPage from '../PersonalDashboardPage.vue';

const sources = {
  workspace: { status: 'fulfilled', value: { metricCards: [] } },
  todos: { status: 'fulfilled', value: { records: [], total: 0 } },
  touchPending: { status: 'fulfilled', value: { records: [], total: 0 } },
  touchInProgress: { status: 'fulfilled', value: { records: [], total: 0 } },
  customers: { status: 'fulfilled', value: { records: [], total: 0 } },
  assets: { status: 'fulfilled', value: { records: [], total: 0 } },
  supports: { status: 'fulfilled', value: { records: [], total: 0 } }
};

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function mountPage() {
  store.user = reactive(store.user);
  return mount(PersonalDashboardPage);
}

beforeEach(() => {
  http.get.mockReset();
  api.loadPersonalDashboard.mockReset();
  api.buildPersonalDashboardModel.mockReset();
  router.push.mockReset();
  router.back.mockReset();
  store.setUser.mockReset();
  Object.values(ui).forEach(fn => fn.mockReset());
  api.loadPersonalDashboard.mockResolvedValue(sources);
  api.buildPersonalDashboardModel.mockImplementation((value, identity) => ({ identity, metrics: {}, priorities: {}, customers: {}, progress: {}, refreshedAt: null }));
  http.get.mockResolvedValue({ empId: 'E-1', displayName: '新用户', mainOrgName: '新机构' });
});

describe('PersonalDashboardPage', () => {
  it('先严格确认当前 Session 身份，再加载聚合数据，并使用服务端新身份', async () => {
    const wrapper = mountPage();
    await flushPromises();

    expect(http.get).toHaveBeenCalledWith('/api/auth/current-user', { silent: true });
    expect(api.loadPersonalDashboard).toHaveBeenCalledTimes(1);
    expect(api.buildPersonalDashboardModel).toHaveBeenCalledWith(sources, { name: '新用户', orgName: '新机构' });
    expect(wrapper.vm.model.identity).toEqual({ name: '新用户', orgName: '新机构' });
    expect(store.setUser).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('当前用户工号变化时更新 store、重载授权，并且清理旧模型', async () => {
    http.get.mockResolvedValueOnce({ empId: 'E-2', displayName: '用户二', mainOrgName: '机构二' });
    const wrapper = mountPage();
    await flushPromises();

    expect(store.setUser).toHaveBeenCalledWith({ empId: 'E-2', displayName: '用户二', mainOrgName: '机构二' });
    expect(wrapper.vm.model.identity).toEqual({ name: '用户二', orgName: '机构二' });
    wrapper.unmount();
  });

  it('导航只接受可信 kind：客户严格取详情、触达传递服务端能力模式、未知待办回工作台', async () => {
    http.get.mockResolvedValueOnce({ empId: 'E-1', displayName: '用户一', mainOrgName: '机构一' });
    http.get.mockResolvedValueOnce({ id: 'C-1', custName: '客户甲' });
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('[data-action="customer"]').trigger('click');
    expect(http.get).toHaveBeenLastCalledWith('/api/marketing/customers/C-1', { silent: true });
    await wrapper.get('[data-action="touch"]').trigger('click');
    expect(wrapper.vm.touchTaskId).toBe('T-1');
    expect(wrapper.vm.touchTaskMode).toBe('supplement');
    await wrapper.get('[data-action="asset"]').trigger('click');
    expect(router.push).toHaveBeenCalledWith('/marketing/asset-projects/A-1');
    await wrapper.get('[data-action="unknown"]').trigger('click');
    expect(router.push).toHaveBeenLastCalledWith('/workspace');
    wrapper.unmount();
  });

  it('查看更多业务进度显示资产/中台两个入口，支持浏览器不支持全屏时友好提示', async () => {
    const wrapper = mountPage();
    await flushPromises();
    await wrapper.get('[data-action="progress"]').trigger('click');
    expect(wrapper.find('[role="dialog"]').text()).toContain('资产立项');
    expect(wrapper.find('[role="dialog"]').text()).toContain('中台支持');

    await wrapper.get('[data-action="fullscreen"]').trigger('click');
    expect(ui.info).toHaveBeenCalled();
    wrapper.unmount();
  });

  it('并发刷新以后发结果为准，迟到的身份/数据响应不能覆盖新模型', async () => {
    const firstIdentity = deferred();
    const firstSources = deferred();
    const secondSources = deferred();
    http.get.mockReturnValueOnce(firstIdentity.promise).mockResolvedValueOnce({
      empId: 'E-1', displayName: '当前用户', mainOrgName: '当前机构'
    });
    api.loadPersonalDashboard.mockReturnValueOnce(firstSources.promise).mockReturnValueOnce(secondSources.promise);
    api.buildPersonalDashboardModel.mockImplementation((value, identity) => ({
      ...value, identity, metrics: {}, priorities: {}, customers: {}, progress: {}, refreshedAt: null
    }));

    const wrapper = mountPage();
    await flushPromises();
    firstIdentity.resolve({ empId: 'E-1', displayName: '当前用户', mainOrgName: '当前机构' });
    await flushPromises();
    const refreshPromise = wrapper.vm.refresh();
    await flushPromises();
    secondSources.resolve({ ...sources, marker: 'new' });
    await refreshPromise;
    await flushPromises();

    firstSources.resolve({ ...sources, marker: 'old' });
    await flushPromises();
    expect(wrapper.vm.model.marker).toBe('new');
    wrapper.unmount();
  });

  it('旧客户详情迟到时不能覆盖后一次打开的客户或换代后的抽屉', async () => {
    const customerA = deferred();
    const customerB = deferred();
    http.get
      .mockResolvedValueOnce({ empId: 'E-1', displayName: '当前用户', mainOrgName: '当前机构' })
      .mockReturnValueOnce(customerA.promise)
      .mockReturnValueOnce(customerB.promise);

    const wrapper = mountPage();
    await flushPromises();
    wrapper.vm.navigateToItem({ kind: 'customer', id: 'A' });
    wrapper.vm.navigateToItem({ kind: 'customer', id: 'B' });
    customerB.resolve({ id: 'B', custName: '客户B' });
    await flushPromises();
    customerA.resolve({ id: 'A', custName: '客户A' });
    await flushPromises();

    expect(wrapper.findComponent({ name: 'MarketingCustomerDetailDrawer' }).props('customer')).toEqual({ id: 'B', custName: '客户B' });
    wrapper.unmount();
  });

  it('页面卸载后清理代际，迟到的聚合响应不再写回页面', async () => {
    const pendingSources = deferred();
    api.loadPersonalDashboard.mockReturnValueOnce(pendingSources.promise);
    const wrapper = mountPage();
    await flushPromises();
    wrapper.unmount();

    pendingSources.resolve({ ...sources, marker: 'late' });
    await flushPromises();
    expect(api.buildPersonalDashboardModel).not.toHaveBeenCalled();
  });

  it('退出登录时清空旧模型并停止加载，迟到的旧聚合响应不重新入屏', async () => {
    const pendingSources = deferred();
    api.loadPersonalDashboard.mockReturnValueOnce(pendingSources.promise);
    const wrapper = mountPage();
    await flushPromises();

    store.user.empId = null;
    await nextTick();
    pendingSources.resolve({ ...sources, marker: 'late-after-logout' });
    await flushPromises();

    expect(wrapper.vm.loading).toBe(false);
    expect(wrapper.vm.model.metrics.items).toEqual([]);
    expect(wrapper.vm.model.progress.items).toEqual([]);
    wrapper.unmount();
  });
});
