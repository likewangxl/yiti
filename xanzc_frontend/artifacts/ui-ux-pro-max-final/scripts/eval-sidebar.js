() => {
  const aside = document.querySelector('aside, [role="complementary"]');
  const toggle = document.querySelector('button[aria-label*="侧边导航"]');
  return {
    asideWidth: Math.round(aside?.getBoundingClientRect().width || 0),
    toggleLabel: toggle?.getAttribute('aria-label') || null,
    expanded: toggle?.getAttribute('aria-expanded') || null,
    navVisible: Boolean(document.querySelector('nav[aria-label="主导航"]'))
  };
}
