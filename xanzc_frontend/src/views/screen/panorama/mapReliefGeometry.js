/**
 * relief 外观的几何参数和投影 bounds 计算。
 *
 * 这里的输入始终来自真实 GeoJSON 投影后的多边形；辅助函数只负责统一厚度、
 * 底座、标签抬升和倾斜变换所需的数值，不生成任何行政区几何。
 */

export const RELIEF_APPEARANCE = 'relief';

const MIN_DEPTH = 0.95;
const MAX_DEPTH = 1.2;

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
  const depth = clamp(worldScale * 0.10, MIN_DEPTH, MAX_DEPTH);
  const baseDepth = clamp(depth * 0.24, 0.20, 0.27);
  return {
    depth,
    baseDepth,
    // 顶缘/底缘与阴影需要很小但可见的空间间隔，避免 z-fighting。
    contourLift: 0.018,
    shadowGap: 0.035,
    shadowSpread: clamp(worldScale * 0.014, 0.08, 0.16),
    labelLift: 0.075,
    fitHeight: 0.88,
    rotationX: -0.38,
    rotationZ: -0.10,
    cameraOffsetY: -6,
    cameraOffsetZ: 14
  };
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
