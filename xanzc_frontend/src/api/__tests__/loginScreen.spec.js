import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn() }));
vi.mock('../http', () => ({ default: { get: mocks.get, put: mocks.put } }));

import { getLoginScreenRules, getRoleLoginScreen, saveRoleLoginScreen } from '../loginScreen';

describe('登录首屏规则 API', () => {
  beforeEach(() => vi.clearAllMocks());

  it('规则查询静默请求，便于 403/404 按默认大屏回退', () => {
    getLoginScreenRules();
    expect(mocks.get).toHaveBeenCalledWith('/api/sys/login-screens', { silent: true });
  });

  it('PUT sends request body directly and encodes role id', async () => {
    mocks.put.mockResolvedValue({});
    await saveRoleLoginScreen('R/A', { target: 'PERSONAL', priority: 1, expectedVersion: '', reason: 'test' });
    expect(mocks.put).toHaveBeenCalledWith('/api/admin/sys/login-screens/R%2FA', {
      target: 'PERSONAL', priority: 1, expectedVersion: '', reason: 'test'
    });
  });

  it('GET encodes role id without inheriting the landing request silent option', async () => {
    mocks.get.mockResolvedValue({});
    await getRoleLoginScreen('R/A');
    expect(mocks.get).toHaveBeenCalledWith('/api/admin/sys/login-screens/R%2FA');
  });
});
