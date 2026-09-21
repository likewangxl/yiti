async page => {
  const ok = data => ({
    code: '0',
    message: 'success',
    traceId: 'personal-core-metrics-qa',
    data
  });
  const pageOk = (records = []) => ({
    code: '0',
    message: 'success',
    traceId: 'personal-core-metrics-qa',
    page: { pageNo: 1, pageSize: 20, total: records.length, totalPages: records.length ? 1 : 0, records }
  });
  const currentUser = {
    empId: 'E-QA-CORE-001',
    username: 'personal-core-metrics-qa',
    displayName: '个人核心指标验收用户',
    mainOrgCode: 'ORG-QA-CORE',
    mainOrgName: '个人核心指标验收支行',
    roles: [{ roleCode: 'QA_PERSONAL' }]
  };
  const menus = [{
    resourceId: 'M_PERSONAL_CORE_QA',
    resourceUrl: '/personal-dashboard',
   menuName: '个人经营驾驶舱',
   children: []
  }, {
    resourceId: 'M_WORKSPACE_QA',
    resourceUrl: '/workspace',
    menuName: '工作台',
    children: []
  }];
  const permissions = {
    resourceUrls: ['/api/portal/workspace'],
    roleCodes: ['QA_PERSONAL'],
    isSystemAdmin: false
  };
  const coreMetrics = [
    {
      metricCode: 'CORP_DEPOSIT_BALANCE',
      metricName: '对公一般性存款余额',
      currentValue: 0,
      previousValue: null,
      targetValue: null,
      completionRate: null,
      unit: null,
      comparisonType: 'PREVIOUS_MONTH_END',
      dataTime: '2026-09-20T00:00:00',
      changeRate: null
    },
    {
      metricCode: 'GENERAL_DEPOSIT_MONTH_AVG',
      metricName: '一般性存款月均余额',
      currentValue: 123456789012,
      previousValue: 120,
      targetValue: null,
      completionRate: null,
      unit: '万元',
      comparisonType: 'PREVIOUS_MONTH_END',
      dataTime: '2026-09-20T00:00:00',
      changeRate: 2.88
    },
    {
      metricCode: 'GENERAL_DEPOSIT_YEAR_DAY_AVG',
      metricName: '一般性存款年日均余额',
      currentValue: null,
      previousValue: 0,
      targetValue: null,
      completionRate: null,
      unit: null,
      comparisonType: 'PREVIOUS_MONTH_END',
      dataTime: '2026-09-20T00:00:00',
      changeRate: null
    },
    {
      metricCode: 'GENERAL_DEPOSIT_MONTH_AVG_MOM',
      metricName: '一般性存款月均较上月',
      currentValue: 3.2,
      previousValue: null,
      targetValue: null,
      completionRate: null,
      unit: '%',
      comparisonType: 'PREVIOUS_MONTH_END',
      dataTime: '2026-09-20T00:00:00',
      changeRate: null
    },
    {
      metricCode: 'CORP_LOAN_BALANCE',
      metricName: '对公一般性贷款余额',
      currentValue: null,
      previousValue: null,
      targetValue: null,
      completionRate: null,
      unit: '万元',
      comparisonType: 'PREVIOUS_MONTH_END',
      dataTime: '2026-09-20T00:00:00',
      changeRate: null
    },
    {
      metricCode: 'CORP_LOAN_BALANCE_MOM',
      metricName: '对公一般性贷款余额较上月',
      currentValue: 0,
      previousValue: 0,
      targetValue: null,
      completionRate: null,
      unit: null,
      comparisonType: 'PREVIOUS_MONTH_END',
      dataTime: '2026-09-20T00:00:00',
      changeRate: null
    }
  ];
  const latestImportMetrics = coreMetrics.map(metric => ({
    ...metric,
    sourceType: 'EMP_LATEST_IMPORT'
  }));
  const emptyMetrics = latestImportMetrics.map(metric => ({
    ...metric,
    dataTime: null,
    currentValue: null,
    previousValue: null,
    changeRate: null
  }));
  const datedEmptyMetrics = latestImportMetrics.map(metric => ({
    ...metric,
    currentValue: null,
    previousValue: null,
    changeRate: null
  }));
  const pathOf = raw => String(raw || '').replace(/^https?:\/\/[^/]+/, '').split('?')[0];
  const scenarioOf = () => page.url().match(/[?&]qa=([^&#]+)/)?.[1] || 'core';

  await page.route('**/api/**', async route => {
    const path = pathOf(route.request().url());
    if (!path.startsWith('/api/')) return route.continue();
    if (path === '/api/auth/current-user') return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(ok(currentUser)) });
    if (path === '/api/auth/my-menus') return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(ok(menus)) });
    if (path === '/api/auth/permissions') return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(ok(permissions)) });
    if (path === '/api/portal/workspace') {
      const scenario = scenarioOf();
      const metrics = scenario === 'empty' ? emptyMetrics
        : scenario === 'dated-empty' ? datedEmptyMetrics
          : latestImportMetrics;
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(ok({ metricCards: metrics })) });
    }
    if (path.includes('/workflow/') || path.includes('/touch-tasks') || path.includes('/marketing/')
      || path.includes('/support-requests')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(pageOk()) });
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(ok([])) });
  });

  return {
    mode: '仅开发态 mock，非联调',
    handler: '**/api/**（本脚本注册；不触达真实服务）',
    routes: [
      '/api/auth/current-user',
      '/api/auth/my-menus',
      '/api/auth/permissions',
      '/api/portal/workspace',
      '其他个人页查询返回空列表/空对象'
    ],
    scenarios: {
      core: '六项个人核心经营指标，含 0/null、单位缺失、previousValue 和未关联目标',
      empty: '无员工宽表行：六项指标名称保留、dataTime 为空，提示暂无员工指标结果',
      'dated-empty': '有数据日期但 actual 全 null，提示按数据日期暂无员工指标结果'
    }
  };
}
