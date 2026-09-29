// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import PresentationMapWidget from '../PresentationMapWidget.vue';

const mapComponent = {
  componentId: 'map-main',
  componentType: 'MAP',
  visible: true,
  text: { titleMode: 'AUTO', title: '' },
  format: { displayUnit: 'HUNDRED_MILLION', decimals: 2, emptyText: '暂无数据' },
  content: { mainField: 'deposit', subFields: [], series: [], columns: [], tabs: [], rankingMetrics: [] },
  dataRefs: [{ blockId: 1, role: 'PRIMARY', metricCode: 'M_DEPOSIT', metricName: '存款余额', unit: 'HUNDRED_MILLION', dimension: 'ORG' }]
};

const presentation = {
  screenCode: 'SCR_PROVINCE',
  displaySchemaVersion: 1,
  display: { components: [mapComponent] }
};

const geoJson = {
  type: 'FeatureCollection',
  features: [
    { type: 'Feature', properties: { adcode: '610100', name: '西安市' }, geometry: { type: 'Polygon', coordinates: [[[108, 34], [109, 34], [109, 35], [108, 35], [108, 34]]] } },
    { type: 'Feature', properties: { adcode: '610600', name: '延安市' }, geometry: { type: 'Polygon', coordinates: [[[109, 35], [110, 35], [110, 36], [109, 36], [109, 35]]] } }
  ]
};

const model = {
  dataDate: '2026-09-22',
  citySummaries: {
    '610100': { kpis: [{ key: 'deposit', value: 100, unit: '亿元' }] },
    // A business value without an institution must not color the institution map.
    '610600': { kpis: [{ key: 'deposit', value: 999, unit: '亿元' }] }
  },
  institutions: [
    { orgCode: 'A', orgName: '甲机构', cityCode: '610100', located: true, lng: 108.4, lat: 34.4, coordSys: 'GCJ02' },
    { orgCode: 'B', orgName: '待定位机构', cityCode: '610100', located: false }
  ],
  rankings: [{ orgCode: 'A', cityCode: '610100', deposit: 20 }]
};

const mapStub = {
  name: 'PanoramaMap',
  props: [
    'geoJson', 'points', 'metricLabel', 'metricValues', 'metricNumericValues', 'metricColors', 'regionStates',
    'cityDetails', 'cityDetailMode', 'colorByCity', 'labelLayout', 'pointLabelLayout', 'showRegionMetrics',
    'showProvincePointLabels', 'mode', 'selectedRegionCode', 'selectedOrgCode', 'showProvincePoints', 'viewFit'
  ],
  emits: ['region-select', 'branch-select'],
  template: '<div data-testid="panorama-map-stub"><button data-action="select-city" @click="$emit(\'region-select\', { code: \'610100\', name: \'西安市\' })">城市</button><button data-action="select-branch" @click="$emit(\'branch-select\', \'A\')">机构</button></div>'
};

function mountWidget(overrides = {}) {
  return mount(PresentationMapWidget, {
    props: { presentation, model, geoJson, mode: 'province', metricKey: 'deposit', ...overrides },
    global: { stubs: { PanoramaMap: mapStub } }
  });
}

describe('PresentationMapWidget legacy branch institution view', () => {
  it('旧 screenCode 与 branch 模板只显示机构身份、完整目录和占用状态', () => {
    const wrapper = mountWidget();
    const map = wrapper.getComponent(mapStub);

    expect(wrapper.get('.presentation-map-widget__kicker').text()).toBe('机构视图');
    expect(wrapper.get('h2').text()).toBe('经营机构分布');
    expect(wrapper.get('[data-testid="presentation-map-institution-count"]').text()).toContain('2 家机构');
    expect(wrapper.find('[data-testid="presentation-map-metric"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="presentation-map-data-date"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="presentation-map-legend-high"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="presentation-map-legend-mid"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="presentation-map-legend-low"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="presentation-map-legend-missing"]').exists()).toBe(false);
    expect(map.props('metricLabel')).toBe('');
    expect(map.props('metricValues')).toEqual({});
    expect(map.props('metricNumericValues')).toEqual({});
    expect(map.props('showRegionMetrics')).toBe(false);
    expect(map.props('showProvincePoints')).toBe(false);
    expect(map.props('showProvincePointLabels')).toBe(false);
    expect(map.props('cityDetailMode')).toBe('institutions');
    expect(map.props('colorByCity')).toBe(true);
    expect(map.props('regionStates')).toMatchObject({ '610100': 'HAS_INSTITUTION', '610600': 'NO_INSTITUTION' });
    expect(map.props('metricColors')['610100']).toBe('#3d78ba');
    expect(map.props('metricColors')['610600']).toBe('#65738a');
    expect(wrapper.get('[data-testid="presentation-map-legend-has-institution"]').text()).toBe('有经营机构');
    expect(wrapper.get('[data-testid="presentation-map-legend-no-institution"]').text()).toBe('无经营机构');
    expect(map.props('cityDetails')['610100'].institutions.map(item => item.orgName)).toEqual(['甲机构', '待定位机构']);
    wrapper.unmount();

    const templateWrapper = mountWidget({ presentation: { ...presentation, screenCode: '', template: 'branch-overview-v1' } });
    expect(templateWrapper.getComponent(mapStub).props('cityDetailMode')).toBe('institutions');
    expect(templateWrapper.get('.presentation-map-widget__kicker').text()).toBe('机构视图');
    templateWrapper.unmount();
  });

  it('v2、零售、对公和市级地图继续走原业务指标/上下文路径', async () => {
    const variants = [
      { presentation: { ...presentation, screenCode: 'SCR_PROVINCE_MAP_V2' }, mode: 'province', expectedPoints: true },
      { presentation: { ...presentation, screenCode: 'SCR_RETAIL_OVERVIEW' }, mode: 'province', expectedPoints: true },
      { presentation: { ...presentation, screenCode: 'SCR_CORP_OVERVIEW' }, mode: 'province', expectedPoints: true },
      { presentation, mode: 'city', cityCode: '610100', expectedPoints: false }
    ];
    for (const variant of variants) {
      const wrapper = mountWidget(variant);
      const map = wrapper.getComponent(mapStub);
      expect(map.props('metricLabel')).toBe('存款余额');
      expect(map.props('metricValues')).toEqual(expect.objectContaining({ '610100': '100.00亿元' }));
      expect(map.props('showProvincePoints')).toBe(variant.expectedPoints);
      expect(wrapper.get('.presentation-map-widget__kicker').text()).toBe('地图视图');
      await wrapper.get('[data-action="select-city"]').trigger('click');
      await wrapper.get('[data-action="select-branch"]').trigger('click');
      expect(wrapper.emitted('region-select')).toContainEqual([{ code: '610100', name: '西安市' }]);
      expect(wrapper.emitted('branch-select')).toContainEqual(['A']);
      expect(wrapper.emitted('map-context')).toEqual(expect.arrayContaining([
        [expect.objectContaining({ level: 'CITY', cityCode: '610100' })],
        [expect.objectContaining({ level: 'INSTITUTION', orgCode: 'A', cityCode: '610100' })]
      ]));
      wrapper.unmount();
    }
  });
});
