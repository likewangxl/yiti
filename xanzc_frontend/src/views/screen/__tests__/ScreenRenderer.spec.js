// @vitest-environment happy-dom
// ScreenRenderer 绝对定位渲染——运行时读发布态/草稿态渲染包(canvasStyle + components + bindSnapshots),
// 组件按 style.top/left/width/height 绝对定位铺在 1920×1080 画布。
// ChartWidget 从 bindSnapshots 合成 block 复用 BlockContainer;MapCenter 走独立 mapPoints 分支
// (不经 widgets 通用 element/mode 注册——MapCenter.vue 的 props 签名是 mapPoints 数组，与素材类
// element/propValue 完全不同，若走简报原样的通用 <component :element mode> 会拿不到点位数据，
// 属简报之外发现的真实 bug，本测试专门覆盖，详见 impl-t10 报告)。
import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ScreenRenderer from '../components/ScreenRenderer.vue';

const stubs = {
  BlockContainer: {
    template: '<div class="stub-block" :data-bind="block && block.bindJson" '
      + ':data-style="block && block.styleJson" :data-drill="block && block.drillJson" '
      + ':data-type="block && block.componentType" />',
    props: ['block', 'context']
  },
  MapCenter: {
    template: '<div class="stub-map" :data-points="JSON.stringify(mapPoints)" '
      + ':data-region-metrics="JSON.stringify(regionMetrics)" :data-runtime-header-inset="runtimeHeaderInset" />',
    props: ['mapPoints', 'regionMetrics', 'runtimeHeaderInset']
  }
};

function pkg(components, bindSnapshots = {}, canvasStyle = {}) {
  return { schemaVersion: 1, canvasStyle, components, bindSnapshots };
}

