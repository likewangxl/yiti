import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const designerSource = readFileSync(
  fileURLToPath(new URL('../DesignerV2.vue', import.meta.url)),
  'utf8'
);
const scopedStyle = designerSource.match(/<style\s+scoped[^>]*>([\s\S]*?)<\/style>/)?.[1] ?? '';

describe('DesignerV2.vue 图表标题对比度样式契约', () => {
  it('在设计器画布作用域为图表标题提供浅色高对比度和弱发光', () => {
    const titleRule = scopedStyle.match(
      /\.scr-surface-host\s*:deep\(\.scr-block-h\)\s*\{([\s\S]*?)\}/
    )?.[1] ?? '';

    expect(titleRule).toMatch(/color\s*:\s*var\(--scr-text,\s*#f5fbff\)\s*;/);
    expect(titleRule).toMatch(
      /text-shadow\s*:\s*0\s+0\s+10px\s+rgba\(0\s*,\s*229\s*,\s*255\s*,\s*\.35\)\s*;/
    );
  });
});
