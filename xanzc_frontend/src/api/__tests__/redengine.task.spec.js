import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/http', () => ({
  call: vi.fn()
}));

import { call } from '@/api/http';
import * as redengineApi from '@/api/redengine';
import {
  createTask,
  createTaskExport,
  downloadTaskAttachment,
  downloadTaskExport,
  getTaskDetail,
  getTaskExportStatus,
  getMyTaskAssignment,
  listEligibleUsers,
  listMyTaskAssignments,
  listMaterialDetailItems,
  listTaskAssignments,
  listTasks,
  submitTask
} from '@/api/redengine';

describe('红色引擎任务 API', () => {
  beforeEach(() => {
    call.mockReset();
    call.mockResolvedValue({});
  });

  it('任务管理列表保留分页、查询条件并使用任务资源路径', async () => {
    const params = {
      pageNo: 2,
      pageSize: 20,
      title: '季度',
      taskNature: 'PERIODIC',
      businessType: 'FOUR_DIMENSION',
      cycleType: 'QUARTER_END',
      status: 'PUBLISHED'
    };

    await listTasks(params);

    expect(call).toHaveBeenCalledWith('get', '/re/tasks', {
      params: { ...params, taskNature: 'SCHEDULED' }
    });
  });

  it('任务管理列表将前端周期别名转换为 GET DTO 的 SCHEDULED 枚举', async () => {
    await listTasks({ taskNature: 'PERIODIC' });

    expect(call).toHaveBeenCalledWith('get', '/re/tasks', {
      params: { taskNature: 'SCHEDULED' }
    });
  });

  it('新增任务使用 POST 并原样传递任务对象和目标范围', async () => {
    const payload = {
      title: '专项整改',
      taskNature: 'TEMPORARY',
      businessType: 'GENERAL',
      targets: [
        { targetType: 'SPECIFIED_BRANCH', partyOrgId: 11 },
        { targetType: 'SPECIFIED_BRANCH', partyOrgId: 12 }
      ],
      requiresFile: true,
      fileTypeCodes: ['PDF']
    };

    await createTask(payload);

    expect(call).toHaveBeenCalledWith('post', '/re/tasks', { data: payload });
  });

  it('任务详情和支部任务实例使用独立查询', async () => {
    await getTaskDetail(42);
    await listTaskAssignments(42, { pageNo: 1, pageSize: 10, branchId: 11 });

    expect(call).toHaveBeenNthCalledWith(1, 'get', '/re/tasks/42');
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/re/tasks/42/assignments', {
      params: { pageNo: 1, pageSize: 10, branchId: 11 }
    });
  });

  it('报送员任务列表支持分页、页签和任务筛选条件', async () => {
    const params = {
      pageNo: 2,
      pageSize: 10,
      tab: 'PENDING',
      title: '整改',
      taskNature: 'TEMPORARY',
      cycleType: 'MONTH_END'
    };

    await listMyTaskAssignments(params);

    expect(call).toHaveBeenCalledWith('get', '/re/tasks/my-assignments', { params });
  });

  it('指定员工候选使用红色引擎受控分页接口', async () => {
    const params = {
      pageNo: 1,
      pageSize: 100,
      keyword: '张',
      branchId: 11
    };

    await listEligibleUsers(params);

    expect(call).toHaveBeenCalledWith('get', '/re/tasks/eligible-users', { params });
  });

  it('不再暴露已经下线的归档与旧导出接口', () => {
    expect(redengineApi.archiveSettlement).toBeUndefined();
    expect(redengineApi.generateAnnual).toBeUndefined();
    expect(redengineApi.exportData).toBeUndefined();
  });

  it('报送员任务详情和提交使用独立资源，并保留幂等号和附件对象 ID', async () => {
    await getMyTaskAssignment(1001);
    const payload = {
      assignmentId: 1001,
      content: '已完成整改，详见附件',
      fileObjectIds: ['file-1'],
      clientRequestId: 'client-1'
    };
    await submitTask(payload);

    expect(call).toHaveBeenNthCalledWith(1, 'get', '/re/tasks/assignments/1001');
    expect(call).toHaveBeenNthCalledWith(2, 'post', '/re/tasks/submissions', { data: payload });
  });

  it('四维明细通过治理中心 RE_ITEM_CODE 字典读取', async () => {
    await listMaterialDetailItems();

    expect(call).toHaveBeenCalledWith('get', '/sys/dicts/RE_ITEM_CODE/items');
  });

  it('导出使用单任务异步作业，附件和 ZIP 均按二进制读取', async () => {
    await createTaskExport(42, { itemCodes: ['JC_STANDARD'] });
    await getTaskExportStatus('export-1');
    await downloadTaskExport('export-1');
    await downloadTaskAttachment(42, 1001, 'file-1');

    expect(call).toHaveBeenNthCalledWith(1, 'post', '/re/tasks/42/exports', {
      data: { itemCodes: ['JC_STANDARD'] }
    });
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/re/task-exports/export-1');
    expect(call).toHaveBeenNthCalledWith(3, 'get', '/re/task-exports/export-1/download', {
      responseType: 'blob'
    });
    expect(call).toHaveBeenNthCalledWith(
      4,
      'get',
      '/re/tasks/42/assignments/1001/attachments/file-1/download',
      { responseType: 'blob' }
    );
  });
});
