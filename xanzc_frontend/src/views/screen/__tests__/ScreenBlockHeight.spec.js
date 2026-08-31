// .scr-block 高度契约——修复"echarts 图表全部空白"根因(2026-07-18):
// 运行时(ScreenRenderer .scr-abs)与设计器(Shape → .w-chart)的宿主都是定高盒子,
// BlockContainer 根元素 .scr-block 若无 height:100% 会随内容塌陷,
// echarts 组件(height:100% 链)拿到 0 高度 canvas 静默空白(DOM 类组件靠内容撑高故不受影响)。
// 该 bug 无 console 报错、无空态占位,只能靠样式契约守护防回归。
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const scss = readFileSync(
  resolve(dirname(fileURLToPath(import.meta.url)), '../../../styles/_screen-theme.scss'),
  'utf8'
);
const blockContainerSfc = readFileSync(
  resolve(dirname(fileURLToPath(import.meta.url)), '../components/BlockContainer.vue'),
  'utf8'
);

describe('.scr-block 高度契约', () => {
  it('.scr-block 规则块内必须声明 height: 100%（宿主恒为定高盒子，缺失则 echarts 图表 0 高度空白）', () => {
    // 只取 .scr-block { 到第一个嵌套规则的 { 之间的"顶层声明段"，
    // 避免嵌套的 .scr-block-empty { height:100% } 让断言假绿
    const m = scss.match(/\.scr-block\s*\{([^{]*)/);
    expect(m, '_screen-theme.scss 中应存在 .scr-block 规则块').toBeTruthy();
    expect(m[1]).toMatch(/height:\s*100%/);
  });

  it('BlockContainer 自带结构高度兜底，设计器脱离 screen shell 时仍保持区块可伸展', () => {
    const scopedStyle = blockContainerSfc.match(/<style\s+scoped[^>]*>([\s\S]*?)<\/style>/)?.[1] || '';
    const blockRule = scopedStyle.match(/\.scr-block\s*\{([^}]*)\}/)?.[1] || '';
    const bodyRule = scopedStyle.match(/\.scr-block-body\s*\{([^}]*)\}/)?.[1] || '';
    const headerRule = scopedStyle.match(/\.scr-block-h\s*\{([^}]*)\}/)?.[1] || '';
    expect(blockRule).toMatch(/height:\s*100%/);
    expect(blockRule).toMatch(/display:\s*flex/);
    expect(blockRule).toMatch(/flex-direction:\s*column/);
    expect(blockRule).toMatch(/min-width:\s*0/);
    expect(blockRule).toMatch(/min-height:\s*0/);
    expect(blockRule).toMatch(/overflow:\s*hidden/);
    expect(headerRule).toMatch(/flex:\s*none/);
    expect(bodyRule).toMatch(/flex:\s*1/);
    expect(bodyRule).toMatch(/min-height:\s*0/);
    expect(bodyRule).toMatch(/position:\s*relative/);
    expect(bodyRule).toMatch(/overflow:\s*hidden/);
  });
});
