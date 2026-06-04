// 机构 API —— 对接 yiti `/api/orgs`（OrgController）
//
// 后端 OrgTreeNodeDTO 字段：
//   orgCode / orgName / orgLevel / parentOrgCode / organState / children
//
// 前端统一形态（与 mock orgsTree 一致）：
//   { code, name, level, children }
//
// 后端不可用时回退 mock orgsTree（已是前端形态）。
import { call } from './http';
import { orgsTree } from '@/mock';

function toFront(node) {
  if (!node) return node;
  // mock 已是前端形态（node.code 存在）→ 仅递归处理 children
  const code = node.code ?? node.orgCode;
  const name = node.name ?? node.orgName;
  const out = { code, name };
  const deptNo = node.deptNo ?? node.dept_no;
  if (deptNo != null) out.deptNo = deptNo;
  // 机构状态：0-启用 1-禁用（用于左树过滤 + 维护弹窗标记）
  const status = node.status ?? node.organState;
  if (status != null) out.status = status;
  if (node.level ?? node.orgLevel) out.level = node.level ?? node.orgLevel;
  if (Array.isArray(node.children) && node.children.length) {
    out.children = node.children.map(toFront);
  }
  return out;
}

export async function getOrgTree() {
  const r = await call('get', '/orgs/tree', {}, orgsTree);
  return Array.isArray(r) ? r.map(toFront) : [];
}

export async function getOrgSubtree(orgCode) {
  const r = await call('get', '/orgs/subtree', { params: { orgCode } }, orgsTree);
  return Array.isArray(r) ? r.map(toFront) : [];
}

export function listOrgUsers(orgCode, params = {}) {
  return call('get', `/orgs/${orgCode}/users`, { params }, []);
}

/** 新增机构。pId 为父机构编码，根节点传 '' */
export function createOrg(payload) {
  return call('post', '/orgs', { data: payload }, () => ({ ...payload }));
}
/** 更新机构（只支持改 orgName） */
export function updateOrg(orgCode, payload) {
  return call('put', `/orgs/${orgCode}`, { data: payload }, () => ({ orgCode, ...payload }));
}
/** 删除机构（后端 will 拒绝：有下级 / 有用户） */
export function deleteOrg(orgCode) {
  return call('delete', `/orgs/${orgCode}`, {}, () => ({ ok: true }));
}
