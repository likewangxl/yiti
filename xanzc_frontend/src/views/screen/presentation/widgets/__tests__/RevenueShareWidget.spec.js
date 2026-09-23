// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import RevenueShareWidget from '../RevenueShareWidget.vue';

const operating = { componentId: 'business-revenue-operating', rawValue: 200000000, sourceUnit: 'YUAN', text: '2.00亿元', state: 'READY', monthDelta: { text: '较上月 +0.10亿元' } };
const intermediary = { componentId: 'business-revenue-fee', rawValue: 5000, sourceUnit: 'TEN_THOUSAND', text: '5000万元', state: 'READY', monthDelta: { text: '较上月 +100万元' } };

describe('RevenueShareWidget', () => {
  it('用分段比例条表达中收包含于营业收入并展示原值', () => {
    const wrapper = mount(RevenueShareWidget, { props: { operating, intermediary } });
    const chart = wrapper.get('[data-testid="revenue-share-chart"]');
    expect(chart.attributes('data-state')).toBe('READY');
    expect(chart.get('[role="img"]').attributes('aria-label')).toContain('中间业务收入占营业收入 25.0%');
    expect(wrapper.get('[data-testid="revenue-share-part"]').element.style.width).toBe('25%');
    expect(wrapper.get('[data-testid="revenue-share-rest"]').element.style.width).toBe('75%');
    expect(wrapper.text()).toContain('2.00亿元');
    expect(wrapper.text()).toContain('5000万元');
    expect(wrapper.text()).toContain('较上月 +100万元');
  });

  it('金额不完整时显示待核对，不将比例缺失伪装成零', () => {
    const wrapper = mount(RevenueShareWidget, { props: { operating, intermediary: { ...intermediary, rawValue: null, state: 'NO_VALUE' } } });
    expect(wrapper.get('[data-testid="revenue-share-chart"]').attributes('data-state')).toBe('PENDING');
    expect(wrapper.find('[data-testid="revenue-share-part"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('占比待核对');
  });
});
