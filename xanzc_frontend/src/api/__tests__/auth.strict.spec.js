import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../http', () => ({
  API_BASE: '/api',
  USE_MOCK: false,
  call: vi.fn().mockResolvedValue([]),
  default: { get: vi.fn() }
}));

import http, { call } from '../http';
import { getCurrentUser, getMyMenus, getMyPermissions } from '../auth';

beforeEach(() => vi.clearAllMocks());

describe('认证与授权关键 GET 严格失败语义', () => {
  it.each([
    ['current-user', getCurrentUser, '/api/auth/current-user'],
    ['my-menus', getMyMenus, '/api/auth/my-menus'],
    ['permissions', getMyPermissions, '/api/auth/permissions']
  ])('%s 遇到 403 时原样拒绝，不能回退 mock', async (_name, invoke, url) => {
    const denied = new Error('403 denied');
    http.get.mockRejectedValueOnce(denied);

    await expect(invoke()).rejects.toBe(denied);
    expect(http.get).toHaveBeenCalledWith(url);
    expect(call).not.toHaveBeenCalled();
  });
});
