import { describe, it, expect } from 'vitest';
import { BORDER_VARIANTS, CORNER_VARIANTS, borderDecorClass } from '../border-decor/variants';

// BorderDecor 边框样式映射(TDD 先行):本期从 3 种扩到 ≥6 种科技边框(CSS 自绘零图片零依赖)。
// variants.js 是 Component/Attr 共用的唯一样式清单来源,防面板选项与渲染类名漂移。
describe('border-decor/variants.js 边框样式映射', () => {
  it('提供 ≥6 种边框样式且 value 唯一、label 齐全', () => {
    expect(BORDER_VARIANTS.length).toBeGreaterThanOrEqual(6);
    const values = BORDER_VARIANTS.map(v => v.value);
    expect(new Set(values).size).toBe(values.length);
    for (const v of BORDER_VARIANTS) {
      expect(v.value).toBeTruthy();
      expect(v.label).toBeTruthy();
    }
  });
  it('包含既有 3 种(tech-a/b/c,存量画布 JSON 不破坏)与新增科技样式(渐变霓虹/斜切角/点阵角/内发光)', () => {
    const values = BORDER_VARIANTS.map(v => v.value);
    for (const v of ['tech-a', 'tech-b', 'tech-c', 'tech-d', 'tech-e', 'tech-f', 'tech-g']) {
      expect(values).toContain(v);
    }
  });
  it('borderDecorClass:variant → decor-* 类名;非法/缺省回退 tech-a(存量兼容)', () => {
    expect(borderDecorClass('tech-d')).toBe('decor-tech-d');
    expect(borderDecorClass('bogus')).toBe('decor-tech-a');
    expect(borderDecorClass(undefined)).toBe('decor-tech-a');
  });
  it('CORNER_VARIANTS(需渲染四角 span 的变体)是 BORDER_VARIANTS 子集,含 tech-b(四角光标)/tech-f(点阵角)', () => {
    const values = BORDER_VARIANTS.map(v => v.value);
    for (const v of CORNER_VARIANTS) expect(values).toContain(v);
    expect(CORNER_VARIANTS).toContain('tech-b');
    expect(CORNER_VARIANTS).toContain('tech-f');
  });
});
