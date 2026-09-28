/* @vitest-environment happy-dom */
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import BranchCoreMetrics from '../BranchCoreMetrics.vue';

const model = {
  kpis: [
    { key: 'corpDeposit', value: 123456789, unit: '万元', date: '2026-09-20', status: '已核验' },
    { key: 'retailDeposit', value: 0, unit: '万元', date: '2026-09-20', status: '已核验' },
    { key: 'corpLoan', value: 3456789, unit: '万元', date: '2026-09-20', status: '已核验' },
    { key: 'retailLoan', value: 456789, unit: '万元', date: '2026-09-20', status: '已核验' },
    { key: 'revenue', value: 67890, unit: '万元', date: '2026-09-20', status: '已核验' },
    { key: 'intermediaryIncome', value: 12345, unit: '万元', date: '2026-09-20', status: '已核验' }
  ]
};

describe('BranchCoreMetrics', () => {
  it('按固定六项顺序展示来源金额、单位、日期和状态，长值使用千分位且保留零值', () => {
    const wrapper = mount(BranchCoreMetrics, { props: { model } });
    const cards = wrapper.findAll('[data-testid="branch-core-metric"]');

    expect(cards).toHaveLength(6);
    expect(cards.map(card => card.attributes('data-metric-key'))).toEqual([
      'corpDeposit', 'retailDeposit', 'corpLoan', 'retailLoan', 'revenue', 'intermediaryIncome'
    ]);
    expect(cards[0].text()).toContain('123,456,789');
    expect(cards[1].text()).toContain('0');
    expect(cards[0].text()).toContain('万元');
    expect(cards[0].text()).toContain('2026-09-20');
    expect(cards[0].text()).toContain('已核验');
  });

  it('来源缺失时只显示破折号，不用存款总量或其他指标估算分拆', () => {
    const wrapper = mount(BranchCoreMetrics, {
      props: {
        model: {
          kpis: [
            { key: 'deposit', value: 999999, unit: '万元', date: '2026-09-20' },
            { key: 'corpDeposit', value: 100, unit: '万元', date: '2026-09-20' }
          ]
        }
      }
    });

    const cards = wrapper.findAll('[data-testid="branch-core-metric"]');
    expect(cards).toHaveLength(6);
    expect(wrapper.get('[data-metric-key="corpDeposit"]').text()).toContain('100');
    for (const key of ['retailDeposit', 'corpLoan', 'retailLoan', 'revenue', 'intermediaryIncome']) {
      const text = wrapper.get(`[data-metric-key="${key}"]`).text();
      expect(text).toContain('—');
      expect(text).not.toContain('999,999');
    }
  });
});
