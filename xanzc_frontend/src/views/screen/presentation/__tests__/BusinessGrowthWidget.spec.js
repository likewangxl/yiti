// @vitest-environment happy-dom
import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

import BusinessGrowthWidget from '../widgets/BusinessGrowthWidget.vue';

vi.mock('../../panorama/PanoramaTrend.vue', () => ({ default: {
  props: ['rows', 'series', 'title', 'compact', 'amountFriendly', 'amountUnit'],
  template: '<div data-testid="panorama-trend-mock" :data-compact="compact ? \'true\' : \'false\'" :data-amount-friendly="amountFriendly ? \'true\' : \'false\'" :data-amount-unit="amountUnit">{{ title }}|{{ series.map(item => item.label).join(\',\') }}|{{ rows.length }}</div>'
} }));

const presentation = {
  displaySchemaVersion: 1,
  template: 'branch-overview-v1',
  display: { components: [{
    componentId: 'trend-main',
    componentType: 'TREND',
    layoutRegion: 'CENTER',
    content: { series: [
      { seriesKey: 'retailDeposit', field: '测试_直营零售存款', label: '存款', unit: 'YUAN' },
      { seriesKey: 'retailLoan', field: '测试_直营零售贷款', label: '贷款', unit: 'YUAN' },
      { seriesKey: 'corpDeposit', field: '测试_直营对公存款', label: '存款', unit: 'YUAN' },
      { seriesKey: 'corpLoan', field: '测试_直营对公贷款', label: '贷款', unit: 'YUAN' }
    ] },
    dataRefs: [{ blockId: 57, unit: 'YUAN' }]
  }] }
};

const rows = [{
  date: '2026-09-28',
  测试_直营零售存款: 100,
  测试_直营零售贷款: 200,
  测试_直营对公存款: 300,
  测试_直营对公贷款: 400
}];

describe('BusinessGrowthWidget', () => {
  it('显示业务增长曲线标题和同时可见的零售/对公两张存贷款图', () => {
    const wrapper = mount(BusinessGrowthWidget, {
      props: {
        presentation,
        model: { blockResults: { 57: { rows, unitByField: Object.fromEntries([
          ['测试_直营零售存款', 'YUAN'], ['测试_直营零售贷款', 'YUAN'],
          ['测试_直营对公存款', 'YUAN'], ['测试_直营对公贷款', 'YUAN']
        ]) } } }
      }
    });

    expect(wrapper.get('[data-testid="business-growth-widget"] h2').text()).toBe('业务增长曲线');
    expect(wrapper.findAll('[data-business-line]')).toHaveLength(2);
    expect(wrapper.find('[data-business-line="RETAIL"] h3').text()).toBe('零售业务');
    expect(wrapper.find('[data-business-line="CORP"] h3').text()).toBe('对公业务');
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]')).toHaveLength(2);
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').map(node => node.text()))
      .toEqual(['零售业务|存款,贷款|1', '对公业务|存款,贷款|1']);
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').every(node => node.attributes('data-compact') === 'true')).toBe(true);
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').every(node => node.attributes('data-amount-friendly') === 'true')).toBe(true);
    expect(wrapper.get('.business-growth-widget__heading small').text()).toBe('单位：元');
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').every(node => node.attributes('data-amount-unit') === 'YUAN')).toBe(true);
  });

  it('动态显示并传递有效金额单位，非法选择回退元', async () => {
    const wrapper = mount(BusinessGrowthWidget, {
      props: {
        amountUnit: 'TEN_THOUSAND',
        presentation,
        model: { blockResults: { 57: { rows, unitByField: Object.fromEntries([
          ['测试_直营零售存款', 'YUAN'], ['测试_直营零售贷款', 'YUAN'],
          ['测试_直营对公存款', 'YUAN'], ['测试_直营对公贷款', 'YUAN']
        ]) } } }
      }
    });

    expect(wrapper.get('.business-growth-widget__heading small').text()).toBe('单位：万元');
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').every(node => node.attributes('data-amount-unit') === 'TEN_THOUSAND')).toBe(true);

    await wrapper.setProps({ amountUnit: 'HUNDRED_MILLION' });
    expect(wrapper.get('.business-growth-widget__heading small').text()).toBe('单位：亿元');
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').every(node => node.attributes('data-amount-unit') === 'HUNDRED_MILLION')).toBe(true);

    await wrapper.setProps({ amountUnit: 'PERCENT' });
    expect(wrapper.get('.business-growth-widget__heading small').text()).toBe('单位：元');
    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]').every(node => node.attributes('data-amount-unit') === 'YUAN')).toBe(true);
  });

  it('历史四列全为空时分别显示业务线待接入空态，不渲染猜测曲线', () => {
    const emptyRows = [{
      data_date: '2026-09-28',
      测试_直营零售存款: null,
      测试_直营零售贷款: null,
      测试_直营对公存款: null,
      测试_直营对公贷款: null
    }];
    const wrapper = mount(BusinessGrowthWidget, {
      props: {
        presentation,
        model: { blockResults: { 57: { rows: emptyRows, unitByField: Object.fromEntries([
          ['测试_直营零售存款', 'YUAN'], ['测试_直营零售贷款', 'YUAN'],
          ['测试_直营对公存款', 'YUAN'], ['测试_直营对公贷款', 'YUAN']
        ]) } } }
      }
    });

    expect(wrapper.findAll('[data-testid="panorama-trend-mock"]')).toHaveLength(0);
    expect(wrapper.find('[data-business-line="RETAIL"] [data-testid="business-growth-empty"]').text()).toBe('零售业务历史存贷款数据待接入');
    expect(wrapper.find('[data-business-line="CORP"] [data-testid="business-growth-empty"]').text()).toBe('对公业务历史存贷款数据待接入');
  });
});
