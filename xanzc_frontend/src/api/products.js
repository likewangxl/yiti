// 产品资料库 API —— 对接 yiti ProductController(/api/products)
import { call } from './http';

// GET /api/products —— 分页（ProductQueryReqDTO: keyword/category/productDeptOrgCode/supportForSupportRequest/status/pageNo/pageSize）
// 返回 PageResult<ProductDTO>，http 拦截器自动取 body.page → { records, total, pageNo, pageSize }
export function listProducts(params = {}) {
  return call('get', '/products', { params }, { records: [], total: 0 });
}

// GET /api/products/support-available —— 中场支持可用产品 List<ProductSimpleDTO>
export function supportAvailableProducts() {
  return call('get', '/products/support-available', {}, []);
}

// GET /api/products/{id} —— 详情 ProductDTO
export function getProduct(id) {
  return call('get', `/products/${id}`, {}, {});
}

// POST /api/products —— 新增（ProductCreateReqDTO）
export function createProduct(data) {
  return call('post', '/products', { data }, { ok: true });
}

// PUT /api/products/{id} —— 编辑（ProductUpdateReqDTO）
export function updateProduct(id, data) {
  return call('put', `/products/${id}`, { data }, { ok: true });
}

// DELETE /api/products/{id}
export function deleteProduct(id) {
  return call('delete', `/products/${id}`, {}, { ok: true });
}
