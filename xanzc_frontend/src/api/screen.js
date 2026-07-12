// 经营管理大屏 API —— 对接 yiti report-analytics-center screen 子域
//
// 后端控制器（report-analytics-center/controller/）：
//   - ScreenDatasourceAdminController  /api/screen/admin/datasources[...]
//   - ScreenConfigAdminController      /api/screen/admin/screens[...] + /map-points
//   - ScreenViewController             GET /api/screen/view/{screenCode}
//   - ScreenDataController             POST /api/screen/data
//
// 大屏展示类接口一律不给 mock 兜底（宁可空屏不给假数据，同 Dashboard.vue 先例）。
import { call } from './http';

// ===== 数据源管理 =====
export function listScreenDatasources(params = {}) {
  return call('get', '/screen/admin/datasources', { params }, []);
}
export function saveScreenDatasource(data) {
  return call('post', '/screen/admin/datasources', { data });
}
export function updateScreenDatasource(id, data) {
  return call('put', `/screen/admin/datasources/${id}`, { data });
}
export function deleteScreenDatasource(id) {
  return call('delete', `/screen/admin/datasources/${id}`, {});
}
export function tryRunScreenDatasource(data) {
  return call('post', '/screen/admin/datasources/try-run', { data });
}

// ===== 大屏布局管理 =====
export function listScreens() {
  return call('get', '/screen/admin/screens', {}, []);
}
export function getScreen(id) {
  return call('get', `/screen/admin/screens/${id}`, {}, null);
}
export function saveScreen(data) {
  return call('post', '/screen/admin/screens', { data });
}
export function deleteScreen(id) {
  return call('delete', `/screen/admin/screens/${id}`, {});
}
export function listMapPoints() {
  return call('get', '/screen/admin/map-points', {}, []);
}
export function saveMapPoints(points) {
  return call('put', '/screen/admin/map-points', { data: points });
}

// ===== 大屏运行时 =====
export function getScreenView(screenCode) {
  return call('get', `/screen/view/${screenCode}`, {}, null);
}
export function queryScreenData(body) {
  // silent:true —— 大屏区块取数失败不弹全局 toast，由 BlockContainer 按业务码内联展示（引导态/错误态），
  // 避免一屏多区块并行失败时 toast 轰炸（配合 http.js 响应拦截器的 silent 分支）
  return call('post', '/screen/data', { data: body, silent: true }, null);
}
