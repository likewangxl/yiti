/** 只接受当前地图注册的真实城市 surface，排除轮廓线、底座和装饰对象。 */
export function pickRegionSurfaceHit(hits = [], surfaces = []) {
  const surfaceSet = new Set(Array.isArray(surfaces) ? surfaces : []);
  return (Array.isArray(hits) ? hits : []).find(hit => surfaceSet.has(hit?.object)) || null;
}

export function pickInteractiveHit(hits = [], surfaces = []) {
  const region = pickRegionSurfaceHit(hits, surfaces);
  const candidates = region ? [{ kind: 'region', object: region.object, hit: region, distance: Number(region.distance) }] : [];
  const point = (Array.isArray(hits) ? hits : []).find(hit => {
    let object = hit?.object;
    while (object) {
      if (object.userData?.type === 'point') return true;
      object = object.parent;
    }
    return false;
  });
  let object = point?.object;
  while (object && object.userData?.type !== 'point') object = object.parent;
  if (object) candidates.push({ kind: 'point', point: object.userData.point, hit: point, distance: Number(point.distance) });
  candidates.sort((left, right) => (Number.isFinite(left.distance) ? left.distance : Infinity)
    - (Number.isFinite(right.distance) ? right.distance : Infinity));
  return candidates[0] || null;
}
