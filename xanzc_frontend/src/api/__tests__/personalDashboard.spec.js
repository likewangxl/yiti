import { beforeEach, describe, expect, it, vi } from 'vitest';

const http = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock('../http', () => ({
  API_BASE: '/api',
  default: http
}));

import {
  buildPersonalDashboardModel,
  loadPersonalDashboard
} from '../personalDashboard';

const page = (records, total) => ({ records, ...(total === undefined ? {} : { total }) });

beforeEach(() => {
  http.get.mockReset();
});

describe('personalDashboard API 聚合', () => {
  it('并行请求所有个人来源，使用严格真实 HTTP 与固定分页，不传 empId', async () => {
    http.get.mockImplementation((url) => {
      if (url.endsWith('/portal/workspace')) return Promise.resolve({ metricCards: [] });
      if (url.endsWith('/workflow/tasks')) return Promise.resolve(page([], 0));
      if (url.endsWith('/touch-tasks')) return Promise.resolve(page([], 0));
      if (url.endsWith('/marketing/customers/mine')) return Promise.resolve(page([], 0));
      if (url.endsWith('/marketing/asset-projects')) return Promise.resolve(page([], 0));
      if (url.endsWith('/support-requests')) return Promise.resolve(page([], 0));
      return Promise.reject(new Error(`unexpected ${url}`));
    });

    const result = await loadPersonalDashboard();

    expect(http.get).toHaveBeenCalledTimes(7);
    expect(http.get).toHaveBeenCalledWith('/api/portal/workspace', { silent: true });
    expect(http.get).toHaveBeenCalledWith('/api/workflow/tasks', {
      params: { pageNo: 1, pageSize: 20 }, silent: true
    });
    expect(http.get).toHaveBeenCalledWith('/api/touch-tasks', {
      params: { status: 'PENDING', pageNo: 1, pageSize: 10 }, silent: true
    });
    expect(http.get).toHaveBeenCalledWith('/api/touch-tasks', {
      params: { status: 'IN_PROGRESS', pageNo: 1, pageSize: 10 }, silent: true
    });
    expect(http.get).toHaveBeenCalledWith('/api/marketing/customers/mine', {
      params: { pageSize: 6 }, silent: true
    });
    expect(http.get).toHaveBeenCalledWith('/api/marketing/asset-projects', {
      params: { tab: 'MY', pageSize: 6 }, silent: true
    });
    expect(http.get).toHaveBeenCalledWith('/api/support-requests', {
      params: { onlyMine: true, pageSize: 6 }, silent: true
    });
    expect(http.get.mock.calls.flat().some(value => String(value).includes('empId'))).toBe(false);
    expect(result.workspace.status).toBe('fulfilled');
    expect(result.touchPending.status).toBe('fulfilled');
    expect(result.touchInProgress.status).toBe('fulfilled');
  });

  it('工作台指标严格映射字段与 metricCards 错误，保留 0 与 null', () => {
    const model = buildPersonalDashboardModel({
      workspace: {
        status: 'fulfilled',
        value: {
          metricCards: [{
            metricCode: 'DEPOSIT', metricName: '存款余额', currentValue: 0,
            targetValue: null, completionRate: 88.5, unit: '元',
            dataTime: '2026-09-20T00:00:00', changeRate: 6.7
          }],
          aggregateErrors: {
            metricCards: '指标部分不可用',
            unknownSource: '不能归入指标'
          }
        }
      },
      todos: { status: 'fulfilled', value: page([], 0) },
      touchPending: { status: 'fulfilled', value: page([], 0) },
      touchInProgress: { status: 'fulfilled', value: page([], 0) },
      customers: { status: 'fulfilled', value: page([], 0) },
      assets: { status: 'fulfilled', value: page([], 0) },
      supports: { status: 'fulfilled', value: page([], 0) }
    }, { name: '李经理', orgName: '西安支行' });

    expect(model.identity).toEqual({ name: '李经理', orgName: '西安支行' });
    expect(model.metrics.items[0]).toMatchObject({
      metricCode: 'DEPOSIT', currentValue: 0, targetValue: null,
      achievementRate: 88.5, dataDate: '2026-09-20T00:00:00', mom: 6.7
    });
    expect(model.metrics.status).toBe('ready');
    expect(model.metrics.message).toContain('指标部分不可用');
    expect(model.metrics.message).not.toContain('不能归入指标');
  });

  it('分区失败独立保留成功项，触达按 canOperateTask/canWriteLog 判定动作且不合并 total', () => {
    const model = buildPersonalDashboardModel({
      workspace: { status: 'rejected', reason: Object.assign(new Error('workspace down'), { response: { status: 503 } }) },
      todos: { status: 'fulfilled', value: page([{ id: 'W-1', title: '审批待办', slaStatus: 'RED' }], 4) },
      touchPending: { status: 'fulfilled', value: page([
        { id: 'T-1', title: '触达待处理', canOperateTask: false, canWriteLog: true, slaStatus: 'BLUE' },
        { id: 'T-2', title: '仅查看', canOperateTask: false, canWriteLog: false }
      ], 8) },
      touchInProgress: { status: 'rejected', reason: Object.assign(new Error('touch unavailable'), { response: { status: 403 } }) },
      customers: { status: 'fulfilled', value: page([{ id: 'C-1', name: '客户甲' }], 9) },
      assets: { status: 'fulfilled', value: page([], 0) },
      supports: { status: 'fulfilled', value: page([], 0) }
    });

    expect(model.metrics.status).toBe('error');
    expect(model.priorities.status).toBe('ready');
    expect(model.priorities.items.map(item => item.id)).toEqual(['W-1', 'T-1', 'T-2']);
    expect(model.priorities.items.find(item => item.id === 'T-1').actionLabel).toBe('补录日志');
    expect(model.priorities.items.find(item => item.id === 'T-1').target.mode).toBe('supplement');
    expect(model.priorities.items.find(item => item.id === 'T-2').actionLabel).toBe('查看');
    expect(model.priorities.sourceTotals).toEqual({ workflow: 4, touchPending: 8, touchInProgress: null });
    expect(model.priorities.total).toBeNull();
    expect(model.priorities.message).toContain('工作流待办 4 条');
    expect(model.priorities.message).toContain('触达待处理 8 条');
    expect(model.priorities.message).toContain('触达进行中不可用');
    expect(model.customers.total).toBe(9);
  });

  it('业务进度保留资产/支持独立 total，按时间归并并明确支持创建时间和草稿状态', () => {
    const model = buildPersonalDashboardModel({
      workspace: { status: 'fulfilled', value: { metricCards: [] } },
      todos: { status: 'fulfilled', value: page([], 0) },
      touchPending: { status: 'fulfilled', value: page([], 0) },
      touchInProgress: { status: 'fulfilled', value: page([], 0) },
      customers: { status: 'fulfilled', value: page([], 0) },
      assets: { status: 'fulfilled', value: page([
        { id: 'A-1', projectName: '资产草稿', status: 'DRAFT', createdTime: '2026-09-18T10:00:00' },
        { id: 'A-2', projectName: '资产审批', status: 'IN_APPROVAL', submittedTime: '2026-09-19T10:00:00', createdTime: '2026-09-18T10:00:00' }
      ], 6) },
      supports: { status: 'fulfilled', value: page([
        { id: 'S-1', title: '中台支持', status: 'IN_PROGRESS', createdTime: '2026-09-20T10:00:00' }
      ], 3) }
    });

    expect(model.progress.items.map(item => item.id)).toEqual(['S-1', 'A-2', 'A-1']);
    expect(model.progress.items.find(item => item.id === 'S-1')).toMatchObject({
      statusLabel: '办理中', submittedTime: '2026-09-20T10:00:00', timeLabel: '创建时间'
    });
    expect(model.progress.items.find(item => item.id === 'A-1')).toMatchObject({
      statusLabel: '草稿', timeLabel: '创建时间'
    });
    expect(model.progress.sourceTotals).toEqual({ asset: 6, support: 3 });
    expect(model.progress.total).toBeNull();
    expect(model.progress.message).toContain('资产立项 6 条');
    expect(model.progress.message).toContain('中台支持 3 条');
  });
});
