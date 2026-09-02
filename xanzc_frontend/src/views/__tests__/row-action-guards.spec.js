import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const root = new URL('../../views/', import.meta.url);
const sourceOf = (path) => readFileSync(new URL(path, root), 'utf8');

// 该名单锁定本轮收纳进受控 teleported menu 的所有普通后台页面/弹窗。
// 每个菜单至少保留一个真实事件入口，条件菜单必须把可见性提升到“更多”入口，避免空菜单。
const menuSources = [
  'info/ProductLib.vue', 'info/DocCenter.vue',
  'perf/Metrics.vue', 'perf/KpiRules.vue', 'perf/Targets.vue', 'perf/TargetValues.vue',
  'perf/Import.vue', 'perf/Adjust.vue', 'perf/TaskMonitor.vue',
  'eval/Tags.vue', 'eval/Rules.vue', 'eval/Tasks.vue',
  'guarantee/DataImport.vue', 'report/FreeReport.vue', 'report/components/SchemeListDialog.vue',
  'screen/admin/Datasources.vue',
  'system/Users.vue', 'system/Roles.vue', 'system/Resources.vue', 'system/Dict.vue',
  'system/Jobs.vue', 'system/Notifications.vue', 'system/Files.vue', 'system/FlowList.vue',
  'system/WorkflowMonitor.vue', 'system/PersonTags.vue', 'workspace/Index.vue'
];

function dropdownBlocks(source) {
  return [...source.matchAll(/<el-dropdown\b(?=[^>]*\bpopper-class="bp-crud-menu")[\s\S]*?<\/el-dropdown>/g)]
    .map(([block]) => block);
}

