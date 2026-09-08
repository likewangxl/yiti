// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('vue-echarts', () => ({
  default: {
    props: { option: { type: Object, default: () => ({}) } },
    template: '<div data-testid="retail-chart" :data-option="JSON.stringify(option)" />'
  }
}));

import RetailTrend from '../RetailTrend.vue';

function chartOption(wrapper) {
  return JSON.parse(wrapper.get('[data-testid="retail-chart"]').attributes('data-option'));
}

describe('RetailTrend 零售全辖趋势', () => {
  it('只消费全辖 aum 与 deposit 趋势，不随城市筛选改写数据', () => {
    const wrapper = mount(RetailTrend, {
      props: {
        trend: [
          { date: '2026-08', aum: 1800, deposit: 1280 },
          { date: '2026-09', aum: 1824.6, deposit: 1286.42 }
        ],
        dataDate: '2026-09-06'
      }
    });
    expect(chartOption(wrapper).series.map(item => item.name)).toEqual(['零售AUM', '储蓄余额']);
    expect(chartOption(wrapper).series[0].data).toEqual([1800, 1824.6]);
    expect(wrapper.text()).toContain('全辖');
  });

  it('空值与真实零分别保留，完全无数据时显示空态', () => {
    const wrapper = mount(RetailTrend, {
      props: { trend: [{ date: '2026-09', aum: 0, deposit: null }] }
    });
    const option = chartOption(wrapper);
    expect(option.series[0].data).toEqual([0]);
    expect(option.series[1].data).toEqual([null]);
    const empty = mount(RetailTrend, { props: { trend: [] } });
    expect(empty.find('[data-testid="retail-trend-empty"]').exists()).toBe(true);
  });
});
