import { describe, expect, it } from 'vitest';
import {
  PARENT_BRANCH_SOURCE_SCREEN_CODES,
  buildParentBranchOperatingModel,
  parseParentBranchOperatingSource
} from '../parentBranchOperatingSource';

const rules = {
  allowedOperatingLevels: ['PRIMARY', 'NONE'],
  allowedOrgNatures: ['SECONDARY_BRANCH', 'OTHER'],
  displayOrgCodes: ['105', '451']
};
const sourceView = {
  state: 'draft', screenCode: 'SCR_PROVINCE', runtimeSchemaVersion: 2, orgScopeMode: 'NAMED_GROUP',
  institutionRules: rules,
  panoramaInstitutions: [{ orgCode: '105', orgName: '延兴门西路支行', cityCode: '610100', operatingLevel: 'NONE', orgNature: 'OTHER' }],
  renderPackageJson: JSON.stringify({
    schemaVersion: 2,
    canvasStyle: { dataClassification: 'TEST', presentation: { type: 'CODE', template: 'branch-overview-v1' } },
    components: [], bindSnapshots: {}
  })
};

describe('parentBranchOperatingSource', () => {
  it('从已配置单机构构成来源读取六项经营值，保留金额单位与日期', () => {
    const pkg = JSON.parse(sourceView.renderPackageJson);
    pkg.canvasStyle.presentation = { type: 'CODE', template: 'branch-overview-v1', displaySchemaVersion: 1, display: { components: [{
      componentId: 'mix', componentType: 'COMPOSITION_TABS', dataRefs: [{ blockId: 64 }], content: {
        tabs: [{ tabKey: 'deposit', label: '存款', corporateField: 'cd', retailField: 'rd', unit: 'HUNDRED_MILLION' }, { tabKey: 'loan', label: '贷款', corporateField: 'cl', retailField: 'rl', unit: 'HUNDRED_MILLION' }],
        incomeRatio: { numeratorField: 'fee', denominatorField: 'income', unit: 'HUNDRED_MILLION' }
      }
    }] } };
    const row = { cd: 1, rd: 2, cl: 3, rl: 4, fee: 0, income: 0.8 };
    const model = buildParentBranchOperatingModel({ sourceView: { ...sourceView, renderPackageJson: JSON.stringify(pkg) }, orgCode: '105', model: {
      institutions: [{ orgCode: '105' }], kpis: [], blockResults: { '64': { ...row, rows: [row], unit: 'HUNDRED_MILLION', dataDate: '2026-09-20' } }
    } });
    expect(model.kpis).toEqual(expect.arrayContaining([
      expect.objectContaining({ key: 'corpDeposit', value: 1, unit: '亿元' }), expect.objectContaining({ key: 'retailDeposit', value: 2 }),
      expect.objectContaining({ key: 'corpLoan', value: 3 }), expect.objectContaining({ key: 'retailLoan', value: 4 }),
      expect.objectContaining({ key: 'intermediaryIncome', value: 0 }), expect.objectContaining({ key: 'revenue', value: 0.8, date: '2026-09-20' })
    ]));
  });
  it('只接受固定省级 draft 源屏，并校验运行包身份', () => {
    expect(PARENT_BRANCH_SOURCE_SCREEN_CODES).toEqual(['SCR_PROVINCE', 'SCR_PROVINCE_MAP_V2']);
    expect(parseParentBranchOperatingSource(sourceView, { sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' }))
      .toMatchObject({ screenCode: 'SCR_PROVINCE', state: 'draft', runtimeSchemaVersion: 2 });
    const draftV1 = {
      ...sourceView,
      renderPackageJson: JSON.stringify({
        ...JSON.parse(sourceView.renderPackageJson), schemaVersion: 1
      })
    };
    expect(parseParentBranchOperatingSource(draftV1, { sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' }))
      .toMatchObject({ screenCode: 'SCR_PROVINCE', state: 'draft', runtimeSchemaVersion: 2 });
    expect(parseParentBranchOperatingSource({ ...sourceView, state: 'published' }, { sourceScreenCode: 'SCR_PROVINCE' }))
      .toMatchObject({ screenCode: 'SCR_PROVINCE', state: 'published', sourcePreview: '' });
    expect(() => parseParentBranchOperatingSource({
      ...sourceView, state: 'published', renderPackageJson: JSON.stringify({
        ...JSON.parse(sourceView.renderPackageJson), schemaVersion: 1
      })
    }, { sourceScreenCode: 'SCR_PROVINCE' })).toThrow(/schema/);
    expect(() => parseParentBranchOperatingSource({ ...sourceView, state: 'published' }, { sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' }))
      .toThrow(/draft/);
    expect(() => parseParentBranchOperatingSource(sourceView, { sourceScreenCode: 'SCR_CORP_OVERVIEW', sourcePreview: 'draft' }))
      .toThrow(/源屏/);
    expect(() => parseParentBranchOperatingSource(sourceView, { sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'published' }))
      .toThrow(/Preview|preview/);
    expect(() => parseParentBranchOperatingSource({ ...sourceView, institutionRules: null }, { sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' }))
      .toThrow(/规则/);
    expect(() => parseParentBranchOperatingSource({ ...sourceView, institutionRules: null, navigationRules: rules }, { sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' }))
      .toThrow(/institutionRules/);
    expect(() => parseParentBranchOperatingSource({ ...sourceView, panoramaInstitutions: [] }, { sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' }))
      .toThrow(/目录/);
    expect(() => parseParentBranchOperatingSource({ ...sourceView, renderPackageJson: JSON.stringify({
      schemaVersion: 3, canvasStyle: { dataClassification: 'TEST', presentation: { type: 'CODE', template: 'branch-overview-v1' } }, components: [], bindSnapshots: {}
    }) }, { sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' })).toThrow(/schema/);
  });

  it('只把单机构 scoped model 的已存在字段映射为经营总览契约，不从城市总量补齐', () => {
    const model = buildParentBranchOperatingModel({
      sourceView,
      orgCode: '105',
      model: {
        title: '省分行经营总览',
        dataDate: '2026-09-21',
        kpis: [
          { key: 'deposit', value: 12, unit: '亿元' },
          { key: 'loan', value: null, unit: '亿元' },
          { key: 'customers', value: 3, unit: '万户' },
          { key: 'revenue', value: null, unit: '亿元' },
          { key: 'rate', value: 88, unit: '%' }
        ],
        trend: [{ date: '2026-09', deposit: 12, loan: null }],
        targets: [{ key: 'deposit', actual: 12, target: 14, unit: '亿元' }],
        attention: [{ label: '来源关注', count: 1 }],
        institutions: sourceView.panoramaInstitutions,
        citySummaries: { '610100': { kpis: [{ key: 'deposit', value: 999 }] } }
      }
    });
    expect(model).toMatchObject({
      orgCode: '105', orgName: '延兴门西路支行', sourceLabel: expect.stringMatching(/TEST|测试|非生产/),
      kpis: expect.arrayContaining([
        expect.objectContaining({ key: 'deposit', value: 12 }),
        expect.objectContaining({ key: 'loan', value: null })
      ]),
      trend: [{ date: '2026-09', deposit: 12, loan: null }],
      targets: [{ key: 'deposit', actual: 12, target: 14, unit: '亿元' }],
      attention: [{ label: '来源关注', count: 1 }]
    });
    expect(model.kpis.find(item => item.key === 'deposit')?.value).not.toBe(999);
    expect(model.citySummaries).toEqual({});
    expect(model.institutions).toEqual([expect.objectContaining({ orgCode: '105' })]);
  });

  it('父屏单机构模型范围不精确时拒绝，机构身份以授权目录为准', () => {
    const scoped = {
      orgCode: '105',
      institutions: [{ orgCode: '105', orgName: '模型篡改名称', cityCode: '999999' }],
      kpis: [{ key: 'deposit', orgCode: '105', value: 12, unit: '亿元' }, { key: 'deposit', orgCode: '451', value: 999, unit: '亿元' }]
    };
    expect(buildParentBranchOperatingModel({ sourceView, orgCode: '105', model: scoped })).toMatchObject({
      orgCode: '105', orgName: '延兴门西路支行', cityCode: '610100'
    });
    expect(buildParentBranchOperatingModel({ sourceView, orgCode: '105', model: scoped }).kpis)
      .toEqual(expect.arrayContaining([expect.objectContaining({ orgCode: '105', value: 12 })]));
    expect(() => buildParentBranchOperatingModel({
      sourceView, orgCode: '105', model: { ...scoped, institutions: [...scoped.institutions, { orgCode: '451' }] }
    })).toThrow(/机构范围/);
    expect(() => buildParentBranchOperatingModel({ sourceView, orgCode: '105', model: { ...scoped, orgCode: '451' } }))
      .toThrow(/机构范围/);
  });

  it('源模型缺少核心 KPI 时保留明确空位，不从城市汇总补值', () => {
    const model = buildParentBranchOperatingModel({
      sourceView,
      orgCode: '105',
      model: { institutions: sourceView.panoramaInstitutions, kpis: [], citySummaries: { '610100': { kpis: [{ key: 'loan', value: 888 }] } } }
    });
    expect(model.kpis).toEqual(expect.arrayContaining([
      expect.objectContaining({ key: 'deposit', value: null }),
      expect.objectContaining({ key: 'loan', value: null }),
      expect.objectContaining({ key: 'customers', value: null }),
      expect.objectContaining({ key: 'revenue', value: null }),
      expect.objectContaining({ key: 'rate', value: null })
    ]));
    expect(model.kpis.find(item => item.key === 'loan')?.value).toBeNull();
  });
});
