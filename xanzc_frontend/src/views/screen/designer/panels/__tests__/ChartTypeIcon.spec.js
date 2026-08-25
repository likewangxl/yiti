// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import ChartTypeIcon from '../ChartTypeIcon.vue';

const CHART_TYPES = [
  'AREA_STACK', 'BAR_COMPARE', 'COMBO_CHART', 'FLOW_STATUS', 'FUNNEL_CHART', 'GAUGE',
  'HEATMAP_MATRIX', 'KPI_DETAIL_TABLE', 'KPI_RADAR', 'LINE_TREND', 'LIQUID_PROGRESS',
  'METRIC_CARD', 'PIE_SHARE', 'PROGRESS_LIST', 'RANK_LIST', 'SCATTER_BUBBLE',
  'SPARKLINE_CARD', 'SUNBURST_CHART', 'TABLE_LIST'
];

describe('ChartTypeIcon', () => {
  it('为 19 种图表输出 32px viewBox、无障碍隐藏标记和唯一签名', () => {
    const wrappers = CHART_TYPES.map(innerType => mount(ChartTypeIcon, { props: { innerType } }));
    expect(wrappers.every(wrapper => wrapper.find('svg').attributes('viewBox') === '0 0 32 32')).toBe(true);
    expect(wrappers.every(wrapper => wrapper.find('svg').attributes('aria-hidden') === 'true')).toBe(true);
    const signatures = wrappers.map(wrapper => wrapper.find('svg').attributes('data-icon-signature'));
    expect(new Set(signatures).size).toBe(CHART_TYPES.length);
  });

  it('关键类型使用不同且稳定的 SVG 结构语义', () => {
    const line = mount(ChartTypeIcon, { props: { innerType: 'LINE_TREND' } });
    const bar = mount(ChartTypeIcon, { props: { innerType: 'BAR_COMPARE' } });
    const pie = mount(ChartTypeIcon, { props: { innerType: 'PIE_SHARE' } });
    const gauge = mount(ChartTypeIcon, { props: { innerType: 'GAUGE' } });
    const radar = mount(ChartTypeIcon, { props: { innerType: 'KPI_RADAR' } });

    expect(line.find('polyline').exists()).toBe(true);
    expect(bar.findAll('rect').length).toBeGreaterThanOrEqual(3);
    expect(pie.find('path').exists()).toBe(true);
    expect(gauge.find('path').exists()).toBe(true);
    expect(gauge.find('line').exists()).toBe(true);
    expect(radar.find('polygon').exists()).toBe(true);
    expect(line.find('svg').attributes('data-icon-signature')).not.toBe(bar.find('svg').attributes('data-icon-signature'));
  });
});
