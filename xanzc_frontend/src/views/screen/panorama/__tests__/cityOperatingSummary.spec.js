import { describe, expect, it } from 'vitest';
import { buildCityOperatingSummaries } from '../cityOperatingSummary.js';
import { buildCompositionTabsModel } from '../../presentation/model/compositionTabsModel.js';

const metrics = [
  ['BR_DIRECT_DEP', '直营存款余额', 'YUAN'],
  ['BR_DIRECT_RETAIL_DEP', '直营零售存款', 'YUAN'],
  ['BR_DIRECT_CORP_DEP', '直营对公存款', 'YUAN'],
  ['BR_DIRECT_DEP_TARGET', '零售存款目标值', 'YUAN'],
  ['BR_DIRECT_DEP_RATE', '零售存款目标完成率', 'PERCENT'],
  ['BR_DIRECT_LOAN', '直营贷款余额', 'YUAN'],
  ['BR_DIRECT_RETAIL_LOAN', '直营零售贷款', 'YUAN'],
  ['BR_DIRECT_LOAN_TARGET', '零售贷款目标值', 'YUAN'],
  ['BR_DIRECT_CUSTOMERS', '直营有效客户数', 'COUNT'],
  ['BR_DIRECT_REVENUE', '直营FTP收入', 'YUAN']
].map(([metricCode, metricName, unit]) => ({ metricCode, metricName, unit }));

const snapshot = { sourceDefinition: { config: {
  table: 'ORG_INDEX_RESULT', scopeMode: 'NAMED_GROUP', aggregation: { agg: 'SUM', groupBy: 'SUBJECT' }, metrics
} } };
const directory = [
  { orgCode: 'A', cityCode: '610300', cityName: '宝鸡市' },
  { orgCode: 'B', cityCode: '610300', cityName: '宝鸡市' },
  { orgCode: 'C', cityCode: '610400', cityName: '咸阳市' }
];
const row = (orgCode, date = '2026-11-09', n = 1) => ({ orgCode, data_date: date,
  直营存款余额: 100 * n, 直营零售存款: 40 * n, 直营对公存款: 60 * n, 零售存款目标值: 50 * n,
  直营贷款余额: 80 * n, 直营零售贷款: 30 * n, 零售贷款目标值: 40 * n,
  直营有效客户数: 10 * n, 直营FTP收入: 2 * n
});
const table = (rows = [row('A'), row('B')]) => ({ blockId: 60, dataDate: '2026-11-09', rows,
  columnsMeta: metrics.map(m => ({ col: m.metricName, alias: m.metricName, unit: m.unit })),
  quality: { subjectValueMode: 'EXCLUSIVE', batchId: 'batch-1' }
});

describe('cityOperatingSummary', () => {
  it('城市构成读取源单位为元，按父屏万元显示时只转换一次', () => {
    const presentation = { displaySchemaVersion: 1, display: { components: [{ componentId: 'mix', componentType: 'COMPOSITION_TABS', dataRefs: [{ blockId: 60 }], content: { tabs: [{ tabKey: 'deposit', corporateField: '直营对公存款', retailField: '直营零售存款', totalField: '直营存款余额', unit: 'TEN_THOUSAND' }] } }] } };
    const city = buildCityOperatingSummaries({ table: table(), snapshot, directory })['610300'];
    const tab = buildCompositionTabsModel(presentation, city).tabs[0];
    expect(tab.total.value).toBe(0.02);
    expect(tab.corporate.value).toBe(0.012);
  });
  it('按授权机构城市隔离并聚合金额，比例按合计分子分母计算', () => {
    const result = buildCityOperatingSummaries({ table: table(), snapshot, directory });
    expect(result['610300'].kpis).toEqual(expect.arrayContaining([
      expect.objectContaining({ key: 'deposit', value: 200, unit: 'YUAN' }),
      expect.objectContaining({ key: 'retailDepositRate', value: 80, unit: 'PERCENT' }),
      expect.objectContaining({ key: 'loan', value: 160 }),
      expect.objectContaining({ key: 'customers', value: 20 })
    ]));
    expect(result['610400']).toBeUndefined();
    expect(result['610300'].blockResults['60'].rows).toHaveLength(1);
  });

  it('缺行、重复机构或混合日期时 fail close，不把缺失当零', () => {
    expect(buildCityOperatingSummaries({ table: table([row('A')]), snapshot, directory })['610300']).toBeUndefined();
    expect(buildCityOperatingSummaries({ table: table([row('A'), row('A'), row('B')]), snapshot, directory })['610300']).toBeUndefined();
    expect(buildCityOperatingSummaries({ table: table([row('A'), row('B', '2026-11-08')]), snapshot, directory })['610300']).toBeUndefined();
  });

  it('拒绝非 EXCLUSIVE 或非正式来源，并保留零值', () => {
    const zero = row('A', '2026-11-09', 0); const zeroB = row('B', '2026-11-09', 0);
    expect(buildCityOperatingSummaries({ table: table([zero, zeroB]), snapshot: { sourceDefinition: { config: { ...snapshot.sourceDefinition.config, aggregation: { agg: 'AVG', groupBy: 'SUBJECT' } } } }, directory })['610300']).toBeUndefined();
    const result = buildCityOperatingSummaries({ table: { ...table([zero, zeroB]), quality: { subjectValueMode: 'EXCLUSIVE' } }, snapshot, directory });
    expect(result['610300'].kpis.find(k => k.key === 'deposit').value).toBe(0);
    expect(result['610300'].kpis.find(k => k.key === 'retailDepositRate').value).toBeNull();
  });

  it('允许机构行省略日期，并只接受同批次历史基期与可信父级引用', () => {
    const current = table([row('A'), row('B')]);
    const parent = { sourceDefinition: { config: { ...snapshot.sourceDefinition.config, metrics: metrics.slice(0, 2) } } };
    const history = table([row('A', '', 0.9), row('B', '', 0.9)]);
    delete history.rows[0].data_date; delete history.rows[1].data_date;
    history.dataDate = '2026-10-31'; history.quality = { ...history.quality, batchId: 'batch-1' };
    const result = buildCityOperatingSummaries({ table: current, snapshot, directory,
      snapshots: { '31': parent, '999': { sourceDefinition: { config: { ...snapshot.sourceDefinition.config, table: 'OTHER' } } } },
      presentation: { display: { components: [{ dataRefs: [{ blockId: 31 }] }, { dataRefs: [{ blockId: 999 }] }] } },
      historyTables: { '2026-10-31': history }
    });
    expect(result['610300'].blockResults['31']).toBeTruthy();
    expect(result['610300'].blockResults['999']).toBeUndefined();
    expect(result['610300'].rangeResults['31'].rows).toHaveLength(1);
    expect(result['610300'].rangeResults['31'].rows[0].date).toBe('2026-10-31');
  });
});
