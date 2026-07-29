import { describe, expect, it } from 'vitest';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const syncPath = fileURLToPath(
  new URL('../../../../../docs/superpowers/sql/2026-07-29-redengine-sync-yiti.sql', import.meta.url)
);
const syncSql = existsSync(syncPath) ? readFileSync(syncPath, 'utf8') : '';

describe('红色引擎 yiti 正式库同步脚本', () => {
  it('显式锁定 yiti，并按基础表、正式种子、平台菜单顺序执行', () => {
    expect(syncSql).toContain('USE yiti;');

    const tableIndex = syncSql.indexOf('SOURCE docs/superpowers/sql/2026-07-18-redengine-tables.sql;');
    const seedIndex = syncSql.indexOf('SOURCE docs/superpowers/sql/2026-07-18-redengine-seed.sql;');
    const menuIndex = syncSql.indexOf(
      'SOURCE docs/superpowers/sql/2026-07-29-redengine-platform-menu-align.sql;'
    );

    expect(tableIndex).toBeGreaterThan(-1);
    expect(seedIndex).toBeGreaterThan(tableIndex);
    expect(menuIndex).toBeGreaterThan(seedIndex);
  });

  it('不引入仅供 yiti_test 使用的演示业务数据', () => {
    expect(syncSql).not.toContain('2026-07-18-redengine-demo-data-yiti-test-only.sql');
  });
});
