async page => {
  const baseUrl = 'http://127.0.0.1:8091/';
  const root = '/home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization-20260813/final-pass/1920';
  const evidencePath = `${root}/json/route-dom-probes-1920-22108e35.json`;
  const screenshotDir = `${root}/screenshots`;
  const safeMethods = new Set(['GET', 'HEAD', 'OPTIONS']);
  const warningZeroTargets = new Set([
    '/perf/adjust', '/history/perf-adjust',
    '/screen-admin/datasources', '/screen-admin/org-profiles'
  ]);
  const pointRoutes = new Set([
    '/screen-admin/datasources', '/screen-admin/org-profiles', '/screen-admin/org-groups',
    '/system/users', '/eval/tags', '/eval/user-tags'
  ]);
  const safeDialogActions = {
    '/screen-admin/datasources': { kind: 'row', label: '编辑' },
    '/screen-admin/org-profiles': { kind: 'row', label: '编辑画像' },
    '/screen-admin/org-groups': { kind: 'page', label: '新建机构组' },
    '/system/users': { kind: 'row', label: '编辑' },
    '/eval/tags': { kind: 'row', label: '编辑' },
    '/eval/user-tags': { kind: 'row', label: '编辑' }
  };
  const routeSpecs = [
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
    { name: 'ReportAmasApprovals', path: '/report/amas-approvals', discover: { key: 'amasApproval', url: '/api/reports/amas-approvals?pageNo=1&pageSize=20', field: 'perfAdjustNo' } },
    { name: 'ReportAmasApprovalDetail', template: '/report/amas-approvals/:perfAdjustNo', parameter: 'amasApproval' },
    { name: 'ScreenAdminDs', path: '/screen-admin/datasources' },
    { name: 'ScreenAdminOrgProfiles', path: '/screen-admin/org-profiles' },
    { name: 'ScreenAdminOrgGroups', path: '/screen-admin/org-groups' },
    { name: 'GuaranteeQuery', path: '/guarantee/query' },
    { name: 'HistoryDataImport', path: '/guarantee/data-import' },
    { name: 'HistoryNotice', path: '/guarantee/notice' },
    { name: 'HistoryPriceApproval', path: '/history/price-approval', discover: { key: 'priceApproval', url: '/api/reports/amas-price-approvals?pageNo=1&pageSize=20', field: 'priceApprId' } },
    { name: 'HistoryPriceApprovalDetail', template: '/history/price-approval/:priceApprId', parameter: 'priceApproval' },
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
    { name: 'SysWorkflowFlows', path: '/system/workflow-flows', discover: { key: 'workflowFlow', url: '/api/admin/workflow/flows', field: 'id' } },
    { name: 'SysWorkflowFlowEdit', template: '/system/workflow-flows/:id', parameter: 'workflowFlow' },
    { name: 'SysWorkflowMonitor', path: '/system/workflow-monitor' },
    { name: 'SysPersonTags', path: '/system/person-tags' }
  ];
  const discoveries = {};
  const realIds = new Set();
  const evidence = {
    batch: 'final-pass-1920-22108e35', finalPassClaim: true,
    viewport: { width: 1920, height: 1080 },
    tool: 'xanzc_frontend/node_modules/.bin/playwright-cli', session: 'final1920xianfinal',
    gitHead: '22108e35',
    generatedAt: new Date().toISOString(), mockRoutesAtStart: 'No active routes',
    authentication: { loginPostStatus: 200, postLoginPath: '/workspace', credentialsArchived: false },
    safety: { mockRoutesRegistered: 0, allowedMethodsAfterLogin: [...safeMethods], responseBodiesArchived: false, realIdsArchived: false },
    routes: [], parameterDiscovery: [], fatalIssue: null, stoppedEarly: false
  };

  const sanitizeArchivedString = raw => {
    let value = String(raw);
    for (const id of realIds) {
      const escaped = String(id).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      // 真实 ID 只在浏览器内用于导航；归档时仅替换完整路径段，避免一位数字 ID 破坏 URL 其他部分。
      value = value.replace(new RegExp(`/${escaped}(?=/|[?#]|$)`, 'g'), '/<redacted-id>');
      value = value.replace(new RegExp(`/${encodeURIComponent(id).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?=/|[?#]|$)`, 'g'), '/<redacted-id>');
    }
    // 其他只读 GET 中的实体路径段同样最小化；固定资源名和集合端点保持原样。
    return value
      .replace(/(\/api\/admin\/roles\/)[^/?#]+(?=\/resources(?:[/?#]|$))/g, '$1<redacted-id>')
      .replace(/(\/api\/admin\/users\/)[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
      .replace(/(\/api\/admin\/workflow\/flows\/)(?!meta(?:[/?#]|$))[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
      .replace(/(\/api\/perf\/kpi-schemes\/)[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
      .replace(/(\/api\/perf\/metrics\/)(?!categories(?:[/?#]|$))[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
      .replace(/(\/api\/portal\/announcements\/)(?!recent(?:[/?#]|$))[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
      .replace(/(\/api\/reports\/amas-approvals\/)[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
      .replace(/(\/api\/reports\/amas-price-approvals\/)[^/?#]+(?=[/?#]|$)/g, '$1<redacted-id>')
      .replace(/(\/api\/sys\/dicts\/)[^/?#]+(?=\/items(?:[/?#]|$))/g, '$1<redacted-id>');
  };
  const publicEvidence = () => {
    const archived = JSON.parse(JSON.stringify(evidence));
    const sanitize = value => {
      if (typeof value === 'string') return sanitizeArchivedString(value);
      if (Array.isArray(value)) return value.map(sanitize);
      if (value && typeof value === 'object') {
        for (const [key, item] of Object.entries(value)) value[key] = sanitize(item);
      }
      return value;
    };
    sanitize(archived);
    for (const route of archived.routes || []) {
      if (route.realIdSource && route.dom) route.dom.actualPath = route.templatePath;
    }
    return archived;
  };
  const saveEvidence = async () => {
    const downloadPromise = page.waitForEvent('download');
    await page.evaluate(text => {
      const blob = new Blob([text], { type: 'application/json;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = 'route-dom-probes-1920-22108e35.json';
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      setTimeout(() => URL.revokeObjectURL(url), 0);
    }, JSON.stringify(publicEvidence(), null, 2));
    const download = await downloadPromise;
    await download.saveAs(evidencePath);
  };
  const waitForApiIdle = async pending => {
    const deadline = Date.now() + 15000;
    let emptySince = null;
    while (Date.now() < deadline) {
      if (!pending.size) {
        emptySince ??= Date.now();
        if (Date.now() - emptySince >= 600) return true;
      } else emptySince = null;
      await page.waitForTimeout(100);
    }
    return !pending.size;
  };
  const redactUrl = raw => {
    let value = String(raw).replace(baseUrl.replace(/\/$/, ''), '');
    value = sanitizeArchivedString(value);
    return value.replace(/([?&][^=&#]+)=([^&#]*)/g, '$1=<redacted>');
  };
  const discoverRealId = async spec => {
    const result = await page.evaluate(async ({ url, field }) => {
      const response = await fetch(url, { method: 'GET', credentials: 'same-origin' });
      let body = null;
      try { body = await response.json(); } catch { body = null; }
      const walk = (value, depth = 0) => {
        if (depth > 12 || value == null) return null;
        if (Array.isArray(value)) {
          for (const item of value) { const found = walk(item, depth + 1); if (found != null) return found; }
          return null;
        }
        if (typeof value !== 'object') return null;
        if (value[field] != null && String(value[field]).length) return String(value[field]);
        for (const item of Object.values(value)) { const found = walk(item, depth + 1); if (found != null) return found; }
        return null;
      };
      return { status: response.status, value: walk(body) };
    }, spec);
    if (result.value) { discoveries[spec.key] = result.value; realIds.add(result.value); }
    evidence.parameterDiscovery.push({ key: spec.key, source: redactUrl(spec.url), method: 'GET', status: result.status, found: Boolean(result.value), valueArchived: false });
    return result;
  };
  const takeScreenshot = async name => {
    const mask = page.locator('.el-table__body:visible, .el-descriptions__content, input, textarea');
    await page.screenshot({ path: `${screenshotDir}/g-${name}.png`, mask: [mask], maskColor: '#d8dee9' });
  };
  const loginContext = await page.context().browser().newContext({ viewport: { width: 1920, height: 1080 } });
  const loginPage = await loginContext.newPage();
  await loginPage.goto(`${baseUrl}#/login?normal`);
  await loginPage.waitForTimeout(500);
  const loginDom = await loginPage.evaluate(() => ({
    actualPath: location.hash.replace(/^#/, ''), mainCount: document.querySelectorAll('main').length,
    h1Count: document.querySelectorAll('h1').length,
    hasUsername: Boolean(document.querySelector('input[placeholder="请输入用户名"]')),
    hasPassword: Boolean(document.querySelector('input[placeholder="请输入密码"]')),
    hasSubmit: Boolean([...document.querySelectorAll('button')].find(button => button.textContent?.includes('登'))),
    documentOverflowX: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1
  }));
  await loginPage.screenshot({ path: `${screenshotDir}/g-01-login.png` });
  await loginContext.close();
  const loginIssues = [];
  if (loginDom.mainCount !== 1 || loginDom.h1Count < 1 || !loginDom.hasUsername || !loginDom.hasPassword || !loginDom.hasSubmit || loginDom.documentOverflowX) loginIssues.push('login-dom-contract');
  evidence.routes.push({ name: 'Login', templatePath: '/login?normal', status: loginIssues.length ? 'fail' : 'pass', dom: loginDom, issues: loginIssues, console: { errors: 0, warnings: 0 }, network: { unexpectedWrites: [] } });
  if (loginIssues.length) { evidence.fatalIssue = { route: 'Login', issues: loginIssues }; evidence.stoppedEarly = true; await saveEvidence(); return { summary: { recorded: 1, failed: 1 }, fatalIssue: evidence.fatalIssue }; }

  const probeDom = async () => page.evaluate(() => {
    const visible = element => { const rect = element.getBoundingClientRect(); return rect.width > 0 && rect.height > 0; };
    const heights = selector => [...document.querySelectorAll(selector)].filter(visible).map(element => Math.round(element.getBoundingClientRect().height));
    const content = document.querySelector('.content');
    const main = document.querySelector('main');
    const compactCells = [...document.querySelectorAll('td.compact-stack-cell .cell')].filter(visible).map(cell => ({
      td: Math.round(cell.closest('td').getBoundingClientRect().height), cell: Math.round(cell.getBoundingClientRect().height),
      children: [...cell.children].map(child => ({ height: Math.round(child.getBoundingClientRect().height), marginTop: getComputedStyle(child).marginTop, marginBottom: getComputedStyle(child).marginBottom })),
      scrollHeight: cell.scrollHeight, clientHeight: cell.clientHeight
    }));
    const clampCells = [...document.querySelectorAll('td.compact-clamp-cell .cell')].filter(visible).map(cell => {
      const style = getComputedStyle(cell);
      return { textLength: (cell.textContent || '').trim().length, lineClamp: style.webkitLineClamp, lineHeight: style.lineHeight, maxHeight: style.maxHeight, scrollHeight: cell.scrollHeight, clientHeight: cell.clientHeight, title: cell.getAttribute('title') };
    });
    const operations = [...document.querySelectorAll('td.operation-cell')].filter(visible).map(td => ({ position: getComputedStyle(td).position, right: getComputedStyle(td).right, height: Math.round(td.getBoundingClientRect().height), cellOverflow: getComputedStyle(td.querySelector('.cell') || td).overflow }));
    const tableScrollers = [...document.querySelectorAll('.el-table .el-scrollbar__wrap')].filter(visible).map(element => ({ clientWidth: element.clientWidth, scrollWidth: element.scrollWidth, overflowX: getComputedStyle(element).overflowX }));
    const filters = [...document.querySelectorAll('.filter-form')].filter(visible).map(element => ({ display: getComputedStyle(element).display, buttons: [...element.querySelectorAll('button')].map(button => button.textContent?.trim()).filter(Boolean) }));
    const controls = [...document.querySelectorAll('.bp-crud .el-input__wrapper, .bp-crud .el-select__wrapper, .bp-crud .el-button')].filter(visible).slice(0, 30).map(element => Math.round(element.getBoundingClientRect().height));
    const tabs = document.querySelector('.workspace-tabs');
    const tabScroll = document.querySelector('.workspace-tabs__scroll');
    const tabItems = [...document.querySelectorAll('.workspace-tabs__tab')];
    const closeButtons = [...document.querySelectorAll('.workspace-tabs__close')];
    const active = document.querySelector('.workspace-tabs__tab--active');
    const scrollRect = tabScroll?.getBoundingClientRect();
    const activeRect = active?.getBoundingClientRect();
    return {
      actualPath: location.hash.replace(/^#/, '').split('?')[0], mainCount: document.querySelectorAll('main').length,
      h1Count: document.querySelectorAll('h1').length,
      h1Text: [...document.querySelectorAll('h1')].map(item => item.textContent?.trim()).filter(Boolean).join(' | '),
      overflow: {
        document: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
        body: document.body.scrollWidth > document.body.clientWidth + 1,
        content: Boolean(content && content.scrollWidth > content.clientWidth + 1),
        main: Boolean(main && main.scrollWidth > main.clientWidth + 1)
      },
      tables: {
        count: document.querySelectorAll('.el-table').length,
        headerHeights: heights('.el-table__header tr'), rowHeights: heights('.el-table__body tr'),
        headerCellHeights: heights('.el-table__header th.el-table__cell'), dataCellHeights: heights('.el-table__body td.el-table__cell'),
        bottomBorders: [...document.querySelectorAll('.el-table th.el-table__cell, .el-table td.el-table__cell')].filter(visible).map(cell => getComputedStyle(cell).borderBottomWidth),
        dataCellPaddings: [...document.querySelectorAll('.el-table__body td.el-table__cell')].filter(visible).map(cell => ({ top: getComputedStyle(cell).paddingTop, bottom: getComputedStyle(cell).paddingBottom })),
        scrollers: tableScrollers
      },
      compactCells, clampCells, operations,
      tags: [...document.querySelectorAll('.bp-crud .el-table .el-tag')].filter(visible).slice(0, 40).map(element => {
        const style = getComputedStyle(element);
        return {
          height: Math.round(element.getBoundingClientRect().height),
          lineHeight: style.lineHeight,
          paddingLeft: style.paddingLeft,
          paddingRight: style.paddingRight,
          verticalAlign: style.verticalAlign
        };
      }),
      compactCodes: [...document.querySelectorAll('td.compact-stack-cell code.mono')].filter(visible).map(code => {
        const style = getComputedStyle(code);
        return {
          rectHeight: Math.round(code.getBoundingClientRect().height),
          clientHeight: code.clientHeight,
          scrollHeight: code.scrollHeight,
          borderTop: style.borderTopWidth,
          borderBottom: style.borderBottomWidth,
          paddingTop: style.paddingTop,
          paddingBottom: style.paddingBottom,
          lineHeight: style.lineHeight,
          rowHeight: Math.round(code.closest('tr')?.getBoundingClientRect().height || 0)
        };
      }),
      controls, filters,
      taskMonitorErrors: [...document.querySelectorAll('.err-inline')].filter(visible).map(element => ({ marginTop: getComputedStyle(element).marginTop, marginBottom: getComputedStyle(element).marginBottom, height: Math.round(element.getBoundingClientRect().height), clientHeight: element.clientHeight, scrollHeight: element.scrollHeight })),
      tabs: tabs ? {
        stripHeight: Math.round(tabs.getBoundingClientRect().height), itemCount: tabItems.length,
        itemHeights: [...new Set(tabItems.map(item => Math.round(item.getBoundingClientRect().height)))],
        closeSizes: [...new Set(closeButtons.map(item => `${Math.round(item.getBoundingClientRect().width)}x${Math.round(item.getBoundingClientRect().height)}`))],
        activeCount: document.querySelectorAll('.workspace-tabs__tab--active').length,
        ariaCurrent: document.querySelectorAll('.workspace-tabs__tab[aria-current="page"]').length,
        horizontalOverflow: Boolean(tabScroll && tabScroll.scrollWidth > tabScroll.clientWidth + 1),
        activeVisible: Boolean(scrollRect && activeRect && activeRect.left >= scrollRect.left - 1 && activeRect.right <= scrollRect.right + 1)
      } : null
    };
  });
  const probeFirstOverflowHover = async () => {
    const cells = page.locator('.el-table__body .cell');
    const count = await cells.count();
    for (let index = 0; index < count; index += 1) {
      const cell = cells.nth(index);
      if (!(await cell.isVisible()) || await cell.locator('button, input, textarea').count()) continue;
      const state = await cell.evaluate(element => ({
        overflowing: element.scrollWidth > element.clientWidth + 1 || element.scrollHeight > element.clientHeight + 1 || [...element.children].some(child => child.scrollWidth > child.clientWidth + 1 || child.scrollHeight > child.clientHeight + 1),
        businessTitle: element.hasAttribute('title') && !element.hasAttribute('data-bp-overflow-tooltip'),
        expected: [...element.children].map(child => child.textContent?.trim()).filter(Boolean).length > 1
          ? [...element.children].map(child => child.textContent?.trim()).filter(Boolean).join(' / ')
          : element.textContent?.trim() || ''
      }));
      if (!state.overflowing || state.businessTitle || !state.expected) continue;
      const target = (await cell.locator('*').count()) ? cell.locator('*').first() : cell;
      await target.hover();
      const title = await cell.getAttribute('title');
      return { tested: true, titleMatchesFullText: title === state.expected, textLength: state.expected.length };
    }
    return { tested: false, titleMatchesFullText: null, textLength: 0 };
  };
  const probeProductClamp = async () => {
    const cells = page.locator('td.compact-clamp-cell .cell');
    const count = await cells.count();
    const longResults = [];
    const shortResults = [];
    for (let index = 0; index < count; index += 1) {
      const cell = cells.nth(index);
      if (!(await cell.isVisible())) continue;
      const state = await cell.evaluate(element => ({ text: element.textContent?.trim() || '', overflow: element.scrollHeight > element.clientHeight + 1 || element.scrollWidth > element.clientWidth + 1, title: element.getAttribute('title') }));
      await cell.hover();
      const after = await cell.getAttribute('title');
      if (state.overflow) longResults.push({ textLength: state.text.length, titleMatches: after === state.text });
      else shortResults.push({ textLength: state.text.length, noGeneratedTitle: after == null });
    }
    return {
      cellCount: count,
      long: longResults.length
        ? { sampleCount: longResults.length, allTitlesMatch: longResults.every(item => item.titleMatches), samples: longResults }
        : { sampleCount: 0, status: 'not_exercised_no_real_overflow_sample' },
      short: { sampleCount: shortResults.length, allWithoutGeneratedTitle: shortResults.every(item => item.noGeneratedTitle), samples: shortResults }
    };
  };
  const probeWorkflowCompactCodes = async () => {
    const codes = page.locator('td.compact-stack-cell code.mono:visible');
    const results = [];
    for (let index = 0; index < await codes.count(); index += 1) {
      const code = codes.nth(index);
      const state = await code.evaluate(element => {
        const cell = element.closest('.cell');
        const children = [...(cell?.children || [])];
        const expected = children.map(child => child.textContent?.trim()).filter(Boolean).join(' / ');
        const overflow = children.some(child => child.scrollWidth - child.clientWidth > 1 || child.scrollHeight - child.clientHeight > 1);
        return { expected, overflow, businessTitle: cell?.hasAttribute('title') && !cell.hasAttribute('data-bp-overflow-tooltip') };
      });
      await code.hover();
      const after = await code.evaluate(element => {
        const cell = element.closest('.cell');
        return { title: cell?.getAttribute('title') || null, generated: cell?.hasAttribute('data-bp-overflow-tooltip') || false };
      });
      results.push({
        overflow: state.overflow,
        businessTitle: state.businessTitle,
        titleContract: state.businessTitle || (state.overflow ? after.title === state.expected : !after.generated && after.title == null),
        expectedTextLength: state.expected.length
      });
    }
    return { sampleCount: results.length, allTitleContractsPass: results.every(item => item.titleContract), samples: results };
  };
  const inspectMoreMenu = async () => {
    const buttons = page.getByRole('button', { name: /^更多/ });
    if (!(await buttons.count()) || !(await buttons.first().isVisible())) return { triggerCount: await buttons.count(), opened: false, dangerItems: [] };
    await buttons.first().click();
    const menu = page.locator('.el-dropdown-menu:visible').last();
    await menu.waitFor({ state: 'visible', timeout: 3000 });
    const items = menu.locator('.el-dropdown-menu__item:visible');
    const dangerItems = [];
    for (let i = 0; i < await items.count(); i += 1) {
      const item = items.nth(i);
      if (/danger/i.test((await item.getAttribute('class')) || '')) dangerItems.push((await item.innerText()).trim());
    }
    const result = { triggerCount: await buttons.count(), opened: true, items: (await items.allTextContents()).map(text => text.trim()).filter(Boolean), dangerItems };
    await page.keyboard.press('Escape');
    return result;
  };
  const openAndCancelSafeDialog = async path => {
    const action = safeDialogActions[path];
    if (!action) return null;
    let trigger;
    if (action.kind === 'row') {
      const row = page.locator('.el-table__body-wrapper tbody tr:visible').first();
      if (!(await row.count())) return { status: 'blocked_no_row' };
      trigger = row.getByRole('button', { name: action.label, exact: true }).first();
    } else trigger = page.getByRole('button', { name: new RegExp(action.label) }).first();
    if (!(await trigger.count()) || !(await trigger.isVisible())) return { status: 'blocked_no_trigger' };
    await trigger.click();
    const dialog = page.locator('.el-dialog:visible').last();
    try { await dialog.waitFor({ state: 'visible', timeout: 4000 }); } catch { return { status: 'blocked_dialog_not_opened' }; }
    const title = ((await dialog.locator('.el-dialog__title').textContent()) || '').trim();
    const controls = await dialog.locator('input, textarea, .el-select').count();
    const cancel = dialog.getByRole('button', { name: '取消', exact: true }).last();
    if (await cancel.count()) await cancel.click(); else await dialog.locator('.el-dialog__headerbtn').click();
    await dialog.waitFor({ state: 'hidden', timeout: 4000 });
    return { status: 'opened_and_cancelled', title, controls };
  };
  const probeReset = async (path, pending, records) => {
    if (path === '/eval/tasks') {
      const keyword = page.getByLabel('按任务名称批次或创建人筛选');
      await keyword.fill('终验重置筛选');
      const before = records.length;
      await page.getByRole('button', { name: '重置', exact: true }).click();
      await waitForApiIdle(pending);
      const requests = records.slice(before).filter(item => item.url.includes('/api/'));
      return { inputCleared: (await keyword.inputValue()) === '', requests: requests.map(item => ({ method: item.method, url: redactUrl(item.url), status: item.status })), correctReload: requests.some(item => item.method === 'GET' && item.url.includes('/api/admin/eval/tasks')) };
    }
    if (path === '/system/config') {
      const before = records.length;
      await page.getByRole('button', { name: '重置', exact: true }).click();
      await waitForApiIdle(pending);
      const requests = records.slice(before).filter(item => item.url.includes('/api/'));
      const urls = requests.map(item => item.url);
      return {
        requests: requests.map(item => ({ method: item.method, url: redactUrl(item.url), status: item.status })),
        definitionsReloaded: urls.some(url => url.includes('/api/admin/workflow/process-definitions')),
        candidatesReloaded: urls.some(url => url.includes('/api/admin/workflow/node-candidates')),
        wrongTabRequests: urls.filter(url => url.includes('/api/admin/workflow/node-forms') || url.includes('/api/admin/workflow/timeout-rules')).length,
        buttonEnabledAfter: await page.getByRole('button', { name: '重置', exact: true }).isEnabled()
      };
    }
    return null;
  };

  for (let routeIndex = 0; routeIndex < routeSpecs.length; routeIndex += 1) {
    if (evidence.fatalIssue) break;
    const spec = routeSpecs[routeIndex];
    let actualPath = spec.path;
    if (spec.parameter) {
      const value = discoveries[spec.parameter];
      if (!value) {
        const blocked = { name: spec.name, templatePath: spec.template, status: 'fail', issues: ['blocked-no-real-id'], realIdArchived: false };
        evidence.routes.push(blocked); evidence.fatalIssue = { route: spec.name, issues: blocked.issues }; evidence.stoppedEarly = true; await saveEvidence(); break;
      }
      actualPath = spec.template.replace(/:[^/]+/, encodeURIComponent(value));
      if (spec.query) actualPath += `?${spec.query}`;
    }
    const consoleEvents = [];
    const requestRecords = [];
    const requestMap = new Map();
    const pending = new Set();
    const requestFailures = [];
    const onConsole = message => consoleEvents.push({ type: message.type(), text: message.text() });
    const onRequest = request => {
      if (!request.url().includes('/api/')) return;
      const record = { method: request.method(), url: request.url(), status: null };
      requestMap.set(request, record); requestRecords.push(record); pending.add(request);
    };
    const onResponse = response => { const request = response.request(); const record = requestMap.get(request); if (record) record.status = response.status(); pending.delete(request); };
    const onRequestFailed = request => { if (request.url().includes('/api/')) requestFailures.push({ method: request.method(), url: request.url(), error: request.failure()?.errorText || 'unknown' }); pending.delete(request); };
    page.on('console', onConsole); page.on('request', onRequest); page.on('response', onResponse); page.on('requestfailed', onRequestFailed);
    await page.evaluate(path => { location.hash = path; }, actualPath);
    await page.waitForTimeout(400);
    await waitForApiIdle(pending);
    const routeResult = { name: spec.name, templatePath: spec.template || spec.path, status: 'pass', realIdSource: spec.parameter || null, realIdArchived: false };
    routeResult.dom = await probeDom();
    routeResult.overflowHover = await probeFirstOverflowHover();
    if (spec.path === '/info/products') routeResult.productClamp = await probeProductClamp();
    if (spec.path === '/system/workflow-monitor') routeResult.workflowCompactCodeHover = await probeWorkflowCompactCodes();
    if (pointRoutes.has(spec.path)) {
      const query = page.locator('.filter-form button:visible').filter({ hasText: /^查询$/ }).first();
      if (await query.count()) { await query.click(); await waitForApiIdle(pending); routeResult.queryInteraction = 'clicked-read-only-query'; }
      routeResult.moreMenu = await inspectMoreMenu();
      routeResult.dialog = await openAndCancelSafeDialog(spec.path);
      await waitForApiIdle(pending);
    }
    routeResult.reset = await probeReset(spec.path, pending, requestRecords);
    if (spec.discover) { const found = await discoverRealId(spec.discover); routeResult.parameterDiscovery = { source: redactUrl(spec.discover.url), status: found.status, found: Boolean(found.value), valueArchived: false }; await waitForApiIdle(pending); }
    await takeScreenshot(`${String(routeIndex + 2).padStart(2, '0')}-${spec.name}`);
    page.off('console', onConsole); page.off('request', onRequest); page.off('response', onResponse); page.off('requestfailed', onRequestFailed);
    const allowedWarnings = consoleEvents.filter(event => event.type === 'warning' && event.text.includes('[Vue Router warn]: No match found for location with path'));
    const fallbackEvents = consoleEvents.filter(event => event.text.includes('[api fallback]'));
    const disallowedConsole = consoleEvents.filter(event => event.type === 'error' || (event.type === 'warning' && !event.text.includes('[Vue Router warn]: No match found for location with path')));
    const unexpectedWrites = requestRecords.filter(record => !safeMethods.has(record.method));
    const badResponses = requestRecords.filter(record => record.status == null || record.status >= 400);
    routeResult.console = { errors: consoleEvents.filter(event => event.type === 'error').length, warnings: consoleEvents.filter(event => event.type === 'warning').length, allowedDynamicMenuWarnings: allowedWarnings.length, fallbackMarkers: fallbackEvents.length, entries: consoleEvents.map(event => ({ type: event.type, text: event.text.slice(0, 500) })) };
    routeResult.network = { requests: requestRecords.map(record => ({ method: record.method, url: redactUrl(record.url), status: record.status })), failed: requestFailures.map(record => ({ ...record, url: redactUrl(record.url) })), unexpectedWrites: unexpectedWrites.map(record => ({ method: record.method, url: redactUrl(record.url), status: record.status })), badResponses: badResponses.map(record => ({ method: record.method, url: redactUrl(record.url), status: record.status })) };
    const issues = [];
    const expectedPath = actualPath.split('?')[0];
    if (routeResult.dom.actualPath !== expectedPath) issues.push(`redirected:${routeResult.dom.actualPath}`);
    if (routeResult.dom.mainCount !== 1) issues.push(`main-count:${routeResult.dom.mainCount}`);
    if (routeResult.dom.h1Count < 1) issues.push('missing-h1');
    for (const [key, value] of Object.entries(routeResult.dom.overflow)) if (value) issues.push(`${key}-overflow-x`);
    if (routeResult.dom.tables.headerHeights.some(height => height !== 40)) issues.push(`header-height:${routeResult.dom.tables.headerHeights.join(',')}`);
    if (routeResult.dom.tables.rowHeights.some(height => height !== 40)) issues.push(`row-height:${routeResult.dom.tables.rowHeights.join(',')}`);
    if (routeResult.dom.tables.headerCellHeights.some(height => height !== 40)) issues.push(`header-cell-height:${routeResult.dom.tables.headerCellHeights.join(',')}`);
    if (routeResult.dom.tables.dataCellHeights.some(height => height !== 40)) issues.push(`data-cell-height:${routeResult.dom.tables.dataCellHeights.join(',')}`);
    if (routeResult.dom.tables.bottomBorders.some(width => width !== '1px')) issues.push(`cell-bottom-border:${[...new Set(routeResult.dom.tables.bottomBorders)].join(',')}`);
    if (routeResult.dom.tables.dataCellPaddings.some(item => item.top !== '0px' || item.bottom !== '0px')) issues.push('data-cell-padding');
    if (routeResult.dom.tables.scrollers.some(item => item.scrollWidth > item.clientWidth + 1 && !['auto', 'scroll'].includes(item.overflowX))) issues.push('wide-table-without-internal-scroll');
    if (routeResult.dom.operations.some(item => item.position !== 'sticky' || item.height !== 40 || item.cellOverflow === 'hidden')) issues.push('sticky-operation-contract');
    if (routeResult.dom.compactCells.some(item => item.td !== 40 || item.cell !== 39 || item.children.reduce((sum, child) => sum + child.height + Number.parseFloat(child.marginTop) + Number.parseFloat(child.marginBottom), 0) > 39)) issues.push('compact-stack-height');
    if (routeResult.dom.taskMonitorErrors.some(item => item.marginTop !== '0px' || item.marginBottom !== '0px' || item.height > 15 || item.scrollHeight > item.clientHeight + 1)) issues.push('task-monitor-error-clipped');
    if (routeResult.dom.clampCells.some(item => item.lineClamp !== '2' || item.lineHeight !== '18px' || item.maxHeight !== '36px')) issues.push('compact-clamp-style');
    if (routeResult.dom.tags.some(tag => tag.height !== 24 || tag.lineHeight !== '22px' || tag.paddingLeft !== '8px' || tag.paddingRight !== '8px')) {
      issues.push(`tag-density:${routeResult.dom.tags.map(tag => `${tag.height}/${tag.lineHeight}/${tag.paddingLeft}/${tag.paddingRight}`).join(',')}`);
    }
    if (spec.path === '/system/workflow-monitor') {
      const codes = routeResult.dom.compactCodes;
      if (!codes.length || codes.some(code => code.rectHeight !== 18 || code.clientHeight !== 16 || code.scrollHeight !== 16 || code.borderTop !== '1px' || code.borderBottom !== '1px' || code.paddingTop !== '0px' || code.paddingBottom !== '0px' || code.lineHeight !== '16px' || code.rowHeight !== 40)) issues.push('workflow-compact-code-density');
      if (!routeResult.workflowCompactCodeHover?.sampleCount || !routeResult.workflowCompactCodeHover?.allTitleContractsPass) issues.push('workflow-compact-code-title');
    }
    if (routeResult.dom.controls.some(height => height < 32)) issues.push(`control-height:${routeResult.dom.controls.join(',')}`);
    if (routeResult.overflowHover.tested && !routeResult.overflowHover.titleMatchesFullText) issues.push('overflow-hover-title-mismatch');
    if (spec.path === '/info/products') {
      const long = routeResult.productClamp?.long;
      const longContractPassed = long?.status === 'not_exercised_no_real_overflow_sample' || long?.allTitlesMatch;
      const shortContractPassed = routeResult.productClamp?.short?.sampleCount > 0 && routeResult.productClamp?.short?.allWithoutGeneratedTitle;
      if (!longContractPassed || !shortContractPassed) issues.push('product-long-short-hover-contract');
    }
    if (pointRoutes.has(spec.path) && routeResult.dialog?.status !== 'opened_and_cancelled') issues.push(`safe-dialog:${routeResult.dialog?.status || 'missing'}`);
    if (spec.path === '/eval/tasks' && (!routeResult.reset?.inputCleared || !routeResult.reset?.correctReload)) issues.push('eval-reset-contract');
    if (spec.path === '/system/config' && (!routeResult.reset?.definitionsReloaded || !routeResult.reset?.candidatesReloaded || routeResult.reset?.wrongTabRequests || !routeResult.reset?.buttonEnabledAfter)) issues.push('config-reset-contract');
    const targetWarnings = consoleEvents.filter(event => event.type === 'warning' && !event.text.includes('[Vue Router warn]: No match found for location with path'));
    if (warningZeroTargets.has(spec.path) && targetWarnings.length) issues.push(`target-non-dynamic-warning:${targetWarnings.length}`);
    if (disallowedConsole.length) issues.push(`console-error-or-warning:${disallowedConsole.length}`);
    if (fallbackEvents.length) issues.push(`api-fallback:${fallbackEvents.length}`);
    if (requestFailures.length) issues.push(`request-failed:${requestFailures.length}`);
    if (unexpectedWrites.length) issues.push(`unexpected-write:${unexpectedWrites.length}`);
    if (badResponses.length) issues.push(`bad-api-response:${badResponses.length}`);
    routeResult.issues = issues;
    if (issues.length) { routeResult.status = 'fail'; evidence.fatalIssue = { route: spec.name, templatePath: spec.template || spec.path, issues }; evidence.stoppedEarly = true; }
    evidence.routes.push(routeResult);
    await saveEvidence();
  }
  const counts = evidence.routes.reduce((result, route) => { result[route.status] = (result[route.status] || 0) + 1; return result; }, {});
  evidence.summary = { totalNamedRoutes: 59, recorded: evidence.routes.length, passed: counts.pass || 0, failed: counts.fail || 0, stoppedEarly: evidence.stoppedEarly };
  await saveEvidence();
  return { summary: evidence.summary, fatalIssue: evidence.fatalIssue, parameterDiscovery: evidence.parameterDiscovery };
}
