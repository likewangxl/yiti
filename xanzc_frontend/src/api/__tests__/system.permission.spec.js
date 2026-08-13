import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../http', () => ({ call: vi.fn().mockResolvedValue(['P_1']) }));

import { call } from '../http';
import { getRoleResourceIds } from '../system';

describe('角色资源 API 的失败语义', () => {
  beforeEach(() => call.mockClear());

  it('读取角色资源不提供空集合 fallback，失败必须由调用方处理', async () => {
    await getRoleResourceIds('role-1');

    expect(call).toHaveBeenCalledWith('get', '/admin/roles/role-1/resources', {});
  });
});
