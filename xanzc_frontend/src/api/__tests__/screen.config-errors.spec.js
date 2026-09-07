// 配置/权限读取必须沿真实 http.call 链路保留 transport 错误，不能由 GET fallback 改成空集合。
import { beforeEach, describe, expect, it, vi } from 'vitest';

const transport = vi.hoisted(() => ({ request: vi.fn() }));

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      request: (...args) => transport.request(...args),
      interceptors: {
        request: { use: vi.fn() },
        response: { use: vi.fn() }
      }
    }))
  }
}));
vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), error: vi.fn() }
}));
vi.mock('@/stores/authorizationSnapshot', () => ({
  resetAuthorizationSnapshots: vi.fn()
}));

import {
  listOrgGroups,
  listScreenAccessRoles,
  listScreenDatasources,
  listScreenPublishLogs,
  listScreenRoles,
  listScreens
} from '../screen';

describe('屏配置 GET 的真实 HTTP 错误边界', () => {
  beforeEach(() => {
    transport.request.mockReset();
  });

  it.each([
    ['access roles', () => listScreenAccessRoles(9105)],
    ['role candidates', () => listScreenRoles()],
    ['organization groups', () => listOrgGroups()],
    ['publish logs', () => listScreenPublishLogs(9105)],
    ['screen directory', () => listScreens()],
    ['datasource directory', () => listScreenDatasources()]
  ])('%s transport 403 原样 reject，不变成空数组', async (_name, invoke) => {
    const forbidden = Object.assign(new Error('forbidden'), {
      response: { status: 403, data: { message: 'forbidden' } },
      config: { url: '/api/config' }
    });
    transport.request.mockRejectedValueOnce(forbidden);

    await expect(invoke()).rejects.toBe(forbidden);
    expect(transport.request).toHaveBeenCalledTimes(1);
  });
});
