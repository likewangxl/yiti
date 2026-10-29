// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  listScreens: vi.fn(), getScreenCanvas: vi.fn(), listScreenDatasources: vi.fn(),
  listScreenMetricCatalog: vi.fn(async () => []), saveScreenCanvas: vi.fn()
}));
vi.mock('@/api/screen', () => ({ ...api }));
vi.mock('@/stores/permission', () => ({ usePermissionStore: () => ({ loaded: true, canAccess: () => true }) }));
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn() }) }));
vi.mock('../PanoramaDatasourcePicker.vue', () => ({ default: { props: ['sources', 'modelValue', 'disabled'], template: '<select v-bind="$attrs" :value="modelValue" />' } }));
vi.mock('../PanoramaDataVerification.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../BranchThumbnailChart.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../PanoramaIntegrationReadiness.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../PanoramaSettings.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../../presentation/editor/PresentationEditor.vue', () => ({ default: { template: '<div />' } }));
vi.mock('../MetricBindingComposer.vue', () => ({ default: { props: ['fieldSpecs'], template: '<div data-testid="composer-fields">{{ fieldSpecs.map(item => item.semantic).join(",") }}</div>' } }));
import PanoramaBindings from '../PanoramaBindings.vue';

const fixture = {
  screenId: 60, screenCode: 'SCR_BRANCH', canvasVersion: 12,
  canvasStyleJson: JSON.stringify({ presentation: {
    type: 'CODE', template: 'branch-overview-v1', displaySchemaVersion: 1,
    institutionRules: { allowedOperatingLevels: ['PRIMARY'], allowedOrgNatures: ['SECONDARY_BRANCH'] },
    display: { components: [{ componentId: 'main-card', componentType: 'METRIC_CARD', visible: true, order: 0, interaction: { action: 'NONE' },
      layoutRegion: 'HEADER', text: { titleMode: 'CUSTOM', title: '存款余额' }, format: { displayUnit: 'YUAN' },
      content: { mainField: 'deposit_col' }, dataRefs: [{ blockId: 55, role: 'PRIMARY', metricCode: 'M_DEPOSIT', metricName: '存款', dimension: 'ORG', unit: 'YUAN' }] }] }
  } }),
  canvasDraftJson: JSON.stringify({ components: [
    { id: 'main', component: 'ChartWidget', blockId: 55, propValue: { bindingKey: 'deposit' }, bindJson: JSON.stringify({ dsId: 901, period: 'LATEST', fields: { value: 'deposit_col' }, units: { value: 'YUAN' } }) },
    { id: 'city', component: 'ChartWidget', blockId: 60, propValue: { bindingKey: 'citySummary' }, bindJson: JSON.stringify({ dsId: 902, period: 'LATEST', fields: { cityCode: 'city_code', deposit: 'deposit_col', loan: 'loan_col', customers: 'customer_count', revenue: 'revenue_col' }, units: { deposit: 'YUAN', loan: 'YUAN', customers: 'COUNT', revenue: 'YUAN' } }) },
    { id: 'branch', component: 'ChartWidget', blockId: 61, propValue: { bindingKey: 'branchTrend' }, bindJson: JSON.stringify({ dsId: 903, period: 'LAST_6M_EOM', fields: { date: 'data_date', deposit: 'deposit_col', loan: 'loan_col' }, units: { deposit: 'YUAN', loan: 'YUAN' } }) }
  ] })
};

