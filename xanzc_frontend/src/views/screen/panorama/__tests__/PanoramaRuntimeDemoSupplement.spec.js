// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { ref } from 'vue';

const harness = vi.hoisted(() => ({ state: null }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn(), back: vi.fn() }) }));
vi.mock('../usePanoramaData', () => ({ usePanoramaData: () => harness.state }));
vi.mock('../PanoramaDashboard.vue', () => ({ default: {
  name: 'DashboardProbe',
  props: ['model', 'sourcePresentation', 'loading', 'error'],
  template: '<main data-testid="dashboard-probe">{{ JSON.stringify(model.blockResults) }}</main>'
} }));
vi.mock('../CorporateDashboard.vue', () => ({ default: { template: '<main>对公</main>' } }));
vi.mock('../RetailDashboard.vue', () => ({ default: { template: '<main>零售</main>' } }));

import PanoramaRuntime from '../PanoramaRuntime.vue';

function view(classification = 'TEST', state = 'draft', template = 'branch-overview-v1') {
  return { screenCode: 'SCR_PROVINCE', state, renderPackage: { canvasStyle: {
    dataClassification: classification,
    presentation: { type: 'CODE', template, displaySchemaVersion: 1, display: { components: [{
      componentId: 'business-corp-deposit-rate', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', visible: true,
      content: { mainField: '测试_对公存款目标完成率' }, dataRefs: [{ blockId: 31, unit: 'PERCENT' }]
    }] } }
  } } };
}

beforeEach(() => {
  harness.state = {
    model: ref({ dataDate: '2026-09-21', blockResults: { 31: { rows: [{}] } }, quality: { status: 'STALE' } }),
    loading: ref(false), error: ref(''), slotIssues: ref({}), lastQueriedAt: ref(''),
    refresh: vi.fn(), selectBranch: vi.fn()
  };
});