describe('ScreenRenderer.vue', () => {
  it('renderPackage 缺省时不抛错(空 components/bindSnapshots/canvasStyle 兜底)', () => {
    expect(() => mount(ScreenRenderer, { global: { stubs } })).not.toThrow();
  });

  it('组件节点缺 style 时不抛错(absStyle 兜底 top/left/width/height=0),rev-t10 复审 Minor', () => {
    const c = { id: 'w0', component: 'TextLabel', propValue: {}, isShow: true }; // 无 style 字段
    let wrapper;
    expect(() => { wrapper = mount(ScreenRenderer, { props: { renderPackage: pkg([c]) }, global: { stubs } }); }).not.toThrow();
    expect(wrapper.find('.scr-abs').attributes('style')).toContain('top: 0px');
  });

  it('素材组件(TextLabel)按 style 绝对定位', () => {
    const c = { id: 'w1', component: 'TextLabel', style: { top: 10, left: 20, width: 300, height: 40 },
      propValue: { text: 'hi' }, isShow: true };
    const wrapper = mount(ScreenRenderer, { props: { renderPackage: pkg([c]) }, global: { stubs } });
    const style = wrapper.find('.scr-abs').attributes('style');
    expect(style).toContain('position: absolute');
    expect(style).toContain('top: 10px');
    expect(style).toContain('left: 20px');
    expect(style).toContain('width: 300px');
    expect(style).toContain('height: 40px');
  });

  it('isShow:false 的组件不可见(v-show 生成 display:none)', () => {
    // 不用 isVisible():happy-dom 无真实布局引擎，其可见性判定不可靠；直接断言 v-show 落的内联样式。
    const c = { id: 'w2', component: 'TextLabel', style: { top: 0, left: 0, width: 10, height: 10 },
      propValue: {}, isShow: false };
    const wrapper = mount(ScreenRenderer, { props: { renderPackage: pkg([c]) }, global: { stubs } });
    expect(wrapper.find('.scr-abs').attributes('style')).toContain('display: none');
  });

  it('ChartWidget 从 bindSnapshots 合成 block(bindJson/styleJson/drillJson 字符串化)复用 BlockContainer', () => {
    const c = { id: 'w3', component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 501,
      style: { top: 0, left: 0, width: 200, height: 100 }, isShow: true };
    const snap = { bind: { dsId: 9 }, componentType: 'METRIC_CARD', styleCfg: { title: '存款' }, drill: { drillEnabled: false } };
    const wrapper = mount(ScreenRenderer, {
      props: { renderPackage: pkg([c], { 501: snap }), context: { orgCode: 'O1' } },
      global: { stubs }
    });
    const stub = wrapper.find('.stub-block');
    expect(stub.exists()).toBe(true);
    expect(stub.attributes('data-bind')).toBe(JSON.stringify({ dsId: 9 }));
    expect(stub.attributes('data-style')).toBe(JSON.stringify({ title: '存款' }));
    expect(stub.attributes('data-drill')).toBe(JSON.stringify({ drillEnabled: false }));
    expect(stub.attributes('data-type')).toBe('METRIC_CARD');
  });

  it('ChartWidget 缺 bindSnapshot(如草稿预览态 bindSnapshots 恒空)不裸传 null 崩溃,渲染占位', () => {
    const c = { id: 'w4', component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 999,
      style: { top: 0, left: 0, width: 200, height: 100 }, isShow: true };
    expect(() => mount(ScreenRenderer, { props: { renderPackage: pkg([c], {}) }, global: { stubs } })).not.toThrow();
    const wrapper = mount(ScreenRenderer, { props: { renderPackage: pkg([c], {}) }, global: { stubs } });
    expect(wrapper.find('.stub-block').exists()).toBe(false);
    expect(wrapper.find('.scr-abs-empty').exists()).toBe(true);
  });

  it('MapCenter 走独立分支,接收外部传入的 mapPoints(而非通用 element/mode)', () => {
    const c = { id: 'w5', component: 'MapCenter', style: { top: 0, left: 0, width: 640, height: 880 }, isShow: true };
    const points = [{ orgCode: 'O1', orgName: '西安', lng: 108.9, lat: 34.2 }];
    const wrapper = mount(ScreenRenderer, {
      props: { renderPackage: pkg([c]), mapPoints: points },
      global: { stubs }
    });
    const stub = wrapper.find('.stub-map');
    expect(stub.exists()).toBe(true);
    expect(JSON.parse(stub.attributes('data-points'))).toEqual(points);
  });

  it('MapCenter 接收服务端授权后的地图机构指标，不从画布 JSON 猜测指标数据', () => {
    const c = { id: 'w-map-kpi', component: 'MapCenter', style: { top: 0, left: 0, width: 640, height: 880 }, isShow: true };
    const metrics = [{ orgCode: '128', metricValues: { KPI_ACHIEVE_RATE_ORG: 86.2 } }];
    const wrapper = mount(ScreenRenderer, {
      props: { renderPackage: pkg([c]), mapRegionMetrics: metrics },
      global: { stubs }
    });
    expect(JSON.parse(wrapper.find('.stub-map').attributes('data-region-metrics'))).toEqual(metrics);
  });

  it.each([
    ['1920x1080', 1],
    ['2560x1440', 2560 / 1920]
  ])('%s 运行态复合地图标题必须避开外层 72px 标题浮层', (_viewport, stageScale) => {
    const c = { id: 'map-top', component: 'MapCenter', style: { top: 0, left: 0, width: 1920, height: 1080 }, isShow: true };
    const wrapper = mount(ScreenRenderer, {
      props: { renderPackage: pkg([c]) },
      global: { stubs }
    });
    const inset = Number(wrapper.find('.stub-map').attributes('data-runtime-header-inset') || 0);
    const outerHeaderBottom = 72 * stageScale;
    const innerTitleTop = (c.style.top + inset) * stageScale;
    expect(innerTitleTop).toBeGreaterThanOrEqual(outerHeaderBottom);
  });

  it('canvasStyle.background 缺省时舞台透明', () => {
    const wrapper = mount(ScreenRenderer, { props: { renderPackage: pkg([]) }, global: { stubs } });
    expect(wrapper.find('.scr-canvas-render').attributes('style')).toContain('transparent');
  });

  it('Group 节点展开为绝对坐标子节点渲染(组左上角+子相对坐标,子 ChartWidget 仍走 bindSnapshots)', () => {
    const g = { id: 'g1', component: 'Group', style: { top: 100, left: 200, width: 500, height: 300 },
      isShow: true,
      children: [
        { id: 'w6', component: 'TextLabel', style: { top: 10, left: 20, width: 100, height: 40 }, propValue: { text: 'in' }, isShow: true },
        { id: 'w7', component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 502,
          style: { top: 60, left: 0, width: 200, height: 100 }, isShow: true }
      ] };
    const snap = { bind: { dsId: 1 }, componentType: 'METRIC_CARD', styleCfg: {}, drill: {} };
    const wrapper = mount(ScreenRenderer, { props: { renderPackage: pkg([g], { 502: snap }) }, global: { stubs } });
    const abs = wrapper.findAll('.scr-abs');
    expect(abs.length).toBe(2); // Group 自身不渲染节点,只展开 children
    expect(abs[0].attributes('style')).toContain('top: 110px');   // 100+10
    expect(abs[0].attributes('style')).toContain('left: 220px');  // 200+20
    expect(wrapper.find('.stub-block').attributes('data-type')).toBe('METRIC_CARD');
  });

  it('Group isShow:false 时整组子组件不渲染', () => {
    const g = { id: 'g2', component: 'Group', style: { top: 0, left: 0, width: 100, height: 100 },
      isShow: false,
      children: [{ id: 'w8', component: 'TextLabel', style: { top: 0, left: 0, width: 10, height: 10 }, propValue: {}, isShow: true }] };
    const wrapper = mount(ScreenRenderer, { props: { renderPackage: pkg([g]) }, global: { stubs } });
    expect(wrapper.findAll('.scr-abs').length).toBe(0);
  });
});
