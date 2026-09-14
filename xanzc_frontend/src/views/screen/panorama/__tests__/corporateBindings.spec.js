import { describe, expect, it } from 'vitest';
import {
  CORPORATE_BINDING_SLOTS,
  CORPORATE_SLOT_ORDER,
  CORPORATE_TEMPLATE,
  UNIT_VALUES,
  isCorporateBindingSlot,
  isCorporateTemplate,
  normalizeCorporateBinding,
  validateCorporateBinding
} from '../corporateBindings.js';

describe('corporateBindings 对公经营总览绑定契约', () => {
  it('声明固定模板、单值、趋势、客群、排名、关注、目标和共享机构槽位', () => {
    expect(CORPORATE_TEMPLATE).toBe('corporate-overview-v1');
    expect(Object.keys(CORPORATE_BINDING_SLOTS)).toEqual(expect.arrayContaining([
      'corpDeposit', 'corpDepositAverage', 'corpLoan', 'corpRevenue',
      'corpCustomers', 'corpNplRate', 'corpTrend', 'corpSegments',
      'corpRanking', 'corpAttention', 'corpTargets', 'branches'
    ]));
    expect(CORPORATE_BINDING_SLOTS.corpDeposit.required).toEqual(['value']);
    expect(CORPORATE_BINDING_SLOTS.corpTrend.required).toEqual(['date']);
    expect(CORPORATE_BINDING_SLOTS.corpTrend.atLeastOneOf).toEqual(['deposit', 'loan']);
    expect(CORPORATE_BINDING_SLOTS.corpSegments.required).toEqual(['name', 'customers', 'loan']);
    expect(CORPORATE_BINDING_SLOTS.corpRanking.required).toEqual(['orgCode', 'name', 'deposit']);
    expect(CORPORATE_BINDING_SLOTS.corpAttention.required).toEqual(['label', 'count']);
    expect(CORPORATE_BINDING_SLOTS.corpTargets.required).toEqual(['name', 'actual', 'target']);
    expect(CORPORATE_SLOT_ORDER.at(-1)).toBe('branches');
    expect(isCorporateTemplate('corporate-overview-v1')).toBe(true);
    expect(isCorporateBindingSlot('corpDeposit')).toBe(true);
    expect(isCorporateBindingSlot('retailAum')).toBe(false);
  });

  it('按业务语义校验单位，关注事项固定 COUNT，客户支持 COUNT/万户', () => {
    expect(validateCorporateBinding('corpDeposit', {
      dsId: 1, fields: { value: 'deposit' }, units: { value: 'HUNDRED_MILLION' }
    })).toEqual([]);
    expect(validateCorporateBinding('corpCustomers', {
      dsId: 1, fields: { value: 'customers' }, units: { value: 'TEN_THOUSAND_COUNT' }
    })).toEqual([]);
    expect(validateCorporateBinding('corpNplRate', {
      dsId: 1, fields: { value: 'npl' }, units: { value: 'RATIO' }
    })).toEqual([]);
    expect(validateCorporateBinding('corpAttention', {
      dsId: 1, fields: { label: 'label', count: 'count' }, units: { count: 'COUNT' }
    })).toEqual([]);
    expect(validateCorporateBinding('corpAttention', {
      dsId: 1, fields: { label: 'label', count: 'count' }, units: { count: 'TEN_THOUSAND_COUNT' }
    })).toContain('单位不适用: count');
    expect(validateCorporateBinding('corpTrend', {
      dsId: 1, fields: { date: 'date' }, units: {}
    })).toContain('至少选择一个字段: deposit、loan');
    expect(validateCorporateBinding('unknown', {})).toContain('槽位不受支持');
    expect(UNIT_VALUES).toEqual(expect.arrayContaining([
      'YUAN', 'TEN_THOUSAND', 'HUNDRED_MILLION', 'COUNT', 'TEN_THOUSAND_COUNT', 'PERCENT', 'RATIO'
    ]));
    expect(normalizeCorporateBinding({
      dsId: 2, fields: { value: ' deposit ' }, units: { value: 'YUAN' }
    }, 'corpDeposit')).toMatchObject({
      dsId: 2, period: 'LATEST', fields: { value: 'deposit' }, units: { value: 'YUAN' }
    });
  });
});
