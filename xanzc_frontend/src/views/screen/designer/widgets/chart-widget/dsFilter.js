// 属性面板数据源下拉的联动过滤纯函数（前端侧；后端 RPT-43005 等校验为另一道防线）。
// 图表元数据约束：needTimeseries → 仅 ds_type=TIMESERIES；needKinds → 仅对应 source_kind。

/**
 * 按图表元数据过滤可选数据源列表。
 * @param {Array<{dsType: string, sourceKind: string}>} list listScreenDatasources 响应行
 * @param {{needTimeseries?: boolean, needKinds?: string[]}|null} meta charts/*.js 图表元数据
 */
export function filterDatasourcesByMeta(list, meta) {
  const rows = Array.isArray(list) ? list : [];
  if (!meta) return rows;
  return rows.filter(d =>
    (!meta.needTimeseries || d.dsType === 'TIMESERIES') &&
    (!Array.isArray(meta.needKinds) || !meta.needKinds.length || meta.needKinds.includes(d.sourceKind))
  );
}
