// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('vue-echarts', () => ({
  default: {
    name: 'VChart',
    props: { option: { type: Object, required: true } },
    template: '<div data-testid="chart-option" :data-option="JSON.stringify(option)"></div>'
  }
}));

import ComboChart from '../ComboChart.vue';
import FunnelChart from '../FunnelChart.vue';
import ScatterBubble from '../ScatterBubble.vue';
import HeatmapMatrix from '../HeatmapMatrix.vue';
import SunburstChart from '../SunburstChart.vue';
import SparklineCard from '../SparklineCard.vue';

const chartStubs = { 'el-icon': { template: '<span><slot /></span>' } };
function optionOf(wrapper) {
  return JSON.parse(wrapper.find('[data-testid="chart-option"]').attributes('data-option'));
}
function triggerChartClick(wrapper, payload) {
  const chart = wrapper.findComponent({ name: 'VChart' });
  const listener = chart.vm.$attrs.onClick;
  if (Array.isArray(listener)) listener[0](payload);
  else listener(payload);
}
function mountChart(component, props) {
  return mount(component, { props, global: { stubs: chartStubs } });
}

describe('新增六种大屏图表组件', () => {
  it('ComboChart 自动识别首列类目和前两个数值列，并生成双轴柱线 option', () => {
    const wrapper = mountChart(ComboChart, {
      columns: ['month', 'sales', 'rate', 'note'],
      rows: [['一月', 10, 0.2, 'ok'], ['二月', 20, 0.4, 'ok']],
      bind: {}, styleCfg: { visualPreset: 'vivid' },
      columnsMeta: [{ col: 'sales', alias: '销售额' }, { col: 'rate', alias: '增长率' }]
    });
    const option = optionOf(wrapper);
    expect(option.yAxis).toHaveLength(2);
    expect(option.series.map(s => s.type)).toEqual(['bar', 'line']);
    expect(option.series[0].name).toBe('销售额');
    expect(option.series[1].yAxisIndex).toBe(1);
    expect(option.tooltip.trigger).toBe('axis');
    expect(option.series[0].itemStyle.color.colorStops.length).toBeGreaterThanOrEqual(2);
  });

  it('ComboChart 过滤空类目时同步过滤 series，并按有效行回传原始 row', () => {
    const wrapper = mountChart(ComboChart, {
      columns: ['month', 'sales', 'rate'],
      rows: [['一月', 10, 0.2], ['', 999, 9.9], ['三月', 30, 0.6]],
      bind: {}, styleCfg: {}
    });
    const option = optionOf(wrapper);
    expect(option.xAxis.data).toEqual(['一月', '三月']);
    expect(option.series[0].data).toEqual([10, 30]);
    triggerChartClick(wrapper, { componentType: 'series', seriesIndex: 0, dataIndex: 1 });
    expect(wrapper.emitted('item-click')?.[0]?.[0]).toMatchObject({
      label: '三月', row: { month: '三月', sales: 30, rate: 0.6 }
    });
  });

  it('FunnelChart 按 bind 列排序并输出百分比标签，空数据显示空态', () => {
    const wrapper = mountChart(FunnelChart, {
      columns: ['stage', 'count'], rows: [['浏览', 100], ['提交', 20], ['成交', 5]],
      bind: { nameCol: 'stage', valueCol: 'count' }, styleCfg: { showLabels: true }
    });
    const option = optionOf(wrapper);
    expect(option.series[0].type).toBe('funnel');
    expect(option.series[0].data[0]).toMatchObject({ name: '浏览', value: 100 });
    expect(option.series[0].label.formatter).toBe('{b} {d}%');
    expect(option.series[0].emphasis).toBeTruthy();

    const empty = mountChart(FunnelChart, { columns: [], rows: [], bind: {}, styleCfg: {} });
    expect(empty.find('.scr-block-empty').exists()).toBe(true);
  });

  it('FunnelChart 排序后点击仍回传排序项对应的原始 row', () => {
    const wrapper = mountChart(FunnelChart, {
      columns: ['stage', 'count'], rows: [['浏览', 20], ['成交', 100], ['提交', 50]],
      bind: { nameCol: 'stage', valueCol: 'count' }, styleCfg: {}
    });
    const option = optionOf(wrapper);
    expect(option.series[0].data[0]).toMatchObject({ name: '成交', value: 100, sourceIndex: 1 });
    triggerChartClick(wrapper, { componentType: 'series', dataIndex: 0 });
    expect(wrapper.emitted('item-click')?.[0]?.[0]).toMatchObject({
      label: '成交', row: { stage: '成交', count: 100 }
    });
  });

  it('ScatterBubble 自动识别标签与数值列，第三数值列作为气泡大小且空态安全', () => {
    const wrapper = mountChart(ScatterBubble, {
      columns: ['name', 'x', 'y', 'size'], rows: [['A', 1, 2, 9], ['B', 3, 4, 16]],
      bind: {}, styleCfg: { showLabels: true }
    });
    const option = optionOf(wrapper);
    expect(option.series[0].type).toBe('scatter');
    expect(option.series[0].data[0]).toEqual([1, 2, 9, 'A']);
    expect(option.visualMap).toBeTruthy();
    expect(option.tooltip.trigger).toBe('item');

    const empty = mountChart(ScatterBubble, { columns: ['name', 'x'], rows: [], bind: {}, styleCfg: {} });
    expect(empty.find('.scr-block-empty').exists()).toBe(true);
  });

  it('HeatmapMatrix 使用两个类目轴和数值 visualMap，支持标签开关', () => {
    const wrapper = mountChart(HeatmapMatrix, {
      columns: ['region', 'month', 'value'],
      rows: [['甲', '一月', 10], ['乙', '一月', 20], ['甲', '二月', 30]],
      bind: {}, styleCfg: { showLabels: true }
    });
    const option = optionOf(wrapper);
    expect(option.series[0].type).toBe('heatmap');
    expect(option.xAxis.data).toEqual(['一月', '二月']);
    expect(option.yAxis.data).toEqual(['甲', '乙']);
    expect(option.visualMap).toBeTruthy();
    expect(option.series[0].label.show).toBe(true);
  });

  it('SunburstChart 从一到两层类目生成层级数据并提供强调态', () => {
    const wrapper = mountChart(SunburstChart, {
      columns: ['region', 'branch', 'value'],
      rows: [['甲', 'A', 10], ['甲', 'B', 8], ['乙', 'C', 12]], bind: {}, styleCfg: {}
    });
    const option = optionOf(wrapper);
    expect(option.series[0].type).toBe('sunburst');
    expect(option.series[0].data).toEqual(expect.arrayContaining([
      expect.objectContaining({ name: '甲', children: expect.any(Array) }),
      expect.objectContaining({ name: '乙', children: expect.any(Array) })
    ]));
    expect(option.series[0].emphasis).toBeTruthy();
  });

  it('SunburstChart 点击聚合节点回传当前层级和数值列，不伪造其它列', () => {
    const wrapper = mountChart(SunburstChart, {
      columns: ['region', 'branch', 'value', 'note'],
      rows: [['甲', 'A', 10, 'x'], ['甲', 'B', 8, 'y']], bind: {}, styleCfg: {}
    });
    const option = optionOf(wrapper);
    triggerChartClick(wrapper, { componentType: 'series', name: '甲', data: option.series[0].data[0] });
    expect(wrapper.emitted('item-click')?.[0]?.[0]).toMatchObject({
      col: 'value', label: '甲', row: { region: '甲', value: 18 }
    });
    expect(wrapper.emitted('item-click')?.[0]?.[0]?.row).not.toHaveProperty('note');
  });

  it('SparklineCard 显示最新值、较上期变化和迷你渐变折线，并对零除数/非数值安全', () => {
    const wrapper = mountChart(SparklineCard, {
      columns: ['data_date', 'value'], rows: [['2026-08-01', 0], ['2026-08-02', 12]],
      bind: {}, styleCfg: { showLabels: true }
    });
    expect(wrapper.find('[data-testid="sparkline-value"]').text()).toContain('12');
    expect(wrapper.find('[data-testid="sparkline-change"]').exists()).toBe(false);
    const option = optionOf(wrapper);
    expect(option.series[0].type).toBe('line');
    expect(option.series[0].areaStyle.color.colorStops.length).toBeGreaterThanOrEqual(2);

    const invalid = mountChart(SparklineCard, {
      columns: ['data_date', 'value'], rows: [['2026-08-01', 'n/a'], ['2026-08-02', 'bad']],
      bind: {}, styleCfg: {}
    });
    expect(invalid.find('.scr-block-empty').exists()).toBe(true);
  });

  it('SparklineCard 外壳消费视觉预设，并支持键盘触发且每次只触发一次 item-click', async () => {
    const wrapper = mountChart(SparklineCard, {
      columns: ['data_date', 'value'], rows: [['2026-08-01', 10], ['2026-08-02', 12]],
      bind: {}, styleCfg: { visualPreset: 'graphite' }
    });
    const card = wrapper.find('.sp-card');
    expect(card.attributes('role')).toBe('button');
    expect(card.attributes('tabindex')).toBe('0');
    expect(card.attributes('style')).toContain('--sp-accent: #9bc1bc');
    const vivid = mountChart(SparklineCard, {
      columns: ['data_date', 'value'], rows: [['2026-08-01', 10], ['2026-08-02', 12]],
      bind: {}, styleCfg: { visualPreset: 'vivid' }
    });
    expect(vivid.find('.sp-card').attributes('style')).toContain('--sp-accent: #39c7ee');
    await card.trigger('keydown', { key: 'Enter' });
    await card.trigger('keydown', { key: ' ' });
    expect(wrapper.emitted('item-click')).toHaveLength(2);
  });

  it('ComboChart showMarks 开关控制 max/average 辅助标记', () => {
    const on = mountChart(ComboChart, {
      columns: ['month', 'sales', 'rate'], rows: [['一月', 10, 0.2], ['二月', 20, 0.4]],
      bind: {}, styleCfg: { showMarks: true }
    });
    const onOption = optionOf(on);
    expect(onOption.series[0].markPoint.data[0].type).toBe('max');
    expect(onOption.series[1].markLine.data[0].type).toBe('average');

    const off = mountChart(ComboChart, {
      columns: ['month', 'sales', 'rate'], rows: [['一月', 10, 0.2], ['二月', 20, 0.4]],
      bind: {}, styleCfg: { showMarks: false }
    });
    const offOption = optionOf(off);
    expect(offOption.series[0].markPoint).toBeUndefined();
    expect(offOption.series[1].markLine).toBeUndefined();
  });
});
