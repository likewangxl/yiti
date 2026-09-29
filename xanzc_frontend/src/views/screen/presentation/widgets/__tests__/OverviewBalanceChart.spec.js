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
    month: { baseValue: 20 * 1e8, referenceDate: '2028-02-29' },
    day: { baseValue: 10 * 1e8, referenceDate: '2028-03-01' }
  }
};

describe('OverviewBalanceChart', () => {
  it('从当前余额减合法比较增量还原上月末与上日，并按当前单位生成横向条形图数据', () => {
    const model = buildOverviewBalanceChartModel(metric, 'HUNDRED_MILLION');

    expect(model.state).toBe('READY');
    expect(model.unit).toBe('HUNDRED_MILLION');
    expect(model.bars.map(item => ({ key: item.key, value: item.value, text: item.text }))).toEqual([
      { key: 'current', value: 120 * 1e8, text: '120.00亿元' },
      { key: 'month', value: 100 * 1e8, text: '100.00亿元' },
      { key: 'day', value: 110 * 1e8, text: '110.00亿元' }
    ]);
    expect(model.bars.map(item => item.percent)).toEqual([100, 83.33, 91.67]);
  });

  it('缺少显式基准日、当前日期非法、未来基准日或还原出负基期时保持待接入，不绘制伪造零值', () => {
    const cases = [
      { ...metric, comparisons: { ...metric.comparisons, month: { baseValue: 20 * 1e8 } } },
      { ...metric, date: '2028-02-31', dateValid: true },
      { ...metric, comparisons: { ...metric.comparisons, day: { baseValue: 10 * 1e8, referenceDate: '2028-03-03' } } },
      { ...metric, comparisons: { ...metric.comparisons, day: { baseValue: 130 * 1e8, referenceDate: '2028-03-01' } } }
    ];

    for (const candidate of cases) {
      const model = buildOverviewBalanceChartModel(candidate, 'HUNDRED_MILLION');
      const invalidCurrentDate = candidate.date === '2028-02-31';
      const expectedMonthState = !invalidCurrentDate && candidate.comparisons.month?.referenceDate ? 'READY' : 'PENDING';
      expect(model.bars.find(item => item.key === 'month')?.state).toBe(expectedMonthState);
      if (invalidCurrentDate) {
        expect(model.state).toBe('PENDING');
        expect(model.bars.find(item => item.key === 'current')).toMatchObject({ state: 'PENDING', referenceDate: '', text: '待接入' });
      }
      if (candidate.comparisons.day?.referenceDate === '2028-03-03' || candidate.comparisons.day?.baseValue === 130 * 1e8) {
        expect(model.bars.find(item => item.key === 'day')).toMatchObject({ state: 'PENDING', value: null, text: '待接入' });
      }
    }
  });

  it('零余额基线保留真实零值，但不绘制可见填充条', () => {
    const zeroMetric = {
      ...metric,
      baseValue: 0,
      comparisons: {
        month: { baseValue: -1 * 1e8, referenceDate: '2028-02-29' },
        day: { baseValue: 0, referenceDate: '2028-03-01' }
      }
    };
    const wrapper = mount(OverviewBalanceChart, { props: { metric: zeroMetric, displayUnit: 'YUAN' } });

    expect(wrapper.find('[data-bar-key="current"] [data-testid="overview-balance-fill"]').exists()).toBe(false);
    expect(wrapper.find('[data-bar-key="month"] [data-testid="overview-balance-fill"]').exists()).toBe(true);
    expect(wrapper.get('[data-bar-key="current"]').text()).toContain('0.00元');
    expect(wrapper.get('[data-testid="overview-balance-bars"]').attributes('aria-label')).toContain('上月末 2028-02-29 100,000,000.00元');
  });

  it.each([' ', [], [1], {}, true])('非法当前余额 %j 保持待接入，不按 Number 强转成真实值', invalidValue => {
    const model = buildOverviewBalanceChartModel({ ...metric, baseValue: invalidValue }, 'HUNDRED_MILLION');

    expect(model.state).toBe('PENDING');
    expect(model.bars[0]).toMatchObject({ state: 'PENDING', value: null, text: '待接入' });
  });

  it.each([' ', [], [1], {}, true])('非法比较基期增量 %j 不还原历史余额', invalidValue => {
    const model = buildOverviewBalanceChartModel({
      ...metric,
      comparisons: { ...metric.comparisons, month: { baseValue: invalidValue, referenceDate: '2028-02-29' } }
    }, 'HUNDRED_MILLION');

    expect(model.bars.find(item => item.key === 'month')).toMatchObject({ state: 'PENDING', value: null, text: '待接入' });
  });

  it('单位切换同步条形图标签，长金额保持单元格可收缩', () => {
    const wrapper = mount(OverviewBalanceChart, { props: { metric, displayUnit: 'TEN_THOUSAND' } });
    expect(wrapper.attributes('data-unit')).toBe('TEN_THOUSAND');
    expect(wrapper.get('[data-bar-key="current"]').text()).toContain('1,200,000.00万元');
    expect(wrapper.get('[data-bar-key="current"]').classes()).toContain('overview-balance-chart__bar');
    expect(wrapper.get('[data-testid="overview-balance-bars"]').attributes('aria-label')).toContain('当前 2028-03-02 1,200,000.00万元');

    wrapper.setProps({ displayUnit: 'YUAN' });
    return wrapper.vm.$nextTick().then(() => {
      expect(wrapper.attributes('data-unit')).toBe('YUAN');
      expect(wrapper.get('[data-bar-key="current"]').text()).toContain('12,000,000,000.00元');
      expect(wrapper.get('[data-testid="overview-balance-bars"]').attributes('aria-label')).toContain('当前 2028-03-02 12,000,000,000.00元');
    });
  });
});
