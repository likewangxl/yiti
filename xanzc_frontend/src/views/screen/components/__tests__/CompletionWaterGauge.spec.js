// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';

import CompletionWaterGauge from '../CompletionWaterGauge.vue';

function mountGauge(value, props = {}) {
  return mount(CompletionWaterGauge, {
    props: { value, ...props }
  });
}

describe('CompletionWaterGauge', () => {
  it('以两位小数显示零值，并将水位与颜色标记为珊瑚色段', () => {
    const wrapper = mountGauge(0);
    const gauge = wrapper.get('[data-testid="completion-water-gauge"]');

    expect(gauge.attributes('data-level')).toBe('0');
    expect(gauge.attributes('data-tone')).toBe('coral');
    expect(gauge.attributes('aria-label')).toBe('目标完成率 0.00%');
    expect(gauge.find('[data-testid="target-progress-value"]').text()).toBe('0.00%');
    expect(gauge.findAll('path')).toHaveLength(0);
    expect(gauge.find('clipPath').exists()).toBe(true);
  });

  it('暂无数据时保留空态，不把 null 伪装成 0%', () => {
    const wrapper = mountGauge(null);
    const gauge = wrapper.get('[data-testid="completion-water-gauge"]');

    expect(gauge.attributes('data-level')).toBe('unknown');
    expect(gauge.attributes('data-tone')).toBe('unknown');
    expect(gauge.attributes('aria-label')).toBe('目标完成率 暂无数据');
    expect(gauge.find('[data-testid="target-progress-value"]').text()).toBe('—');
    expect(gauge.text()).not.toContain('0.00%');
  });

  it('保留 85.92 的原始百分比，并将水位封顶范围内的值标为蓝青色段', () => {
    const wrapper = mountGauge(85.92);
    const gauge = wrapper.get('[data-testid="completion-water-gauge"]');

    expect(gauge.attributes('data-level')).toBe('85.92');
    expect(gauge.attributes('data-tone')).toBe('cyan');
    expect(gauge.attributes('aria-label')).toBe('目标完成率 85.92%');
    expect(gauge.find('[data-testid="target-progress-value"]').text()).toBe('85.92%');
  });

  it('超额完成时水位封顶 100，但中心数字与可访问名称保留真实值', () => {
    const wrapper = mountGauge(125.5);
    const gauge = wrapper.get('[data-testid="completion-water-gauge"]');

    expect(gauge.attributes('data-level')).toBe('100');
    expect(gauge.attributes('data-tone')).toBe('green');
    expect(gauge.attributes('aria-label')).toBe('目标完成率 125.50%');
    expect(gauge.find('[data-testid="target-progress-value"]').text()).toBe('125.50%');
  });

  it('负值的水位归零，但中心数字保留真实负值并落在珊瑚色段', () => {
    const wrapper = mountGauge(-7.25);
    const gauge = wrapper.get('[data-testid="completion-water-gauge"]');

    expect(gauge.attributes('data-level')).toBe('0');
    expect(gauge.attributes('data-tone')).toBe('coral');
    expect(gauge.attributes('aria-label')).toBe('目标完成率 -7.25%');
    expect(gauge.find('[data-testid="target-progress-value"]').text()).toBe('-7.25%');
  });

  it.each([
    [59.99, 'coral'],
    [60, 'gold'],
    [79.99, 'gold'],
    [80, 'cyan'],
    [99.99, 'cyan'],
    [100, 'green']
  ])('按 %s 的阈值将视觉色段标为 %s', (value, tone) => {
    const wrapper = mountGauge(value);
    expect(wrapper.get('[data-testid="completion-water-gauge"]').attributes('data-tone')).toBe(tone);
  });

  it('支持自定义标签并为多个实例生成不冲突的 SVG clip id', () => {
    const first = mountGauge(60, { label: '季度目标' });
    const second = mountGauge(60);

    expect(first.get('[data-testid="completion-water-gauge"]').attributes('aria-label')).toBe('季度目标 60.00%');
    expect(first.find('clipPath').attributes('id')).not.toBe(second.find('clipPath').attributes('id'));
  });

  it('贷款变体使用独立色系，并让前后水体各自包含明显的多色渐变', () => {
    const wrapper = mountGauge(72, { variant: 'loan', label: '零售贷款目标完成率' });
    const gauge = wrapper.get('[data-testid="completion-water-gauge"]');

    expect(gauge.attributes('data-variant')).toBe('loan');
    expect(gauge.attributes('aria-label')).toBe('零售贷款目标完成率 72.00%');
    const gradients = gauge.findAll('linearGradient');
    expect(gradients).toHaveLength(2);
    gradients.forEach(gradient => {
      const colors = gradient.findAll('stop').map(stop => stop.attributes('stop-color'));
      expect(colors.length).toBeGreaterThanOrEqual(2);
      expect(new Set(colors).size).toBeGreaterThanOrEqual(2);
    });
    expect(gauge.find('circle.completion-water-gauge__edge').attributes('stroke')).toMatch(/^#/);
  });

  it('默认变体仍是存款蓝青色系并兼容旧调用', () => {
    const wrapper = mountGauge(72);
    const gauge = wrapper.get('[data-testid="completion-water-gauge"]');
    expect(gauge.attributes('data-variant')).toBe('deposit');
    const colors = gauge.findAll('linearGradient').at(0).findAll('stop').map(stop => stop.attributes('stop-color'));
    expect(new Set(colors).size).toBeGreaterThanOrEqual(2);
    expect(colors.some(color => ['#45d7e7', '#29b9e7', '#2e8bff', '#0d6efd'].includes(color))).toBe(true);
  });
});
