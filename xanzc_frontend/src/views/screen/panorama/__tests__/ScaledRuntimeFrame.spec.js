// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import ScaledRuntimeFrame from '../ScaledRuntimeFrame.vue';

describe('ScaledRuntimeFrame', () => {
  let resizeCallback; let resizeDisconnect; let mutationOptions;
  beforeEach(() => {
    global.ResizeObserver = class { constructor(callback) { resizeCallback = callback; } observe() {} disconnect() { resizeDisconnect = true; } };
    global.MutationObserver = class { constructor() {} observe(_target, options) { mutationOptions = options; } disconnect() { resizeDisconnect = true; } };
  });
  afterEach(() => { document.head.querySelectorAll('[data-test-scaled-style]').forEach(node => node.remove()); vi.restoreAllMocks(); });

  it('保持设计画布尺寸并按外部容器等比缩放', async () => {
    const wrapper = mount(ScaledRuntimeFrame, { props: { width: 1920, height: 1080 }, slots: { default: '<div data-testid="runtime-slot">运行态</div>' } });
    const frame = wrapper.get('iframe');
    expect(frame.attributes('width')).toBe('1920');
    expect(frame.attributes('height')).toBe('1080');
    expect(wrapper.get('[data-testid="scaled-runtime-frame"]')).toBeTruthy();
    expect(wrapper.emitted('layout')).toBeTruthy();
    wrapper.unmount();
  });

  it('设计宽高变化后更新 iframe 尺寸', async () => {
    const wrapper = mount(ScaledRuntimeFrame, { props: { width: 1600, height: 900 }, slots: { default: '<div />' } });
    expect(wrapper.get('iframe').attributes('width')).toBe('1600');
    await wrapper.setProps({ width: 1280, height: 720 });
    expect(wrapper.get('iframe').attributes('width')).toBe('1280');
    expect(wrapper.get('iframe').attributes('height')).toBe('720');
  });

  it('按真实容器宽度计算 scale，复制带 base 的样式并监听 HMR 变更', async () => {
    const style = document.createElement('style'); style.dataset.testScaledStyle = 'true'; style.textContent = '.runtime-test{color:red}'; document.head.appendChild(style);
    const link = document.createElement('link'); link.rel = 'stylesheet'; link.href = 'data:text/css,.runtime-test%7Bcolor:red%7D'; link.dataset.testScaledStyle = 'true'; document.head.appendChild(link);
    const wrapper = mount(ScaledRuntimeFrame, { props: { width: 1920, height: 1080 }, slots: { default: '<div />' } });
    Object.defineProperty(wrapper.element, 'clientWidth', { configurable: true, value: 480 });
    resizeCallback();
    const frameElement = wrapper.get('iframe').element;
    const frameDocument = document.implementation.createHTMLDocument('frame');
    Object.defineProperty(frameElement, 'contentDocument', { configurable: true, value: frameDocument });
    Object.defineProperty(frameElement, 'contentWindow', { configurable: true, value: frameDocument.defaultView });
    frameElement.dispatchEvent(new Event('load'));
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(wrapper.get('iframe').element.contentDocument.body.style.margin).toBe('0px');
    expect(wrapper.emitted('layout').at(-1)[0].scale).toBe(0.25);
    expect(mutationOptions).toMatchObject({ childList: true, subtree: true, characterData: true, attributes: true });
    expect(wrapper.get('iframe').element.contentDocument.head.querySelector('base').href).toBe(document.baseURI);
    expect(wrapper.get('iframe').element.contentDocument.head.querySelectorAll('[data-scaled-runtime-clone]').length).toBeGreaterThan(1);
    wrapper.unmount();
    expect(resizeDisconnect).toBe(true);
  });

  it('iframe reload 会重建 Teleport 目标并再次 ready', async () => {
    const wrapper = mount(ScaledRuntimeFrame, { slots: { default: '<div data-testid="runtime-slot">运行态</div>' } });
    const frame = wrapper.get('iframe').element;
    frame.dispatchEvent(new Event('load'));
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(wrapper.emitted('ready')?.length).toBeGreaterThanOrEqual(1);
    frame.dispatchEvent(new Event('load'));
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(wrapper.emitted('ready')?.length).toBeGreaterThanOrEqual(2);
  });
});
