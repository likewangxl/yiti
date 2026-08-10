// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';

const axiosState = vi.hoisted(() => ({ rejected: null }));
vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      request: vi.fn(),
      get: vi.fn(),
      interceptors: {
        request: { use: vi.fn() },
        response: {
          use: vi.fn((_fulfilled, rejected) => { axiosState.rejected = rejected; })
        }
      }
    }))
  }
}));
vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), error: vi.fn() }
}));

import { registerAuthorizationReset } from '@/stores/authorizationSnapshot';
import '../http';

describe('HTTP 401 会话清理', () => {
  beforeEach(() => {
    sessionStorage.setItem('xanzc:user', '{"empId":"A"}');
    window.location.hash = '#/workspace';
  });

  it('非登录请求收到 401 时同步清理授权快照后跳登录页', async () => {
    const reset = vi.fn();
    const unregister = registerAuthorizationReset(reset);
    const error = {
      response: { status: 401, data: {} },
      config: { url: '/api/auth/current-user' }
    };

    await expect(axiosState.rejected(error)).rejects.toBe(error);
    expect(reset).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem('xanzc:user')).toBeNull();
    expect(window.location.hash).toContain('#/login?redirect=');
    unregister();
  });
});
