import { describe, expect, it } from 'vitest';
import { buildLegacyComponentCatalog } from '../legacyComponentCatalog';

describe('非 schema1 对公/零售 legacy 组件目录', () => {
  it('按对公页面真实组件拆分字段，不混入分行或虚构指标', () => {
    const entries = buildLegacyComponentCatalog('corporate-overview-v1');
    expect(entries.filter(item=>item.key!=='branches').every(item => item.kind === 'LEGACY_COMPONENT' && item.editable === true)).toBe(true);
    expect(entries.find(item=>item.key==='branches')).toMatchObject({kind:'INSTITUTION_MAP',readOnly:true,titleEditable:false});
    expect(entries.map(item => item.slot)).toEqual([
      'corpDeposit', 'corpDepositAverage', 'corpLoan', 'corpRevenue', 'corpCustomers', 'corpNplRate',
      'corpTrend', 'corpSegments', 'corpRanking', 'corpAttention', 'corpTargets', 'branches'
    ]);
    expect(entries.find(item => item.slot === 'corpDeposit')).toMatchObject({ fieldSemantics: ['value', 'change', 'date'] });
    expect(entries.find(item => item.slot === 'corpTrend')).toMatchObject({
      fieldSemantics: ['date', 'deposit', 'loan'], titleEditable: false
    });
    expect(entries.find(item => item.slot === 'corpRanking')).toMatchObject({
      fieldSemantics: ['orgCode', 'name', 'deposit', 'increase', 'rate', 'nplRate']
    });
    expect(entries.find(item => item.slot === 'corpSegments').derivedNotes).toContain('户均贷款由客群贷款与客户数派生，仅展示不可绑定');
    expect(entries.find(item => item.slot === 'corpDeposit').titleEditable).toBe(true);
  });

  it('按真实适配器始终输出存款字段的零售运行路径配置，不暴露未消费的旧排名指标', () => {
    const entries = buildLegacyComponentCatalog('retail-overview-v1');
    expect(entries.map(item => item.key)).toEqual([
      'retailAum', 'retailDeposit', 'retailDepositAverage', 'retailRevenue', 'retailValueCustomers',
      'retailLoan', 'retailNplRate', 'retailTrend', 'retailSegments', 'retailRanking',
      'retailAttention', 'retailTargets', 'branches'
    ]);
    expect(entries.find(item => item.slot === 'retailDeposit')).toMatchObject({ fieldSemantics: ['value', 'change', 'date'] });
    expect(entries.find(item => item.slot === 'retailTrend').fieldSemantics).toEqual(['date', 'deposit', 'depositAverage']);
    expect(entries.find(item => item.key === 'retailRanking')).toMatchObject({
      slot: 'retailRanking', fieldSemantics: ['orgCode', 'name', 'deposit', 'average', 'date'], titleEditable: false
    });
    expect(entries.find(item => item.key === 'retailRankingAum')).toBeUndefined();
    expect(entries.find(item => item.slot === 'retailSegments').derivedNotes).toContain('客户占比、资产占比和户均AUM由客户数与资产额派生，仅展示不可绑定');
    expect(entries.find(item => item.slot === 'retailDeposit').titleEditable).toBe(true);

    const modern = buildLegacyComponentCatalog('retail-overview-v1', {
      retailRanking: { fields: { orgCode: 'org_code', name: 'org_name', deposit: 'deposit', average: 'average' } }
    }).find(item => item.key === 'retailRanking');
    expect(modern.fieldSemantics).toEqual(['orgCode', 'name', 'deposit', 'average', 'date']);
  });

  it('非目标模板不扩展 legacy 目录', () => {
    expect(buildLegacyComponentCatalog('branch-overview-v1')).toEqual([]);
    expect(buildLegacyComponentCatalog('')).toEqual([]);
  });
});
