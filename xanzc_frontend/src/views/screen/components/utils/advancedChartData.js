// 六种扩展图表共用的数据探测函数。所有输入均来自 /api/screen/data，
// 只能把可证明为数字的值交给 ECharts，脏行/空列则以空态或空洞呈现。

export function finiteNumber(value) {
  if (value == null || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

export function columnIndex(columns, preferred, fallback = -1) {
  const index = Array.isArray(columns) && preferred ? columns.indexOf(preferred) : -1;
  return index >= 0 ? index : fallback;
}

/** 返回非空值均可证明为数值的列，空洞允许存在，混合文本列不会被误当成数值列。 */
export function numericColumnIndexes(columns, rows, exclude = []) {
  const cols = Array.isArray(columns) ? columns : [];
  const data = Array.isArray(rows) ? rows : [];
  const excluded = new Set(exclude.filter(index => index >= 0));
  return cols.map((_, index) => index)
    .filter(index => !excluded.has(index))
    .filter(index => {
      const values = data.map(row => row?.[index]).filter(value => value != null && value !== '');
      return values.length > 0 && values.every(value => finiteNumber(value) != null);
    });
}

/** 首个含非数值标签的列；找不到时回退给指定首列。 */
export function firstCategoryIndex(columns, rows, exclude = [], fallback = 0) {
  const cols = Array.isArray(columns) ? columns : [];
  const data = Array.isArray(rows) ? rows : [];
  const excluded = new Set(exclude.filter(index => index >= 0));
  const match = cols.findIndex((_, index) => {
    if (excluded.has(index)) return false;
    return data.some(row => {
      const value = row?.[index];
      return value != null && value !== '' && finiteNumber(value) == null;
    });
  });
  return match >= 0 ? match : (cols.length ? Math.max(0, Math.min(fallback, cols.length - 1)) : -1);
}

export function uniqueValues(rows, index) {
  const result = [];
  const seen = new Set();
  for (const row of Array.isArray(rows) ? rows : []) {
    const value = row?.[index];
    if (value == null || value === '') continue;
    const key = String(value);
    if (!seen.has(key)) {
      seen.add(key);
      result.push(value);
    }
  }
  return result;
}
