async page => {
  const root = '/home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization2-20260813/final-pass/1920';
  const context = page.context();
  const writes = [];
  const failures = [];
  const onRequest = request => {
    if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method())) writes.push({ method: request.method(), url: request.url().replace(/\/\d+(?=\/|[?#]|$)/g, '/<redacted-id>') });
  };
  const onFailed = request => failures.push({ method: request.method(), url: request.url(), failure: request.failure()?.errorText });
  context.on('request', onRequest);
  context.on('requestfailed', onFailed);
  await page.evaluate(() => { location.hash = '/info/products'; });
  await page.waitForTimeout(1200);
  await page.evaluate(() => document.fonts?.ready);
  const button = page.locator('[data-bp-row-actions-visible] .el-button--danger:visible').filter({ hasText: '删除' }).first();
  await button.click();
  const dialog = page.locator('.el-message-box:visible');
  await dialog.waitFor({ state: 'visible', timeout: 4000 });
  await page.waitForTimeout(400);
  const probe = await dialog.evaluate(element => ({
    role: element.getAttribute('role'),
    title: element.querySelector('.el-message-box__title')?.textContent?.trim(),
    buttons: [...element.querySelectorAll('button')].filter(item => item.getBoundingClientRect().width > 0).map(item => item.textContent.trim()),
    opacity: getComputedStyle(element).opacity,
    rect: element.getBoundingClientRect().toJSON()
  }));
  await page.screenshot({
    path: `${root}/screenshots/64-danger-confirm-cancel.png`,
    mask: [page.locator('.el-message-box__message'), page.locator('.el-table__body:visible')],
    maskColor: '#d8dee9'
  });
  await dialog.getByRole('button', { name: /取消/ }).click();
  await dialog.waitFor({ state: 'hidden' });
  await page.waitForTimeout(300);
  context.off('request', onRequest);
  context.off('requestfailed', onFailed);
  const evidence = {
    gitHead: '4f5d9ceb7ef5a53b39b02f79bfd3c9abd6039eb4',
    viewport: { width: 1920, height: 1080 },
    source: 'natural expanded visible danger action',
    action: 'open confirmation then cancel',
    probe,
    writes,
    failures,
    summary: { dialogVisible: probe.opacity === '1' && probe.rect.width > 0, cancelPresent: probe.buttons.includes('取消'), writeCount: writes.length, requestFailureCount: failures.length }
  };
  const downloadPromise = page.waitForEvent('download');
  await page.evaluate(text => {
    const url = URL.createObjectURL(new Blob([text], { type: 'application/json;charset=utf-8' }));
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'danger-cancel-visual.json';
    document.body.appendChild(anchor); anchor.click(); anchor.remove(); setTimeout(() => URL.revokeObjectURL(url), 0);
  }, JSON.stringify(evidence, null, 2));
  await (await downloadPromise).saveAs(`${root}/json/danger-cancel-visual.json`);
  return evidence.summary;
}
