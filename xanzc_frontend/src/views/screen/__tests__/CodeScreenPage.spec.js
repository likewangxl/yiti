// @vitest-environment happy-dom
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { reactive } from 'vue';
import { parse } from '@vue/compiler-sfc';

const { listAvailableScreens, getScreenView, queryScreenData, routerPush, runtimeStub, runtimeRefresh } = vi.hoisted(() => {
  const runtimeRefresh = vi.fn();
  return {
  listAvailableScreens: vi.fn(),
  getScreenView: vi.fn(),
  queryScreenData: vi.fn(),
  routerPush: vi.fn(),
  runtimeRefresh: vi.fn(),
  runtimeStub: { props: ['view', 'context', 'backPath'], emits: ['back'], methods: { refresh: runtimeRefresh }, template: '<div data-testid="panorama-runtime">runtime</div>' },
  runtimeRefresh
  };
});
const routeState = reactive({ params: { template: 'branch-overview-v1' }, query: {} });
vi.mock('@/api/screen', () => ({ listAvailableScreens, getScreenView, queryScreenData }));
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush }),
  useRoute: () => routeState
}));
vi.mock('../panorama/PanoramaRuntime.vue', () => ({ default: runtimeStub }));

import CodeScreenPage from '../CodeScreenPage.vue';
import { useUserStore } from '@/stores/user';

const branch = {
  screenCode: 'SCR_PROVINCE', screenName: '分行经营总览', viewLevel: 'PROVINCE', bizLine: 'COMMON',
  template: 'branch-overview-v1', dataMode: 'TEST'
};
const retail = {
  screenCode: 'SCR_RETAIL_OVERVIEW', screenName: '零售经营总览', viewLevel: 'BRANCH', bizLine: 'RETAIL',
  template: 'retail-overview-v1', dataMode: 'LIVE'
};
const corporate = {
  screenCode: 'SCR_CORP_OVERVIEW', screenName: '对公经营总览', viewLevel: 'PROVINCE', bizLine: 'CORP',
  template: 'corporate-overview-v1', dataMode: 'LIVE'
};

function runtimePackage(template, dataClassification) {
  return {
    schemaVersion: 2,
    canvasStyle: {
      ...(dataClassification ? { dataClassification } : {}),
      presentation: { type: 'CODE', template }
    },
    components: [],
    bindSnapshots: {}
  };
}

function runtimeResponse(screenCode, template, dataClassification) {
  return {
    state: 'published', screenCode, runtimeSchemaVersion: 2,
    renderPackageJson: JSON.stringify(runtimePackage(template, dataClassification))
  };
}

let wrapper;
beforeEach(() => {
  vi.clearAllMocks();
  setActivePinia(createPinia());
  useUserStore().setUser({ empId: 'USER_A' });
  routeState.params.template = 'branch-overview-v1';
  routeState.query = {};
  listAvailableScreens.mockResolvedValue([branch, retail]);
  getScreenView.mockResolvedValue(runtimeResponse('SCR_PROVINCE', 'branch-overview-v1', 'TEST'));
});
afterEach(() => wrapper?.unmount());

async function mountPage() {
  wrapper = mount(CodeScreenPage);
  await flushPromises();
  return wrapper;
}

