// @vitest-environment happy-dom
import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useUserStore } from '../user';

beforeEach(() => {
  sessionStorage.clear();
  setActivePinia(createPinia());
});

describe('user store 全角色语义', () => {
  it('角色摘要包含全部角色，次要角色也能被 hasRoleCode 命中', () => {
    const store = useUserStore();
    store.setUser({
      empId: 'E001',
      roles: [
        { roleId: 'R1', roleCode: 'NORMAL', roleChName: '普通用户' },
        { roleId: 'R2', roleCode: 'R_2FAB45A1', roleChName: '自由报表操作人' }
      ]
    });

    expect(store.roleSummary).toBe('普通用户、自由报表操作人');
    expect(store.hasRoleCode('R_2FAB45A1')).toBe(true);
    expect(store.hasRoleCode('MISSING')).toBe(false);
  });

  it('SYS_ADMIN 即使不是首位或历史 activeRoleId 指向普通角色，也按管理员处理', () => {
    const store = useUserStore();
    store.setUser({
      empId: 'E001',
      activeRoleId: 'R1',
      roles: [
        { roleId: 'R1', roleCode: 'NORMAL', roleChName: '普通用户' },
        { roleId: 'R2', roleCode: 'SYS_ADMIN', roleChName: '系统管理员' }
      ]
    });

    expect(store.isSystemAdmin).toBe(true);
  });
});
