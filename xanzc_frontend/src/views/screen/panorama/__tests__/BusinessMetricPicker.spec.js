// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import BusinessMetricPicker from '../BusinessMetricPicker.vue';

const entries = [
  {
    key: '11:deposit_balance',
    sourceId: 11,
    field: 'deposit_balance',
    metricName: '存款余额',
    sourceDescription: '机构经营来源',
    unit: 'HUNDRED_MILLION',
    rawUnit: 'HUNDRED_MILLION',
    shape: 'SINGLE',
    semantic: 'depositBalance',
    metricCode: 'M_DEP',
    disabled: false,
    disabledReason: ''
  },
  {
    key: '11:unknown_amount',
    sourceId: 11,
    field: 'unknown_amount',
    metricName: '待维护金额',
    sourceDescription: '机构经营来源',
    unit: null,
    rawUnit: null,
    shape: 'SINGLE',
    semantic: null,
    metricCode: null,
    disabled: true,
    disabledReason: '原始单位未认证，请由技术人员维护'
  }
];

const testEntries = [
  ...entries,
  {
    key: '12:test_revenue',
    sourceId: 12,
    field: 'TEST_REVENUE',
    metricName: '测试收入',
    sourceDescription: '正式来源',
    unit: 'YUAN',
    rawUnit: 'YUAN',
    shape: 'SINGLE',
    semantic: 'revenue',
    metricCode: 'TEST_REVENUE',
    disabled: false,
    disabledReason: ''
  },
  {
    key: '13:TEST_LOAN',
    sourceId: 13,
    field: 'TEST_LOAN',
    metricName: '测试贷款',
    sourceDescription: '正式来源',
    unit: null,
    rawUnit: null,
    shape: 'SINGLE',
    semantic: 'loan',
    metricCode: 'M_TEST_LOAN',
    disabled: true,
    disabledReason: '测试来源字段单位待维护'
  }
];

