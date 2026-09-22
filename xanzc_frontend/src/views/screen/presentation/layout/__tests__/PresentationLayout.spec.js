// @vitest-environment happy-dom
import { afterEach, describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';

import PresentationLayout from '../PresentationLayout.vue';

const presentation = {
  displaySchemaVersion: 1,
  display: {
    components: [
      {
        componentId: 'detail', componentType: 'DETAIL_TABLE', layoutRegion: 'BOTTOM', order: 0, visible: true,
        text: { titleMode: 'CUSTOM', title: '明细' },
        format: { displayUnit: 'AUTO' },
        content: { columns: [{ columnKey: 'name', field: 'name', label: '机构', unit: 'AUTO', visible: true }] },
        dataRefs: [{ blockId: 7, role: 'PRIMARY', unit: 'COUNT' }]
      },
      {
        componentId: 'ranking', componentType: 'RANKING', layoutRegion: 'RIGHT', order: 0, visible: true,
        text: { titleMode: 'CUSTOM', title: '机构排名' },
        content: { rankingMetrics: [{ metricKey: 'deposit', field: 'deposit', label: '存款', unit: 'HUNDRED_MILLION', direction: 'DESC' }] },
        dataRefs: [{ blockId: 6, role: 'PRIMARY', unit: 'HUNDRED_MILLION' }]
      },
      {
        componentId: 'map', componentType: 'MAP', layoutRegion: 'CENTER', order: 0, visible: true,
        text: { titleMode: 'CUSTOM', title: '机构地图' },
        format: { displayUnit: 'HUNDRED_MILLION' },
        content: { mainField: 'deposit' },
        dataRefs: [{ blockId: 5, role: 'PRIMARY', unit: 'HUNDRED_MILLION' }]
      },
      {
        componentId: 'trend', componentType: 'TREND', layoutRegion: 'LEFT', order: 1, visible: true,
        text: { titleMode: 'CUSTOM', title: '趋势' },
        content: { series: [{ seriesKey: 'deposit', field: 'deposit', label: '存款', unit: 'HUNDRED_MILLION' }] },
        dataRefs: [{ blockId: 4, role: 'PRIMARY', unit: 'HUNDRED_MILLION' }]
      },
      {
        componentId: 'metric', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 0, visible: true,
        text: { titleMode: 'CUSTOM', title: '存款余额' },
        format: { displayUnit: 'HUNDRED_MILLION', decimals: 2 },
        content: { mainField: 'value' },
        dataRefs: [{ blockId: 1, role: 'PRIMARY', metricCode: 'deposit', metricName: '存款余额', unit: 'HUNDRED_MILLION' }]
      },
      {
        componentId: 'completion', componentType: 'COMPLETION', layoutRegion: 'HEADER', order: 1, visible: true,
        text: { titleMode: 'CUSTOM', title: '对公完成率' },
        format: { displayUnit: 'PERCENT', decimals: 1 },
        content: { mainField: 'value' },
        dataRefs: [{ blockId: 2, role: 'PRIMARY', metricCode: 'corpRate', metricName: '对公完成率', unit: 'PERCENT' }]
      },
      {
        componentId: 'structure', componentType: 'COMPOSITION_TABS', layoutRegion: 'CENTER', order: 1, visible: true,
        text: { titleMode: 'CUSTOM', title: '业务结构' },
        content: { tabs: [{ tabKey: 'deposit', label: '存款', corporateField: 'corporate', retailField: 'retail', totalField: 'total', unit: 'HUNDRED_MILLION' }] },
        dataRefs: [{ blockId: 3, role: 'PRIMARY', unit: 'HUNDRED_MILLION' }]
      }
    ]
  }
};

const stubs = {
  MetricDisplayWidgets: { props: ['components'], template: '<div data-testid="metric-widget"><span v-for="item in components" :key="item.componentId">{{ item.componentId }}</span></div>' },
  SeriesTableWidgets: { props: ['components'], template: '<div data-testid="series-widget"><span v-for="item in components" :key="item.componentId">{{ item.componentId }}</span></div>' },
  CompositionTabsWidget: { props: ['model'], template: '<div data-testid="structure-widget">{{ model.components?.[0]?.componentId }}</div>' },
  InstitutionRankingWidget: { props: ['model', 'title'], template: '<div data-testid="ranking-widget">{{ title }}</div>' },
  PresentationMapWidget: { props: ['presentation'], template: '<div data-testid="map-widget">地图</div>' }
};

const mounted = [];
afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()));

describe('PresentationLayout', () => {
  it('只按协议组件的 region/order/visible 渲染七类组件，且不带旧固定模块', () => {
    const wrapper = mount(PresentationLayout, {
      props: {
        presentation,
        model: {
          kpis: [{ key: 'deposit', value: 1286.42, unit: '亿元' }],
          trend: [{ date: '2026-09', deposit: 1286.42 }],
          items: [{ name: '机构一' }],
          blockResults: {
            2: { value: null, unit: '%' },
            3: { total: 100, corporate: 60, retail: 40, unit: '亿元' },
            4: [{ date: '2026-09', deposit: 1286.42 }],
            7: [{ name: '机构一' }]
          },
          institutions: [{ orgCode: 'A', orgName: '机构一' }],
          rankings: [{ orgCode: 'A', name: '机构一', deposit: 100 }]
        }
      },
      global: { stubs }
    });
    mounted.push(wrapper);
    expect(wrapper.findAll('[data-testid="presentation-layout-component"]').map(item => item.attributes('data-component-id')))
      .toEqual(['metric', 'completion', 'trend', 'map', 'structure', 'ranking', 'detail']);
    expect(wrapper.find('[data-component-type="COMPLETION"]').text()).not.toContain('93.6');
    expect(wrapper.find('[data-testid="metric-widget"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="series-widget"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="structure-widget"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="ranking-widget"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="map-widget"]').exists()).toBe(true);
    expect(wrapper.text()).not.toContain('目标攻坚');
    expect(wrapper.text()).not.toContain('客户营销');
    expect(wrapper.text()).not.toContain('重点项目');
    expect(wrapper.text()).not.toContain('团队贡献');
  });
});
