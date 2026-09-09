// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('three', async () => {
  const actual = await vi.importActual('three');
  class WebGLRendererStub {
    constructor(options) {
      this.domElement = options.canvas;
    }
    setPixelRatio() {}
    setClearColor() {}
    setSize() {}
    render() {}
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
});
