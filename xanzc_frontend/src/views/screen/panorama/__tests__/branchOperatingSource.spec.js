import { describe, it, expect, vi } from 'vitest';
import { readBranchDeposit, branchHistoryDates, loadBranchDeposit, chooseCoveredBranch } from '../branchOperatingSource';
const entry = { blockId: 54, bind: { fields: { orgCode: 'org_code', deposit: 'balance' }, units: { deposit: 'YUAN' } } };
const result = { columns: ['org_code', 'balance'], rows: [['109', 100000000]], quality: { dataDate: '2026-09-20', status: 'PARTIAL' } };
describe('支行机构来源', () => {
  it('从授权支行中优先选存款与目标都有值的机构，不选择分行或目录外机构', () => {
    const deposit = { ...result, rows: [['1', 9e12], ['109', null], ['110', 2e8], ['111', 1e8], ['OUTSIDE', 8e9]] };
    const targets = { columns: ['org_code', 'actual_value', 'target_value'], rows: [['111', 1e8, 2e8]] };
    const institutions = ['109', '110', '111'].map(orgCode => ({ orgCode }));
    expect(chooseCoveredBranch(deposit, targets, entry, institutions)).toBe('111');
  });
  it('读取精确机构的存款，拒绝多行、其他机构、模拟批次和未知单位', () => {
    expect(readBranchDeposit(result, entry, '109')).toMatchObject({ value: 1, date: '2026-09-20' });
    expect(() => readBranchDeposit(result, entry, '110')).toThrow(/机构/);
    expect(() => readBranchDeposit({ ...result, rows: [...result.rows, ...result.rows] }, entry, '109')).toThrow(/一行/);
    expect(() => readBranchDeposit({ ...result, quality: { dataClassification: 'TEST' } }, entry, '109')).toThrow(/测试/);
    expect(() => readBranchDeposit(result, { ...entry, bind: { ...entry.bind, units: {} } }, '109')).toThrow(/单位/);
    expect(readBranchDeposit({ ...result, rows: [['109', 0]] }, entry, '109').value).toBe(0);
  });
  it('历史包含五个月末、精确上月同期和去年同期，不把多日余额求和', async () => {
    const query = vi.fn(async body => body.period === 'LATEST' ? result : { ...result, rows: [['109', 50000000]] });
    const data = await loadBranchDeposit({ query, screenCode: 'SCR_CORP_OVERVIEW', entry, orgCode: '109' });
    expect(data.current.value).toBe(1);
    expect(data.trend).toHaveLength(6);
    expect(data.comparisonTrend.some(r => r.date === '2025-09-20')).toBe(true);
    expect(query.mock.calls.slice(1).every(([r]) => r.period === 'RANGE' && r.dateFrom === r.dateTo && r.contextParams.orgCode === '109')).toBe(true);
    expect(query.mock.calls.every(([r]) => r.blockId === 54 && !('dsId' in r))).toBe(true);
  });
  it('缺日不补零、迟到机构结果废弃', async () => {
    const query = vi.fn(async body => body.period === 'LATEST' ? result : { ...result, rows: [] });
    const data = await loadBranchDeposit({ query, screenCode: 'SCR_CORP_OVERVIEW', entry, orgCode: '109' });
    expect(data.trend.filter(r => r.deposit === null)).toHaveLength(5);
    const cancelled = await loadBranchDeposit({ query, screenCode: 'SCR_CORP_OVERVIEW', entry, orgCode: '109', isCurrent: () => false });
    expect(cancelled).toBeNull();
  });
  it('闰年同期取目标月有效日期，不生成未来点', () => {
    const dates = branchHistoryDates('2024-02-29');
    expect(dates.comparison).toContain('2023-02-28');
    expect(dates.comparison).toContain('2024-01-29');
    expect(dates.trend.every(d => d < '2024-02-29')).toBe(true);
  });
});
