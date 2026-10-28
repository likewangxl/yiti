// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const fixture = vi.hoisted(() => ({
  screen: { id: 19, screenCode: 'SCR_BUSINESS', screenName: '业务配置屏', bizLine: 'COMMON', orgScopeMode: 'LEGACY_CONTEXT', status: 'ACTIVE' },
  binding: { dsId: 77, period: 'LATEST', fields: { value: 'old_balance' }, units: { value: 'YUAN' } }
}));
const api = vi.hoisted(() => ({ save: vi.fn() }));
const { permissionStore } = vi.hoisted(() => ({ permissionStore: {
  loaded: true, canAccess: vi.fn(() => true), load: vi.fn()
} }));
vi.mock('@/stores/permission', () => ({ usePermissionStore: () => permissionStore }));
vi.mock('@/api/screen', () => ({
  listScreens: vi.fn(async () => [fixture.screen]),
  getScreenCanvas: vi.fn(async () => ({ screenId: 19, screenCode: 'SCR_BUSINESS', canvasVersion: 4,
    canvasStyleJson: JSON.stringify({ presentation: { type: 'CODE', template: 'branch-overview-v1' } }),
    canvasDraftJson: JSON.stringify({ components: [{ id: 'deposit', component: 'ChartWidget', blockId: 41,
      propValue: { bindingKey: 'deposit' }, bindJson: JSON.stringify(fixture.binding) }] }) })),
  listScreenDatasources: vi.fn(async () => [{ id: 77, dsName: '已认证存款指标', status: 'ACTIVE', bizLine: 'COMMON', dsType: 'SINGLE', sourceKind: 'WIDE_TABLE',
    configJson: JSON.stringify({ schemaVersion: 2, table: 'ORG_INDEX_RESULT', subjectCol: 'org_code', aggregation: { groupBy: 'NONE' },
      fieldMeta: [{ col: 'old_balance', alias: '原存款余额', role: 'METRIC', semantic: 'deposit', unit: 'YUAN' },
        { col: 'balance', alias: '存款余额', role: 'METRIC', semantic: 'deposit', unit: 'YUAN' }] }), timeParamJson: '["LATEST"]' }]),
  getScreenView: vi.fn(), queryScreenData: vi.fn(), saveScreenCanvas: api.save,
  publishScreenCanvas: vi.fn(), discardScreenCanvas: vi.fn(), listOrgProfiles: vi.fn(), listOrgGroups: vi.fn(),
  listScreenMetricCatalog: vi.fn(async () => []), saveScreenDatasource: vi.fn(), tryRunScreenDatasource: vi.fn()
}));
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn() }) }));
import PanoramaBindings from '../PanoramaBindings.vue';
import { getScreenCanvas, listScreenDatasources, listScreens } from '@/api/screen';

const stubs = { PanoramaDataVerification: true, PanoramaDatasourcePicker: true,
  'el-button': { template: '<button><slot /></button>' },
  'el-dialog': { props: ['modelValue'], template: '<div v-if="modelValue"><slot name="header" /><slot /><slot name="footer" /></div>' } };
async function page() {
  const wrapper = mount(PanoramaBindings, { global: { stubs, directives: { 'bp-overflow-tooltip': {} } } });
  await flushPromises();
  return wrapper;
}

