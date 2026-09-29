import { describe, expect, it } from 'vitest';
import {
  fitReliefView,
  createReliefWallColors,
  smoothReliefWallNormals,
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

  it('允许用占用区域的 camera-space bounds 中心替换全市 bounds 中心，同时保留原 fit 跨度', () => {
    const points = [{ x: -8, y: -2 }, { x: 8, y: -2 }, { x: 8, y: 2 }, { x: -8, y: 2 }];
    const defaultFit = fitReliefView(points, { aspect: 1 });
    const focusedFit = fitReliefView(points, { aspect: 1, center: { x: -6, y: 1 } });
    expect((focusedFit.left + focusedFit.right) / 2).toBeCloseTo(-6);
    expect((focusedFit.top + focusedFit.bottom) / 2).toBeCloseTo(1);
    expect(focusedFit.right - focusedFit.left).toBeCloseTo(defaultFit.right - defaultFit.left);
    expect(focusedFit.top - focusedFit.bottom).toBeCloseTo(defaultFit.top - defaultFit.bottom);
  });

  it('只把 relief 作为显式外观开关，默认和未知值保持 classic', () => {
    expect(RELIEF_APPEARANCE).toBe('relief');
    expect(isReliefAppearance('relief')).toBe(true);
    expect(isReliefAppearance('RELIEF')).toBe(true);
    expect(isReliefAppearance('classic')).toBe(false);
    expect(isReliefAppearance()).toBe(false);
  });

  it('地图与底座保留可辨识厚度，标签始终高于顶面', () => {
    const config = createReliefGeometryConfig({ worldWidth: 10, worldHeight: 9 });

    expect(config.depth).toBeGreaterThanOrEqual(0.24);
    expect(config.depth).toBeLessThanOrEqual(0.36);
    expect(config.baseDepth).toBeGreaterThan(0);
    expect(config.baseDepth).toBeLessThan(config.depth);
    expect(config.fitHeight).toBeGreaterThanOrEqual(0.85);
    expect(config.fitHeight).toBeLessThanOrEqual(0.9);
    expect(getReliefSurfaceZ(config)).toBeGreaterThan(config.depth);
  });

  it('侧壁颜色按高度连续过渡，倒角越界被夹紧且不改动原始坐标', () => {
    const positions = new Float32Array([0, 0, -.02, 1, 0, .35, 1, 1, .7, 0, 1, .72]);
    const before = positions.slice();
    const colors = createReliefWallColors(positions, .7);
    expect(positions).toEqual(before);
    expect(colors).toHaveLength(positions.length);
    expect([...colors].every(value => Number.isFinite(value) && value >= 0 && value <= 1)).toBe(true);
    expect(colors[4]).toBeGreaterThan(colors[1]);
    expect(colors[7]).toBeGreaterThan(colors[4]);
    for (let channel = 0; channel < 3; channel += 1) {
      expect(colors[6 + channel]).toBeCloseTo(colors[9 + channel], 6);
    }
    expect([...createReliefWallColors(positions, 0)].every(Number.isFinite)).toBe(true);
  });

  it('仅平滑共享侧壁的法线，顶面法线与地理坐标保持不变', () => {
    const positions = new Float32Array([1, 1, 0, 1, 1, .7, 1, 1, .7]);
    const normals = new Float32Array([1, 0, 0, 0, 1, 0, 0, 0, 1]);
    const result = smoothReliefWallNormals(positions, normals);
    expect(result[0]).toBeCloseTo(Math.SQRT1_2);
    expect(result[1]).toBeCloseTo(Math.SQRT1_2);
    expect([...result.slice(0, 3)]).toEqual([...result.slice(3, 6)]);
    expect([...result.slice(6)]).toEqual([0, 0, 1]);
    expect([...normals]).toEqual([1, 0, 0, 0, 1, 0, 0, 0, 1]);
  });

});
