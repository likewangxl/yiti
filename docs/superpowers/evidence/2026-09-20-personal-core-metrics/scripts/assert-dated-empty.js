async page => {
  await page.waitForTimeout(700);
  const text = await page.locator('body').innerText();
  const result = {
    url: page.url(),
    metricCards: await page.locator('[data-testid="personal-metric"]').count(),
    hasDatedResultMessage: text.includes('当前账号在该数据日期暂无所选指标结果'),
    hasNoNoDateMessage: !text.includes('当前账号暂无员工指标结果')
  };
  const failed = ['hasDatedResultMessage', 'hasNoNoDateMessage']
    .filter(key => !result[key]);
  if (result.metricCards !== 6) failed.push('metricCards=6');
  if (failed.length) throw new Error('dated-empty assertions failed: ' + failed.join(','));
  return result;
}
