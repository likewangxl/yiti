() => {
  const trim = value => String(value || '').replace(/\s+/g, ' ').trim();
  const bodyText = trim(document.body?.innerText || '');
  const html = document.documentElement;
  const body = document.body;
  const viewportWidth = window.innerWidth;
  const scrollWidth = Math.max(html?.scrollWidth || 0, body?.scrollWidth || 0);
  const errorTexts = [
    ...document.querySelectorAll('[role="alert"], .el-message, .el-message-box, .el-alert, [class*="error"], [class*="Error"]')
  ]
    .map(node => trim(node.innerText))
    .filter(Boolean)
    .filter(text => /error|错误|失败|异常|无权限|暂无权限|禁止|forbidden|unauthorized|登录|不存在|404|500/i.test(text))
    .map(text => text.replace(/\b\d{6,}\b/g, '[redacted-number]').slice(0, 180))
    .slice(0, 5);
  const genericErrorTexts = bodyText.split('\n')
    .map(trim)
    .filter(line => /^(Error|错误|请求失败|加载失败|无权限|暂无权限|Forbidden|Unauthorized|404|500)/i.test(line))
    .slice(0, 5);
  const pageErrorTexts = [...new Set([...errorTexts, ...genericErrorTexts])].slice(0, 5);
  return {
    path: location.hash.replace(/^#/, ''),
    finalUrl: location.href,
    h1: [...document.querySelectorAll('h1')].map(node => trim(node.innerText)).filter(Boolean).slice(0, 3),
    h1Count: document.querySelectorAll('h1').length,
    mainCount: document.querySelectorAll('main').length,
    viewport: { width: viewportWidth, height: window.innerHeight },
    domHorizontalOverflow: scrollWidth > viewportWidth + 1,
    scrollWidth,
    clientWidth: document.documentElement?.clientWidth || 0,
    pageErrorTexts,
    loginRedirect: /#\/login(?:[/?]|$)/.test(location.hash),
    noAccess: Boolean(document.querySelector('main.no-access, h1#no-access-title')) || /#\/no-access(?:[/?]|$)/.test(location.hash),
    elapsedMs: 900
  };
}
