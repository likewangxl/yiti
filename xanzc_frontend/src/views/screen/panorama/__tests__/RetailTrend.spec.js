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
  it('只消费授权范围 aum 与 deposit 趋势，不随城市筛选改写数据', () => {
    const wrapper = mount(RetailTrend, {
      props: {
        trend: [
          { date: '2026-08', aum: 1800, deposit: 1280 },
          { date: '2026-09', aum: 1824.6, deposit: 1286.42 }
        ],
        dataDate: '2026-09-06'
      }
    });
    expect(chartOption(wrapper).series.map(item => item.name)).toEqual(['零售AUM', '零售一般性存款余额']);
    expect(chartOption(wrapper).series[0].data).toEqual([1800, 1824.6]);
    expect(wrapper.text()).toContain('当前授权范围');
  });

  it('真实数据只接入存款时不展示 AUM 图例或空序列', () => {
    const wrapper = mount(RetailTrend, {
      props: { trend: [{ date: '2026-09', deposit: 1286.42 }] }
    });
    expect(chartOption(wrapper).series.map(item => item.name)).toEqual(['零售一般性存款余额']);
    expect(wrapper.text()).not.toContain('AUM');
  });

  it('空值与真实零分别保留，完全无数据时显示空态', () => {
    const wrapper = mount(RetailTrend, {
      props: { trend: [{ date: '2026-09', aum: 0, deposit: null }] }
    });
    const option = chartOption(wrapper);
    expect(option.series[0].data).toEqual([0]);
    expect(option.series).toHaveLength(1);
    const empty = mount(RetailTrend, { props: { trend: [] } });
    expect(empty.find('[data-testid="retail-trend-empty"]').exists()).toBe(true);
  });

  it('存款趋势同时展示余额与月日均，统一单位且不使用平滑曲线', () => {
    const wrapper = mount(RetailTrend, {
      props: {
        trend: [
          { date: '2026-07', deposit: 0.8, depositAverage: 0.79 },
          { date: '2026-08', deposit: 0.9, depositAverage: 0.88 }
        ]
      }
    });
    const option = chartOption(wrapper);
    expect(option.series.map(item => item.name)).toEqual(['零售一般性存款余额', '零售存款月日均']);
    expect(option.series.every(item => item.smooth === false)).toBe(true);
    expect(option.yAxis.name).toBe('万元');
    expect(wrapper.get('[data-testid="retail-trend-summary"]').text()).toContain('观察区间 2026-07 至 2026-08');
    expect(wrapper.get('[data-testid="retail-trend-summary"]').text()).not.toContain('环比');
    expect(wrapper.get('[data-testid="retail-trend-summary"]').text()).toContain('峰值');
    expect(wrapper.get('[data-testid="retail-trend-summary"]').text()).toContain('谷值');
  });
});
