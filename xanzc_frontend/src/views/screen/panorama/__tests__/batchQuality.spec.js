import { describe, expect, it } from 'vitest';
import {
  BATCH_QUALITY_STATUSES,
  compareBatchQuality,
  isUsableBatchQuality,
  mergeBatchQualities,
  normalizeBatchQuality
} from '../batchQuality';

const complete = (overrides = {}) => ({
  batchId: 'opaque-batch-1',
  dataDate: '2026-09-10',
  version: 'V7',
  dataClassification: 'TEST',
  status: 'COMPLETE',
  calculatedAt: '2026-09-11T01:02:03Z',
  sourceAsOf: { financial: '2026-09-10', marketing: '2026-09-11' },
  expected: 14,
  received: 14,
  expectedSubjects: 4,
  receivedSubjects: 4,
  missingSubjects: [],
  missing: [],
  mixedPeriod: false,
  selectedComplete: true,
  newerIncomplete: [],
  ...overrides
});

describe('batchQuality', () => {
  it('按冻结 Quality 契约归一化 camel/snake 字段，并保留来源截至时间和缺失明细', () => {
    expect(normalizeBatchQuality({
      batch_id: 'opaque-batch-1', data_date: '2026-09-10', version: 'V7',
      data_classification: 'test',
      status: 'STALE', calculated_at: '2026-09-11T01:02:03Z',
      source_as_of: { financial: '2026-09-10' }, expected_subjects: 4,
      received_subjects: 3, missing_subjects: ['B'], missing: ['customers'],
      mixed_period: true, selected_complete: true, newer_incomplete: ['V8'],
      history_coverage: [{
        data_date: '2026-09-09', expected: 8, received: 7,
        expected_subjects: 4, received_subjects: 3, complete: false,
        missing_subjects: ['B'], missing: ['M_0309']
      }]
    })).toMatchObject({
      batchId: 'opaque-batch-1', dataDate: '2026-09-10', status: 'STALE',
      dataClassification: 'TEST',
      calculatedAt: '2026-09-11T01:02:03Z', sourceAsOf: { financial: '2026-09-10' },
      expectedSubjects: 4, receivedSubjects: 3, missingSubjects: ['B'],
      missing: ['customers'], mixedPeriod: true, selectedComplete: true,
      newerIncomplete: ['V8'],
      historyCoverage: [{
        dataDate: '2026-09-09', expected: 8, received: 7,
        expectedSubjects: 4, receivedSubjects: 3, complete: false,
        missingSubjects: ['B'], missing: ['M_0309']
      }]
    });
  });

  it('只把 COMPLETE/STALE 且有不透明 batchId 的响应视为可用批次', () => {
    expect(BATCH_QUALITY_STATUSES).toEqual(['COMPLETE', 'STALE', 'NO_COMPLETE_BATCH', 'PARTIAL']);
    expect(isUsableBatchQuality(complete())).toBe(true);
    expect(isUsableBatchQuality(complete({ status: 'STALE' }))).toBe(true);
    expect(isUsableBatchQuality(complete({ status: 'PARTIAL' }))).toBe(false);
    expect(isUsableBatchQuality(complete({ batchId: null }))).toBe(false);
    expect(isUsableBatchQuality(complete({ selectedComplete: false }))).toBe(false);
    expect(isUsableBatchQuality(complete({ dataClassification: 'UNKNOWN' }))).toBe(false);
    expect(isUsableBatchQuality(complete({ dataClassification: 'FAKE' }))).toBe(false);
  });

  it('拒绝批次身份、日期或版本不一致，缺少质量也拒绝', () => {
    const anchor = complete();
    expect(compareBatchQuality(anchor, complete())).toMatchObject({ ok: true });
    expect(compareBatchQuality(anchor, complete({ batchId: 'opaque-batch-2' }))).toMatchObject({
      ok: false, code: 'BATCH_MISMATCH'
    });
    expect(compareBatchQuality(anchor, complete({ dataDate: '2026-09-09' }))).toMatchObject({
      ok: false, code: 'BATCH_MISMATCH'
    });
    expect(compareBatchQuality(anchor, complete({ version: 'V8' }))).toMatchObject({
      ok: false, code: 'BATCH_MISMATCH'
    });
    expect(compareBatchQuality(anchor, complete({ dataClassification: 'PROD' }))).toMatchObject({
      ok: false, code: 'DATA_CLASSIFICATION_MISMATCH'
    });
    expect(compareBatchQuality(anchor, null)).toMatchObject({ ok: false, code: 'QUALITY_MISSING' });
  });

  it('合并同批 Quality 时保留来源截至时间、混期和全部缺失明细，STALE 显式升级展示状态', () => {
    const merged = mergeBatchQualities([
      complete(),
      complete({
        status: 'STALE', sourceAsOf: { target: '2026-09-09' }, missing: ['revenue'], mixedPeriod: true,
        historyCoverage: [{
          dataDate: '2026-09-09', expected: 8, received: 7,
          expectedSubjects: 4, receivedSubjects: 3, complete: false,
          missingSubjects: ['B'], missing: ['M_0309']
        }]
      })
    ]);
    expect(merged).toMatchObject({
      batchId: 'opaque-batch-1', status: 'STALE', mixedPeriod: true,
      sourceAsOf: { financial: '2026-09-10', marketing: '2026-09-11', target: '2026-09-09' },
      missing: ['revenue'],
      dataClassification: 'TEST',
      historyCoverage: [{
        dataDate: '2026-09-09', expected: 8, received: 7,
        expectedSubjects: 4, receivedSubjects: 3, complete: false,
        missingSubjects: ['B'], missing: ['M_0309']
      }]
    });
  });
});
