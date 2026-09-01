import { beforeEach, describe, expect, it, vi } from 'vitest';

const call = vi.hoisted(() => vi.fn());
const unwrapPage = vi.hoisted(() => vi.fn(value => value));

vi.mock('../http', () => ({ call, unwrapPage }));

describe('中台支持 API', () => {
  beforeEach(() => {
    call.mockReset();
    unwrapPage.mockImplementation(value => value);
  });

  it('发起侧列表使用正式 support-requests 路由并保留分页参数', async () => {
    call.mockResolvedValue({ records: [{ id: 'SR-1' }], total: 1 });
    const { listSupportRequests } = await import('../supportRequests');

    await expect(listSupportRequests({ keyword: '浦发', pageNo: 2, pageSize: 20 }))
      .resolves.toEqual({ records: [{ id: 'SR-1' }], total: 1 });
    expect(call).toHaveBeenCalledWith('get', '/support-requests', {
      params: { keyword: '浦发', pageNo: 2, pageSize: 20 }
    });
  });

  it('创建、提交和撤回均调用真实写接口，写失败不使用 fallback', async () => {
    call.mockResolvedValue({ id: 'SR-1' });
    const {
      createSupportRequest, submitSupportRequest, withdrawSupportRequest
    } = await import('../supportRequests');

    await createSupportRequest({
      sourceType: 'TOUCH_TASK', sourceTouchTaskId: 'TT-1', custId: 'C-1',
      productIds: ['P-1'], otherDemand: '', supportDeptId: '', confirmParallel: true,
      attachmentIds: ['F-1']
    });
    await submitSupportRequest('SR-1');
    await withdrawSupportRequest('SR-1', '客户暂缓');

    expect(call).toHaveBeenNthCalledWith(1, 'post', '/support-requests', {
      data: {
        sourceType: 'TOUCH_TASK', sourceTouchTaskId: 'TT-1', custId: 'C-1',
        productIds: ['P-1'], otherDemand: '', supportDeptId: '', confirmParallel: true,
        attachmentIds: ['F-1']
      }
    });
    expect(call).toHaveBeenNthCalledWith(2, 'post', '/support-requests/SR-1/submit', { data: {} });
    expect(call).toHaveBeenNthCalledWith(3, 'post', '/support-requests/SR-1/cancel', {
      data: { reason: '客户暂缓' }
    });
    expect(call.mock.calls.every(args => args.length < 4)).toBe(true);
  });

  it('承接侧提供详情、过程日志、新增日志、派单和完成接口', async () => {
    call.mockResolvedValue({ ok: true });
    const {
      getSupportDeptRequest, listSupportDeptLogs, addSupportDeptLog,
      dispatchSupportRequest, completeSupportRequest
    } = await import('../supportRequests');

    await getSupportDeptRequest('SR-2');
    await listSupportDeptLogs('SR-2');
    await addSupportDeptLog('SR-2', {
      content: '已完成方案沟通', operatorLocation: '西安', checkInTime: '2026-09-01T10:00:00',
      photoUrls: ['/api/files/F-1/download']
    });
    await dispatchSupportRequest('SR-2', { assignedEmpId: 'E-1', dispatchRemark: '请优先处理' });
    await completeSupportRequest('SR-2', {
      result: 'SUCCESS', summary: '已完成支持', outputAttachmentIds: ['F-2']
    });

    expect(call).toHaveBeenNthCalledWith(1, 'get', '/support-dept/requests/SR-2');
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/support-dept/requests/SR-2/logs');
    expect(call).toHaveBeenNthCalledWith(3, 'post', '/support-dept/requests/SR-2/logs', {
      data: {
        content: '已完成方案沟通', operatorLocation: '西安', checkInTime: '2026-09-01T10:00:00',
        photoUrls: ['/api/files/F-1/download']
      }
    });
    expect(call).toHaveBeenNthCalledWith(4, 'post', '/support-dept/requests/SR-2/dispatch', {
      data: { assignedEmpId: 'E-1', dispatchRemark: '请优先处理' }
    });
    expect(call).toHaveBeenNthCalledWith(5, 'post', '/support-dept/requests/SR-2/complete', {
      data: { result: 'SUCCESS', summary: '已完成支持', outputAttachmentIds: ['F-2'] }
    });
  });

  it('客户、产品和照片分别走需求指定的 API 资源', async () => {
    call.mockResolvedValue([]);
    const {
      listSupportCustomers, listSupportProducts, uploadSupportPhoto
    } = await import('../supportRequests');

    await listSupportCustomers({ keyword: '测试' });
    await listSupportProducts({ custId: 'C-1' });
    const file = new File(['photo'], '现场.jpg', { type: 'image/jpeg' });
    await uploadSupportPhoto(file);

    expect(call).toHaveBeenNthCalledWith(1, 'get', '/marketing/customers', {
      params: { keyword: '测试' }
    });
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/support-requests/available-products', {
      params: { custId: 'C-1' }
    });
    expect(call).toHaveBeenNthCalledWith(3, 'post', '/files/upload', expect.objectContaining({
      data: expect.any(FormData), headers: { 'Content-Type': 'multipart/form-data' }
    }));
  });
});
