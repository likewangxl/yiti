// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
const renderSpy = vi.hoisted(() => vi.fn());

vi.mock('three', async () => {
  const actual = await vi.importActual('three');
  class WebGLRendererStub {
    constructor(options) {
      this.domElement = options.canvas;
    }
    setPixelRatio() {}
    setClearColor() {}
    setSize() {}
    render(scene, camera) { renderSpy(scene, camera); }
    dispose() {}
  }
  return { ...actual, WebGLRenderer: WebGLRendererStub };
});

import PanoramaMap from '../PanoramaMap.vue';

const geoJson = {
  type: 'FeatureCollection',
  features: [{
    type: 'Feature',
    properties: { adcode: 610100, name: '西安市' },
    geometry: { type: 'Polygon', coordinates: [[[108, 34], [109, 34], [109, 35], [108, 35], [108, 34]]] }
  }]
};

describe('PanoramaMap WebGL 初始化', () => {
  it('WebGL 初始化后真实 GeoJSON 被构造成可渲染区域，不能静默得到空场景', async () => {
    vi.stubGlobal('WebGL2RenderingContext', function WebGL2RenderingContext() {});
    vi.stubGlobal('WebGLRenderingContext', function WebGLRenderingContext() {});
    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({}));
    vi.stubGlobal('requestAnimationFrame', vi.fn(() => 0));
    vi.stubGlobal('cancelAnimationFrame', vi.fn());
    const wrapper = mount(PanoramaMap, { props: { geoJson } });
    await nextTick();
    expect(wrapper.attributes('data-webgl-ready')).toBe('true');
    expect(wrapper.attributes('data-region-count')).toBe('1');
    expect(wrapper.find('.panorama-map__fallback').exists()).toBe(false);
    wrapper.unmount();
  });

  it('relief 外观在同一真实 GeoJSON 上构建挤出层并完成倾斜 fit', async () => {
    vi.stubGlobal('WebGL2RenderingContext', function WebGL2RenderingContext() {});
    vi.stubGlobal('WebGLRenderingContext', function WebGLRenderingContext() {});
    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({}));
    vi.stubGlobal('requestAnimationFrame', vi.fn(() => 0));
    vi.stubGlobal('cancelAnimationFrame', vi.fn());
    const wrapper = mount(PanoramaMap, { props: { geoJson, appearance: 'relief' } });
    await nextTick();
    expect(wrapper.attributes('data-appearance')).toBe('relief');
    expect(wrapper.attributes('data-webgl-ready')).toBe('true');
    expect(wrapper.find('.panorama-map__fallback').exists()).toBe(false);
    wrapper.unmount();
  });
  it('synchronizes lights when appearance changes on an existing component', async () => {
    vi.stubGlobal('WebGL2RenderingContext', function() {});
    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({}));
    const wrapper = mount(PanoramaMap, { props: { geoJson } });
    await nextTick();
    const scene = renderSpy.mock.calls.at(-1)[0];
    const classicIntensity = scene.children.find(o => o.isAmbientLight).intensity;
    await wrapper.setProps({ appearance: 'relief' });
    expect(scene.children.filter(o => o.isPointLight).length).toBeGreaterThan(0);
    expect(scene.children.find(o => o.isAmbientLight).intensity).toBeLessThan(classicIntensity);
    await wrapper.setProps({ appearance: 'classic' });
    expect(scene.children.filter(o => o.isPointLight)).toHaveLength(0);
    expect(scene.children.find(o => o.isAmbientLight).intensity).toBe(classicIntensity);
    wrapper.unmount();
  });

  it('reprojects DOM city labels after ResizeObserver changes the camera aspect', async () => {
    let resize;
    vi.stubGlobal('ResizeObserver', class { constructor(fn) { resize = fn; } observe() {} disconnect() {} });
    vi.stubGlobal('WebGL2RenderingContext', function() {});
    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({}));
    const fixture = structuredClone(geoJson);
    fixture.features[0].properties.center = [108.15, 34.2];
    const wrapper = mount(PanoramaMap, { props: { geoJson: fixture, appearance: 'relief' } });
    let width = 900;
    Object.defineProperty(wrapper.element, 'clientWidth', { get: () => width });
    Object.defineProperty(wrapper.element, 'clientHeight', { get: () => 700 });
    resize(); await nextTick();
    const before = wrapper.get('.panorama-map__region-label-hit').attributes('style');
    width = 400;
    resize(); await nextTick();
    expect(wrapper.get('.panorama-map__region-label-hit').attributes('style')).not.toBe(before);
    wrapper.unmount();
    vi.unstubAllGlobals();
  });

});
