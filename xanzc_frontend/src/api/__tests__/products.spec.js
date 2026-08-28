import { beforeEach, describe, expect, it, vi } from 'vitest';

const call = vi.hoisted(() => vi.fn());
vi.mock('../http', () => ({ call }));

import { listActiveProducts } from '../products';

describe('产品候选 API', () => {
  beforeEach(() => call.mockReset());

  it('按 ACTIVE 状态完整读取分页产品资料库', async () => {
    call
      .mockResolvedValueOnce({ records: [{ id: 'P1', productName: '产品一', status: 'ACTIVE' }], total: 2 })
      .mockResolvedValueOnce({ records: [{ id: 'P2', productName: '产品二', status: 'ACTIVE' }], total: 2 });

    await expect(listActiveProducts({ pageSize: 1 })).resolves.toEqual([
      { id: 'P1', productName: '产品一', status: 'ACTIVE' },
      { id: 'P2', productName: '产品二', status: 'ACTIVE' }
    ]);
    expect(call).toHaveBeenNthCalledWith(1, 'get', '/products', {
      params: { status: 'ACTIVE', pageNo: 1, pageSize: 1 }
    }, { records: [], total: 0 });
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/products', {
      params: { status: 'ACTIVE', pageNo: 2, pageSize: 1 }
    }, { records: [], total: 0 });
  });
});
