// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';

const threeState = vi.hoisted(() => ({
  instances: [],
  renderError: null,
  resizeError: null
}));

vi.mock('three', async () => {
  const actual = await vi.importActual('three');
  class WebGLRendererStub {
    constructor(options) {
      this.domElement = options.canvas;
      this.forceContextLoss = vi.fn();
      this.dispose = vi.fn();
      threeState.instances.push(this);
    }

    setPixelRatio() {}
    setClearColor() {}
    setSize() {
      if (threeState.resizeError) throw threeState.resizeError;
    }
    render() {
      if (threeState.renderError) throw threeState.renderError;
    }
  }
  return { ...actual, WebGLRenderer: WebGLRendererStub };
});

import PanoramaMap from '../PanoramaMap.vue';

const geoJson = {
  type: 'FeatureCollection',
  features: [{
    type: 'Feature',
    properties: { adcode: 610600, name: '延安市' },
    geometry: { type: 'Polygon', coordinates: [[[108, 35], [109, 35], [109, 36], [108, 36], [108, 35]]] }
  }]
};

let originalGetContext;

beforeEach(() => {
  threeState.instances.length = 0;
  threeState.renderError = null;
  threeState.resizeError = null;
  originalGetContext = HTMLCanvasElement.prototype.getContext;
  vi.stubGlobal('WebGL2RenderingContext', function WebGL2RenderingContext() {});
  vi.stubGlobal('WebGLRenderingContext', function WebGLRenderingContext() {});
  vi.stubGlobal('requestAnimationFrame', vi.fn(() => 0));
  vi.stubGlobal('cancelAnimationFrame', vi.fn());
});

afterEach(() => {
  HTMLCanvasElement.prototype.getContext = originalGetContext;
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function mountMap() {
  return mount(PanoramaMap, { props: { geoJson } });
}

describe('PanoramaMap WebGL 故障回退与资源生命周期', () => {
  it('无 WebGL2 时不降级使用 WebGL1，直接显示真实 SVG 回退', async () => {
    HTMLCanvasElement.prototype.getContext = vi.fn(type => (type === 'webgl2' ? null : {}));

    const wrapper = mountMap();
    await nextTick();

    expect(wrapper.attributes('data-webgl-ready')).toBe('false');
    expect(wrapper.find('.panorama-map__fallback').exists()).toBe(true);
    expect(wrapper.find('.panorama-map__fallback path').exists()).toBe(true);
    expect(wrapper.find('.panorama-map__canvas').classes()).toContain('is-hidden');
    expect(threeState.instances).toHaveLength(0);
    wrapper.unmount();
  });

  it('contextlost 立即停绘制、释放资源并隐藏 canvas，保留 SVG 城市交互', async () => {
    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({}));

    const wrapper = mountMap();
    await nextTick();
    const renderer = threeState.instances[0];
    expect(renderer).toBeTruthy();

    wrapper.find('canvas').element.dispatchEvent(new Event('webglcontextlost', { cancelable: true }));
    await nextTick();

    expect(wrapper.attributes('data-webgl-ready')).toBe('false');
    expect(wrapper.find('.panorama-map__fallback').exists()).toBe(true);
    expect(wrapper.find('.panorama-map__canvas').classes()).toContain('is-hidden');
    expect(renderer.forceContextLoss).toHaveBeenCalledTimes(1);
    expect(wrapper.find('.panorama-map__fallback path').exists()).toBe(true);
    wrapper.unmount();
  });

  it('render 与 resize 异常都切换到真实 SVG 回退而不让异常遮住地图', async () => {
    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({}));
    const wrapper = mountMap();
    await nextTick();
    expect(threeState.instances).toHaveLength(1);

    threeState.renderError = new Error('render failed');
    await wrapper.get('button[aria-label="放大地图"]').trigger('click');
    await nextTick();
    expect(wrapper.find('.panorama-map__fallback').exists()).toBe(true);
    expect(wrapper.find('.panorama-map__canvas').classes()).toContain('is-hidden');

    wrapper.unmount();

    threeState.renderError = null;
    vi.stubGlobal('ResizeObserver', undefined);
    const resizeWrapper = mountMap();
    await nextTick();
    threeState.resizeError = new Error('resize failed');
    window.dispatchEvent(new Event('resize'));
    await nextTick();
    expect(resizeWrapper.find('.panorama-map__fallback').exists()).toBe(true);
    expect(resizeWrapper.find('.panorama-map__canvas').classes()).toContain('is-hidden');
    resizeWrapper.unmount();
  });

  it('静态场景不启动持续 RAF，重复重建复用同一 WebGL context，卸载强制释放 context', async () => {
    HTMLCanvasElement.prototype.getContext = vi.fn(() => ({}));
    const wrapper = mountMap();
    await nextTick();
    expect(requestAnimationFrame).not.toHaveBeenCalled();
    expect(threeState.instances).toHaveLength(1);

    await wrapper.setProps({ selectedRegionCode: '610600' });
    await nextTick();
    expect(threeState.instances).toHaveLength(1);

    const renderer = threeState.instances[0];
    wrapper.unmount();
    expect(renderer.forceContextLoss).toHaveBeenCalledTimes(1);
    expect(renderer.dispose).toHaveBeenCalledTimes(1);
  });
});
