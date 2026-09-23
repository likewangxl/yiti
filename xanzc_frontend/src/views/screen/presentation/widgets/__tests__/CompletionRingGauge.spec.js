// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import CompletionRingGauge from '../CompletionRingGauge.vue';

const CIRCUMFERENCE = 2 * Math.PI * 29;

describe('CompletionRingGauge', () => {
  it.each([
    { text: '90.64%', value: 90.64, accent: '#4de8ef' },
    { text: '86.44%', value: 86.44, accent: '#a979ff' }
  ])('保留中心百分比文本并按数值绘制环进度：$text', ({ text, value, accent }) => {
    const wrapper = mount(CompletionRingGauge, { props: { text, value, accent } });

    expect(wrapper.find('[data-testid="completion-ring-value"]').text()).toBe(text);
    expect(wrapper.attributes('data-state')).toBe('READY');
    expect(wrapper.attributes('data-progress')).toBe(String(value));
    expect(wrapper.attributes('style')).toContain(`--completion-ring-accent: ${accent}`);
    expect(Number(wrapper.find('[data-testid="completion-ring-progress"]').attributes('stroke-dashoffset')))
      .toBeCloseTo(CIRCUMFERENCE * (1 - value / 100), 5);
  });

  it('超出100时保留真实中心文本，但环形进度封顶100', () => {
    const wrapper = mount(CompletionRingGauge, { props: { text: '125.00%', value: 125 } });

    expect(wrapper.find('[data-testid="completion-ring-value"]').text()).toBe('125.00%');
    expect(wrapper.attributes('data-progress')).toBe('100');
    expect(Number(wrapper.find('[data-testid="completion-ring-progress"]').attributes('stroke-dashoffset')))
      .toBeCloseTo(0, 5);
  });

  it('负值环进度为0但保留真实中心文本', () => {
    const wrapper = mount(CompletionRingGauge, { props: { text: '-4.00%', value: -4 } });

    expect(wrapper.find('[data-testid="completion-ring-value"]').text()).toBe('-4.00%');
    expect(wrapper.attributes('data-progress')).toBe('0');
    expect(Number(wrapper.find('[data-testid="completion-ring-progress"]').attributes('stroke-dashoffset')))
      .toBeCloseTo(CIRCUMFERENCE, 5);
  });

  it('缺数显示空环和待接入文本，不把缺失值伪装为0%', () => {
    const wrapper = mount(CompletionRingGauge, { props: { text: '待接入', value: null } });

    expect(wrapper.find('[data-testid="completion-ring-value"]').text()).toBe('待接入');
    expect(wrapper.attributes('data-state')).toBe('MISSING');
    expect(wrapper.attributes('data-progress')).toBe('0');
    expect(Number(wrapper.find('[data-testid="completion-ring-progress"]').attributes('stroke-dashoffset')))
      .toBeCloseTo(CIRCUMFERENCE, 5);
  });
});
