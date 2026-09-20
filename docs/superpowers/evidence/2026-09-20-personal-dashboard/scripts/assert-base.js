async page => {
  const text = await page.locator('body').innerText();
  const headingTexts = await page.locator('h1,h2').allTextContents();
  const result = {
    url: page.url(),
    viewport: await page.evaluate(() => ({ width: window.innerWidth, height: window.innerHeight })),
    mockLabel: await page.locator('[data-personal-qa-label]').textContent(),
    headings: headingTexts.map(value => value.trim()).filter(Boolean),
    metricCards: await page.locator('[data-testid="personal-metric"]').count(),
    priorityRows: await page.locator('[data-testid="personal-priority"]').count(),
    customerRows: await page.locator('[data-testid="personal-customer"]').count(),
    progressRows: await page.locator('[data-testid="personal-progress"]').count(),
    hasZeroValue: text.includes('0'),
    hasUnknownDash: text.includes('—'),
    hasReturnedRate: text.includes('完成率 88.50%'),
    hasLongName: text.includes('长名称用于窄屏换行检查'),
    hasRestrictedTouch: text.includes('触达受限'),
    hasNoFakeRank: !text.includes('全量优先排名')
  };
  const failed = ['mockLabel', 'hasZeroValue', 'hasUnknownDash', 'hasReturnedRate', 'hasLongName', 'hasRestrictedTouch', 'hasNoFakeRank']
    .filter(key => !result[key]);
  if (result.metricCards < 6 || result.priorityRows < 6 || result.customerRows < 6 || result.progressRows < 6) failed.push('zone-row-counts');
  if (failed.length) throw new Error(`base assertions failed: ${failed.join(',')}`);
  return result;
}
