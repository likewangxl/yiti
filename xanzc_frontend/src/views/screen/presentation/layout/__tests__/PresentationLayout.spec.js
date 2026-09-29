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
  SeriesTableWidgets: { props: ['components', 'tabbed', 'trendDisplayMode'], template: '<div data-testid="series-widget" :data-tabbed="tabbed ? \'true\' : \'false\'" :data-trend-display-mode="trendDisplayMode"><span v-for="item in components" :key="item.componentId" data-testid="series-component">{{ item.componentId }}</span></div>' },
  BusinessGrowthWidget: { props: ['presentation', 'model', 'amountUnit'], template: '<div data-testid="business-growth-widget" :data-template="presentation?.template || \'\'" :data-amount-unit="amountUnit">业务增长曲线</div>' },
  CompositionTabsWidget: { props: ['model', 'ringKeys', 'compact'], emits: ['business-line-select'], template: '<div data-testid="structure-widget" :data-ring-keys="ringKeys?.join(\',\') || \'\'" :data-compact="compact ? \'true\' : \'false\'"><span>{{ model.components?.[0]?.componentId }}</span><button v-if="ringKeys?.length" type="button" data-testid="structure-business-line-select" @click="$emit(\'business-line-select\', { businessLine: \'CORP\', tabKey: ringKeys[0] })">选择</button></div>' },
  InstitutionRankingWidget: { props: ['model', 'title', 'paginate', 'pageSize', 'pageInterval', 'metricCarousel'], template: '<div data-testid="ranking-widget" :data-paginate="paginate ? \'true\' : \'false\'" :data-page-size="pageSize" :data-page-interval="pageInterval" :data-metric-carousel="metricCarousel ? \'true\' : \'false\'">{{ title }}</div>' },
  PresentationMapWidget: { props: ['presentation'], template: '<div data-testid="map-widget">地图</div>' }
};

const mounted = [];
afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()));

