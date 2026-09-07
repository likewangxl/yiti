/**
 * 全景地图的几何适配函数。
 *
 * GeoJSON 资产和机构坐标都使用 GCJ-02。这里采用局部等距投影：陕西省的范围足够
 * 小，投影误差远小于大屏像素尺寸，同时保持东向为正、北向为正，便于 Three.js
 * 按 XY 平面拉伸真实行政区几何。该文件不依赖 DOM 或 Three.js，便于单元测试。
 */

export const EMPTY_GEO_JSON = Object.freeze({ type: 'FeatureCollection', features: [] });

const GCJ02_NAMES = new Set(['GCJ02', 'GCJ-02', 'GCJ_02']);

function finite(value) {
  return typeof value === 'number' && Number.isFinite(value);
}

function coordinatePair(value) {
  if (Array.isArray(value) && value.length >= 2
    && value[0] !== null && value[1] !== null
    && String(value[0]).trim() !== '' && String(value[1]).trim() !== ''
    && finite(Number(value[0])) && finite(Number(value[1]))) {
    const lng = Number(value[0]);
    const lat = Number(value[1]);
    if (lng >= -180 && lng <= 180 && lat >= -90 && lat <= 90) return [lng, lat];
  }
  return null;
}

function ringCoordinates(ring) {
  if (!Array.isArray(ring)) return [];
  return ring.map(coordinatePair).filter(Boolean);
}

function featureCode(properties = {}) {
  const code = properties.adcode ?? properties.code ?? properties.cityCode ?? properties.regionCode;
  return code == null || code === '' ? '' : String(code);
}

function featureName(properties = {}) {
  return String(properties.name ?? properties.regionName ?? properties.cityName ?? '');
}

/**
 * 将 Feature、Geometry 和 FeatureCollection 统一为 FeatureCollection。
 * 非法/空值永远返回新空集合，防止坏地图资产阻止大屏启动。
 */
export function normalizeGeoJson(value) {
  if (!value || typeof value !== 'object') return { type: 'FeatureCollection', features: [] };
  if (value.type === 'FeatureCollection') {
    return { type: 'FeatureCollection', features: Array.isArray(value.features) ? value.features.filter(Boolean) : [] };
  }
  if (value.type === 'Feature') return { type: 'FeatureCollection', features: [value] };
  if (typeof value.type === 'string' && value.type !== 'FeatureCollection') {
    return { type: 'FeatureCollection', features: [{ type: 'Feature', properties: {}, geometry: value }] };
  }
  return { type: 'FeatureCollection', features: [] };
}

function appendGeometryPolygons(geometry, context, output) {
  if (!geometry || typeof geometry !== 'object') return;
  if (geometry.type === 'GeometryCollection') {
    for (const child of geometry.geometries || []) appendGeometryPolygons(child, context, output);
    return;
  }
  if (geometry.type === 'Polygon') {
    const rings = (geometry.coordinates || []).map(ringCoordinates).filter(ring => ring.length >= 3);
    if (rings.length) {
      output.push({ ...context, polygonIndex: context.polygonIndex, outer: rings[0], holes: rings.slice(1) });
    }
    return;
  }
  if (geometry.type === 'MultiPolygon') {
    (geometry.coordinates || []).forEach((polygon, index) => {
      const rings = (polygon || []).map(ringCoordinates).filter(ring => ring.length >= 3);
      if (rings.length) {
        output.push({ ...context, polygonIndex: index, outer: rings[0], holes: rings.slice(1) });
      }
    });
  }
}

/**
 * 返回所有可绘制多边形。一个 MultiPolygon 会展开为多个 polygon，但保留原 Feature
 * 引用和属性，调用方可把同一行政区的多个岛屿绑定到一个点击事件。
 */
