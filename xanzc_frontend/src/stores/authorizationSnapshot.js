/**
 * 授权快照失效通知中心。
 *
 * 这里只维护回调，不反向依赖具体 Pinia store，避免 http → store → api/http 的循环依赖。
 * 用户会话建立、退出或 401 时统一触发，menu/permission store 各自负责废弃 pending 请求。
 */
const resetters = new Set();

export function registerAuthorizationReset(resetter) {
  resetters.add(resetter);
  return () => resetters.delete(resetter);
}

export function resetAuthorizationSnapshots() {
  for (const reset of [...resetters]) {
    reset();
  }
}
