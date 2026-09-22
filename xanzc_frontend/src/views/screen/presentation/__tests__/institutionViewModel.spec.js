import { describe, expect, it } from 'vitest';
import { buildInstitutionViewModel } from '../model/institutionViewModel.js';

const rules = {
  allowedOperatingLevels: ['BRANCH_1', 'BRANCH_2'],
  allowedOrgNatures: ['BRANCH']
};

const directory = [
  {
    orgCode: 'A', orgName: '同名机构', operatingLevel: 'BRANCH_1', orgNature: 'BRANCH',
    active: true, authorized: true, located: true, lng: 108.9, lat: 34.2, coordSys: 'GCJ02'
  },
  {
    orgCode: 'B', orgName: '同名机构', operatingLevel: 'BRANCH_2', orgNature: 'BRANCH',
    active: true, authorized: true, located: false, metrics: { deposit: 0 }
  }
];

describe('institutionViewModel', () => {
  it('只消费服务端授权目录，保留同名机构并按明确属性白名单过滤', () => {
    const result = buildInstitutionViewModel(directory, rules, {
      fallbackInstitutions: [{ orgCode: 'OUTSIDE', orgName: '不应进入' }]
    });

    expect(result.displayInstitutions.map(item => item.orgCode)).toEqual(['A', 'B']);
    expect(result.displayInstitutions.every(item => item.authorized)).toBe(true);
    expect(result.displayInstitutions[0].duplicateName).toBe(true);
    expect(result.displayInstitutions[1].duplicateName).toBe(true);
    expect(result.displayInstitutions).not.toEqual(expect.arrayContaining([
      expect.objectContaining({ orgCode: 'OUTSIDE' })
    ]));
  });

  it('停用、无权、属性不匹配和缺失属性的机构不进入展示集合', () => {
    const result = buildInstitutionViewModel([
      ...directory,
      { orgCode: 'DISABLED', orgName: '停用', operatingLevel: 'BRANCH_1', orgNature: 'BRANCH', active: false, authorized: true },
      { orgCode: 'FORBIDDEN', orgName: '无权', operatingLevel: 'BRANCH_1', orgNature: 'BRANCH', active: true, authorized: false },
      { orgCode: 'MICRO', orgName: '小微', operatingLevel: 'MICRO', orgNature: 'MICRO', active: true, authorized: true },
      { orgCode: 'MISSING', orgName: '属性缺失', active: true, authorized: true }
    ], rules);

    expect(result.displayInstitutions.map(item => item.orgCode)).toEqual(['A', 'B']);
    expect(result.contributionUnknown).toEqual(expect.arrayContaining([
      expect.objectContaining({ orgCode: 'DISABLED', reason: 'INACTIVE' }),
      expect.objectContaining({ orgCode: 'FORBIDDEN', reason: 'UNAUTHORIZED' }),
      expect.objectContaining({ orgCode: 'MICRO', reason: 'OPERATING_LEVEL_NOT_ALLOWED' }),
      expect.objectContaining({ orgCode: 'MISSING', reason: 'OPERATING_LEVEL_MISSING' })
    ]));
  });

  it('无过滤规则时 fail-close 并标记 UNCONFIRMED，不按名称或编码猜测', () => {
    const result = buildInstitutionViewModel(directory, {});

    expect(result.displayInstitutions).toEqual([]);
    expect(result.status).toBe('UNCONFIRMED');
    expect(result.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'FILTER_RULES_UNCONFIRMED' })
    ]));
    expect(result.contributionUnknown).toEqual(expect.arrayContaining([
      expect.objectContaining({ orgCode: 'A', reason: 'FILTER_RULES_UNCONFIRMED' })
    ]));
  });

  it('缺坐标仍保留可访问机构，located=false；合法零值与空指标状态分别保留', () => {
    const result = buildInstitutionViewModel([
      { ...directory[0], metrics: { deposit: 0, customers: null } },
      { ...directory[1], metrics: {} }
    ], rules);

    expect(result.displayInstitutions).toEqual(expect.arrayContaining([
      expect.objectContaining({ orgCode: 'A', located: true, metrics: { deposit: 0, customers: null }, status: 'READY' }),
      expect.objectContaining({ orgCode: 'B', located: false, lng: null, lat: null, status: 'NO_METRIC' })
    ]));
    expect(result.displayInstitutions.find(item => item.orgCode === 'A').metrics.deposit).toBe(0);
  });

  it('父子或经营归属重叠不双计，保留节点但标记不能现场汇总，不修改上级指标', () => {
    const result = buildInstitutionViewModel([
      { orgCode: 'PARENT', orgName: '上级', operatingLevel: 'BRANCH_1', orgNature: 'BRANCH', active: true, authorized: true, metrics: { deposit: 100 } },
      { orgCode: 'CHILD', orgName: '下级', operatingLevel: 'BRANCH_2', orgNature: 'BRANCH', parentOrgCode: 'PARENT', active: true, authorized: true, metrics: { deposit: 40 } },
      { orgCode: 'OWNER', orgName: '经营归属', operatingLevel: 'BRANCH_2', orgNature: 'BRANCH', ownerOperatingOrgCode: 'PARENT', active: true, authorized: true, metrics: { deposit: 20 } }
    ], rules);

    expect(result.displayInstitutions.map(item => item.orgCode)).toEqual(['PARENT', 'CHILD', 'OWNER']);
    expect(result.displayInstitutions.find(item => item.orgCode === 'PARENT').metrics.deposit).toBe(100);
    expect(result.displayInstitutions.every(item => item.contributionStatus === 'CANNOT_AGGREGATE')).toBe(true);
    expect(result.contributionUnknown).toEqual(expect.arrayContaining([
      expect.objectContaining({ orgCode: 'PARENT', reason: 'PARENT_CHILD_OVERLAP' }),
      expect.objectContaining({ orgCode: 'CHILD', reason: 'PARENT_CHILD_OVERLAP' }),
      expect.objectContaining({ orgCode: 'OWNER', reason: 'OPERATING_OWNERSHIP_OVERLAP' })
    ]));
  });

  it('目录外的额外指标记录被拒绝，不伪造或扩展展示集合', () => {
    const result = buildInstitutionViewModel(directory, rules, {
      contributions: [
        { orgCode: 'A', metrics: { deposit: 0 } },
        { orgCode: 'NOT_AUTHORIZED', metrics: { deposit: 999 } }
      ]
    });

    expect(result.displayInstitutions.find(item => item.orgCode === 'A').metrics.deposit).toBe(0);
    expect(result.displayInstitutions.map(item => item.orgCode)).not.toContain('NOT_AUTHORIZED');
    expect(result.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'UNAUTHORIZED_CONTRIBUTION', orgCode: 'NOT_AUTHORIZED' })
    ]));
  });

  it('重复机构编码不产生双行，并以明确问题标记冲突', () => {
    const result = buildInstitutionViewModel([
      ...directory,
      { ...directory[0], orgName: '同编码另一名称', metrics: { deposit: 2 } }
    ], rules);

    expect(result.displayInstitutions.map(item => item.orgCode)).toEqual(['A', 'B']);
    expect(result.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'DUPLICATE_ORG_CODE', orgCode: 'A' })
    ]));
  });

  it('从发布响应的 institutionRules 消费精确集合，不按名称或编码补规则', () => {
    const result = buildInstitutionViewModel({
      institutionRules: rules,
      panoramaInstitutions: directory,
      contributions: [{ orgCode: 'A', metrics: { deposit: 1 } }]
    });

    expect(result.status).toBe('READY');
    expect(result.filters).toEqual(rules);
    expect(result.displayInstitutions.map(item => item.orgCode)).toEqual(['A', 'B']);
  });

  it('机构规则任一维度为空时 fail-close，不展示授权目录', () => {
    const result = buildInstitutionViewModel(directory, {
      allowedOperatingLevels: ['BRANCH_1'],
      allowedOrgNatures: []
    });

    expect(result.status).toBe('UNCONFIRMED');
    expect(result.displayInstitutions).toEqual([]);
    expect(result.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'FILTER_RULES_UNCONFIRMED' })
    ]));
  });
});
