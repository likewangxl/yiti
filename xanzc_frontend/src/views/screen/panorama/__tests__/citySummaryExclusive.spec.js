import { describe, expect, it } from 'vitest';
import { adaptPanoramaResults } from '../dataAdapter';

const binding = (fields, units) => ({
  slot: 'citySummary', dsId: 1, period: 'LATEST', fields, units
});

const directory = [
  { orgCode: 'A', orgName: '机构A', cityCode: '610100', cityName: '西安市' },
  { orgCode: 'B', orgName: '机构B', cityCode: '610100', cityName: '西安市' },
  { orgCode: 'C', orgName: '机构C', cityCode: '610200', cityName: '铜川市' }
];

const response = (rows, subjectValueMode = 'EXCLUSIVE') => ({
  quality: { subjectValueMode },
  columns: ['org_code', 'deposit', 'customers', 'rate'],
  columnsMeta: [
    { col: 'deposit', role: 'METRIC', unit: 'YUAN' },
    { col: 'customers', role: 'METRIC', unit: 'COUNT' },
    { col: 'rate', role: 'METRIC', unit: 'PERCENT' }
  ],
  rows
});

describe('citySummary EXCLUSIVE subject values', () => {
  it('按授权目录归一化合计同城金额和 COUNT，过滤范围外机构，rate 留空并说明来源', () => {
    const model = adaptPanoramaResults({
      citySummary: {
        binding: binding(
          { orgCode: 'org_code', deposit: 'deposit', customers: 'customers', rate: 'rate' },
          { deposit: 'YUAN', customers: 'COUNT', rate: 'PERCENT' }
        ),
        response: response([
          ['A', 100000000, 10, 40],
          ['B', 200000000, 20, 60],
          ['OUTSIDE', 900000000, 999, 99]
        ])
      }
    }, { panoramaInstitutions: directory });

    expect(model.citySummaries['610100'].kpis).toEqual([
      { key: 'deposit', label: '存款余额', value: 3, unit: '亿元', change: null },
      { key: 'customers', label: '营销有效归属客户数', value: 0.003, unit: '万户', change: null },
      { key: 'rate', label: '目标完成率', value: null, unit: '%', change: null }
    ]);
    expect(model.citySummaries.OUTSIDE).toBeUndefined();
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({
        slot: 'citySummary', code: 'RATE_CITY_SOURCE_REQUIRED', field: 'rate',
        message: expect.stringContaining('独立城市比例来源')
      })
    ]));
    expect(model.issues.some(item => item.code === 'UNAUTHORIZED_ORG')).toBe(true);
  });

  it('同城任一指标缺值时该指标留空并报告机构覆盖不足', () => {
    const model = adaptPanoramaResults({
      citySummary: {
        binding: binding(
          { orgCode: 'org_code', deposit: 'deposit', customers: 'customers' },
          { deposit: 'YUAN', customers: 'COUNT' }
        ),
        response: response([
          ['A', 100000000, 10, null],
          ['B', 200000000, null, null]
        ])
      }
    }, { panoramaInstitutions: directory });

    const kpis = model.citySummaries['610100'].kpis;
    expect(kpis.find(item => item.key === 'deposit')).toMatchObject({ value: 3 });
    expect(kpis.find(item => item.key === 'customers')).toMatchObject({ value: null });
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({
        slot: 'citySummary', code: 'INSUFFICIENT_COVERAGE', field: 'customers',
        message: expect.stringContaining('覆盖不足')
      })
    ]));
  });

  it('同一机构重复时拒绝该城市，旧包和普通 cityCode 重复继续 fail-close', () => {
    const exclusive = adaptPanoramaResults({
      citySummary: {
        binding: binding({ orgCode: 'org_code', deposit: 'deposit' }, { deposit: 'YUAN' }),
        response: response([['A', 100000000, null, null], ['A', 200000000, null, null]])
      }
    }, { panoramaInstitutions: directory });
    expect(exclusive.citySummaries['610100']).toBeUndefined();
    expect(exclusive.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'citySummary', code: 'DUPLICATE_ORG_CODE', field: 'orgCode' })
    ]));

    const oldPackage = adaptPanoramaResults({
      citySummary: {
        binding: binding({ orgCode: 'org_code', deposit: 'deposit' }, { deposit: 'YUAN' }),
        response: response([['A', 100000000, null, null]], null)
      }
    }, { panoramaInstitutions: directory });
    expect(oldPackage.citySummaries['610100']).toMatchObject({ kpis: [expect.objectContaining({ value: 1 })] });

    const duplicateCity = adaptPanoramaResults({
      citySummary: {
        binding: binding({ cityCode: 'city_code', deposit: 'deposit' }, { deposit: 'YUAN' }),
        response: {
          columns: ['city_code', 'deposit'],
          rows: [['610100', 100000000], ['610100', 200000000]]
        }
      }
    }, { panoramaInstitutions: directory });
    expect(duplicateCity.citySummaries['610100']).toBeUndefined();
    expect(duplicateCity.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'citySummary', code: 'DUPLICATE_CITY' })
    ]));
  });
});

it('EXCLUSIVE 缺少同城整家机构时不得显示部分合计', () => {
  const model=adaptPanoramaResults({citySummary:{binding:binding({orgCode:'org_code',deposit:'deposit'},{deposit:'YUAN'}),response:response([['A',100000000,10,40]])}},{panoramaInstitutions:directory});
  expect(model.citySummaries['610100'].kpis.find(k=>k.key==='deposit').value).toBeNull();
  expect(model.issues.some(issue=>issue.code==='INSUFFICIENT_COVERAGE')).toBe(true);
});
