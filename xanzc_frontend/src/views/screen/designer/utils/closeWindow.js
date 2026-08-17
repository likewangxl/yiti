/**
 * 尝试关闭当前设计器窗口。
 *
 * 浏览器只允许脚本关闭由脚本打开的窗口；用户直接访问 URL 时 close() 会被拒绝。
 * 关闭后仅在窗口仍开启时执行路由降级，不用 opener/history 猜测浏览器权限。
 */
export async function closeWindowOrFallback(win, fallback) {
  try {
    win.close();
  } catch {
    // 个别宿主会直接抛出权限异常，与静默拒绝统一走降级路径。
  }
  if (!win.closed) await fallback();
}
