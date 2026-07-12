import { describe, it, expect } from 'vitest';
import { findWidget, findAttr, newComponentFromMeta, materialMetas, chartMetas } from '../index';

describe('widgets 注册表', () => {
  it('素材元数据 5 个 + 图表元数据 9 个', () => {
    expect(materialMetas.length).toBe(5);
    expect(chartMetas.length).toBe(9);
  });
  it('findWidget 能取到素材/图表渲染组件', () => {
    expect(findWidget('TextLabel')).toBeTruthy();
    expect(findWidget('ChartWidget')).toBeTruthy();
    expect(findWidget('NotExist')).toBeFalsy();
  });
  it('findAttr 能取到素材/图表的属性面板组件(Produces 接口)', () => {
    expect(findAttr('TextLabel')).toBeTruthy();
    expect(findAttr('ChartWidget')).toBeTruthy();
    expect(findAttr('NotExist')).toBeFalsy();
  });
  it('newComponentFromMeta 生成带 id/style/默认 propValue 的节点', () => {
    const n = newComponentFromMeta('TextLabel');
    expect(n.id).toMatch(/^w-/);
    expect(n.component).toBe('TextLabel');
    expect(n.style.width).toBeGreaterThan(0);
    // ChartWidget 需带 innerType 与 blockId(null 待保存 upsert)
    const c = newComponentFromMeta('ChartWidget', 'METRIC_CARD');
    expect(c.innerType).toBe('METRIC_CARD');
    expect(c.blockId).toBeNull();
  });
  it('newComponentFromMeta 对 enabled:false 的占位图表类型直接拒绝(注册表层兜底门禁,防面板层漏过滤)', () => {
    const disabled = chartMetas.find(c => c.enabled === false);
    expect(disabled).toBeTruthy(); // 前提:9 图表元数据里确有占位类型,否则本用例区分力为零
    expect(() => newComponentFromMeta('ChartWidget', disabled.innerType)).toThrow('该图表类型尚未启用');
  });
});
