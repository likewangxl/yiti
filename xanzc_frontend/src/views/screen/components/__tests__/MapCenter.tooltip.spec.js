// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('vue-echarts', () => ({
  default: { name: 'VChart', props: ['option'], emits: ['click'], template: '<div class="v-chart-stub" />' }
}));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));

import MapCenter from '../MapCenter.vue';

const originalViewport = { width: window.innerWidth, height: window.innerHeight };

function rect(left, top, width, height) {
  return { left, top, right: left + width, bottom: top + height, width, height };
}

function setRect(element, value) {
  vi.spyOn(element, 'getBoundingClientRect').mockReturnValue(value);
}

function mountMap(mapConfig) {
  const wrapper = mount(MapCenter, { props: { mapConfig, mapPoints: [] } });
  const root = wrapper.get('.mp-block').element;
  const chart = wrapper.get('.mp-chart').element;
  setRect(root, rect(300, 100, 400, 300));
  setRect(chart, rect(320, 120, 200, 130));
  Object.defineProperty(chart, 'clientWidth', { configurable: true, value: 400 });
  Object.defineProperty(chart, 'clientHeight', { configurable: true, value: 260 });
  return { wrapper, chart };
}

afterEach(() => {
  vi.restoreAllMocks();
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: originalViewport.width });
  Object.defineProperty(window, 'innerHeight', { configurable: true, value: originalViewport.height });
});

describe('MapCenter hover tooltip 外侧定位', () => {
  it.each([
    ['v1 省级', { schemaVersion: 1, mode: 'SHAANXI_LEGACY' }, 'shaanxi'],
    ['v2 西安复合地图', { schemaVersion: 2, mode: 'XIAN_COMPOSITE', satelliteNodes: [] }, 'xian-six-districts'],
    ['v1 地市区县', { schemaVersion: 1, mode: 'SHAANXI_LEGACY', regionCode: '610300' }, 'shaanxi-city-610300']
  ])('%s 的地区与 scatter tooltip 共用 viewport 定位回调', (_label, mapConfig, mapName) => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1200 });
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 800 });
    const { wrapper } = mountMap(mapConfig);
    const option = wrapper.findComponent({ name: 'VChart' }).props('option');

    expect(option.geo.map).toBe(mapName);
    expect(option.tooltip.renderMode).toBe('html');
    expect(option.tooltip.appendToBody).toBe(true);
    expect(option.tooltip.confine).toBe(false);
    expect(option.tooltip.className).toBe('mp-map-tooltip');
    expect(option.tooltip.transitionDuration).toBe(0);
    expect(option.tooltip.position).toBeTypeOf('function');
    expect(option.series.at(-1).tooltip).not.toHaveProperty('position');
    expect(option.series.at(-1).tooltip).not.toHaveProperty('appendToBody');

    const tooltipDom = document.createElement('div');
    const position = option.tooltip.position([240, 120], {}, tooltipDom, null, { contentSize: [150, 60] });

    // 图表可见矩形是逻辑 400×260 的 0.5 缩放；MapCenter 矩形用于计算外侧位置。
    // label viewport point=(440,180)，helper 在 MapCenter 左侧给出 (140,150)，再逆变换到图表坐标。
    expect(position[0]).toBeCloseTo(-360);
    expect(position[1]).toBeCloseTo(60);
    expect(tooltipDom.style.display).not.toBe('none');
    wrapper.unmount();
  });

  it('视口无地图外可用空间时隐藏 tooltip，并设置尺寸上限与纵向滚动', () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1200 });
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 800 });
    const { wrapper } = mountMap({ schemaVersion: 1, mode: 'SHAANXI_LEGACY' });
    const option = wrapper.findComponent({ name: 'VChart' }).props('option');
    const tooltipDom = document.createElement('div');
    expect(option.tooltip.extraCssText).toMatch(/max-width/i);
    expect(option.tooltip.extraCssText).toMatch(/max-height/i);
    expect(option.tooltip.extraCssText).toMatch(/overflow-y:\s*auto/i);

    setRect(wrapper.get('.mp-block').element, rect(0, 0, 1200, 800));
    option.tooltip.position([240, 120], {}, tooltipDom, null, { contentSize: [150, 60] });

    expect(tooltipDom.style.display).toBe('none');
    wrapper.unmount();
  });

  it('窄屏只能上下回退时将 tooltip 高度限制为地图外可用空间', () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 420 });
    Object.defineProperty(window, 'innerHeight', { configurable: true, value: 760 });
    const { wrapper } = mountMap({ schemaVersion: 1, mode: 'SHAANXI_LEGACY' });
    const root = wrapper.get('.mp-block').element;
    const chart = wrapper.get('.mp-chart').element;
    setRect(root, rect(10, 100, 400, 550));
    setRect(chart, rect(20, 120, 200, 130));
    const option = wrapper.findComponent({ name: 'VChart' }).props('option');
    const tooltipDom = document.createElement('div');

    option.tooltip.position([240, 120], {}, tooltipDom, null, { contentSize: [200, 180] });

    expect(tooltipDom.style.maxHeight).toBe('88px');
    expect(tooltipDom.style.width).toBe('200px');
    expect(tooltipDom.style.overflowY).toBe('auto');
    wrapper.unmount();
  });
});
