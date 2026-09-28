import { describe, expect, it } from 'vitest';
import {
  buildProvinceInstitutionMapState,
  PROVINCE_HAS_INSTITUTION_COLOR
} from '../provinceInstitutionMapModel';
import { MAP_MISSING_COLOR } from '../../presentation/map/mapModel';

const geoJson = {
  type: 'FeatureCollection',
  features: [
    { type: 'Feature', properties: { adcode: '610100', name: '西安市' }, geometry: null },
    { type: 'Feature', properties: { adcode: '610200', name: '铜川市' }, geometry: null },
    { type: 'Feature', properties: { adcode: '610300', name: '宝鸡市' }, geometry: null },
    { type: 'Feature', properties: { adcode: '610400', name: '咸阳市' }, geometry: null }
  ]
};

describe('provinceInstitutionMapModel', () => {
  it('完整授权目录按 cityCode 标记 HAS/NO，颜色与指标值无关', () => {
    const result = buildProvinceInstitutionMapState(geoJson, [
      { orgCode: 'A', cityCode: '610100' },
      { orgCode: 'B', cityCode: '610300' },
      { orgCode: 'C', cityCode: '610300' }
    ]);

    expect(result.regionStates).toEqual({
      '610100': 'HAS_INSTITUTION',
      '610200': 'NO_INSTITUTION',
      '610300': 'HAS_INSTITUTION',
      '610400': 'NO_INSTITUTION'
    });
    expect(result.metricColors).toEqual({
      '610100': PROVINCE_HAS_INSTITUTION_COLOR,
      '610200': MAP_MISSING_COLOR,
      '610300': PROVINCE_HAS_INSTITUTION_COLOR,
      '610400': MAP_MISSING_COLOR
    });
    expect(result.hasUnknownLocations).toBe(false);
    expect(result.hasCompleteDirectory).toBe(true);
  });

  it('零值与无指标不改变占用状态', () => {
    const result = buildProvinceInstitutionMapState(geoJson, [
      { orgCode: 'ZERO', cityCode: '610100', deposit: 0 },
      { orgCode: 'EMPTY', cityCode: '610300', deposit: null }
    ]);

    expect(result.regionStates['610100']).toBe('HAS_INSTITUTION');
    expect(result.regionStates['610300']).toBe('HAS_INSTITUTION');
    expect(result.regionStates['610200']).toBe('NO_INSTITUTION');
  });

  it('机构缺 cityCode 或 cityCode 不在 GeoJSON 时不推断无机构', () => {
    const result = buildProvinceInstitutionMapState(geoJson, [
      { orgCode: 'KNOWN', cityCode: '610100' },
      { orgCode: 'UNKNOWN_CITY', cityCode: '619999' },
      { orgCode: 'NO_CITY' }
    ]);

    expect(result.regionStates).toEqual({
      '610100': 'HAS_INSTITUTION',
      '610200': 'MISSING',
      '610300': 'MISSING',
      '610400': 'MISSING'
    });
    expect(result.metricColors['610100']).toBe(PROVINCE_HAS_INSTITUTION_COLOR);
    expect(result.metricColors['610200']).toBe(MAP_MISSING_COLOR);
    expect(result.hasUnknownLocations).toBe(true);
    expect(result.hasCompleteDirectory).toBe(false);
  });

  it('空目录保持 MISSING，刷新目录后重新计算而不残留旧占用城市', () => {
    const empty = buildProvinceInstitutionMapState(geoJson, []);
    expect(Object.values(empty.regionStates)).toEqual(['MISSING', 'MISSING', 'MISSING', 'MISSING']);

    const refreshed = buildProvinceInstitutionMapState(geoJson, [{ orgCode: 'NEW', cityCode: '610200' }]);
    expect(refreshed.regionStates).toEqual({
      '610100': 'NO_INSTITUTION',
      '610200': 'HAS_INSTITUTION',
      '610300': 'NO_INSTITUTION',
      '610400': 'NO_INSTITUTION'
    });
  });
});
