async page => {
  await page.waitForTimeout(300);
  const result = await page.evaluate(() => {
    const viewportWidth = document.documentElement.clientWidth;
    const bodyScrollWidth = document.documentElement.scrollWidth;
    const cards = [...document.querySelectorAll('[data-testid="personal-metric"]')];
    const cardBounds = cards.map(card => {
      const rect = card.getBoundingClientRect();
      return { right: rect.right, left: rect.left, width: rect.width, scrollWidth: card.scrollWidth, clientWidth: card.clientWidth };
    });
    return {
      viewportWidth,
      bodyScrollWidth,
      cardCount: cardBounds.length,
      cardBounds,
      horizontalOverflow: bodyScrollWidth > viewportWidth + 1 || cardBounds.some(item =>
        item.left < -1 || item.right > viewportWidth + 1 || item.scrollWidth > item.clientWidth + 1)
    };
  });
  if (result.cardCount !== 6 || result.horizontalOverflow) {
    throw new Error('personal metric bounds failed: ' + JSON.stringify(result));
  }
  return result;
}
