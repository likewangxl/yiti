// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import PanoramaMap from '../PanoramaMap.vue';

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
});
