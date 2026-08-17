async page => {
  const root = '/home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization2-20260813/final-pass/1920';
  const out = `${root}/json/designer-readonly.json`;
  const shots = `${root}/screenshots`;
  const context = page.context();
  const main = page;
  const safeMethods = new Set(['GET', 'HEAD', 'OPTIONS']);
  const consoleEvents = [];
  const requests = [];
  const requestMap = new Map();
  const attachPage = candidate => candidate.on('console', message => consoleEvents.push({
    page: candidate === main ? 'main' : 'designer', type: message.type(), text: message.text()
  }));
  context.pages().forEach(attachPage);
  context.on('page', attachPage);
  context.on('request', request => {
    if (!request.url().includes('/api/')) return;
    const item = {
      method: request.method(),
      url: request.url().replace(/\/\d+(?=\/|[?#]|$)/g, '/<redacted-id>').replace(/([?&][^=&#]+)=([^&#]*)/g, '$1=<redacted>'),
      status: null,
      failure: null
    };
    requestMap.set(request, item);
    requests.push(item);
  });
  context.on('response', response => {
    const item = requestMap.get(response.request());
    if (item) item.status = response.status();
  });
  context.on('requestfailed', request => {
    const item = requestMap.get(request);
    if (item) item.failure = request.failure()?.errorText || 'unknown';
  });
  const waitStable = async candidate => {
    await candidate.waitForTimeout(1600);
    await candidate.evaluate(() => document.fonts?.ready);
  };
  const shellProbe = candidate => candidate.evaluate(() => ({
    url: location.href,
    name: window.name,
    hasOpener: Boolean(window.opener),
    hasFocus: document.hasFocus(),
    layout: document.querySelectorAll('.layout').length,
    sidebar: document.querySelectorAll('#app-sidebar').length,
    header: document.querySelectorAll('.hdr').length,
    workspaceTabs: document.querySelectorAll('.workspace-tabs').length,
    designer: document.querySelectorAll('.dsn2').length,
    dirtyText: document.querySelector('.dirty-state')?.textContent?.trim()
  }));
  const evidence = {
    gitHead: '4f5d9ceb7ef5a53b39b02f79bfd3c9abd6039eb4',
    viewport: { width: 1920, height: 1080 },
    fixedWindowName: 'yiti-screen-designer',
    popupOpen: null,
    popupReuse: null,
    cleanReturn: null,
    directUrlFallback: null,
    dirtyCancel: null,
    dirtyDiscard: null,
    beforeUnloadProbe: null,
    network: null,
    console: null,
    summary: null
  };
  const designerLink = () => main.locator('a[href="#/screen-admin/designer"]:visible');

  await main.evaluate(() => { location.hash = '/system/config'; });
  await waitStable(main);
  const mainBefore = {
    url: main.url(),
    pageCount: context.pages().length,
    tabCount: await main.locator('.workspace-tabs__tab').count()
  };
  const popupPromise = context.waitForEvent('page', { timeout: 5000 });
  await designerLink().click();
  const popup = await popupPromise;
  await popup.waitForLoadState('domcontentloaded');
  await waitStable(popup);
  const toolLabels = ['新建', '编辑范围', '管理查看角色', '撤销', '重做', '适应窗口', '预览草稿', '放弃草稿', '回滚', '保存', '发布', '返回工作区'];
  const tools = {};
  for (const label of toolLabels) tools[label] = await popup.getByRole('button', { name: label, exact: true }).count();
  evidence.popupOpen = {
    mainBefore,
    mainAfter: { url: main.url(), pageCount: context.pages().length, tabCount: await main.locator('.workspace-tabs__tab').count() },
    popup: await shellProbe(popup),
    returnAriaLabel: await popup.getByRole('button', { name: '返回工作区' }).getAttribute('aria-label'),
    tools
  };
  await popup.screenshot({ path: `${shots}/72-designer-independent-popup.png`, mask: [popup.locator('.dsn2-center')], maskColor: '#d8dee9' });

  await main.bringToFront();
  const pagesBeforeReuse = context.pages().length;
  await designerLink().click();
  await waitStable(popup);
  evidence.popupReuse = {
    pagesBefore: pagesBeforeReuse,
    pagesAfter: context.pages().length,
    samePopupStillOpen: !popup.isClosed(),
    popup: await shellProbe(popup)
  };

  const cleanWriteStart = requests.length;
  evidence.cleanReturn = { before: await shellProbe(popup) };
  const cleanClosePromise = popup.waitForEvent('close', { timeout: 5000 });
  await popup.getByRole('button', { name: '返回工作区' }).click();
  await cleanClosePromise;
  evidence.cleanReturn.after = {
    closed: popup.isClosed(),
    mainUrl: main.url(),
    writeDelta: requests.slice(cleanWriteStart).filter(item => !safeMethods.has(item.method)).length
  };

  await main.bringToFront();
  await main.evaluate(() => { location.hash = '/screen-admin/designer'; });
  await waitStable(main);
  evidence.directUrlFallback = { before: await shellProbe(main) };
  await main.getByRole('button', { name: '返回工作区' }).click();
  await waitStable(main);
  evidence.directUrlFallback.after = {
    closed: main.isClosed(),
    url: main.url(),
    workspaceShell: await main.locator('.layout').count(),
    mainFocus: await main.evaluate(() => document.activeElement?.id === 'app-main')
  };
  await main.screenshot({ path: `${shots}/73-designer-direct-url-fallback-workspace.png`, mask: [main.locator('.el-table__body:visible')], maskColor: '#d8dee9' });

  const dirtyPopupPromise = context.waitForEvent('page', { timeout: 5000 });
  await designerLink().click();
  const dirtyPopup = await dirtyPopupPromise;
  await dirtyPopup.waitForLoadState('domcontentloaded');
  await waitStable(dirtyPopup);
  const activeBackgroundType = await dirtyPopup.locator('.el-radio-button.is-active').first().textContent();
  const nextBackgroundType = activeBackgroundType?.trim() === '渐变' ? '纯色' : '渐变';
  const dirtyRequestStart = requests.length;
  await dirtyPopup.locator('.el-radio-button').filter({ hasText: nextBackgroundType }).click();
  await dirtyPopup.getByText('有未保存修改', { exact: true }).waitFor({ state: 'visible', timeout: 4000 });
  evidence.beforeUnloadProbe = await dirtyPopup.evaluate(() => {
    const event = new Event('beforeunload', { cancelable: true });
    const dispatchResult = window.dispatchEvent(event);
    return { syntheticLocalDirtyOnly: true, defaultPrevented: event.defaultPrevented, dispatchResult, returnValue: event.returnValue };
  });

  await dirtyPopup.getByRole('button', { name: '返回工作区' }).click();
  const exitDialog = dirtyPopup.getByRole('dialog', { name: '未保存修改' });
  await exitDialog.waitFor({ state: 'visible', timeout: 4000 });
  await dirtyPopup.waitForTimeout(300);
  const exitButtons = {};
  for (const label of ['取消', '放弃并关闭', '保存并关闭']) {
    exitButtons[label] = await exitDialog.getByRole('button', { name: label, exact: true }).count();
  }
  evidence.dirtyCancel = {
    activeBackgroundType: activeBackgroundType?.trim(),
    changedTo: nextBackgroundType,
    dirtyText: await dirtyPopup.locator('.dirty-state').textContent(),
    exitButtons,
    initialFocusText: await dirtyPopup.evaluate(() => document.activeElement?.textContent?.trim())
  };
  await dirtyPopup.screenshot({ path: `${shots}/74-designer-dirty-three-branches.png` });
  await exitDialog.getByRole('button', { name: '取消', exact: true }).click();
  await exitDialog.waitFor({ state: 'hidden' });
  await dirtyPopup.waitForTimeout(300);
  evidence.dirtyCancel.after = {
    open: !dirtyPopup.isClosed(),
    dirtyText: await dirtyPopup.locator('.dirty-state').textContent(),
    focusAriaLabel: await dirtyPopup.evaluate(() => document.activeElement?.getAttribute?.('aria-label')),
    writeDelta: requests.slice(dirtyRequestStart).filter(item => !safeMethods.has(item.method)).length
  };

  await dirtyPopup.getByRole('button', { name: '返回工作区' }).click();
  await exitDialog.waitFor({ state: 'visible', timeout: 4000 });
  const discardStart = requests.length;
  const discardClosePromise = dirtyPopup.waitForEvent('close', { timeout: 5000 });
  await exitDialog.getByRole('button', { name: '放弃并关闭', exact: true }).click();
  await discardClosePromise;
  evidence.dirtyDiscard = {
    closed: dirtyPopup.isClosed(),
    writeDelta: requests.slice(discardStart).filter(item => !safeMethods.has(item.method)).length,
    saveEndpointCalls: requests.slice(dirtyRequestStart).filter(item => /\/canvas(?:[?#]|$)/.test(item.url) && !safeMethods.has(item.method)).length
  };

  await main.bringToFront();
  const browserCloseWarnings = consoleEvents.filter(item => item.type === 'warning' && /Scripts may close only/i.test(item.text));
  const dynamicWarnings = consoleEvents.filter(item => item.type === 'warning' && item.text.includes('[Vue Router warn]: No match found for location with path'));
  const nonDynamicWarnings = consoleEvents.filter(item => item.type === 'warning'
    && !item.text.includes('[Vue Router warn]: No match found for location with path')
    && !/Scripts may close only/i.test(item.text));
  const errors = consoleEvents.filter(item => item.type === 'error');
  const fallbacks = consoleEvents.filter(item => item.text.includes('[api fallback]'));
  const badNetwork = requests.filter(item => item.failure || item.status == null || item.status >= 400);
  const writes = requests.filter(item => !safeMethods.has(item.method));
  evidence.network = { requests, badCount: badNetwork.length, writeCount: writes.length };
  evidence.console = {
    errors,
    fallbacks,
    nonDynamicWarnings,
    browserCloseWarnings,
    dynamicWarningCount: dynamicWarnings.length
  };
  evidence.summary = {
    popupFixedName: evidence.popupOpen.popup.name === evidence.fixedWindowName,
    mainUnchangedOnOpen: evidence.popupOpen.mainBefore.url === evidence.popupOpen.mainAfter.url
      && evidence.popupOpen.mainBefore.tabCount === evidence.popupOpen.mainAfter.tabCount,
    independentNoShell: evidence.popupOpen.popup.designer === 1 && evidence.popupOpen.popup.layout === 0
      && evidence.popupOpen.popup.sidebar === 0 && evidence.popupOpen.popup.header === 0 && evidence.popupOpen.popup.workspaceTabs === 0,
    allToolsPresent: Object.values(evidence.popupOpen.tools).every(count => count === 1),
    popupReusedAndFocused: evidence.popupReuse.pagesBefore === evidence.popupReuse.pagesAfter
      && evidence.popupReuse.samePopupStillOpen && evidence.popupReuse.popup.hasFocus,
    cleanReturnClosed: evidence.cleanReturn.after.closed && evidence.cleanReturn.after.writeDelta === 0,
    directUrlFallbackWorkspace: evidence.directUrlFallback.before.hasOpener === false
      && evidence.directUrlFallback.after.closed === false && /#\/workspace$/.test(evidence.directUrlFallback.after.url)
      && evidence.directUrlFallback.after.workspaceShell === 1,
    beforeUnloadBlockedWhenDirty: evidence.beforeUnloadProbe.defaultPrevented === true && evidence.beforeUnloadProbe.dispatchResult === false,
    dirtyCancelPassed: evidence.dirtyCancel.after.open && /有未保存修改/.test(evidence.dirtyCancel.after.dirtyText)
      && evidence.dirtyCancel.after.focusAriaLabel === '返回工作区' && evidence.dirtyCancel.after.writeDelta === 0,
    dirtyDiscardNoSave: evidence.dirtyDiscard.closed && evidence.dirtyDiscard.writeDelta === 0 && evidence.dirtyDiscard.saveEndpointCalls === 0,
    consoleErrors: errors.length,
    fallbacks: fallbacks.length,
    nonDynamicWarnings: nonDynamicWarnings.length,
    badNetwork: badNetwork.length,
    unexpectedWrites: writes.length,
    browserCloseWarnings: browserCloseWarnings.length
  };
  const downloadPromise = main.waitForEvent('download');
  await main.evaluate(text => {
    const url = URL.createObjectURL(new Blob([text], { type: 'application/json;charset=utf-8' }));
    const a = document.createElement('a'); a.href = url; a.download = 'designer-readonly.json';
    document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 0);
  }, JSON.stringify(evidence, null, 2));
  await (await downloadPromise).saveAs(out);
  return evidence.summary;
}
