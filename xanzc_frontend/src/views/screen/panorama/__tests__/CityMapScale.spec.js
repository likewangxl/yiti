// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import PanoramaMap from '../PanoramaMap.vue';
import { buildCityDistrictMapState } from '../cityDistrictMapModel';

const cityGeo = {
  type: 'FeatureCollection',
  features: [{
    type: 'Feature',
    properties: { adcode: 'D-A', name: '甲区' },
    geometry: { type: 'Polygon', coordinates: [[[108, 34], [109, 34], [109, 35], [108, 35], [108, 34]]] }
  }]
};

function pathBounds(path) {
  const points = [...path.matchAll(/[ML]\s*(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)/g)]
    .map((match) => ({ x: Number(match[1]), y: Number(match[2]) }));
  return {
    width: Math.max(...points.map(point => point.x)) - Math.min(...points.map(point => point.x)),
    height: Math.max(...points.map(point => point.y)) - Math.min(...points.map(point => point.y))
  };
}

describe('市级地图初始缩放', () => {
  afterEach(() => vi.restoreAllMocks());

  it('分行下钻从 6.6 缩至 3.3，以碑林为中心且重置保持 3.3', async () => {
    const geo = { ...cityGeo, features: [{ ...cityGeo.features[0], properties: { adcode: '610103', name: '碑林区' } }] };
    const { viewFit } = buildCityDistrictMapState(geo, [
      { orgCode: 'B01', lng: 108.5, lat: 34.5, located: true, coordSys: 'GCJ02' }
    ], { businessLine: 'COMMON', cityCode: '610100' });
    const baseline = mount(PanoramaMap, { props: { geoJson: geo, mode: 'city', viewFit: { ...viewFit, initialZoom: 6.6 } } });
    const enlarged = mount(PanoramaMap, { props: { geoJson: geo, mode: 'city', viewFit } });
    await nextTick();
    const initialPath = enlarged.get('path[data-region-code="610103"]').attributes('d');
    const before = pathBounds(baseline.get('path[data-region-code="610103"]').attributes('d'));
    const after = pathBounds(initialPath);
    expect(viewFit.focusCenterSource).toBe('BEILIN_DISTRICT');
    expect(viewFit.focusCenter).toEqual({ lng: 108.5, lat: 34.5 });
    expect(enlarged.attributes('data-zoom')).toBe('3.30');
    expect(after.width / before.width).toBeCloseTo(0.5, 2);
    expect(after.height / before.height).toBeCloseTo(0.5, 2);
    await enlarged.get('button[aria-label="缩小地图"]').trigger('click');
    await enlarged.get('button[aria-label="重置地图视图"]').trigger('click');
    expect(enlarged.attributes('data-zoom')).toBe('3.30');
    expect(enlarged.get('path[data-region-code="610103"]').attributes('d')).toBe(initialPath);
    baseline.unmount();
    enlarged.unmount();
  });

  it('initialZoom 3.3 使 SVG 投影路径的实际宽高相对 1.65 翻倍', async () => {
    const focus = { focusRegionCodes: ['D-A'], focusCenter: { lng: 108.5, lat: 34.5 } };
    const baseline = mount(PanoramaMap, {
      props: { geoJson: cityGeo, mode: 'city', viewFit: { ...focus, initialZoom: 1.65 } }
    });
    const enlarged = mount(PanoramaMap, {
      props: { geoJson: cityGeo, mode: 'city', viewFit: { ...focus, initialZoom: 3.3 } }
    });
    await nextTick();

    const baselineBounds = pathBounds(baseline.get('path[data-region-code="D-A"]').attributes('d'));
    const enlargedBounds = pathBounds(enlarged.get('path[data-region-code="D-A"]').attributes('d'));
    expect(baseline.attributes('data-zoom')).toBe('1.65');
    expect(enlarged.attributes('data-zoom')).toBe('3.30');
    expect(enlargedBounds.width / baselineBounds.width).toBeCloseTo(2, 2);
    expect(enlargedBounds.height / baselineBounds.height).toBeCloseTo(2, 2);

    baseline.unmount();
    enlarged.unmount();
  });

  it('reset 回到 3.3，并让无 focus 或无法匹配 focus 的市图采用合法 initialZoom 和原中心', async () => {
    const focused = mount(PanoramaMap, {
      props: {
        geoJson: cityGeo,
        mode: 'city',
        viewFit: { focusRegionCodes: ['D-A'], focusCenter: { lng: 108.5, lat: 34.5 }, initialZoom: 3.3 }
      }
    });
    await focused.get('button[aria-label="放大地图"]').trigger('click');
    await focused.get('button[aria-label="重置地图视图"]').trigger('click');
    expect(focused.attributes('data-zoom')).toBe('3.30');
    focused.unmount();

    const noFocus = mount(PanoramaMap, {
      props: { geoJson: cityGeo, mode: 'city', viewFit: { initialZoom: 2 } }
    });
    await nextTick();
    expect(noFocus.attributes('data-zoom')).toBe('2.00');
    const noFocusPath = noFocus.get('path[data-region-code="D-A"]').attributes('d');
    noFocus.unmount();

    const unmatchedFocus = mount(PanoramaMap, {
      props: {
        geoJson: cityGeo,
        mode: 'city',
        viewFit: { focusRegionCodes: ['UNKNOWN'], focusCenter: { lng: 109, lat: 35 }, initialZoom: 2 }
      }
    });
    await nextTick();
    expect(unmatchedFocus.attributes('data-zoom')).toBe('2.00');
    expect(unmatchedFocus.get('path[data-region-code="D-A"]').attributes('d'))
      .toBe(noFocusPath);
    unmatchedFocus.unmount();
  });

  it('对初始缩放安全解析并限制在全局范围，省级默认缩放保持 1', async () => {
    const invalid = mount(PanoramaMap, {
      props: { geoJson: cityGeo, mode: 'city', viewFit: { initialZoom: 'not-a-zoom' } }
    });
    await nextTick();
    expect(invalid.attributes('data-zoom')).toBe('1.00');
    invalid.unmount();

    const tooLarge = mount(PanoramaMap, {
      props: { geoJson: cityGeo, mode: 'city', viewFit: { initialZoom: 99 } }
    });
    await nextTick();
    expect(tooLarge.attributes('data-zoom')).toBe('12.00');
    tooLarge.unmount();

    const province = mount(PanoramaMap, {
      props: { geoJson: cityGeo, mode: 'province', viewFit: { initialZoom: 3.3 } }
    });
    await nextTick();
    expect(province.attributes('data-zoom')).toBe('1.00');
    province.unmount();
  });

  it('碑林显式中心驱动 SVG 取景，重置后中心与倍率恢复一致', async () => {
    const beilin = mount(PanoramaMap, {
      props: {
        geoJson: cityGeo,
        mode: 'city',
        viewFit: {
          focusRegionCodes: ['D-A'],
          focusCenterMode: 'OCCUPIED_BOUNDS',
          focusCenterSource: 'BEILIN_DISTRICT',
          focusCenter: { lng: 108.2, lat: 34.5 },
          initialZoom: 1.65
        }
      }
    });
    const region = mount(PanoramaMap, {
      props: {
        geoJson: cityGeo,
        mode: 'city',
        viewFit: {
          focusRegionCodes: ['D-A'],
          focusCenterMode: 'OCCUPIED_BOUNDS',
          focusCenterSource: 'REGION_BOUNDS',
          focusCenter: { lng: 108.5, lat: 34.5 },
          initialZoom: 1.65
        }
      }
    });
    await nextTick();
    const initialPath = beilin.get('path[data-region-code="D-A"]').attributes('d');
    const regionPath = region.get('path[data-region-code="D-A"]').attributes('d');
    expect(initialPath).not.toBe(regionPath);
    await beilin.get('button[aria-label="放大地图"]').trigger('click');
    await beilin.get('button[aria-label="重置地图视图"]').trigger('click');
    expect(beilin.attributes('data-zoom')).toBe('1.65');
    expect(beilin.get('path[data-region-code="D-A"]').attributes('d')).toBe(initialPath);
    beilin.unmount();
    region.unmount();
  });
});
