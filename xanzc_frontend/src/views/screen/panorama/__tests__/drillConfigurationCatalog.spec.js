import { describe, expect, it } from 'vitest';
import { buildDrillConfigurationCatalog } from '../drillConfigurationCatalog';

const parent = [
  { key: 'deposit', slot: 'deposit', label: '存款余额' },
  { key: 'loan', slot: 'loan', label: '贷款余额' },
  { key: 'branches', slot: 'branches', label: '机构分布' }
];

describe('drillConfigurationCatalog', () => {
  it('现代分行下钻替换原地图和机构排名，不能留下两个失真的父屏入口', () => {
    const model=buildDrillConfigurationCatalog({catalogEntries:[{key:'institution-map',kind:'INSTITUTION_MAP'}, {key:'institution-ranking',kind:'INSTITUTION_RANKING'}, {key:'business-revenue-operating',kind:'DISPLAY_COMPONENT'}]});
    expect(model.branchEntries.filter(e=>e.kind==='LINKED_COMPONENT').map(e=>e.parentKey)).toEqual(['business-revenue-operating']);
    expect(model.cityEntries.filter(e=>e.layoutGroup==='OVERVIEW')).toHaveLength(5);
  });
  it('城市目录完整覆盖实际区域，citySummary/branches缺失时保留只读缺来源状态', () => {
    const model = buildDrillConfigurationCatalog({ catalogEntries: parent, draftComponents: [] });
    expect(model.cityEntries.map(item => item.key)).toEqual(expect.arrayContaining([
      'city-deposit-total', 'city-deposit-distribution', 'city-loan-total', 'city-loan-distribution',
      'city-settlement-deposit', 'city-retail-deposit', 'city-retail-deposit-rate', 'city-retail-loan',
      'city-retail-loan-rate', 'city-corp-deposit', 'city-corp-deposit-rate', 'city-corp-loan',
      'city-corp-loan-rate', 'city-revenue', 'city-intermediary-income', 'city-institution-map', 'city-branch-ranking'
    ]));
    expect(model.cityEntries.filter(item => item.sourceMissing).length).toBeGreaterThan(0);
    expect(model.cityEntries.find(item => item.key === 'city-institution-map')).toMatchObject({ kind: 'READONLY_REGION', readOnly: true });
  });

  it('城市字段白名单精确对应citySummary/branches，且不包含金额排名字段', () => {
    const model = buildDrillConfigurationCatalog({ catalogEntries: parent, draftComponents: [
      { propValue: { bindingKey: 'citySummary' } }, { propValue: { bindingKey: 'branches' } }
    ] });
    const summary = model.cityEntries.filter(item => item.slot === 'citySummary');
    expect(summary.every(item => item.fieldSemantics.includes('cityCode') && item.fieldSemantics.includes('orgCode') && item.fieldSemantics.includes('cityName'))).toBe(true);
    expect(model.cityEntries.find(item => item.key === 'city-branch-ranking').fieldSemantics).toEqual([
      'orgCode', 'retailDepositRate', 'retailLoanRate', 'retailNplRate', 'corpDepositRate', 'corpLoanRate', 'corpNplRate'
    ]);
    expect(model.cityEntries.find(item => item.key === 'city-branch-ranking').fieldSemantics).not.toContain('deposit');
  });

  it('支行目录只复用真实父目录，显式branchTrend才增加趋势，地图和员工排名保持只读替换', () => {
    const model = buildDrillConfigurationCatalog({ catalogEntries: parent, draftComponents: [
      { propValue: { bindingKey: 'branchTrend' } }
    ] });
    expect(model.branchEntries.filter(item => item.kind === 'LINKED_COMPONENT').map(item => item.parentKey)).toEqual(['deposit', 'loan']);
    expect(model.branchEntries.find(item => item.parentKey === 'deposit').sourceEntry).toEqual(parent[0]);
    expect(model.branchEntries.find(item => item.slot === 'branchTrend')).toMatchObject({ fieldSemantics: ['date', 'deposit', 'loan'] });
    expect(model.branchEntries.find(item => item.key === 'branch-institution-map')).toMatchObject({ kind: 'READONLY_REGION', label: '未完成指标' });
    expect(model.branchEntries.find(item => item.key === 'branch-employee-ranking')).toMatchObject({ kind: 'READONLY_REGION', label: '当前支行员工排名' });
    expect(model.branchEntries.find(item => item.key === 'branch-institution-map').slot).not.toBe('branches');
  });
});
