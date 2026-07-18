// 框选纯函数测试(TDD Red 先行)——画布空白拖拽框选:两点归一化矩形、矩形求交、
// 命中组件集合(跳过锁定/隐藏)。坐标全部为 1920×1080 设计态像素。
import { describe, it, expect } from 'vitest';
import { normalizeRect, rectsIntersect, hitComponents } from '../marquee';

describe('normalizeRect 两点归一化矩形', () => {
  it('右下方向拖拽', () => {
    expect(normalizeRect({ x: 100, y: 50 }, { x: 300, y: 200 }))
      .toEqual({ top: 50, left: 100, width: 200, height: 150 });
  });

  it('左上方向反向拖拽(起点在右下)', () => {
    expect(normalizeRect({ x: 300, y: 200 }, { x: 100, y: 50 }))
      .toEqual({ top: 50, left: 100, width: 200, height: 150 });
  });

  it('原地点击宽高为 0', () => {
    expect(normalizeRect({ x: 10, y: 10 }, { x: 10, y: 10 }))
      .toEqual({ top: 10, left: 10, width: 0, height: 0 });
  });
});

describe('rectsIntersect 矩形求交', () => {
  const a = { top: 0, left: 0, width: 100, height: 100 };

  it('相交为 true', () => {
    expect(rectsIntersect(a, { top: 50, left: 50, width: 100, height: 100 })).toBe(true);
  });

  it('包含为 true', () => {
    expect(rectsIntersect(a, { top: 10, left: 10, width: 20, height: 20 })).toBe(true);
  });

  it('相离为 false', () => {
    expect(rectsIntersect(a, { top: 200, left: 200, width: 50, height: 50 })).toBe(false);
  });

  it('仅边缘贴合(交集面积为 0)为 false', () => {
    expect(rectsIntersect(a, { top: 0, left: 100, width: 50, height: 50 })).toBe(false);
    expect(rectsIntersect(a, { top: 100, left: 0, width: 50, height: 50 })).toBe(false);
  });
});

describe('hitComponents 框选命中(与组件矩形求交,跳过锁定/隐藏)', () => {
  const comps = [
    { id: 'a', isLock: false, isShow: true, style: { top: 0, left: 0, width: 100, height: 100 } },
    { id: 'b', isLock: true, isShow: true, style: { top: 0, left: 150, width: 100, height: 100 } },
    { id: 'c', isLock: false, isShow: false, style: { top: 0, left: 300, width: 100, height: 100 } },
    { id: 'd', isLock: false, isShow: true, style: { top: 500, left: 500, width: 100, height: 100 } },
    { id: 'e', isLock: false, isShow: true, style: { top: 50, left: 380, width: 100, height: 100 } }
  ];

  it('命中相交组件,锁定(b)与隐藏(c)跳过,相离(d)不命中', () => {
    const rect = { top: 10, left: 10, width: 400, height: 80 }; // 覆盖 a/b/c/e 区域
    expect(hitComponents(rect, comps)).toEqual(['a', 'e']);
  });

  it('isShow 缺省(undefined)视为可见可命中', () => {
    const comps2 = [{ id: 'x', isLock: false, style: { top: 0, left: 0, width: 50, height: 50 } }];
    expect(hitComponents({ top: 0, left: 0, width: 100, height: 100 }, comps2)).toEqual(['x']);
  });

  it('无命中返回空数组', () => {
    expect(hitComponents({ top: 900, left: 1800, width: 10, height: 10 }, comps)).toEqual([]);
  });
});
