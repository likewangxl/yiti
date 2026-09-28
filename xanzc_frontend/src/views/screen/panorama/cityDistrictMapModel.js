import { collectGeoPolygons } from './mapGeometry';
import {
  MAP_MISSING_COLOR,
  MAP_NO_INSTITUTION_COLOR,
  MAP_PALETTE
} from '../presentation/map/mapModel';

export const CITY_DISTRICT_HAS_INSTITUTION_COLOR = MAP_PALETTE.mid;

const DISTRICT_CODE_KEYS = [
  'districtCode', 'district_code', 'districtAdcode', 'district_adcode',
  'countyCode', 'county_code', 'countyAdcode', 'county_adcode',
  'regionCode', 'region_code'
];

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function finite(value) {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function coordinate(value) {
  const lng = finite(value?.lng ?? value?.longitude ?? value?.lon);
  const lat = finite(value?.lat ?? value?.latitude);
  if (lng === null || lat === null || lng < -180 || lng > 180 || lat < -90 || lat > 90) return null;
  return [lng, lat];
}

function coordinateSystem(value) {
  return text(value?.coordSys ?? value?.coordinateSystem ?? value?.coord_sys)
    .toUpperCase().replace(/[-_\s]/g, '');
}

function isDemoInstitution(value) {
  const flag = item => item === true || ['true', '1', 'yes', 'y'].includes(text(item).toLowerCase());
  return flag(value?.demo) || flag(value?.isDemo) || flag(value?.demoOnly)
    || flag(value?.synthetic) || text(value?.markerType).toUpperCase() === 'DEMO'
    || text(value?.source).toLowerCase() === 'demo';
}

function renderableCoordinate(value, demo) {
  if (!value || typeof value !== 'object' || (isDemoInstitution(value) && demo !== true)) return null;
  if (value.located === false || ['false', '0', 'no', 'n'].includes(text(value.located).toLowerCase())) return null;
  if (coordinateSystem(value) !== 'GCJ02') return null;
  return coordinate(value);
}

function explicitDistrictCode(value) {
  for (const key of DISTRICT_CODE_KEYS) {
    const candidate = value?.[key];
    const code = typeof candidate === 'object' ? candidate?.adcode ?? candidate?.code : candidate;
    if (text(code)) return text(code);
  }
  const nested = value?.district ?? value?.county ?? value?.region;
  if (nested && typeof nested === 'object') {
    return text(nested.adcode ?? nested.code ?? nested.regionCode);
  }
  return '';
}

function pointOnSegment(point, start, end) {
  const cross = (point[1] - start[1]) * (end[0] - start[0])
    - (point[0] - start[0]) * (end[1] - start[1]);
  if (Math.abs(cross) > 1e-9) return false;
  return point[0] >= Math.min(start[0], end[0]) - 1e-9
    && point[0] <= Math.max(start[0], end[0]) + 1e-9
    && point[1] >= Math.min(start[1], end[1]) - 1e-9
    && point[1] <= Math.max(start[1], end[1]) + 1e-9;
}

function pointInRing(point, ring = []) {
  let inside = false;
  for (let index = 0, previous = ring.length - 1; index < ring.length; previous = index++) {
    const current = ring[index];
    const prior = ring[previous];
    if (pointOnSegment(point, prior, current)) return true;
    const intersects = ((current[1] > point[1]) !== (prior[1] > point[1]))
      && point[0] < ((prior[0] - current[0]) * (point[1] - current[1]))
        / (prior[1] - current[1] || Number.EPSILON) + current[0];
    if (intersects) inside = !inside;
  }
  return inside;
}

function pointInPolygon(point, polygon) {
  return pointInRing(point, polygon.outer)
    && !polygon.holes.some(hole => pointInRing(point, hole));
}

function signedRingArea(ring = []) {
  return ring.reduce((area, point, index) => {
    const next = ring[(index + 1) % ring.length] || point;
    return area + point[0] * next[1] - next[0] * point[1];
  }, 0) / 2;
}

function ringCenter(ring = []) {
  const area = signedRingArea(ring);
  if (Math.abs(area) < 1e-12) {
    if (!ring.length) return null;
    return ring.reduce((center, point) => ({
      lng: center.lng + point[0] / ring.length,
      lat: center.lat + point[1] / ring.length
    }), { lng: 0, lat: 0 });
  }
  const center = ring.reduce((result, point, index) => {
    const next = ring[(index + 1) % ring.length] || point;
    const factor = point[0] * next[1] - next[0] * point[1];
    return {
      lng: result.lng + (point[0] + next[0]) * factor,
      lat: result.lat + (point[1] + next[1]) * factor
    };
  }, { lng: 0, lat: 0 });
  const denominator = 6 * area;
  return { lng: center.lng / denominator, lat: center.lat / denominator };
}

function polygonCenter(polygon) {
  const outerArea = Math.abs(signedRingArea(polygon.outer));
  const holesArea = polygon.holes.reduce((sum, hole) => sum + Math.abs(signedRingArea(hole)), 0);
  const outer = ringCenter(polygon.outer);
  if (!outer) return null;
  const weighted = {
    lng: outer.lng * outerArea,
    lat: outer.lat * outerArea
  };
  polygon.holes.forEach(hole => {
    const center = ringCenter(hole);
    const area = Math.abs(signedRingArea(hole));
    if (center) {
      weighted.lng -= center.lng * area;
      weighted.lat -= center.lat * area;
    }
  });
  const area = outerArea - holesArea;
  if (area <= 1e-12) return outer;
  return { lng: weighted.lng / area, lat: weighted.lat / area };
}

function featureCenter(polygons) {
  const propertyCenter = polygons.find(polygon => {
    const candidate = polygon.properties?.centroid ?? polygon.properties?.center;
    return Array.isArray(candidate) && coordinate({ lng: candidate[0], lat: candidate[1] });
  })?.properties?.centroid ?? polygons.find(polygon => Array.isArray(polygon.properties?.center))?.properties?.center;
  const preferred = coordinate({ lng: propertyCenter?.[0], lat: propertyCenter?.[1] });
  if (preferred) return { lng: preferred[0], lat: preferred[1] };

  const centers = polygons.map(polygon => polygonCenter(polygon)).filter(Boolean);
  if (!centers.length) return null;
  return centers.reduce((center, item) => ({
    lng: center.lng + item.lng / centers.length,
    lat: center.lat + item.lat / centers.length
  }), { lng: 0, lat: 0 });
}

function emptyViewFit() {
  return { focusRegionCodes: [], initialZoom: 1 };
}

/**
 * 根据真实市级区县边界和当前授权机构目录，生成市级地图的占用状态与默认视图。
 * 未定位、非法或出界机构不能用于推断区县无机构；只有目录完整且所有机构均能
 * 确认归属时，未占用区县才进入 NO_INSTITUTION。
 */
export function buildCityDistrictMapState(geoJson, institutions, { demo = false } = {}) {
  const polygons = collectGeoPolygons(geoJson);
  const features = new Map();
  polygons.forEach(polygon => {
    const code = text(polygon.code);
    if (!code) return;
    const item = features.get(code) || { code, polygons: [] };
    item.polygons.push(polygon);
    features.set(code, item);
  });
  const codes = [...features.keys()];
  const directory = Array.isArray(institutions)
    ? institutions.filter(item => item && typeof item === 'object' && (demo === true || !isDemoInstitution(item)))
    : [];
  const occupied = new Set();
  let hasUnknownLocations = false;

  directory.forEach(institution => {
    const explicit = explicitDistrictCode(institution);
    let code = explicit && features.has(explicit) ? explicit : '';
    const point = renderableCoordinate(institution, demo);
    if (!code && point) {
      code = codes.find(candidate => features.get(candidate).polygons.some(polygon => pointInPolygon(point, polygon))) || '';
    }
    if (code) occupied.add(code);
    else hasUnknownLocations = true;
  });

  const hasCompleteDirectory = directory.length > 0 && !hasUnknownLocations;
  const regionStates = Object.fromEntries(codes.map(code => [
    code,
    occupied.has(code) ? 'HAS_INSTITUTION' : hasCompleteDirectory ? 'NO_INSTITUTION' : 'MISSING'
  ]));
  const metricColors = Object.fromEntries(codes.map(code => {
    const state = regionStates[code];
    return [code, state === 'HAS_INSTITUTION'
      ? CITY_DISTRICT_HAS_INSTITUTION_COLOR
      : state === 'NO_INSTITUTION' ? MAP_NO_INSTITUTION_COLOR : MAP_MISSING_COLOR];
  }));
  const occupiedRegionCodes = codes.filter(code => occupied.has(code));
  const viewFit = emptyViewFit();
  if (occupiedRegionCodes.length) {
    const centers = occupiedRegionCodes.map(code => featureCenter(features.get(code).polygons)).filter(Boolean);
    if (centers.length) {
      viewFit.focusRegionCodes = occupiedRegionCodes;
      viewFit.initialZoom = 1.25;
      viewFit.focusCenter = centers.reduce((center, item) => ({
        lng: center.lng + item.lng / centers.length,
        lat: center.lat + item.lat / centers.length
      }), { lng: 0, lat: 0 });
    }
  }

  return { regionStates, metricColors, occupiedRegionCodes, viewFit, hasUnknownLocations };
}

export default buildCityDistrictMapState;
