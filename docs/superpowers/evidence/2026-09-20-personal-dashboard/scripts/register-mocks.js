async page => {
  const ok = data => ({ code: '0', message: 'success', traceId: 'personal-qa-mock', data });
  const pageOk = (records, total = records.length, pageSize = 20) => ({
    code: '0', message: 'success', traceId: 'personal-qa-mock',
    page: { pageNo: 1, pageSize, total, totalPages: total ? 1 : 0, records }
  });
  const currentUser = {
    empId: 'E-QA-001', username: 'personal-qa', displayName: '验收示例·客户经理',
    mainOrgCode: 'ORG-QA-001', mainOrgName: '验收示例支行', roles: [{ roleCode: 'QA_PERSONAL' }]
  };
  const menus = [
    { resourceId: 'M_PERSONAL_QA', resourceUrl: '/personal-dashboard', menuName: '个人经营驾驶舱', children: [] },
    { resourceId: 'M_WORKSPACE_QA', resourceUrl: '/workspace', menuName: '工作台', children: [] },
    { resourceId: 'M_CUSTOMERS_QA', resourceUrl: '/customers/list', menuName: '我的客户', children: [] },
    { resourceId: 'M_ASSET_QA', resourceUrl: '/marketing/asset-projects', menuName: '资产立项', children: [] },
    { resourceId: 'M_SUPPORT_QA', resourceUrl: '/bizexec/supports', menuName: '中台支持', children: [] }
  ];
  const permissions = {
    resourceUrls: [
      '/api/portal/workspace', '/api/workflow/tasks', '/api/workflow/tasks/**',
      '/api/touch-tasks', '/api/touch-tasks/**', '/api/marketing/customers/mine',
      '/api/marketing/customers/**', '/api/marketing/asset-projects',
      '/api/marketing/asset-projects/**', '/api/support-requests',
      '/api/support-requests/**', '/api/orgs/tree', '/api/products/**'
    ], isSystemAdmin: false, roleCodes: ['QA_PERSONAL']
  };
  const metrics = [
    { metricCode: 'customerCount', metricName: '我的客户数', currentValue: 0, targetValue: 12, completionRate: null, unit: '户', dataTime: '2026-09-20', changeRate: 0 },
    { metricCode: 'activeCustomers', metricName: '活跃客户', currentValue: 12, targetValue: 20, completionRate: 60, unit: '户', dataTime: '2026-09-20', changeRate: null },
    { metricCode: 'depositBalance', metricName: '存款余额', currentValue: null, targetValue: null, completionRate: null, unit: '万元', dataTime: null, changeRate: null },
    { metricCode: 'businessRate', metricName: '业务完成率', currentValue: 88.5, targetValue: 100, completionRate: 88.5, unit: '%', dataTime: '2026-09-20', changeRate: 0 },
    { metricCode: 'leadConversion', metricName: '线索转化数', currentValue: 3, targetValue: 10, completionRate: null, unit: '户', dataTime: '2026-09-20', changeRate: null },
    { metricCode: 'taskCompleted', metricName: '本月已办业务', currentValue: 0, targetValue: 4, completionRate: 0, unit: '笔', dataTime: '2026-09-20', changeRate: 0 }
  ];
  const workflow = [
    { taskId: 'W-QA-001', title: '验收示例授信材料待补充', customerName: '验收示例科技有限公司', bizType: 'ALLOC_ADJUST', taskName: '材料补充', slaStatus: 'RED', timeoutTime: null, reason: '合成验收事项', canApprove: true },
    { taskId: 'W-QA-002', title: '验收示例资产立项审批', customerName: '验收示例科技有限公司', bizType: 'ASSET_PROJECT', bizId: 'A-QA-001', taskName: '分行审批', slaStatus: 'GREEN', timeoutTime: '2026-09-24 17:00', reason: '合成审批事项', canApprove: true }
  ];
  const touchPending = [
    { id: 'T-QA-001', taskNo: '验收示例触达-001', customerName: '验收示例制造有限公司', taskType: 'FIRST_TOUCH', taskStatus: 'PENDING', slaStatus: 'YELLOW', planFinishTime: '2026-09-21 17:00', canOperateTask: true, canWriteLog: true, custId: 'C-QA-001' },
    { id: 'T-QA-003', taskNo: '验收示例触达-003', customerName: '验收示例供应链有限公司', taskType: 'FIRST_TOUCH', taskStatus: 'PENDING', slaStatus: null, planFinishTime: null, canOperateTask: false, canWriteLog: false, custId: 'C-QA-004' }
  ];
  const touchInProgress = [
    { id: 'T-QA-002', taskNo: '验收示例触达-002', customerName: '验收示例商贸有限公司', taskType: 'FOLLOW_UP', taskStatus: 'IN_PROGRESS', slaStatus: 'BLUE', planFinishTime: null, canOperateTask: false, canWriteLog: true, custId: 'C-QA-002' },
    { id: 'T-QA-004', taskNo: '验收示例触达-004', customerName: '验收示例零售有限公司', taskType: 'FOLLOW_UP', taskStatus: 'IN_PROGRESS', slaStatus: 'GREEN', planFinishTime: '2026-09-26 17:00', canOperateTask: false, canWriteLog: true, custId: 'C-QA-005' }
  ];
  const customers = [
    { id: 'C-QA-001', custId: 'C-QA-001', custName: '验收示例科技有限公司', isKeystone: 1, isAccountOpened: 1, lastTouchTime: '2026-09-19 10:20', touchRestricted: 0 },
    { id: 'C-QA-002', custId: 'C-QA-002', custName: '验收示例制造有限公司（长名称用于窄屏换行检查）', isKeystone: 0, isAccountOpened: 0, lastTouchTime: '2026-09-18 14:05', touchRestricted: 1 },
    { id: 'C-QA-003', custId: 'C-QA-003', custName: '验收示例商贸有限公司', isKeystone: null, isAccountOpened: null, lastTouchTime: null, touchRestricted: 0 },
    { id: 'C-QA-004', custId: 'C-QA-004', custName: '验收示例供应链有限公司', isKeystone: 1, isAccountOpened: 1, lastTouchTime: '2026-09-17 09:00', touchRestricted: 0 },
    { id: 'C-QA-005', custId: 'C-QA-005', custName: '验收示例零售有限公司', isKeystone: 0, isAccountOpened: 1, lastTouchTime: '2026-09-16 15:40', touchRestricted: 0 },
    { id: 'C-QA-006', custId: 'C-QA-006', custName: '验收示例能源有限公司', isKeystone: null, isAccountOpened: 0, lastTouchTime: null, touchRestricted: 0 }
  ];
  const assets = [
    { id: 'A-QA-001', projectName: '验收示例资产立项', custId: 'C-QA-001', customerName: '验收示例科技有限公司', status: 'IN_APPROVAL', currentNode: '分行审批', submittedTime: '2026-09-19 09:30', createdTime: '2026-09-18 16:20' },
    { id: 'A-QA-002', projectName: '验收示例供应链项目', custId: 'C-QA-004', customerName: '验收示例供应链有限公司', status: 'DRAFT', currentNode: '发起人草稿', submittedTime: null, createdTime: '2026-09-17 13:10' },
    { id: 'A-QA-003', projectName: '验收示例能源项目', custId: 'C-QA-006', customerName: '验收示例能源有限公司', status: 'COMPLETED', currentNode: '已完成', submittedTime: '2026-09-15 12:00', createdTime: '2026-09-14 09:00' }
  ];
  const supports = [
    { id: 'S-QA-001', requestNo: '验收示例支持-001', custName: '验收示例制造有限公司', productName: '验收示例产品支持', status: 'DRAFT', currentNodeName: '发起人草稿', createdTime: '2026-09-18 11:10' },
    { id: 'S-QA-002', requestNo: '验收示例支持-002', custName: '验收示例零售有限公司', productName: '验收示例系统支持', status: 'IN_PROGRESS', currentNodeName: '中台办理', createdTime: '2026-09-16 10:10' },
    { id: 'S-QA-003', requestNo: '验收示例支持-003', custName: '验收示例能源有限公司', productName: '验收示例产品咨询', status: 'COMPLETED', currentNodeName: '已完成', createdTime: '2026-09-14 14:10' }
  ];
  const customerDetail = { id: 'C-QA-001', custId: 'C-QA-001', custName: '验收示例科技有限公司', custNo: 'QA-CUSTOMER-001', unifiedCreditCode: 'QA-CODE-001', legalRepresentative: '验收示例联系人', enterpriseType: '合成企业', customerType: '公司客户', isKeystone: 1, isAccountOpened: 1, touchRestricted: 0, lastTouchTime: '2026-09-19 10:20', mainManagerName: '验收示例·客户经理', mainOrgName: '验收示例支行' };
  const touchDetail = { id: 'T-QA-001', taskId: 'T-QA-001', taskNo: '验收示例触达-001', taskStatus: 'PENDING', custId: 'C-QA-001', customerName: '验收示例制造有限公司', canOperateTask: true, canWriteLog: true, planFinishTime: '2026-09-21 17:00', logCount: 1 };
  const touchLogs = [{ id: 'LOG-QA-001', touchTime: '2026-09-19 10:20', touchMethod: 'PHONE', logContent: '合成验收触达记录', createdBy: 'E-QA-001' }];
  const assetDetail = { ...assets[0], id: 'A-QA-001', applyNo: 'QA-ASSET-001', customerName: '验收示例科技有限公司', projectTotalInvestment: 1200, creditAmount: 500, creditExposureAmount: 300, attachments: [] };
  const supportDetail = { ...supports[0], id: 'S-QA-001', sourceType: 'EXISTING_CUSTOMER', custId: 'C-QA-002', productIds: [], otherDemand: '合成验收支持详情' };
  const routeManifest = [
    '/api/auth/current-user', '/api/auth/my-menus', '/api/auth/permissions',
    '/api/portal/workspace', '/api/workflow/tasks', '/api/touch-tasks?status=PENDING',
    '/api/touch-tasks?status=IN_PROGRESS', '/api/marketing/customers/mine',
    '/api/marketing/asset-projects', '/api/support-requests',
    '/api/marketing/customers/C-QA-001', '/api/touch-tasks/T-QA-001',
    '/api/touch-tasks/T-QA-001/logs', '/api/marketing/asset-projects/A-QA-001',
    '/api/support-requests/S-QA-001', '/api/marketing/customers',
    '/api/support-requests/available-products', '/api/orgs/tree', '/api/products/support-available',
    '/api/notifications/unread-count',
    '/api/sys/dicts/INDUSTRY/items'
  ];
  const pageResponse = (records, total, size) => pageOk(records, total, size);
  const queryValue = (raw, name) => {
    const query = String(raw || '').split('?')[1]?.split('#')[0] || '';
    const pair = query.split('&').find(item => item.split('=')[0] === name);
    if (!pair) return '';
    try { return decodeURIComponent(pair.split('=').slice(1).join('=')); } catch (_) { return pair.split('=').slice(1).join('='); }
  };
  const pathnameOf = raw => String(raw || '').replace(/^https?:\/\/[^/]+/, '').split('?')[0];
  await page.route('**/api/**', async route => {
    const requestRawUrl = route.request().url();
    const path = pathnameOf(requestRawUrl);
    if (!path.startsWith('/api/')) {
      await route.continue();
      return;
    }
    // Hash route query is not sent with API requests; read the active page URL instead.
    const scenario = queryValue(page.url(), 'qa') || 'base';
    if (scenario === 'unauth' && path === '/api/auth/current-user') {
      await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ code: '401', message: '未登录（合成验收场景）' }) });
      return;
    }
    if (scenario === 'partial' && path === '/api/workflow/tasks') {
      await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 'QA-500', message: '工作流待办 mock 500（仅开发态 mock）' }) });
      return;
    }
    if (scenario === 'partial' && path === '/api/marketing/customers/mine') {
      await route.fulfill({ status: 403, contentType: 'application/json', body: JSON.stringify({ code: 'QA-403', message: '客户列表 mock 403（仅开发态 mock）' }) });
      return;
    }
    let response;
    if (path === '/api/auth/current-user') response = ok(currentUser);
    else if (path === '/api/auth/my-menus') response = ok(menus);
    else if (path === '/api/auth/permissions') response = ok(permissions);
    else if (path === '/api/portal/workspace') response = ok({ metricCards: metrics });
    else if (path === '/api/workflow/tasks') response = pageResponse(workflow, workflow.length, 20);
    else if (path === '/api/touch-tasks' && queryValue(requestRawUrl, 'status') === 'PENDING') response = pageResponse(touchPending, touchPending.length, 10);
    else if (path === '/api/touch-tasks' && queryValue(requestRawUrl, 'status') === 'IN_PROGRESS') response = pageResponse(touchInProgress, touchInProgress.length, 10);
    else if (path === '/api/touch-tasks/T-QA-001/logs') response = ok(touchLogs);
    else if (path === '/api/touch-tasks/T-QA-001') response = ok(touchDetail);
    else if (path === '/api/marketing/customers/mine') response = pageResponse(customers, customers.length, 6);
    else if (path === '/api/marketing/customers/C-QA-001') response = ok(customerDetail);
    else if (path === '/api/marketing/customers') response = pageResponse(customers, 3, 20);
    else if (path === '/api/marketing/asset-projects/A-QA-001') response = ok(assetDetail);
    else if (path === '/api/marketing/asset-projects') response = pageResponse(assets, assets.length, 6);
    else if (path === '/api/support-requests/S-QA-001') response = ok(supportDetail);
    else if (path === '/api/support-requests') response = pageResponse(supports, supports.length, 6);
    else if (path === '/api/support-requests/available-products') response = ok([{ id: 'P-QA-001', productName: '验收示例支持产品', name: '验收示例支持产品' }]);
    else if (path === '/api/orgs/tree') response = ok([{ code: 'ORG-QA-001', name: '验收示例支行', children: [] }]);
    else if (path === '/api/products/support-available') response = ok([{ id: 'P-QA-001', productName: '验收示例支持产品', name: '验收示例支持产品' }]);
    else if (path === '/api/notifications/unread-count') response = ok(0);
    else if (/^\/api\/(?:dicts|sys\/dicts)\/[^/]+\/items$/.test(path)) response = ok([]);
    else {
      await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 'QA_UNMATCHED', message: `未登记的 mock 请求：${path}` }) });
      return;
    }
    await route.fulfill({ status: 200, contentType: 'application/json; charset=utf-8', body: JSON.stringify(response) });
  });
  return {
    mode: '仅开发态 mock，非联调',
    session: 'personal-qa',
    handler: '**/api/**（run-code 注册；未知 API 返回 QA_UNMATCHED 404，不触达真实服务）',
    routes: routeManifest,
    scenarios: { base: '四区合成数据', partial: 'workflow 500 + customers 403，其余成功', unauth: 'current-user 401' }
  };
}
