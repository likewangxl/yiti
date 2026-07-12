import { describe, it, expect } from 'vitest';
import { DESIGN_W, DESIGN_H, ADAPTORS, fitScale, screenDeltaToDesign, clampRect, stageStyle } from '../scale';

describe('scale.js 坐标换算(设计态恒 1920×1080)', () => {
  it('基准常量', () => {
    expect(DESIGN_W).toBe(1920);
    expect(DESIGN_H).toBe(1080);
    expect(ADAPTORS).toEqual(['keep', 'keepProportion', 'widthFirst', 'heightFirst']);
  });

  it('fitScale 取宽高比较小值', () => {
    expect(fitScale(1920, 1080)).toBeCloseTo(1);
    expect(fitScale(960, 1080)).toBeCloseTo(0.5);   // 宽受限
    expect(fitScale(1920, 540)).toBeCloseTo(0.5);   // 高受限
    expect(fitScale(0, 0)).toBe(1);                 // 兜底
  });

  it('screenDeltaToDesign 屏幕位移除以 scale 换算回设计坐标', () => {
    expect(screenDeltaToDesign(100, 0.5)).toBe(200); // 缩到 50% 时屏幕移 100px = 设计移 200px
    expect(screenDeltaToDesign(100, 0)).toBe(100);   // scale<=0 兜底 1
  });

  it('clampRect 禁拖出画布 + 极小尺寸下限', () => {
    // 负坐标夹到 0
    expect(clampRect({ top: -10, left: -20, width: 100, height: 50 }))
      .toEqual({ top: 0, left: 0, width: 100, height: 50 });
    // 超右边界:left 回退保证 left+width<=1920
    expect(clampRect({ top: 0, left: 1900, width: 100, height: 50 }).left).toBe(1820);
    // 极小尺寸:width/height 提升到 minW/minH(默认 20)
    expect(clampRect({ top: 0, left: 0, width: 5, height: 3 }))
      .toEqual({ top: 0, left: 0, width: 20, height: 20 });
  });

  it('stageStyle 四种适配策略(一次性映射,幂等)', () => {
    expect(stageStyle('keep', 960, 540).scale).toBe(1);
    expect(stageStyle('widthFirst', 960, 1080).scale).toBeCloseTo(0.5);
    expect(stageStyle('heightFirst', 1920, 540).scale).toBeCloseTo(0.5);
    expect(stageStyle('keepProportion', 960, 540).scale).toBeCloseTo(0.5);
  });
});
