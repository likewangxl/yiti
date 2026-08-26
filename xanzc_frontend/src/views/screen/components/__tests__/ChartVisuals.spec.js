// @vitest-environment happy-dom
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('vue-echarts', () => ({
  default: {
    name: 'VChart',
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
import { SCR_MORANDI_PALETTE, scrWithAlpha } from '@/styles/screenChartTheme';

const chartStubs = {
  'el-icon': { template: '<span><slot /></span>' }
};

function optionOf(wrapper) {
  return JSON.parse(wrapper.find('[data-testid="chart-option"]').attributes('data-option'));
}

function triggerChartClick(wrapper, payload) {
  const chart = wrapper.findComponent({ name: 'VChart' });
  const listener = chart.vm.$attrs.onClick;
  if (Array.isArray(listener)) listener[0](payload);
  else listener(payload);
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

  it('LineTrend 多指标显式使用莫兰迪色，线/点/面积同色并按十色循环', () => {
    const columns = ['month', ...Array.from({ length: 11 }, (_, i) => `metric_${i + 1}`)];
    const wrapper = mount(LineTrend, {
      props: {
        columns,
        rows: [['Jan', ...Array.from({ length: 11 }, (_, i) => i + 1)]],
        bind: { items: columns.slice(1).map(col => ({ col })) },
        styleCfg: {}
      },
      global: { stubs: chartStubs }
    });
    const option = optionOf(wrapper);
    expect(option.series).toHaveLength(11);
    expect(option.series.map(series => series.lineStyle.color)).toEqual([
      ...SCR_MORANDI_PALETTE, SCR_MORANDI_PALETTE[0]
    ]);
    expect(option.series.map(series => series.itemStyle.color)).toEqual([
      ...SCR_MORANDI_PALETTE, SCR_MORANDI_PALETTE[0]
    ]);
    expect(option.series[0].areaStyle.color.colorStops[0].color)
      .toBe(scrWithAlpha(SCR_MORANDI_PALETTE[0], .28));
    expect(option.series[10].areaStyle.color.colorStops[0].color)
      .toBe(scrWithAlpha(SCR_MORANDI_PALETTE[0], .28));

    const custom = mount(LineTrend, {
      props: {
        columns: ['month', 'a', 'b', 'c'], rows: [['Jan', 1, 2, 3]],
        bind: { items: [{ col: 'a' }, { col: 'b' }, { col: 'c' }] },
        styleCfg: { colors: ['#112233', '#445566'] }
      },
      global: { stubs: chartStubs }
    });
    expect(optionOf(custom).series.map(series => series.lineStyle.color))
      .toEqual(['#112233', '#445566', '#112233']);
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

  it('PieShare 指标列绑定按 items 顺序取最新行，过滤未选列并使用 label', () => {
    const wrapper = mount(PieShare, {
      props: {
        columns: ['month', 'sales', 'cost', 'other'],
        rows: [['Jan', 10, 2, 99], ['Feb', 20, 4, 88]],
        bind: {
          items: [
            { col: 'cost', label: '成本' },
            { col: 'missing', label: '不存在' },
            { col: 'sales', label: '' }
          ]
        },
        styleCfg: {}
      },
      global: { stubs: chartStubs }
    });

    expect(optionOf(wrapper).series[0].data).toEqual([
      { name: '成本', value: 4 },
      { name: 'sales', value: 20 }
    ]);
  });

  it('PieShare 指标列点击按 dataIndex 返回真实 col、label 和最新原始行，并最多显示十项', () => {
    const columns = ['month', ...Array.from({ length: 11 }, (_, i) => `metric_${i + 1}`)];
    const latest = ['Feb', ...Array.from({ length: 11 }, (_, i) => i + 10)];
    const wrapper = mount(PieShare, {
      props: {
        columns,
        rows: [['Jan', ...Array.from({ length: 11 }, (_, i) => i + 1)], latest],
        bind: {
          items: columns.slice(1).map((col, index) => ({ col, label: index === 1 ? '第二指标' : '' }))
        },
        styleCfg: {}
      },
      global: { stubs: chartStubs }
    });

    const option = optionOf(wrapper);
    expect(option.series[0].data).toHaveLength(10);
    expect(option.series[0].data[1]).toEqual({ name: '第二指标', value: 11 });
    triggerChartClick(wrapper, { componentType: 'series', seriesIndex: 0, dataIndex: 1 });
    expect(wrapper.emitted('item-click')?.[0]?.[0]).toEqual({
      col: 'metric_2', label: '第二指标',
      row: Object.fromEntries(columns.map((col, index) => [col, latest[index]]))
    });
  });

  it('PieShare 未配置有效指标列时兼容 nameCol/valueCol，并按 dataIndex 找到重复名称的原始行', () => {
    const wrapper = mount(PieShare, {
      props: {
        columns: ['name', 'value', 'memo'],
        rows: [['A', 4, 'first'], ['A', 6, 'second']],
        bind: { nameCol: 'name', valueCol: 'value', items: [{ col: 'missing' }] },
        styleCfg: {}
      },
      global: { stubs: chartStubs }
    });

    expect(optionOf(wrapper).series[0].data).toEqual([
      { name: 'A', value: 4 }, { name: 'A', value: 6 }
    ]);
    triggerChartClick(wrapper, { componentType: 'series', seriesIndex: 0, dataIndex: 1 });
    expect(wrapper.emitted('item-click')?.[0]?.[0]).toEqual({
      col: 'value', label: 'A', row: { name: 'A', value: 6, memo: 'second' }
    });
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

  it('RankList 只显示排名、类目和指标值，不显示色块与占比列', async () => {
    const wrapper = mount(RankList, {
      props: {
        columns: ['name', 'value'], rows: [['A', 10], ['B', 5], ['C', 3], ['D', 1]],
        bind: { nameCol: 'name', valueCol: 'value' }, styleCfg: { visualPreset: 'graphite' },
        propValue: { carousel: false }
      }, global: { stubs: chartStubs }
    });
    expect(wrapper.findAll('.rl-rank-medal')).toHaveLength(3);
    expect(wrapper.find('.rl-bar').exists()).toBe(false);
    expect(wrapper.find('.rl-fill').exists()).toBe(false);
    expect(wrapper.find('.rl-share').exists()).toBe(false);
    expect(wrapper.findAll('[data-testid="rank-metric-value"]')).toHaveLength(4);
    const source = readFileSync(resolve(process.cwd(), 'src/views/screen/components/RankList.vue'), 'utf8');
    expect(source).toMatch(/\.rl-name\s*\{[^}]*width:\s*140px/);
    const first = wrapper.findAll('.rl-row')[0];
    expect(first.attributes('role')).toBe('button');
    expect(first.attributes('tabindex')).toBe('0');
    await first.trigger('keydown.enter');
    await first.trigger('keydown.space');
    expect(wrapper.emitted('item-click')[0][0]).toMatchObject({ col: 'value', label: 'A' });
    expect(wrapper.emitted('item-click')).toHaveLength(2);
  });

  it('RankList 未配置 nameCol 时回退首个非指标列，且保留全量排序后的排名', () => {
    const wrapper = mount(RankList, {
      props: {
        columns: ['org_name', 'org_code', 'balance'],
        rows: [['机构甲', '001', 10], ['机构乙', '002', 30], ['机构丙', '003', 20]],
        bind: { items: [{ col: 'balance', label: '余额' }] },
        columnsMeta: [
          { col: 'org_name', role: 'DIM' },
          { col: 'org_code', role: 'DIM' },
          { col: 'balance', role: 'METRIC' }
        ],
        styleCfg: {},
        propValue: { carousel: false }
      }, global: { stubs: chartStubs }
    });

    expect(wrapper.findAll('.rl-name').map(node => node.text())).toEqual(['机构乙', '机构丙', '机构甲']);
    expect(wrapper.findAll('.rl-no').map(node => node.attributes('aria-label'))).toEqual([
      '第1名', '第2名', '第3名'
    ]);
  });

  it('RankList 多指标展示顶部按钮与当前排序注释，切换指标/方向后重排并点击回传当前指标', async () => {
    const wrapper = mount(RankList, {
      props: {
        columns: ['org_name', 'balance', 'growth'],
        rows: [
          ['甲', 100, 2], ['乙', 80, 8], ['丙', 'invalid', 5], ['丁', 120, 1]
        ],
        bind: {
          items: [{ col: 'balance', label: '存款余额' }, { col: 'growth', label: '增幅' }]
        },
        columnsMeta: [
          { col: 'org_name', role: 'DIM', alias: '机构' },
          { col: 'balance', role: 'METRIC', alias: '存款余额', decimals: 0 },
          { col: 'growth', role: 'METRIC', alias: '增幅', decimals: 1 }
        ],
        styleCfg: {}, propValue: { carousel: false }
      }, global: { stubs: chartStubs }
    });

    expect(wrapper.find('[data-testid="rank-sort-status"]').text()).toBe('当前排序：存款余额（倒序）');
    expect(wrapper.findAll('[data-testid="rank-metric-button"]').map(node => node.text())).toEqual([
      '存款余额', '增幅'
    ]);
    expect(wrapper.findAll('.rl-name').map(node => node.text())).toEqual(['丁', '甲', '乙', '丙']);
    expect(wrapper.findAll('.rl-val').map(node => node.text())).toEqual(['120', '100', '80', '—']);
    expect(wrapper.findAll('.rl-row')[0].findAll('[data-testid="rank-metric-value"]')
      .map(node => node.text())).toEqual(['120', '1.0']);

    const growthButton = wrapper.findAll('[data-testid="rank-metric-button"]')[1];
    expect(growthButton.attributes('aria-label')).toContain('增幅');
    await growthButton.trigger('click');
    expect(wrapper.find('[data-testid="rank-sort-status"]').text()).toBe('当前排序：增幅（倒序）');
    expect(wrapper.findAll('.rl-name').map(node => node.text())).toEqual(['乙', '丙', '甲', '丁']);

    await growthButton.trigger('click');
    expect(wrapper.find('[data-testid="rank-sort-status"]').text()).toBe('当前排序：增幅（正序）');
    expect(wrapper.findAll('.rl-name').map(node => node.text())).toEqual(['丁', '甲', '丙', '乙']);

    await wrapper.findAll('.rl-row')[0].trigger('click');
    expect(wrapper.emitted('item-click')?.at(-1)?.[0]).toMatchObject({ col: 'growth', label: '丁' });
  });

  it('RankList 默认开启自动滚动，超过可视行数时每 2 秒逐行循环且名次按全量排序', async () => {
    vi.useFakeTimers();
    try {
      const wrapper = mount(RankList, {
        props: {
          columns: ['name', 'value'],
          rows: [['A', 10], ['B', 40], ['C', 30], ['D', 20]],
          bind: { valueCol: 'value' }, styleCfg: {}, propValue: {}
        }, global: { stubs: chartStubs }
      });
      Object.defineProperty(wrapper.find('.rl-wrap').element, 'clientHeight', { configurable: true, value: 154 });
      wrapper.vm.containerH = 154;
      await wrapper.vm.$nextTick();

      expect(wrapper.findAll('.rl-row')).toHaveLength(3);
      expect(wrapper.findAll('.rl-name').map(node => node.text())).toEqual(['B', 'C', 'D']);
      expect(wrapper.findAll('.rl-no').map(node => node.attributes('aria-label'))).toEqual([
        '第1名', '第2名', '第3名'
      ]);

      await vi.advanceTimersByTimeAsync(2000);
      expect(wrapper.findAll('.rl-name').map(node => node.text())).toEqual(['C', 'D', 'A']);
      expect(wrapper.findAll('.rl-no').map(node => node.attributes('aria-label'))).toEqual([
        '第2名', '第3名', '第4名'
      ]);

      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
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
    expect(option.series[0].data.map(item => item.value)).toEqual([null, 42, 7]);
    expect(option.series[0].data.map(item => item.itemStyle.color.colorStops[1].color)).toEqual([
      SCR_MORANDI_PALETTE[0], SCR_MORANDI_PALETTE[1], SCR_MORANDI_PALETTE[2]
    ]);
  });

  it('BarCompare 单行多指标第十一柱循环色板且点击仍按 dataIndex 回传原始指标', () => {
    const columns = Array.from({ length: 11 }, (_, i) => `metric_${i + 1}`);
    const wrapper = mount(BarCompare, {
      props: {
        columns,
        rows: [[null, ...Array.from({ length: 10 }, (_, i) => i + 1)]],
        bind: { items: columns.map((col, i) => ({ col, label: `指标${i + 1}` })) },
        styleCfg: {}
      },
      global: { stubs: chartStubs }
    });
    const option = optionOf(wrapper);
    expect(option.series[0].data).toHaveLength(11);
    expect(option.series[0].data[10].itemStyle.color.colorStops[1].color).toBe(SCR_MORANDI_PALETTE[0]);
    triggerChartClick(wrapper, { componentType: 'series', seriesIndex: 0, dataIndex: 10 });
    expect(wrapper.emitted('item-click')?.[0]?.[0]).toMatchObject({
      col: 'metric_11', label: '指标11', row: { metric_11: 10 }
    });
  });

  it('BarCompare 常规多系列按指标着色并循环使用自定义色板', () => {
    const wrapper = mount(BarCompare, {
      props: {
        columns: ['month', 'a', 'b', 'c'], rows: [['Jan', 10, 20, 30]],
        bind: { items: [{ col: 'a' }, { col: 'b' }, { col: 'c' }] },
        styleCfg: { colors: ['#112233', '#445566'] }
      },
      global: { stubs: chartStubs }
    });
    const option = optionOf(wrapper);
    expect(option.series.map(series => series.itemStyle.color.colorStops[1].color))
      .toEqual(['#112233', '#445566', '#112233']);
    expect(option.series.map(series => series.data)).toEqual([[10], [20], [30]]);
  });

  it('BarCompare 横向单系列逐柱使用十色莫兰迪色并循环第十一柱', () => {
    const columns = ['name', 'value'];
    const rows = Array.from({ length: 11 }, (_, i) => [`类别${i + 1}`, i + 1]);
    const wrapper = mount(BarCompare, {
      props: {
        columns, rows,
        bind: { items: [{ col: 'value' }] },
        propValue: { barMode: 'horizontal' },
        styleCfg: {}
      },
      global: { stubs: chartStubs }
    });
    const option = optionOf(wrapper);
    expect(option.series[0].data.map(item => item.value)).toEqual(rows.map(row => row[1]));
    expect(option.series[0].data.map(item => item.itemStyle.color.colorStops[1].color))
      .toEqual([...SCR_MORANDI_PALETTE, SCR_MORANDI_PALETTE[0]]);
    triggerChartClick(wrapper, { componentType: 'series', seriesIndex: 0, dataIndex: 10 });
    expect(wrapper.emitted('item-click')?.[0]?.[0]).toEqual({
      col: 'value', label: '类别11', row: { name: '类别11', value: 11 }
    });
  });

  it('BarCompare 横向多系列按 seriesIndex 加 dataIndex 错开颜色', () => {
    const wrapper = mount(BarCompare, {
      props: {
        columns: ['name', 'a', 'b'],
        rows: [['A', 10, 1], ['B', 20, 2], ['C', 30, 3]],
        bind: { items: [{ col: 'a' }, { col: 'b' }] },
        propValue: { barMode: 'horizontal' },
        styleCfg: {}
      },
      global: { stubs: chartStubs }
    });
    const option = optionOf(wrapper);
    expect(option.series.map(series => series.data.map(item => item.value)))
      .toEqual([[10, 20, 30], [1, 2, 3]]);
    expect(option.series.map(series => series.data.map(item => item.itemStyle.color.colorStops[1].color)))
      .toEqual([
        [SCR_MORANDI_PALETTE[0], SCR_MORANDI_PALETTE[1], SCR_MORANDI_PALETTE[2]],
        [SCR_MORANDI_PALETTE[1], SCR_MORANDI_PALETTE[2], SCR_MORANDI_PALETTE[3]]
      ]);
  });

  it('BarCompare 横向模式按自定义色板长度逐柱循环', () => {
    const wrapper = mount(BarCompare, {
      props: {
        columns: ['name', 'value'],
        rows: [['A', 10], ['B', 20], ['C', 30]],
        bind: { items: [{ col: 'value' }] },
        propValue: { barMode: 'horizontal' },
        styleCfg: { colors: ['#112233', '#445566'] }
      },
      global: { stubs: chartStubs }
    });
    const option = optionOf(wrapper);
    expect(option.series[0].data.map(item => item.itemStyle.color.colorStops[1].color))
      .toEqual(['#112233', '#445566', '#112233']);
  });

  it('BarCompare 纵向堆叠模式仍按系列着色并保留数值数据', () => {
    const wrapper = mount(BarCompare, {
      props: {
        columns: ['name', 'a', 'b'],
        rows: [['A', 10, 1], ['B', 20, 2]],
        bind: { items: [{ col: 'a' }, { col: 'b' }] },
        propValue: { barMode: 'stack' },
        styleCfg: {}
      },
      global: { stubs: chartStubs }
    });
    const option = optionOf(wrapper);
    expect(option.series.map(series => series.data)).toEqual([[10, 20], [1, 2]]);
    expect(option.series.map(series => series.stack)).toEqual(['total', 'total']);
    expect(option.series.map(series => series.itemStyle.color.colorStops[1].color))
      .toEqual([SCR_MORANDI_PALETTE[0], SCR_MORANDI_PALETTE[1]]);
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

  it('BarCompare 有效 categoryCol 控制类目轴并排除同列数值系列，点击回传原始列', () => {
    const wrapper = mount(BarCompare, {
      props: {
        columns: ['org_code', 'org_name', 'sales', 'cost'],
        rows: [['A', '甲', 10, 2], ['B', '乙', 20, 4]],
        bind: {
          categoryCol: 'org_name',
          items: [
            { col: 'org_name', label: '机构' },
            { col: 'sales', label: '销售额' },
            { col: 'cost', label: '成本' }
          ]
        },
        styleCfg: {}
      },
      global: { stubs: chartStubs }
    });
    const option = optionOf(wrapper);
    expect(option.xAxis.data).toEqual(['甲', '乙']);
    expect(option.series.map(series => series.name)).toEqual(['sales', 'cost']);
    expect(option.series.map(series => series.data)).toEqual([[10, 20], [2, 4]]);

    triggerChartClick(wrapper, { componentType: 'series', seriesIndex: 0, dataIndex: 1 });
    expect(wrapper.emitted('item-click')?.[0]?.[0]).toEqual({
      col: 'sales', label: '乙', row: { org_code: 'B', org_name: '乙', sales: 20, cost: 4 }
    });
  });

  it('BarCompare 横向模式将有效 categoryCol 放到 Y 轴，缺失/无效时回退首列', () => {
    const horizontal = mount(BarCompare, {
      props: {
        columns: ['org_code', 'sales'], rows: [['A', 10], ['B', 20]],
        bind: { categoryCol: 'org_code', items: [{ col: 'org_code' }, { col: 'sales' }] },
        propValue: { barMode: 'horizontal' }, styleCfg: {}
      },
      global: { stubs: chartStubs }
    });
    const horizontalOption = optionOf(horizontal);
    expect(horizontalOption.xAxis.type).toBe('value');
    expect(horizontalOption.yAxis.data).toEqual(['A', 'B']);
    expect(horizontalOption.series.map(series => series.name)).toEqual(['sales']);

    const fallback = mount(BarCompare, {
      props: {
        columns: ['month', 'sales'], rows: [['Jan', 10], ['Feb', 20]],
        bind: { categoryCol: 'missing', items: [{ col: 'month' }, { col: 'sales' }] }, styleCfg: {}
      },
      global: { stubs: chartStubs }
    });
    const fallbackOption = optionOf(fallback);
    expect(fallbackOption.xAxis.data).toEqual(['Jan', 'Feb']);
    expect(fallbackOption.series.map(series => series.name)).toEqual(['sales']);
  });

  it('BarCompare 单行数据显式选择有效 categoryCol 后不再转置指标', () => {
    const wrapper = mount(BarCompare, {
      props: {
        columns: ['metric_a', 'metric_b', 'category'],
        rows: [[10, 20, '总计']],
        bind: {
          categoryCol: 'category',
          items: [{ col: 'metric_a', label: '指标 A' }, { col: 'metric_b', label: '指标 B' }]
        },
        styleCfg: {}
      },
      global: { stubs: chartStubs }
    });
    const option = optionOf(wrapper);
    expect(option.xAxis.data).toEqual(['总计']);
    expect(option.series.map(series => series.name)).toEqual(['metric_a', 'metric_b']);
    expect(option.series.map(series => series.data)).toEqual([[10], [20]]);
  });

  it('BarCompare 显式 items 仅含类目列时不自动绘制其他数值列，空 items 仍自动取数值列', () => {
    const selectedCategory = mount(BarCompare, {
      props: {
        columns: ['name', 'sales', 'cost'],
        rows: [['A', 10, 2], ['B', 20, 4]],
        bind: { categoryCol: 'name', items: [{ col: 'name' }] },
        styleCfg: {}
      },
      global: { stubs: chartStubs }
    });
    expect(selectedCategory.find('.scr-block-empty').exists()).toBe(true);

    const automatic = mount(BarCompare, {
      props: {
        columns: ['name', 'sales', 'cost'],
        rows: [['A', 10, 2], ['B', 20, 4]],
        bind: { categoryCol: 'name', items: [] },
        styleCfg: {}
      },
      global: { stubs: chartStubs }
    });
    expect(optionOf(automatic).series.map(series => series.name)).toEqual(['sales', 'cost']);
  });
});
