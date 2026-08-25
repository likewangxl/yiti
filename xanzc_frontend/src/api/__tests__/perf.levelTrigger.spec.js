import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../http', () => ({ call: vi.fn().mockResolvedValue({ accepted: true }) }));

import { call } from '../http';
import { triggerMetricLevelRecalc } from '../perf';

describe('按级别重算 API', () => {
  beforeEach(() => call.mockClear());

  it('按后端契约 POST 级别触发，并原样发送可选业绩分配日期', async () => {
    const payload = {
      level: 1,
      dataDate: '2026-08-17',
      reason: '补跑一级指标',
      allocDate: '2026-08-16'
    };

    await triggerMetricLevelRecalc(payload);

    expect(call).toHaveBeenCalledWith(
      'post',
      '/perf/metric-calc/level-trigger',
      { data: payload }
    );
  });

  it('不提供写请求 fallback，后端失败必须向调用方抛出', async () => {
    const error = new Error('接口失败');
    call.mockRejectedValueOnce(error);

    await expect(triggerMetricLevelRecalc({
      level: 2,
      dataDate: '2026-08-17',
      reason: '补跑二级指标'
    })).rejects.toBe(error);
    expect(call.mock.calls[0]).toHaveLength(3);
  });
});
