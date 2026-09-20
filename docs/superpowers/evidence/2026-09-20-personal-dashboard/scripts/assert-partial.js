async page => {
  const text = await page.locator('body').innerText();
  const result = {
    url: page.url(),
    metricsVisible: await page.locator('[data-testid="personal-metric"]').count(),
    progressVisible: await page.locator('[data-testid="personal-progress"]').count(),
    customerStateVisible: await page.locator('[data-testid="personal-customers-state"]').count(),
    customerForbiddenText: text.includes('客户列表 mock 403') || text.includes('无权查看客户'),
    prioritiesRetainTouch: text.includes('验收示例触达-001'),
    workflowErrorText: text.includes('工作流待办不可用') || text.includes('mock 500'),
    unaffectedZonesRetained: text.includes('个人核心指标') && text.includes('我发起的业务进度')
  };
  const failed = ['customerStateVisible', 'customerForbiddenText', 'prioritiesRetainTouch', 'workflowErrorText', 'unaffectedZonesRetained']
    .filter(key => !result[key]);
  if (failed.length) throw new Error(`partial assertions failed: ${failed.join(',')}`);
  return result;
}
