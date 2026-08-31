// 属性面板数据源下拉的联动过滤纯函数（前端侧；后端 RPT-43005 等校验为另一道防线）。
import { isDatasourceCompatible } from '@/utils/screenScope';
// 图表元数据约束：needTimeseries → 仅 ds_type=TIMESERIES；needKinds → 仅对应 source_kind。

/** 严格读取 configJson；字符串解析失败、数组或空值均按不安全处理。 */
export function parseDatasourceConfig(configJson) {
  let config = configJson;
  if (typeof configJson === 'string') {
    try { config = JSON.parse(configJson); } catch { return null; }
  }
  if (!config || typeof config !== 'object' || Array.isArray(config)) return null;
  return config;
}

/**
 * 命名机构组只允许服务端可审计的机构宽表主体。
 * 这组值必须与 ScreenCanvasServiceImpl.isNamedGroupSafeDatasource 保持完全一致，
 * 解析失败必须拒绝，不能把未知配置当作安全数据源。
 */
export function isNamedGroupSafeDatasource(datasource = {}) {
  if (datasource.sourceKind !== 'WIDE_TABLE') return false;
  const config = parseDatasourceConfig(datasource.configJson);
  return config?.table === 'ORG_INDEX_RESULT' && config?.subjectCol === 'org_code';
}

function normalizeDatasourceBizLine(value) {
  const normalized = String(value ?? '').trim().toUpperCase();
  return normalized || 'COMMON';
}

function matchesScreenScope(datasource, scope) {
  if (!scope || typeof scope !== 'object') return true;

  if (Object.prototype.hasOwnProperty.call(scope, 'bizLine')) {
    if (!isDatasourceCompatible(
      normalizeDatasourceBizLine(scope.bizLine), normalizeDatasourceBizLine(datasource.bizLine)
    )) return false;
  }
  if (String(scope.orgScopeMode || '').toUpperCase() === 'NAMED_GROUP'
      && !isNamedGroupSafeDatasource(datasource)) {
    return false;
  }
  return true;
}

/**
 * 按图表 meta、屏业务条线和机构范围过滤数据源。
 * 第三个参数为可选屏范围，保留两参数调用以兼容旧属性面板/单测。
 */
export function filterDatasourcesByMeta(list, meta, screenScope = null) {
  const rows = Array.isArray(list) ? list : [];
  return rows.filter(d =>
    (!meta || (
      (!meta.needTimeseries || d.dsType === 'TIMESERIES') &&
      (!Array.isArray(meta.needKinds) || !meta.needKinds.length || meta.needKinds.includes(d.sourceKind))
    )) && matchesScreenScope(d, screenScope)
  );
}
