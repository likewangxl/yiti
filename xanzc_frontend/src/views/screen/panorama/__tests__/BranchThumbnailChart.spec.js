// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const { chart, echarts } = vi.hoisted(() => {
  const chart = { setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() };
  return { chart, echarts: { use: vi.fn(), registerMap: vi.fn(), init: vi.fn(() => chart) } };
});
vi.mock('echarts/core', () => echarts);
vi.mock('echarts/charts', () => ({ BarChart: {}, LineChart: {}, MapChart: {}, PieChart: {} }));
vi.mock('echarts/components', () => ({ GeoComponent: {}, GridComponent: {}, TooltipComponent: {}, VisualMapComponent: {} }));
vi.mock('echarts/renderers', () => ({ CanvasRenderer: {} }));
import BranchThumbnailChart from '../BranchThumbnailChart.vue';

describe('BranchThumbnailChart', () => {
  afterEach(() => { chart.setOption.mockClear(); chart.resize.mockClear(); chart.dispose.mockClear(); });

  it('地图使用独立的透明示意资产，隐藏于辅助技术且不渲染经营数据', () => {
    const wrapper = mount(BranchThumbnailChart, { props: { kind: 'INSTITUTION_MAP' } });
    const map = wrapper.get('[data-testid="branch-thumbnail-map-art"]');
    expect(map.attributes('src')).toContain('branch-thumbnail-map.png');
    expect(map.attributes('alt')).toBe('');
    expect(wrapper.attributes('aria-hidden')).toBe('true');
    expect(wrapper.text()).toBe('');
    wrapper.unmount();
  });

  it('占位趋势包含双曲线和柱图且不显示数值，卸载后释放实例', async () => {
    const original = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = () => ({});
    const wrapper = mount(BranchThumbnailChart, { props: { kind: 'BUSINESS_GROWTH' } });
    await vi.waitFor(() => expect(chart.setOption).toHaveBeenCalled());
    const options = chart.setOption.mock.calls[0][0];
    expect(options.series.map(series => series.type)).toEqual(['bar', 'line', 'line']);
    expect(options.yAxis.axisLabel.show).toBe(false);
    expect(options.tooltip.show).toBe(false);
    wrapper.unmount();
    expect(chart.dispose).toHaveBeenCalled();
    HTMLCanvasElement.prototype.getContext = original;
  });
});
