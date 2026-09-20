import { describe, expect, it } from 'vitest';
import { adaptRetailResults, createEmptyRetailModel } from '../retailDataAdapter';

const binding = (slot, fields, units = {}) => ({
  dsId: 1,
  period: slot === 'retailTrend' ? 'LAST_6M_EOM' : 'LATEST',
  fields,
  units
});

const table = (columns, rows, extra = {}) => ({ columns, rows, ...extra });

describe('retailDataAdapter', () => {
  it('返回零售空模型且不从旧分行槽位构造数据', () => {
    expect(createEmptyRetailModel()).toEqual({
      title: '零售经营总览', dataDate: '', kpis: [], trend: [], segments: [], rankings: [],
      attention: [], targets: [], institutions: [], issues: []
    });
    const model = adaptRetailResults({
      deposit: { binding: binding('deposit', { value: 'value' }, { value: 'YUAN' }), response: table(['value'], [[100000000]]) },
      loan: { binding: binding('loan', { value: 'value' }, { value: 'YUAN' }), response: table(['value'], [[200000000]]) }
    });
    expect(model.kpis).toEqual([]);
    expect(model.issues).toEqual([]);
  });

  it('统一金额、客户和事项数量的单位，保留零值并把空值变为 null', () => {
    const model = adaptRetailResults({
      retailAum: {
        binding: binding('retailAum', { value: 'aum', change: 'change', date: 'date' },
          { value: 'YUAN', change: 'RATIO' }),
        response: table(['aum', 'change', 'date'], [[0, '', '2026-09-08']])
      },
      retailValueCustomers: {
        binding: binding('retailValueCustomers', { value: 'customers' }, { value: 'COUNT' }),
        response: table(['customers'], [[23000]])
      },
      retailNplRate: {
        binding: binding('retailNplRate', { value: 'npl' }, { value: 'RATIO' }),
        response: table(['npl'], [[0.012]])
      },
      retailAttention: {
        binding: binding('retailAttention', { label: 'label', count: 'count' }, { count: 'COUNT' }),
        response: table(['label', 'count'], [['逾期', 0]])
      },
      retailSegments: {
        binding: binding('retailSegments', { name: 'name', customers: 'customers', aum: 'aum' },
          { customers: 'TEN_THOUSAND_COUNT', aum: 'HUNDRED_MILLION' }),
        response: table(['name', 'customers', 'aum'], [['大众', '', 0]])
      }
    });
    expect(model.kpis).toEqual(expect.arrayContaining([
      { key: 'retailAum', label: '零售AUM', value: 0, unit: '亿元', change: null, date: '2026-09-08' },
      { key: 'retailValueCustomers', label: '价值客户', value: 2.3, unit: '万户', change: null },
      { key: 'retailNplRate', label: '个贷不良率', value: 1.2, unit: '%', change: null }
    ]));
    expect(model.attention).toEqual([{ label: '逾期', count: 0, owner: null, deadline: null }]);
    expect(model.segments).toEqual([{ name: '大众', customers: null, aum: 0 }]);
    expect(model.dataDate).toBe('2026-09-08');
  });

  it('趋势、客群、排名和目标使用独立字段映射', () => {
    const model = adaptRetailResults({
      retailTrend: {
        binding: binding('retailTrend', { date: 'month', aum: 'aum', deposit: 'deposit' },
          { aum: 'TEN_THOUSAND', deposit: 'HUNDRED_MILLION' }),
        response: table(['month', 'aum', 'deposit'], [['2026-08', 10000, 2]])
      },
      retailRanking: {
        binding: binding('retailRanking', {
          orgCode: 'code', name: 'label', aum: 'amount', increase: 'inc', rate: 'rate', nplRate: 'npl'
        }, { aum: 'YUAN', increase: 'TEN_THOUSAND', rate: 'PERCENT', nplRate: 'RATIO' }),
        response: table(['code', 'label', 'amount', 'inc', 'rate', 'npl'], [['A', '甲', 100000000, 10000, 3, 0.02]])
      },
      retailTargets: {
        binding: binding('retailTargets', { name: 'targetName', actual: 'actualRaw', target: 'targetRaw' },
          { actual: 'TEN_THOUSAND', target: 'HUNDRED_MILLION' }),
        response: table(['targetName', 'actualRaw', 'targetRaw'], [['AUM', 20000, 3]])
      }
    });
    expect(model.trend).toEqual([{ date: '2026-08', aum: 1, deposit: 2, depositAverage: null }]);
    expect(model.rankings).toEqual([{
      orgCode: 'A', name: '甲', orgName: null, aum: 1, deposit: null, average: null, date: null,
      increase: 1, rate: 3, nplRate: 2, cityCode: null, orgNature: null,
      operatingLevel: null, ownerOperatingOrgCode: null
    }]);
    expect(model.targets).toEqual([{ name: 'AUM', actual: 2, target: 3 }]);
  });

  it('拒绝单值多行、趋势重复日期和排名重复机构，不叠加重复行', () => {
    const model = adaptRetailResults({
      retailAum: {
        binding: binding('retailAum', { value: 'aum' }, { value: 'YUAN' }),
        response: table(['aum'], [[100000000], [200000000]])
      },
      retailTrend: {
        binding: binding('retailTrend', { date: 'date', aum: 'aum' }, { aum: 'YUAN' }),
        response: table(['date', 'aum'], [['D1', 100000000], ['D1', 200000000], ['D2', 300000000]])
      },
      retailRanking: {
        binding: binding('retailRanking', { orgCode: 'code', name: 'name', aum: 'aum' }, { aum: 'YUAN' }),
        response: table(['code', 'name', 'aum'], [['A', '甲', 100000000], ['A', '甲2', 200000000], ['B', '乙', 300000000]])
      }
    });
    expect(model.kpis).toEqual([{ key: 'retailAum', label: '零售AUM', value: null, unit: null, change: null }]);
    expect(model.trend).toEqual([{ date: 'D2', aum: 3, deposit: null, depositAverage: null }]);
    expect(model.rankings).toEqual([{
      orgCode: 'B', name: '乙', orgName: null, aum: 3, deposit: null, average: null, date: null,
      increase: null, rate: null, nplRate: null, cityCode: null, orgNature: null,
      operatingLevel: null, ownerOperatingOrgCode: null
    }]);
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'retailAum', code: 'INVALID_ROW_COUNT' }),
      expect.objectContaining({ slot: 'retailTrend', code: 'DUPLICATE_DATE' }),
      expect.objectContaining({ slot: 'retailRanking', code: 'DUPLICATE_ORG' })
    ]));
  });

  it('缺少显式单位或数值非有限时 fail closed', () => {
    const model = adaptRetailResults({
      retailAum: {
        binding: binding('retailAum', { value: 'aum' }),
        response: table(['aum'], [['not-a-number']])
      },
      retailLoan: {
        binding: binding('retailLoan', { value: 'loan' }, { value: 'YUAN' }),
        response: table(['loan'], [[Infinity]])
      }
    });
    expect(model.kpis).toEqual(expect.arrayContaining([
      { key: 'retailAum', label: '零售AUM', value: null, unit: null, change: null },
      { key: 'retailLoan', label: '个人贷款', value: null, unit: '亿元', change: null }
    ]));
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'retailAum', code: 'UNKNOWN_UNIT' }),
      expect.objectContaining({ slot: 'retailLoan', code: 'INVALID_NUMBER' })
    ]));
  });

  it('必填身份为空时跳过该行，响应列名重复时拒绝整张表', () => {
    const model = adaptRetailResults({
      retailTrend: {
        binding: binding('retailTrend', { date: 'date', aum: 'aum' }, { aum: 'YUAN' }),
        response: table(['date', 'aum'], [[null, 100000000], ['D2', 200000000]])
      },
      retailRanking: {
        binding: binding('retailRanking', { orgCode: 'code', name: 'name', aum: 'aum' }, { aum: 'YUAN' }),
        response: table(['code', 'name', 'aum'], [[null, '无机构', 100000000], ['B', '乙', 200000000]])
      },
      retailLoan: {
        binding: binding('retailLoan', { value: 'loan' }, { value: 'YUAN' }),
        response: table(['loan', 'loan'], [[100000000, 200000000]])
      }
    });
    expect(model.trend).toEqual([{ date: 'D2', aum: 2, deposit: null, depositAverage: null }]);
    expect(model.rankings).toEqual([{
      orgCode: 'B', name: '乙', orgName: null, aum: 2, deposit: null, average: null, date: null,
      increase: null, rate: null, nplRate: null, cityCode: null, orgNature: null,
      operatingLevel: null, ownerOperatingOrgCode: null
    }]);
    expect(model.kpis).toEqual([{ key: 'retailLoan', label: '个人贷款', value: null, unit: null, change: null }]);
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'retailTrend', code: 'MISSING_DIMENSION' }),
      expect.objectContaining({ slot: 'retailRanking', code: 'MISSING_DIMENSION' }),
      expect.objectContaining({ slot: 'retailLoan', code: 'DUPLICATE_COLUMNS' })
    ]));
  });

  it('NAMED_GROUP 排名只保留授权机构并补 cityCode，不猜名称和坐标', () => {
    const model = adaptRetailResults({
      retailRanking: {
        binding: binding('retailRanking', { orgCode: 'code', name: 'name', aum: 'aum' }, { aum: 'YUAN' }),
        response: table(['code', 'name', 'aum'], [['AUTH', '返回名称', 100000000], ['OUT', '外部名称', 200000000]])
      }
    }, {
      view: {
        orgScopeMode: 'NAMED_GROUP',
        panoramaInstitutions: [{ orgCode: 'AUTH', orgName: '目录名称', cityCode: '610100', lng: 108.9, lat: 34.2 }]
      }
    });
    expect(model.rankings).toEqual([{
      orgCode: 'AUTH', name: '返回名称', orgName: '目录名称', aum: 1, deposit: null, average: null,
      date: null, increase: null, rate: null, nplRate: null, cityCode: '610100',
      orgNature: null, operatingLevel: null, ownerOperatingOrgCode: null
    }]);
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'retailRanking', code: 'UNAUTHORIZED_ORG', message: '来源包含授权目录外机构，已排除' })
    ]));
  });

  it('branches 只产出机构身份，不给零售指标填值，也不从目录缺失值回填源行坐标城市', () => {
    const model = adaptRetailResults({
      branches: {
        binding: binding('branches', {
          orgCode: 'code', orgName: 'name', cityCode: 'city', lng: 'lng', lat: 'lat', coordSys: 'coord', located: 'located', deposit: 'deposit'
        }, { deposit: 'YUAN' }),
        response: table(['code', 'name', 'city', 'lng', 'lat', 'coord', 'located', 'deposit'], [['A', '甲', '610100', 108.9, 34.2, 'GCJ02', true, 100000000]])
      }
    }, {
      view: {
        orgScopeMode: 'NAMED_GROUP',
        panoramaInstitutions: [{ orgCode: 'A', orgName: '目录名', cityCode: null, lng: null, lat: null, coordSys: null, located: 'false' }]
      }
    });
    expect(model.institutions).toEqual([expect.objectContaining({
      orgCode: 'A', orgName: '目录名', cityCode: null, lng: null, lat: null, located: false
    })]);
    expect(model.institutions[0]).not.toHaveProperty('metrics');
    expect(model.kpis).toEqual([]);
    expect(model.segments).toEqual([]);
  });

  it('没有 branches 响应时仍保留已授权机构目录，并保存单值各自日期且提示混合日期', () => {
    const model = adaptRetailResults({
      retailAum: {
        binding: binding('retailAum', { value: 'aum', date: 'date' }, { value: 'YUAN' }),
        response: table(['aum', 'date'], [[100000000, 'D1']])
      },
      retailDeposit: {
        binding: binding('retailDeposit', { value: 'deposit', date: 'date' }, { value: 'YUAN' }),
        response: table(['deposit', 'date'], [[200000000, 'D2']])
      }
    }, { view: { screenName: '零售经营屏', panoramaInstitutions: [{ orgCode: 'A', cityCode: '610100' }] } });
    expect(model.institutions).toEqual([expect.objectContaining({ orgCode: 'A', cityCode: '610100' })]);
    expect(model).toMatchObject({ title: '零售经营屏', scopeLabel: '当前大屏授权范围' });
    expect(model.kpis).toEqual([
      { key: 'retailAum', label: '零售AUM', value: 1, unit: '亿元', change: null, date: 'D1' },
      { key: 'retailDeposit', label: '储蓄存款余额', value: 2, unit: '亿元', change: null, date: 'D2' }
    ]);
    expect(model.dataDate).toBe('D1');
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'MIXED_DATES' })
    ]));
  });

  it('存款排名保留 deposit、月日均和日期，并从权威目录补充同层筛选身份', () => {
    const model = adaptRetailResults({
      retailRanking: {
        binding: binding('retailRanking', {
          orgCode: 'code', name: 'label', deposit: 'deposit', average: 'average', date: 'date'
        }, { deposit: 'YUAN', average: 'YUAN' }),
        response: table(
          ['code', 'label', 'deposit', 'average', 'date'],
          [['AUTH', '来源名称', 120000000, 100000000, '2026-08']]
        )
      }
    }, {
      view: {
        orgScopeMode: 'NAMED_GROUP',
        panoramaInstitutions: [{
          orgCode: 'AUTH', orgName: '目录名称', cityCode: '610100', orgNature: 'BRANCH',
          operatingLevel: 'PRIMARY', ownerOperatingOrgCode: 'ROOT'
        }]
      }
    });
    expect(model.rankings).toEqual([expect.objectContaining({
      orgCode: 'AUTH', name: '来源名称', aum: null, deposit: 1.2, average: 1,
      date: '2026-08', orgNature: 'BRANCH', operatingLevel: 'PRIMARY',
      ownerOperatingOrgCode: 'ROOT'
    })]);
  });

  it('月日均趋势保留 depositAverage 金额序列，同时允许旧 AUM/deposit 形状', () => {
    const model = adaptRetailResults({
      retailTrend: {
        binding: binding('retailTrend', {
          date: 'month', deposit: 'deposit', depositAverage: 'average'
        }, { deposit: 'YUAN', depositAverage: 'YUAN' }),
        response: table(
          ['month', 'deposit', 'average'],
          [['2026-08', 200000000, 180000000], ['2026-09', 210000000, 190000000]]
        )
      }
    });
    expect(model.trend).toEqual([
      { date: '2026-08', aum: null, deposit: 2, depositAverage: 1.8 },
      { date: '2026-09', aum: null, deposit: 2.1, depositAverage: 1.9 }
    ]);
  });
});
