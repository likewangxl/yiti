// @vitest-environment happy-dom
// 右栏属性面板宽度回归——修复 260px 栏内 el-input-number 默认宽度(~150px)两列放不下、
// Y/高输入框被截断的缺陷:input-number 必须用 controls-position="right"(±按钮竖排靠右,
// 窄栏可用),宽度铺满由 scoped CSS 控制(单测不覆盖样式,只锁模板契约)。
import { describe, it, expect, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import CommonAttr from '../panels/CommonAttr.vue';

const stubs = {
  'el-collapse': { template: '<div><slot /></div>' },
  'el-collapse-item': { template: '<div><slot /></div>' },
  'el-form': { template: '<form @submit.prevent><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input-number': true,
  'el-slider': true
};

describe('CommonAttr.vue 窄栏适配', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('位置与尺寸的 4 个数字输入均为 controls-position=right(否则 260px 栏内被截断)', () => {
    const wrapper = mount(CommonAttr, {
      props: { element: { style: { top: 0, left: 0, width: 100, height: 80, opacity: 1 } } },
      global: { stubs }
    });
    const inputs = wrapper.findAll('el-input-number-stub');
    expect(inputs.length).toBe(4);
    for (const i of inputs) {
      expect(i.attributes('controls-position')).toBe('right');
    }
  });
});
