import { describe, expect, it, vi } from 'vitest';
import { readAllPerformancePages, loadBranchPerformance } from '../branchPerformanceLoader';

describe('支行KPI全量读取', () => {
  it('拒绝旧后端未确认机构范围、无效total及错误主体类型', async () => {
    await expect(readAllPerformancePages(async () => ({ total: 0, records: [] }), { orgCode: '105' })).rejects.toThrow(/范围/);
    await expect(readAllPerformancePages(async () => ({ total: null, records: [] }), {})).rejects.toThrow(/响应/);
    await expect(readAllPerformancePages(async () => ({ total: 1, records: [{ subjectId: '1', subjectType: 'ORG' }] }), { subjectType: 'EMP' })).rejects.toThrow(/主体/);
  });
  it('读取所有分页后才交付排名，保持同机构同日期同方案', async () => {
    const read = vi.fn(async params => ({ scopeOrgCode: params.orgCode, total: 151, metrics: [{ metricCode: 'M1' }], records: Array.from({ length: params.pageNo === 1 ? 100 : 51 }, (_, i) => ({ subjectType: params.subjectType, subjectId: `${params.pageNo}-${i}` })) }));
    const result = await readAllPerformancePages(read, { orgCode: '105', schemeCode: 'S1', dataDate: '2026-09-20', subjectType: 'EMP' });
    expect(result.records).toHaveLength(151);
    expect(read).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 2, orgCode: '105', schemeCode: 'S1', dataDate: '2026-09-20', subjectType: 'EMP' }));
  });
  it('响应缺页、重复对象或总数变化都拒绝给出部分排名', async () => {
    await expect(readAllPerformancePages(async () => ({ total: 3, records: [] }), {})).rejects.toThrow(/完整/);
    await expect(readAllPerformancePages(async () => ({ total: 2, records: [{ subjectId: '1' }, { subjectId: '1' }] }), {})).rejects.toThrow(/重复/);
    let count = 0;
    await expect(readAllPerformancePages(async () => ({ total: ++count === 1 ? 101 : 102, records: Array.from({ length: 100 }, (_, i) => ({ subjectId: `${count}-${i}` })) }), {})).rejects.toThrow(/变化/);
  });
  it('过期请求停止读取，错误和空响应不可变成零人数', async () => {
    await expect(readAllPerformancePages(async () => null, {})).rejects.toThrow(/响应/);
    await expect(readAllPerformancePages(async () => { throw new Error('403'); }, {})).rejects.toThrow('403');
    const read = vi.fn();
    await expect(readAllPerformancePages(read, {}, () => false)).rejects.toThrow(/过期/);
    expect(read).not.toHaveBeenCalled();
  });
  it('ORG与EMP各自保留完整指标列，缺关键上下文不发送请求', async () => {
    const read = vi.fn(async p => ({ scopeOrgCode: p.orgCode, total: 0, metrics: [{ metricCode: p.subjectType }], records: [] }));
    const result = await loadBranchPerformance(read, { orgCode: '105', schemeCode: 'S1', dataDate: '2026-09-20' });
    expect(result.orgMetrics[0].metricCode).toBe('ORG');
    expect(result.empMetrics[0].metricCode).toBe('EMP');
    await expect(loadBranchPerformance(read, { orgCode: '105' })).rejects.toThrow(/方案|日期/);
    expect(read).toHaveBeenCalledTimes(2);
  });
});
