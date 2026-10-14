// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import MetricBindingComposer from '../MetricBindingComposer.vue';

const catalog = [
  { metricCode: 'M_0265', metricName: '一般性存款月均余额-机构', baseDim: 'ORG', valSlot: 12, unit: 'YUAN', unitSource: 'CONFIRMED_CODE', metricCategory: '规模类', summaryRule: 'SUM', tableName: 'ORG_INDEX_RESULT', subjectColumn: 'org_code', outputColumn: '一般性存款月均余额-机构', bindable: true },
  { metricCode: 'M_0266', metricName: '一般性存款月均余额较上月-机构', baseDim: 'ORG', valSlot: 51, unit: null, unitSource: 'NONE', metricCategory: '规模类', summaryRule: 'SUM', tableName: 'ORG_INDEX_RESULT', subjectColumn: 'org_code', outputColumn: '一般性存款月均余额较上月-机构', bindable: true }
];

describe('MetricBindingComposer', () => {
  it('名称建议只填入月均余额，并生成可提交的绑定计划', async () => {
    const wrapper = mount(MetricBindingComposer, {
      props: {
        slotKey: 'depositAverage',
        slotLabel: '存款月均',
        templateName: 'branch-overview-v1',
        fieldSpecs: [{ semantic: 'value', label: '数值', kind: 'metric', required: true, unitKinds: ['YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION'] }],
        screen: { bizLine: 'COMMON', orgScopeMode: 'NAMED_GROUP', orgGroupCode: 'GRP_SAMPLE' },
        datasources: [],
        catalog
      }
    });

    expect(wrapper.text()).toContain('M_0266');
    await wrapper.get('[data-testid="metric-catalog-suggest"]').trigger('click');
    expect(wrapper.get('[data-testid="metric-catalog-assign-value"]').element.value).toBe('M_0265');
    await wrapper.get('[data-testid="metric-catalog-apply"]').trigger('click');
    const plan = wrapper.emitted('apply')[0][0];
    expect(plan.ok).toBe(true);
    expect(plan.binding.fields.value).toBe('一般性存款月均余额-机构');
    expect(plan.binding.units.value).toBe('YUAN');
    expect(plan.datasourceRequest.sourceKind).toBe('WIDE_TABLE');
    expect(JSON.parse(plan.datasourceRequest.configJson).metrics).toEqual([{ metricCode: 'M_0265' }]);
  });
});
