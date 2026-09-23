/** 清理展示文案中的临时命名，指标编码和查询字段仍须使用原值。 */
export function screenDisplayText(value) {
  if (value === null || value === undefined) return '';
  return String(value).trim()
    .replace(/测试\s*[_－-]?\s*直营/gu, '')
    .replace(/测试\s*[_－-]?/gu, '联调')
    .trim();
}
