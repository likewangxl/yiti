import { describe, expect, it } from 'vitest';
import {
  ALL_SLOT_ORDER,
  BINDING_SLOTS,
  BRANCH_SLOT_ORDER,
  SLOT_ORDER,
  UNIT_VALUES,
  validateBinding
} from '../bindings';
import {
  RETAIL_AMOUNT_UNITS,
  RETAIL_BINDING_SLOTS,
  RETAIL_SLOT_ORDER,
  RETAIL_TEMPLATE,
  isRetailBindingSlot,
  normalizeRetailBinding,
  validateRetailBinding
} from '../retailBindings';

describe('retail panorama bindings contract', () => {
  it('公开零售模板、13 个槽位以及共享 branches 身份槽', () => {
    expect(RETAIL_TEMPLATE).toBe('retail-overview-v1');
    expect(RETAIL_SLOT_ORDER).toEqual([
      'retailAum', 'retailDeposit', 'retailDepositAverage', 'retailRevenue',
      'retailValueCustomers', 'retailLoan', 'retailNplRate',
      'retailTrend', 'retailSegments', 'retailRanking', 'retailAttention', 'retailTargets',
      'branches'
    ]);
    expect(RETAIL_BINDING_SLOTS.branches.required).toEqual(['orgCode']);
    expect(RETAIL_BINDING_SLOTS.retailAum.required).toEqual(['value']);
    expect(RETAIL_BINDING_SLOTS.retailValueCustomers.fields).toEqual(expect.arrayContaining([
      expect.objectContaining({ semantic: 'value', unitKinds: ['COUNT', 'TEN_THOUSAND_COUNT'] })
    ]));
    expect(RETAIL_BINDING_SLOTS.retailNplRate.fields).toEqual(expect.arrayContaining([
      expect.objectContaining({ semantic: 'value', unitKinds: ['PERCENT', 'RATIO'] })
    ]));
    expect(RETAIL_BINDING_SLOTS.retailTrend.atLeastOneOf).toEqual(['aum', 'deposit']);
    expect(RETAIL_BINDING_SLOTS.retailSegments.required).toEqual(['name', 'customers', 'aum']);
    expect(RETAIL_BINDING_SLOTS.retailRanking.required).toEqual(['orgCode', 'name']);
    expect(RETAIL_BINDING_SLOTS.retailAttention.required).toEqual(['label', 'count']);
    expect(RETAIL_BINDING_SLOTS.retailTargets.required).toEqual(['name', 'actual', 'target']);
  });

  it('把零售槽位纳入全局白名单，同时保留分行旧槽位清单', () => {
    expect(RETAIL_SLOT_ORDER.every(isRetailBindingSlot)).toBe(true);
    expect(BINDING_SLOTS.retailAum).toBe(RETAIL_BINDING_SLOTS.retailAum);
    expect(BINDING_SLOTS.branches).toBeDefined();
    expect(SLOT_ORDER).toEqual(BRANCH_SLOT_ORDER);
    expect(BRANCH_SLOT_ORDER).toHaveLength(14);
    expect(ALL_SLOT_ORDER).toEqual(expect.arrayContaining(RETAIL_SLOT_ORDER));
  });

  it('要求显式单位并按槽位语义校验字段', () => {
    expect(validateRetailBinding('retailAum', {
      dsId: 1, fields: { value: 'aum' }, units: { value: 'HUNDRED_MILLION' }
    })).toEqual([]);
    expect(validateRetailBinding('retailValueCustomers', {
      dsId: 1, fields: { value: 'customers' }, units: { value: 'COUNT' }
    })).toEqual([]);
    expect(validateRetailBinding('retailNplRate', {
      dsId: 1, fields: { value: 'npl' }, units: { value: 'RATIO' }
    })).toEqual([]);
    expect(validateRetailBinding('retailTrend', {
      dsId: 1, fields: { date: 'date', aum: 'aum' }, units: { aum: 'YUAN' }
    })).toEqual([]);
    expect(validateRetailBinding('retailSegments', {
      dsId: 1, fields: { name: 'name', customers: 'customers', aum: 'aum' },
      units: { customers: 'COUNT', aum: 'YUAN' }
    })).toEqual([]);
    expect(validateRetailBinding('retailRanking', {
      dsId: 1, fields: { orgCode: 'org', name: 'name', aum: 'aum', increase: 'inc', rate: 'rate', nplRate: 'npl' },
      units: { aum: 'YUAN', increase: 'YUAN', rate: 'RATIO', nplRate: 'PERCENT' }
    })).toEqual([]);
    expect(validateRetailBinding('retailAttention', {
      dsId: 1, fields: { label: 'label', count: 'count', owner: 'owner', deadline: 'deadline' },
      units: { count: 'COUNT' }
    })).toEqual([]);
    expect(validateRetailBinding('retailTargets', {
      dsId: 1, fields: { name: 'name', actual: 'actual', target: 'target' },
      units: { actual: 'TEN_THOUSAND', target: 'HUNDRED_MILLION' }
    })).toEqual([]);
    expect(validateRetailBinding('retailAum', {
      dsId: 1, fields: { value: 'aum' }
    })).toContain('缺少单位: value');
    expect(validateRetailBinding('retailAum', {
      dsId: 1, fields: { value: 'aum' }, units: { value: 'COUNT' }
    })).toContain('单位不适用: value');
    expect(validateRetailBinding('retailTrend', {
      dsId: 1, fields: { date: 'date' }, units: {}
    })).toContain('至少选择一个字段: aum、deposit');
    expect(validateRetailBinding('unknown', {})).toContain('槽位不受支持');
    expect(normalizeRetailBinding({ dsId: 1, fields: { value: ' aum ' }, units: { value: 'YUAN' } }, 'retailAum'))
      .toMatchObject({ dsId: 1, period: 'LATEST', fields: { value: 'aum' }, units: { value: 'YUAN' } });
    expect(UNIT_VALUES).toEqual(expect.arrayContaining([
      'YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION', 'COUNT', 'TEN_THOUSAND_COUNT', 'PERCENT', 'RATIO'
    ]));
    expect(validateBinding('retailAum', {
      dsId: 1, fields: { value: 'aum' }, units: { value: 'YUAN' }
    })).toEqual([]);
  });

  it('零售机构排名以机构身份为必填，并支持存款、月日均和日期维度', () => {
    expect(RETAIL_BINDING_SLOTS.retailRanking.required).toEqual(['orgCode', 'name']);
    expect(RETAIL_BINDING_SLOTS.retailRanking.atLeastOneOf).toEqual(['aum', 'deposit']);
    expect(RETAIL_BINDING_SLOTS.retailRanking.fields).toEqual(expect.arrayContaining([
      expect.objectContaining({ semantic: 'deposit', unitKinds: RETAIL_AMOUNT_UNITS }),
      expect.objectContaining({ semantic: 'average', unitKinds: RETAIL_AMOUNT_UNITS }),
      expect.objectContaining({ semantic: 'date', kind: 'dimension' })
    ]));
    expect(validateRetailBinding('retailRanking', {
      dsId: 1,
      fields: { orgCode: 'org', name: 'name', deposit: 'deposit', average: 'average', date: 'date' },
      units: { deposit: 'YUAN', average: 'YUAN' }
    })).toEqual([]);
    expect(validateRetailBinding('retailRanking', {
      dsId: 1, fields: { orgCode: 'org', name: 'name' }
    })).toContain('至少选择一个字段: aum、deposit');
  });

  it('月日均趋势字段允许绑定但仍要求 AUM 或存款时点余额', () => {
    expect(RETAIL_BINDING_SLOTS.retailTrend.fields).toEqual(expect.arrayContaining([
      expect.objectContaining({ semantic: 'depositAverage', unitKinds: RETAIL_AMOUNT_UNITS })
    ]));
    expect(validateRetailBinding('retailTrend', {
      dsId: 1,
      fields: { date: 'date', depositAverage: 'average' },
      units: { depositAverage: 'YUAN' }
    })).toContain('至少选择一个字段: aum、deposit');
  });
});
