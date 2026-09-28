// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({ listAvailableScreens: vi.fn(), getScreenView: vi.fn() }));
const router = vi.hoisted(() => ({ push: vi.fn(), back: vi.fn() }));
const harness = vi.hoisted(() => ({ state: null }));

vi.mock('@/api/screen', () => api);
vi.mock('vue-router', () => ({ useRouter: () => router }));
vi.mock('../PanoramaDashboard.vue', () => ({ default: { name: 'PanoramaDashboard', template: '<main>分行内容</main>' } }));
vi.mock('../CorporateDashboard.vue', () => ({ default: {
  name: 'CorporateDashboard',
  emits: ['branch-select'],
  template: '<main><button data-action="select-branch" @click="$emit(\'branch-select\', \'126\')">机构</button></main>'
} }));
vi.mock('../RetailDashboard.vue', () => ({ default: {
  name: 'RetailDashboard',
  emits: ['branch-select'],
  template: '<main><button data-action="select-branch" @click="$emit(\'branch-select\', \'126\')">机构</button></main>'
} }));
vi.mock('../usePanoramaData', () => ({ usePanoramaData: () => harness.state }));

import PanoramaRuntime from '../PanoramaRuntime.vue';

const rules = Object.freeze({
  allowedOperatingLevels: ['PRIMARY_BRANCH'],
  allowedOrgNatures: ['BRANCH'],
  displayOrgCodes: ['126', '169']
});

function renderPackage(template) {
  return {
    schemaVersion: 2,
    canvasStyle: {
      dataClassification: 'TEST',
      presentation: { type: 'CODE', template }
    },
    components: [],
    bindSnapshots: {}
  };
}

function branchSource({ state = 'draft', orgCode = '126', cityCode = '610100', known = true, displayOrgCodes = rules.displayOrgCodes } = {}) {
  const institution = { orgCode, orgName: `${orgCode}机构`, cityCode };
  if (known) {
    institution.operatingLevel = 'PRIMARY_BRANCH';
    institution.orgNature = 'BRANCH';
  }
  return {
    screenCode: 'SCR_PROVINCE',
    state,
    runtimeSchemaVersion: 2,
    renderPackage: renderPackage('branch-overview-v1'),
    institutionRules: { ...rules, displayOrgCodes },
    panoramaInstitutions: [institution]
  };
}

function currentView(template = 'corporate-overview-v1', orgCode = '126', cityCode = '610100', known = false) {
  const institution = { orgCode, orgName: `${orgCode}当前机构`, cityCode };
  if (known) {
    institution.operatingLevel = 'PRIMARY_BRANCH';
    institution.orgNature = 'BRANCH';
  }
  return {
    screenCode: template === 'corporate-overview-v1' ? 'SCR_CORP_OVERVIEW' : 'SCR_RETAIL_OVERVIEW',
    state: 'published',
    runtimeSchemaVersion: 2,
    renderPackage: renderPackage(template),
    institutionRules: { ...rules, displayOrgCodes: [orgCode] },
    panoramaInstitutions: [institution]
  };
}

