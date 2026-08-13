async page => {
  const root = '/home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization2-20260813/final-pass/1920';
  const out = `${root}/json/tab-focus-diagnostic.json`;
  const shots = `${root}/screenshots`;
  const sample = async label => page.evaluate(label => {
    const scroll = document.querySelector('.workspace-tabs__scroll');
    const active = document.querySelector('.workspace-tabs__tab--active');
    const sr = scroll?.getBoundingClientRect(); const ar = active?.getBoundingClientRect();
    return {
      label, hash: location.hash, scrollLeft: scroll?.scrollLeft, scrollClientWidth: scroll?.clientWidth,
      scrollWidth: scroll?.scrollWidth, scrollRect: sr && { left: sr.left, right: sr.right, width: sr.width },
      activeText: active?.querySelector('.workspace-tabs__label')?.textContent.trim(),
      activeRect: ar && { left: ar.left, right: ar.right, width: ar.width },
      activeFullyVisible: Boolean(sr && ar && ar.left >= sr.left - 1 && ar.right <= sr.right + 1),
      activeElement: { tag: document.activeElement?.tagName, className: document.activeElement?.className, text: document.activeElement?.textContent?.trim()?.slice(0, 80) },
      activeElementIsCurrentLabel: document.activeElement === document.querySelector('.workspace-tabs__label[aria-current="page"]')
    };
  }, label);
  const evidence = { viewport: { width: 1920, height: 1080 }, routeSwitchTimeline: [], clickCloseTimeline: [], keyboardCloseTimeline: [] };
  evidence.routeSwitchTimeline.push(await sample('before-switch'));
  await page.evaluate(() => { location.hash = '/system/person-tags'; });
  evidence.routeSwitchTimeline.push(await sample('immediate-after-hash'));
  await page.evaluate(() => Promise.resolve()); evidence.routeSwitchTimeline.push(await sample('after-microtask'));
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))); evidence.routeSwitchTimeline.push(await sample('after-two-animation-frames'));
  await page.waitForTimeout(100); evidence.routeSwitchTimeline.push(await sample('after-100ms'));
  await page.waitForTimeout(500); evidence.routeSwitchTimeline.push(await sample('after-600ms'));
  await page.waitForTimeout(600); evidence.routeSwitchTimeline.push(await sample('after-1200ms'));
  await page.locator('.workspace-tabs__tab--active .workspace-tabs__label').focus();
  evidence.clickCloseTimeline.push(await sample('label-focused-before-click-close'));
  await page.screenshot({ path: `${shots}/66-active-tab-focused-before-close.png`, mask: [page.locator('.el-table__body:visible')], maskColor: '#d8dee9' });
  await page.locator('.workspace-tabs__tab--active .workspace-tabs__close').click();
  evidence.clickCloseTimeline.push(await sample('immediate-after-click-close'));
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))); evidence.clickCloseTimeline.push(await sample('after-two-animation-frames'));
  await page.waitForTimeout(100); evidence.clickCloseTimeline.push(await sample('after-100ms'));
  await page.waitForTimeout(500); evidence.clickCloseTimeline.push(await sample('after-600ms'));
  await page.waitForTimeout(600); evidence.clickCloseTimeline.push(await sample('after-1200ms'));
  await page.screenshot({ path: `${shots}/67-after-click-close-focus-adjacent.png`, mask: [page.locator('.el-table__body:visible')], maskColor: '#d8dee9' });
  await page.evaluate(() => { location.hash = '/system/notifications'; }); await page.waitForTimeout(1200);
  const keyboardClose = page.locator('.workspace-tabs__tab--active .workspace-tabs__close'); await keyboardClose.focus();
  evidence.keyboardCloseTimeline.push(await sample('close-button-focused-before-enter'));
  await page.keyboard.press('Enter');
  evidence.keyboardCloseTimeline.push(await sample('immediate-after-enter'));
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))); evidence.keyboardCloseTimeline.push(await sample('after-two-animation-frames'));
  await page.waitForTimeout(100); evidence.keyboardCloseTimeline.push(await sample('after-100ms'));
  await page.waitForTimeout(500); evidence.keyboardCloseTimeline.push(await sample('after-600ms'));
  await page.waitForTimeout(600); evidence.keyboardCloseTimeline.push(await sample('after-1200ms'));
  await page.screenshot({ path: `${shots}/68-after-keyboard-close-focus-adjacent.png`, mask: [page.locator('.el-table__body:visible')], maskColor: '#d8dee9' });
  evidence.conclusion = {
    routeSwitchStableVisible: evidence.routeSwitchTimeline.at(-1).activeFullyVisible,
    clickCloseStableFocusedAdjacent: evidence.clickCloseTimeline.at(-1).activeElementIsCurrentLabel,
    keyboardCloseStableFocusedAdjacent: evidence.keyboardCloseTimeline.at(-1).activeElementIsCurrentLabel,
    stableClickFocusClass: evidence.clickCloseTimeline.at(-1).activeElement.className,
    stableKeyboardFocusClass: evidence.keyboardCloseTimeline.at(-1).activeElement.className
  };
  const downloadPromise = page.waitForEvent('download');
  await page.evaluate(text => {
    const url = URL.createObjectURL(new Blob([text], { type: 'application/json;charset=utf-8' }));
    const a = document.createElement('a'); a.href = url; a.download = 'tab-focus-diagnostic.json'; document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 0);
  }, JSON.stringify(evidence, null, 2));
  await (await downloadPromise).saveAs(out);
  return evidence.conclusion;
}
