import { describe, expect, it } from 'vitest';
import {
  RUNTIME_STATUS,
  buildRuntimePresentation,
  buildRuntimeSlotState
} from '../runtime/runtimeState';

describe('新展示协议运行时状态', () => {
  it('统一未配置、缺数、目标不适用、过期、错误和权限失效状态，合法零值仍为可用', () => {
    const slots = buildRuntimePresentation({
      enabled: true,
      configuredSlots: ['deposit', 'loan', 'rate', 'revenue', 'customers'],
      runtimeIssues: {
        loan: [{ code: 'NO_VALUES', field: 'value', message: '贷款当前无有效值' }],
        rate: [{ code: 'NOT_APPLICABLE', message: '当前屏不适用目标完成率' }],
        revenue: [{ code: 'REQUEST_FAILED', message: '收入来源失败' }]
      },
      sourceQualities: {
        deposit: { batchId: 'B-1', dataDate: '2026-09-20', status: 'STALE', dataClassification: 'TEST' }
      },
      sourceDates: { customers: '2026-09-19' },
      valuePresence: { deposit: true, customers: false },
      permissionStatus: 403,
      error: '无权访问收入来源'
    });

    expect(slots.status).toBe(RUNTIME_STATUS.PERMISSION_DENIED);
    expect(slots.slots.deposit).toMatchObject({ status: RUNTIME_STATUS.PERMISSION_DENIED });

    const noPermission = buildRuntimePresentation({
      enabled: true,
      configuredSlots: ['deposit', 'loan', 'rate', 'revenue', 'customers'],
      runtimeIssues: {
        loan: [{ code: 'NO_VALUES', field: 'value', message: '贷款当前无有效值' }],
        rate: [{ code: 'NOT_APPLICABLE', message: '当前屏不适用目标完成率' }],
        revenue: [{ code: 'REQUEST_FAILED', message: '收入来源失败' }]
      },
      sourceQualities: {
        deposit: { batchId: 'B-1', dataDate: '2026-09-20', status: 'STALE', dataClassification: 'TEST' }
      },
      sourceDates: { customers: '2026-09-19' },
      valuePresence: { deposit: true, customers: false }
    });

    expect(noPermission.slots.deposit).toMatchObject({ status: RUNTIME_STATUS.STALE, dataDate: '2026-09-20' });
    expect(noPermission.slots.loan).toMatchObject({ status: RUNTIME_STATUS.NO_DATA });
    expect(noPermission.slots.rate).toMatchObject({ status: RUNTIME_STATUS.NOT_APPLICABLE });
    expect(noPermission.slots.revenue).toMatchObject({ status: RUNTIME_STATUS.ERROR });
    expect(noPermission.slots.customers).toMatchObject({ status: RUNTIME_STATUS.NO_DATA, dataDate: '2026-09-19' });

    const notApplicable = buildRuntimePresentation({
      enabled: true,
      configuredSlots: ['corpTargets'],
      staticAvailability: { corpTargets: { status: 'NOT_APPLICABLE', message: '当前条线没有该目标口径' } }
    });
    expect(notApplicable.slots.corpTargets).toMatchObject({
      status: RUNTIME_STATUS.NOT_APPLICABLE,
      message: '当前条线没有该目标口径'
    });

    const zero = buildRuntimeSlotState({ configured: true, valuePresent: true, value: 0 });
    expect(zero).toMatchObject({ status: RUNTIME_STATUS.READY, value: 0 });
  });

  it('不把不同来源日期拼成一个日期，并保留批次、来源截至和刷新说明', () => {
    const result = buildRuntimePresentation({
      enabled: true,
      configuredSlots: ['deposit', 'customers'],
      sourceQualities: {
        deposit: {
          batchId: 'B-1', dataDate: '2026-09-20', status: 'COMPLETE', version: 'V1',
          sourceAsOf: { financial: '2026-09-20' }
        }
      },
      sourceDates: { customers: '2026-09-19' },
      queriedAt: '2026-09-22T09:00:00Z',
      valuePresence: { deposit: true, customers: true }
    });

    expect(result.batch).toMatchObject({ batchId: 'B-1', dataDate: '2026-09-20', status: 'COMPLETE' });
    expect(result.sourceDates).toEqual({ deposit: '2026-09-20', customers: '2026-09-19' });
    expect(result.dataDate).toBe('');
    expect(result.queriedAt).toBe('2026-09-22T09:00:00Z');
    expect(result.sourceAsOf).toEqual({ deposit: { financial: '2026-09-20' } });
  });

  it('旧完整批次在刷新失败时只作为过期旧快照保留，不能被标成新鲜成功', () => {
    const result = buildRuntimePresentation({
      enabled: true,
      configuredSlots: ['deposit'],
      quality: { batchId: 'B-OLD', dataDate: '2026-09-18', status: 'COMPLETE', dataClassification: 'TEST' },
      qualityGuard: { code: 'REFRESH_FAILED', status: 'STALE', message: '刷新失败，保留上一完整批次' },
      runtimeIssues: { batch: [{ code: 'REQUEST_FAILED', message: '刷新失败，保留上一完整批次' }] },
      sourceQualities: { deposit: { batchId: 'B-OLD', dataDate: '2026-09-18', status: 'COMPLETE' } },
      valuePresence: { deposit: true },
      queriedAt: '2026-09-22T09:00:00Z'
    });

    expect(result.status).toBe(RUNTIME_STATUS.STALE);
    expect(result.batch).toMatchObject({ batchId: 'B-OLD', dataDate: '2026-09-18', status: 'STALE' });
    expect(result.message).toContain('刷新失败');
    expect(result.slots.deposit.status).toBe(RUNTIME_STATUS.STALE);
  });
});
