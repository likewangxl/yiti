// @vitest-environment happy-dom
import { afterEach, describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import PanoramaDashboard from '../PanoramaDashboard.vue';
import CorporateDashboard from '../CorporateDashboard.vue';
import RetailDashboard from '../RetailDashboard.vue';

const sourcePresentation = {
  displaySchemaVersion: 1,
  display: {
    components: [
      {
        componentId: 'ranking-main', componentType: 'RANKING', layoutRegion: 'RIGHT', order: 0, visible: true,
        content: { rankingMetrics: [
          { metricKey: 'deposit', field: 'deposit', label: '存款余额', unit: 'HUNDRED_MILLION', direction: 'DESC' },
          { metricKey: 'increase', field: 'increase', label: '存款净增', unit: 'HUNDRED_MILLION', direction: 'ASC' }
        ] }
      },
      {
        componentId: 'map-main', componentType: 'MAP', layoutRegion: 'CENTER', order: 1, visible: true,
        text: { titleMode: 'AUTO', title: '' },
        format: { displayUnit: 'HUNDRED_MILLION', decimals: 2 },
        content: { mainField: 'deposit', subFields: [], series: [], columns: [], tabs: [], rankingMetrics: [] },
        interaction: { action: 'OPEN_CITY' },
        dataRefs: [{ blockId: 2, role: 'PRIMARY', metricCode: 'M_DEPOSIT', metricName: '存款余额', unit: 'HUNDRED_MILLION', dimension: 'ORG' }]
      }
    ]
  }
};

const model = {
  title: 'S12 map', dataDate: '2026-09-22', scopeLabel: '授权范围', kpis: [], trend: [], composition: [], attention: [], targets: [], segments: [],
  citySummaries: { '610100': { kpis: [{ key: 'deposit', value: 100, unit: '亿元' }] } },
  institutions: [{ orgCode: 'A', orgName: '甲机构', cityCode: '610100' }],
  rankings: [{ orgCode: 'A', name: '甲机构', cityCode: '610100', deposit: 100, increase: 2 }]
};

const rankingStub = {
  props: ['model', 'title'],
  template: '<section data-testid="ranking-stub"><button data-testid="ranking-select-increase" @click="$emit(\'metric-change\', { metricKey: \'increase\' })">切换净增</button><span data-testid="ranking-active-key">{{ model?.activeMetricKey }}</span></section>'
};
const mapStub = {
  props: ['presentation', 'model', 'metricKey', 'mode'],
  template: '<section data-testid="presentation-map-stub"><span data-testid="map-active-key">{{ metricKey }}</span><span>{{ mode }}</span></section>'
};

const stubs = {
  InstitutionRankingWidget: rankingStub,
  PresentationMapWidget: mapStub,
  MetricDisplayWidgets: true,
  SeriesTableWidgets: true,
  CompositionTabsWidget: true,
  CompositionBreakdown: true,
  PanoramaMap: true,
  CityPanorama: true,
  PanoramaInstitutionDirectory: true,
  PanoramaTrend: true,
  CorporateTrend: true,
  RetailTrend: true,
  RetailAttentionDetails: true,
  CompletionWaterGauge: true,
  VChart: true,
  'v-chart': true
};

const mounted = [];
afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()));

describe('S12 MAP 与 S10 RANKING 指标同步', () => {
  it.each([
    ['综合', PanoramaDashboard],
    ['对公', CorporateDashboard],
    ['零售', RetailDashboard]
  ])('%s Dashboard 仅在可见 MAP 配置启用地图，并随排名 metric-change 同步指标', async (_label, component) => {
    const wrapper = mount(component, {
      props: { model, sourcePresentation: { displayPresentation: sourcePresentation }, loading: false, error: '', demo: true },
      global: { stubs }, attachTo: document.body
    });
    mounted.push(wrapper);
    expect(wrapper.get('[data-testid="presentation-map-stub"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="map-active-key"]').text()).toBe('deposit');
    await wrapper.get('[data-testid="ranking-select-increase"]').trigger('click');
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-testid="map-active-key"]').text()).toBe('increase');
  });
});
