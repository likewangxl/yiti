import { describe, expect, it } from 'vitest';

import {
  buildTargetCards,
  resolveTargetRate,
  stripTestModifier
} from '../targetPresentation';

describe('target presentation', () => {
  it('按明确字段选择存款完成率，并保留旧 rate 兼容', () => {
    const kpis = [
      { key: 'depositRate', label: '零售存款完成率', value: 81.5, unit: '%', date: '2026-09-20' },
      { key: 'rate', label: '旧存款完成率', value: 73, unit: '%' }
    ];

    expect(resolveTargetRate(kpis, 'deposit')).toMatchObject({
      key: 'depositRate',
      value: 81.5,
      item: kpis[0]
    });
    expect(resolveTargetRate([{ key: 'rate', value: 73 }], 'deposit')).toMatchObject({
      key: 'rate',
      value: 73
    });
  });

  it('贷款完成率只读贷款候选，绝不从存款 rate 或贷款余额推导', () => {
    const source = {
      kpis: [
        { key: 'rate', value: 91 },
        { key: 'depositRate', value: 88 },
        { key: 'loan', value: 560 }
      ]
    };

    expect(resolveTargetRate(source, 'loan')).toMatchObject({
      key: null,
      value: null,
      item: null
    });
    expect(buildTargetCards(source, { dataDate: '2026-09-21' })[1]).toMatchObject({
      key: 'loan',
      value: null,
      rate: null,
      date: '—',
      gap: null,
      hasData: false,
      message: '暂无目标数据'
    });
  });

  it('为两张卡分别生成标签、日期、缺口和视觉变体', () => {
    const cards = buildTargetCards({
      dataDate: '2026-09-21',
      kpis: [
        { key: 'retailDepositCompletionRate', value: 125, unit: '%', date: '2026-09-20' },
        { key: 'retailLoanCompletionRate', value: 96.4, unit: '%' }
      ]
    });

    expect(cards).toHaveLength(2);
    expect(cards[0]).toMatchObject({
      key: 'deposit',
      variant: 'deposit',
      label: '零售存款目标完成率',
      value: 125,
      rate: 125,
      date: '2026-09-20',
      gap: 25,
      hasData: true
    });
    expect(cards[1]).toMatchObject({
      key: 'loan',
      variant: 'loan',
      label: '零售贷款目标完成率',
      value: 96.4,
      date: '2026-09-21',
      gap: -3.6,
      hasData: true
    });
  });

  it('只剥离标题末尾测试修饰，不改动其他展示文本', () => {
    expect(stripTestModifier('分行经营总览（测试）')).toBe('分行经营总览');
    expect(stripTestModifier('零售经营总览 (测试)')).toBe('零售经营总览');
    expect(stripTestModifier('分行经营总览-测试')).toBe('分行经营总览');
    expect(stripTestModifier('测试数据质量提醒')).toBe('测试数据质量提醒');
  });

  it('将泛称完成率规范为存款业务标签，保留明确的来源口径标签', () => {
    expect(buildTargetCards({ kpis: [{ key: 'rate', label: '目标完成率', value: 80 }] })[0].label)
      .toBe('零售存款目标完成率');
    expect(buildTargetCards({ kpis: [{ key: 'rate', label: '已设目标机构完成率', value: 80 }] })[0].label)
      .toBe('已设目标机构完成率');
  });
});


describe('目标金额与完成率分离', () => {
  it('金额目标不能作为存款完成率，旧target仅接受明确百分比单位', () => {
    expect(buildTargetCards({kpis:[{key:'target',value:500,unit:'万元'}]})[0].value).toBeNull();
    expect(buildTargetCards({kpis:[{key:'target',value:80,unit:'%'}]})[0].value).toBe(80);
  });
});

it('真实来源的目标标题测试后缀在目标卡与水位图标签入口被清理', () => {
  const cards = buildTargetCards({ kpis: [
    { key: 'rate', value: 85.92, label: '零售存款目标完成率（测试）' },
    { key: 'loanRate', value: 76, label: '零售贷款目标完成率 (测试)' }
  ] });
  expect(cards.map(card => card.label)).toEqual(['零售存款目标完成率', '零售贷款目标完成率']);
});
