// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import PresentationMapWidget from '../PresentationMapWidget.vue';

const presentation = {
  displaySchemaVersion: 1,
  display: {
    components: [{
      componentId: 'map-main', componentType: 'MAP', layoutRegion: 'CENTER', order: 1, visible: true,
      text: { titleMode: 'AUTO', title: '' },
      format: { displayUnit: 'HUNDRED_MILLION', decimals: 2, emptyText: '暂无数据' },
      content: { mainField: 'deposit', subFields: [], series: [], columns: [], tabs: [], rankingMetrics: [] },
      interaction: { action: 'OPEN_CITY' },
      dataRefs: [{ blockId: 1, role: 'PRIMARY', metricCode: 'M_DEPOSIT', metricName: '存款余额', unit: 'HUNDRED_MILLION', dimension: 'ORG' }]
    }]
  }
};

const geoJson = {
  type: 'FeatureCollection',
  features: [{ type: 'Feature', properties: { adcode: '610100', name: '西安市' }, geometry: { type: 'Polygon', coordinates: [[[108, 34], [109, 34], [109, 35], [108, 35], [108, 34]]] } }]
};

const model = {
  dataDate: '2026-09-22',
  citySummaries: { '610100': { kpis: [{ key: 'deposit', value: 100, unit: '亿元' }] } },
  institutions: [
    { orgCode: 'A', orgName: '甲机构', cityCode: '610100', located: true, lng: 108.4, lat: 34.4, coordSys: 'GCJ02' },
    { orgCode: 'B', orgName: '待定位机构', cityCode: '610100', located: false }
  ],
  rankings: [{ orgCode: 'A', cityCode: '610100', deposit: 20 }]
};

const mapStub = {
  props: ['geoJson', 'points', 'metricLabel', 'metricValues', 'metricNumericValues', 'mode', 'selectedRegionCode', 'selectedOrgCode'],
  template: '<div data-testid="panorama-map-stub"><button data-city="610100" @click="$emit(\'region-select\', { code: \'610100\', name: \'西安市\' })">城市</button><button data-org="A" @click="$emit(\'branch-select\', \'A\')">机构</button></div>'
};

describe('PresentationMapWidget', () => {
  it('把 MAP 配置适配到既有 PanoramaMap，并展示当前指标/日期/图例', () => {
    const wrapper = mount(PresentationMapWidget, {
      props: { presentation, model, geoJson, mode: 'province', metricKey: 'deposit' },
      global: { stubs: { PanoramaMap: mapStub } }
    });
    expect(wrapper.get('[data-testid="presentation-map-widget"]').text()).toContain('存款余额');
    expect(wrapper.get('[data-testid="presentation-map-data-date"]').text()).toContain('2026-09-22');
    expect(wrapper.get('[data-testid="presentation-map-legend-missing"]').text()).toContain('暂无数据');
    expect(wrapper.getComponent(mapStub).props('metricValues')).toMatchObject({ '610100': '100.00亿元' });
  });

  it('无坐标机构由旁侧可访问列表进入，并向上发固定上下文事件', async () => {
    const wrapper = mount(PresentationMapWidget, {
      props: { presentation, model, geoJson, mode: 'city', cityCode: '610100', metricKey: 'deposit' },
      global: { stubs: { PanoramaMap: mapStub } }
    });
    expect(wrapper.find('[data-testid="map-missing-coordinates"]').text()).toContain('待定位机构');
    await wrapper.get('[data-org-code="B"]').trigger('click');
    expect(wrapper.emitted('branch-select')).toContainEqual(['B']);
    expect(wrapper.emitted('map-context')).toContainEqual([expect.objectContaining({ level: 'INSTITUTION', orgCode: 'B', cityCode: '610100' })]);
    await wrapper.get('[data-testid="panorama-map-stub"] [data-city="610100"]').trigger('click');
    expect(wrapper.emitted('region-select')).toContainEqual([{ code: '610100', name: '西安市' }]);
    expect(wrapper.emitted('map-context')).toContainEqual([expect.objectContaining({ level: 'CITY', cityCode: '610100' })]);
  });
});
