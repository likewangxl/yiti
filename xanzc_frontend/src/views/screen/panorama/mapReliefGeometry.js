/**
 * relief 外观的几何参数和投影 bounds 计算。
 *
 * 这里的输入始终来自真实 GeoJSON 投影后的多边形；辅助函数只负责统一厚度、
 * 底座、标签抬升和倾斜变换所需的数值，不生成任何行政区几何。
 */

export const RELIEF_APPEARANCE = 'relief';

const MIN_DEPTH = 0.24;
const MAX_DEPTH = 0.36;

function finitePositive(value, fallback) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : fallback;
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

export function isReliefAppearance(value) {
  return String(value || '').trim().toLowerCase() === RELIEF_APPEARANCE;
}

/** 返回与当前投影尺度一致的 relief 几何与相机参数。 */
export function createReliefGeometryConfig(options = {}) {
  const worldWidth = finitePositive(options.worldWidth, 10);
  const worldHeight = finitePositive(options.worldHeight, 10);
  const worldScale = Math.max(worldWidth, worldHeight, 1);
  const depth = clamp(worldScale * 0.03, MIN_DEPTH, MAX_DEPTH);
  const baseDepth = clamp(depth * 0.15, 0.035, 0.055);
  return {
    depth,
    baseDepth,
    // 顶缘/底缘与阴影需要很小但可见的空间间隔，避免 z-fighting。
    contourLift: 0.018,
    shadowGap: 0.035,
    shadowSpread: clamp(worldScale * 0.014, 0.08, 0.16),
    labelLift: 0.075,
    fitHeight: 0.88,
    rotationX: -0.32,
    rotationZ: -0.19,
    cameraOffsetY: -8,
    cameraOffsetZ: 14
  };
}

/** Linear-light vertex colors for the side wall; geographic positions remain untouched. */
export function createReliefWallColors(positions, depth) {
  const height = finitePositive(depth, MIN_DEPTH);
  const linear = value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4;
  const bottom = [10, 19, 50].map(value => linear(value / 255));
  const top = [91, 116, 205].map(value => linear(value / 255));
  const colors = new Float32Array(positions.length);
  for (let index = 0; index < positions.length; index += 3) {
    const z = Number(positions[index + 2]);
    const ratio = Number.isFinite(z) ? clamp(z / height, 0, 1) : 0;
    for (let channel = 0; channel < 3; channel += 1) {
      colors[index + channel] = bottom[channel] + (top[channel] - bottom[channel]) * ratio;
    }
  }
  return colors;
}

/** Smooth duplicated vertical wall vertices without rounding cap/bevel normals or moving boundaries. */
export function smoothReliefWallNormals(positions, normals) {
  const result = new Float32Array(normals);
  const sums = new Map();
  const keyAt = index => `${positions[index].toFixed(5)},${positions[index + 1].toFixed(5)}`;
  for (let index = 0; index < normals.length; index += 3) {
    if (Math.abs(normals[index + 2]) > .001) continue;
    const key = keyAt(index);
    const sum = sums.get(key) || [0, 0];
    sum[0] += normals[index];
    sum[1] += normals[index + 1];
    sums.set(key, sum);
  }
  for (let index = 0; index < normals.length; index += 3) {
    if (Math.abs(normals[index + 2]) > .001) continue;
    const sum = sums.get(keyAt(index));
    const length = Math.hypot(...sum);
    if (length <= 1e-8) continue;
    result[index] = sum[0] / length;
    result[index + 1] = sum[1] / length;
    result[index + 2] = 0;
  }
  return result;
}

/** 顶面上的标签、城市光环和点位 marker 统一使用这个 z。 */
export function getReliefSurfaceZ(config = {}) {
  return finitePositive(config.depth, 0.72) + finitePositive(config.labelLift, 0.075);
}

/** Fit camera-space geometry directly; a rotated world AABB would add empty corners. */
export function fitReliefView(points, { aspect = 1, fitHeight = .88 } = {}) {
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
  for (const point of points) {
    if (!Number.isFinite(point.x) || !Number.isFinite(point.y)) continue;
    minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
    minY = Math.min(minY, point.y); maxY = Math.max(maxY, point.y);
  }
  if (!Number.isFinite(minX)) return null;
  const ratio = finitePositive(aspect, 1);
  const height = Math.max((maxY - minY) / clamp(fitHeight, .8, .92), (maxX - minX) / ratio / .90, .5);
  const width = height * ratio;
  const x = (minX + maxX) / 2, y = (minY + maxY) / 2;
  return { left: x - width / 2, right: x + width / 2, top: y + height / 2, bottom: y - height / 2 };
}
