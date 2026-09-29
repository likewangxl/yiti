// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { mount } from '@vue/test-utils';

vi.mock('vue-echarts', () => ({
  default: {
    name: 'ChartSurface',
    props: { option: { type: Object, default: () => ({}) } },
    template: '<div data-testid="chart-option" :data-option="JSON.stringify(option)" />'
  }
}));

import PanoramaTrend from '../PanoramaTrend.vue';

function chartOption(wrapper) {
  return JSON.parse(wrapper.get('[data-testid="chart-option"]').attributes('data-option'));
}

function rawChartOption(wrapper) {
  return wrapper.getComponent({ name: 'ChartSurface' }).props('option');
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

  it('分行长金额趋势使用简明轴单位，隐藏重叠点标签且 tooltip 保留原值', () => {
    const wrapper = mount(PanoramaTrend, {
      props: {
        amountFriendly: true,
        trend: [
          { date: '2026-08', deposit: 2221766695.77, loan: 1847721045.09 },
          { date: '2026-09', deposit: 2221766695.77, loan: 1847721045.09 }
        ]
      }
    });
    const option = rawChartOption(wrapper);
    expect(option.yAxis.axisLabel.formatter(2221766695.77)).toBe('22.22亿');
    expect(option.series.every(item => item.label.show === false)).toBe(true);
    const tooltip = option.tooltip.formatter([
      { axisValue: '2026-08', seriesName: '存款余额', value: 2221766695.77, marker: '' },
      { axisValue: '2026-08', seriesName: '贷款余额', value: 1847721045.09, marker: '' }
    ]);
    expect(tooltip).toContain('2,221,766,695.77');
    expect(tooltip).toContain('1,847,721,045.09');
  });

  it('分行 tooltip 对日期和序列名称做 HTML 转义', () => {
    const wrapper = mount(PanoramaTrend, {
      props: {
        amountFriendly: true,
        trend: [{ date: '<2026&09>', deposit: 2221766695.77 }],
        series: [{ key: 'deposit', label: '<存款&余额>', color: '#42e8ef' }]
      }
    });
    const tooltip = rawChartOption(wrapper).tooltip.formatter([
      { axisValue: '<2026&09>', seriesName: '<存款&余额>', value: 2221766695.77, marker: '' }
    ]);
    expect(tooltip).toContain('&lt;2026&amp;09&gt;');
    expect(tooltip).toContain('&lt;存款&amp;余额&gt;');
    expect(tooltip).not.toContain('<2026&09>');
  });

  it('有效金额单位按原始元值格式化 tooltip 和轴，切换单位不改写原始序列', async () => {
    const sourceRows = [{ date: '2026-09-21', deposit: 1068201500, loan: -12500 }];
    const wrapper = mount(PanoramaTrend, {
      props: {
        amountFriendly: true,
        amountUnit: 'TEN_THOUSAND',
        trend: sourceRows
      }
    });

    const tenThousandOption = rawChartOption(wrapper);
    expect(tenThousandOption.series.map(item => item.data)).toEqual([[1068201500], [-12500]]);
    expect(tenThousandOption.yAxis.axisLabel.formatter(1068201500)).toBe('106,820.15万元');
    expect(tenThousandOption.yAxis.axisLabel.formatter(-12500)).toBe('-1.25万元');
    expect(tenThousandOption.yAxis.axisLabel.formatter(0)).toBe('0.00万元');
    const tenThousandTooltip = tenThousandOption.tooltip.formatter([
      { axisValue: '2026-09-21', seriesName: '存款余额', value: 1068201500, marker: '' },
      { axisValue: '2026-09-21', seriesName: '贷款余额', value: -12500, marker: '' },
      { axisValue: '2026-09-21', seriesName: '空值', value: null, marker: '' }
    ]);
    expect(tenThousandTooltip).toContain('存款余额: 106,820.15万元');
    expect(tenThousandTooltip).toContain('贷款余额: -1.25万元');
    expect(tenThousandTooltip).toContain('空值: —');

    await wrapper.setProps({ amountUnit: 'HUNDRED_MILLION' });
    const hundredMillionOption = rawChartOption(wrapper);
    expect(hundredMillionOption.yAxis.axisLabel.formatter(1068201500)).toBe('10.68亿元');
    expect(hundredMillionOption.tooltip.formatter([
      { axisValue: '2026-09-21', seriesName: '存款余额', value: 1068201500, marker: '' }
    ])).toContain('存款余额: 10.68亿元');
    expect(hundredMillionOption.series.map(item => item.data)).toEqual([[1068201500], [-12500]]);

    await wrapper.setProps({ amountUnit: 'YUAN' });
    const yuanOption = rawChartOption(wrapper);
    expect(yuanOption.yAxis.axisLabel.formatter(1068201500)).toBe('1,068,201,500.00元');
    expect(yuanOption.tooltip.formatter([
      { axisValue: '2026-09-21', seriesName: '存款余额', value: 1068201500, marker: '' }
    ])).toContain('存款余额: 1,068,201,500.00元');
    expect(yuanOption.series.map(item => item.data)).toEqual([[1068201500], [-12500]]);
  });

  it('金额友好趋势把完整原值 tooltip 挂到 body 并限制在视口内，普通趋势不启用 body 浮层', () => {
    const amountWrapper = mount(PanoramaTrend, {
      props: {
        amountFriendly: true,
        trend: [{ date: '2026-09-21', deposit: 1068201504.93, loan: 1120758514 }]
      }
    });
    const amountOption = rawChartOption(amountWrapper);
    expect(amountOption.tooltip).toMatchObject({
      renderMode: 'html',
      appendTo: 'body',
      confine: true,
      className: 'panorama-trend-tooltip'
    });
    const lines = amountOption.tooltip.formatter([
      { axisValue: '2026-09-21', seriesName: '存款余额', value: 1068201504.93, marker: '' },
      { axisValue: '2026-09-21', seriesName: '贷款余额', value: 1120758514, marker: '' }
    ]).split('<br/>');
    expect(lines).toHaveLength(3);
    expect(lines[0]).toBe('2026-09-21');
    expect(lines[1]).toContain('存款余额: 1,068,201,504.93');
    expect(lines[2]).toContain('贷款余额: 1,120,758,514');

    const regularWrapper = mount(PanoramaTrend, {
      props: { trend: [{ date: '2026-09-21', deposit: 1, loan: 2 }] }
    });
    const regularTooltip = rawChartOption(regularWrapper).tooltip;
    expect(regularTooltip).not.toHaveProperty('appendTo');
    expect(regularTooltip).not.toHaveProperty('renderMode');
  });

  it('STACKED_GRADIENT 变体沿用官方面积堆叠渐变样式，并使用 cross 指针', () => {
    const wrapper = mount(PanoramaTrend, {
      props: {
        variant: 'STACKED_GRADIENT',
        amountFriendly: true,
        trend: [
          { date: '2026-09-27', deposit: 100, loan: 80 },
          { date: '2026-09-28', deposit: 110, loan: 90 }
        ]
      }
    });
    const option = rawChartOption(wrapper);

    expect(option.tooltip.axisPointer.type).toBe('cross');
    expect(option.series).toHaveLength(2);
    expect(option.series.every(item => item.stack === 'Total')).toBe(true);
    expect(option.series.every(item => item.smooth === true)).toBe(true);
    expect(option.series.every(item => item.lineStyle.width === 0)).toBe(true);
    expect(option.series.every(item => item.showSymbol === false)).toBe(true);
    expect(option.series.every(item => item.label.show === false)).toBe(true);
    expect(option.series.map(item => item.areaStyle.opacity)).toEqual([0.8, 0.8]);
    expect(option.series[0].areaStyle.color.colorStops).toEqual([
      { offset: 0, color: 'rgb(128, 255, 165)' },
      { offset: 1, color: 'rgb(1, 191, 236)' }
    ]);
    expect(option.series[1].areaStyle.color.colorStops).toEqual([
      { offset: 0, color: 'rgb(0, 221, 255)' },
      { offset: 1, color: 'rgb(77, 119, 255)' }
    ]);
    expect(option.series.every(item => item.emphasis.focus === 'series')).toBe(true);
  });

  it('暖色堆叠配色保持示例基色，并适配白底示例在深色画布上的视觉亮度', () => {
    const wrapper = mount(PanoramaTrend, {
      props: {
        variant: 'STACKED_GRADIENT', stackedPalette: 'WARM',
        trend: [{ date: '2026-09-29', deposit: 100, loan: 80 }]
      }
    });
    const option = rawChartOption(wrapper);
    expect(option.color).toEqual(['#FF0087', '#FFBF00']);
    expect(option.series.map(item => item.itemStyle.color)).toEqual(option.color);
    expect(option.series.map(item => item.areaStyle.opacity)).toEqual([1, 1]);
    expect(option.series[0].areaStyle.color.colorStops).toEqual([
      { offset: 0, color: 'rgb(255, 51, 159)' },
      { offset: 1, color: 'rgb(159, 51, 177)' }
    ]);
    expect(option.series[1].areaStyle.color.colorStops).toEqual([
      { offset: 0, color: 'rgb(255, 204, 51)' },
      { offset: 1, color: 'rgb(230, 101, 112)' }
    ]);
    expect(option.series.map(item => item.data)).toEqual([[100], [80]]);
  });

  it('未指定变体时保持原有非堆叠趋势选项', () => {
    const wrapper = mount(PanoramaTrend, {
      props: { trend: [{ date: '2026-09-28', deposit: 1, loan: 2 }] }
    });
    const option = rawChartOption(wrapper);

    expect(option.tooltip.axisPointer.type).toBe('line');
    expect(option.series.every(item => item.stack === undefined)).toBe(true);
    expect(option.series.every(item => item.lineStyle.width === 2)).toBe(true);
    expect(option.series.every(item => item.showSymbol === true)).toBe(true);
  });

  it('有效日轴按完整类别对齐稀疏行，缺失点为null且0保持为0', () => {
    const wrapper = mount(PanoramaTrend, {
      props: {
        rows: [
          { date: '2026-09-27', deposit: 0, loan: 2 },
          { date: '2026-09-29', deposit: 9, loan: null }
        ],
        series: [
          { key: 'deposit', label: '存款', color: '#42e8ef' },
          { key: 'loan', label: '贷款', color: '#a77bff' }
        ],
        timeAxis: { granularity: 'DAY', categories: ['2026-09-27', '2026-09-28', '2026-09-29'] }
      }
    });
    const option = rawChartOption(wrapper);
    expect(option.xAxis.data).toEqual(['2026-09-27', '2026-09-28', '2026-09-29']);
    expect(option.series.map(item => item.data)).toEqual([[0, null, 9], [2, null, null]]);
    expect(option.xAxis.axisLabel.formatter('2026-09-27', 0)).toBe('09-27');
  });

  it('月轴直接按YYYY-MM映射已采样月末余额，tooltip保留月份且不聚合', () => {
    const wrapper = mount(PanoramaTrend, {
      props: {
        rows: [
          { date: '2025-12-31', deposit: 10, loan: 20 },
          { date: '2026-01-31', deposit: 0, loan: 22 }
        ],
        series: [{ key: 'deposit', label: '存款', color: '#42e8ef' }],
        timeAxis: { granularity: 'MONTH', categories: ['2025-12', '2026-01'] },
        amountFriendly: true
      }
    });
    const option = rawChartOption(wrapper);
    expect(option.xAxis.data).toEqual(['2025-12', '2026-01']);
    expect(option.series[0].data).toEqual([10, 0]);
    expect(option.xAxis.axisLabel.formatter('2025-12', 0)).toBe('2025-12');
    expect(option.xAxis.axisLabel.formatter('2026-01', 1)).toBe('2026-01');
    const tooltip = option.tooltip.formatter([{ axisValue: '2026-01', axisValueLabel: '2026-01', seriesName: '存款', value: 0, marker: '' }]);
    expect(tooltip.split('<br/>')[0]).toBe('2026-01');
  });

  it('未传时间轴时保持原有源行标签和序列数据行为', () => {
    const wrapper = mount(PanoramaTrend, {
      props: { trend: [{ date: '2026-09', deposit: 1, loan: 2 }] }
    });
    const option = rawChartOption(wrapper);
    expect(option.xAxis.data).toEqual(['2026-09']);
    expect(option.series.map(item => item.data)).toEqual([[1], [2]]);
  });
});
