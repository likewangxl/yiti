// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('vue-echarts', () => ({
  default: { props: { option: { type: Object, default: () => ({}) } }, template: '<div data-testid="chart-stub" />' }
}));

import PanoramaDashboard from '../PanoramaDashboard.vue';
import CorporateDashboard from '../CorporateDashboard.vue';
import RetailDashboard from '../RetailDashboard.vue';
import BranchOperatingDashboard from '../BranchOperatingDashboard.vue';

const componentConfig = [
  { componentId: 'detail', componentType: 'DETAIL_TABLE', layoutRegion: 'BOTTOM', order: 0, visible: true, content: { columns: [{ columnKey: 'name', field: 'name', label: '机构', unit: 'AUTO', visible: true }] }, dataRefs: [{ blockId: 7, role: 'PRIMARY', unit: 'COUNT' }] },
  { componentId: 'ranking', componentType: 'RANKING', layoutRegion: 'RIGHT', order: 0, visible: true, content: { rankingMetrics: [{ metricKey: 'deposit', field: 'deposit', label: '存款', unit: 'HUNDRED_MILLION', direction: 'DESC' }] }, dataRefs: [{ blockId: 6, role: 'PRIMARY', unit: 'HUNDRED_MILLION' }] },
  { componentId: 'map', componentType: 'MAP', layoutRegion: 'CENTER', order: 0, visible: true, content: { mainField: 'deposit' }, dataRefs: [{ blockId: 5, role: 'PRIMARY', unit: 'HUNDRED_MILLION' }] },
  { componentId: 'trend', componentType: 'TREND', layoutRegion: 'LEFT', order: 1, visible: true, content: { series: [{ seriesKey: 'deposit', field: 'deposit', label: '存款', unit: 'HUNDRED_MILLION' }] }, dataRefs: [{ blockId: 4, role: 'PRIMARY', unit: 'HUNDRED_MILLION' }] },
  { componentId: 'metric', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 0, visible: true, text: { titleMode: 'CUSTOM', title: '存款余额' }, content: { mainField: 'value' }, dataRefs: [{ blockId: 1, role: 'PRIMARY', metricCode: 'deposit', metricName: '存款余额', unit: 'HUNDRED_MILLION' }] },
  { componentId: 'completion', componentType: 'COMPLETION', layoutRegion: 'HEADER', order: 1, visible: true, text: { titleMode: 'CUSTOM', title: '对公完成率' }, content: { mainField: 'value' }, dataRefs: [{ blockId: 2, role: 'PRIMARY', metricCode: 'corpRate', metricName: '对公完成率', unit: 'PERCENT' }] },
  { componentId: 'structure', componentType: 'COMPOSITION_TABS', layoutRegion: 'CENTER', order: 1, visible: true, content: { tabs: [{ tabKey: 'deposit', label: '存款', corporateField: 'corporate', retailField: 'retail', totalField: 'total', unit: 'HUNDRED_MILLION' }] }, dataRefs: [{ blockId: 3, role: 'PRIMARY', unit: 'HUNDRED_MILLION' }] }
];

const displayPresentation = { displaySchemaVersion: 1, display: { components: componentConfig } };

const model = {
  title: '配置化测试大屏', dataDate: '2026-09-22', scopeLabel: '授权范围', sourceLabel: '测试数据',
  kpis: [{ key: 'deposit', label: '存款余额', value: 1286.42, unit: '亿元' }],
  trend: [{ date: '2026-09', deposit: 1286.42 }],
  composition: [{ name: '对公', value: 60, unit: '亿元' }],
  blockResults: { 3: { total: 100, corporate: 60, retail: 40, unit: '亿元' }, 4: [{ date: '2026-09', deposit: 1286.42 }], 7: [{ name: '机构一' }] },
  institutions: [{ orgCode: 'ORG-1', orgName: '机构一', cityCode: '610100', located: true, lng: 108.94, lat: 34.34 }],
  rankings: [{ orgCode: 'ORG-1', name: '机构一', deposit: 100 }],
  targets: [{ name: '旧目标', actual: 1, target: 2 }],
  attention: [{ label: '旧观察', count: 1 }],
  marketing: [{ key: 'PENDING', count: 1 }],
  projects: [{ name: '旧项目' }],
  teams: [{ name: '旧团队', rate: 80 }]
};

