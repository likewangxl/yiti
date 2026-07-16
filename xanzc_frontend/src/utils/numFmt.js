// 数字显示格式化工具。
// truncate2：把"带小数的数字字符串"截断保留两位小数（不四舍五入，直接砍尾、不足补零）。
// 仅处理形如 -?\d+\.\d+ 的纯小数字符串；整数、文本、日期、空一律原样返回——
// 避免把工号/编号等整数或"看着像数字"的编码误加小数点。原始数据不变，仅用于显示。
const DECIMAL_RE = /^-?\d+\.\d+$/;

export function truncate2(s) {
  if (s == null) return s;
  const str = String(s);
  if (!DECIMAL_RE.test(str)) return s;
  const [intPart, frac] = str.split('.');
  const frac2 = (frac + '00').slice(0, 2);
  return `${intPart}.${frac2}`;
}
