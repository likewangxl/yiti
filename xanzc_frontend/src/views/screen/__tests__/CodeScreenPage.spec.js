// @vitest-environment happy-dom
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { reactive } from 'vue';
import { compileStyle, parse } from '@vue/compiler-sfc';

const { listAvailableScreens, queryScreenData, routerPush, branchStub, retailStub } = vi.hoisted(() => ({
  listAvailableScreens: vi.fn(),
  queryScreenData: vi.fn(),
  routerPush: vi.fn(),
  branchStub: { template: '<div data-testid="branch-dashboard"><div class="panorama-demo-badge" data-testid="inner-branch-demo-badge">inner branch badge</div>branch dashboard</div>' },
  retailStub: { template: '<div data-testid="retail-dashboard"><div class="retail-demo-badge" data-testid="inner-retail-demo-badge">inner retail badge</div>retail dashboard</div>' }
}));
const routeState = reactive({ params: { template: 'branch-overview-v1' } });
vi.mock('@/api/screen', () => ({ listAvailableScreens, queryScreenData }));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush }),
  useRoute: () => routeState
}));
vi.mock('../panorama/PanoramaDashboard.vue', () => ({ default: branchStub }));
vi.mock('../panorama/RetailDashboard.vue', () => ({ default: retailStub }));

import CodeScreenPage from '../CodeScreenPage.vue';
import { useUserStore } from '@/stores/user';

const branch = {
  screenCode: 'SCR_PROVINCE', screenName: '分行经营总览', viewLevel: 'PROVINCE', bizLine: 'COMMON',
  template: 'branch-overview-v1', dataMode: 'DEMO'
};
const retail = {
  screenCode: 'SCR_RETAIL_OVERVIEW', screenName: '零售经营总览', viewLevel: 'BRANCH', bizLine: 'RETAIL',
  template: 'retail-overview-v1', dataMode: 'DEMO'
};

let wrapper;
beforeEach(() => {
  vi.clearAllMocks();
  setActivePinia(createPinia());
  useUserStore().setUser({ empId: 'USER_A' });
  routeState.params.template = 'branch-overview-v1';
  listAvailableScreens.mockResolvedValue([branch, retail]);
});
afterEach(() => wrapper?.unmount());

async function mountPage() {
  wrapper = mount(CodeScreenPage);
  await flushPromises();
  return wrapper;
}

describe('CodeScreenPage', () => {
  it('rechecks protected catalog and renders branch demo with explicit non-business marker', async () => {
    const page = await mountPage();
    expect(page.find('[data-testid="branch-dashboard"]').exists()).toBe(true);
    expect(page.find('[data-demo="true"]').exists()).toBe(true);
    expect(page.text()).toContain('演示数据');
    expect(page.text()).toContain('非业务数据');
    expect(queryScreenData).not.toHaveBeenCalled();
  });

  it('renders the retail dashboard only for the authorized retail registration', async () => {
    routeState.params.template = 'retail-overview-v1';
    const page = await mountPage();
    expect(page.find('[data-testid="retail-dashboard"]').exists()).toBe(true);
    expect(page.find('[data-testid="branch-dashboard"]').exists()).toBe(false);
  });

  it('hides the dashboard internal demo badge because the page banner is the single marker', async () => {
    const page = await mountPage();
    expect(page.find('[data-testid="inner-branch-demo-badge"]').exists()).toBe(true);

    const source = readFileSync(resolve(process.cwd(), 'src/views/screen/CodeScreenPage.vue'), 'utf8');
    const descriptor = parse(source).descriptor;
    const compiled = compileStyle({
      source: descriptor.styles.find(style => style.scoped).content,
      filename: 'CodeScreenPage.vue',
      id: 'data-v-code-screen-page-test',
      scoped: true
    });
    expect(compiled.errors).toEqual([]);
    expect(compiled.code).toContain('.code-screen-page[data-v-code-screen-page-test] .panorama-demo-badge');
    expect(compiled.code).toContain('.code-screen-page[data-v-code-screen-page-test] .retail-demo-badge');
    expect(compiled.code).toMatch(/\.panorama-demo-badge[\s\S]*?display:\s*none/);
    expect(compiled.code).toMatch(/\.retail-demo-badge[\s\S]*?display:\s*none/);
  });

  it('fails closed for forbidden template catalog response', async () => {
    listAvailableScreens.mockResolvedValue([retail]);
    const page = await mountPage();
    expect(page.find('[data-testid="branch-dashboard"]').exists()).toBe(false);
    expect(page.find('[data-testid="code-screen-forbidden"]').exists()).toBe(true);
    expect(page.text()).toContain('无权访问');
  });

  it('fails closed for unknown template or non-demo data mode', async () => {
    routeState.params.template = 'unknown-v1';
    listAvailableScreens.mockResolvedValue([{ ...branch, template: 'unknown-v1' }]);
    let page = await mountPage();
    expect(page.find('[data-testid="branch-dashboard"]').exists()).toBe(false);
    expect(page.find('[data-testid="code-screen-unsupported"]').exists()).toBe(true);
    page.unmount();

    routeState.params.template = 'branch-overview-v1';
    listAvailableScreens.mockResolvedValue([{ ...branch, dataMode: 'LIVE' }]);
    page = await mountPage();
    expect(page.find('[data-testid="branch-dashboard"]').exists()).toBe(false);
    expect(page.find('[data-testid="code-screen-unsupported"]').exists()).toBe(true);
  });

  it('renders a forbidden state for a backend 403 without a dashboard', async () => {
    const error = new Error('forbidden');
    error.response = { status: 403 };
    listAvailableScreens.mockRejectedValue(error);
    const page = await mountPage();
    expect(page.find('[data-testid="code-screen-forbidden"]').exists()).toBe(true);
    expect(page.find('[data-testid="branch-dashboard"]').exists()).toBe(false);
  });

  it('does not render a late response after user and route switch', async () => {
    let resolveFirst;
    let resolveSecond;
    listAvailableScreens
      .mockReturnValueOnce(new Promise(resolve => { resolveFirst = resolve; }))
      .mockReturnValueOnce(new Promise(resolve => { resolveSecond = resolve; }));
    wrapper = mount(CodeScreenPage);
    routeState.params.template = 'retail-overview-v1';
    useUserStore().setUser({ empId: 'USER_B' });
    await flushPromises();
    resolveSecond([retail]);
    await flushPromises();
    expect(wrapper.find('[data-testid="retail-dashboard"]').exists()).toBe(true);
    resolveFirst([branch]);
    await flushPromises();
    expect(wrapper.find('[data-testid="retail-dashboard"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="branch-dashboard"]').exists()).toBe(false);
  });

  it('goes back to screen center and refresh only updates local demo time', async () => {
    const page = await mountPage();
    await page.find('[data-action="back-to-screen-center"]').trigger('click');
    expect(routerPush).toHaveBeenCalledWith('/screens');
    expect(listAvailableScreens).toHaveBeenCalledTimes(1);
    await page.find('[data-action="refresh-demo"]').trigger('click');
    expect(listAvailableScreens).toHaveBeenCalledTimes(1);
  });
});
