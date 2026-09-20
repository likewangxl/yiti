async page => page.evaluate(() => {
  const old = document.querySelector('[data-personal-qa-label]');
  if (old) old.remove();
  const label = document.createElement('div');
  label.dataset.personalQaLabel = 'true';
  label.textContent = '仅开发态 mock · 非联调';
  Object.assign(label.style, {
    position: 'fixed', top: '10px', right: '12px', zIndex: '9999',
    padding: '5px 9px', border: '1px solid rgba(249,201,105,.55)',
    borderRadius: '999px', color: '#ffd982', background: 'rgba(22,25,50,.9)',
    font: '600 12px/1.2 sans-serif', pointerEvents: 'none'
  });
  document.body.appendChild(label);
  return { injected: true, text: label.textContent };
})