describe('PanoramaBindings schema1 drill slots', () => {
  it('暴露真实存在的城市/支行下钻槽并按槽位限制字段，保存重载不改变主图组件', async () => {
    api.listScreens.mockResolvedValue([{ id: 60, screenCode: 'SCR_BRANCH', screenName: '分行', bizLine: 'COMMON', viewLevel: 'PROVINCE' }]);
    let stored = structuredClone(fixture);
    api.getScreenCanvas.mockImplementation(async () => stored);
    api.saveScreenCanvas.mockImplementation(async payload => {
      stored = { ...stored, canvasVersion: stored.canvasVersion + 1, canvasStyleJson: JSON.stringify(payload.canvasStyle), canvasDraftJson: JSON.stringify({ components: payload.components }) };
      return stored;
    });
    api.listScreenDatasources.mockResolvedValue([
      { id: 901, dsName: 'main', status: 'ACTIVE', sourceKind: 'WIDE_TABLE', configJson: JSON.stringify({ fieldMeta: [{ col: 'deposit_col', role: 'METRIC', unit: 'YUAN' }] }) },
      { id: 902, dsName: 'city', status: 'ACTIVE', sourceKind: 'WIDE_TABLE', configJson: JSON.stringify({ fieldMeta: [{ col: 'city_code', role: 'DIM' }, { col: 'deposit_col', role: 'METRIC', unit: 'YUAN' }, { col: 'loan_col', role: 'METRIC', unit: 'YUAN' }, { col: 'customer_count', role: 'METRIC', unit: 'COUNT' }, { col: 'revenue_col', role: 'METRIC', unit: 'YUAN' }] }) },
      { id: 903, dsName: 'branch trend', status: 'ACTIVE', sourceKind: 'WIDE_TABLE', configJson: JSON.stringify({ fieldMeta: [{ col: 'data_date', role: 'DIM' }, { col: 'deposit_col', role: 'METRIC', unit: 'YUAN' }, { col: 'loan_col', role: 'METRIC', unit: 'YUAN' }] }) }
    ]);
    const create = () => mount(PanoramaBindings, { props: { screenId: 60 }, global: {
      stubs: { 'el-button': { template: '<button><slot /></button>' }, 'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' } },
      directives: { 'bp-overflow-tooltip': {} }
    } });
    const wrapper = create();
    await flushPromises();
    expect(wrapper.classes()).toContain('panorama-bindings--visual');
    expect(wrapper.get('[data-testid="configuration-heading"]').text()).toBe('配置信息');
    expect(wrapper.find('.panorama-bindings__slots').exists()).toBe(false);
    await wrapper.get('[data-config-key="main-card"]').trigger('click');
    await wrapper.get('[data-testid="component-title"]').setValue('可编辑存款标题');
    expect(wrapper.get('[data-config-key="main-card"]').text()).toContain('可编辑存款标题');
    await wrapper.get('[data-config-key="overview-loan"]').trigger('click');
    await wrapper.get('[data-config-key="main-card"]').trigger('click');
    expect(wrapper.get('[data-testid="component-title"]').element.value).toBe('可编辑存款标题');
    expect(api.saveScreenCanvas).not.toHaveBeenCalled();
    await vi.waitFor(() => expect(wrapper.find('[data-testid="drill-slot-citySummary"]').exists()).toBe(true));
    expect(wrapper.find('[data-testid="drill-slot-branchTrend"]').exists()).toBe(true);
    await wrapper.get('[data-testid="drill-slot-citySummary"]').trigger('click');
    expect(wrapper.get('[data-testid="component-title"]').element.disabled).toBe(true);
    expect(wrapper.get('[data-testid="composer-fields"]').text()).toBe('orgCode,cityCode,cityName,deposit,loan,customers,revenue');
    await wrapper.get('[data-testid="drill-slot-branchTrend"]').trigger('click');
    expect(wrapper.get('[data-testid="composer-fields"]').text()).toBe('date,deposit,loan');
    const originalDisplay = JSON.stringify(wrapper.vm.displaySession.presentation.display);
    await wrapper.get('[data-testid="drill-slot-citySummary"]').trigger('click');
    await wrapper.get('[data-testid="field-option-citySummary-deposit"]').setValue('loan_col');
    await wrapper.get('[data-testid="binding-save"]').trigger('click'); await flushPromises();
    expect(api.saveScreenCanvas, wrapper.text()).toHaveBeenCalled();
    expect(JSON.stringify(api.saveScreenCanvas.mock.lastCall[0].canvasStyle.presentation.display)).toBe(originalDisplay);
    wrapper.unmount();
    const reloaded = create(); await flushPromises();
    await reloaded.get('[data-testid="drill-slot-citySummary"]').trigger('click');
    expect(reloaded.get('[data-testid="field-option-citySummary-deposit"]').element.value).toBe('loan_col');
    reloaded.unmount();
  });
});
