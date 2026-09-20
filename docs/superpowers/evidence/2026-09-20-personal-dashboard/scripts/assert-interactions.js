async page => {
  const result = {};
  const wait = () => page.waitForTimeout(500);
  const closeOverlay = async () => {
    await page.keyboard.press('Escape');
    await page.waitForTimeout(250);
  };
  await page.locator('[data-testid="personal-customer"]').first().click();
  await wait();
  const drawer = page.locator('.el-drawer:visible');
  result.customerDrawer = await drawer.count() === 1;
  result.customerDetailText = result.customerDrawer && (await drawer.innerText()).includes('QA-CUSTOMER-001');
  await closeOverlay();

  await page.locator('[data-testid="personal-priority"]').filter({ hasText: '验收示例制造有限公司' }).click();
  await wait();
  const touchDialog = page.locator('.el-dialog:visible');
  result.touchDialog = await touchDialog.count() === 1;
  result.touchDialogText = result.touchDialog && (await touchDialog.innerText()).includes('验收示例触达-001');
  await closeOverlay();

  await page.locator('[data-action="view-progress"]').click();
  await wait();
  const progressChooser = page.getByRole('dialog', { name: '选择业务进度入口' });
  result.progressChooser = await progressChooser.count() === 1 && await progressChooser.isVisible();
  result.progressChooserText = result.progressChooser && (await progressChooser.innerText()).includes('资产立项') && (await progressChooser.innerText()).includes('中台支持');
  await page.locator('[data-choice="cancel"]').click();

  await page.locator('[data-action="refresh"]').click();
  await wait();
  result.refreshStillOnDashboard = await page.locator('main[aria-label="个人经营驾驶舱"]').count() === 1;
  await page.locator('[data-action="fullscreen"]').click();
  result.fullscreenActionReturned = true;

  const origin = page.url().match(/^(https?:\/\/[^/]+)/)?.[1] || 'http://127.0.0.1:8092';
  await page.goto(`${origin}/?qa=base#/personal-dashboard`);
  await wait();
  await page.locator('[data-action="view-progress"]').click();
  await page.locator('[data-choice="asset"]').click();
  await wait();
  result.assetRoute = page.url();

  await page.goto(`${origin}/?qa=base#/personal-dashboard`);
  await wait();
  await page.locator('[data-action="view-progress"]').click();
  await page.locator('[data-choice="support"]').click();
  await wait();
  result.supportRoute = page.url();
  const required = ['customerDrawer', 'customerDetailText', 'touchDialog', 'touchDialogText', 'progressChooser', 'progressChooserText', 'refreshStillOnDashboard', 'fullscreenActionReturned'];
  const failed = required.filter(key => !result[key])
    .concat(result.assetRoute.includes('/marketing/asset-projects') ? [] : ['assetRoute'])
    .concat(result.supportRoute.includes('/bizexec/supports') ? [] : ['supportRoute']);
  if (failed.length) throw new Error(`interaction assertions failed: ${failed.join(',')}`);
  return result;
}
