import { describe, expect, it, vi } from 'vitest';

const { http } = vi.hoisted(() => ({ http: { get: vi.fn(), put: vi.fn() } }));
vi.mock('../http', () => ({ default: http }));
import { getRoleLoginScreen, saveRoleLoginScreen } from '../loginScreen';

describe('loginScreen API', () => {
  it('PUT sends request body directly and encodes role id', async () => {
    http.put.mockResolvedValue({});
    await saveRoleLoginScreen('R/A', { target: 'PERSONAL', priority: 1, expectedVersion: '', reason: 'test' });
    expect(http.put).toHaveBeenCalledWith('/api/admin/sys/login-screens/R%2FA', {
      target: 'PERSONAL', priority: 1, expectedVersion: '', reason: 'test'
    });
  });
  it('GET encodes role id', async () => {
    http.get.mockResolvedValue({});
    await getRoleLoginScreen('R/A');
    expect(http.get).toHaveBeenCalledWith('/api/admin/sys/login-screens/R%2FA');
  });
});
