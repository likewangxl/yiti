// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import { mount } from '@vue/test-utils';
import PanoramaMap from '../PanoramaMap.vue';
import { createProjection, projectGeoJson } from '../mapGeometry';
import { provinceGeo } from '../geography';
import { provinceCityColor } from '../provinceCityPalette';
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

function rectangle(left, top, width, height) {
  return { left, top, right: left + width, bottom: top + height, width, height };
}

function rectanglesIntersect(first, second) {
  return first.left < second.right && first.right > second.left
    && first.top < second.bottom && first.bottom > second.top;
}

function mountMapWithTooltipGeometry(props) {
  const originalViewport = {
    width: Object.getOwnPropertyDescriptor(window, 'innerWidth'),
    height: Object.getOwnPropertyDescriptor(window, 'innerHeight')
  };
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1800 });
  Object.defineProperty(window, 'innerHeight', { configurable: true, value: 1100 });

  const widget = document.createElement('div');
  widget.className = 'presentation-map-widget';
  const host = document.createElement('div');
  widget.appendChild(host);
  document.body.appendChild(widget);

  const widgetRect = rectangle(450, 150, 900, 700);
  const mapRect = rectangle(600, 220, 600, 560);
  widget.getBoundingClientRect = () => widgetRect;
  const wrapper = mount(PanoramaMap, {
    attachTo: host,
    global: { stubs: { Teleport: true } },
    props
  });
  wrapper.element.getBoundingClientRect = () => mapRect;
  wrapper.findAll('.panorama-map__region-label-hit[data-city-code]').forEach(label => {
    const code = label.attributes('data-city-code');
    const labelRect = code === '610200'
      ? rectangle(1030, 440, 92, 36)
      : rectangle(680, 390, 92, 36);
    label.element.getBoundingClientRect = () => labelRect;
  });

  return {
    wrapper,
    mapRect,
    widgetRect,
    cleanup() {
      wrapper.unmount();
      widget.remove();
      for (const [key, descriptor] of Object.entries(originalViewport)) {
        if (descriptor) Object.defineProperty(window, key === 'width' ? 'innerWidth' : 'innerHeight', descriptor);
        else delete window[key === 'width' ? 'innerWidth' : 'innerHeight'];
      }
    }
  };
}

async function hoverCityTooltip(wrapper, code) {
  await wrapper.get(`button[data-city-code="${code}"]`).trigger('pointerenter');
  await nextTick();
  await nextTick();
  return wrapper.get('[role="tooltip"]');
}

