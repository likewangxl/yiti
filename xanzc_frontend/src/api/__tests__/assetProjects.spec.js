import { beforeEach, describe, expect, it, vi } from 'vitest';

const call = vi.fn();
vi.mock('../http', () => ({ call, unwrapPage: value => value }));

describe('资产立项正式 API', () => {
  beforeEach(() => call.mockReset());

  it('列表使用 MARKETING 正式路由并把元转换为万元', async () => {
    call.mockResolvedValue({ records: [{ id: 1, creditAmount: 250000 }], total: 1 });
    const { listAssetProjects } = await import('../assetProjects');
    const page = await listAssetProjects({ tab: 'MY' });
    expect(call).toHaveBeenCalledWith('get', '/marketing/asset-projects', { params: { tab: 'MY' } });
    expect(page.records[0].creditAmount).toBe(25);
  });

  it('新建完整转换四个金额字段且不调用旧 loans 路由', async () => {
    call.mockResolvedValue({ id: 9, creditAmount: 300000 });
    const { createAssetProject } = await import('../assetProjects');
    await createAssetProject({ custId: 1, projectTotalInvestment: 100, projectLoanAmount: 80, creditAmount: 30, creditExposureAmount: 20 });
    const [, url, config] = call.mock.calls[0];
    expect(url).toBe('/marketing/asset-projects');
    expect(config.data).toMatchObject({ projectTotalInvestment: 1000000, projectLoanAmount: 800000, creditAmount: 300000, creditExposureAmount: 200000 });
  });

  it('删除草稿携带乐观锁版本和审计原因', async () => {
    call.mockResolvedValue(null);
    const { deleteAssetProject } = await import('../assetProjects');
    await deleteAssetProject(9, 3, '重复创建');
    expect(call).toHaveBeenCalledWith('delete', '/marketing/asset-projects/9', {
      params: { lockVersion: 3, reason: '重复创建' }
    });
  });
});
