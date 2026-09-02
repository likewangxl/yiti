import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/http', () => ({
  call: vi.fn()
}));

import { call } from '@/api/http';
import {
  executeTaskOverdue,
  getHomeRanking,
  getHomeSummary,
  getTaskOverdueList,
  getWarningPool
} from '@/api/redengine';

describe('红色引擎首页与预警池 API', () => {
  beforeEach(() => {
    call.mockReset();
    call.mockResolvedValue({});
  });

  it('首页聚合数据固定读取 /re/home/summary', async () => {
    await getHomeSummary();

    expect(call).toHaveBeenCalledWith('get', '/re/home/summary');
  });

  it('季度支部排名固定读取 /re/home/ranking', async () => {
    await getHomeRanking();

    expect(call).toHaveBeenCalledWith('get', '/re/home/ranking');
  });

  it('红牌和黄牌通过预警池聚合接口读取', async () => {
    await getWarningPool();

    expect(call).toHaveBeenCalledWith('get', '/re/home/warning-pool');
  });

  it('逾期扣分查询携带分页和筛选条件，执行使用请求体', async () => {
    const params = { pageNo: 2, pageSize: 10, taskType: 'TEMPORARY', keyword: '整改' };
    await getTaskOverdueList(params);
    await executeTaskOverdue({ assignmentId: 9, deductionPoints: 3, reason: '逾期核实' });

    expect(call).toHaveBeenNthCalledWith(1, 'get', '/re/home/overdue', { params });
    expect(call).toHaveBeenNthCalledWith(2, 'post', '/re/home/overdue/execute', {
      data: { assignmentId: 9, deductionPoints: 3, reason: '逾期核实' }
    });
  });
});
