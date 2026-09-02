import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const routerSource = readFileSync(new URL('../../router/index.js', import.meta.url), 'utf8');
const mainSource = readFileSync(new URL('../../main.js', import.meta.url), 'utf8');
const routePattern = /\{\s*path:\s*'([^']+)'\s*,\s*name:\s*'([^']+)'\s*,\s*component:\s*\(\)\s*=>\s*import\('\@\/([^']+)'\)/g;
const excludedScreenViews = new Set([
  'views/screen/ScreenView.vue',
  'views/screen/designer/DesignerV2.vue'
]);
const independentPageNames = new Set(['Login', 'NoAccess', 'Workspace', 'ReportDash']);

const namedRoutes = [...routerSource.matchAll(routePattern)].map(([, path, name, view]) => ({ path, name, view }));
const normalRoutes = namedRoutes.filter(route =>
  !route.view.includes('/redengine/') && !excludedScreenViews.has(route.view)
);

describe('普通后台路由 CRUD 审计矩阵', () => {
  it('精确覆盖 77 个命名路由，并明确排除红色引擎和大屏运行/设计器', () => {
    expect(normalRoutes).toHaveLength(77);
    expect(normalRoutes.some(route => route.view.includes('/redengine/'))).toBe(false);
    expect(normalRoutes.some(route => excludedScreenViews.has(route.view))).toBe(false);
  });

  it('除独立信息架构外，所有普通后台页面均接入受控 bp-crud 基线', () => {
    const missingBaseline = normalRoutes
      .filter(route => !independentPageNames.has(route.name))
      .filter(route => {
        const source = readFileSync(new URL(`../../${route.view}`, import.meta.url), 'utf8');
        return !/class="[^"]*\bbp-crud\b/.test(source);
      })
      .map(route => `${route.name} (${route.path})`);

    expect(missingBaseline).toEqual([]);
  });

  it('在标准页面根容器挂载受控溢出提示，覆盖其全部 119 张表', () => {
    const pagesWithoutTooltip = normalRoutes
      .filter(route => !independentPageNames.has(route.name))
      .flatMap(route => {
        const source = readFileSync(new URL(`../../${route.view}`, import.meta.url), 'utf8');
        const roots = [...source.matchAll(/<(?:main|section|div)\b[^>]*\bclass="[^"]*\bbp-crud\b[^"]*"[^>]*>/g)];

        return roots.some(([root]) => /\bv-bp-overflow-tooltip\b/.test(root))
          ? []
          : [`${route.name} (${route.path})`];
      });

    const tableCount = normalRoutes
      .filter(route => !independentPageNames.has(route.name))
      .reduce((total, route) => {
        const source = readFileSync(new URL(`../../${route.view}`, import.meta.url), 'utf8');
        return total + [...source.matchAll(/<el-table(?=[\s>])[^>]*>/g)].length;
      }, 0);

    expect(pagesWithoutTooltip).toEqual([]);
    expect(tableCount).toBe(119);
  });

  it('append-to-body 对话框也带 bp-crud-dialog 边界，避免提示跨入红色引擎', () => {
    const unscopedDialogs = normalRoutes.flatMap(route => {
      const source = readFileSync(new URL(`../../${route.view}`, import.meta.url), 'utf8');
      return [...source.matchAll(/<el-dialog\b[^>]*\bappend-to-body\b[^>]*>/g)]
        .filter(([dialog]) => !/class="[^"]*\bbp-crud-dialog\b/.test(dialog))
        .map(() => `${route.name} (${route.path})`);
    });

    const nonPlatformDirectiveUsages = namedRoutes
      .filter(route => route.view.includes('/redengine/') || excludedScreenViews.has(route.view))
      .filter(route => readFileSync(new URL(`../../${route.view}`, import.meta.url), 'utf8').includes('v-bp-overflow-tooltip'))
      .map(route => `${route.name} (${route.path})`);

    expect(unscopedDialogs).toEqual([]);
    expect(nonPlatformDirectiveUsages).toEqual([]);
  });

  it('注册的是受控溢出提示指令，而非全局 Element Plus 表格配置', () => {
    expect(mainSource).toMatch(/app\.directive\(\s*['"]bp-overflow-tooltip['"]/);
    expect(mainSource).not.toMatch(/table:\s*\{\s*showOverflowTooltip:\s*true\s*\}/);
  });

  it('普通后台表格的操作列固定在右侧并标记 operation-cell，避免操作在窄列换行', () => {
    const violations = normalRoutes
      .flatMap(route => {
        const source = readFileSync(new URL(`../../${route.view}`, import.meta.url), 'utf8');
        const operationColumns = [...source.matchAll(/<el-table-column\b[^>]*\blabel=(?:"操作"|'操作')[^>]*>/g)];

        return operationColumns
          .filter(([column]) => !/(?:\:)?fixed=(?:"right"|'right')/.test(column) || !/class-name=(?:"[^"]*\boperation-cell\b[^"]*"|'[^']*\boperation-cell\b[^']*')/.test(column))
          .map(() => `${route.name} (${route.path})`);
      });

    expect(violations).toEqual([]);
  });
});
