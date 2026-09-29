import { describe, expect, it } from 'vitest';
import { buildCityBranchRankingModel, CITY_BRANCH_RANKING_TABS } from '../cityBranchRankingModel';

const tabs = CITY_BRANCH_RANKING_TABS;
const institutions = [
  {
    orgCode: 'A', orgName: '甲支行', cityCode: '610100', metrics: {
      retailDepositRate: 80, retailLoanRate: 0, retailNplRate: 0.3,
      corpDepositRate: 95, corpLoanRate: 80, corpNplRate: 0.2, rate: 999
    }
  },
  {
    orgCode: 'B', orgName: '乙支行', cityCode: '610100', metrics: {
      retailDepositRate: 120, retailLoanRate: 60, retailNplRate: 0.1,
      corpDepositRate: 110, corpLoanRate: 130, corpNplRate: 0.4
    }
  },
  {
    orgCode: 'C', orgName: '丙支行', cityCode: '610100', metrics: {
      retailDepositRate: 0, retailLoanRate: null, retailNplRate: null,
      corpDepositRate: 75, corpLoanRate: 0, corpNplRate: -0.1
    }
  },
  { orgCode: 'OUT', orgName: '外市支行', cityCode: '610200', metrics: { retailDepositRate: 999, corpDepositRate: 999 } }
];

