import { describe, it, expect } from 'vitest';
import { marqueeDurationSec, marqueeAnimStyle } from '../marquee/anim';

// Marquee 跑马灯参数 → CSS animation 样式纯函数(TDD 先行,CSS animation 自研零依赖)。
// 契约:speed 为像素/秒;时长 = 滚动距离 / 速度(下限 3s 防瞬闪);direction 'right' 反向播放。
describe('marquee/anim.js 跑马灯动画纯函数', () => {
  describe('marqueeDurationSec', () => {
    it('时长 = 距离/速度(保留 2 位)', () => {
      expect(marqueeDurationSec(1200, 60)).toBe(20);
      expect(marqueeDurationSec(900, 40)).toBe(22.5);
    });
    it('时长下限 3s(距离小/速度大时防瞬闪)', () => {
      expect(marqueeDurationSec(100, 500)).toBe(3);
    });
    it('速度非法(0/负/NaN)兜底 60px/s', () => {
      expect(marqueeDurationSec(1200, 0)).toBe(20);
      expect(marqueeDurationSec(1200, -5)).toBe(20);
      expect(marqueeDurationSec(1200, 'x')).toBe(20);
    });
    it('距离非法兜底 800px', () => {
      expect(marqueeDurationSec(undefined, 40)).toBe(20);
    });
  });

  describe('marqueeAnimStyle', () => {
    it('生成 animation 样式对象(名称/时长/线性无限循环)', () => {
      const s = marqueeAnimStyle({ speed: 60, direction: 'left' }, 1200);
      expect(s.animationName).toBe('w-marquee-roll');
      expect(s.animationDuration).toBe('20s');
      expect(s.animationTimingFunction).toBe('linear');
      expect(s.animationIterationCount).toBe('infinite');
      expect(s.animationDirection).toBe('normal');
    });
    it("direction 'right' 反向播放(reverse),其余值回退 normal", () => {
      expect(marqueeAnimStyle({ direction: 'right' }, 800).animationDirection).toBe('reverse');
      expect(marqueeAnimStyle({ direction: 'bogus' }, 800).animationDirection).toBe('normal');
      expect(marqueeAnimStyle({}, 800).animationDirection).toBe('normal');
    });
    it('propValue 缺省(空对象)也能生成合法样式(速度兜底 60)', () => {
      const s = marqueeAnimStyle({}, 1200);
      expect(s.animationDuration).toBe('20s');
    });
  });
});
