async page => {
  const ROOT = '/home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization-20260813/final-pass/1920';
  const BASE = 'http://127.0.0.1:8091';
  const OVERVIEW_URL = `${BASE}/#/screen/SCR_RETAIL_OVERVIEW?preview=draft`;
  const expectedRegions = ['未央区', '莲湖区', '新城区', '碑林区', '雁塔区', '长安区'];
  const branches = [
    { name: '宝鸡分行', orgCode: '128', anchor: 'LEFT', action: 'click' },
    { name: '渭南分行', orgCode: '191', anchor: 'RIGHT', action: 'Enter' },
    { name: '咸阳分行', orgCode: '169', anchor: 'TOP', action: 'Space' },
    { name: '榆林分行', orgCode: '129', anchor: 'FAR_TOP', action: 'click' }
  ];
  const out = {
    status: 'running',
    viewport: { width: 1920, height: 1080 },
    session: 'final1920xianfinal',
    gitHead: '22108e35edb4f859c2d806ec4b1868d7aac274f6',
    generatedAt: new Date().toISOString(),
    authentication: { mode: 'normal', credentialsArchived: false },
    safety: {
      mockRoutesAtStart: 'No active routes', mockRoutesRegistered: 0,
      allowedWrites: ['POST /api/screen/data (read-only runtime query)'],
      requestBodiesArchived: false, responseBodiesArchived: false,
      dsIdValuesArchived: false
    },
    overview: null,
    branches: [],
    summary: null,
    firstError: null
  };

  const save = async () => {
    const pending = page.waitForEvent('download');
    await page.evaluate(value => {
      const url = URL.createObjectURL(new Blob([JSON.stringify(value, null, 2)], { type: 'application/json' }));
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = 'xian-final-1920-22108e35.json';
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    }, out);
    await (await pending).saveAs(`${ROOT}/json/xian-final-1920-22108e35.json`);
  };

  const nullPaths = (value, path = '$') => {
    const result = [];
    if (value === null) return [path];
    if (Array.isArray(value)) {
      value.forEach((item, index) => result.push(...nullPaths(item, `${path}[${index}]`)));
    } else if (value && typeof value === 'object') {
      for (const [key, item] of Object.entries(value)) result.push(...nullPaths(item, `${path}.${key}`));
    }
    return result;
  };

  const waitForIdle = async pending => {
    const deadline = Date.now() + 15000;
    let emptySince = 0;
    while (Date.now() < deadline) {
      if (!pending.size) {
        if (!emptySince) emptySince = Date.now();
        if (Date.now() - emptySince >= 1000) return true;
      } else emptySince = 0;
      await page.waitForTimeout(100);
    }
    return !pending.size;
  };

  const capture = async trigger => {
    const requests = [];
    const byRequest = new Map();
    const pending = new Set();
    const responseReads = [];
    const events = [];
    const onRequest = request => {
      if (!request.url().includes('/api/')) return;
      let body = null;
      try { body = request.postDataJSON(); } catch {}
      const item = {
        method: request.method(),
        url: request.url().replace(BASE, '').replace(/([?&][^=&#]+)=([^&#]*)/g, '$1=<redacted>'),
        status: null, code: null, body, viewContract: null
      };
      byRequest.set(request, item);
      requests.push(item);
      pending.add(request);
    };
    const onResponse = response => {
      const request = response.request();
      const item = byRequest.get(request);
      if (!item) return;
      item.status = response.status();
      pending.delete(request);
      responseReads.push((async () => {
        if (!(response.headers()['content-type'] || '').includes('json')) return;
        try {
          const envelope = await response.json();
          if (envelope && Object.hasOwn(envelope, 'code')) item.code = envelope.code;
          if (item.url.startsWith('/api/screen/view/SCR_RETAIL_OVERVIEW')) {
            const data = envelope?.data || {};
            const map = data.mapPackage || {};
            item.viewContract = {
              screenCode: data.screenCode,
              screenName: data.screenName,
              schemaVersion: map.schemaVersion,
              mode: map.mode,
              baseRegion: map.baseRegion,
              localCount: Array.isArray(map.localPoints) ? map.localPoints.length : null,
              satelliteNodes: Array.isArray(map.satelliteNodes)
                ? map.satelliteNodes.map(node => ({ orgCode: String(node.orgCode), anchor: node.anchor })) : []
            };
          }
        } catch {}
      })());
    };
    const onFailed = request => pending.delete(request);
    const onConsole = message => events.push({ type: message.type(), text: message.text().slice(0, 500) });
    page.on('request', onRequest);
    page.on('response', onResponse);
    page.on('requestfailed', onFailed);
    page.on('console', onConsole);
    let triggerError = null;
    try { await trigger(); } catch (error) { triggerError = String(error?.message || error); }
    const settled = await waitForIdle(pending);
    await Promise.allSettled(responseReads);
    page.off('request', onRequest);
    page.off('response', onResponse);
    page.off('requestfailed', onFailed);
    page.off('console', onConsole);

    const sanitized = requests.map(item => {
      let body = null;
      if (item.url.startsWith('/api/screen/data') && item.body && typeof item.body === 'object') {
        body = {
          keys: Object.keys(item.body).sort(),
          schemaVersion: item.body.schemaVersion,
          screenCode: item.body.screenCode,
          dsId: {
            present: Object.hasOwn(item.body, 'dsId'),
            valueArchived: false,
            valueType: typeof item.body.dsId,
            positiveSafeInteger: Number.isSafeInteger(item.body.dsId) && item.body.dsId > 0
          },
          period: {
            present: Object.hasOwn(item.body, 'period'),
            value: item.body.period,
            valueType: typeof item.body.period,
            nonEmpty: typeof item.body.period === 'string' && item.body.period.length > 0
          },
          contextParams: item.body.contextParams && typeof item.body.contextParams === 'object' ? {
            keys: Object.keys(item.body.contextParams).sort(),
            orgCode: String(item.body.contextParams.orgCode),
            orgCodeType: typeof item.body.contextParams.orgCode,
            empId: {
              present: Object.hasOwn(item.body.contextParams, 'empId'),
              valueType: typeof item.body.contextParams.empId,
              emptyString: item.body.contextParams.empId === '',
              emptyStringIsValid: true
            }
          } : null,
          nullPaths: nullPaths(item.body),
          undefinedPaths: []
        };
      }
      return {
        method: item.method, url: item.url, status: item.status, code: item.code,
        body, viewContract: item.viewContract
      };
    });
    const warnings = events.filter(item => item.type === 'warning'
      && !item.text.includes('[Vue Router warn]: No match found for location with path'));
    return {
      triggerError, settled,
      requests: sanitized,
      dataRequests: sanitized.filter(item => item.url.startsWith('/api/screen/data')),
      unexpectedWrites: sanitized.filter(item => !['GET', 'HEAD', 'OPTIONS'].includes(item.method)
        && !(item.method === 'POST' && item.url.startsWith('/api/screen/data'))),
      bad: sanitized.filter(item => item.status == null || item.status >= 400
        || (item.code != null && String(item.code) !== '0')),
      console: {
        errors: events.filter(item => item.type === 'error').length,
        nonDynamicWarnings: warnings.length,
        dynamicMenuWarnings: events.filter(item => item.type === 'warning'
          && item.text.includes('[Vue Router warn]: No match found for location with path')).length,
        fallback: events.filter(item => item.text.includes('[api fallback]')).length,
        entries: events.map(item => ({ type: item.type, text: item.text }))
      }
    };
  };

  const overviewNetwork = await capture(async () => {
    await page.goto(OVERVIEW_URL);
    await page.locator('.mp-title').waitFor({ state: 'visible', timeout: 10000 });
    await page.locator('.mp-xian-chart canvas').waitFor({ state: 'visible', timeout: 10000 });
    await page.waitForTimeout(1000);
  });
  const overviewDom = await page.evaluate(() => {
    const rect = element => {
      if (!element) return null;
      const value = element.getBoundingClientRect();
      return { left: value.left, top: value.top, right: value.right, bottom: value.bottom,
        width: value.width, height: value.height };
    };
    const outer = document.querySelector('.scr-title');
    const inner = document.querySelector('.mp-title');
    const outerRect = rect(outer);
    const innerRect = rect(inner);
    const chartRoot = document.querySelector('.mp-xian-chart');
    const chart = chartRoot?.__vueParentComponent?.setupState?.chart;
    const series = chart?.getModel?.().getSeriesByIndex?.(0);
    const regionNames = (series?.coordinateSystem?.regions || []).map(region => region.name);
    const nodes = [...document.querySelectorAll('.mp-satellite-node')].map(node => ({
      name: (node.textContent || '').trim(),
      ariaLabel: node.getAttribute('aria-label'),
      anchor: node.dataset.anchor,
      disabled: node.disabled,
      tabIndex: node.tabIndex,
      rect: rect(node)
    }));
    const attribution = document.querySelector('.mp-attribution-link');
    return {
      viewport: { width: window.innerWidth, height: window.innerHeight },
      route: location.hash.replace(/^#/, ''),
      outerTitle: (outer?.textContent || '').trim(),
      innerTitle: (inner?.textContent || '').trim(),
      outerRect, innerRect,
      titleOverlap: Boolean(outerRect && innerRect && outerRect.right > innerRect.left
        && outerRect.left < innerRect.right && outerRect.bottom > innerRect.top && outerRect.top < innerRect.bottom),
      chart: {
        found: Boolean(chart), width: chart?.getWidth?.() || 0, height: chart?.getHeight?.() || 0,
        geoMap: chart?.getOption?.()?.geo?.[0]?.map || null,
        regionNames
      },
      satelliteNodes: nodes,
      localNodeCount: document.querySelectorAll('.mp-local-node').length,
      disclaimer: (document.querySelector('.mp-disclaimer')?.textContent || '').trim(),
      attribution: attribution ? {
        href: attribution.href, target: attribution.target,
        relTokens: (attribution.rel || '').split(/\s+/).filter(Boolean).sort()
      } : null,
      documentOverflowX: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
      configGap: (document.querySelector('.mp-config-gap')?.textContent || '').trim(),
      loadError: (document.querySelector('.scr-block-err')?.textContent || '').trim(),
      redengineElements: document.querySelectorAll('[class*="redengine"],a[href*="redengine"]').length
    };
  });
  const overviewView = overviewNetwork.requests.find(item => item.url.startsWith('/api/screen/view/SCR_RETAIL_OVERVIEW'));
  const overviewIssues = [];
  const expectedNodes = branches.map(item => ({ orgCode: item.orgCode, anchor: item.anchor }));
  if (overviewNetwork.triggerError) overviewIssues.push('trigger-error');
  if (!overviewNetwork.settled || overviewNetwork.unexpectedWrites.length || overviewNetwork.bad.length
    || overviewNetwork.console.errors || overviewNetwork.console.nonDynamicWarnings || overviewNetwork.console.fallback) {
    overviewIssues.push('runtime-or-network');
  }
  if (!overviewView || overviewView.status !== 200 || String(overviewView.code) !== '0') overviewIssues.push('draft-get');
  if (!overviewView?.viewContract || overviewView.viewContract.screenCode !== 'SCR_RETAIL_OVERVIEW'
    || overviewView.viewContract.schemaVersion !== 2 || overviewView.viewContract.mode !== 'XIAN_COMPOSITE'
    || overviewView.viewContract.baseRegion !== 'XIAN_OUTLINE'
    || JSON.stringify(overviewView.viewContract.satelliteNodes) !== JSON.stringify(expectedNodes)) {
    overviewIssues.push('draft-contract');
  }
  if (overviewDom.viewport.width !== 1920 || overviewDom.viewport.height !== 1080
    || overviewDom.route !== '/screen/SCR_RETAIL_OVERVIEW?preview=draft'
    || overviewDom.outerTitle !== '零售经营总览' || overviewDom.innerTitle !== '西安六区经营地图'
    || overviewDom.titleOverlap || overviewDom.documentOverflowX || overviewDom.configGap || overviewDom.loadError) {
    overviewIssues.push('overview-layout');
  }
  if (!overviewDom.chart.found || overviewDom.chart.geoMap !== 'xian-six-districts'
    || JSON.stringify(overviewDom.chart.regionNames) !== JSON.stringify(expectedRegions)) overviewIssues.push('six-district-chart');
  if (overviewDom.satelliteNodes.length !== 4 || overviewDom.satelliteNodes.some((node, index) =>
    node.name !== branches[index].name || node.anchor !== branches[index].anchor || node.disabled || node.tabIndex !== 0
    || !node.ariaLabel?.includes('二级分行示意位置，非地理比例，按 Enter 或空格进入机构详情屏'))) {
    overviewIssues.push('satellite-nodes');
  }
  if (overviewDom.disclaimer !== '二级分行示意位置，非地理比例'
    || overviewDom.attribution?.href !== 'https://www.openstreetmap.org/copyright'
    || overviewDom.attribution?.target !== '_blank'
    || !overviewDom.attribution?.relTokens.includes('noopener')
    || !overviewDom.attribution?.relTokens.includes('noreferrer')) overviewIssues.push('attribution');
  if (overviewDom.redengineElements) overviewIssues.push('redengine-element');
  out.overview = {
    status: overviewIssues.length ? 'fail' : 'pass', issues: overviewIssues,
    network: overviewNetwork, dom: overviewDom
  };
  await page.screenshot({ path: `${ROOT}/screenshots/XIAN-overview-1920.png`, fullPage: false });
  if (overviewIssues.length) out.firstError = { target: 'XIAN-overview', issues: overviewIssues };
  await save();

  for (const branch of branches) {
    if (out.firstError) break;
    const result = { ...branch, status: 'pass', issues: [] };
    const network = await capture(async () => {
      if (await page.evaluate(() => location.hash) !== '#/screen/SCR_RETAIL_OVERVIEW?preview=draft') {
        await page.goto(OVERVIEW_URL);
        await page.locator('.mp-title').waitFor({ state: 'visible', timeout: 10000 });
        await page.waitForTimeout(700);
      }
      const button = page.getByRole('button', { name: new RegExp(`^${branch.name}，`) });
      await button.waitFor({ state: 'visible', timeout: 5000 });
      if (branch.action === 'click') await button.click();
      else {
        await button.focus();
        await page.keyboard.press(branch.action);
      }
      await page.waitForFunction(code => location.hash.startsWith('#/screen/SCR_BRANCH?')
        && new URLSearchParams(location.hash.split('?')[1] || '').get('orgCode') === code,
      branch.orgCode, { timeout: 10000 });
      await page.locator('.scr-body').waitFor({ state: 'visible', timeout: 10000 });
    });
    result.route = await page.evaluate(() => location.hash.replace(/^#/, ''));
    result.network = network;
    result.dataRequestCount = network.dataRequests.length;
    result.dataContracts = network.dataRequests.map(item => item.body);
    result.dom = await page.evaluate(() => ({
      outerTitle: (document.querySelector('.scr-title')?.textContent || '').trim(),
      viewport: [window.innerWidth, window.innerHeight],
      document: [document.documentElement.clientWidth, document.documentElement.scrollWidth],
      blockCount: document.querySelectorAll('.scr-body .scr-block').length,
      visibleBlockCount: [...document.querySelectorAll('.scr-body .scr-block')]
        .filter(item => { const rect = item.getBoundingClientRect(); return rect.width > 0 && rect.height > 0; }).length,
      redengineElements: document.querySelectorAll('[class*="redengine"],a[href*="redengine"]').length,
      loadError: (document.querySelector('.scr-block-err')?.textContent || '').trim(),
      guide: (document.querySelector('.scr-guide-empty')?.textContent || '').trim()
    }));
    if (network.triggerError) result.issues.push('trigger-error');
    if (result.route !== `/screen/SCR_BRANCH?orgCode=${branch.orgCode}`) result.issues.push('route');
    if (result.dataRequestCount !== 5) result.issues.push('screen-data-count');
    if (network.dataRequests.some(item => item.method !== 'POST' || item.status !== 200
      || String(item.code) !== '0' || !item.body || item.body.nullPaths.length || item.body.undefinedPaths.length
      || item.body.screenCode !== 'SCR_BRANCH' || item.body.schemaVersion !== 1
      || !item.body.dsId.present || !item.body.dsId.positiveSafeInteger
      || !item.body.period.present || !item.body.period.nonEmpty
      || item.body.contextParams?.orgCode !== branch.orgCode
      || item.body.contextParams?.orgCodeType !== 'string'
      || !item.body.contextParams?.empId.present || item.body.contextParams?.empId.valueType !== 'string')) {
      result.issues.push('screen-data-contract');
    }
    if (!network.settled || network.unexpectedWrites.length || network.bad.length
      || network.console.errors || network.console.nonDynamicWarnings || network.console.fallback) {
      result.issues.push('runtime-or-network');
    }
    if (result.dom.viewport[0] !== 1920 || result.dom.viewport[1] !== 1080
      || result.dom.document[0] !== result.dom.document[1] || result.dom.redengineElements
      || result.dom.loadError || result.dom.guide || result.dom.blockCount !== 5 || result.dom.visibleBlockCount !== 5) {
      result.issues.push('dom');
    }
    result.status = result.issues.length ? 'fail' : 'pass';
    out.branches.push(result);
    await page.screenshot({
      path: `${ROOT}/screenshots/XIAN-${branch.orgCode}-1920.png`,
      mask: [page.locator('.scr-body .scr-block:visible')], maskColor: '#d8dee9'
    });
    if (result.issues.length) out.firstError = { target: branch.name, orgCode: branch.orgCode, issues: result.issues };
    await save();
  }

  const allData = out.branches.flatMap(item => item.network.dataRequests);
  const branchLayouts = out.branches.map(item => `${item.dom.outerTitle}|${item.dom.blockCount}|${item.dom.visibleBlockCount}`);
  out.summary = {
    overview: out.overview.status,
    branchesPassed: out.branches.filter(item => item.status === 'pass').length,
    branchesExpected: 4,
    identicalBranchLayout: branchLayouts.length === 4 && new Set(branchLayouts).size === 1,
    screenDataRequests: allData.length,
    screenData200Code0: allData.filter(item => item.status === 200 && String(item.code) === '0').length,
    screenDataBodiesWithoutNullOrUndefined: allData.filter(item => item.body
      && !item.body.nullPaths.length && !item.body.undefinedPaths.length).length,
    screenDataWithValidDsIdAndPeriod: allData.filter(item => item.body?.dsId.positiveSafeInteger
      && item.body?.period.nonEmpty).length,
    empIdEmptyStringAccepted: allData.filter(item => item.body?.contextParams?.empId.emptyString).length,
    unexpectedWrites: out.branches.reduce((sum, item) => sum + item.network.unexpectedWrites.length, 0)
      + out.overview.network.unexpectedWrites.length,
    consoleErrors: out.branches.reduce((sum, item) => sum + item.network.console.errors, 0)
      + out.overview.network.console.errors,
    redengineElements: out.branches.reduce((sum, item) => sum + item.dom.redengineElements, 0)
      + out.overview.dom.redengineElements
  };
  if (!out.firstError && (!out.summary.identicalBranchLayout || out.summary.screenDataRequests !== 20
    || out.summary.screenData200Code0 !== 20 || out.summary.screenDataBodiesWithoutNullOrUndefined !== 20
    || out.summary.screenDataWithValidDsIdAndPeriod !== 20)) {
    out.firstError = { target: 'XIAN-summary', issues: ['aggregate-contract'] };
  }
  out.status = out.firstError ? 'fail-stopped' : out.branches.length === 4 ? 'pass' : 'incomplete';
  await save();
  return {
    status: out.status, firstError: out.firstError, summary: out.summary,
    overview: {
      status: out.overview.status, issues: out.overview.issues,
      chart: out.overview.dom.chart, nodes: out.overview.dom.satelliteNodes.map(node => ({ name: node.name, anchor: node.anchor }))
    },
    branches: out.branches.map(item => ({ name: item.name, action: item.action, status: item.status,
      route: item.route, dataRequestCount: item.dataRequestCount, issues: item.issues }))
  };
}
