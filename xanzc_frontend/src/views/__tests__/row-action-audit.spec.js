import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const routerSource = readFileSync(new URL('../../router/index.js', import.meta.url), 'utf8');
const routePattern = /\{\s*path:\s*'([^']+)'\s*,\s*name:\s*'([^']+)'\s*,\s*component:\s*\(\)\s*=>\s*import\('\@\/([^']+)'\)/g;
const excludedViews = new Set([
  'views/screen/ScreenView.vue',
  'views/screen/panorama/PanoramaBindings.vue'
]);
const namedRoutes = [...routerSource.matchAll(routePattern)]
  .map(([, path, name, view]) => ({ path, name, view }))
  .filter(route => !route.view.includes('/redengine/') && !excludedViews.has(route.view));

function sourceOf(view) {
  return readFileSync(new URL(`../../${view}`, import.meta.url), 'utf8');
}

function operationColumns(source) {
  return [...source.matchAll(/<el-table-column\b(?=[^>]*\blabel=(?:"操作"|'操作'))[^>]*>[\s\S]*?<\/el-table-column>/g)]
    .map(([column]) => column);
}

const physicalColumnMatrix = [
  ['views/customerMarketing/AssetProjects.vue', 1],
  ['views/customerMarketing/AvailablePool.vue', 1], ['views/customerMarketing/ClaimedPool.vue', 1],
  ['views/customerMarketing/CrossOrgMarketing.vue', 1], ['views/customerMarketing/MarketingCustomerList.vue', 1],
  ['views/customerMarketing/MyCustomers.vue', 1], ['views/customerMarketing/MarketingCustomerTags.vue', 1],
  ['views/customerMarketing/MarketingLeadApproval.vue', 1], ['views/customerMarketing/MarketingLeadEntry.vue', 2],
  ['views/customerMarketing/MarketingTagCustomerApproval.vue', 2], ['views/customerMarketing/MyTouches.vue', 1],
  ['views/customerMarketing/TouchLimitManagement.vue', 1],
  ['views/customerMarketing/TouchOverview.vue', 1],
  ['views/eval/MyTasks.vue', 2], ['views/eval/Rules.vue', 2], ['views/eval/Tags.vue', 1],
  ['views/eval/Tasks.vue', 1], ['views/eval/UserTags.vue', 1], ['views/guarantee/DataImport.vue', 1],
  ['views/guarantee/Notice.vue', 1], ['views/guarantee/Query.vue', 1], ['views/history/PriceApproval.vue', 1],
  ['views/info/AddressBook.vue', 1], ['views/info/DocCenter.vue', 1], ['views/info/ProductLib.vue', 1],
  ['views/perf/Adjust.vue', 5], ['views/perf/Import.vue', 1], ['views/perf/KpiRules.vue', 2],
  ['views/perf/Metrics.vue', 1], ['views/perf/TargetValues.vue', 1], ['views/perf/Targets.vue', 3],
  ['views/perf/TaskMonitor.vue', 1], ['views/report/AmasApprovals.vue', 2], ['views/report/FreeReport.vue', 1],
  ['views/report/Sql.vue', 1], ['views/report/components/SchemeListDialog.vue', 1],
  ['views/screen/admin/Datasources.vue', 1], ['views/screen/admin/OrgProfiles.vue', 1],
  ['views/system/Announcements.vue', 1], ['views/system/Audit.vue', 1], ['views/system/Config.vue', 3],
  ['views/system/Dict.vue', 1], ['views/system/Files.vue', 1], ['views/system/FlowList.vue', 1],
  ['views/system/Jobs.vue', 1], ['views/system/Notifications.vue', 1], ['views/system/Permission.vue', 1],
  ['views/system/PersonTags.vue', 3], ['views/system/Resources.vue', 1], ['views/system/Roles.vue', 1],
  ['views/system/TimeoutRules.vue', 1], ['views/system/Users.vue', 1], ['views/system/WorkflowMonitor.vue', 1],
  ['views/workspace/AnnouncementList.vue', 1], ['views/workspace/Index.vue', 3],
  ['views/yundun/ViolationManagement.vue', 1]
];

const adaptiveMatrix = [
  ['views/info/ProductLib.vue', 0, '编辑', ['附件', '删除']],
  ['views/info/DocCenter.vue', 0, '下载', ['编辑', '删除']],
  ['views/perf/Metrics.vue', 0, '查看', ['编辑']],
  ['views/perf/KpiRules.vue', 0, '查看', ['复制版本', '编辑']],
  ['views/perf/Targets.vue', 0, '目标值', ['编辑', '删除']],
  ['views/perf/TargetValues.vue', 0, '修改', ['调整', '删除']],
  ['views/perf/Import.vue', 0, '刷新', ['下载文件', '下载错误', '重试', '删除']],
  ['views/perf/Adjust.vue', 0, '查看', ['编辑', '撤回']],
  ['views/perf/TaskMonitor.vue', 0, '执行', ['历史']],
  ['views/eval/Tags.vue', 0, '编辑', ['删除']],
  ['views/eval/Rules.vue', 0, '详情', ['编辑', '删除']],
  ['views/eval/Tasks.vue', 0, '详情', ['发布', '关闭', '导出', '删除']],
  ['views/report/FreeReport.vue', 0, '查看', ['下载', '启用', '禁用', '删除']],
  ['views/guarantee/DataImport.vue', 0, '查看', ['下载']],
  ['views/screen/admin/Datasources.vue', 0, '编辑', ['试跑', '探测列', '新建副本', '删除']],
  ['views/system/Users.vue', 0, '编辑', ['分配角色', '删除']],
  ['views/system/Roles.vue', 0, '编辑', ['分配菜单', '已绑用户', '删除']],
  ['views/system/Resources.vue', 0, '编辑', ['新增子菜单', '分配角色', '删除']],
  ['views/system/Dict.vue', 0, '编辑', ['禁用', '启用']],
  ['views/system/Jobs.vue', 0, '日志', ['暂停', '恢复', '手动触发']],
  ['views/system/Notifications.vue', 0, '详情', ['标记已读', '跳转']],
  ['views/system/Files.vue', 0, '下载', ['删除']],
  ['views/system/FlowList.vue', 0, '编辑', ['发布', '克隆', '删除']],
  ['views/system/WorkflowMonitor.vue', 0, '查看', ['转交']],
  ['views/system/PersonTags.vue', 0, '详情', ['编辑', '删除']],
  ['views/system/PersonTags.vue', 1, '修改', ['删除']],
  ['views/system/PersonTags.vue', 2, '修改', ['删除']],
  ['views/workspace/Index.vue', 1, '认领', ['拒绝']],
  ['views/workspace/Index.vue', 2, '查看', ['撤回']],
  ['views/customerMarketing/AssetProjects.vue', 0, '详情', ['编辑', '提交', '删除', '撤回', '申请加急']],
  ['views/report/components/SchemeListDialog.vue', 0, '载入', ['编辑', '删除']]
];

const conditionalMatrix = [
  ['views/perf/KpiRules.vue', 0, ['isCaizai', 'row.createdByMe']],
  ['views/perf/Targets.vue', 0, ['row.createdBy === userStore.user?.empId']],
  ['views/perf/TargetValues.vue', 0, ['canTargetAdjust']],
  ['views/perf/Import.vue', 0, ["row.status === 'FAILED'"]],
  ['views/perf/Adjust.vue', 0, ["row.status === 'DRAFT'", 'canWithdraw(row.status)']],
  ['views/eval/Tasks.vue', 0, ["row.status === 2", "row.sourceType === 'AUTO' && row.status === 0", 'isDeadlinePassed(row)']],
  ['views/report/FreeReport.vue', 0, ['isOperator', "row.status === 'DISABLED'"]],
  ['views/screen/admin/Datasources.vue', 0, ['referenceState(row).publishedReferenced']],
  ['views/system/Jobs.vue', 0, ["row.status === 'ACTIVE'", 'row.allowManualTrigger']],
  ['views/system/Notifications.vue', 0, ['!row.isRead', 'row.linkUrl || row.bizId']],
  ['views/system/FlowList.vue', 0, ["row.status === 'DRAFT' && row.isReadonlyImport != 1"]],
  ['views/system/Resources.vue', 0, ["row.menuEndFlag === '1'"]],
  ['views/workspace/Index.vue', 1, ["row.status === 'PENDING_ACCEPT'"]],
  ['views/workspace/Index.vue', 2, ["row.status === 'PENDING_ACCEPT'"]],
  ['views/customerMarketing/AssetProjects.vue', 0, ['row.canEdit', 'row.canCancel', 'row.canApplyUrgent']]
];

function slot(block, name) {
  return block.match(new RegExp(`<template #${name}>([\\s\\S]*?)<\\/template>`))?.[1] || '';
}

describe('普通后台行操作审计矩阵', () => {
  it('77 个普通命名路由精确盘点出 74 个物理操作列，SchemeListDialog 单独纳入', () => {
    const actual = new Map(namedRoutes.flatMap(route => {
      const count = operationColumns(sourceOf(route.view)).length;
      return count ? [[route.view, count]] : [];
    }));
    actual.set('views/report/components/SchemeListDialog.vue', operationColumns(sourceOf('views/report/components/SchemeListDialog.vue')).length);

    expect(namedRoutes).toHaveLength(77);
    expect([...actual.entries()].sort()).toEqual([...physicalColumnMatrix].sort());
    expect([...actual.values()].reduce((sum, count) => sum + count, 0)).toBe(74);
  });

  it('每个包含多个实际可见操作的物理列都接入同一自适应契约，单操作列保持原行为', () => {
    const expected = new Set(adaptiveMatrix.map(([view, index]) => `${view}#${index}`));
    const actual = new Set(physicalColumnMatrix.flatMap(([view]) => operationColumns(sourceOf(view))
      .map((column, index) => column.includes('<BpAdaptiveRowActions') ? `${view}#${index}` : null)
      .filter(Boolean)));

    expect(actual).toEqual(expected);
    expect(actual.size).toBe(31);
  });

  it.each(adaptiveMatrix)('%s 的第 %i 个多操作列具备主操作、全直出和 fail-close 更多三态', (view, index, primary, secondary) => {
    const column = operationColumns(sourceOf(view))[index];
    const component = column.match(/<BpAdaptiveRowActions\b[\s\S]*?<\/BpAdaptiveRowActions>/)?.[0] || '';
    const primarySlot = slot(component, 'primary');
    const expandedSlot = slot(component, 'expanded');
    const compactSlot = slot(component, 'compact');

    expect(component, `${view} 第${index + 1}个操作列`).toBeTruthy();
    expect(primarySlot).toContain(primary);
    expect(compactSlot).toContain('popper-class="bp-crud-menu"');
    expect(compactSlot).toContain('更多');
    for (const action of secondary) {
      expect(expandedSlot, `${action} 必须在宽度足够时直出`).toContain(action);
      expect(compactSlot, `${action} 必须在空间不足时保留于更多`).toContain(action);
    }
    if (secondary.some(action => ['删除', '撤回', '拒绝', '关闭'].includes(action))) {
      expect(expandedSlot).toMatch(/type="danger"/);
      expect(compactSlot).toContain('class="danger-item"');
    }
  });

  it.each(conditionalMatrix)('%s 的第 %i 个多操作列在直出和更多分支保持权限/状态条件', (view, index, conditions) => {
    const column = operationColumns(sourceOf(view))[index];
    const component = column.match(/<BpAdaptiveRowActions\b[\s\S]*?<\/BpAdaptiveRowActions>/)?.[0] || '';
    const expandedSlot = slot(component, 'expanded');
    const compactSlot = slot(component, 'compact');

    for (const condition of conditions) {
      expect(expandedSlot, `${condition} 必须约束直出分支`).toContain(condition);
      expect(compactSlot, `${condition} 必须约束更多分支`).toContain(condition);
    }
  });

  it('两个展示分支复用原事件入口；命令型菜单仍显式映射到同一处理函数', () => {
    for (const [view, index] of adaptiveMatrix) {
      if (['views/system/Users.vue', 'views/screen/admin/Datasources.vue'].includes(view)) continue;
      const column = operationColumns(sourceOf(view))[index];
      const component = column.match(/<BpAdaptiveRowActions\b[\s\S]*?<\/BpAdaptiveRowActions>/)?.[0] || '';
      const clicksOf = (name) => [...slot(component, name).matchAll(/@click(?:\.[\w-]+)*="([^"]+)"/g)].map(([, handler]) => handler);
      expect(clicksOf('expanded'), `${view} 第${index + 1}列事件入口`).toEqual(clicksOf('compact'));
    }

    const users = sourceOf('views/system/Users.vue');
    expect(users).toMatch(/command === 'roles'\) openAssignRoles\(row\)/);
    expect(users).toMatch(/command === 'delete'\) batch\('delete', \[row\.userId\]\)/);
    expect(users).toContain('@click="openAssignRoles(row)"');
    expect(users).toContain('@click="batch(\'delete\', [row.userId])"');

    const datasources = sourceOf('views/screen/admin/Datasources.vue');
    for (const [command, handler] of [['try', 'openTryRun'], ['probe', 'openProbeColumns'], ['copy', 'openCopy'], ['delete', 'onDelete']]) {
      expect(datasources).toContain(`command === '${command}') ${handler}(row)`);
      expect(datasources).toContain(`@click="${handler}(row)"`);
    }
  });
});
