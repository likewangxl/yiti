import { describe, expect, it, vi } from 'vitest';
import {
  BUSINESS_LINE_TARGETS,
  buildNavigationQuery,
  classifyInstitutionLayer,
  navigationRulesOf,
  parseNavigationQuery,
  resolveAuthorizedScreen,
  resolveInstitution,
  routeForBusinessLine,
  routeForInstitution
} from '../navigationModel';

const entries = [
  { screenCode: 'SCR_PROVINCE', template: 'branch-overview-v1', dataMode: 'TEST' },
  { screenCode: 'SCR_CORP_OVERVIEW', template: 'corporate-overview-v1', dataMode: 'LIVE' },
  { screenCode: 'SCR_RETAIL_OVERVIEW', template: 'retail-overview-v1', dataMode: 'LIVE' }
];

const view = screenCode => ({
  screenCode,
  navigationRules: {
    allowedOperatingLevels: ['PRIMARY_BRANCH'],
    allowedOrgNatures: ['BRANCH'],
    hiddenOperatingLevels: ['COMMUNITY_BRANCH'],
    hiddenOrgNatures: ['COMMUNITY']
  },
  panoramaInstitutions: [
    { orgCode: 'ORG-1', cityCode: '610100', operatingLevel: 'PRIMARY_BRANCH', orgNature: 'BRANCH' },
    { orgCode: 'ORG-2', cityCode: '610100', operatingLevel: 'COMMUNITY_BRANCH', orgNature: 'COMMUNITY' }
  ]
});

