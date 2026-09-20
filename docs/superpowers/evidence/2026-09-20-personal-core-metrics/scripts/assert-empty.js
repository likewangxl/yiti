async page => {
  await page.waitForTimeout(700);
  const text = await page.locator('body').innerText();
  const result = {
    url: page.url(),
    metricCards: await page.locator('[data-testid="personal-metric"]').count(),
    metricNames: await page.locator('[data-testid="personal-metric"] .personal-metric__name').allTextContents(),
    hasNoResultMessage: text.includes('当前账号暂无员工指标结果'),
    hasSixNames: ['对公一般性存款余额', '一般性存款月均余额', '一般性存款年日均余额',
      '一般性存款月均较上月', '对公一般性贷款余额', '对公一般性贷款余额较上月']
      .every(name => text.includes(name)),
    hasNoFakeConnectionMessage: !text.includes('未接指标')
  };
  const failed = ['hasNoResultMessage', 'hasSixNames', 'hasNoFakeConnectionMessage']
    .filter(key => !result[key]);
  if (result.metricCards !== 6) failed.push('metricCards=6');
  if (result.metricNames.length !== 6) failed.push('metricNames=6');
  if (failed.length) throw new Error('empty assertions failed: ' + failed.join(','));
  return result;
}