export function collectGeoPolygons(value) {
  const geoJson = normalizeGeoJson(value);
  const output = [];
  geoJson.features.forEach((feature, featureIndex) => {
    if (!feature || feature.type !== 'Feature') return;
    const context = {
      feature,
      featureIndex,
      properties: feature.properties || {},
      code: featureCode(feature.properties || {}),
      name: featureName(feature.properties || {}),
      polygonIndex: 0
    };
    appendGeometryPolygons(feature.geometry, context, output);
  });
  return output;
}

/** 返回有效坐标范围；空集合返回 null。 */
export function getGeoBounds(value) {
  const coordinates = [];
  for (const polygon of collectGeoPolygons(value)) {
    coordinates.push(...polygon.outer, ...polygon.holes.flat());
  }
  if (!coordinates.length) return null;
  const bounds = coordinates.reduce((result, [lng, lat]) => ({
    minLng: Math.min(result.minLng, lng),
    maxLng: Math.max(result.maxLng, lng),
    minLat: Math.min(result.minLat, lat),
    maxLat: Math.max(result.maxLat, lat)
  }), { minLng: Infinity, maxLng: -Infinity, minLat: Infinity, maxLat: -Infinity });
  return {
    ...bounds,
    width: bounds.maxLng - bounds.minLng,
    height: bounds.maxLat - bounds.minLat,
    centerLng: (bounds.minLng + bounds.maxLng) / 2,
    centerLat: (bounds.minLat + bounds.maxLat) / 2
  };
}

/**
 * 创建局部等距投影。scale 是 Three.js 世界单位/纬度度数；默认让最长边约 10 个单位。
 */
export function createProjection(value, options = {}) {
  const bounds = getGeoBounds(value);
  if (!bounds) {
    const emptyProject = () => ({ x: 0, y: 0 });
    return {
      bounds: null,
      scale: 1,
      width: 0,
      height: 0,
      project: emptyProject,
      projectCoordinate: emptyProject,
      inverse: () => [0, 0]
    };
  }
  const latitudeScale = Math.cos((bounds.centerLat * Math.PI) / 180) || 1;
  const maxDimension = Math.max(bounds.width * latitudeScale, bounds.height, 1e-9);
  const scale = finite(options.scale) && options.scale > 0
    ? options.scale
    : Number(options.worldSize) > 0 ? Number(options.worldSize) / maxDimension : 10 / maxDimension;
  const project = (coordinate) => {
    const pair = coordinatePair(Array.isArray(coordinate)
      ? coordinate
      : [coordinate?.lng ?? coordinate?.longitude, coordinate?.lat ?? coordinate?.latitude]);
    if (!pair) return { x: 0, y: 0 };
    return {
      x: (pair[0] - bounds.centerLng) * latitudeScale * scale,
      y: (pair[1] - bounds.centerLat) * scale
    };
  };
  return {
    bounds,
    scale,
    latitudeScale,
    width: bounds.width * latitudeScale * scale,
    height: bounds.height * scale,
    project,
    projectCoordinate: project,
    inverse: ({ x = 0, y = 0 } = {}) => [
      bounds.centerLng + Number(x) / (latitudeScale * scale),
      bounds.centerLat + Number(y) / scale
    ]
  };
}

/** 将所有 GeoJSON 外环/内环投影到 XY 世界坐标。 */
export function projectGeoJson(value, projection = createProjection(value)) {
  return collectGeoPolygons(value).map(polygon => ({
    ...polygon,
    outer: polygon.outer.map(projection.project),
    holes: polygon.holes.map(ring => ring.map(projection.project))
  }));
}

// 常见写法别名，减少地图渲染层对坐标函数名称的耦合。
export const projectGeoJSON = projectGeoJson;
export const geoJsonBounds = getGeoBounds;

function coordinateOfPoint(point) {
  if (!point || typeof point !== 'object') return null;
  return coordinatePair([
    point.lng ?? point.longitude ?? point.lon,
    point.lat ?? point.latitude
  ]);
}

