import shaanxiGeo from './shaanxi.json';

/**
 * 西安市外轮廓静态适配资产。
 * 来源：仓库既有 DataV/GeoAtlas 陕西边界文件中的 adcode=610100 Feature；
 * 坐标系：GCJ-02（与既有地图资产一致）。运行时不访问互联网地图服务。
 * 生产上线前仍需归档可用于本项目的地图授权依据和以下内容哈希。
 */
export const XIAN_OUTLINE_METADATA = Object.freeze({
  sourceUrl: 'https://geo.datav.aliyun.com/areas_v3/bound/610000_full.json',
  fetchedAt: '2026-08-11',
  // SHA-256(JSON.stringify(extracted Feature))，用于资产变更检测。
  contentHash: '00aa9468d96b8101825416d3f86cbe879eb087daa6aacd70a1cb6ed6cf5af2b2',
  administrativeName: '西安市',
  adcode: 610100,
  coordinateSystem: 'GCJ02'
});

const feature = (shaanxiGeo.features || []).find(item =>
  Number(item?.properties?.adcode) === XIAN_OUTLINE_METADATA.adcode
  || item?.properties?.name === XIAN_OUTLINE_METADATA.administrativeName
);

// 空 FeatureCollection 仍可被 ECharts 注册，便于坏资产时以空态展示而非启动崩溃。
export const XIAN_OUTLINE = Object.freeze({
  type: 'FeatureCollection',
  features: feature ? [feature] : []
});

export default XIAN_OUTLINE;
