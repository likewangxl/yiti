async page => {
  const root = '/home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization2-20260813/final-pass/1920';
  const out = `${root}/json/shell-tabs-actions.json`;
  const shots = `${root}/screenshots`;
  const safe = new Set(['GET', 'HEAD', 'OPTIONS']);
  const consoleEvents = [];
  const requests = [];
  const requestMap = new Map();
  page.on('console', message => consoleEvents.push({ type: message.type(), text: message.text() }));
  page.on('request', request => {
    if (!request.url().includes('/api/')) return;
    const item = { method: request.method(), url: request.url(), status: null, failure: null };
    requestMap.set(request, item); requests.push(item);
  });
  page.on('response', response => { const item = requestMap.get(response.request()); if (item) item.status = response.status(); });
  page.on('requestfailed', request => { const item = requestMap.get(request); if (item) item.failure = request.failure()?.errorText || 'unknown'; });
  const wait = async (ms = 1000) => { await page.waitForTimeout(ms); await page.evaluate(() => document.fonts?.ready); };
  const save = async evidence => {
    const downloadPromise = page.waitForEvent('download');
    await page.evaluate(text => {
      const url = URL.createObjectURL(new Blob([text], { type: 'application/json;charset=utf-8' }));
      const a = document.createElement('a'); a.href = url; a.download = 'shell-tabs-actions.json';
      document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 0);
    }, JSON.stringify(evidence, null, 2));
    await (await downloadPromise).saveAs(out);
  };
  const hostProbe = async () => page.evaluate(() => {
    const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
    return [...document.querySelectorAll('[data-bp-row-actions-host]')].filter(vis).map((host, index) => {
      const shown = host.querySelector('[data-bp-row-actions-visible]'); const probe = host.querySelector('[data-bp-row-actions-probe]');
      return {
        index, mode: host.dataset.mode, measured: host.dataset.measured, available: host.clientWidth, required: probe?.scrollWidth || 0,
        visibleItems: [...shown.querySelectorAll('button,[role="button"]')].filter(vis).map(button => ({
          text: button.textContent?.trim(), ariaLabel: button.getAttribute('aria-label'),
          disabled: button.matches(':disabled,[aria-disabled="true"]'),
          danger: button.classList.contains('el-button--danger') || button.closest('.danger-item') != null
        })),
        probe: { ariaHidden: probe?.getAttribute('aria-hidden'), inert: probe?.hasAttribute('inert') && probe.inert, pointerEvents: getComputedStyle(probe).pointerEvents, visibility: getComputedStyle(probe).visibility, descendantTabIndexes: [...probe.querySelectorAll('button,a,input,[tabindex]')].map(e => e.tabIndex) }
      };
    });
  });
  const evidence = {
    batch: 'page-optimization2-final-1920-shell-tabs-actions', gitHead: '4f5d9ceb7ef5a53b39b02f79bfd3c9abd6039eb4',
    viewport: { width: 1920, height: 1080 }, syntheticLayoutOnly: null, dangerCancel: null,
    focusRoutes: {}, schemeDialog: null, shell: {}, tabs: {}, fatalIssue: null
  };
  evidence.shell.expanded = await page.evaluate(() => {
    const header = document.querySelector('.hdr'); const sidebar = document.querySelector('#app-sidebar');
    const logo = sidebar?.querySelector('.logo'); const toggle = sidebar?.querySelector('[data-testid="sidebar-toggle"]');
    const nav = document.querySelector('.workspace-tabs'); const content = document.querySelector('.content');
    toggle?.focus(); const focusStyle = toggle ? getComputedStyle(toggle) : null;
    return {
      headerHeight: Math.round(header?.getBoundingClientRect().height || 0), sidebarWidth: Math.round(sidebar?.getBoundingClientRect().width || 0),
      logoHeight: Math.round(logo?.getBoundingClientRect().height || 0), logoMarkVisible: Boolean(logo?.querySelector('.mark')?.getClientRects().length), logoNameVisible: Boolean(logo?.querySelector('.logo-name')?.getClientRects().length),
      toggle: { tag: toggle?.tagName, ariaExpanded: toggle?.getAttribute('aria-expanded'), ariaLabel: toggle?.getAttribute('aria-label'), ariaControls: toggle?.getAttribute('aria-controls'), width: Math.round(toggle?.getBoundingClientRect().width || 0), height: Math.round(toggle?.getBoundingClientRect().height || 0), outlineStyle: focusStyle?.outlineStyle, outlineWidth: focusStyle?.outlineWidth },
      workspaceNavGlobalCount: document.querySelectorAll('nav.workspace-tabs').length, workspaceNavInHeaderCount: header?.querySelectorAll('nav.workspace-tabs').length || 0,
      workspaceNavHeight: Math.round(nav?.getBoundingClientRect().height || 0), contentTop: Math.round(content?.getBoundingClientRect().top || 0), oldStandalone48RowPresent: Boolean(header && content && Math.round(content.getBoundingClientRect().top - header.getBoundingClientRect().bottom) === 48)
    };
  });
  await page.screenshot({ path: `${shots}/60-shell-expanded.png`, mask: [page.locator('.el-table__body:visible')], maskColor: '#d8dee9' });
  await page.locator('#app-sidebar [data-testid="sidebar-toggle"]').click(); await wait(300);
  evidence.shell.collapsed = await page.evaluate(() => {
    const sidebar = document.querySelector('#app-sidebar'); const logo = sidebar?.querySelector('.logo'); const toggle = logo?.querySelector('[data-testid="sidebar-toggle"]');
    const sr = sidebar?.getBoundingClientRect(); const tr = toggle?.getBoundingClientRect();
    return { sidebarWidth: Math.round(sr?.width || 0), logoMarkVisible: Boolean(logo?.querySelector('.mark')?.getClientRects().length), logoNameVisible: Boolean(logo?.querySelector('.logo-name')?.getClientRects().length), ariaExpanded: toggle?.getAttribute('aria-expanded'), ariaLabel: toggle?.getAttribute('aria-label'), ariaControls: toggle?.getAttribute('aria-controls'), toggleCenteredDelta: sr && tr ? Math.round((tr.left + tr.width / 2) - (sr.left + sr.width / 2)) : null };
  });
  await page.screenshot({ path: `${shots}/61-shell-collapsed.png`, mask: [page.locator('.el-table__body:visible')], maskColor: '#d8dee9' });
  await page.locator('#app-sidebar [data-testid="sidebar-toggle"]').click(); await wait(300);
  evidence.tabs.initial = await page.evaluate(() => {
    const scroll = document.querySelector('.workspace-tabs__scroll'); const tabs = [...document.querySelectorAll('.workspace-tabs__tab')];
    const active = document.querySelector('.workspace-tabs__tab--active'); const sr = scroll?.getBoundingClientRect(); const ar = active?.getBoundingClientRect();
    return {
      count: tabs.length, activeCount: document.querySelectorAll('.workspace-tabs__tab--active').length,
      ariaCurrentCount: document.querySelectorAll('.workspace-tabs__label[aria-current="page"]').length,
      horizontalOverflow: Boolean(scroll && scroll.scrollWidth > scroll.clientWidth + 1), activeVisible: Boolean(sr && ar && ar.left >= sr.left - 1 && ar.right <= sr.right + 1),
      itemHeights: [...new Set(tabs.map(e => Math.round(e.getBoundingClientRect().height)))],
      longTruncatedCount: tabs.filter(tab => { const label = tab.querySelector('.workspace-tabs__label'); return label && label.scrollWidth > label.clientWidth + 1; }).length,
      allLabelsHaveFullTitle: tabs.every(tab => { const label = tab.querySelector('.workspace-tabs__label'); return !label || label.getAttribute('title') === label.textContent.trim(); })
    };
  });
  const activeLabel = page.locator('.workspace-tabs__tab--active .workspace-tabs__label'); await activeLabel.focus();
  await page.keyboard.press('Home'); evidence.tabs.afterHomeFocus = await page.evaluate(() => ({ text: document.activeElement?.textContent?.trim(), first: document.activeElement === document.querySelector('.workspace-tabs__label') }));
  await page.keyboard.press('End'); evidence.tabs.afterEndFocus = await page.evaluate(() => ({ text: document.activeElement?.textContent?.trim(), last: document.activeElement === [...document.querySelectorAll('.workspace-tabs__label')].at(-1) }));
  await page.keyboard.press('ArrowLeft'); evidence.tabs.afterArrowLeftFocus = await page.evaluate(() => ({ text: document.activeElement?.textContent?.trim(), index: [...document.querySelectorAll('.workspace-tabs__label')].indexOf(document.activeElement), total: document.querySelectorAll('.workspace-tabs__label').length }));
  await page.locator('.workspace-tabs__tab--active .workspace-tabs__close').click(); await wait(900);
  evidence.tabs.afterClose = await page.evaluate(() => ({ hash: location.hash, focusText: document.activeElement?.classList.contains('workspace-tabs__label') ? document.activeElement.textContent.trim() : null, activeText: document.querySelector('.workspace-tabs__label[aria-current="page"]')?.textContent?.trim(), count: document.querySelectorAll('.workspace-tabs__tab').length }));
  await page.screenshot({ path: `${shots}/62-header-many-tabs.png`, mask: [page.locator('.el-table__body:visible')], maskColor: '#d8dee9' });
  const focus = [
    ['InfoProducts', '/info/products'], ['EvalTasks', '/eval/tasks'], ['SysUsers', '/system/users'],
    ['SysJobs', '/system/jobs'], ['SysNotifications', '/system/notifications'], ['SysResources', '/system/resources'],
    ['ScreenAdminDs', '/screen-admin/datasources'], ['ReportSql', '/report/sql']
  ];
  for (const [name, path] of focus) {
    await page.evaluate(value => { location.hash = value; }, path); await wait(1200);
    evidence.focusRoutes[name] = { hosts: await hostProbe(), operationCellsVisible: await page.locator('td.operation-cell:visible').count() };
    await page.screenshot({ path: `${shots}/focus-${name}.png`, mask: [page.locator('.el-table__body td:not(.operation-cell):visible,input,textarea')], maskColor: '#d8dee9' });
  }
  await page.evaluate(() => { location.hash = '/info/products'; }); await wait(1200);
  const requestBeforeSynthetic = requests.length;
  evidence.syntheticLayoutOnly = await page.evaluate(async () => {
    const host = [...document.querySelectorAll('[data-bp-row-actions-host]')].find(e => e.getBoundingClientRect().width > 0 && e.getBoundingClientRect().height > 0);
    if (!host) return { status: 'not_exercised_no_real_host' };
    const cell = host.closest('td')?.querySelector('.cell') || host.parentElement; const beforeCss = cell.getAttribute('style');
    const snapshot = () => { const probe = host.querySelector('[data-bp-row-actions-probe]'); return { mode: host.dataset.mode, available: host.clientWidth, required: probe?.scrollWidth || 0, visible: [...host.querySelectorAll('[data-bp-row-actions-visible] button')].filter(e => e.getBoundingClientRect().width > 0).map(e => e.textContent.trim()) }; };
    const before = snapshot(); cell.style.width = '72px'; cell.style.maxWidth = '72px'; cell.style.minWidth = '0';
    await new Promise(r => setTimeout(r, 500)); const narrowed = snapshot();
    if (beforeCss == null) cell.removeAttribute('style'); else cell.setAttribute('style', beforeCss);
    await new Promise(r => setTimeout(r, 500)); const restored = snapshot();
    return { status: 'synthetic-layout-only', target: 'InfoProducts first real visible host parent cell', before, narrowed, restored, sourceOrDataChanged: false };
  });
  evidence.syntheticLayoutOnly.requestCountDelta = requests.length - requestBeforeSynthetic;
  await page.screenshot({ path: `${shots}/63-products-restored.png`, mask: [page.locator('.el-table__body td:not(.operation-cell):visible')], maskColor: '#d8dee9' });
  const dangerRequestStart = requests.length;
  evidence.dangerCancel = { status: 'not_exercised_no_visible_danger' };
  const dangerButton = page.locator('[data-bp-row-actions-visible] .el-button--danger:visible').filter({ hasText: '删除' }).first();
  if (await dangerButton.count()) {
    await dangerButton.click(); const box = page.locator('.el-message-box:visible'); await box.waitFor({ state: 'visible', timeout: 4000 });
    await page.screenshot({ path: `${shots}/64-danger-confirm-cancel.png`, mask: [page.locator('.el-message-box__message'), page.locator('.el-table__body:visible')], maskColor: '#d8dee9' });
    const cancel = box.getByRole('button', { name: /取消/ }); await cancel.click(); await box.waitFor({ state: 'hidden' });
    evidence.dangerCancel = { status: 'opened_and_cancelled', source: 'expanded visible danger button', writeRequestDelta: requests.slice(dangerRequestStart).filter(r => !safe.has(r.method)).length };
  }
  await page.evaluate(() => { location.hash = '/report/dynamic'; }); await wait(1200);
  await page.getByRole('button', { name: /我的方案/ }).click(); const scheme = page.locator('.el-dialog:visible').filter({ hasText: '我的查询方案' }); await scheme.waitFor({ state: 'visible', timeout: 4000 }); await wait(700);
  evidence.schemeDialog = { title: await scheme.locator('.el-dialog__title').textContent(), realRows: await scheme.locator('.el-table__body tr:visible').count(), hosts: await hostProbe() };
  if (!evidence.schemeDialog.realRows) evidence.schemeDialog.status = 'not_exercised_no_real_rows'; else evidence.schemeDialog.status = 'exercised_real_rows';
  await page.screenshot({ path: `${shots}/65-scheme-dialog.png`, mask: [scheme.locator('.el-table__body')], maskColor: '#d8dee9' });
  await scheme.getByRole('button', { name: '关闭', exact: true }).click(); await scheme.waitFor({ state: 'hidden' });
  const badConsole = consoleEvents.filter(item => item.type === 'error' || (item.type === 'warning' && !item.text.includes('[Vue Router warn]: No match found for location with path')) || item.text.includes('[api fallback]'));
  const badNetwork = requests.filter(item => item.failure || item.status == null || item.status >= 400 || !safe.has(item.method));
  evidence.summary = { consoleErrors: consoleEvents.filter(i => i.type === 'error').length, fallbacks: consoleEvents.filter(i => i.text.includes('[api fallback]')).length, nonDynamicWarnings: badConsole.filter(i => i.type === 'warning').length, resizeObserverLoops: consoleEvents.filter(i => /ResizeObserver loop/i.test(i.text)).length, requestCount: requests.length, badNetwork: badNetwork.length, unexpectedWrites: requests.filter(i => !safe.has(i.method)).length };
  const shell = evidence.shell; const tabs = evidence.tabs;
  const issues = [];
  if (shell.expanded.headerHeight !== 52 || shell.expanded.sidebarWidth !== 220 || shell.expanded.workspaceNavGlobalCount !== 1 || shell.expanded.workspaceNavInHeaderCount !== 1 || shell.expanded.oldStandalone48RowPresent) issues.push('expanded-shell');
  if (shell.collapsed.sidebarWidth !== 64 || shell.collapsed.logoMarkVisible || shell.collapsed.logoNameVisible || shell.collapsed.ariaExpanded !== 'false' || shell.collapsed.toggleCenteredDelta !== 0) issues.push('collapsed-shell');
  if (!tabs.initial.horizontalOverflow || !tabs.initial.activeVisible || !tabs.initial.allLabelsHaveFullTitle || !tabs.afterHomeFocus.first || !tabs.afterEndFocus.last || tabs.afterArrowLeftFocus.index !== tabs.afterArrowLeftFocus.total - 2 || !tabs.afterClose.focusText || tabs.afterClose.focusText !== tabs.afterClose.activeText) issues.push('tabs-keyboard-focus');
  if (evidence.syntheticLayoutOnly.status !== 'synthetic-layout-only' || evidence.syntheticLayoutOnly.before.mode !== 'expanded' || evidence.syntheticLayoutOnly.narrowed.mode !== 'compact' || evidence.syntheticLayoutOnly.restored.mode !== 'expanded' || evidence.syntheticLayoutOnly.requestCountDelta) issues.push('synthetic-compact-restore');
  if (evidence.dangerCancel.status !== 'opened_and_cancelled' || evidence.dangerCancel.writeRequestDelta) issues.push('danger-confirm-cancel');
  if (badConsole.length || badNetwork.length) issues.push('console-or-network');
  evidence.fatalIssue = issues.length ? { issues } : null;
  await save(evidence);
  return { shell: evidence.shell, tabs: evidence.tabs, synthetic: evidence.syntheticLayoutOnly, danger: evidence.dangerCancel, scheme: evidence.schemeDialog, summary: evidence.summary, fatalIssue: evidence.fatalIssue };
}
