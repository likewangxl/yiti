import axios from 'axios';
import { ElMessage } from 'element-plus';

export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';
export const API_BASE = import.meta.env.VITE_API_BASE || '/api';

const http = axios.create({
  baseURL: '',           // proxy 已经在 vite.config 配 /api → yiti
  timeout: 15000,
  withCredentials: true  // 走 yiti session
});

// 请求拦截：注入 traceId（yiti audit_log 需要）
http.interceptors.request.use(cfg => {
  cfg.headers['X-Trace-Id'] = `pc-${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
  return cfg;
});

// 401 时跳登录页：用 hashchange 解耦，避免 http.js 直接 import router 形成循环
// 同时清空 user store 缓存
let unauth401Lock = false;
function gotoLogin() {
  if (unauth401Lock) return;
  unauth401Lock = true;
  setTimeout(() => { unauth401Lock = false; }, 1500);
  try { sessionStorage.removeItem('xanzc:user'); } catch {}
  // 已经在登录页就别再跳
  if (location.hash.startsWith('#/login')) return;
  const cur = location.hash.replace(/^#/, '') || '/';
  location.hash = '#/login?redirect=' + encodeURIComponent(cur);
}

// 响应拦截：yiti ResponseWrapper 解包 + 401 跳登录 + 业务码校验
// 实际 yiti 返回：{ code:'0', message:'success', traceId, data, page, timestamp }
http.interceptors.response.use(
  res => {
    const body = res.data;
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === '00000' || body.code === 0 || body.code === '0') {
        // yiti 分页响应把数据塞在 body.page（PageResult），不是 body.data。
        // body.page 形如 { pageNo, pageSize, total, totalPages, records }
        if (body.page) return body.page;
        return body.data;
      }
      // 业务失败：yiti 字段名是 message，不是 msg
      const m = body.message || body.msg;
      // silent 标记（如大屏区块取数）：不弹全局 toast，交由调用方按业务码内联展示；避免多区块并行失败时 toast 轰炸
      if (!res.config?.silent) {
        ElMessage.error(m || `请求失败 (${body.code})`);
      }
      // reject 的 error 携带业务码（如 RPT-43010），业务侧 catch 可据此区分「缺参引导」与「真错误」
      const bizErr = new Error(m || String(body.code));
      bizErr.code = body.code;
      return Promise.reject(bizErr);
    }
    // 非 envelope（比如二进制、流），原样返回
    return body;
  },
  err => {
    // silent 标记（如大屏区块取数）：抑制全局 toast，交由调用方内联展示；401 会话过期不受抑制（必须提示+跳登录）
    const silent = err.config?.silent;
    if (err.response) {
      const { status, data } = err.response;
      // 后端 yiti 业务错也走 200 + ResponseWrapper.error；这里 4xx/5xx 通常是 Spring 框架级
      // 错误（参数校验失败、JSON 反序列化错），data 里仍可能有 { code, message }
      const bizMsg = data?.message || data?.msg;
      const bizCode = data?.code;
      if (status === 401) {
        // 登录接口本身返回的 401 不要再跳登录页（避免登录失败时 ElMessage 被覆盖）
        if (!err.config?.url?.endsWith('/auth/login')) {
          ElMessage.warning('未登录或会话过期，请重新登录');
          gotoLogin();
        }
      } else if (status === 403) {
        if (!silent) ElMessage.error('没有权限');
      } else if (status >= 500) {
        if (!silent) ElMessage.error(bizMsg || '服务器异常，请稍后重试');
      } else {
        // 400 Bad Request 等：把后端真实 message 抛出，方便用户看到"metricCode 不能为空"等
        if (!silent) ElMessage.error(bizMsg || `请求失败 (${status})`);
      }
      // 把 message / 业务码挂到 err 对象上，业务侧 catch 能直接读 err.message / err.code
      if (bizMsg) err.message = bizMsg;
      if (bizCode) err.code = bizCode;
    } else {
      if (!silent) ElMessage.error('网络异常或后端未启动');
    }
    return Promise.reject(err);
  }
);

/**
 * yiti PageResult 兜底：后端分页接口返回 { records|list|content, total, page, size }，
 * 前端 view 只关心数组，统一抽出来。如果传入的本身就是数组（mock 阶段）或别的
 * 形态，原样返回。
 */
export function unwrapPage(r) {
  if (r == null) return r;
  if (Array.isArray(r)) return r;
  // 保留 {records, total} 结构供分页组件用
  const arr = r.records || r.list || r.content || r.rows || r.data;
  if (Array.isArray(arr)) return { records: arr, total: r.total ?? r.totalCount ?? arr.length };
  return r;
}

/**
 * 包一层"先 HTTP，失败回退 mock"——开发期最保险。
 * @param {string} method 'get' | 'post' | ...
 * @param {string} url
 * @param {object} [config] axios config（params/data 等）
 * @param {function|*} fallback 后端不可用 / 返回异常时使用的 mock 数据（可以是函数）
 */
export async function call(method, url, config = {}, fallback = null) {
  if (USE_MOCK) {
    return typeof fallback === 'function' ? fallback() : fallback;
  }
  try {
    return await http.request({ method, url: API_BASE + url, ...config });
  } catch (err) {
    // V1.6 修复 Bug5：写操作（POST/PUT/DELETE/PATCH）真错时必须 throw，
    // 不再被 fallback 静默吞掉，否则前端拿到 mock { id:'mock' } 会误判成功并关掉弹窗。
    // 仅 GET 类查询走 fallback 兜底（用于后端崩溃时也能展示骨架）。
    const isWrite = method && method.toLowerCase() !== 'get';
    if (fallback != null && !isWrite) {
      console.warn(`[api fallback] ${method.toUpperCase()} ${url} 失败，使用 mock 兜底`, err.message);
      return typeof fallback === 'function' ? fallback() : fallback;
    }
    throw err;
  }
}

export default http;
