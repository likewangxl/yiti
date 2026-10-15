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
  it('在业务配置名称下解释用途并去除表名槽位说明，提交内容不变', async () => {
    const wrapper = mountComposer();
    try {
      expect(wrapper.get('[data-testid="metric-catalog-aggregation-help"]').text()).toContain('不能把两个不同指标相加');
      expect(wrapper.get('[data-testid="metric-catalog-filter-help"]').text()).toContain('不用添加');
      expect(wrapper.get('[data-testid="metric-catalog-reason-help"]').text()).toContain('不参与指标计算');
      expect(wrapper.text()).toContain('筛选条件（可选）');
      expect(wrapper.text()).toContain('修改说明');
      expect(wrapper.text()).not.toContain('ORG_INDEX_RESULT');
      expect(wrapper.text()).not.toContain('val_槽位');
      await wrapper.get('[data-testid="metric-catalog-suggest"]').trigger('click');
      expect(wrapper.get('[data-testid="metric-catalog-assign-value"]').text()).toContain('M_0265');
      expect(wrapper.get('[data-testid="metric-catalog-assign-value"]').text()).not.toContain('槽位');
      await wrapper.get('[data-testid="metric-catalog-apply"]').trigger('click');
      const plan = wrapper.emitted('apply')[0][0];
      expect(plan.datasourceRequest.reason).toBe('从指标库绑定到当前组件');
      expect(JSON.parse(plan.datasourceRequest.configJson)).toMatchObject({
        table: 'ORG_INDEX_RESULT', aggregation: { groupBy: 'NONE', agg: 'SUM' }
      });
    } finally {
      wrapper.unmount();
    }
  });

  it('不展示聚合维度，保留聚合函数、过滤及已保存分组口径', async () => {
    const filters = [{ col: 'val_12', op: 'GT', value: '0' }];
    const wrapper = mountComposer({
      initialAggregation: { groupBy: 'DATE', agg: 'SUM' },
      initialFilters: filters
    });
    try {
      expect(wrapper.find('[data-testid="metric-catalog-groupby"]').exists()).toBe(false);
      expect(wrapper.text()).not.toContain('聚合维度');
      expect(wrapper.get('[data-testid="metric-catalog-agg"]').exists()).toBe(true);
      expect(wrapper.get('[data-testid="metric-catalog-add-filter"]').exists()).toBe(true);
      await wrapper.get('[data-testid="metric-catalog-suggest"]').trigger('click');
      await wrapper.get('[data-testid="metric-catalog-apply"]').trigger('click');
      const plan = wrapper.emitted('apply')[0][0];
      expect(JSON.parse(plan.datasourceRequest.configJson).aggregation)
        .toEqual({ groupBy: 'DATE', agg: 'SUM', filters });
    } finally {
      wrapper.unmount();
    }
  });

  it('名称建议只填入月均余额，并生成可提交的绑定计划', async () => {
    const wrapper = mountComposer();

    expect(wrapper.get('.el-form').exists()).toBe(true);
    expect(wrapper.get('[data-testid="metric-catalog-dim"] .el-select__wrapper').classes()).toContain('is-disabled');
    expect(wrapper.text()).toContain('选择本组件需要展示的业务指标');
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

  it('已有比例字段唯一匹配目录后回显，不误报需要配置并允许 PERCENT 期间比较', async () => {
    const rate = {
      metricCode: 'M_RATE', metricName: '测试完成率', baseDim: 'ORG', valSlot: 331, unit: 'PERCENT',
      metricCategory: '完成率', summaryRule: 'AVG', tableName: 'ORG_INDEX_RESULT', subjectColumn: 'org_code',
      outputColumn: '测试完成率', bindable: true
    };
    const wrapper = mountComposer({
      catalog: [...catalog, rate], initialFields: { value: '测试完成率' }, initialUnits: { value: 'PERCENT' },
      showPeriodComparisons: true,
      fieldSpecs: [{ ...valueField, unitKinds: ['PERCENT', 'RATIO'] }]
    });
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-testid="metric-catalog-assign-value"]').text()).toContain('M_RATE');
    expect(wrapper.get('[data-testid="metric-catalog-assign-value"]').text()).not.toContain('请选择指标');
    expect(wrapper.text()).not.toContain('数值需要配置');
    const mode = wrapper.get('[data-testid="metric-period-mode-month"]').findComponent({ name: 'ElSelect' });
    mode.vm.$emit('update:modelValue', 'METRIC');
    await wrapper.vm.$nextTick();
    wrapper.get('[data-testid="metric-period-metric-month"]').findComponent({ name: 'ElSelect' }).vm.$emit('change', 'M_RATE');
    await wrapper.vm.$nextTick();
    wrapper.get('[data-testid="metric-period-unit-month"]').findComponent({ name: 'ElSelect' }).vm.$emit('change', 'PERCENT');
    await wrapper.vm.$nextTick();
    await wrapper.get('[data-testid="metric-catalog-apply"]').trigger('click');
    expect(wrapper.emitted('apply').at(-1)[0]).toMatchObject({ ok: true, periodComparisons: { month: { unit: 'PERCENT' } } });
  });

  it('完成率期间比较候选和单位始终服从主指标比例量纲', async () => {
    const rate = {
      metricCode: 'M_RATE_MAIN', metricName: '主完成率', baseDim: 'ORG', valSlot: 333, unit: 'PERCENT',
      metricCategory: '完成率', summaryRule: 'AVG', tableName: 'ORG_INDEX_RESULT', subjectColumn: 'org_code',
      outputColumn: '主完成率', bindable: true
    };
    const amount = {
      metricCode: 'M_AMOUNT_PERIOD', metricName: '金额比较项', baseDim: 'ORG', valSlot: 334, unit: 'YUAN',
      metricCategory: '规模类', summaryRule: 'SUM', tableName: 'ORG_INDEX_RESULT', subjectColumn: 'org_code',
      outputColumn: '金额比较项', bindable: true
    };
    const wrapper = mountComposer({
      catalog: [rate, amount], showPeriodComparisons: true,
      fieldSpecs: [{ ...valueField, unitKinds: ['PERCENT', 'RATIO'] }]
    });
    const mode = wrapper.get('[data-testid="metric-period-mode-month"]').findComponent({ name: 'ElSelect' });
    mode.vm.$emit('update:modelValue', 'METRIC');
    await wrapper.vm.$nextTick();
    wrapper.get('[data-testid="metric-period-metric-month"]').findComponent({ name: 'ElSelect' }).vm.$emit('change', 'M_AMOUNT_PERIOD');
    await wrapper.vm.$nextTick();
    const unitOptions = wrapper.vm.periodUnitOptions('month');
    expect(unitOptions).toEqual(['PERCENT', 'RATIO']);
  });

  it('目录没有旧手工字段时保留来源字段说明且不把未操作状态标成红色缺配置', async () => {
    const wrapper = mountComposer({
      initialFields: { value: '历史手工列' }, initialUnits: { value: 'YUAN' },
      existingFieldNotes: ['已保存来源：历史手工列（YUAN）']
    });
    await wrapper.vm.$nextTick();
    expect(wrapper.text()).toContain('历史手工列');
    expect(wrapper.text()).not.toContain('数值需要配置');
  });

  it('目录未匹配的完成率直接回显已有字段和单位，并仍可选择新目录指标替换', async () => {
    const rate = {
      metricCode: 'M_RATE_REPLACE', metricName: '新目录完成率', baseDim: 'ORG', valSlot: 332, unit: 'PERCENT',
      metricCategory: '完成率', summaryRule: 'AVG', tableName: 'ORG_INDEX_RESULT', subjectColumn: 'org_code',
      outputColumn: '新目录完成率', bindable: true
    };
    const wrapper = mountComposer({
      catalog: [rate],
      initialFields: { value: '历史完成率字段' },
      initialUnits: { value: 'PERCENT' },
      fieldSpecs: [{ ...valueField, unitKinds: ['PERCENT', 'RATIO'] }]
    });
    await wrapper.vm.$nextTick();

    const metricSelect = wrapper.get('[data-testid="metric-catalog-assign-value"]');
    const unitSelect = wrapper.get('[data-testid="metric-catalog-unit-value"]');
    expect(metricSelect.text()).toContain('历史完成率字段');
    expect(metricSelect.text()).not.toContain('请选择指标');
    expect(unitSelect.text()).toContain('百分数');
    expect(unitSelect.text()).not.toContain('选择单位');
    expect(wrapper.text()).not.toContain('需要配置');

    metricSelect.findComponent({ name: 'ElSelect' }).vm.$emit('change', 'M_RATE_REPLACE');
    await wrapper.vm.$nextTick();
    await wrapper.get('[data-testid="metric-catalog-apply"]').trigger('click');
    expect(wrapper.emitted('apply').at(-1)[0]).toMatchObject({
      ok: true,
      binding: { fields: { value: '新目录完成率' }, units: { value: 'PERCENT' } }
    });
  });

  it('相同 slot 切换组件字段时按新的 initialFields 重置回显，不串用上一张卡', async () => {
    const first = { metricCode: 'M_FIRST', metricName: '第一张卡', baseDim: 'ORG', valSlot: 1, unit: 'YUAN', tableName: 'ORG_INDEX_RESULT', outputColumn: '第一列', bindable: true };
    const second = { metricCode: 'M_SECOND', metricName: '第二张卡', baseDim: 'ORG', valSlot: 2, unit: 'YUAN', tableName: 'ORG_INDEX_RESULT', outputColumn: '第二列', bindable: true };
    const wrapper = mountComposer({
      catalog: [first, second], initialFields: { value: '第一列' }, initialUnits: { value: 'YUAN' }
    });
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-testid="metric-catalog-assign-value"]').text()).toContain('M_FIRST');
    await wrapper.setProps({ initialFields: { value: '第二列' }, initialUnits: { value: 'YUAN' } });
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-testid="metric-catalog-assign-value"]').text()).toContain('M_SECOND');
    expect(wrapper.get('[data-testid="metric-catalog-assign-value"]').text()).not.toContain('M_FIRST');
  });

  it('catalog 从空数组异步到达后仍回填唯一字段，不提前锁定空回显', async () => {
    const rate = { metricCode: 'M_RATE_ASYNC', metricName: '异步完成率', baseDim: 'ORG', valSlot: 331, unit: 'PERCENT', tableName: 'ORG_INDEX_RESULT', outputColumn: '异步完成率', bindable: true };
    const wrapper = mountComposer({
      catalog: [], initialFields: { value: '异步完成率' }, initialUnits: { value: 'PERCENT' },
      fieldSpecs: [{ ...valueField, unitKinds: ['PERCENT', 'RATIO'] }]
    });
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-testid="metric-catalog-assign-value"]').text()).toContain('异步完成率');
    await wrapper.setProps({ catalog: [rate] });
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-testid="metric-catalog-assign-value"]').text()).toContain('M_RATE_ASYNC');
  });

  it('已有来源过滤条件按 data_date、主体列和 val_N 回显，未知旧条件只读保留且保存不丢失', async () => {
    const main = {
      metricCode: 'M_FILTER_MAIN', metricName: '过滤主指标', baseDim: 'ORG', valSlot: 319, unit: 'YUAN',
      metricCategory: '规模类', summaryRule: 'SUM', tableName: 'ORG_INDEX_RESULT', subjectColumn: 'org_code',
      outputColumn: '过滤主指标', bindable: true
    };
    const filteredMetric = {
      metricCode: 'M_FILTER_VALUE', metricName: '过滤指标', baseDim: 'ORG', valSlot: 320, unit: 'YUAN',
      metricCategory: '规模类', summaryRule: 'SUM', tableName: 'ORG_INDEX_RESULT', subjectColumn: 'org_code',
      outputColumn: '过滤指标', bindable: true
    };
    const datasource = {
      id: 77, sourceKind: 'WIDE_TABLE', bizLine: 'COMMON', status: 'ACTIVE',
      configJson: JSON.stringify({ table: 'ORG_INDEX_RESULT', scopeMode: 'GLOBAL',
        metrics: [{ metricCode: 'M_FILTER_MAIN' }, { metricCode: 'M_FILTER_VALUE' }],
        aggregation: { groupBy: 'NONE', agg: 'SUM', filters: [
          { col: 'data_date', op: 'GT', value: '2026-10-01' },
          { col: 'org_code', op: 'EQ', value: '001' },
          { col: 'val_320', op: 'GT', value: '0' },
          { col: 'manual_flag', op: 'EQ', value: 'Y' }
        ] }
      })
    };
    const wrapper = mountComposer({
      catalog: [main, filteredMetric],
      datasources: [datasource],
      initialDatasourceId: 77,
      initialFields: { value: '过滤主指标' },
      initialUnits: { value: 'YUAN' },
      initialAggregation: { groupBy: 'NONE', agg: 'SUM' },
      initialFilters: [
        { col: 'data_date', op: 'GT', value: '2026-10-01' },
        { col: 'org_code', op: 'EQ', value: '001' },
        { col: 'val_320', op: 'GT', value: '0' },
        { col: 'manual_flag', op: 'EQ', value: 'Y' }
      ]
    });
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-testid="metric-catalog-legacy-filter-3"]').text()).toContain('manual_flag');
    await wrapper.get('[data-testid="metric-catalog-apply"]').trigger('click');
    const plan = wrapper.emitted('apply').at(-1)[0];
    expect(plan.ok).toBe(true);
    expect(JSON.parse(plan.previewRow.configJson).aggregation.filters).toEqual([
      { col: 'data_date', op: 'GT', value: '2026-10-01' },
      { col: 'org_code', op: 'EQ', value: '001' },
      { col: 'val_320', op: 'GT', value: '0' },
      { col: 'manual_flag', op: 'EQ', value: 'Y' }
    ]);
  });
});
