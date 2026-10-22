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
  it('把数值与单位移到首行，右侧保留维度和分类，不再提供独立搜索栏', () => {
    const wrapper = mountComposer({ showPeriodComparisons: true });
    expect(wrapper.find('[data-testid="metric-catalog-search"]').exists()).toBe(false);
    const row = wrapper.get('[data-testid="metric-catalog-selection-row"]');
    expect(row.findAll('.el-form-item__label').map(label => label.text())).toEqual(['数值需要配置', '单位', '维度', '分类']);
    expect(wrapper.findAll('[data-testid="metric-catalog-assign-value"]')).toHaveLength(1);
    expect(row.find('[data-testid="metric-catalog-unit-value"]').exists()).toBe(true);
  });

  it('独立搜索栏移除后，仍能在指标下拉中按编码查找展示上限之外的指标', async () => {
    const many = Array.from({ length: 201 }, (_, i) => ({ ...catalog[0], metricCode: `M_${i}`, metricName: `余额${i}`, outputColumn: `余额${i}` }));
    const wrapper = mountComposer({ catalog: many });
    const select = wrapper.get('[data-testid="metric-catalog-assign-value"]').findComponent({ name: 'ElSelect' });
    expect(wrapper.findAllComponents({ name: 'ElOption' }).some(option => option.props('value') === 'M_200')).toBe(false);
    select.props('filterMethod')('M_200');
    await wrapper.vm.$nextTick();
    expect(wrapper.findAllComponents({ name: 'ElOption' }).some(option => option.props('value') === 'M_200')).toBe(true);
    select.vm.$emit('change', 'M_200');
    select.vm.$emit('visible-change', false);
    await wrapper.vm.$nextTick();
    await wrapper.get('[data-testid="metric-catalog-apply"]').trigger('click');
    expect(wrapper.emitted('apply').at(-1)[0].binding.fields.value).toBe('余额200');
    expect(wrapper.get('[data-testid="metric-catalog-count"]').text()).toContain('可绑定 201 项');
  });

  it('期间比较组件不再显示通用变化值，技术取数配置默认收起', () => {
    const wrapper = mountComposer({ showPeriodComparisons: true });
    expect(wrapper.find('[data-testid="metric-catalog-assign-change"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="metric-period-comparisons"]').text()).toContain('较上月');
    const advanced = wrapper.get('[data-testid="metric-catalog-advanced"]');
    expect(advanced.attributes('open')).toBeUndefined();
    expect(advanced.get('summary').text()).toBe('高级取数设置');
    expect(advanced.find('[data-testid="metric-catalog-groupby"]').exists()).toBe(true);
    expect(advanced.find('[data-testid="metric-catalog-add-filter"]').exists()).toBe(true);
    expect(advanced.find('[data-testid="metric-catalog-reason"]').exists()).toBe(true);
  });

  it('收起高级设置仍按比例口径生成平均值，不把隐藏的求和默认值用于完成率', async () => {
    const wrapper = mountComposer({
      fieldSpecs: [{ ...valueField, unitKinds: ['PERCENT', 'RATIO'] }],
      catalog: [{ ...catalog[0], unit: 'PERCENT', summaryRule: 'AVG' }]
    });
    wrapper.get('[data-testid="metric-catalog-assign-value"]').findComponent({ name: 'ElSelect' }).vm.$emit('change', 'M_0265');
    await wrapper.vm.$nextTick();
    await wrapper.get('[data-testid="metric-catalog-apply"]').trigger('click');
    const plan = wrapper.emitted('apply').at(-1)[0];
    expect(plan.ok).toBe(true);
    expect(JSON.parse(plan.datasourceRequest.configJson).aggregation).toEqual({ groupBy: 'NONE', agg: 'AVG' });
  });

  it('高级设置展开调整后再收起，聚合、过滤与旧版变化配置仍参与绑定', async () => {
    const wrapper = mountComposer();
    const advanced = wrapper.get('[data-testid="metric-catalog-advanced"]');
    advanced.element.open = true;
    await wrapper.get('[data-testid="metric-catalog-suggest"]').trigger('click');
    const change = wrapper.get('[data-testid="metric-catalog-assign-change"]').findComponent({ name: 'ElSelect' });
    change.vm.$emit('change', 'M_0266');
    wrapper.get('[data-testid="metric-catalog-unit-change"]').findComponent({ name: 'ElSelect' }).vm.$emit('change', 'PERCENT');
    wrapper.get('[data-testid="metric-catalog-agg"]').findComponent({ name: 'ElSelect' }).vm.$emit('update:modelValue', 'MAX');
    await wrapper.get('[data-testid="metric-catalog-add-filter"]').trigger('click');
    await wrapper.get('input[placeholder="过滤值"]').setValue('100');
    advanced.element.open = false;
    await wrapper.get('[data-testid="metric-catalog-apply"]').trigger('click');
    const plan = wrapper.emitted('apply').at(-1)[0];
    expect(plan.ok).toBe(true);
    expect(plan.binding.fields.change).toBe(catalog[1].outputColumn);
    const config = JSON.parse(plan.datasourceRequest.configJson);
    expect(config.aggregation.agg).toBe('MAX');
    expect(config.aggregation.filters).toEqual([{ col: 'val_12', op: 'GT', value: '100' }]);
  });

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

  it('期间比较可以绑定增量指标，并把该指标写入同一结果表', async () => {
    const wrapper = mountComposer({ showPeriodComparisons: true });
    expect(wrapper.get('[data-testid="metric-period-comparisons"]').text()).toContain('较昨日');
    expect(wrapper.text()).toContain('较上月');
    expect(wrapper.text()).toContain('较上年');
    const mode = wrapper.get('[data-testid="metric-period-mode-month"]').findComponent({ name: 'ElSelect' });
    mode.vm.$emit('update:modelValue', 'METRIC');
    await wrapper.vm.$nextTick();
    const metric = wrapper.get('[data-testid="metric-period-metric-month"]').findComponent({ name: 'ElSelect' });
    metric.vm.$emit('change', 'M_0266');
    await wrapper.vm.$nextTick();
    const unit = wrapper.get('[data-testid="metric-period-unit-month"]').findComponent({ name: 'ElSelect' });
    unit.vm.$emit('change', 'YUAN');
    await wrapper.vm.$nextTick();
    await wrapper.get('[data-testid="metric-catalog-suggest"]').trigger('click');
    await wrapper.get('[data-testid="metric-catalog-apply"]').trigger('click');
    const plan = wrapper.emitted('apply').at(-1)[0];
    expect(plan.ok).toBe(true);
    expect(plan.periodComparisons.month).toMatchObject({
      mode: 'METRIC',
      metricCode: 'M_0266',
      outputColumn: '一般性存款月均余额较上月-机构',
      unit: 'YUAN'
    });
    expect(plan.periodComparisons.day.mode).toBe('UNSET');
    expect(JSON.parse(plan.datasourceRequest.configJson).metrics.map(item => item.metricCode)).toEqual(['M_0265', 'M_0266']);
    expect(wrapper.text()).not.toContain('选择数值指标');
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
