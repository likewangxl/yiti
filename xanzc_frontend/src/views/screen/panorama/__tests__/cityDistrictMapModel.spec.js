import { describe, expect, it } from 'vitest';
import {
  buildCityDistrictMapState,
  CITY_DISTRICT_HAS_INSTITUTION_COLOR
} from '../cityDistrictMapModel';
import { MAP_MISSING_COLOR, MAP_NO_INSTITUTION_COLOR } from '../../presentation/map/mapModel';

const geoJson = {
  type: 'FeatureCollection',
  features: [
    {
      type: 'Feature',
      properties: { adcode: 'D-A', name: '甲区' },
      geometry: {
        type: 'Polygon',
        coordinates: [
          [[0, 0], [1, 0], [1, 1], [0, 1], [0, 0]],
          [[0.4, 0.4], [0.6, 0.4], [0.6, 0.6], [0.4, 0.6], [0.4, 0.4]]
        ]
      }
    },
    {
      type: 'Feature',
      properties: { adcode: 'D-B', name: '乙区' },
      geometry: {
        type: 'Polygon',
        coordinates: [[[1, 0], [2, 0], [2, 1], [1, 1], [1, 0]]]
      }
    },
    {
      type: 'Feature',
      properties: { adcode: 'D-C', name: '丙区' },
      geometry: {
        type: 'MultiPolygon',
        coordinates: [
          [[[2, 0], [3, 0], [3, 1], [2, 1], [2, 0]]],
          [[[3.2, 0], [3.4, 0], [3.4, 0.2], [3.2, 0.2], [3.2, 0]]]
        ]
      }
    }
  ]
};

describe('cityDistrictMapModel', () => {
  it('以 GCJ02 点和可信 districtCode 标记占用区县，并聚焦真实占用区县中心', () => {
    const result = buildCityDistrictMapState(geoJson, [
      { orgCode: 'A', located: true, lng: 0.2, lat: 0.2, coordSys: 'GCJ02' },
      { orgCode: 'B', located: false, districtCode: 'D-B' }
    ]);

    expect(result.regionStates).toEqual({ 'D-A': 'HAS_INSTITUTION', 'D-B': 'HAS_INSTITUTION', 'D-C': 'NO_INSTITUTION' });
    expect(result.occupiedRegionCodes).toEqual(['D-A', 'D-B']);
    expect(result.metricColors).toEqual({
      'D-A': CITY_DISTRICT_HAS_INSTITUTION_COLOR,
      'D-B': CITY_DISTRICT_HAS_INSTITUTION_COLOR,
      'D-C': MAP_NO_INSTITUTION_COLOR
    });
    expect(Object.keys(result.districtColors).sort()).toEqual(['D-A', 'D-B', 'D-C']);
    expect(result.districtColors['D-A']).not.toBe(result.districtColors['D-B']);
    expect(result.districtColors['D-C']).toBe(MAP_NO_INSTITUTION_COLOR);
    expect(result.hasUnknownLocations).toBe(false);
    expect(result.viewFit.focusRegionCodes).toEqual(['D-A', 'D-B']);
    expect(result.viewFit.focusCenterMode).toBe('OCCUPIED_BOUNDS');
    expect(result.viewFit.initialZoom).toBe(1.65);
    expect(result.viewFit.focusCenter).toEqual({ lng: 1, lat: 0.5 });
  });

  it('区县分类色按行政代码稳定映射，GeoJSON 乱序及 MultiPolygon 不会改变颜色', () => {
    const first = buildCityDistrictMapState(geoJson, []);
    const shuffled = buildCityDistrictMapState({ ...geoJson, features: [...geoJson.features].reverse() }, []);

    expect(first.districtColors).toEqual(shuffled.districtColors);
    expect(first.districtColors['D-C']).toBe(shuffled.districtColors['D-C']);
    expect(Object.keys(first.districtColors)).toHaveLength(3);
  });

  it('holes、出界和非法坐标进入未知态，未确认区县不误判为无机构', () => {
    const result = buildCityDistrictMapState(geoJson, [
      { orgCode: 'HOLE', located: true, lng: 0.5, lat: 0.5, coordSys: 'GCJ02' },
      { orgCode: 'OUTSIDE', located: true, lng: 99, lat: 99, coordSys: 'GCJ02' },
      { orgCode: 'WGS', located: true, lng: 1.2, lat: 0.2, coordSys: 'WGS84' }
    ]);

    expect(result.hasUnknownLocations).toBe(true);
    expect(Object.values(result.regionStates)).toEqual(['MISSING', 'MISSING', 'MISSING']);
    expect(Object.values(result.metricColors)).toEqual([MAP_MISSING_COLOR, MAP_MISSING_COLOR, MAP_MISSING_COLOR]);
    expect(Object.values(result.districtColors)).toEqual([MAP_MISSING_COLOR, MAP_MISSING_COLOR, MAP_MISSING_COLOR]);
    expect(result.occupiedRegionCodes).toEqual([]);
    expect(result.viewFit.focusRegionCodes).toEqual([]);
    expect(result.viewFit.initialZoom).toBe(1);
  });

  it('空目录保留当前授权范围待确认态，避免把全局无机构误报成区县无机构', () => {
    const result = buildCityDistrictMapState(geoJson, []);

    expect(result.regionStates).toEqual({ 'D-A': 'MISSING', 'D-B': 'MISSING', 'D-C': 'MISSING' });
    expect(result.occupiedRegionCodes).toEqual([]);
    expect(result.hasUnknownLocations).toBe(false);
    expect(result.viewFit).toEqual({ focusRegionCodes: [], initialZoom: 1 });
  });

  it('缺少 geo 边界时保留原始默认缩放', () => {
    const result = buildCityDistrictMapState(null, []);

    expect(result.districtColors).toEqual({});
    expect(result.viewFit).toEqual({ focusRegionCodes: [], initialZoom: 1 });
  });
});