describe('业务人员配置指标', () => {
  it('明确声明TEST的屏显示联调提示，不把测试目录包装成正式经营目录', async () => {
    getScreenCanvas.mockResolvedValueOnce({ screenId: 19, screenCode: 'SCR_BUSINESS', canvasVersion: 4,
      canvasStyleJson: JSON.stringify({ dataClassification: 'TEST', presentation: { type: 'CODE', template: 'branch-overview-v1' } }),
      canvasDraftJson: JSON.stringify({ components: [{ id: 'deposit', component: 'ChartWidget', blockId: 41,
        propValue: { bindingKey: 'deposit' }, bindJson: JSON.stringify(fixture.binding) }] }) });
    const wrapper = await page();
    expect(wrapper.get('[data-testid="business-test-notice"]').text()).toContain('联调测试');
  });

  it('组件配置不再提供选择数值指标，数值和较上期都在指标库绑定里配置', async () => {
    const wrapper = await page();
    expect(wrapper.text()).not.toContain('选择数值指标');
    expect(wrapper.find('[data-testid="business-metric-section"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="business-semantic"]').exists()).toBe(false);
    expect(wrapper.find('[data-metric-key]').exists()).toBe(false);
    const binder = wrapper.get('[data-testid="metric-catalog-binder"]');
    expect(binder.text()).toContain('数值');
    expect(binder.text()).toContain('较上期变化');
    expect(binder.find('.el-form').exists()).toBe(true);
    expect(wrapper.get('[data-testid="advanced-binding-fields"]').attributes('open')).toBeUndefined();
    expect(api.save).not.toHaveBeenCalled();
  });

  it('当前数据源只声明最新周期时不能选择不支持的历史周期', async () => {
    const wrapper = await page();
    const periods = wrapper.get('[data-testid="binding-period"]').findAll('option').map(option => option.element.value);
    expect(periods).toEqual(['LATEST']);
  });

  it('总览存款可以配置较昨日、较上月、较上年，结算性存款也保留期间比较', async () => {
    getScreenCanvas.mockResolvedValueOnce({ screenId: 19, screenCode: 'SCR_BUSINESS', canvasVersion: 4,
      canvasStyleJson: JSON.stringify({ presentation: { type: 'CODE', template: 'branch-overview-v1', displaySchemaVersion: 1, display: { components: [] } } }),
      canvasDraftJson: JSON.stringify({ components: [{ id: 'deposit', component: 'ChartWidget', blockId: 41,
        propValue: { bindingKey: 'deposit' }, bindJson: JSON.stringify(fixture.binding) }] }) });
    const wrapper = await page();
    expect(wrapper.text()).not.toContain('选择数值指标');
    expect(wrapper.get('[data-config-key="overview-deposit"]').text()).toContain('存款总额');
    expect(wrapper.get('[data-testid="metric-period-comparisons"]').text()).toContain('较昨日');
    expect(wrapper.get('[data-testid="metric-period-comparisons"]').text()).toContain('较上月');
    expect(wrapper.get('[data-testid="metric-period-comparisons"]').text()).toContain('较上年');
    await wrapper.get('[data-config-key="overview-settlementDeposit"]').trigger('click');
    expect(wrapper.get('[data-testid="metric-period-comparisons"]').text()).toContain('没有独立查询');
    expect(wrapper.text()).not.toContain('选择数值指标');
  });

  it('命名机构组屏的指标库维度锁定为机构，不再列出选择数值指标', async () => {
    const bind = { dsId: 77, period: 'LATEST', fields: { value: 'amount', date: 'data_date' }, units: { value: 'YUAN' } };
    listScreens.mockResolvedValueOnce([{ ...fixture.screen, bizLine: 'CORP', orgScopeMode: 'NAMED_GROUP' }]);
    getScreenCanvas.mockResolvedValueOnce({ screenId: 19, screenCode: 'SCR_BUSINESS', canvasVersion: 4,
      canvasStyleJson: JSON.stringify({ presentation: { type: 'CODE', template: 'corporate-overview-v1' } }),
      canvasDraftJson: JSON.stringify({ components: [{ id: 'revenue', component: 'ChartWidget', blockId: 41,
        propValue: { bindingKey: 'corpRevenue' }, bindJson: JSON.stringify(bind) }] }),
      blocks: [{ id: 41, bindJson: JSON.stringify(bind) }] });
    listScreenDatasources.mockResolvedValueOnce([{ id: 77, dsName: '对公收益统计', status: 'ACTIVE', bizLine: 'CORP', dsType: 'SINGLE', sourceKind: 'M98_STAT',
      configJson: JSON.stringify({ schemaVersion: 2, scopeMode: 'NAMED_GROUP', profile: 'CORP_REVENUE', mode: 'SUMMARY' }), timeParamJson: '["LATEST"]' }]);
    const wrapper = await page();
    expect(wrapper.text()).not.toContain('选择数值指标');
    expect(wrapper.find('[data-metric-key]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="metric-catalog-count"]').text()).toContain('只能选择机构指标');
    expect(wrapper.get('[data-testid="metric-catalog-dim"] .el-select__wrapper').classes()).toContain('is-disabled');
  });
});
