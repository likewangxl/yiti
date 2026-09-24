async page => {
  const ok = data => ({ code: '0', message: 'success', data });
  const responses = {
    '/api/auth/current-user': ok({ empId: 'QA-BRANCH-PREVIEW', displayName: '演示验收账号', mainOrgName: '验收机构', roles: [] }),
    '/api/auth/my-menus': ok([{ resourceId: 'QA_SCREENS', resourceUrl: '/screens', menuName: '大屏中心', children: [] }]),
    '/api/auth/permissions': ok({ resourceUrls: ['/api/screen/view/*'], isSystemAdmin: false }),
    '/api/screen/view/catalog': ok([]),
    '/api/notifications/unread-count': ok(0)
  };
  const observed = [];
  const pageErrors = [];
  await page.unroute('**/api/**');
  await page.route('**/api/**', async route => {
    const path = route.request().url().replace(/^https?:\/\/[^/]+/, '').split('?')[0];
    if (!path.startsWith('/api/')) return route.continue();
    observed.push(path);
    const body = responses[path];
    return route.fulfill({
      status: body ? 200 : 404,
      contentType: 'application/json',
      body: JSON.stringify(body || { code: 'QA_UNMATCHED', message: '仅开发态 mock 未登记的接口' })
    });
  });
  page.on('pageerror', error => pageErrors.push(error.message));
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('http://127.0.0.1:8092/#/screens', { waitUntil: 'domcontentloaded' });
  await page.getByRole('heading', { name: '大屏中心' }).waitFor({ state: 'visible' });
  const actions = page.locator('[data-testid="screen-center-preview-tools"] button');
  if (await actions.count() !== 3) throw new Error('改造版演示入口数量不是 3');
  const branch = page.locator('[data-action="open-branch-preview"]');
  await branch.waitFor({ state: 'visible' });
  if (!await branch.getByText('本地演示 · 非业务数据').count()) throw new Error('分行演示数据标识缺失');
  if (!await page.getByText('0个可访问大屏').count()) throw new Error('演示按钮被计入授权目录');
  await page.screenshot({ path: '../docs/superpowers/evidence/2026-09-25-branch-preview-entry/center.png', fullPage: true });
  await branch.click();
  await page.waitForURL('**/screen-preview?from=screen-center');
  await page.getByRole('heading', { name: '分行经营总览' }).waitFor({ state: 'visible' });
  await page.getByText('本地演示 · 非业务数据').first().waitFor({ state: 'visible' });
  await page.screenshot({ path: '../docs/superpowers/evidence/2026-09-25-branch-preview-entry/branch.png', fullPage: true });
  await page.locator('[data-action="back"]').click();
  await page.waitForURL('**#/screens');
  await branch.waitFor({ state: 'visible' });
  return { mode: '仅开发态 mock，非联调', mockRoutes: Object.keys(responses), observed, pageErrors, branchOpenAndReturn: true };
}
