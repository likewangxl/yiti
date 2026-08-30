import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

function sourceOf(relativePath) {
  return readFileSync(new URL(relativePath, import.meta.url), 'utf8');
}

describe('按机构会签模式前端契约', () => {
  it('节点属性面板提供 GROUP_ALL 选项及机构内或签说明', () => {
    const source = sourceOf('../flow/FlowNodePanel.vue');

    expect(source).toContain('<el-radio-button value="GROUP_ALL">按机构会签</el-radio-button>');
    expect(source).toContain('机构间按顺序会签；同一机构内任一负责人审批即可');
  });

  it('流程画布用按机构会签文案和分组数量表达 GROUP_ALL 节点', () => {
    const source = sourceOf('../flow/FlowCanvas.vue');

    expect(source).toContain("node.approveMode === 'GROUP_ALL' ? '按机构会签'");
    expect(source).toContain('审批机构组');
    expect(source).not.toContain('mode-hint');
  });
});
