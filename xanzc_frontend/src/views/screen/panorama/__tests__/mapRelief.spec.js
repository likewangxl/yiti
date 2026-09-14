import { describe, expect, it } from 'vitest';
import {
  fitReliefView,
  RELIEF_APPEARANCE,
  createReliefGeometryConfig,
  getReliefSurfaceZ,
  isReliefAppearance
} from '../mapReliefGeometry';

describe('mapReliefGeometry', () => {
  it('fits actual camera-space vertices with padding and retains tall geometry in a wide panel', () => {
    const points = [{x:-2,y:-5},{x:2,y:-5},{x:1,y:5},{x:-1,y:5}];
    const fit = fitReliefView(points, { aspect: 1.5, fitHeight: .88 });
    expect(10 / (fit.top - fit.bottom)).toBeCloseTo(.88);
    expect((fit.right - fit.left) / (fit.top - fit.bottom)).toBeCloseTo(1.5);
    for (const p of points) {
      expect(p.x).toBeGreaterThan(fit.left); expect(p.x).toBeLessThan(fit.right);
      expect(p.y).toBeGreaterThan(fit.bottom); expect(p.y).toBeLessThan(fit.top);
    }
    const narrow = fitReliefView(points, { aspect: .3 });
    expect(narrow.right - narrow.left).toBeGreaterThan(4);
    expect(fitReliefView([], { aspect: 1 })).toBeNull();
  });

  it('只把 relief 作为显式外观开关，默认和未知值保持 classic', () => {
    expect(RELIEF_APPEARANCE).toBe('relief');
    expect(isReliefAppearance('relief')).toBe(true);
    expect(isReliefAppearance('RELIEF')).toBe(true);
    expect(isReliefAppearance('classic')).toBe(false);
    expect(isReliefAppearance()).toBe(false);
  });

  it('按归一化地图尺度生成 0.6~0.8 的厚挤出、底座和随厚度抬升的标签层', () => {
    const config = createReliefGeometryConfig({ worldWidth: 10, worldHeight: 9 });

    expect(config.depth).toBeGreaterThanOrEqual(0.6);
    expect(config.depth).toBeLessThanOrEqual(0.8);
    expect(config.baseDepth).toBeGreaterThan(0);
    expect(config.baseDepth).toBeLessThan(config.depth);
    expect(config.fitHeight).toBeGreaterThanOrEqual(0.85);
    expect(config.fitHeight).toBeLessThanOrEqual(0.9);
    expect(getReliefSurfaceZ(config)).toBeGreaterThan(config.depth);
  });

});
