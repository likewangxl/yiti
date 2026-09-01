import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/http', () => ({
  call: vi.fn()
}));

import { call } from '@/api/http';
import {
  approveBranchTask,
  approveOrgTask,
  downloadTaskAttachment,
  getBranchTaskReview,
  getOrgTaskReview,
  listBranchTaskReviews,
  listOrgTaskReviews,
  rejectBranchTask,
  rejectOrgTask,
  submitBranchTaskToOrg
} from '@/api/redengine';

describe('红色引擎任务审核 API', () => {
  beforeEach(() => {
    call.mockReset();
    call.mockResolvedValue({});
  });

  it('支部审核队列保留页签和任务筛选条件', async () => {
    const params = {
      pageNo: 1,
      pageSize: 20,
      tab: 'PENDING',
      taskNature: 'TEMPORARY',
      businessType: 'GENERAL',
      title: '整改'
    };

    await listBranchTaskReviews(params);

    expect(call).toHaveBeenCalledWith('get', '/re/reviews/tasks/branch/queue', { params });
  });

  it('支部任务审核详情、通过、提交组织和驳回使用 assignment 资源', async () => {
    await getBranchTaskReview(1001);
    await approveBranchTask(1001, { feedback: '材料完整' });
    await submitBranchTaskToOrg(1001, { feedback: '支部已复核' });
    await rejectBranchTask(1001, { feedback: '请补充附件' });

    expect(call).toHaveBeenNthCalledWith(1, 'get', '/re/reviews/tasks/branch/1001');
    expect(call).toHaveBeenNthCalledWith(2, 'post', '/re/reviews/tasks/branch/1001/approve', {
      data: { feedback: '材料完整' }
    });
    expect(call).toHaveBeenNthCalledWith(3, 'post', '/re/reviews/tasks/branch/1001/submit-to-org', {
      data: { feedback: '支部已复核' }
    });
    expect(call).toHaveBeenNthCalledWith(4, 'post', '/re/reviews/tasks/branch/1001/reject', {
      data: { feedback: '请补充附件' }
    });
  });

  it('组织审核队列和任务详情使用独立 role 资源', async () => {
    const params = {
      pageNo: 1,
      pageSize: 20,
      tab: 'PENDING',
      taskNature: 'TEMPORARY'
    };

    await listOrgTaskReviews(params);
    await getOrgTaskReview(1001);
    await approveOrgTask(1001, { feedback: '通过' });
    await rejectOrgTask(1001, { feedback: '退回' });

    expect(call).toHaveBeenNthCalledWith(1, 'get', '/re/reviews/tasks/org/queue', { params });
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/re/reviews/tasks/org/1001');
    expect(call).toHaveBeenNthCalledWith(3, 'post', '/re/reviews/tasks/org/1001/approve', {
      data: { feedback: '通过' }
    });
    expect(call).toHaveBeenNthCalledWith(4, 'post', '/re/reviews/tasks/org/1001/reject', {
      data: { feedback: '退回' }
    });
  });

  it('任务附件继续使用已有二进制下载封装', async () => {
    await downloadTaskAttachment(42, 1001, 'file-1');

    expect(call).toHaveBeenCalledWith(
      'get',
      '/re/tasks/42/assignments/1001/attachments/file-1/download',
      { responseType: 'blob' }
    );
  });
});
