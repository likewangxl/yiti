() => {
  const nav = document.querySelector('nav[aria-label="工作区页签"]');
  const active = document.activeElement;
  const box = nav?.getBoundingClientRect();
  const activeBox = active?.getBoundingClientRect?.();
  return {
    navVisible: Boolean(nav && getComputedStyle(nav).visibility !== 'hidden' && getComputedStyle(nav).display !== 'none'),
    navWidth: Math.round(box?.width || 0),
    tabButtonCount: nav?.querySelectorAll('button').length || 0,
    focusedInsideTabNav: Boolean(nav && active && nav.contains(active)),
    focusedTag: active?.tagName || null,
    focusedVisible: Boolean(activeBox && activeBox.width > 0 && activeBox.height > 0 && activeBox.bottom > 0 && activeBox.right > 0)
  };
}
