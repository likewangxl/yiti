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
