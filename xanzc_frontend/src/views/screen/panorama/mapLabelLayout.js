/** Avoid collisions in projected screen space; geographic geometry stays unchanged. */
export function layoutMapLabels(labels) {
  const placed=[]; const result={};
  for (const label of labels) {
    const offsets=[[0,0]];
    for(let ring=1;ring<=5;ring++) for(const [x,y] of [[0,1],[0,-1],[1,0],[-1,0],[1,1],[-1,1],[1,-1],[-1,-1]]) offsets.push([x*ring,y*ring]);
    const w=label.width,h=label.height;
    let chosen;
    for(const [dx,dy] of offsets) {
      const x=Math.max(w/2+1,Math.min(99-w/2,label.x+dx*(w+1)));
      const y=Math.max(h/2+1,Math.min(99-h/2,label.y+dy*(h+1)));
      if(!placed.some(p=>Math.abs(x-p.x)<(w+p.width)/2+0.4&&Math.abs(y-p.y)<(h+p.height)/2+0.4)) {chosen={x,y};break;}
    }
    chosen ||= {x:Math.max(w/2+1,Math.min(99-w/2,label.x)),y:Math.max(h/2+1,Math.min(99-h/2,label.y))};
    placed.push({...chosen,width:w,height:h});result[label.key]=chosen;
  }
  return result;
}

function finiteNumber(value, fallback = 0) {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function calloutAnchor(label) {
  const anchor = label?.anchor;
  if (anchor && typeof anchor === 'object') {
    return { x: finiteNumber(anchor.x), y: finiteNumber(anchor.y) };
  }
  return {
    x: finiteNumber(label?.anchorX ?? label?.x),
    y: finiteNumber(label?.anchorY ?? label?.y)
  };
}

function clamp(value, minimum, maximum) {
  return Math.max(minimum, Math.min(maximum, value));
}

/**
 * Lay out province city labels as two outside columns with a three-point leader.
 *
 * `anchor` is deliberately kept separate from the output label position. The
 * caller must pass the projected administrative centre here, never a position
 * that has already been moved to avoid another label.
 */
export function layoutMapCallouts(labels = [], options = {}) {
  const source = Array.isArray(labels) ? labels : [];
  const bounds = {
    left: finiteNumber(options.bounds?.left, 7),
    right: finiteNumber(options.bounds?.right, 93),
    top: finiteNumber(options.bounds?.top, 14),
    bottom: finiteNumber(options.bounds?.bottom, 84)
  };
  const minGap = Math.max(0, finiteNumber(options.minGap, 2));
  const normalized = source.map((label, index) => ({
    label,
    index,
    key: label?.key == null ? String(index) : String(label.key),
    anchor: calloutAnchor(label),
    width: Math.max(0, finiteNumber(label?.width, 14)),
    height: Math.max(0, finiteNumber(label?.height, 6))
  }));
  if (!normalized.length) return {};

  // Western cities use the left column and eastern cities use the right one.
  // Sorting by the true anchor keeps the columns balanced even when the source
  // feature order changes, then each side is laid out independently by latitude.
  const byLongitude = normalized.slice().sort((a, b) => (
    a.anchor.x - b.anchor.x || a.anchor.y - b.anchor.y || a.index - b.index
  ));
  const leftCount = Math.ceil(byLongitude.length / 2);
  const left = byLongitude.slice(0, leftCount).map(item => ({ ...item, side: 'left' }));
  const right = byLongitude.slice(leftCount).map(item => ({ ...item, side: 'right' }));

  const sideRows = (rows, side) => {
    const sorted = rows.slice().sort((a, b) => a.anchor.y - b.anchor.y || a.index - b.index);
    if (!sorted.length) return [];
    const heights = sorted.map(row => row.height);
    const totalHeight = heights.reduce((sum, height) => sum + height, 0);
    const availableHeight = Math.max(0, bounds.bottom - bounds.top);
    const gap = sorted.length > 1
      ? Math.max(0, Math.min(minGap, (availableHeight - totalHeight) / (sorted.length - 1)))
      : 0;
    const ideal = sorted.map(row => clamp(
      row.anchor.y,
      bounds.top + row.height / 2,
      bounds.bottom - row.height / 2
    ));
    const positions = [];
    sorted.forEach((row, index) => {
      const previous = positions[index - 1];
      const minimum = previous == null
        ? bounds.top + row.height / 2
        : previous + heights[index - 1] / 2 + gap + row.height / 2;
      positions.push(Math.max(ideal[index], minimum));
    });

    const lastBottom = positions.at(-1) + sorted.at(-1).height / 2;
    if (lastBottom > bounds.bottom) {
      const shift = lastBottom - bounds.bottom;
      for (let index = 0; index < positions.length; index += 1) positions[index] -= shift;
    }
    const firstTop = positions[0] - sorted[0].height / 2;
    if (firstTop < bounds.top) {
      const shift = bounds.top - firstTop;
      for (let index = 0; index < positions.length; index += 1) positions[index] += shift;
    }

    const maxWidth = Math.max(...sorted.map(row => row.width), 0);
    const columnX = side === 'left'
      ? bounds.left + maxWidth / 2
      : bounds.right - maxWidth / 2;
    return sorted.map((row, index) => {
      const labelY = clamp(positions[index], bounds.top + row.height / 2, bounds.bottom - row.height / 2);
      const edgeX = side === 'left' ? columnX + row.width / 2 : columnX - row.width / 2;
      const elbowX = side === 'left'
        ? edgeX + Math.max(3, (50 - edgeX) * 0.18)
        : edgeX - Math.max(3, (edgeX - 50) * 0.18);
      const anchor = { ...row.anchor };
      const elbow = { x: elbowX, y: labelY };
      const label = { x: columnX, y: labelY };
      const edge = { x: edgeX, y: labelY };
      return {
        ...row.label,
        key: row.key,
        side,
        width: row.width,
        height: row.height,
        anchor,
        elbow,
        label,
        edge,
        // A polyline can be rendered in either SVG or a canvas-independent
        // overlay without reconstructing the geometry in the component.
        points: [[anchor.x, anchor.y], [elbow.x, elbow.y], [edge.x, edge.y]],
        x: label.x,
        y: label.y
      };
    });
  };

  return Object.fromEntries([
    ...sideRows(left, 'left'),
    ...sideRows(right, 'right')
  ].map(position => [position.key, position]));
}
