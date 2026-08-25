import tongchuan from './shaanxi-cities/610200.json';
import baoji from './shaanxi-cities/610300.json';
import xianyang from './shaanxi-cities/610400.json';
import weinan from './shaanxi-cities/610500.json';
import yanan from './shaanxi-cities/610600.json';
import hanzhong from './shaanxi-cities/610700.json';
import yulin from './shaanxi-cities/610800.json';
import ankang from './shaanxi-cities/610900.json';
import shangluo from './shaanxi-cities/611000.json';

/**
 * 陕西九个地市的区县边界。西安继续使用业务确认的六区 OSM 裁剪资产，避免改变既有经营口径。
 * 其余边界来自阿里云 DataV.GeoAtlas 地理边界 GeoJSON 接口，并随前端包本地化，避免运行时依赖公网。
 */
export const SHAANXI_CITY_DISTRICT_GEO = Object.freeze({
  '610200': tongchuan,
  '610300': baoji,
  '610400': xianyang,
  '610500': weinan,
  '610600': yanan,
  '610700': hanzhong,
  '610800': yulin,
  '610900': ankang,
  '611000': shangluo
});

export const SHAANXI_CITY_BOUNDARY_ATTRIBUTION = '区县边界：阿里云 DataV.GeoAtlas';
export const SHAANXI_CITY_BOUNDARY_SOURCE_URL = 'https://help.aliyun.com/zh/datav/datav-6-0/user-guide/choropleth-layer-1';

export function cityDistrictMapName(code) {
  return `shaanxi-city-${code}`;
}
