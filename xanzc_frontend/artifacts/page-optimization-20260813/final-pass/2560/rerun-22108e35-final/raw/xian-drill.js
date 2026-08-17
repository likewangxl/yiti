async page => {
  const ROOT = '/home/djdev/leid/yiti/xanzc_frontend/artifacts/page-optimization-20260813/final-pass/2560/rerun-22108e35-final';
  const BASE = 'http://127.0.0.1:8091';
  const branches = [
    { name: '宝鸡分行', orgCode: '128', anchor: 'LEFT', action: 'click' },
    { name: '渭南分行', orgCode: '191', anchor: 'RIGHT', action: 'Enter' },
    { name: '咸阳分行', orgCode: '169', anchor: 'TOP', action: 'Space' },
    { name: '榆林分行', orgCode: '129', anchor: 'FAR_TOP', action: 'click' }
  ];
  const out = {
    status: 'running', viewport: [2560, 1440], session: 'pageopt-final-2560-22108e35',
    gitHead: '22108e35edb4f859c2d806ec4b1868d7aac274f6',
    overview: {
      status: 'pass',
      evidence: ['visible-canvas-screenshot-six-labels', 'schema2-XIAN_COMPOSITE-draft-contract',
        'four-satellite-buttons', 'OSM-attribution', 'title-gap-positive'],
      internalRegionsProbe: 'not_available_vue_echarts_encapsulation'
    },
    branches: [], summary: null, firstError: null
  };
  const save = async () => {
    const pending = page.waitForEvent('download');
    await page.evaluate(value => {
      const url = URL.createObjectURL(new Blob([JSON.stringify(value, null, 2)], { type: 'application/json' }));
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = 'xian-drill.json';
      anchor.click();
      URL.revokeObjectURL(url);
    }, out);
    await (await pending).saveAs(`${ROOT}/json/xian-drill.json`);
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
  const capture = async trigger => {
    const requests = [], byRequest = new Map(), pending = new Set(), reads = [], events = [];
    const onRequest = request => {
      if (!request.url().includes('/api/')) return;
      let body = null;
      try { body = request.postDataJSON(); } catch {}
      const item = { method: request.method(), url: request.url().replace(BASE, '')
        .replace(/([?&][^=&#]+)=([^&#]*)/g, '$1=<redacted>'), status: null, code: null, body };
      byRequest.set(request, item);
      requests.push(item);
      pending.add(request);
    };
    const onResponse = response => {
      const request = response.request(), item = byRequest.get(request);
      if (!item) return;
      item.status = response.status();
      pending.delete(request);
      reads.push((async () => {
        if (!(response.headers()['content-type'] || '').includes('json')) return;
        try {
          const body = await response.json();
          if (body && Object.hasOwn(body, 'code')) item.code = body.code;
        } catch {}
      })());
    };
    const onFailed = request => pending.delete(request);
    const onConsole = message => events.push({ type: message.type(), text: message.text().slice(0, 300) });
    page.on('request', onRequest);
    page.on('response', onResponse);
    page.on('requestfailed', onFailed);
    page.on('console', onConsole);
    await trigger();
    const deadline = Date.now() + 15000;
    let emptySince = 0;
    while (Date.now() < deadline) {
      if (!pending.size) {
        if (!emptySince) emptySince = Date.now();
        if (Date.now() - emptySince >= 1000) break;
      } else emptySince = 0;
      await page.waitForTimeout(100);
    }
    await Promise.allSettled(reads);
    page.off('request', onRequest);
    page.off('response', onResponse);
    page.off('requestfailed', onFailed);
    page.off('console', onConsole);
    const sanitized = requests.map(item => {
      let body = null;
      if (item.url.startsWith('/api/screen/data') && item.body && typeof item.body === 'object') {
        const identity = Object.hasOwn(item.body, 'blockId') ? 'blockId'
          : Object.hasOwn(item.body, 'dsId') ? 'dsId' : null;
        body = {
          keys: Object.keys(item.body).sort(),
          schemaVersion: item.body.schemaVersion,
          screenCode: item.body.screenCode,
          identity: identity ? { field: identity, present: true, value: '<redacted>', valueType: typeof item.body[identity] } : null,
          dateFrom: { present: Object.hasOwn(item.body, 'dateFrom'), valueType: typeof item.body.dateFrom },
          dateTo: { present: Object.hasOwn(item.body, 'dateTo'), valueType: typeof item.body.dateTo },
          contextParams: item.body.contextParams && typeof item.body.contextParams === 'object' ? {
            keys: Object.keys(item.body.contextParams).sort(),
            orgCode: String(item.body.contextParams.orgCode),
            orgCodeType: typeof item.body.contextParams.orgCode,
            empIdPresent: Object.hasOwn(item.body.contextParams, 'empId'),
            empIdType: typeof item.body.contextParams.empId
          } : null,
          nullPaths: nullPaths(item.body)
        };
      }
      return { method: item.method, url: item.url, status: item.status, code: item.code, body };
    });
    const warnings = events.filter(item => item.type === 'warning'
      && !item.text.includes('[Vue Router warn]: No match found for location with path'));
    return {
      settled: !pending.size,
      requests: sanitized,
      dataRequests: sanitized.filter(item => item.url.startsWith('/api/screen/data')),
      unexpectedWrites: sanitized.filter(item => !['GET', 'HEAD', 'OPTIONS'].includes(item.method)
        && !(item.method === 'POST' && item.url.startsWith('/api/screen/data'))),
      bad: sanitized.filter(item => item.status == null || item.status >= 400
        || (item.code != null && String(item.code) !== '0')),
      console: {
        errors: events.filter(item => item.type === 'error').length,
        nonDynamicWarnings: warnings.length,
        fallback: events.filter(item => item.text.includes('[api fallback]')).length
      }
    };
  };
  for (const branch of branches) {
    if (out.firstError) break;
    const result = { ...branch, status: 'pass', issues: [] };
    const network = await capture(async () => {
      if (await page.evaluate(() => location.hash) !== '#/screen/SCR_RETAIL_OVERVIEW?preview=draft') {
        await page.evaluate(() => { location.hash = '/screen/SCR_RETAIL_OVERVIEW?preview=draft'; });
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
      document: [document.documentElement.clientWidth, document.documentElement.scrollWidth],
      redengineElements: document.querySelectorAll('[class*=redengine],[class^=re-],a[href*=redengine]').length,
      loadError: (document.querySelector('.scr-block-err')?.textContent || '').trim(),
      guide: (document.querySelector('.scr-guide-empty')?.textContent || '').trim()
    }));
    if (result.route !== `/screen/SCR_BRANCH?orgCode=${branch.orgCode}`) result.issues.push('route');
    if (result.dataRequestCount !== 5) result.issues.push('screen-data-count');
    if (network.dataRequests.some(item => item.method !== 'POST' || item.status !== 200
      || String(item.code) !== '0' || !item.body || item.body.nullPaths.length
      || item.body.screenCode !== 'SCR_BRANCH' || item.body.schemaVersion !== 1
      || item.body.contextParams?.orgCode !== branch.orgCode
      || (item.body.contextParams?.empIdPresent && item.body.contextParams?.empIdType !== 'string'))) {
      result.issues.push('screen-data-contract');
    }
    if (!network.settled || network.unexpectedWrites.length || network.bad.length
      || network.console.errors || network.console.nonDynamicWarnings || network.console.fallback) {
      result.issues.push('runtime-or-network');
    }
    if (result.dom.document[0] !== result.dom.document[1] || result.dom.redengineElements
      || result.dom.loadError || result.dom.guide) result.issues.push('dom');
    result.status = result.issues.length ? 'fail' : 'pass';
    out.branches.push(result);
    await page.screenshot({ path: `${ROOT}/screenshots/XIAN-${branch.orgCode}-2560.png`,
      mask: [page.locator('.scr-body .scr-block:visible')], maskColor: '#d8dee9' });
    if (result.issues.length) out.firstError = { target: branch.name, orgCode: branch.orgCode, issues: result.issues };
    await save();
  }
  const allData = out.branches.flatMap(item => item.network.dataRequests);
  out.summary = {
    overview: 'pass',
    branchesPassed: out.branches.filter(item => item.status === 'pass').length,
    branchesExpected: 4,
    screenDataRequests: allData.length,
    screenData200Code0: allData.filter(item => item.status === 200 && String(item.code) === '0').length,
    screenDataBodiesWithoutNull: allData.filter(item => item.body && item.body.nullPaths.length === 0).length,
    unexpectedWrites: out.branches.reduce((sum, item) => sum + item.network.unexpectedWrites.length, 0),
    consoleErrors: out.branches.reduce((sum, item) => sum + item.network.console.errors, 0),
    redengineElements: out.branches.reduce((sum, item) => sum + item.dom.redengineElements, 0)
  };
  out.status = out.firstError ? 'fail-stopped' : out.branches.length === 4 ? 'pass' : 'incomplete';
  await save();
  return {
    status: out.status, firstError: out.firstError, summary: out.summary,
    branches: out.branches.map(item => ({ name: item.name, action: item.action, status: item.status,
      route: item.route, dataRequestCount: item.dataRequestCount, issues: item.issues,
      contracts: item.dataContracts }))
  };
}