function tooltipRectangle(tooltip) {
  return rectangle(
    Number.parseFloat(tooltip.element.style.left) || 0,
    Number.parseFloat(tooltip.element.style.top) || 0,
    Number.parseFloat(tooltip.element.style.width) || 304,
    420
  );
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

  it('colorByCity 仅在显式开启时让 SVG 地市使用 palette，metric 模式继续使用输入颜色', async () => {
    const city = mount(PanoramaMap, {
      props: {
        geoJson,
        appearance: 'relief',
        colorByCity: true,
        colorByMetric: true,
        regionStates: { '610100': 'HAS_INSTITUTION' },
        metricColors: { '610100': '#123456', '610200': '#654321' }
      }
    });
    await city.vm.$nextTick();
    expect(city.attributes('data-color-by-city')).toBe('true');
    const cityPath = city.get('path[data-region-code="610100"]');
    const cityBaseStyle = cityPath.attributes('style');
    expect(cityBaseStyle).toContain(provinceCityColor('610100'));
    await cityPath.trigger('pointerenter');
    expect(cityPath.attributes('style')).not.toBe(cityBaseStyle);
    await cityPath.trigger('pointerleave');
    expect(cityPath.attributes('style')).toBe(cityBaseStyle);
    city.unmount();

    const metric = mount(PanoramaMap, {
      props: { geoJson, colorByMetric: true, metricColors: { '610100': '#123456' } }
    });
    await metric.vm.$nextTick();
    expect(metric.attributes('data-color-by-city')).toBe('false');
    expect(metric.get('path[data-region-code="610100"]').attributes('style')).toContain('#123456');
    metric.unmount();

    const district = mount(PanoramaMap, {
      props: { geoJson, mode: 'city', colorByCity: true, colorByMetric: true, metricColors: { '610100': '#123456' } }
    });
    await district.vm.$nextTick();
    expect(district.get('path[data-region-code="610100"]').attributes('style')).toContain('#123456');
    district.unmount();
  });

  it('colorByCity 按 regionStates 只给 HAS_INSTITUTION 彩色，NO_INSTITUTION/MISSING 保持中性灰', async () => {
    const wrapper = mount(PanoramaMap, {
      props: {
        geoJson,
        colorByCity: true,
        colorByMetric: true,
        regionStates: { '610100': 'HAS_INSTITUTION', '610200': 'NO_INSTITUTION' },
        metricColors: { '610100': '#123456', '610200': '#ef4444' }
      }
    });
    await wrapper.vm.$nextTick();
    expect(wrapper.get('path[data-region-code="610100"]').attributes('style')).toContain(provinceCityColor('610100'));
    expect(wrapper.get('path[data-region-code="610200"]').attributes('style')).toContain('#65738a');
    wrapper.unmount();
  });

  it('市级分类色在选中和悬浮后恢复，NO_INSTITUTION 与 MISSING 状态不抹掉分类色', async () => {
    const wrapper = mount(PanoramaMap, {
      props: {
        geoJson,
        mode: 'city',
        colorByMetric: true,
        metricColors: { '610100': '#123456', '610200': '#654321' },
        regionStates: { '610100': 'NO_INSTITUTION', '610200': 'MISSING' },
        selectedRegionCode: '610100'
      }
    });
    await wrapper.vm.$nextTick();

    const noInstitutionGroup = wrapper.findAll('g.panorama-map__region')
      .find(group => group.find('path[data-region-code="610100"]').exists());
    const noInstitutionPath = noInstitutionGroup.get('path[data-region-code="610100"]');
    const missingPath = wrapper.get('path[data-region-code="610200"]');
    const selectedStyle = noInstitutionPath.attributes('style');
    expect(noInstitutionGroup.classes()).toContain('is-no-institution');
    expect(noInstitutionPath.attributes('data-region-state')).toBe('NO_INSTITUTION');
    expect(selectedStyle).toContain('stroke-dasharray');
    expect(selectedStyle).toContain('#2a4c6e');
    expect(selectedStyle).toContain('#f3c8ff');
    expect(selectedStyle).toContain('stroke-width: 0.65');
    expect(selectedStyle).not.toContain('#65738a');
    expect(missingPath.attributes('data-region-state')).toBe('MISSING');
    expect(missingPath.attributes('style')).toContain('#654321');

    await wrapper.setProps({ selectedRegionCode: null });
    await wrapper.vm.$nextTick();
    expect(noInstitutionPath.attributes('style')).toContain('#123456');
    await noInstitutionPath.trigger('pointerenter');
    expect(noInstitutionPath.attributes('style')).toContain('#2a4c6e');
    await noInstitutionPath.trigger('pointerleave');
    expect(noInstitutionPath.attributes('style')).toContain('#123456');

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
  it('metrics inline 和 callout 悬浮详情都定位在地图外侧，不与 widget 或地图相交', async () => {
    const details = {
      '610100': { institutionCount: 2, metrics: [{ key: 'deposit', label: '存款余额', value: '125万元' }] },
      '610200': { institutionCount: 1, metrics: [{ key: 'deposit', label: '存款余额', value: '20万元' }] }
    };
    const inline = mountMapWithTooltipGeometry({
      geoJson,
      appearance: 'relief',
      labelLayout: 'inline',
      cityDetailMode: 'metrics',
      metricValues: { '610100': '125万元', '610200': '20万元' },
      cityDetails: details
    });
    try {
      const tooltip = await hoverCityTooltip(inline.wrapper, '610100');
      const position = tooltipRectangle(tooltip);
      expect(position.left + position.width).toBeLessThanOrEqual(inline.widgetRect.left);
      expect(rectanglesIntersect(position, inline.widgetRect)).toBe(false);
      expect(rectanglesIntersect(position, inline.mapRect)).toBe(false);
    } finally {
      inline.cleanup();
    }

    const callout = mountMapWithTooltipGeometry({
      geoJson,
      appearance: 'relief',
      labelLayout: 'callout',
      cityDetailMode: 'metrics',
      metricValues: { '610100': '125万元', '610200': '20万元' },
      cityDetails: details
    });
    try {
      const leftTooltip = await hoverCityTooltip(callout.wrapper, '610100');
      const leftPosition = tooltipRectangle(leftTooltip);
      expect(leftPosition.left + leftPosition.width).toBeLessThanOrEqual(callout.widgetRect.left);
      expect(rectanglesIntersect(leftPosition, callout.widgetRect)).toBe(false);
      expect(rectanglesIntersect(leftPosition, callout.mapRect)).toBe(false);

      await callout.wrapper.get('button[data-city-code="610100"]').trigger('pointerleave');
      await nextTick();
      const rightTooltip = await hoverCityTooltip(callout.wrapper, '610200');
      const rightPosition = tooltipRectangle(rightTooltip);
      expect(rightPosition.left).toBeGreaterThanOrEqual(callout.widgetRect.right);
      expect(rectanglesIntersect(rightPosition, callout.widgetRect)).toBe(false);
      expect(rectanglesIntersect(rightPosition, callout.mapRect)).toBe(false);
    } finally {
      callout.cleanup();
    }
  });

  it('机构模式的 callout popup 继续位于地图外围左右列', async () => {
    const institution = orgName => ({ institutionCount: 1, institutions: [{ orgCode: orgName, orgName }] });
    const geometry = mountMapWithTooltipGeometry({
      geoJson,
      labelLayout: 'callout',
      cityDetailMode: 'institutions',
      cityDetails: { '610100': institution('西安支行'), '610200': institution('铜川支行') }
    });
    try {
      const leftTooltip = await hoverCityTooltip(geometry.wrapper, '610100');
      const leftPosition = tooltipRectangle(leftTooltip);
      expect(leftPosition.left + leftPosition.width).toBeLessThanOrEqual(geometry.widgetRect.left);
      expect(rectanglesIntersect(leftPosition, geometry.widgetRect)).toBe(false);
      expect(rectanglesIntersect(leftPosition, geometry.mapRect)).toBe(false);

      await geometry.wrapper.get('button[data-city-code="610100"]').trigger('pointerleave');
      await nextTick();
      const rightTooltip = await hoverCityTooltip(geometry.wrapper, '610200');
      const rightPosition = tooltipRectangle(rightTooltip);
      expect(rightPosition.left).toBeGreaterThanOrEqual(geometry.widgetRect.right);
      expect(rectanglesIntersect(rightPosition, geometry.widgetRect)).toBe(false);
      expect(rectanglesIntersect(rightPosition, geometry.mapRect)).toBe(false);
    } finally {
      geometry.cleanup();
    }
  });

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

  it('机构模式的飞线和地市 focus 只展示全部机构名称，不泄露指标、金额、日期或定位数', async () => {
    vi.useFakeTimers();
    const institutionNames = ['西安一支行', '西安二支行', '西安三支行', '西安四支行'];
    const wrapper=mount(PanoramaMap,{
      global:{stubs:{Teleport:true}},
      props:{
        geoJson,
        labelLayout:'callout',
        cityDetailMode:'institutions',
        metricLabel:'存款余额',
        metricValues:{'610100':'999万元'},
        cityDetails:{
          '610100':{
            institutionCount:4,
            locatedCount:1,
            dataDate:'2026-08-30',
            metrics:[{key:'deposit',label:'存款余额',value:'999万元'}],
            institutions:institutionNames.map((orgName,index)=>({orgCode:`A-${index}`,orgName}))
          }
        }
      }
    });
    const hit=wrapper.get('[data-testid="map-city-callout-hit"][data-city-code="610100"]');
    expect(wrapper.findAll('[data-testid="map-city-callout-hit"]')).toHaveLength(1);
    await hit.trigger('pointerenter');
    const tooltip=wrapper.get('[role="tooltip"]');
    expect(tooltip.get('[data-testid="map-city-institution-count"]').text()).toContain('4 家');
    expect(tooltip.findAll('[data-testid="map-city-institution-name"]')).toHaveLength(4);
    institutionNames.forEach(name => expect(tooltip.text()).toContain(name));
    expect(tooltip.text()).not.toContain('存款余额');
    expect(tooltip.text()).not.toContain('999万元');
    expect(tooltip.text()).not.toContain('2026-08-30');
    expect(tooltip.text()).not.toContain('已定位');
    expect(tooltip.text()).not.toContain('地市经营概览');
    expect(tooltip.text()).not.toContain('当前授权范围');
    expect(tooltip.text()).not.toContain('点击城市查看详情');
    expect(tooltip.find('footer').exists()).toBe(false);
    await hit.trigger('pointerleave');
    const tooltipAfterLineLeave=wrapper.get('[role="tooltip"]');
    await tooltipAfterLineLeave.trigger('pointerenter');
    vi.advanceTimersByTime(350);
    await nextTick();
    expect(wrapper.find('[role="tooltip"]').exists()).toBe(true);
    await tooltipAfterLineLeave.trigger('pointerleave');
    vi.advanceTimersByTime(350);
    await nextTick();
    expect(wrapper.find('[role="tooltip"]').exists()).toBe(false);

    const label=wrapper.get('button[data-city-code="610100"]');
    await label.trigger('focus');
    expect(wrapper.get('[role="tooltip"]').text()).toContain('西安四支行');
    await label.trigger('blur');
    vi.advanceTimersByTime(350);
    expect(wrapper.find('[role="tooltip"]').exists()).toBe(false);
    wrapper.unmount();
    vi.useRealTimers();
  });

  it('机构模式无机构城市显示 0 家和明确空态', async () => {
    const wrapper=mount(PanoramaMap,{
      global:{stubs:{Teleport:true}},
      props:{
        geoJson,
        labelLayout:'callout',
        cityDetailMode:'institutions',
        cityDetails:{'610100':{institutionCount:0,institutions:[]},'610200':{institutionCount:0,institutions:[]}}
      }
    });
    expect(wrapper.findAll('[data-testid="map-city-callout-line"]')).toHaveLength(0);
    expect(wrapper.findAll('[data-testid="map-city-callout-hit"]')).toHaveLength(0);
    await wrapper.get('button[data-city-code="610200"]').trigger('focus');
    const tooltip=wrapper.get('[role="tooltip"]');
    expect(tooltip.get('[data-testid="map-city-institution-count"]').text()).toContain('0 家');
    expect(tooltip.text()).toContain('无经营机构');
    expect(tooltip.findAll('[data-testid="map-city-institution-name"]')).toHaveLength(0);
    await wrapper.get('button[data-city-code="610200"]').trigger('blur');
    wrapper.unmount();
  });

  it('机构模式无经营机构地市把名称放回对应区域并阻止下钻，有机构地市仍保留外围 callout', async () => {
    const cityDetails = {
      '610100': { institutionCount: 1, institutions: [{ orgCode: 'XIAN-1', orgName: '西安一支行' }] },
      '610200': { institutionCount: 0, institutions: [] }
    };
    const wrapper = mount(PanoramaMap, {
      props: {
        geoJson: provinceGeo,
        appearance: 'relief',
        labelLayout: 'callout',
        cityDetailMode: 'institutions',
        cityDetails
      }
    });
    await wrapper.vm.$nextTick();

    const labels = wrapper.findAll('.panorama-map__region-label-hit[data-city-code]');
    expect(labels).toHaveLength(10);
    expect(new Set(labels.map(label => label.attributes('data-city-code'))).size).toBe(10);
    expect(wrapper.get('button[data-city-code="610100"]').attributes('data-label-placement')).toBe('callout');
    const emptyLabel = wrapper.get('button[data-city-code="610200"]');
    expect(emptyLabel.attributes('data-label-placement')).toBe('map');
    expect(emptyLabel.find('.panorama-map__city-marker').exists()).toBe(false);
    expect(emptyLabel.find('[data-testid="map-region-metric"]').exists()).toBe(false);

    const region = wrapper.vm.regionLabels.find(item => String(item.code) === '610200');
    const expected = wrapper.vm.overlayPoint(wrapper.vm.interiorRegionPoint(region, wrapper.vm.regionAnchorWorldPoint(region)));
    const style = emptyLabel.attributes('style');
    expect(style).toContain(`left: ${expected.x}%`);
    expect(style).toContain(`top: ${expected.y}%`);
    await emptyLabel.trigger('click');
    expect(wrapper.emitted('region-select')).toBeUndefined();
    expect(wrapper.get('[data-testid="map-activation-status"]').text()).toBe('该地区无经营机构');
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
