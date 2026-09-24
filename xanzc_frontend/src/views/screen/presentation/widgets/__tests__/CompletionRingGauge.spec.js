// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import CompletionRingGauge from '../CompletionRingGauge.vue';

vi.mock('vue-echarts', () => ({
  default: { name: 'VChart', props: ['option'], template: '<div class="chart-stub" />' }
}));

const optionOf = wrapper => wrapper.findComponent({ name: 'VChart' }).props('option').series[0];

describe('CompletionRingGauge', () => {
  it('使用带 0 到 100 刻度、青蓝红分段和指针的 ECharts 百分比仪表盘', () => {
    const wrapper = mount(CompletionRingGauge, { props: { text: '90.64%', value: 90.64, accent: '#4de8ef' } });
    const option = optionOf(wrapper);

    expect(wrapper.find('[data-testid="completion-ring-value"]').text()).toBe('90.64%');
    expect(wrapper.attributes('data-progress')).toBe('90.64');
    expect(option).toMatchObject({
      type: 'gauge', min: 0, max: 100, splitNumber: 10,
      pointer: { show: true }, data: [{ value: 90.64 }]
    });
    expect(option.startAngle - option.endAngle).toBeGreaterThan(180);
    expect(option.axisLine.lineStyle.color).toHaveLength(3);
    expect(option.axisLabel.show).toBe(true);
    expect(option.axisTick.show).toBe(true);
    expect(option.splitLine.show).toBe(true);
    expect(JSON.stringify(option)).not.toMatch(/km\/h|速度/);
  });

  it.each([
    { value: 125, text: '125.00%', progress: 100 },
    { value: -4, text: '-4.00%', progress: 0 }
  ])('真实文本保留 $text，但指针限制在 0 到 100', ({ value, text, progress }) => {
    const wrapper = mount(CompletionRingGauge, { props: { text, value } });
    expect(wrapper.find('[data-testid="completion-ring-value"]').text()).toBe(text);
    expect(wrapper.attributes('data-progress')).toBe(String(progress));
    expect(optionOf(wrapper).data[0].value).toBe(progress);
  });

  it('缺数显示空值，不绘制指针，也不伪装成 0%', () => {
    const wrapper = mount(CompletionRingGauge, { props: { text: '待接入', value: null } });
    expect(wrapper.find('[data-testid="completion-ring-value"]').text()).toBe('—');
    expect(wrapper.attributes('aria-label')).toContain('待接入');
    expect(wrapper.attributes('data-state')).toBe('MISSING');
    expect(optionOf(wrapper).pointer.show).toBe(false);
    expect(optionOf(wrapper).data).toEqual([]);
  });
});
