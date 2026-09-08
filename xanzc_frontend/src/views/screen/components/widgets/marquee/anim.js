// Marquee 跑马灯动画纯函数——CSS animation 自研零依赖。
// 契约:propValue.speed 为像素/秒(滚动速度),direction 'left'(默认,内容右→左) / 'right'(反向)。
// keyframes(w-marquee-roll: translateX(0) → translateX(-100%),配合内容 padding-left:100%)
// 定义在 Component.vue 样式内;本模块只负责参数 → animation 内联样式的纯计算,便于 vitest 覆盖。

const DEFAULT_SPEED = 60;   // px/s
const DEFAULT_DISTANCE = 800; // 容器宽未测得时的兜底滚动距离(px)
const MIN_DURATION = 3;     // 秒,防距离小/速度大时瞬闪

/**
 * 滚动一圈时长(秒) = 距离 / 速度,保留 2 位小数,下限 3s。
 * 速度非法(0/负/NaN)兜底 60px/s;距离非法兜底 800px。
 */
export function marqueeDurationSec(distancePx, speed) {
  const d = Number.isFinite(Number(distancePx)) && Number(distancePx) > 0 ? Number(distancePx) : DEFAULT_DISTANCE;
  const s = Number.isFinite(Number(speed)) && Number(speed) > 0 ? Number(speed) : DEFAULT_SPEED;
  return Math.max(MIN_DURATION, Math.round((d / s) * 100) / 100);
}

/**
 * propValue + 滚动距离 → CSS animation 内联样式对象。
 * direction 'right' 反向播放(reverse),其余值回退 normal(左滚)。
 */
export function marqueeAnimStyle(propValue, distancePx) {
  const p = propValue && typeof propValue === 'object' ? propValue : {};
  return {
    animationName: 'w-marquee-roll',
    animationDuration: marqueeDurationSec(distancePx, p.speed) + 's',
    animationTimingFunction: 'linear',
    animationIterationCount: 'infinite',
    animationDirection: p.direction === 'right' ? 'reverse' : 'normal'
  };
}