describe('S13 navigation contract', () => {
  it('only exposes fixed route targets and never accepts an arbitrary URL', () => {
    expect(BUSINESS_LINE_TARGETS).toMatchObject({
      COMMON: { template: 'branch-overview-v1', screenCode: 'SCR_PROVINCE' },
      CORP: { template: 'corporate-overview-v1', screenCode: 'SCR_CORP_OVERVIEW' },
      RETAIL: { template: 'retail-overview-v1', screenCode: 'SCR_RETAIL_OVERVIEW' }
    });
    expect(routeForBusinessLine('CORP', { cityCode: '610100', orgCode: 'ORG-1' })).toEqual({
      name: 'CodeScreenPage',
      params: { template: 'corporate-overview-v1' },
      query: { cityCode: '610100', orgCode: 'ORG-1', businessLine: 'CORP' }
    });
    expect(routeForBusinessLine('/evil', { orgCode: 'ORG-1' })).toBeNull();
    expect(routeForInstitution({ orgCode: 'ORG-1', cityCode: '610100', businessLine: 'COMMON' })).toEqual({
      name: 'BranchOperatingPage',
      query: { orgCode: 'ORG-1', cityCode: '610100', businessLine: 'COMMON' }
    });
  });

  it('round-trips only serializable navigation context through route query', () => {
    const query = buildNavigationQuery({
      cityCode: '610100', orgCode: 'ORG-1', businessLine: 'COMMON', period: 'LATEST',
      metricKey: 'deposit', view: { zoom: 2, center: [108.9, 34.2] }, state: { page: 2, detailExpanded: false }
    });
    expect(query).toMatchObject({ cityCode: '610100', orgCode: 'ORG-1', businessLine: 'COMMON', period: 'LATEST', metricKey: 'deposit' });
    expect(parseNavigationQuery(query)).toMatchObject({
      cityCode: '610100', orgCode: 'ORG-1', businessLine: 'COMMON', period: 'LATEST', metricKey: 'deposit',
      view: { zoom: 2, center: [108.9, 34.2] }, state: { page: 2, detailExpanded: false }
    });
    expect(parseNavigationQuery({ view: '{bad', state: '[]' })).toMatchObject({ view: null, state: null });
  });

  it('仅保留省级源屏导航白名单，非法 source 屏编码或 preview 不进入目标 query', () => {
    const query = buildNavigationQuery({
      orgCode: 'ORG-1', sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft'
    });
    expect(query).toMatchObject({ orgCode: 'ORG-1', sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft' });
    expect(buildNavigationQuery({
      orgCode: 'ORG-1', sourceScreenCode: 'SCR_CORP_OVERVIEW', sourcePreview: 'published'
    })).toEqual({ orgCode: 'ORG-1' });
    expect(parseNavigationQuery(query)).toMatchObject({
      sourceScreenCode: 'SCR_PROVINCE', sourcePreview: 'draft'
    });
    expect(buildNavigationQuery({ orgCode: 'ORG-1', sourceScreenCode: 'SCR_PROVINCE' }))
      .toEqual({ orgCode: 'ORG-1', sourceScreenCode: 'SCR_PROVINCE' });
    expect(parseNavigationQuery({ orgCode: 'ORG-1', sourceScreenCode: 'SCR_PROVINCE' }))
      .toMatchObject({ sourceScreenCode: 'SCR_PROVINCE', sourcePreview: '' });
  });

  it('classifies institutions only from explicit profile fields and keeps unknown layers pending', () => {
    const rules = { allowedOperatingLevels: ['PRIMARY_BRANCH'], allowedOrgNatures: ['BRANCH'], hiddenOperatingLevels: ['COMMUNITY_BRANCH'], hiddenOrgNatures: ['COMMUNITY'] };
    expect(classifyInstitutionLayer({ operatingLevel: 'PRIMARY_BRANCH', orgNature: 'BRANCH' }, rules)).toMatchObject({ known: true, displayable: true });
    expect(classifyInstitutionLayer({ operatingLevel: 'COMMUNITY_BRANCH', orgNature: 'COMMUNITY' }, rules)).toMatchObject({ known: true, displayable: false });
    expect(classifyInstitutionLayer({ operatingLevel: 'PRIMARY_BRANCH', orgNature: 'BRANCH' })).toMatchObject({ known: false, displayable: false, reason: 'LAYER_UNCONFIRMED' });
    expect(classifyInstitutionLayer({ orgName: '某某支行', orgCode: '001' }, rules)).toMatchObject({ known: false, displayable: false, reason: 'LAYER_UNCONFIRMED' });
  });

  it('启用名称排除规则时分类和机构解析都拒绝命中机构，缺名 fail-close', () => {
    const rules = {
      allowedOperatingLevels: ['PRIMARY_BRANCH'],
      allowedOrgNatures: ['BRANCH'],
      excludedOrgNameKeywords: ['小微支行', '社区支行']
    };
    expect(classifyInstitutionLayer({ orgName: '高新小微支行', operatingLevel: 'PRIMARY_BRANCH', orgNature: 'BRANCH' }, rules))
      .toMatchObject({ known: true, displayable: false, reason: 'ORG_NAME_EXCLUDED_KEYWORD' });
    expect(classifyInstitutionLayer({ operatingLevel: 'PRIMARY_BRANCH', orgNature: 'BRANCH' }, rules))
      .toMatchObject({ known: false, displayable: false, reason: 'ORG_NAME_MISSING' });

    const response = {
      institutionRules: rules,
      panoramaInstitutions: [
        { orgCode: 'MICRO', orgName: '高新小微支行', operatingLevel: 'PRIMARY_BRANCH', orgNature: 'BRANCH' },
        { orgCode: 'ORDINARY', orgName: '高新支行', operatingLevel: 'PRIMARY_BRANCH', orgNature: 'BRANCH' },
        { orgCode: 'MISSING', operatingLevel: 'PRIMARY_BRANCH', orgNature: 'BRANCH' }
      ]
    };
    expect(resolveInstitution(response, 'MICRO')).toMatchObject({
      authorized: true,
      layer: { known: true, displayable: false, reason: 'ORG_NAME_EXCLUDED_KEYWORD' }
    });
    expect(resolveInstitution(response, 'MISSING')).toMatchObject({
      authorized: true,
      reason: 'ORG_NAME_MISSING',
      layer: { known: false, displayable: false, reason: 'ORG_NAME_MISSING' }
    });
    expect(resolveInstitution(response, 'ORDINARY')).toMatchObject({
      authorized: true,
      layer: { known: true, displayable: true }
    });
  });

  it('名称排除字段存在但格式无效时导航 fail-close', () => {
    const response = {
      institutionRules: {
        allowedOperatingLevels: ['PRIMARY_BRANCH'],
        allowedOrgNatures: ['BRANCH'],
        excludedOrgNameKeywords: []
      },
      panoramaInstitutions: [
        { orgCode: 'ORDINARY', orgName: '高新支行', operatingLevel: 'PRIMARY_BRANCH', orgNature: 'BRANCH' }
      ]
    };

    expect(resolveInstitution(response, 'ORDINARY')).toMatchObject({
      authorized: true,
      layer: { known: false, displayable: false, reason: 'LAYER_UNCONFIRMED' }
    });
  });

  it('优先读取 ScreenRenderRespDTO 的 institutionRules，不被旧 navigationRules 覆盖', () => {
    const response = {
      screenCode: 'SCR_PROVINCE',
      institutionRules: { allowedOperatingLevels: ['PRIMARY'], allowedOrgNatures: ['SECONDARY_BRANCH'] },
      navigationRules: { allowedOperatingLevels: ['DENIED'], allowedOrgNatures: ['DENIED'] },
      panoramaInstitutions: [{ orgCode: 'ORG-1', operatingLevel: 'PRIMARY', orgNature: 'SECONDARY_BRANCH' }]
    };
    expect(resolveInstitution(response, 'ORG-1')).toMatchObject({
      authorized: true,
      layer: { known: true, displayable: true }
    });
  });

  it('启用 displayOrgCodes 时导航直达拒绝不在展示编码白名单的授权机构', () => {
    const response = {
      institutionRules: {
        allowedOperatingLevels: ['PRIMARY'],
        allowedOrgNatures: ['SECONDARY_BRANCH'],
        displayOrgCodes: ['ORG-1']
      },
      panoramaInstitutions: [
        { orgCode: 'ORG-1', operatingLevel: 'PRIMARY', orgNature: 'SECONDARY_BRANCH' },
        { orgCode: 'ORG-2', operatingLevel: 'PRIMARY', orgNature: 'SECONDARY_BRANCH' }
      ]
    };

    expect(resolveInstitution(response, 'ORG-1')).toMatchObject({
      authorized: true,
      layer: { known: true, displayable: true }
    });
    expect(resolveInstitution(response, 'ORG-2')).toMatchObject({
      authorized: false,
      reason: 'ORG_NOT_IN_DISPLAY_LIST'
    });
    expect(resolveInstitution(response, 'ORG-2', response.institutionRules)).toMatchObject({
      authorized: false,
      reason: 'ORG_NOT_IN_DISPLAY_LIST'
    });

    expect(navigationRulesOf({
      ...response,
      institutionRules: { ...response.institutionRules, displayOrgCodes: ['ORG-1', 'org-1'] }
    })).toBeNull();
  });

  it('显式传入 navigationRulesOf 生成的 Set 规则时仍保留机构层级与白名单判断', () => {
    const source = view('SCR_PROVINCE');
    const rules = navigationRulesOf(source);

    expect(resolveInstitution(source, 'ORG-1', rules)).toMatchObject({
      authorized: true,
      layer: { known: true, displayable: true }
    });
  });

  it('rechecks target catalog and view, then requires current org in target authorization directory', async () => {
    const listAvailableScreens = vi.fn().mockResolvedValue(entries);
    const getScreenView = vi.fn().mockImplementation(screenCode => view(screenCode));
    await expect(resolveAuthorizedScreen({ businessLine: 'CORP', orgCode: 'ORG-1', listAvailableScreens, getScreenView }))
      .resolves.toMatchObject({ entry: entries[1], view: { screenCode: 'SCR_CORP_OVERVIEW' } });
    expect(listAvailableScreens).toHaveBeenCalledTimes(1);
    expect(getScreenView).toHaveBeenCalledWith('SCR_CORP_OVERVIEW');

    await expect(resolveAuthorizedScreen({ businessLine: 'RETAIL', orgCode: 'OUTSIDE', listAvailableScreens, getScreenView }))
      .rejects.toMatchObject({ code: 'ORG_NOT_AUTHORIZED' });
    await expect(resolveAuthorizedScreen({ businessLine: 'UNKNOWN', orgCode: 'ORG-1', listAvailableScreens, getScreenView }))
      .rejects.toMatchObject({ code: 'BUSINESS_LINE_UNSUPPORTED' });
  });

  it('does not treat an authorized but unconfirmed institution as a valid branch target', () => {
    expect(resolveInstitution(view('SCR_PROVINCE'), 'ORG-2')).toMatchObject({
      orgCode: 'ORG-2', authorized: true, layer: { known: true, displayable: false }
    });
    expect(resolveInstitution(view('SCR_PROVINCE'), 'ORG-UNKNOWN')).toMatchObject({ authorized: false });
  });
});
