// @vitest-environment happy-dom
// 严格机构树调用用于提交前选择器：真实 HTTP 失败必须向调用方传播，不能静默回退空树。
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      request: vi.fn(),
      interceptors: {
        request: { use: vi.fn() },
        response: { use: vi.fn() }
      }
    }))
  }
}));

import http from '../http';
import { getOrgTree } from '../orgs';

describe('getOrgTree strict', () => {
  beforeEach(() => vi.clearAllMocks());

  it('真实 HTTP 拒绝时 strict 调用向选择器传播错误，而不回退为空树', async () => {
    const failure = new Error('network unavailable');
    http.request.mockRejectedValueOnce(failure);

    await expect(getOrgTree({ strict: true })).rejects.toBe(failure);
    expect(http.request).toHaveBeenCalledWith(expect.objectContaining({
      method: 'get',
      url: '/api/orgs/tree',
      silent: true
    }));
  });
});
