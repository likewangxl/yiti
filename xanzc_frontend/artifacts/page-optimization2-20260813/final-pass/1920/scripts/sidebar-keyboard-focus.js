async page => {
  const root = '/home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization2-20260813/final-pass/1920';
  await page.evaluate(() => { location.hash = '/system/config'; });
  await page.waitForTimeout(1200);
  await page.evaluate(() => {
    document.body.setAttribute('tabindex', '-1');
    document.body.focus();
    document.body.removeAttribute('tabindex');
  });
  const sequence = [];
  let reachedToggle = false;
  for (let index = 0; index < 8; index += 1) {
    await page.keyboard.press('Tab');
    const step = await page.evaluate(() => {
      const active = document.activeElement;
      return {
        tag: active?.tagName,
        testId: active?.getAttribute?.('data-testid'),
        ariaLabel: active?.getAttribute?.('aria-label'),
        focusVisible: active?.matches?.(':focus-visible') || false
      };
    });
    sequence.push(step);
    if (step.testId === 'sidebar-toggle') {
      reachedToggle = true;
      break;
    }
  }
  const result = await page.locator('[data-testid="sidebar-toggle"]').evaluate(element => {
    const style = getComputedStyle(element);
    return {
      active: document.activeElement === element,
      focusVisible: element.matches(':focus-visible'),
      ariaLabel: element.getAttribute('aria-label'),
      ariaExpanded: element.getAttribute('aria-expanded'),
      ariaControls: element.getAttribute('aria-controls'),
      outlineStyle: style.outlineStyle,
      outlineWidth: style.outlineWidth,
      outlineColor: style.outlineColor,
      outlineOffset: style.outlineOffset
    };
  });
  await page.screenshot({
    path: `${root}/screenshots/75-sidebar-keyboard-focus-visible.png`,
    mask: [page.locator('.el-table__body:visible')],
    maskColor: '#d8dee9'
  });
  const evidence = {
    gitHead: '4f5d9ceb7ef5a53b39b02f79bfd3c9abd6039eb4',
    viewport: { width: 1920, height: 1080 },
    input: 'official playwright-cli keyboard Tab after temporary focus-only body normalization',
    sequence,
    result,
    summary: {
      reachedToggle,
      keyboardFocusVisible: result.active && result.focusVisible,
      visibleTwoPixelOutline: result.outlineStyle === 'solid' && result.outlineWidth === '2px',
      ariaComplete: Boolean(result.ariaLabel) && ['true', 'false'].includes(result.ariaExpanded)
        && result.ariaControls === 'app-sidebar'
    }
  };
  const downloadPromise = page.waitForEvent('download');
  await page.evaluate(text => {
    const url = URL.createObjectURL(new Blob([text], { type: 'application/json;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'sidebar-keyboard-focus.json';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }, JSON.stringify(evidence, null, 2));
  await (await downloadPromise).saveAs(`${root}/json/sidebar-keyboard-focus.json`);
  return evidence.summary;
}
