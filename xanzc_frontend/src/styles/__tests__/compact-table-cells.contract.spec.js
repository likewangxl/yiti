import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const sourceOf = (relativePath) => readFileSync(resolve(process.cwd(), 'src/views', relativePath), 'utf8');

const compactPages = [
  ['工作台通知', 'workspace/NotificationList.vue'],
  ['通讯录', 'info/AddressBook.vue'],
  ['KPI规则', 'perf/KpiRules.vue'],
  ['目标管理', 'perf/Targets.vue'],
  ['目标值', 'perf/TargetValues.vue'],
  ['业绩调整', 'perf/Adjust.vue'],
  ['考核计算', 'perf/Compute.vue'],
  ['任务监控', 'perf/TaskMonitor.vue'],
  ['担保查询', 'guarantee/Query.vue'],
  ['定价审批历史', 'history/PriceApproval.vue'],
  ['业绩调整历史', 'history/PerfAdjustQuery.vue'],
  ['权限配置', 'system/Permission.vue'],
  ['系统通知', 'system/Notifications.vue'],
  ['审批流监控', 'system/WorkflowMonitor.vue']
];

describe('普通后台表格双行信息显式紧凑契约', () => {
  it.each(compactPages)('%s 的双行/多块单元格显式接入 compact-stack-cell', (_name, file) => {
    expect(sourceOf(file)).toMatch(/<el-table-column\b[^>]*class-name="[^"]*\bcompact-stack-cell\b[^"]*"/);
  });

  it('Adjust、History 与 AddressBook 代表页不靠裁切丢弃第二行', () => {
    for (const file of ['perf/Adjust.vue', 'history/PerfAdjustQuery.vue', 'info/AddressBook.vue']) {
      const source = sourceOf(file);
      expect(source).toContain('compact-stack-cell');
      expect(source).not.toMatch(/compact-stack-cell[\s\S]{0,160}(?:display:\s*none|max-height:\s*0|overflow:\s*hidden)/);
    }
  });

  it('ProductLib 三个长文本列按方案 B 接入两行紧凑省略', () => {
    const source = sourceOf('info/ProductLib.vue');
    expect(source.match(/class-name="compact-clamp-cell"/g)).toHaveLength(3);
    expect(source).not.toContain('class-name="wrap-cell"');
    expect(source).not.toMatch(/\.wrap-cell\s+:deep\(\.cell\)/);
  });

  it('PriceApproval 的双行表头显式使用 18px + 18px 紧凑结构', () => {
    const source = sourceOf('history/PriceApproval.vue');
    expect(source.match(/label-class-name="compact-stack-header"/g)).toHaveLength(2);
    expect(source.match(/class="compact-header-lines"/g)).toHaveLength(2);
    expect(source).not.toMatch(/<template #header>[^<]+<br\s*\/?>(?:[^<]+)<\/template>/);
  });
});
