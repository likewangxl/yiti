// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const fixture = vi.hoisted(() => ({
  screen: { id: 19, screenCode: 'SCR_BUSINESS', screenName: '业务配置屏', bizLine: 'COMMON', orgScopeMode: 'LEGACY_CONTEXT', status: 'ACTIVE' },
  binding: { dsId: 77, period: 'LATEST', fields: { value: 'old_balance' }, units: { value: 'YUAN' } }
}));
const api = vi.hoisted(() => ({ save: vi.fn() }));
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
  publishScreenCanvas: vi.fn(), discardScreenCanvas: vi.fn(), listOrgProfiles: vi.fn(), listOrgGroups: vi.fn()
}));
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn() }) }));
import PanoramaBindings from '../PanoramaBindings.vue';
import BusinessMetricPicker from '../BusinessMetricPicker.vue';
import { getScreenCanvas, listScreenDatasources, listScreens } from '@/api/screen';

const stubs = { PanoramaDataVerification: true, PanoramaDatasourcePicker: true,
  'el-button': { template: '<button><slot /></button>' } };
async function page() {
  const wrapper = mount(PanoramaBindings, { global: { stubs, directives: { 'bp-overflow-tooltip': {} } } });
  await flushPromises();
  return wrapper;
}

describe('业务人员配置指标', () => {
  it('业务目录接收当前配置项关键词和已绑定来源，排序不需要用户再填写技术查询', async () => {
    const wrapper = await page();
    const context = wrapper.findComponent(BusinessMetricPicker).props('relevanceContext');
    expect(context.keywords).toContain('存款');
    expect(context.semantic).toContain('deposit');
    expect(context.preferredSourceIds).toContain(77);
  });
  it('较上期变化只接受明确变化用途，不能把同为百分数的完成率当成变化', async () => {
    listScreenDatasources.mockResolvedValueOnce([{ id: 77, dsName: '经营比例指标', status: 'ACTIVE', bizLine: 'COMMON', dsType: 'SINGLE', sourceKind: 'WIDE_TABLE',
      configJson: JSON.stringify({ schemaVersion: 2, table: 'ORG_INDEX_RESULT', subjectCol: 'org_code', aggregation: { groupBy: 'NONE' }, fieldMeta: [
        { col: 'growth', alias: '较上期变化率', role: 'METRIC', semantic: 'change', unit: 'PERCENT' },
        { col: 'completion', alias: '目标完成率', role: 'METRIC', semantic: 'completionRate', unit: 'PERCENT' }
      ] }), timeParamJson: '["LATEST"]' }]);
    const wrapper = await page();
    await wrapper.get('[data-testid="business-semantic"]').setValue('change');
    const entries = wrapper.findComponent(BusinessMetricPicker).props('entries');
    expect(entries.find(item => item.field === 'growth').disabled).toBe(false);
    expect(entries.find(item => item.field === 'completion').disabled).toBe(true);
    expect(entries.find(item => item.field === 'completion').disabledReason).toContain('较上期变化');
  });

  it('按主体返回多行的来源不能因能力标签SINGLE而成为单值卡片的可选来源', async () => {
    listScreenDatasources.mockResolvedValueOnce([{ id: 77, dsName: '机构明细', status: 'ACTIVE', bizLine: 'COMMON', dsType: 'SINGLE', sourceKind: 'WIDE_TABLE',
      configJson: JSON.stringify({ schemaVersion: 2, table: 'ORG_INDEX_RESULT', subjectCol: 'org_code', aggregation: { groupBy: 'SUBJECT', agg: 'SUM' },
        fieldMeta: [{ col: 'balance', role: 'METRIC', semantic: 'deposit', unit: 'YUAN' }] }), timeParamJson: '["LATEST"]' }]);
    const wrapper = await page();
    const entry = wrapper.findComponent(BusinessMetricPicker).props('entries').find(item => item.field === 'balance');
    expect(entry.disabled).toBe(true);
    expect(entry.disabledReason).toContain('单值');
  });

  it('明确声明TEST的屏显示联调提示，不把测试目录包装成正式经营目录', async () => {
    getScreenCanvas.mockResolvedValueOnce({ screenId: 19, screenCode: 'SCR_BUSINESS', canvasVersion: 4,
      canvasStyleJson: JSON.stringify({ dataClassification: 'TEST', presentation: { type: 'CODE', template: 'branch-overview-v1' } }),
      canvasDraftJson: JSON.stringify({ components: [{ id: 'deposit', component: 'ChartWidget', blockId: 41,
        propValue: { bindingKey: 'deposit' }, bindJson: JSON.stringify(fixture.binding) }] }) });
    const wrapper = await page();
    expect(wrapper.get('[data-testid="business-test-notice"]').text()).toContain('联调测试');
  });

  it('主值指标选择直接绑定明确字段与原始单位，技术字段配置默认折叠', async () => {
    const wrapper = await page();
    expect(wrapper.find('[data-testid="business-metric-section"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="advanced-binding-fields"]').attributes('open')).toBeUndefined();
    const picker = wrapper.findAll('[data-testid="business-metric-section"]')[0];
    await picker.get('[data-metric-key="77:balance"]').trigger('click');
    expect(wrapper.get('[data-testid="field-option-deposit-value"]').element.value).toBe('balance');
    expect(wrapper.get('[data-testid="unit-deposit-value"]').element.value).toBe('YUAN');
    expect(wrapper.find('[data-testid="binding-cancel"]').exists()).toBe(true);
    expect(api.save).not.toHaveBeenCalled();
  });

  it('当前数据源只声明最新周期时不能选择不支持的历史周期', async () => {
    const wrapper = await page();
    const periods = wrapper.get('[data-testid="binding-period"]').findAll('option').map(option => option.element.value);
    expect(periods).toEqual(['LATEST']);
  });

  it('多个业务字段共用一个指标目录，通过配置项切换，避免重复长列表', async () => {
    const wrapper = await page();
    expect(wrapper.findAll('[data-testid="business-metric-section"]')).toHaveLength(1);
    await wrapper.get('[data-testid="business-semantic"]').setValue('change');
    expect(wrapper.get('[data-testid="business-metric-picker-label"]').text()).toContain('较上期变化');
    expect(wrapper.find('[data-metric-key="77:balance"]').exists()).toBe(false);
    expect(wrapper.findComponent(BusinessMetricPicker).props('entries').find(item => item.field === 'balance').disabled).toBe(true);
  });

  it('M98固定收益指标可按当前组件和已保存绑定单位选择，不需往固定配置添加元数据', async () => {
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
    expect(wrapper.get('[data-metric-key="77:amount"]').attributes('disabled')).toBeUndefined();
    expect(wrapper.get('[data-metric-key="77:amount"]').text()).toContain('沿用已保存绑定单位');
    await wrapper.get('[data-metric-key="77:amount"]').trigger('click');
    expect(wrapper.get('[data-testid="field-option-corpRevenue-value"]').element.value).toBe('amount');
    expect(wrapper.get('[data-testid="unit-corpRevenue-value"]').element.value).toBe('YUAN');
  });
});
