import { describe, expect, it } from 'vitest';
import {
  SCR_COLOR,
  SCR_PALETTE,
  SCR_CHART_PRESETS,
  resolveChartTheme,
  normalizeChartPreset
} from '../screenChartTheme';

describe('screenChartTheme 视觉预设', () => {
  it('保留既有 SCR_COLOR/SCR_PALETTE API，并提供至少三套可解析 preset', () => {
    expect(SCR_COLOR).toHaveProperty('cyan');
    expect(Array.isArray(SCR_PALETTE)).toBe(true);
    expect(Object.keys(SCR_CHART_PRESETS)).toEqual(expect.arrayContaining(['aurora', 'graphite', 'vivid']));
    for (const name of ['aurora', 'graphite', 'vivid']) {
      const theme = resolveChartTheme(name);
      expect(theme.palette.length).toBeGreaterThanOrEqual(10);
      expect(new Set(theme.palette).size).toBe(theme.palette.length);
      expect(theme.tokens).toMatchObject({ accent: expect.any(String), text: expect.any(String) });
    }
  });

  it('未知 preset 回退默认主题，别名/大小写可归一化', () => {
    expect(normalizeChartPreset('GRAPHITE')).toBe('graphite');
    expect(resolveChartTheme('not-a-theme')).toEqual(resolveChartTheme('aurora'));
    expect(resolveChartTheme({ visualPreset: 'vivid' }).name).toBe('vivid');
    expect(resolveChartTheme({ preset: 'graphite' }).name).toBe('graphite');
    const graphite = resolveChartTheme('graphite');
    expect(resolveChartTheme(graphite)).toBe(graphite);
  });
});
