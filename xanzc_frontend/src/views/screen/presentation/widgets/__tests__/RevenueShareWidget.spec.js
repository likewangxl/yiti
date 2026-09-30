// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import RevenueShareWidget from '../RevenueShareWidget.vue';

const operating = { componentId: 'business-revenue-operating', rawValue: 200000000, sourceUnit: 'YUAN', text: '2.00亿元', state: 'READY', monthDelta: { text: '较上月 +0.10亿元' } };
const intermediary = { componentId: 'business-revenue-fee', rawValue: 5000, sourceUnit: 'TEN_THOUSAND', text: '5000万元', state: 'READY', monthDelta: { text: '较上月 +100万元' } };

describe('RevenueShareWidget', () => {
  it('上下两张共享收入卡保留原值与月差，不显示比例区', () => {
    const wrapper = mount(RevenueShareWidget, { props: { operating, intermediary } });
    const chart = wrapper.get('[data-testid="revenue-share-chart"]');
    expect(chart.attributes('data-state')).toBe('READY');
    expect(chart.find('[role="img"]').exists()).toBe(false);
    expect(chart.find('[data-testid="revenue-share-ratio-label"]').exists()).toBe(false);
    expect(chart.find('[data-testid="revenue-share-percentage"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('2.00亿元');
    expect(wrapper.text()).toContain('5000万元');
    expect(wrapper.text()).toContain('较上月 +100万元');
  });

  it('把营业收入与中间业务收入分成上下等高语义子卡，并保留对应图标', () => {
    const wrapper = mount(RevenueShareWidget, { props: { operating, intermediary } });
    const operatingSection = wrapper.get('[data-testid="revenue-share-operating-section"]');
    const intermediarySection = wrapper.get('[data-testid="revenue-share-intermediary-section"]');

    expect(operatingSection.attributes('aria-label')).toBe('营业收入');
    expect(intermediarySection.attributes('aria-label')).toBe('中间业务收入');
    expect(operatingSection.text()).toContain('2.00亿元');
    expect(operatingSection.text()).toContain('较上月 +0.10亿元');
    expect(operatingSection.text()).not.toContain('5000万元');
    expect(intermediarySection.text()).toContain('5000万元');
    expect(intermediarySection.text()).toContain('较上月 +100万元');
    expect(intermediarySection.text()).not.toContain('2.00亿元');
    expect(intermediarySection.find('[data-testid="revenue-share-ratio-label"]').exists()).toBe(false);
    expect(intermediarySection.find('[data-testid="revenue-share-percentage"]').exists()).toBe(false);
    expect(intermediarySection.find('[role="img"]').exists()).toBe(false);
    expect(operatingSection.find('[role="img"]').exists()).toBe(false);

    const operatingCard = operatingSection.get('.presentation-metric-widget');
    const intermediaryCard = intermediarySection.get('.presentation-metric-widget');
    expect(operatingCard.classes()).toContain('presentation-metric-widget--grouped');
    expect(intermediaryCard.classes()).toContain('presentation-metric-widget--grouped');
    expect(operatingCard.get('[data-testid="presentation-metric-icon"]').attributes('data-icon')).toBe('Tickets');
    expect(intermediaryCard.get('[data-testid="presentation-metric-icon"]').attributes('data-icon')).toBe('CreditCard');
    expect(operatingCard.get('[data-testid="presentation-metric-icon"] svg').exists()).toBe(true);
    expect(intermediaryCard.get('[data-testid="presentation-metric-icon"] svg').exists()).toBe(true);
  });

  it('金额不完整时保留共享收入卡缺失态，不显示比例区', () => {
    const wrapper = mount(RevenueShareWidget, { props: { operating, intermediary: { ...intermediary, rawValue: null, state: 'NO_VALUE' } } });
    expect(wrapper.get('[data-testid="revenue-share-chart"]').attributes('data-state')).toBe('PENDING');
    expect(wrapper.find('[data-testid="revenue-share-part"]').exists()).toBe(false);
    expect(wrapper.find('[role="img"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="revenue-share-intermediary-section"] [data-testid="presentation-metric-status"]').text()).toContain('暂无有效值');
    expect(wrapper.text()).not.toContain('占比待核对');
  });

  it('收入子卡在三维模式下各自传递上年、上月、上日对比', () => {
    const comparisons = {
      year: { state: 'READY', text: '较上年 +0.20亿元', referenceDate: '2027-12-31' },
      month: { state: 'READY', text: '较上月 +0.10亿元', referenceDate: '2028-02-29' },
      day: { state: 'READY', text: '较上日 +0.05亿元', referenceDate: '2028-03-01' }
    };
    const wrapper = mount(RevenueShareWidget, { props: {
      operating: { ...operating, comparisons }, intermediary: { ...intermediary, comparisons }
    } });
    expect(wrapper.get('[data-testid="revenue-share-operating-section"] [data-testid="presentation-metric-comparisons"]').text()).toContain('较上年 +0.20亿元');
    expect(wrapper.get('[data-testid="revenue-share-intermediary-section"] [data-testid="presentation-metric-comparisons"]').text()).toContain('较上日 +0.05亿元');
  });

  it('保留来源组件的自定义标题，缺省时才使用收入静态标题', () => {
    const wrapper = mount(RevenueShareWidget, { props: {
      operating: { ...operating, title: '全辖营业收入' }, intermediary: { ...intermediary, title: '全辖中收' }
    } });
    expect(wrapper.get('[data-testid="revenue-share-operating-section"]').text()).toContain('全辖营业收入');
    expect(wrapper.get('[data-testid="revenue-share-intermediary-section"]').text()).toContain('全辖中收');
  });
});
