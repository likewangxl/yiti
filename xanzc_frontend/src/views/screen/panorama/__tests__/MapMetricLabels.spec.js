// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import PanoramaMap from '../PanoramaMap.vue';
import { provinceGeo } from '../geography';
const geoJson = { type: 'FeatureCollection', features: [{ type: 'Feature', properties: { adcode: 610100, name: '西安市', center: [108.5, 34.5] }, geometry: { type: 'Polygon', coordinates: [[[108,34],[109,34],[109,35],[108,35],[108,34]]] } }, { type: 'Feature', properties: { adcode: 610200, name: '铜川市', center: [108.8, 35.2] }, geometry: { type: 'Polygon', coordinates: [[[109,35],[110,35],[110,36],[109,36],[109,35]]] } }] };
describe('地图指标联动显示', () => {
  it('按调用方已确认的范围显示指标，切换后更新且无数据不补零', async () => {
    const wrapper = mount(PanoramaMap, { props: { geoJson: { ...geoJson, features: [geoJson.features[0]] }, metricLabel: '存款余额', metricValues: { '610100': '125万元' } } });
    expect(wrapper.get('[data-testid="map-metric-label"]').text()).toBe('存款余额');
    expect(wrapper.text()).toContain('125万元');
    await wrapper.setProps({ metricLabel: '净增', metricValues: { '610100': '-3万元' } });
    expect(wrapper.text()).toContain('-3万元');
    expect(wrapper.text()).not.toContain('125万元');
    await wrapper.setProps({ metricValues: {} });
    expect(wrapper.find('[data-testid="map-region-metric"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('省级 callout 模式用每个地市原始行政中心连线，缺失指标显示暂无数据', async () => {
    const wrapper = mount(PanoramaMap, {
      props: {
        geoJson,
        labelLayout: 'callout',
        metricLabel: '存款余额',
        metricValues: { '610100': '125万元' }
      }
    });
    await wrapper.vm.$nextTick();

    expect(wrapper.findAll('[data-testid="map-city-callout-line"]')).toHaveLength(2);
    expect(wrapper.find('[data-city-code="610100"]').exists()).toBe(true);
    expect(wrapper.find('[data-city-code="610200"]').exists()).toBe(true);
    expect(wrapper.find('[data-city-code="610200"] .panorama-map__metric-value').text()).toBe('暂无数据');
    expect(wrapper.find('[data-city-code="610100"] .panorama-map__metric-value').text()).toBe('125万元');
    wrapper.unmount();
  });

  it('真实陕西省 GeoJSON 的十个地市均有唯一 callout，标签分列且不使用避让点作锚点', async () => {
    const wrapper = mount(PanoramaMap, {
      props: {
        geoJson: provinceGeo,
        labelLayout: 'callout',
        metricValues: Object.fromEntries(provinceGeo.features.map(feature => [feature.properties.adcode, '100万元']))
      }
    });
    await wrapper.vm.$nextTick();

    const lines = wrapper.findAll('[data-testid="map-city-callout-line"]');
    const labels = wrapper.findAll('.panorama-map__region-label-hit[data-city-code]');
    expect(lines).toHaveLength(10);
    expect(labels).toHaveLength(10);
    expect(new Set(lines.map(line => line.attributes('data-city-code'))).size).toBe(10);
    expect(new Set(labels.map(label => label.attributes('data-city-code'))).size).toBe(10);
    expect(wrapper.findAll('.panorama-map__region-label-hit[data-city-code="610100"]')).toHaveLength(1);
    wrapper.unmount();
  });
});


describe('城市悬浮详情', () => {
  it('悬停和键盘聚焦展示已有指标、机构与日期，离开后收起', async () => {
    const wrapper=mount(PanoramaMap,{global:{stubs:{Teleport:true}},props:{geoJson,labelLayout:'callout',metricValues:{'610100':'125万元'},cityDetails:{'610100':{institutionCount:2,locatedCount:1,dataDate:'2026-08-30',metrics:[{key:'deposit',label:'存款余额',value:'125万元'},{key:'loan',label:'贷款余额',value:'暂无数据'}],institutions:[{orgCode:'A',orgName:'西安一支行'},{orgCode:'B',orgName:'西安二支行'}]}}}});
    const label=wrapper.get('button[data-city-code="610100"]');
    await label.trigger('pointerenter');
    const tooltip=wrapper.get('[role="tooltip"]');
    expect(tooltip.text()).toContain('125万元');
    expect(tooltip.text()).toContain('贷款余额');
    expect(tooltip.text()).toContain('2026-08-30');
    expect(tooltip.text()).toContain('西安一支行');
    expect(tooltip.text()).toContain('2 家');
    expect(wrapper.findAll('[data-testid="map-city-callout-line"]')[0].element.tagName.toLowerCase()).toBe('path');
    await label.trigger('pointerleave');
    expect(wrapper.find('[role="tooltip"]').exists()).toBe(false);
    await label.trigger('focus');
    expect(wrapper.find('[role="tooltip"]').exists()).toBe(true);
    await label.trigger('blur');
    expect(wrapper.find('[role="tooltip"]').exists()).toBe(false);
    wrapper.unmount();
  });
});
