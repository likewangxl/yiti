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
  MetricDisplayWidgets: { props: ['components', 'grouped'], template: '<div data-testid="metric-widget" :data-grouped="grouped ? \'true\' : \'false\'"><span v-for="item in components" :key="item.componentId">{{ item.componentId }}</span></div>' },
  SeriesTableWidgets: { props: ['components'], template: '<div data-testid="series-widget"><span v-for="item in components" :key="item.componentId">{{ item.componentId }}</span></div>' },
  CompositionTabsWidget: { props: ['model'], template: '<div data-testid="structure-widget">{{ model.components?.[0]?.componentId }}</div>' },
  InstitutionRankingWidget: { props: ['model', 'title', 'paginate', 'pageSize', 'pageInterval', 'metricCarousel'], template: '<div data-testid="ranking-widget" :data-paginate="paginate ? \'true\' : \'false\'" :data-page-size="pageSize" :data-page-interval="pageInterval" :data-metric-carousel="metricCarousel ? \'true\' : \'false\'">{{ title }}</div>' },
  PresentationMapWidget: { props: ['presentation'], template: '<div data-testid="map-widget">地图</div>' }
};

const mounted = [];
afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()));

describe('PresentationLayout', () => {
  it('成对收入卡合并为包含关系图表并保留两个原值', () => {
    const revenueComponents = [
      ['business-revenue-operating', '营业收入', '营业收入', 'YUAN'],
      ['business-revenue-fee', '中间业务收入', '中间业务收入', 'YUAN']
    ].map(([componentId, title, mainField, unit], order) => ({
      componentId, componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order, visible: true,
      text: { titleMode: 'CUSTOM', title }, format: { displayUnit: 'TEN_THOUSAND', decimals: 2 },
      content: { mainField }, dataRefs: [{ blockId: 31, unit }]
    }));
    const wrapper = mount(PresentationLayout, {
      props: {
        presentation: { displaySchemaVersion: 1, display: { components: revenueComponents } },
        model: { blockResults: { 31: { 营业收入: 200000000, 中间业务收入: 50000000, unitByField: { 营业收入: 'YUAN', 中间业务收入: 'YUAN' } } } }
      },
      global: { stubs }
    });
    mounted.push(wrapper);
    expect(wrapper.find('[data-layout-group="REVENUE"] [data-testid="revenue-share-chart"]').exists()).toBe(true);
    expect(wrapper.find('[data-layout-group="REVENUE"] [data-testid="revenue-share-chart"]').text()).toContain('25.0%');
    expect(wrapper.find('[data-layout-group="REVENUE"] [data-testid="metric-widget"]').exists()).toBe(false);
  });

  it('收入组有额外已配置指标时仍展示该指标', () => {
    const ids = ['business-revenue-operating', 'business-revenue-fee', 'business-revenue-other'];
    const wrapper = mount(PresentationLayout, {
      props: {
        presentation: { displaySchemaVersion: 1, display: { components: ids.map((componentId, order) => ({
          componentId, componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order, visible: true,
          text: { titleMode: 'CUSTOM', title: componentId }, content: { mainField: 'value' }, dataRefs: [{ blockId: order + 1, unit: 'YUAN' }]
        })) } },
        model: { blockResults: { 1: { value: 100, unit: 'YUAN' }, 2: { value: 20, unit: 'YUAN' }, 3: { value: 5, unit: 'YUAN' } } }
      },
      global: { stubs }
    });
    mounted.push(wrapper);
    expect(wrapper.find('[data-component-id="business-revenue-share"]').exists()).toBe(true);
    expect(wrapper.find('[data-component-id="business-revenue-other"]').exists()).toBe(true);
  });

  it('只净化展示文案，不改动指标绑定字段和原始配置', () => {
    const metric = {
      componentId: 'business-retail-deposit-balance', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 0, visible: true,
      text: { titleMode: 'CUSTOM', title: '测试_直营存款余额' },
      format: { displayUnit: 'YUAN' }, content: { mainField: '测试_直营存款余额' },
      dataRefs: [{ blockId: 31, metricName: '测试_直营存款余额', unit: 'YUAN' }]
    };
    const ranking = {
      componentId: 'ranking', componentType: 'RANKING', layoutRegion: 'RIGHT', order: 0, visible: true,
      text: { titleMode: 'CUSTOM', title: '测试_直营机构排名' },
      content: { rankingMetrics: [{ metricKey: 'deposit', field: 'deposit', label: '测试_直营存款余额', unit: 'YUAN' }] }
    };
    const wrapper = mount(PresentationLayout, {
      props: { presentation: { displaySchemaVersion: 1, display: { components: [metric, ranking] } },
        model: { blockResults: { 31: { '测试_直营存款余额': 100, unit: 'YUAN' } }, institutions: [{ orgCode: 'A', orgName: 'A' }], rankings: [{ orgCode: 'A', deposit: 100 }] } },
      global: { stubs: {
        ...stubs,
        MetricDisplayWidgets: { props: ['components'], template: '<div data-testid="sanitized-metric">{{ components[0]?.title }}|{{ components[0]?.metricName }}|{{ components[0]?.value }}</div>' },
        InstitutionRankingWidget: { props: ['model', 'title'], template: '<div data-testid="sanitized-ranking">{{ title }}|{{ model.metric?.label }}</div>' }
      } }
    });
    mounted.push(wrapper);
    expect(wrapper.find('[data-testid="sanitized-metric"]').text()).toBe('存款余额|存款余额|100');
    expect(wrapper.find('[data-testid="sanitized-ranking"]').text()).toBe('机构排名|存款余额');
    expect(metric.content.mainField).toBe('测试_直营存款余额');
    expect(metric.dataRefs[0].metricName).toBe('测试_直营存款余额');
  });

  it('仅按 business componentId 前缀把分行顶部指标分成零售、对公和收入三组', () => {
    const groupedHeader = [
      'business-retail-deposit-balance', 'business-retail-deposit-rate',
      'business-retail-loan-balance', 'business-retail-loan-rate',
      'business-corp-deposit-balance', 'business-corp-deposit-rate',
      'business-corp-loan-balance', 'business-corp-loan-rate',
      'business-revenue-operating', 'business-revenue-fee'
    ].map((componentId, order) => ({
      componentId, componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order, visible: true,
      text: { titleMode: 'CUSTOM', title: componentId },
      format: { displayUnit: 'TEN_THOUSAND' },
      content: { mainField: componentId.includes('rate') ? '测试_对公贷款目标完成率' : '测试_直营存款' },
      dataRefs: [{ blockId: 31, unit: componentId.includes('rate') ? 'PERCENT' : 'YUAN' }]
    }));
    const wrapper = mount(PresentationLayout, {
      props: {
        presentation: { displaySchemaVersion: 1, display: { components: groupedHeader } },
        model: { blockResults: { 31: { value: 88, unit: 'PERCENT' } } }
      },
      global: { stubs }
    });
    mounted.push(wrapper);

    expect(wrapper.find('[data-layout-mode="grouped"]').exists()).toBe(true);
    expect(wrapper.find('[data-layout-mode="generic"]').exists()).toBe(false);
    expect(wrapper.findAll('[data-layout-group]')).toHaveLength(3);
    expect(wrapper.find('[data-layout-group="RETAIL"]').attributes('data-group-prefix')).toBe('business-retail-');
    expect(wrapper.find('[data-layout-group="CORP"]').attributes('data-group-prefix')).toBe('business-corp-');
    expect(wrapper.find('[data-layout-group="REVENUE"]').attributes('data-group-prefix')).toBe('business-revenue-');
    expect(wrapper.find('[data-layout-group="RETAIL"]').findAll('[data-testid="presentation-layout-component"]')).toHaveLength(4);
    expect(wrapper.find('[data-layout-group="CORP"]').findAll('[data-testid="presentation-layout-component"]')).toHaveLength(4);
    expect(wrapper.find('[data-layout-group="REVENUE"]').findAll('[data-testid="presentation-layout-component"]')).toHaveLength(1);
    expect(wrapper.find('[data-layout-group="RETAIL"] [data-testid="metric-widget"]').attributes('data-grouped')).toBe('true');
  });

  it('将配置组件重排为重点卡/次级卡、左中右三栏，并让地图先于趋势且明细下置', () => {
    const components = [
      ...Array.from({ length: 4 }, (_, index) => ({
        componentId: `metric-${index + 1}`, componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: index,
        visible: true, text: { titleMode: 'CUSTOM', title: `重点指标${index + 1}` },
        format: { displayUnit: 'HUNDRED_MILLION' }, content: { mainField: 'value' }, dataRefs: [{ blockId: index + 1 }]
      })),
      ...Array.from({ length: 4 }, (_, index) => ({
        componentId: `metric-${index + 5}`, componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: index + 4,
        visible: true, text: { titleMode: 'CUSTOM', title: `次级指标${index + 1}` },
        format: { displayUnit: 'AUTO' }, content: { mainField: 'value' }, dataRefs: [{ blockId: index + 5 }]
      })),
      { componentId: 'composition', componentType: 'COMPOSITION_TABS', layoutRegion: 'LEFT', order: 0, visible: true, text: { titleMode: 'CUSTOM', title: '业务结构' }, content: { tabs: [] }, dataRefs: [] },
      { componentId: 'trend-before-map', componentType: 'TREND', layoutRegion: 'CENTER', order: 0, visible: true, text: { titleMode: 'CUSTOM', title: '趋势一' }, content: { series: [] }, dataRefs: [] },
      { componentId: 'map-main', componentType: 'MAP', layoutRegion: 'CENTER', order: 2, visible: true, text: { titleMode: 'CUSTOM', title: '机构地图' }, content: { mainField: 'deposit' }, dataRefs: [] },
      { componentId: 'trend-after-map', componentType: 'TREND', layoutRegion: 'CENTER', order: 1, visible: true, text: { titleMode: 'CUSTOM', title: '趋势二' }, content: { series: [] }, dataRefs: [] },
      { componentId: 'ranking', componentType: 'RANKING', layoutRegion: 'RIGHT', order: 0, visible: true, text: { titleMode: 'CUSTOM', title: '全量排名' }, content: { rankingMetrics: [] }, dataRefs: [] },
      ...Array.from({ length: 3 }, (_, index) => ({
        componentId: `detail-${index + 1}`, componentType: 'DETAIL_TABLE', layoutRegion: 'BOTTOM', order: index, visible: true,
        text: { titleMode: 'CUSTOM', title: `明细${index + 1}` }, content: { columns: [] }, dataRefs: []
      }))
    ];
    const wrapper = mount(PresentationLayout, {
      props: {
        presentation: { displaySchemaVersion: 1, display: { components } },
        model: { institutions: [], rankings: [], blockResults: {} }
      },
      global: { stubs }
    });
    mounted.push(wrapper);

    expect(wrapper.find('[data-layout-mode="generic"]').exists()).toBe(true);
    expect(wrapper.find('[data-layout-mode="grouped"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="metric-widget"]').attributes('data-grouped')).toBe('false');
    expect(wrapper.find('[data-layout-tier="PRIMARY"]').findAll('[data-component-type="METRIC_CARD"]')).toHaveLength(4);
    expect(wrapper.find('[data-layout-tier="SECONDARY"]').findAll('[data-component-type="METRIC_CARD"]')).toHaveLength(4);
    expect(wrapper.findAll('[data-layout-column]')).toHaveLength(3);
    expect(wrapper.find('[data-layout-column="LEFT"]').find('[data-component-id="composition"]').exists()).toBe(true);
    expect(wrapper.find('[data-layout-column="RIGHT"]').find('[data-component-id="ranking"]').exists()).toBe(true);
    expect(wrapper.find('[data-layout-column="CENTER"]').findAll('[data-testid="presentation-layout-component"]')
      .map(node => node.attributes('data-component-id'))).toEqual(['map-main', 'trend-before-map', 'trend-after-map']);
    expect(wrapper.find('[data-layout-region="BOTTOM"]').findAll('[data-component-type="DETAIL_TABLE"]')).toHaveLength(3);
  });

  it('分行总览把中央趋势视觉放到左侧业务结构下方，同时保留保存区域', () => {
    const components = [
      { componentId: 'structure', componentType: 'COMPOSITION_TABS', layoutRegion: 'LEFT', order: 0, visible: true, text: { titleMode: 'CUSTOM', title: '业务结构' }, content: { tabs: [] }, dataRefs: [] },
      { componentId: 'trend', componentType: 'TREND', layoutRegion: 'CENTER', order: 1, visible: true, text: { titleMode: 'CUSTOM', title: '趋势' }, content: { series: [] }, dataRefs: [] },
      { componentId: 'branch-trend', componentType: 'TREND', layoutRegion: 'CENTER', order: 2, visible: true, text: { titleMode: 'CUSTOM', title: '支行趋势' }, content: { series: [] }, dataRefs: [] },
      { componentId: 'map', componentType: 'MAP', layoutRegion: 'CENTER', order: 0, visible: true, text: { titleMode: 'CUSTOM', title: '机构地图' }, content: { mainField: 'deposit' }, dataRefs: [] },
      { componentId: 'ranking', componentType: 'RANKING', layoutRegion: 'RIGHT', order: 0, visible: true, text: { titleMode: 'CUSTOM', title: '机构排名' }, content: { rankingMetrics: [] }, dataRefs: [] }
    ];
    const wrapper = mount(PresentationLayout, {
      props: {
        presentation: { type: 'CODE', template: 'branch-overview-v1', displaySchemaVersion: 1, display: { components } },
        model: { institutions: [], rankings: [], blockResults: {} }
      },
      global: { stubs }
    });
    mounted.push(wrapper);

    expect(wrapper.find('.presentation-layout--branch-overview').exists()).toBe(true);
    expect(wrapper.find('[data-layout-column="LEFT"]').findAll('[data-testid="presentation-layout-component"]')
      .map(node => node.attributes('data-component-id'))).toEqual(['structure', 'trend', 'branch-trend']);
    expect(wrapper.find('[data-layout-column="CENTER"]').findAll('[data-testid="presentation-layout-component"]')
      .map(node => node.attributes('data-component-id'))).toEqual(['map']);
    expect(wrapper.find('[data-component-id="trend"]').attributes('data-layout-region')).toBe('CENTER');
    expect(wrapper.get('[data-testid="presentation-layout-main"]').classes()).toContain('presentation-layout__main--branch-overview');
    expect(wrapper.get('[data-layout-column="LEFT"]').classes()).toContain('presentation-layout__column--branch-overview');
    expect(wrapper.get('[data-layout-column="LEFT"]').findAll('.presentation-layout__component--trend')).toHaveLength(2);
    expect(wrapper.get('[data-layout-column="RIGHT"] .presentation-layout__component--ranking').attributes('data-visible-rows')).toBe('10');
    expect(wrapper.get('[data-testid="ranking-widget"]').attributes()).toMatchObject({
      'data-paginate': 'true', 'data-page-size': '10', 'data-page-interval': '5000', 'data-metric-carousel': 'false'
    });
  });

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
