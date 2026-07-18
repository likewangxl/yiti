import { describe, it, expect } from 'vitest';
import { visibleCount, shouldCarousel, nextStart, windowIndices } from '../carousel';

describe('carousel.visibleCount（可视行数=容器高减表头后按行高取整，至少 1）', () => {
  it('常规计算', () => {
    expect(visibleCount(232, 40, 32)).toBe(6);   // (232-40)/32 = 6
    expect(visibleCount(250, 40, 32)).toBe(6);   // 向下取整
  });
  it('容器过矮时兜底 1 行', () => {
    expect(visibleCount(30, 40, 32)).toBe(1);
    expect(visibleCount(0, 0, 32)).toBe(1);
  });
});

describe('carousel.shouldCarousel（开关开 + 行数超出可视区才轮播）', () => {
  it('行数超出且开关开 → true', () => {
    expect(shouldCarousel(10, 6, true)).toBe(true);
  });
  it('行数未超 / 开关关 → false', () => {
    expect(shouldCarousel(6, 6, true)).toBe(false);
    expect(shouldCarousel(10, 6, false)).toBe(false);
  });
});

describe('carousel.nextStart（起始行环形推进）', () => {
  it('逐行推进并回绕', () => {
    expect(nextStart(0, 10)).toBe(1);
    expect(nextStart(9, 10)).toBe(0);
  });
  it('total<=0 兜底 0', () => {
    expect(nextStart(3, 0)).toBe(0);
  });
});

describe('carousel.windowIndices（环形窗口取行下标）', () => {
  it('总行数不超窗口时全量顺序返回', () => {
    expect(windowIndices(2, 6, 4)).toEqual([0, 1, 2, 3]);
  });
  it('从 start 起环形取 count 个（跨尾回绕）', () => {
    expect(windowIndices(0, 3, 5)).toEqual([0, 1, 2]);
    expect(windowIndices(3, 3, 5)).toEqual([3, 4, 0]);
  });
  it('空数据返回 []', () => {
    expect(windowIndices(0, 3, 0)).toEqual([]);
  });
});