describe('BusinessMetricPicker', () => {
  it('回显当前绑定、支持搜索，并在选择时返回来源字段单位和必要语义', async () => {
    const wrapper = mount(BusinessMetricPicker, {
      props: {
        entries,
        modelValue: { datasourceId: 11, field: 'deposit_balance', unit: 'HUNDRED_MILLION' },
        label: '主值指标'
      }
    });

    expect(wrapper.get('[data-testid="business-metric-picker-label"]').text()).toContain('主值指标');
    expect(wrapper.get('[data-metric-key="11:deposit_balance"]').classes()).toContain('is-selected');

    await wrapper.get('[data-testid="business-metric-search"]').setValue('存款');
    expect(wrapper.findAll('[data-metric-option]').map(node => node.text())).toHaveLength(1);

    await wrapper.get('[data-metric-key="11:deposit_balance"]').trigger('click');
    expect(wrapper.emitted('select')?.[0]?.[0]).toMatchObject({
      datasourceId: 11,
      field: 'deposit_balance',
      unit: 'HUNDRED_MILLION',
      semantic: 'depositBalance',
      metricCode: 'M_DEP'
    });
  });

  it('显示禁用原因，禁用项不可选，并支持无结果和空目录状态', async () => {
    const wrapper = mount(BusinessMetricPicker, { props: { entries } });
    expect(wrapper.find('[data-metric-key="11:unknown_amount"]').exists()).toBe(false);
    await wrapper.get('[data-testid="business-metric-toggle-unavailable"]').trigger('click');
    const disabled = wrapper.get('[data-metric-key="11:unknown_amount"]');

    expect(disabled.attributes('disabled')).toBeDefined();
    expect(disabled.text()).toContain('原始单位未认证');
    await disabled.trigger('click');
    expect(wrapper.emitted('select')).toBeUndefined();

    await wrapper.get('[data-testid="business-metric-search"]').setValue('不存在');
    expect(wrapper.get('[data-testid="business-metric-empty"]').text()).toContain('没有匹配');

    await wrapper.setProps({ entries: [] });
    expect(wrapper.get('[data-testid="business-metric-empty"]').text()).toContain('当前配置项暂无适用指标');
  });

  it('disabled 时不允许搜索或选择', async () => {
    const wrapper = mount(BusinessMetricPicker, { props: { entries, disabled: true } });
    expect(wrapper.get('[data-testid="business-metric-search"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-metric-key="11:deposit_balance"]').attributes('disabled')).toBeDefined();

    await wrapper.get('[data-metric-key="11:deposit_balance"]').trigger('click');
    expect(wrapper.emitted('select')).toBeUndefined();
  });

  it('默认只显示可用项，并分开统计可用与不可用数量', () => {
    const wrapper = mount(BusinessMetricPicker, { props: { entries } });

    expect(wrapper.get('[data-testid="business-metric-count-available"]').text()).toContain('可用 1 项');
    expect(wrapper.get('[data-testid="business-metric-count-unavailable"]').text()).toContain('不可用 1 项');
    expect(wrapper.get('[data-testid="business-metric-toggle-unavailable"]').text()).toContain('查看不可用项（1）');
    expect(wrapper.find('[data-metric-key="11:deposit_balance"]').exists()).toBe(true);
    expect(wrapper.find('[data-metric-key="11:unknown_amount"]').exists()).toBe(false);
  });

  it('当前已保存绑定即使不可用也单独回显并解释，不受诊断折叠和搜索影响', async () => {
    const wrapper = mount(BusinessMetricPicker, {
      props: { entries, modelValue: { datasourceId: 11, field: 'unknown_amount' } }
    });

    const current = wrapper.get('[data-testid="business-metric-current"]');
    expect(current.text()).toContain('待维护金额');
    expect(current.text()).toContain('原始单位未认证');
    expect(current.classes()).toContain('is-unavailable');

    await wrapper.get('[data-testid="business-metric-search"]').setValue('不存在');
    expect(wrapper.get('[data-testid="business-metric-current"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="business-metric-empty"]').text()).toContain('没有匹配');
  });

  it('搜索服从当前模式：默认搜可用项，展开后搜不可用项', async () => {
    const wrapper = mount(BusinessMetricPicker, { props: { entries } });
    const search = wrapper.get('[data-testid="business-metric-search"]');

    await search.setValue('待维护');
    expect(wrapper.find('[data-metric-key="11:unknown_amount"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="business-metric-empty"]').text()).toContain('没有匹配');

    await wrapper.get('[data-testid="business-metric-toggle-unavailable"]').trigger('click');
    expect(wrapper.find('[data-metric-key="11:unknown_amount"]').exists()).toBe(true);
    expect(wrapper.find('[data-metric-key="11:deposit_balance"]').exists()).toBe(false);
  });

  it('区分当前配置没有适用指标与搜索没有匹配项', async () => {
    const noApplicable = mount(BusinessMetricPicker, { props: { entries: [] } });
    expect(noApplicable.get('[data-testid="business-metric-empty"]').text()).toContain('当前配置项暂无适用指标');

    const noMatch = mount(BusinessMetricPicker, { props: { entries: [entries[0]] } });
    await noMatch.get('[data-testid="business-metric-search"]').setValue('不存在');
    expect(noMatch.get('[data-testid="business-metric-empty"]').text()).toContain('没有匹配');
  });

  it('多个实例使用唯一 search/label id，并保持 label、listbox 关联正确', () => {
    const first = mount(BusinessMetricPicker, { props: { entries, label: '存款主值' } });
    const second = mount(BusinessMetricPicker, { props: { entries, label: '贷款主值' } });
    const firstSearch = first.get('[data-testid="business-metric-search"]');
    const secondSearch = second.get('[data-testid="business-metric-search"]');
    const firstLabel = first.get('[data-testid="business-metric-picker-label"]');
    const secondLabel = second.get('[data-testid="business-metric-picker-label"]');

    expect(firstSearch.attributes('id')).toBeTruthy();
    expect(secondSearch.attributes('id')).toBeTruthy();
    expect(firstSearch.attributes('id')).not.toBe(secondSearch.attributes('id'));
    expect(firstLabel.attributes('id')).not.toBe(secondLabel.attributes('id'));
    expect(firstLabel.attributes('for')).toBe(firstSearch.attributes('id'));
    expect(secondLabel.attributes('for')).toBe(secondSearch.attributes('id'));
    expect(first.get('[role="listbox"]').attributes('aria-labelledby')).toBe(firstLabel.attributes('id'));
    expect(second.get('[role="listbox"]').attributes('aria-labelledby')).toBe(secondLabel.attributes('id'));
  });

  it('默认隐藏名称带测试标识的指标，显式开关显示且保留原始名称和 TEST 标识', async () => {
    const wrapper = mount(BusinessMetricPicker, { props: { entries: testEntries } });

    expect(wrapper.find('[data-metric-key="12:test_revenue"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="business-metric-toggle-test"]').text()).toContain('显示测试指标（2）');
    await wrapper.get('[data-testid="business-metric-toggle-test"]').trigger('click');
    expect(wrapper.get('[data-metric-key="12:test_revenue"]').text()).toContain('测试收入');
    expect(wrapper.get('[data-metric-key="12:test_revenue"]').text()).toContain('TEST_REVENUE');
    await wrapper.get('[data-testid="business-metric-toggle-unavailable"]').trigger('click');
    expect(wrapper.get('[data-metric-key="13:TEST_LOAN"]').attributes('disabled')).toBeDefined();
  });

  it('当前绑定是隐藏测试指标时单独回显并说明已隐藏，不自动清空或选择', () => {
    const wrapper = mount(BusinessMetricPicker, {
      props: {
        entries: testEntries,
        modelValue: { datasourceId: 12, field: 'TEST_REVENUE' }
      }
    });

    const current = wrapper.get('[data-testid="business-metric-current"]');
    expect(current.text()).toContain('测试收入');
    expect(current.text()).toContain('已隐藏');
    expect(wrapper.emitted('select')).toBeUndefined();
  });

  it('按 relevanceContext 排序适配项，当前 source+field 和明确用途靠前且不合并同字段身份', async () => {
    const sortable = [
      { ...entries[0], key: '21:deposit_balance', sourceId: 21, metricName: '普通指标', semantic: 'other' },
      { ...entries[0], key: '22:deposit_balance', sourceId: 22, metricName: '增长指标', semantic: 'deposit' },
      { ...entries[0], key: '23:deposit_balance', sourceId: 23, metricName: '当前来源指标', semantic: 'other' },
      { ...entries[0], key: '23:deposit_balance-copy', sourceId: 23, metricName: '同源不同身份', semantic: 'other' }
    ];
    const wrapper = mount(BusinessMetricPicker, {
      props: {
        entries: sortable,
        modelValue: { datasourceId: 23, field: 'deposit_balance' },
        relevanceContext: { semantic: 'deposit', keywords: ['增长'], preferredSourceIds: [23] }
      }
    });

    await wrapper.get('[data-testid="business-metric-toggle-other"]').trigger('click');
    expect(wrapper.findAll('[data-metric-option]').map(node => node.attributes('data-metric-key')))
      .toEqual(['23:deposit_balance', '23:deposit_balance-copy', '22:deposit_balance', '21:deposit_balance']);
  });

  it('hideTestNamed=false 时保留测试指标可见但仍按可用性和诊断开关处理', () => {
    const wrapper = mount(BusinessMetricPicker, { props: { entries: testEntries, hideTestNamed: false } });
    expect(wrapper.get('[data-metric-key="12:test_revenue"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="business-metric-toggle-test"]').text()).toContain('隐藏测试指标（2）');
  });

  it('来源名称含存贷款时仍按字段语义分相关与其他，不合并不同 source+field 身份', async () => {
    const mixedSourceEntries = [
      { ...entries[0], key: '30:deposit_balance', sourceId: 30, sourceDescription: '存款贷款综合来源' },
      { ...entries[0], key: '30:loan_balance', sourceId: 30, field: 'loan_balance', metricName: '贷款余额', sourceDescription: '存款贷款综合来源', semantic: 'loan' }
    ];
    const wrapper = mount(BusinessMetricPicker, {
      props: { entries: mixedSourceEntries, relevanceContext: { keywords: ['存款'] } }
    });

    expect(wrapper.find('[data-metric-key="30:deposit_balance"]').exists()).toBe(true);
    expect(wrapper.find('[data-metric-key="30:loan_balance"]').exists()).toBe(false);
    await wrapper.get('[data-testid="business-metric-toggle-other"]').trigger('click');
    expect(wrapper.find('[data-metric-key="30:loan_balance"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-metric-option]')).toHaveLength(2);
  });

  it('没有相关候选但存在其他适配项时明确提示展开，不误报没有来源', () => {
    const wrapper = mount(BusinessMetricPicker, {
      props: { entries: [entries[0]], relevanceContext: { keywords: ['不存在'] } }
    });

    expect(wrapper.get('[data-testid="business-metric-empty"]').text()).toContain('暂无相关候选');
    expect(wrapper.get('[data-testid="business-metric-empty"]').text()).toContain('其他适配指标');
    expect(wrapper.get('[data-testid="business-metric-toggle-other"]').text()).toContain('查看其他适配指标（1）');
  });
});
