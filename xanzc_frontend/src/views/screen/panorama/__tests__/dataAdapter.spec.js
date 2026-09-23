import { describe, expect, it } from 'vitest';
import { adaptPanoramaResults, createEmptyPanoramaModel } from '../dataAdapter';

const binding = (slot, fields, units = {}) => ({
  slot, dsId: 1, period: 'LATEST', fields, units
});

describe('panorama data adapter', () => {
  it('columns 模式适配中间收入和营业收入字段，保留金额单位与值', () => {
    const model = adaptPanoramaResults({ composition: {
      binding: binding('composition', {
        corporate: 'corp', retail: 'retail', total: 'total',
        intermediaryIncome: 'intermediate', operatingRevenue: 'revenue'
      }, {
        corporate: 'YUAN', retail: 'YUAN', total: 'YUAN', intermediaryIncome: 'YUAN', operatingRevenue: 'YUAN'
      }),
      response: {
        columns: ['corp', 'retail', 'total', 'intermediate', 'revenue'],
        rows: [[100, 200, 300, 30, 150]],
        columnsMeta: ['corp', 'retail', 'total', 'intermediate', 'revenue'].map(col => ({ col, role: 'METRIC', unit: 'YUAN' }))
      }
    }});
    expect(model.issues.filter(item => item.slot === 'composition')).toEqual([]);
    expect(model.composition).toEqual([
      { name: '对公业务', value: 1e-6, unit: '亿元' },
      { name: '零售业务', value: 2e-6, unit: '亿元' }
    ]);
  });

  it('columns 模式适配贷款字段为独立构成项，不覆盖存款构成项', () => {
    const model = adaptPanoramaResults({ composition: {
      binding: binding('composition', {
        corporate: 'corp', retail: 'retail', corporateLoan: 'corpLoan', retailLoan: 'retailLoan', totalLoan: 'loanTotal'
      }, { corporate: 'YUAN', retail: 'YUAN', corporateLoan: 'YUAN', retailLoan: 'YUAN', totalLoan: 'YUAN' }),
      response: { columns: ['corp', 'retail', 'corpLoan', 'retailLoan', 'loanTotal'], rows: [[100, 200, 30, 70, 100]], columnsMeta: ['corp', 'retail', 'corpLoan', 'retailLoan', 'loanTotal'].map(col => ({ col, role: 'METRIC', unit: 'YUAN' })) }
    }});
    expect(model.issues.filter(item => item.slot === 'composition')).toEqual([]);
    expect(model.composition).toEqual([
      { name: '对公业务', value: 1e-6, unit: '亿元' },
      { name: '零售业务', value: 2e-6, unit: '亿元' }
    ]);
  });

  it('合法数值字段为 null 时分类为 NO_VALUES，非空非法字符串仍为 INVALID_NUMBER', () => {
    const missing = adaptPanoramaResults({ deposit: {
      binding: binding('deposit', { value: 'amount' }, { value: 'YUAN' }),
      response: { columns: ['amount'], rows: [[null]] }
    } });
    expect(missing.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'deposit', code: 'NO_VALUES', field: 'value' })
    ]));
    expect(missing.issues.some(item => item.slot === 'deposit' && item.code === 'INVALID_NUMBER')).toBe(false);

    const invalid = adaptPanoramaResults({ deposit: {
      binding: binding('deposit', { value: 'amount' }, { value: 'YUAN' }),
      response: { columns: ['amount'], rows: [['bad']] }
    } });
    expect(invalid.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'deposit', code: 'INVALID_NUMBER', field: 'value' })
    ]));
  });

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
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'loan', code: 'NO_VALUES', field: 'value' })
    ]));
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

  it('显式 HUNDRED_MILLION 单位优先于 columnsMeta amountScale，不重复换算亿元原值', () => {
    const model = adaptPanoramaResults({
      loan: {
        binding: binding('loan', { value: 'loan_amount' }, { value: 'HUNDRED_MILLION' }),
        response: {
          columns: ['loan_amount'], rows: [[12]],
          columnsMeta: [{ col: 'loan_amount', role: 'METRIC', amountScale: 'YUAN' }]
        }
      }
    });
    expect(model.kpis[0]).toMatchObject({ key: 'loan', value: 12, unit: '亿元' });
    expect(model.issues).toEqual([]);
  });

  it('新批次 columnsMeta 的权威原始单位与发布绑定冲突时不换算正常值', () => {
    const model = adaptPanoramaResults({
      deposit: {
        binding: binding('deposit', { value: 'deposit_amount' }, { value: 'HUNDRED_MILLION' }),
        response: {
          quality: { batchId: 'batch-1', status: 'COMPLETE', selectedComplete: true },
          columns: ['deposit_amount'], rows: [[100000000]],
          columnsMeta: [{ col: 'deposit_amount', role: 'METRIC', unit: 'YUAN', decimals: 2 }]
        }
      }
    });
    expect(model.kpis[0]).toMatchObject({ value: null, unit: null });
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'deposit', code: 'UNIT_MISMATCH', field: 'value' })
    ]));
  });

  it('新批次 columnsMeta 的中文元单位可归一为原始元并只换算一次', () => {
    const model = adaptPanoramaResults({
      deposit: {
        binding: binding('deposit', { value: 'deposit_amount' }, { value: 'YUAN' }),
        response: {
          quality: { batchId: 'batch-1', status: 'COMPLETE', selectedComplete: true },
          columns: ['deposit_amount'], rows: [[100000000]],
          columnsMeta: [{ col: 'deposit_amount', role: 'METRIC', unit: '元', decimals: 2 }]
        }
      }
    });
    expect(model.kpis[0]).toMatchObject({ value: 1, unit: '亿元' });
    expect(model.issues).toEqual([]);
  });

  it('新批次把 DIM 列绑定到指标时留空并记录角色口径异常', () => {
    const model = adaptPanoramaResults({
      deposit: {
        binding: binding('deposit', { value: 'deposit_amount' }, { value: 'YUAN' }),
        response: {
          quality: { batchId: 'batch-1', status: 'COMPLETE', selectedComplete: true },
          columns: ['deposit_amount'], rows: [[100000000]],
          columnsMeta: [{ col: 'deposit_amount', role: 'DIM', unit: null, decimals: null }]
        }
      }
    });
    expect(model.kpis[0]).toMatchObject({ value: null, unit: null });
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'deposit', code: 'ROLE_MISMATCH', field: 'value' })
    ]));
  });

  it('新批次 METRIC 列缺少 columnsMeta UNIT 时留空并标为口径/数据缺项，不误报缺值', () => {
    const model = adaptPanoramaResults({
      deposit: {
        binding: binding('deposit', { value: 'deposit_amount' }, { value: 'YUAN' }),
        response: {
          quality: { batchId: 'batch-1', status: 'COMPLETE', selectedComplete: true },
          columns: ['deposit_amount'], rows: [[100000000]],
          columnsMeta: [{ col: 'deposit_amount', role: 'METRIC', unit: null, decimals: 2 }]
        }
      }
    });
    expect(model.kpis[0]).toMatchObject({ value: null, unit: null });
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'deposit', code: 'MISSING_METADATA_UNIT', field: 'value', message: expect.stringContaining('口径/数据缺项') })
    ]));
    expect(model.issues.some(item => item.slot === 'deposit' && item.code === 'NO_VALUES')).toBe(false);
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

  it('attention 按授权目录挂载四个机构，且全辖列表保留合法行', () => {
    const model = adaptPanoramaResults({
      attention: {
        binding: binding('attention', { label: 'org_code', count: 'TEST_BRANCH_ATTENTION', orgCode: 'org_code' }, { count: 'COUNT' }),
        response: { columns: ['org_code', 'TEST_BRANCH_ATTENTION'], rows: [
          ['A', 1], ['B', 2], ['C', 3], ['D', 4]
        ] }
      }
    }, { view: { orgScopeMode: 'NAMED_GROUP', panoramaInstitutions: ['A', 'B', 'C', 'D'].map(orgCode => ({ orgCode, orgName: `机构${orgCode}` })) } });
    expect(model.attention).toEqual([
      { label: 'A', count: 1, orgCode: 'A', orgName: '机构A' },
      { label: 'B', count: 2, orgCode: 'B', orgName: '机构B' },
      { label: 'C', count: 3, orgCode: 'C', orgName: '机构C' },
      { label: 'D', count: 4, orgCode: 'D', orgName: '机构D' }
    ]);
    expect(model.institutions.map(item => item.attention)).toEqual([
      [{ label: 'A', count: 1, orgCode: 'A', orgName: '机构A' }],
      [{ label: 'B', count: 2, orgCode: 'B', orgName: '机构B' }],
      [{ label: 'C', count: 3, orgCode: 'C', orgName: '机构C' }],
      [{ label: 'D', count: 4, orgCode: 'D', orgName: '机构D' }]
    ]);
  });

  it('attention 越组或空机构号只报告 issue，不挂载到任何机构', () => {
    const model = adaptPanoramaResults({
      attention: {
        binding: binding('attention', { label: 'label', count: 'count', orgCode: 'org_code' }, { count: 'COUNT' }),
        response: { columns: ['org_code', 'label', 'count'], rows: [['OUT', '越组', 1], ['', '空机构', 2]] }
      }
    }, { view: { orgScopeMode: 'NAMED_GROUP', panoramaInstitutions: [{ orgCode: 'A' }] } });
    expect(model.institutions[0].attention).toEqual([]);
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'attention', code: 'UNAUTHORIZED_ORG' }),
      expect.objectContaining({ slot: 'attention', code: 'MISSING_ORG_CODE' })
    ]));
  });

  it('attention 未绑定机构号保持全辖兼容，不新增机构', () => {
    const model = adaptPanoramaResults({
      attention: {
        binding: binding('attention', { label: 'label', count: 'count' }, { count: 'COUNT' }),
        response: { columns: ['label', 'count'], rows: [['全辖关注', 4]] }
      }
    }, { view: { orgScopeMode: 'NAMED_GROUP', panoramaInstitutions: [{ orgCode: 'A' }] } });
    expect(model.attention).toEqual([{ label: '全辖关注', count: 4 }]);
    expect(model.institutions).toHaveLength(1);
    expect(model.institutions[0].attention).toEqual([]);
  });

  it('attention 重复机构号 fail closed，且 attention 先于 branches 时仍能挂载', () => {
    const model = adaptPanoramaResults({
      attention: {
        binding: binding('attention', { label: 'label', count: 'count', orgCode: 'org_code' }, { count: 'COUNT' }),
        response: { columns: ['org_code', 'label', 'count'], rows: [['A', '重复1', 1], ['A', '重复2', 2]] }
      },
      branches: {
        binding: binding('branches', { orgCode: 'org_code' }, {}),
        response: { columns: ['org_code'], rows: [['A']] }
      }
    }, { view: { orgScopeMode: 'NAMED_GROUP', panoramaInstitutions: [{ orgCode: 'A' }] } });
    expect(model.institutions[0].attention).toEqual([]);
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'attention', code: 'DUPLICATE_ORG_CODE' })
    ]));
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

  it('机构目录保留 camel/snake locationSource，未定位机构强制清空来源，未知来源可透传', () => {
    const model = adaptPanoramaResults({
      branches: {
        binding: binding('branches', { orgCode: 'org_code', deposit: 'deposit' }, { deposit: 'YUAN' }),
        response: {
          columns: ['org_code', 'deposit'],
          rows: [['A', 100000000], ['B', 200000000], ['C', 300000000]]
        }
      }
    }, {
      view: {
        orgScopeMode: 'NAMED_GROUP',
        panoramaInstitutions: [
          { orgCode: 'A', lng: 108.90, lat: 34.20, located: true, locationSource: 'MANUAL' },
          { org_code: 'B', longitude: 108.91, latitude: 34.21, located: false, location_source: 'PROFILE' },
          { orgCode: 'C', lng: 108.92, lat: 34.22, located: true, location_source: 'NEEDS_REVIEW' }
        ]
      }
    });

    expect(model.institutions.map(item => item.locationSource))
      .toEqual(['MANUAL', null, 'NEEDS_REVIEW']);
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

  it('composition 旧行模式保留明确名称和值的数值换算', () => {
    const model = adaptPanoramaResults({
      composition: {
        binding: binding('composition', { name: 'kind', value: 'amount' }, { value: 'TEN_THOUSAND' }),
        response: { columns: ['kind', 'amount'], rows: [['存款', 123456]] }
      }
    });
    expect(model.composition).toEqual([{ name: '存款', value: 12.3456, unit: '亿元' }]);
    expect(model.issues).toEqual([]);
  });

  it('composition 旧行模式保留多行分类，不把第二行当作重复列拒绝', () => {
    const model = adaptPanoramaResults({
      composition: {
        binding: binding('composition', { name: 'kind', value: 'amount' }, { value: 'YUAN' }),
        response: { columns: ['kind', 'amount'], rows: [['对公', 100000000], ['零售', 200000000]] }
      }
    });
    expect(model.composition).toEqual([
      { name: '对公', value: 1, unit: '亿元' },
      { name: '零售', value: 2, unit: '亿元' }
    ]);
    expect(model.issues).toEqual([]);
  });

  it('composition 旧行模式保留历史空值与单位不匹配结果形状', () => {
    const emptyValue = adaptPanoramaResults({
      composition: {
        binding: binding('composition', { name: 'kind', value: 'amount' }, { value: 'YUAN' }),
        response: { columns: ['kind', 'amount'], rows: [['空值', null]] }
      }
    });
    expect(emptyValue.composition).toEqual([{ name: '空值', value: null, unit: '亿元' }]);
    expect(emptyValue.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'composition', code: 'NO_VALUES', field: 'value' })
    ]));

    const mismatchedUnit = adaptPanoramaResults({
      composition: {
        binding: binding('composition', { name: 'kind', value: 'amount' }, { value: 'COUNT' }),
        response: { columns: ['kind', 'amount'], rows: [['错误单位', 1]] }
      }
    });
    expect(mismatchedUnit.composition).toEqual([{ name: '错误单位', value: null, unit: null }]);
    expect(mismatchedUnit.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'composition', code: 'UNIT_MISMATCH', field: 'value' })
    ]));
  });

  it('composition 双列金额允许元/万元混用并统一为亿元，保留零值', () => {
    const model = adaptPanoramaResults({
      composition: {
        binding: binding('composition', { corporate: 'corp', retail: 'retail' }, {
          corporate: 'YUAN', retail: 'TEN_THOUSAND'
        }),
        response: { columns: ['corp', 'retail'], rows: [[0, 50000]] }
      }
    });
    expect(model.composition).toEqual([
      { name: '对公业务', value: 0, unit: '亿元' },
      { name: '零售业务', value: 5, unit: '亿元' }
    ]);
    expect(model.issues).toEqual([]);
  });

  it('composition 双列比例允许 PERCENT/RATIO 混用并统一为百分数', () => {
    const model = adaptPanoramaResults({
      composition: {
        binding: binding('composition', { corporate: 'corp', retail: 'retail' }, {
          corporate: 'PERCENT', retail: 'RATIO'
        }),
        response: { columns: ['corp', 'retail'], rows: [[0, 0.25]] }
      }
    });
    expect(model.composition).toEqual([
      { name: '对公业务', value: 0, unit: '%' },
      { name: '零售业务', value: 25, unit: '%' }
    ]);
    expect(model.issues).toEqual([]);
  });

  it('composition 对空行、重复行、缺列和非数值原子拒绝且不取首行/求和/填零', () => {
    const cases = [
      { response: { columns: ['corp', 'retail'], rows: [] }, code: 'INVALID_ROW_COUNT' },
      { response: { columns: ['corp', 'retail'], rows: [[1, 2], [3, 4]] }, code: 'INVALID_ROW_COUNT' },
      { response: { columns: ['corp'], rows: [[1]] }, code: 'MISSING_COLUMN' },
      { response: { columns: ['corp', 'retail'], rows: [['oops', 2]] }, code: 'INVALID_NUMBER' }
    ];
    for (const item of cases) {
      const model = adaptPanoramaResults({
        composition: {
          binding: binding('composition', { corporate: 'corp', retail: 'retail' }, {
            corporate: 'YUAN', retail: 'YUAN'
          }),
          response: item.response
        }
      });
      expect(model.composition).toEqual([]);
      expect(model.issues).toEqual(expect.arrayContaining([
        expect.objectContaining({ slot: 'composition', code: item.code })
      ]));
    }
  });

  it('composition 拒绝混合模式、混类单位和单位指向未绑定字段', () => {
    const mixedMode = adaptPanoramaResults({
      composition: {
        binding: binding('composition', {
          name: 'kind', value: 'amount', corporate: 'corp', retail: 'retail'
        }, { value: 'YUAN', corporate: 'YUAN', retail: 'YUAN' }),
        response: { columns: ['kind', 'amount', 'corp', 'retail'], rows: [['旧', 1, 2, 3]] }
      }
    });
    expect(mixedMode.composition).toEqual([]);
    expect(mixedMode.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'composition', code: 'INVALID_BINDING_MODE' })
    ]));

    const mixedUnits = adaptPanoramaResults({
      composition: {
        binding: binding('composition', { corporate: 'corp', retail: 'retail' }, {
          corporate: 'PERCENT', retail: 'YUAN'
        }),
        response: { columns: ['corp', 'retail'], rows: [[50, 100000000]] }
      }
    });
    expect(mixedUnits.composition).toEqual([]);
    expect(mixedUnits.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'composition', code: 'MIXED_UNIT_KIND' })
    ]));

    const unboundUnit = adaptPanoramaResults({
      composition: {
        binding: binding('composition', { corporate: 'corp', retail: 'retail' }, {
          corporate: 'YUAN', retail: 'YUAN', value: 'YUAN'
        }),
        response: { columns: ['corp', 'retail'], rows: [[1, 2]] }
      }
    });
    expect(unboundUnit.composition).toEqual([]);
    expect(unboundUnit.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'composition', code: 'UNIT_UNBOUND_FIELD' })
    ]));
  });

  it('composition columns 可选读取 total 的单位/角色校验，但旧 CompositionBreakdown 仍只输出对公和零售', () => {
    const model = adaptPanoramaResults({
      composition: {
        binding: binding('composition', { corporate: 'corp', retail: 'retail', total: 'total' }, {
          corporate: 'TEN_THOUSAND', retail: 'YUAN', total: 'HUNDRED_MILLION'
        }),
        response: {
          columns: ['corp', 'retail', 'total'], rows: [[10000, 200000000, 3]],
          columnsMeta: [
            { col: 'corp', role: 'METRIC', unit: 'TEN_THOUSAND' },
            { col: 'retail', role: 'METRIC', unit: 'YUAN' },
            { col: 'total', role: 'METRIC', unit: 'HUNDRED_MILLION' }
          ]
        }
      }
    });
    expect(model.composition).toEqual([
      { name: '对公业务', value: 1, unit: '亿元' },
      { name: '零售业务', value: 2, unit: '亿元' }
    ]);
    expect(model.issues).toEqual([]);
  });

  it('composition columns 拒绝 total 的混类单位或 DIM 角色', () => {
    const mixed = adaptPanoramaResults({
      composition: {
        binding: binding('composition', { corporate: 'corp', retail: 'retail', total: 'total' }, {
          corporate: 'YUAN', retail: 'YUAN', total: 'PERCENT'
        }),
        response: { columns: ['corp', 'retail', 'total'], rows: [[1, 2, 3]] }
      }
    });
    expect(mixed.composition).toEqual([]);
    expect(mixed.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'composition', code: 'MIXED_UNIT_KIND' })
    ]));

    const dimension = adaptPanoramaResults({
      composition: {
        binding: binding('composition', { corporate: 'corp', retail: 'retail', total: 'total' }, {
          corporate: 'YUAN', retail: 'YUAN', total: 'YUAN'
        }),
        response: {
          quality: { batchId: 'batch-1', status: 'COMPLETE', selectedComplete: true },
          columns: ['corp', 'retail', 'total'], rows: [[1, 2, 3]],
          columnsMeta: [
            { col: 'corp', role: 'METRIC', unit: 'YUAN' },
            { col: 'retail', role: 'METRIC', unit: 'YUAN' },
            { col: 'total', role: 'DIM', unit: null }
          ]
        }
      }
    });
    expect(dimension.composition).toEqual([]);
    expect(dimension.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'composition', code: 'ROLE_MISMATCH', field: 'total' })
    ]));
  });

  it('旧 composition 行模式不接受仅列式的 total 字段，但仍不要求分母', () => {
    const model = adaptPanoramaResults({
      composition: {
        binding: binding('composition', { name: 'kind', value: 'amount', total: 'total' }, {
          value: 'YUAN', total: 'YUAN'
        }),
        response: { columns: ['kind', 'amount', 'total'], rows: [['对公', 1, 2]] }
      }
    });
    expect(model.composition).toEqual([]);
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'composition', code: 'UNSUPPORTED_FIELD', field: 'total' })
    ]));
  });
});
