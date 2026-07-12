// @vitest-environment happy-dom
// 挂载冒烟测试——回归 Task 9 复审 Critical 缺陷:删 `import { useRouter } from 'vue-router'` 时
// 漏删了调用行 `const router = useRouter();`,导致 <script setup> 顶层执行时 ReferenceError,
// 组件一挂载就崩溃。纯逻辑单测(store/registry/utils)测不到这类"setup 顶层执行期"错误,
// 必须真实 mount 才能捕获,故补这个最小冒烟测试。
// element-plus 组件与本页四个子组件(CanvasCore/ComponentPanel/LayerPanel/CanvasAttr)全部 stub,
// 只关心 DesignerV2 自身能否正常挂载,不关心子组件内部渲染细节(那些各自模块已有/未来有专属测试)。
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';

vi.mock('@/api/screen', () => ({
  listScreens: vi.fn().mockResolvedValue([]),
  getScreenCanvas: vi.fn(),
  saveScreenCanvas: vi.fn(),
  publishScreenCanvas: vi.fn(),
  rollbackScreenCanvas: vi.fn(),
  discardScreenCanvas: vi.fn(),
  listScreenPublishLogs: vi.fn()
}));

import DesignerV2 from '../DesignerV2.vue';

const stubs = {
  'el-select': true, 'el-option': true, 'el-button-group': true, 'el-button': true,
  'el-slider': true, 'el-tabs': true, 'el-tab-pane': true,
  CanvasCore: true, ComponentPanel: true, LayerPanel: true, CanvasAttr: true
};

describe('DesignerV2.vue 挂载冒烟测试', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('mount 不抛错', () => {
    expect(() => mount(DesignerV2, { global: { stubs } })).not.toThrow();
  });

  it('挂载后根节点带 scr-surface-host(画布根主题变量挂载点,附加验收 #1)', () => {
    const wrapper = mount(DesignerV2, { global: { stubs } });
    expect(wrapper.classes()).toContain('scr-surface-host');
  });

  it('卸载不抛错(keydown 监听器能正常移除)', () => {
    const wrapper = mount(DesignerV2, { global: { stubs } });
    expect(() => wrapper.unmount()).not.toThrow();
  });
});
