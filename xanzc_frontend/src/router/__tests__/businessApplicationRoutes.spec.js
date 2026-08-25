// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest';

describe('资产立项路由', () => {
  it('主菜单路由精确使用 /bizexec/loans 并声明 LOAN 资源契约', async () => {
    const { default: router } = await import('@/router');
    const route = router.getRoutes().find(item => item.name === 'BusinessApplicationLoans');

    expect(route?.path).toBe('/bizexec/loans');
    expect(route?.meta?.requiredMenu).toBe('/bizexec/loans');
    expect(route?.meta?.requiredResource).toBe('/api/loans');
  });

  it('新建和详情入口隐藏于同一业务菜单下，不复制出其他菜单路径', async () => {
    const { default: router } = await import('@/router');
    const routes = router.getRoutes();

    expect(routes.find(item => item.name === 'BusinessApplicationLoanCreate')?.path).toBe('/bizexec/loans/new');
    expect(routes.find(item => item.name === 'BusinessApplicationLoanDetail')?.path).toBe('/bizexec/loans/:id');
    expect(routes.find(item => item.name === 'BusinessApplicationLoanCreate')?.meta?.hideInMenu).toBe(true);
    expect(routes.find(item => item.name === 'BusinessApplicationLoanDetail')?.meta?.hideInMenu).toBe(true);
  });
});
