() => {
  const trim = value => String(value || '').replace(/\s+/g, ' ').trim();
  const html = document.documentElement;
  const body = document.body;
  const viewportWidth = window.innerWidth;
  const scrollWidth = Math.max(html?.scrollWidth || 0, body?.scrollWidth || 0);
  const errorTexts = [...document.querySelectorAll('[role="alert"], .el-message, .el-alert, [class*="error"], [class*="Error"]')]
    .map(node => trim(node.innerText)).filter(Boolean).slice(0, 5);
  return {
    finalUrl: location.href,
    h1: [...document.querySelectorAll('h1')].map(node => trim(node.innerText)).filter(Boolean),
    h1Count: document.querySelectorAll('h1').length,
    mainCount: document.querySelectorAll('main').length,
    viewport: { width: viewportWidth, height: window.innerHeight },
    domHorizontalOverflow: scrollWidth > viewportWidth + 1,
    scrollWidth,
    clientWidth: document.documentElement?.clientWidth || 0,
    pageErrorTexts: errorTexts,
    loginPage: /^#\/login(?:[/?]|$)/.test(location.hash),
    passwordFieldCount: document.querySelectorAll('input[type="password"]').length,
    loginButtonCount: [...document.querySelectorAll('button')].filter(node => {
      const style = getComputedStyle(node);
      const box = node.getBoundingClientRect();
      const accessibleName = trim(node.innerText || node.getAttribute('aria-label') || node.getAttribute('title'));
      return /登\s*录/.test(accessibleName) && style.display !== 'none' && style.visibility !== 'hidden' && box.width > 0 && box.height > 0;
    }).length
  };
}
