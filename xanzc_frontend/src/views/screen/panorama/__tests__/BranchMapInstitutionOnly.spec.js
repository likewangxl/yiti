// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import PanoramaDashboard from '../PanoramaDashboard.vue';
import PanoramaMap from '../PanoramaMap.vue';

vi.mock('vue-echarts', () => ({
  default: { props: { option: { type: Object, default: () => ({}) } }, template: '<div />' }
}));

const geoJson = {
  type: 'FeatureCollection',
  features: [{
    type: 'Feature',
    properties: { adcode: '610100', name: '西安市', center: [108.5, 34.5] },
    geometry: { type: 'Polygon', coordinates: [[[108, 34], [109, 34], [109, 35], [108, 35], [108, 34]]] }
  }]
};

const model = {
  title: '分行经营总览',
  scopeLabel: '全辖机构',
  dataDate: '2026-09-22',
  kpis: [],
  trend: [],
  composition: [],
  rankings: [{ orgCode: 'A', cityCode: '610100', deposit: 100 }],
  attention: [],
  institutions: [
    { orgCode: 'A', orgName: '已定位机构', cityCode: '610100', located: true, lng: 108.4, lat: 34.4 },
    { orgCode: 'B', orgName: '待定位机构', cityCode: '610100', located: false }
  ],
  issues: []
};

const mapStub = {
  name: 'PanoramaMap',
  props: [
    'metricLabel', 'metricValues', 'metricNumericValues', 'metricColors', 'regionStates', 'cityDetails',
    'cityDetailMode', 'showRegionMetrics', 'showProvincePoints', 'showProvincePointLabels',
    'labelLayout', 'mode', 'selectedOrgCode'
  ],
  emits: ['region-select', 'branch-select'],
  template: '<div data-testid="panorama-map-stub" />'
};

describe('PanoramaDashboard legacy branch map institution-only display', () => {
  it('legacy PanoramaMap 清空业务地图数据并传递机构状态与机构明细', () => {
    const wrapper = mount(PanoramaDashboard, {
      props: { model, loading: false, error: '', demo: false },
      global: { stubs: { PanoramaMap: mapStub } }
    });
    const map = wrapper.getComponent(mapStub);
    expect(map.props('metricLabel')).toBe('');
    expect(map.props('metricValues')).toEqual({});
    expect(map.props('metricNumericValues')).toEqual({});
    expect(map.props('showRegionMetrics')).toBe(false);
    expect(map.props('showProvincePoints')).toBe(false);
    expect(map.props('showProvincePointLabels')).toBe(false);
    expect(map.props('cityDetailMode')).toBe('institutions');
    expect(map.props('regionStates')['610100']).toBe('HAS_INSTITUTION');
    expect(map.props('metricColors')['610100']).toBe('#3d78ba');
    expect(map.props('cityDetails')['610100'].institutions.map(item => item.orgName)).toEqual(['已定位机构', '待定位机构']);
    wrapper.unmount();
  });
});

describe('PanoramaMap institution hover detail', () => {
  it('悬停只列出完整机构姓名，未定位有 code 可导航且不触发城市选择', async () => {
    const wrapper = mount(PanoramaMap, {
      props: {
        geoJson,
        labelLayout: 'callout',
        cityDetailMode: 'institutions',
        cityDetails: {
          '610100': {
            institutionCount: 3,
            institutions: [
              { orgCode: 'LOCATED-1', orgName: '已定位机构', located: true },
              { orgCode: 'UNLOCATED-1', orgName: '待定位机构', located: false },
              { orgName: '无编码机构', located: false }
            ]
          }
        }
      },
      global: { stubs: { Teleport: true } }
    });

    await wrapper.get('button[data-city-code="610100"]').trigger('pointerenter');
    const nameRows = wrapper.findAll('[data-testid="map-city-institution-name"]');
    const names = nameRows.map(row => row.get('button'));
    expect(names).toHaveLength(3);
    expect(names.map(item => item.text())).toEqual(['已定位机构', '待定位机构', '无编码机构']);

    await names[1].trigger('click');
    await names[2].trigger('click');
    expect(wrapper.emitted('branch-select')).toEqual([['UNLOCATED-1']]);
    expect(wrapper.emitted('region-select')).toBeUndefined();
    expect(wrapper.text()).not.toContain('存款');
    expect(wrapper.text()).not.toContain('数据日期');
    expect(wrapper.text()).not.toContain('已定位数');
    wrapper.unmount();
  });

  it('空机构城市只显示空态，不出现业务指标字段', async () => {
    const wrapper = mount(PanoramaMap, {
      props: {
        geoJson,
        labelLayout: 'callout',
        cityDetailMode: 'institutions',
        cityDetails: { '610100': { institutionCount: 0, institutions: [] } }
      },
      global: { stubs: { Teleport: true } }
    });
    await wrapper.get('button[data-city-code="610100"]').trigger('pointerenter');
    expect(wrapper.get('[data-testid="map-city-institution-empty"]').text()).toBe('无经营机构');
    expect(wrapper.text()).not.toContain('存款');
    expect(wrapper.text()).not.toContain('数据日期');
    wrapper.unmount();
  });
});
