import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../MarketingLeadImportDetailDrawer.vue', import.meta.url), 'utf8');

describe('线索导入明细抽屉契约', () => {
  it('包含失败优先明细、原始文件下载和待确认操作', () => {
    expect(source).toContain('失败原因');
    expect(source).toContain('downloadLeadImportSourceFile');
    expect(source).toContain('下载原始文件');
    expect(source).not.toContain('downloadLeadImportErrorFile');
    expect(source).not.toContain('下载失败明细');
    expect(source).toContain('PROCESS_VALID');
    expect(source).toContain('ABANDON_REIMPORT');
  });
});
