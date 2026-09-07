import { describe, expect, it } from 'vitest';
import { cityGeoByCode, cityOptions, PROVINCE_GEO_METADATA, provinceGeo, XIAN_FULL_GEO_SOURCE_URL } from '../geography';

describe('panorama geography assets', () => {
  it('导出陕西省十个地市的真实 GeoJSON 和稳定选项', () => {
    expect(provinceGeo.type).toBe('FeatureCollection');
    expect(provinceGeo.features).toHaveLength(10);
    expect(cityOptions).toHaveLength(10);
    expect(cityOptions.map(city => city.code)).toEqual([
      '610100', '610200', '610300', '610400', '610500',
      '610600', '610700', '610800', '610900', '611000'
    ]);
  });

  it('西安使用同源全市 13 区县轮廓，不能退回旧六区口径', () => {
    expect(cityGeoByCode['610100'].type).toBe('FeatureCollection');
    expect(cityGeoByCode['610100'].features).toHaveLength(13);
    expect(cityGeoByCode['610100'].features.every(feature => feature.properties.parent?.adcode === 610100)).toBe(true);
    expect(cityGeoByCode['610100'].features.every(feature => feature.geometry.type === 'MultiPolygon')).toBe(true);
    expect(cityGeoByCode['610100'].features.some(feature => feature.properties.name === '周至县')).toBe(true);
  });

  it('其余地市保留随包区县 GeoJSON，空值不会污染公共导出', () => {
    expect(cityGeoByCode['610300'].features.length).toBeGreaterThan(1);
    expect(cityGeoByCode['610300'].features.every(feature => feature.geometry)).toBe(true);
    expect(Object.keys(cityGeoByCode)).toHaveLength(10);
  });

  it('记录西安全市区县资产同源 URL、取数日期和内容哈希', () => {
    expect(PROVINCE_GEO_METADATA.citySourceUrl).toBe(XIAN_FULL_GEO_SOURCE_URL);
    expect(PROVINCE_GEO_METADATA.citySourceUrl).toContain('/610100_full.json');
    expect(PROVINCE_GEO_METADATA.citySourceContentSha256).toMatch(/^[a-f0-9]{64}$/);
  });
});
