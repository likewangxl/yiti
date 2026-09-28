// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const mapStub = vi.hoisted(() => ({
  name: 'PanoramaMap',
  props: ['pointLabelLayout', 'labelLayout', 'mode', 'showProvincePointLabels'],
  template: '<div data-testid="panorama-map-stub" :data-point-label-layout="pointLabelLayout" :data-label-layout="labelLayout" :data-mode="mode" :data-show-province-point-labels="String(showProvincePointLabels)" />',
  emits: ['branch-select', 'region-select']
}));

vi.mock('../PanoramaMap.vue', () => ({ default: mapStub }));
vi.mock('../PanoramaTrend.vue', () => ({ default: { template: '<div data-testid="panorama-trend-stub" />' } }));
vi.mock('vue-echarts', () => ({
  default: { props: { option: { type: Object, default: () => ({}) } }, template: '<div data-testid="chart-option" />' }
}));

import CityPanorama from '../CityPanorama.vue';
import PresentationMapWidget from '../../presentation/map/PresentationMapWidget.vue';

const mapComponent = {
  componentId: 'map-main',
  componentType: 'MAP',
  layoutRegion: 'CENTER',
  order: 1,
  visible: true,
  text: { titleMode: 'AUTO', title: '' },
  format: { displayUnit: 'HUNDRED_MILLION', decimals: 2, emptyText: '暂无数据' },
  content: { mainField: 'deposit', subFields: [], series: [], columns: [], tabs: [], rankingMetrics: [] },
  interaction: { action: 'OPEN_CITY' },
  dataRefs: [{ blockId: 1, role: 'PRIMARY', metricCode: 'M_DEPOSIT', metricName: '存款余额', unit: 'HUNDRED_MILLION', dimension: 'ORG' }]
};

const model = {
  dataDate: '2026-09-22',
  citySummaries: { '610100': { kpis: [] } },
  institutions: [{
    orgCode: 'ORG-1',
    orgName: '一号支行',
    cityCode: '610100',
    located: true,
    lng: 108.9,
    lat: 34.2,
    coordSys: 'GCJ02',
    metrics: { deposit: 1, loan: 2, customers: 3, rate: 90 },
    trend: [],
    attention: []
  }],
  rankings: [{ orgCode: 'ORG-1', cityCode: '610100', deposit: 1 }]
};

const geoJson = { type: 'FeatureCollection', features: [] };

function presentation(overrides = {}) {
  return {
    displaySchemaVersion: 1,
    display: { components: [mapComponent] },
    ...overrides
  };
}

function mountPresentation(sourcePresentation, mode = 'city') {
  return mount(PresentationMapWidget, {
    props: {
      presentation: sourcePresentation,
      model,
      geoJson,
      mode,
      cityCode: mode === 'city' ? '610100' : '',
      metricKey: 'deposit'
    }
  });
}

describe('地市支行名称飞线标注', () => {
  it.each([
    ['旧 SCR_PROVINCE 屏', presentation({ screenCode: 'SCR_PROVINCE' })],
    ['branch-overview-v1 模板', presentation({ screenCode: undefined, template: 'branch-overview-v1' })],
    ['新版 SCR_PROVINCE_MAP_V2 屏', presentation({ screenCode: 'SCR_PROVINCE_MAP_V2' })]
  ])('%s进入 city 模式时使用 callout', (label, sourcePresentation) => {
    const wrapper = mountPresentation(sourcePresentation);
    expect(wrapper.get('[data-testid="panorama-map-stub"]').attributes('data-point-label-layout')).toBe('callout');
    wrapper.unmount();
  });

  it('province 模式仍使用 inline 地市标注', () => {
    const wrapper = mountPresentation(presentation({ screenCode: 'SCR_PROVINCE_MAP_V2' }), 'province');
    expect(wrapper.get('[data-testid="panorama-map-stub"]').attributes('data-point-label-layout')).toBe('inline');
    wrapper.unmount();
  });

  it('CityPanorama 没有可见 presentation 时 fallback 地图也使用 callout', () => {
    const wrapper = mount(CityPanorama, {
      props: { model, cityCode: '610100', cityName: '西安市' }
    });
    expect(wrapper.get('[data-testid="panorama-map-stub"]').attributes('data-point-label-layout')).toBe('callout');
    wrapper.unmount();
  });
});
