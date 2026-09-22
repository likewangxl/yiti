import { describe, expect, it, vi } from 'vitest';
import {
  BUSINESS_LINE_TARGETS,
  buildNavigationQuery,
  classifyInstitutionLayer,
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

  it('classifies institutions only from explicit profile fields and keeps unknown layers pending', () => {
    const rules = { allowedOperatingLevels: ['PRIMARY_BRANCH'], allowedOrgNatures: ['BRANCH'], hiddenOperatingLevels: ['COMMUNITY_BRANCH'], hiddenOrgNatures: ['COMMUNITY'] };
    expect(classifyInstitutionLayer({ operatingLevel: 'PRIMARY_BRANCH', orgNature: 'BRANCH' }, rules)).toMatchObject({ known: true, displayable: true });
    expect(classifyInstitutionLayer({ operatingLevel: 'COMMUNITY_BRANCH', orgNature: 'COMMUNITY' }, rules)).toMatchObject({ known: true, displayable: false });
    expect(classifyInstitutionLayer({ operatingLevel: 'PRIMARY_BRANCH', orgNature: 'BRANCH' })).toMatchObject({ known: false, displayable: false, reason: 'LAYER_UNCONFIRMED' });
    expect(classifyInstitutionLayer({ orgName: '某某支行', orgCode: '001' }, rules)).toMatchObject({ known: false, displayable: false, reason: 'LAYER_UNCONFIRMED' });
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
