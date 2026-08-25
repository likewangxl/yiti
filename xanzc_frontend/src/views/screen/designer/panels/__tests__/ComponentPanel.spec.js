// @vitest-environment happy-dom
import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('@/views/screen/designer/widgets', () => ({
  materialMetas: [
    { component: 'TextLabel', label: '文本', icon: 'T' },
    { component: 'RectShape', label: '矩形', icon: '▭' }
  ],
  chartMetas: [
    'AREA_STACK', 'BAR_COMPARE', 'COMBO_CHART', 'FLOW_STATUS', 'FUNNEL_CHART', 'GAUGE',
    'HEATMAP_MATRIX', 'KPI_DETAIL_TABLE', 'KPI_RADAR', 'LINE_TREND', 'LIQUID_PROGRESS',
    'METRIC_CARD', 'PIE_SHARE', 'PROGRESS_LIST', 'RANK_LIST', 'SCATTER_BUBBLE',
    'SPARKLINE_CARD', 'SUNBURST_CHART', 'TABLE_LIST'
  ].map(innerType => ({ innerType, label: '图表', enabled: true })),
  mapCenterMeta: { component: 'MapCenter', label: '经营地图', icon: '🗺' }
}));

import ComponentPanel from '../ComponentPanel.vue';

function mountPanel() {
  return mount(ComponentPanel);
}

describe('ComponentPanel.vue 现代组件工作区', () => {
  it('提供可访问搜索框并显示分类数量，同时保留独立地图入口', () => {
    const wrapper = mountPanel();
    const search = wrapper.find('input[aria-label="搜索组件"]');

    expect(search.exists()).toBe(true);
    expect(search.attributes('placeholder')).toBe('搜索组件');
    expect(wrapper.find('[data-testid="component-count-material"]').text()).toBe('2');
    expect(wrapper.find('[data-testid="component-count-map"]').text()).toBe('1');
    expect(wrapper.find('[data-testid="component-count-chart"]').text()).toBe('19');
    expect(wrapper.find('[data-component="MapCenter"]').exists()).toBe(true);
  });

  it('按 label、component 和 innerType 不区分大小写过滤', async () => {
    const wrapper = mountPanel();
    const search = wrapper.find('input[aria-label="搜索组件"]');

    await search.setValue('TEXTLABEL');
    expect(wrapper.find('[data-component="TextLabel"]').exists()).toBe(true);
    expect(wrapper.find('[data-component="RectShape"]').exists()).toBe(false);

    await search.setValue('line_trend');
    expect(wrapper.find('[data-inner-type="LINE_TREND"]').exists()).toBe(true);
    expect(wrapper.find('[data-component="TextLabel"]').exists()).toBe(false);

    await search.setValue('地图');
    expect(wrapper.find('[data-component="MapCenter"]').exists()).toBe(true);
  });

  it('无匹配项显示空状态', async () => {
    const wrapper = mountPanel();
    await wrapper.find('input[aria-label="搜索组件"]').setValue('不存在的组件');

    expect(wrapper.find('[data-testid="component-empty"]').text()).toContain('没有找到匹配组件');
    expect(wrapper.find('[data-component="MapCenter"]').exists()).toBe(false);
  });

  it('拖拽继续写入 HTML5 component/innerType，并使用 copy 效果', () => {
    const wrapper = mountPanel();
    const setData = vi.fn();
    const cell = wrapper.find('[data-component="LINE_TREND"]');
    const event = { dataTransfer: { setData, effectAllowed: '' } };

    cell.trigger('dragstart', event);
    expect(setData).toHaveBeenCalledWith('component', 'ChartWidget');
    expect(setData).toHaveBeenCalledWith('innerType', 'LINE_TREND');
    expect(event.dataTransfer.effectAllowed).toBe('copy');
  });

  it('19 个图表均使用语义 SVG 缩略图且签名唯一，不回退为统一 ▤', () => {
    const wrapper = mountPanel();
    const chartCells = wrapper.findAll('[data-inner-type]');
    const icons = chartCells.map(cell => cell.find('svg.chart-type-icon'));
    expect(chartCells).toHaveLength(19);
    expect(icons.every(icon => icon.exists())).toBe(true);
    expect(new Set(icons.map(icon => icon.attributes('data-icon-signature'))).size).toBe(19);
    expect(wrapper.text()).not.toContain('▤');
    expect(wrapper.find('[data-inner-type="LINE_TREND"] svg').attributes('data-icon-signature')).toBe('line-trend');
  });

  it('图表卡片不显示英文 innerType 文本，但保留 data-inner-type 与英文搜索', async () => {
    const wrapper = mountPanel();
    expect(wrapper.text()).not.toContain('LINE_TREND');
    expect(wrapper.find('[data-inner-type="LINE_TREND"]').exists()).toBe(true);

    await wrapper.find('input[aria-label="搜索组件"]').setValue('LINE_TREND');
    expect(wrapper.find('[data-inner-type="LINE_TREND"]').exists()).toBe(true);
    expect(wrapper.find('[data-inner-type="BAR_COMPARE"]').exists()).toBe(false);
  });
});
