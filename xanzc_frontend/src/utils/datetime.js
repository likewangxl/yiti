// 日期时间格式化工具
//
// 后端 LocalDateTime 序列化为 ISO 字符串（如 "2026-05-21T01:15:49" 或 "2026-05-21T01:15:49.123"），
// 直接绑到 el-table-column / 模板会显示带 'T' 和小数点的原始串。
// 统一在这里处理：去掉 'T'、截掉毫秒，最终形如 "2026-05-21 01:15:49"。

/** ISO 串 / 时间字符串 → "YYYY-MM-DD HH:mm:ss"，空值返回 '-' */
export function fmtDateTime(v) {
  if (v == null || v === '') return '-';
  return String(v).replace('T', ' ').slice(0, 19);
}

/** 同上但截到分钟（"YYYY-MM-DD HH:mm"） */
export function fmtDateMinute(v) {
  if (v == null || v === '') return '-';
  return String(v).replace('T', ' ').slice(0, 16);
}

/** 只取日期部分（"YYYY-MM-DD"） */
export function fmtDate(v) {
  if (v == null || v === '') return '-';
  return String(v).slice(0, 10);
}

/** el-table-column :formatter 签名包装：(row, column, cellValue) → 显示串 */
export const fmtDateTimeCol = (_row, _col, v) => fmtDateTime(v);
export const fmtDateMinuteCol = (_row, _col, v) => fmtDateMinute(v);
export const fmtDateCol = (_row, _col, v) => fmtDate(v);