describe('CodeScreenPage', () => {
  it('拒绝目录中的 DEMO 模式并保持无运行时内容', async () => {
    routeState.params.template = 'corporate-overview-v1';
    listAvailableScreens.mockResolvedValue([{ ...corporate, dataMode: 'DEMO' }]);
    const page = await mountPage();
    expect(page.find('[data-testid="code-screen-unsupported"]').exists()).toBe(true);
    expect(page.find('[data-testid="panorama-runtime"]').exists()).toBe(false);
    expect(getScreenView).not.toHaveBeenCalled();
    expect(queryScreenData).not.toHaveBeenCalled();
  });
  it('对公页复核目录授权后读取可信发布包并展示已接入数据说明', async () => {
    routeState.params.template = 'corporate-overview-v1';
    listAvailableScreens.mockResolvedValue([corporate]);
    getScreenView.mockResolvedValue(runtimeResponse('SCR_CORP_OVERVIEW', 'corporate-overview-v1'));
    const page = await mountPage();
    expect(page.find('[data-testid="panorama-runtime"]').exists()).toBe(true);
    expect(page.attributes('data-mode')).toBe('LIVE');
    expect(page.text()).toContain('已接入数据');
    expect(page.text()).toContain('统计口径与环境见来源说明');
    expect(getScreenView).toHaveBeenCalledWith('SCR_CORP_OVERVIEW');
    expect(page.findComponent(runtimeStub).props('view')).toMatchObject({ screenCode: 'SCR_CORP_OVERVIEW', state: 'published' });
  });

  it('rechecks protected catalog and renders branch TEST runtime with explicit test marker', async () => {
    const page = await mountPage();
    expect(page.find('[data-testid="panorama-runtime"]').exists()).toBe(true);
    expect(page.find('[data-mode="TEST"]').exists()).toBe(true);
    expect(page.text()).toContain('非生产联调数据');
    expect(page.text()).not.toContain('测试');
    expect(page.attributes('aria-label')).toBe('非生产联调大屏');
    expect(page.text()).toContain('非生产业务数据');
    expect(getScreenView).toHaveBeenCalledWith('SCR_PROVINCE');
    const runtime = page.findComponent(runtimeStub);
    expect(runtime.props('view')).toMatchObject({ screenCode: 'SCR_PROVINCE', state: 'published' });
    expect(runtime.props('context')).toMatchObject({ screenCode: 'SCR_PROVINCE', runtimeSchemaVersion: 2 });
    expect(runtime.props('backPath')).toBe('/screens');
    runtime.vm.$emit('back');
    expect(routerPush).not.toHaveBeenCalled();
    expect(queryScreenData).not.toHaveBeenCalled();
  });

  it('renders the retail runtime only for an authorized TEST or LIVE registration', async () => {
    routeState.params.template = 'retail-overview-v1';
    listAvailableScreens.mockResolvedValue([retail]);
    getScreenView.mockResolvedValue(runtimeResponse('SCR_RETAIL_OVERVIEW', 'retail-overview-v1'));
    const page = await mountPage();
    expect(page.find('[data-testid="panorama-runtime"]').exists()).toBe(true);
    expect(page.text()).toContain('已接入数据');
    expect(page.find('[data-mode="DEMO"]').exists()).toBe(false);
    expect(getScreenView).toHaveBeenCalledWith('SCR_RETAIL_OVERVIEW');
  });

  it('对公页刷新调用运行时真实取数，而不是只更新本地演示时间', async () => {
    routeState.params.template = 'corporate-overview-v1';
    listAvailableScreens.mockResolvedValue([corporate]);
    getScreenView.mockResolvedValue(runtimeResponse('SCR_CORP_OVERVIEW', 'corporate-overview-v1'));
    const page = await mountPage();
    runtimeRefresh.mockClear();
    await page.find('[data-action="refresh-runtime"]').trigger('click');
    expect(runtimeRefresh).toHaveBeenCalledTimes(1);
  });

  it('does not reference hardcoded demo models or preview components', async () => {
    const source = readFileSync(resolve(process.cwd(), 'src/views/screen/CodeScreenPage.vue'), 'utf8');
    expect(parse(source).descriptor.scriptSetup?.content).not.toContain('retailDemoModel');
    expect(parse(source).descriptor.scriptSetup?.content).not.toContain('CorporatePreview');
  });

  it('fails closed for forbidden template catalog response', async () => {
    listAvailableScreens.mockResolvedValue([retail]);
    const page = await mountPage();
    expect(page.find('[data-testid="panorama-runtime"]').exists()).toBe(false);
    expect(page.find('[data-testid="code-screen-forbidden"]').exists()).toBe(true);
    expect(page.text()).toContain('无权访问');
  });

  it('fails closed for unknown template or non-demo data mode', async () => {
    routeState.params.template = 'unknown-v1';
    listAvailableScreens.mockResolvedValue([{ ...branch, template: 'unknown-v1' }]);
    let page = await mountPage();
    expect(page.find('[data-testid="panorama-runtime"]').exists()).toBe(false);
    expect(page.find('[data-testid="code-screen-unsupported"]').exists()).toBe(true);
    page.unmount();

    routeState.params.template = 'branch-overview-v1';
    listAvailableScreens.mockResolvedValue([{ ...branch, dataMode: 'LIVE' }]);
    page = await mountPage();
    expect(page.find('[data-testid="panorama-runtime"]').exists()).toBe(false);
    expect(page.find('[data-testid="code-screen-unsupported"]').exists()).toBe(true);
  });

  it('renders a forbidden state for a backend 403 without a dashboard', async () => {
    const error = new Error('forbidden');
    error.response = { status: 403 };
    listAvailableScreens.mockRejectedValue(error);
    const page = await mountPage();
    expect(page.find('[data-testid="code-screen-forbidden"]').exists()).toBe(true);
    expect(page.find('[data-testid="panorama-runtime"]').exists()).toBe(false);
  });

  it('fails closed when the view response belongs to another screen', async () => {
    getScreenView.mockResolvedValue(runtimeResponse('SCR_OTHER', 'branch-overview-v1'));
    const page = await mountPage();
    expect(page.find('[data-testid="panorama-runtime"]').exists()).toBe(false);
    expect(page.find('[data-testid="code-screen-error"]').exists()).toBe(true);
  });

  it('fails closed when the published runtime schema is not version 2', async () => {
    getScreenView.mockResolvedValue({ ...runtimeResponse('SCR_PROVINCE', 'branch-overview-v1'), runtimeSchemaVersion: 1 });
    const page = await mountPage();
    expect(page.find('[data-testid="panorama-runtime"]').exists()).toBe(false);
    expect(page.find('[data-testid="code-screen-error"]').exists()).toBe(true);
  });

  it('fails closed when the published CODE package has no immutable binding map', async () => {
    getScreenView.mockResolvedValue({
      state: 'published', screenCode: 'SCR_PROVINCE', runtimeSchemaVersion: 2,
      renderPackageJson: JSON.stringify({
        schemaVersion: 2,
        canvasStyle: { presentation: { type: 'CODE', template: 'branch-overview-v1' } },
        components: []
      })
    });
    const page = await mountPage();
    expect(page.find('[data-testid="panorama-runtime"]').exists()).toBe(false);
    expect(page.find('[data-testid="code-screen-error"]').exists()).toBe(true);
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
    getScreenView.mockResolvedValue(runtimeResponse('SCR_RETAIL_OVERVIEW', 'retail-overview-v1'));
    resolveSecond([retail]);
    await flushPromises();
    expect(wrapper.find('[data-testid="panorama-runtime"]').exists()).toBe(true);
    resolveFirst([branch]);
    await flushPromises();
    expect(wrapper.find('[data-testid="panorama-runtime"]').exists()).toBe(true);
  });

  it('goes back to screen center and refresh delegates to the runtime', async () => {
    const page = await mountPage();
    await page.find('[data-action="back-to-screen-center"]').trigger('click');
    expect(routerPush).toHaveBeenCalledWith('/screens');
    expect(listAvailableScreens).toHaveBeenCalledTimes(1);
    runtimeRefresh.mockClear();
    await page.find('[data-action="refresh-runtime"]').trigger('click');
    expect(listAvailableScreens).toHaveBeenCalledTimes(1);
    expect(runtimeRefresh).toHaveBeenCalledTimes(1);
    expect(page.find('[data-testid="runtime-updated-at"]').text()).toContain('本次查询/刷新时间');
  });

  it('page refresh invokes runtime refresh exactly once', async () => {
    const page = await mountPage();
    runtimeRefresh.mockClear();
    await page.find('[data-action="refresh-runtime"]').trigger('click');
    expect(runtimeRefresh).toHaveBeenCalledTimes(1);
    expect(page.find('[data-testid="runtime-updated-at"]').text()).toBeTruthy();
    expect(page.find('[data-testid="runtime-updated-at"]').text()).toContain('本次查询/刷新时间');
  });

  it('preserves navigation context in runtime and rejects an org outside target screen directory', async () => {
    routeState.query = {
      cityCode: '610100', orgCode: 'ORG-1', businessLine: 'COMMON', period: 'LATEST', metricKey: 'deposit',
      view: JSON.stringify({ zoom: 2 }), state: JSON.stringify({ page: 2 })
    };
    getScreenView.mockResolvedValue({
      ...runtimeResponse('SCR_PROVINCE', 'branch-overview-v1'),
      navigationRules: { allowedOperatingLevels: ['PRIMARY_BRANCH'], allowedOrgNatures: ['BRANCH'] },
      panoramaInstitutions: [{ orgCode: 'ORG-1', cityCode: '610100', operatingLevel: 'PRIMARY_BRANCH', orgNature: 'BRANCH' }]
    });
    const page = await mountPage();
    expect(page.findComponent(runtimeStub).props('context')).toMatchObject({
      screenCode: 'SCR_PROVINCE', orgCode: 'ORG-1', cityCode: '610100', businessLine: 'COMMON', period: 'LATEST', metricKey: 'deposit',
      navigationView: { zoom: 2 }, navigationState: { page: 2 }
    });

    page.unmount();
    routeState.query = { orgCode: 'OUTSIDE', businessLine: 'COMMON' };
    const forbidden = await mountPage();
    expect(forbidden.find('[data-testid="code-screen-forbidden"]').exists()).toBe(true);
    expect(forbidden.find('[data-testid="panorama-runtime"]').exists()).toBe(false);
  });

  it('rejects a tampered businessLine query instead of rendering the route template', async () => {
    routeState.query = { businessLine: 'RETAIL' };
    const page = await mountPage();
    expect(page.find('[data-testid="code-screen-unsupported"]').exists()).toBe(true);
    expect(page.find('[data-testid="panorama-runtime"]').exists()).toBe(false);
  });
});
