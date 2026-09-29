import { describe, expect, it } from 'vitest';
import { cleanOverviewLabel } from '../overviewDisplayLabels.js';

describe('cleanOverviewLabel', () => {
  it('清理中文和英文括号中的约定数据标记', () => {
    expect(cleanOverviewLabel('存款余额（本级·测试）')).toBe('存款余额');
    expect(cleanOverviewLabel('存款余额 (本级 · 测试)')).toBe('存款余额');
    expect(cleanOverviewLabel('存款余额（本级测试）')).toBe('存款余额');
    expect(cleanOverviewLabel('存款余额（主库）')).toBe('存款余额');
    expect(cleanOverviewLabel('存款余额 (分类样本)')).toBe('存款余额');
  });

  it('保留业务括号和其他文字', () => {
    expect(cleanOverviewLabel('营业收入（贴息）')).toBe('营业收入（贴息）');
    expect(cleanOverviewLabel('客户（本级·测试）余额（贴息）')).toBe('客户余额（贴息）');
    expect(cleanOverviewLabel('存款余额（分类样本外）')).toBe('存款余额（分类样本外）');
  });
});
