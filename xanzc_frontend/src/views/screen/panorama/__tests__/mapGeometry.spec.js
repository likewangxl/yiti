import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  clusterPoints,
  collectGeoPolygons,
  createProjection,
  filterRenderablePoints,
  projectGeoJson
} from '../mapGeometry';

const polygonGeoJson = {
  type: 'FeatureCollection',
  features: [
    {
      type: 'Feature',
      properties: { adcode: 610100, name: '西安市' },
      geometry: {
        type: 'Polygon',
        coordinates: [[[108, 34], [109, 34], [109, 35], [108, 35], [108, 34]]]
      }
    },
    {
      type: 'Feature',
      properties: { adcode: 610200, name: '铜川市' },
      geometry: {
        type: 'MultiPolygon',
        coordinates: [
          [[[109.2, 34.1], [109.4, 34.1], [109.4, 34.3], [109.2, 34.3], [109.2, 34.1]]],
          [[[109.5, 34.5], [109.7, 34.5], [109.7, 34.7], [109.5, 34.7], [109.5, 34.5]]]
        ]
      }
    }
  ]
};

describe('mapGeometry', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('空 GeoJSON 返回空几何和稳定投影，不抛异常', () => {
    expect(collectGeoPolygons({ type: 'FeatureCollection', features: [] })).toEqual([]);
    const projection = createProjection({ type: 'FeatureCollection', features: [] });
    expect(projection.project([108.9, 34.2])).toEqual({ x: 0, y: 0 });
    expect(projectGeoJson(null)).toEqual([]);
  });

  it('提取 Polygon 与 MultiPolygon 的全部外环和内环', () => {
    const polygons = collectGeoPolygons(polygonGeoJson);
    expect(polygons).toHaveLength(3);
    expect(polygons[0].outer).toHaveLength(5);
    expect(polygons.every(item => item.outer.length >= 4)).toBe(true);
  });

  it('投影保持东向为正、北向为正，且输出为归一化二维坐标', () => {
    const projection = createProjection(polygonGeoJson);
    const westSouth = projection.project([108, 34]);
    const eastNorth = projection.project([109, 35]);
    expect(eastNorth.x).toBeGreaterThan(westSouth.x);
    expect(eastNorth.y).toBeGreaterThan(westSouth.y);
    const rendered = projectGeoJson(polygonGeoJson);
    expect(rendered).toHaveLength(3);
    expect(rendered[0].outer[0]).toMatchObject({ x: expect.any(Number), y: expect.any(Number) });
  });

  it('只绘制明确 GCJ02 且有有效坐标的真实机构，演示/未知/缺坐标留在外部列表', () => {
    const points = [
      { orgCode: 'A', orgName: '有效', lng: 108.9, lat: 34.2, coordSys: 'GCJ02' },
      { orgCode: 'B', orgName: '无坐标', coordSys: 'GCJ02' },
      { orgCode: 'C', orgName: '未知坐标系', lng: 108.9, lat: 34.2, coordSys: 'WGS84' },
      { orgCode: 'D', orgName: '演示点', lng: 108.9, lat: 34.2, coordSys: 'GCJ02', demo: true },
      { orgCode: 'E', orgName: '非法坐标', lng: 181, lat: 34.2, coordSys: 'GCJ02' },
      { orgCode: 'F', orgName: '未定位标记', lng: 108.9, lat: 34.2, coordSys: 'GCJ02', located: false },
      { orgCode: 'G', orgName: '空坐标', lng: null, lat: null, coordSys: 'GCJ02' }
    ];
    expect(filterRenderablePoints(points).map(point => point.orgCode)).toEqual(['A']);
  });

  it('仅开发环境且显式 demo=true 时绘制带 DEMO 标记的合法坐标，仍拒绝无坐标演示点', () => {
    vi.stubEnv('DEV', true);
    const points = [
      { orgCode: 'DEMO-OK', lng: 108.9, lat: 34.2, coordSys: 'GCJ02', demo: true },
      { orgCode: 'DEMO-NO-COORD', coordSys: 'GCJ02', demo: true }
    ];
    expect(filterRenderablePoints(points, { demo: true }).map(point => point.orgCode)).toEqual(['DEMO-OK']);
  });

  it('生产环境即使传入 demo=true 也始终排除演示点', () => {
    vi.stubEnv('DEV', false);
    const points = [{ orgCode: 'DEMO-PROD', lng: 108.9, lat: 34.2, coordSys: 'GCJ02', demo: true }];
    expect(filterRenderablePoints(points, { demo: true })).toEqual([]);
  });

  it('在屏幕近邻范围内聚合点位，单点保留原机构，聚合项带成员列表', () => {
    const points = [
      { orgCode: 'A', orgName: '甲', lng: 108.9, lat: 34.2, coordSys: 'GCJ02' },
      { orgCode: 'B', orgName: '乙', lng: 108.9001, lat: 34.2001, coordSys: 'GCJ02' },
      { orgCode: 'C', orgName: '丙', lng: 109.4, lat: 34.8, coordSys: 'GCJ02' }
    ];
    const projection = createProjection(polygonGeoJson);
    const clusters = clusterPoints(points, { projection, threshold: 24, width: 800, height: 600 });
    expect(clusters).toHaveLength(2);
    const cluster = clusters.find(item => item.isCluster);
    expect(cluster.count).toBe(2);
    expect(cluster.points.map(point => point.orgCode)).toEqual(['A', 'B']);
    expect(clusters.find(item => item.orgCode === 'C').isCluster).toBe(false);
  });
});
