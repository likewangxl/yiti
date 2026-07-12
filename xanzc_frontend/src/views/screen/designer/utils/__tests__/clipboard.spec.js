import { describe, it, expect } from 'vitest';
import { cloneComponentForClipboard, pasteFromClipboard } from '../clipboard';

// 剪贴板纯函数——DesignerV2 快捷键 Ctrl+C/Ctrl+V 与 canvas/ContextMenu.vue 右键复制粘贴
// 对齐同一口径(深拷贝 + 新 id + top/left 各 +20),抽成纯函数便于独立单测(TDD)。
describe('剪贴板纯函数(Ctrl+C/V 与右键复制粘贴同一口径)', () => {
  it('cloneComponentForClipboard 深拷贝节点,不引用原对象', () => {
    const src = { id: 'w-abc123', component: 'TextLabel', style: { top: 10, left: 10 } };
    const clip = cloneComponentForClipboard(src);
    expect(clip).toEqual(src);
    expect(clip).not.toBe(src);
    clip.style.top = 999;
    expect(src.style.top).toBe(10); // 修改克隆件不影响原节点
  });

  it('cloneComponentForClipboard(null) 返回 null(未选中组件时复制无效)', () => {
    expect(cloneComponentForClipboard(null)).toBeNull();
  });

  it('pasteFromClipboard 生成新 id 且 top/left 各按默认偏移量 +20', () => {
    const clip = { id: 'w-orig01', component: 'TextLabel', style: { top: 10, left: 10, width: 100, height: 40 } };
    const node = pasteFromClipboard(clip);
    expect(node.id).toMatch(/^w-/);
    expect(node.id).not.toBe(clip.id);
    expect(node.style.top).toBe(30);
    expect(node.style.left).toBe(30);
    // 宽高等其余字段原样保留
    expect(node.style.width).toBe(100);
    expect(node.component).toBe('TextLabel');
  });

  it('pasteFromClipboard 支持自定义偏移量', () => {
    const clip = { id: 'w-orig02', style: { top: 0, left: 0 } };
    const node = pasteFromClipboard(clip, 5);
    expect(node.style.top).toBe(5);
    expect(node.style.left).toBe(5);
  });

  it('pasteFromClipboard 两次粘贴生成不同 id(可连续粘贴多份)', () => {
    const clip = { id: 'w-orig03', style: { top: 0, left: 0 } };
    const a = pasteFromClipboard(clip);
    const b = pasteFromClipboard(clip);
    expect(a.id).not.toBe(b.id);
  });

  it('pasteFromClipboard(null) 返回 null(剪贴板为空时粘贴不产生节点)', () => {
    expect(pasteFromClipboard(null)).toBeNull();
  });
});
