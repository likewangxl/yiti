import { describe, it, expect } from 'vitest';
import { findWidget, newComponentFromMeta, materialMetas, chartMetas } from '../index';

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
});
