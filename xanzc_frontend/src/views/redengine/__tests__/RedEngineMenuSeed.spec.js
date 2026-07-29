import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const seedPath = fileURLToPath(
  new URL('../../../../../docs/superpowers/sql/2026-07-29-redengine-platform-menu-align.sql', import.meta.url)
);
const seedSql = readFileSync(seedPath, 'utf8');

describe('红色引擎平台菜单种子', () => {
  it('注册一个指向红色工作台的顶层叶子菜单', () => {
    expect(seedSql).toMatch(
      /'M_RE_ENGINE'\s*,\s*'\/redengine\/dashboard'\s*,\s*'MENU'\s*,\s*'红色引擎'/
    );
    expect(seedSql).toMatch(
      /'M_RE_ENGINE'[\s\S]*?\b1\s*,\s*'1'\s*,\s*NULL\s*,\s*0\s*,\s*'RE'/
    );
  });

  it('四个党建角色和系统管理员均获得红色引擎菜单', () => {
    expect(seedSql).toMatch(
      /ROLE_CODE IN \('R_RE_ORGREV','R_RE_BRREV','R_RE_SECR','R_RE_REPORT'\)[\s\S]*?'M_RE_ENGINE'/
    );
    expect(seedSql).toMatch(
      /ROLE_CODE = 'SYS_ADMIN'[\s\S]*?'M_RE_ENGINE'/
    );
  });
});
