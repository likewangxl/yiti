// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import PanoramaMap from '../PanoramaMap.vue';
import { createProjection, projectGeoJson } from '../mapGeometry';
import { provinceGeo } from '../geography';
const geoJson = { type: 'FeatureCollection', features: [{ type: 'Feature', properties: { adcode: 610100, name: '西安市', center: [108.5, 34.5] }, geometry: { type: 'Polygon', coordinates: [[[108,34],[109,34],[109,35],[108,35],[108,34]]] } }, { type: 'Feature', properties: { adcode: 610200, name: '铜川市', center: [108.8, 35.2] }, geometry: { type: 'Polygon', coordinates: [[[109,35],[110,35],[110,36],[109,36],[109,35]]] } }] };

function pointInRing(point, ring) {
  let inside = false;
  for (let index = 0, previous = ring.length - 1; index < ring.length; previous = index++) {
    const currentPoint = ring[index];
    const previousPoint = ring[previous];
    const intersects = ((currentPoint.y > point.y) !== (previousPoint.y > point.y))
      && point.x < ((previousPoint.x - currentPoint.x) * (point.y - currentPoint.y))
        / (previousPoint.y - currentPoint.y || Number.EPSILON) + currentPoint.x;
    if (intersects) inside = !inside;
  }
  return inside;
}

function pointInRegion(point, region) {
  return pointInRing(point, region.outer) && !region.holes.some(hole => pointInRing(point, hole));
}
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

  it('真实陕西省 GeoJSON 的十个地市在 relief inline 模式显示唯一名称和原指标，不绘制飞线', async () => {
    const metricValues = Object.fromEntries(provinceGeo.features.map(feature => [feature.properties.adcode, '100万元']));
    const wrapper = mount(PanoramaMap, {
      props: {
        geoJson: provinceGeo,
        appearance: 'relief',
        labelLayout: 'inline',
        showRegionMetrics: true,
        metricValues
      }
    });
    await wrapper.vm.$nextTick();

    expect(wrapper.attributes('data-label-layout')).toBe('inline');
    expect(wrapper.findAll('[data-testid="map-city-callout-line"]')).toHaveLength(0);
    expect(wrapper.findAll('[data-testid="map-region-metric"]')).toHaveLength(10);
    const labels = wrapper.findAll('.panorama-map__region-label-hit[data-city-code]');
    expect(labels).toHaveLength(10);
    expect(new Set(labels.map(label => label.attributes('data-city-code'))).size).toBe(10);
    expect(new Set(labels.map(label => label.find('.panorama-map__city-name').text())).size).toBe(10);
    provinceGeo.features.forEach(feature => expect(wrapper.text()).toContain(feature.properties.name));
    await labels.find(label => label.attributes('data-city-code') === '610300').trigger('click');
    expect(wrapper.emitted('region-select')).toContainEqual([{ code: '610300', name: '宝鸡市' }]);
    wrapper.unmount();
  });

  it('relief inline 的初始 labelWorld 必须留在对应地市多边形内', async () => {
    const wrapper = mount(PanoramaMap, {
      props: {
        geoJson: provinceGeo,
        appearance: 'relief',
        labelLayout: 'inline',
        showRegionMetrics: true,
        metricValues: Object.fromEntries(provinceGeo.features.map(feature => [feature.properties.adcode, '100万元']))
      }
    });
    await wrapper.vm.$nextTick();

    const projected = projectGeoJson(provinceGeo, createProjection(provinceGeo));
    wrapper.vm.regionLabels.forEach(label => {
      const region = projected.find(item => String(item.code) === String(label.code));
      expect(region).toBeTruthy();
      expect(pointInRegion(label.labelWorld, region)).toBe(true);
      const screenRegion = {
        outer: region.outer.map(point => wrapper.vm.overlayPoint(point)),
        holes: region.holes.map(ring => ring.map(point => wrapper.vm.overlayPoint(point)))
      };
      expect(pointInRegion(wrapper.vm.metricLabelPositions[label.key], screenRegion)).toBe(true);
    });
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

  it('relief inline 标签仍支持键盘聚焦详情，且离开后收起', async () => {
    const wrapper=mount(PanoramaMap,{global:{stubs:{Teleport:true}},props:{appearance:'relief',labelLayout:'inline',geoJson,metricValues:{'610100':'125万元'},cityDetails:{'610100':{institutionCount:2,locatedCount:1,dataDate:'2026-08-30',metrics:[{key:'deposit',label:'存款余额',value:'125万元'}],institutions:[{orgCode:'A',orgName:'西安一支行'}]}}}});
    const label=wrapper.get('button[data-city-code="610100"]');
    await label.trigger('focus');
    expect(wrapper.get('[role="tooltip"]').text()).toContain('125万元');
    expect(label.attributes('aria-describedby')).toBeTruthy();
    await label.trigger('blur');
    expect(wrapper.find('[role="tooltip"]').exists()).toBe(false);
    wrapper.unmount();
  });
});
