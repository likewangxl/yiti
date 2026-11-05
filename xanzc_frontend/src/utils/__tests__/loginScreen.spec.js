import { describe, expect, it } from 'vitest';
import { selectLoginScreenTarget, targetRoute, resolveLoginScreen } from '../loginScreen';
import { vi } from 'vitest';

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

  it('系统管理员无规则时优先解析分行经营总览', async () => {
    const view = { screenCode: 'SCR_PROVINCE', state: 'published', runtimeSchemaVersion: 2,
      renderPackageJson: JSON.stringify({ schemaVersion: 2, components: [], bindSnapshots: {},
        canvasStyle: { presentation: { type: 'CODE', template: 'branch-overview-v1' } } }) };
    const menuStore = { hasUrl: vi.fn().mockReturnValue(true), resolveLandingPath: vi.fn().mockReturnValue('/workspace') };
    const result = await resolveLoginScreen({ user: { isSystemAdmin: true, roles: [{ roleCode: 'SYS_ADMIN' }] }, payload: { rules: [] }, menuStore,
      permissionStore: { canAccess: vi.fn().mockReturnValue(true) },
      listAvailableScreens: vi.fn().mockResolvedValue([{ screenCode: 'SCR_PROVINCE', template: 'branch-overview-v1', dataMode: 'TEST' }]),
      getScreenView: vi.fn().mockResolvedValue(view) });
    expect(result).toEqual({ name: 'CodeScreenPage', params: { template: 'branch-overview-v1' }, query: { from: 'login' } });
  });

  it('规则接口 403 时仍按授权目录解析管理员默认分行屏', async () => {
    const view = { screenCode: 'SCR_PROVINCE', state: 'published', runtimeSchemaVersion: 2,
      renderPackageJson: JSON.stringify({ schemaVersion: 2, components: [], bindSnapshots: {},
        canvasStyle: { presentation: { type: 'CODE', template: 'branch-overview-v1' } } }) };
    const result = await resolveLoginScreen({ user: { isSystemAdmin: true, roles: [{ roleCode: 'SYS_ADMIN' }] }, payload: { rules: [] },
      menuStore: { hasUrl: vi.fn().mockReturnValue(false), resolveLandingPath: vi.fn().mockReturnValue('/no-access') },
      permissionStore: { canAccess: vi.fn().mockReturnValue(true) },
      listAvailableScreens: vi.fn().mockResolvedValue([{ screenCode: 'SCR_PROVINCE', template: 'branch-overview-v1', dataMode: 'TEST' }]),
      getScreenView: vi.fn().mockResolvedValue(view) });
    expect(result.name).toBe('CodeScreenPage');
  });

  it('管理员即使主角色偏好个人屏也优先分行经营总览', async () => {
    const view = { screenCode: 'SCR_PROVINCE', state: 'published', runtimeSchemaVersion: 2,
      renderPackageJson: JSON.stringify({ schemaVersion: 2, components: [], bindSnapshots: {},
        canvasStyle: { presentation: { type: 'CODE', template: 'branch-overview-v1' } } }) };
    const getScreenView = vi.fn().mockResolvedValue(view);
    const result = await resolveLoginScreen({ user: { roles: [
      { roleId: 'R_PERSONAL', roleCode: 'PERSONAL', primary: true },
      { roleId: 'SYS_ADMIN', roleCode: 'SYS_ADMIN' }
    ] }, payload: { rules: [
      { roleId: 'R_PERSONAL', target: 'PERSONAL', priority: 1 },
      { roleId: 'SYS_ADMIN', target: 'PROVINCE', priority: 99 }
    ] }, menuStore: { hasUrl: vi.fn().mockReturnValue(true), resolveLandingPath: vi.fn().mockReturnValue('/workspace') },
    permissionStore: { canAccess: vi.fn().mockReturnValue(true) },
    listAvailableScreens: vi.fn().mockResolvedValue([{ screenCode: 'SCR_PROVINCE', template: 'branch-overview-v1', dataMode: 'TEST' }]), getScreenView });
    expect(result.params.template).toBe('branch-overview-v1');
    expect(getScreenView).toHaveBeenCalledTimes(1);
  });
});
