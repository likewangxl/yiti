import { describe, expect, it } from 'vitest';
import {
  CITY_DISTRICT_PALETTE,
  cityDistrictColors,
  cityDistrictPaletteGradient
} from '../cityDistrictPalette';

describe('cityDistrictPalette', () => {
  const xianDistrictCodes = [
    '610102', '610103', '610104', '610111', '610112', '610113', '610114',
    '610115', '610116', '610117', '610118', '610122', '610124'
  ];

  it('13 个西安区县各自使用不同的协调分类色', () => {
    const colors = cityDistrictColors(xianDistrictCodes);

    expect(Object.keys(colors)).toHaveLength(13);
    expect(new Set(Object.values(colors)).size).toBe(13);
  });

  it('对去重并排序行政代码后映射，输入顺序和重复项不影响颜色', () => {
    const expected = cityDistrictColors(xianDistrictCodes);
    const shuffled = cityDistrictColors([...xianDistrictCodes].reverse().concat('610113'));

    expect(shuffled).toEqual(expected);
  });

  it('提供至少 24 个可区分的颜色及覆盖整组分类色的渐变图例', () => {
    expect(CITY_DISTRICT_PALETTE.length).toBeGreaterThanOrEqual(24);
    expect(new Set(CITY_DISTRICT_PALETTE).size).toBe(CITY_DISTRICT_PALETTE.length);
    expect(cityDistrictPaletteGradient()).toContain('linear-gradient');
    CITY_DISTRICT_PALETTE.forEach(color => expect(cityDistrictPaletteGradient()).toContain(color));
  });
});