function isDemoPoint(point) {
  const demoValue = value => value === true || String(value || '').trim().toLowerCase() === 'true';
  return demoValue(point?.demo) || demoValue(point?.isDemo) || demoValue(point?.demoOnly)
    || demoValue(point?.synthetic) || String(point?.markerType || '').toUpperCase() === 'DEMO'
    || String(point?.source || '').toLowerCase() === 'demo';
}

/**
 * 演示点只能由开发构建且由上层显式打开。不要接受调用方自带的环境覆盖，避免
 * production 构建通过 options 绕过真实数据边界。
 */
function demoPointAllowed(options = {}) {
  return options.demo === true && Boolean(import.meta.env?.DEV);
}

/** 只有明确 GCJ02、有效坐标且非演示数据的机构才进入地图绘制层。 */
export function isRenderablePoint(point, options = {}) {
  const notLocated = point?.located === false || String(point?.located ?? '').trim().toLowerCase() === 'false';
  if (!point || typeof point !== 'object' || (isDemoPoint(point) && !demoPointAllowed(options)) || notLocated) return false;
  const coordinateSystem = String(point.coordSys ?? point.coordinateSystem ?? '').trim().toUpperCase();
  return GCJ02_NAMES.has(coordinateSystem) && Boolean(coordinateOfPoint(point));
}

export function filterRenderablePoints(points, options = {}) {
  return (Array.isArray(points) ? points : []).filter(point => isRenderablePoint(point, options));
}

function projectPoint(point, projection) {
  const coordinate = coordinateOfPoint(point);
  const projected = projection?.project ? projection.project(coordinate) : { x: coordinate[0], y: coordinate[1] };
  return { ...point, lng: coordinate[0], lat: coordinate[1], x: projected.x, y: projected.y };
}

/**
 * 按屏幕距离进行近邻聚合。返回项兼容单点和聚合点：单点保留 orgCode，聚合项
 * `isCluster=true`、`count` 和 `points` 可用于点击后放大到成员范围。
 */
export function clusterPoints(points, options = {}) {
  const source = options.filter === false
    ? (Array.isArray(points) ? points : [])
    : filterRenderablePoints(points, { demo: options.demo === true });
  const projection = options.projection;
  const projected = source.map(point => projectPoint(point, projection));
  if (!projected.length) return [];
  const threshold = Math.max(0, Number(options.threshold ?? 28));
  const worldWidth = Number(options.worldWidth ?? projection?.width ?? 10) || 10;
  const width = Number(options.width ?? 1) || 1;
  const worldHeight = Number(options.worldHeight ?? projection?.height ?? worldWidth) || worldWidth;
  const height = Number(options.height ?? width) || width;
  const sx = width / worldWidth;
  const sy = height / worldHeight;
  const remaining = projected.slice();
  const result = [];
  while (remaining.length) {
    const seed = remaining.shift();
    const members = [seed];
    for (let index = remaining.length - 1; index >= 0; index -= 1) {
      const candidate = remaining[index];
      const dx = (candidate.x - seed.x) * sx;
      const dy = (candidate.y - seed.y) * sy;
      if (Math.hypot(dx, dy) <= threshold) members.push(...remaining.splice(index, 1));
    }
    const x = members.reduce((sum, point) => sum + point.x, 0) / members.length;
    const y = members.reduce((sum, point) => sum + point.y, 0) / members.length;
    const lng = members.reduce((sum, point) => sum + point.lng, 0) / members.length;
    const lat = members.reduce((sum, point) => sum + point.lat, 0) / members.length;
    if (members.length === 1) {
      result.push({ ...members[0], points: [members[0]], count: 1, isCluster: false });
    } else {
      result.push({
        id: `cluster:${members.map(point => point.orgCode || point.orgName || '').join('|')}`,
        orgCode: null,
        orgName: `${members.length} 个机构`,
        lng,
        lat,
        x,
        y,
        count: members.length,
        isCluster: true,
        points: members.map(({ x: _x, y: _y, ...point }) => point)
      });
    }
  }
  return result;
}

export const clusterMapPoints = clusterPoints;
