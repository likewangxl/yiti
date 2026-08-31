import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/api/http', () => ({
  call: vi.fn()
}));

import { call } from '@/api/http';
import {
  createTask,
  createTaskExport,
  downloadTaskAttachment,
  downloadTaskExport,
  getTaskDetail,
  getTaskExportStatus,
  listMaterialDetailItems,
  listTaskAssignments,
  listTaskFileTypes,
  listTaskTypes,
  listTasks
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

    expect(call).toHaveBeenCalledWith('get', '/re/tasks', { params });
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

  it('任务字典覆盖任务类型、文件类型和四维明细项', async () => {
    await listTaskTypes();
    await listTaskFileTypes();
    await listMaterialDetailItems();

    expect(call).toHaveBeenNthCalledWith(1, 'get', '/re/tasks/types', {});
    expect(call).toHaveBeenNthCalledWith(2, 'get', '/re/tasks/file-types', {});
    expect(call).toHaveBeenNthCalledWith(3, 'get', '/re/tasks/material-details', {});
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
