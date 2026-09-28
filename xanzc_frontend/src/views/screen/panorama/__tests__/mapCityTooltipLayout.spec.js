import { describe, expect, it } from 'vitest';
import { positionCityTooltip } from '../mapCityTooltipLayout';

function rect(left, top, width, height) {
  return { left, top, right: left + width, bottom: top + height, width, height };
}

function intersects(left, top, width, height, other) {
  return left < other.right && left + width > other.left
    && top < other.bottom && top + height > other.top;
}

describe('机构模式地市 popup 外侧定位', () => {
  it('左列和右列分别放到地图矩形外，并以标签垂直中心为锚', () => {
    const mapRect = rect(300, 180, 420, 520);
    const viewport = { width: 1200, height: 900 };
    const left = positionCityTooltip({
      mapRect, labelRect: rect(314, 310, 86, 36), tooltipWidth: 220, tooltipHeight: 180,
      viewport, side: 'left', gap: 12, margin: 12
    });
    const right = positionCityTooltip({
      mapRect, labelRect: rect(630, 420, 86, 36), tooltipWidth: 220, tooltipHeight: 180,
      viewport, side: 'right', gap: 12, margin: 12
    });

    expect(left.placement).toBe('left');
    expect(left.left + left.width).toBeLessThanOrEqual(mapRect.left - 12);
    expect(left.top + left.height / 2).toBeCloseTo(328, 4);
    expect(intersects(left.left, left.top, left.width, left.height, mapRect)).toBe(false);
    expect(right.placement).toBe('right');
    expect(right.left).toBeGreaterThanOrEqual(mapRect.right + 12);
    expect(right.top + right.height / 2).toBeCloseTo(438, 4);
    expect(intersects(right.left, right.top, right.width, right.height, mapRect)).toBe(false);
  });

  it('两侧无空间时退到地图上下方并限制最大高度', () => {
    const mapRect = rect(0, 120, 400, 520);
    const result = positionCityTooltip({
      mapRect, labelRect: rect(180, 360, 80, 32), tooltipWidth: 240, tooltipHeight: 360,
      viewport: { width: 420, height: 700 }, side: 'left', gap: 8, margin: 8
    });

    expect(['above', 'below']).toContain(result.placement);
    expect(result.maxHeight).toBeLessThanOrEqual(700 - 16);
    expect(result.left).toBeGreaterThanOrEqual(8);
    expect(result.left + result.width).toBeLessThanOrEqual(420 - 8);
  });

  it('真实宽屏地图两侧和原始高度都不足时缩短到下方可用空间，不与地图相交', () => {
    const mapRect = rect(24, 100, 976, 550);
    const viewport = { width: 1024, height: 768 };
    const result = positionCityTooltip({
      mapRect, labelRect: rect(890, 390, 86, 36), tooltipWidth: 304, tooltipHeight: 230,
      viewport, side: 'right', gap: 10, margin: 12
    });

    expect(result.placement).toBe('below');
    expect(result.top).toBe(660);
    expect(result.height).toBe(96);
    expect(result.maxHeight).toBe(96);
    expect(result.top + result.height).toBeLessThanOrEqual(viewport.height - 12);
    expect(intersects(result.left, result.top, result.width, result.height, mapRect)).toBe(false);
  });

  it('上下空间均为零时返回 unavailable，调用方不应显示 popup', () => {
    const result = positionCityTooltip({
      mapRect: rect(0, 0, 1024, 768), labelRect: rect(900, 360, 80, 30), tooltipWidth: 304, tooltipHeight: 230,
      viewport: { width: 1024, height: 768 }, side: 'right', gap: 10, margin: 12
    });
    expect(result).toBeNull();
  });
});
