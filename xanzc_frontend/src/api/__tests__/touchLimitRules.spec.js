import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../http', () => ({
  call: vi.fn().mockResolvedValue({ records: [], total: 0 })
}));

import { call } from '../http';
import { listTouchLimitRules, updateTouchLimitRule } from '../customerMarketing';

describe('客户触达周期管理 API', () => {
  beforeEach(() => call.mockClear());

  it('按关键词分页查询触达周期规则', async () => {
    await listTouchLimitRules({ keyword: '重点', pageNo: 2, pageSize: 20 });

    expect(call).toHaveBeenCalledWith(
      'get',
      '/touch-limit-rules',
      { params: { keyword: '重点', pageNo: 2, pageSize: 20 } },
      { records: [], total: 0 },
    );
  });

  it('按标签 ID 更新周期维度和触达次数上限', async () => {
    await updateTouchLimitRule('TAG-1', { cycleUnit: 'WEEK', maxTouches: 8 });

    expect(call).toHaveBeenCalledWith(
      'put',
      '/touch-limit-rules/TAG-1',
      { data: { cycleUnit: 'WEEK', maxTouches: 8 } },
    );
  });
});
