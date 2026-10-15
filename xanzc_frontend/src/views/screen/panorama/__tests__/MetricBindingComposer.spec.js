// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import MetricBindingComposer from '../MetricBindingComposer.vue';

vi.mock('@/api/screen', () => ({
  listScreenMetricCatalog: vi.fn(async () => {
    throw new Error('AUTH-40302 资源未登记');
  }),
  tryRunScreenDatasource: vi.fn()
}));

const catalog = [
  { metricCode: 'M_0265', metricName: '一般性存款月均余额-机构', baseDim: 'ORG', valSlot: 12, unit: 'YUAN', unitSource: 'CONFIRMED_CODE', metricCategory: '规模类', summaryRule: 'SUM', tableName: 'ORG_INDEX_RESULT', subjectColumn: 'org_code', outputColumn: '一般性存款月均余额-机构', bindable: true },
  { metricCode: 'M_0266', metricName: '一般性存款月均余额较上月-机构', baseDim: 'ORG', valSlot: 51, unit: null, unitSource: 'NONE', metricCategory: '规模类', summaryRule: 'SUM', tableName: 'ORG_INDEX_RESULT', subjectColumn: 'org_code', outputColumn: '一般性存款月均余额较上月-机构', bindable: true }
];

const valueField = { semantic: 'value', label: '数值', kind: 'metric', required: true, unitKinds: ['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION'] };

function mountComposer(props = {}) {
  return mount(MetricBindingComposer, {
    props: {
      slotKey: 'depositAverage',
      slotLabel: '存款月均',
      templateName: 'branch-overview-v1',
      fieldSpecs: [valueField, { semantic: 'change', label: '较上期变化', kind: 'metric', unitKinds: ['PERCENT', 'RATIO'] }],
      screen: { bizLine: 'COMMON', orgScopeMode: 'NAMED_GROUP', orgGroupCode: 'GRP_SAMPLE' },
      datasources: [],
      catalog,
      ...props
    },
    global: { plugins: [ElementPlus] }
  });
}

describe('MetricBindingComposer', () => {
  it('名称建议只填入月均余额，并生成可提交的绑定计划', async () => {
    const wrapper = mountComposer();

    expect(wrapper.get('.el-form').exists()).toBe(true);
    expect(wrapper.get('[data-testid="metric-catalog-dim"] .el-select__wrapper').classes()).toContain('is-disabled');
    expect(wrapper.text()).toContain('ORG_INDEX_RESULT');
    expect(wrapper.text()).toContain('需要配置');
    expect(wrapper.text()).toContain('较上期变化');
    await wrapper.get('[data-testid="metric-catalog-suggest"]').trigger('click');
    const assigned = wrapper.get('[data-testid="metric-catalog-assign-value"] .el-select__placeholder');
    expect(assigned.text()).toContain('一般性存款月均余额-机构');
    expect(assigned.text()).toContain('M_0265');
    await wrapper.get('[data-testid="metric-catalog-apply"]').trigger('click');
    const plan = wrapper.emitted('apply')[0][0];
    expect(plan.ok).toBe(true);
    expect(plan.binding.fields.value).toBe('一般性存款月均余额-机构');
    expect(plan.binding.units.value).toBe('YUAN');
    expect(plan.datasourceRequest.sourceKind).toBe('WIDE_TABLE');
    expect(JSON.parse(plan.datasourceRequest.configJson).metrics).toEqual([{ metricCode: 'M_0265' }]);
    expect(JSON.parse(plan.datasourceRequest.configJson).table).toBe('ORG_INDEX_RESULT');
  });

  it('指标目录接口失败时仍显示后端状态', async () => {
    const wrapper = mountComposer({ catalog: null });
    await flushPromises();
    const alert = wrapper.get('[data-testid="metric-catalog-error"]');
    expect(alert.find('.el-alert').exists()).toBe(true);
    expect(alert.text()).toContain('资源未登记');
    expect(wrapper.get('[data-testid="metric-catalog-count"]').text()).toContain('目录 0 项');
  });
});
