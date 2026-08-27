import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../http', () => ({ call: vi.fn().mockResolvedValue({ records: [], total: 0 }) }));

import { call } from '../http';
import {
  claimCustomer,
  listAvailableCustomers,
  listClaimedCustomers,
  validateCrossOrgMarketing,
} from '../customerMarketing';

describe('客户营销池与跨机构营销筛选 API', () => {
  beforeEach(() => call.mockClear());

  it('待认领池固定把 PUBLIC 来源和关键词传给后端', async () => {
    await listAvailableCustomers({
      keyword: '华夏',
      sourceType: 'PUBLIC',
      pageNo: 2,
      pageSize: 20,
    });

    expect(call).toHaveBeenCalledWith('get', '/customer-pool', {
      params: {
        keyword: '华夏',
        sourceType: 'PUBLIC',
        pageNo: 2,
        pageSize: 20,
      },
    }, null);
  });

  it('已认领池把 tab、关键词和来源传给后端', async () => {
    await listClaimedCustomers({
      tab: 'UNTOUCHED',
      keyword: '华夏',
      sourceType: 'PUBLIC',
      pageNo: 3,
      pageSize: 10,
    });

    expect(call).toHaveBeenCalledWith('get', '/claims/mine/customers', {
      params: {
        tab: 'UNTOUCHED',
        keyword: '华夏',
        sourceType: 'PUBLIC',
        pageNo: 3,
        pageSize: 10,
      },
    }, null);
  });

  it('跨机构校验按客户号传参而不是把客户主键暴露为检索条件', async () => {
    await validateCrossOrgMarketing('KH-20260827-001');

    expect(call).toHaveBeenCalledWith('get', '/cross-org-marketing/validate', {
      params: { custNo: 'KH-20260827-001' },
    }, null);
  });

  it('认领公开线索时携带来源线索标识，避免回退到旧客户主档认领表', async () => {
    await claimCustomer('CUST-1', 'LEAD-1');

    expect(call).toHaveBeenCalledWith('post', '/claims', {
      data: { custId: 'CUST-1', sourceLeadId: 'LEAD-1' },
    });
  });
});
