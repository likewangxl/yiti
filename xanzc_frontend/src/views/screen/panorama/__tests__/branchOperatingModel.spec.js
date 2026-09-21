import { describe, it, expect } from 'vitest';
import { buildBranchOperatingModel, parseBranchSource, comparableChange, toBranchDisplayUnits } from '../branchOperatingModel';

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
    expect(model.kpis.find(k => k.key === 'corpLoan')).toBeUndefined();
    expect(model.kpis.find(k => k.key === 'corpDeposit').yoy).toBeNull();
    expect(model.projects).toEqual([]);
    expect(model.teams).toEqual([]);
  });
  it('隔离随机数月均/贷款来源，存款使用按机构明细绑定而非固定本级单值', () => {
    const pkg = { schemaVersion: 2, canvasStyle: { dataClassification: 'LIVE', presentation: { template: 'corporate-overview-v1' } },
      components: [{ blockId: 39, propValue: { bindingKey: 'corpDeposit' } }, { blockId: 53, propValue: { bindingKey: 'corpLoan' } }, { blockId: 54, propValue: { bindingKey: 'corpRanking' } }],
      bindSnapshots: { '54': { bind: { fields: { orgCode: 'org_code', deposit: 'deposit' }, units: { deposit: 'YUAN' } } } } };
    const source = parseBranchSource({ state: 'published', screenCode: 'SCR_CORP_OVERVIEW', runtimeSchemaVersion: 2, orgScopeMode: 'NAMED_GROUP', renderPackageJson: JSON.stringify(pkg) });
    expect(source.renderPackage.components).toEqual([]);
    expect(source.branchDepositBinding.blockId).toBe(54);
    const model = buildBranchOperatingModel({ financial: { kpis: [{ key: 'corpLoan', value: 999 }, { key: 'corpDepositAverage', value: 999 }], trend: [{ date: '2026-09-20', deposit: 1, loan: 999 }] } });
    expect(model.kpis.some(k => k.value === 999)).toBe(false);
    expect(model.trend[0].loan).toBeNull();
    expect(model.parkedMetrics.length).toBeGreaterThan(0);
  });
  it('保留真实零值及超额目标，不把关注事项当流程待办', () => {
    const model = buildBranchOperatingModel({ orgCode: '109', financial: { targets: [{ actual: 12, target: 10 }], attention: [{ label: '目标未达标', count: 0 }] }, touch: { orgId: '109', pendingCount: 0, inProgressCount: 2, successCount: 3, slaWarningCount: 0 } });
    expect(model.targets[0].gap).toBe(-2);
    expect(model.marketing[0].count).toBe(0);
    expect(model.attention[0].label).toBe('目标未达标');
  });

  it('将支行金额从亿元转换为万元且不改变原模型，保留比例、项数、零值、负值和空值', () => {
    const model = {
      kpis: [
        { key: 'corpDeposit', value: 1.207989, unit: '亿元' },
        { key: 'negativeAmount', value: -2, unit: '亿元' },
        { key: 'missingAmount', value: null, unit: '亿元' },
        { key: 'corpNplRate', value: 44.45, unit: '%' },
        { key: 'touchTotal', value: 0, unit: '项' }
      ],
      targets: [{ actual: 0, target: 0.5, gap: 0.5, unit: '亿元' }],
      trend: [{ date: '2026-09-18', deposit: 0, loan: -1, rate: 44.45 }],
      composition: [{ name: '对公', value: 0.25, unit: '亿元' }, { name: '不良率', value: 44.45, unit: '%' }]
    };
    const snapshot = structuredClone(model);
    const display = toBranchDisplayUnits(model);

    expect(display.kpis).toEqual([
      { key: 'corpDeposit', value: 12079.89, unit: '万元' },
      { key: 'negativeAmount', value: -20000, unit: '万元' },
      { key: 'missingAmount', value: null, unit: '万元' },
      { key: 'corpNplRate', value: 44.45, unit: '%' },
      { key: 'touchTotal', value: 0, unit: '项' }
    ]);
    expect(display.targets[0]).toMatchObject({ actual: 0, target: 5000, gap: 5000, unit: '万元' });
    expect(display.trend).toEqual([{ date: '2026-09-18', deposit: 0, loan: -10000, rate: 44.45 }]);
    expect(display.trendUnit).toBe('万元');
    expect(display.composition).toEqual([{ name: '对公', value: 2500, unit: '万元' }, { name: '不良率', value: 44.45, unit: '%' }]);
    expect(model).toEqual(snapshot);
  });

  it('不重复转换已经是万元的趋势，并从目标源日期标记目标和过期存款批次', () => {
    const display = toBranchDisplayUnits({ trendUnit: '万元', trend: [{ date: '2026-09-18', deposit: 120, loan: -10 }] });
    expect(display.trend).toEqual([{ date: '2026-09-18', deposit: 120, loan: -10 }]);
    expect(display.trendUnit).toBe('万元');

    const model = buildBranchOperatingModel({
      financial: {
        kpis: [{ key: 'corpDeposit', value: 0.01207989, unit: '亿元', date: '2026-07-22' }],
        targets: [{ actual: 0, target: 0.5 }],
        sourceQualities: {
          corpTargets: { dataDate: '2026-09-18' },
          corpDeposit: { dataDate: '2026-07-22', status: 'STALE', ageDays: 61 }
        }
      }
    });
    expect(model.targetDate).toBe('2026-09-18');
    expect(model.targets[0].date).toBe('2026-09-18');
    expect(model.kpis.find(k => k.key === 'rate')).toMatchObject({ date: '2026-09-18' });
    expect(model.kpis.find(k => k.key === 'corpDeposit').status).toBe('历史批次 · 61天前');
  });
});
