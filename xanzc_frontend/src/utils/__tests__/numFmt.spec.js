import { describe, it, expect } from 'vitest';
import { truncate2 } from '../numFmt';

describe('truncate2 —— 小数截断保留两位(不四舍五入)', () => {
  it('多位小数直接砍尾到两位,不进位', () => {
    expect(truncate2('3.1779998')).toBe('3.17');
    expect(truncate2('2.999')).toBe('2.99');
    expect(truncate2('0.005')).toBe('0.00');
  });
  it('不足两位小数补零', () => {
    expect(truncate2('3.1')).toBe('3.10');
  });
  it('负数向零截断(砍尾)', () => {
    expect(truncate2('-2.999')).toBe('-2.99');
  });
  it('整数/文本/日期/空 原样不动', () => {
    expect(truncate2('1001')).toBe('1001');
    expect(truncate2('张三')).toBe('张三');
    expect(truncate2('2026-07-16')).toBe('2026-07-16');
    expect(truncate2('')).toBe('');
    expect(truncate2(null)).toBe(null);
    expect(truncate2(undefined)).toBe(undefined);
  });
  it('恰好两位小数保持不变', () => {
    expect(truncate2('3.17')).toBe('3.17');
  });
});