const stubs = {
  MetricDisplayWidgets: { props: ['components'], template: '<section data-testid="stub-metrics"><span v-for="item in components" :key="item.componentId">{{ item.componentId }}|{{ item.text }}</span></section>' },
  SeriesTableWidgets: { props: ['components'], template: '<section data-testid="stub-series"><span v-for="item in components" :key="item.componentId">{{ item.componentId }}</span></section>' },
  CompositionTabsWidget: { props: ['model'], template: '<section data-testid="stub-structure">{{ model.components?.[0]?.componentId }}</section>' },
  InstitutionRankingWidget: { props: ['model', 'title'], template: '<section data-testid="stub-ranking">{{ title }}</section>' },
  PresentationMapWidget: { props: ['presentation'], template: '<section data-testid="stub-map">地图</section>' },
  PanoramaMap: true,
  CityPanorama: true,
  PanoramaInstitutionDirectory: true,
  PanoramaTrend: true,
  CorporateTrend: true,
  RetailTrend: true,
  RetailAttentionDetails: true,
  CompositionBreakdown: true,
  CompletionWaterGauge: true,
  VChart: true,
  'v-chart': true
};

const mounted = [];
afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()));

function mountConfigured(component) {
  const sourcePresentation = component === BranchOperatingDashboard
    ? displayPresentation
    : { displayPresentation };
  const wrapper = mount(component, {
    props: { model, sourcePresentation, loading: false, error: '', demo: false },
    global: { stubs },
    attachTo: document.body
  });
  mounted.push(wrapper);
  return wrapper;
}

describe('S13 displaySchemaVersion=1 整页配置化', () => {
  it.each([
    ['综合', PanoramaDashboard],
    ['对公', CorporateDashboard],
    ['零售', RetailDashboard],
    ['支行', BranchOperatingDashboard]
  ])('%s 仅渲染七类可见组件，不叠加固定目标/观察/营销/项目/团队模块', (_label, component) => {
    const wrapper = mountConfigured(component);
    expect(wrapper.find('[data-testid="presentation-layout"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-testid="presentation-layout-component"]').map(item => item.attributes('data-component-id')))
      .toEqual(['metric', 'completion', 'trend', 'map', 'structure', 'ranking', 'detail']);
    expect(wrapper.find('[data-testid="stub-metrics"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stub-series"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stub-structure"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stub-ranking"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="stub-map"]').exists()).toBe(true);
    for (const selector of [
      '.panorama-kpi-grid', '.corporate-kpi-grid', '.retail-kpi-grid', '.branch-operating-kpis',
      '.panorama-workspace', '.corporate-main-grid', '.retail-main-grid', '.branch-operating-main-grid',
      '[data-testid="branch-operating-projects"]', '[data-testid="branch-operating-marketing"]', '[data-testid="branch-operating-teams"]'
    ]) expect(wrapper.find(selector).exists()).toBe(false);
  });

  it.each([
    ['综合', PanoramaDashboard],
    ['对公', CorporateDashboard],
    ['零售', RetailDashboard],
    ['支行', BranchOperatingDashboard]
  ])('%s 未声明版本1时完整回归旧 Dashboard', (_label, component) => {
    const sourcePresentation = component === BranchOperatingDashboard ? {} : { displayPresentation: {} };
    const wrapper = mount(component, { props: { model, sourcePresentation, loading: false, error: '', demo: false }, global: { stubs } });
    mounted.push(wrapper);
    expect(wrapper.find('[data-testid="presentation-layout"]').exists()).toBe(false);
    expect(wrapper.find(['.panorama-kpi-grid', '.corporate-kpi-grid', '.retail-kpi-grid', '.branch-operating-kpis'].find(selector => wrapper.find(selector).exists())).exists()).toBe(true);
  });
});
