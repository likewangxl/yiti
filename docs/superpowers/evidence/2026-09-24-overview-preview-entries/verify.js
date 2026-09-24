async page => {
  const base = 'http://127.0.0.1:8092/';
  const envelope = data => ({ code: '0', message: 'success', data });
  const routeResponses = {
    '/api/auth/current-user': envelope({ empId: 'QA-PREVIEW', displayName: '演示验收账号', mainOrgName: '验收机构', roles: [] }),
    '/api/auth/my-menus': envelope([{ resourceId: 'QA_SCREENS', resourceUrl: '/screens', menuName: '大屏中心', children: [] }]),
    '/api/auth/permissions': envelope({ resourceUrls: ['/api/screen/view/*'], isSystemAdmin: false }),
    '/api/screen/view/catalog': envelope([]),
    '/api/notifications/unread-count': envelope(0)
  };
  const observed = [];
  await page.unroute('**/api/**');
  await page.route('**/api/**', async route => {
    const path = route.request().url().replace(/^https?:\/\/[^/]+/, '').split('?')[0];
    if (!path.startsWith('/api/')) {
      await route.continue();
      return;
    }
    observed.push(path);
    const body = routeResponses[path];
    await route.fulfill({
      status: body ? 200 : 404,
      contentType: 'application/json',
      body: JSON.stringify(body || { code: 'QA_UNMATCHED', message: '仅开发态 mock 未登记的接口' })
    });
  });
  const runtimeErrors = [];
  page.on('pageerror', error => runtimeErrors.push(error.message));
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(`${base}#/screens`, { waitUntil: 'domcontentloaded' });
  const heading = page.getByRole('heading', { name: '大屏中心' });
  await heading.waitFor({ state: 'visible' });
  const corporate = page.locator('[data-action="open-corporate-preview"]');
  const retail = page.locator('[data-action="open-retail-preview"]');
  await corporate.waitFor({ state: 'visible' });
  await retail.waitFor({ state: 'visible' });
  if (!await page.getByText('本地演示 · 非业务数据').count()) throw new Error('大屏中心缺少演示数据标识');
  await page.screenshot({ path: '../docs/superpowers/evidence/2026-09-24-overview-preview-entries/center.png', fullPage: true });
  await corporate.click();
  await page.waitForURL('**/screen-preview/corporate?from=screen-center');
  await page.getByText('本地演示 · 非业务数据').first().waitFor({ state: 'visible' });
  await page.screenshot({ path: '../docs/superpowers/evidence/2026-09-24-overview-preview-entries/corporate.png', fullPage: true });
  await page.locator('[data-action="back"]').click();
  await page.waitForURL('**#/screens');
  await retail.click();
  await page.waitForURL('**/screen-preview/retail?from=screen-center');
  await page.getByText('本地演示 · 非业务数据').first().waitFor({ state: 'visible' });
  await page.screenshot({ path: '../docs/superpowers/evidence/2026-09-24-overview-preview-entries/retail.png', fullPage: true });
  await page.locator('[data-action="back"]').click();
  await page.waitForURL('**#/screens');
  return { mode: '仅开发态 mock，非联调', routes: Object.keys(routeResponses), observed, runtimeErrors, bothButtonsAndReturns: true };
}
