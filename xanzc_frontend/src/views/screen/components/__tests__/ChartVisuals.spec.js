// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('vue-echarts', () => ({
  default: {
    props: { option: { type: Object, required: true } },
    template: '<div data-testid="chart-option" :data-option="JSON.stringify(option)"></div>'
  }
}));

import LineTrend from '../LineTrend.vue';
import PieShare from '../PieShare.vue';
import MetricCard from '../MetricCard.vue';
import RankList from '../RankList.vue';
import FlowStatus from '../FlowStatus.vue';
import BarCompare from '../BarCompare.vue';
import AreaStack from '../AreaStack.vue';

const chartStubs = {
  'el-icon': { template: '<span><slot /></span>' }
};

function optionOf(wrapper) {
  return JSON.parse(wrapper.find('[data-testid="chart-option"]').attributes('data-option'));
}

const lineProps = {
  columns: ['month', 'sales', 'cost'],
  rows: [['Jan', 10, 5], ['Feb', 20, 7], ['Mar', 16, 9]],
  bind: { items: [{ col: 'sales' }, { col: 'cost' }] },
  styleCfg: {}
};

describe('图表视觉预设与 option', () => {
  beforeEach(() => vi.clearAllMocks());

  it('LineTrend 默认有渐变面积、cross axisPointer、末端标签和极值/均值标记，开关可关闭', () => {
    const wrapper = mount(LineTrend, { props: lineProps, global: { stubs: chartStubs } });
    const option = optionOf(wrapper);
    expect(option.tooltip.axisPointer.type).toBe('cross');
    expect(option.series[0].areaStyle.color.colorStops.length).toBeGreaterThanOrEqual(2);
    expect(option.series[0].endLabel).toMatchObject({ show: false });
    expect(option.series[0].markPoint.data).toEqual(expect.arrayContaining([
      expect.objectContaining({ type: 'max' }), expect.objectContaining({ type: 'min' })
    ]));
    expect(option.series[0].markLine.data).toEqual(expect.arrayContaining([
      expect.objectContaining({ type: 'average' })
    ]));

    const compact = mount(LineTrend, {
      props: { ...lineProps, styleCfg: { showLegend: false, showLabels: false, showMarks: false, smooth: false } },
      global: { stubs: chartStubs }
    });
    const compactOption = optionOf(compact);
    expect(compactOption.legend.show).toBe(false);
    expect(compactOption.series[0].endLabel.show).toBe(false);
    expect(compactOption.series[0].markPoint).toBeUndefined();
    expect(compactOption.series[0].markLine).toBeUndefined();
    expect(compactOption.series[0].smooth).toBe(false);
  });

  it('PieShare 支持 donut/rose/solid，提供中心汇总、强调态和标签线', () => {
    const props = {
      columns: ['name', 'value'], rows: [['A', 4], ['B', 6]],
      bind: { nameCol: 'name', valueCol: 'value' }, styleCfg: { visualPreset: 'graphite' }
    };
    const donut = mount(PieShare, { props, global: { stubs: chartStubs } });
    const donutOption = optionOf(donut);
    expect(donutOption.graphic).toEqual(expect.arrayContaining([expect.objectContaining({ type: 'text' })]));
    expect(donutOption.series[0].radius).toEqual(['40%', '68%']);
    expect(donutOption.series[0].labelLine.show).toBe(true);
    expect(donutOption.series[0].emphasis).toBeTruthy();

    const rose = mount(PieShare, {
      props: { ...props, styleCfg: { visualPreset: 'vivid', pieShape: 'rose' } },
      global: { stubs: chartStubs }
    });
    const roseOption = optionOf(rose);
    expect(roseOption.series[0].roseType).toBe('radius');
    expect(roseOption.series[0].radius).toEqual(['18%', '68%']);
    expect(roseOption.graphic).toHaveLength(0);

    const solid = mount(PieShare, {
      props: { ...props, styleCfg: { pieShape: 'solid', showLegend: false, showLabels: false } },
      global: { stubs: chartStubs }
    });
    const solidOption = optionOf(solid);
    expect(solidOption.series[0].radius).toEqual(['0%', '68%']);
    expect(solidOption.legend.show).toBe(false);
    expect(solidOption.series[0].label.show).toBe(false);
    expect(solidOption.graphic).toHaveLength(0);
  });

  it('MetricCard 只在可安全计算时显示最近两行环比，并保持 item-click payload', async () => {
    const wrapper = mount(MetricCard, {
      props: {
        columns: ['date', 'sales'], rows: [['Jan', 100], ['Feb', 120]],
        bind: { items: [{ col: 'sales', label: '销售额' }] }, styleCfg: { cardVariant: 'glass', showTrend: true }
      },
      global: { stubs: chartStubs }
    });
    expect(wrapper.find('.mc-item').classes()).toContain('variant-glass');
    expect(wrapper.find('[data-testid="metric-trend"]').text()).toContain('20.0%');
    expect(wrapper.find('[data-testid="metric-trend"]').attributes('aria-label')).toContain('较上期上升');
    await wrapper.find('.mc-item').trigger('click');
    expect(wrapper.emitted('item-click')[0][0]).toEqual({
      col: 'sales', label: '销售额', row: { date: 'Feb', sales: 120 }
    });

    await wrapper.setProps({ rows: [['Jan', 100], ['Feb', 80]] });
    expect(wrapper.find('[data-testid="metric-trend"]').attributes('aria-label')).toContain('较上期下降');
    await wrapper.setProps({ rows: [['Jan', 0], ['Feb', 80]] });
    expect(wrapper.find('[data-testid="metric-trend"]').exists()).toBe(false);
  });

  it('MetricCard 默认不把最近两行解释为趋势', () => {
    const wrapper = mount(MetricCard, {
      props: {
        columns: ['date', 'sales'], rows: [['Jan', 100], ['Feb', 120]],
        bind: { items: [{ col: 'sales', label: '销售额' }] }, styleCfg: {}
      },
      global: { stubs: chartStubs }
    });
    expect(wrapper.find('[data-testid="metric-trend"]').exists()).toBe(false);
  });

  it('RankList 增加前三名奖牌、占比文字和主题化条形，保持排序与点击事件', async () => {
    const wrapper = mount(RankList, {
      props: {
        columns: ['name', 'value'], rows: [['A', 10], ['B', 5], ['C', 3], ['D', 1]],
        bind: { nameCol: 'name', valueCol: 'value' }, styleCfg: { visualPreset: 'graphite' }
      }, global: { stubs: chartStubs }
    });
    expect(wrapper.findAll('.rl-rank-medal')).toHaveLength(3);
    expect(wrapper.findAll('.rl-share')).toHaveLength(4);
    expect(wrapper.find('.rl-share').text()).toContain('52.6%');
    expect(wrapper.find('.rl-fill').attributes('style')).toContain('width: 100%');
    const first = wrapper.findAll('.rl-row')[0];
    expect(first.attributes('role')).toBe('button');
    expect(first.attributes('tabindex')).toBe('0');
    await first.trigger('keydown.enter');
    await first.trigger('keydown.space');
    expect(wrapper.emitted('item-click')[0][0]).toMatchObject({ col: 'value', label: 'A' });
    expect(wrapper.emitted('item-click')).toHaveLength(2);
  });

  it('FlowStatus 增加状态视觉与占总量比例，同时保留点击事件', async () => {
    const wrapper = mount(FlowStatus, {
      props: {
        columns: ['status', 'value'], rows: [['处理中', 2], ['已完成', 8]],
        bind: { nameCol: 'status', valueCol: 'value' }, styleCfg: { visualPreset: 'aurora' }
      }, global: { stubs: chartStubs }
    });
    expect(wrapper.findAll('.fs-ratio')).toHaveLength(2);
    expect(wrapper.find('.fs-ratio').text()).toContain('20.0%');
    expect(wrapper.findAll('.fs-status-dot')).toHaveLength(2);
    await wrapper.findAll('.fs-tile')[1].trigger('click');
    expect(wrapper.emitted('item-click')[0][0]).toMatchObject({ col: 'value', label: '已完成' });
  });

  it('BarCompare/AreaStack 使用 preset、图例标签开关、axisPointer 和 emphasis', () => {
    const common = {
      columns: ['month', 'sales'], rows: [['Jan', 10], ['Feb', 20]],
      bind: { items: [{ col: 'sales' }] }, styleCfg: { visualPreset: 'graphite', showLegend: false, showLabels: true, showMarks: true }
    };
    const bar = mount(BarCompare, { props: common, global: { stubs: chartStubs } });
    const barOption = optionOf(bar);
    expect(barOption.legend.show).toBe(false);
    expect(barOption.tooltip.axisPointer).toMatchObject({ type: 'shadow' });
    expect(barOption.series[0].label.show).toBe(true);
    expect(barOption.series[0].emphasis).toBeTruthy();
    expect(barOption.series[0].markPoint).toBeTruthy();

    const area = mount(AreaStack, { props: common, global: { stubs: chartStubs } });
    const areaOption = optionOf(area);
    expect(areaOption.legend.show).toBe(false);
    expect(areaOption.tooltip.axisPointer.type).toBe('cross');
    expect(areaOption.series[0].label.show).toBe(true);
    expect(areaOption.series[0].emphasis).toBeTruthy();
    expect(areaOption.series[0].markLine).toBeTruthy();
  });

  it('BarCompare 单行多指标时使用 items label 作为类目，并保留空值柱', () => {
    const wrapper = mount(BarCompare, {
      props: {
        columns: ['annual_avg_deposit', 'annual_avg_growth', 'annual_avg_no_alias'],
        rows: [[null, 42, 7]],
        bind: {
          items: [
            { col: 'annual_avg_deposit', label: '年日均存款' },
            { col: 'annual_avg_growth', label: '' },
            { col: 'annual_avg_no_alias', label: '  ' }
          ]
        },
        columnsMeta: [
          { col: 'annual_avg_deposit', alias: '年日均存款别名' },
          { col: 'annual_avg_growth', alias: '年日均增长' }
        ],
        styleCfg: {}
      },
      global: { stubs: chartStubs }
    });
    const option = optionOf(wrapper);
    expect(option.xAxis.data).toEqual(['年日均存款', '年日均增长', 'annual_avg_no_alias']);
    expect(option.series).toHaveLength(1);
    expect(option.series[0].data).toEqual([null, 42, 7]);
  });

  it('BarCompare 带独立维度列的多行数据仍按首列作为类目', () => {
    const wrapper = mount(BarCompare, {
      props: {
        columns: ['org_code', 'annual_avg_deposit', 'annual_avg_growth'],
        rows: [['A', 10, 2], ['B', 20, 4]],
        bind: {
          items: [
            { col: 'annual_avg_deposit', label: '年日均存款' },
            { col: 'annual_avg_growth', label: '年日均增长' }
          ]
        },
        styleCfg: {}
      },
      global: { stubs: chartStubs }
    });
    const option = optionOf(wrapper);
    expect(option.xAxis.data).toEqual(['A', 'B']);
    expect(option.series.map(s => s.data)).toEqual([[10, 20], [2, 4]]);
  });
});
