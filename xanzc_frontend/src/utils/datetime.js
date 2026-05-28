// 日期时间格式化工具
//
// 后端 LocalDateTime 序列化为 ISO 字符串（如 "2026-05-21T01:15:49" 或 "2026-05-21T01:15:49.123"），
// 也可能直接传 Date 对象或时间戳。统一在这里处理：
//   - 通过 new Date() 解析（兼容字符串 / Date / 数字时间戳），非法值返回 '-'
//   - 输出格式 "YYYY-MM-DD HH:mm:ss" / "YYYY-MM-DD HH:mm" / "YYYY-MM-DD"

function pad(n) {
  return n < 10 ? '0' + n : '' + n;
}

function toDate(value) {
  if (value === null || value === undefined || value === '') return null;
  const d = value instanceof Date ? value : new Date(value);
  return isNaN(d.getTime()) ? null : d;
}

/** ISO 串 / Date / 时间戳 → "YYYY-MM-DD HH:mm:ss"，空值/非法值返回 '-' */
export function fmtDateTime(value) {
  const d = toDate(value);
  if (!d) return '-';
  return (
    d.getFullYear() +
    '-' + pad(d.getMonth() + 1) +
    '-' + pad(d.getDate()) +
    ' ' + pad(d.getHours()) +
    ':' + pad(d.getMinutes()) +
    ':' + pad(d.getSeconds())
  );
}

/** 同上但截到分钟（"YYYY-MM-DD HH:mm"） */
export function fmtDateMinute(value) {
  const d = toDate(value);
  if (!d) return '-';
  return (
    d.getFullYear() +
    '-' + pad(d.getMonth() + 1) +
    '-' + pad(d.getDate()) +
    ' ' + pad(d.getHours()) +
    ':' + pad(d.getMinutes())
  );
}

/** 只取日期部分（"YYYY-MM-DD"） */
export function fmtDate(value) {
  const d = toDate(value);
  if (!d) return '-';
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
}

/** el-table-column :formatter 签名包装：(row, column, cellValue) → 显示串 */
export const fmtDateTimeCol = (_row, _col, v) => fmtDateTime(v);
export const fmtDateMinuteCol = (_row, _col, v) => fmtDateMinute(v);
export const fmtDateCol = (_row, _col, v) => fmtDate(v);

export default {
  fmtDateTime,
  fmtDateMinute,
  fmtDate,
  fmtDateTimeCol,
  fmtDateMinuteCol,
  fmtDateCol,
};
