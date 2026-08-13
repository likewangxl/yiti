// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import BpAdaptiveRowActions from '@/components/BpAdaptiveRowActions.vue';

class ResizeObserverHarness {
  static instances = [];

  constructor(callback) {
    this.callback = callback;
    ResizeObserverHarness.instances.push(this);
  }

  observe() {}
  unobserve() {}
  disconnect() {}

  flush() {
    this.callback([]);
  }
}

const previousResizeObserver = globalThis.ResizeObserver;

function setMeasuredWidths(wrapper, available, required) {
  Object.defineProperty(wrapper.get('[data-bp-row-actions-host]').element, 'clientWidth', {
    configurable: true,
    value: available
  });
  Object.defineProperty(wrapper.get('[data-bp-row-actions-probe]').element, 'scrollWidth', {
    configurable: true,
    value: required
  });
}

function mountActions() {
  return mount(BpAdaptiveRowActions, {
    slots: {
      primary: '<button type="button">编辑</button>',
      expanded: '<button type="button">附件</button><button type="button" class="danger">删除</button>',
      compact: '<button type="button" aria-label="更多产品资料操作">更多</button>'
    }
  });
}

beforeEach(() => {
  ResizeObserverHarness.instances = [];
  globalThis.ResizeObserver = ResizeObserverHarness;
});

afterEach(() => {
  globalThis.ResizeObserver = previousResizeObserver;
});

describe('普通后台响应式行操作', () => {
  it('ResizeObserver 不可用的 SSR/测试环境安全回退到紧凑态', async () => {
    globalThis.ResizeObserver = undefined;
    const wrapper = mountActions();
    await nextTick();

    expect(wrapper.get('[data-bp-row-actions-host]').attributes()).toMatchObject({
      'data-mode': 'compact',
      'data-measured': 'true'
    });
    expect(wrapper.get('[data-bp-row-actions-visible]').text()).toBe('编辑更多');
  });

  it('无法取得有效尺寸时 fail-close 到主操作加更多，且测量副本不进入键盘顺序', async () => {
    const wrapper = mountActions();
    await nextTick();

    setMeasuredWidths(wrapper, 0, 0);
    ResizeObserverHarness.instances[0].flush();
    await nextTick();

    expect(wrapper.get('[data-bp-row-actions-host]').attributes('data-mode')).toBe('compact');
    expect(wrapper.get('[data-bp-row-actions-visible]').text()).toBe('编辑更多');
    expect(wrapper.get('[data-bp-row-actions-probe]').attributes()).toMatchObject({
      'aria-hidden': 'true',
      inert: ''
    });
    expect(wrapper.findAll('[data-bp-row-actions-visible] button').map((node) => node.text())).toEqual(['编辑', '更多']);
  });

  it('按真实测量在同一行空间充足时直出全部操作，危险操作也保留语义和键盘顺序', async () => {
    const wrapper = mountActions();
    await nextTick();

    setMeasuredWidths(wrapper, 180, 160);
    ResizeObserverHarness.instances[0].flush();
    await nextTick();

    expect(wrapper.get('[data-bp-row-actions-host]').attributes('data-mode')).toBe('expanded');
    expect(wrapper.findAll('[data-bp-row-actions-visible] button').map((node) => node.text())).toEqual(['编辑', '附件', '删除']);
    expect(wrapper.get('[data-bp-row-actions-visible] .danger').text()).toBe('删除');
  });

  it('ResizeObserver 检测到空间不足时恢复既有主操作加更多，不依赖操作文本长度硬编码', async () => {
    const wrapper = mountActions();
    await nextTick();

    setMeasuredWidths(wrapper, 180, 160);
    ResizeObserverHarness.instances[0].flush();
    await nextTick();
    expect(wrapper.get('[data-bp-row-actions-host]').attributes('data-mode')).toBe('expanded');

    setMeasuredWidths(wrapper, 120, 160);
    ResizeObserverHarness.instances[0].flush();
    await nextTick();
    expect(wrapper.get('[data-bp-row-actions-host]').attributes('data-mode')).toBe('compact');
    expect(wrapper.get('[data-bp-row-actions-visible]').text()).toBe('编辑更多');
  });
});
