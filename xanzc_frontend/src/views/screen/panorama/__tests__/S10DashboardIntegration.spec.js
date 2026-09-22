// @vitest-environment happy-dom
import { afterEach, describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';

import PanoramaDashboard from '../PanoramaDashboard.vue';
import CorporateDashboard from '../CorporateDashboard.vue';
import RetailDashboard from '../RetailDashboard.vue';
import BranchOperatingDashboard from '../BranchOperatingDashboard.vue';

const rankingWidgetStub = {
  name: 'InstitutionRankingWidget',
  props: ['model', 'title'],
  template: `
    <section data-testid="institution-ranking-widget-stub">
      <h2>{{ title }}</h2>
      <span data-testid="ranking-expected-count">{{ model?.expectedCount }}</span>
      <span data-testid="ranking-rankable-count">{{ model?.rankableCount }}</span>
      <span data-testid="ranking-metrics">{{ model?.metrics?.map(item => item.metricKey).join(',') }}</span>
      <span data-testid="ranking-org-codes">{{ model?.rows?.map(item => item.orgCode).join(',') }}</span>
    </section>
  `
};

const sourcePresentation = {
  displaySchemaVersion: 1,
  display: {
    components: [{
      componentId: 'ranking-main',
      componentType: 'RANKING',
      layoutRegion: 'RIGHT',
      order: 0,
      visible: true,
      content: {
        rankingMetrics: [
          { metricKey: 'deposit', field: 'deposit', label: '存款余额', unit: '亿元', direction: 'DESC' },
          { metricKey: 'increase', field: 'increase', label: '较上期净增', unit: '亿元', direction: 'ASC' }
        ]
      }
    }]
  }
};

const institutions = Array.from({ length: 12 }, (_, index) => ({
  orgCode: `ORG-${index + 1}`,
  orgName: `机构${index + 1}`,
  cityCode: '610100'
}));
const rankings = institutions.map((institution, index) => ({
  orgCode: institution.orgCode,
  name: institution.orgName,
  deposit: 100 - index,
  increase: index - 6,
  aum: 100 - index,
  rate: 80 + index,
  cityCode: institution.cityCode
}));
const model = {
  title: 'S10 集成测试',
  orgCode: 'ORG-1',
  orgName: '机构1',
  dataDate: '2026-09-22',
  scopeLabel: '授权机构',
  kpis: [],
  trend: [],
  composition: [],
  rankings,
  institutions,
  attention: [],
  targets: [],
  segments: [],
  marketing: [],
  projects: [],
  teams: [],
  gaps: {},
  sources: []
};

const stubs = {
  InstitutionRankingWidget: rankingWidgetStub,
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

function mountDashboard(component) {
  const source = component === BranchOperatingDashboard
    ? sourcePresentation
    : { displayPresentation: sourcePresentation };
  const wrapper = mount(component, {
    props: { model, sourcePresentation: source, loading: false, error: '', demo: true },
    global: { stubs },
    attachTo: document.body
  });
  mounted.push(wrapper);
  return wrapper;
}

describe('S10 RANKING 组件的 Dashboard 集成', () => {
  it.each([
    ['综合', PanoramaDashboard],
    ['对公', CorporateDashboard],
    ['零售', RetailDashboard],
    ['支行', BranchOperatingDashboard]
  ])('%s Dashboard 使用授权目录和返回排名渲染完整机构榜单，并隐藏旧榜单', (label, component) => {
    const wrapper = mountDashboard(component);

    const ranking = wrapper.get('[data-testid="institution-ranking-widget-stub"]');
    expect(ranking.get('[data-testid="ranking-expected-count"]').text()).toBe('12');
    expect(ranking.get('[data-testid="ranking-rankable-count"]').text()).toBe('12');
    expect(ranking.get('[data-testid="ranking-metrics"]').text()).toBe('deposit,increase');
    expect(ranking.get('[data-testid="ranking-org-codes"]').text()).toContain('ORG-12');
    expect(wrapper.findAll('[data-testid="institution-ranking-widget-stub"]')).toHaveLength(1);

    if (component === PanoramaDashboard) {
      expect(wrapper.text()).not.toContain('TOP 10');
      expect(wrapper.findAll('[data-testid="ranking-row"]')).toHaveLength(0);
    }
    if (component === CorporateDashboard) {
      expect(wrapper.findAll('[data-testid="corporate-ranking-row"]')).toHaveLength(0);
    }
    if (component === RetailDashboard) {
      expect(wrapper.findAll('[data-testid="retail-ranking-row"]')).toHaveLength(0);
    }
    expect(wrapper.text()).toContain(label === '支行' ? '单支行经营监测' : label === '综合' ? '经营监测' : `${label}经营监测`);
  });

  it.each([
    ['综合', PanoramaDashboard],
    ['对公', CorporateDashboard],
    ['零售', RetailDashboard],
    ['支行', BranchOperatingDashboard]
  ])('%s Dashboard 在没有可见 RANKING 组件时不切换旧协议', (_label, component) => {
    const source = component === BranchOperatingDashboard ? {} : { displayPresentation: {} };
    const wrapper = mount(component, {
      props: { model, sourcePresentation: source, loading: false, error: '', demo: true },
      global: { stubs },
      attachTo: document.body
    });
    mounted.push(wrapper);

    expect(wrapper.find('[data-testid="institution-ranking-widget-stub"]').exists()).toBe(false);
  });

  it.each([
    ['综合', PanoramaDashboard],
    ['对公', CorporateDashboard],
    ['零售', RetailDashboard],
    ['支行', BranchOperatingDashboard]
  ])('%s Dashboard 仅在 displaySchemaVersion=1 时启用 RANKING 新协议', (_label, component) => {
    const unsupportedPresentation = { ...sourcePresentation, displaySchemaVersion: 2 };
    const source = component === BranchOperatingDashboard
      ? unsupportedPresentation
      : { displayPresentation: unsupportedPresentation };
    const wrapper = mount(component, {
      props: { model, sourcePresentation: source, loading: false, error: '', demo: true },
      global: { stubs },
      attachTo: document.body
    });
    mounted.push(wrapper);

    expect(wrapper.find('[data-testid="institution-ranking-widget-stub"]').exists()).toBe(false);
  });
});