describe('cityBranchRankingModel', () => {
  it('固定六个 tab 顺序，完成率降序、不良率按实际率升序，同分并列', () => {
    expect(tabs.map(tab => tab.key)).toEqual([
      'retailDepositRate', 'retailLoanRate', 'retailNplRate',
      'corpDepositRate', 'corpLoanRate', 'corpNplRate'
    ]);
    expect(tabs.map(tab => tab.label)).toEqual(['零售存款', '零售贷款', '零售贷款不良率', '对公存款', '对公贷款', '对公贷款不良率']);
    const result = buildCityBranchRankingModel({ cityCode: '610100', institutions, activeTabKey: 'retailDepositRate' });
    expect(result.rows.map(row => row.orgCode)).toEqual(['B', 'A', 'C']);
    expect(result.rows.map(row => row.rank)).toEqual([1, 2, 3]);
    expect(result.rows.find(row => row.orgCode === 'A')?.value).toBe(80);
    expect(result.rows.some(row => row.value === 999)).toBe(false);

    const npl = buildCityBranchRankingModel({ cityCode: '610100', institutions, activeTabKey: 'retailNplRate' });
    expect(npl.rows.map(row => row.orgCode)).toEqual(['B', 'A']);
    expect(npl.rows.map(row => row.value)).toEqual([0.1, 0.3]);
  });

  it('零完成率有效，通用 rate 不冒充六维字段；缺失/负不良率进入未提供列表', () => {
    const result = buildCityBranchRankingModel({ cityCode: '610100', institutions, activeTabKey: 'retailLoanRate' });
    expect(result.rows.some(row => row.orgCode === 'A' && row.value === 0)).toBe(true);
    expect(result.rows.some(row => row.orgCode === 'A' && row.value === 999)).toBe(false);
    expect(result.missingRows.map(row => row.orgCode)).toEqual(expect.arrayContaining(['C']));
    expect(result.missingCount).toBeGreaterThan(0);
    const npl = buildCityBranchRankingModel({ cityCode: '610100', institutions, activeTabKey: 'corpNplRate' });
    expect(npl.rows.map(row => row.orgCode)).toEqual(['A', 'B']);
    expect(npl.rows.some(row => row.value === 0)).toBe(false);
    expect(npl.missingRows.map(row => row.orgCode)).toContain('C');
    const alias = buildCityBranchRankingModel({
      cityCode: '610100',
      institutions: [{ orgCode: 'D', cityCode: '610100', metrics: { corporateNplRate: 0.05 } }],
      activeTabKey: 'corpNplRate'
    });
    expect(alias.rows).toMatchObject([{ orgCode: 'D', value: 0.05 }]);
  });

  it('百分比/RATIO 只按明确字段换算，机构范围和带 org 身份 block 行严格过滤', () => {
    const result = buildCityBranchRankingModel({
      cityCode: '610100',
      institutions: [{ orgCode: 'A', orgName: '甲支行', cityCode: '610100' }, { orgCode: 'B', orgName: '乙支行', cityCode: '610100' }],
      sourcePresentation: { display: { components: [{ dataRefs: [{ blockId: 31 }] }] } },
      blockResults: { 31: {
        rows: [
          { org_code: 'OUT', retailDepositRate: 999 },
          { org_code: 'A', retailDepositRate: 0.8, retailLoanRate: 80, retailNplRate: 0.2 },
          { org_code: 'A', retailDepositRate: 0.9, retailLoanRate: 81, retailNplRate: 0.3 },
          { org_code: 'B', retailDepositRate: 0.7, retailLoanRate: 70, retailNplRate: 0.1 }
        ],
        unitByField: { retailDepositRate: 'RATIO', retailLoanRate: 'PERCENT', retailNplRate: 'PERCENT' }
      } },
      activeTabKey: 'retailDepositRate'
    });
    expect(result.rows.map(row => row.orgCode)).toEqual(['B']);
    expect(result.rows[0]).toMatchObject({ value: 70, unit: '%' });
    expect(result.missingRows.map(row => row.orgCode)).toContain('A');
  });

  it('读取 displayPresentation 包装中的机构 block，支持中文目标字段并合并同值重复 block，不消费无机构汇总行', () => {
    const sourcePresentation = {
      screenCode: 'SCR_PROVINCE',
      displayPresentation: {
        displaySchemaVersion: 1,
        template: 'branch-overview-v1',
        display: { components: [{ dataRefs: [{ blockId: 31 }, { blockId: 38 }, { blockId: 58 }, { blockId: 59 }, { blockId: 60 }] }] }
      }
    };
    const row = {
      org_code: 'A', city_code: '610100',
      retailDepositRate: 88, retailLoanRate: 81, retailNplRate: 0.25,
      corpDepositRate: 76, corpLoanRate: 91, corpNplRate: 0.15,
      '测试_零售存款目标完成率': 88,
      '测试_零售贷款目标完成率': 81,
      '测试_对公存款目标完成率': 76,
      '测试_对公贷款目标完成率': 91
    };
    const blocks = {
      31: { rows: [{ retailDepositRate: 999 }], unitByField: { retailDepositRate: 'PERCENT' } },
      38: { rows: [{ ...row, retailDepositRate: undefined, retailLoanRate: undefined, retailNplRate: undefined, corpDepositRate: undefined, corpLoanRate: undefined, corpNplRate: undefined }], unitByField: {
        '测试_零售存款目标完成率': 'PERCENT', '测试_零售贷款目标完成率': 'PERCENT', '测试_对公存款目标完成率': 'PERCENT', '测试_对公贷款目标完成率': 'PERCENT'
      } },
      58: { rows: [{ ...row }], unitByField: { retailDepositRate: 'PERCENT', retailLoanRate: 'PERCENT', retailNplRate: 'PERCENT', corpDepositRate: 'PERCENT', corpLoanRate: 'PERCENT', corpNplRate: 'PERCENT' } },
      59: { rows: [{ ...row }] },
      60: { rows: [{ ...row }] }
    };
    const result = buildCityBranchRankingModel({
      cityCode: '610100',
      institutions: [{ orgCode: 'A', orgName: '甲支行', cityCode: '610100' }],
      sourcePresentation,
      blockResults: blocks,
      activeTabKey: 'retailDepositRate'
    });
    expect(result.rows).toMatchObject([{ orgCode: 'A', value: 88 }]);
    expect(buildCityBranchRankingModel({ cityCode: '610100', institutions: [{ orgCode: 'A', cityCode: '610100' }], sourcePresentation, blockResults: blocks, activeTabKey: 'corpNplRate' }).rows[0].value).toBe(0.15);
  });

  it('拒绝空白完成率及不同值的重复机构 block，避免把冲突源默取首行', () => {
    const sourcePresentation = { display: { components: [{ dataRefs: [{ blockId: 38 }, { blockId: 58 }] }] } };
    const result = buildCityBranchRankingModel({
      cityCode: '610100',
      institutions: [{ orgCode: 'A', cityCode: '610100' }],
      sourcePresentation,
      blockResults: {
        38: { rows: [{ orgCode: 'A', retailDepositRate: 10 }], unitByField: { retailDepositRate: 'PERCENT' } },
        58: { rows: [{ orgCode: 'A', retailDepositRate: 80 }], unitByField: { retailDepositRate: 'PERCENT' } }
      },
      activeTabKey: 'retailDepositRate'
    });
    expect(result.rows).toHaveLength(0);
    expect(result.missingRows.map(row => row.orgCode)).toEqual(['A']);
    const duplicate = buildCityBranchRankingModel({
      cityCode: '610100', institutions: [{ orgCode: 'A', cityCode: '610100' }], sourcePresentation,
      blockResults: { 38: { rows: [{ orgCode: 'A', retailDepositRate: 80 }, { orgCode: 'A', retailDepositRate: 80 }], unitByField: { retailDepositRate: 'PERCENT' } }, 58: { rows: [{ orgCode: 'A', retailDepositRate: 80 }], unitByField: { retailDepositRate: 'PERCENT' } } },
      activeTabKey: 'retailDepositRate'
    });
    expect(duplicate.rows).toHaveLength(0);
    const blank = buildCityBranchRankingModel({
      cityCode: '610100', institutions: [{ orgCode: 'A', cityCode: '610100' }], sourcePresentation,
      blockResults: { 38: { rows: [{ orgCode: 'A', retailDepositRate: '  '}], unitByField: { retailDepositRate: 'PERCENT' } } },
      activeTabKey: 'retailDepositRate'
    });
    expect(blank.rows).toHaveLength(0);
  });
});
