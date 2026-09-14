// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('vue-echarts', () => ({
  default: {
    props: { option: { type: Object, default: () => ({}) } },
    template: '<div data-testid="corporate-chart" :data-option="JSON.stringify(option)" />'
  }
}));

import CorporateTrend from '../CorporateTrend.vue';

function chartOption(wrapper) {
  return JSON.parse(wrapper.get('[data-testid="corporate-chart"]').attributes('data-option'));
}

describe('CorporateTrend 对公全辖趋势', () => {
  it('消费 deposit 与 loan 趋势，保持零值和缺失值语义', () => {
    const wrapper = mount(CorporateTrend, {
      props: {
        trend: [
          { date: '2026-08', deposit: 1800, loan: 1280 },
          { date: '2026-09', deposit: 0, loan: null }
        ],
        dataDate: '2026-09-06'
      }
    });
    expect(chartOption(wrapper).series.map(item => item.name)).toEqual(['对公存款', '对公贷款']);
    expect(chartOption(wrapper).series[0].data).toEqual([1800, 0]);
    expect(chartOption(wrapper).series[1].data).toEqual([1280, null]);
    expect(wrapper.text()).toContain('全辖');
  });

  it('完全无趋势数据时显示空态', () => {
    const empty = mount(CorporateTrend, { props: { trend: [] } });
    expect(empty.find('[data-testid="corporate-trend-empty"]').exists()).toBe(true);
  });
});