describe('分行大屏运行态不合成指标数据', () => {
  it('不扩展收入绑定或补齐构成数据，切换服务端分类时保留源配置', async () => {
    const currentView = view();
    currentView.renderPackage.canvasStyle.presentation.display.components.push({
      componentId: 'legacy-composition-64', componentType: 'COMPOSITION_TABS', layoutRegion: 'LEFT', visible: true,
      content: { tabs: [{ tabKey: 'business-structure', corporateField: '测试_直营对公存款',
        retailField: '测试_直营零售存款', totalField: '', unit: 'YUAN' }] },
      dataRefs: [{ blockId: 64, role: 'PRIMARY', unit: 'YUAN' }]
    });
    harness.state.model.value.blockResults[64] = { 测试_直营营业收入: 1000, rows: [{ 测试_直营营业收入: 1000 }] };
    const original = JSON.stringify(currentView);
    const wrapper = mount(PanoramaRuntime, { props: { view: currentView } });
    const probe = () => wrapper.getComponent({ name: 'DashboardProbe' });
    const tabs = () => probe().props('sourcePresentation').displayPresentation.display.components[1].content.tabs;
    expect(tabs()).toHaveLength(1);
    expect(tabs().find(tab => tab.tabKey === 'income')).toBeUndefined();
    expect(probe().props('model').blockResults[64].测试_直营对公营业收入).toBeUndefined();
    expect(probe().props('model').blockResults[64].测试_直营零售营业收入).toBeUndefined();
    const liveView = view('LIVE');
    liveView.renderPackage.canvasStyle.presentation.display.components.push(currentView.renderPackage.canvasStyle.presentation.display.components[1]);
    await wrapper.setProps({ view: liveView });
    expect(tabs()).toHaveLength(1);
    expect(probe().props('model').blockResults[64].测试_直营对公营业收入).toBeUndefined();
    expect(JSON.stringify(currentView)).toBe(original);
    wrapper.unmount();
  });

  it('TEST 草稿保留真实值和缺值，不派生完成率、收入、结算存款、历史或年度比较', async () => {
    const currentView = view();
    const components = currentView.renderPackage.canvasStyle.presentation.display.components;
    components.push(
      { componentId: 'fee-card', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', visible: true,
        content: { mainField: '测试_直营中间业务收入' }, dataRefs: [{ blockId: 31, unit: 'YUAN' }] },
      { componentId: 'legacy-composition-64', componentType: 'COMPOSITION_TABS', layoutRegion: 'LEFT', visible: true,
        content: { tabs: [{ tabKey: 'business-structure', corporateField: '测试_直营对公存款',
          retailField: '测试_直营零售存款', totalField: '', unit: 'YUAN' }] },
        dataRefs: [{ blockId: 64, role: 'PRIMARY', unit: 'YUAN' }] },
      { componentId: 'trend-57', componentType: 'TREND', layoutRegion: 'CENTER', visible: true,
        dataRefs: [{ blockId: 57, role: 'PRIMARY' }] }
    );
    const sourceModel = harness.state.model.value;
    sourceModel.blockResults[31] = {
      测试_对公存款目标完成率: 0,
      测试_直营营业收入: 1000,
      rows: [{ data_date: '2026-09-21', 测试_对公存款目标完成率: 0, 测试_直营营业收入: 1000 }]
    };
    sourceModel.blockResults[64] = {
      测试_直营营业收入: 1000,
      rows: [{ data_date: '2026-09-21', 测试_直营营业收入: 1000 }]
    };
    sourceModel.blockResults[57] = { rows: [{ data_date: '2026-09-21' }] };
    sourceModel.kpis = [{ key: 'deposit', value: 100, unit: 'YUAN', dataDate: '2026-09-21', comparisons: {} }];
    const original = JSON.stringify(harness.state.model.value);
    const originalPresentation = JSON.stringify(currentView.renderPackage.canvasStyle.presentation);
    const wrapper = mount(PanoramaRuntime, { props: { view: currentView } });
    const probe = wrapper.getComponent({ name: 'DashboardProbe' });
    const output = probe.props('model');
    expect(wrapper.find('[data-testid="branch-demo-supplement"]').exists()).toBe(false);
    expect(output.blockResults[31]['测试_对公存款目标完成率']).toBe(0);
    expect(output.blockResults[31].rows[0]['测试_直营中间业务收入']).toBeUndefined();
    expect(output.blockResults[31]['对公结算性存款余额-机构']).toBeUndefined();
    expect(output.blockResults[31]['零售结算性存款余额-机构']).toBeUndefined();
    expect(output.blockResults[64]['测试_直营对公营业收入']).toBeUndefined();
    expect(output.blockResults[64]['测试_直营零售营业收入']).toBeUndefined();
    expect(output.blockResults[57].rows).toHaveLength(1);
    expect(output.blockResults[57].rows[0]['对公结算性存款余额-机构']).toBeUndefined();
    expect(output.kpis[0].comparisons).toEqual({});
    expect(JSON.stringify(output)).not.toContain('93.6');
    expect(JSON.stringify(harness.state.model.value)).toBe(original);
    expect(JSON.stringify(currentView.renderPackage.canvasStyle.presentation)).toBe(originalPresentation);
    wrapper.unmount();
  });

  it('TEST 分行 schema1 已发布且全辖时仍不补充指标', () => {
    const wrapper = mount(PanoramaRuntime, { props: { view: view('TEST', 'published') } });
    expect(wrapper.get('[data-testid="dashboard-probe"]').text()).not.toContain('93.6');
    wrapper.unmount();
  });

  it('已发布旧 schema 无新版演示补齐', () => {
    const currentView = view('TEST', 'published');
    delete currentView.renderPackage.canvasStyle.presentation.displaySchemaVersion;
    const wrapper = mount(PanoramaRuntime, { props: { view: currentView } });
    expect(wrapper.get('[data-testid="dashboard-probe"]').text()).not.toContain('93.6');
    wrapper.unmount();
  });

  it.each([
    ['LIVE', 'draft', 'branch-overview-v1'],
    ['LIVE', 'published', 'branch-overview-v1'],
    [undefined, 'draft', 'branch-overview-v1'],
    ['TEST', 'review', 'branch-overview-v1'],
    ['TEST', 'draft', 'corporate-overview-v1'],
    ['TEST', 'draft', 'retail-overview-v1']
  ])('分类%s、状态%s、模板%s不提供草稿演示补齐', (classification, state, template) => {
    const currentView = view(classification, state, template);
    if (classification === undefined) delete currentView.renderPackage.canvasStyle.dataClassification;
    const wrapper = mount(PanoramaRuntime, { props: { view: currentView } });
    expect(wrapper.find('[data-testid="branch-demo-supplement"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('93.6');
    wrapper.unmount();
  });

  it.each([{ orgCode: 'O1' }, { cityCode: '610100' }, { empId: 'E1' }])('局部范围 %j 不补入全辖示例', context => {
    const wrapper = mount(PanoramaRuntime, { props: { view: view(), context } });
    expect(wrapper.find('[data-testid="branch-demo-supplement"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('93.6');
    wrapper.unmount();
  });

  it.each(['loading', 'error', 'forbidden', 'slot-forbidden', 'slot-error', 'retained-query-error'])('%s 时不补齐或隐藏数据故障', kind => {
    if (kind === 'loading') harness.state.loading.value = true;
    if (kind === 'error') harness.state.error.value = '数据查询失败';
    if (kind === 'forbidden') harness.state.model.value.permissionStatus = 'FORBIDDEN';
    if (kind === 'slot-forbidden') harness.state.slotIssues.value = { deposit: [{ code: 'HTTP_403', message: '无权查询' }] };
    if (kind === 'slot-error') {
      harness.state.slotIssues.value = { deposit: [{ code: 'REQUEST_FAILED', message: '查询失败' }] };
      harness.state.model.value.sourceQualities = { deposit: { status: 'STALE' } };
    }
    if (kind === 'retained-query-error') harness.state.model.value.qualityGuard = { status: 'STALE', code: 'REQUEST_FAILED' };
    const wrapper = mount(PanoramaRuntime, { props: { view: view() } });
    expect(wrapper.find('[data-testid="branch-demo-supplement"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('93.6');
    if (kind === 'loading') expect(wrapper.getComponent({ name: 'DashboardProbe' }).props('loading')).toBe(true);
    if (kind === 'error') expect(wrapper.getComponent({ name: 'DashboardProbe' }).props('error')).toBe('数据查询失败');
    wrapper.unmount();
  });
});