describe('PresentationLayout', () => {
  it('零售和对公分组显示查看更多并沿用业务线事件，营业收入组不显示入口', async () => {
    const components = [
      ['business-retail-deposit-balance', '零售存款余额'],
      ['business-corp-deposit-balance', '对公存款余额'],
      ['business-revenue-operating', '营业收入'],
      ['business-revenue-fee', '中间业务收入']
    ].map(([componentId, title], order) => ({
      componentId, componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order, visible: true,
      text: { titleMode: 'CUSTOM', title }, format: { displayUnit: 'TEN_THOUSAND' },
      content: { mainField: 'value' }, dataRefs: [{ blockId: order + 1, unit: 'YUAN' }]
    }));
    const wrapper = mount(PresentationLayout, {
      props: { presentation: { displaySchemaVersion: 1, template: 'branch-overview-v1', display: { components } }, model: { blockResults: {} } },
      global: { stubs }
    });
    mounted.push(wrapper);

    expect(wrapper.find('[data-layout-group="RETAIL"] [data-action="business-line-more"]').text()).toBe('查看更多');
    expect(wrapper.find('[data-layout-group="CORP"] [data-action="business-line-more"]').text()).toBe('查看更多');
    expect(wrapper.find('[data-layout-group="REVENUE"] [data-action="business-line-more"]').exists()).toBe(false);
    await wrapper.find('[data-layout-group="RETAIL"] [data-action="business-line-more"]').trigger('click');
    await wrapper.find('[data-layout-group="CORP"] [data-action="business-line-more"]').trigger('click');
    expect(wrapper.emitted('business-line-select')).toEqual([
      [{ businessLine: 'RETAIL', tabKey: 'deposit' }],
      [{ businessLine: 'CORP', tabKey: 'deposit' }]
    ]);
  });

  it('非分行配置保留分组项数，不出现经营总览入口', () => {
    const components = ['business-retail-deposit-balance', 'business-corp-deposit-balance']
      .map((componentId, order) => ({
        componentId, componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order, visible: true,
        text: { titleMode: 'CUSTOM', title: componentId }, format: { displayUnit: 'TEN_THOUSAND' },
        content: { mainField: 'value' }, dataRefs: [{ blockId: order + 1, unit: 'YUAN' }]
      }));
    const wrapper = mount(PresentationLayout, {
      props: { presentation: { displaySchemaVersion: 1, display: { components } }, model: { blockResults: {} } },
      global: { stubs }
    });
    mounted.push(wrapper);

    expect(wrapper.find('[data-layout-group="RETAIL"] [data-action="business-line-more"]').exists()).toBe(false);
    expect(wrapper.find('[data-layout-group="RETAIL"] .presentation-layout__metric-group-header small').text()).toBe('1项');
  });

  it('把金额单位传入指标模型，金额与较上月同步变更而完成率保持百分比', () => {
    const components = [
      {
        componentId: 'business-retail-deposit-balance', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 0, visible: true,
        text: { titleMode: 'CUSTOM', title: '零售存款余额' }, format: { displayUnit: 'TEN_THOUSAND', decimals: 2 },
        content: { mainField: 'deposit' }, dataRefs: [{ blockId: 31, metricCode: 'deposit', unit: 'YUAN' }]
      },
      {
        componentId: 'business-retail-deposit-rate', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 1, visible: true,
        text: { titleMode: 'CUSTOM', title: '零售存款完成率' }, format: { displayUnit: 'PERCENT', decimals: 2 },
        content: { mainField: 'rate' }, dataRefs: [{ blockId: 31, metricCode: 'rate', unit: 'RATIO' }]
      }
    ];
    const wrapper = mount(PresentationLayout, {
      props: {
        amountUnit: 'YUAN',
        presentation: { displaySchemaVersion: 1, template: 'branch-overview-v1', display: { components } },
        dataDate: '2026-09-21',
        model: {
          dataDate: '2026-09-21',
          blockResults: {
            31: { deposit: 1068201500, rate: 0.9064, unitByField: { deposit: 'YUAN', rate: 'RATIO' } },
            57: { rows: [{ date: '2026-09-21', deposit: 1068201500, rate: 0.9064 }, { date: '2026-08-31', deposit: 1056201500, rate: 0.8 }], unitByField: { deposit: 'YUAN', rate: 'RATIO' } }
          }
        }
      },
      global: {
        stubs: {
          ...stubs,
          MetricDisplayWidgets: { props: ['components', 'grouped'], template: '<div data-testid="metric-widget"><span v-for="item in components" :key="item.componentId">{{ item.text }} {{ item.monthDelta?.text }}</span></div>' }
        }
      }
    });
    mounted.push(wrapper);
    const text = wrapper.findAll('[data-layout-group="RETAIL"] [data-testid="metric-widget"]')
      .map(node => node.text()).join(' ');
    expect(text).toContain('1,068,201,500.00元');
    expect(text).toContain('较上月 +12,000,000.00元');
    expect(text).toContain('90.64%');
    expect(text).toContain('较上月 +10.64个百分点');
  });

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
      global: {
        stubs: {
          ...stubs,
          MetricDisplayWidgets: {
            props: ['components', 'grouped'],
            template: '<div data-testid="metric-widget" :data-grouped="grouped ? \'true\' : \'false\'"><span v-for="item in components" :key="item.componentId">{{ item.componentId }} {{ item.text }} {{ item.monthDelta?.text }}</span></div>'
          }
        }
      }
    });
    mounted.push(wrapper);
    const revenueGroup = wrapper.get('[data-layout-group="REVENUE"]');
    const chart = revenueGroup.get('[data-testid="revenue-share-chart"]');
    expect(revenueGroup.findAll('[data-testid="presentation-layout-component"]')).toHaveLength(1);
    expect(revenueGroup.find('[data-component-id="business-revenue-share"]').exists()).toBe(true);
    expect(chart.findAll('[data-testid="metric-widget"]')).toHaveLength(2);
    expect(chart.find('[role="img"]').exists()).toBe(false);
    expect(chart.find('[data-testid="revenue-share-ratio-label"]').exists()).toBe(false);
    expect(chart.text()).toContain('20,000.00万元');
    expect(chart.text()).toContain('5,000.00万元');
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

  it('draftOverview 在原分组上方保留存款与贷款总额，移除存贷款合计且缺失或单位不兼容不补0', () => {
    const header = ['business-corp-deposit-balance', 'business-corp-loan-balance'].map((componentId, order) => ({
      componentId, componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order, visible: true,
      text: { titleMode: 'CUSTOM', title: componentId }, format: { displayUnit: 'HUNDRED_MILLION' },
      content: { mainField: 'value' }, dataRefs: [{ blockId: order + 1, metricCode: order ? 'loan' : 'deposit', unit: 'HUNDRED_MILLION' }]
    }));
    const wrapper = mount(PresentationLayout, {
      props: {
        draftOverview: true,
        presentation: { displaySchemaVersion: 1, template: 'branch-overview-v1', display: { components: header } },
        model: { dataDate: '2028-03-02', kpis: [
          { key: 'deposit', value: 120, unit: '亿元', dataDate: '2028-03-02', comparisons: {
            year: { state: 'READY', value: 30, text: '较上年 +30.00亿元', referenceDate: '2027-12-31' }, month: { state: 'READY', value: 20, text: '较上月 +20.00亿元', referenceDate: '2028-02-29' }, day: { state: 'READY', value: 10, text: '较上日 +10.00亿元', referenceDate: '2028-03-01' }
          } },
          { key: 'loan', value: 80, unit: '亿元', dataDate: '2028-03-02', comparisons: {
            year: { state: 'READY', value: 10, text: '较上年 +10.00亿元', referenceDate: '2027-12-31' }, month: { state: 'READY', value: 8, text: '较上月 +8.00亿元', referenceDate: '2028-02-29' }, day: { state: 'READY', value: 5, text: '较上日 +5.00亿元', referenceDate: '2028-03-01' }
          } }
        ] }
      },
      global: { stubs }
    });
    mounted.push(wrapper);
    const summary = wrapper.get('[data-testid="draft-overview-summary"]');
    expect(summary.findAll('[data-testid="draft-overview-summary-card"]')).toHaveLength(2);
    expect(summary.findAll('[data-testid="draft-overview-summary-card"]').map(node => node.attributes('data-summary-key')))
      .toEqual(['deposit', 'loan']);
    expect(summary.get('[data-summary-key="deposit"]').text()).toContain('存款总额');
    expect(summary.get('[data-summary-key="deposit"]').text()).toContain('120.00亿元');
    expect(summary.get('[data-summary-key="loan"]').text()).toContain('80.00亿元');
    expect(summary.find('[data-summary-key="total"]').exists()).toBe(false);
    const depositComparisons = summary.get('[data-summary-key="deposit"] .presentation-layout__overview-summary-card-comparisons');
    expect(depositComparisons.text()).toContain('较上年 +30.00亿元');
    expect(depositComparisons.text()).toContain('较上月 +20.00亿元');
    expect(depositComparisons.text()).toContain('较上日 +10.00亿元');

    const invalid = mount(PresentationLayout, {
      props: {
        draftOverview: true,
        presentation: { displaySchemaVersion: 1, template: 'branch-overview-v1', display: { components: header } },
        model: { dataDate: '2028-03-02', kpis: [
          { key: 'deposit', value: 120, unit: '亿元', dataDate: '2028-03-02' },
          { key: 'loan', value: null, unit: '万元', dataDate: '2028-03-01' }
        ] }
      },
      global: { stubs }
    });
    mounted.push(invalid);
    expect(invalid.get('[data-summary-key="loan"]').text()).toContain('—');

    const mixedUnits = mount(PresentationLayout, {
      props: {
        draftOverview: true,
        presentation: { displaySchemaVersion: 1, template: 'branch-overview-v1', display: { components: header } },
        model: { dataDate: '2028-03-02', kpis: [
          { key: 'deposit', value: 120, unit: '亿元', dataDate: '2028-03-02' },
          { key: 'loan', value: 8000, unit: '万元', dataDate: '2028-03-02' }
        ] }
      },
      global: { stubs }
    });
    mounted.push(mixedUnits);
    expect(mixedUnits.get('[data-summary-key="loan"]').text()).toContain('0.80亿元');

    const incompatibleUnits = mount(PresentationLayout, {
      props: {
        draftOverview: true,
        presentation: { displaySchemaVersion: 1, template: 'branch-overview-v1', display: { components: header } },
        model: { dataDate: '2028-03-02', kpis: [
          { key: 'deposit', value: 120, unit: '亿元', dataDate: '2028-03-02' },
          { key: 'loan', value: 80, unit: '%', dataDate: '2028-03-02' }
        ] }
      },
      global: { stubs }
    });
    mounted.push(incompatibleUnits);
    expect(incompatibleUnits.get('[data-summary-key="loan"]').text()).toContain('—');

    for (const invalidValue of [' ', true, [], {}, Infinity]) {
      const invalidNumber = mount(PresentationLayout, {
        props: {
          draftOverview: true,
          presentation: { displaySchemaVersion: 1, template: 'branch-overview-v1', display: { components: header } },
          model: { dataDate: '2028-03-02', kpis: [
            { key: 'deposit', value: 120, unit: '亿元', dataDate: '2028-03-02' },
            { key: 'loan', value: invalidValue, unit: '亿元', dataDate: '2028-03-02' }
          ] }
        },
        global: { stubs }
      });
      mounted.push(invalidNumber);
      expect(invalidNumber.get('[data-summary-key="loan"]').text()).toContain('—');
    }

    const published = mount(PresentationLayout, {
      props: { draftOverview: false, presentation: { displaySchemaVersion: 1, template: 'branch-overview-v1', display: { components: header } }, model: {} },
      global: { stubs }
    });
    mounted.push(published);
    expect(published.find('[data-testid="draft-overview-summary"]').exists()).toBe(false);
  });

  it('总额卡保留交错顺序并接入当前、上月末、上日余额条形图，金额单位随页头切换', async () => {
    const components = [
      { componentId: 'business-retail-deposit-balance', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 0, visible: true, text: { titleMode: 'CUSTOM', title: '零售存款余额' }, format: { displayUnit: 'HUNDRED_MILLION' }, content: { mainField: 'value' }, dataRefs: [{ blockId: 31, metricCode: 'retailDeposit', unit: 'HUNDRED_MILLION' }] },
      { componentId: 'business-corp-deposit-balance', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 1, visible: true, text: { titleMode: 'CUSTOM', title: '对公存款余额' }, format: { displayUnit: 'HUNDRED_MILLION' }, content: { mainField: 'value' }, dataRefs: [{ blockId: 31, metricCode: 'corpDeposit', unit: 'HUNDRED_MILLION' }] },
      { componentId: 'business-retail-loan-balance', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 2, visible: true, text: { titleMode: 'CUSTOM', title: '零售贷款余额' }, format: { displayUnit: 'HUNDRED_MILLION' }, content: { mainField: 'value' }, dataRefs: [{ blockId: 31, metricCode: 'retailLoan', unit: 'HUNDRED_MILLION' }] },
      { componentId: 'business-corp-loan-balance', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 3, visible: true, text: { titleMode: 'CUSTOM', title: '对公贷款余额' }, format: { displayUnit: 'HUNDRED_MILLION' }, content: { mainField: 'value' }, dataRefs: [{ blockId: 31, metricCode: 'corpLoan', unit: 'HUNDRED_MILLION' }] },
      { componentId: 'left-structure', componentType: 'COMPOSITION_TABS', layoutRegion: 'LEFT', order: 0, visible: true, text: { titleMode: 'CUSTOM', title: '业务结构' }, content: { tabs: [] }, dataRefs: [] }
    ];
    const wrapper = mount(PresentationLayout, {
      props: {
        amountUnit: 'HUNDRED_MILLION',
        draftOverview: true,
        presentation: { displaySchemaVersion: 1, template: 'branch-overview-v1', display: { components } },
        model: {
          dataDate: '2028-03-02',
          kpis: [
            { key: 'deposit', value: 120, unit: '亿元', dataDate: '2028-03-02', comparisons: { month: { value: 20, unit: '亿元', referenceDate: '2028-02-29' }, day: { value: 10, unit: '亿元', referenceDate: '2028-03-01' } } },
            { key: 'loan', value: 80, unit: '亿元', dataDate: '2028-03-02', comparisons: { month: { value: 8, unit: '亿元', referenceDate: '2028-02-29' }, day: { value: 5, unit: '亿元', referenceDate: '2028-03-01' } } }
          ]
        }
      },
      global: { stubs }
    });
    mounted.push(wrapper);

    expect(wrapper.get('[data-testid="draft-overview-summary"]').findAll('[data-testid="draft-overview-summary-card"]').map(node => node.attributes('data-summary-key')))
      .toEqual(['deposit', 'deposit-composition', 'loan', 'loan-composition']);
    expect(wrapper.get('[data-summary-key="deposit"] [data-testid="overview-balance-chart"]').attributes('data-state')).toBe('READY');
    expect(wrapper.get('[data-summary-key="deposit"] [data-bar-key="month"]').text()).toContain('100.00亿元');
    expect(wrapper.get('[data-summary-key="loan"] .presentation-layout__overview-summary-card-comparisons').text()).toContain('较上日 +5.00亿元');

    await wrapper.setProps({ amountUnit: 'TEN_THOUSAND' });
    expect(wrapper.get('[data-summary-key="deposit"] [data-testid="overview-balance-chart"]').attributes('data-unit')).toBe('TEN_THOUSAND');
    expect(wrapper.get('[data-summary-key="deposit"] [data-bar-key="current"]').text()).toContain('1,200,000.00万元');
  });

  it('未发布分行总览按总额与业务分布交错排列，左下只保留收入且其他业务结构不受过滤', async () => {
    const composition = (componentId, layoutRegion, order) => ({
      componentId, componentType: 'COMPOSITION_TABS', layoutRegion, order, visible: true,
      text: { titleMode: 'CUSTOM', title: componentId },
      content: { tabs: [
        { tabKey: 'deposit', label: '存款', corporateField: 'corpDeposit', retailField: 'retailDeposit', totalField: 'depositTotal', unit: 'HUNDRED_MILLION' },
        { tabKey: 'loan', label: '贷款', corporateField: 'corpLoan', retailField: 'retailLoan', totalField: 'loanTotal', unit: 'HUNDRED_MILLION' },
        { tabKey: 'income', label: '收入', corporateField: 'corpIncome', retailField: 'retailIncome', totalField: 'incomeTotal', unit: 'HUNDRED_MILLION' }
      ] },
      dataRefs: [{ blockId: 31, role: 'PRIMARY', unit: 'HUNDRED_MILLION' }]
    });
    const wrapper = mount(PresentationLayout, {
      props: {
        draftOverview: true,
        presentation: { displaySchemaVersion: 1, template: 'branch-overview-v1', display: { components: [
          composition('left-structure', 'LEFT', 0), composition('other-structure', 'CENTER', 0)
        ] } },
        model: {
          dataDate: '2028-03-02',
          kpis: [{ key: 'deposit', value: 120, unit: '亿元', dataDate: '2028-03-02' }, { key: 'loan', value: 80, unit: '亿元', dataDate: '2028-03-02' }],
          blockResults: { 31: {
            corpDeposit: 60, retailDeposit: 40, depositTotal: 100,
            corpLoan: 30, retailLoan: 20, loanTotal: 50,
            corpIncome: 12, retailIncome: 8, incomeTotal: 20,
            unitByField: { corpDeposit: 'HUNDRED_MILLION', retailDeposit: 'HUNDRED_MILLION', depositTotal: 'HUNDRED_MILLION', corpLoan: 'HUNDRED_MILLION', retailLoan: 'HUNDRED_MILLION', loanTotal: 'HUNDRED_MILLION', corpIncome: 'HUNDRED_MILLION', retailIncome: 'HUNDRED_MILLION', incomeTotal: 'HUNDRED_MILLION' }
          } }
        }
      },
      global: { stubs }
    });
    mounted.push(wrapper);

    const summaryCards = wrapper.get('[data-testid="draft-overview-summary"]').findAll('[data-testid="draft-overview-summary-card"]');
    expect(summaryCards.map(node => node.attributes('data-summary-key')))
      .toEqual(['deposit', 'deposit-composition', 'loan', 'loan-composition']);
    expect(wrapper.get('[data-summary-key="deposit-composition"]').attributes('data-summary-kind')).toBe('COMPOSITION');
    expect(wrapper.get('[data-summary-key="deposit-composition"] [data-testid="structure-widget"]').attributes('data-ring-keys')).toBe('deposit');
    expect(wrapper.get('[data-summary-key="deposit-composition"] [data-testid="structure-widget"]').attributes('data-compact')).toBe('true');
    expect(wrapper.get('[data-summary-key="loan-composition"] [data-testid="structure-widget"]').attributes('data-ring-keys')).toBe('loan');

    const leftStructure = wrapper.get('[data-layout-column="LEFT"] [data-component-id="left-structure"] [data-testid="structure-widget"]');
    expect(leftStructure.attributes('data-ring-keys')).toBe('income');
    expect(leftStructure.attributes('data-compact')).toBe('false');
    const otherStructure = wrapper.get('[data-layout-column="CENTER"] [data-component-id="other-structure"] [data-testid="structure-widget"]');
    expect(otherStructure.attributes('data-ring-keys')).toBe('');
    expect(otherStructure.attributes('data-compact')).toBe('false');

    await wrapper.get('[data-summary-key="deposit-composition"] [data-testid="structure-business-line-select"]').trigger('click');
    expect(wrapper.emitted('business-line-select')).toContainEqual([{ businessLine: 'CORP', tabKey: 'deposit' }]);
  });

  it('总额与完整细分余额同日但数值不一致时拒绝借用细分三维差值', () => {
    const fields = [
      ['business-retail-deposit-balance', 'retailDeposit'],
      ['business-corp-deposit-balance', 'corpDeposit'],
      ['business-retail-loan-balance', 'retailLoan'],
      ['business-corp-loan-balance', 'corpLoan']
    ];
    const components = fields.map(([componentId, field], order) => ({
      componentId, componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order, visible: true,
      text: { titleMode: 'CUSTOM', title: componentId }, format: { displayUnit: 'TEN_THOUSAND' },
      content: { mainField: field }, dataRefs: [{ blockId: 31, metricCode: field, unit: 'TEN_THOUSAND' }]
    }));
    const rows = [
      { date: '2028-03-02', retailDeposit: 10000, corpDeposit: 23000, retailLoan: 10000, corpLoan: 13000 },
      { date: '2027-12-31', retailDeposit: 8000, corpDeposit: 16000, retailLoan: 9000, corpLoan: 12000 },
      { date: '2028-02-29', retailDeposit: 9000, corpDeposit: 21000, retailLoan: 9500, corpLoan: 12500 },
      { date: '2028-03-01', retailDeposit: 9500, corpDeposit: 22000, retailLoan: 9800, corpLoan: 12800 }
    ];
    const wrapper = mount(PresentationLayout, {
      props: {
        draftOverview: true,
        presentation: { displaySchemaVersion: 1, template: 'branch-overview-v1', display: { components } },
        model: {
          dataDate: '2028-03-02',
          kpis: [
            { key: 'deposit', value: 50000, unit: '万元', dataDate: '2028-03-02' },
            { key: 'loan', value: 23000, unit: '万元', dataDate: '2028-03-02' }
          ],
          blockResults: {
            31: { ...rows[0], unitByField: { retailDeposit: 'TEN_THOUSAND', corpDeposit: 'TEN_THOUSAND', retailLoan: 'TEN_THOUSAND', corpLoan: 'TEN_THOUSAND' } },
            57: { rows, unitByField: { retailDeposit: 'TEN_THOUSAND', corpDeposit: 'TEN_THOUSAND', retailLoan: 'TEN_THOUSAND', corpLoan: 'TEN_THOUSAND' } }
          }
        }
      },
      global: { stubs }
    });
    mounted.push(wrapper);
    expect(wrapper.get('[data-summary-key="deposit"]').text()).toContain('50,000.00万元');
    expect(wrapper.get('[data-summary-key="loan"]').text()).toContain('23,000.00万元');
    expect(wrapper.get('[data-summary-key="loan"] .presentation-layout__overview-summary-card-comparisons').text()).toContain('较上年 +2,000.00万元');

    const decimalEquivalent = mount(PresentationLayout, {
      props: {
        draftOverview: true,
        presentation: { displaySchemaVersion: 1, template: 'branch-overview-v1', display: { components } },
        model: {
          dataDate: '2028-03-02',
          kpis: [
            { key: 'deposit', value: 2.3, unit: '亿元', dataDate: '2028-03-02' },
            { key: 'loan', value: 2.3, unit: '亿元', dataDate: '2028-03-02' }
          ],
          blockResults: {
            31: { ...rows[0], retailDeposit: 10000, corpDeposit: 13000, retailLoan: 10000, corpLoan: 13000, unitByField: { retailDeposit: 'TEN_THOUSAND', corpDeposit: 'TEN_THOUSAND', retailLoan: 'TEN_THOUSAND', corpLoan: 'TEN_THOUSAND' } },
            57: { rows: [
              { date: '2028-03-02', retailDeposit: 10000, corpDeposit: 13000, retailLoan: 10000, corpLoan: 13000 },
              { date: '2027-12-31', retailDeposit: 8000, corpDeposit: 10000, retailLoan: 9000, corpLoan: 9000 },
              { date: '2028-02-29', retailDeposit: 9000, corpDeposit: 11000, retailLoan: 9500, corpLoan: 10500 },
              { date: '2028-03-01', retailDeposit: 9500, corpDeposit: 12000, retailLoan: 9800, corpLoan: 11700 }
            ], unitByField: { retailDeposit: 'TEN_THOUSAND', corpDeposit: 'TEN_THOUSAND', retailLoan: 'TEN_THOUSAND', corpLoan: 'TEN_THOUSAND' } }
          }
        }
      },
      global: { stubs }
    });
    mounted.push(decimalEquivalent);
    expect(decimalEquivalent.get('[data-summary-key="deposit"]').text()).toContain('2.30亿元');
    expect(decimalEquivalent.get('[data-summary-key="loan"]').text()).toContain('2.30亿元');
    const decimalComparisons = decimalEquivalent.get('[data-summary-key="loan"] .presentation-layout__overview-summary-card-comparisons').text();
    expect(decimalComparisons).toContain('较上年 +0.50亿元');
    expect(decimalComparisons).toContain('较上月 +0.30亿元');
    expect(decimalComparisons).toContain('较上日 +0.15亿元');

    const invalidDate = mount(PresentationLayout, {
      props: {
        draftOverview: true,
        presentation: { displaySchemaVersion: 1, template: 'branch-overview-v1', display: { components } },
        model: { dataDate: '2028-03-02', kpis: [
          { key: 'deposit', value: 120, unit: '亿元', dataDate: 'not-a-date' },
          { key: 'loan', value: 80, unit: '亿元', dataDate: '2028-03-02' }
        ] }
      },
      global: { stubs }
    });
    mounted.push(invalidDate);
    expect(invalidDate.get('[data-summary-key="deposit"]').text()).toContain('—');
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

  it('分行总览把中央趋势归组为同时可见的业务增长曲线，并保留保存区域', () => {
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
      .map(node => node.attributes('data-component-id'))).toEqual(['structure']);
    const trendGroup = wrapper.get('[data-testid="presentation-layout-trend-group"]');
    expect(trendGroup.classes()).toContain('presentation-layout__component--trend-tabs');
    expect(trendGroup.attributes('data-trend-count')).toBe('2');
    expect(trendGroup.get('[data-testid="business-growth-widget"]').text()).toBe('业务增长曲线');
    expect(trendGroup.get('[data-testid="business-growth-widget"]').attributes('data-template')).toBe('branch-overview-v1');
    expect(trendGroup.find('[data-testid="series-widget"]').exists()).toBe(false);
    expect(wrapper.find('[data-layout-column="CENTER"]').findAll('[data-testid="presentation-layout-component"]')
      .map(node => node.attributes('data-component-id'))).toEqual(['map']);
    expect(trendGroup.attributes('data-layout-region')).toBe('CENTER');
    expect(wrapper.get('[data-testid="presentation-layout-main"]').classes()).toContain('presentation-layout__main--branch-overview');
    expect(wrapper.get('[data-layout-column="LEFT"]').classes()).toContain('presentation-layout__column--branch-overview');
    expect(wrapper.get('[data-layout-column="LEFT"]').findAll('.presentation-layout__component--trend')).toHaveLength(1);
    expect(wrapper.get('[data-layout-column="RIGHT"] .presentation-layout__component--ranking').attributes('data-visible-rows')).toBe('10');
    expect(wrapper.get('[data-testid="ranking-widget"]').attributes()).toMatchObject({
      'data-paginate': 'true', 'data-page-size': '10', 'data-page-interval': '5000', 'data-metric-carousel': 'false'
    });
  });

  it('将页头金额单位传递给业务增长曲线并随选择变化', async () => {
    const wrapper = mount(PresentationLayout, {
      props: {
        amountUnit: 'TEN_THOUSAND',
        presentation: {
          displaySchemaVersion: 1,
          template: 'branch-overview-v1',
          display: {
            components: [{
              componentId: 'trend', componentType: 'TREND', layoutRegion: 'CENTER', order: 0, visible: true,
              text: { titleMode: 'CUSTOM', title: '趋势' }, content: { series: [] }, dataRefs: []
            }]
          }
        },
        model: { blockResults: {} }
      },
      global: { stubs }
    });
    mounted.push(wrapper);

    const widget = () => wrapper.get('[data-testid="business-growth-widget"]');
    expect(widget().attributes('data-amount-unit')).toBe('TEN_THOUSAND');

    await wrapper.setProps({ amountUnit: 'HUNDRED_MILLION' });
    expect(widget().attributes('data-amount-unit')).toBe('HUNDRED_MILLION');
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
