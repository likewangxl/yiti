// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  SCREEN_DESIGNER_WINDOW_NAME,
  openScreenDesignerWindow
} from '../screenDesignerWindow';

afterEach(() => vi.restoreAllMocks());

describe('openScreenDesignerWindow', () => {
  it('使用固定安全名称复用浏览器默认尺寸窗口，并在每次打开后聚焦', () => {
    const focus = vi.fn();
    const designerWindow = { focus };
    const open = vi.spyOn(window, 'open').mockReturnValue(designerWindow);

    expect(openScreenDesignerWindow()).toBe(true);
    expect(openScreenDesignerWindow()).toBe(true);

    expect(SCREEN_DESIGNER_WINDOW_NAME).toBe('yiti-screen-designer');
    expect(open).toHaveBeenNthCalledWith(1, '/#/screen-admin/designer', SCREEN_DESIGNER_WINDOW_NAME);
    expect(open).toHaveBeenNthCalledWith(2, '/#/screen-admin/designer', SCREEN_DESIGNER_WINDOW_NAME);
    expect(open.mock.calls.every((call) => call.length === 2)).toBe(true);
    expect(focus).toHaveBeenCalledTimes(2);
  });

  it('浏览器阻止弹窗时返回 false，交由调用方反馈且不做同页降级', () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);

    expect(openScreenDesignerWindow()).toBe(false);
    expect(open).toHaveBeenCalledTimes(1);
  });

  it('浏览器安全策略抛出异常时同样返回 false', () => {
    vi.spyOn(window, 'open').mockImplementation(() => {
      throw new DOMException('Blocked', 'SecurityError');
    });

    expect(openScreenDesignerWindow()).toBe(false);
  });
});
