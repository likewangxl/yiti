/** 大屏设计器在浏览器中复用的固定命名窗口。 */
export const SCREEN_DESIGNER_WINDOW_NAME = 'yiti-screen-designer';

/** 设计器菜单对应的工作区路由。 */
export const SCREEN_DESIGNER_ROUTE_PATH = '/screen-admin/designer';

const SCREEN_DESIGNER_WINDOW_URL = `/#${SCREEN_DESIGNER_ROUTE_PATH}`;

/**
 * 在浏览器默认尺寸的命名窗口中打开设计器。
 * 固定名称让浏览器复用已存在的设计器窗口；返回 false 表示弹窗被阻止。
 */
export function openScreenDesignerWindow() {
  try {
    const designerWindow = window.open(
      SCREEN_DESIGNER_WINDOW_URL,
      SCREEN_DESIGNER_WINDOW_NAME
    );
    if (!designerWindow) return false;
    designerWindow.focus();
    return true;
  } catch {
    return false;
  }
}
