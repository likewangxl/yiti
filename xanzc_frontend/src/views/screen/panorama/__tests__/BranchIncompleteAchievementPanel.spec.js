// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import BranchIncompleteAchievementPanel from '../BranchIncompleteAchievementPanel.vue';

describe('支行未完成指标横向图', () => {
  it('只展示未完成目标，完成率统一为百分比宽度并保留原始单位', () => {
    const wrapper = mount(BranchIncompleteAchievementPanel, { props: {
      targets: [
        { key: 'deposit', label: '存款净增', actual: 80, target: 100, unit: '亿元' },
        { key: 'loan', label: '贷款投放', actual: 120, target: 100, unit: '亿元' },
        { key: 'customers', label: '有效客户', actual: 800, target: 1000, unit: '户' }
      ]
    } });

    expect(wrapper.get('[data-testid="branch-incomplete-chart"]').attributes('aria-label')).toContain('未完成');
    const rows = wrapper.findAll('[data-testid="branch-incomplete-row"]');
    expect(rows).toHaveLength(2);
    expect(rows.map(row => row.attributes('data-item-id'))).toEqual(['deposit-0', 'customers-2']);
    expect(rows[0].attributes('data-rate')).toBe('80');
    expect(rows[0].get('[role="progressbar"]').attributes('aria-valuenow')).toBe('80');
    expect(rows[0].text()).toContain('80.00 亿元');
    expect(rows[0].text()).toContain('100.00 亿元');
    expect(rows[0].text()).toContain('20.00 亿元');
    expect(wrapper.text()).not.toContain('贷款投放');
  });

  it('完整列出大量未完成目标并独立提示缺失或零目标', () => {
    const targets = Array.from({ length: 12 }, (_, index) => ({
      key: `metric-${index}`, label: `指标${index + 1}`, actual: index + 1, target: 100, unit: '项'
    }));
    targets.push(
      { key: 'missing-actual', label: '缺少实际', actual: null, target: 10, unit: '户' },
      { key: 'zero-target', label: '零目标', actual: 0, target: 0, unit: '万元' }
    );
    const wrapper = mount(BranchIncompleteAchievementPanel, { props: { targets } });

    expect(wrapper.findAll('[data-testid="branch-incomplete-row"]')).toHaveLength(12);
    expect(wrapper.get('[data-testid="branch-incomplete-list"]').attributes('tabindex')).toBe('0');
    expect(wrapper.findAll('[data-testid="branch-incomplete-missing-row"]')).toHaveLength(2);
    expect(wrapper.text()).toContain('目标值必须大于0');
    expect(wrapper.text()).toContain('实际值缺失');
  });
});
