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

    expect(wrapper.attributes('data-mode')).toBe('dashboard');
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

  it('compact 模式使用 SVG 圆环，中心保留真实百分比且不渲染仪表盘刻度与指针', () => {
    const wrapper = mount(CompletionRingGauge, {
      props: { compact: true, text: '90.64%', value: 90.64, accent: '#4de8ef' }
    });

    expect(wrapper.attributes('data-mode')).toBe('compact');
    expect(wrapper.find('[data-testid="completion-ring-svg"]').exists()).toBe(true);
    expect(wrapper.findComponent({ name: 'VChart' }).exists()).toBe(false);
    expect(wrapper.find('[data-testid="completion-ring-value"]').text()).toBe('90.64%');
    expect(wrapper.find('[data-testid="completion-ring-track"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="completion-ring-progress"]').attributes('stroke-dasharray')).toBe('100');
    expect(Number(wrapper.find('[data-testid="completion-ring-progress"]').attributes('stroke-dashoffset'))).toBeCloseTo(9.36);
    expect(wrapper.find('[data-testid="completion-ring-pointer"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="completion-ring-scale"]').exists()).toBe(false);
  });

  it.each([
    { value: 125, text: '125.00%', progress: 100 },
    { value: 0, text: '0.00%', progress: 0 },
    { value: -4, text: '-4.00%', progress: 0 }
  ])('compact 模式对 $text 只钳制圆弧，不改中心真实文本', ({ value, text, progress }) => {
    const wrapper = mount(CompletionRingGauge, { props: { compact: true, text, value } });
    const arc = wrapper.find('[data-testid="completion-ring-progress"]');

    expect(wrapper.find('[data-testid="completion-ring-value"]').text()).toBe(text);
    expect(wrapper.attributes('data-progress')).toBe(String(progress));
    expect(Number(arc.attributes('stroke-dashoffset'))).toBe(100 - progress);
  });

  it.each([
    { value: 0, text: '0.00%' },
    { value: null, text: '待接入' }
  ])('compact 模式 $text 不绘制进度弧，只保留空轨', ({ value, text }) => {
    const wrapper = mount(CompletionRingGauge, { props: { compact: true, text, value } });

    expect(wrapper.find('[data-testid="completion-ring-track"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="completion-ring-progress"]').classes()).toContain('completion-ring-gauge__progress--empty');
  });

  it('compact 模式缺数保留空轨与破折号，并明确标识待接入', () => {
    const wrapper = mount(CompletionRingGauge, {
      props: { compact: true, text: '待接入', value: null, label: '对公贷款完成率' }
    });

    expect(wrapper.attributes('data-state')).toBe('MISSING');
    expect(wrapper.attributes('aria-label')).toBe('对公贷款完成率：待接入');
    expect(wrapper.find('[data-testid="completion-ring-track"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="completion-ring-value"]').text()).toBe('—');
    expect(wrapper.find('[data-testid="completion-ring-status"]').text()).toBe('待接入');
    expect(wrapper.find('[data-testid="completion-ring-progress"]').attributes('stroke-dashoffset')).toBe('100');
  });
});
