import { describe, expect, it } from 'vitest';
import {
  isRelatedBusinessMetric,
  isTestNamedMetric,
  sortBusinessMetricOptions
} from '../businessMetricOptions';

describe('businessMetricOptions', () => {
  it('只按指标名、字段和编码识别测试标识，不误杀普通英文词或测试来源名', () => {
    expect(isTestNamedMetric({ metricName: '测试收入' })).toBe(true);
    expect(isTestNamedMetric({ field: 'TEST_BRANCH_REVENUE' })).toBe(true);
    expect(isTestNamedMetric({ metricCode: '[TEST] M_REVENUE' })).toBe(true);
    expect(isTestNamedMetric({ metricCode: 'M_TEST_2026' })).toBe(true);
    expect(isTestNamedMetric({ metricName: 'LATEST' })).toBe(false);
    expect(isTestNamedMetric({ metricName: 'contest收入' })).toBe(false);
    expect(isTestNamedMetric({ sourceDescription: 'TEST测试来源', metricName: '正式收入' })).toBe(false);
  });

  it('适配项优先，当前 source+field、明确语义、关键词和首选来源只影响排序且不合并身份', () => {
    const entries = [
      { key: '2:deposit', sourceId: 2, field: 'deposit', metricName: '乙指标', semantic: 'other', disabled: false },
      { key: '1:deposit', sourceId: 1, field: 'deposit', metricName: '当前指标', semantic: 'other', disabled: false },
      { key: '3:deposit', sourceId: 3, field: 'deposit', metricName: '增长指标', semantic: 'deposit', disabled: false },
      { key: '4:deposit', sourceId: 4, field: 'deposit', metricName: '不可用增长指标', semantic: 'deposit', disabled: true },
      { key: '5:deposit', sourceId: 5, field: 'deposit', metricName: '甲指标', semantic: 'other', disabled: false },
      { key: '5:deposit', sourceId: 5, field: 'deposit', metricName: '同身份副本', semantic: 'other', disabled: false }
    ];
    const result = sortBusinessMetricOptions(entries, {
      currentSelection: { datasourceId: 1, field: 'deposit' },
      semantic: 'deposit',
      keywords: ['增长'],
      preferredSourceIds: [3]
    });

    expect(result.map(item => item.key)).toEqual([
      '1:deposit', '3:deposit', '5:deposit', '5:deposit', '2:deposit', '4:deposit'
    ]);
    expect(result).not.toBe(entries);
    expect(entries.map(item => item.key)).toEqual([
      '2:deposit', '1:deposit', '3:deposit', '4:deposit', '5:deposit', '5:deposit'
    ]);
  });

  it('关键词相关性只读取显式文本，不读取单位或猜测字段含义', () => {
    const entries = [
      { key: '1:a', sourceId: 1, field: 'a', metricName: '普通指标', unit: 'YUAN', disabled: false },
      { key: '2:b', sourceId: 2, field: 'b', metricName: '客户增长', unit: 'COUNT', disabled: false }
    ];
    expect(sortBusinessMetricOptions(entries, { keywords: ['增长'] }).map(item => item.key))
      .toEqual(['2:b', '1:a']);
    expect(sortBusinessMetricOptions(entries, { keywords: ['YUAN'] }).map(item => item.key))
      .toEqual(sortBusinessMetricOptions(entries).map(item => item.key));
  });

  it('相关候选只由显式语义或字段/名称/编码关键词决定，不因来源名称混合存贷款而误相关', () => {
    const loan = {
      sourceId: 9,
      field: 'loan_balance',
      metricName: '贷款余额',
      metricCode: 'M_LOAN',
      sourceDescription: '存款贷款综合来源'
    };
    expect(isRelatedBusinessMetric(loan, { keywords: ['存款'] })).toBe(false);
    expect(isRelatedBusinessMetric(loan, { keywords: ['贷款'] })).toBe(true);
    expect(isRelatedBusinessMetric({ ...loan, semantic: 'deposit' }, { semantic: 'deposit' })).toBe(true);
    expect(isRelatedBusinessMetric(loan, {})).toBe(true);
  });
});
