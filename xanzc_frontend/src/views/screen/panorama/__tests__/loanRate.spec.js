import { describe, expect, it } from 'vitest';

import {
  ALL_SLOT_ORDER,
  BINDING_SLOTS,
  BRANCH_SLOT_ORDER,
  buildCodeComponents,
  validateBinding
} from '../bindings';
import { adaptPanoramaResults } from '../dataAdapter';

const binding = (fields = { value: 'loan_rate' }, units = { value: 'RATIO' }) => ({
  dsId: 17,
  period: 'LATEST',
  fields,
  units
});

describe('loanRate 独立比例槽位', () => {
  it('在保留历史14个分行槽位的同时注册独立单值槽位', () => {
    expect(BRANCH_SLOT_ORDER).toHaveLength(14);
    expect(BRANCH_SLOT_ORDER).not.toContain('loanRate');
    expect(ALL_SLOT_ORDER).toContain('loanRate');
    expect(BINDING_SLOTS.loanRate).toMatchObject({
      label: '零售贷款目标完成率',
      innerType: 'METRIC_CARD',
      required: ['value']
    });
    expect(BINDING_SLOTS.loanRate.fields).toEqual(expect.arrayContaining([
      expect.objectContaining({ semantic: 'value', unitKinds: ['PERCENT', 'RATIO'] }),
      expect.objectContaining({ semantic: 'change', unitKinds: ['PERCENT', 'RATIO'] }),
      expect.objectContaining({ semantic: 'date', kind: 'dimension' })
    ]));
  });

  it('只接受比例单位，且不会把 loanRate 绑定成旧 rate', () => {
    expect(validateBinding('loanRate', binding())).toEqual([]);
    expect(validateBinding('loanRate', binding({ value: 'loan_rate' }, { value: 'YUAN' })))
      .toContain('单位不适用: value');
    expect(validateBinding('loanRate', binding({ value: 'loan_rate' }, {})))
      .toContain('缺少单位: value');

    const components = buildCodeComponents({ loanRate: binding() });
    expect(components).toHaveLength(1);
    expect(components[0]).toMatchObject({
      propValue: { bindingKey: 'loanRate' },
      innerType: 'METRIC_CARD'
    });
    expect(components[0].propValue.bindingKey).not.toBe('rate');
  });

  it('读取 loanRate 单值并按 RATIO 转成百分比，不伪造旧 rate', () => {
    const model = adaptPanoramaResults({
      loanRate: {
        binding: binding(),
        response: {
          dataDate: '2026-09-21',
          columns: ['loan_rate'],
          rows: [[0.42]]
        }
      }
    });

    expect(model.kpis).toEqual([expect.objectContaining({
      key: 'loanRate',
      label: '零售贷款目标完成率',
      value: 42,
      unit: '%'
    })]);
    expect(model.kpis.find(item => item.key === 'rate')).toBeUndefined();
    expect(model.issues).toEqual([]);
    expect(model.dataDate).toBe('2026-09-21');
  });

  it('loanRate 空值只报告自身缺失，不回退到贷款余额或旧 rate', () => {
    const model = adaptPanoramaResults({
      loan: {
        binding: { dsId: 17, period: 'LATEST', fields: { value: 'loan' }, units: { value: 'YUAN' } },
        response: { columns: ['loan'], rows: [[100000000]] }
      },
      rate: {
        binding: { dsId: 17, period: 'LATEST', fields: { value: 'rate' }, units: { value: 'PERCENT' } },
        response: { columns: ['rate'], rows: [[88]] }
      },
      loanRate: {
        binding: binding(),
        response: { columns: ['loan_rate'], rows: [[null]] }
      }
    });

    expect(model.kpis.find(item => item.key === 'loanRate')).toMatchObject({ value: null, unit: '%' });
    expect(model.kpis.find(item => item.key === 'loanRate').value).not.toBe(88);
    expect(model.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ slot: 'loanRate', code: 'NO_VALUES', field: 'value' })
    ]));
  });
});
