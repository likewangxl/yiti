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
import { provinceCityColor } from '../provinceCityPalette';

const geoJson = {
  type: 'FeatureCollection',
  features: [{
    type: 'Feature',
    properties: { adcode: 610100, name: '西安市' },
    geometry: { type: 'Polygon', coordinates: [[[108, 34], [109, 34], [109, 35], [108, 35], [108, 34]]] }
  }]
};

describe('PanoramaMap WebGL 初始化', () => {
  it('悬停使同一城市所有地块变色，离开后恢复且不触发业务筛选', async () => {
    vi.stubGlobal('WebGL2RenderingContext', function() {});
    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({}));
    const fixture = structuredClone(geoJson);
    const ring = fixture.features[0].geometry.coordinates;
    fixture.features[0].geometry = { type: 'MultiPolygon', coordinates: [ring, ring.map(r => r.map(([x,y]) => [x + 1.2, y]))] };
    fixture.features.push({ type: 'Feature', properties: { adcode: 610300, name: '宝鸡市' }, geometry: { type: 'Polygon', coordinates: ring.map(r => r.map(([x,y]) => [x, y + 1.3])) } });
    const wrapper = mount(PanoramaMap, { props: { geoJson: fixture, appearance: 'relief', selectedRegionCode: '610300' } });
    await nextTick();
    const meshes = [];
    renderSpy.mock.calls.at(-1)[0].traverse(mesh => {
      if (mesh.isMesh && mesh.material?.[0]?.isMeshStandardMaterial) meshes.push(mesh);
    });
    expect(meshes).toHaveLength(3);
    const before = meshes.map(mesh => mesh.material[0].color.getHex());
    const label = wrapper.get('[aria-label="选择西安市"]');
    await label.trigger('pointerenter');
    expect(wrapper.attributes('data-hovered-region')).toBe('610100');
    meshes.forEach((mesh, index) => {
      if (String(mesh.userData.code) === '610100') expect(mesh.material[0].color.getHex()).not.toBe(before[index]);
      else expect(mesh.material[0].color.getHex()).toBe(before[index]);
    });
    expect(wrapper.emitted('region-select')).toBeUndefined();
    await label.trigger('pointerleave');
    expect(meshes.map(mesh => mesh.material[0].color.getHex())).toEqual(before);
    wrapper.unmount();
    vi.unstubAllGlobals();
  });
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
  it('省级 colorByCity 让 WebGL 顶面颜色与稳定地市 palette 一致', async () => {
    vi.stubGlobal('WebGL2RenderingContext', function WebGL2RenderingContext() {});
    vi.stubGlobal('WebGLRenderingContext', function WebGLRenderingContext() {});
    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({}));
    vi.stubGlobal('requestAnimationFrame', vi.fn(() => 0));
    vi.stubGlobal('cancelAnimationFrame', vi.fn());
    const fixture = {
      type: 'FeatureCollection',
      features: [
        ...geoJson.features,
        { type: 'Feature', properties: { adcode: 610300, name: '宝鸡市' }, geometry: { type: 'Polygon', coordinates: [[[109, 34], [110, 34], [110, 35], [109, 35], [109, 34]]] } }
      ]
    };
    const wrapper = mount(PanoramaMap, { props: { geoJson: fixture, appearance: 'relief', mode: 'province', colorByCity: true } });
    await nextTick();
    const meshes = [];
    renderSpy.mock.calls.at(-1)[0].traverse(mesh => {
      if (mesh.isMesh && mesh.userData?.type === 'region' && mesh.material?.[0]?.isMeshStandardMaterial) meshes.push(mesh);
    });
    expect(meshes.find(mesh => String(mesh.userData.code) === '610100').material[0].color.getHexString())
      .toBe(provinceCityColor('610100').slice(1));
    expect(meshes.find(mesh => String(mesh.userData.code) === '610300').material[0].color.getHexString())
      .toBe(provinceCityColor('610300').slice(1));
    expect(wrapper.attributes('data-color-by-city')).toBe('true');
    const before = meshes.map(mesh => mesh.material[0].color.getHexString());
    await wrapper.get('[aria-label="选择西安市"]').trigger('pointerenter');
    expect(meshes.find(mesh => String(mesh.userData.code) === '610100').material[0].color.getHexString())
      .not.toBe(before[meshes.findIndex(mesh => String(mesh.userData.code) === '610100')]);
    expect(meshes.find(mesh => String(mesh.userData.code) === '610300').material[0].color.getHexString())
      .toBe(before[meshes.findIndex(mesh => String(mesh.userData.code) === '610300')]);
    await wrapper.get('[aria-label="选择西安市"]').trigger('pointerleave');
    expect(meshes.find(mesh => String(mesh.userData.code) === '610100').material[0].color.getHexString())
      .toBe(before[meshes.findIndex(mesh => String(mesh.userData.code) === '610100')]);
    wrapper.unmount();
    vi.unstubAllGlobals();
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
