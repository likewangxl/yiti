const DATA_MARKER_PATTERN = /\s*[（(]\s*(?:本级\s*(?:[·•・]\s*)?测试|主库|分类样本)\s*[)）]/gu;

/**
 * 清理总览 KPI 标签末尾或中间明确的数据标记。
 *
 * 只处理约定的数据来源标记，保留业务括号（例如“贴息”）和原始 model。
 */
export function cleanOverviewLabel(value) {
  if (value === null || value === undefined) return '';
  return String(value)
    .replace(DATA_MARKER_PATTERN, '')
    .replace(/[ \t]{2,}/g, ' ')
    .trim();
}
