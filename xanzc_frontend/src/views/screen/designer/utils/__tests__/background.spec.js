import { describe, it, expect } from 'vitest';
import {
  gradientCss, normalizeCanvasStyle, canvasBackgroundStyle, componentBackgroundStyle
} from '../background';

// 背景增强纯函数(TDD 先行):画布全局背景三选一(纯色/线性渐变/图片 URL) + 组件级背景(透明/纯色/渐变)。
// canvas_style_json 读时兼容:旧数据(schemaVersion 1 只有 background 纯色)经 normalizeCanvasStyle 补默认,
// 不写迁移、不 mutate 输入(与画布 JSON 既有"读时兼容集中在适配函数"模式一致)。
describe('background.js 渐变/背景纯函数', () => {
  describe('gradientCss', () => {
    it('双色+角度生成标准 linear-gradient', () => {
      expect(gradientCss('#0a1f4e', '#050e2b', 90)).toBe('linear-gradient(90deg, #0a1f4e 0%, #050e2b 100%)');
    });
    it('角度缺省 135°,非法角度(NaN/字符串)回退 135°', () => {
      expect(gradientCss('#111', '#222')).toBe('linear-gradient(135deg, #111 0%, #222 100%)');
      expect(gradientCss('#111', '#222', 'abc')).toBe('linear-gradient(135deg, #111 0%, #222 100%)');
    });
    it('颜色缺省回退深色主题双色(#050e2b → #0a1f4e)', () => {
      expect(gradientCss()).toBe('linear-gradient(135deg, #050e2b 0%, #0a1f4e 100%)');
    });
  });

  describe('normalizeCanvasStyle(读时兼容)', () => {
    it('旧 schemaVersion 1 数据(仅 background 纯色)补齐 backgroundType/bgGradient/bgImage 默认', () => {
      const old = { schemaVersion: 1, background: '#050e2b', adaptor: 'keepProportion' };
      const n = normalizeCanvasStyle(old);
      expect(n.backgroundType).toBe('solid');
      expect(n.bgGradient).toEqual({ from: '#050e2b', to: '#0a1f4e', angle: 135 });
      expect(n.bgImage).toBe('');
      // 既有字段原样保留
      expect(n.background).toBe('#050e2b');
      expect(n.adaptor).toBe('keepProportion');
    });
    it('不 mutate 输入对象', () => {
      const old = { background: '#000' };
      normalizeCanvasStyle(old);
      expect(old.backgroundType).toBeUndefined();
    });
    it('已含新字段的数据原样透传(幂等)', () => {
      const cs = { backgroundType: 'gradient', bgGradient: { from: '#111', to: '#222', angle: 45 }, bgImage: 'http://x/a.png' };
      const n = normalizeCanvasStyle(cs);
      expect(n.backgroundType).toBe('gradient');
      expect(n.bgGradient).toEqual({ from: '#111', to: '#222', angle: 45 });
      expect(n.bgImage).toBe('http://x/a.png');
    });
    it('null/undefined 输入兜底返回全默认', () => {
      const n = normalizeCanvasStyle(null);
      expect(n.backgroundType).toBe('solid');
      expect(n.background).toBe('#050e2b');
    });
  });

  describe('canvasBackgroundStyle(画布/运行时舞台背景)', () => {
    it('solid:输出纯色 background', () => {
      expect(canvasBackgroundStyle({ backgroundType: 'solid', background: '#123456' }))
        .toEqual({ background: '#123456' });
    });
    it('solid 无色值时用 fallback(运行时传 transparent 保持既有行为)', () => {
      expect(canvasBackgroundStyle({}, 'transparent')).toEqual({ background: 'transparent' });
    });
    it('gradient:输出 linear-gradient', () => {
      const css = canvasBackgroundStyle({ backgroundType: 'gradient', bgGradient: { from: '#111', to: '#222', angle: 90 } });
      expect(css).toEqual({ background: 'linear-gradient(90deg, #111 0%, #222 100%)' });
    });
    it('image:输出 url 背景(cover 居中) + 纯色垫底', () => {
      const css = canvasBackgroundStyle({ backgroundType: 'image', bgImage: 'http://x/bg.png', background: '#050e2b' });
      expect(css).toEqual({
        backgroundColor: '#050e2b',
        backgroundImage: 'url(http://x/bg.png)',
        backgroundSize: 'cover',
        backgroundPosition: 'center center'
      });
    });
    it('image 但 URL 为空:回退纯色(不产生 url() 空引用)', () => {
      expect(canvasBackgroundStyle({ backgroundType: 'image', bgImage: '', background: '#050e2b' }))
        .toEqual({ background: '#050e2b' });
    });
  });

  describe('componentBackgroundStyle(组件级背景)', () => {
    it('缺省/none:返回空对象(透明,与现状一致)', () => {
      expect(componentBackgroundStyle({})).toEqual({});
      expect(componentBackgroundStyle({ bgType: 'none' })).toEqual({});
      expect(componentBackgroundStyle(null)).toEqual({});
    });
    it('solid:输出纯色', () => {
      expect(componentBackgroundStyle({ bgType: 'solid', bgColor: 'rgba(10,32,74,.55)' }))
        .toEqual({ background: 'rgba(10,32,74,.55)' });
    });
    it('solid 但未选颜色:视为透明(空对象)', () => {
      expect(componentBackgroundStyle({ bgType: 'solid' })).toEqual({});
    });
    it('gradient:输出 linear-gradient', () => {
      expect(componentBackgroundStyle({ bgType: 'gradient', bgFrom: '#111', bgTo: '#222', bgAngle: 180 }))
        .toEqual({ background: 'linear-gradient(180deg, #111 0%, #222 100%)' });
    });
  });
});
