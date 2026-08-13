async page => {
  const root = '/home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization2-20260813/final-pass/1920';
  const out = `${root}/json/tab-focus-extended.json`;
  const shots = `${root}/screenshots`;
  const consoleEvents = [];
  const requests = [];
  page.on('console', message => consoleEvents.push({ type: message.type(), text: message.text() }));
  page.on('request', request => {
    if (request.url().includes('/api/')) requests.push({ method: request.method(), url: request.url().replace(/([?&][^=&#]+)=([^&#]*)/g, '$1=<redacted>') });
  });
  const waitStable = async () => {
    await page.waitForTimeout(1200);
    await page.evaluate(() => document.fonts?.ready);
  };
  const snapshot = async label => page.evaluate(label => {
    const activeLabel = document.querySelector('.workspace-tabs__label[aria-current="page"]');
    const activeTab = activeLabel?.closest('.workspace-tabs__tab');
    const scroll = document.querySelector('.workspace-tabs__scroll');
    const sr = scroll?.getBoundingClientRect();
    const ar = activeTab?.getBoundingClientRect();
    return {
      label,
      hash: location.hash,
      activeText: activeLabel?.textContent?.trim(),
      activeTabKey: activeTab?.dataset.tabKey,
      focus: {
        tag: document.activeElement?.tagName,
        id: document.activeElement?.id,
        className: document.activeElement?.className,
        ariaLabel: document.activeElement?.getAttribute?.('aria-label'),
        text: document.activeElement?.textContent?.trim()?.slice(0, 80)
      },
      focusIsActiveLabel: document.activeElement === activeLabel,
      focusIsMain: document.activeElement?.id === 'app-main',
      activeFullyVisible: Boolean(sr && ar && ar.left >= sr.left - 1 && ar.right <= sr.right + 1),
      scrollLeft: scroll?.scrollLeft,
      scrollClientWidth: scroll?.clientWidth,
      scrollWidth: scroll?.scrollWidth
    };
  }, label);
  const evidence = {
    gitHead: '4f5d9ceb7ef5a53b39b02f79bfd3c9abd6039eb4',
    viewport: { width: 1920, height: 1080 },
    ordinaryNavigation: null,
    backgroundClose: null,
    userFocusOverride: null,
    summary: null
  };

  await page.evaluate(() => { location.hash = '/system/config'; });
  await waitStable();
  evidence.ordinaryNavigation = await snapshot('ordinary-route-navigation-stable');
  await page.screenshot({ path: `${shots}/69-ordinary-navigation-focus-main.png`, mask: [page.locator('.el-table__body:visible')], maskColor: '#d8dee9' });

  await page.evaluate(() => { location.hash = '/system/audit'; });
  await waitStable();
  const beforeBackground = await snapshot('before-background-close');
  const backgroundTab = page.locator('.workspace-tabs__tab[data-tab-key="/system/jobs"]');
  const backgroundExisted = await backgroundTab.count() > 0;
  if (backgroundExisted) {
    await backgroundTab.locator('.workspace-tabs__close').click();
    await waitStable();
  }
  evidence.backgroundClose = {
    backgroundExisted,
    before: beforeBackground,
    after: await snapshot('background-close-stable')
  };
  await page.screenshot({ path: `${shots}/70-background-close-focus-current.png`, mask: [page.locator('.el-table__body:visible')], maskColor: '#d8dee9' });

  await page.evaluate(() => { location.hash = '/system/config'; });
  await waitStable();
  const userOverrideResult = await page.evaluate(() => {
    const activeKey = document.querySelector('.workspace-tabs__tab--active')?.dataset.tabKey;
    const background = [...document.querySelectorAll('.workspace-tabs__tab')]
      .find(tab => tab.dataset.tabKey !== activeKey && tab.querySelector('.workspace-tabs__close'));
    const close = background?.querySelector('.workspace-tabs__close');
    const focusTarget = document.querySelector('[data-testid="sidebar-toggle"]');
    if (!close || !focusTarget) return { exercised: false, activeKey, backgroundKey: background?.dataset.tabKey };
    close.click();
    focusTarget.focus();
    return { exercised: true, activeKey, backgroundKey: background.dataset.tabKey, focusTarget: 'sidebar-toggle' };
  });
  await waitStable();
  evidence.userFocusOverride = {
    syntheticFocusOnly: true,
    action: userOverrideResult,
    after: await snapshot('user-focus-override-stable')
  };
  await page.screenshot({ path: `${shots}/71-user-focus-not-stolen.png`, mask: [page.locator('.el-table__body:visible')], maskColor: '#d8dee9' });

  const nonDynamicWarnings = consoleEvents.filter(item => item.type === 'warning' && !item.text.includes('[Vue Router warn]: No match found for location with path'));
  const writes = requests.filter(item => !['GET', 'HEAD', 'OPTIONS'].includes(item.method));
  evidence.summary = {
    consoleErrors: consoleEvents.filter(item => item.type === 'error').length,
    nonDynamicWarnings: nonDynamicWarnings.length,
    writes: writes.length,
    ordinaryNavigationMain: evidence.ordinaryNavigation?.focusIsMain === true,
    backgroundCloseCurrent: evidence.backgroundClose?.backgroundExisted === true
      && evidence.backgroundClose.after.hash === evidence.backgroundClose.before.hash
      && evidence.backgroundClose.after.focusIsActiveLabel === true,
    userFocusNotStolen: evidence.userFocusOverride?.action?.exercised === true
      && evidence.userFocusOverride.after.focus.ariaLabel === '折叠侧边导航'
  };
  const downloadPromise = page.waitForEvent('download');
  await page.evaluate(text => {
    const url = URL.createObjectURL(new Blob([text], { type: 'application/json;charset=utf-8' }));
    const a = document.createElement('a'); a.href = url; a.download = 'tab-focus-extended.json';
    document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 0);
  }, JSON.stringify(evidence, null, 2));
  await (await downloadPromise).saveAs(out);
  return evidence.summary;
}
