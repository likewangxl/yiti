function number(value, fallback = 0) {
  const result = Number(value);
  return Number.isFinite(result) ? result : fallback;
}

function bounds(rect = {}) {
  const left = number(rect.left);
  const top = number(rect.top);
  const width = Math.max(0, number(rect.width, number(rect.right) - left));
  const height = Math.max(0, number(rect.height, number(rect.bottom) - top));
  return { left, top, right: left + width, bottom: top + height, width, height };
}

function clamp(value, minimum, maximum) {
  return Math.max(minimum, Math.min(maximum, value));
}

/** Calculate an institution popup position outside the province map rectangle. */
export function positionCityTooltip(options = {}) {
  const mapRect = bounds(options.mapRect);
  const labelRect = bounds(options.labelRect);
  const viewportWidth = Math.max(1, number(options.viewport?.width, 1));
  const viewportHeight = Math.max(1, number(options.viewport?.height, 1));
  const margin = Math.max(0, number(options.margin, 12));
  const gap = Math.max(0, number(options.gap, 10));
  const width = Math.min(Math.max(1, number(options.tooltipWidth, 304)), Math.max(1, viewportWidth - margin * 2));
  const maxHeight = Math.max(1, viewportHeight - margin * 2);
  const height = Math.min(Math.max(1, number(options.tooltipHeight, 420)), maxHeight);
  const labelCenterX = labelRect.left + labelRect.width / 2;
  const labelCenterY = labelRect.top + labelRect.height / 2;
  const sideTop = clamp(labelCenterY - height / 2, margin, Math.max(margin, viewportHeight - margin - height));
  const preferred = options.side === 'right' ? 'right' : 'left';
  const sides = preferred === 'left' ? ['left', 'right'] : ['right', 'left'];

  for (const side of sides) {
    const left = side === 'left' ? mapRect.left - gap - width : mapRect.right + gap;
    const fitsViewport = left >= margin && left + width <= viewportWidth - margin;
    const outsideMap = side === 'left'
      ? left + width <= mapRect.left - gap
      : left >= mapRect.right + gap;
    if (fitsViewport && outsideMap) return { left, top: sideTop, width, height, maxHeight, placement: side };
  }

  const left = clamp(labelCenterX - width / 2, margin, Math.max(margin, viewportWidth - margin - width));
  const aboveAvailable = Math.max(0, mapRect.top - gap - margin);
  const belowAvailable = Math.max(0, viewportHeight - margin - mapRect.bottom - gap);
  if (!aboveAvailable && !belowAvailable) return null;
  const placement = belowAvailable >= aboveAvailable ? 'below' : 'above';
  const available = placement === 'below' ? belowAvailable : aboveAvailable;
  const verticalHeight = Math.min(height, available);
  const top = placement === 'below'
    ? mapRect.bottom + gap
    : mapRect.top - gap - verticalHeight;
  return { left, top, width, height: verticalHeight, maxHeight: available, placement };
}
