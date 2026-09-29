// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';

import OverviewBalanceChart from '../OverviewBalanceChart.vue';
import { buildOverviewBalanceChartModel } from '../overviewBalanceChartModel';

const metric = {
  baseValue: 120 * 1e8,
  unit: 'HUNDRED_MILLION',
  date: '2028-03-02',
  dateValid: true,
  comparisons: {
    year: { baseValue: 30 * 1e8, referenceDate: '2027-12-31' },
    month: { baseValue: 20 * 1e8, referenceDate: '2028-02-29' },
    day: { baseValue: 10 * 1e8, referenceDate: '2028-03-01' }
  }
};

describe('OverviewBalanceChart', () => {
  it('按较昨日、较上月、较上年顺序还原基期余额，并保留当前减基期的增长量', () => {
    const model = buildOverviewBalanceChartModel(metric, 'HUNDRED_MILLION');

    expect(model.state).toBe('READY');
    expect(model.unit).toBe('HUNDRED_MILLION');
    expect(model.bars.map(item => ({
      key: item.key, label: item.label, value: item.value, text: item.text, growth: item.growth, growthText: item.growthText
    }))).toEqual([
      { key: 'day', label: '较昨日', value: 110 * 1e8, text: '110.00亿元', growth: 10 * 1e8, growthText: '+10亿' },
      { key: 'month', label: '较上月', value: 100 * 1e8, text: '100.00亿元', growth: 20 * 1e8, growthText: '+20亿' },
      { key: 'year', label: '较上年', value: 90 * 1e8, text: '90.00亿元', growth: 30 * 1e8, growthText: '+30亿' }
    ]);
    expect(model.bars.map(item => item.percent)).toEqual([100, 90.91, 81.82]);
    expect(model.bars.every(item => item.referenceDate)).toBe(true);
  });

  it('比较日期必须符合日、月末、年末项目口径，缺失或非法时保持待接入', () => {
    const cases = [
      { ...metric, comparisons: { ...metric.comparisons, day: { baseValue: 10 * 1e8, referenceDate: '2028-03-02' } } },
      { ...metric, comparisons: { ...metric.comparisons, month: { baseValue: 20 * 1e8, referenceDate: '2028-02-28' } } },
      { ...metric, comparisons: { ...metric.comparisons, year: { baseValue: 30 * 1e8, referenceDate: '2028-01-01' } } },
      { ...metric, comparisons: { ...metric.comparisons, year: undefined } },
      { ...metric, date: '2028-02-31', dateValid: true }
    ];

    for (const candidate of cases) {
      const model = buildOverviewBalanceChartModel(candidate, 'HUNDRED_MILLION');
      if (candidate.date === '2028-02-31') {
        expect(model.state).toBe('PENDING');
        expect(model.bars.every(item => item.state === 'PENDING')).toBe(true);
      }
      if (candidate.comparisons.day?.referenceDate === '2028-03-02') {
        expect(model.bars.find(item => item.key === 'day')).toMatchObject({ state: 'PENDING', value: null, text: '待接入' });
      }
      if (candidate.comparisons.month?.referenceDate === '2028-02-28') {
        expect(model.bars.find(item => item.key === 'month')).toMatchObject({ state: 'PENDING', value: null, text: '待接入' });
      }
      if (candidate.comparisons.year?.referenceDate === '2028-01-01' || !candidate.comparisons.year) {
        expect(model.bars.find(item => item.key === 'year')).toMatchObject({ state: 'PENDING', value: null, text: '待接入' });
      }
    }
  });

  it('负基期不造数，零余额保留真实零值并不绘制可见填充条', () => {
    const zeroMetric = {
      ...metric,
      baseValue: 0,
      comparisons: {
        year: { baseValue: 0, referenceDate: '2027-12-31' },
        month: { baseValue: -1 * 1e8, referenceDate: '2028-02-29' },
        day: { baseValue: 0, referenceDate: '2028-03-01' }
      }
    };
    const wrapper = mount(OverviewBalanceChart, { props: { metric: zeroMetric, displayUnit: 'YUAN' } });

    expect(wrapper.find('[data-bar-key="day"] [data-testid="overview-balance-fill"]').exists()).toBe(false);
    expect(wrapper.find('[data-bar-key="month"] [data-testid="overview-balance-fill"]').exists()).toBe(true);
    expect(wrapper.get('[data-bar-key="day"]').text()).toContain('0.00元');
    expect(wrapper.get('[data-testid="overview-balance-bars"]').attributes('aria-label')).toContain('较上月 100,000,000.00元 -100000000元');
  });

  it.each([' ', [], [1], {}, true])('非法当前余额 %j 保持待接入，不按 Number 强转成真实值', invalidValue => {
    const model = buildOverviewBalanceChartModel({ ...metric, baseValue: invalidValue }, 'HUNDRED_MILLION');
    expect(model.state).toBe('PENDING');
    expect(model.bars.every(item => item.state === 'PENDING')).toBe(true);
  });

  it.each([' ', [], [1], {}, true])('非法比较基期增量 %j 不还原历史余额', invalidValue => {
    const model = buildOverviewBalanceChartModel({
      ...metric,
      comparisons: { ...metric.comparisons, month: { baseValue: invalidValue, referenceDate: '2028-02-29' } }
    }, 'HUNDRED_MILLION');
    expect(model.bars.find(item => item.key === 'month')).toMatchObject({ state: 'PENDING', value: null, text: '待接入' });
  });

  it('增长量使用当前单位的元/万/亿简写，最多两位小数且保留正负号', () => {
    const candidate = {
      ...metric,
      baseValue: 1001.32 * 1e4,
      unit: 'TEN_THOUSAND',
      comparisons: {
        day: { baseValue: -100.9 * 1e4, referenceDate: '2028-03-01' },
        month: { baseValue: 0, referenceDate: '2028-02-29' },
        year: { baseValue: 1001.32 * 1e4, referenceDate: '2027-12-31' }
      }
    };
    const model = buildOverviewBalanceChartModel(candidate, 'TEN_THOUSAND');
    expect(model.bars.map(item => item.growthText)).toEqual(['-100.9万', '0万', '+1001.32万']);
  });

  it('组件只显示标签、基期余额和增长量，不显示 header 日期或基期日期', () => {
    const wrapper = mount(OverviewBalanceChart, { props: { metric, displayUnit: 'TEN_THOUSAND' } });

    expect(wrapper.find('.overview-balance-chart__header').exists()).toBe(false);
    expect(wrapper.findAll('[data-bar-key]')).toHaveLength(3);
    expect(wrapper.get('[data-bar-key="day"]').text()).toContain('1,100,000.00万元');
    expect(wrapper.get('[data-bar-key="day"]').text()).toContain('+100000万');
    expect(wrapper.get('[data-bar-key="year"]').text()).toContain('+300000万');
    expect(wrapper.get('[data-testid="overview-balance-bars"]').text()).not.toContain('2028-03');
    expect(wrapper.get('[data-testid="overview-balance-bars"]').attributes('aria-label')).not.toContain('2028-03');
  });
});
