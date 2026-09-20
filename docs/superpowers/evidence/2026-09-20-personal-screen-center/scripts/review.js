async page => {
  const ok = data => ({ code: '0', message: 'success', data });
  await page.route('**/api/auth/my-menus', async route => {
    const menus = [{ resourceId: 'QA_SCREENS', resourceUrl: '/screens', menuName: '大屏中心', children: [] }];
    if (!page.url().includes('qa=nopersonal')) menus.push({ resourceId: 'QA_WORKSPACE', resourceUrl: '/workspace', menuName: '工作台', children: [] });
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(ok(menus)) });
  });
  await page.route('**/api/auth/permissions', async route => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(ok({ resourceUrls: ['/api/screen/view/*'], isSystemAdmin: false })) });
  });
  await page.route('**/api/screen/view/catalog', async route => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(ok([])) });
  });
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('http://127.0.0.1:8092/?qa=base#/screens');
  const card = page.locator('[data-screen-kind="personal"]');
  await card.waitFor({ state: 'visible' });
  await page.evaluate(() => {
    document.querySelectorAll('body > div').forEach(e => { if (e.textContent === '仅开发态 mock · 非联调') e.remove(); });
    const label = document.createElement('div');
    label.textContent = '仅开发态 mock · 非联调';
    label.style.cssText = 'pointer-events:none;position:fixed;right:12px;top:12px;z-index:9999;padding:6px 10px;background:#fff4ce;color:#704d00;border:1px solid #ddc276;border-radius:6px;font-size:13px';
    document.body.appendChild(label);
  });
  await page.screenshot({ path: '/tmp/personal-screen-center-evidence/center.png' });
  const search = page.getByRole('searchbox', { name: '搜索大屏' });
  await search.fill('个人大屏');
  if (await card.count() !== 1) throw new Error('个人搜索未找到卡片');
  await page.locator('[data-biz-line="RETAIL"]').click();
  if (await card.count() !== 0) throw new Error('零售筛选误显示个人卡片');
  await page.locator('[data-biz-line="ALL"]').click();
  await card.getByRole('button').click();
  await page.waitForURL('**/personal-dashboard?from=screen-center');
  const back = page.getByRole('button', { name: '返回大屏中心', exact: true });
  await back.waitFor({ state: 'visible' });
  const personalUrl = page.url();
  await back.click();
  await page.waitForURL('**#/screens');
  await card.waitFor({ state: 'visible' });
  await page.goto('http://127.0.0.1:8092/?qa=nopersonal#/screens');
  await page.getByRole('heading', { name: '当前没有已接入大屏' }).waitFor({ state: 'visible' });
  if (await card.count() !== 0) throw new Error('无工作台授权仍显示个人卡片');
  return { mode: '仅开发态 mock，非联调', emptyCatalogPersonalEntry: true, search: true, filter: true, personalUrl, returnToCenter: true, unauthorizedEntryHidden: true };
}
