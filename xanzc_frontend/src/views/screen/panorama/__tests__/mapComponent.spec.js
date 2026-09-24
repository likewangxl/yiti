// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import PanoramaMap from '../PanoramaMap.vue';
import { provinceGeo } from '../geography';

const geoJson = {
  type: 'FeatureCollection',
  features: [{
    type: 'Feature',
    properties: { adcode: 610100, name: '西安市' },
    geometry: { type: 'Polygon', coordinates: [[[108, 34], [109, 34], [109, 35], [108, 35], [108, 34]]] }
  }]
};

const points = [
  { orgCode: 'A', orgName: '有效支行', lng: 108.4, lat: 34.4, coordSys: 'GCJ02' },
  { orgCode: 'B', orgName: '无坐标机构', coordSys: 'GCJ02' },
  { orgCode: 'C', orgName: 'WGS84机构', lng: 108.6, lat: 34.6, coordSys: 'WGS84' }
];

const clusteredPoints = [
  { orgCode: 'CLUSTER-A', orgName: '聚合机构甲', lng: 108.4, lat: 34.4, coordSys: 'GCJ02', demo: true },
  { orgCode: 'CLUSTER-B', orgName: '聚合机构乙', lng: 108.4001, lat: 34.4001, coordSys: 'GCJ02', demo: true }
];

