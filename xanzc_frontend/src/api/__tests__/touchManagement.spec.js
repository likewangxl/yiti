import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../http', () => ({ call: vi.fn().mockResolvedValue({ records: [], total: 0 }) }));

import { call } from '../http';
import { batchAssignTouchTasks } from '../customerMarketing';

describe('触达任务管理 API', () => {
  beforeEach(() => call.mockClear());

  it('批量改派必须提交任务、新办理人员工号和改派原因', async () => {
    await batchAssignTouchTasks(['TASK-1', 'TASK-2'], 'E-200', '原执行人岗位调整');

    expect(call).toHaveBeenCalledWith('post', '/admin/touch-tasks/batch-assign', {
      data: {
        taskIds: ['TASK-1', 'TASK-2'],
        newAssigneeEmpId: 'E-200',
        reason: '原执行人岗位调整'
      }
    });
  });
});
