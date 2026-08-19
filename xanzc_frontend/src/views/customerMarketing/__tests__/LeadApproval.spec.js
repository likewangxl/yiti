// @vitest-environment node
import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const source = readFileSync(
  fileURLToPath(new URL('../LeadApproval.vue', import.meta.url)),
  'utf8',
);

describe('线索审批免领取契约', () => {
  it('页面不展示领取按钮，也不在通过或驳回前自动领取', () => {
    expect(source).not.toContain('>领取</el-button>');
    expect(source).not.toContain('claimTask');
    expect(source).not.toContain('ensureClaimed');
    expect(source).not.toContain('row.claimable');
    expect(source).toContain('await approveTask(row.taskId');
    expect(source).toContain('await rejectTask(row.taskId');
  });
});