describe('PanoramaMap', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('WebGL 不可用时提供可用的真实 SVG 几何回退和无坐标外部列表', async () => {
    const wrapper = mount(PanoramaMap, { props: { geoJson, points, mode: 'city' } });
    await nextTick();
    expect(wrapper.find('.panorama-map').exists()).toBe(true);
    expect(wrapper.find('.panorama-map__fallback').exists()).toBe(true);
    expect(wrapper.find('.panorama-map__fallback path').exists()).toBe(true);
    expect(wrapper.text()).toContain('未绘制 2 个机构');
    expect(wrapper.text()).toContain('缺少有效 GCJ-02 坐标');
  });

  it('区域和机构点击遵循公共事件契约，聚合/缩放控件可访问', async () => {
    const wrapper = mount(PanoramaMap, { props: { geoJson, points, mode: 'city' } });
    await nextTick();
    await wrapper.find('.panorama-map__fallback path').trigger('click');
    expect(wrapper.emitted('region-select')[0][0]).toEqual({ code: '610100', name: '西安市' });
    await wrapper.find('[data-org-code="A"]').trigger('click');
    expect(wrapper.emitted('branch-select')[0][0]).toBe('A');
    expect(wrapper.find('button[aria-label="放大地图"]').exists()).toBe(true);
    expect(wrapper.find('button[aria-label="缩小地图"]').exists()).toBe(true);
    expect(wrapper.find('button[aria-label="重置地图视图"]').exists()).toBe(true);
  });

  it('selectedOrgCode 高亮点位，mode 和 selectedRegionCode 写入地图语义状态', () => {
    const wrapper = mount(PanoramaMap, {
      props: { geoJson, points, selectedOrgCode: 'A', mode: 'city', selectedRegionCode: '610100' }
    });
    expect(wrapper.find('[data-org-code="A"]').classes()).toContain('is-selected');
    expect(wrapper.attributes('data-mode')).toBe('city');
    expect(wrapper.attributes('data-selected-region')).toBe('610100');
  });

  it('支行超过 8 家时仍在地图上显示每个未聚合点的名称', async () => {
    const manyPoints = Array.from({ length: 9 }, (_, index) => ({
      orgCode: `BRANCH-${index}`,
      orgName: `测试${index + 1}号支行`,
      lng: 108.05 + index * 0.11,
      lat: 34.5,
      coordSys: 'GCJ02'
    }));
    const wrapper = mount(PanoramaMap, { props: { geoJson, points: manyPoints, mode: 'city' } });
    await nextTick();
    expect(wrapper.findAll('.panorama-map__point-label')).toHaveLength(9);
    expect(wrapper.text()).toContain('测试9号支行');
    wrapper.unmount();
  });

  it('放大后可通过拖动平移地图，且拖动不误触发支行选择', async () => {
    const wrapper = mount(PanoramaMap, { props: { geoJson, points, mode: 'city' } });
    await wrapper.find('button[aria-label="放大地图"]').trigger('click');
    await nextTick();
    const marker = wrapper.get('[data-org-code="A"]');
    const before = marker.attributes('style');
    await marker.trigger('pointerdown', { pointerId: 7, clientX: 200, clientY: 180, button: 0, isPrimary: true });
    await marker.trigger('pointermove', { pointerId: 7, clientX: 280, clientY: 220, buttons: 1, isPrimary: true });
    await marker.trigger('pointerup', { pointerId: 7, clientX: 280, clientY: 220, button: 0, isPrimary: true });
    await nextTick();
    expect(wrapper.attributes('data-pan-enabled')).toBe('true');
    expect(marker.attributes('style')).not.toBe(before);
    expect(wrapper.emitted('branch-select')).toBeUndefined();
    wrapper.unmount();
  });

  it('relief 是显式 opt-in 外观，默认地图保持 classic', async () => {
    const classic = mount(PanoramaMap, { props: { geoJson } });
    const relief = mount(PanoramaMap, { props: { geoJson, appearance: 'relief' } });
    await nextTick();

    expect(classic.attributes('data-appearance')).toBe('classic');
    expect(relief.attributes('data-appearance')).toBe('relief');
    classic.unmount();
    relief.unmount();
  });

  it('省模式只保留城市行政区选择，不展开机构点位；市模式才启用点位层', async () => {
    const province = mount(PanoramaMap, { props: { geoJson, points } });
    await nextTick();
    expect(province.find('.panorama-map__point-layer').exists()).toBe(false);
    const city = mount(PanoramaMap, { props: { geoJson, points, mode: 'city' } });
    await nextTick();
    expect(city.find('.panorama-map__point-layer').exists()).toBe(true);
    expect(city.find('[data-org-code="A"]').exists()).toBe(true);
    province.unmount();
    city.unmount();
  });

  it('省模式显式开启机构点位时显示合法点、可访问名称和指标，非法坐标仍不造点', async () => {
    const wrapper = mount(PanoramaMap, {
      props: {
        geoJson,
        points: [{ ...points[0], metricText: '20.00亿元' }, points[1], points[2]],
        metricValues: { A: '20.00亿元' },
        showProvincePoints: true
      }
    });
    await nextTick();
    expect(wrapper.find('.panorama-map__point-layer').exists()).toBe(true);
    expect(wrapper.get('[data-org-code="A"]').attributes('aria-label')).toContain('有效支行');
    expect(wrapper.get('[data-org-code="A"] .panorama-map__point-label').text()).toContain('20.00亿元');
    expect(wrapper.find('[data-org-code="B"]').exists()).toBe(false);
    expect(wrapper.find('[data-org-code="C"]').exists()).toBe(false);
    await wrapper.get('[data-org-code="A"]').trigger('click');
    expect(wrapper.emitted('branch-select')).toContainEqual(['A']);
    wrapper.unmount();
  });

  it('省级真实 GeoJSON 的十个地市都保留可点击标签，不因展示上限截断', async () => {
    const wrapper = mount(PanoramaMap, { props: { geoJson: provinceGeo } });
    await nextTick();
    expect(wrapper.findAll('.panorama-map__region-label text')).toHaveLength(10);
    expect(wrapper.text()).toContain('西安市');
    expect(wrapper.text()).toContain('榆林市');
    wrapper.unmount();
  });

  it('新 MAP 分支可按当前指标着色，缺数区域使用中性色且保留标签', async () => {
    const metricGeoJson = {
      type: 'FeatureCollection',
      features: [
        { type: 'Feature', properties: { adcode: '610100', name: '西安市' }, geometry: { type: 'Polygon', coordinates: [[[108, 34], [109, 34], [109, 35], [108, 35], [108, 34]]] } },
        { type: 'Feature', properties: { adcode: '610200', name: '铜川市' }, geometry: { type: 'Polygon', coordinates: [[[109, 34], [110, 34], [110, 35], [109, 35], [109, 34]]] } }
      ]
    };
    const wrapper = mount(PanoramaMap, {
      props: {
        geoJson: metricGeoJson,
        colorByMetric: true,
        metricValues: { '610100': '100.00亿元', '610200': '暂无数据' },
        metricNumericValues: { '610100': 100, '610200': null },
        metricColors: { '610100': '#f4c95d', '610200': '#65738a' }
      }
    });
    await nextTick();
    const paths = wrapper.findAll('.panorama-map__region path');
    expect(paths[0].attributes('data-metric-state')).toBe('READY');
    expect(paths[0].attributes('style')).toContain('#f4c95d');
    expect(paths[1].attributes('data-metric-state')).toBe('MISSING');
    expect(paths[1].attributes('style')).toContain('#65738a');
    wrapper.unmount();
  });

  it('无经营机构地市的地图、引线和标签有独立状态，仍能点击进入', async () => {
    const wrapper = mount(PanoramaMap, {
      props: {
        geoJson: provinceGeo,
        labelLayout: 'callout',
        colorByMetric: true,
        regionStates: { '610200': 'NO_INSTITUTION', '610100': 'MISSING' },
        metricValues: { '610200': '无经营机构', '610100': '暂无数据' },
        metricNumericValues: { '610200': null, '610100': null },
        metricColors: { '610200': '#293245', '610100': '#65738a' }
      }
    });
    await nextTick();
    const noInstitutionPath = wrapper.get('path[data-region-code="610200"]');
    const missingPath = wrapper.get('path[data-region-code="610100"]');
    expect(noInstitutionPath.attributes('data-metric-state')).toBe('NO_INSTITUTION');
    expect(missingPath.attributes('data-metric-state')).toBe('MISSING');
    expect(wrapper.get('.panorama-map__callout[data-city-code="610200"]').classes()).toContain('is-no-institution');
    const label = wrapper.get('button[data-city-code="610200"]');
    expect(label.classes()).toContain('is-no-institution');
    expect(label.text()).toContain('无经营机构');
    await label.trigger('click');
    expect(wrapper.emitted('region-select')).toContainEqual([{ code: '610200', name: '铜川市' }]);
    wrapper.unmount();
  });

  it('行内布局仅在无机构地市显示地图内地名，不显示无机构文字和标注线', async () => {
    const wrapper = mount(PanoramaMap, {
      props: {
        geoJson: provinceGeo,
        points,
        showProvincePoints: true,
        showProvincePointLabels: false,
        labelLayout: 'inline',
        colorByMetric: true,
        showRegionMetrics: false,
        regionStates: { '610200': 'NO_INSTITUTION' },
        metricValues: { '610100': '11.27亿元', '610200': '无经营机构' },
        metricNumericValues: { '610100': 11.27, '610200': null },
        metricColors: { '610100': '#f4c95d', '610200': '#26364d' }
      }
    });
    await nextTick();
    expect(wrapper.attributes('data-label-layout')).toBe('inline');
    expect(wrapper.find('[data-testid="map-city-callout-line"]').exists()).toBe(false);
    const tongchuan = wrapper.findAll('.panorama-map__region-label').find(label => label.text().includes('铜川市'));
    expect(tongchuan?.text()).toBe('铜川市');
    expect(tongchuan?.find('[data-testid="map-region-metric"]').exists()).toBe(false);
    const xian = wrapper.findAll('.panorama-map__region-label').find(label => label.text().includes('西安市'));
    expect(xian?.text()).toBe('西安市');
    expect(wrapper.findAll('.panorama-map__city-halo.is-no-institution')).toHaveLength(0);
    expect(wrapper.findAll('.panorama-map__city-halo-svg.is-no-institution')).toHaveLength(0);
    expect(wrapper.find('[data-org-code="A"]').exists()).toBe(true);
    expect(wrapper.find('.panorama-map__point-label').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('无经营机构');
    wrapper.unmount();
  });

  it('开发环境显式 demo=true 时显示合法演示点，默认仍排除演示点', async () => {
    vi.stubEnv('DEV', true);
    const hidden = mount(PanoramaMap, { props: { geoJson, points: clusteredPoints, mode: 'city' } });
    await nextTick();
    expect(hidden.find('[data-org-code="CLUSTER-A"]').exists()).toBe(false);
    hidden.unmount();

    const shown = mount(PanoramaMap, { props: { geoJson, points: clusteredPoints, mode: 'city', demo: true } });
    await nextTick();
    expect(shown.find('.panorama-map__point-hit').exists()).toBe(true);
    shown.unmount();
  });

  it('最大缩放仍有聚合时打开机构选择器，点击成员继续发出 branch-select', async () => {
    vi.stubEnv('DEV', true);
    const wrapper = mount(PanoramaMap, { props: { geoJson, points: clusteredPoints, mode: 'city', demo: true } });
    await nextTick();
    const plus = wrapper.find('button[aria-label="放大地图"]');
    for (let index = 0; index < 12; index += 1) await plus.trigger('click');
    expect(wrapper.attributes('data-zoom')).toBe('12.00');
    expect(plus.attributes()).toHaveProperty('disabled');
    const cluster = wrapper.find('.panorama-map__point-hit.is-cluster');
    expect(cluster.exists()).toBe(true);
    await cluster.trigger('click');
    expect(wrapper.find('.panorama-map__cluster-picker').exists()).toBe(true);
    await wrapper.find('[data-cluster-member="CLUSTER-B"]').trigger('click');
    expect(wrapper.emitted('branch-select')).toContainEqual(['CLUSTER-B']);
    wrapper.unmount();
  });

});
