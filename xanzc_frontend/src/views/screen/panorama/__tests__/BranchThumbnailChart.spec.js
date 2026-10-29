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

  it('用静态无业务数据的地图 option 注册陕西 GeoJSON，并清理图表', async () => {
    const original = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = () => ({});
    const wrapper = mount(BranchThumbnailChart, { props: { kind: 'INSTITUTION_MAP' } });
    await vi.waitFor(() => expect(chart.setOption).toHaveBeenCalled());
    expect(echarts.registerMap).toHaveBeenCalledWith('branch-thumbnail-shaanxi', expect.any(Object));
    expect(chart.setOption.mock.calls[0][0].series[0].type).toBe('map');
    expect(chart.setOption.mock.calls[0][0].series[0].itemStyle.areaColor).toBe('#174987');
    wrapper.unmount();
    expect(chart.dispose).toHaveBeenCalled();
    HTMLCanvasElement.prototype.getContext = original;
  });
});
