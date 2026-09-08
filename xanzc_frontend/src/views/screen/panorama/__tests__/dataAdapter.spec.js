import { describe, expect, it } from 'vitest';
import { adaptPanoramaResults, createEmptyPanoramaModel } from '../dataAdapter';

const binding = (slot, fields, units = {}) => ({
  slot, dsId: 1, period: 'LATEST', fields, units
});

describe('panorama data adapter', () => {
  it('解析 columns/rows 二维数据，保留 0 与 null，不从日期或列名猜语义', () => {
    const model = adaptPanoramaResults({
      deposit: {
        binding: binding('deposit', { value: 'deposit_raw' }, { value: 'YUAN' }),
        response: {
          dataDate: '2026-09-01',
          columns: ['deposit_raw'],
          rows: [[0]]
        }
      },
      loan: {
        binding: binding('loan', { value: 'loan_raw' }, { value: 'YUAN' }),
        response: { columns: ['loan_raw'], rows: [[null]] }
      }
    });
    expect(model.kpis.find(item => item.key === 'deposit')).toMatchObject({ value: 0, unit: '亿元' });
    expect(model.kpis.find(item => item.key === 'loan')).toMatchObject({ value: null, unit: '亿元' });
    expect(model.dataDate).toBe('2026-09-01');
    expect(model.issues).toEqual([]);
  });

  it('columnsMeta amountScale 仍代表原始元值的展示预设，raw 100000000 只换算为 1 亿元一次', () => {
    const model = adaptPanoramaResults({
      deposit: {
        binding: binding('deposit', { value: 'amount' }),
        response: {
          columns: ['amount'], rows: [[100000000]],
          columnsMeta: [{ col: 'amount', role: 'METRIC', amountScale: 'HUNDRED_MILLION_YUAN' }]
        }
      }
    });
    expect(model.kpis[0]).toMatchObject({ value: 1, unit: '亿元' });
  });

  it('未知单位保留 null 并记录 slot issue，不按名称猜测', () => {
    const model = adaptPanoramaResults({
      deposit: {
        binding: binding('deposit', { value: 'deposit_balance' }),
        response: { columns: ['deposit_balance'], rows: [[100]] }
      }
    });
    expect(model.kpis[0].value).toBeNull();
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'deposit', code: 'UNKNOWN_UNIT' })
    ]));
  });

  it('新增存款金额 KPI 将元换算为亿元并保留 0/null，错误单位不猜测', () => {
    const model = adaptPanoramaResults({
      depositIncrease: {
        binding: binding('depositIncrease', { value: 'increase' }, { value: 'YUAN' }),
        response: { columns: ['increase'], rows: [[0]] }
      },
      depositAverage: {
        binding: binding('depositAverage', { value: 'average' }, { value: 'YUAN' }),
        response: { columns: ['average'], rows: [[null]] }
      },
      loan: {
        binding: binding('loan', { value: 'loan' }, { value: 'COUNT' }),
        response: { columns: ['loan'], rows: [[100]] }
      }
    });
    expect(model.kpis.find(item => item.key === 'depositIncrease')).toMatchObject({ value: 0, unit: '亿元' });
    expect(model.kpis.find(item => item.key === 'depositAverage')).toMatchObject({ value: null, unit: '亿元' });
    expect(model.kpis.find(item => item.key === 'loan')).toMatchObject({ value: null, unit: null });
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'loan', code: 'UNIT_MISMATCH', field: 'value' })
    ]));
  });

  it('金额槽位和排名 value 误用比例单位时保留 null并记录单位不匹配', () => {
    const model = adaptPanoramaResults({
      depositIncrease: {
        binding: binding('depositIncrease', { value: 'increase' }, { value: 'PERCENT' }),
        response: { columns: ['increase'], rows: [[12]] }
      },
      ranking: {
        binding: binding('ranking', { orgCode: 'org', name: 'name', value: 'deposit' }, { value: 'PERCENT' }),
        response: { columns: ['org', 'name', 'deposit'], rows: [['B-1', '支行一', 12]] }
      }
    });
    expect(model.kpis.find(item => item.key === 'depositIncrease')).toMatchObject({ value: null, unit: null });
    expect(model.rankings[0]).toMatchObject({ deposit: null, increase: null, average: null });
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'depositIncrease', code: 'UNIT_MISMATCH', field: 'value' }),
      expect.objectContaining({ slot: 'ranking', code: 'UNIT_MISMATCH', field: 'value' })
    ]));
  });

  it('branches 以授权机构目录为集合，目录无数据仍保留，目录外读数不进入城市', () => {
    const model = adaptPanoramaResults({
      branches: {
        binding: binding('branches', {
          orgCode: 'org_code', orgName: 'org_name', cityCode: 'city_code',
          deposit: 'deposit', customers: 'customers'
        }, { deposit: 'YUAN', customers: 'COUNT' }),
        response: {
          columns: ['org_code', 'org_name', 'city_code', 'deposit', 'customers'],
          rows: [['AUTH-1', '授权机构1', '610100', 100000000, 0], ['OUT-1', '外部机构', '610100', 999, 2]]
        }
      }
    }, {
      panoramaInstitutions: [
        { orgCode: 'AUTH-1', orgName: '授权机构1', cityCode: '610100', located: true, lng: 108.9, lat: 34.2, coordSys: 'GCJ02' },
        { orgCode: 'AUTH-2', orgName: '无数据授权机构', cityCode: '610100', located: false }
      ]
    });
    expect(model.institutions.map(item => item.orgCode)).toEqual(['AUTH-1', 'AUTH-2']);
    expect(model.institutions[0].metrics).toMatchObject({ deposit: 1, customers: 0 });
    expect(model.institutions[1].metrics).toMatchObject({ deposit: null, customers: null });
    expect(model.institutions.some(item => item.orgCode === 'OUT-1')).toBe(false);
    expect(model.institutions[1]).toMatchObject({ lng: null, lat: null, located: false });
  });

  it('趋势不生成日期，城市汇总只使用明确 cityCode 结果', () => {
    const model = adaptPanoramaResults({
      trend: {
        binding: binding('trend', { date: 'period', deposit: 'deposit' }, { deposit: 'YUAN' }),
        response: { columns: ['period', 'deposit'], rows: [[null, 100000000], ['2026-08', 0]] }
      },
      citySummary: {
        binding: binding('citySummary', { cityCode: 'city_code', deposit: 'deposit' }, { deposit: 'YUAN' }),
        response: { dataDate: '2026-09-01', columns: ['city_code', 'deposit'], rows: [['610100', 100000000]] }
      }
    });
    expect(model.trend).toEqual([
      { date: null, deposit: 1, loan: null },
      { date: '2026-08', deposit: 0, loan: null }
    ]);
    expect(model.citySummaries['610100'].kpis[0]).toMatchObject({ value: 1 });
    expect(model.citySummaries['610100'].dataDate).toBe('2026-09-01');
  });

  it('趋势仅绑定 date 与 depositIncrease 时有效，保留净增字段且不扩展 branchTrend', () => {
    const model = adaptPanoramaResults({
      trend: {
        binding: binding('trend', { date: 'period', depositIncrease: 'increase' }, { depositIncrease: 'YUAN' }),
        response: { columns: ['period', 'increase'], rows: [['2026-08', 100000000], ['2026-09', -20000000]] }
      },
      branchTrend: {
        binding: binding('branchTrend', { date: 'period', deposit: 'deposit' }, { deposit: 'YUAN' }),
        response: { columns: ['period', 'deposit'], rows: [['2026-09', 100000000]] }
      }
    });
    expect(model.trend).toEqual([
      { date: '2026-08', deposit: null, loan: null, depositIncrease: 1 },
      { date: '2026-09', deposit: null, loan: null, depositIncrease: -0.2 }
    ]);
    expect(model.institutions).toEqual([]);
  });

  it('排名缺少可选列时保留 null，且不会把 value 复制到 increase/average', () => {
    const model = adaptPanoramaResults({
      ranking: {
        binding: binding('ranking', {
          orgCode: 'org', name: 'name', value: 'deposit', increase: 'increase', average: 'average'
        }, { value: 'YUAN', increase: 'YUAN', average: 'YUAN' }),
        response: { columns: ['org', 'name', 'deposit'], rows: [['B-1', '支行一', 100000000]] }
      }
    });
    expect(model.rankings).toEqual([{
      orgCode: 'B-1', name: '支行一', deposit: 1, increase: null, average: null, change: null
    }]);
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'ranking', code: 'MISSING_COLUMN', field: 'increase' }),
      expect.objectContaining({ slot: 'ranking', code: 'MISSING_COLUMN', field: 'average' })
    ]));
  });

  it('attention.count 保留原始个数，branchTrend 不污染全辖 trend', () => {
    const model = adaptPanoramaResults({
      attention: {
        binding: binding('attention', { label: 'label', count: 'count' }, { count: 'COUNT' }),
        response: { columns: ['label', 'count'], rows: [['超时', 4]] }
      },
      branchTrend: {
        binding: binding('branchTrend', { date: 'date', deposit: 'deposit' }, { deposit: 'YUAN' }),
        response: { columns: ['date', 'deposit'], rows: [['2026-09', 100000000]] }
      }
    });
    expect(model.attention).toEqual([{ label: '超时', count: 4 }]);
    expect(model.trend).toEqual([]);
  });

  it('命名机构组无目录时不回退 rows，目录存在时绑定失败也保留机构空行', () => {
    const noDirectory = adaptPanoramaResults({
      branches: {
        binding: binding('branches', { orgCode: 'org_code', deposit: 'deposit' }, { deposit: 'YUAN' }),
        response: { columns: ['org_code', 'deposit'], rows: [['OUTSIDE', 100000000]] }
      }
    }, { view: { orgScopeMode: 'NAMED_GROUP', panoramaInstitutions: [] } });
    expect(noDirectory.institutions).toEqual([]);

    const directoryOnly = adaptPanoramaResults({
      branches: { binding: binding('branches', { orgCode: 'org_code' }, {}), response: null }
    }, { view: { orgScopeMode: 'NAMED_GROUP', panoramaInstitutions: [{ orgCode: 'AUTH', orgName: '授权', cityCode: '610100' }] } });
    expect(directoryOnly.institutions).toHaveLength(1);
    expect(directoryOnly.institutions[0].metrics.deposit).toBeNull();
  });

  it('空响应不是 mock，适配器清空模型并暴露明确问题', () => {
    const model = adaptPanoramaResults({
      deposit: { binding: binding('deposit', { value: 'amount' }, { value: 'YUAN' }), response: null }
    });
    const empty = createEmptyPanoramaModel();
    expect(model).toMatchObject({ ...empty, issues: expect.any(Array) });
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'deposit', code: 'NULL_RESPONSE' })
    ]));
  });

  it('citySummary 可由授权机构号解析城市，未授权机构不进入汇总', () => {
    const model = adaptPanoramaResults({
      citySummary: {
        binding: binding('citySummary', { orgCode: 'org_code', revenue: 'revenue' }, { revenue: 'YUAN' }),
        response: {
          columns: ['org_code', 'revenue'],
          rows: [['AUTH-1', 100000000], ['OUTSIDE', 999000000]]
        }
      }
    }, {
      panoramaInstitutions: [{ orgCode: 'AUTH-1', cityCode: '610100', cityName: '西安市' }]
    });
    expect(model.citySummaries['610100']).toMatchObject({ cityName: '西安市' });
    expect(model.citySummaries['610100'].kpis[0]).toMatchObject({ key: 'revenue', value: 1 });
    expect(model.citySummaries['OUTSIDE']).toBeUndefined();
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'citySummary', code: 'UNAUTHORIZED_ORG' })
    ]));
  });

  it('citySummary 重复城市会移除该城市汇总，不覆盖或累加最后一条', () => {
    const model = adaptPanoramaResults({
      citySummary: {
        binding: binding('citySummary', { cityCode: 'city_code', deposit: 'deposit' }, { deposit: 'YUAN' }),
        response: {
          columns: ['city_code', 'deposit'],
          rows: [['610100', 100000000], ['610100', 200000000]]
        }
      }
    }, {
      panoramaInstitutions: [{ orgCode: 'AUTH-1', cityCode: '610100', cityName: '西安市' }]
    });
    expect(model.citySummaries['610100']).toBeUndefined();
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'citySummary', code: 'DUPLICATE_CITY' })
    ]));
  });
});