describe('行级更多菜单可见性与原始交互门禁', () => {
  it('全量受控更多菜单均有菜单项和真实事件，不允许只渲染空壳', () => {
    const missing = menuSources.flatMap((path) => {
      const blocks = dropdownBlocks(sourceOf(path));
      return blocks
        .map((block, index) => ({ block, index }))
        .filter(({ block }) => !/<el-dropdown-item\b/.test(block) || !/@(?:click|command)(?:\.[\w-]+)*=/.test(block))
        .map(({ index }) => `${path} 第${index + 1}个更多菜单`);
    });

    expect(missing).toEqual([]);
  });

  it('仅包含条件操作的更多入口与其可见性条件同级，避免空菜单', () => {
    expect(sourceOf('perf/KpiRules.vue')).toMatch(/<el-dropdown\s+v-if="isCaizai"[^>]*popper-class="bp-crud-menu"/);
    expect(sourceOf('perf/Targets.vue')).toMatch(/<el-dropdown\s+v-if="row\.createdBy === userStore\.user\?\.empId"[^>]*popper-class="bp-crud-menu"/);
    expect(sourceOf('perf/Adjust.vue')).toMatch(/<el-dropdown\s+v-if="row\.status === 'DRAFT' \|\| canWithdraw\(row\.status\)"[^>]*popper-class="bp-crud-menu"/);
    expect(sourceOf('system/Notifications.vue')).toMatch(/<el-dropdown\s+v-if="!row\.isRead \|\| row\.linkUrl \|\| row\.bizId"[^>]*popper-class="bp-crud-menu"/);

    const workspace = sourceOf('workspace/Index.vue');
    expect(workspace).toMatch(/<el-dropdown\s+v-if="row\.status === 'PENDING_ACCEPT'"[^>]*>[\s\S]*?aria-label="更多转交操作"/);
    expect(workspace).toMatch(/<el-dropdown\s+v-if="row\.status === 'PENDING_ACCEPT'"[^>]*>[\s\S]*?aria-label="更多转出记录操作"/);
  });

  it('撤回使用单个 prompt 同时确认并收集原因，菜单事件仍经 confirmWithdraw', () => {
    const source = sourceOf('perf/Adjust.vue');
    expect(source).toMatch(/<el-dropdown-item\b(?=[^>]*class="danger-item")(?=[^>]*@click="confirmWithdraw\(row\)")[^>]*>撤回<\/el-dropdown-item>/);
    const confirmBlock = source.match(/async function confirmWithdraw\(row\)\s*\{[\s\S]*?\n\}/)?.[0] || '';
    expect(confirmBlock).not.toContain('ElMessageBox.confirm');
    expect(confirmBlock).toContain('await onWithdraw(row);');
    const withdrawBlock = source.match(/async function onWithdraw\(row\)[\s\S]*?\n\}\n\nasync function confirmWithdraw/)?.[0] || '';
    expect(withdrawBlock).toContain('ElMessageBox.prompt');
    expect(withdrawBlock).toContain('确认撤回申请 ${row.applyNo || row.id}？\\n请填写撤回原因');
    expect(withdrawBlock).toMatch(/inputPattern:\s*\/\\S\+\//);
    expect(withdrawBlock).toContain("inputErrorMessage: '撤回原因必填'");
    expect(withdrawBlock).toMatch(/await withdrawAdjust\(row\.id \|\| row\.applyNo, reason\)/);
    expect(source).not.toMatch(/<el-dropdown-item\b[^>]*@click="onWithdraw\(row\)"/);
  });

  it('不同异步操作仅禁用自身；更多入口不扩大禁用范围', () => {
    const flow = sourceOf('system/FlowList.vue');
    expect(flow).not.toContain(':disabled="isPublishing(row.id) || isCloning(row.id) || isDeleting(row.id)"');
    expect(flow).toMatch(/<el-dropdown-item\b[^>]*:disabled="isPublishing\(row\.id\)"[^>]*@click="doPublish\(row\)"/);
    expect(flow).toMatch(/<el-dropdown-item\b[^>]*:disabled="isCloning\(row\.id\)"[^>]*@click="doClone\(row\)"/);
    expect(flow).toMatch(/<el-dropdown-item\b[^>]*:disabled="isDeleting\(row\.id\)"[^>]*@click="doDelete\(row\)"/);
    expect(flow).toContain("isDeleting(row.id) ? '删除中…' : '删除'");
    expect(flow).toMatch(/if \(!row\?\.id \|\| isPublishing\(row\.id\)\) return;/);
    expect(flow).toMatch(/if \(!row\?\.id \|\| isCloning\(row\.id\)\) return;/);
    expect(flow).toMatch(/if \(!row\?\.id \|\| isDeleting\(row\.id\)\) return;/);

    const jobs = sourceOf('system/Jobs.vue');
    expect(jobs).not.toMatch(/aria-label="更多调度任务操作"[^>]*:disabled="isJobPending/);
    expect(jobs).toMatch(/v-if="row\.allowManualTrigger"\s+divided\s+class="warning-item"\s+@click="onTrigger\(row\)"/);
    expect(jobs).not.toMatch(/v-if="row\.allowManualTrigger"[^>]*:disabled="isJobPending/);

    const resources = sourceOf('system/Resources.vue');
    expect(resources).not.toMatch(/aria-label="更多菜单操作"[^>]*:disabled=/);
    expect(resources).toMatch(/<el-dropdown-item\b[^>]*@click\.stop="openCreate\(row\)"/);
    expect(resources).toMatch(/<el-dropdown-item\b[^>]*v-if="row\.menuEndFlag === '1'"[^>]*@click\.stop="openAssign\(row\)"/);
    expect(resources).toMatch(/<el-dropdown-item\b[^>]*:disabled="isDeleting\(row\.resourceId\)"[^>]*@click\.stop="confirmDelete\(row\)"/);

    const personTags = sourceOf('system/PersonTags.vue');
    expect(personTags).not.toMatch(/aria-label="更多人员标签操作"[^>]*:disabled=/);
    expect(personTags).toMatch(/<el-dropdown-item\b[^>]*@click="openEdit\(row\)"/);
    expect(personTags).toMatch(/<el-dropdown-item\b[^>]*:disabled="isDeletingTag\(row\.tagId\)"[^>]*@click="onDeleteTag\(row\)"/);
    expect(personTags).toContain("isDeletingTag(row.tagId) ? '删除中…' : '删除'");
  });
});
