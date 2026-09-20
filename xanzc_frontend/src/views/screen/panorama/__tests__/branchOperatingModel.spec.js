import { describe, it, expect } from 'vitest';
import { buildBranchOperatingModel, parseBranchSource, comparableChange } from '../branchOperatingModel';

describe('支行真实数据展示模型', () => {
  it('拒绝测试包，保留后端发布身份且不接受未发布包', () => {
    const response = { state: 'published', screenCode: 'SCR_CORP_OVERVIEW', orgScopeMode: 'NAMED_GROUP', runtimeSchemaVersion: 2,
      renderPackageJson: JSON.stringify({ schemaVersion: 2, canvasStyle: { dataClassification: 'TEST', presentation: { template: 'corporate-overview-v1' } }, components: [], bindSnapshots: {} }) };
    expect(() => parseBranchSource(response)).toThrow(/测试|演示/);
    response.renderPackageJson = response.renderPackageJson.replace('TEST', 'LIVE');
    expect(parseBranchSource(response).screenCode).toBe('SCR_CORP_OVERVIEW');
    expect(() => parseBranchSource({ ...response, state: 'draft' })).toThrow();
  });
  it('只比较精确同期日期，不用最近点冒充同期，分母为零不生成增长率', () => {
    expect(comparableChange(12, '2026-09-20', [{ date: '2026-08-19', deposit: 10 }], 'deposit', 1)).toBeNull();
    expect(comparableChange(12, '2026-09-20', [{ date: '2026-08-20', deposit: 10 }], 'deposit', 1)).toBe(20);
    expect(comparableChange(12, '2026-09-20', [{ date: '2026-08-20', deposit: 0 }], 'deposit', 1)).toBeNull();
    expect(comparableChange(12, '2026-09-20', [{ date: '2026-08-20', deposit: 10 }, { date: '2026-08-20', deposit: 8 }], 'deposit', 1)).toBeNull();
  });
  it('目标只用目标源实际值；机构不匹配的触达数据拒绝；缺失不变零', () => {
    const model = buildBranchOperatingModel({ orgCode: '109', orgName: '高新开发区支行',
      financial: { kpis: [{ key: 'corpDeposit', value: 80, unit: '亿元', date: '2026-09-20' }], targets: [{ name: '109', actual: 8, target: 10 }], trend: [] },
      touch: { orgId: '110', pendingCount: 100 }, view: { renderPackage: { canvasStyle: {} } } });
    expect(model.targets[0]).toMatchObject({ actual: 8, target: 10, gap: 2, rate: 80 });
    expect(model.marketing).toEqual([]);
    expect(model.kpis.find(k => k.key === 'corpLoan').value).toBeNull();
    expect(model.kpis.find(k => k.key === 'corpDeposit').yoy).toBeNull();
    expect(model.projects).toEqual([]);
    expect(model.teams).toEqual([]);
  });
  it('保留真实零值及超额目标，不把关注事项当流程待办', () => {
    const model = buildBranchOperatingModel({ orgCode: '109', financial: { targets: [{ actual: 12, target: 10 }], attention: [{ label: '目标未达标', count: 0 }] }, touch: { orgId: '109', pendingCount: 0, inProgressCount: 2, successCount: 3, slaWarningCount: 0 } });
    expect(model.targets[0].gap).toBe(-2);
    expect(model.marketing[0].count).toBe(0);
    expect(model.attention[0].label).toBe('目标未达标');
  });
});
