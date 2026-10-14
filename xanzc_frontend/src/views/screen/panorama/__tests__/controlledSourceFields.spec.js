import { describe, expect, it } from 'vitest';
import {
  M98_PROFILE_DEFINITIONS,
  getControlledSourceFieldOptions,
  isStrictM98Source
} from '../controlledSourceFields';

function m98Source(overrides = {}) {
  return {
    id: 9801,
    status: 'ACTIVE',
    bizLine: 'CORP',
    sourceKind: 'M98_STAT',
    dsType: 'SINGLE',
    configJson: JSON.stringify({
      schemaVersion: 2,
      scopeMode: 'NAMED_GROUP',
      profile: 'CORP_REVENUE',
      mode: 'SUMMARY'
    }),
    ...overrides
  };
}

describe('controlledSourceFields', () => {
  it('唯一声明 M98 profile、固定槽位、输出字段和无猜测原始单位', () => {
    expect(M98_PROFILE_DEFINITIONS.CORP_REVENUE).toMatchObject({
      slot: 'corpRevenue',
      field: 'amount',
      semantic: 'corpRevenue',
      unit: null
    });
    expect(isStrictM98Source(m98Source())).toBe(true);
    expect(getControlledSourceFieldOptions(m98Source())).toEqual([
      expect.objectContaining({ col: 'data_date', role: 'DIM', fixed: true }),
      expect.objectContaining({
        col: 'amount', role: 'METRIC', profile: 'CORP_REVENUE', fixedSlot: 'corpRevenue',
        semantic: 'corpRevenue', unit: null, fixed: true
      })
    ]);
  });

  it('M98 profile 错误或增加配置键时 fail-close，不生成固定指标字段', () => {
    expect(isStrictM98Source(m98Source({
      configJson: JSON.stringify({
        schemaVersion: 2, scopeMode: 'NAMED_GROUP', profile: 'UNKNOWN', mode: 'SUMMARY'
      })
    }))).toBe(false);
    expect(getControlledSourceFieldOptions(m98Source({
      configJson: JSON.stringify({
        schemaVersion: 2, scopeMode: 'NAMED_GROUP', profile: 'CORP_REVENUE', mode: 'SUMMARY', extra: true
      })
    }))).toEqual([]);
  });

  it('FREE_REPORT 只返回专用模板说明，不返回可通用选择的 METRIC', () => {
    const options = getControlledSourceFieldOptions({
      sourceKind: 'FREE_REPORT',
      dsType: 'SINGLE',
      configJson: JSON.stringify({
        schemaVersion: 2,
        scopeMode: 'NAMED_GROUP',
        dataClassification: 'TEST',
        profile: 'BRANCH_OVERVIEW',
        batchId: 'TEST_BRANCH_OPERATING_20260921'
      })
    });

    expect(options).toEqual([expect.objectContaining({
      kind: 'DATASET_NOTICE',
      role: 'NOTICE',
      disabled: true,
      label: '专用经营数据集'
    })]);
    expect(options.some(option => option.role === 'METRIC')).toBe(false);
  });
});
