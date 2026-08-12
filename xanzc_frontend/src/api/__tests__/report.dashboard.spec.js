import { beforeEach, describe, expect, it, vi } from 'vitest';

const { callMock } = vi.hoisted(() => ({ callMock: vi.fn() }));

vi.mock('../http', () => ({
  call: callMock,
  default: { request: vi.fn() },
  API_BASE: '/api'
}));

import { getDashboardPresident } from '../report';

const presidentResponse = {
  dataDate: '2026-08-10',
  stats: [{ label: '存款日均', value: 1200, unit: '万', trend: '↑ 2%', trendType: 'up' }],
  depositTrend: {
    title: '全行存款趋势',
    xAxis: ['2026-07', '2026-08'],
    series: [{ name: '一般性存款月均', unit: '万元', data: [1100, 1200] }]
  },
  loanTrend: {
    title: '全行贷款趋势',
    xAxis: ['2026-07', '2026-08'],
    series: [{ name: '对公一般性贷款', unit: '万元', data: [900, 980] }]
  },
  orgRanking: [{
    rank: 2,
    orgName: '城东支行',
    achievementRate: 91.2,
    target: 800,
    actual: 729.6,
    unit: '万元'
  }]
};

describe('行长仪表盘 API 适配', () => {
  beforeEach(() => {
    callMock.mockReset();
  });

  it('把 date 映射为 dataDate，并保留趋势系列和排名业务字段', async () => {
    callMock.mockResolvedValue(presidentResponse);

    const result = await getDashboardPresident({ orgCode: 'XA001', date: '2026-08-10' });

    expect(callMock).toHaveBeenCalledWith(
      'get',
      '/reports/dashboard/president',
      { params: { orgCode: 'XA001', dataDate: '2026-08-10' } },
      null
    );
    expect(result.trend.xAxis).toEqual(['2026-07', '2026-08']);
    expect(result.trend.series).toEqual([
      { name: '一般性存款月均', unit: '万元', data: [1100, 1200] },
      { name: '对公一般性贷款', unit: '万元', data: [900, 980] }
    ]);
    expect(result.ranking).toEqual([{
      rank: 2,
      orgName: '城东支行',
      achievementRate: 91.2,
      target: 800,
      actual: 729.6,
      unit: '万元'
    }]);
  });

  it('真实 HTTP 失败时继续 reject，不回退为仿真报表', async () => {
    const failure = new Error('网络不可用');
    callMock.mockRejectedValue(failure);

    await expect(getDashboardPresident({ date: '2026-08-10' })).rejects.toBe(failure);
  });
});
