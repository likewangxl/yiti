import provinceGeoAsset from '@/assets/geo/shaanxi.json';
import xianFullGeoAsset from '@/assets/geo/shaanxi-cities/610100.json';
import tongchuan from '@/assets/geo/shaanxi-cities/610200.json';
import baoji from '@/assets/geo/shaanxi-cities/610300.json';
import xianyang from '@/assets/geo/shaanxi-cities/610400.json';
import weinan from '@/assets/geo/shaanxi-cities/610500.json';
import yanan from '@/assets/geo/shaanxi-cities/610600.json';
import hanzhong from '@/assets/geo/shaanxi-cities/610700.json';
import yulin from '@/assets/geo/shaanxi-cities/610800.json';
import ankang from '@/assets/geo/shaanxi-cities/610900.json';
import shangluo from '@/assets/geo/shaanxi-cities/611000.json';

/**
 * 全景地图使用的陕西省地市边界。
 *
 * 省级资产是仓库随包的 DataV.GeoAtlas GeoJSON（坐标系 GCJ-02）。西安市的旧
 * `xian-six-districts` 只覆盖经营展示的六区，不能作为市级全市边界；市级地图随包
 * 使用同源 610100_full.json 的 13 个区县 Feature。保留省级 Feature 提取回退，避免
 * 资源被裁剪时启动崩溃。
 */
export const XIAN_FULL_GEO_SOURCE_URL = 'https://geo.datav.aliyun.com/areas_v3/bound/610100_full.json';

export const PROVINCE_GEO_METADATA = Object.freeze({
  sourceUrl: 'https://geo.datav.aliyun.com/areas_v3/bound/610000_full.json',
  citySourceUrl: XIAN_FULL_GEO_SOURCE_URL,
  citySourceRetrievedAt: '2026-09-07',
  citySourceContentSha256: '17b60bf21415fc8ff30a4b4aa63edbc1ea8de1dc149fc0ddbf330517a6ebe317',
  coordinateSystem: 'GCJ02',
  note: '610100 使用同源 610100_full.json 的全市 13 区县真实轮廓，不使用旧西安六区裁剪口径；缺失资产时才回退省级 Feature。'
});

export const provinceGeo = provinceGeoAsset;

const xianFeature = (provinceGeo.features || []).find(feature =>
  String(feature?.properties?.adcode) === '610100'
);

const xianProvinceFallbackGeo = {
  type: 'FeatureCollection',
  features: xianFeature ? [xianFeature] : []
};

const xianFullGeo = xianFullGeoAsset?.features?.length ? xianFullGeoAsset : xianProvinceFallbackGeo;

const cityDistrictGeo = {
  '610100': xianFullGeo,
  '610200': tongchuan,
  '610300': baoji,
  '610400': xianyang,
  '610500': weinan,
  '610600': yanan,
  '610700': hanzhong,
  '610800': yulin,
  '610900': ankang,
  '611000': shangluo
};

export const cityGeoByCode = Object.freeze(cityDistrictGeo);

const sourceCityFeatures = Array.isArray(provinceGeo.features) ? provinceGeo.features : [];
export const cityOptions = Object.freeze(sourceCityFeatures
  .map(feature => {
    const properties = feature?.properties || {};
    const code = properties.adcode == null ? '' : String(properties.adcode);
    return {
      code,
      name: String(properties.name || code),
      center: Array.isArray(properties.center) ? properties.center.slice(0, 2) : null,
      centroid: Array.isArray(properties.centroid) ? properties.centroid.slice(0, 2) : null
    };
  })
  .filter(city => city.code));

export default Object.freeze({ provinceGeo, cityGeoByCode, cityOptions });
