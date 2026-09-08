// @vitest-environment node
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const rendererSource = readFileSync(new URL('../components/ScreenRenderer.vue', import.meta.url), 'utf8');

describe('ScreenRenderer 运行时边界', () => {
  it('加载历史发布布局时不依赖拖拽编辑器注册表或 store', () => {
    expect(rendererSource).not.toContain("@/views/screen/designer/widgets");
    expect(rendererSource).not.toContain("@/stores/screenDesigner");
    expect(rendererSource).toContain("@/views/screen/components/runtimeWidgets");
  });
});
