// 客户 API —— 对接 yiti `/api/customers`（CustomerController）
//
// 后端 PageResult<CustomerDTO>，CustomerDTO 字段：
//   id / custNo / custName / unifiedCreditCode / industry / industryName /
//   ownerOrgId / ownerOrgName / status / createdAt ...
//
// 前端统一形态（与 EMP/CUST 候选选择器对齐）：
//   { id, name, org, industry, status }
//
// 真接口失败 / 表为空 时回退 mock customersList（已是前端形态）。
import { call } from './http';
import { customersList } from '@/mock';

function toFront(c) {
  if (!c) return c;
  // mock 已是前端形态（无 custName 字段）→ 原样返回
  if (c.custName == null) return c;
  return {
    id: c.id || c.custNo,
    name: c.custName,
    org: c.ownerOrgName || '',
    industry: c.industryName || c.industry || '',
    status: c.status
  };
}

// GET /api/customers —— 客户主档分页列表
// view 期望拿到数组：unwrap PageResult.records 后字段适配
export async function listCustomers(params = {}) {
  const r = await call('get', '/customers', { params }, customersList);
  if (Array.isArray(r)) return r.map(toFront);
  if (Array.isArray(r?.records)) return r.records.map(toFront);
  return [];
}

// GET /api/customers/{id}
export async function getCustomer(id) {
  const r = await call('get', `/customers/${id}`, {},
    () => customersList.find(c => c.id === id) || customersList[0]);
  return toFront(r);
}
