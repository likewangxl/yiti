import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

const claimedPoolSource = readFileSync(new URL('../ClaimedPool.vue', import.meta.url), 'utf8');
const overviewSource = readFileSync(new URL('../TouchOverview.vue', import.meta.url), 'utf8');

describe('触达管理页面缺口契约', () => {
  it('已认领池的已完成任务可补录日志，并保留再次触达入口', () => {
    expect(claimedPoolSource).toContain('loadError');
    expect(claimedPoolSource).toContain('role="alert"');
    expect(claimedPoolSource).toContain('补录日志');
    expect(claimedPoolSource).toContain('再次触达');
    expect(claimedPoolSource).toContain("@click=\"viewTask(row, 'supplement')\"");
    expect(claimedPoolSource).toContain("['SUCCESS', 'COMPLETED'].includes(taskStatus(row))");
  });

  it('触达任务一览允许仅选择进行中任务后批量改派，并要求填写原因', () => {
    expect(overviewSource).toContain('type="selection"');
    expect(overviewSource).toContain(':selectable="isSelectable"');
    expect(overviewSource).toContain('批量改派');
    expect(overviewSource).toContain('newAssigneeEmpId');
    expect(overviewSource).toContain('batchAssignDlg.reason');
    expect(overviewSource).toContain('请填写改派原因');
    expect(overviewSource).toContain('batchAssignTouchTasks');
    expect(overviewSource).toContain("['PENDING', 'IN_PROGRESS'].includes");
  });
});
