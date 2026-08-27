async page => {
  const ok = data => ({ code: '0', message: 'success', data });
  const pageOk = records => ({
    code: '0', message: 'success',
    page: { pageNo: 1, pageSize: 20, total: records.length, totalPages: 1, records },
  });
  const menus = [{
    resourceId: 'M_CUSTOMER_MARKETING', menuName: '客户营销', children: [
      { resourceId: 'M_POOL_AVAILABLE', resourceUrl: '/customers/pool/available', menuName: '待认领线索池' },
      { resourceId: 'M_POOL_CLAIMED', resourceUrl: '/customers/pool/claimed', menuName: '已认领线索池' },
      { resourceId: 'M_CROSS_ORG', resourceUrl: '/customers/cross-org', menuName: '跨机构营销' },
    ],
  }];
  await page.route('**/api/**', async route => {
    const requestUrl = route.request().url();
    const path = requestUrl.split('?')[0].replace(/^https?:\/\/[^/]+/, '');
    if (!path.startsWith('/api/')) {
      await route.continue();
      return;
    }
    const method = route.request().method();
    let body;
    if (path === '/api/auth/current-user') {
      body = ok({ empId: 'E90001', username: 'reviewer', displayName: '公司部审核员', mainOrgCode: 'ORG-HQ', mainOrgName: '公司业务部', roles: [{ roleCode: 'SYS_ADMIN' }] });
    } else if (path === '/api/auth/my-menus') {
      body = ok(menus);
    } else if (path === '/api/auth/permissions') {
      body = ok({ resourceUrls: ['/api/customer-pool', '/api/claims', '/api/cross-org-marketing'], roleCodes: ['SYS_ADMIN'], roleIds: ['R_ADMIN'], bizScopes: {} });
    } else if (path === '/api/customer-pool') {
      body = pageOk([{ id: '101', custId: '101', sourceLeadId: '501', leadId: '501', leadNo: 'LEAD-20260827-001', leadType: 'NEW_ACCOUNT', custName: '上海星海智能制造有限公司', unifiedCreditCode: '91310115MA1K4X9Y8Q', industryName: '制造业', groupTypeName: '集团客户', groupName: '星海产业集团', ownerOrgName: '浦东支行', customerTypeName: '公司客户', isKeystone: true, enterpriseTypeName: '民营企业', isAccountOpened: false, tagNames: ['重点拓展', '智能制造'], customerDesc: '拟开立基本户并开展供应链融资合作', creditAmount: 8000000, creditExposureAmount: 2500000, distributionMode: 'PUBLIC', claimScope: '全行客户经理', claimedCount: 2, releasedTime: '2026-08-27T09:30:00' }]);
    } else if (path === '/api/lead-approvals/501') {
      body = ok({ lead: { id: '501', leadNo: 'LEAD-20260827-001', custName: '上海星海智能制造有限公司', unifiedCreditCode: '91310115MA1K4X9Y8Q', distributionMode: 'PUBLIC', tagNames: ['重点拓展', '智能制造'] }, currentCustomer: null, managerEmpIds: [], tagIds: ['T01', 'T02'], attachments: [] });
    } else if (path === '/api/claims/mine/customers') {
      const touched = requestUrl.includes('tab=TOUCHED');
      body = pageOk([touched
        ? { claimId: '701', custId: '201', sourceLeadId: '601', custName: '上海云桥科技有限公司', unifiedCreditCode: '91310000MA8ABCDE12', allocationSource: 'SCOPE', sourceType: 'CLAIM', claimStatus: 'CLAIMED', claimTime: '2026-08-26T10:00:00', latestTaskId: '901', latestTaskStatus: 'SUCCESS', latestTaskNo: 'MKT-CLAIM-701', tagNames: ['科技成长'] }
        : { claimId: '702', custId: '202', sourceLeadId: '602', custName: '上海澄明供应链有限公司', unifiedCreditCode: '91310000MA8FGHIJ34', allocationSource: 'PUBLIC', sourceType: 'CLAIM', claimStatus: 'CLAIMED', claimTime: '2026-08-27T08:40:00', latestTaskStatus: null, contactPerson: '陈经理', contactMobile: '138****8899', industryName: '商务服务业', customerTypeName: '公司客户', tagNames: ['供应链'] }]);
    } else if (path === '/api/cross-org-marketing/validate') {
      body = ok({ valid: true, custId: '301', custNo: 'KH-20260827-001', custName: '上海远望新能源有限公司', mainManagerId: 'E10009', mainManagerName: '王经理', mainOrgId: 'ORG-01', mainOrgName: '徐汇支行', hasApplicantPerformance: false, hasApplicantOrgPerformance: false, checks: [
        { ruleCode: 'APPLICANT_NOT_MAIN_MANAGER', ruleName: '申请人不是主办客户经理', passed: true, message: '校验通过', dataSource: 'MARKETING_CUSTOMER_INFO' },
        { ruleCode: 'MAIN_ORG_DIFFERENT', ruleName: '主办机构与申请机构不同', passed: true, message: '校验通过', dataSource: 'MARKETING_CUSTOMER_INFO' },
        { ruleCode: 'APPLICANT_NO_PERFORMANCE', ruleName: '申请人无该客户业绩归属', passed: true, message: '校验通过', dataSource: 'MARKETING_CUSTOMER_PERFORMANCE_REL_SNAPSHOT' },
        { ruleCode: 'APPLICANT_ORG_NO_PERFORMANCE', ruleName: '申请机构无该客户业绩归属', passed: true, message: '校验通过', dataSource: 'MARKETING_CUSTOMER_PERFORMANCE_REL_SNAPSHOT' },
      ] });
    } else if (path === '/api/cross-org-marketing') {
      body = method === 'GET' ? ok([{ id: '801', applyNo: 'CROSS202608270001', custId: '301', custNo: 'KH-20260827-001', custName: '上海远望新能源有限公司', applicantName: '李经理', applicantOrgName: '公司业务二部', mainManagerName: '王经理', mainOrgName: '徐汇支行', applicantNoPerformanceCheck: 1, applicantOrgNoPerformanceCheck: 1, applicantNotMainCheck: 1, mainOrgDifferentCheck: 1, applyReason: '联合营销新能源产业链客户', createdTime: '2026-08-27T10:20:00', status: 'IN_APPROVAL', canReview: true }]) : ok('802');
    } else if (path === '/api/cross-org-marketing/801') {
      body = ok({ id: '801', applyNo: 'CROSS202608270001', custId: '301', custNo: 'KH-20260827-001', custName: '上海远望新能源有限公司', applicantName: '李经理', applicantOrgName: '公司业务二部', mainManagerName: '王经理', mainOrgName: '徐汇支行', applicantNoPerformanceCheck: 1, applicantOrgNoPerformanceCheck: 1, applicantNotMainCheck: 1, mainOrgDifferentCheck: 1, applyReason: '联合营销新能源产业链客户', status: 'IN_APPROVAL', canReview: true });
    } else {
      body = ok([]);
    }
    await route.fulfill({ status: 200, contentType: 'application/json; charset=utf-8', body: JSON.stringify(body) });
  });
}
