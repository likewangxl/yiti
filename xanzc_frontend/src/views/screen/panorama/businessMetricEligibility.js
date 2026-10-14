import { parseDatasourceConfig } from '../designer/widgets/chart-widget/dsFilter';

/** 配置候选资格与实际取数质量分别验证；排序、搜索和名称筛选不能放宽此门禁。 */
export function qualifyBusinessMetricEntries(entries, { fieldSpec, innerType, sources = [], period, savedBindings = [] } = {}) {
  return entries.map(entry => {
    const reasons = [entry.disabledReason].filter(Boolean);
    if (!fieldSpec?.unitKinds?.includes(entry.unit)) reasons.push('原始单位不适用于当前展示内容');
    if (fieldSpec?.semantic === 'change') {
      const declared = (entry.semantics || []).some(semantic => ['change', 'changeRate', 'periodChangeRate'].includes(semantic));
      const saved = savedBindings.some(binding => String(binding.dsId) === String(entry.sourceId)
        && binding.fields?.change === entry.field && binding.units?.change === entry.unit);
      if (!declared && !saved) reasons.push('未声明较上期变化用途，请使用已核对的变化指标或配置历史比较');
    }
    if (innerType === 'METRIC_CARD') {
      const source = sources.find(item => String(item.id) === String(entry.sourceId));
      const config = parseDatasourceConfig(source?.configJson);
      const kind = String(source?.sourceKind || '').toUpperCase();
      const group = config?.aggregation?.groupBy;
      const scalar = kind === 'M98_STAT' || (kind === 'WIDE_TABLE'
        && (group === 'NONE' || (group === 'DATE' && period === 'LATEST')));
      if (!scalar) reasons.push('来源未声明单值汇总，不能将机构明细或指标明细直接作为单值卡片');
    }
    return { ...entry, disabled: entry.disabled || reasons.length > 0, disabledReason: reasons.join('；') };
  });
}
