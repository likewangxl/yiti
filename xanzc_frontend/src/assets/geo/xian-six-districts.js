import xianSixDistricts from './xian-six-districts.json';

/**
 * 西安运行态六区边界资产的可追溯信息。
 *
 * 几何来自 OpenStreetMap 行政边界关系，依 ODbL 1.0 重新分发；行政区名称及
 * 六位代码以陕西省民政厅 2025 年行政区划代码信息交叉核对。源数据为 WGS84，
 * 为与已经入库的 GCJ-02 机构点位一致，构建时逐坐标做 WGS84 -> GCJ-02 正向转换。
 * 该过程只筛选、转换坐标，不手绘、不平滑、不补点。
 */
export const XIAN_SIX_DISTRICTS_METADATA = Object.freeze({
  sourceName: 'OpenStreetMap contributors',
  license: 'ODbL 1.0',
  licenseUrl: 'https://www.openstreetmap.org/copyright',
  sourceUrl: 'https://nominatim.openstreetmap.org/lookup?format=geojson&polygon_geojson=1&osm_ids=R3226095%2CR3226093%2CR3226096%2CR3226088%2CR3226098%2CR3226089',
  retrievedAt: '2026-08-13',
  sourceContentSha256: '1b469566e42dad9cc21dbfce0f55b2b31f384ac362b0b3a25ddbf0d49ad93e95',
  deliveredContentSha256: 'fb3980443aada65db8703c1c29399ba065f91a34959b9072ebf6d373a086e389',
  sourceCoordinateSystem: 'WGS84 / EPSG:4326',
  deliveredCoordinateSystem: 'GCJ-02',
  coordinateTransform: 'WGS84 -> GCJ-02 标准正向转换；逐坐标执行，未简化几何。',
  cropMethod: '仅保留西安市未央、莲湖、新城、碑林、雁塔、长安六个县级行政区的 OSM relation Geometry；不手绘、不平滑、不补点。',
  administrativeCodeReference: Object.freeze({
    publisher: '陕西省民政厅',
    title: '2025年度陕西省行政区划代码信息',
    asOf: '2025-12-31',
    url: 'https://mzt.shaanxi.gov.cn/bs/bmcx/201711/t20171107_2703806.html'
  }),
  districts: Object.freeze([
    Object.freeze({ name: '未央区', adcode: '610112', osmRelationId: 3226095 }),
    Object.freeze({ name: '莲湖区', adcode: '610104', osmRelationId: 3226093 }),
    Object.freeze({ name: '新城区', adcode: '610102', osmRelationId: 3226096 }),
    Object.freeze({ name: '碑林区', adcode: '610103', osmRelationId: 3226088 }),
    Object.freeze({ name: '雁塔区', adcode: '610113', osmRelationId: 3226098 }),
    Object.freeze({ name: '长安区', adcode: '610116', osmRelationId: 3226089 })
  ])
});

export const XIAN_SIX_DISTRICTS_ATTRIBUTION = '边界数据：© OpenStreetMap contributors（ODbL）';

export default xianSixDistricts;
