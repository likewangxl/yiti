import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../MarketingCustomerOwnerDialog.vue', import.meta.url), 'utf8');

describe('营销客户主办权弹窗契约', () => {
  it('支持管理员取消主办和客户经理转交两种模式', () => {
    expect(source).toContain('allowUnassign');
    expect(source).toContain('UNASSIGN');
    expect(source).toContain('TRANSFER');
    expect(source).toContain('转交原因');
    expect(source).toContain("emit('submit'");
  });
});
