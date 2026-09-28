// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { ref } from 'vue';

const harness = vi.hoisted(() => ({ state: null }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn(), back: vi.fn() }) }));
vi.mock('../usePanoramaData', () => ({ usePanoramaData: () => harness.state }));
vi.mock('../PanoramaDashboard.vue', () => ({ default: {
  name: 'DashboardProbe',
  props: ['model', 'sourcePresentation'],
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

describe('分行 TEST 草稿演示补齐', () => {
  it('演示状态展开旧业务结构的收入绑定，关闭后恢复原配置且不修改发布包', async () => {
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
    expect(tabs().find(tab => tab.tabKey === 'income')?.corporateField).toBe('测试_直营对公营业收入');
    expect(probe().props('model').blockResults[64].测试_直营对公营业收入).toBe(600);
    expect(probe().props('model').blockResults[64].测试_直营零售营业收入).toBe(400);
    await wrapper.get('[data-action="toggle-demo-supplement"]').trigger('click');
    expect(tabs()).toHaveLength(1);
    expect(probe().props('model').blockResults[64].测试_直营对公营业收入).toBeUndefined();
    expect(JSON.stringify(currentView)).toBe(original);
    wrapper.unmount();
  });

  it('默认补齐空字段、显示性质说明，关闭后恢复源值，源模型保持不变', async () => {
    const original = JSON.stringify(harness.state.model.value);
    const wrapper = mount(PanoramaRuntime, { props: { view: view() } });
    expect(wrapper.get('[data-testid="branch-demo-supplement"]').text()).toContain('演示补齐');
    expect(wrapper.text()).toContain('非业务数据');
    expect(wrapper.get('[data-testid="dashboard-probe"]').text()).toContain('93.6');
    expect(JSON.stringify(harness.state.model.value)).toBe(original);
    await wrapper.get('[data-action="toggle-demo-supplement"]').trigger('click');
    expect(wrapper.get('[data-testid="dashboard-probe"]').text()).not.toContain('93.6');
    expect(wrapper.text()).toContain('开启演示补齐');
    await wrapper.get('[data-action="toggle-demo-supplement"]').trigger('click');
    expect(wrapper.get('[data-testid="dashboard-probe"]').text()).toContain('93.6');
    wrapper.unmount();
  });

  it.each([
    ['LIVE', 'draft', 'branch-overview-v1'],
    [undefined, 'draft', 'branch-overview-v1'],
    ['TEST', 'published', 'branch-overview-v1'],
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
    wrapper.unmount();
  });
});
