import { describe, expect, it } from 'vitest';
import { adaptCorporateResults, createEmptyCorporateModel } from '../corporateDataAdapter.js';

const binding = (slot, fields, units = {}) => ({
  dsId: 1,
  period: slot === 'corpTrend' ? 'LAST_6M_EOM' : 'LATEST',
  fields,
  units
});

const table = (columns, rows, extra = {}) => ({ columns, rows, ...extra });

describe('corporateDataAdapter 对公数据适配', () => {
  it('创建独立空模型，不从零售或旧分行指标拼装对公数据', () => {
    expect(createEmptyCorporateModel()).toEqual({
      title: '对公经营总览', dataDate: '', kpis: [], trend: [], segments: [], rankings: [],
      attention: [], targets: [], institutions: [], issues: []
    });
    const model = adaptCorporateResults({
      retailAum: { binding: binding('retailAum', { value: 'value' }, { value: 'YUAN' }), response: table(['value'], [[1]]) }
    });
    expect(model.kpis).toEqual([]);
    expect(model.issues).toEqual([]);
  });

  it('把金额转为亿元、客户转为万户、比率转为百分数，保留零值和负数', () => {
    const model = adaptCorporateResults({
      corpDeposit: {
        binding: binding('corpDeposit', { value: 'deposit', change: 'change', date: 'date' }, { value: 'YUAN', change: 'RATIO' }),
        response: table(['deposit', 'change', 'date'], [[0, -0.03, '2026-09-08']])
      },
      corpCustomers: {
        binding: binding('corpCustomers', { value: 'customers' }, { value: 'COUNT' }),
        response: table(['customers'], [[23000]])
      },
      corpNplRate: {
        binding: binding('corpNplRate', { value: 'npl' }, { value: 'RATIO' }),
        response: table(['npl'], [[0.012]])
      },
      corpAttention: {
        binding: binding('corpAttention', { label: 'label', count: 'count' }, { count: 'COUNT' }),
        response: table(['label', 'count'], [['逾期客户', 0]])
      },
      corpSegments: {
        binding: binding('corpSegments', { name: 'name', customers: 'customers', loan: 'loan' }, { customers: 'TEN_THOUSAND_COUNT', loan: 'HUNDRED_MILLION' }),
        response: table(['name', 'customers', 'loan'], [['制造业', -1.5, -20]])
      }
    });
    expect(model.kpis).toEqual(expect.arrayContaining([
      { key: 'corpDeposit', label: '对公存款余额', value: 0, unit: '亿元', change: -3, date: '2026-09-08' },
      { key: 'corpCustomers', label: '有效对公客户', value: 2.3, unit: '万户', change: null },
      { key: 'corpNplRate', label: '对公不良率', value: 1.2, unit: '%', change: null }
    ]));
    expect(model.attention).toEqual([{ label: '逾期客户', count: 0, owner: null, deadline: null }]);
    expect(model.segments).toEqual([{ name: '制造业', customers: -1.5, loan: -20 }]);
    expect(model.dataDate).toBe('2026-09-08');
  });

  it('独立映射趋势、重点客群、机构排名和目标，并按显式单位换算', () => {
    const model = adaptCorporateResults({
      corpTrend: {
        binding: binding('corpTrend', { date: 'month', deposit: 'deposit', loan: 'loan' }, { deposit: 'TEN_THOUSAND', loan: 'HUNDRED_MILLION' }),
        response: table(['month', 'deposit', 'loan'], [['2026-08', 10000, 2]])
      },
      corpRanking: {
        binding: binding('corpRanking', { orgCode: 'code', name: 'label', deposit: 'amount', increase: 'inc', rate: 'rate', nplRate: 'npl' }, { deposit: 'YUAN', increase: 'TEN_THOUSAND', rate: 'PERCENT', nplRate: 'RATIO' }),
        response: table(['code', 'label', 'amount', 'inc', 'rate', 'npl'], [['A', '甲', 100000000, -10000, 3, 0.02]])
      },
      corpTargets: {
        binding: binding('corpTargets', { name: 'targetName', actual: 'actualRaw', target: 'targetRaw' }, { actual: 'TEN_THOUSAND', target: 'HUNDRED_MILLION' }),
        response: table(['targetName', 'actualRaw', 'targetRaw'], [['对公存款', 20000, 3]])
      }
    });
    expect(model.trend).toEqual([{ date: '2026-08', deposit: 1, loan: 2 }]);
    expect(model.rankings).toEqual([{ orgCode: 'A', name: '甲', deposit: 1, increase: -1, rate: 3, nplRate: 2, cityCode: null }]);
    expect(model.targets).toEqual([{ name: '对公存款', actual: 2, target: 3 }]);
  });

  it('缺失单位、非有限值、重复日期和重复机构均 fail closed', () => {
    const model = adaptCorporateResults({
      corpDeposit: {
        binding: binding('corpDeposit', { value: 'deposit' }),
        response: table(['deposit'], [['not-a-number']])
      },
      corpLoan: {
        binding: binding('corpLoan', { value: 'loan' }, { value: 'YUAN' }),
        response: table(['loan'], [[Infinity]])
      },
      corpTrend: {
        binding: binding('corpTrend', { date: 'date', deposit: 'deposit' }, { deposit: 'YUAN' }),
        response: table(['date', 'deposit'], [['D1', 100000000], ['D1', 200000000], ['D2', 300000000]])
      },
      corpRanking: {
        binding: binding('corpRanking', { orgCode: 'code', name: 'name', deposit: 'deposit' }, { deposit: 'YUAN' }),
        response: table(['code', 'name', 'deposit'], [['A', '甲', 100000000], ['A', '甲2', 200000000], ['B', '乙', 300000000]])
      }
    });
    expect(model.kpis).toEqual(expect.arrayContaining([
      { key: 'corpDeposit', label: '对公存款余额', value: null, unit: null, change: null },
      { key: 'corpLoan', label: '对公贷款余额', value: null, unit: '亿元', change: null }
    ]));
    expect(model.trend).toEqual([{ date: 'D2', deposit: 3, loan: null }]);
    expect(model.rankings).toEqual([{ orgCode: 'B', name: '乙', deposit: 3, increase: null, rate: null, nplRate: null, cityCode: null }]);
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'corpDeposit', code: 'UNKNOWN_UNIT' }),
      expect.objectContaining({ slot: 'corpLoan', code: 'INVALID_NUMBER' }),
      expect.objectContaining({ slot: 'corpTrend', code: 'DUPLICATE_DATE' }),
      expect.objectContaining({ slot: 'corpRanking', code: 'DUPLICATE_ORG' })
    ]));
  });

  it('命名机构组仅保留授权目录身份，branches 不填充业务指标', () => {
    const model = adaptCorporateResults({
      corpRanking: {
        binding: binding('corpRanking', { orgCode: 'code', name: 'name', deposit: 'deposit' }, { deposit: 'YUAN' }),
        response: table(['code', 'name', 'deposit'], [['AUTH', '返回名称', 100000000], ['OUT', '外部名称', 200000000]])
      },
      branches: {
        binding: binding('branches', { orgCode: 'code', orgName: 'name', cityCode: 'city', lng: 'lng', lat: 'lat', coordSys: 'coord', located: 'located', deposit: 'deposit' }, { deposit: 'YUAN' }),
        response: table(['code', 'name', 'city', 'lng', 'lat', 'coord', 'located', 'deposit'], [['AUTH', '源名称', '610100', 108.9, 34.2, 'GCJ02', true, 100000000]])
      }
    }, {
      view: {
        orgScopeMode: 'NAMED_GROUP',
        panoramaInstitutions: [{ orgCode: 'AUTH', orgName: '目录名称', cityCode: '610100', lng: 108.9, lat: 34.2, coordSys: 'GCJ02', located: true }]
      }
    });
    expect(model.rankings).toEqual([{ orgCode: 'AUTH', name: '返回名称', deposit: 1, increase: null, rate: null, nplRate: null, cityCode: '610100' }]);
    expect(model.institutions).toEqual([expect.objectContaining({ orgCode: 'AUTH', orgName: '目录名称', cityCode: '610100' })]);
    expect(model.institutions[0]).not.toHaveProperty('metrics');
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'corpRanking', code: 'UNAUTHORIZED_ORG' })
    ]));
  });
});
