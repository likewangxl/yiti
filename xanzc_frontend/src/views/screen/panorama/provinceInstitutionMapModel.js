import { MAP_MISSING_COLOR, MAP_PALETTE } from '../presentation/map/mapModel';

/**
 * Legacy 零售省级地图只需要机构占用状态，不把指标缺失当成机构缺失。
 *
 * 只有授权机构目录中的每一条记录都能通过 cityCode 对上当前 GeoJSON
 * 地市时，才有足够证据把其余地市标为 NO_INSTITUTION；否则未知归属会
 * 让未占用地市保持 MISSING，避免把不完整目录误报成“无经营机构”。
 */
export const PROVINCE_HAS_INSTITUTION_COLOR = MAP_PALETTE.low;

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function featureCode(feature) {
  const properties = feature?.properties || {};
  return text(properties.adcode
    ?? properties.cityCode
    ?? properties.city_code
    ?? properties.code
    ?? properties.regionCode
    ?? properties.region_code);
}

function institutionCityCode(institution) {
  if (!institution || typeof institution !== 'object' || Array.isArray(institution)) return '';
  return text(institution.cityCode ?? institution.city_code);
}

/**
 * 根据省级 GeoJSON 和当前授权机构目录生成 PanoramaMap 的地市状态与颜色。
 * 指标数据不参与判断，因此零值、空值或尚未返回的指标不会覆盖 HAS_INSTITUTION。
 *
 * @param {object} geoJson 省级地市 FeatureCollection。
 * @param {Array<object>} institutions 当前授权机构目录。
 * @returns {{regionStates: Record<string, string>, metricColors: Record<string, string>, occupiedRegionCodes: string[], noInstitutionRegionCodes: string[], hasUnknownLocations: boolean, hasCompleteDirectory: boolean}}
 */
export function buildProvinceInstitutionMapState(geoJson, institutions) {
  const features = Array.isArray(geoJson?.features) ? geoJson.features : [];
  const regionCodes = [...new Set(features.map(featureCode).filter(Boolean))];
  const regionCodeSet = new Set(regionCodes);
  const directory = Array.isArray(institutions) ? institutions : [];
  const occupiedRegionCodes = new Set();
  let hasUnknownLocations = false;

  directory.forEach(institution => {
    const cityCode = institutionCityCode(institution);
    if (!cityCode || !regionCodeSet.has(cityCode)) {
      hasUnknownLocations = true;
      return;
    }
    occupiedRegionCodes.add(cityCode);
  });

  const hasCompleteDirectory = directory.length > 0 && !hasUnknownLocations;
  const regionStates = Object.fromEntries(regionCodes.map(code => [
    code,
    occupiedRegionCodes.has(code)
      ? 'HAS_INSTITUTION'
      : hasCompleteDirectory ? 'NO_INSTITUTION' : 'MISSING'
  ]));
  const noInstitutionRegionCodes = regionCodes.filter(code => regionStates[code] === 'NO_INSTITUTION');
  const metricColors = Object.fromEntries(regionCodes.map(code => [
    code,
    regionStates[code] === 'HAS_INSTITUTION'
      ? PROVINCE_HAS_INSTITUTION_COLOR
      : MAP_MISSING_COLOR
  ]));

  return {
    regionStates,
    metricColors,
    occupiedRegionCodes: regionCodes.filter(code => occupiedRegionCodes.has(code)),
    noInstitutionRegionCodes,
    hasUnknownLocations,
    hasCompleteDirectory
  };
}

export default buildProvinceInstitutionMapState;
