// 自由报表单元格显示工具。
//
// 背景：导入时后端已用 POI DataFormatter 把每个单元格按其自身数字格式渲染成
// 「与 Excel 里肉眼所见完全一致」的文本，连同完整原值一起落库：
//   col_N        显示文本，如 "-0.0" / "54.5%"
//   col_N__raw   完整原值，如 "-0.000000500000000069889" / "0.545175438596492"
//   col_N__fmt   原数字格式，如 "0.0" / "0.0%"（仅导出复刻 Excel 用，前端不消费）
//
// 所以新批次前端不需要再做任何数字加工——直接显示 col_N 即可，
// 否则会把 "-0.0" 二次截断成 "-0.00"、把 "54.5%" 弄坏。
//
// 老批次（本次改造前导入的）没有 __raw/__fmt，col_N 里是原始数值串，
// 仍沿用既有的「截断两位」展示，保证存量数据观感不变。
import { truncate2 } from './numFmt';

const RAW_SUFFIX = '__raw';

/** 表格里显示的文本。 */
export function cellDisplay(row, key) {
  if (!row) return '';
  const v = row[key];
  if (v == null) return '';
  // 有 __raw 说明是新批次：col_N 已是 Excel 显示文本，原样输出
  if (row[key + RAW_SUFFIX] != null) return v;
  // 老批次：沿用截断两位
  return truncate2(v);
}

/** 点击/悬停时展示的完整值。 */
export function cellFull(row, key) {
  if (!row) return '';
  const raw = row[key + RAW_SUFFIX];
  if (raw != null && raw !== '') return raw;
  const v = row[key];
  return v == null ? '' : v;
}
