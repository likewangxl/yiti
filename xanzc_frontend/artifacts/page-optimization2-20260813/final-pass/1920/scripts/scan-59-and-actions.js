async page => {
  const base = 'http://127.0.0.1:8091/';
  const root = '/home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization2-20260813/final-pass/1920';
  const out = `${root}/json/scan-59-and-actions.json`;
  const shots = `${root}/screenshots`;
  const safeMethods = new Set(['GET', 'HEAD', 'OPTIONS']);
  const expectedOperationColumns = {
    Workspace: 3, AnnouncementList: 1, InfoAddressBook: 1, InfoProducts: 1,
    InfoDocuments: 1, PerfMetrics: 1, PerfKpiRules: 2, PerfTargets: 3,
    PerfTargetValues: 1, PerfImport: 1, PerfAdjust: 5, PerfTaskMonitor: 1,
    EvalTags: 1, EvalUserTags: 1, EvalRules: 2, EvalTasks: 1, EvalMyTasks: 2,
    ReportFree: 1, ReportSql: 1, ReportAmasApprovals: 2, ScreenAdminDs: 1,
    ScreenAdminOrgProfiles: 1, GuaranteeQuery: 1, HistoryDataImport: 1,
    HistoryNotice: 1, HistoryPriceApproval: 1, SysUsers: 1, SysRoles: 1,
    SysResources: 1, SysPermission: 1, SysDict: 1, SysJobs: 1, SysAudit: 1,
    SysNotifications: 1, SysConfig: 3, SysFiles: 1, SysTimeoutRules: 1,
    SysAnnouncements: 1, SysWorkflowFlows: 1, SysWorkflowMonitor: 1,
    SysPersonTags: 3
  };
  const expectedAdaptiveHosts = new Set([
    'Workspace', 'InfoProducts', 'InfoDocuments', 'PerfMetrics', 'PerfKpiRules',
    'PerfTargets', 'PerfTargetValues', 'PerfImport', 'PerfAdjust', 'PerfTaskMonitor',
    'EvalTags', 'EvalRules', 'EvalTasks', 'ReportFree', 'ScreenAdminDs', 'HistoryDataImport',
    'SysUsers', 'SysRoles', 'SysResources', 'SysDict', 'SysJobs', 'SysNotifications',
    'SysFiles', 'SysWorkflowFlows', 'SysWorkflowMonitor', 'SysPersonTags'
  ]);
  const focusRoutes = new Set([
    'InfoProducts', 'EvalTasks', 'SysUsers', 'SysJobs', 'SysNotifications',
    'SysResources', 'ScreenAdminDs', 'ReportSql', 'ReportDynamic'
  ]);
  const specs = [
    { name: 'NoAccess', path: '/no-access' },
    { name: 'Workspace', path: '/workspace' },
    { name: 'AnnouncementList', path: '/workspace/announcements', discover: { key: 'announcement', url: '/api/portal/announcements?pageNo=1&pageSize=20', field: 'id' } },
    { name: 'AnnouncementDetail', template: '/announcement/:id', parameter: 'announcement' },
    { name: 'NotificationList', path: '/workspace/notifications' },
    { name: 'InfoNav', path: '/info/nav' },
    { name: 'InfoAddressBook', path: '/info/address-book' },
    { name: 'InfoProducts', path: '/info/products' },
    { name: 'InfoDocuments', path: '/info/documents' },
    { name: 'PerfMetrics', path: '/perf/metrics' },
    { name: 'PerfKpiRules', path: '/perf/kpi-rules' },
    { name: 'PerfTargets', path: '/perf/targets' },
    { name: 'PerfTargetValues', path: '/perf/target-values' },
    { name: 'PerfImport', path: '/perf/import' },
    { name: 'PerfAdjust', path: '/perf/adjust' },
    { name: 'PerfCompute', path: '/perf/compute' },
    { name: 'PerfTaskMonitor', path: '/perf/task-monitor' },
    { name: 'PerfKpiScoreDetail', path: '/perf/kpi-score-detail' },
    { name: 'EvalTags', path: '/eval/tags' },
    { name: 'EvalUserTags', path: '/eval/user-tags' },
    { name: 'EvalRules', path: '/eval/rules' },
    { name: 'EvalTasks', path: '/eval/tasks' },
    { name: 'EvalMyTasks', path: '/eval/my-tasks' },
    { name: 'ReportDynamic', path: '/report/dynamic' },
    { name: 'ReportDash', path: '/report/dashboard' },
    { name: 'ReportPresets', path: '/report/presets' },
    { name: 'ReportFree', path: '/report/free', discover: { key: 'freeBatch', url: '/api/reports/free/batches', field: 'id' } },
    { name: 'ReportFreeDetail', template: '/report/free/:batchId', parameter: 'freeBatch' },
    { name: 'ReportSql', path: '/report/sql' },
    { name: 'ReportAmasApprovals', path: '/report/amas-approvals', discover: { key: 'amas', url: '/api/reports/amas-approvals?pageNo=1&pageSize=20', field: 'perfAdjustNo' } },
    { name: 'ReportAmasApprovalDetail', template: '/report/amas-approvals/:perfAdjustNo', parameter: 'amas' },
    { name: 'ScreenAdminDs', path: '/screen-admin/datasources' },
    { name: 'ScreenAdminOrgProfiles', path: '/screen-admin/org-profiles' },
    { name: 'ScreenAdminOrgGroups', path: '/screen-admin/org-groups' },
    { name: 'GuaranteeQuery', path: '/guarantee/query' },
    { name: 'HistoryDataImport', path: '/guarantee/data-import' },
    { name: 'HistoryNotice', path: '/guarantee/notice' },
    { name: 'HistoryPriceApproval', path: '/history/price-approval', discover: { key: 'price', url: '/api/reports/amas-price-approvals?pageNo=1&pageSize=20', field: 'priceApprId' } },
    { name: 'HistoryPriceApprovalDetail', template: '/history/price-approval/:priceApprId', parameter: 'price' },
    { name: 'HistoryPerfAdjust', path: '/history/perf-adjust' },
    { name: 'SysUsers', path: '/system/users' },
    { name: 'SysRoles', path: '/system/roles' },
    { name: 'SysResources', path: '/system/resources' },
    { name: 'SysPermission', path: '/system/permission' },
    { name: 'SysDict', path: '/system/dict' },
    { name: 'SysCalendar', path: '/system/calendar' },
    { name: 'SysJobs', path: '/system/jobs' },
    { name: 'SysAudit', path: '/system/audit' },
    { name: 'SysNotifications', path: '/system/notifications' },
    { name: 'SysConfig', path: '/system/config' },
    { name: 'SysFiles', path: '/system/files' },
    { name: 'SysTimeoutRules', path: '/system/timeout-rules' },
    { name: 'SysAnnouncements', path: '/system/announcements', discover: { key: 'sysAnnouncement', url: '/api/admin/announcements?pageNo=1&pageSize=20', field: 'id' } },
    { name: 'SysAnnouncementDetail', template: '/system/announcements/:id', parameter: 'sysAnnouncement', query: 'admin=1' },
    { name: 'SysWorkflowFlows', path: '/system/workflow-flows', discover: { key: 'flow', url: '/api/admin/workflow/flows', field: 'id' } },
    { name: 'SysWorkflowFlowEdit', template: '/system/workflow-flows/:id', parameter: 'flow' },
    { name: 'SysWorkflowMonitor', path: '/system/workflow-monitor' },
    { name: 'SysPersonTags', path: '/system/person-tags' }
  ];
  const discoveries = {};
  const realIds = new Set();
  const allConsole = [];
  const allRequests = [];
  const requestMap = new Map();
  const pending = new Set();
  const evidence = {
    batch: 'page-optimization2-final-1920', gitHead: '4f5d9ceb7ef5a53b39b02f79bfd3c9abd6039eb4',
    viewport: { width: 1920, height: 1080 }, tool: 'node_modules/.bin/playwright-cli 0.1.18',
    browser: 'chromium', mockRoutes: 0, credentialsArchived: false, responseBodiesArchived: false,
    generatedAt: new Date().toISOString(), routes: [], parameterDiscovery: [], focusRoutes: {},
    naturalModes: { expanded: 0, compact: 0 }, runtimeVisible: { operationCells: 0, adaptiveHosts: 0 },
    expectedStaticMatrix: { namedRoutes: 59, physicalOperationColumns: 58, adaptiveColumns: 30 },
    stoppedEarly: false, fatalIssue: null
  };
  const visible = element => { const r = element.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  const sanitizeString = raw => {
    let value = String(raw);
    for (const id of realIds) {
      const esc = String(id).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      value = value.replace(new RegExp(`/${esc}(?=/|[?#]|$)`, 'g'), '/<redacted-id>');
    }
    return value.replace(/([?&][^=&#]+)=([^&#]*)/g, '$1=<redacted>');
  };
  const publicEvidence = () => JSON.parse(JSON.stringify(evidence, (key, value) => typeof value === 'string' ? sanitizeString(value) : value));
  const save = async () => {
    const downloadPromise = page.waitForEvent('download');
    await page.evaluate(text => {
      const url = URL.createObjectURL(new Blob([text], { type: 'application/json;charset=utf-8' }));
      const a = document.createElement('a'); a.href = url; a.download = 'scan-59-and-actions.json';
      document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 0);
    }, JSON.stringify(publicEvidence(), null, 2));
    await (await downloadPromise).saveAs(out);
  };
  const waitIdle = async () => {
    const limit = Date.now() + 20000; let emptyAt = null;
    while (Date.now() < limit) {
      if (!pending.size) { emptyAt ??= Date.now(); if (Date.now() - emptyAt > 700) return true; }
      else emptyAt = null;
      await page.waitForTimeout(100);
    }
    return !pending.size;
  };
  page.on('console', message => allConsole.push({ type: message.type(), text: message.text() }));
  page.on('request', request => {
    if (!request.url().includes('/api/')) return;
    const item = { method: request.method(), url: request.url(), status: null, failure: null };
    requestMap.set(request, item); allRequests.push(item); pending.add(request);
  });
  page.on('response', response => { const item = requestMap.get(response.request()); if (item) item.status = response.status(); pending.delete(response.request()); });
  page.on('requestfailed', request => { const item = requestMap.get(request); if (item) item.failure = request.failure()?.errorText || 'unknown'; pending.delete(request); });
  const discover = async spec => {
    const result = await page.evaluate(async ({ url, field }) => {
      const response = await fetch(url, { credentials: 'same-origin' });
      let json; try { json = await response.json(); } catch { json = null; }
      const walk = (value, depth = 0) => {
        if (value == null || depth > 12) return null;
        if (Array.isArray(value)) { for (const item of value) { const found = walk(item, depth + 1); if (found != null) return found; } return null; }
        if (typeof value !== 'object') return null;
        if (value[field] != null && String(value[field])) return String(value[field]);
        for (const item of Object.values(value)) { const found = walk(item, depth + 1); if (found != null) return found; }
        return null;
      };
      return { status: response.status, value: walk(json) };
    }, spec);
    if (result.value) { discoveries[spec.key] = result.value; realIds.add(result.value); }
    evidence.parameterDiscovery.push({ key: spec.key, source: spec.url, status: result.status, found: Boolean(result.value), valueArchived: false });
    return result;
  };
  const domProbe = async () => page.evaluate(() => {
    const vis = element => { const r = element.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
    const heightSet = selector => [...new Set([...document.querySelectorAll(selector)].filter(vis).map(e => Math.round(e.getBoundingClientRect().height)))];
    const content = document.querySelector('.content'); const main = document.querySelector('main');
    const hosts = [...document.querySelectorAll('[data-bp-row-actions-host]')].filter(vis).map((host, index) => {
      const probe = host.querySelector('[data-bp-row-actions-probe]');
      const shown = host.querySelector('[data-bp-row-actions-visible]');
      return {
        index, mode: host.dataset.mode, measured: host.dataset.measured,
        available: host.clientWidth, required: probe?.scrollWidth || 0,
        visibleItems: [...(shown?.querySelectorAll('button,[role="button"]') || [])].filter(vis).map(b => ({ text: b.textContent?.trim(), ariaLabel: b.getAttribute('aria-label'), disabled: b.matches(':disabled,[aria-disabled="true"]'), danger: b.classList.contains('el-button--danger') || b.closest('.danger-item') != null })),
        probe: { ariaHidden: probe?.getAttribute('aria-hidden'), inertAttr: probe?.hasAttribute('inert'), inertProperty: Boolean(probe?.inert), pointerEvents: probe ? getComputedStyle(probe).pointerEvents : null, visibility: probe ? getComputedStyle(probe).visibility : null, descendantTabIndexes: [...(probe?.querySelectorAll('button,a,input,[tabindex]') || [])].map(e => e.tabIndex) }
      };
    });
    const ops = [...document.querySelectorAll('td.operation-cell')].filter(vis).map(td => ({
      height: Math.round(td.getBoundingClientRect().height), position: getComputedStyle(td).position,
      right: getComputedStyle(td).right, width: Math.round(td.getBoundingClientRect().width)
    }));
    return {
      path: location.hash.replace(/^#/, '').split('?')[0], mainCount: document.querySelectorAll('main').length,
      h1Count: document.querySelectorAll('h1').length,
      overflow: { document: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1, body: document.body.scrollWidth > document.body.clientWidth + 1, content: Boolean(content && content.scrollWidth > content.clientWidth + 1), main: Boolean(main && main.scrollWidth > main.clientWidth + 1) },
      density: {
        tableHeader: heightSet('.el-table__header tr'), tableRows: heightSet('.el-table__body tr'),
        tags: heightSet('.bp-crud .el-table .el-tag'), controls: heightSet('.bp-crud .el-input__wrapper,.bp-crud .el-select__wrapper,.bp-crud .el-button')
      }, operations: ops, adaptiveHosts: hosts,
      tableInternalScrollers: [...document.querySelectorAll('.el-table .el-scrollbar__wrap')].filter(vis).map(e => ({ clientWidth: e.clientWidth, scrollWidth: e.scrollWidth, overflowX: getComputedStyle(e).overflowX }))
    };
  });
  const loginContext = await page.context().browser().newContext({ viewport: { width: 1920, height: 1080 } });
  const loginPage = await loginContext.newPage(); await loginPage.goto(`${base}#/login?normal`);
  await loginPage.screenshot({ path: `${shots}/01-login.png` }); await loginContext.close();
  evidence.routes.push({ name: 'Login', templatePath: '/login?normal', status: 'pass', actualPath: '/login?normal' });
  for (let i = 0; i < specs.length; i += 1) {
    const spec = specs[i]; let target = spec.path;
    if (spec.parameter) {
      const id = discoveries[spec.parameter];
      if (!id) { evidence.fatalIssue = { route: spec.name, issues: ['blocked-no-real-id'] }; evidence.stoppedEarly = true; break; }
      target = spec.template.replace(/:[^/]+/, encodeURIComponent(id)); if (spec.query) target += `?${spec.query}`;
    }
    const consoleStart = allConsole.length; const requestStart = allRequests.length;
    await page.evaluate(path => { location.hash = path; }, target); await page.waitForTimeout(350); await waitIdle(); await page.evaluate(() => document.fonts?.ready);
    const result = { name: spec.name, templatePath: spec.template || spec.path, actualIdArchived: false, status: 'pass' };
    result.dom = await domProbe();
    result.console = allConsole.slice(consoleStart).map(item => ({ type: item.type, text: item.text.slice(0, 500) }));
    result.network = allRequests.slice(requestStart).map(item => ({ method: item.method, url: item.url, status: item.status, failure: item.failure }));
    result.expectedPhysicalOperationColumns = expectedOperationColumns[spec.name] || 0;
    result.expectedAdaptiveInPage = expectedAdaptiveHosts.has(spec.name);
    evidence.runtimeVisible.operationCells += result.dom.operations.length;
    evidence.runtimeVisible.adaptiveHosts += result.dom.adaptiveHosts.length;
    for (const host of result.dom.adaptiveHosts) evidence.naturalModes[host.mode] = (evidence.naturalModes[host.mode] || 0) + 1;
    if (focusRoutes.has(spec.name)) evidence.focusRoutes[spec.name] = { hosts: result.dom.adaptiveHosts, operations: result.dom.operations };
    if (spec.discover) await discover(spec.discover);
    const issues = [];
    if (result.dom.path !== target.split('?')[0]) issues.push(`redirected:${result.dom.path}`);
    if (result.dom.mainCount !== 1 || result.dom.h1Count < 1) issues.push('semantic-shell');
    for (const [key, bad] of Object.entries(result.dom.overflow)) if (bad) issues.push(`${key}-overflow-x`);
    if (result.dom.density.tableHeader.some(v => v !== 40) || result.dom.density.tableRows.some(v => v !== 40)) issues.push(`table-density:${JSON.stringify(result.dom.density)}`);
    if (result.dom.density.tags.some(v => v !== 24)) issues.push(`tag-density:${result.dom.density.tags}`);
    if (result.dom.density.controls.some(v => v < 32)) issues.push(`control-density:${result.dom.density.controls}`);
    if (result.dom.operations.some(v => v.height !== 40 || v.position !== 'sticky')) issues.push('sticky-operation');
    if (result.dom.tableInternalScrollers.some(v => v.scrollWidth > v.clientWidth + 1 && !['auto', 'scroll'].includes(v.overflowX))) issues.push('wide-table-without-internal-scroll');
    if (result.dom.adaptiveHosts.some(h => !['expanded', 'compact'].includes(h.mode) || h.measured !== 'true' || h.probe.ariaHidden !== 'true' || !h.probe.inertAttr || !h.probe.inertProperty || h.probe.pointerEvents !== 'none' || h.probe.visibility !== 'hidden')) issues.push('adaptive-probe-contract');
    const consoleBad = result.console.filter(item => item.type === 'error' || (item.type === 'warning' && !item.text.includes('[Vue Router warn]: No match found for location with path')) || item.text.includes('[api fallback]'));
    const networkBad = result.network.filter(item => item.failure || item.status == null || item.status >= 400 || !safeMethods.has(item.method));
    if (consoleBad.length) issues.push(`console-or-fallback:${consoleBad.length}`);
    if (networkBad.length) issues.push(`network-or-write:${networkBad.length}`);
    result.issues = issues;
    await page.screenshot({ path: `${shots}/${String(i + 2).padStart(2, '0')}-${spec.name}.png`, mask: [page.locator('.el-table__body:visible,input,textarea,.el-descriptions__content')], maskColor: '#d8dee9' });
    if (issues.length) { result.status = 'fail'; evidence.fatalIssue = { route: spec.name, issues }; evidence.stoppedEarly = true; }
    evidence.routes.push(result); await save(); if (issues.length) break;
  }
  evidence.summary = {
    totalNamedRoutes: 59, recorded: evidence.routes.length,
    passed: evidence.routes.filter(r => r.status === 'pass').length,
    failed: evidence.routes.filter(r => r.status === 'fail').length,
    dynamicMenuWarningEvents: allConsole.filter(item => item.type === 'warning' && item.text.includes('[Vue Router warn]: No match found for location with path')).length,
    distinctDynamicMenuWarnings: new Set(allConsole.filter(item => item.type === 'warning' && item.text.includes('[Vue Router warn]: No match found for location with path')).map(item => item.text)).size,
    errors: allConsole.filter(item => item.type === 'error').length,
    fallbacks: allConsole.filter(item => item.text.includes('[api fallback]')).length,
    resizeObserverLoops: allConsole.filter(item => /ResizeObserver loop/i.test(item.text)).length,
    unexpectedWrites: allRequests.filter(item => !safeMethods.has(item.method)).length,
    failedApi: allRequests.filter(item => item.failure || item.status == null || item.status >= 400).length
  };
  await save();
  return { summary: evidence.summary, naturalModes: evidence.naturalModes, runtimeVisible: evidence.runtimeVisible, fatalIssue: evidence.fatalIssue };
}
