import { describe, expect, it } from 'vitest';
import { analyzeOrgProfiles } from '../orgProfileReadiness';

describe('orgProfileReadiness', () => {
  it('按画像状态、城市、地址和真实坐标完整统计缺口', () => {
    const rows = [
      { orgCode: 'U1', orgName: '未配置机构' },
      {
        orgCode: 'A1', orgName: '已启用机构', status: 'ACTIVE', version: 1,
        cityCode: '610100', cityName: '西安市', address: '雁塔区一号',
        lng: 108.9, lat: 34.2, coordSys: 'GCJ02'
      },
      {
        orgCode: 'D1', orgName: '停用机构', status: 1, orgNature: 'LOCAL_BRANCH',
        cityName: '宝鸡市', location: { address: '渭滨区二号' },
        lng: 108.9, lat: 34.2, coordSys: 'GCJ02'
      },
      {
        orgCode: 'X1', orgName: '未知状态机构', status: 'PAUSED', operatingLevel: 'PRIMARY',
        cityCode: '610400', lng: 108.9, lat: 34.2, coordSys: 'WGS84'
      },
      {
        orgCode: 'M1', orgName: '演示坐标机构', status: 'ACTIVE', version: 1,
        cityCode: '610100', lng: 108.9, lat: 34.2, coordSys: 'GCJ02',
        remark: 'SCREEN_MAP_DEMO_20260824'
      },
      {
        orgCode: 'P1', orgName: '待定位机构', status: '0', version: 1,
        lat: 34.2, coordSys: 'GCJ02'
      }
    ];

    const result = analyzeOrgProfiles(rows);

    expect(result).toMatchObject({
      total: 6,
      profileConfigured: 5,
      missingProfile: 1,
      enabled: 3,
      disabled: 1,
      unknownStatus: 1,
      located: 2,
      missingCoordinates: 4,
      missingCity: 3,
      missingAddress: 0
    });
    expect(result.entries).toEqual([
      expect.objectContaining({ orgCode: 'U1', profileStatus: 'UNCONFIGURED', hasCity: false, located: false, hasAddress: null }),
      expect.objectContaining({ orgCode: 'A1', profileStatus: 'ACTIVE', hasCity: true, located: true, hasAddress: true, issues: [] }),
      expect.objectContaining({ orgCode: 'D1', profileStatus: 'DISABLED', hasCity: false, located: true, hasAddress: true }),
      expect.objectContaining({ orgCode: 'X1', profileStatus: 'UNKNOWN', hasCity: true, located: false, hasAddress: null }),
      expect.objectContaining({ orgCode: 'M1', profileStatus: 'ACTIVE', hasCity: true, located: false, hasAddress: null, issues: expect.arrayContaining(['SCREEN_MAP_DEMO']) }),
      expect.objectContaining({ orgCode: 'P1', profileStatus: 'ACTIVE', hasCity: false, located: false, hasAddress: null })
    ]);
  });

  it('坐标必须成对、在合法范围内、使用 GCJ02，并且不修改输入行', () => {
    const rows = [
      { orgCode: 'PAIR', status: 'ACTIVE', lng: 108.9, coordSys: 'GCJ02' },
      { orgCode: 'RANGE', status: 'ACTIVE', lng: 181, lat: 34.2, coordSys: 'GCJ02' },
      { orgCode: 'SYS', status: 'ACTIVE', lng: 108.9, lat: 34.2, coordSys: 'WGS84' },
      { orgCode: 'VALID', status: 'ACTIVE', lng: '108.9', lat: '34.2', coordSys: 'GCJ-02' }
    ];
    const before = JSON.parse(JSON.stringify(rows));

    const result = analyzeOrgProfiles(rows);

    expect(result.located).toBe(1);
    expect(result.missingCoordinates).toBe(3);
    expect(result.entries.map(entry => entry.located)).toEqual([false, false, false, true]);
    expect(rows).toEqual(before);
  });

  it('只有 status 缺失但存在画像字段时不误判为未配置', () => {
    const result = analyzeOrgProfiles([
      { orgCode: 'N1', status: null, orgNature: 'OTHER' },
      { orgCode: 'N2', status: '', version: 1 },
      { orgCode: 'N3', status: null }
    ]);

    expect(result.entries.map(entry => entry.profileStatus)).toEqual(['UNKNOWN', 'UNKNOWN', 'UNCONFIGURED']);
    expect(result.profileConfigured).toBe(2);
    expect(result.missingProfile).toBe(1);
    expect(result.unknownStatus).toBe(2);
  });

  it('城市归属必须有至少六位有效 cityCode，不能由 cityName 推断', () => {
    const result = analyzeOrgProfiles([
      { orgCode: 'CODE', cityCode: '610100' },
      { orgCode: 'LONG', cityCode: '61010001' },
      { orgCode: 'NAME', cityName: '西安市' },
      { orgCode: 'SHORT', cityCode: '6101', cityName: '西安市' },
      { orgCode: 'EMPTY', cityCode: '  ' }
    ]);

    expect(result.entries.map(entry => entry.hasCity)).toEqual([true, true, false, false, false]);
    expect(result.missingCity).toBe(3);
  });

  it('SCREEN_MAP_DEMO 仅在来源可信且坐标有效时仍算已定位', () => {
    const result = analyzeOrgProfiles([
      { orgCode: 'MANUAL', status: 'ACTIVE', remark: 'SCREEN_MAP_DEMO', locationSource: 'MANUAL', lng: 108.9, lat: 34.2, coordSys: 'GCJ02' },
      { orgCode: 'GEOCODE', status: 'ACTIVE', remark: 'SCREEN_MAP_DEMO', locationSource: 'GEOCODE_VERIFIED', lng: 108.9, lat: 34.2, coordSys: 'GCJ02' },
      { orgCode: 'PROFILE', status: 'ACTIVE', remark: 'SCREEN_MAP_DEMO', locationSource: 'PROFILE', lng: 108.9, lat: 34.2, coordSys: 'GCJ02' },
      { orgCode: 'UNKNOWN', status: 'ACTIVE', remark: 'SCREEN_MAP_DEMO', lng: 108.9, lat: 34.2, coordSys: 'GCJ02' }
    ]);

    expect(result.entries.map(entry => entry.located)).toEqual([true, true, false, false]);
    expect(result.entries[0].issues).not.toContain('SCREEN_MAP_DEMO');
    expect(result.entries[1].issues).not.toContain('SCREEN_MAP_DEMO');
    expect(result.entries[2].issues).toContain('SCREEN_MAP_DEMO');
    expect(result.entries[3].issues).toContain('SCREEN_MAP_DEMO');
  });

  it('机构画像列表未提供地址字段时保持地址未知，不制造地址缺失缺口', () => {
    const result = analyzeOrgProfiles([{ orgCode: 'UNKNOWN_ADDRESS', status: 'ACTIVE' }]);

    expect(result.entries[0].hasAddress).toBe(null);
    expect(result.entries[0].issues).not.toContain('MISSING_ADDRESS');
    expect(result.missingAddress).toBe(0);
  });
});
