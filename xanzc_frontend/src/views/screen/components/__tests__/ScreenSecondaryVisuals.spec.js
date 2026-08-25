// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('vue-echarts', () => ({
  default: {
    props: { option: { type: Object, required: true } },
    template: '<div data-testid="chart-option" :data-option="JSON.stringify(option)"></div>'
  }
}));

import { SCR_CHART_PRESETS } from '@/styles/screenChartTheme';
import GaugeDial from '../GaugeDial.vue';
import KpiRadar from '../KpiRadar.vue';
import LiquidProgress from '../LiquidProgress.vue';
import ProgressList from '../ProgressList.vue';
import KpiDetailTable from '../KpiDetailTable.vue';
import TableList from '../TableList.vue';

const chartStubs = {
  'el-icon': { template: '<span><slot /></span>' }
};

function optionOf(wrapper) {
  return JSON.parse(wrapper.find('[data-testid="chart-option"]').attributes('data-option'));
}

const kpiProps = {
  columns: ['metric_code', '细项名称', '目标值', '实际值', '权重', '得分', '完成率', '缺口'],
  rows: [['m1', '净增客户', 100, 80, 20, 16, 80, 20]],
  bind: {},
  styleCfg: { visualPreset: 'vivid' },
  propValue: {}
};

describe('其余大屏组件视觉预设', () => {
  const mounted = [];
  afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()));

  it('GaugeDial 与 KpiRadar 使用预设的 ECharts token', () => {
    const gauge = mount(GaugeDial, {
      props: {
        columns: ['metric', '完成率'], rows: [['总览', 80]],
        bind: { valueCol: '完成率' }, styleCfg: { visualPreset: 'graphite' }
      },
      global: { stubs: chartStubs }
    });
    mounted.push(gauge);
    const graphite = SCR_CHART_PRESETS.graphite;
    const gaugeOption = optionOf(gauge);
    expect(gaugeOption.series[0].progress.itemStyle.color.colorStops[1].color).toBe(graphite.tokens.accent);
    expect(gaugeOption.series[0].detail.color).toBe(graphite.tokens.number);

    const radar = mount(KpiRadar, { props: kpiProps, global: { stubs: chartStubs } });
    mounted.push(radar);
    const vivid = SCR_CHART_PRESETS.vivid;
    const radarOption = optionOf(radar);
    expect(radarOption.tooltip.backgroundColor).toBe(vivid.tokens.tooltipBg);
    expect(radarOption.radar.axisName.color).toBe(vivid.tokens.textDim);
    expect(radarOption.series[0].data[0].lineStyle.color).toBe(vivid.palette[0]);
  });

  it('LiquidProgress、ProgressList、KpiDetailTable、TableList 暴露主题 CSS variables', () => {
    const liquid = mount(LiquidProgress, {
      props: {
        columns: ['metric', '完成率'], rows: [['总览', 80]],
        bind: { valueCol: '完成率' }, styleCfg: { visualPreset: 'vivid' }
      }, global: { stubs: chartStubs }
    });
    mounted.push(liquid);
    expect(liquid.find('.lp-wrap').attributes('style')).toContain('--lp-accent');
    expect(liquid.find('.lp-wrap').attributes('style')).toContain(SCR_CHART_PRESETS.vivid.tokens.number);

    const progress = mount(ProgressList, { props: kpiProps, global: { stubs: chartStubs } });
    mounted.push(progress);
    expect(progress.find('.pl-wrap').attributes('style')).toContain('--pl-accent');
    expect(progress.find('.pl-wrap').attributes('style')).toContain(SCR_CHART_PRESETS.vivid.tokens.up);

    const detail = mount(KpiDetailTable, { props: kpiProps, global: { stubs: chartStubs } });
    mounted.push(detail);
    expect(detail.find('.kdt-wrap').attributes('style')).toContain('--kdt-border');
    expect(detail.find('.kdt-wrap').attributes('style')).toContain(SCR_CHART_PRESETS.vivid.tokens.down);

    const table = mount(TableList, {
      props: {
        columns: ['name', 'value'], rows: [['A', 1]], bind: {}, styleCfg: { visualPreset: 'vivid' }, propValue: { carousel: false }
      }, global: { stubs: chartStubs }
    });
    mounted.push(table);
    expect(table.find('.tl-wrap').attributes('style')).toContain('--tl-accent');
    expect(table.find('.tl-wrap').attributes('style')).toContain(SCR_CHART_PRESETS.vivid.tokens.text);
  });
});
