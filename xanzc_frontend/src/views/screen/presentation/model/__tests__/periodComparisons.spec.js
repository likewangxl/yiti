import { describe, expect, it } from 'vitest';
import {
  indexPeriodComparisonSources,
  normalizePeriodComparisons,
  resolvePeriodComparisons
} from '../periodComparisons';

const daySlot = {
  mode: 'METRIC',
  metricCode: 'M_0266',
  outputColumn: '一般性存款月均余额较上月-机构',
  unit: 'YUAN',
  tableName: 'ORG_INDEX_RESULT'
};

describe('期间比较配置', () => {
  it('未配置的槽位不造数，错单位直接失败', () => {
    const normalized = normalizePeriodComparisons({
      day: { mode: 'UNSET' },
      month: { mode: 'METRIC', metricCode: 'M_1', outputColumn: '比例', unit: 'PERCENT', tableName: 'ORG_INDEX_RESULT' },
      extra: { mode: 'METRIC' }
    });
    expect(normalized.day.mode).toBe('UNSET');
    expect(normalized.year.mode).toBe('UNSET');
    expect(normalized.extra).toBeUndefined();

    const resolved = resolvePeriodComparisons({
      config: normalized,
      dataDate: '2026-10-15',
      currentYuan: 100,
      rows: [{ data_date: '2026-10-15', 比例: 3 }]
    });
    expect(resolved.comparisons.day).toBeNull();
    expect(resolved.comparisons.month).toMatchObject({ state: 'NO_VALUE', baseValue: null });
    expect(resolved.active).toBe(true);
  });

  it('绑定增量指标时，最新行的列值就是差额，并标上约定基准日', () => {
    const resolved = resolvePeriodComparisons({
      config: { day: daySlot, month: { mode: 'UNSET' }, year: { mode: 'UNSET' } },
      dataDate: '2026-10-15',
      currentYuan: 120,
      rows: [{ data_date: '2026-10-15', '一般性存款月均余额较上月-机构': 12 }]
    });
    expect(resolved.comparisons.day).toEqual({
      state: 'READY',
      baseValue: 12,
      referenceDate: '2026-10-14',
      unit: 'YUAN'
    });
    expect(resolved.comparisons.month).toBeNull();
  });

  it('增量来源日期不是大屏数据日时保持无值', () => {
    const resolved = resolvePeriodComparisons({
      config: { month: { ...daySlot, mode: 'METRIC' } },
      dataDate: '2026-10-15',
      currentYuan: 120,
      rows: [{ data_date: '2026-10-14', '一般性存款月均余额较上月-机构': 12 }]
    });
    expect(resolved.comparisons.month).toMatchObject({ state: 'NO_VALUE', baseValue: null });
  });

  it('按基准日回推用当前主值减去基准日余额，缺日则无值', () => {
    const config = {
      month: {
        mode: 'OFFSET',
        metricCode: 'M_0277',
        outputColumn: '对公一般性存款余额',
        unit: 'HUNDRED_MILLION',
        tableName: 'ORG_INDEX_RESULT'
      }
    };
    const ready = resolvePeriodComparisons({
      config,
      dataDate: '2026-10-15',
      currentYuan: 12e8,
      rows: [
        { data_date: '2026-10-15', 对公一般性存款余额: 12 },
        { data_date: '2026-09-30', 对公一般性存款余额: 10 }
      ]
    });
    expect(ready.comparisons.month).toEqual({
      state: 'READY',
      baseValue: 2e8,
      referenceDate: '2026-09-30',
      unit: 'YUAN'
    });

    const missing = resolvePeriodComparisons({
      config,
      dataDate: '2026-10-15',
      currentYuan: 12e8,
      rows: [{ data_date: '2026-10-15', 对公一般性存款余额: 12 }]
    });
    expect(missing.comparisons.month).toMatchObject({ state: 'NO_VALUE', baseValue: null });
  });

  it('结算性存款没有独立区块时扫描已加载结果行，回推则附带存款区块的区间查询', () => {
    const configs = {
      'overview-settlementDeposit': {
        day: { mode: 'OFFSET', metricCode: 'M_0298', outputColumn: '结算', unit: 'YUAN', tableName: 'ORG_INDEX_RESULT' }
      },
      'overview-deposit': {
        month: { mode: 'METRIC', metricCode: 'M_0266', outputColumn: '差额', unit: 'YUAN', tableName: 'ORG_INDEX_RESULT' }
      }
    };
    const indexed = indexPeriodComparisonSources({
      configs,
      slots: { deposit: 41, loan: 42 },
      components: [],
      blockResults: {
        41: { rows: [{ data_date: '2026-10-15', 差额: 3 }] }
      },
      rangeResults: {}
    });
    expect(indexed.rangeBlockIds).toEqual([41, 42]);
    expect(indexed.sources['overview-deposit']).toEqual([{ data_date: '2026-10-15', 差额: 3 }]);
    expect(indexed.sources['overview-settlementDeposit']).toEqual([{ data_date: '2026-10-15', 差额: 3 }]);
  });
});
