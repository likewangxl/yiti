import { describe, expect, it } from 'vitest';
import { provinceCityColor, provinceCityPaletteGradient, PROVINCE_CITY_PALETTE } from '../provinceCityPalette';

const codes = ['610100', '610200', '610300', '610400', '610500', '610600', '610700', '610800', '610900', '611000'];

describe('省级地市科技配色', () => {
  it('陕西十个地市颜色鲜明且互不相同，重排输入不改变映射', () => {
    const colors = codes.map(provinceCityColor);
    const reordered = [...codes].reverse().map(provinceCityColor);

    expect(PROVINCE_CITY_PALETTE.length).toBeGreaterThanOrEqual(10);
    expect(new Set(colors).size).toBe(10);
    expect(reordered.reverse()).toEqual(colors);
  });

  it('未知 code 也稳定返回 palette 颜色', () => {
    expect(provinceCityColor('UNKNOWN-CITY')).toBe(provinceCityColor('UNKNOWN-CITY'));
    expect(PROVINCE_CITY_PALETTE).toContain(provinceCityColor('UNKNOWN-CITY'));
    expect(provinceCityPaletteGradient()).toContain('linear-gradient');
  });
});
