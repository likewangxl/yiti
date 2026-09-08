import { describe, it, expect } from 'vitest';
import { DESIGN_W, DESIGN_H, ADAPTORS, stageStyle } from '../scale';

describe('scale.js 已发布大屏舞台适配(恒 1920×1080)', () => {
  it('基准常量', () => {
    expect(DESIGN_W).toBe(1920);
    expect(DESIGN_H).toBe(1080);
    expect(ADAPTORS).toEqual(['keep', 'keepProportion', 'widthFirst', 'heightFirst']);
  });

  it('stageStyle 四种适配策略(一次性映射,幂等)', () => {
    expect(stageStyle('keep', 960, 540).scale).toBe(1);
    expect(stageStyle('widthFirst', 960, 1080).scale).toBeCloseTo(0.5);
    expect(stageStyle('heightFirst', 1920, 540).scale).toBeCloseTo(0.5);
    expect(stageStyle('keepProportion', 960, 540).scale).toBeCloseTo(0.5);
  });
});
