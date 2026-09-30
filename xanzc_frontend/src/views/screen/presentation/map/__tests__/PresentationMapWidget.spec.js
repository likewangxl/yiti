// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import PresentationMapWidget from '../PresentationMapWidget.vue';

const presentation = {
  screenCode: 'SCR_PROVINCE_MAP_V2',
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
  props: ['geoJson', 'points', 'metricLabel', 'metricValues', 'metricNumericValues', 'metricColors', 'regionStates', 'cityDetails', 'cityDetailMode', 'colorByCity', 'labelLayout', 'pointLabelLayout', 'showRegionMetrics', 'showProvincePointLabels', 'mode', 'selectedRegionCode', 'selectedOrgCode', 'showProvincePoints', 'viewFit'],
  template: '<div data-testid="panorama-map-stub"><button data-city="610100" @click="$emit(\'region-select\', { code: \'610100\', name: \'西安市\' })">城市</button><button data-org="A" @click="$emit(\'branch-select\', \'A\')">机构</button></div>'
};

describe('PresentationMapWidget', () => {
  it('地图指标展示名称清理临时前缀，原始字段仍用于取数', () => {
    const configured = structuredClone(presentation);
    configured.display.components[0].dataRefs[0].metricName = '测试_直营存款余额';
    const wrapper = mount(PresentationMapWidget, {
      props: { presentation: configured, model, geoJson, mode: 'province', metricKey: 'deposit' },
      global: { stubs: { PanoramaMap: mapStub } }
    });
    expect(wrapper.get('[data-testid="presentation-map-metric"]').text()).toBe('存款余额');
    expect(wrapper.getComponent(mapStub).props('metricLabel')).toBe('存款余额');
    expect(configured.display.components[0].dataRefs[0].metricName).toBe('测试_直营存款余额');
  });

  it('把 MAP 配置适配到既有 PanoramaMap，并展示当前指标/日期/图例', () => {
    const wrapper = mount(PresentationMapWidget, {
      props: { presentation, model, geoJson, mode: 'province', metricKey: 'deposit' },
      global: { stubs: { PanoramaMap: mapStub } }
    });
    expect(wrapper.get('[data-testid="presentation-map-widget"]').text()).toContain('存款余额');
    expect(wrapper.get('[data-testid="presentation-map-data-date"]').text()).toContain('2026-09-22');
    expect(wrapper.get('[data-testid="presentation-map-legend-missing"]').text()).toContain('暂无数据');
    expect(wrapper.getComponent(mapStub).props('metricValues')).toMatchObject({ '610100': '100.00亿元' });
    expect(wrapper.getComponent(mapStub).props('showProvincePoints')).toBe(true);
    expect(wrapper.getComponent(mapStub).props('metricValues')).toMatchObject({ A: '20.00亿元' });
  });

  it('将无经营机构状态传给地图，并在图例中解释专用展示', () => {
    const mapGeoJson = { ...geoJson, features: [...geoJson.features, { ...geoJson.features[0], properties: { adcode: '610600', name: '延安市' } }] };
    const wrapper = mount(PresentationMapWidget, {
      props: { presentation, model, geoJson: mapGeoJson, mode: 'province', metricKey: 'deposit' },
      global: { stubs: { PanoramaMap: mapStub } }
    });
    expect(wrapper.getComponent(mapStub).props('regionStates')['610600']).toBe('NO_INSTITUTION');
    expect(wrapper.getComponent(mapStub).props('metricValues')['610600']).toBe('无经营机构');
    expect(wrapper.get('[data-testid="presentation-map-legend-no-institution"]').text()).toContain('无经营机构');
    expect(wrapper.getComponent(mapStub).props('labelLayout')).toBe('inline');
    expect(wrapper.getComponent(mapStub).props('showRegionMetrics')).toBe(false);
    expect(wrapper.getComponent(mapStub).props('showProvincePointLabels')).toBe(false);
    expect(wrapper.getComponent(mapStub).props('pointLabelLayout')).toBe('inline');
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
    expect(wrapper.getComponent(mapStub).props('showProvincePoints')).toBe(false);
    expect(wrapper.getComponent(mapStub).props('pointLabelLayout')).toBe('callout');
    expect(wrapper.getComponent(mapStub).props('showRegionMetrics')).toBe(false);
  });

  it('旧省级屏编码切换为机构视图，隐藏业务指标与机构点但保留机构占用状态', () => {
    const oldPresentation = { ...presentation, screenCode: 'SCR_PROVINCE' };
    const mapGeoJson = { ...geoJson, features: [...geoJson.features, { ...geoJson.features[0], properties: { adcode: '610600', name: '延安市' } }] };
    const province = mount(PresentationMapWidget, {
      props: { presentation: oldPresentation, model, geoJson: mapGeoJson, mode: 'province', metricKey: 'deposit' },
      global: { stubs: { PanoramaMap: mapStub } }
    });
    const oldMap = province.getComponent(mapStub);
    expect(province.get('.presentation-map-widget__kicker').text()).toBe('机构视图');
    expect(province.get('h2').text()).toBe('经营机构分布');
    expect(province.find('[data-testid="presentation-map-metric"]').exists()).toBe(false);
    expect(province.find('[data-testid="presentation-map-data-date"]').exists()).toBe(false);
    expect(province.get('[data-testid="presentation-map-institution-count"]').text()).toContain('2 家机构');
    expect(province.find('[data-testid="presentation-map-legend-high"]').exists()).toBe(false);
    expect(province.find('[data-testid="presentation-map-legend-mid"]').exists()).toBe(false);
    expect(province.find('[data-testid="presentation-map-legend-low"]').exists()).toBe(false);
    expect(province.find('[data-testid="presentation-map-legend-missing"]').exists()).toBe(false);
    expect(oldMap.props('labelLayout')).toBe('callout');
    expect(oldMap.props('pointLabelLayout')).toBe('inline');
    expect(oldMap.props('metricLabel')).toBe('');
    expect(oldMap.props('metricValues')).toEqual({});
    expect(oldMap.props('metricNumericValues')).toEqual({});
    expect(oldMap.props('showRegionMetrics')).toBe(false);
    expect(oldMap.props('showProvincePoints')).toBe(false);
    expect(oldMap.props('showProvincePointLabels')).toBe(false);
    expect(oldMap.props('cityDetailMode')).toBe('institutions');
    expect(oldMap.props('colorByCity')).toBe(true);
    expect(oldMap.props('regionStates')['610600']).toBe('NO_INSTITUTION');
    expect(oldMap.props('metricColors')['610600']).toBe('#65738a');
    expect(province.get('[data-testid="presentation-map-legend-has-institution"]').text()).toContain('有经营机构');
    expect(province.get('[data-testid="presentation-map-legend-no-institution"]').text()).toContain('无经营机构');
    province.unmount();

    const unrelated = mount(PresentationMapWidget, {
      props: { presentation: { ...presentation, screenCode: 'SCR_RETAIL' }, model, geoJson, mode: 'province', metricKey: 'deposit' },
      global: { stubs: { PanoramaMap: mapStub } }
    });
    expect(unrelated.getComponent(mapStub).props('labelLayout')).toBe('callout');
    expect(unrelated.getComponent(mapStub).props('cityDetails')).toEqual({});
    expect(unrelated.getComponent(mapStub).props('colorByCity')).toBe(false);
    unrelated.unmount();

    const templateBranch = mount(PresentationMapWidget, {
      props: { presentation: { ...presentation, screenCode: undefined, template: 'branch-overview-v1' }, model, geoJson, mode: 'province', metricKey: 'deposit' },
      global: { stubs: { PanoramaMap: mapStub } }
    });
    expect(templateBranch.getComponent(mapStub).props('labelLayout')).toBe('callout');
    expect(templateBranch.getComponent(mapStub).props('showProvincePointLabels')).toBe(false);
    expect(templateBranch.getComponent(mapStub).props('cityDetailMode')).toBe('institutions');
    templateBranch.unmount();

    const v2TemplateBranch = mount(PresentationMapWidget, {
      props: { presentation: { ...presentation, template: 'branch-overview-v1' }, model, geoJson, mode: 'province', metricKey: 'deposit' },
      global: { stubs: { PanoramaMap: mapStub } }
    });
    expect(v2TemplateBranch.getComponent(mapStub).props('labelLayout')).toBe('inline');
    expect(v2TemplateBranch.getComponent(mapStub).props('cityDetailMode')).toBe('metrics');
    expect(v2TemplateBranch.getComponent(mapStub).props('cityDetails')).toEqual({});
    expect(v2TemplateBranch.getComponent(mapStub).props('colorByCity')).toBe(false);
    v2TemplateBranch.unmount();

    const city = mount(PresentationMapWidget, {
      props: { presentation: oldPresentation, model, geoJson, mode: 'city', cityCode: '610100' },
      global: { stubs: { PanoramaMap: mapStub } }
    });
    expect(city.getComponent(mapStub).props('pointLabelLayout')).toBe('callout');
    city.unmount();
  });

  it('旧省级机构地图尊重 MAP 组件的自定义标题，AUTO 仍使用默认标题', () => {
    const custom = {
      ...presentation,
      screenCode: 'SCR_PROVINCE',
      display: { components: [{
        ...presentation.display.components[0], componentId: 'legacy-map-58',
        dataRefs: [{ blockId: 58, role: 'PRIMARY', unit: 'YUAN' }],
        text: { titleMode: 'CUSTOM', title: '全辖机构分布' }
      }] }
    };
    const wrapper = mount(PresentationMapWidget, {
      props: { presentation: custom, model, geoJson, mode: 'province', metricKey: 'deposit' },
      global: { stubs: { PanoramaMap: mapStub } }
    });
    expect(wrapper.get('h2').text()).toBe('全辖机构分布');
  });

  it('city 按真实区县边界传递有机构突出态、无机构灰色和默认聚焦', () => {
    const cityGeoJson = {
      type: 'FeatureCollection',
      features: [
        { type: 'Feature', properties: { adcode: 'D-A', name: '甲区' }, geometry: { type: 'Polygon', coordinates: [[[108, 34], [108.5, 34], [108.5, 35], [108, 35], [108, 34]]] } },
        { type: 'Feature', properties: { adcode: 'D-B', name: '乙区' }, geometry: { type: 'Polygon', coordinates: [[[108.5, 34], [109, 34], [109, 35], [108.5, 35], [108.5, 34]]] } }
      ]
    };
    const wrapper = mount(PresentationMapWidget, {
      props: {
        presentation,
        model: { ...model, institutions: [model.institutions[0]] },
        geoJson: cityGeoJson,
        mode: 'city',
        cityCode: '610100',
        metricKey: 'deposit'
      },
      global: { stubs: { PanoramaMap: mapStub } }
    });
    const map = wrapper.getComponent(mapStub);
    expect(map.props('regionStates')).toMatchObject({ 'D-A': 'HAS_INSTITUTION', 'D-B': 'NO_INSTITUTION' });
    expect(map.props('metricColors')['D-B']).toBe('#26364d');
    expect(map.props('showRegionMetrics')).toBe(false);
    expect(map.props('viewFit')).toMatchObject({ focusRegionCodes: ['D-A'], initialZoom: 1.65 });
    expect(wrapper.get('[data-testid="presentation-map-legend-has-institution"]').text()).toBe('有经营机构');
    expect(wrapper.get('[data-testid="presentation-map-legend-no-institution"]').text()).toBe('无经营机构');
    expect(wrapper.get('[data-testid="presentation-map-legend-district-unknown"]').text()).toBe('归属待确认');
    expect(wrapper.find('[data-testid="presentation-map-legend-high"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="presentation-map-legend-no-institution"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('city 存在未定位机构时未确认区县保持 MISSING，不伪造无机构灰色', () => {
    const cityGeoJson = {
      type: 'FeatureCollection',
      features: [
        { type: 'Feature', properties: { adcode: 'D-A', name: '甲区' }, geometry: { type: 'Polygon', coordinates: [[[108, 34], [108.5, 34], [108.5, 35], [108, 35], [108, 34]]] } },
        { type: 'Feature', properties: { adcode: 'D-B', name: '乙区' }, geometry: { type: 'Polygon', coordinates: [[[108.5, 34], [109, 34], [109, 35], [108.5, 35], [108.5, 34]]] } }
      ]
    };
    const wrapper = mount(PresentationMapWidget, {
      props: { presentation, model, geoJson: cityGeoJson, mode: 'city', cityCode: '610100', metricKey: 'deposit' },
      global: { stubs: { PanoramaMap: mapStub } }
    });
    const map = wrapper.getComponent(mapStub);
    expect(map.props('regionStates')['D-A']).toBe('HAS_INSTITUTION');
    expect(map.props('regionStates')['D-B']).toBe('MISSING');
    expect(map.props('metricColors')['D-B']).toBe('#65738a');
    wrapper.unmount();
  });

  it('明确无经营机构的城市由 MAP 组件防御拦截，不向上发 region-select 或 map-context', async () => {
    const mapGeoJson = { ...geoJson, features: [
      ...geoJson.features,
      { ...geoJson.features[0], properties: { adcode: '610200', name: '铜川市' } }
    ] };
    const wrapper = mount(PresentationMapWidget, {
      props: {
        presentation, model: { ...model, institutions: [{ ...model.institutions[0], cityCode: '610100' }] },
        geoJson: mapGeoJson, mode: 'province', metricKey: 'deposit'
      },
      global: { stubs: { PanoramaMap: {
        props: mapStub.props,
        emits: ['region-select', 'branch-select'],
        template: '<div data-testid="panorama-map-stub"><button data-city="610200" @click="$emit(\'region-select\', { code: \'610200\', name: \'铜川市\' })">无机构城市</button></div>'
      } } }
    });
    await wrapper.get('[data-city="610200"]').trigger('click');
    const status = wrapper.find('[role="status"]');
    expect(status.exists()).toBe(true);
    expect(status.text()).toContain('无经营机构');
    expect(wrapper.emitted('region-select')).toBeUndefined();
    expect(wrapper.emitted('map-context')).toBeUndefined();
    wrapper.unmount();
  });

  it('city 下钻不使用省级 NO_INSTITUTION 门禁阻止区县选择', async () => {
    const districtGeoJson = {
      type: 'FeatureCollection',
      features: [{ type: 'Feature', properties: { adcode: 'D-A', name: '甲区' }, geometry: { type: 'Polygon', coordinates: [[[108, 34], [108.5, 34], [108.5, 35], [108, 35], [108, 34]]] } }]
    };
    const wrapper = mount(PresentationMapWidget, {
      props: { presentation, model: { ...model, institutions: [] }, geoJson: districtGeoJson, mode: 'city', cityCode: '610100' },
      global: { stubs: { PanoramaMap: {
        props: mapStub.props,
        emits: ['region-select', 'branch-select'],
        template: '<div data-testid="panorama-map-stub"><button data-city="D-A" @click="$emit(\'region-select\', { code: \'D-A\', name: \'甲区\' })">甲区</button></div>'
      } } }
    });
    await wrapper.get('[data-city="D-A"]').trigger('click');
    expect(wrapper.emitted('region-select')).toContainEqual([{ code: 'D-A', name: '甲区' }]);
    expect(wrapper.find('[data-testid="map-activation-status"]').exists()).toBe(false);
    wrapper.unmount();
  });
});
