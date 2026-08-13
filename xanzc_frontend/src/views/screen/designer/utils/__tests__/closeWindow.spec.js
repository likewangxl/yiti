import { describe, expect, it, vi } from 'vitest';
import { closeWindowOrFallback } from '../closeWindow';

describe('closeWindowOrFallback', () => {
  it('命名子窗口关闭成功时不执行路由降级', async () => {
    const fallback = vi.fn();
    const win = { closed: false, close: vi.fn(function close() { this.closed = true; }) };

    await closeWindowOrFallback(win, fallback);

    expect(win.close).toHaveBeenCalledTimes(1);
    expect(fallback).not.toHaveBeenCalled();
  });

  it('直接访问或浏览器拒绝关闭时返回 workspace', async () => {
    const fallback = vi.fn().mockResolvedValue();
    const win = { closed: false, close: vi.fn() };

    await closeWindowOrFallback(win, fallback);

    expect(win.close).toHaveBeenCalledTimes(1);
    expect(fallback).toHaveBeenCalledTimes(1);
  });
});
