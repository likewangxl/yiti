/** 大屏管理在浏览器中复用的固定命名窗口。 */
export const SCREEN_ADMIN_WINDOW_NAME = 'yiti-screen-admin';

/** 大屏管理菜单对应的工作区路由；保留旧路径以兼容历史书签与 RBAC。 */
export const SCREEN_ADMIN_ROUTE_PATH = '/screen-admin/designer';

const SCREEN_ADMIN_WINDOW_URL = `/#${SCREEN_ADMIN_ROUTE_PATH}`;

/**
 * 在浏览器默认尺寸的命名窗口中打开大屏管理。
 * 固定名称让浏览器复用已存在的管理窗口；返回 false 表示弹窗被阻止。
 */
export function openScreenAdminWindow() {
  try {
    const adminWindow = window.open(
      SCREEN_ADMIN_WINDOW_URL,
      SCREEN_ADMIN_WINDOW_NAME
    );
    if (!adminWindow) return false;
    adminWindow.focus();
    return true;
  } catch {
    return false;
  }
}

// 兼容仍引用旧工具名的插件/页面；实际打开的已是大屏管理页。
export const SCREEN_DESIGNER_WINDOW_NAME = SCREEN_ADMIN_WINDOW_NAME;
export const SCREEN_DESIGNER_ROUTE_PATH = SCREEN_ADMIN_ROUTE_PATH;
export const openScreenDesignerWindow = openScreenAdminWindow;
