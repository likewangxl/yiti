async page => {
  const origin = 'http://127.0.0.1:8094/';
  const ok = data => ({ code: '0', message: 'success', data });
  const catalog = [
    { screenCode: 'SCR_PROVINCE', screenName: '分行经营总览', template: 'branch-overview-v1', viewLevel: 'PROVINCE', bizLine: 'COMMON', dataMode: 'TEST' },
    { screenCode: 'SCR_CORP_OVERVIEW', screenName: '对公经营总览', template: 'corporate-overview-v1', viewLevel: 'PROVINCE', bizLine: 'CORP', dataMode: 'LIVE' },
    { screenCode: 'SCR_RETAIL_OVERVIEW', screenName: '零售经营总览', template: 'retail-overview-v1', viewLevel: 'PROVINCE', bizLine: 'RETAIL', dataMode: 'LIVE' }
  ];
  const draft = {
    screenId: 1,
    screenCode: 'SCR_PROVINCE',
    screenName: '分行经营总览',
    state: 'draft',
    runtimeSchemaVersion: 2,
    renderPackageJson: JSON.stringify({
      schemaVersion: 2,
      canvasStyle: { presentation: { type: 'CODE', template: 'branch-overview-v1', displaySchemaVersion: 1, display: { components: [] } } },
      components: [], bindSnapshots: {}
    }),
    panoramaInstitutions: [], mapPoints: [], institutionRules: { allowedOperatingLevels: ['PRIMARY'], allowedOrgNatures: ['SECONDARY_BRANCH'] }
  };
  const observed = [];
  const pageErrors = [];
  await page.route('**/api/**', async route => {
    const url = route.request().url();
    const path = url.replace(/^https?:\/\/[^/]+/, '').split('?')[0];
    if (!path.startsWith('/api/')) return route.continue();
    observed.push({ path, status: 200 });
    const manager = !page.url().includes('qa=viewer');
    let body;
    if (path === '/api/auth/current-user') body = ok({ empId: 'QA-DRAFT', displayName: '开发态验收账号', roles: [] });
    else if (path === '/api/auth/my-menus') body = ok([{ resourceId: 'QA_SCREENS', resourceUrl: '/screens', menuName: '大屏中心', children: [] }]);
    else if (path === '/api/auth/permissions') body = ok({ resourceUrls: manager ? ['/api/screen/view/*', '/api/screen/admin/canvas/*'] : ['/api/screen/view/*'], isSystemAdmin: false });
    else if (path === '/api/screen/view/catalog') body = ok(catalog);
    else if (path === '/api/screen/view/SCR_PROVINCE' && url.includes('preview=draft')) body = ok(draft);
    else if (path === '/api/notifications/unread-count') body = ok(0);
    else {
      observed[observed.length - 1].status = 404;
      body = { code: 'QA_UNMATCHED', message: '仅开发态 mock 未登记的接口' };
    }
    return route.fulfill({ status: observed[observed.length - 1].status, contentType: 'application/json', body: JSON.stringify(body) });
  });
  page.on('pageerror', error => pageErrors.push(error.message));
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(`${origin}?qa=manager#/screens`, { waitUntil: 'domcontentloaded' });
  const section = page.locator('[data-testid="screen-center-preview-tools"]');
  await section.waitFor({ state: 'visible' });
  if (await section.locator('button').count() !== 3) throw new Error('生产构建未显示三个受权草稿按钮');
  await page.screenshot({ path: '../docs/superpowers/evidence/2026-09-27-draft-entry/production-center.png', fullPage: true });
  await page.locator('[data-action="open-branch-preview"]').click();
  await page.waitForURL(url => url.pathname.endsWith('/screen/SCR_PROVINCE') || url.hash.includes('/screen/SCR_PROVINCE'));
  if (!page.url().includes('preview=draft')) throw new Error('新版入口未请求草稿');
  await page.locator('[data-testid="screen-draft-preview"]').waitFor({ state: 'visible' });
  await page.locator('[data-testid="presentation-layout"]').waitFor({ state: 'visible' });
  await page.screenshot({ path: '../docs/superpowers/evidence/2026-09-27-draft-entry/production-draft.png', fullPage: true });
  await page.locator('[data-action="back"]').click();
  await page.waitForURL('**#/screens');
  await page.goto(`${origin}?qa=viewer#/screens`, { waitUntil: 'domcontentloaded' });
  await page.getByRole('heading', { name: '大屏中心' }).waitFor({ state: 'visible' });
  if (await page.locator('[data-testid="screen-center-preview-tools"]').count()) throw new Error('普通查看者看到了草稿入口');
  return { mode: '仅开发态 mock，非联调', registeredRoutePattern: '**/api/**', observed, pageErrors, managerDraftEntry: true, viewerFailClosed: true };
}
