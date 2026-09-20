async page => {
  await page.waitForTimeout(700);
  const text = await page.locator('body').innerText();
  const cards = page.locator('[data-testid="personal-metric"]');
  const result = {
    url: page.url(),
    viewport: await page.evaluate(() => ({ width: innerWidth, height: innerHeight })),
    heading: await page.getByRole('heading', { name: '个人核心指标' }).count(),
    scopeNote: text.includes('本人经营指标 · 最新导入快照（非考核结算）'),
    metricCards: await cards.count(),
    metricNames: await cards.locator('.personal-metric__name').allTextContents(),
    hasZero: text.includes('0'),
    hasMissingUnit: text.includes('单位未配置'),
    hasPreviousMonthValue: text.includes('上月末值 120'),
    hasPreviousMonthLabel: text.includes('较上月末 2.88%'),
    hasNoDailyLabel: !text.includes('日环比') && !text.includes('环比'),
    hasUnlinkedTarget: text.includes('未关联考核目标'),
    hasNoFakeTarget: !text.includes('目标 —') && !text.includes('完成率 —'),
    hasDataDate: text.includes('数据日期 2026-09-20'),
    hasFourZoneHeadings: ['个人核心指标', '今日优先事项', '我的客户', '我发起的业务进度']
      .every(title => text.includes(title))
  };
  const failed = [
    'heading', 'scopeNote', 'hasZero', 'hasMissingUnit', 'hasPreviousMonthValue',
    'hasPreviousMonthLabel', 'hasNoDailyLabel', 'hasUnlinkedTarget',
    'hasNoFakeTarget', 'hasDataDate', 'hasFourZoneHeadings'
  ].filter(key => !result[key]);
  if (result.metricCards !== 6) failed.push('metricCards=6');
  if (result.metricNames.length !== 6) failed.push('metricNames=6');
  if (failed.length) throw new Error('core assertions failed: ' + failed.join(','));
  return result;
}
