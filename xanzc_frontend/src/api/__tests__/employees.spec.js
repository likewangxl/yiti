import { beforeEach, describe, expect, it, vi } from 'vitest';

const call = vi.hoisted(() => vi.fn());
vi.mock('../http', () => ({ call }));

import * as employees from '../employees';

describe('通讯录员工 API', () => {
  beforeEach(() => call.mockReset());

  it('列表继续使用 GET /api/employees 分页查询', async () => {
    call.mockResolvedValueOnce({ records: [], total: 0 });

    await employees.pageEmployees({ pageNo: 1, pageSize: 20 });

    expect(call).toHaveBeenCalledWith('get', '/employees', { params: { pageNo: 1, pageSize: 20 } }, { records: [], total: 0 });
  });

  it('自助维护使用 PUT /api/employees/me，并只提交允许字段', async () => {
    call.mockResolvedValueOnce({ ok: true });

    await employees.updateMyEmployee({ mobile: '18600000000', email: 'me@example.com', responsibleProductIds: ['P1'] });

    expect(call).toHaveBeenCalledWith('put', '/employees/me', {
      data: { mobile: '18600000000', email: 'me@example.com', responsibleProductIds: ['P1'] }
    }, { ok: true });
    expect(employees.importEmployeesFile).toBeUndefined();
  });
});
