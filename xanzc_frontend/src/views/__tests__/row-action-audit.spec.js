import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const routerSource = readFileSync(new URL('../../router/index.js', import.meta.url), 'utf8');
const routePattern = /\{\s*path:\s*'([^']+)'\s*,\s*name:\s*'([^']+)'\s*,\s*component:\s*\(\)\s*=>\s*import\('\@\/([^']+)'\)/g;
const excludedViews = new Set([
  'views/screen/ScreenView.vue',
  'views/screen/designer/DesignerV2.vue'
]);
const namedRoutes = [...routerSource.matchAll(routePattern)].map(([, path, name, view]) => ({ path, name, view }));

function sourceOf(view) {
  return readFileSync(new URL(`../../${view}`, import.meta.url), 'utf8');
}

function operationColumns(source) {
  return [...source.matchAll(/<el-table-column\b(?=[^>]*\blabel=(?:"操作"|'操作'))[^>]*>[\s\S]*?<\/el-table-column>/g)]
    .map(([column]) => column);
}

function directActionCount(column) {
  const dropdownAt = column.indexOf('<el-dropdown');
  const directArea = dropdownAt < 0 ? column : column.slice(0, dropdownAt);
  return (directArea.match(/<el-button\b/g) || []).length;
}

const rowActionMatrix = [
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
  ['views/system/Users.vue', 0, '编辑', ['分配角色', '删除']],
  ['views/system/Roles.vue', 0, '编辑', ['分配菜单', '已绑用户', '删除']],
  ['views/system/Resources.vue', 0, '编辑', ['新增子菜单', '分配角色', '删除']],
  ['views/system/Dict.vue', 0, '编辑', ['禁用', '启用']],
  ['views/system/Jobs.vue', 0, '日志', ['暂停', '恢复', '手动触发']],
  ['views/system/Notifications.vue', 0, '详情', ['标记已读', '跳转']],
  ['views/system/Files.vue', 0, '下载', ['删除']],
  ['views/system/FlowList.vue', 0, '编辑', ['发布', '克隆', '删除']],
  ['views/system/WorkflowMonitor.vue', 0, '查看', ['转交', '指派']],
  ['views/system/PersonTags.vue', 0, '详情', ['编辑', '删除']],
  ['views/system/PersonTags.vue', 1, '修改', ['删除']],
  ['views/system/PersonTags.vue', 2, '修改', ['删除']],
  ['views/workspace/Index.vue', 1, '认领', ['拒绝']],
  ['views/workspace/Index.vue', 2, '查看', ['撤回']],
  ['views/report/components/SchemeListDialog.vue', 0, '载入', ['编辑', '删除']]
];

describe('普通后台行操作审计矩阵', () => {
  it('每个普通后台表格行至多直出一个主操作，数据源的三项显式例外受冻结需求约束', () => {
    const violations = namedRoutes
      .filter(route => !route.view.includes('/redengine/') && !excludedViews.has(route.view))
      .flatMap(route => operationColumns(sourceOf(route.view))
        .map((column, index) => ({ route, index, count: directActionCount(column) }))
        .filter(({ route, index, count }) => !(route.name === 'ScreenAdminDs' && index === 0) && count > 1)
        .map(({ route, index, count }) => `${route.name} (${route.path}) 第${index + 1}列直出${count}项`));

    expect(violations).toEqual([]);
  });

  it.each(rowActionMatrix)('%s 的第 %i 个操作列保留一个主操作，其余收纳在更多中', (view, index, primary, secondary) => {
    const column = operationColumns(sourceOf(view))[index];
    const dropdownAt = column.indexOf('<el-dropdown');

    expect(column, `${view} 第${index + 1}个操作列`).toBeTruthy();
    expect(dropdownAt).toBeGreaterThan(-1);
    expect(directActionCount(column)).toBeLessThanOrEqual(1);
    expect(column.slice(0, dropdownAt)).toContain(primary);
    expect(column.slice(dropdownAt)).toContain('popper-class="bp-crud-menu"');
    for (const action of secondary) {
      expect(column.slice(dropdownAt)).toContain(action);
    }
  });
});
