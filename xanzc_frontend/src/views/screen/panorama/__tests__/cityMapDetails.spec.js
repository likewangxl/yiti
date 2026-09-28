import { describe, expect, it } from 'vitest';
import { buildCityInstitutionDetails, buildCityMapDetails } from '../cityMapDetails';

describe('城市地图详情适配', () => {
  it('机构模式按 cityCode 去重授权机构，包含未定位机构和零机构城市，不携带指标日期', () => {
    const details = buildCityInstitutionDetails({
      dataDate: '2026-09-06',
      citySummaries: { '610100': { kpis: [{ key: 'deposit', value: 999, unit: '亿元' }] } },
      rankings: [{ orgCode: 'A', cityCode: '610100', deposit: 999 }],
      institutions: [
        { orgCode: 'A', orgName: '西安一支行', cityCode: '610100', located: true, lng: 108.9, lat: 34.2, dataDate: '2026-09-01', metrics: { deposit: 999 } },
        { orgCode: 'A', orgName: '重复名称不应出现', cityCode: '610100', located: true, lng: 108.9, lat: 34.2 },
        { orgCode: 'B', orgName: '西安二支行', cityCode: '610100', located: false },
        { orgName: '无编码机构', cityCode: '610100', located: false },
        { orgCode: 'NO-CITY', orgName: '无归属机构', located: false }
      ]
    }, { cityCodes: ['610100', '610200'] });

    expect(details).toEqual({
      '610100': {
        institutionCount: 2,
        institutions: [
          { orgCode: 'A', orgName: '西安一支行' },
          { orgCode: 'B', orgName: '西安二支行' }
        ]
      },
      '610200': { institutionCount: 0, institutions: [] }
    });
  });

  it('按城市汇总构建指标、日期和授权机构目录，保留来源单位', () => {
    const details = buildCityMapDetails({
      dataDate: '2026-09-06',
      institutions: [
        { orgCode: 'ORG-1', orgName: '西安一支行', cityCode: '610100', located: true, lng: 108.9, lat: 34.2 },
        { orgCode: 'ORG-2', orgName: '西安二支行', cityCode: '610100', located: false, lng: null, lat: null },
        { orgCode: 'ORG-3', orgName: '榆林支行', cityCode: '610800', located: true, lng: 109.7, lat: 38.2 }
      ],
      citySummaries: {
        '610100': {
          dataDate: '2026-08-31',
          kpis: [
            { key: 'deposit', label: '存款余额', value: 125, unit: '万元' },
            { key: 'loan', label: '贷款余额', value: 0, unit: '亿元' },
            { key: 'customers', label: '营销有效归属客户数', value: 0, unit: '万户' },
            { key: 'rate', label: '目标完成率', value: null, unit: '%' }
          ]
        }
      }
    });

    expect(details['610100']).toMatchObject({
      institutionCount: 2,
      locatedCount: 1,
      dataDate: '2026-08-31',
      institutions: [
        { orgCode: 'ORG-1', orgName: '西安一支行' },
        { orgCode: 'ORG-2', orgName: '西安二支行' }
      ]
    });
    expect(details['610100'].metrics).toEqual([
      { key: 'deposit', label: '存款余额', value: '125万元' },
      { key: 'loan', label: '贷款余额', value: '0亿元' },
      { key: 'depositIncrease', label: '存款较上月净增', value: '暂无数据' },
      { key: 'depositAverage', label: '存款月均余额', value: '暂无数据' },
      { key: 'customers', label: '营销有效归属客户数', value: '0万户' },
      { key: 'rate', label: '目标完成率', value: '暂无数据' },
      { key: 'revenue', label: '手工测试收入', value: '暂无数据' }
    ]);
  });

  it('城市汇总缺失时不从多家机构擅自合计，仍返回授权范围内的机构计数', () => {
    const details = buildCityMapDetails({
      dataDate: '2026-09-06',
      institutions: [
        { orgCode: 'A', orgName: '甲支行', cityCode: '610300', located: true, lng: 107, lat: 34 },
        { orgCode: 'B', orgName: '乙支行', cityCode: '610300', located: true, lng: 107.1, lat: 34.1 }
      ],
      citySummaries: {}
    });

    expect(details['610300'].institutionCount).toBe(2);
    expect(details['610300'].locatedCount).toBe(2);
    expect(details['610300'].metrics.every(metric => metric.value === '暂无数据')).toBe(true);
    expect(details['610300'].metrics.find(metric => metric.key === 'deposit').value).not.toContain('30');
  });

  it('单家机构可作为地图已有口径的明确范围回退，多家机构不回退', () => {
    const details = buildCityMapDetails({
      rankings: [{ orgCode: 'ONLY', cityCode: '610800', deposit: 80 }],
      institutions: [{ orgCode: 'ONLY', orgName: '榆林支行', cityCode: '610800' }],
      citySummaries: {}
    });

    expect(details['610800'].metrics.find(metric => metric.key === 'deposit').value)
      .toBe('80亿元');
    expect(details['610800'].scopeLabel).toBe('当前范围单家机构');
  });

  it('没有授权机构的城市以 0 计数，不推测全城市机构总数并回退到模型日期', () => {
    const details = buildCityMapDetails({
      dataDate: '2026-09-06',
      institutions: [],
      citySummaries: {
        '610500': {
          kpis: [{ key: 'deposit', value: 12.5, unit: '亿元' }]
        }
      }
    });

    expect(details['610500']).toMatchObject({ institutionCount: 0, locatedCount: 0, dataDate: '2026-09-06', institutions: [] });
    expect(details['610500'].metrics.find(metric => metric.key === 'deposit')).toEqual({ key: 'deposit', label: '存款余额', value: '12.50亿元' });
  });

  it('不会把金额目标字段误识别为目标完成率', () => {
    const details = buildCityMapDetails({
      citySummaries: {
        '610100': { kpis: [{ key: 'target', label: '存款目标', value: 88, unit: '亿元' }] }
      }
    });

    expect(details['610100'].metrics.find(metric => metric.key === 'rate').value).toBe('暂无数据');
  });
});
