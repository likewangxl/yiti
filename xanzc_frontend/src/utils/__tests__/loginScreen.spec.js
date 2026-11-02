import { describe, expect, it } from 'vitest';
import { selectLoginScreenTarget, targetRoute } from '../loginScreen';

describe('login screen selection', () => {
  it('主角色优先于低 priority 的其他角色', () => {
    const user = { mainOrgCode: 'B01', roles: [
      { roleId: 'R2', primary: false }, { roleId: 'R1', primary: true }
    ] };
    expect(selectLoginScreenTarget(user, { rules: [
      { roleId: 'R2', target: 'RETAIL', priority: 1 }, { roleId: 'R1', target: 'PERSONAL', priority: 99 }
    ] })).toBe('PERSONAL');
  });
  it('无主角色时按 priority 和 roleId 稳定选择，WORKSPACE 可显式终止', () => {
    const user = { roles: [{ roleId: 'B' }, { roleId: 'A' }] };
    expect(selectLoginScreenTarget(user, { rules: [
      { roleId: 'B', target: 'WORKSPACE', priority: 2 }, { roleId: 'A', target: 'RETAIL', priority: 2 }
    ] })).toBe('RETAIL');
  });
  it('支行目标缺少主机构时不构造猜测路由', () => {
    expect(targetRoute('BRANCH', {})).toBeNull();
    expect(targetRoute('PERSONAL', {}).query).toEqual({ from: 'login' });
  });

  it('主机构号本身不构成支行授权，必须持有已验证的父屏与城市上下文', () => {
    expect(targetRoute('BRANCH', { mainOrgCode: 'B01' })).toBeNull();
  });
});
