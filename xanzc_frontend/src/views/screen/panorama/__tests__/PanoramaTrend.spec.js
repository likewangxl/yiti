// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { mount } from '@vue/test-utils';

vi.mock('vue-echarts', () => ({
  default: {
    props: { option: { type: Object, default: () => ({}) } },
    template: '<div data-testid="chart-option" :data-option="JSON.stringify(option)" />'
  }
}));

import PanoramaTrend from '../PanoramaTrend.vue';

function chartOption(wrapper) {
  return JSON.parse(wrapper.get('[data-testid="chart-option"]').attributes('data-option'));
}

describe('PanoramaTrend', () => {
  it('compact 图表首次挂载保留非零最小高度', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/views/screen/panorama/PanoramaTrend.vue'), 'utf8');
    expect(source).toContain('.panorama-trend.is-compact .panorama-trend-chart { min-height: 170px; }');
  });
  it('省级可切换趋势默认只绘制净增，余额模式再绘制存贷款余额', async () => {
    const wrapper = mount(PanoramaTrend, {
      props: {
        switchable: true,
        trend: [
          { date: '2026-08', deposit: 100, loan: 80, depositIncrease: -2 },
          { date: '2026-09', deposit: 98, loan: 82, depositIncrease: 0 }
        ]
      }
    });
    expect(chartOption(wrapper).series).toHaveLength(1);
    expect(chartOption(wrapper).series[0]).toMatchObject({ name: '存款净增', data: [-2, 0] });
    await wrapper.get('[data-trend-mode="deposit"]').trigger('click');
    const option = chartOption(wrapper);
    expect(option.series.map(item => item.name)).toEqual(['存款余额', '贷款余额']);
    expect(option.legend.left).toBe('center');
  });

  it('City 传入显式 series 时保持存贷款两条原始序列，不显示省级切换控件', () => {
    const wrapper = mount(PanoramaTrend, {
      props: {
        trend: [{ date: '2026-09', deposit: 98, loan: 82, depositIncrease: 0 }],
        series: [
          { key: 'deposit', label: '存款余额', color: '#42e8ef' },
          { key: 'loan', label: '贷款余额', color: '#a77bff' }
        ]
      }
    });
    expect(wrapper.find('[data-trend-mode="deposit"]').exists()).toBe(false);
    expect(chartOption(wrapper).series.map(item => item.name)).toEqual(['存款余额', '贷款余额']);
  });

  it('仅有贷款余额时仍可切换余额模式，且不渲染无数据的存款空序列', () => {
    const wrapper = mount(PanoramaTrend, {
      props: {
        switchable: true,
        trend: [{ date: '2026-09', loan: 82 }]
      }
    });
    const balanceButton = wrapper.get('[data-trend-mode="deposit"]');
    expect(balanceButton.attributes('disabled')).toBeUndefined();
    expect(chartOption(wrapper).series.map(item => item.name)).toEqual(['贷款余额']);
  });
});
