import { describe, expect, it } from 'vitest';
import { provinceCityColor, provinceCityPaletteGradient, PROVINCE_CITY_PALETTE } from '../provinceCityPalette';

const codes = ['610100', '610200', '610300', '610400', '610500', '610600', '610700', '610800', '610900', '611000'];

function saturation(hex) {
  const channels = hex.slice(1).match(/../g).map(value => Number.parseInt(value, 16) / 255);
  const max = Math.max(...channels);
  const min = Math.min(...channels);
  return max === min ? 0 : (max - min) / (1 - Math.abs(max + min - 1));
}

describe('省级地市科技配色', () => {
  it('陕西十个地市颜色鲜明且互不相同，重排输入不改变映射', () => {
    const colors = codes.map(code => provinceCityColor(code));
    const reordered = [...codes].reverse().map(code => provinceCityColor(code));

    expect(PROVINCE_CITY_PALETTE.length).toBeGreaterThanOrEqual(10);
    expect(new Set(colors).size).toBe(10);
    expect(colors.every(color => saturation(color) < 0.52)).toBe(true);
    expect(reordered.reverse()).toEqual(colors);
    expect(provinceCityColor('610100', 'HAS_INSTITUTION')).toBe('#48aeb9');
    expect(provinceCityColor('610300', 'HAS_INSTITUTION')).toBe('#777fbd');
    expect(provinceCityColor('610400', 'HAS_INSTITUTION')).toBe('#a07fbf');
    expect(provinceCityColor('610500', 'HAS_INSTITUTION')).toBe('#b99c70');
    expect(provinceCityColor('610800', 'HAS_INSTITUTION')).toBe('#4f94b7');
  });

  it('未知 code 也稳定返回 palette 颜色', () => {
    expect(provinceCityColor('UNKNOWN-CITY')).toBe(provinceCityColor('UNKNOWN-CITY'));
    expect(PROVINCE_CITY_PALETTE).toContain(provinceCityColor('UNKNOWN-CITY'));
    expect(provinceCityPaletteGradient()).toContain('linear-gradient');
    expect(provinceCityColor('610100', 'NO_INSTITUTION')).toBe('#65738a');
    expect(provinceCityColor('610100', 'MISSING')).toBe('#65738a');
  });

  it('新省级 palette 保持 code 稳定并提高最低饱和度，未知/无机构中性色不变', () => {
    const colors = codes.map(code => provinceCityColor(code));
    expect(Math.min(...colors.map(saturation))).toBeGreaterThan(0.24);
    expect(provinceCityColor('610100', 'NO_INSTITUTION')).toBe('#65738a');
    expect(provinceCityColor('610100', 'MISSING')).toBe('#65738a');
  });
});