function mountRuntime({ template = 'corporate-overview-v1', context = {}, view = currentView(template) } = {}) {
  return mount(PanoramaRuntime, {
    props: {
      view,
      context: {
        businessLine: template === 'corporate-overview-v1' ? 'CORP' : 'RETAIL',
        cityCode: '610100',
        metricKey: 'deposit',
        ...context
      }
    }
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  harness.state = {
    model: { value: { kpis: [], issues: [] } },
    loading: { value: false },
    error: { value: '' },
    slotIssues: { value: {} },
    refresh: vi.fn(),
    selectBranch: vi.fn()
  };
  api.listAvailableScreens.mockResolvedValue([
    { screenCode: 'SCR_PROVINCE', template: 'branch-overview-v1', dataMode: 'LIVE' }
  ]);
  api.getScreenView.mockImplementation(async (_screenCode, preview) => preview === 'draft'
    ? branchSource({ state: 'draft', known: true })
    : branchSource({ state: 'published', known: false }));
});

describe('PanoramaRuntime 对公与零售机构源屏复核', () => {
  it('零售显式 draft 源屏复核成功后，按源机构城市进入固定支行页', async () => {
    const wrapper = mountRuntime({
      template: 'retail-overview-v1',
      context: { sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' },
      view: currentView('retail-overview-v1', '126', '610100', false)
    });

    await wrapper.get('[data-action="select-branch"]').trigger('click');
    await flushPromises();

    expect(api.getScreenView).toHaveBeenCalledWith('SCR_PROVINCE', 'draft');
    expect(router.push).toHaveBeenCalledWith({
      name: 'BranchOperatingPage',
      query: {
        cityCode: '610100', orgCode: '126', businessLine: 'RETAIL', metricKey: 'deposit',
        sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft'
      }
    });
    wrapper.unmount();
  });

  it('对公无 query 时复核 published 目录，层级缺失才安全回读 draft 源屏', async () => {
    const wrapper = mountRuntime({
      template: 'corporate-overview-v1',
      view: currentView('corporate-overview-v1', '126', '610100', false)
    });

    await wrapper.get('[data-action="select-branch"]').trigger('click');
    await flushPromises();

    expect(api.listAvailableScreens).toHaveBeenCalledTimes(1);
    expect(api.getScreenView).toHaveBeenNthCalledWith(1, 'SCR_PROVINCE');
    expect(api.getScreenView).toHaveBeenNthCalledWith(2, 'SCR_PROVINCE', 'draft');
    expect(router.push).toHaveBeenCalledWith({
      name: 'BranchOperatingPage',
      query: {
        cityCode: '610100', orgCode: '126', businessLine: 'CORP', metricKey: 'deposit',
        sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft'
      }
    });
    wrapper.unmount();
  });

  it('published 旧源屏缺少 institutionRules 时仍只允许回读已授权 draft 并复核最终规则', async () => {
    const publishedWithoutRules = { ...branchSource({ state: 'published', known: false }), institutionRules: null };
    api.getScreenView.mockImplementation(async (_screenCode, preview) => preview === 'draft'
      ? branchSource({ state: 'draft', known: true }) : publishedWithoutRules);
    const wrapper = mountRuntime({ template: 'corporate-overview-v1' });

    await wrapper.get('[data-action="select-branch"]').trigger('click');
    await flushPromises();

    expect(api.getScreenView).toHaveBeenNthCalledWith(1, 'SCR_PROVINCE');
    expect(api.getScreenView).toHaveBeenNthCalledWith(2, 'SCR_PROVINCE', 'draft');
    expect(router.push).toHaveBeenCalledWith(expect.objectContaining({
      query: expect.objectContaining({ sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' })
    }));
    wrapper.unmount();
  });

  it('published 层级已明确时沿用 published 源；不凭机构名称推断层级', async () => {
    api.getScreenView.mockResolvedValueOnce(branchSource({ state: 'published', known: true }));
    const wrapper = mountRuntime({ template: 'corporate-overview-v1' });

    await wrapper.get('[data-action="select-branch"]').trigger('click');
    await flushPromises();

    expect(api.getScreenView).toHaveBeenCalledTimes(1);
    expect(api.getScreenView).toHaveBeenCalledWith('SCR_PROVINCE');
    expect(router.push).toHaveBeenCalledWith(expect.objectContaining({
      query: expect.objectContaining({ sourceScreenCode: 'SCR_PROVINCE' })
    }));
    expect(router.push.mock.calls[0][0].query).not.toHaveProperty('sourcePreview');
    wrapper.unmount();
  });

  it('当前机构未授权、源屏明确排除、层级未知或城市冲突时拒绝导航', async () => {
    const cases = [
      { view: currentView('corporate-overview-v1', '999', '610100', false), context: {}, calls: 0 },
      { view: currentView('corporate-overview-v1', '126', '610100', false), source: branchSource({ displayOrgCodes: ['169'] }) },
      { view: currentView('corporate-overview-v1', '126', '610100', false), source: branchSource({ known: false }) },
      { view: currentView('corporate-overview-v1', '126', '610100', false), source: branchSource({ cityCode: '610300' }) }
    ];

    for (const entry of cases) {
      vi.clearAllMocks();
      api.listAvailableScreens.mockResolvedValue([
        { screenCode: 'SCR_PROVINCE', template: 'branch-overview-v1', dataMode: 'LIVE' }
      ]);
      api.getScreenView.mockResolvedValue(entry.source || branchSource({ state: 'published', known: false }));
      const wrapper = mountRuntime({ view: entry.view, context: entry.context || {} });
      await wrapper.get('[data-action="select-branch"]').trigger('click');
      await flushPromises();
      expect(router.push).not.toHaveBeenCalled();
      if (entry.calls === 0) expect(api.getScreenView).not.toHaveBeenCalled();
      wrapper.unmount();
      router.push.mockClear();
    }
  });

  it('源屏身份、状态、模板或 schema 不匹配时 fail-close，且不把 query 当凭据', async () => {
    const invalidSources = [
      { ...branchSource(), screenCode: 'SCR_OTHER' },
      { ...branchSource(), state: 'published' },
      { ...branchSource(), runtimeSchemaVersion: 1 },
      { ...branchSource(), renderPackage: renderPackage('corporate-overview-v1') },
      { ...branchSource(), renderPackage: null },
      { ...branchSource(), institutionRules: null }
    ];
    for (const source of invalidSources) {
      vi.clearAllMocks();
      api.getScreenView.mockResolvedValue(source);
      const wrapper = mountRuntime({
        template: 'retail-overview-v1',
        context: { sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' },
        view: currentView('retail-overview-v1', '126', '610100', false)
      });
      await wrapper.get('[data-action="select-branch"]').trigger('click');
      await flushPromises();
      expect(router.push).not.toHaveBeenCalled();
      wrapper.unmount();
      router.push.mockClear();
    }
  });

  it('双击机构、上下文变化和卸载都会废弃迟到源屏响应', async () => {
    let releaseFirst;
    const first = new Promise(resolve => { releaseFirst = resolve; });
    api.getScreenView.mockImplementationOnce(() => first).mockResolvedValueOnce(branchSource({ known: true }));
    const wrapper = mountRuntime({
      template: 'retail-overview-v1',
      context: { sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' },
      view: currentView('retail-overview-v1', '126', '610100', false)
    });
    const button = wrapper.get('[data-action="select-branch"]');
    await button.trigger('click');
    await button.trigger('click');
    await flushPromises();
    expect(router.push).toHaveBeenCalledTimes(1);
    releaseFirst(branchSource({ known: true }));
    await flushPromises();
    expect(router.push).toHaveBeenCalledTimes(1);
    wrapper.unmount();

    router.push.mockClear();
    let releaseContext;
    const contextPending = new Promise(resolve => { releaseContext = resolve; });
    api.getScreenView.mockReturnValue(contextPending);
    const changed = mountRuntime({
      template: 'retail-overview-v1',
      context: { sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' },
      view: currentView('retail-overview-v1', '126', '610100', false)
    });
    await changed.get('[data-action="select-branch"]').trigger('click');
    await changed.setProps({ context: { businessLine: 'RETAIL', cityCode: '610500', metricKey: 'loan', sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' } });
    releaseContext(branchSource({ known: true }));
    await flushPromises();
    expect(router.push).not.toHaveBeenCalled();
    changed.unmount();

    let releaseUnmount;
    const unmountPending = new Promise(resolve => { releaseUnmount = resolve; });
    api.getScreenView.mockReturnValue(unmountPending);
    const disposed = mountRuntime({
      template: 'retail-overview-v1',
      context: { sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' },
      view: currentView('retail-overview-v1', '126', '610100', false)
    });
    await disposed.get('[data-action="select-branch"]').trigger('click');
    disposed.unmount();
    releaseUnmount(branchSource({ known: true }));
    await flushPromises();
    expect(router.push).not.toHaveBeenCalled();
  });
});
